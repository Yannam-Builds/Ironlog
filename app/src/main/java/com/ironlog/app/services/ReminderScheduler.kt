package com.ironlog.app.services

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.Operation
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val DAILY_REMINDER_WORK = "ironlog_daily_reminder"
    private const val DAILY_REMINDER_TAG = "ironlog_daily_reminder_occurrence"
    internal const val REQUEST_GENERATION_KEY = "ironlog_reminder_schedule_generation"
    internal const val REQUEST_OCCURRENCE_EPOCH_MILLIS_KEY =
        "ironlog_reminder_occurrence_epoch_millis"

    suspend fun scheduleDailyReminder(
        context: Context,
        hour: Int,
        minute: Int,
        quietStartMinutes: Int = NotificationKeys.DEFAULT_QUIET_START_MINUTES,
        quietEndMinutes: Int = NotificationKeys.DEFAULT_QUIET_END_MINUTES,
        scheduleGeneration: Long,
        forceReschedule: Boolean = false,
    ) {
        val now = ZonedDateTime.now()
        val target = computeReminderTarget(
            now,
            hour,
            minute,
            quietStartMinutes,
            quietEndMinutes,
        )
        val workManager = WorkManager.getInstance(context)
        if (forceReschedule) cancelAllScheduledWork(workManager)
        enqueueOccurrence(workManager, target, scheduleGeneration)
    }

    /**
     * Schedules the next deterministic occurrence. If a worker crashes after this enqueue and
     * is retried, it computes the same local-time target and KEEP prevents a duplicate delivery.
     */
    internal suspend fun scheduleNextAfterRun(
        context: Context,
        hour: Int,
        minute: Int,
        quietStartMinutes: Int = NotificationKeys.DEFAULT_QUIET_START_MINUTES,
        quietEndMinutes: Int = NotificationKeys.DEFAULT_QUIET_END_MINUTES,
        scheduleGeneration: Long,
        afterOccurrenceEpochMillis: Long,
    ) {
        val target = computeReminderTargetStrictlyAfter(
            ZonedDateTime.now(),
            hour,
            minute,
            quietStartMinutes,
            quietEndMinutes,
            afterOccurrenceEpochMillis,
        )
        enqueueOccurrence(WorkManager.getInstance(context), target, scheduleGeneration)
    }

    /** Used when clock/time-zone changes move an already-running worker into quiet hours. */
    internal suspend fun scheduleAtQuietHoursEnd(
        context: Context,
        quietEndMinutes: Int,
        scheduleGeneration: Long,
        afterOccurrenceEpochMillis: Long,
    ) {
        val target = computeQuietHoursEndTarget(
            reminderSchedulingLowerBound(ZonedDateTime.now(), afterOccurrenceEpochMillis),
            quietEndMinutes,
        )
        enqueueOccurrence(WorkManager.getInstance(context), target, scheduleGeneration)
    }

    suspend fun cancelDailyReminder(context: Context) {
        cancelAllScheduledWork(WorkManager.getInstance(context))
    }

    private suspend fun enqueueOccurrence(
        workManager: WorkManager,
        target: ZonedDateTime,
        scheduleGeneration: Long,
    ) {
        val targetEpochMillis = target.toInstant().toEpochMilli()
        val delayMs = Duration.between(ZonedDateTime.now(target.zone), target)
            .toMillis()
            .coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .addTag(DAILY_REMINDER_TAG)
            .setInputData(workDataOf(
                REQUEST_GENERATION_KEY to scheduleGeneration,
                REQUEST_OCCURRENCE_EPOCH_MILLIS_KEY to targetEpochMillis,
            ))
            .setInitialDelay(
                delayMs,
                TimeUnit.MILLISECONDS,
            )
            .build()
        awaitOperation(workManager.enqueueUniqueWork(
            reminderOccurrenceWorkName(scheduleGeneration, targetEpochMillis),
            ExistingWorkPolicy.KEEP,
            request,
        ))
    }

    private suspend fun cancelAllScheduledWork(workManager: WorkManager) {
        // The unique legacy name catches pre-v3 chains; the tag catches every v3 occurrence.
        awaitOperation(workManager.cancelUniqueWork(DAILY_REMINDER_WORK))
        awaitOperation(workManager.cancelAllWorkByTag(DAILY_REMINDER_TAG))
    }

    private suspend fun awaitOperation(operation: Operation) {
        withTimeout(WORK_MANAGER_OPERATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val future = operation.result
                future.addListener(
                    {
                        if (continuation.isActive) {
                            continuation.resumeWith(runCatching {
                                future.get()
                                Unit
                            })
                        }
                    },
                    DIRECT_EXECUTOR,
                )
                continuation.invokeOnCancellation { future.cancel(true) }
            }
        }
    }

    private val DIRECT_EXECUTOR = Executor { command -> command.run() }
    private const val WORK_MANAGER_OPERATION_TIMEOUT_MS = 10_000L
}

internal const val CURRENT_REMINDER_SCHEDULE_VERSION = 3
internal const val MAX_REMINDER_LATENESS_MS = 2L * 60L * 60L * 1_000L
internal const val REMINDER_SCHEDULE_VERSION_KEY = "notification_schedule_version"
internal const val REMINDER_SCHEDULE_GENERATION_KEY = "notification_schedule_generation"
internal const val REMINDER_SCHEDULE_SIGNATURE_KEY = "notification_schedule_signature"

internal fun reminderScheduleNeedsMigration(storedVersion: Int?): Boolean =
    storedVersion != CURRENT_REMINDER_SCHEDULE_VERSION

