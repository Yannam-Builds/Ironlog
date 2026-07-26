package com.ironlog.app.domain.intelligence

import com.ironlog.app.data.model.ExerciseFilters
import com.ironlog.app.data.model.LegacyExerciseShape

object SubstitutionEngine {
    data class AlternativesResult(
        val bestMatches: List<LegacyExerciseShape>,
        val sameMuscle: List<LegacyExerciseShape>,
        val sameEquipment: List<LegacyExerciseShape>,
        val fallback: List<LegacyExerciseShape>,
    ) {
        companion object { val EMPTY = AlternativesResult(emptyList(), emptyList(), emptyList(), emptyList()) }
    }

    fun buildExerciseAlternatives(
        exercise: LegacyExerciseShape,
        candidates: List<LegacyExerciseShape>,
        filters: ExerciseFilters,
        limit: Int,
    ): AlternativesResult {
        val pool = candidates
            .asSequence()
            .filter { it.id != exercise.id }
            .filter { filters.primaryMuscle?.let { m -> it.primaryMuscle.equals(m, true) } ?: true }
            .filter { filters.equipment?.let { e -> it.equipment.equals(e, true) } ?: true }
            .filter { filters.category?.let { c -> it.category.equals(c, true) } ?: true }
            .toList()
        val scored = pool.map { candidate ->
            val muscleScore = if (candidate.primaryMuscle.equals(exercise.primaryMuscle, true)) 3 else 0
            val equipScore = if (candidate.equipment.equals(exercise.equipment, true)) 2 else 0
            val patternScore = if (candidate.movementPattern.equals(exercise.movementPattern, true)) 2 else 0
            val categoryScore = if (candidate.category.equals(exercise.category, true)) 1 else 0
            val total = muscleScore + equipScore + patternScore + categoryScore
            candidate to total
        }.sortedByDescending { it.second }
        val best = scored.filter { it.second >= 5 }.map { it.first }.take(limit)
        val sameMuscle = scored.filter { it.first.primaryMuscle.equals(exercise.primaryMuscle, true) }.map { it.first }.take(limit)
        val sameEquipment = scored.filter { it.first.equipment.equals(exercise.equipment, true) }.map { it.first }.take(limit)
        val fallback = scored.map { it.first }.take(limit)
        return AlternativesResult(best, sameMuscle, sameEquipment, fallback)
    }
}
