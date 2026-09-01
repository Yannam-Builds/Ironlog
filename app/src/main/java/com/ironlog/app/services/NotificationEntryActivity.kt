package com.ironlog.app.services

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.ironlog.app.MainActivity
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.navigation.isAllowedPendingRoute
import timber.log.Timber

/**
 * Private notification entry point. SystemUI can launch the immutable PendingIntent created by
 * IronLog, while other apps cannot invoke this component or inject workout/action identifiers.
 */
class NotificationEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        persistNotificationRequest(intent)
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }

    private fun persistNotificationRequest(source: Intent?) {
        val requestedAction = source?.getStringExtra(EXTRA_ACTION_ID).orEmpty()
        val actionId = requestedAction.takeIf {
            it == NotificationActionRouter.Actions.FINISH_WORKOUT
        }
        val navigateToWorkout = source?.getBooleanExtra(EXTRA_NAVIGATE_TO_WORKOUT, false) == true
        if (navigateToWorkout || requestedAction.isNotBlank()) {
            if (requestedAction.isNotBlank() && actionId == null) {
                Timber.w("Ignoring unsupported workout notification action")
                return
            }
            runCatching {
                WorkoutNotificationActionInbox.commitNotificationTap(
                    source?.getStringExtra(EXTRA_WORKOUT_ID).orEmpty(),
                    actionId,
                )
            }.onFailure { error ->
                Timber.e(error, "Could not commit a workout notification tap")
            }
            return
        }

        val route = source?.getStringExtra(EXTRA_ROUTE).orEmpty()
        if (isAllowedPendingRoute(route)) {
            runCatching { SettingsRepository().setStringBlocking("pending_nav_route", route) }
                .onFailure { error -> Timber.e(error, "Could not persist a notification route") }
        }
    }

    companion object {
        internal const val EXTRA_ROUTE = "ironlog_route"
        internal const val EXTRA_ACTION_ID = "actionId"
        internal const val EXTRA_NAVIGATE_TO_WORKOUT = "navigate_to_workout"
        internal const val EXTRA_WORKOUT_ID = "workout_id"
    }
}
