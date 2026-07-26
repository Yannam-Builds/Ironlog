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
            listOf("Graphite", "Iron", "Steel", "Titanium", "founder"),
            badges,
        )
    }
}
