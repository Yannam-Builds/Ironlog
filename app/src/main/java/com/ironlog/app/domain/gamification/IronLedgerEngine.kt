package com.ironlog.app.domain.gamification

import androidx.compose.runtime.Immutable
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

enum class IronGrade(
    val label: String,
    val minVerifiedSessions: Int,
    val minQualifyingWeeks: Int,
    val minTenureDays: Int,
) {
    UNCALIBRATED("Uncalibrated", 0, 0, 0),
    GRAPHITE("Graphite", 4, 2, 14),
    IRON("Iron", 12, 3, 28),
    STEEL("Steel", 36, 8, 90),
    TITANIUM("Titanium", 80, 20, 180),
    OBSIDIAN("Obsidian", 160, 40, 365),
    IRIDIUM("Iridium", 300, 90, 730),
    AETHER("Aether", 450, 140, 1095),
    APEX("Apex", 650, 200, 1460),
}

@Immutable
data class AthleteCalibration(
    val trainingAgeMonths: Int = 0,
    val historicalTrainingDaysPerWeek: Int = 3,
    val importedHistory: Boolean = false,
    val weeklyGoalDays: Int = 4,
    val bodyweightKg: Double? = null,
    val hasPastTraining: Boolean = false,
    val hasGymAccess: Boolean = true,
    val baselinePushups: Int = 0,
    val baselinePullups: Int = 0,
    val baselineBenchKg: Int = 0,
    val baselineLatPulldownKg: Int = 0,
    val baselineMileRunSeconds: Int = 0,
)

@Immutable
data class IronLedgerStats(
    val strength: Int = 1,
    val power: Int = 1,
    val hypertrophy: Int = 1,
    val endurance: Int = 1,
    val agility: Int = 1,
    val discipline: Int = 1,
    val recovery: Int = 1,
)

@Immutable
data class IronGradeGate(
    val grade: IronGrade,
    val label: String,
    val current: Int,
    val required: Int,
    val met: Boolean,
)

@Immutable
data class IronLedgerEvent(
    val sourceId: String,
    val kind: String,
    val title: String,
    val detail: String,
    val xp: Int,
    val occurredAt: String,
    val trust: Double,
)

@Immutable
data class IronLedgerSnapshot(
    val totalXp: Long,
    val level: Int,
    val xpInLevel: Long,
    val xpForNextLevel: Long,
    val grade: IronGrade,
    val stats: IronLedgerStats,
    val integrityScore: Double,
    val verifiedSessions: Int,
    val qualifyingWeeks: Int,
    val tenureDays: Int,
    val nextGrade: IronGrade?,
    val nextGradeGates: List<IronGradeGate>,
    val events: List<IronLedgerEvent>,
)

class IronLedgerEngine {
    fun xpForLevel(level: Int): Long =
        (125.0 * level.toDouble() * level.toDouble()).roundToInt().toLong().coerceAtLeast(125L)

