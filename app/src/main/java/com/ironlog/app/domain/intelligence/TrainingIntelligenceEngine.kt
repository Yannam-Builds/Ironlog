package com.ironlog.app.domain.intelligence

import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.domain.training.TrainingSetPolicy
import com.ironlog.app.domain.training.PersonalBestPolicy
import com.ironlog.app.ui.model.HistoryEntry
import java.time.LocalDate
import java.time.Clock
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlin.math.max
import kotlin.math.roundToInt

data class VolumeLandmark(val sets: Int, val status: String, val min: Int, val max: Int, val optimal: Int)

data class TrainingIntelligenceProfile(
    val goalMode: String = "hypertrophy",
    val weeklyGoalDays: Int = 3,
)

data class NeuralFatigueResult(
    val isFlagged: Boolean,
    val consecutiveDays: Int,
    val lastHeavyExercises: List<String> = emptyList(),
)

data class TrainingIntelligenceSnapshot(
    /** 6 anatomical groups (Chest/Back/Legs/Shoulders/Arms/Core), based on current ISO week. */
    val setsByMuscle: Map<String, Int>,
    val volumeLandmarks: Map<String, VolumeLandmark>,
    /** Push = Chest+Shoulders %, Pull = Back %, Legs = Legs % */
    val movementBalance: Map<String, Int>,
    val prLast30: Int,
    val prPrev30: Int,
    val prTrend: String,        // "accelerating" | "steady" | "slowing"
    val prVelocity30d: Float,   // prLast30 / session count (0-1)
    val bestWindow: String,
    val trainingAgeYears: Double,
    val trainingAgeLabel: String,
    val trainingAgeTip: String,
    val neuralFatigue: NeuralFatigueResult,
)

object TrainingIntelligenceEngine {

    // 6 anatomical groups — matches RN's MUSCLE_GROUPS / VOLUME_LANDMARKS
    private val VOLUME_LANDMARKS_MAP = mapOf(
        "Chest"     to Triple(10, 20, 14),
        "Back"      to Triple(10, 22, 16),
        "Legs"      to Triple(12, 22, 16),
        "Shoulders" to Triple(8,  16, 12),
        "Arms"      to Triple(8,  16, 12),
        "Core"      to Triple(6,  16, 10),
    )

    private val HEAVY_COMPOUNDS = setOf(
        "squat", "deadlift", "bench press", "overhead press", "ohp", "barbell row",
        "power clean", "front squat", "sumo", "romanian",
    )

    /** Warmup builder for working sets; rounds to nearest 2.5kg and keeps at least bar weight. */
    fun generateWarmupSets(workingWeightKg: Double, barWeightKg: Double = 20.0): List<Pair<Double, Int>> {
        if (workingWeightKg <= 0.0 || workingWeightKg <= barWeightKg) return emptyList()
        val base = workingWeightKg
        val steps = listOf(
            0.40 to 8,
            0.55 to 5,
            0.70 to 3,
            0.85 to 1,
        )
        return steps.map { (pct, reps) ->
            val w = ((base * pct) / 2.5).roundToInt() * 2.5
            val clamped = w.coerceAtLeast(barWeightKg.coerceAtMost(base))
            clamped to reps
        }
            .filter { (weight, _) -> weight < base }
            .distinctBy { it.first }
    }

    fun build(
        history: List<HistoryEntry>,
        @Suppress("UNUSED_PARAMETER") prCount: Int = 0,
        activeDraftContribution: Map<String, Int> = emptyMap(),
        profile: TrainingIntelligenceProfile = TrainingIntelligenceProfile(),
        clock: Clock = Clock.systemDefaultZone(),
        prResetAt: Instant? = null,
    ): TrainingIntelligenceSnapshot {
        // One time/zone snapshot for the entire evaluation, including local date-only imports.
        val evaluationClock = Clock.fixed(clock.instant(), clock.zone)
        val validHistory = history.filter { entry ->
            val at = parseHistoryInstant(entry.date, evaluationClock.zone)
            at != null && !at.isAfter(evaluationClock.instant()) && entry.exercises.any { ex ->
                ex.sets.any { !it.type.equals("warmup", true) && it.weight.isFinite() && it.weight >= 0 && it.reps.isFinite() && it.reps > 0 }
            }
        }
        val last30 = validHistory.filter { ageDays(it.date, evaluationClock) in 0..30 }
        val byGroup = setsByGroup(validHistory, activeDraftContribution, evaluationClock)
        val (prLast30, prPrev30, prTrend) = computePrVelocity(validHistory, evaluationClock, prResetAt)
        val velocity = if (last30.isEmpty()) 0f
            else (prLast30.toFloat() / last30.size.toFloat()).coerceIn(0f, 1f)
        val (years, label, tip) = computeTrainingAge(validHistory, evaluationClock)
        return TrainingIntelligenceSnapshot(
            setsByMuscle    = byGroup,
            volumeLandmarks = computeVolumeLandmarks(byGroup, profile),
            movementBalance = movementBalance(byGroup),
            prLast30        = prLast30,
            prPrev30        = prPrev30,
            prTrend         = prTrend,
            prVelocity30d   = velocity,
            bestWindow      = bestPerformanceWindow(last30, evaluationClock),
            trainingAgeYears = years,
            trainingAgeLabel = label,
            trainingAgeTip   = tip,
            neuralFatigue    = computeNeuralFatigue(validHistory, evaluationClock),
        )
    }

