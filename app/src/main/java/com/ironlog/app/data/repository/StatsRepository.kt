package com.ironlog.app.data.repository

import com.ironlog.app.data.model.PrRecord
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity_
import com.ironlog.app.domain.intelligence.PrLinkingEngine
import com.ironlog.app.util.calculateEstimated1RM
import com.ironlog.app.util.calculateSetVolume
import com.ironlog.app.util.startOfWeekMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StatsRepository {
    private val workoutsBox get() = ObjectBox.store.boxFor(WorkoutEntity::class.java)
    private val workoutExercisesBox get() = ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java)
    private val workoutSetsBox get() = ObjectBox.store.boxFor(WorkoutSetEntity::class.java)
    private val exercisesBox get() = ObjectBox.store.boxFor(ExerciseEntity::class.java)
    private val exerciseMusclesBox get() = ObjectBox.store.boxFor(ExerciseMuscleEntity::class.java)

    fun getSessionsPerWeekFlow(): Flow<Long> {
        val weekStart = startOfWeekMillis()
        return workoutsBox.query(WorkoutEntity_.status.equal("completed").and(WorkoutEntity_.startedAt.greaterOrEqual(weekStart)))
            .build()
            .asFlow()
            .map { it.size.toLong() }
            .conflate()
    }

    suspend fun getSessionsPerWeekSnapshot(): Long = withContext(Dispatchers.IO) {
        val weekStart = startOfWeekMillis()
        workoutsBox.query(WorkoutEntity_.status.equal("completed").and(WorkoutEntity_.startedAt.greaterOrEqual(weekStart)))
            .build().use { it.count() }
    }

    fun getWeeklyVolumeFlow(): Flow<Double> {
        val weekStart = startOfWeekMillis()
        return workoutsBox.query(WorkoutEntity_.status.equal("completed").and(WorkoutEntity_.startedAt.greaterOrEqual(weekStart)))
            .build()
            .asFlow()
            .map { workouts ->
                val workoutIds = workouts.map { it.uid }.toTypedArray()
                if (workoutIds.isEmpty()) return@map 0.0
                val workoutExercises = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds)).build().use { it.find() }
                val workoutExerciseIds = workoutExercises.map { it.uid }.toTypedArray()
                if (workoutExerciseIds.isEmpty()) return@map 0.0
                val workoutSets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(workoutExerciseIds).and(WorkoutSetEntity_.isWarmup.equal(false))).build().use { it.find() }
                workoutSets.sumOf { calculateSetVolume(it.weight, it.reps) }
            }
            .conflate()
    }

    suspend fun getWeeklyVolumeSnapshot(): Double = withContext(Dispatchers.IO) {
        val weekStart = startOfWeekMillis()
        val workouts = workoutsBox.query(WorkoutEntity_.status.equal("completed").and(WorkoutEntity_.startedAt.greaterOrEqual(weekStart))).build().use { it.find() }
        val workoutIds = workouts.map { it.uid }.toTypedArray()
        if (workoutIds.isEmpty()) return@withContext 0.0
        val workoutExercises = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds)).build().use { it.find() }
        val workoutExerciseIds = workoutExercises.map { it.uid }.toTypedArray()
        if (workoutExerciseIds.isEmpty()) return@withContext 0.0
        val workoutSets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(workoutExerciseIds).and(WorkoutSetEntity_.isWarmup.equal(false))).build().use { it.find() }
        workoutSets.sumOf { calculateSetVolume(it.weight, it.reps) }
    }

    fun getExerciseEstimatedOneRepMaxFlow(exerciseId: String): Flow<Double> {
        val completedWorkouts = workoutsBox.query(WorkoutEntity_.status.equal("completed")).build().asFlow()
        val exerciseRows = workoutExercisesBox.query(WorkoutExerciseEntity_.exerciseUid.equal(exerciseId)).build().asFlow()
        val workingSets = workoutSetsBox.query(WorkoutSetEntity_.isWarmup.equal(false)).build().asFlow()
        return combine(completedWorkouts, exerciseRows, workingSets) { workouts, rows, sets ->
            val completedIds = workouts.mapTo(hashSetOf()) { it.uid }
            val eligibleRowIds = rows.asSequence()
                .filter { it.workoutUid in completedIds }
                .mapTo(hashSetOf()) { it.uid }
            sets.asSequence()
                .filter { it.workoutExerciseUid in eligibleRowIds }
                .maxOfOrNull { calculateEstimated1RM(it.weight, it.reps) }
                ?: 0.0
        }
            .conflate()
    }

    suspend fun getExerciseEstimatedOneRepMaxSnapshot(exerciseId: String): Double = withContext(Dispatchers.IO) {
        val completedIds = workoutsBox.query(WorkoutEntity_.status.equal("completed")).build().use { it.find() }
            .mapTo(hashSetOf()) { it.uid }
        if (completedIds.isEmpty()) return@withContext 0.0
        val exerciseRows = workoutExercisesBox.query(WorkoutExerciseEntity_.exerciseUid.equal(exerciseId)).build().use { it.find() }
            .filter { it.workoutUid in completedIds }
        val exerciseRowIds = exerciseRows.map { it.uid }.toTypedArray()
        if (exerciseRowIds.isEmpty()) return@withContext 0.0
        val sets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(exerciseRowIds).and(WorkoutSetEntity_.isWarmup.equal(false))).build().use { it.find() }
        var max = 0.0
        sets.forEach { set ->
            max = kotlin.math.max(max, calculateEstimated1RM(set.weight, set.reps))
        }
        max
    }

    fun getPRsFlow(): Flow<List<PrRecord>> {
        return workoutsBox.query(WorkoutEntity_.status.equal("completed")).orderDesc(WorkoutEntity_.startedAt)
            .build()
            .asFlow()
            .map { workouts ->
                val workoutIds = workouts.map { it.uid }.toTypedArray()
                if (workoutIds.isEmpty()) return@map emptyList()
                val workoutExercises = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds)).build().use { it.find() }
                val workoutExerciseIds = workoutExercises.map { it.uid }.toTypedArray()
                if (workoutExerciseIds.isEmpty()) return@map emptyList()
                val sets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(workoutExerciseIds).and(WorkoutSetEntity_.isWarmup.equal(false))).build().use { it.find() }
                val exerciseIds = workoutExercises.map { it.exerciseUid }.distinct().toTypedArray()
                val exercises = if (exerciseIds.isEmpty()) {
                    emptyList()
                } else {
                    exercisesBox.query(ExerciseEntity_.uid.oneOf(exerciseIds)).build().use { it.find() }
                }
                val exByWorkoutExerciseId = workoutExercises.associate { it.uid to it.exerciseUid }
                val exerciseById = exercises.associateBy { it.uid }
                val best = linkedMapOf<String, PrRecord>()

                sets.forEach { set ->
                    val exerciseId = exByWorkoutExerciseId[set.workoutExerciseUid] ?: return@forEach
                    val ex = exerciseById[exerciseId]
                    val groupId = PrLinkingEngine.getPrGroupId(
                        name = ex?.name.orEmpty(),
                        primaryMuscle = ex?.primaryMuscle,
                        equipment = ex?.equipment,
                        category = ex?.category,
                        mode = "safe",
                    ) ?: "exact_${ex?.name ?: exerciseId}"
                    val candidate = calculateEstimated1RM(set.weight, set.reps)
                    val current = best[groupId]
                    if (current == null || candidate > current.estimated1RM) {
                        best[groupId] = PrRecord(
                            exerciseId = exerciseId,
                            exerciseName = ex?.name ?: "Unknown",
                            exercisePrGroupId = groupId,
                            weight = set.weight,
                            reps = set.reps,
                            estimated1RM = candidate,
                        )
                    }
                }
                best.values.sortedByDescending { it.estimated1RM }
            }
            .conflate()
    }

    suspend fun getPRsSnapshot(): List<PrRecord> = withContext(Dispatchers.IO) {
        val workouts = workoutsBox.query(WorkoutEntity_.status.equal("completed")).build().use { it.find() }
        val workoutIds = workouts.map { it.uid }.toTypedArray()
        if (workoutIds.isEmpty()) return@withContext emptyList()
        val workoutExercises = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds)).build().use { it.find() }
        val workoutExerciseIds = workoutExercises.map { it.uid }.toTypedArray()
        if (workoutExerciseIds.isEmpty()) return@withContext emptyList()
        val sets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(workoutExerciseIds).and(WorkoutSetEntity_.isWarmup.equal(false))).build().use { it.find() }
        val exerciseIds = workoutExercises.map { it.exerciseUid }.distinct().toTypedArray()
        val exercises = if (exerciseIds.isEmpty()) {
            emptyList()
        } else {
            exercisesBox.query(ExerciseEntity_.uid.oneOf(exerciseIds)).build().use { it.find() }
        }
        val exByWorkoutExerciseId = workoutExercises.associate { it.uid to it.exerciseUid }
        val exerciseById = exercises.associateBy { it.uid }
        val best = linkedMapOf<String, PrRecord>()

        sets.forEach { set ->
            val exerciseId = exByWorkoutExerciseId[set.workoutExerciseUid] ?: return@forEach
            val ex = exerciseById[exerciseId]
            val groupId = PrLinkingEngine.getPrGroupId(
                name = ex?.name.orEmpty(),
                primaryMuscle = ex?.primaryMuscle,
                equipment = ex?.equipment,
                category = ex?.category,
                mode = "safe",
            ) ?: "exact_${ex?.name ?: exerciseId}"
            val candidate = calculateEstimated1RM(set.weight, set.reps)
            val current = best[groupId]
            if (current == null || candidate > current.estimated1RM) {
                best[groupId] = PrRecord(
                    exerciseId = exerciseId,
                    exerciseName = ex?.name ?: "Unknown",
                    exercisePrGroupId = groupId,
                    weight = set.weight,
                    reps = set.reps,
                    estimated1RM = candidate,
                )
            }
        }
        best.values.sortedByDescending { it.estimated1RM }
    }

    fun getMuscleVolumeFlow(timeRange: Int = 14): Flow<Map<String, Double>> {
        val from = System.currentTimeMillis() - timeRange * 24L * 60L * 60L * 1000L
        return workoutsBox.query(WorkoutEntity_.status.equal("completed").and(WorkoutEntity_.startedAt.greaterOrEqual(from)))
            .build()
            .asFlow()
            .map { workouts ->
                val workoutIds = workouts.map { it.uid }.toTypedArray()
                if (workoutIds.isEmpty()) return@map emptyMap()
                val workoutExerciseRows = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds)).build().use { it.find() }
                val workoutExerciseById = workoutExerciseRows.associateBy { it.uid }
                val workoutExerciseIds = workoutExerciseRows.map { it.uid }.toTypedArray()
                if (workoutExerciseIds.isEmpty()) return@map emptyMap()
                val sets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(workoutExerciseIds).and(WorkoutSetEntity_.isWarmup.equal(false))).build().use { it.find() }
                val exerciseIds = workoutExerciseRows.map { it.exerciseUid }.distinct().toTypedArray()
                val musclesByExercise = if (exerciseIds.isEmpty()) {
                    emptyMap()
                } else {
                    exerciseMusclesBox.query(ExerciseMuscleEntity_.exerciseUid.oneOf(exerciseIds))
                        .build().use { it.find() }
                        .groupBy { it.exerciseUid }
                }
                val totals = linkedMapOf<String, Double>()

                sets.forEach { set ->
                    val workoutExercise = workoutExerciseById[set.workoutExerciseUid] ?: return@forEach
                    val volume = calculateSetVolume(set.weight, set.reps)
                    val muscles = musclesByExercise[workoutExercise.exerciseUid].orEmpty()
                    muscles.forEach { muscle ->
                        val contrib = volume * muscle.contributionFraction
                        totals[muscle.muscle] = (totals[muscle.muscle] ?: 0.0) + contrib
                    }
                }
                totals
            }
            .conflate()
    }

    suspend fun getMuscleVolumeSnapshot(timeRange: Int = 14): Map<String, Double> = withContext(Dispatchers.IO) {
        val from = System.currentTimeMillis() - timeRange * 24L * 60L * 60L * 1000L
        val workouts = workoutsBox.query(WorkoutEntity_.status.equal("completed").and(WorkoutEntity_.startedAt.greaterOrEqual(from))).build().use { it.find() }
        val workoutIds = workouts.map { it.uid }.toTypedArray()
        if (workoutIds.isEmpty()) return@withContext emptyMap()
        val workoutExerciseRows = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds)).build().use { it.find() }
        val workoutExerciseById = workoutExerciseRows.associateBy { it.uid }
        val workoutExerciseIds = workoutExerciseRows.map { it.uid }.toTypedArray()
        if (workoutExerciseIds.isEmpty()) return@withContext emptyMap()
        val sets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(workoutExerciseIds).and(WorkoutSetEntity_.isWarmup.equal(false))).build().use { it.find() }
        val exerciseIds = workoutExerciseRows.map { it.exerciseUid }.distinct().toTypedArray()
        val musclesByExercise = if (exerciseIds.isEmpty()) {
            emptyMap()
        } else {
            exerciseMusclesBox.query(ExerciseMuscleEntity_.exerciseUid.oneOf(exerciseIds))
                .build().use { it.find() }
                .groupBy { it.exerciseUid }
        }
        val totals = linkedMapOf<String, Double>()

        sets.forEach { set ->
            val workoutExercise = workoutExerciseById[set.workoutExerciseUid] ?: return@forEach
            val volume = calculateSetVolume(set.weight, set.reps)
            val muscles = musclesByExercise[workoutExercise.exerciseUid].orEmpty()
            muscles.forEach { muscle ->
                val contrib = volume * muscle.contributionFraction
                totals[muscle.muscle] = (totals[muscle.muscle] ?: 0.0) + contrib
            }
        }
        totals
    }
}
