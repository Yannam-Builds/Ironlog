package com.ironlog.app.domain.intelligence

object VolumeInterpretationEngine {

    /**
     * Generates a one-line insight string from pre-computed volume landmarks.
     * Prefers muscles that are out of range (low/high) first, then summarises the rest.
     */
    fun buildMuscleInsight(volumeLandmarks: Map<String, VolumeLandmark>): String {
        if (volumeLandmarks.isEmpty()) return "Log workouts to see volume interpretation."

        // Muscles outside optimal range first
        val outOfRange = volumeLandmarks.filter { it.value.status != "optimal" }
        val optimal   = volumeLandmarks.filter { it.value.status == "optimal" }

        val parts = mutableListOf<String>()
        outOfRange.forEach { (muscle, lm) ->
            val verb = if (lm.status == "low") "below MEV" else "above MRV"
            parts += "$muscle is $verb (${lm.sets} sets)"
        }
        when {
            optimal.size == volumeLandmarks.size ->
                parts += "All groups are in optimal range"
            optimal.isNotEmpty() ->
                parts += "${optimal.keys.joinToString(", ")} ${if (optimal.size == 1) "is" else "are"} optimal"
        }
        return parts.joinToString(". ").trimEnd('.') + "."
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
