package com.ironlog.app.ui.screens

import com.ironlog.app.domain.gamification.DailyProofStatus
import com.ironlog.app.ui.screens.home.showDailyProofAction
import org.junit.Assert.*
import org.junit.Test

class HomeProofHierarchyTest {
    @Test fun `daily proof keeps setup and recovery actions without duplicating workout resume`() {
        assertFalse(showDailyProofAction(DailyProofStatus.ACTIVE_WORKOUT))
        assertFalse(showDailyProofAction(DailyProofStatus.TRAIN_TODAY))
        assertFalse(showDailyProofAction(DailyProofStatus.FIRST_PROOF))
        assertTrue(showDailyProofAction(DailyProofStatus.SETUP))
        assertTrue(showDailyProofAction(DailyProofStatus.RECOVER_SMART))
    }
}
