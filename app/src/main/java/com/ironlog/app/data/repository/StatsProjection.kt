package com.ironlog.app.data.repository

import com.ironlog.app.domain.gamification.parseHistoryLocalDate
import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.domain.intelligence.PrLinkingEngine
import com.ironlog.app.domain.training.TrainingSetPolicy
import com.ironlog.app.domain.training.PersonalBestPolicy
import com.ironlog.app.ui.model.ChartPoint
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import com.ironlog.app.ui.model.PersonalBest
import com.ironlog.app.ui.model.StatsUiState
import java.time.Instant
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal data class EstimatedPerformance(
    val exercise: HistoryExercise,
    val set: HistoryExerciseSet,
    val workoutDate: String,
    val estimatedOneRm: Double,
) {
    val exerciseKey: String get() = exercise.exerciseId.ifBlank { exercise.name }
    val groupId: String get() = PrLinkingEngine.getPrGroupId(
        exercise.name, exercise.primaryMuscle, exercise.equipment, exercise.category, "safe",
    ) ?: "exact_$exerciseKey"
}

internal fun eligibleStatsHistory(history: List<HistoryEntry>, now: Instant, zoneId: ZoneId): List<HistoryEntry> =
    history.filter { parseHistoryInstant(it.date, zoneId)?.let { date -> !date.isAfter(now) } == true }

internal fun estimatedPerformances(
    history: List<HistoryEntry>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    prResetAt: Instant? = null,
): List<EstimatedPerformance> =
    eligibleStatsHistory(history, now, zoneId).flatMap { workout ->
        workout.exercises.flatMap { exercise ->
            exercise.sets.filter { set ->
                PersonalBestPolicy.isAfterReset(set, workout.date, prResetAt, zoneId)
            }.mapNotNull { set ->
                TrainingSetPolicy.estimatedOneRm(exercise, set)?.let { estimate ->
                    EstimatedPerformance(exercise, set, workout.date, estimate)
                }
            }
        }
    }

internal fun historicalPrBaselines(
    history: List<HistoryEntry>,
    prResetAt: Instant? = null,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Map<String, Double> = estimatedPerformances(history, now, zoneId, prResetAt)
    .groupBy { it.exerciseKey }
    .mapValues { (_, records) -> records.maxOf { it.estimatedOneRm } }

/** Deterministic analytics over the same immutable history consumed by recovery and widgets. */
fun projectStats(
    history: List<HistoryEntry>,
    weightUnit: String = "kg",
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    prResetAt: Instant? = null,
): StatsUiState {
    val today = now.atZone(zoneId).toLocalDate()
    val eligible = eligibleStatsHistory(history, now, zoneId)
    val dates = eligible.mapNotNull { parseHistoryLocalDate(it.date, zoneId) }
    val uniqueDays = dates.toSet()
    var day = if (today in uniqueDays) today else today.minusDays(1)
    var streak = 0
    while (day in uniqueDays) { streak++; day = day.minusDays(1) }
    val counts = dates.groupingBy { it }.eachCount()
    val formatter = DateTimeFormatter.ofPattern("dd/MM")
    val chart = (0..13).map { index ->
        val date = today.minusDays((13 - index).toLong())
        ChartPoint(counts[date] ?: 0, if (index in setOf(0, 4, 9, 13)) date.format(formatter) else "")
    }
    val performances = estimatedPerformances(eligible, now, zoneId, prResetAt)
    val bests = performances.groupBy { it.exerciseKey }.values.map { records ->
        val ranked = records.sortedByDescending { it.estimatedOneRm }
        val best = ranked.first()
        PersonalBest(
            exerciseName = best.exercise.name,
            bestWeight = best.set.weight,
            estOneRm = best.estimatedOneRm,
            date = parseHistoryLocalDate(best.workoutDate, zoneId)?.toString(),
            previousOrm = ranked.getOrNull(1)?.estimatedOneRm,
            exerciseId = best.exercise.exerciseId.takeIf { it.isNotBlank() },
            setId = best.set.id.takeIf { it.isNotBlank() },
        )
    }.sortedByDescending { it.estOneRm }
    return StatsUiState(
        history = history,
        pb = bests.groupBy { it.exerciseName }.mapValues { (_, rows) -> rows.maxOf { it.estOneRm } },
        personalBests = bests,
        pbGroups = performances.groupBy { it.groupId }.mapValues { (_, rows) -> rows.maxOf { it.estimatedOneRm } },
        streak = streak,
        totalSets = eligible.sumOf { it.sets },
        avgDurationMin = if (eligible.isEmpty()) 0 else (eligible.sumOf { it.duration.toLong() }.toDouble() / eligible.size / 60.0).roundToInt(),
        chartData = chart,
        weightUnit = weightUnit,
        prResetAtEpochMs = prResetAt?.toEpochMilli(),
    )
}

internal fun statsWeekStart(now: Instant, zoneId: ZoneId): Instant = now.atZone(zoneId).toLocalDate()
    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zoneId).toInstant()

fun projectWeeklyVolume(
    history: List<HistoryEntry>, now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault(),
): Double {
    val start = statsWeekStart(now, zoneId)
    return eligibleStatsHistory(history, now, zoneId)
        .filter { parseHistoryInstant(it.date, zoneId)?.isBefore(start) == false }.sumOf { it.volume }
}

fun projectMuscleVolume(
    history: List<HistoryEntry>, timeRange: Int = 14,
    now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault(),
): Map<String, Double> {
    val from = now.minusSeconds(timeRange.coerceAtLeast(0).toLong() * 86_400L)
    val totals = linkedMapOf<String, Double>()
    eligibleStatsHistory(history, now, zoneId)
        .filter { parseHistoryInstant(it.date, zoneId)?.isBefore(from) == false }
        .flatMap { it.exercises }.forEach { exercise ->
            val volume = exercise.sets.sumOf { TrainingSetPolicy.externalLoadVolume(exercise, it) }
            exercise.muscleContributions.forEach { (muscle, fraction) ->
                val contribution = volume * fraction
                if (fraction.isFinite() && fraction > 0.0 && contribution.isFinite()) {
                    totals[muscle] = (totals[muscle] ?: 0.0) + contribution
                }
            }
        }
    return totals
}
