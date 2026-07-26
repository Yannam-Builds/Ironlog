package com.ironlog.app.domain.intelligence

/**
 * Maps plan days to muscle-group readiness scores and recommends the best
 * day to train based on recovery state from [RecoveryReadinessEngine].
 */
class WorkoutSuggestionEngine {

    // keyword → region mapping (lowercase keywords for case-insensitive matching).
    // Order matters: checked top-to-bottom via firstOrNull.
    // Shoulders is placed before Pull so "lateral raise" matches "lateral raise" before "lat".
    private val regionKeywords: Map<String, List<String>> = mapOf(
        "Push"      to listOf("bench", "press", "dip", "push", "fly", "flye", "chest", "tricep"),
        "Shoulders" to listOf("lateral raise", "front raise", "face pull", "upright row", "shoulder", "overhead", "ohp"),
        "Pull"      to listOf("row", "pull", "lat", "deadlift", "shrug", "bicep", "chin"),
        "Legs"      to listOf("squat", "leg", "lunge", "calf", "glute", "hip thrust", "rdl", "hamstring", "quad"),
        "Core"      to listOf("plank", "crunch", "ab", "oblique", "core", "sit-up", "situp", "hollow"),
        "Arms"      to listOf("curl", "extension", "forearm", "wrist"),
    )

    /**
     * Returns the training region for [exerciseName] using keyword matching,
     * or null if no region matches.
     */
    fun regionForExercise(exerciseName: String): String? {
        val lower = exerciseName.lowercase()
        return regionKeywords.entries.firstOrNull { (_, keywords) ->
            keywords.any { kw -> lower.contains(kw) }
        }?.key
    }

    /**
     * Scores a plan day against [readiness] (0.0 = fully fatigued, 1.0 = fully recovered).
     * Returns 0.5 (neutral) if none of the exercises map to a known region.
     */
    fun scoreDay(readiness: Map<String, Double>, exerciseNames: List<String>): Double {
        val scores = exerciseNames
            .mapNotNull { name -> regionForExercise(name)?.let { region -> readiness[region] } }
        return if (scores.isEmpty()) 0.5 else scores.average()
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
    fun recommendationBlurb(readiness: Map<String, Double>, dayName: String): String {
        val topRegion = readiness.maxByOrNull { it.value }
        val freshness = when {
            (topRegion?.value ?: 0.5) >= 0.8 -> "Your muscles are fresh"
            (topRegion?.value ?: 0.5) >= 0.5 -> "You're recovering well"
            else -> "Take it easy today"
        }
        return "$freshness — $dayName looks like your best pick."
    }
}
