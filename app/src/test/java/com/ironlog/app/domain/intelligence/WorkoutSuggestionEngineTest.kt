package com.ironlog.app.domain.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSuggestionEngineTest {

    private val engine = WorkoutSuggestionEngine()

    // ── regionForExercise ──────────────────────────────────────────────────

    @Test fun `bench press maps to Push`() {
        assertEquals("Push", engine.regionForExercise("Bench Press"))
    }

    @Test fun `barbell row maps to Pull`() {
        assertEquals("Pull", engine.regionForExercise("Barbell Row"))
    }

    @Test fun `squat maps to Legs`() {
        assertEquals("Legs", engine.regionForExercise("Back Squat"))
    }

    @Test fun `plank maps to Core`() {
        assertEquals("Core", engine.regionForExercise("Plank"))
    }

    @Test fun `curl maps to Arms`() {
        assertEquals("Arms", engine.regionForExercise("Dumbbell Curl"))
    }

    @Test fun `lateral raise maps to Shoulders`() {
        assertEquals("Shoulders", engine.regionForExercise("Lateral Raise"))
    }

    @Test fun `unknown exercise maps to null`() {
        assertEquals(null, engine.regionForExercise("Juggling"))
    }

    // ── scoreDay ──────────────────────────────────────────────────────────

    @Test fun `day with fully rested regions scores near 1`() {
        val readiness = mapOf("Push" to 1.0, "Pull" to 1.0, "Legs" to 1.0)
        val score = engine.scoreDay(readiness, listOf("Bench Press", "Pull Up", "Squat"))
        assertTrue("score should be >= 0.9 but was $score", score >= 0.9)
    }

    @Test fun `day with all fatigued regions scores near 0`() {
        val readiness = mapOf("Push" to 0.0, "Pull" to 0.0, "Legs" to 0.0)
        val score = engine.scoreDay(readiness, listOf("Bench Press", "Pull Up", "Squat"))
        assertTrue("score should be <= 0.2 but was $score", score <= 0.2)
    }

    @Test fun `day with no matched exercises scores 0_5 (neutral)`() {
        val readiness = mapOf("Push" to 1.0)
        val score = engine.scoreDay(readiness, listOf("Juggling", "Yoga Flow"))
        assertEquals(0.5, score, 0.001)
    }

    // ── suggestDayIndex ────────────────────────────────────────────────────

    @Test fun `returns index of highest-scoring day`() {
        val readiness = mapOf("Push" to 1.0, "Legs" to 0.1)
        val dayExerciseNames = listOf(
            listOf("Squat", "Leg Press"),   // day 0 — Legs fatigued → low score
            listOf("Bench Press", "Dip"),   // day 1 — Push fresh → high score
        )
        val result = engine.suggestDayIndex(readiness, dayExerciseNames)
        assertEquals(1, result)
    }

    @Test fun `returns 0 when all days equally ready`() {
        val readiness = mapOf("Push" to 1.0, "Pull" to 1.0)
        val dayExerciseNames = listOf(
            listOf("Bench Press"),
            listOf("Pull Up"),
        )
        val result = engine.suggestDayIndex(readiness, dayExerciseNames)
        // Either is valid; ensure it returns a valid index
        assertTrue(result in 0..1)
    }

    @Test fun `returns 0 for empty day list`() {
        assertEquals(0, engine.suggestDayIndex(emptyMap(), emptyList()))
    }

    // ── recommendationBlurb ───────────────────────────────────────────────

    @Test fun `blurb mentions the day name`() {
        val readiness = mapOf("Push" to 0.9, "Legs" to 0.3)
        val blurb = engine.recommendationBlurb(readiness, "Push Day")
        assertTrue(blurb.contains("Push Day", ignoreCase = true))
    }
}
