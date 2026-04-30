package com.ironlog.app.backup

import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.facebook.react.HeadlessJsTaskService

class IronlogBackupWorker(appContext: android.content.Context, params: WorkerParameters) : Worker(appContext, params) {
  override fun doWork(): Result {
    val reason = inputData.getString("reason") ?: "background"

    val serviceIntent = Intent(applicationContext, IronlogBackupTaskService::class.java).apply {
      putExtra("reason", reason)
    }
    applicationContext.startService(serviceIntent)
    HeadlessJsTaskService.acquireWakeLockNow(applicationContext)

    return Result.success()
  }

  companion object {
    const val WORK_NAME = "ironlog_encrypted_backup_work"
  }
}
