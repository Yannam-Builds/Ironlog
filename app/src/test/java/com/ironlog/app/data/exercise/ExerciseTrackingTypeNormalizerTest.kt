package com.ironlog.app.data.exercise

import com.ironlog.app.util.ExerciseTrackingTypeNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseTrackingTypeNormalizerTest {
    @Test
    fun `rep based strength exercises override bad duration seed data`() {
        assertEquals(
            "weight_reps",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Ab Crunch Machine",
                category = "strength",
                equipment = "Machine",
                explicitTrackingType = "duration_distance",
            ),
        )
        assertEquals(
            "weight_reps",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Cable Crunch",
                category = "strength",
                equipment = "Cable",
                explicitTrackingType = "duration_distance",
            ),
        )
        assertEquals(
            "weight_reps",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Seated Cable Row",
                category = "strength",
                equipment = "Cable",
                explicitTrackingType = "duration_distance",
            ),
        )
        assertEquals(
            "weight_reps",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Dumbbell Seated Box Jump",
                category = "strength",
                equipment = "Dumbbell",
                explicitTrackingType = "duration",
            ),
        )
    }

    @Test
    fun `time and distance exercises stay time based`() {
        assertEquals(
            "duration",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Front Plank",
                category = "mobility",
                equipment = "Bodyweight",
                explicitTrackingType = null,
            ),
        )
        assertEquals(
            "duration",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Hip 90-90 Stretch",
                category = "mobility",
                equipment = "Bodyweight",
                explicitTrackingType = "duration",
            ),
        )
        assertEquals(
            "duration",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Standing Hamstring and Calf Stretch",
                category = "mobility",
                equipment = "Bodyweight",
                explicitTrackingType = "duration",
            ),
        )
        assertEquals(
            "duration_distance",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Treadmill Walk",
                category = "cardio",
                equipment = "Machine",
                explicitTrackingType = null,
            ),
        )
        assertEquals(
            "duration_distance",
            ExerciseTrackingTypeNormalizer.normalize(
                name = "Ski Erg",
                category = "cardio",
                equipment = "Conditioning",
                explicitTrackingType = "duration_distance",
            ),
        )
    }
}
