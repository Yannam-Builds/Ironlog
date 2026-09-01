package com.ironlog.app.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.ironlog.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.time.ZoneId
import timber.log.Timber

enum class NotificationMutationTarget {
    ENABLED,
    TRAINING_REMINDERS,
    MILESTONE_ALERTS,
    REMINDER_TIME,
    QUIET_HOURS,
    FREQUENCY_PROFILE,
    RECONCILE,
}

/** Serializes preference writes, WorkManager reconciliation, and visible reminder cleanup. */
object NotificationCoordinator {
    private val mutationMutex = Mutex()
    private val requestGenerations = NotificationMutationTarget.entries
        .associateWith { AtomicLong(0L) }

    fun issueMutationToken(target: NotificationMutationTarget): Long =
        requestGenerations.getValue(target).incrementAndGet()

    fun isMutationTokenCurrent(target: NotificationMutationTarget, token: Long): Boolean =
        token > 0L && requestGenerations.getValue(target).get() == token

    private data class ReminderSchedulePlan(
        val deliveryAvailable: Boolean,
        val minutes: Int,
        val quietStart: Int,
        val quietEnd: Int,
        val generation: Long,
        val replaceExisting: Boolean,
        val signature: String,
    )

    suspend fun setEnabled(
        context: Context,
        enabled: Boolean,
        requestToken: Long = issueMutationToken(NotificationMutationTarget.ENABLED),
    ) {
        mutationMutex.withLock {
            if (!isMutationTokenCurrent(NotificationMutationTarget.ENABLED, requestToken)) return@withLock
            NotificationDeliveryGate.mutex.withLock {
                if (isMutationTokenCurrent(NotificationMutationTarget.ENABLED, requestToken)) {
                    SettingsRepository().setBoolean(NotificationKeys.ENABLED, enabled)
                }
            }
            if (isMutationTokenCurrent(NotificationMutationTarget.ENABLED, requestToken)) {
                reconcileLocked(context.applicationContext, forceReschedule = true)
            }
        }
    }

    suspend fun updateReminderTime(
        context: Context,
        minutes: Int,
        requestToken: Long = issueMutationToken(NotificationMutationTarget.REMINDER_TIME),
    ): Boolean {
        if (minutes !in 0 until 24 * 60) return false
        mutationMutex.withLock {
            if (!isMutationTokenCurrent(NotificationMutationTarget.REMINDER_TIME, requestToken)) {
                return@withLock
            }
            NotificationDeliveryGate.mutex.withLock {
                SettingsRepository().setSetting(NotificationKeys.REMINDER_MINUTES, minutes, "number")
            }
            if (isMutationTokenCurrent(NotificationMutationTarget.REMINDER_TIME, requestToken)) {
                reconcileLocked(context.applicationContext, forceReschedule = true)
            }
        }
        return isMutationTokenCurrent(NotificationMutationTarget.REMINDER_TIME, requestToken)
    }

    suspend fun updateQuietHours(
        context: Context,
        startMinutes: Int,
        endMinutes: Int,
        requestToken: Long = issueMutationToken(NotificationMutationTarget.QUIET_HOURS),
    ): Boolean {
        if (startMinutes !in 0 until 24 * 60 || endMinutes !in 0 until 24 * 60) return false
        mutationMutex.withLock {
            if (!isMutationTokenCurrent(NotificationMutationTarget.QUIET_HOURS, requestToken)) {
                return@withLock
            }
            NotificationDeliveryGate.mutex.withLock {
                val repo = SettingsRepository()
                repo.setLongSettingsAtomically(mapOf(
                    NotificationKeys.QUIET_START to startMinutes.toLong(),
                    NotificationKeys.QUIET_END to endMinutes.toLong(),
                ))
            }
            if (isMutationTokenCurrent(NotificationMutationTarget.QUIET_HOURS, requestToken)) {
                reconcileLocked(context.applicationContext, forceReschedule = true)
            }
        }
        return isMutationTokenCurrent(NotificationMutationTarget.QUIET_HOURS, requestToken)
    }

