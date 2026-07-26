package com.ironlog.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.ironlog.app.MainActivity
import com.ironlog.app.R
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.ui.theme.IronLogThemes
import org.json.JSONObject

/**
 * Foreground service that keeps the workout session alive when the app is in the background.
 * Displays one persistent, theme-tinted notification with live session and rest-timer info.
 */
class WorkoutForegroundService : Service() {

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val settingsBox by lazy { ObjectBox.store.boxFor(AppSettingEntity::class.java) }

    private var timerThread: Thread? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val workoutName = intent?.getStringExtra(EXTRA_WORKOUT_NAME) ?: "Workout"
        val startMs = intent?.getLongExtra(EXTRA_START_MS, System.currentTimeMillis())
            ?: System.currentTimeMillis()

        ensureChannel()
        // The foreground service is now the single source for workout notifications.
        runCatching {
            notificationManager.cancel(7101)
            notificationManager.cancel(7102)
        }
        startForeground(NOTIFICATION_ID, buildNotification(workoutName, startMs))

        running = false
        timerThread?.let { old -> old.interrupt(); runCatching { old.join(200) } }
        timerThread = null

        running = true
        timerThread = Thread {
            var lastHapticSec = Int.MIN_VALUE
            while (running && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(1000)
                    if (running) {
                        runCatching {
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                buildNotification(workoutName, startMs),
                            )
                        }
                        // Fired from the service so countdown haptics continue on lockscreen/background.
                        runCatching {
                            val restEndMs = readSettingString("active_workout_rest_end_ms")?.toLongOrNull() ?: 0L
                            val remaining = if (restEndMs > System.currentTimeMillis()) {
                                ((restEndMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                            } else 0
                            if (remaining in 1..5 && remaining != lastHapticSec && hapticsEnabled()) {
                                triggerCountdownHaptic(remaining)
                                lastHapticSec = remaining
                            } else if (remaining == 0 || restEndMs == 0L) {
                                lastHapticSec = Int.MIN_VALUE
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }

        return START_STICKY
    }

    override fun onDestroy() {
        stopTimer()
        super.onDestroy()
    }

    /** Keep the ObjectBox row and resume keys intact when the task is swiped away. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopTimer()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopTimer() {
        running = false
        timerThread?.interrupt()
        timerThread = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout in Progress",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Live active-workout notification."
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(workoutName: String, startMs: Long): Notification {
        val elapsedSec = (System.currentTimeMillis() - startMs) / 1000L
        val timeStr = elapsedSec.toTimerString()

        val setLabel = readSettingString("active_workout_set_label").orEmpty()
        val restEndMs = readSettingString("active_workout_rest_end_ms")?.toLongOrNull() ?: 0L
        val restRemainingSec = if (restEndMs > System.currentTimeMillis()) {
            ((restEndMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        } else 0

        val displaySet = if (setLabel.isBlank()) "--" else setLabel
        val summary = if (restRemainingSec > 0) {
            "Set: $displaySet - Rest ${restRemainingSec}s - Session $timeStr"
        } else {
            "Set: $displaySet - Session $timeStr"
        }

        val tapPendingIntent = PendingIntent.getActivity(
            this,
            700,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to_workout", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ironlog)
            .setContentTitle("IRONLOG - $workoutName")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(readCurrentAccentColor())
            .setColorized(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (restRemainingSec > 0) {
            builder.addAction(
                0,
                "Skip",
                workoutActionActivityIntent(NotificationActionRouter.Actions.SKIP_REST, 701),
            )
            builder.addAction(
                0,
                "+30s",
                workoutActionActivityIntent(NotificationActionRouter.Actions.ADD_30S, 702),
            )
        }
        builder.addAction(
            0,
            "Finish",
            workoutActionActivityIntent(NotificationActionRouter.Actions.FINISH_WORKOUT, 703),
        )
        return builder.build()
    }

    private fun readSettingString(key: String): String? {
        return settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }?.value
    }

    private fun hapticsEnabled(): Boolean {
        val raw = readSettingString("ironlog_settings") ?: return true
        return runCatching {
            JSONObject(raw).optBoolean("hapticFeedback", true)
        }.getOrDefault(true)
    }

    private fun readCurrentAccentColor(): Int {
        val raw = readSettingString("ironlog_settings").orEmpty()
        val theme = runCatching { JSONObject(raw).optString("theme", IronLogThemes.DEFAULT_THEME) }
            .getOrDefault(IronLogThemes.DEFAULT_THEME)
            .ifBlank { IronLogThemes.DEFAULT_THEME }
        return IronLogThemes.byName(theme).accent.toArgb()
    }

    private fun workoutActionActivityIntent(actionId: String, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to_workout", true)
                putExtra("actionId", actionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @Suppress("DEPRECATION")
    private fun triggerCountdownHaptic(secondsRemaining: Int) {
        val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vib == null || !vib.hasVibrator()) return
        val (duration, amplitude) = when (secondsRemaining) {
            1 -> 40L to 255
            2 -> 30L to 220
            3 -> 22L to 180
            4 -> 16L to 140
            else -> 12L to 100
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { vib.vibrate(VibrationEffect.createOneShot(duration, amplitude)) }
        } else {
            runCatching { vib.vibrate(duration) }
        }
    }

    companion object {
        private const val CHANNEL_ID = "ironlog_workout_timer"
        private const val NOTIFICATION_ID = 7001
        private const val EXTRA_WORKOUT_NAME = "workout_name"
        private const val EXTRA_START_MS = "start_ms"

        fun start(context: Context, workoutName: String, startMs: Long) {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                putExtra(EXTRA_WORKOUT_NAME, workoutName)
                putExtra(EXTRA_START_MS, startMs)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutForegroundService::class.java))
        }
    }
}

private fun Long.toTimerString(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }
}
