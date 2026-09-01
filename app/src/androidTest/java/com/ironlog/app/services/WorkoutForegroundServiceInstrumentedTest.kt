package com.ironlog.app.services

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutForegroundServiceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)
    private val repository by lazy { SettingsRepository() }

    @Before
    fun setUp() = runBlocking {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
            ).close()
        }
        repository.removeSetting("active_workout_id")
        repository.removeSetting("active_workout_start_ms")
        repository.removeSetting("active_workout_start_elapsed_ms")
        repository.removeSetting("active_workout_start_boot_count")
        repository.removeSetting("active_workout_rest_end_ms")
        repository.removeSetting("active_workout_rest_end_elapsed_ms")
        repository.removeSetting("active_workout_rest_boot_count")
        repository.removeSetting("active_workout_rest_paused_remaining_ms")
        WorkoutForegroundService.cancelNotification(context)
    }

    @After
    fun tearDown() = runBlocking {
        val activeId = repository.getActiveWorkoutId().orEmpty()
        repository.removeSetting("active_workout_id")
        repository.removeSetting("active_workout_start_ms")
        repository.removeSetting("active_workout_start_elapsed_ms")
        repository.removeSetting("active_workout_start_boot_count")
        repository.removeSetting("active_workout_rest_end_ms")
        repository.removeSetting("active_workout_rest_end_elapsed_ms")
        repository.removeSetting("active_workout_rest_boot_count")
        repository.removeSetting("active_workout_rest_paused_remaining_ms")
        if (activeId.isNotBlank()) WorkoutForegroundService.stop(context, activeId)
        WorkoutForegroundService.cancelNotification(context)
    }

    @Test
    fun discardStateRemovalCannotBeFollowedByALateNotification() = runBlocking {
        repository.setString("active_workout_id", "instrumented-session")
        repository.setString("active_workout_start_ms", System.currentTimeMillis().toString())
        assertTrue(WorkoutForegroundService.start(
            context,
            "instrumented-session",
            "Instrumented workout",
            System.currentTimeMillis(),
        ))
        assertTrue(awaitNotification(present = true))

        repository.removeSetting("active_workout_id")
        repository.removeSetting("active_workout_start_ms")
        WorkoutForegroundService.stop(context, "instrumented-session")

        assertTrue(awaitNotification(present = false))
        Thread.sleep(1_500L)
        assertFalse(hasWorkoutNotification())
    }

    @Test
    fun staleStartWithoutDurableSessionNeverPosts() {
        WorkoutForegroundService.start(
            context,
            "missing-session",
            "Stale workout",
            System.currentTimeMillis(),
        )

        assertTrue(awaitNotification(present = false))
        Thread.sleep(1_500L)
        assertFalse(hasWorkoutNotification())
    }

    @Test
    fun upgradeCleanupRemovesNotificationAndKeysForAnAbandonedWorkout() = runBlocking {
        val workout = WorkoutEntity().apply {
            uid = "stale-abandoned-session"
            name = "Old workout"
            status = "abandoned"
            startedAt = System.currentTimeMillis() - 60_000L
            completedAt = System.currentTimeMillis()
            createdAt = startedAt
            updatedAt = completedAt ?: startedAt
        }
        ObjectBox.store.boxFor(WorkoutEntity::class.java).put(workout)
        repository.setString("active_workout_id", workout.uid)
        repository.setString("active_workout_start_ms", workout.startedAt.toString())
        assertTrue(WorkoutForegroundService.start(context, workout.uid, workout.name, workout.startedAt))
        assertTrue(awaitNotification(present = true))

        WorkoutForegroundService.clearOrphanedNotification(context)

        assertTrue(awaitNotification(present = false))
        assertTrue(repository.getActiveWorkoutId().isNullOrBlank())
        ObjectBox.store.boxFor(WorkoutEntity::class.java).remove(workout)
        Unit
    }

    @Test
    fun pausedRestSurvivesRecreationAndResumeCannotLeaveSplitBrainState() = runBlocking {
        val workoutId = "paused-rest-session"
        repository.setString("active_workout_id", workoutId)
        val initialNow = WorkoutTimerClock.now(context)
        assertTrue(WorkoutForegroundService.setRestDeadline(
            context,
            workoutId,
            WorkoutTimerClock.deadlineAfter(initialNow, 60_000L),
        ))

        val paused = WorkoutNotificationActionInbox.commitRestControl(
            sessionId = workoutId,
            actionId = NotificationActionRouter.Actions.PAUSE_REST,
            now = WorkoutTimerClock.now(context),
            enqueueUiSync = false,
        )
        assertTrue(paused.applied)
        assertTrue(paused.pausedRemainingMs > 0L)
        val extendedPaused = WorkoutNotificationActionInbox.commitRestControl(
            sessionId = workoutId,
            actionId = NotificationActionRouter.Actions.ADD_30S,
            now = WorkoutTimerClock.now(context),
            enqueueUiSync = false,
        )
        assertTrue(extendedPaused.applied)
        assertTrue(extendedPaused.pausedRemainingMs >= paused.pausedRemainingMs + 30_000L)

        val recreated = WorkoutForegroundService.readRestStateSnapshot(context, workoutId)
        assertTrue(recreated?.deadline == null)
        assertTrue((recreated?.pausedRemainingMs ?: 0L) >= extendedPaused.pausedRemainingMs)

        val resumedDeadline = WorkoutTimerClock.deadlineAfter(
            WorkoutTimerClock.now(context),
            recreated!!.pausedRemainingMs,
        )
        assertTrue(WorkoutForegroundService.setRestDeadline(context, workoutId, resumedDeadline))
        assertTrue(repository.getString("active_workout_rest_paused_remaining_ms") == "0")
        val extendedActive = WorkoutNotificationActionInbox.commitRestControl(
            sessionId = workoutId,
            actionId = NotificationActionRouter.Actions.ADD_30S,
            now = WorkoutTimerClock.now(context),
            enqueueUiSync = false,
        )
        assertTrue(extendedActive.applied)
        assertTrue(extendedActive.wallEndMs > resumedDeadline.wallEndMs)
        assertTrue(extendedActive.pausedRemainingMs == 0L)
    }

    @Test
    fun delayedOldSessionStartCannotTearDownTheCurrentSession() = runBlocking {
        val startMs = System.currentTimeMillis()
        repository.setString("active_workout_id", "session-b")
        repository.setString("active_workout_start_ms", startMs.toString())
        assertTrue(WorkoutForegroundService.start(context, "session-b", "Session B", startMs))
        assertTrue(awaitNotification(present = true))

        assertTrue(WorkoutForegroundService.start(context, "session-a", "Stale A", startMs - 1_000L))
        Thread.sleep(1_500L)

        val active = manager.activeNotifications.firstOrNull {
            it.id == WorkoutForegroundService.NOTIFICATION_ID
        }
        assertTrue(active != null)
        assertTrue(active?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)
            ?.contains("Session B") == true)
    }

    @Test
    fun lockedScreenReceiverExtendsThenSkipsTheAuthoritativeRestWithoutOpeningActivity() = runBlocking {
        val workoutId = "receiver-session"
        val startMs = System.currentTimeMillis()
        repository.setString("active_workout_id", workoutId)
        repository.setString("active_workout_start_ms", startMs.toString())
        assertTrue(WorkoutForegroundService.start(context, workoutId, "Receiver workout", startMs))
        assertTrue(awaitNotification(present = true))
        val initialEnd = System.currentTimeMillis() + 8_000L
        assertTrue(WorkoutForegroundService.setRestDeadline(context, workoutId, initialEnd))

        context.sendBroadcast(Intent(context, WorkoutNotificationActionReceiver::class.java).apply {
            action = NotificationActionRouter.Actions.ADD_30S
            putExtra(WorkoutNotificationActionReceiver.EXTRA_WORKOUT_ID, workoutId)
            putExtra(WorkoutNotificationActionReceiver.EXTRA_REST_END_WALL_MS, initialEnd)
        })
        assertTrue(awaitSetting("active_workout_rest_end_ms") {
            (it?.toLongOrNull() ?: 0L) >= initialEnd + 28_000L
        })
        val extendedEnd = repository.getString("active_workout_rest_end_ms")?.toLongOrNull() ?: 0L

        // A captured action from the previous countdown must not mutate the newer rest.
        context.sendBroadcast(Intent(context, WorkoutNotificationActionReceiver::class.java).apply {
            action = NotificationActionRouter.Actions.SKIP_REST
            putExtra(WorkoutNotificationActionReceiver.EXTRA_WORKOUT_ID, workoutId)
            putExtra(WorkoutNotificationActionReceiver.EXTRA_REST_END_WALL_MS, initialEnd)
        })
        Thread.sleep(500L)
        assertTrue((repository.getString("active_workout_rest_end_ms")?.toLongOrNull() ?: 0L) == extendedEnd)

        context.sendBroadcast(Intent(context, WorkoutNotificationActionReceiver::class.java).apply {
            action = NotificationActionRouter.Actions.SKIP_REST
            putExtra(WorkoutNotificationActionReceiver.EXTRA_WORKOUT_ID, workoutId)
            putExtra(WorkoutNotificationActionReceiver.EXTRA_REST_END_WALL_MS, extendedEnd)
        })
        assertTrue(awaitSetting("active_workout_rest_end_ms") { it == "0" })
        Thread.sleep(1_500L)
        assertFalse(manager.activeNotifications.any { it.id == WorkoutNotificationBridge.NOTIF_REST_COMPLETE })
    }

    @Test
    fun notificationChannelsHaveSeparateSemanticImportance() {
        WorkoutForegroundService.isRestCompletionNotificationAvailable(context)
        WorkoutNotificationBridge.ensureReminderChannel(context)
        val startMs = System.currentTimeMillis()
        runBlocking {
            repository.setString("active_workout_id", "channel-session")
            repository.setString("active_workout_start_ms", startMs.toString())
        }
        WorkoutForegroundService.start(context, "channel-session", "Channels", startMs)
        assertTrue(awaitNotification(present = true))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertTrue(manager.getNotificationChannel(WorkoutForegroundService.CHANNEL_ID).importance <=
                NotificationManager.IMPORTANCE_LOW)
            assertTrue(manager.getNotificationChannel(WorkoutNotificationBridge.REST_COMPLETE_CHANNEL_ID).importance >=
                NotificationManager.IMPORTANCE_DEFAULT)
            assertTrue(manager.getNotificationChannel(WorkoutNotificationBridge.REMINDER_CHANNEL_ID).importance >=
                NotificationManager.IMPORTANCE_DEFAULT)
        }
    }

    private fun awaitNotification(present: Boolean): Boolean {
        repeat(40) {
            if (hasWorkoutNotification() == present) return true
            Thread.sleep(100L)
        }
        return false
    }

    private fun hasWorkoutNotification(): Boolean =
        manager.activeNotifications.any { it.id == WorkoutForegroundService.NOTIFICATION_ID }

    private suspend fun awaitSetting(key: String, predicate: (String?) -> Boolean): Boolean {
        repeat(50) {
            if (predicate(repository.getString(key))) return true
            Thread.sleep(100L)
        }
        return false
    }
}
