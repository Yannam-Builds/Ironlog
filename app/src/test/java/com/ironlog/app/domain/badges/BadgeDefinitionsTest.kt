package com.ironlog.app.domain.badges

import org.junit.Assert.*
import org.junit.Test

class BadgeDefinitionsTest {

    @Test fun `all badges have unique ids`() {
        val ids = BadgeDefinitions.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `all four tiers are present`() {
        val tiers = BadgeDefinitions.all.map { it.tier }.toSet()
        assertEquals(setOf(BadgeTier.BRONZE, BadgeTier.SILVER, BadgeTier.GOLD, BadgeTier.BLUE), tiers)
    }

    @Test fun `first_workout unlocks after 1 workout`() {
        val stats = AppStats(totalWorkouts = 1)
        assertTrue("first_workout" in BadgeDefinitions.evaluate(stats))
    }

    @Test fun `no badges unlock for a fresh user`() {
        val stats = AppStats()
        assertTrue(BadgeDefinitions.evaluate(stats).isEmpty())
    }

    @Test fun `streak_3 does not unlock at streak 2`() {
        val stats = AppStats(currentStreak = 2)
        assertFalse("streak_3" in BadgeDefinitions.evaluate(stats))
    }

    @Test fun `streak_3 unlocks at streak 3`() {
        val stats = AppStats(currentStreak = 3)
        assertTrue("streak_3" in BadgeDefinitions.evaluate(stats))
    }

    @Test fun `s_rank badge unlocks only for S rank`() {
        assertFalse("s_rank" in BadgeDefinitions.evaluate(AppStats(currentRank = "A")))
        assertTrue("s_rank" in BadgeDefinitions.evaluate(AppStats(currentRank = "S")))
    }

    @Test fun `evaluate returns correct set for multiple stats`() {
        val stats = AppStats(totalWorkouts = 1, usedRestTimer = true, createdPlan = true)
        val earned = BadgeDefinitions.evaluate(stats)
        assertTrue("first_workout" in earned)
        assertTrue("first_rest_timer" in earned)
        assertTrue("first_plan" in earned)
        assertFalse("workouts_10" in earned)
    }
}
