package com.ironlog.app.data.model

import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.PlanDayEntity
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.PlanExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import kotlinx.serialization.Serializable

// Exercise repository

data class ExerciseFilters(
    val primaryMuscle: String? = null,
    val equipment: String? = null,
    val category: String? = null,
    val customOnly: Boolean = false,
    val limit: Int? = null,
)

data class MuscleInput(
    val muscle: String? = null,
    val role: String? = null,
    val contribution_fraction: Double? = null,
)

data class CreateExerciseInput(
    val name: String? = null,
    val primaryMuscle: String? = null,
    val equipment: String? = null,
    val category: String? = null,
    val notes: String? = null,
    val muscles: List<MuscleInput>? = null,
    val trackingType: String? = null,
    val movementPattern: String? = null,
    val difficulty: String? = null,
    val isBodyweight: Boolean? = null,
    val secondaryMuscles: List<String>? = null,
)

data class UpdateExerciseInput(
    val name: String? = null,
    val primaryMuscle: String? = null,
    val equipment: String? = null,
    val category: String? = null,
    val notes: String? = null,
)

data class LegacyExerciseShape(
    val id: String,
    val exerciseId: String,
    val name: String,
    val primaryMuscles: List<String>,
    val primaryMuscle: String?,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val category: String,
    val trackingType: String,
    val isCustom: Boolean,
    val aliases: List<String>,
    val isBodyweight: Boolean,
    val movementPattern: String?,
    val difficulty: String?,
    val apparatus: String?,
    val equipmentDetail: String?,
    val sourceTags: List<String>,
    val notes: String,
)

// Plan repository

data class PlanInput(
    val name: String? = null,
    val goal: String? = null,
    val description: String? = null,
    val isActive: Boolean? = null,
)

data class PlanDayInput(
    val name: String? = null,
    val color: String? = null,
    val orderIndex: Int? = null,
)

data class PlanExerciseInput(
    val exerciseId: String? = null,
    val name: String? = null,
    val orderIndex: Int? = null,
    val sets: Int? = null,
    val reps: String? = null,
    val restSeconds: Int? = null,
    val supersetGroup: String? = null,
    val isWarmup: Boolean? = null,
    val notes: String? = null,
)

data class PlanBundle(
    val plan: PlanEntity,
    val days: List<PlanDayEntity>,
    val activeDayId: String?,
)

data class PlanSnapshot(
    val plan: PlanEntity,
    val days: List<PlanDayEntity>,
    val exercises: List<PlanExerciseEntity>,
)

data class WorkoutPerformedSet(
    val type: String? = null,
    val reps: Double? = null,
    val restSeconds: Int? = null,
    val rest: Int? = null,
    val isWarmup: Boolean? = null,
)

data class WorkoutPerformedExercise(
    val exerciseId: String? = null,
    val name: String? = null,
    val sets: List<WorkoutPerformedSet>? = null,
    val prescribedReps: Any? = null,
)

data class FullPlanObject(
    val name: String? = null,
    val goal: String? = null,
    val description: String? = null,
    val days: List<FullPlanDay> = emptyList(),
)

data class FullPlanDay(
    val name: String? = null,
    val color: String? = null,
    val exercises: List<PlanExerciseInput> = emptyList(),
)

// Workout repository

data class SetInput(
    val setIndex: Int? = null,
    val weight: Double? = null,
    val reps: Double? = null,
    val rpe: Double? = null,
    val rir: Double? = null,
    val restSeconds: Int? = null,
    val isWarmup: Boolean? = null,
    val isDropset: Boolean? = null,
    val isAmrap: Boolean? = null,
    val isAMRAP: Boolean? = null,
    val toFailure: Boolean? = null,
    val completedAt: Long? = null,
    val type: String? = null,
    val rest: Int? = null,
)

data class WorkoutMetadataInput(
    val startedAt: Long? = null,
    val durationSeconds: Int? = null,
    val rating: Double? = null,
    val notes: String? = null,
)

data class CompletedExerciseInput(
    val exerciseId: String? = null,
    val name: String? = null,
    val primaryMuscles: List<String>? = null,
    val primaryMuscle: String? = null,
    val equipment: String? = null,
    val category: String? = null,
    val supersetGroup: String? = null,
    val note: String? = null,
    val notes: String? = null,
    val sets: List<SetInput> = emptyList(),
)

data class CreateCompletedWorkoutInput(
    val name: String? = null,
    val startedAt: Long,
    val durationSeconds: Int,
    val rating: Double? = null,
    val notes: String? = null,
    val exerciseData: List<CompletedExerciseInput> = emptyList(),
)

data class WorkoutDetail(
    val workout: WorkoutEntity,
    val exercises: List<WorkoutExerciseEntity>,
    val sets: List<WorkoutSetEntity>,
    val totalVolume: Double,
)

// Stats repository

data class PrRecord(
    val exerciseId: String,
    val exerciseName: String,
    val exercisePrGroupId: String,
    val weight: Double,
    val reps: Double,
    val estimated1RM: Double,
)

// Seed JSON DTOs
@Serializable
data class ExerciseSeedPayload(
    val meta: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val exercises: List<ExerciseSeedEntry> = emptyList(),
)

@Serializable
data class ExerciseSeedEntry(
    val id: String? = null,
    val name: String? = null,
    val primaryMuscle: String? = null,
    val primaryMuscles: List<String>? = null,
    val secondaryMuscles: List<String>? = null,
    val equipment: String? = null,
    val equipmentDetail: String? = null,
    val apparatus: List<String>? = null,
    val trackingType: String? = null,
    val category: String? = null,
    val isBodyweight: Boolean? = null,
    val requiresExternalLoad: Boolean? = null,
    val movementPattern: String? = null,
    val difficulty: String? = null,
    val aliases: List<String>? = null,
    val sourceTags: List<String>? = null,
    val notes: String? = null,
)
