package com.ironlog.app.domain.intelligence

import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.ui.model.HistoryEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlin.math.max
import kotlin.math.roundToInt

data class VolumeLandmark(val sets: Int, val status: String, val min: Int, val max: Int, val optimal: Int)

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
        val base = workingWeightKg.coerceAtLeast(barWeightKg)
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
        }.distinctBy { it.first to it.second }
    }

    fun build(
        history: List<HistoryEntry>,
        @Suppress("UNUSED_PARAMETER") prCount: Int = 0,
        activeDraftContribution: Map<String, Int> = emptyMap(),
    ): TrainingIntelligenceSnapshot {
        val last30 = history.filter { ageDays(it.date) <= 30 }
        val byGroup = setsByGroup(history, activeDraftContribution)
        val (prLast30, prPrev30, prTrend) = computePrVelocity(history)
        val velocity = if (last30.isEmpty()) 0f
            else (prLast30.toFloat() / last30.size.toFloat()).coerceIn(0f, 1f)
        val (years, label, tip) = computeTrainingAge(history)
        return TrainingIntelligenceSnapshot(
            setsByMuscle    = byGroup,
            volumeLandmarks = computeVolumeLandmarks(byGroup),
            movementBalance = movementBalance(byGroup),
            prLast30        = prLast30,
            prPrev30        = prPrev30,
            prTrend         = prTrend,
            prVelocity30d   = velocity,
            bestWindow      = bestPerformanceWindow(last30),
            trainingAgeYears = years,
            trainingAgeLabel = label,
            trainingAgeTip   = tip,
            neuralFatigue    = computeNeuralFatigue(history),
        )
    }

    /** Public entry-point for VolumeInterpretationEngine and other callers. */
    fun buildLandmarks(byMuscle: Map<String, Int>): Map<String, VolumeLandmark> =
        computeVolumeLandmarks(byMuscle)

    // ── Volume landmarks ──────────────────────────────────────────────────────

    private fun computeVolumeLandmarks(byGroup: Map<String, Int>): Map<String, VolumeLandmark> {
        val result = linkedMapOf<String, VolumeLandmark>()
        VOLUME_LANDMARKS_MAP.forEach { (group, triple) ->
            val (min, max, optimal) = triple
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
    private fun setsByGroup(history: List<HistoryEntry>, activeDraftContribution: Map<String, Int>): Map<String, Int> {
        val thisWeekStart = LocalDate.now().with(WeekFields.ISO.dayOfWeek(), 1L)
        val weekHistory = history.filter { entry ->
            val d = parseHistoryInstant(entry.date)?.atZone(ZoneId.systemDefault())?.toLocalDate()
                ?: return@filter false
            !d.isBefore(thisWeekStart)
        }
        val accumulator = linkedMapOf(
            "Chest" to 0.0, "Back" to 0.0, "Legs" to 0.0,
            "Shoulders" to 0.0, "Arms" to 0.0, "Core" to 0.0,
        )
        weekHistory.forEach { w ->
            w.exercises.forEach { ex ->
                val n = ex.sets.count { it.type != "warmup" }
                if (n > 0) {
                    val contrib = resolveContribution(ex)
                    if (contrib.isNotEmpty()) {
                        foldContributions(contrib, FINE_MUSCLE_TO_RADAR).forEach { (bucket, frac) ->
                            accumulator[bucket] = (accumulator[bucket] ?: 0.0) + n * frac
                        }
                    } else {
                        val bucket = dominantRadarBucket(ex)
                        accumulator[bucket] = (accumulator[bucket] ?: 0.0) + n
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

    private fun dominantRadarBucket(ex: com.ironlog.app.ui.model.HistoryExercise): String {
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
            else -> "Core"
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
    private fun computePrVelocity(history: List<HistoryEntry>): Triple<Int, Int, String> {
        val sorted = history.sortedBy { it.date } // oldest first
        val runningBest = mutableMapOf<String, Double>() // exercise key → best e1rm
        var prLast30 = 0
        var prPrev30 = 0
        sorted.forEach { session ->
            val age = ageDays(session.date)
            var sessionIsPr = false
            session.exercises.forEach { ex ->
                val key = ex.exerciseId.ifBlank { ex.name }
                val maxE1rm = ex.sets
                    .filter { it.type != "warmup" }
                    .maxOfOrNull { s ->
                        val w = s.weight; val r = s.reps
                        if (w > 0 && r > 0) w * (1.0 + r / 30.0) else 0.0
                    } ?: 0.0
                if (maxE1rm > 0.0) {
                    val prev = runningBest[key] ?: 0.0
                    if (maxE1rm > prev) {
                        runningBest[key] = maxE1rm
                        sessionIsPr = true
                    }
                }
            }
            if (sessionIsPr) when {
                age <= 30L -> prLast30++
                age <= 60L -> prPrev30++
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
    private fun computeNeuralFatigue(history: List<HistoryEntry>): NeuralFatigueResult {
        val heavyDays = history
            .filter { ageDays(it.date) <= 14 }
            .filter { session ->
                session.exercises.any { ex ->
                    val lower = ex.name.lowercase()
                    HEAVY_COMPOUNDS.any { lower.contains(it) } &&
                        ex.sets.any { it.type != "warmup" && it.weight > 0 }
                }
            }
            .sortedByDescending { it.date } // most recent first

        var consecutive = 0
        var prev: LocalDate? = null
        val lastHeavyEx = mutableListOf<String>()

        for (session in heavyDays) {
            val day = parseHistoryInstant(session.date)?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: break
            val gap = if (prev == null) 0L else ChronoUnit.DAYS.between(day, prev)
            if (prev == null || gap == 1L) {
                consecutive++
                session.exercises
                    .filter { ex -> HEAVY_COMPOUNDS.any { ex.name.lowercase().contains(it) } }
                    .forEach { if (lastHeavyEx.size < 3 && it.name !in lastHeavyEx) lastHeavyEx += it.name }
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

    private fun bestPerformanceWindow(history: List<HistoryEntry>): String {
        val buckets = linkedMapOf(
            "Morning" to mutableListOf<Double>(),
            "Afternoon" to mutableListOf<Double>(),
            "Evening" to mutableListOf<Double>(),
            "Night" to mutableListOf<Double>(),
        )
        history.forEach { w ->
            val hour = parseHistoryInstant(w.date)?.atZone(ZoneId.systemDefault())?.hour ?: return@forEach
            val b = when (hour) {
                in 5..11 -> "Morning"; in 12..16 -> "Afternoon"
                in 17..20 -> "Evening"; else -> "Night"
            }
            buckets[b]?.add(w.volume)
        }
        val best = buckets.maxByOrNull { (_, v) -> if (v.isEmpty()) 0.0 else v.average() }?.key ?: "Morning"
        return "$best has the highest average session volume."
    }

    // ── Training age ──────────────────────────────────────────────────────────

    private fun computeTrainingAge(history: List<HistoryEntry>): Triple<Double, String, String> {
        if (history.isEmpty()) return Triple(
            0.0, "Beginner", "Track your first workout to start measuring progress."
        )
        val oldest = history.minByOrNull { it.date }
            ?: return Triple(0.0, "Beginner", "")
        val oldestDate = parseHistoryInstant(oldest.date)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            ?: return Triple(0.0, "Beginner", "")
        val months = ChronoUnit.MONTHS.between(oldestDate, LocalDate.now()).coerceAtLeast(1)
        val years = months / 12.0

        // Estimate monthly e1rm progression from compound sets
        val compoundKeywords = setOf("bench", "squat", "deadlift", "press", "row")
        fun sessionAvgE1rm(s: HistoryEntry) = s.exercises
            .filter { ex -> compoundKeywords.any { ex.name.lowercase().contains(it) } }
            .flatMap { ex ->
                ex.sets.filter { it.type != "warmup" }.mapNotNull { set ->
                    val w = set.weight; val r = set.reps
                    if (w > 0 && r > 0) w * (1.0 + r / 30.0) else null
                }
            }.average().takeIf { it.isFinite() }

        val compoundSessions = history
            .sortedBy { it.date }
            .filter { sessionAvgE1rm(it) != null }

        val monthlyProgression: Double = if (compoundSessions.size >= 8) {
            val avgFirst = compoundSessions.take(4).mapNotNull { sessionAvgE1rm(it) }.average()
            val avgLast  = compoundSessions.takeLast(4).mapNotNull { sessionAvgE1rm(it) }.average()
            ((avgLast - avgFirst) / months).takeIf { it.isFinite() } ?: 0.0
        } else {
            if (months < 6) 5.0 else 1.0
        }

        val (label, tip) = when {
            monthlyProgression > 3.0 || months < 6 ->
                "Beginner" to "Linear progression works best — add weight to each session."
            monthlyProgression < 0.5 && months > 18 ->
                "Advanced" to "Periodization matters — vary intensity and volume week to week."
            months > 36 ->
                "Elite" to "Consistency and injury prevention are your highest priorities."
            else ->
                "Intermediate" to "Block periodization: focus one quality per cycle (strength, volume, peak)."
        }
        return Triple(years, label, tip)
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun ageDays(iso: String): Long {
        val d = parseHistoryInstant(iso)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            ?: return Long.MAX_VALUE
        return ChronoUnit.DAYS.between(d, LocalDate.now())
    }
}
