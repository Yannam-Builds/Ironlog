package com.ironlog.app.domain.intelligence

import com.ironlog.app.util.normalizeExerciseNameKey

object PrLinkingEngine {
    private val aliases = linkedMapOf(
        "barbell_bench_press" to "bench_press",
        "dumbbell_bench_press" to "bench_press",
        "incline_barbell_bench_press" to "incline_press",
        "incline_dumbbell_press" to "incline_press",
        "lat_pulldown" to "vertical_pull",
        "weighted_pull_up" to "vertical_pull",
        "pull_up" to "vertical_pull",
        "barbell_row" to "row",
        "seated_cable_row" to "row",
        "romanian_deadlift" to "hip_hinge",
        "deadlift" to "hip_hinge",
        "back_squat" to "squat",
        "front_squat" to "squat",
    )

    fun getPrGroupId(
        name: String,
        primaryMuscle: String?,
        equipment: String?,
        category: String?,
        mode: String = "safe",
    ): String? {
        val normalized = normalizeExerciseNameKey(name)
        if (normalized.isBlank()) return null
        val canonical = aliases[normalized] ?: normalized
        val muscleKey = normalizeExerciseNameKey(primaryMuscle.orEmpty()).ifBlank { "unknown_muscle" }
        val equipmentKey = normalizeExerciseNameKey(equipment.orEmpty()).ifBlank { "unknown_equipment" }
        val categoryKey = normalizeExerciseNameKey(category.orEmpty()).ifBlank { "general" }
        return when (mode.lowercase()) {
            "strict" -> "strict_${equipmentKey}_${canonical}"
            "broad" -> "broad_${muscleKey}_${categoryKey}_${canonical}"
            else -> "safe_${muscleKey}_${canonical}"
        }
    }
}
