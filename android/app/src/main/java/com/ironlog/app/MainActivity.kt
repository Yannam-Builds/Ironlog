package com.ironlog.app

import android.os.Build
import android.os.Bundle
import android.app.NotificationManager
import android.content.Context
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.zoontek.rnbootsplash.RNBootSplash

class MainActivity : ReactActivity() {
  companion object {
    private const val ONGOING_WORKOUT_NOTIFICATION_ID = "ironlog-ongoing-workout"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    RNBootSplash.init(this, R.style.BootTheme)
    super.onCreate(null)
    applyPreferredRefreshRate()
  }

  override fun onResume() {
    super.onResume()
    applyPreferredRefreshRate()
  }

  override fun getMainComponentName(): String = "main"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  override fun invokeDefaultOnBackPressed() {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
      if (!moveTaskToBack(false)) {
        super.invokeDefaultOnBackPressed()
      }
      return
    }
    super.invokeDefaultOnBackPressed()
  }

  override fun onStop() {
    super.onStop()
    // If task is no longer in recents, clear any stale ongoing notification.
    if (isFinishing) {
      clearOngoingWorkoutNotificationSafely()
    }
  }

  override fun onDestroy() {
    clearOngoingWorkoutNotificationSafely()
    super.onDestroy()
  }

  private fun applyPreferredRefreshRate() {
    val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      display
    } else {
      @Suppress("DEPRECATION")
      windowManager.defaultDisplay
    }

    val refreshRate = currentDisplay?.refreshRate ?: return
    if (refreshRate <= 0f) return

    val params = window.attributes
    params.preferredRefreshRate = refreshRate
    window.attributes = params
  }

  private fun clearOngoingWorkoutNotificationSafely() {
    try {
      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      // Notifee stores the app-supplied string id as the notification tag on Android.
      // Keep cleanup scoped to the live workout notification so reminder/test notifications survive.
      notificationManager?.cancel(ONGOING_WORKOUT_NOTIFICATION_ID, 0)
      notificationManager?.cancel(ONGOING_WORKOUT_NOTIFICATION_ID.hashCode())
    } catch (_: Exception) {
      // no-op
    }
  }
}
