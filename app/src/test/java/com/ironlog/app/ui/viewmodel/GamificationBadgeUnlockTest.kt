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
    }

    @Test
    fun `goal modes accumulate across settings changes`() {
        val used = mergedGoalModes(setOf("strength", "hypertrophy"), "GENERAL_FITNESS")

        assertEquals(setOf("strength", "hypertrophy", "general_fitness"), used)
    }

    @Test
    fun `legacy and unknown onboarding goal modes cannot inflate multiclass progress`() {
        val used = mergedGoalModes(setOf("strength", "PERFORMANCE", "bad-value"), "ENDURANCE")

        assertEquals(setOf("strength", "general_fitness"), used)
    }

    @Test
    fun `known badges without current proof are revoked while legacy awards remain`() {
        val badges = mergedUnlockedBadges(
            existingCsv = "Graphite,ai_activated,founder",
            currentGrade = IronGrade.GRAPHITE,
            appBadges = emptySet(),
        )

        assertEquals(listOf("Graphite", "founder"), badges)
    }
}
