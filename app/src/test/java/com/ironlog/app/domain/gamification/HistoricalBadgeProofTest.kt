package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoricalBadgeProofTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-09-01T12:00:00Z")
    private fun session(id: String, date: String) = HistoryEntry(id, date, duration = 1800, exercises = listOf(HistoryExercise(name = "Bench", sets = List(8) { HistoryExerciseSet(weight = 60.0, reps = 8.0) })))
    @Test fun `old native and imported streak unlock at the same historical proof time`() {
        val history = (1..3).map { session("w$it", "2026-08-0${it}T12:00:00Z") }
        val unlocks = historicalBadgeUnlocks(history, now, zone)
        assertEquals(Instant.parse("2026-08-03T12:00:00Z").toEpochMilli(), unlocks["streak_3"])
        assertEquals(unlocks, historicalBadgeUnlocks(history.reversed().map { it.copy(imported = true) }, now, zone))
    }
    @Test fun `same local day cannot multiply streak and future proof earns nothing`() {
        val history = listOf(session("a", "2026-08-01T20:00:00Z"), session("b", "2026-08-02T04:00:00Z"), session("c", "2026-08-03T04:00:00Z"), session("future", "2026-09-02T12:00:00Z"))
        assertFalse(historicalBadgeUnlocks(history, now, zone).containsKey("streak_3"))
    }
    @Test fun `local monday proof belongs to the new ISO week`() {
        val history = listOf(session("monday", "2026-08-30T20:00:00Z"))
        assertEquals(1, creditedSessionsThisWeek(history, now, zone))
        assertEquals(0, creditedSessionsThisWeek(history, now, ZoneId.of("America/Los_Angeles")))
    }
    @Test fun `badge merge prefers durable timestamps then history over a legacy refresh time`() {
        val earned = mapOf("streak_3" to 100L, "first_workout" to 80L)
        assertEquals(mapOf("streak_3" to 100L, "first_workout" to 70L), mergeBadgeUnlockTimes(
            listOf("streak_3", "first_workout"), mapOf("first_workout" to 70L), earned, 999L))
    }
    @Test fun `old PR run survives later non PR session and colon workout IDs`() {
        val history = (1..6).map { session("import:w$it", "2026-08-0${it}T12:00:00Z") }
        val events = (2..5).map { IronLedgerEvent(sourceId = "import:w$it:bench", kind = "pr", title = "PR", detail = "", xp = 35, occurredAt = history[it - 1].date, trust = 1.0) }
        val unlocks = historicalPrBadgeUnlocks(history, events, now, zone)
        assertEquals(Instant.parse(history[4].date).toEpochMilli(), unlocks["progressive_streak"])
        assertEquals(Instant.parse(history[1].date).toEpochMilli(), unlocks["first_pr"])
    }
    @Test fun `latest badge ignores unearned cache keys and resolves ties deterministically`() {
        assertEquals("b", latestEarnedBadgeId("a,b", "{\"a\":100,\"b\":100,\"rogue\":999}"))
    }
}
