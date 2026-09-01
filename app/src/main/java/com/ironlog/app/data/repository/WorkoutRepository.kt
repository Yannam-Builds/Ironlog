package com.ironlog.app.data.repository

import com.ironlog.app.data.model.CompletedExerciseInput
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.data.model.SetInput
import com.ironlog.app.data.model.WorkoutDetail
import com.ironlog.app.data.model.WorkoutMetadataInput
import com.ironlog.app.data.history.HistoricalExerciseSnapshotCodec
import com.ironlog.app.data.history.HistoricalExerciseSnapshotOverride
import com.ironlog.app.data.history.captureHistoricalExerciseSnapshot
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity_
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
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

data class PostWorkoutMetricsResult(
    val newlyRecorded: Boolean,
    val cumulativeVolumeKg: Double,
)

data class LastExerciseSession(
    val sets: List<WorkoutSetEntity>,
    val date: String,
    val notes: String,
    val workoutId: String,
    val workoutExerciseId: String,
)

class WorkoutRepository(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
    private val boxStore: BoxStore = ObjectBox.store,
) {
    private val workoutsBox get() = boxStore.boxFor(WorkoutEntity::class.java)
    private val workoutExercisesBox get() = boxStore.boxFor(WorkoutExerciseEntity::class.java)
    private val workoutSetsBox get() = boxStore.boxFor(WorkoutSetEntity::class.java)
    private val planDaysBox get() = boxStore.boxFor(com.ironlog.app.data.objectbox.PlanDayEntity::class.java)
    private val planExercisesBox get() = boxStore.boxFor(com.ironlog.app.data.objectbox.PlanExerciseEntity::class.java)
    private val exercisesBox get() = boxStore.boxFor(ExerciseEntity::class.java)
    private val exerciseMusclesBox get() = boxStore.boxFor(ExerciseMuscleEntity::class.java)
    private val settingsBox get() = boxStore.boxFor(AppSettingEntity::class.java)

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
        boxStore.runInTx {
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
        boxStore.runInTx {
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
        boxStore.callInTx {
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
        boxStore.callInTx {
        val row = findWorkoutExerciseByUidOrThrow(workoutExerciseId)
        row.supersetGroup = group?.trim().orEmpty()
        row.updatedAt = System.currentTimeMillis()
        workoutExercisesBox.put(row)
        row
        }
    }

    suspend fun persistExerciseOrder(workoutExerciseIds: List<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        boxStore.runInTx {
            workoutExerciseIds.forEachIndexed { index, uid ->
                val row = findWorkoutExerciseByUidOrThrow(uid)
                row.orderIndex = index
                row.updatedAt = now
                workoutExercisesBox.put(row)
            }
        }
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
            uid = input.uid?.takeIf { it.isNotBlank() } ?: uid
              this.workoutExercise.targetId = workoutExercise.objectBoxId
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
            notes = input.notes.orEmpty()
            completedAt = input.completedAt ?: now
            createdAt = now
            updatedAt = now
        }
        workoutSetsBox.put(created)
        created
    }

    /** Inserts a stable set at a one-based position and shifts later rows atomically. */
    suspend fun insertSetAt(workoutExerciseId: String, setOrderIndex: Int, input: SetInput): WorkoutSetEntity =
        withContext(Dispatchers.IO) {
            requireNonEmpty(workoutExerciseId, "workoutExerciseId")
            require(setOrderIndex > 0) { "setOrderIndex must be >= 1" }
            requireNumberMin(input.weight ?: 0.0, 0.0, "weight")
            requireNumberMin(input.reps ?: 0.0, 0.0, "reps")
            val workoutExercise = findWorkoutExerciseByUidOrThrow(workoutExerciseId)
            val now = System.currentTimeMillis()
            var created: WorkoutSetEntity? = null
            boxStore.runInTx {
                val rows = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.equal(workoutExerciseId))
                    .orderDesc(WorkoutSetEntity_.setIndex)
                    .build().use { it.find() }
                rows.filter { it.setIndex >= setOrderIndex }.forEach { row ->
                    row.setIndex += 1
                    row.updatedAt = now
                    workoutSetsBox.put(row)
                }
                created = WorkoutSetEntity().apply {
                    uid = input.uid?.takeIf { it.isNotBlank() } ?: uid
                      this.workoutExercise.targetId = workoutExercise.objectBoxId
                    workoutExerciseUid = workoutExercise.uid
                    setIndex = setOrderIndex
                    weight = input.weight ?: 0.0
                    reps = input.reps ?: 0.0
                    rpe = input.rpe
                    rir = input.rir
                    restSeconds = input.restSeconds ?: 0
                    isWarmup = input.isWarmup == true
                    isDropset = input.isDropset == true
                    isAmrap = input.isAmrap == true
                    toFailure = input.toFailure == true
                    notes = input.notes.orEmpty()
                    completedAt = input.completedAt ?: now
                    createdAt = now
                    updatedAt = now
                    workoutSetsBox.put(this)
                }
            }
            checkNotNull(created)
        }

    suspend fun updateSet(setId: String, input: SetInput): WorkoutSetEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(setId, "setId")
        boxStore.callInTx {
        val row = findSetByUidOrThrow(setId)
        if (input.setIndex != null) row.setIndex = input.setIndex
        if (input.weight != null) row.weight = input.weight
        if (input.reps != null) row.reps = input.reps
        if (input.rpe != null) row.rpe = input.rpe
        if (input.rir != null) row.rir = input.rir
        row.rpe = input.rpeUpdate.resolve(row.rpe)
        row.rir = input.rirUpdate.resolve(row.rir)
        if (input.restSeconds != null) row.restSeconds = input.restSeconds
        if (input.isWarmup != null) row.isWarmup = input.isWarmup
        if (input.isDropset != null) row.isDropset = input.isDropset
        if (input.isAmrap != null) row.isAmrap = input.isAmrap
        if (input.toFailure != null) row.toFailure = input.toFailure
        if (input.notes != null) row.notes = input.notes
        if (input.completedAt != null) row.completedAt = input.completedAt.takeIf { it != 0L }
        row.updatedAt = System.currentTimeMillis()
        workoutSetsBox.put(row)
        row
        }
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

    /** Deletes by stable UID and rewrites the remaining durable order in one transaction. */
    suspend fun deleteSetAndCompact(workoutExerciseId: String, setId: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutExerciseId, "workoutExerciseId")
        requireNonEmpty(setId, "setId")
        boxStore.runInTx {
            val row = findSetByUidOrThrow(setId)
            require(row.workoutExerciseUid == workoutExerciseId) { "Set does not belong to workout exercise" }
            workoutSetsBox.remove(row)
            workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.equal(workoutExerciseId))
                .order(WorkoutSetEntity_.setIndex)
                .build()
                .use { it.find() }
                .forEachIndexed { index, remaining ->
                    val compacted = index + 1
                    if (remaining.setIndex != compacted) {
                        remaining.setIndex = compacted
                        remaining.updatedAt = System.currentTimeMillis()
                        workoutSetsBox.put(remaining)
                    }
                }
        }
    }

    suspend fun deleteWorkoutExercise(workoutExerciseId: String) = withContext(Dispatchers.IO) {
        deleteWorkoutExerciseBlocking(workoutExerciseId)
    }

    internal fun deleteWorkoutExerciseBlocking(workoutExerciseId: String) {
        requireNonEmpty(workoutExerciseId, "workoutExerciseId")
        boxStore.runInTx {
        val entity = workoutExercisesBox.query(WorkoutExerciseEntity_.uid.equal(workoutExerciseId))
            .build().use { it.findFirst() } ?: return@runInTx
        // Also delete all sets belonging to this exercise
        val sets = workoutSetsBox.query(
            com.ironlog.app.data.objectbox.WorkoutSetEntity_.workoutExerciseUid.equal(workoutExerciseId)
        ).build().use { it.find() }
        workoutSetsBox.remove(sets)
        workoutExercisesBox.remove(entity)
        val remaining = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.equal(entity.workoutUid))
            .order(WorkoutExerciseEntity_.orderIndex).build().use { it.find() }
        remaining.forEachIndexed { index, row -> row.orderIndex = index }
        workoutExercisesBox.put(remaining)
        }
    }

    suspend fun completeWorkout(
        workoutId: String,
        durationStartEpochMs: Long? = null,
        durationSecondsOverride: Int? = null,
        metadata: WorkoutMetadataInput = WorkoutMetadataInput(),
        exerciseNotesByUid: Map<String, String> = emptyMap(),
    ): WorkoutEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        val now = System.currentTimeMillis()
        boxStore.callInTx {
            val workout = findWorkoutByUidOrThrow(workoutId)
            check(workout.status != "abandoned") { "An abandoned workout cannot be completed." }
            if (workout.status != "completed") {
                // Validate every stable row ID before writing any part of the completion.
                val noteRows = exerciseNotesByUid.map { (uid, note) ->
                    val row = workoutExercisesBox.query(WorkoutExerciseEntity_.uid.equal(uid))
                        .build().use { it.findFirst() }
                    require(row != null && row.workoutUid == workoutId) {
                        "Exercise note does not belong to this workout: $uid"
                    }
                    row.apply { notes = note; updatedAt = now }
                }
                if (noteRows.isNotEmpty()) workoutExercisesBox.put(noteRows)
                freezeWorkoutExerciseSnapshots(workoutId, now)
                val validDurationStart = durationStartEpochMs?.takeIf { it in 1..now }
                val rawDurationSec = durationSecondsOverride?.takeIf { it >= 0 }
                    ?: validDurationStart?.let { ((now - it) / 1000.0).roundToInt() }
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
            if (settingsRepository.getStringBlocking("active_workout_id") == workoutId) {
                clearActiveWorkoutSettings(workoutId)
            }
            workout
        }
    }

    /**
     * Clears only exercise-level notes belonging to the currently active workout and replaces
     * its draft in the same ObjectBox transaction, so a stale draft cannot restore deleted notes.
     */
    suspend fun clearActiveWorkoutExerciseNotes(
        workoutId: String,
        sanitizedDraftJson: String,
    ): Int = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        require(sanitizedDraftJson.isNotBlank()) { "sanitizedDraftJson must not be empty" }
        boxStore.callInTx {
            val workout = findWorkoutByUidOrThrow(workoutId)
            check(workout.status == "active") { "Only an active workout's exercise notes can be deleted." }
            check(settingsRepository.getStringBlocking("active_workout_id") == workoutId) {
                "This workout is no longer active."
            }

            val rows = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.equal(workoutId))
                .build().use { it.find() }
            val changed = rows.filter { it.notes.isNotBlank() }
            if (changed.isNotEmpty()) {
                val now = System.currentTimeMillis()
                changed.forEach { row ->
                    row.notes = ""
                    row.updatedAt = now
                }
                workoutExercisesBox.put(changed)
            }
            settingsRepository.setStringBlocking(
                "active_workout_draft_$workoutId",
                sanitizedDraftJson,
                "json",
            )
            changed.size
        }
    }

    suspend fun abandonWorkout(workoutId: String): WorkoutEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        val now = System.currentTimeMillis()
        boxStore.callInTx {
            val workout = findWorkoutByUidOrThrow(workoutId)
            check(workout.status != "completed") { "A completed workout cannot be abandoned." }
            if (workout.status != "abandoned") {
                workout.status = "abandoned"
                workout.completedAt = now
                workout.updatedAt = now
                workoutsBox.put(workout)
            }
            if (settingsRepository.getStringBlocking("active_workout_id") == workoutId) {
                clearActiveWorkoutSettings(workoutId)
            }
            workout
        }
    }

    suspend fun recordPostWorkoutMetrics(
        workoutId: String,
        volumeKg: Double,
        hasNewPr: Boolean,
    ): PostWorkoutMetricsResult = withContext(Dispatchers.IO) {
        requireNonEmpty(workoutId, "workoutId")
        val markerKey = "workout_post_processed_$workoutId"
        var result = PostWorkoutMetricsResult(newlyRecorded = false, cumulativeVolumeKg = 0.0)
        boxStore.runInTx {
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
            "active_workout_start_elapsed_ms",
            "active_workout_start_boot_count",
            "active_workout_set_label",
            "active_workout_rest_end_ms",
            "active_workout_rest_end_elapsed_ms",
            "active_workout_rest_boot_count",
            "active_workout_rest_paused_remaining_ms",
            "active_workout_intended_date",
        )
    }

    private fun clearActiveWorkoutSettings(workoutId: String) {
        removeSettings(
            "active_workout_id",
            "active_workout_day_id",
            "active_workout_day_name",
            "active_workout_start_ms",
            "active_workout_start_elapsed_ms",
            "active_workout_start_boot_count",
            "active_workout_set_label",
            "active_workout_rest_end_ms",
            "active_workout_rest_end_elapsed_ms",
            "active_workout_rest_boot_count",
            "active_workout_rest_paused_remaining_ms",
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
        observeQuery { workoutsBox.query(WorkoutEntity_.status.equal("completed")).orderDesc(WorkoutEntity_.startedAt).build() }

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
        createCompletedWorkoutBlocking(input)
    }

    private fun createCompletedWorkoutBlocking(input: CreateCompletedWorkoutInput): WorkoutEntity {
        validateCompletedInput(input)
        val now = System.currentTimeMillis()
        val completedAt = Math.addExact(input.startedAt, input.durationSeconds * 1000L)

        // Extract unique normalized names of the incoming exercises to avoid loading the entire table.
        val targetNames = input.exerciseData.mapNotNull { it.name?.let(::normalizeExerciseNameKey) }.distinct().toTypedArray()
        val existing = if (targetNames.isEmpty()) emptyList() else {
            exercisesBox.query(ExerciseEntity_.normalizedName.oneOf(targetNames)).build().use { it.find() }
        }
        val exByNormName = existing.associateBy { it.normalizedName }.toMutableMap()

        var workoutResult: WorkoutEntity? = null
        boxStore.runInTx {
            val missing = input.exerciseData.filter { ex ->
                ex.exerciseId?.let(::findExerciseByUidOrNull) == null &&
                    !exByNormName.containsKey(normalizeExerciseNameKey(ex.name ?: ""))
            }
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
                input.uid?.let { uid = it }
                name = input.name ?: "Workout"
                startedAt = input.startedAt
                this.completedAt = completedAt
                durationSeconds = max(0.0, input.durationSeconds.toDouble()).roundToInt()
                rating = input.rating
                notes = input.notes ?: ""
                status = "completed"
                imported = input.imported
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
                    exerciseSnapshotJson = captureSnapshotJson(exercise, ex)
                    createdAt = input.startedAt
                    updatedAt = now
                }
                workoutExercisesBox.put(we)
                val setRows = ex.sets.mapIndexed { si, s ->
                    WorkoutSetEntity().apply {
                        s.uid?.let { uid = it }
                        workoutExercise.target = we
                        workoutExerciseUid = we.uid
                        setIndex = si + 1
                        weight = max(0.0, s.weight ?: 0.0)
                        reps = max(0.0, s.reps ?: 0.0)
                        rpe = s.rpe
                        rir = s.rir
                        restSeconds = max(0, s.restSeconds ?: s.rest ?: 0)
                        isWarmup = s.type == "warmup" || s.isWarmup == true
                        isDropset = s.type in setOf("drop", "dropset") || s.isDropset == true
                        isAmrap = s.type == "amrap" || s.isAmrap == true || s.isAMRAP == true
                        toFailure = s.type == "failure" || s.toFailure == true
                        notes = s.notes.orEmpty()
                        this.completedAt = s.completedAt ?: completedAt
                        createdAt = input.startedAt
                        updatedAt = now
                    }
                }
                if (setRows.isNotEmpty()) workoutSetsBox.put(setRows)
            }
            workoutResult = workout
        }
        return workoutResult ?: error("Failed to save workout in transaction")
    }


    /** No live-session keys, timers, metrics awards or services participate in historical entry. */
    suspend fun saveHistoricalWorkout(
        input: CreateCompletedWorkoutInput,
        zoneId: ZoneId,
        allowSameDay: Boolean = false,
    ): com.ironlog.app.data.model.HistoricalWorkoutSaveResult = withContext(Dispatchers.IO) {
        require(!input.uid.isNullOrBlank()) { "A historical draft needs a stable ID." }
        require(!input.imported) { "Local historical entries are not imports." }
        validateCompletedInput(input)
        require(input.startedAt <= System.currentTimeMillis()) { "Historical workouts cannot start in the future." }
        val localDay = java.time.Instant.ofEpochMilli(input.startedAt).atZone(zoneId).toLocalDate()
        val from = localDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val until = localDay.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        boxStore.callInTx {
            val existing = workoutsBox.query(WorkoutEntity_.uid.equal(input.uid)).build().use { it.findFirst() }
            if (existing != null) {
                require(matchesCompletedInput(existing, input)) { "This draft was already saved with different contents. Reopen it from History." }
                com.ironlog.app.data.model.HistoricalWorkoutSaveResult.Saved(existing.uid)
            } else {
                val conflicts = workoutsBox.query(WorkoutEntity_.status.equal("completed")
                    .and(WorkoutEntity_.startedAt.between(from, until - 1)))
                    .order(WorkoutEntity_.startedAt).build().use { it.find() }
                if (!allowSameDay && conflicts.isNotEmpty()) {
                    com.ironlog.app.data.model.HistoricalWorkoutSaveResult.DateConflict(conflicts.map {
                        com.ironlog.app.data.model.HistoricalWorkoutConflict(it.uid, it.name, it.startedAt)
                    })
                } else {
                    // Validate selected IDs again inside the write transaction.
                    input.exerciseData.forEach { exercise ->
                        require(!exercise.exerciseId.isNullOrBlank() && findExerciseByUidOrNull(exercise.exerciseId) != null) {
                            "A selected exercise is no longer available. Choose it again."
                        }
                    }
                    com.ironlog.app.data.model.HistoricalWorkoutSaveResult.Saved(createCompletedWorkoutBlocking(input).uid)
                }
            }
        }
    }

    private fun validateCompletedInput(input: CreateCompletedWorkoutInput) {
        require(input.uid == null || input.uid.isNotBlank()) { "Workout ID cannot be blank." }
        require(input.durationSeconds >= 0) { "Duration cannot be negative." }
        Math.addExact(input.startedAt, input.durationSeconds * 1000L)
        require(input.rating == null || (input.rating.isFinite() && input.rating in 1.0..5.0)) { "Rating must be 1–5." }
        input.exerciseData.flatMap { it.sets }.forEach { set ->
            require(set.uid == null || set.uid.isNotBlank()) { "Set ID cannot be blank." }
            require(set.weight == null || (set.weight.isFinite() && set.weight >= 0.0)) { "Load must be finite and nonnegative." }
            require(set.reps == null || (set.reps.isFinite() && set.reps >= 0.0)) { "Reps or seconds must be finite and nonnegative." }
            require(set.rpe == null || (set.rpe.isFinite() && set.rpe in 0.0..10.0)) { "RPE must be 0–10." }
            require(set.rir == null || (set.rir.isFinite() && set.rir in 0.0..10.0)) { "RIR must be 0–10." }
        }
    }

    /** Called inside the completion transaction; an existing historical snapshot is never rewritten. */
    private fun freezeWorkoutExerciseSnapshots(workoutId: String, now: Long) {
        val rows = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.equal(workoutId))
            .build().use { it.find() }
            .filter { it.exerciseSnapshotJson.isNullOrBlank() }
        if (rows.isEmpty()) return
        rows.forEach { row ->
            val exercise = findExerciseByUidOrNull(row.exerciseUid) ?: return@forEach
            row.exerciseSnapshotJson = captureSnapshotJson(exercise)
            row.updatedAt = maxOf(row.updatedAt, now)
        }
        workoutExercisesBox.put(rows.filter { !it.exerciseSnapshotJson.isNullOrBlank() })
    }

    private fun captureSnapshotJson(
        exercise: ExerciseEntity,
        performed: CompletedExerciseInput? = null,
    ): String {
        val muscles = exerciseMusclesBox.query(ExerciseMuscleEntity_.exerciseUid.equal(exercise.uid))
            .build().use { it.find() }
        val override = performed?.let { input ->
            HistoricalExerciseSnapshotOverride(
                name = input.name,
                primaryMuscles = input.primaryMuscles,
                primaryMuscle = input.primaryMuscle,
                secondaryMuscles = input.secondaryMuscles,
                muscleContributions = input.muscleContributions,
                equipment = input.equipment,
                category = input.category,
                trackingType = input.trackingType,
                isBodyweight = input.isBodyweight,
                requiresExternalLoad = input.requiresExternalLoad,
            )
        } ?: HistoricalExerciseSnapshotOverride()
        return HistoricalExerciseSnapshotCodec.encode(captureHistoricalExerciseSnapshot(exercise, muscles, override))
    }

    private fun matchesCompletedInput(workout: WorkoutEntity, input: CreateCompletedWorkoutInput): Boolean {
        if (workout.status != "completed" || workout.name != (input.name ?: "Workout") ||
            workout.startedAt != input.startedAt || workout.durationSeconds != input.durationSeconds ||
            workout.completedAt != input.startedAt + input.durationSeconds * 1000L ||
            workout.rating != input.rating || workout.notes.orEmpty() != input.notes.orEmpty() || workout.imported != input.imported) return false
        val links = workoutExercisesBox.query(WorkoutExerciseEntity_.workoutUid.equal(workout.uid))
            .order(WorkoutExerciseEntity_.orderIndex).build().use { it.find() }
        if (links.size != input.exerciseData.size) return false
        return links.zip(input.exerciseData).all { (link, exercise) ->
            val sets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.equal(link.uid))
                .order(WorkoutSetEntity_.setIndex).build().use { it.find() }
            link.exerciseUid == exercise.exerciseId && link.notes == (exercise.note ?: exercise.notes.orEmpty()) &&
                link.supersetGroup == exercise.supersetGroup.orEmpty() && sets.size == exercise.sets.size &&
                sets.zip(exercise.sets).all { (row, set) ->
                    (set.uid == null || row.uid == set.uid) && row.weight == (set.weight ?: 0.0) &&
                        row.reps == (set.reps ?: 0.0) && row.rpe == set.rpe && row.rir == set.rir &&
                        row.completedAt == (set.completedAt ?: (input.startedAt + input.durationSeconds * 1000L)) &&
                        row.notes.orEmpty() == set.notes.orEmpty() && row.restSeconds == max(0, set.restSeconds ?: set.rest ?: 0) &&
                        row.isWarmup == (set.type == "warmup" || set.isWarmup == true) &&
                        row.isDropset == (set.type in setOf("drop", "dropset") || set.isDropset == true) &&
                        row.isAmrap == (set.type == "amrap" || set.isAmrap == true || set.isAMRAP == true) &&
                        row.toFailure == (set.type == "failure" || set.toFailure == true)
                }
        }
    }

    suspend fun clearCompletedWorkouts() = withContext(Dispatchers.IO) {
        boxStore.runInTx {
            val workouts = workoutsBox.query(WorkoutEntity_.status.equal("completed")).build().use { it.find() }
            if (workouts.isEmpty()) return@runInTx
            val workoutIds = workouts.map { it.uid }.toTypedArray()
            val workoutExercises = workoutExercisesBox
                .query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds))
                .build().use { it.find() }
            if (workoutExercises.isNotEmpty()) {
                val workoutExerciseIds = workoutExercises.map { it.uid }.toTypedArray()
                val sets = workoutSetsBox
                    .query(WorkoutSetEntity_.workoutExerciseUid.oneOf(workoutExerciseIds))
                    .build().use { it.find() }
                if (sets.isNotEmpty()) workoutSetsBox.remove(sets)
                workoutExercisesBox.remove(workoutExercises)
            }
            workoutsBox.remove(workouts)
        }
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

    /** Uses session time, not import/link creation order; legacy null set timestamps remain valid. */
    suspend fun getLastExerciseSession(
        exerciseId: String,
        now: Instant = Instant.now(),
    ): LastExerciseSession? = withContext(Dispatchers.IO) {
        boxStore.callInReadTx {
            val links = workoutExercisesBox.query(WorkoutExerciseEntity_.exerciseUid.equal(exerciseId))
                .build().use { it.find() }
            val parentIds = links.map { it.workoutUid }.distinct().toTypedArray()
            if (parentIds.isEmpty()) return@callInReadTx null
            val parents = workoutsBox.query(WorkoutEntity_.uid.oneOf(parentIds)
                .and(WorkoutEntity_.status.equal("completed"))
                .and(WorkoutEntity_.startedAt.lessOrEqual(now.toEpochMilli())))
                .build().use { it.find() }.associateBy { it.uid }
            val ordered = links.filter { it.workoutUid in parents }.sortedWith(
                compareByDescending<WorkoutExerciseEntity> { parents.getValue(it.workoutUid).startedAt }
                    .thenBy { it.workoutUid }.thenBy { it.orderIndex }.thenBy { it.uid },
            )
            for (row in ordered) {
                val sets = workoutSetsBox.query(WorkoutSetEntity_.workoutExerciseUid.equal(row.uid))
                    .order(WorkoutSetEntity_.setIndex).build().use { it.find() }.filter { !it.isWarmup }
                if (sets.isNotEmpty()) return@callInReadTx LastExerciseSession(
                    sets = sets,
                    date = Instant.ofEpochMilli(parents.getValue(row.workoutUid).startedAt).toString(),
                    notes = row.notes,
                    workoutId = row.workoutUid,
                    workoutExerciseId = row.uid,
                )
            }
            null
        }
    }

    suspend fun getLastSessionSetsForExercise(exerciseId: String): List<WorkoutSetEntity> =
        getLastExerciseSession(exerciseId)?.sets.orEmpty()

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
