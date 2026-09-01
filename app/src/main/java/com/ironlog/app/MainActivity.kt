package com.ironlog.app

import android.os.Build.VERSION.SDK_INT
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ironlog.app.ui.IronLogApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Native Android entry point equivalent to index.js + App.js.
 * Exported launcher entry point. Privileged notification requests are persisted
 * by the app-private NotificationEntryActivity before this activity is opened.
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
        setContent { IronLogApp() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Android permission/channel changes can happen while this process stays alive.
        // Ordinary reconciliation uses KEEP, preserving healthy or already-due reminder work.
        lifecycleScope.launch(Dispatchers.IO) {
            // Opening IronLog fulfills the purpose of a re-engagement reminder. Serialize the
            // dismissal with any worker that may currently be selecting/posting one.
            runCatching {
                com.ironlog.app.services.WorkoutNotificationBridge
                    .acknowledgeAppForeground(this@MainActivity)
            }.onFailure { Timber.w(it, "Could not dismiss the visible reminder on resume") }
            runCatching { com.ironlog.app.services.NotificationCoordinator.reconcile(this@MainActivity) }
                .onFailure { Timber.w(it, "Could not reconcile notifications on resume") }
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