internal fun nextReminderScheduleGeneration(current: Long?, invalidate: Boolean): Long {
    if (current == null || current <= 0L) return 1L
    val safe = current.coerceAtLeast(1L)
    return when {
        !invalidate -> safe
        safe == Long.MAX_VALUE -> 1L
        else -> safe + 1L
    }
}

internal fun reminderScheduleGenerationMatches(requestGeneration: Long, currentGeneration: Long): Boolean =
    requestGeneration > 0L && requestGeneration == currentGeneration

internal fun reminderScheduleSignature(
    zoneId: String,
    reminderMinutes: Int,
    quietStartMinutes: Int,
    quietEndMinutes: Int,
): String = listOf(
    zoneId,
    reminderMinutes.coerceIn(0, 1439),
    quietStartMinutes.coerceIn(0, 1439),
    quietEndMinutes.coerceIn(0, 1439),
).joinToString("|")

internal fun reminderOccurrenceWorkName(scheduleGeneration: Long, targetEpochMillis: Long): String =
    "ironlog_daily_reminder_${scheduleGeneration.coerceAtLeast(1L)}_${targetEpochMillis.coerceAtLeast(0L)}"

/**
 * WorkManager is deliberately inexact, but a morning training prompt should not arrive much later
 * in the day after long Doze, offline, or battery-restriction delays. Missing legacy occurrence
 * metadata is also treated as expired so an obsolete request can only seed the next occurrence.
 */
internal fun reminderOccurrenceIsExpired(
    targetEpochMillis: Long,
    nowEpochMillis: Long,
    maxLatenessMs: Long = MAX_REMINDER_LATENESS_MS,
): Boolean {
    if (targetEpochMillis <= 0L) return true
    if (nowEpochMillis <= targetEpochMillis) return false
    val safeLateness = maxLatenessMs.coerceAtLeast(0L)
    val latestDeliveryEpochMillis = if (targetEpochMillis > Long.MAX_VALUE - safeLateness) {
        Long.MAX_VALUE
    } else {
        targetEpochMillis + safeLateness
    }
    return nowEpochMillis > latestDeliveryEpochMillis
}

internal fun computeQuietHoursEndTarget(now: ZonedDateTime, quietEndMinutes: Int): ZonedDateTime {
    val end = quietEndMinutes.coerceIn(0, 1439)
    var next = now.toLocalDate().atTime(end / 60, end % 60).atZone(now.zone)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return next
}

internal fun computeReminderTargetStrictlyAfter(
    now: ZonedDateTime,
    hour: Int,
    minute: Int,
    quietStartMinutes: Int = NotificationKeys.DEFAULT_QUIET_START_MINUTES,
    quietEndMinutes: Int = NotificationKeys.DEFAULT_QUIET_END_MINUTES,
    afterOccurrenceEpochMillis: Long,
): ZonedDateTime = computeReminderTarget(
    reminderSchedulingLowerBound(now, afterOccurrenceEpochMillis),
    hour,
    minute,
    quietStartMinutes,
    quietEndMinutes,
)

private fun reminderSchedulingLowerBound(
    now: ZonedDateTime,
    afterOccurrenceEpochMillis: Long,
): ZonedDateTime {
    if (afterOccurrenceEpochMillis <= 0L) return now
    val occurrence = Instant.ofEpochMilli(afterOccurrenceEpochMillis)
    return if (occurrence.isAfter(now.toInstant())) occurrence.atZone(now.zone) else now
}

internal fun computeQuietHoursEndDelayMs(now: ZonedDateTime, quietEndMinutes: Int): Long =
    Duration.between(now, computeQuietHoursEndTarget(now, quietEndMinutes))
        .toMillis()
        .coerceAtLeast(60_000L)

internal fun computeReminderDelayMs(
    now: ZonedDateTime,
    hour: Int,
    minute: Int,
    quietStartMinutes: Int = NotificationKeys.DEFAULT_QUIET_START_MINUTES,
    quietEndMinutes: Int = NotificationKeys.DEFAULT_QUIET_END_MINUTES,
): Long = Duration.between(
    now,
    computeReminderTarget(now, hour, minute, quietStartMinutes, quietEndMinutes),
).toMillis().coerceAtLeast(60_000L)

internal fun computeReminderTarget(
    now: ZonedDateTime,
    hour: Int,
    minute: Int,
    quietStartMinutes: Int = NotificationKeys.DEFAULT_QUIET_START_MINUTES,
    quietEndMinutes: Int = NotificationKeys.DEFAULT_QUIET_END_MINUTES,
): ZonedDateTime {
    val safeHour = hour.coerceIn(0, 23)
    val safeMinute = minute.coerceIn(0, 59)
    val start = quietStartMinutes.coerceIn(0, 1439)
    val end = quietEndMinutes.coerceIn(0, 1439)
    val requestedMinutes = safeHour * 60 + safeMinute
    val inQuietHours = when {
        start == end -> false
        start < end -> requestedMinutes in start until end
        else -> requestedMinutes >= start || requestedMinutes < end
    }
    fun adjustedForDate(date: java.time.LocalDate): ZonedDateTime {
        if (!inQuietHours) return date.atTime(safeHour, safeMinute).atZone(now.zone)
        val endDate = when {
            start < end -> date
            requestedMinutes >= start -> date.plusDays(1)
            else -> date
        }
        return endDate.atTime(end / 60, end % 60).atZone(now.zone)
    }
    var next = adjustedForDate(now.toLocalDate())
    if (!next.isAfter(now)) next = adjustedForDate(now.toLocalDate().plusDays(1))
    return next
}
