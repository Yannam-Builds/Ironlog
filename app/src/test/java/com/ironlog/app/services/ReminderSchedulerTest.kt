package com.ironlog.app.services

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulerTest {

    @Test
    fun `daily reminder failures have a bounded retry budget`() {
        assertTrue(dailyReminderShouldRetry(runAttemptCount = 0))
        assertTrue(dailyReminderShouldRetry(runAttemptCount = 1))
        assertFalse(dailyReminderShouldRetry(runAttemptCount = 2))
        assertFalse(dailyReminderShouldRetry(runAttemptCount = Int.MAX_VALUE))
    }

    @Test
    fun `only the current persisted schedule generation may deliver or append`() {
        assertFalse(reminderScheduleGenerationMatches(requestGeneration = 0L, currentGeneration = 0L))
        assertFalse(reminderScheduleGenerationMatches(requestGeneration = 4L, currentGeneration = 5L))
        assertTrue(reminderScheduleGenerationMatches(requestGeneration = 5L, currentGeneration = 5L))
    }

    @Test
    fun `legacy periodic schedule version requires a one time replacement`() {
        assertTrue(reminderScheduleNeedsMigration(storedVersion = null))
        assertTrue(reminderScheduleNeedsMigration(storedVersion = CURRENT_REMINDER_SCHEDULE_VERSION - 1))
        assertFalse(reminderScheduleNeedsMigration(storedVersion = CURRENT_REMINDER_SCHEDULE_VERSION))
    }

    @Test
    fun `invalidating a schedule advances a positive generation`() {
        assertEquals(1L, nextReminderScheduleGeneration(current = null, invalidate = false))
        assertEquals(8L, nextReminderScheduleGeneration(current = 7L, invalidate = true))
        assertEquals(7L, nextReminderScheduleGeneration(current = 7L, invalidate = false))
        assertEquals(1L, nextReminderScheduleGeneration(current = Long.MAX_VALUE, invalidate = true))
    }

    @Test
    fun `schedule signature changes with timezone or any configured boundary`() {
        val base = reminderScheduleSignature("Asia/Kolkata", 8 * 60, 22 * 60, 8 * 60)
        assertFalse(base == reminderScheduleSignature("America/Los_Angeles", 8 * 60, 22 * 60, 8 * 60))
        assertFalse(base == reminderScheduleSignature("Asia/Kolkata", 9 * 60, 22 * 60, 8 * 60))
        assertFalse(base == reminderScheduleSignature("Asia/Kolkata", 8 * 60, 21 * 60, 8 * 60))
        assertFalse(base == reminderScheduleSignature("Asia/Kolkata", 8 * 60, 22 * 60, 7 * 60))
    }
    @Test
    fun `reminder occurrence identity is deterministic and generation scoped`() {
        val first = reminderOccurrenceWorkName(7L, 1_788_200_000_000L)
        assertEquals(first, reminderOccurrenceWorkName(7L, 1_788_200_000_000L))
        assertFalse(first == reminderOccurrenceWorkName(8L, 1_788_200_000_000L))
        assertFalse(first == reminderOccurrenceWorkName(7L, 1_788_286_400_000L))
    }

    @Test
    fun `occurrence remains valid through the bounded delivery window`() {
        val target = 1_788_200_000_000L

        assertFalse(reminderOccurrenceIsExpired(target, target))
        assertFalse(reminderOccurrenceIsExpired(target, target + MAX_REMINDER_LATENESS_MS))
    }

    @Test
    fun `occurrence is expired immediately after the bounded delivery window`() {
        val target = 1_788_200_000_000L

        assertTrue(reminderOccurrenceIsExpired(target, target + MAX_REMINDER_LATENESS_MS + 1L))
        assertTrue(reminderOccurrenceIsExpired(target, target + Duration.ofDays(3).toMillis()))
    }

    @Test
    fun `missing occurrence is rejected while future occurrence is not stale`() {
        assertTrue(reminderOccurrenceIsExpired(targetEpochMillis = 0L, nowEpochMillis = 100L))
        assertFalse(reminderOccurrenceIsExpired(targetEpochMillis = 200L, nowEpochMillis = 100L))
    }

    @Test
    fun `clock rollback still schedules strictly after the running occurrence`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val rolledBackNow = ZonedDateTime.of(2026, 9, 1, 7, 0, 0, 0, zone)
        val runningOccurrence = ZonedDateTime.of(2026, 9, 1, 8, 0, 0, 0, zone)
            .toInstant().toEpochMilli()

        val next = computeReminderTargetStrictlyAfter(
            now = rolledBackNow,
            hour = 8,
            minute = 0,
            afterOccurrenceEpochMillis = runningOccurrence,
        )

        assertEquals(ZonedDateTime.of(2026, 9, 2, 8, 0, 0, 0, zone), next)
        assertTrue(next.toInstant().toEpochMilli() > runningOccurrence)
    }

    @Test
    fun `next reminder preserves local time across spring daylight saving change`() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = ZonedDateTime.of(2026, 3, 7, 8, 30, 0, 0, zone)

        val delay = computeReminderDelayMs(now, 8, 0)

        assertEquals(Duration.ofHours(22).plusMinutes(30).toMillis(), delay)
    }

    @Test
    fun `next reminder preserves local time across fall daylight saving change`() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = ZonedDateTime.of(2026, 10, 31, 8, 30, 0, 0, zone)

        val delay = computeReminderDelayMs(now, 8, 0)

        assertEquals(Duration.ofHours(24).plusMinutes(30).toMillis(), delay)
    }

    @Test
    fun `quiet hour defaults agree with settings`() {
        assertEquals(22 * 60, NotificationKeys.DEFAULT_QUIET_START_MINUTES)
        assertEquals(8 * 60, NotificationKeys.DEFAULT_QUIET_END_MINUTES)
        assertEquals(8 * 60, NotificationKeys.DEFAULT_REMINDER_MINUTES)
    }

    @Test
    fun `a reminder inside overnight quiet hours waits until quiet hours end`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 9, 1, 21, 0, 0, 0, zone)

        val delay = computeReminderDelayMs(now, 23, 0, 22 * 60, 8 * 60)

        assertEquals(Duration.ofHours(11).toMillis(), delay)
    }

    @Test
    fun `a reminder inside same-day quiet hours waits for the end boundary`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 9, 1, 10, 0, 0, 0, zone)

        val delay = computeReminderDelayMs(now, 13, 0, 12 * 60, 14 * 60)

        assertEquals(Duration.ofHours(4).toMillis(), delay)
    }

    @Test
    fun `a worker delayed into quiet hours retries at the next quiet end`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 9, 1, 23, 30, 0, 0, zone)

        val delay = computeQuietHoursEndDelayMs(now, 8 * 60)

        assertEquals(Duration.ofHours(8).plusMinutes(30).toMillis(), delay)
    }

    @Test
    fun `past overnight reminder is still held to this mornings quiet end`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 9, 1, 7, 30, 0, 0, zone)

        val delay = computeReminderDelayMs(now, 7, 0, 22 * 60, 8 * 60)

        assertEquals(Duration.ofMinutes(30).toMillis(), delay)
    }

    @Test
    fun `past daytime reminder is held to the same days quiet end`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 9, 1, 13, 30, 0, 0, zone)

        val delay = computeReminderDelayMs(now, 13, 0, 12 * 60, 14 * 60)

        assertEquals(Duration.ofMinutes(30).toMillis(), delay)
    }
}
