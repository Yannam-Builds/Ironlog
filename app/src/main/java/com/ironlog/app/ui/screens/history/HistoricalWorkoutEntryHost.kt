package com.ironlog.app.ui.screens.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ironlog.app.data.model.HistoricalWorkoutSaveResult
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.HistoryRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.domain.training.TrainingSetPolicy
import com.ironlog.app.domain.training.TrackingMode
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.state.rememberPresentationTime
import com.ironlog.app.ui.theme.Text
import com.ironlog.app.widget.WidgetUpdateWorker
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import timber.log.Timber

private suspend fun dismissReminderAfterHistoricalWorkoutSave(context: android.content.Context) {
    try {
        com.ironlog.app.services.WorkoutNotificationBridge
            .cancelReminderAfterDataMutation(context)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // The workout is already committed. Notification cleanup is best-effort and must
        // not turn a successful historical import into a misleading save failure.
        Timber.w(error, "Could not dismiss stale reminder after historical workout save")
    }
}

@Composable
internal fun HistoricalWorkoutEntryHost(initialDate: String, weightUnit: String, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repo = remember { WorkoutRepository() }
    val library = remember { ExerciseRepository() }
    val historyRepo = remember { HistoryRepository() }
    val c = useTheme()
    var choices by remember { mutableStateOf(emptyList<HistoricalExerciseChoice>()) }
    var libraryStatus by remember { mutableStateOf<String?>("Loading exercise library…") }
    var viewId by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<HistoryEntry?>(null) }
    var viewError by remember { mutableStateOf<String?>(null) }
    val now by rememberPresentationTime()
    val zoneName = rememberSaveable { ZoneId.systemDefault().id }
    val zone = remember(zoneName) { ZoneId.of(zoneName) }
    LaunchedEffect(library) {
        try {
            choices = library.getExercisesSnapshot().map { exercise ->
                val mode = TrainingSetPolicy.tracking(HistoryExercise(name = exercise.name, category = exercise.category,
                    equipment = exercise.equipment, trackingType = exercise.trackingType, isBodyweight = exercise.isBodyweight))
                val tracking = when (mode) {
                    TrackingMode.DURATION -> "duration"
                    TrackingMode.WEIGHTED_DURATION -> "duration_weight"
                    TrackingMode.DURATION_DISTANCE -> "duration_distance"
                    TrackingMode.BODYWEIGHT_REPS -> "bodyweight_reps"
                    TrackingMode.ADDED_LOAD_REPS -> "bodyweight_plus_weight_reps"
                    TrackingMode.ASSISTED_REPS -> "assisted_bodyweight"
                    else -> "weight_reps"
                }
                HistoricalExerciseChoice(exercise.id, exercise.name, tracking, exercise.category, exercise.equipment, exercise.isBodyweight)
            }
            libraryStatus = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { libraryStatus = "Could not load exercises. Close and reopen to retry; quick summaries remain available." }
    }
    HistoricalWorkoutEditor(initialDate = initialDate, choices = choices, weightUnit = weightUnit,
        initialDetailed = false, now = Instant.ofEpochMilli(now), zoneId = zone, libraryStatus = libraryStatus,
        onDismiss = onDismiss, onViewExisting = { viewId = it },
        onSave = { input, allowSameDay ->
            repo.saveHistoricalWorkout(input, zone, allowSameDay).also { result ->
                if (result is HistoricalWorkoutSaveResult.Saved) {
                    dismissReminderAfterHistoricalWorkoutSave(context)
                    runCatching { WidgetUpdateWorker.enqueueOneTime(context) }
                }
            }
        })
    LaunchedEffect(viewId) {
        existing = null; viewError = null
        val id = viewId ?: return@LaunchedEffect
        try {
            existing = historyRepo.completedSnapshot().firstOrNull { it.id == id }
            if (existing == null) viewError = "This workout is no longer available."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { viewError = "Could not open this workout. Your draft is unchanged." }
    }
    if (viewId != null) AlertDialog(onDismissRequest = { viewId = null }, containerColor = c.card,
        title = { Text(existing?.name ?: "Recorded workout") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val entry = existing
            if (entry == null) Text(viewError ?: "Loading…") else {
                Text("${com.ironlog.app.domain.gamification.parseHistoryInstant(entry.date, zone)?.atZone(zone)} · ${entry.duration / 60} min")
                entry.summaryText?.let { Text(it) }
                entry.exercises.forEach { exercise ->
                    Text(exercise.name)
                    exercise.note?.let { Text(it) }
                    exercise.sets.forEachIndexed { index, set ->
                        Text("Set ${index + 1}: ${set.weight} × ${set.reps} · ${set.type}")
                        Text("RPE ${set.rpe ?: "—"} · RIR ${set.rir ?: "—"}")
                        set.note?.let { Text(it) }
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = { viewId = null }) { Text("Return to draft") } })
}
