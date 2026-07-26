package com.ironlog.app.util

import com.ironlog.app.data.model.LegacyExerciseShape
import java.util.Locale

private fun norm(value: String?): String = value.orEmpty()
    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
    .lowercase(Locale.US)
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

fun getExerciseFilterSummary(exercise: LegacyExerciseShape, maxItems: Int = 6): String {
    val muscles = buildList {
        addAll(exercise.primaryMuscles)
        addAll(exercise.secondaryMuscles)
        exercise.primaryMuscle?.let { add(it) }
    }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    return muscles.take(maxItems).joinToString(" · ")
}

fun matchesExerciseFilter(exercise: LegacyExerciseShape, filter: String?): Boolean {
    val f = norm(filter)
    if (f.isBlank()) return true
    val haystack = listOf(
        exercise.name,
        exercise.primaryMuscle.orEmpty(),
        exercise.primaryMuscles.joinToString(" "),
        exercise.secondaryMuscles.joinToString(" "),
        exercise.equipment,
        exercise.category,
        exercise.movementPattern.orEmpty(),
        exercise.difficulty.orEmpty(),
        exercise.equipmentDetail.orEmpty(),
    ).joinToString(" ").let(::norm)
    return haystack.contains(f)
}

fun buildFilterChipOptions(exercises: List<LegacyExerciseShape>, includeCategory: Boolean = true, includeEquipment: Boolean = true): List<String> {
    val set = linkedSetOf<String>()
    exercises.forEach { ex ->
        ex.primaryMuscles.forEach { if (it.isNotBlank()) set += it }
        ex.primaryMuscle?.takeIf { it.isNotBlank() }?.let { set += it }
        ex.secondaryMuscles.forEach { if (it.isNotBlank()) set += it }
        if (includeEquipment && ex.equipment.isNotBlank()) set += ex.equipment
        if (includeCategory && ex.category.isNotBlank()) set += ex.category.replaceFirstChar { it.titlecase(Locale.US) }
    }
    return set.sorted()
}

fun queryExerciseSearch(exercises: List<LegacyExerciseShape>, query: String): List<LegacyExerciseShape> {
    val q = norm(query)
    if (q.isBlank()) return exercises

    // Split into individual words; only keep words ≥2 chars to avoid noise
    val queryWords = q.split(" ").filter { it.length >= 2 }

    return exercises
        .map { ex ->
            val name = norm(ex.name)
            val aliases = ex.aliases.joinToString(" ").let(::norm)
            val haystack = listOf(
                ex.name,
                ex.primaryMuscle.orEmpty(),
                ex.primaryMuscles.joinToString(" "),
                ex.secondaryMuscles.joinToString(" "),
                ex.equipment,
                ex.category,
                ex.movementPattern.orEmpty(),
                ex.difficulty.orEmpty(),
            ).joinToString(" ").let(::norm)

            val score = when {
                // Exact full-name match
                name == q -> 0
                // Name starts with the full query
                name.startsWith(q) -> 1
                // Name contains the full query as a substring
                name.contains(q) -> 2
                // Alias contains full query
                aliases.contains(q) -> 2
                // All query words found in the name (handles "bench press" → "Incline Bench Press")
                queryWords.isNotEmpty() && queryWords.all { w -> name.contains(w) } -> 3
                // Any single word in the query starts a word in the name
                // e.g. "squa" → "squat", "benchp" → no, but "curl" → "Bicep Curl"
                queryWords.isNotEmpty() && queryWords.any { w ->
                    name.split(" ").any { namePart -> namePart.startsWith(w) && w.length >= 3 }
                } -> 4
                // All query words found anywhere in the full metadata haystack
                queryWords.isNotEmpty() && queryWords.all { w -> haystack.contains(w) } -> 5
                // Any query word ≥3 chars found in haystack (broadest catch-all)
                queryWords.any { w -> w.length >= 3 && haystack.contains(w) } -> 6
                else -> 1000
            }
            score to ex
        }
        .filter { it.first < 1000 }
        .sortedWith(compareBy<Pair<Int, LegacyExerciseShape>> { it.first }.thenBy { it.second.name })
        .map { it.second }
}

fun normalizeExerciseNameKey(name: String): String =
    name.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trimEnd('_')
