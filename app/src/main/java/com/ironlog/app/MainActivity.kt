package com.ironlog.app

import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.services.NotificationActionRouter
import com.ironlog.app.services.PendingWorkoutNotificationBridge
import com.ironlog.app.ui.IronLogApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native Android entry point equivalent to index.js + App.js.
 * React Native's AppRegistry is replaced by Activity startup, and the headless
 * Notifee task is represented by NotificationActionReceiver/BackupWorker stubs.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force fully transparent nav bar on ALL navigation modes (gesture AND 3-button).
        // The default SystemBarStyle.auto() applies a translucent scrim on 3-button nav —
        // that scrim is the "chin." dark(transparent) skips it entirely.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        requestMaxRefreshRate()
        handleIntentRouting(intent)
        setContent { IronLogApp() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val pending = PendingWorkoutNotificationBridge.consumePendingAction(this@MainActivity)
            if (pending?.actionId != null) {
                NotificationActionRouter.handleNotificationAction(
                    actionId = pending.actionId,
                    payload = pending,
                    isForeground = true,
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentRouting(intent)
    }

    private fun handleIntentRouting(intent: android.content.Intent?) {
        val explicitRoute = intent?.getStringExtra("ironlog_route").orEmpty()
        val workoutAction = intent?.getStringExtra("actionId").orEmpty()
        val navigateToWorkout = intent?.getBooleanExtra("navigate_to_workout", false) == true
        lifecycleScope.launch(Dispatchers.IO) {
            val repo = SettingsRepository()
            if (workoutAction in setOf(
                    NotificationActionRouter.Actions.SKIP_REST,
                    NotificationActionRouter.Actions.ADD_30S,
                    NotificationActionRouter.Actions.FINISH_WORKOUT,
                )
            ) {
                repo.setString("pending_workout_action", workoutAction)
            }
            val route = when {
                explicitRoute.isNotBlank() -> explicitRoute
                workoutAction.isNotBlank() -> {
                    val dayId = repo.getString("active_workout_day_id").orEmpty()
                    if (dayId.isNotBlank()) "ActiveWorkout/${Uri.encode(dayId)}" else "ActiveWorkout"
                }
                navigateToWorkout -> {
                    val dayId = repo.getString("active_workout_day_id").orEmpty()
                    if (dayId.isNotBlank()) "ActiveWorkout/${Uri.encode(dayId)}" else "ActiveWorkout"
                }
                else -> ""
            }
            if (route.isNotBlank()) repo.setString("pending_nav_route", route)
        }
    }

    /**
     * Requests the highest refresh rate/mode available on the current display so
     * all Compose content and interactions can run at the panel's maximum Hz.
     */
    private fun requestMaxRefreshRate() {
        val display = if (SDK_INT >= Build.VERSION_CODES.R) {
            this.display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return

        val maxMode = display.supportedModes.maxByOrNull { it.refreshRate } ?: return

        // Prefer the physical display mode with highest refresh rate (API 23+).
        if (SDK_INT >= Build.VERSION_CODES.M) {
            val attrs = window.attributes
            attrs.preferredDisplayModeId = maxMode.modeId
            attrs.preferredRefreshRate = maxMode.refreshRate
            window.attributes = attrs
        } else {
            @Suppress("DEPRECATION")
            window.attributes = window.attributes.apply {
                preferredRefreshRate = maxMode.refreshRate
            }
        }

    }
}
