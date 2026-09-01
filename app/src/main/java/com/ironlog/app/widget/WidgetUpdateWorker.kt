package com.ironlog.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that refreshes all IronLog widgets every 30 minutes.
 *
 * Also call [enqueueOneTime] after any workout is saved to push a debounced near-immediate update.
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val box = ObjectBox.store

            val history = com.ironlog.app.data.repository.HistoryRepository(box).completedSnapshotBlocking()

            // Load weekly goal from settings
            val settingBox = box.boxFor(com.ironlog.app.data.objectbox.AppSettingEntity::class.java)
            val weeklyGoal = settingBox.query(AppSettingEntity_.key.equal("ironlog_settings"))
                .build().use { query ->
                    query.findFirst()?.value?.let { raw ->
                        runCatching { org.json.JSONObject(raw).optInt("weeklyGoalDays", 4) }.getOrNull()
                    }
                }?.coerceIn(1, 7) ?: 4

            // Build shared WidgetState
            val repo = WidgetDataRepository(applicationContext, box)
            val state = repo.buildWidgetState(history, weeklyGoal)

            // Write state to all BadgeWidget instances
            val badgeIds = GlanceAppWidgetManager(applicationContext)
                .getGlanceIds(BadgeWidget::class.java)
            badgeIds.forEach { id ->
                updateAppWidgetState(applicationContext, WidgetStateDefinition, id) { state }
            }

            // Write state to all DashboardWidget instances
            val dashIds = GlanceAppWidgetManager(applicationContext)
                .getGlanceIds(DashboardWidget::class.java)
            dashIds.forEach { id ->
                updateAppWidgetState(applicationContext, WidgetStateDefinition, id) { state }
            }

            // Write state to all WarRoomWidget instances
            val warIds = GlanceAppWidgetManager(applicationContext)
                .getGlanceIds(WarRoomWidget::class.java)
            warIds.forEach { id ->
                updateAppWidgetState(applicationContext, WidgetStateDefinition, id) { state }
            }

            // Trigger UI recomposition for all widget instances
            BadgeWidget().updateAll(applicationContext)
            DashboardWidget().updateAll(applicationContext)
            WarRoomWidget().updateAll(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "ironlog_widget_refresh"
        private const val ONETIME_WORK_NAME  = "ironlog_widget_refresh_now"

        /**
         * Enqueue the periodic 30-minute refresh. Call once from Application.onCreate().
         */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Enqueue a debounced one-time refresh (e.g. after workout saved).
         * Uses REPLACE policy with a 2-second initial delay to natively debounce rapid-fire triggers
         * (e.g. rapid database writes during active tracking).
         */
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(2, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONETIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
