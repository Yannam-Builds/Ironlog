package com.ironlog.app.domain.intelligence

/**
 * Maps plan days to muscle-group readiness scores and recommends the best
 * day to train based on recovery state from [RecoveryReadinessEngine].
 */
class WorkoutSuggestionEngine {

    // Specific regions come before broad movement families. Matching is token
    // aware, so "lat" no longer catches "lateral" and "ab" no longer catches
    // "abduction".
    private val regionKeywords: Map<String, List<String>> = linkedMapOf(
        "Core"      to listOf("leg raise", "knee raise", "toes to bar", "ab wheel", "plank", "crunch", "ab", "oblique", "core", "sit up", "situp", "hollow", "russian twist"),
        "Shoulders" to listOf("lateral raise", "front raise", "face pull", "upright row", "shoulder", "overhead press", "military press", "ohp", "rear delt"),
        "Legs"      to listOf("squat", "leg", "lunge", "calf", "glute", "hip thrust", "hip abduction", "hip adduction", "rdl", "hamstring", "quad", "deadlift"),
        "Arms"      to listOf("bicep", "tricep", "curl", "pushdown", "arm extension", "skull crusher", "forearm", "wrist"),
        "Push"      to listOf("bench", "chest press", "press", "dip", "push up", "pushup", "fly", "flye", "chest"),
        "Pull"      to listOf("row", "pull up", "pullup", "pulldown", "lat", "shrug", "chin up", "chinup"),
    )

    private val singularTokens = mapOf(
        "dips" to "dip", "shrugs" to "shrug", "biceps" to "bicep", "triceps" to "tricep",
        "curls" to "curl", "raises" to "raise", "extensions" to "extension", "legs" to "leg",
        "hamstrings" to "hamstring", "quads" to "quad", "glutes" to "glute", "calves" to "calf",
        "shoulders" to "shoulder", "delts" to "delt", "squats" to "squat", "lunges" to "lunge",
        "rows" to "row", "pullups" to "pullup", "pushups" to "pushup", "chinups" to "chinup",
        "crunches" to "crunch", "planks" to "plank", "abs" to "ab", "obliques" to "oblique",
    )

    /**
     * Returns the training region for [exerciseName] using keyword matching,
     * or null if no region matches.
     */
    fun regionForExercise(exerciseName: String): String? {
        val normalized = exerciseName.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
            .split(' ').joinToString(" ") { singularTokens[it] ?: it }
        val padded = " $normalized "
        return regionKeywords.entries.firstOrNull { (_, keywords) ->
            keywords.any { keyword -> padded.contains(" $keyword ") }
        }?.key
    }

    /**
     * Scores a plan day against [readiness] (0.0 = fully fatigued, 1.0 = fully recovered).
     * Returns 0.5 (neutral) if none of the exercises map to a known region.
     */
    fun scoreDay(readiness: Map<String, Double>, exerciseNames: List<String>): Double {
        val scores = exerciseNames
            .mapNotNull { name -> usableReadiness(readiness, name) }
        if (scores.isEmpty()) return 0.5
        val coverage = scores.size.toDouble() / exerciseNames.size.coerceAtLeast(1).toDouble()
        return (0.5 + (scores.average() - 0.5) * coverage).coerceIn(0.0, 1.0)
    }

    /**
     * Returns the 0-based index of the plan day with the highest readiness score.
     * Ties are broken by lower index (stable).
     */
    fun suggestDayIndex(
        readiness: Map<String, Double>,
        dayExerciseNames: List<List<String>>,
    ): Int {
        if (dayExerciseNames.isEmpty()) return 0
        return dayExerciseNames
            .mapIndexed { i, exercises -> i to scoreDay(readiness, exercises) }
            .maxByOrNull { (_, score) -> score }
            ?.first ?: 0
    }

    /**
     * Returns a short human-readable recommendation sentence for the UI.
     */
    fun recommendationBlurb(
        readiness: Map<String, Double>,
        dayName: String,
        exerciseNames: List<String> = emptyList(),
    ): String {
        val dayScore = scoreDay(readiness, exerciseNames)
        val mappedCount = exerciseNames.count { usableReadiness(readiness, it) != null }
        val coverage = mappedCount.toDouble() / exerciseNames.size.coerceAtLeast(1)
        val freshness = when {
            mappedCount == 0 -> "$dayName has insufficient recovery evidence"
            dayScore >= 0.8 -> "$dayName is well recovered"
            dayScore >= 0.55 -> "$dayName is reasonably recovered"
            else -> "$dayName overlaps fatigued regions"
        }
        val confidence = if (coverage < 0.5) " Limited exercise mapping or readiness data lowers confidence." else ""
        return "$freshness based on estimated training load.$confidence"
    }

    private fun usableReadiness(readiness: Map<String, Double>, exerciseName: String): Double? =
        regionForExercise(exerciseName)?.let(readiness::get)?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
}