    /** Public entry-point for VolumeInterpretationEngine and other callers. */
    fun buildLandmarks(byMuscle: Map<String, Int>): Map<String, VolumeLandmark> =
        computeVolumeLandmarks(byMuscle)

    // ── Volume landmarks ──────────────────────────────────────────────────────

    private fun computeVolumeLandmarks(
        byGroup: Map<String, Int>,
        profile: TrainingIntelligenceProfile = TrainingIntelligenceProfile(),
    ): Map<String, VolumeLandmark> {
        val result = linkedMapOf<String, VolumeLandmark>()
        VOLUME_LANDMARKS_MAP.forEach { (group, triple) ->
            val goalScale = when (profile.goalMode.trim().lowercase()) {
                "strength" -> 0.78
                "general_fitness", "general fitness", "performance", "endurance" -> 0.68
                else -> 1.0
            }
            val scheduleScale = if (profile.weeklyGoalDays.coerceIn(1, 7) <= 2) 0.82 else 1.0
            val scale = goalScale * scheduleScale
            val min = (triple.first * scale).roundToInt().coerceAtLeast(4)
            val max = (triple.second * scale).roundToInt().coerceAtLeast(min + 4)
            val optimal = (triple.third * scale).roundToInt().coerceIn(min, max)
            val sets = byGroup[group] ?: 0
            val status = when {
                sets < min -> "low"
                sets > max -> "high"
                else -> "optimal"
            }
            result[group] = VolumeLandmark(sets = sets, status = status, min = min, max = max, optimal = optimal)
        }
        return result
    }

    // ── Sets-by-group (current ISO week only) ─────────────────────────────────

    /**
     * Accumulates working sets per 6 anatomical group using [FINE_MUSCLE_TO_RADAR].
     * Uses the current ISO week (Mon–now) to match RN's weekly volume card behaviour.
     */
    private fun setsByGroup(history: List<HistoryEntry>, activeDraftContribution: Map<String, Int>, clock: Clock): Map<String, Int> {
        val thisWeekStart = LocalDate.now(clock).with(WeekFields.ISO.dayOfWeek(), 1L)
        val weekHistory = history.filter { entry ->
            val d = parseHistoryInstant(entry.date, clock.zone)?.atZone(clock.zone)?.toLocalDate()
                ?: return@filter false
            !d.isBefore(thisWeekStart) && !d.isAfter(LocalDate.now(clock))
        }
        val accumulator = linkedMapOf(
            "Chest" to 0.0, "Back" to 0.0, "Legs" to 0.0,
            "Shoulders" to 0.0, "Arms" to 0.0, "Core" to 0.0,
        )
        weekHistory.forEach { w ->
            w.exercises.forEach { ex ->
                val n = ex.sets.count { TrainingSetPolicy.isValidWorkingSet(ex, it) }
                if (n > 0) {
                    val contrib = resolveContribution(ex)
                    if (contrib.isNotEmpty()) {
                        foldContributions(contrib, FINE_MUSCLE_TO_RADAR).forEach { (bucket, frac) ->
                            accumulator[bucket] = (accumulator[bucket] ?: 0.0) + n * frac
                        }
                    } else {
                        dominantRadarBucket(ex)?.let { bucket ->
                            accumulator[bucket] = (accumulator[bucket] ?: 0.0) + n
                        }
                    }
                }
            }
        }
        val base = accumulator.mapValues { it.value.roundToInt() }.toMutableMap()
        // Draft contribution: PPL keys title-cased → map to radar buckets
        activeDraftContribution.forEach { (k, v) ->
            val key = k.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val radarKey = when (key) {
                "Push" -> "Chest"; "Pull" -> "Back"
                "Legs" -> "Legs"; "Core" -> "Core"
                else -> if (key in base) key else null
            }
            if (radarKey != null && v > 0) base[radarKey] = (base[radarKey] ?: 0) + v
        }
        return base
    }

