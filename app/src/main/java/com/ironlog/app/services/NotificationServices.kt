package com.ironlog.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ironlog.app.R
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.WorkoutEntity
import io.objectbox.Box
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** One process-wide gate prevents a late delivery from racing an opt-out/cancel operation. */
internal object NotificationDeliveryGate {
    val mutex = Mutex()
}

/** Process lifecycle signal used to avoid re-engaging someone who is already in IronLog. */
internal object NotificationAppVisibility {
    private val foreground = AtomicBoolean(false)

    fun setForeground(value: Boolean) {
        foreground.set(value)
    }

    fun isForeground(): Boolean = foreground.get()
}

/** Stable action identifiers shared by the workout notification and Active Workout. */
object NotificationActionRouter {
    object Actions {
        const val SKIP_REST = "ironlog.skip_rest"
        const val ADD_30S = "ironlog.add_30s"
        /** Internal UI command; never exposed as a reusable notification PendingIntent. */
        const val PAUSE_REST = "ironlog.pause_rest"
        const val FINISH_WORKOUT = "ironlog.finish_workout"
        /** Internal inbox event: the receiver already changed the authoritative rest deadline. */
        const val REST_STATE_CHANGED = "ironlog.rest_state_changed"
    }
}

enum class NotificationDeliveryResult {
    SENT,
    NO_CANDIDATE,
    APP_DISABLED,
    QUIET_HOURS,
    PERMISSION_DENIED,
    CHANNEL_BLOCKED,
    POLICY_SUPPRESSED,
    STALE_SCHEDULE,
    APP_FOREGROUND,
    APP_OPEN_ACKNOWLEDGED,
}

/**
 * A completed evaluation that intentionally delivers nothing must not leave yesterday's
 * reminder visible. A stale worker is the exception: it has no authority to change the
 * notification produced by the current schedule generation.
 */
internal fun shouldClearVisibleReminderAfterEvaluation(result: NotificationDeliveryResult): Boolean =
    when (result) {
        NotificationDeliveryResult.NO_CANDIDATE,
        NotificationDeliveryResult.APP_DISABLED,
        NotificationDeliveryResult.QUIET_HOURS,
        NotificationDeliveryResult.PERMISSION_DENIED,
        NotificationDeliveryResult.CHANNEL_BLOCKED,
        NotificationDeliveryResult.POLICY_SUPPRESSED,
        NotificationDeliveryResult.APP_FOREGROUND,
        NotificationDeliveryResult.APP_OPEN_ACKNOWLEDGED -> true
        NotificationDeliveryResult.SENT,
        NotificationDeliveryResult.STALE_SCHEDULE -> false
    }

internal const val MAX_APP_OPEN_ACK_FUTURE_SKEW_MS = 5L * 60L * 1_000L

internal fun reminderOccurrenceAcknowledgedByAppOpen(
    occurrenceEpochMillis: Long,
    lastAppForegroundEpochMillis: Long,
    nowEpochMillis: Long,
): Boolean {
    if (occurrenceEpochMillis <= 0L || nowEpochMillis <= 0L) return false
    val latestPlausibleAck = if (nowEpochMillis > Long.MAX_VALUE - MAX_APP_OPEN_ACK_FUTURE_SKEW_MS) {
        Long.MAX_VALUE
    } else {
        nowEpochMillis + MAX_APP_OPEN_ACK_FUTURE_SKEW_MS
    }
    return lastAppForegroundEpochMillis in occurrenceEpochMillis..latestPlausibleAck
}

internal fun expiredOccurrenceMayClearVisibleReminder(
    requestGeneration: Long,
    currentGeneration: Long,
    expiredOccurrenceEpochMillis: Long,
    visibleGeneration: Long,
    visibleOccurrenceEpochMillis: Long,
): Boolean =
    reminderScheduleGenerationMatches(requestGeneration, currentGeneration) &&
        visibleGeneration == requestGeneration &&
        expiredOccurrenceEpochMillis > 0L &&
        visibleOccurrenceEpochMillis > 0L &&
        visibleOccurrenceEpochMillis <= expiredOccurrenceEpochMillis

private const val VISIBLE_REMINDER_MARKER_KEY = "notification_schedule_visible_reminder"

private data class VisibleReminderMarker(
    val generation: Long,
    val occurrenceEpochMillis: Long,
)

private fun encodeVisibleReminderMarker(generation: Long, occurrenceEpochMillis: Long): String =
    "$generation:$occurrenceEpochMillis"

