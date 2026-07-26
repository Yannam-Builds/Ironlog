package com.ironlog.app.domain.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpEngineTest {

    private val engine = XpEngine()

    @Test fun `level 1 requires 125 XP`() {
        assertEquals(125L, engine.xpForLevel(1))
    }

    @Test fun `level 2 requires 500 XP`() {
        assertEquals(500L, engine.xpForLevel(2))
    }

    @Test fun `level 10 requires 12500 XP`() {
        assertEquals(12500L, engine.xpForLevel(10))
    }

    @Test fun `xp curve is strictly increasing`() {
        val xps = (1..20).map { engine.xpForLevel(it) }
        for (i in 1 until xps.size) {
            assertTrue("Level ${i + 1} xp should exceed level $i xp", xps[i] > xps[i - 1])
        }
    }

    @Test fun `rank E for level 1`() {
        assertEquals("E", engine.rankForLevel(1))
    }

    @Test fun `rank D for level 11`() {
        assertEquals("D", engine.rankForLevel(11))
    }

    @Test fun `rank C for level 21`() {
        assertEquals("C", engine.rankForLevel(21))
    }

    @Test fun `rank B for level 36`() {
        assertEquals("B", engine.rankForLevel(36))
    }

    @Test fun `rank A for level 51`() {
        assertEquals("A", engine.rankForLevel(51))
    }

    @Test fun `rank S for level 71`() {
        assertEquals("S", engine.rankForLevel(71))
    }

    @Test fun `rank Apex for level 91`() {
        assertEquals("Apex", engine.rankForLevel(91))
    }

    @Test fun `xpForAction RECOVERY_CIRCUIT returns positive value`() {
        assertTrue(engine.xpForAction(XpAction.RECOVERY_CIRCUIT) > 0)
    }
}