    private fun dominantRadarBucket(ex: com.ironlog.app.ui.model.HistoryExercise): String? {
        val t = (listOfNotNull(ex.primaryMuscle) + ex.primaryMuscles +
                listOf(ex.name, ex.category.orEmpty())).joinToString(" ").lowercase()
        return when {
            t.contains("chest") || t.contains("pec") -> "Chest"
            t.contains("back") || t.contains("lat") || t.contains("row") -> "Back"
            t.contains("shoulder") || t.contains("delt") -> "Shoulders"
            t.contains("bicep") || t.contains("tricep") || t.contains("curl") -> "Arms"
            t.contains("quad") || t.contains("hamstring") || t.contains("glute") ||
            t.contains("calf") || t.contains("leg") || t.contains("squat") ||
            t.contains("deadlift") -> "Legs"
            t.contains("core") || t.contains("abs") || t.contains("plank") || t.contains("crunch") -> "Core"
            else -> null
        }
    }

    // ── Movement balance ──────────────────────────────────────────────────────

    /** Push = Chest + Shoulders, Pull = Back, Legs = Legs. Arms / Core excluded from ratio. */
    private fun movementBalance(byGroup: Map<String, Int>): Map<String, Int> {
        val push = (byGroup["Chest"] ?: 0) + (byGroup["Shoulders"] ?: 0)
        val pull = byGroup["Back"] ?: 0
        val legs = byGroup["Legs"] ?: 0
        val total = max(1, push + pull + legs)
        return linkedMapOf(
            "Push" to ((push * 100f) / total).toInt(),
            "Pull" to ((pull * 100f) / total).toInt(),
            "Legs" to ((legs * 100f) / total).toInt(),
        )
    }

    // ── PR velocity ───────────────────────────────────────────────────────────

    /**
     * Counts PR sessions in the last 30 and previous 30-60 days by tracking
     * running e1rm (Epley formula) per exercise across chronologically sorted history.
     */
    private fun computePrVelocity(
        history: List<HistoryEntry>,
        clock: Clock,
        prResetAt: Instant?,
    ): Triple<Int, Int, String> {
        val sorted = history
            .mapNotNull { session -> parseHistoryInstant(session.date, clock.zone)?.let { it to session } }
            .sortedBy { it.first }
            .map { it.second }
        val runningBest = mutableMapOf<String, Double>() // exercise key → best e1rm
        var prLast30 = 0
        var prPrev30 = 0
        sorted.forEach { session ->
            val age = ageDays(session.date, clock)
            var sessionIsPr = false
            session.exercises.forEach { ex ->
                val key = ex.exerciseId.ifBlank { ex.name }
                val maxE1rm = ex.sets
                    .filter { PersonalBestPolicy.isAfterReset(it, session.date, prResetAt, clock.zone) }
                    .mapNotNull { TrainingSetPolicy.estimatedOneRm(ex, it) }
                    .maxOrNull()
                if (maxE1rm != null) {
                    val previous = runningBest[key]
                    if (previous != null && maxE1rm > previous) sessionIsPr = true
                    runningBest[key] = maxOf(previous ?: 0.0, maxE1rm)
                }
            }
            if (sessionIsPr) when (age) {
                in 0L..30L -> prLast30++
                in 31L..60L -> prPrev30++
            }
        }
        val trend = when {
            prLast30 > prPrev30 -> "accelerating"
            prLast30 < prPrev30 -> "slowing"
            else -> "steady"
        }
        return Triple(prLast30, prPrev30, trend)
    }

    // ── Neural fatigue ────────────────────────────────────────────────────────

