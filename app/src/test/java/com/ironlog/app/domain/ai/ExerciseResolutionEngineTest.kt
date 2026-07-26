package com.ironlog.app.domain.ai

import com.ironlog.app.data.model.LegacyExerciseShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseResolutionEngineTest {
    @Test
    fun exactNameIsMatchedAtHighConfidence() {
        val lib = listOf(ex("1", "Ab Crunch Machine"), ex("2", "Cable Lateral Raise"))
        val result = ExerciseResolutionEngine.resolve("Ab Crunch Machine", lib)
        assertEquals(ResolutionStatus.MATCHED, result.status)
        assertNotNull(result.matched)
        assertTrue(result.confidence >= 95)
    }

    @Test
    fun typoNameResolvesToBestMatch() {
        val lib = listOf(ex("1", "Ab Crunch Machine"), ex("2", "Cable Lateral Raise"))
        val result = ExerciseResolutionEngine.resolve("ab crnch machne", lib)
        assertNotEquals(ResolutionStatus.UNRESOLVED, result.status)
        assertNotNull(result.matched)
        assertEquals("Ab Crunch Machine", result.matched?.name)
    }

    @Test
    fun lowSimilarityIsUnresolved() {
        val lib = listOf(ex("1", "Barbell Bench Press"), ex("2", "Seated Cable Row"))
        val result = ExerciseResolutionEngine.resolve("Underwater dolphin kick", lib)
        assertEquals(ResolutionStatus.UNRESOLVED, result.status)
        assertTrue(result.confidence < 70)
    }

    private fun ex(id: String, name: String) = LegacyExerciseShape(
        id = id,
        exerciseId = id,
        name = name,
        primaryMuscles = listOf("Chest"),
        primaryMuscle = "Chest",
        secondaryMuscles = emptyList(),
        equipment = "Machine",
        category = "strength",
        trackingType = "weight_reps",
        isCustom = false,
        aliases = emptyList(),
        isBodyweight = false,
        movementPattern = "isolation",
        difficulty = "beginner",
        apparatus = null,
        equipmentDetail = null,
        sourceTags = emptyList(),
        notes = "",
    )
}
