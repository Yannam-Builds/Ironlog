package com.ironlog.app.ui.viewmodel

import com.ironlog.app.domain.gamification.IronGrade
import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationBadgeUnlockTest {
    @Test
    fun `grade progression unlocks canonical grade badges in order`() {
        val badges = unlockedBadgesAfterGrade(
            existingCsv = "",
            currentGrade = IronGrade.STEEL,
        )

        assertEquals(
            listOf("Graphite", "Iron", "Steel"),
            badges,
        )
    }

    @Test
    fun `existing non grade badges are preserved while grade badges are upgraded`() {
        val badges = unlockedBadgesAfterGrade(
            existingCsv = "Graphite,founder",
            currentGrade = IronGrade.TITANIUM,
        )

        assertEquals(
            listOf("Graphite", "founder", "Iron", "Steel", "Titanium"),
            badges,
        )
    }

    @Test
    fun `cloud badge requires selected cloud mode and complete credentials`() {
        assertEquals(false, isCloudAiBadgeActive("built_in", "https://api.example", "model", "key"))
        assertEquals(false, isCloudAiBadgeActive("cloud_ai", "https://api.example", "model", ""))
        assertEquals(true, isCloudAiBadgeActive("cloud_ai", "https://api.example", "model", "key"))
        assertEquals(true, isCloudAiBadgeActive("AUTO", "https://api.example", "model", "key"))
    }

    @Test
    fun `goal modes accumulate only when a different credited workout supplies evidence`() {
        val unchanged = goalModeEvidenceAfterProof(
            existing = setOf("strength"),
            current = "hypertrophy",
            lastCreditedWorkoutId = "workout-1",
            latestCreditedWorkoutId = "workout-1",
        )
        val advanced = goalModeEvidenceAfterProof(
            existing = unchanged.goalModes,
            current = "GENERAL_FITNESS",
            lastCreditedWorkoutId = unchanged.lastCreditedWorkoutId,
            latestCreditedWorkoutId = "workout-2",
        )

        assertEquals(setOf("strength"), unchanged.goalModes)
        assertEquals("workout-1", unchanged.lastCreditedWorkoutId)
        assertEquals(setOf("strength", "general_fitness"), advanced.goalModes)
        assertEquals("workout-2", advanced.lastCreditedWorkoutId)
    }

    @Test
    fun `legacy and unknown onboarding goal modes cannot inflate multiclass progress`() {
        val used = mergedGoalModes(setOf("strength", "PERFORMANCE", "bad-value"), "ENDURANCE")

        assertEquals(setOf("strength", "general_fitness"), used)
    }

    @Test
    fun `no credited workout cannot add goal mode evidence`() {
        val evidence = goalModeEvidenceAfterProof(
            existing = setOf("strength"),
            current = "hypertrophy",
            lastCreditedWorkoutId = null,
            latestCreditedWorkoutId = null,
        )

        assertEquals(setOf("strength"), evidence.goalModes)
        assertEquals(null, evidence.lastCreditedWorkoutId)
    }

    @Test
    fun `earned badges remain unlocked when current conditions later change`() {
        val badges = mergedUnlockedBadges(
            existingCsv = "Graphite,ai_activated,founder",
            currentGrade = IronGrade.GRAPHITE,
            appBadges = emptySet(),
        )

        assertEquals(listOf("Graphite", "ai_activated", "founder"), badges)
    }

    @Test
    fun `obsolete s rank duplicate is removed while real earned badges remain`() {
        val badges = mergedUnlockedBadges(
            existingCsv = "Graphite,s_rank,first_workout",
            currentGrade = IronGrade.GRAPHITE,
            appBadges = emptySet(),
        )

        assertEquals(listOf("Graphite", "first_workout"), badges)
    }

    @Test
    fun `calibration only refresh reconstructs baseline supported badges`() {
        assertEquals(
            setOf("first_workout", "workouts_10", "first_plan"),
            combinedBaselineAndEvidenceBadgeIds(
                baselineSupported = setOf("first_workout", "workouts_10"),
                evaluated = setOf("first_plan"),
                historical = emptySet(),
            ),
        )
    }
}
