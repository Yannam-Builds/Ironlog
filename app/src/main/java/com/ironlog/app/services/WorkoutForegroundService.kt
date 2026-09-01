package com.ironlog.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ironlog.app.R
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.ui.theme.IronLogThemes
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONObject
import timber.log.Timber

/** Session-bound foreground status for a user-started active workout. */
class WorkoutForegroundService : Service() {
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val settingsBox by lazy { ObjectBox.store.boxFor(AppSettingEntity::class.java) }

    @Volatile private var running = false
    @Volatile private var currentWorkoutId: String? = null
    @Volatile private var currentWorkoutName: String = "Workout"
    @Volatile private var currentStartWallMs: Long = 0L
    private var timerThread: Thread? = null
    private var restWakeLock: PowerManager.WakeLock? = null
    private var restWakeLockHeldUntilElapsedMs: Long = 0L
    private val restWakeLockLock = Any()
    private var currentBootCount: Int = -1
    private val timerGeneration = AtomicInteger(0)
    private val deadlineRefreshRequested = AtomicBoolean(true)
    private val updaterSignalLock = ReentrantLock()
    private val updaterSignal = updaterSignalLock.newCondition()
    private val sessionCommandLock = Any()
    private val sessionOwner = AtomicReference<SessionOwner?>(null)

    private class SessionOwner(val workoutId: String)

