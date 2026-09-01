package com.ironlog.app.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val scheduleGeneration = inputData.getLong(ReminderScheduler.REQUEST_GENERATION_KEY, 0L)
        val occurrenceEpochMillis = inputData.getLong(
            ReminderScheduler.REQUEST_OCCURRENCE_EPOCH_MILLIS_KEY,
            0L,
        )
        try {
            if (reminderOccurrenceIsExpired(occurrenceEpochMillis, System.currentTimeMillis())) {
                Timber.i(
                    "Skipping expired reminder occurrence %d for generation %d",
                    occurrenceEpochMillis,
                    scheduleGeneration,
                )
                WorkoutNotificationBridge.clearExpiredReminderIfCurrent(
                    applicationContext,
                    scheduleGeneration,
                    occurrenceEpochMillis,
                )
                scheduleNextIfEnabled(scheduleGeneration, occurrenceEpochMillis)
                return@withContext Result.success()
            }
            when (WorkoutNotificationBridge.showDailyReminder(
                applicationContext,
                scheduleGeneration,
                occurrenceEpochMillis,
            )) {
                NotificationDeliveryResult.APP_DISABLED,
                NotificationDeliveryResult.PERMISSION_DENIED,
                NotificationDeliveryResult.CHANNEL_BLOCKED,
                NotificationDeliveryResult.STALE_SCHEDULE,
                -> Unit // App resume/settings reconciliation restarts the chain when available.
                NotificationDeliveryResult.QUIET_HOURS -> scheduleAtQuietHoursEndIfEnabled(
                    scheduleGeneration,
                    occurrenceEpochMillis,
                )
                else -> scheduleNextIfEnabled(scheduleGeneration, occurrenceEpochMillis)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.e(error, "Daily reminder delivery failed on attempt %d", runAttemptCount + 1)
            if (dailyReminderShouldRetry(runAttemptCount)) {
                Result.retry()
            } else {
                // The next deterministic occurrence is independent from this failed attempt.
                try {
                    scheduleNextIfEnabled(scheduleGeneration, occurrenceEpochMillis)
                    Result.success()
                } catch (scheduleError: Exception) {
                    Timber.e(scheduleError, "Could not schedule the next reminder occurrence")
                    // Stop the broken chain after the bounded retry budget. App startup/resume,
                    // boot/time-change receivers, and Settings reconciliation can rebuild it.
                    Result.failure()
                }
            }
        }
    }

    private suspend fun scheduleNextIfEnabled(
        scheduleGeneration: Long,
        sourceOccurrenceEpochMillis: Long,
    ) {
        NotificationCoordinator.appendNextOccurrenceIfCurrent(
            applicationContext,
            scheduleGeneration,
            sourceOccurrenceEpochMillis,
            atQuietHoursEnd = false,
        )
    }

    private suspend fun scheduleAtQuietHoursEndIfEnabled(
        scheduleGeneration: Long,
        sourceOccurrenceEpochMillis: Long,
    ) {
        NotificationCoordinator.appendNextOccurrenceIfCurrent(
            applicationContext,
            scheduleGeneration,
            sourceOccurrenceEpochMillis,
            atQuietHoursEnd = true,
        )
    }
}

internal fun dailyReminderShouldRetry(runAttemptCount: Int): Boolean = runAttemptCount in 0..1