private fun decodeVisibleReminderMarker(value: String?): VisibleReminderMarker? {
    val parts = value?.split(':', limit = 2) ?: return null
    if (parts.size != 2) return null
    val generation = parts[0].toLongOrNull() ?: return null
    val occurrence = parts[1].toLongOrNull() ?: return null
    if (generation <= 0L || occurrence <= 0L) return null
    return VisibleReminderMarker(generation, occurrence)
}

/**
 * Owns non-foreground notification delivery. Active-workout status remains the responsibility
 * of [WorkoutForegroundService], so reminders, rest completion, and live status can be tuned
 * independently in Android's notification settings.
 */
object WorkoutNotificationBridge {
    internal const val REMINDER_CHANNEL_ID = "ironlog_reminders_v2"
    internal const val REST_COMPLETE_CHANNEL_ID = "ironlog_rest_complete_v1"
    internal const val NOTIF_REMINDER = 7103
    internal const val NOTIF_TEST = 7104
    internal const val NOTIF_REST_COMPLETE = 7105

    private const val LEGACY_NOTIF_ACTIVE = 7101
    private const val LEGACY_NOTIF_REST = 7102
    private const val LEGACY_SHARED_CHANNEL_ID = "ironlog_workout"
    private val settingsBox: Box<AppSettingEntity> by lazy {
        ObjectBox.store.boxFor(AppSettingEntity::class.java)
    }

    /** Removes notifications created by the pre-foreground-service workout implementation. */
    fun clearWorkout(context: Context) {
        context.getSystemService(NotificationManager::class.java).apply {
            cancel(LEGACY_NOTIF_REST)
            cancel(LEGACY_NOTIF_ACTIVE)
        }
    }

    fun clearRestTimer(context: Context) {
        context.getSystemService(NotificationManager::class.java).apply {
            cancel(LEGACY_NOTIF_REST)
            cancel(NOTIF_REST_COMPLETE)
        }
    }

    fun cancelSmartNotifications(context: Context) {
        context.getSystemService(NotificationManager::class.java).apply {
            cancel(NOTIF_TEST)
        }
        cancelReminderNotification(context)
    }

