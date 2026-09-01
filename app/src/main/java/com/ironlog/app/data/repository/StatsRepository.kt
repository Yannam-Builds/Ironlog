package com.ironlog.app.data.repository

import com.ironlog.app.data.model.PrRecord
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import com.ironlog.app.ui.model.HistoryEntry
import java.time.Instant
import java.time.ZoneId

class StatsRepository(
    private val clock: () -> Instant = { Instant.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val boxStore: BoxStore? = null,
    private val settingsRepository: SettingsRepository? = null,
) {
    private val store get() = boxStore ?: ObjectBox.store
    private val workoutsBox get() = store.boxFor(WorkoutEntity::class.java)
    private val prSettings: SettingsRepository by lazy {
        settingsRepository ?: SettingsRepository(store.boxFor(AppSettingEntity::class.java))
    }

    /** Training invalidation only; presentation settings have their own selected-key flow. */
    fun observeStatsChanges(): Flow<Unit> = observeEntityChanges(
        { store }, WorkoutEntity::class.java, WorkoutExerciseEntity::class.java,
        WorkoutSetEntity::class.java, ExerciseEntity::class.java, ExerciseMuscleEntity::class.java,
    )

    fun getSessionsPerWeekFlow(): Flow<Long> {
        return completedHistoryFlow().map { history ->
            val now = clock()
            val start = statsWeekStart(now, zoneId)
            eligibleStatsHistory(history, now, zoneId).count {
                com.ironlog.app.domain.gamification.parseHistoryInstant(it.date, zoneId)?.isBefore(start) == false
            }.toLong()
        }.flowOn(Dispatchers.IO)
    }

    suspend fun getSessionsPerWeekSnapshot(): Long = withContext(Dispatchers.IO) {
        val now = clock()
        val weekStart = statsWeekStart(now, zoneId).toEpochMilli()
        workoutsBox.query(WorkoutEntity_.status.equal("completed").and(WorkoutEntity_.startedAt.greaterOrEqual(weekStart))
            .and(WorkoutEntity_.startedAt.lessOrEqual(now.toEpochMilli())))
            .build().use { it.count() }
    }

    suspend fun completedHistorySnapshot(): List<HistoryEntry> = HistoryRepository(store).completedSnapshot()

    /** Compare immutable, full-fidelity completed snapshots before running any statistics. */
    fun completedHistoryFlow(): Flow<List<HistoryEntry>> = observeStatsChanges()
        .map { completedHistorySnapshot() }
        .distinctUntilChanged()
        .conflate()
        .flowOn(Dispatchers.IO)

    fun getWeeklyVolumeFlow(): Flow<Double> = completedHistoryFlow().map(::weeklyVolume).flowOn(Dispatchers.IO)

    suspend fun getWeeklyVolumeSnapshot(): Double = withContext(Dispatchers.IO) { weeklyVolume(completedHistorySnapshot()) }

    private fun weeklyVolume(history: List<HistoryEntry>): Double = projectWeeklyVolume(history, clock(), zoneId)

    fun getExerciseEstimatedOneRepMaxFlow(exerciseId: String): Flow<Double> =
        combine(completedHistoryFlow(), personalBestResetFlow()) { history, resetAt ->
            bestEstimate(history, exerciseId, resetAt)
        }.flowOn(Dispatchers.IO)

    suspend fun getExerciseEstimatedOneRepMaxSnapshot(exerciseId: String): Double =
        withContext(Dispatchers.IO) {
            bestEstimate(completedHistorySnapshot(), exerciseId, prSettings.getPersonalBestResetAt())
        }

    private fun bestEstimate(history: List<HistoryEntry>, exerciseId: String, resetAt: Instant?): Double =
        estimatedPerformances(history, clock(), zoneId, resetAt).filter { it.exercise.exerciseId == exerciseId }
            .maxOfOrNull { it.estimatedOneRm } ?: 0.0

    fun getPRsFlow(): Flow<List<PrRecord>> =
        combine(completedHistoryFlow(), personalBestResetFlow(), ::prRecords).flowOn(Dispatchers.IO)

    suspend fun getPRsSnapshot(): List<PrRecord> = withContext(Dispatchers.IO) {
        prRecords(completedHistorySnapshot(), prSettings.getPersonalBestResetAt())
    }

    private fun prRecords(history: List<HistoryEntry>, resetAt: Instant?): List<PrRecord> =
        estimatedPerformances(history, clock(), zoneId, resetAt).groupBy { it.groupId }.values.map { records ->
            val best = records.maxBy { it.estimatedOneRm }
            PrRecord(
                exerciseId = best.exercise.exerciseId,
                exerciseName = best.exercise.name,
                exercisePrGroupId = best.groupId,
                weight = best.set.weight,
                reps = best.set.reps,
                estimated1RM = best.estimatedOneRm,
            )
        }.sortedByDescending { it.estimated1RM }

    private fun personalBestResetFlow(): Flow<Instant?> =
        prSettings.observeStrings(setOf(PR_RESET_AT_KEY))
            .map { parsePersonalBestResetAt(it[PR_RESET_AT_KEY]) }
            .distinctUntilChanged()

    fun getMuscleVolumeFlow(timeRange: Int = 14): Flow<Map<String, Double>> =
        completedHistoryFlow().map { muscleVolume(it, timeRange) }.flowOn(Dispatchers.IO)

    suspend fun getMuscleVolumeSnapshot(timeRange: Int = 14): Map<String, Double> =
        withContext(Dispatchers.IO) { muscleVolume(completedHistorySnapshot(), timeRange) }

    private fun muscleVolume(history: List<HistoryEntry>, timeRange: Int): Map<String, Double> =
        projectMuscleVolume(history, timeRange, clock(), zoneId)
}
