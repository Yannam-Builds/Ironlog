package com.ironlog.app

import android.app.Application
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutImportProvenanceMigration
import com.ironlog.app.data.objectbox.WorkoutExerciseSnapshotMigration
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.AppearanceBackupRegistry
import com.ironlog.app.data.repository.reconcileCurrentAthleteBodyweight
import com.ironlog.app.services.BackupScheduler
import com.ironlog.app.services.NotificationCoordinator
import com.ironlog.app.services.WorkoutForegroundService
import com.ironlog.app.services.WorkoutNotificationActionInbox
import com.ironlog.app.services.WorkoutNotificationBridge
import com.ironlog.app.widget.WidgetUpdateWorker
import com.ironlog.app.ui.theme.SharedPreferencesAppearanceBackupStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class IronLogApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val acceptedMutations = AcceptedMutationCoordinator(appScope)
    internal val acceptedMutationCount get() = acceptedMutations.inFlightCount

    /**
     * Starts work the user has already confirmed in a process-owned scope. Navigation away from
     * the initiating screen must not cancel an accepted import or destructive mutation halfway.
     */
    internal fun launchAcceptedMutation(block: suspend () -> Unit): Job =
        acceptedMutations.launch(block)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        AppearanceBackupRegistry.install(SharedPreferencesAppearanceBackupStore(this))
        ObjectBox.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                com.ironlog.app.services.NotificationAppVisibility.setForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                com.ironlog.app.services.NotificationAppVisibility.setForeground(false)
            }
        })
        // An update/process restart can retain a notification created by an older service
        // instance. Durable active-workout state is authoritative.
        WorkoutForegroundService.clearOrphanedNotification(this)
        WorkoutNotificationBridge.clearWorkout(this)
        WorkoutNotificationActionInbox.clearLegacyKeys()
        appScope.launch {
            runCatching {
                val raw = SettingsRepository().getString("ironlog_settings")
                val provider = raw?.let { org.json.JSONObject(it).optString("cloudAiProviderPreset") }
                com.ironlog.app.domain.intelligence.CloudAiKeyStore.migrateLegacy(this@IronLogApplication, provider)
            }.onFailure { Timber.w("Secure credential migration unavailable; re-enter a provider key in Settings.") }
            // Idempotently migrates legacy onboarding/profile bodyweight into the canonical kg
            // measurement timeline before any background intelligence/widget refresh is queued.
            reconcileCurrentAthleteBodyweight(ObjectBox.store)
            WorkoutImportProvenanceMigration.run(ObjectBox.store)
            WorkoutExerciseSnapshotMigration.run(ObjectBox.store)
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
                NotificationCoordinator.reconcile(this@IronLogApplication)
            }.onFailure { Timber.e(it, "Could not reconcile background schedules at startup") }
        }
    }

}
