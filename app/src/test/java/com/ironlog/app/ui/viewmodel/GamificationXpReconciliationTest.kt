package com.ironlog.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationXpReconciliationTest {
    @Test
    fun `refresh keeps bonus xp above canonical ledger floor`() {
        val total = reconciledLedgerXp(
            cachedTotalXp = 125L,
            ledgerTotalXp = 100L,
        )

        assertEquals(125L, total)
    }

    @Test
    fun `refresh raises stale cached xp to canonical ledger floor`() {
        val total = reconciledLedgerXp(
            cachedTotalXp = 25L,
            ledgerTotalXp = 100L,
        )

        assertEquals(100L, total)
    }
}
