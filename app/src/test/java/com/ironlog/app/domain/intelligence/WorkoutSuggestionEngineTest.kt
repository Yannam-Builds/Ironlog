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

    @Test fun `hip abduction maps to legs instead of core`() {
        assertEquals("Legs", engine.regionForExercise("Cable Hip Abduction"))
    }

    @Test fun `lat token does not capture lateral raise`() {
        assertEquals("Shoulders", engine.regionForExercise("Cable Lateral Raise"))
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

    @Test fun `low mapping coverage pulls confidence toward neutral`() {
        val readiness = mapOf("Push" to 1.0)
        val score = engine.scoreDay(readiness, listOf("Bench Press", "Juggling", "Yoga Flow", "Unknown Drill"))
        assertTrue(score in 0.60..0.65)
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

    @Test fun `leg curls are not arm work`() {
        listOf("Seated Leg Curl", "Lying Leg Curls", "Nordic Hamstring Curl").forEach {
            assertEquals(it, "Legs", engine.regionForExercise(it))
        }
    }

    @Test fun `leg raises are core work`() {
        listOf("Hanging Leg Raise", "Lying Leg Raises", "Hanging Knee Raise").forEach {
            assertEquals(it, "Core", engine.regionForExercise(it))
        }
    }

    @Test fun `common plural exercise names retain their region`() {
        mapOf("Dips" to "Push", "Dumbbell Shrugs" to "Pull", "Biceps Curls" to "Arms",
            "Triceps Extensions" to "Arms", "Lateral Raises" to "Shoulders").forEach { (name, region) ->
            assertEquals(name, region, engine.regionForExercise(name))
        }
    }

    @Test fun `fatigued leg curls cannot be promoted by fresh arms`() {
        assertEquals(1, engine.suggestDayIndex(
            mapOf("Legs" to 0.1, "Arms" to 1.0, "Pull" to 0.8),
            listOf(listOf("Seated Leg Curl"), listOf("Barbell Row")),
        ))
    }

    @Test fun `nonfinite readiness is missing evidence not a winning score`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { invalid ->
            assertEquals(0.5, engine.scoreDay(mapOf("Push" to invalid), listOf("Bench Press")), 0.001)
            assertEquals(1, engine.suggestDayIndex(
                mapOf("Push" to invalid, "Pull" to 0.9),
                listOf(listOf("Bench Press"), listOf("Barbell Row")),
            ))
        }
    }

    @Test fun `readiness clamps each valid region before averaging`() {
        assertEquals(0.5, engine.scoreDay(
            mapOf("Push" to 10.0, "Pull" to -1.0), listOf("Bench Press", "Barbell Row"),
        ), 0.001)
    }

    @Test fun `blurb confidence counts usable readiness rather than name matching`() {
        val blurb = engine.recommendationBlurb(mapOf("Push" to Double.NaN), "Push Day", listOf("Bench Press"))
        assertTrue(blurb.contains("Limited", ignoreCase = true))
        assertTrue(!blurb.contains("well recovered"))
    }

    @Test fun `blurb does not certify recovery without exercise coverage`() {
        val blurb = engine.recommendationBlurb(mapOf("Push" to 1.0), "Unknown Day")
        assertTrue(!blurb.contains("well recovered"))
        assertTrue(blurb.contains("Limited", ignoreCase = true))
    }
}
