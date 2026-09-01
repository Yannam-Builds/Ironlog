package com.ironlog.app.domain.training

import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import org.junit.Assert.*
import org.junit.Test

class TrainingSetPolicyTest {
    @Test fun `tracking distinguishes added load and preserves unknown`() {
        assertEquals(TrackingMode.ADDED_LOAD_REPS, TrainingSetPolicy.tracking(ex("bodyweight_plus_weight_reps")))
        assertEquals(TrackingMode.ADDED_LOAD_REPS, TrainingSetPolicy.tracking(ex("weight_reps").copy(isBodyweight = true)))
        assertEquals(TrackingMode.WEIGHTED_DURATION, TrainingSetPolicy.tracking(ex("duration_weight")))
        assertEquals(TrackingMode.UNKNOWN, TrainingSetPolicy.tracking(ex(null)))
        assertEquals(TrackingMode.UNKNOWN, TrainingSetPolicy.tracking(ex("unrecognized")))
    }

    @Test fun `working sets require finite positive performed reps and valid required load`() {
        val strength = ex("weight_reps").copy(requiresExternalLoad = true)
        listOf(set(100.0, 0.0), set(100.0, -1.0), set(0.0, 8.0), set(-1.0, 8.0),
            set(Double.NaN, 8.0), set(100.0, Double.POSITIVE_INFINITY)).forEach {
            assertFalse(it.toString(), TrainingSetPolicy.isValidWorkingSet(strength, it))
        }
        assertTrue(TrainingSetPolicy.isValidWorkingSet(strength, set(100.0, 8.0)))
    }

    @Test fun `warmup flag and legacy label both exclude even with other techniques`() {
        val strength = ex("weight_reps")
        assertFalse(TrainingSetPolicy.isValidWorkingSet(strength, set().copy(type = "WaRmUp")))
        assertFalse(TrainingSetPolicy.isValidWorkingSet(strength, set().copy(isWarmup = true, isAmrap = true, toFailure = true)))
        assertTrue(TrainingSetPolicy.isValidWorkingSet(strength, set().copy(isAmrap = true, toFailure = true)))
    }

    @Test fun `completed parent legacy set remains valid without a completion timestamp`() {
        assertTrue(TrainingSetPolicy.isValidWorkingSet(ex(null), set().copy(completedAt = null)))
        assertNotNull(TrainingSetPolicy.estimatedOneRm(ex(null), set()))
        assertNull(TrainingSetPolicy.estimatedOneRm(ex("unknown"), set()))
    }

    @Test fun `added load is external volume but never invented system mass one rm`() {
        val exercise = ex("bodyweight_plus_weight_reps")
        assertTrue(TrainingSetPolicy.isValidWorkingSet(exercise, set(0.0, 8.0)))
        assertEquals(160.0, TrainingSetPolicy.externalLoadVolume(exercise, set(20.0, 8.0)), 0.0)
        assertNull(TrainingSetPolicy.estimatedOneRm(exercise, set(20.0, 8.0)))
        assertEquals(0.0, TrainingSetPolicy.externalLoadVolume(ex("assisted_bodyweight"), set(20.0, 8.0)), 0.0)
    }

    @Test fun `weighted duration has neither reps volume nor strength estimate nor automatic cardio credit`() {
        val exercise = ex("duration_weight")
        assertTrue(TrainingSetPolicy.isValidWorkingSet(exercise, set(20.0, 60.0)))
        assertEquals(0.0, TrainingSetPolicy.externalLoadVolume(exercise, set(20.0, 60.0)), 0.0)
        assertNull(TrainingSetPolicy.estimatedOneRm(exercise, set(20.0, 60.0)))
        assertEquals(0.0, TrainingSetPolicy.cardioSeconds(exercise, set(20.0, 60.0)), 0.0)
    }

    @Test fun `cardio duration uses seconds and excludes warmups without inventing distance`() {
        val exercise = ex("duration_distance").copy(category = "cardio")
        assertEquals(600.0, TrainingSetPolicy.cardioSeconds(exercise, set(0.0, 600.0)), 0.0)
        assertEquals(0.0, TrainingSetPolicy.cardioSeconds(exercise, set(0.0, 600.0).copy(isWarmup = true)), 0.0)
        assertEquals(0.0, TrainingSetPolicy.externalLoadVolume(exercise, set(5.0, 600.0)), 0.0)
        assertEquals(0.0, TrainingSetPolicy.cardioSeconds(ex("weight_reps").copy(name = "Crunch"), set()), 0.0)
        assertEquals(0.0, TrainingSetPolicy.cardioSeconds(ex("duration").copy(name = "Run hold"), set()), 0.0)
        assertEquals(0.0, TrainingSetPolicy.cardioSeconds(ex("duration_distance").copy(category = "strength"), set()), 0.0)
    }

    @Test fun `one rm has a documented rep ceiling and never emits infinity`() {
        val exercise = ex("weight_reps")
        assertEquals(100.0, TrainingSetPolicy.estimatedOneRm(exercise, set(100.0, 1.0))!!, 0.0)
        assertEquals(200.0, TrainingSetPolicy.estimatedOneRm(exercise, set(100.0, 30.0))!!, 0.0)
        assertNull(TrainingSetPolicy.estimatedOneRm(exercise, set(100.0, 31.0)))
        assertNull(TrainingSetPolicy.estimatedOneRm(exercise, set(Double.MAX_VALUE, 30.0)))
        assertEquals(0.0, TrainingSetPolicy.externalLoadVolume(exercise, set(Double.MAX_VALUE, 30.0)), 0.0)
    }

    private fun ex(type: String?) = HistoryExercise(name = "Synthetic", trackingType = type)
    private fun set(weight: Double = 100.0, reps: Double = 8.0) = HistoryExerciseSet(weight = weight, reps = reps)
}
