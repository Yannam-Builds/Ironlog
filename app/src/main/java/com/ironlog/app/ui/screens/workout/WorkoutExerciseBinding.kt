package com.ironlog.app.ui.screens.workout

internal data class WorkoutExerciseBinding(
    val workoutExerciseId: String,
    val exerciseId: String,
    val orderIndex: Int,
)

internal fun buildWorkoutExerciseIndexMap(
    exerciseIdsInUiOrder: List<String>,
    workoutExerciseRows: List<WorkoutExerciseBinding>,
): Map<Int, String> {
    if (exerciseIdsInUiOrder.isEmpty() || workoutExerciseRows.isEmpty()) return emptyMap()

    val orderedRows = workoutExerciseRows.sortedBy { it.orderIndex }
    val rowsByExercise = orderedRows
        .groupBy { it.exerciseId }
        .mapValues { (_, rows) -> ArrayDeque(rows) }
        .toMutableMap()

    val result = mutableMapOf<Int, String>()
    exerciseIdsInUiOrder.forEachIndexed { index, exerciseId ->
        val exact = rowsByExercise[exerciseId]?.removeFirstOrNull()
        val fallback = orderedRows.getOrNull(index)
        val binding = exact ?: fallback
        if (binding != null) result[index] = binding.workoutExerciseId
    }
    return result
}

