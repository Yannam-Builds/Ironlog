package com.ironlog.app.domain.gamification

import androidx.compose.runtime.Immutable
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import com.ironlog.app.domain.training.TrainingSetPolicy
import java.time.Instant
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ln
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
    val strength: Int = 0,
    val power: Int = 0,
    val hypertrophy: Int = 0,
    val endurance: Int = 0,
    val agility: Int = 0,
    val discipline: Int = 0,
    val recovery: Int = 0,
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
    val workoutId: String? = null,
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

class IronLedgerEngine(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zoneId),
) {
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
        if (level >= 100) return 0L
        val spent = (1 until level).sumOf { xpForLevel(it) }
        return (totalXp - spent).coerceAtLeast(0L)
    }

    fun rebuild(
        history: List<HistoryEntry>,
        weeklyGoal: Int,
        calibration: AthleteCalibration,
    ): IronLedgerSnapshot {
        val now = Instant.now(clock)
        val sorted = history.sortedBy { parseInstant(it.date)?.toEpochMilli() ?: Long.MAX_VALUE }
        val qualified = sorted.filter { workout ->
            val occurredAt = parseInstant(workout.date)
            occurredAt != null && !occurredAt.isAfter(now) && CreditedProof.qualifies(workout, now, zoneId)
        }
        val firstDate = qualified.firstOrNull()?.date?.let(::parseInstant)
        val lastDate = qualified.lastOrNull()?.date?.let(::parseInstant) ?: now
        val tenureDays = firstDate?.let { ChronoUnit.DAYS.between(it, lastDate).toInt().coerceAtLeast(0) } ?: 0
        val qualifyingWeeks = qualified.mapNotNull { weekKey(it.date) }.distinct().size
        val integrity = integrityScore(qualified, qualified)
        val events = buildEvents(qualified, qualified, integrity)
        val totalXp = events.sumOf { it.xp.toLong() }.coerceAtLeast(0L)
        val level = levelFromTotalXp(totalXp)
        val stats = computeStats(qualified, qualifyingWeeks)
        val grade = gradeFor(
            verifiedSessions = qualified.size,
            qualifyingWeeks = qualifyingWeeks,
            tenureDays = tenureDays,
            integrity = integrity,
            stats = stats,
        )
        val nextGrade = IronGrade.entries.firstOrNull { it.ordinal > grade.ordinal }
        val gates = nextGrade?.let { ng ->
            buildList {
                add(IronGradeGate(ng, "Credited sessions", qualified.size, ng.minVerifiedSessions, qualified.size >= ng.minVerifiedSessions))
                add(IronGradeGate(ng, "Qualifying weeks", qualifyingWeeks, ng.minQualifyingWeeks, qualifyingWeeks >= ng.minQualifyingWeeks))
                add(IronGradeGate(ng, "Training tenure", tenureDays, ng.minTenureDays, tenureDays >= ng.minTenureDays))
                // Integrity gate only applies for OBSIDIAN and above
                if (ng.ordinal >= IronGrade.OBSIDIAN.ordinal) {
                    val required = if (ng.ordinal >= IronGrade.APEX.ordinal) 95 else 88
                    val threshold = required / 100.0
                    add(IronGradeGate(ng, "Integrity", (integrity * 100).roundToInt(), required, integrity >= threshold))
                }
                if (ng.ordinal >= IronGrade.IRIDIUM.ordinal) {
                    val balance = listOf(stats.strength, stats.hypertrophy, stats.discipline).average().roundToInt()
                    add(IronGradeGate(ng, "Balanced signals", balance, 300, balance >= 300))
                }
            }
        }.orEmpty()

        return IronLedgerSnapshot(
            totalXp = totalXp,
            level = level,
            xpInLevel = xpInCurrentLevel(totalXp),
            xpForNextLevel = if (level >= 100) 0L else xpForLevel(level),
            grade = grade,
            stats = stats,
            integrityScore = integrity,
            verifiedSessions = qualified.size,
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
        history.sortedWith(compareBy({ parseInstant(it.date) }, { it.id })).forEach { workout ->
            if (workout.id !in qualifiedIds) return@forEach
            val dayCount = dailyCounts[dayKey(workout.date) ?: workout.date] ?: 1
            val dailyMultiplier = when {
                dayCount <= 1 -> 1.0
                dayCount == 2 -> 0.45
                else -> 0.0
            }
            val trust = (integrity * dailyMultiplier).coerceIn(0.0, 1.0)
            val hardSets = CreditedProof.hardSetCount(workout)
            val baseXp = ((40 + hardSets.coerceAtMost(18) * 2) * trust).roundToInt()
            if (baseXp > 0) {
                events += IronLedgerEvent(
                    sourceId = workout.id,
                    workoutId = workout.id,
                    kind = "workout",
                    title = "Training proof logged",
                    detail = "${workout.name} - $hardSets hard sets",
                    xp = baseXp,
                    occurredAt = workout.date,
                    trust = trust,
                )
            }
            workout.exercises
                .groupBy { exercise -> exercise.exerciseId.ifBlank { exercise.name.lowercase(Locale.ROOT) } }
                .forEach { (key, blocks) ->
                val bestBlock = blocks.mapNotNull { exercise ->
                    exercise.sets.filter { TrainingSetPolicy.isValidWorkingSet(exercise, it) }
                        .mapNotNull { set -> estimatedPerformance(exercise, set) }
                        .maxOrNull()
                        ?.let { exercise to it }
                }.maxByOrNull { it.second } ?: return@forEach
                val exercise = bestBlock.first
                val best = bestBlock.second
                val previous = previousBestByExercise[key]
                if (previous != null && best > previous * 1.025 && trust >= 0.75) {
                    val eventSourceId = "${workout.id}:$key"
                    events += IronLedgerEvent(
                        sourceId = eventSourceId,
                        workoutId = workout.id,
                        kind = "pr",
                        title = "Verified PR",
                        detail = "${exercise.name} improved ${previous.roundToInt()} -> ${best.roundToInt()}",
                        xp = 35,
                        occurredAt = workout.date,
                        trust = trust,
                    )
                }
                previousBestByExercise[key] = maxOf(previous ?: 0.0, best)
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
    ): IronGrade {
        val balance = listOf(stats.strength, stats.hypertrophy, stats.discipline).average()
        return IronGrade.entries.lastOrNull { grade ->
            verifiedSessions >= grade.minVerifiedSessions &&
                qualifyingWeeks >= grade.minQualifyingWeeks &&
                tenureDays >= grade.minTenureDays &&
                (grade.ordinal < IronGrade.OBSIDIAN.ordinal || integrity >= 0.88) &&
                (grade.ordinal < IronGrade.IRIDIUM.ordinal || balance >= 300.0) &&
                (grade.ordinal < IronGrade.APEX.ordinal || integrity >= 0.95)
        } ?: IronGrade.UNCALIBRATED
    }

    private fun computeStats(
        history: List<HistoryEntry>,
        qualifyingWeeks: Int,
    ): IronLedgerStats {
        val sets = history.flatMap { workout -> workout.exercises.flatMap { ex -> ex.sets.map { ex to it } } }
        val working = sets.filter { (ex, set) -> TrainingSetPolicy.isValidWorkingSet(ex, set) }
        val strengthRaw = working
            .mapNotNull { (ex, set) -> TrainingSetPolicy.estimatedOneRm(ex, set) }.maxOrNull()
            ?: 0.0
        val enduranceRaw = history.sumOf { workout ->
            workout.exercises.sumOf { ex ->
                ex.sets.sumOf { TrainingSetPolicy.cardioSeconds(ex, it) } / 60.0
            }
        } + history.sumOf { if (it.duration >= 45 * 60) 2.0 else 0.0 }
        val hypertrophyRaw = working.count().toDouble()
        val powerRaw = working.count { (ex, set) ->
            TrainingSetPolicy.estimatedOneRm(ex, set) != null && set.reps in 1.0..5.0
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
        return verifiedStats
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

    private fun estimatedPerformance(exercise: HistoryExercise, set: HistoryExerciseSet): Double? =
        TrainingSetPolicy.estimatedOneRm(exercise, set)

    private fun toStat(value: Double, scale: Double): Int =
        if (value <= 0.0) 0 else (1 + ln(1.0 + value / scale) * 180.0).roundToInt().coerceIn(1, 999)

    private fun parseInstant(value: String) = parseHistoryInstant(value, zoneId)

    private fun dayKey(value: String): String? =
        parseInstant(value)?.atZone(zoneId)?.toLocalDate()?.toString()

    private fun weekKey(value: String): String? {
        val date = parseInstant(value)?.atZone(zoneId)?.toLocalDate() ?: return null
        val wf = WeekFields.ISO
        return "${date.get(wf.weekBasedYear())}-W${date.get(wf.weekOfWeekBasedYear())}"
    }
}
