package com.ironlog.app.data.repository

import com.ironlog.app.data.model.CompletedExerciseInput
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.data.model.SetInput
import com.ironlog.app.data.model.WorkoutDetail
import com.ironlog.app.data.model.WorkoutMetadataInput
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.PlanDayEntity_
import com.ironlog.app.data.objectbox.PlanExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity_
import com.ironlog.app.util.calculateSetVolume
import com.ironlog.app.util.normalizeExerciseNameKey
import com.ironlog.app.util.requireNonEmpty
import com.ironlog.app.util.requireNumberMin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

data class PostWorkoutMetricsResult(
    val newlyRecorded: Boolean,
    val cumulativeVolumeKg: Double,
)

class WorkoutRepository(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
) {
    private val workoutsBox get() = ObjectBox.store.boxFor(WorkoutEntity::class.java)
    private val workoutExercisesBox get() = ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java)
    private val workoutSetsBox get() = ObjectBox.store.boxFor(WorkoutSetEntity::class.java)
    private val planDaysBox get() = ObjectBox.store.boxFor(com.ironlog.app.data.objectbox.PlanDayEntity::class.java)
    private val planExercisesBox get() = ObjectBox.store.boxFor(com.ironlog.app.data.objectbox.PlanExerciseEntity::class.java)
    private val exercisesBox get() = ObjectBox.store.boxFor(ExerciseEntity::class.java)
    private val settingsBox get() = ObjectBox.store.boxFor(AppSettingEntity::class.java)

    suspend fun startWorkoutFromPlanDay(planDayId: String): WorkoutEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(planDayId, "planDayId")
        val now = System.currentTimeMillis()
        val startedAt = readIntendedWorkoutStartMs(now)
        val planDay = planDaysBox.query(PlanDayEntity_.uid.equal(planDayId)).build().use { it.findFirst() }
            ?: error("Plan day not found: $planDayId")
        val planRows = planExercisesBox.query(PlanExerciseEntity_.planDayUid.equal(planDayId))
            .order(PlanExerciseEntity_.orderIndex).build().use { it.find() }
        val workout = WorkoutEntity().apply {
            this.planDay.target = planDay
            planDayUid = planDay.uid
            plan.target = planDay.plan.target
            planUid = planDay.planUid.ifBlank { null }
            name = "${planDay.name.ifBlank { "Workout" }} Session"
            this.startedAt = startedAt
            durationSeconds = 0
            status = "active"
            createdAt = now
            updatedAt = now
        }
        val workoutExerciseRows = planRows.mapIndexedNotNull { index, planExercise ->
            val exercise = exercisesBox.query(ExerciseEntity_.uid.equal(planExercise.exerciseUid)).build().use { it.findFirst() } ?: return@mapIndexedNotNull null
            WorkoutExerciseEntity().apply {
                this.workout.target = workout
                workoutUid = workout.uid
                this.exercise.target = exercise
                exerciseUid = exercise.uid
                orderIndex = index
                supersetGroup = planExercise.supersetGroup.ifBlank { "" }
                notes = planExercise.notes.ifBlank { "" }
                createdAt = now
                updatedAt = now
            }
        }
        ObjectBox.store.runInTx {
            workoutsBox.put(workout)
            if (workoutExerciseRows.isNotEmpty()) workoutExercisesBox.put(workoutExerciseRows)
            writeActiveWorkoutSettings(workout.uid, planDayId, planDay.name.ifBlank { "Workout" }, now)
        }
        // active_workout_start_ms is NOT written here — the timer only begins when the
        // first set is logged (see ActiveWorkoutViewModel.startTimerOnFirstSet).
        workout
    }

    suspend fun startEmptyWorkout(name: String): WorkoutEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(name, "name")
        val now = System.currentTimeMillis()
        val startedAt = readIntendedWorkoutStartMs(now)
        val workout = WorkoutEntity().apply {
            this.name = name.trim()
            this.startedAt = startedAt
            durationSeconds = 0
            status = "active"
            createdAt = now
            updatedAt = now
        }
        ObjectBox.store.runInTx {
            workoutsBox.put(workout)
            writeActiveWorkoutSettings(workout.uid, null, name.trim(), now)
        }
        // active_workout_start_ms is NOT written here — timer starts on first set.
        workout
    }

    private suspend fun readIntendedWorkoutStartMs(now: Long): Long {
        val dateKey = settingsRepository.getString("active_workout_intended_date")
        if (dateKey.isNullOrBlank()) return now
        return runCatching {
            val selectedDate = LocalDate.parse(dateKey)
            val today = LocalDate.now()
            if (selectedDate.isAfter(today)) return now
            selectedDate
                .atTime(LocalTime.now())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(now)
    }

    suspend fun addExerciseToWorkout(workoutId: String, exerciseId: String): WorkoutExerciseEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        requireNonEmpty(exerciseId, "exerciseId")
        val workout = findWorkoutByUidOrThrow(workoutId)
        val exercise = findExerciseByUidOrThrow(exerciseId)
        val now = System.currentTimeMillis()
        val count = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.equal(workoutId)).build().use { it.count().toInt() }
        val created = WorkoutExerciseEntity().apply {
            this.workout.target = workout
            workoutUid = workout.uid
            this.exercise.target = exercise
            exerciseUid = exercise.uid
            orderIndex = count
            supersetGroup = ""
            notes = ""
            createdAt = now
            updatedAt = now
        }
        workoutExercisesBox.put(created)
        created
    }

    suspend fun swapWorkoutExercise(workoutExerciseId: String, newExerciseId: String): WorkoutExerciseEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutExerciseId, "workoutExerciseId")
        requireNonEmpty(newExerciseId, "newExerciseId")
        val row = findWorkoutExerciseByUidOrThrow(workoutExerciseId)
        val exercise = findExerciseByUidOrThrow(newExerciseId)
        row.exercise.target = exercise
        row.exerciseUid = exercise.uid
        row.updatedAt = System.currentTimeMillis()
        workoutExercisesBox.put(row)
        row
    }

    suspend fun updateWorkoutExerciseSuperset(workoutExerciseId: String, group: String?): WorkoutExerciseEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutExerciseId, "workoutExerciseId")
        val row = findWorkoutExerciseByUidOrThrow(workoutExerciseId)
        row.supersetGroup = group?.trim().orEmpty()
        row.updatedAt = System.currentTimeMillis()
        workoutExercisesBox.put(row)
        row
    }

    suspend fun removeExerciseFromWorkout(workoutExerciseId: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutExerciseId, "workoutExerciseId")
        val workoutExercise = findWorkoutExerciseByUidOrThrow(workoutExerciseId)
        val sets = getSetsForWorkoutExercise(workoutExerciseId)
        workoutSetsBox.remove(sets)
        workoutExercisesBox.remove(workoutExercise)
        Unit
    }

    suspend fun addSet(workoutExerciseId: String, input: SetInput): WorkoutSetEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutExerciseId, "workoutExerciseId")
        requireNumberMin(input.weight ?: 0.0, 0.0, "weight")
        requireNumberMin(input.reps ?: 0.0, 0.0, "reps")
        requireNumberMin(input.restSeconds ?: 0, 0.0, "restSeconds")
        val workoutExercise = findWorkoutExerciseByUidOrThrow(workoutExerciseId)
        val now = System.currentTimeMillis()
        val count = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.equal(workoutExerciseId)).build().use { it.count().toInt() }
        val created = WorkoutSetEntity().apply {
            this.workoutExercise.target = workoutExercise
            this.workoutExerciseUid = workoutExercise.uid
            setIndex = count + 1
            weight = input.weight ?: 0.0
            reps = input.reps ?: 0.0
            rpe = input.rpe
            rir = input.rir
            restSeconds = input.restSeconds ?: 0
            isWarmup = input.isWarmup == true
            isDropset = input.isDropset == true
            isAmrap = input.isAmrap == true
            toFailure = input.toFailure == true
            completedAt = input.completedAt ?: now
            createdAt = now
            updatedAt = now
        }
        workoutSetsBox.put(created)
        created
    }

    suspend fun updateSet(setId: String, input: SetInput): WorkoutSetEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(setId, "setId")
        val row = findSetByUidOrThrow(setId)
        if (input.setIndex != null) row.setIndex = input.setIndex
        if (input.weight != null) row.weight = input.weight
        if (input.reps != null) row.reps = input.reps
        if (input.rpe != null) row.rpe = input.rpe
        if (input.rir != null) row.rir = input.rir
        if (input.restSeconds != null) row.restSeconds = input.restSeconds
        if (input.isWarmup != null) row.isWarmup = input.isWarmup
        if (input.isDropset != null) row.isDropset = input.isDropset
        if (input.isAmrap != null) row.isAmrap = input.isAmrap
        if (input.toFailure != null) row.toFailure = input.toFailure
        if (input.completedAt != null) row.completedAt = input.completedAt.takeIf { it != 0L }
        row.updatedAt = System.currentTimeMillis()
        workoutSetsBox.put(row)
        row
    }

    suspend fun updateSetByWorkoutExerciseAndOrder(
        workoutExerciseId: String,
        setOrderIndex: Int,
        input: SetInput,
    ): WorkoutSetEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutExerciseId, "workoutExerciseId")
        require(setOrderIndex > 0) { "setOrderIndex must be >= 1" }
        val row = workoutSetsBox.query(
            WorkoutSetEntity_.workoutExerciseUid.equal(workoutExerciseId)
                .and(WorkoutSetEntity_.setIndex.equal(setOrderIndex.toLong())),
        ).build().use { it.findFirst() } ?: error("Set not found for workoutExerciseId=$workoutExerciseId setIndex=$setOrderIndex")
        updateSet(row.uid, input)
    }

    suspend fun deleteSet(setId: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(setId, "setId")
        workoutSetsBox.remove(findSetByUidOrThrow(setId))
        Unit
    }

    suspend fun deleteWorkoutExercise(workoutExerciseId: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutExerciseId, "workoutExerciseId")
        val entity = runCatching { findWorkoutExerciseByUidOrThrow(workoutExerciseId) }.getOrNull() ?: return@withContext
        // Also delete all sets belonging to this exercise
        val sets = workoutSetsBox.query(
            com.ironlog.app.data.objectbox.WorkoutSetEntity_.workoutExerciseUid.equal(workoutExerciseId)
        ).build().use { it.find() }
        workoutSetsBox.remove(sets)
        workoutExercisesBox.remove(entity)
    }

    suspend fun completeWorkout(
        workoutId: String,
        durationStartEpochMs: Long? = null,
        metadata: WorkoutMetadataInput = WorkoutMetadataInput(),
    ): WorkoutEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        val workout = findWorkoutByUidOrThrow(workoutId)
        val now = System.currentTimeMillis()
        ObjectBox.store.runInTx {
            if (workout.status != "completed") {
                val validDurationStart = durationStartEpochMs?.takeIf { it in 1..now }
                val rawDurationSec = validDurationStart?.let { ((now - it) / 1000.0).roundToInt() }
                    ?: workout.durationSeconds.coerceAtLeast(0)
                workout.durationSeconds = rawDurationSec.coerceIn(0, 86_400)
                workout.status = "completed"
                workout.completedAt = if (workout.startedAt > 0L && now - workout.startedAt >= 86_400_000L) {
                    workout.startedAt + workout.durationSeconds * 1000L
                } else {
                    now
                }
                if (metadata.rating != null) workout.rating = metadata.rating
                if (metadata.notes != null) workout.notes = metadata.notes
                workout.updatedAt = now
                workoutsBox.put(workout)
            }
            clearActiveWorkoutSettings(workoutId)
        }
        workout
    }

    suspend fun abandonWorkout(workoutId: String): WorkoutEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        val workout = findWorkoutByUidOrThrow(workoutId)
        val now = System.currentTimeMillis()
        ObjectBox.store.runInTx {
            if (workout.status != "abandoned") {
                workout.status = "abandoned"
                workout.completedAt = now
                workout.updatedAt = now
                workoutsBox.put(workout)
            }
            clearActiveWorkoutSettings(workoutId)
        }
        workout
    }

    suspend fun recordPostWorkoutMetrics(
        workoutId: String,
        volumeKg: Double,
        hasNewPr: Boolean,
    ): PostWorkoutMetricsResult = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        val markerKey = "workout_post_processed_$workoutId"
        var result = PostWorkoutMetricsResult(newlyRecorded = false, cumulativeVolumeKg = 0.0)
        ObjectBox.store.runInTx {
            val existingMarker = settingsBox.query(AppSettingEntity_.key.equal(markerKey))
                .build().use { it.findFirst() }
            val lifetimeRow = settingsBox.query(AppSettingEntity_.key.equal("lifetime_volume_kg"))
                .build().use { it.findFirst() }
            val currentLifetime = lifetimeRow?.value?.toDoubleOrNull() ?: 0.0
            if (existingMarker != null) {
                result = PostWorkoutMetricsResult(false, currentLifetime)
                return@runInTx
            }

            val now = System.currentTimeMillis()
            val cumulative = currentLifetime + volumeKg.coerceAtLeast(0.0)
            putSetting("lifetime_volume_kg", cumulative.toString(), now)
            if (hasNewPr) putSetting("widget_last_new_pb_ms", now.toString(), now)
            putSetting(markerKey, "true", now)
            result = PostWorkoutMetricsResult(true, cumulative)
        }
        result
    }

    private fun writeActiveWorkoutSettings(
        workoutId: String,
        dayId: String?,
        dayName: String,
        now: Long,
    ) {
        putSetting("active_workout_id", workoutId, now)
        if (dayId.isNullOrBlank()) removeSettings("active_workout_day_id")
        else putSetting("active_workout_day_id", dayId, now)
        putSetting("active_workout_day_name", dayName, now)
        removeSettings(
            "active_workout_start_ms",
            "active_workout_set_label",
            "active_workout_rest_end_ms",
            "active_workout_intended_date",
        )
    }

    private fun clearActiveWorkoutSettings(workoutId: String) {
        removeSettings(
            "active_workout_id",
            "active_workout_day_id",
            "active_workout_day_name",
            "active_workout_start_ms",
            "active_workout_set_label",
            "active_workout_rest_end_ms",
            "active_workout_draft_$workoutId",
            "active_workout_rest_override_$workoutId",
        )
    }

    private fun putSetting(key: String, value: String, now: Long) {
        settingsBox.put(AppSettingEntity().apply {
            this.key = key
            this.value = value
            valueType = "string"
            updatedAt = now
        })
    }

    private fun removeSettings(vararg keys: String) {
        if (keys.isEmpty()) return
        val rows = settingsBox.query(AppSettingEntity_.key.oneOf(keys)).build().use { it.find() }
        settingsBox.remove(rows)
    }

    fun getCompletedWorkoutsFlow(): Flow<List<WorkoutEntity>> =
        workoutsBox.query(WorkoutEntity_.status.equal("completed")).orderDesc(WorkoutEntity_.startedAt).build().asFlow()

    suspend fun updateWorkoutMetadata(workoutId: String, input: WorkoutMetadataInput): WorkoutEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        val workout = findWorkoutByUidOrThrow(workoutId)
        if (input.startedAt != null) {
            workout.startedAt = input.startedAt
            if ((workout.completedAt ?: 0L) < workout.startedAt) workout.completedAt = workout.startedAt
        }
        if (input.durationSeconds != null) workout.durationSeconds = max(0, input.durationSeconds)
        if (input.rating != null) workout.rating = input.rating
        if (input.notes != null) workout.notes = input.notes
        workout.updatedAt = System.currentTimeMillis()
        workoutsBox.put(workout)
        workout
    }

    suspend fun createCompletedWorkout(input: CreateCompletedWorkoutInput): WorkoutEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val completedAt = input.startedAt + input.durationSeconds * 1000L

        // Extract unique normalized names of the incoming exercises to avoid loading the entire table.
        val targetNames = input.exerciseData.mapNotNull { it.name?.let(::normalizeExerciseNameKey) }.distinct().toTypedArray()
        val existing = if (targetNames.isEmpty()) emptyList() else {
            exercisesBox.query(ExerciseEntity_.normalizedName.oneOf(targetNames)).build().use { it.find() }
        }
        val exByNormName = existing.associateBy { it.normalizedName }.toMutableMap()

        var workoutResult: WorkoutEntity? = null
        ObjectBox.store.runInTx {
            val missing = input.exerciseData.filter { ex -> !exByNormName.containsKey(normalizeExerciseNameKey(ex.name ?: "")) }
            for (ex in missing) {
                val normName = normalizeExerciseNameKey(ex.name ?: "Exercise")
                val created = ExerciseEntity().apply {
                    name = ex.name ?: "Exercise"
                    normalizedName = normName
                    primaryMuscle = ex.primaryMuscles?.firstOrNull() ?: ex.primaryMuscle ?: "Other"
                    equipment = ex.equipment ?: "Other"
                    category = ex.category ?: "strength"
                    isCustom = true
                    source = "user_custom"
                    notes = ""
                    createdAt = now
                    updatedAt = now
                    isBodyweight = equipment.lowercase() == "bodyweight"
                }
                exercisesBox.put(created)
                exByNormName[normName] = created
            }

            val workout = WorkoutEntity().apply {
                name = input.name ?: "Workout"
                startedAt = input.startedAt
                this.completedAt = completedAt
                durationSeconds = max(0.0, input.durationSeconds.toDouble()).roundToInt()
                rating = input.rating
                notes = input.notes ?: ""
                status = "completed"
                createdAt = input.startedAt
                updatedAt = now
            }
            workoutsBox.put(workout)

            input.exerciseData.forEachIndexed { exIndex, ex ->
                val normName = normalizeExerciseNameKey(ex.name ?: "")
                val exercise = ex.exerciseId?.let(::findExerciseByUidOrNull) ?: exByNormName[normName] ?: return@forEachIndexed
                val we = WorkoutExerciseEntity().apply {
                    this.workout.target = workout
                    workoutUid = workout.uid
                    this.exercise.target = exercise
                    exerciseUid = exercise.uid
                    orderIndex = exIndex
                    supersetGroup = ex.supersetGroup ?: ""
                    notes = ex.note ?: ex.notes ?: ""
                    createdAt = input.startedAt
                    updatedAt = now
                }
                workoutExercisesBox.put(we)
                val setRows = ex.sets.mapIndexed { si, s ->
                    WorkoutSetEntity().apply {
                        workoutExercise.target = we
                        workoutExerciseUid = we.uid
                        setIndex = si + 1
                        weight = max(0.0, s.weight ?: 0.0)
                        reps = max(0.0, s.reps ?: 0.0)
                        rpe = s.rpe
                        rir = s.rir
                        restSeconds = max(0, s.restSeconds ?: s.rest ?: 0)
                        isWarmup = s.type == "warmup" || s.isWarmup == true
                        isDropset = s.type == "dropset" || s.isDropset == true
                        isAmrap = s.type == "amrap" || s.isAmrap == true || s.isAMRAP == true
                        toFailure = s.type == "failure" || s.toFailure == true
                        this.completedAt = completedAt
                        createdAt = input.startedAt
                        updatedAt = now
                    }
                }
                if (setRows.isNotEmpty()) workoutSetsBox.put(setRows)
            }
            workoutResult = workout
        }
        workoutResult ?: error("Failed to save workout in transaction")
    }

    suspend fun clearCompletedWorkouts() = withContext(Dispatchers.IO) {
        val workouts = workoutsBox.query(WorkoutEntity_.status.equal("completed")).build().use { it.find() }
        if (workouts.isEmpty()) return@withContext
        val workoutIds = workouts.map { it.uid }.toTypedArray()
        val wes = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds)).build().use { it.find() }
        if (wes.isNotEmpty()) {
            val weIds = wes.map { it.uid }.toTypedArray()
            val sets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(weIds)).build().use { it.find() }
            if (sets.isNotEmpty()) {
                workoutSetsBox.remove(sets)
            }
            workoutExercisesBox.remove(wes)
        }
        workoutsBox.remove(workouts)
    }

    suspend fun getWorkoutDetailSnapshot(workoutId: String): WorkoutDetail = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        val workout = findWorkoutByUidOrThrow(workoutId)
        val exercises = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.equal(workoutId)).order(WorkoutExerciseEntity_.orderIndex).build().use { it.find() }
        val exerciseIds = exercises.map { it.uid }.toSet()
        val scopedSets = if (exerciseIds.isEmpty()) emptyList() else {
            workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(exerciseIds.toTypedArray()))
                .order(WorkoutSetEntity_.setIndex)
                .build().use { it.find() }
        }
        val totalVolume = scopedSets.sumOf { calculateSetVolume(it.weight, it.reps) }
        WorkoutDetail(workout, exercises, scopedSets, totalVolume)
    }

    /** Returns non-warmup sets from the most recent completed workout that contained [exerciseId]. */
    suspend fun getLastSessionSetsForExercise(exerciseId: String): List<WorkoutSetEntity> = withContext(Dispatchers.IO) {
        val recentWes = workoutExercisesBox
            .query(WorkoutExerciseEntity_.exerciseUid.equal(exerciseId))
            .build().use { it.find() }
            .sortedByDescending { it.createdAt }
            .take(10)

        for (we in recentWes) {
            workoutsBox.query(
                WorkoutEntity_.uid.equal(we.workoutUid)
                    .and(WorkoutEntity_.status.equal("completed")),
            ).build().use { it.findFirst() } ?: continue

            val sets = workoutSetsBox
                .query(WorkoutSetEntity_.workoutExerciseUid.equal(we.uid))
                .order(WorkoutSetEntity_.setIndex)
                .build().use { it.find() }
                .filter { !it.isWarmup }

            if (sets.isNotEmpty()) return@withContext sets
        }
        emptyList()
    }

    private fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity> =
        workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.equal(workoutExerciseId)).build().use { it.find() }

    private fun findWorkoutByUidOrThrow(uid: String): WorkoutEntity =
        workoutsBox.query(WorkoutEntity_.uid.equal(uid)).build().use { it.findFirst() } ?: error("Workout not found: $uid")

    private fun findWorkoutExerciseByUidOrThrow(uid: String): WorkoutExerciseEntity =
        workoutExercisesBox.query(WorkoutExerciseEntity_.uid.equal(uid)).build().use { it.findFirst() } ?: error("Workout exercise not found: $uid")

    private fun findSetByUidOrThrow(uid: String): WorkoutSetEntity =
        workoutSetsBox.query(WorkoutSetEntity_.uid.equal(uid)).build().use { it.findFirst() } ?: error("Set not found: $uid")

    private fun findExerciseByUidOrThrow(uid: String): ExerciseEntity =
        findExerciseByUidOrNull(uid) ?: error("Exercise not found: $uid")

    private fun findExerciseByUidOrNull(uid: String): ExerciseEntity? =
        exercisesBox.query(ExerciseEntity_.uid.equal(uid)).build().use { it.findFirst() }
}