    suspend fun setTrainingRemindersEnabled(
        context: Context,
        enabled: Boolean,
        requestToken: Long = issueMutationToken(NotificationMutationTarget.TRAINING_REMINDERS),
    ) {
        mutationMutex.withLock {
            if (!isMutationTokenCurrent(NotificationMutationTarget.TRAINING_REMINDERS, requestToken)) {
                return@withLock
            }
            NotificationDeliveryGate.mutex.withLock {
                SettingsRepository().setBoolean(NotificationKeys.TRAINING_ENABLED, enabled)
                if (!enabled) WorkoutNotificationBridge.cancelReminderNotification(context)
            }
        }
    }

    suspend fun setMilestoneAlertsEnabled(
        enabled: Boolean,
        requestToken: Long = issueMutationToken(NotificationMutationTarget.MILESTONE_ALERTS),
    ) {
        mutationMutex.withLock {
            if (!isMutationTokenCurrent(NotificationMutationTarget.MILESTONE_ALERTS, requestToken)) {
                return@withLock
            }
            SettingsRepository().setBoolean("milestone_alerts_enabled", enabled)
        }
    }

    suspend fun setFrequencyProfile(
        context: Context,
        profile: String,
        requestToken: Long = issueMutationToken(NotificationMutationTarget.FREQUENCY_PROFILE),
    ) {
        require(profile in setOf("conservative", "balanced", "aggressive"))
        mutationMutex.withLock {
            if (!isMutationTokenCurrent(NotificationMutationTarget.FREQUENCY_PROFILE, requestToken)) {
                return@withLock
            }
            NotificationDeliveryGate.mutex.withLock {
                SettingsRepository().setSetting(NotificationKeys.PROFILE, profile, "string")
                WorkoutNotificationBridge.cancelReminderNotification(context)
            }
        }
    }

    suspend fun reconcile(context: Context, forceReschedule: Boolean = false) = mutationMutex.withLock {
        reconcileLocked(context.applicationContext, forceReschedule)
    }

    /** Appends only if this worker still belongs to the authoritative reminder schedule. */
    internal suspend fun appendNextOccurrenceIfCurrent(
        context: Context,
        scheduleGeneration: Long,
        sourceOccurrenceEpochMillis: Long,
        atQuietHoursEnd: Boolean,
    ): Boolean = mutationMutex.withLock {
        val canAppend = NotificationDeliveryGate.mutex.withLock delivery@{
            val repo = SettingsRepository()
            val currentGeneration = readScheduleGeneration(repo)
            if (!reminderScheduleGenerationMatches(scheduleGeneration, currentGeneration) ||
                !repo.getBoolean(NotificationKeys.ENABLED, false) ||
                !isSystemDeliveryAvailable(context)
            ) return@delivery false

            true
        }
        if (!canAppend) return@withLock false

        val repo = SettingsRepository()
        if (atQuietHoursEnd) {
            val quietEnd = repo.getSettingNumber(NotificationKeys.QUIET_END)?.toInt()
                    ?.takeIf { it in 0 until 24 * 60 } ?: NotificationKeys.DEFAULT_QUIET_END_MINUTES
            ReminderScheduler.scheduleAtQuietHoursEnd(
                context,
                quietEnd,
                scheduleGeneration,
                sourceOccurrenceEpochMillis,
            )
        } else {
            val minutes = repo.getSettingNumber(NotificationKeys.REMINDER_MINUTES)?.toInt()
                    ?.takeIf { it in 0 until 24 * 60 } ?: NotificationKeys.DEFAULT_REMINDER_MINUTES
            val quietStart = repo.getSettingNumber(NotificationKeys.QUIET_START)?.toInt()
                    ?.takeIf { it in 0 until 24 * 60 } ?: NotificationKeys.DEFAULT_QUIET_START_MINUTES
            val quietEnd = repo.getSettingNumber(NotificationKeys.QUIET_END)?.toInt()
                    ?.takeIf { it in 0 until 24 * 60 } ?: NotificationKeys.DEFAULT_QUIET_END_MINUTES
            ReminderScheduler.scheduleNextAfterRun(
                context,
                minutes / 60,
                minutes % 60,
                quietStart,
                quietEnd,
                scheduleGeneration,
                sourceOccurrenceEpochMillis,
            )
        }
        true
    }