    fun cancelReminderNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_REMINDER)
        runCatching { writeSetting(settingsBox, VISIBLE_REMINDER_MARKER_KEY, "", "string") }
    }

    /**
     * Invalidates a reminder after workout/bodyweight data changes. The delivery gate makes
     * this deterministic if a scheduled worker is evaluating at the same time: either the
     * worker sees the new data, or its earlier result is removed after it finishes posting.
     */
    suspend fun cancelReminderAfterDataMutation(context: Context) {
        withContext(NonCancellable) {
            NotificationDeliveryGate.mutex.withLock {
                cancelReminderNotification(context)
            }
        }
    }

    /**
     * Opening IronLog satisfies any reminder occurrence that was already due. Persist the
     * acknowledgement under the same delivery gate used by workers so a delayed Doze job cannot
     * post immediately after the user leaves the app again.
     */
    suspend fun acknowledgeAppForeground(context: Context) {
        withContext(NonCancellable) {
            NotificationDeliveryGate.mutex.withLock {
                com.ironlog.app.data.repository.SettingsRepository().setSetting(
                    NotificationKeys.LAST_APP_FOREGROUND_EPOCH_MS,
                    System.currentTimeMillis(),
                    "number",
                )
                cancelReminderNotification(context)
            }
        }
    }

    /** An obsolete worker must never remove a reminder posted by the current schedule. */
    suspend fun clearExpiredReminderIfCurrent(
        context: Context,
        expectedScheduleGeneration: Long,
        expiredOccurrenceEpochMillis: Long,
    ): Boolean = NotificationDeliveryGate.mutex.withLock {
        val currentGeneration = readString(settingsBox, REMINDER_SCHEDULE_GENERATION_KEY)
            ?.toDoubleOrNull()?.toLong() ?: 0L
        val visible = decodeVisibleReminderMarker(
            readString(settingsBox, VISIBLE_REMINDER_MARKER_KEY),
        ) ?: return@withLock false
        if (!expiredOccurrenceMayClearVisibleReminder(
                expectedScheduleGeneration,
                currentGeneration,
                expiredOccurrenceEpochMillis,
                visible.generation,
                visible.occurrenceEpochMillis,
            )
        ) return@withLock false
        cancelReminderNotification(context)
        true
    }

    fun cancelAllNotificationArtifacts(context: Context) {
        clearWorkout(context)
        clearRestTimer(context)
        cancelSmartNotifications(context)
        WorkoutForegroundService.cancelNotification(context)
    }

    suspend fun showDailyReminder(
        context: Context,
        expectedScheduleGeneration: Long? = null,
        occurrenceEpochMillis: Long? = null,
    ): NotificationDeliveryResult =
        NotificationDeliveryGate.mutex.withLock {
            if (expectedScheduleGeneration != null) {
                val currentGeneration = readString(settingsBox, REMINDER_SCHEDULE_GENERATION_KEY)
                    ?.toDoubleOrNull()?.toLong() ?: 0L
                if (!reminderScheduleGenerationMatches(expectedScheduleGeneration, currentGeneration)) {
                    return@withLock NotificationDeliveryResult.STALE_SCHEDULE
                }
            }
            if (occurrenceEpochMillis != null) {
                val lastAppForegroundEpochMillis = readString(
                    settingsBox,
                    NotificationKeys.LAST_APP_FOREGROUND_EPOCH_MS,
                )?.toDoubleOrNull()?.toLong() ?: 0L
                if (reminderOccurrenceAcknowledgedByAppOpen(
                        occurrenceEpochMillis,
                        lastAppForegroundEpochMillis,
                        System.currentTimeMillis(),
                    )
                ) {
                    NotificationPolicyEngine.recordSuppressed(
                        settingsBox,
                        key = "system_delivery",
                        topic = "system",
                        reason = "app_open_acknowledged",
                    )
                    return@withLock finishDailyReminderEvaluation(
                        context,
                        NotificationDeliveryResult.APP_OPEN_ACKNOWLEDGED,
                    )
                }
            }
            if (NotificationAppVisibility.isForeground()) {
                NotificationPolicyEngine.recordSuppressed(
                    settingsBox,
                    key = "system_delivery",
                    topic = "system",
                    reason = "app_foreground",
                )
                return@withLock finishDailyReminderEvaluation(
                    context,
                    NotificationDeliveryResult.APP_FOREGROUND,
                )
            }
            ensureReminderChannel(context)
            val blocked = deliveryBlockReason(
                context,
                channelId = REMINDER_CHANNEL_ID,
                includeAppPreference = true,
                includeQuietHours = true,
            )
            if (blocked != null) {
                NotificationPolicyEngine.recordSuppressed(
                    settingsBox,
                    key = "system_delivery",
                    topic = "system",
                    reason = blocked.reason,
                )
                return@withLock finishDailyReminderEvaluation(context, blocked.result)
            }

            val candidates = SmartCandidateGenerator.buildCandidates(
                store = ObjectBox.store,
            )
            val candidate = NotificationPolicyEngine.chooseCandidate(settingsBox, candidates)
                ?: return@withLock finishDailyReminderEvaluation(
                    context,
                    NotificationDeliveryResult.NO_CANDIDATE,
                )
            val decision = NotificationPolicyEngine.shouldSend(settingsBox, candidate.key, candidate.topic)
            if (!decision.allowed) {
                NotificationPolicyEngine.recordSuppressed(
                    settingsBox,
                    candidate.key,
                    candidate.topic,
                    decision.reason ?: "suppressed",
                )
                return@withLock finishDailyReminderEvaluation(
                    context,
                    NotificationDeliveryResult.POLICY_SUPPRESSED,
                )
            }

            val openIntent = PendingIntent.getActivity(
                context,
                3100 + candidate.route.hashCode().and(0x3ff),
            Intent(context, NotificationEntryActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(NotificationEntryActivity.EXTRA_ROUTE, candidate.route)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ironlog)
                .setContentTitle(candidate.title)
                .setContentText(candidate.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(candidate.body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openIntent)
                .build()
            val visibleGeneration = expectedScheduleGeneration
                ?: readString(settingsBox, REMINDER_SCHEDULE_GENERATION_KEY)
                    ?.toDoubleOrNull()?.toLong()
                ?: 0L
            val visibleOccurrence = occurrenceEpochMillis ?: System.currentTimeMillis()
            if (visibleGeneration > 0L && visibleOccurrence > 0L) {
                writeSetting(
                    settingsBox,
                    VISIBLE_REMINDER_MARKER_KEY,
                    encodeVisibleReminderMarker(visibleGeneration, visibleOccurrence),
                    "string",
                )
            }
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIF_REMINDER, notification)
            NotificationPolicyEngine.recordSent(settingsBox, candidate.key, candidate.topic)
            NotificationDeliveryResult.SENT
        }

    private fun finishDailyReminderEvaluation(
        context: Context,
        result: NotificationDeliveryResult,
    ): NotificationDeliveryResult {
        if (shouldClearVisibleReminderAfterEvaluation(result)) {
            cancelReminderNotification(context)
        }
        return result
    }

    fun showTestNotification(
        context: Context,
        text: String = "IronLog notifications are working.",
    ): NotificationDeliveryResult {
        ensureReminderChannel(context)
        val blocked = deliveryBlockReason(
            context,
            channelId = REMINDER_CHANNEL_ID,
            includeAppPreference = false,
            includeQuietHours = false,
        )
        if (blocked != null) return blocked.result
        val openIntent = PendingIntent.getActivity(
            context,
            3141,
            Intent(context, NotificationEntryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(NotificationEntryActivity.EXTRA_ROUTE, "Settings")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ironlog)
            .setContentTitle("IronLog test")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_TEST, notification)
        return NotificationDeliveryResult.SENT
    }

    fun showRestComplete(context: Context, workoutId: String) {
        if (workoutId.isBlank() || readString(settingsBox, "active_workout_id") != workoutId) return
        ensureRestCompleteChannel(context)
        if (deliveryBlockReason(
                context,
                channelId = REST_COMPLETE_CHANNEL_ID,
                includeAppPreference = false,
                includeQuietHours = false,
            ) != null
        ) return
        val openIntent = PendingIntent.getActivity(
            context,
            restCompleteRequestCode(workoutId),
            Intent(context, NotificationEntryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("ironlog://workout/${Uri.encode(workoutId)}/rest-complete")
                putExtra(NotificationEntryActivity.EXTRA_NAVIGATE_TO_WORKOUT, true)
                putExtra(NotificationEntryActivity.EXTRA_WORKOUT_ID, workoutId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REST_COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ironlog)
            .setContentTitle("Rest complete")
            .setContentText("Your next set is ready.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_REST_COMPLETE, notification)
    }

    private fun restCompleteRequestCode(workoutId: String): Int =
        3_151 + workoutId.hashCode().and(0x3fff)

    fun ensureReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "Training reminders",
                    migratedImportance(manager, NotificationManager.IMPORTANCE_DEFAULT),
                ).apply {
                    description = "Smart training, bodyweight, streak, and backup reminders"
                },
            )
        }
    }

    fun ensureRestCompleteChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(REST_COMPLETE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REST_COMPLETE_CHANNEL_ID,
                    "Rest timer complete",
                    migratedImportance(manager, NotificationManager.IMPORTANCE_HIGH),
                ).apply {
                    description = "One-time alerts when an active rest countdown finishes"
                    enableVibration(true)
                },
            )
        }
    }

    /**
     * Splitting the old shared channel must not silently bypass a user's existing block or
     * custom importance. Its untouched default is replaced by the new channel's semantic
     * default; any user-modified importance is inherited.
     */
    private fun migratedImportance(manager: NotificationManager, desired: Int): Int {
        val legacy = manager.getNotificationChannel(LEGACY_SHARED_CHANNEL_ID) ?: return desired
        return if (legacy.importance == NotificationManager.IMPORTANCE_DEFAULT) desired else legacy.importance
    }

    private data class DeliveryBlock(
        val result: NotificationDeliveryResult,
        val reason: String,
    )

    private fun deliveryBlockReason(
        context: Context,
        channelId: String,
        includeAppPreference: Boolean,
        includeQuietHours: Boolean,
    ): DeliveryBlock? {
        if (includeAppPreference && !readBoolean(settingsBox, NotificationKeys.ENABLED, false)) {
            return DeliveryBlock(NotificationDeliveryResult.APP_DISABLED, "disabled")
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return DeliveryBlock(NotificationDeliveryResult.PERMISSION_DENIED, "permission_denied")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(channelId)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                return DeliveryBlock(NotificationDeliveryResult.CHANNEL_BLOCKED, "channel_blocked")
            }
        }
        if (includeQuietHours && isInQuietHours(settingsBox)) {
            return DeliveryBlock(NotificationDeliveryResult.QUIET_HOURS, "quiet_hours")
        }
        return null
    }
}
