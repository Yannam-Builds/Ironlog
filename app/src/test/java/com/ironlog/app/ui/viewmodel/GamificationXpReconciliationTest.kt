package com.ironlog.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationXpReconciliationTest {
    @Test
    fun `refresh adds durable bonus xp to canonical workout proof`() {
        val total = totalXpFromLedger(ledgerTotalXp = 100L, bonusXp = 25L)

        assertEquals(125L, total)
    }

    @Test
    fun `deleting workout proof lowers total while preserving bonus xp`() {
        val beforeDelete = totalXpFromLedger(ledgerTotalXp = 100L, bonusXp = 25L)
        val afterDelete = totalXpFromLedger(ledgerTotalXp = 0L, bonusXp = 25L)

        assertEquals(125L, beforeDelete)
        assertEquals(25L, afterDelete)
    }

    @Test
    fun `negative imported bonus cannot lower canonical xp`() {
        assertEquals(100L, totalXpFromLedger(ledgerTotalXp = 100L, bonusXp = -50L))
    }

    @Test
    fun `recovery proof can only be recorded once per ISO week`() {
        assertEquals(true, canRecordRecoveryCircuit(emptyMap(), "2026-W31", hasDurableEvent = false))
        assertEquals(false, canRecordRecoveryCircuit(mapOf("2026-W31" to 1), "2026-W31", hasDurableEvent = false))
        assertEquals(false, canRecordRecoveryCircuit(emptyMap(), "2026-W31", hasDurableEvent = true))
    }
}
