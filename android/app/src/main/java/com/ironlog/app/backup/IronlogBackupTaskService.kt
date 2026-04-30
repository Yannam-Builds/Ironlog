package com.ironlog.app.backup

import android.content.Intent
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

class IronlogBackupTaskService : HeadlessJsTaskService() {
  override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig {
    val data = Arguments.createMap().apply {
      putString("reason", intent?.getStringExtra("reason") ?: "background")
    }
    return HeadlessJsTaskConfig(
      "IronlogBackupTask",
      data,
      300000,
      true
    )
  }
}
