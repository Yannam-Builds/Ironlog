package com.ironlog.app.domain.intelligence

object VolumeInterpretationEngine {

    /**
     * Generates a one-line insight string from pre-computed volume landmarks.
     * Prefers muscles that are out of range (low/high) first, then summarises the rest.
     */
    fun buildMuscleInsight(volumeLandmarks: Map<String, VolumeLandmark>): String {
        if (volumeLandmarks.isEmpty()) return "Log workouts to see volume interpretation."

        // Muscles outside optimal range first
        if (volumeLandmarks.values.all { it.sets == 0 }) return "No mapped working-set exposure this week yet. Follow your plan; an incomplete week is not a deficit."
        val outOfRange = volumeLandmarks.filter { it.value.status != "optimal" }
        val optimal   = volumeLandmarks.filter { it.value.status == "optimal" }

        val parts = mutableListOf<String>()
        outOfRange.forEach { (muscle, lm) ->
            val verb = if (lm.status == "low") "below the reference band so far" else "above the reference band"
            parts += "$muscle is $verb (${lm.sets} weighted set equivalents)"
        }
        when {
            optimal.size == volumeLandmarks.size ->
                parts += "All groups are in the weighted-exposure reference bands"
            optimal.isNotEmpty() ->
                parts += "${optimal.keys.joinToString(", ")} ${if (optimal.size == 1) "is" else "are"} in range"
        }
        return parts.joinToString(". ").trimEnd('.') + ". These heuristic bands are not personal volume requirements; review a complete week and recovery before changing your plan."
    }

    /**
     * Legacy overload — accepts a raw set-count map and derives status using
     * TrainingIntelligenceEngine's landmark thresholds. Kept for backward compatibility.
     */
    @JvmName("buildMuscleInsightFromSets")
    fun buildMuscleInsight(volumeByMuscle: Map<String, Int>): String {
        val landmarks = TrainingIntelligenceEngine.buildLandmarks(volumeByMuscle)
        return buildMuscleInsight(landmarks)
    }
}
