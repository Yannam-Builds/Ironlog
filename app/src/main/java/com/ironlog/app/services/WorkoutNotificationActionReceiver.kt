package com.ironlog.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/** Fast, non-exported receiver for controls that must work while the phone is locked. */
class WorkoutNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val sessionId = intent?.getStringExtra(EXTRA_WORKOUT_ID).orEmpty()
        val actionId = intent?.action.orEmpty()
        val expectedRestEndWallMs = intent?.getLongExtra(EXTRA_REST_END_WALL_MS, 0L) ?: 0L
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MS) {
                    val result = WorkoutNotificationActionInbox.commitRestControl(
                        sessionId = sessionId,
                        actionId = actionId,
                        now = WorkoutTimerClock.now(context),
                        expectedRestEndWallMs = expectedRestEndWallMs,
                    )
                    if (result.applied) {
                        WorkoutNotificationBridge.clearRestTimer(context)
                        WorkoutForegroundService.onExternalRestStateChanged(context, sessionId)
                    }
                }
            } catch (error: Exception) {
                Timber.e(error, "Could not apply workout notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        internal const val EXTRA_WORKOUT_ID = "workout_id"
        internal const val EXTRA_REST_END_WALL_MS = "rest_end_wall_ms"
        private const val RECEIVER_TIMEOUT_MS = 5_000L
    }
}