    fun levelFromTotalXp(totalXp: Long): Int {
        var level = 1
        var remaining = totalXp
        while (level < 100 && remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level)
            level++
        }
        return level
    }

    fun xpInCurrentLevel(totalXp: Long): Long {
        val level = levelFromTotalXp(totalXp)
        val spent = (1 until level).sumOf { xpForLevel(it) }
        return (totalXp - spent).coerceAtLeast(0L)
    }

    fun rebuild(
        history: List<HistoryEntry>,
        weeklyGoal: Int,
        calibration: AthleteCalibration,
    ): IronLedgerSnapshot {
        val sorted = history.sortedBy { it.date }
        val qualified = sorted.filter(::isQualifyingWorkout)
        val verified = qualified.filterNot { it.imported }
        val firstDate = verified.firstOrNull()?.date?.let(::parseInstant)
        val lastDate = verified.lastOrNull()?.date?.let(::parseInstant) ?: Instant.now()
        val tenureDays = firstDate?.let { ChronoUnit.DAYS.between(it, lastDate).toInt().coerceAtLeast(0) } ?: 0
        val qualifyingWeeks = verified.mapNotNull { weekKey(it.date) }.distinct().size
        val integrity = integrityScore(verified, verified)
        val events = buildEvents(sorted, qualified, integrity)
        val totalXp = events.sumOf { it.xp.toLong() }.coerceAtLeast(0L)
        val level = levelFromTotalXp(totalXp)
        val stats = computeStats(sorted, qualifyingWeeks, calibration)
        val grade = gradeFor(
            verifiedSessions = verified.size,
            qualifyingWeeks = qualifyingWeeks,
            tenureDays = tenureDays,
            integrity = integrity,
            stats = stats,
            calibration = calibration,
        )
        val nextGrade = IronGrade.entries.firstOrNull { it.ordinal > grade.ordinal }
        val gates = nextGrade?.let { ng ->
            buildList {
                add(IronGradeGate(ng, "Verified sessions", verified.size, ng.minVerifiedSessions, verified.size >= ng.minVerifiedSessions))
                add(IronGradeGate(ng, "Qualifying weeks", qualifyingWeeks, ng.minQualifyingWeeks, qualifyingWeeks >= ng.minQualifyingWeeks))
                add(IronGradeGate(ng, "Training tenure", tenureDays, ng.minTenureDays, tenureDays >= ng.minTenureDays))
                // Integrity gate only applies for OBSIDIAN and above
                if (ng.ordinal >= IronGrade.OBSIDIAN.ordinal) {
                    val required = if (ng.ordinal >= IronGrade.APEX.ordinal) 95 else 88
                    val threshold = required / 100.0
                    add(IronGradeGate(ng, "Integrity", (integrity * 100).roundToInt(), required, integrity >= threshold))
                }
            }
        }.orEmpty()

        return IronLedgerSnapshot(
            totalXp = totalXp,
            level = level,
            xpInLevel = xpInCurrentLevel(totalXp),
            xpForNextLevel = xpForLevel(level),
            grade = grade,
            stats = stats,
            integrityScore = integrity,
            verifiedSessions = verified.size,
            qualifyingWeeks = qualifyingWeeks,
            tenureDays = tenureDays,
            nextGrade = nextGrade,
            nextGradeGates = gates,
            events = events.sortedByDescending { it.occurredAt },
        )
    }

    private fun buildEvents(
        history: List<HistoryEntry>,
        qualified: List<HistoryEntry>,
        integrity: Double,
    ): List<IronLedgerEvent> {
        val qualifiedIds = qualified.map { it.id }.toSet()
        val dailyCounts = qualified.groupingBy { dayKey(it.date) ?: it.date }.eachCount()
        val previousBestByExercise = mutableMapOf<String, Double>()
        val events = mutableListOf<IronLedgerEvent>()
        history.sortedBy { it.date }.forEach { workout ->
            if (workout.id !in qualifiedIds) return@forEach
            val dayCount = dailyCounts[dayKey(workout.date) ?: workout.date] ?: 1
            val dailyMultiplier = when {
                dayCount <= 1 -> 1.0
                dayCount == 2 -> 0.45
                else -> 0.0
            }
            val importMultiplier = if (workout.imported) 0.55 else 1.0
            val trust = (integrity * dailyMultiplier * importMultiplier).coerceIn(0.0, 1.0)
            val hardSets = hardSets(workout)
            val baseXp = ((40 + hardSets.coerceAtMost(18) * 2) * trust).roundToInt()
            if (baseXp > 0) {
                events += IronLedgerEvent(
                    sourceId = workout.id,
                    kind = "workout",
                    title = "Training proof logged",
                    detail = "${workout.name} - $hardSets hard sets",
                    xp = baseXp,
                    occurredAt = workout.date,
                    trust = trust,
                )
            }
            workout.exercises.forEach { exercise ->
                val best = exercise.sets
                    .filter(::isWorkingSet)
                    .mapNotNull { set -> estimatedPerformance(exercise, set) }
                    .maxOrNull()
                    ?: return@forEach
                val key = exercise.exerciseId.ifBlank { exercise.name.lowercase(Locale.ROOT) }
                val previous = previousBestByExercise[key]
                if (previous != null && best > previous * 1.025 && trust >= 0.75) {
                    val eventSourceId = "${workout.id}:${exercise.id.ifBlank { key }}"
                    events += IronLedgerEvent(
                        sourceId = eventSourceId,
                        kind = "pr",
                        title = "Verified PR",
                        detail = "${exercise.name} improved ${previous.roundToInt()} -> ${best.roundToInt()}",
                        xp = 35,
                        occurredAt = workout.date,
                        trust = trust,
                    )
                }
                previousBestByExercise[key] = max(previous ?: 0.0, best)
            }
        }
        return events
    }

    private fun gradeFor(
        verifiedSessions: Int,
        qualifyingWeeks: Int,
        tenureDays: Int,
        integrity: Double,
        stats: IronLedgerStats,
        calibration: AthleteCalibration,
    ): IronGrade {
        val balance = listOf(stats.strength, stats.hypertrophy, stats.discipline).average()
        val verifiedGrade = IronGrade.entries.lastOrNull { grade ->
            verifiedSessions >= grade.minVerifiedSessions &&
                qualifyingWeeks >= grade.minQualifyingWeeks &&
                tenureDays >= grade.minTenureDays &&
                (grade.ordinal < IronGrade.OBSIDIAN.ordinal || integrity >= 0.88) &&
                (grade.ordinal < IronGrade.IRIDIUM.ordinal || balance >= 300.0) &&
                (grade.ordinal < IronGrade.APEX.ordinal || integrity >= 0.95)
        } ?: IronGrade.UNCALIBRATED
        val seededGrade = baselineGrade(calibration, stats)
        return if (seededGrade.ordinal > verifiedGrade.ordinal) seededGrade else verifiedGrade
    }

    private fun computeStats(
        history: List<HistoryEntry>,
        qualifyingWeeks: Int,
        calibration: AthleteCalibration,
    ): IronLedgerStats {
        val sets = history.flatMap { workout -> workout.exercises.flatMap { ex -> ex.sets.map { ex to it } } }
        val working = sets.filter { (_, set) -> isWorkingSet(set) }
        val strengthRaw = working
            .filter { (ex, set) -> !isCardio(ex) && set.weight > 0.0 && set.reps > 0.0 }
            .maxOfOrNull { (_, set) -> epley(set.weight, set.reps) }
            ?: 0.0
        val enduranceRaw = history.sumOf { workout ->
            workout.exercises.sumOf { ex ->
                if (isCardio(ex)) ex.sets.sumOf { it.reps.coerceAtLeast(0.0) } / 60.0 else 0.0
            }
        } + history.sumOf { if (it.duration >= 45 * 60) 2.0 else 0.0 }
        val hypertrophyRaw = working.count().toDouble()
        val powerRaw = working.count { (ex, set) ->
            !isCardio(ex) && set.weight > 0.0 && set.reps in 1.0..5.0
        }.toDouble()
        val agilityRaw = working.count { (ex, _) ->
            val name = ex.name.lowercase(Locale.ROOT)
            name.contains("jump") || name.contains("lunge") || name.contains("single") ||
                name.contains("carry") || name.contains("crawl") || ex.category.orEmpty().contains("conditioning", ignoreCase = true)
        }.toDouble()
        val disciplineRaw = qualifyingWeeks * 4.0 + working.count { (_, set) -> set.rpe != null || set.rir != null }
        val recoveryRaw = qualifyingWeeks * 3.0 + history.count { it.duration in 20 * 60..120 * 60 }

        val verifiedStats = IronLedgerStats(
            strength = toStat(strengthRaw, 120.0),
            power = toStat(powerRaw, 20.0),
            hypertrophy = toStat(hypertrophyRaw, 80.0),
            endurance = toStat(enduranceRaw, 20.0),
            agility = toStat(agilityRaw, 20.0),
            discipline = toStat(disciplineRaw, 50.0),
            recovery = toStat(recoveryRaw, 50.0),
        )
        val seededStats = baselineStats(calibration)
        return IronLedgerStats(
            strength = max(verifiedStats.strength, seededStats.strength),
            power = max(verifiedStats.power, seededStats.power),
            hypertrophy = max(verifiedStats.hypertrophy, seededStats.hypertrophy),
            endurance = max(verifiedStats.endurance, seededStats.endurance),
            agility = max(verifiedStats.agility, seededStats.agility),
            discipline = max(verifiedStats.discipline, seededStats.discipline),
            recovery = max(verifiedStats.recovery, seededStats.recovery),
        )
    }

    private fun hasMeaningfulBaseline(calibration: AthleteCalibration): Boolean =
        calibration.trainingAgeMonths > 0 ||
            calibration.bodyweightKg != null ||
            calibration.hasPastTraining ||
            calibration.baselinePushups > 0 ||
            calibration.baselinePullups > 0 ||
            calibration.baselineBenchKg > 0 ||
            calibration.baselineLatPulldownKg > 0 ||
            calibration.baselineMileRunSeconds > 0

    private fun estimatedLifetimeSessions(calibration: AthleteCalibration): Int =
        (calibration.trainingAgeMonths * 4.345 * max(1, calibration.historicalTrainingDaysPerWeek)).roundToInt().coerceAtLeast(0)

    private fun baselineStats(calibration: AthleteCalibration): IronLedgerStats {
        if (!hasMeaningfulBaseline(calibration)) return IronLedgerStats()
        val sessions = estimatedLifetimeSessions(calibration).toDouble()
        val bodyweight = calibration.bodyweightKg ?: 0.0
        val push = calibration.baselinePushups.toDouble()
        val pull = calibration.baselinePullups.toDouble()
        val bench = calibration.baselineBenchKg.toDouble()
        val lat = calibration.baselineLatPulldownKg.toDouble()
        val mile = calibration.baselineMileRunSeconds.takeIf { it > 0 }?.toDouble()
        val paceFactor = mile?.let { (900.0 / it).coerceIn(0.0, 2.0) } ?: 0.0

        val strengthRaw = bench * 2.4 + lat * 1.5 + pull * 6.0 + push * 1.2 + bodyweight * 0.45 + sessions * 0.30
        val powerRaw = bench * 1.6 + pull * 3.5 + push * 0.8 + sessions * 0.12
        val hypertrophyRaw = bench * 1.4 + lat * 1.1 + push * 1.6 + pull * 2.2 + sessions * 0.22
        val enduranceRaw = push * 0.9 + sessions * 0.08 + paceFactor * 30.0 + calibration.weeklyGoalDays * 2.5
        val agilityRaw = pull * 1.0 + paceFactor * 55.0 + sessions * 0.05
        val disciplineRaw = sessions * 0.16 + calibration.weeklyGoalDays * 8.0 + if (calibration.hasPastTraining) 18.0 else 0.0
        val recoveryRaw = sessions * 0.10 + calibration.weeklyGoalDays * 6.0 + if (calibration.hasGymAccess) 8.0 else 4.0

        return IronLedgerStats(
            strength = toStat(strengthRaw, 45.0),
            power = toStat(powerRaw, 28.0),
            hypertrophy = toStat(hypertrophyRaw, 55.0),
            endurance = toStat(enduranceRaw, 18.0),
            agility = toStat(agilityRaw, 16.0),
            discipline = toStat(disciplineRaw, 22.0),
            recovery = toStat(recoveryRaw, 24.0),
        )
    }

    private fun baselineGrade(
        calibration: AthleteCalibration,
        stats: IronLedgerStats,
    ): IronGrade {
        if (!hasMeaningfulBaseline(calibration)) return IronGrade.UNCALIBRATED
        val sessions = estimatedLifetimeSessions(calibration)
        val months = calibration.trainingAgeMonths
        val score = listOf(
            stats.strength,
            stats.power,
            stats.hypertrophy,
            stats.endurance,
            stats.agility,
            stats.discipline,
            stats.recovery,
        ).average()
        return when {
            score >= 150.0 && sessions >= 180 && months >= 16 -> IronGrade.TITANIUM
            score >= 110.0 && sessions >= 90 && months >= 10 -> IronGrade.STEEL
            score >= 80.0 && sessions >= 28 && months >= 6 -> IronGrade.IRON
            score >= 45.0 && sessions >= 8 && months >= 2 -> IronGrade.GRAPHITE
            else -> IronGrade.UNCALIBRATED
        }
    }

    private fun integrityScore(
        history: List<HistoryEntry>,
        qualified: List<HistoryEntry>,
    ): Double {
        if (history.isEmpty()) return 1.0
        val dailyMax = qualified.groupingBy { dayKey(it.date) ?: it.date }.eachCount().values.maxOrNull() ?: 0
        val veryShort = history.count { it.duration in 1 until 10 * 60 }
        var score = 1.0
        if (dailyMax > 2) score -= (dailyMax - 2) * 0.12
        score -= veryShort * 0.03
        return score.coerceIn(0.35, 1.0)
    }

    private fun isQualifyingWorkout(workout: HistoryEntry): Boolean {
        val hardSets = hardSets(workout)
        val cardioMinutes = workout.exercises
            .filter(::isCardio)
            .sumOf { ex -> ex.sets.sumOf { it.reps.coerceAtLeast(0.0) } / 60.0 }
        if (hardSets == 0 && cardioMinutes <= 0.0) return false
        return hardSets >= 8 || (hardSets >= 3 && workout.duration >= 20 * 60) || cardioMinutes >= 10.0
    }

    private fun hardSets(workout: HistoryEntry): Int =
        workout.exercises.sumOf { ex -> ex.sets.count(::isWorkingSet) }

    private fun isWorkingSet(set: HistoryExerciseSet): Boolean =
        set.type.lowercase(Locale.ROOT) != "warmup" && (set.weight > 0.0 || set.reps > 0.0)

    private fun isCardio(exercise: HistoryExercise): Boolean {
        val haystack = "${exercise.name} ${exercise.category.orEmpty()} ${exercise.primaryMuscle.orEmpty()}".lowercase(Locale.ROOT)
        return listOf("cardio", "run", "treadmill", "bike", "cycle", "rower", "swim", "elliptical", "conditioning")
            .any { it in haystack }
    }

    private fun estimatedPerformance(exercise: HistoryExercise, set: HistoryExerciseSet): Double =
        if (isCardio(exercise) || set.weight <= 0.0) set.reps else epley(set.weight, set.reps)

    private fun epley(weight: Double, reps: Double): Double = weight * (1.0 + reps / 30.0)

    private fun toStat(value: Double, scale: Double): Int =
        (1 + ln(1.0 + value / scale) * 180.0).roundToInt().coerceIn(1, 999)

    private fun parseInstant(value: String) = parseHistoryInstant(value)

    private fun dayKey(value: String): String? =
        parseInstant(value)?.atZone(ZoneOffset.UTC)?.toLocalDate()?.toString()

    private fun weekKey(value: String): String? {
        val date = parseInstant(value)?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: return null
        val wf = WeekFields.ISO
        return "${date.get(wf.weekBasedYear())}-W${date.get(wf.weekOfWeekBasedYear())}"
    }
}