    fun isSystemDeliveryAvailable(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WorkoutNotificationBridge.ensureReminderChannel(context)
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(WorkoutNotificationBridge.REMINDER_CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private suspend fun reconcileLocked(context: Context, forceReschedule: Boolean) {
        val plan = NotificationDeliveryGate.mutex.withLock {
            val repo = SettingsRepository()
            migrateLegacyProfile(repo)
            val enabled = repo.getBoolean(NotificationKeys.ENABLED, false)
            val minutes = repo.getSettingNumber(NotificationKeys.REMINDER_MINUTES)?.toInt()
            ?.takeIf { it in 0 until 24 * 60 }
            ?: NotificationKeys.DEFAULT_REMINDER_MINUTES
            val quietStart = repo.getSettingNumber(NotificationKeys.QUIET_START)?.toInt()
            ?.takeIf { it in 0 until 24 * 60 } ?: NotificationKeys.DEFAULT_QUIET_START_MINUTES
            val quietEnd = repo.getSettingNumber(NotificationKeys.QUIET_END)?.toInt()
            ?.takeIf { it in 0 until 24 * 60 } ?: NotificationKeys.DEFAULT_QUIET_END_MINUTES
            val scheduleSignature = reminderScheduleSignature(
                ZoneId.systemDefault().id,
                minutes,
                quietStart,
                quietEnd,
            )
            val signatureChanged = repo.getString(REMINDER_SCHEDULE_SIGNATURE_KEY) != scheduleSignature
            val scheduleNeedsMigration = reminderScheduleNeedsMigration(
                repo.getSettingNumber(REMINDER_SCHEDULE_VERSION_KEY)?.toInt(),
            )
            val deliveryAvailable = enabled && isSystemDeliveryAvailable(context)
            val invalidate = forceReschedule || scheduleNeedsMigration || signatureChanged ||
                (enabled && !deliveryAvailable)
            val generation = nextReminderScheduleGeneration(
                current = readScheduleGeneration(repo).takeIf { it > 0L },
                invalidate = invalidate,
            )
            if (readScheduleGeneration(repo) != generation) {
                repo.setSetting(REMINDER_SCHEDULE_GENERATION_KEY, generation, "number")
            }
            if (!deliveryAvailable) WorkoutNotificationBridge.cancelSmartNotifications(context)
            ReminderSchedulePlan(
                deliveryAvailable = deliveryAvailable,
                minutes = minutes,
                quietStart = quietStart,
                quietEnd = quietEnd,
                generation = generation,
                replaceExisting = forceReschedule || scheduleNeedsMigration || signatureChanged,
                signature = scheduleSignature,
            )
        }
        if (plan.deliveryAvailable) {
            ReminderScheduler.scheduleDailyReminder(
                context,
                plan.minutes / 60,
                plan.minutes % 60,
                plan.quietStart,
                plan.quietEnd,
                plan.generation,
                plan.replaceExisting,
            )
        } else {
            ReminderScheduler.cancelDailyReminder(context)
        }
        val repo = SettingsRepository()
        repo.setString(REMINDER_SCHEDULE_SIGNATURE_KEY, plan.signature)
        repo.setSetting(REMINDER_SCHEDULE_VERSION_KEY, CURRENT_REMINDER_SCHEDULE_VERSION, "number")
    }

    private suspend fun migrateLegacyProfile(repo: SettingsRepository) {
        if (repo.getString(NotificationKeys.PROFILE).isNullOrBlank()) {
            repo.getString(NotificationKeys.LEGACY_PROFILE)?.takeIf { it.isNotBlank() }?.let {
                repo.setString(NotificationKeys.PROFILE, it)
            }
        }
    }

    private suspend fun readScheduleGeneration(repo: SettingsRepository): Long =
        repo.getSettingNumber(REMINDER_SCHEDULE_GENERATION_KEY)?.toLong()?.coerceAtLeast(0L) ?: 0L
}

/** Recalculates the next local reminder after time-zone, clock, boot, or app-update changes. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_RECONCILE_TIMEOUT_MS) {
                    NotificationCoordinator.reconcile(context, forceReschedule = true)
                }
            } catch (error: Exception) {
                Timber.e(error, "Could not reconcile reminder schedule after %s", intent.action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val RECEIVER_RECONCILE_TIMEOUT_MS = 8_000L
    }
}
