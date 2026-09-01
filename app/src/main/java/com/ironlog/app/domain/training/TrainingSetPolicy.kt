package com.ironlog.app.domain.training

import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import com.ironlog.app.util.ExerciseTrackingTypeNormalizer
import java.util.Locale

enum class TrackingMode {
    LOAD_REPS, BODYWEIGHT_REPS, ADDED_LOAD_REPS, ASSISTED_REPS,
    DURATION, WEIGHTED_DURATION, DURATION_DISTANCE, UNKNOWN,
}

/** Interprets performed sets; callers supply completed-workout context, not draft inputs. */
object TrainingSetPolicy {
    // Compatibility ceiling for a heuristic estimate, not a validated physiological boundary.
    private const val MAX_ESTIMATE_REPS = 30.0
    private val bodyweightNames = Regex(
        "\\b(?:pull[- ]?up|chin[- ]?up|push[- ]?up|dips?|planks?|crunch(?:es)?|sit[- ]?up|leg raise|mountain climber|muscle[- ]?up|handstand|pistol|nordic)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val assistedName = Regex("\\bassisted\\b", RegexOption.IGNORE_CASE)

    /** Same metadata resolution for library, history and active-workout adapters; no DB mutation. */
    fun isBodyweight(exercise: HistoryExercise): Boolean = exercise.isBodyweight ||
        exercise.equipment.key() == "bodyweight" ||
        exercise.trackingType.key() in setOf("bodyweight_reps", "bodyweight_plus_weight_reps", "weighted_bodyweight", "assisted_bodyweight") ||
        bodyweightNames.containsMatchIn(exercise.name)

    fun tracking(exercise: HistoryExercise): TrackingMode {
        val raw = exercise.trackingType.key()
        // Old library/import rows often say weight_reps for planks and cardio. Use the existing
        // library resolver only for that legacy type; explicit timed types and unknowns stay intact.
        val resolved = if (raw == "weight_reps") ExerciseTrackingTypeNormalizer.normalize(
            exercise.name, exercise.category, exercise.equipment, raw,
        ) else raw
        val assisted = assistedName.containsMatchIn(exercise.name) && isBodyweight(exercise)
        return when (resolved) {
            "assisted_bodyweight" -> TrackingMode.ASSISTED_REPS
            "weight_reps" -> when {
                assisted -> TrackingMode.ASSISTED_REPS
                isBodyweight(exercise) -> TrackingMode.ADDED_LOAD_REPS
                else -> TrackingMode.LOAD_REPS
            }
            "reps", "reps_only", "bodyweight_reps" -> if (assisted) TrackingMode.ASSISTED_REPS else TrackingMode.BODYWEIGHT_REPS
            "bodyweight_plus_weight_reps", "weighted_bodyweight" -> if (assisted) TrackingMode.ASSISTED_REPS else TrackingMode.ADDED_LOAD_REPS
            "duration", "cardio" -> TrackingMode.DURATION
            "duration_weight" -> TrackingMode.WEIGHTED_DURATION
            "duration_distance" -> TrackingMode.DURATION_DISTANCE
            else -> TrackingMode.UNKNOWN
        }
    }

    fun isValidWorkingSet(exercise: HistoryExercise, set: HistoryExerciseSet): Boolean {
        if (set.isWarmup || set.type.key() == "warmup") return false
        if (!set.weight.isFinite() || set.weight < 0.0 || !set.reps.isFinite() || set.reps <= 0.0) return false
        val mode = tracking(exercise)
        val hasLoadSlot = mode in setOf(TrackingMode.LOAD_REPS, TrackingMode.ADDED_LOAD_REPS, TrackingMode.WEIGHTED_DURATION) ||
            (mode == TrackingMode.UNKNOWN && !isCardio(exercise))
        if (hasLoadSlot && exercise.requiresExternalLoad && set.weight <= 0.0) return false
        // Null metadata is a supported legacy record, not a reason to discard performed work.
        if (mode == TrackingMode.UNKNOWN && !exercise.trackingType.isNullOrBlank()) return false
        return true
    }

    fun estimatedOneRm(exercise: HistoryExercise, set: HistoryExerciseSet): Double? {
        if (!isValidWorkingSet(exercise, set) || isCardio(exercise)) return null
        val mode = tracking(exercise)
        val legacyLoad = mode == TrackingMode.UNKNOWN && exercise.trackingType.isNullOrBlank() && !isBodyweight(exercise)
        if (mode != TrackingMode.LOAD_REPS && !legacyLoad) return null
        if (set.weight <= 0.0 || set.reps !in 1.0..MAX_ESTIMATE_REPS) return null
        return (if (set.reps == 1.0) set.weight else set.weight * (1.0 + set.reps / 30.0))
            .takeIf { it.isFinite() }
    }

    fun externalLoadVolume(exercise: HistoryExercise, set: HistoryExerciseSet): Double {
        if (!isValidWorkingSet(exercise, set) || isCardio(exercise)) return 0.0
        val mode = tracking(exercise)
        val legacyLoad = mode == TrackingMode.UNKNOWN && exercise.trackingType.isNullOrBlank()
        if (mode != TrackingMode.LOAD_REPS && mode != TrackingMode.ADDED_LOAD_REPS && !legacyLoad) return 0.0
        return (set.weight * set.reps).takeIf { it.isFinite() } ?: 0.0
    }

    fun cardioSeconds(exercise: HistoryExercise, set: HistoryExerciseSet): Double {
        if (!isCardio(exercise) || !isValidWorkingSet(exercise, set)) return 0.0
        return when (tracking(exercise)) {
            TrackingMode.DURATION, TrackingMode.WEIGHTED_DURATION, TrackingMode.DURATION_DISTANCE -> set.reps
            // Historical cardio sets also used reps for elapsed seconds before metadata was projected.
            TrackingMode.UNKNOWN -> if (exercise.trackingType.isNullOrBlank()) set.reps else 0.0
            else -> 0.0
        }
    }

    fun isCardio(exercise: HistoryExercise): Boolean =
        exercise.trackingType.key() == "cardio" || exercise.category.key() in setOf("cardio", "conditioning")

    private fun String?.key(): String = orEmpty().trim().lowercase(Locale.ROOT)
}
