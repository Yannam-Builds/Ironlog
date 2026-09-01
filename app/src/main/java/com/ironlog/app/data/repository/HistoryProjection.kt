package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.history.HistoricalExerciseSnapshotCodec
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import kotlinx.serialization.json.Json
import java.time.Instant
import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.domain.training.TrainingSetPolicy

/** Lossless performed-history projection. Eligibility for statistics is a separate policy. */
fun projectHistory(
    workouts: List<WorkoutEntity>,
    workoutExercises: List<WorkoutExerciseEntity>,
    sets: List<WorkoutSetEntity>,
    exercises: List<ExerciseEntity>,
    exerciseMuscles: List<ExerciseMuscleEntity> = emptyList(),
): List<HistoryEntry> {
    val exercisesById = exercises.associateBy { it.uid }
    val linksByWorkout = workoutExercises.groupBy { it.workoutUid }
    val setsByLink = sets.groupBy { it.workoutExerciseUid }
    val musclesByExercise = exerciseMuscles.groupBy { it.exerciseUid }
    return workouts.filter { it.status == "completed" }
        .sortedWith(compareByDescending<WorkoutEntity> { it.startedAt }.thenBy { it.uid })
        .map { workout ->
            HistoryEntry(
                id = workout.uid,
                date = Instant.ofEpochMilli(workout.startedAt).toString(),
                duration = workout.durationSeconds,
                rating = workout.rating,
                summaryText = workout.notes?.takeIf { it.isNotBlank() },
                name = workout.name,
                planDayUid = workout.planDayUid?.takeIf { it.isNotBlank() },
                imported = workout.imported,
                exercises = linksByWorkout[workout.uid].orEmpty()
                    .sortedWith(compareBy<WorkoutExerciseEntity> { it.orderIndex }.thenBy { it.uid })
                    .map { link ->
                        val exercise = exercisesById[link.exerciseUid]
                        val muscles = musclesByExercise[link.exerciseUid].orEmpty()
                        val snapshot = HistoricalExerciseSnapshotCodec.decode(link.exerciseSnapshotJson)
                        val currentPrimary = muscles.filter { it.role.equals("primary", true) }.map { it.muscle }
                        val currentSecondary = exercise?.secondaryMusclesJson?.let { raw ->
                            runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
                        }.orEmpty() + muscles.filter { it.role.equals("secondary", true) }.map { it.muscle }
                        val currentContributions = muscles
                            .filter { it.muscle.isNotBlank() && it.contributionFraction.isFinite() && it.contributionFraction > 0.0 }
                            .groupBy { it.muscle }
                            .mapValues { (_, rows) -> rows.sumOf { it.contributionFraction } }
                        HistoryExercise(
                            id = link.uid,
                            exerciseId = link.exerciseUid,
                            name = snapshot?.name ?: exercise?.name ?: "Unknown",
                            primaryMuscles = snapshot?.primaryMuscles
                                ?: currentPrimary.filter { it.isNotBlank() }.distinct(),
                            primaryMuscle = if (snapshot != null) snapshot.primaryMuscle
                                else exercise?.primaryMuscle?.takeIf { it.isNotBlank() },
                            secondaryMuscles = snapshot?.secondaryMuscles
                                ?: currentSecondary.filter { it.isNotBlank() }.distinct(),
                            muscleContributions = snapshot?.muscleContributions ?: currentContributions,
                            equipment = if (snapshot != null) snapshot.equipment
                                else exercise?.equipment?.takeIf { it.isNotBlank() },
                            category = if (snapshot != null) snapshot.category
                                else exercise?.category?.takeIf { it.isNotBlank() },
                            trackingType = if (snapshot != null) snapshot.trackingType
                                else exercise?.trackingType?.takeIf { it.isNotBlank() },
                            isBodyweight = snapshot?.isBodyweight ?: exercise?.isBodyweight ?: false,
                            requiresExternalLoad = snapshot?.requiresExternalLoad ?: exercise?.requiresExternalLoad ?: false,
                            supersetGroup = link.supersetGroup.takeIf { it.isNotBlank() },
                            note = link.notes.takeIf { it.isNotBlank() },
                            sets = setsByLink[link.uid].orEmpty()
                                .sortedWith(compareBy<WorkoutSetEntity> { it.setIndex }.thenBy { it.uid })
                                .map { set ->
                                    HistoryExerciseSet(
                                        id = set.uid, weight = set.weight, reps = set.reps,
                                        type = when {
                                            set.isWarmup -> "warmup"
                                            set.isDropset -> "drop"
                                            set.isAmrap -> "amrap"
                                            set.toFailure -> "failure"
                                            else -> "normal"
                                        },
                                        rpe = set.rpe, rir = set.rir,
                                        note = set.notes?.takeIf { it.isNotBlank() }, restSeconds = set.restSeconds,
                                        isWarmup = set.isWarmup, isDropset = set.isDropset,
                                        isAmrap = set.isAmrap, toFailure = set.toFailure,
                                        completedAt = set.completedAt,
                                    )
                                },
                        ).let { projected -> projected.copy(isBodyweight = TrainingSetPolicy.isBodyweight(projected)) }
                    },
            )
        }
}

/** Exercise Progress selects from the full snapshot; it does not maintain another entity mapper. */
fun projectExerciseHistory(history: List<HistoryEntry>, exerciseId: String): List<HistoryEntry> =
    history.mapNotNull { workout ->
        workout.exercises.filter { it.exerciseId == exerciseId }.takeIf { it.isNotEmpty() }
            ?.let { workout.copy(exercises = it) }
    }.sortedBy { parseHistoryInstant(it.date) }
