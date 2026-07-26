package com.ironlog.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity_
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import com.ironlog.app.util.normalizeExerciseNameKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class ExerciseProgressViewModel(
    private val exerciseName: String,
) : ViewModel() {
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _history.value = withContext(Dispatchers.IO) {
            val workoutsBox = ObjectBox.store.boxFor(WorkoutEntity::class.java)
            val wesBox = ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java)
            val setsBox = ObjectBox.store.boxFor(WorkoutSetEntity::class.java)
            val exercisesBox = ObjectBox.store.boxFor(ExerciseEntity::class.java)

            val byName = exercisesBox.query(ExerciseEntity_.normalizedName.equal(normalizeExerciseNameKey(exerciseName)))
                .build().use { it.findFirst() }
                ?: exercisesBox.query(ExerciseEntity_.name.equal(exerciseName))
                    .build().use { it.findFirst() }
                ?: return@withContext emptyList()
            val rows = wesBox.query(WorkoutExerciseEntity_.exerciseUid.equal(byName.uid))
                .order(WorkoutExerciseEntity_.orderIndex)
                .build().use { it.find() }
            val workoutIds = rows.map { it.workoutUid }.distinct().toTypedArray()
            val completedWorkouts = if (workoutIds.isEmpty()) emptyMap() else {
                workoutsBox.query(
                    WorkoutEntity_.uid.oneOf(workoutIds)
                        .and(WorkoutEntity_.status.equal("completed"))
                ).build().use { it.find() }.associateBy { it.uid }
            }
            val completedRows = rows.filter { completedWorkouts.containsKey(it.workoutUid) }
            val weIds = completedRows.map { it.uid }
            val setsByWe = if (weIds.isEmpty()) emptyMap() else {
                setsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(weIds.toTypedArray())).build().use { it.find() }.groupBy { it.workoutExerciseUid }
            }

            completedRows.mapNotNull { we ->
                val workout = completedWorkouts[we.workoutUid] ?: return@mapNotNull null
                if (workout.startedAt <= 0L) return@mapNotNull null
                val exercise = HistoryExercise(
                    id = we.uid,
                    exerciseId = we.exerciseUid,
                    name = byName.name,
                    primaryMuscle = byName.primaryMuscle,
                    equipment = byName.equipment,
                    category = byName.category,
                    supersetGroup = we.supersetGroup.ifBlank { null },
                    note = we.notes.ifBlank { null },
                    sets = setsByWe[we.uid].orEmpty().sortedBy { it.setIndex }.map { s ->
                        HistoryExerciseSet(
                            id = s.uid,
                            weight = s.weight,
                            reps = s.reps,
                            type = when {
                                s.isWarmup -> "warmup"
                                s.isDropset -> "drop"
                                s.isAmrap -> "amrap"
                                s.toFailure -> "failure"
                                else -> "normal"
                            },
                            rpe = s.rpe,
                            rir = s.rir,
                            restSeconds = s.restSeconds,
                        )
                    },
                )
                HistoryEntry(
                    id = workout.uid,
                    date = Instant.ofEpochMilli(workout.startedAt).toString(),
                    duration = workout.durationSeconds,
                    rating = workout.rating,
                    summaryText = workout.notes.ifBlank { null },
                    name = workout.name,
                    imported = workout.imported,
                    exercises = listOf(exercise),
                )
            }.sortedBy { it.date }
        }
    }
}

class ExerciseProgressViewModelFactory(
    private val exerciseName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExerciseProgressViewModel::class.java)) {
            return ExerciseProgressViewModel(exerciseName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
