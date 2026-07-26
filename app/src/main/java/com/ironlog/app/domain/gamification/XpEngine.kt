package com.ironlog.app.domain.gamification

import kotlin.math.roundToInt

enum class XpAction {
    RECOVERY_CIRCUIT,
}

class XpEngine {

    /**
     * XP required to reach [level] (1-indexed).
     * Curve: 125.0 * level^2, coerced to at least 125L.
     */
    fun xpForLevel(level: Int): Long =
        (125.0 * level.toDouble() * level.toDouble()).roundToInt().toLong().coerceAtLeast(125L)

    /**
     * Legacy grade bridge retained for old cached profiles.
     * Canonical Iron Ledger grades are rebuilt by [IronLedgerEngine].
     */
    fun rankForLevel(level: Int): String = when {
        level >= 91 -> "Apex"
        level >= 71 -> "S"
        level >= 51 -> "A"
        level >= 36 -> "B"
        level >= 21 -> "C"
        level >= 11 -> "D"
        else -> "E"
    }

    fun xpForAction(action: XpAction): Int = when (action) {
        XpAction.RECOVERY_CIRCUIT -> 25
    }

    fun levelFromTotalXp(totalXp: Long): Int {
        var level = 1
        var remaining = totalXp
        while (level < 100 && remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level)
            level++
        }
        return level
    }

    fun xpInCurrentLevel(totalXp: Long): Long {
        val level = levelFromTotalXp(totalXp)
        val spent = (1 until level).sumOf { xpForLevel(it) }
        return (totalXp - spent).coerceAtLeast(0L)
    }
}
