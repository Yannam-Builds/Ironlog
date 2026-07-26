package com.ironlog.app

import android.app.Application
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutImportProvenanceMigration
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.services.BackupScheduler
import com.ironlog.app.services.ReminderScheduler
import com.ironlog.app.widget.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class IronLogApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        ObjectBox.init(this)
        appScope.launch {
            WorkoutImportProvenanceMigration.run()
            WidgetUpdateWorker.enqueuePeriodic(this@IronLogApplication)
        }
        scheduleAutoBackupIfEnabled()
    }

    private fun scheduleAutoBackupIfEnabled() {
        appScope.launch {
            runCatching {
                val repo = SettingsRepository()
                val autoBackupEnabled = repo.getBoolean("auto_backup_enabled", false)
                if (autoBackupEnabled) {
                    val hour = repo.getString("auto_backup_hour")?.toIntOrNull() ?: 3
                    val minute = repo.getString("auto_backup_minute")?.toIntOrNull() ?: 0
                    BackupScheduler.scheduleDaily(this@IronLogApplication, hour, minute)
                }
                val notificationsEnabled = repo.getBoolean("notifications_enabled", false)
                if (notificationsEnabled) {
                    val reminderMinutes = repo.getSettingNumber("dailyReminderTimeMinutes")
                        ?.toInt()
                        ?.takeIf { it in 0 until 24 * 60 }
                        ?: (8 * 60)
                    ReminderScheduler.scheduleDailyReminder(
                        this@IronLogApplication,
                        reminderMinutes / 60,
                        reminderMinutes % 60,
                    )
                } else {
                    ReminderScheduler.cancelDailyReminder(this@IronLogApplication)
                }
            }
        }
    }

}