    /**
     * Checks for consecutive calendar days with heavy compound work in the last 14 days.
     * Flagged when consecutive_days >= 3.
     */
    private fun computeNeuralFatigue(history: List<HistoryEntry>, clock: Clock): NeuralFatigueResult {
        val heavyByDay = history
            .mapNotNull { session ->
                val day = parseHistoryInstant(session.date, clock.zone)?.atZone(clock.zone)?.toLocalDate()
                    ?: return@mapNotNull null
                val age = ChronoUnit.DAYS.between(day, LocalDate.now(clock))
                if (age !in 0..14) return@mapNotNull null
                val names = session.exercises.filter { ex ->
                    val lower = ex.name.lowercase()
                    HEAVY_COMPOUNDS.any { lower.contains(it) } && ex.sets.any { set ->
                        TrainingSetPolicy.estimatedOneRm(ex, set) != null &&
                            ((set.rpe ?: 0.0) >= 8.0 || (set.rir ?: 99.0) <= 2.0 || set.reps in 1.0..6.0)
                    }
                }.map { it.name }
                if (names.isEmpty()) null else day to names
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.flatten().distinct() }
            .toList()
            .sortedByDescending { it.first }

        var consecutive = 0
        var prev: LocalDate? = null
        val lastHeavyEx = mutableListOf<String>()

        val newestDay = heavyByDay.firstOrNull()?.first
        if (newestDay == null || ChronoUnit.DAYS.between(newestDay, LocalDate.now(clock)) > 2) {
            return NeuralFatigueResult(false, 0)
        }

        for ((day, exerciseNames) in heavyByDay) {
            val gap = if (prev == null) 0L else ChronoUnit.DAYS.between(day, prev)
            if (prev == null || gap == 1L) {
                consecutive++
                exerciseNames.forEach { if (lastHeavyEx.size < 3 && it !in lastHeavyEx) lastHeavyEx += it }
                prev = day
            } else {
                break
            }
        }
        return NeuralFatigueResult(
            isFlagged = consecutive >= 3,
            consecutiveDays = consecutive,
            lastHeavyExercises = lastHeavyEx,
        )
    }

    // ── Best performance window ───────────────────────────────────────────────

    private fun bestPerformanceWindow(history: List<HistoryEntry>, clock: Clock): String {
        val buckets = linkedMapOf(
            "Morning" to mutableListOf<Double>(),
            "Afternoon" to mutableListOf<Double>(),
            "Evening" to mutableListOf<Double>(),
            "Night" to mutableListOf<Double>(),
        )
        history.forEach { w ->
            // A date-only import has no known training time and must not become a midnight session.
            if (!w.date.contains('T') && !w.date.contains(' ')) return@forEach
            val hour = parseHistoryInstant(w.date, clock.zone)?.atZone(clock.zone)?.hour ?: return@forEach
            val b = when (hour) {
                in 5..11 -> "Morning"; in 12..16 -> "Afternoon"
                in 17..20 -> "Evening"; else -> "Night"
            }
            val volume = w.exercises.sumOf { ex -> ex.sets.sumOf { TrainingSetPolicy.externalLoadVolume(ex, it) } }
            if (volume.isFinite() && volume > 0) buckets[b]?.add(volume)
        }
        val eligible = buckets.filterValues { it.size >= 3 }
        if (eligible.isEmpty()) return "Log at least 3 sessions in one time window to compare performance."
        val best = eligible.maxByOrNull { (_, values) -> values.average() }
            ?: return "Not enough comparable sessions yet."
        return "${best.key} leads your logged session volume (${best.value.size} sessions)."
    }

    // ── Training age ──────────────────────────────────────────────────────────

    private fun computeTrainingAge(history: List<HistoryEntry>, clock: Clock): Triple<Double, String, String> {
        if (history.isEmpty()) return Triple(
            0.0, "Building baseline", "Track your first workout to start measuring progress."
        )
        val datedHistory = history.mapNotNull { entry ->
            parseHistoryInstant(entry.date, clock.zone)?.atZone(clock.zone)?.toLocalDate()?.let { it to entry }
        }
        val oldestDate = datedHistory.minOfOrNull { it.first }
            ?: return Triple(0.0, "Building baseline", "Keep logging sessions to establish a reliable training history.")
        val months = ChronoUnit.MONTHS.between(oldestDate, LocalDate.now(clock)).coerceAtLeast(0)
        val years = months / 12.0
        val sessions = datedHistory.size
        val (label, tip) = when {
            sessions < 12 || months < 3 ->
                "Building baseline" to "Repeat key movements and progress only when technique and target reps are stable."
            sessions < 50 || months < 12 ->
                "Developing" to "Use small, repeatable load or rep increases while keeping recovery sustainable."
            sessions < 150 || months < 36 ->
                "Established" to "Review volume and performance trends before changing the program."
            else ->
                "Highly experienced" to "Use your own response history to adjust volume, intensity and exercise selection."
        }
        return Triple(years, label, tip)
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun ageDays(iso: String, clock: Clock): Long {
        val d = parseHistoryInstant(iso, clock.zone)?.atZone(clock.zone)?.toLocalDate()
            ?: return Long.MAX_VALUE
        return ChronoUnit.DAYS.between(d, LocalDate.now(clock))
    }
}
