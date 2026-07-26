package com.ironlog.app.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ironlog.app.data.repository.ImportExportRepository
import com.ironlog.app.data.repository.SettingsRepository
import java.io.File

/** Native replacement target for AppRegistry.registerHeadlessTask('IronlogBackupTask', ...). */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            val payload = ImportExportRepository().exportDatabase().toString()
            val dir = File(applicationContext.filesDir, "backups").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val out = File(dir, "ironlog_backup_auto_$stamp.json")
            out.writeText(payload)
            pruneOldAutoBackups(dir)
            SettingsRepository().setString("last_auto_backup_ms", System.currentTimeMillis().toString())
            Result.success()
        }.getOrElse {
            // After 3 attempts give up rather than retrying a corrupt store indefinitely
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    private suspend fun pruneOldAutoBackups(dir: File) {
        val settings = SettingsRepository()
        val keep = settings.getSettingNumber("auto_backup_retention")?.toInt()?.coerceIn(1, 60) ?: 14
        val all = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("ironlog_backup_auto_") && it.extension.equals("json", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
        all.drop(keep).forEach { runCatching { it.delete() } }
    }
}
