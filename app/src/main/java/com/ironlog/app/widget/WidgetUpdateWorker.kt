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
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity_
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
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

            // Load history from ObjectBox
            val workoutBox = box.boxFor(WorkoutEntity::class.java)
            val exerciseBox = box.boxFor(WorkoutExerciseEntity::class.java)
            val libraryExerciseBox = box.boxFor(ExerciseEntity::class.java)
            val setBox = box.boxFor(WorkoutSetEntity::class.java)

            val workouts = workoutBox.query(WorkoutEntity_.status.equal("completed"))
                .orderDesc(WorkoutEntity_.startedAt)
                .build().use { it.find() }

            // Batch-load all workout exercises and sets in two queries, then map by parent UID.
            val workoutUids = workouts.map { it.uid }.toTypedArray()
            val allExercises: List<WorkoutExerciseEntity> = if (workoutUids.isEmpty()) emptyList()
            else exerciseBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutUids))
                .order(WorkoutExerciseEntity_.orderIndex).build().use { it.find() }
            val exerciseUids = allExercises.map { it.uid }.toTypedArray()
            val allSets: List<WorkoutSetEntity> = if (exerciseUids.isEmpty()) emptyList()
            else setBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(exerciseUids))
                .order(WorkoutSetEntity_.setIndex).build().use { it.find() }
            // Batch-load all exercise library names in one query.
            val libraryUids = allExercises.map { it.exerciseUid }.distinct().toTypedArray()
            val libraryNameByUid: Map<String, String> = if (libraryUids.isEmpty()) emptyMap()
            else libraryExerciseBox.query(ExerciseEntity_.uid.oneOf(libraryUids))
                .build().use { q -> q.find() }.associate { it.uid to it.name }
            val exercisesByWorkout = allExercises.groupBy { it.workoutUid }
            val setsByExercise = allSets.groupBy { it.workoutExerciseUid }

            val history: List<HistoryEntry> = workouts.map { w ->
                val historyExercises = (exercisesByWorkout[w.uid] ?: emptyList()).map { we ->
                    val sets = setsByExercise[we.uid] ?: emptyList()
                    HistoryExercise(
                        id = we.uid,
                        exerciseId = we.exerciseUid,
                        name = libraryNameByUid[we.exerciseUid] ?: "Exercise",
                        sets = sets.map { s ->
                            HistoryExerciseSet(
                                id = s.uid,
                                weight = s.weight,
                                reps = s.reps,
                                type = when {
                                    s.isWarmup  -> "warmup"
                                    s.isDropset -> "dropset"
                                    else        -> "normal"
                                },
                                rpe = s.rpe,
                                rir = s.rir,
                                restSeconds = s.restSeconds,
                            )
                        },
                    )
                }
                HistoryEntry(
                    id = w.uid,
                    date = java.time.Instant.ofEpochMilli(w.startedAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate().toString(),
                    duration = w.durationSeconds,
                    rating = w.rating,
                    name = w.name,
                    imported = w.imported,
                    exercises = historyExercises,
                )
            }

            // Load weekly goal from settings
            val settingBox = box.boxFor(com.ironlog.app.data.objectbox.AppSettingEntity::class.java)
            val weeklyGoal = settingBox.query(AppSettingEntity_.key.equal("weeklyGoalDays"))
                .build().use { it.findFirst()?.value?.toIntOrNull() } ?: 4

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
