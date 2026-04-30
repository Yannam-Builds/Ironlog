package com.ironlog.app.backup

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.Calendar
import java.util.concurrent.TimeUnit

class IronlogBackupSchedulerModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  override fun getName(): String = "IronlogBackupScheduler"

  companion object {
    const val PERIODIC_WORK_NAME = "ironlog_scheduled_daily_backup"
  }

  @ReactMethod
  fun scheduleBackup(reason: String, delayMs: Double, promise: Promise) {
    try {
      val inputData = Data.Builder()
        .putString("reason", reason)
        .build()

      val request = OneTimeWorkRequestBuilder<IronlogBackupWorker>()
        .setInitialDelay(delayMs.toLong().coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        .setInputData(inputData)
        .addTag(IronlogBackupWorker.WORK_NAME)
        .build()

      WorkManager.getInstance(reactApplicationContext)
        .enqueueUniqueWork(IronlogBackupWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("schedule_failed", error)
    }
  }

  /**
   * Schedule a repeating daily backup at hour:minute using PeriodicWorkRequest.
   * WorkManager handles the 24-hour recurrence; initial delay is computed to
   * the next occurrence of the requested time.
   */
  @ReactMethod
  fun schedulePeriodicBackup(hour: Int, minute: Int, promise: Promise) {
    try {
      val now = Calendar.getInstance()
      val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(Calendar.MINUTE, minute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      if (!target.after(now)) {
        target.add(Calendar.DAY_OF_YEAR, 1)
      }
      val initialDelayMs = target.timeInMillis - now.timeInMillis

      val inputData = Data.Builder()
        .putString("reason", "scheduled")
        .build()

      val request = PeriodicWorkRequestBuilder<IronlogBackupWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
        .setInputData(inputData)
        .addTag(PERIODIC_WORK_NAME)
        .build()

      WorkManager.getInstance(reactApplicationContext)
        .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("schedule_periodic_failed", error)
    }
  }

  @ReactMethod
  fun cancelPeriodicBackup(promise: Promise) {
    try {
      WorkManager.getInstance(reactApplicationContext)
        .cancelUniqueWork(PERIODIC_WORK_NAME)
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("cancel_periodic_failed", error)
    }
  }

  @ReactMethod
  fun cancelScheduledBackup(promise: Promise) {
    try {
      WorkManager.getInstance(reactApplicationContext)
        .cancelUniqueWork(IronlogBackupWorker.WORK_NAME)
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("cancel_failed", error)
    }
  }

  @ReactMethod
  fun isBatteryOptimizationIgnored(promise: Promise) {
    try {
      val pm = reactApplicationContext.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
      promise.resolve(pm.isIgnoringBatteryOptimizations(reactApplicationContext.packageName))
    } catch (error: Exception) {
      promise.resolve(false)
    }
  }

  @ReactMethod
  fun requestBatteryOptimizationExemption(promise: Promise) {
    try {
      val intent = Intent().apply {
        action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        data = Uri.parse("package:${reactApplicationContext.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      reactApplicationContext.startActivity(intent)
      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject("battery_exemption_failed", error)
    }
  }
}