    override fun onCreate() {
        super.onCreate()
        currentBootCount = WorkoutTimerClock.now(this).bootCount
        liveInstance.set(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val workoutId = intent?.getStringExtra(EXTRA_WORKOUT_ID).orEmpty()
        val workoutName = intent?.getStringExtra(EXTRA_WORKOUT_NAME).orEmpty().ifBlank { "Workout" }
        val startMs = intent?.getLongExtra(EXTRA_START_MS, 0L) ?: 0L
        if (workoutId.isBlank() || startMs <= 0L || !isSessionActive(workoutId)) {
            // A delayed start for session A can arrive after session B is already active.
            // Never let that stale command tear down B's foreground service.
            val current = currentWorkoutId
            if (current.isNullOrBlank() || !isSessionActive(current)) {
                acknowledgeForegroundStartAndTerminate(startId)
            }
            return START_NOT_STICKY
        }

        try {
            synchronized(sessionCommandLock) {
                val owner = SessionOwner(workoutId)
                sessionOwner.set(owner)
                currentWorkoutId = workoutId
                currentWorkoutName = workoutName
                currentStartWallMs = startMs
                ensureSessionStartClock(workoutId, startMs)
                ensureChannel()
                // Remove notification IDs from the obsolete bridge before promoting this service.
                notificationManager.cancel(7101)
                notificationManager.cancel(7102)
                notificationManager.cancel(WorkoutNotificationBridge.NOTIF_REST_COMPLETE)
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(workoutId, workoutName, startMs),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    },
                )
                startUpdater(workoutId, workoutName, startMs, owner)
            }
        } catch (error: RuntimeException) {
            Timber.e(error, "Could not promote workout %s to a foreground service", workoutId)
            terminate(removeNotification = true, startId = startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopUpdater()
        currentWorkoutId = null
        currentWorkoutName = "Workout"
        currentStartWallMs = 0L
        sessionOwner.set(null)
        removeForegroundArtifacts()
        liveInstance.compareAndSet(this, null)
        super.onDestroy()
    }

    /**
     * Removing the task is not discarding the workout. Keep the explicitly-started foreground
     * session alive so a running rest countdown still completes with the screen locked/away.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val owner = sessionOwner.get()
        if (owner == null || !isSessionActive(owner.workoutId)) {
            terminate(removeNotification = true)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Android still requires a service launched with startForegroundService() to enter the
     * foreground even when its command became stale before delivery. Promote with a neutral,
     * action-free notification and remove it immediately; otherwise Android raises
     * ForegroundServiceDidNotStartInTimeException after the service has already stopped.
     */
    private fun acknowledgeForegroundStartAndTerminate(startId: Int) {
        try {
            ensureChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ironlog)
                .setContentTitle("IronLog")
                .setContentText("Workout session closed")
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (error: RuntimeException) {
            Timber.e(error, "Could not acknowledge stale workout foreground-service start")
        } finally {
            terminate(removeNotification = true, startId = startId)
        }
    }

    private fun startUpdater(
        workoutId: String,
        workoutName: String,
        startMs: Long,
        owner: SessionOwner,
    ) {
        stopUpdater()
        // Clear before the read: a concurrent deadline write before this point is included by
        // readRestState(), while a write after it leaves the refresh signal set for the loop.
        deadlineRefreshRequested.set(false)
        val initialRestState = readRestState()
        val initialDeadline = initialRestState.deadline ?: WorkoutTimerDeadline(0L, 0L, -1)
        updateRestWakeLock(initialDeadline.remainingMs(clockNow()))
        val generation = timerGeneration.incrementAndGet()
        running = true
        timerThread = Thread {
            var consecutiveFailures = 0
            var lastHapticSecond = Int.MIN_VALUE
            var observedRestState = initialRestState
            var observedDeadline = initialDeadline
            var completionPostedForEndMs = 0L
            var nextOwnershipCheckElapsedMs = SystemClock.elapsedRealtime() + OWNERSHIP_RECHECK_MS
            while (running && generation == timerGeneration.get() && sessionOwner.get() === owner &&
                !Thread.currentThread().isInterrupted
            ) {
                try {
                    val now = clockNow()
                    if (now.elapsedRealtimeMs >= nextOwnershipCheckElapsedMs) {
                        if (!isSessionActive(workoutId)) {
                            terminateIfOwner(owner)
                            break
                        }
                        nextOwnershipCheckElapsedMs = now.elapsedRealtimeMs + OWNERSHIP_RECHECK_MS
                    }
                    if (deadlineRefreshRequested.getAndSet(false)) {
                        val latestState = readRestState()
                        val restStateChanged = latestState != observedRestState
                        if (restStateChanged) {
                            observedRestState = latestState
                            observedDeadline = latestState.deadline ?: WorkoutTimerDeadline(0L, 0L, -1)
                            completionPostedForEndMs = 0L
                            lastHapticSecond = Int.MIN_VALUE
                            updateRestWakeLock(observedDeadline.remainingMs(now))
                            if (running && generation == timerGeneration.get() && isSessionActive(workoutId)) {
                                notificationManager.notify(
                                    NOTIFICATION_ID,
                                    buildNotification(workoutId, workoutName, startMs),
                                )
                            }
                        }
                    }
                    val remainingMs = observedDeadline.remainingMs(now)
                    // Also checks bounded-wakelock renewal for rests longer than one acquire cap.
                    updateRestWakeLock(remainingMs)
                    val remaining = if (remainingMs <= 0L) 0 else {
                        (((remainingMs - 1L) / 1_000L) + 1L)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()
                    }
                    if ((remaining in 1..5 || remaining > 5 && remaining % 30 == 0) &&
                        remaining != lastHapticSecond && hapticsEnabled() &&
                        generation == timerGeneration.get() && sessionOwner.get() === owner &&
                        isSessionActive(workoutId)
                    ) {
                        triggerCountdownHaptic(remaining)
                        lastHapticSecond = remaining
                    }
                    if (observedDeadline.wallEndMs > 0L && remaining == 0 &&
                        completionPostedForEndMs != observedDeadline.wallEndMs
                    ) {
                        completionPostedForEndMs = observedDeadline.wallEndMs
                        // Claim the deadline before posting so a process restart or a second
                        // updater cannot emit the same completion alert twice.
                        val claimed = clearRestDeadlineIfMatches(workoutId, observedDeadline.wallEndMs)
                        if (claimed) {
                            releaseRestWakeLock()
                            if (-remainingMs <= REST_ALERT_GRACE_MS) {
                                WorkoutNotificationBridge.showRestComplete(this, workoutId)
                            }
                            if (running && generation == timerGeneration.get() && isSessionActive(workoutId)) {
                                notificationManager.notify(
                                    NOTIFICATION_ID,
                                    buildNotification(workoutId, workoutName, startMs),
                                )
                            }
                            observedDeadline = WorkoutTimerDeadline(0L, 0L, now.bootCount)
                            observedRestState = WorkoutPersistedRestState(deadline = observedDeadline)
                        } else {
                            // +30 may have won the compare-and-clear race. Preserve/renew the
                            // new deadline's wake lock instead of releasing it with the old one.
                            val latestNow = clockNow()
                            observedRestState = readRestState()
                            observedDeadline = observedRestState.deadline ?: WorkoutTimerDeadline(0L, 0L, -1)
                            updateRestWakeLock(observedDeadline.remainingMs(latestNow))
                        }
                    }

                    val ownershipRemainingMs =
                        (nextOwnershipCheckElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(100L)
                    val restRemaining = observedDeadline.takeIf { it.wallEndMs > 0L }
                        ?.remainingMs(clockNow())
                    val waitMs = workoutNotificationUpdaterDelayMs(restRemaining, ownershipRemainingMs)
                    consecutiveFailures = 0
                    updaterSignalLock.lock()
                    try {
                        if (!deadlineRefreshRequested.get() && running && generation == timerGeneration.get()) {
                            updaterSignal.await(waitMs, TimeUnit.MILLISECONDS)
                        }
                    } finally {
                        updaterSignalLock.unlock()
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (error: Exception) {
                    consecutiveFailures += 1
                    Timber.w(
                        error,
                        "Workout notification updater failure %d/%d",
                        consecutiveFailures,
                        MAX_CONSECUTIVE_UPDATER_FAILURES,
                    )
                    if (consecutiveFailures >= MAX_CONSECUTIVE_UPDATER_FAILURES) {
                        // If ownership/state cannot be verified repeatedly, a stale foreground
                        // notification is worse than temporarily losing the optional status UI.
                        terminateIfOwner(owner)
                        break
                    }
                    val backoffMs = workoutNotificationUpdaterFailureBackoffMs(consecutiveFailures)
                    try {
                        awaitUpdaterSignal(backoffMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }.also {
            it.name = "IronLogWorkoutNotification"
            it.isDaemon = true
            it.start()
        }
    }

    private fun stopUpdater() {
        running = false
        timerGeneration.incrementAndGet()
        releaseRestWakeLock()
        val old = timerThread
        timerThread = null
        signalUpdater()
        old?.interrupt()
        if (old != null && old !== Thread.currentThread()) {
            runCatching { old.join(500L) }
        }
    }

    private fun terminate(removeNotification: Boolean, startId: Int? = null) {
        stopUpdater()
        currentWorkoutId = null
        currentWorkoutName = "Workout"
        currentStartWallMs = 0L
        sessionOwner.set(null)
        if (removeNotification) removeForegroundArtifacts()
        if (startId != null) stopSelfResult(startId) else stopSelf()
    }

    private fun removeForegroundArtifacts() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(WorkoutNotificationBridge.NOTIF_REST_COMPLETE)
    }

    private fun isSessionActive(workoutId: String): Boolean =
        readSettingString("active_workout_id") == workoutId

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Workout in progress",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Silent status and controls for the workout you are currently logging"
                    setShowBadge(false)
                },
            )
        }
        WorkoutNotificationBridge.ensureRestCompleteChannel(this)
    }

    private fun buildNotification(workoutId: String, workoutName: String, startMs: Long): Notification {
        val setLabel = readSettingString("active_workout_set_label").orEmpty().ifBlank { "Ready" }
        val now = clockNow()
        val restState = readRestState()
        val deadline = restState.deadline ?: WorkoutTimerDeadline(0L, 0L, -1)
        val restActive = deadline.wallEndMs > 0L && deadline.remainingMs(now) > 0L
        val restPaused = !restActive && restState.pausedRemainingMs > 0L
        val restEndMs = deadline.effectiveWallEndMs(now)
        val effectiveStartMs = WorkoutTimerClock.effectiveStartWallMs(
            startWallMs = startMs,
            startElapsedMs = readSettingString(WorkoutTimerSettingKeys.START_ELAPSED_MS)?.toLongOrNull() ?: 0L,
            startBootCount = readSettingString(WorkoutTimerSettingKeys.START_BOOT_COUNT)?.toIntOrNull() ?: -1,
            now = now,
        )
        val summary = when {
            restActive -> "$setLabel · Rest"
            restPaused -> {
                val totalSeconds = (restState.pausedRemainingMs / 1_000L).coerceAtLeast(1L)
                "$setLabel · Rest paused ${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
            }
            else -> setLabel
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this,
            requestCode(workoutId, 0),
            Intent(this, NotificationEntryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("ironlog://workout/${Uri.encode(workoutId)}/open")
                putExtra(NotificationEntryActivity.EXTRA_NAVIGATE_TO_WORKOUT, true)
                putExtra(NotificationEntryActivity.EXTRA_WORKOUT_ID, workoutId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ironlog)
            .setContentTitle("IRONLOG · $workoutName")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(if (restActive) restEndMs else effectiveStartMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(restActive)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(readCurrentAccentColor())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (restActive) {
            builder.addAction(0, "Skip", workoutRestActionIntent(
                workoutId,
                NotificationActionRouter.Actions.SKIP_REST,
                1,
                deadline.wallEndMs,
            ))
            builder.addAction(0, "+30s", workoutRestActionIntent(
                workoutId,
                NotificationActionRouter.Actions.ADD_30S,
                2,
                deadline.wallEndMs,
            ))
        }
        builder.addAction(0, "Finish", workoutActionActivityIntent(workoutId, NotificationActionRouter.Actions.FINISH_WORKOUT, 3))
        return builder.build()
    }

    private fun workoutActionActivityIntent(workoutId: String, actionId: String, actionIndex: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode(workoutId, actionIndex),
            Intent(this, NotificationEntryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse(
                    "ironlog://workout/${Uri.encode(workoutId)}/action/${Uri.encode(actionId)}"
                )
                putExtra(NotificationEntryActivity.EXTRA_NAVIGATE_TO_WORKOUT, true)
                putExtra(NotificationEntryActivity.EXTRA_WORKOUT_ID, workoutId)
                putExtra(NotificationEntryActivity.EXTRA_ACTION_ID, actionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun workoutRestActionIntent(
        workoutId: String,
        actionId: String,
        actionIndex: Int,
        expectedRestEndWallMs: Long,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode(workoutId, actionIndex),
            Intent(this, WorkoutNotificationActionReceiver::class.java).apply {
                action = actionId
                data = Uri.parse(
                    "ironlog://workout/${Uri.encode(workoutId)}/rest/$actionIndex/$expectedRestEndWallMs"
                )
                putExtra(WorkoutNotificationActionReceiver.EXTRA_WORKOUT_ID, workoutId)
                putExtra(WorkoutNotificationActionReceiver.EXTRA_REST_END_WALL_MS, expectedRestEndWallMs)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestCode(workoutId: String, actionIndex: Int): Int =
        7_000 + (workoutId.hashCode().and(0x0fff) * 4) + actionIndex

    private fun readSettingString(key: String): String? =
        settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }?.value

    private fun readRestState(): WorkoutPersistedRestState = ObjectBox.store.callInTx {
        fun value(key: String): String? = settingsBox.query(AppSettingEntity_.key.equal(key))
            .build().use { it.findFirst() }?.value
        WorkoutPersistedRestState(
            deadline = WorkoutTimerDeadline(
                wallEndMs = value(WorkoutTimerSettingKeys.REST_END_WALL_MS)?.toLongOrNull() ?: 0L,
                elapsedEndMs = value(WorkoutTimerSettingKeys.REST_END_ELAPSED_MS)?.toLongOrNull() ?: 0L,
                bootCount = value(WorkoutTimerSettingKeys.REST_BOOT_COUNT)?.toIntOrNull() ?: -1,
            ),
            pausedRemainingMs = value(WorkoutTimerSettingKeys.REST_PAUSED_REMAINING_MS)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        )
    }

    private fun readRestDeadline(): WorkoutTimerDeadline =
        readRestState().deadline ?: WorkoutTimerDeadline(0L, 0L, -1)

    private fun clearRestDeadlineIfMatches(workoutId: String, expectedEndMs: Long): Boolean =
        ObjectBox.store.callInTx {
            if (readSettingString("active_workout_id") != workoutId) return@callInTx false
            val row = settingsBox.query(AppSettingEntity_.key.equal(WorkoutTimerSettingKeys.REST_END_WALL_MS))
                .build().use { it.findFirst() } ?: return@callInTx false
            if (row.value.toLongOrNull() != expectedEndMs) return@callInTx false
            val nowMs = System.currentTimeMillis()
            putTimerSetting(WorkoutTimerSettingKeys.REST_END_WALL_MS, 0L, nowMs)
            putTimerSetting(WorkoutTimerSettingKeys.REST_END_ELAPSED_MS, 0L, nowMs)
            putTimerSetting(WorkoutTimerSettingKeys.REST_BOOT_COUNT, -1L, nowMs)
            putTimerSetting(WorkoutTimerSettingKeys.REST_PAUSED_REMAINING_MS, 0L, nowMs)
            true
        }

    private fun ensureSessionStartClock(workoutId: String, startWallMs: Long) {
        ObjectBox.store.runInTx {
            if (!isSessionActive(workoutId)) return@runInTx
            val storedWall = readSettingString(WorkoutTimerSettingKeys.START_WALL_MS)?.toLongOrNull()
            val storedElapsed = readSettingString(WorkoutTimerSettingKeys.START_ELAPSED_MS)?.toLongOrNull()
            val storedBoot = readSettingString(WorkoutTimerSettingKeys.START_BOOT_COUNT)?.toIntOrNull()
            if (storedWall == startWallMs && storedElapsed != null && storedElapsed > 0L && storedBoot != null) {
                return@runInTx
            }
            val now = clockNow()
            val elapsedAtStart = (now.elapsedRealtimeMs - (now.wallTimeMs - startWallMs)).coerceAtLeast(0L)
            putTimerSetting(WorkoutTimerSettingKeys.START_WALL_MS, startWallMs, now.wallTimeMs)
            putTimerSetting(WorkoutTimerSettingKeys.START_ELAPSED_MS, elapsedAtStart, now.wallTimeMs)
            putTimerSetting(WorkoutTimerSettingKeys.START_BOOT_COUNT, now.bootCount.toLong(), now.wallTimeMs)
        }
    }

    private fun putTimerSetting(key: String, value: Long, nowMs: Long) {
        val existing = settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }
        settingsBox.put((existing ?: AppSettingEntity()).apply {
            this.key = key
            this.value = value.toString()
            valueType = "number"
            updatedAt = nowMs
        })
    }

    private fun updateRestWakeLock(remainingMs: Long) = synchronized(restWakeLockLock) {
        if (remainingMs <= 0L) {
            releaseRestWakeLock()
            return@synchronized
        }
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val requestedMs = remainingMs.coerceAtMost(Long.MAX_VALUE - REST_WAKE_LOCK_GRACE_MS) +
            REST_WAKE_LOCK_GRACE_MS
        val timeoutMs = requestedMs
            .coerceIn(REST_WAKE_LOCK_GRACE_MS, REST_WAKE_LOCK_MAX_MS)
        val desiredHeldUntil = nowElapsedMs.coerceAtMost(Long.MAX_VALUE - timeoutMs) + timeoutMs
        val existing = restWakeLock
        if (existing?.isHeld == true) {
            val enoughForDeadline = requestedMs <= REST_WAKE_LOCK_MAX_MS &&
                restWakeLockHeldUntilElapsedMs >= desiredHeldUntil - 1_000L
            val enoughUntilLongRestRenewal = requestedMs > REST_WAKE_LOCK_MAX_MS &&
                restWakeLockHeldUntilElapsedMs - nowElapsedMs > REST_WAKE_LOCK_RENEW_WINDOW_MS
            if (enoughForDeadline || enoughUntilLongRestRenewal) return@synchronized
        }
        releaseRestWakeLock()
        restWakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:active-rest")
            .apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        restWakeLockHeldUntilElapsedMs = desiredHeldUntil
    }

    private fun releaseRestWakeLock() = synchronized(restWakeLockLock) {
        restWakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        restWakeLock = null
        restWakeLockHeldUntilElapsedMs = 0L
    }

    private fun clockNow(): WorkoutClockSnapshot = WorkoutClockSnapshot(
        wallTimeMs = System.currentTimeMillis(),
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        bootCount = currentBootCount,
    )

    private fun refreshAfterExternalRestState(workoutId: String) {
        synchronized(sessionCommandLock) {
            val owner = sessionOwner.get() ?: return
            if (owner.workoutId != workoutId || !isSessionActive(workoutId)) return
            deadlineRefreshRequested.set(true)
            signalUpdater()
        }
    }

    private fun signalUpdater() {
        updaterSignalLock.lock()
        try {
            updaterSignal.signalAll()
        } finally {
            updaterSignalLock.unlock()
        }
    }

    @Throws(InterruptedException::class)
    private fun awaitUpdaterSignal(waitMs: Long) {
        updaterSignalLock.lockInterruptibly()
        try {
            if (running && !Thread.currentThread().isInterrupted) {
                updaterSignal.await(waitMs.coerceAtLeast(100L), TimeUnit.MILLISECONDS)
            }
        } finally {
            updaterSignalLock.unlock()
        }
    }

    private fun hapticsEnabled(): Boolean {
        val raw = readSettingString("ironlog_settings") ?: return true
        return runCatching { JSONObject(raw).optBoolean("hapticFeedback", true) }.getOrDefault(true)
    }

    private fun readCurrentAccentColor(): Int {
        val raw = readSettingString("ironlog_settings").orEmpty()
        val theme = runCatching { JSONObject(raw).optString("theme", IronLogThemes.DEFAULT_THEME) }
            .getOrDefault(IronLogThemes.DEFAULT_THEME).ifBlank { IronLogThemes.DEFAULT_THEME }
        return IronLogThemes.byName(theme).accent.toArgb()
    }

    @Suppress("DEPRECATION")
    private fun triggerCountdownHaptic(secondsRemaining: Int) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        val (duration, amplitude) = when (secondsRemaining) {
            1 -> 40L to 255
            2 -> 30L to 220
            3 -> 22L to 180
            4 -> 16L to 140
            5 -> 12L to 100
            else -> 18L to 150
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude)) }
        } else {
            runCatching { vibrator.vibrate(duration) }
        }
    }

    companion object {
        internal const val CHANNEL_ID = "ironlog_workout_timer"
        internal const val NOTIFICATION_ID = 7001
        private const val REST_ALERT_GRACE_MS = 30_000L
        private const val REST_WAKE_LOCK_GRACE_MS = 5_000L
        private const val REST_WAKE_LOCK_MAX_MS = 20L * 60L * 1_000L
        private const val REST_WAKE_LOCK_RENEW_WINDOW_MS = 60_000L
        private const val OWNERSHIP_RECHECK_MS = 30_000L
        private const val MAX_CONSECUTIVE_UPDATER_FAILURES = 6
        private const val EXTRA_WORKOUT_ID = "workout_id"
        private const val EXTRA_WORKOUT_NAME = "workout_name"
        private const val EXTRA_START_MS = "start_ms"
        private val liveInstance = AtomicReference<WorkoutForegroundService?>(null)

        fun start(
            context: Context,
            workoutId: String,
            workoutName: String,
            startMs: Long,
        ): Boolean = runCatching {
            require(workoutId.isNotBlank())
            require(startMs > 0L)
            context.startForegroundService(Intent(context, WorkoutForegroundService::class.java).apply {
                putExtra(EXTRA_WORKOUT_ID, workoutId)
                putExtra(EXTRA_WORKOUT_NAME, workoutName)
                putExtra(EXTRA_START_MS, startMs)
            })
            true
        }.onFailure { Timber.e(it, "Could not start workout foreground service") }
            .getOrDefault(false)

        /** Stops only the originating session; an old screen cannot stop a newer workout. */
        fun stop(context: Context, workoutId: String) {
            liveInstance.get()?.let { service ->
                if (service.stopIfCurrentSession(workoutId)) return
            }
            val activeId = runCatching { readActiveWorkoutId() }.getOrNull()
            if (!activeId.isNullOrBlank() && activeId != workoutId) return
            cancelNotification(context)
            context.stopService(Intent(context, WorkoutForegroundService::class.java))
        }

        fun cancelNotification(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
            manager.cancel(WorkoutNotificationBridge.NOTIF_REST_COMPLETE)
        }

        fun isWorkoutNotificationAvailable(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                val channel = manager.getNotificationChannel(CHANNEL_ID)
                if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
            }
            return true
        }

        fun isRestCompletionNotificationAvailable(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WorkoutNotificationBridge.ensureRestCompleteChannel(context)
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(WorkoutNotificationBridge.REST_COMPLETE_CHANNEL_ID)
                        ?.importance == NotificationManager.IMPORTANCE_NONE
                ) return false
            }
            return true
        }

        /** Manual Skip is persisted synchronously so the updater cannot emit a late alert. */
        fun cancelRest(context: Context, workoutId: String) {
            if (workoutId.isBlank()) return
            ObjectBox.store.runInTx {
                if (readActiveWorkoutId() != workoutId) return@runInTx
                val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
                val nowMs = System.currentTimeMillis()
                listOf(
                    WorkoutTimerSettingKeys.REST_END_WALL_MS to 0L,
                    WorkoutTimerSettingKeys.REST_END_ELAPSED_MS to 0L,
                    WorkoutTimerSettingKeys.REST_BOOT_COUNT to -1L,
                    WorkoutTimerSettingKeys.REST_PAUSED_REMAINING_MS to 0L,
                ).forEach { (key, value) ->
                    val row = box.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }
                    box.put((row ?: AppSettingEntity()).apply {
                        this.key = key
                        this.value = value.toString()
                        valueType = "number"
                        updatedAt = nowMs
                    })
                }
            }
            runCatching { WorkoutNotificationBridge.clearRestTimer(context) }
                .onFailure { Timber.w(it, "Could not clear the rest completion notification") }
            onExternalRestStateChanged(context, workoutId)
        }

        /** Persists both clocks before the service/UI announce a newly running deadline. */
        fun setRestDeadline(context: Context, workoutId: String, wallEndMs: Long): Boolean {
            if (workoutId.isBlank() || wallEndMs <= 0L) return false
            val now = WorkoutTimerClock.now(context)
            val deadline = WorkoutTimerClock.deadlineAfter(now, wallEndMs - now.wallTimeMs)
            return setRestDeadline(context, workoutId, deadline)
        }

        internal fun setRestDeadline(
            context: Context,
            workoutId: String,
            deadline: WorkoutTimerDeadline,
        ): Boolean {
            if (workoutId.isBlank() || deadline.wallEndMs <= 0L) return false
            val now = WorkoutTimerClock.now(context)
            val applied = ObjectBox.store.callInTx {
                if (readActiveWorkoutId() != workoutId) return@callInTx false
                val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
                listOf(
                    WorkoutTimerSettingKeys.REST_END_WALL_MS to deadline.wallEndMs,
                    WorkoutTimerSettingKeys.REST_END_ELAPSED_MS to deadline.elapsedEndMs,
                    WorkoutTimerSettingKeys.REST_BOOT_COUNT to deadline.bootCount.toLong(),
                    WorkoutTimerSettingKeys.REST_PAUSED_REMAINING_MS to 0L,
                ).forEach { (key, value) ->
                    val row = box.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }
                    box.put((row ?: AppSettingEntity()).apply {
                        this.key = key
                        this.value = value.toString()
                        valueType = "number"
                        updatedAt = now.wallTimeMs
                    })
                }
                true
            }
            if (applied) {
                runCatching { WorkoutNotificationBridge.clearRestTimer(context) }
                    .onFailure { Timber.w(it, "Could not clear the rest completion notification") }
                onExternalRestStateChanged(context, workoutId)
            }
            return applied
        }

        internal fun readRestDeadlineSnapshot(context: Context, workoutId: String): WorkoutTimerDeadline? =
            readRestStateSnapshot(context, workoutId)?.deadline

        internal fun readRestStateSnapshot(
            context: Context,
            workoutId: String,
        ): WorkoutPersistedRestState? {
            if (workoutId.isBlank()) return null
            val persisted = ObjectBox.store.callInTx {
                val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
                fun value(key: String): String? = box.query(AppSettingEntity_.key.equal(key))
                    .build().use { it.findFirst() }?.value
                if (value("active_workout_id") != workoutId) return@callInTx null
                WorkoutPersistedRestState(
                    deadline = WorkoutTimerDeadline(
                        wallEndMs = value(WorkoutTimerSettingKeys.REST_END_WALL_MS)?.toLongOrNull() ?: 0L,
                        elapsedEndMs = value(WorkoutTimerSettingKeys.REST_END_ELAPSED_MS)?.toLongOrNull() ?: 0L,
                        bootCount = value(WorkoutTimerSettingKeys.REST_BOOT_COUNT)?.toIntOrNull() ?: -1,
                    ),
                    pausedRemainingMs = value(WorkoutTimerSettingKeys.REST_PAUSED_REMAINING_MS)
                        ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                )
            } ?: return null
            val now = WorkoutTimerClock.now(context)
            val activeDeadline = persisted.deadline?.takeIf {
                it.wallEndMs > 0L && it.remainingMs(now) > 0L
            }
            return WorkoutPersistedRestState(
                deadline = activeDeadline,
                pausedRemainingMs = if (activeDeadline == null) persisted.pausedRemainingMs else 0L,
            )
        }

        internal fun onExternalRestStateChanged(@Suppress("UNUSED_PARAMETER") context: Context, workoutId: String) {
            runCatching { liveInstance.get()?.refreshAfterExternalRestState(workoutId) }
                .onFailure { Timber.w(it, "Could not refresh the workout notification after a rest action") }
        }

        /** Startup/upgrade defense: only a real active workout may own service artifacts. */
        fun clearOrphanedNotification(context: Context) {
            val reconciliation = runCatching {
                ObjectBox.store.callInTx {
                    val settings = ObjectBox.store.boxFor(AppSettingEntity::class.java)
                    fun setting(key: String): AppSettingEntity? =
                        settings.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }
                    val activeId = setting("active_workout_id")?.value.orEmpty()
                    val workout = activeId.takeIf { it.isNotBlank() }?.let { uid ->
                        ObjectBox.store.boxFor(WorkoutEntity::class.java)
                            .query(WorkoutEntity_.uid.equal(uid)).build().use { it.findFirst() }
                    }
                    val valid = activeId.isNotBlank() && workout?.status == "active"
                    if (!valid) {
                        val keys = mutableListOf(
                            "active_workout_id",
                            "active_workout_day_id",
                            "active_workout_day_name",
                            WorkoutTimerSettingKeys.START_WALL_MS,
                            WorkoutTimerSettingKeys.START_ELAPSED_MS,
                            WorkoutTimerSettingKeys.START_BOOT_COUNT,
                            "active_workout_set_label",
                            WorkoutTimerSettingKeys.REST_END_WALL_MS,
                            WorkoutTimerSettingKeys.REST_END_ELAPSED_MS,
                            WorkoutTimerSettingKeys.REST_BOOT_COUNT,
                            WorkoutTimerSettingKeys.REST_PAUSED_REMAINING_MS,
                            "active_workout_intended_date",
                        )
                        if (activeId.isNotBlank()) {
                            keys += "active_workout_draft_$activeId"
                            keys += "active_workout_rest_override_$activeId"
                        }
                        val rows = settings.query(AppSettingEntity_.key.oneOf(keys.toTypedArray()))
                            .build().use { it.find() }
                        if (rows.isNotEmpty()) settings.remove(rows)
                    }
                    activeId to valid
                }
            }.onFailure { Timber.w(it, "Could not reconcile workout notification ownership") }
                .getOrNull() ?: return
            if (!reconciliation.second) {
                reconciliation.first.takeIf { it.isNotBlank() }?.let {
                    runCatching { WorkoutNotificationActionInbox.clearSession(it) }
                }
                cancelNotification(context)
                context.stopService(Intent(context, WorkoutForegroundService::class.java))
            }
        }

        private fun readActiveWorkoutId(): String? {
            val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
            return box.query(AppSettingEntity_.key.equal("active_workout_id"))
                .build().use { it.findFirst() }?.value
        }
    }

    private fun stopIfCurrentSession(workoutId: String): Boolean {
        if (workoutId.isBlank()) return false
        return synchronized(sessionCommandLock) {
            val owner = sessionOwner.get() ?: return@synchronized false
            if (owner.workoutId != workoutId) return@synchronized false
            if (sessionOwner.compareAndSet(owner, null)) {
                terminate(removeNotification = true)
                true
            } else {
                false
            }
        }
    }

    private fun terminateIfOwner(owner: SessionOwner) {
        synchronized(sessionCommandLock) {
            if (sessionOwner.compareAndSet(owner, null)) terminate(removeNotification = true)
        }
    }
}
