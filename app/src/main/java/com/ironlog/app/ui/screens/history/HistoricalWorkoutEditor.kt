package com.ironlog.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.data.model.HistoricalWorkoutSaveResult
import com.ironlog.app.ui.components.SetRow
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.state.LoggedSet
import com.ironlog.app.ui.theme.Text
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import com.ironlog.app.util.convertUnitToKg
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val HistoricalDraftSaver = Saver<HistoricalWorkoutDraft, String>(
    save = { encodeHistoricalDraft(it) }, restore = { runCatching { decodeHistoricalDraft(it) }.getOrNull() },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HistoricalWorkoutEditor(
    initialDate: String,
    choices: List<HistoricalExerciseChoice>,
    weightUnit: String,
    onDismiss: () -> Unit,
    onSave: suspend (CreateCompletedWorkoutInput, Boolean) -> HistoricalWorkoutSaveResult,
    modifier: Modifier = Modifier,
    initialDetailed: Boolean = true,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    libraryStatus: String? = null,
    onViewExisting: (String) -> Unit = {},
) {
    val c = useTheme()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(initialDate, initialDetailed, stateSaver = HistoricalDraftSaver) {
        mutableStateOf(HistoricalWorkoutDraft(date = initialDate, detailed = initialDetailed))
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var conflict by remember { mutableStateOf<HistoricalWorkoutSaveResult.DateConflict?>(null) }
    var pending by remember { mutableStateOf<CreateCompletedWorkoutInput?>(null) }
    val closeFocus = remember { FocusRequester() }
    val dirty = draft.time.isNotBlank() || draft.durationMinutes.isNotBlank() || draft.name.isNotBlank() ||
        draft.notes.isNotBlank() || draft.rating != null || draft.exercises.isNotEmpty() || draft.date != initialDate

    fun requestClose() {
        if (saving) return
        if (dirty) discard = true else onDismiss()
    }
    fun submit(allowSameDay: Boolean = false) {
        if (saving) return
        val input = if (allowSameDay) pending ?: return else try {
            draft.toCompletedInput(zoneId, now)
        } catch (failure: IllegalArgumentException) {
            error = failure.message
            return
        }
        saving = true
        error = null
        conflict = null
        scope.launch {
            try {
                when (val result = onSave(input, allowSameDay)) {
                    is HistoricalWorkoutSaveResult.Saved -> onDismiss()
                    is HistoricalWorkoutSaveResult.DateConflict -> { pending = input; conflict = result }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: IllegalArgumentException) {
                error = invalid.message ?: "Review this historical workout before saving."
            } catch (_: Exception) {
                error = "Could not save this historical workout. Your draft is intact; try again."
            } finally { saving = false }
        }
    }
    fun updateExercise(id: String, transform: (HistoricalExerciseDraft) -> HistoricalExerciseDraft) {
        if (!saving) draft = draft.copy(exercises = draft.exercises.map { if (it.id == id) transform(it) else it })
    }

    Dialog(onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Window insets belong to the full dialog, not to a constrained editor viewport.
        Box(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().imePadding()) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).appPadding(16.dp), verticalArrangement = appSpacedBy(12.dp)) {
            LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("LOG PAST WORKOUT", color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = ::requestClose, enabled = !saving, modifier = Modifier.focusRequester(closeFocus).heightIn(min = 48.dp)) { Text("Close") }
            }
            Text("Save a completed workout to history. No live timer or completion celebration.", color = c.subtext)
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = appSpacedBy(12.dp)) {
                listOf(false to "Quick summary", true to "Detailed sets").forEach { (detailed, label) ->
                    Row(Modifier.heightIn(min = 48.dp).selectable(selected = draft.detailed == detailed,
                        enabled = !saving && (detailed || draft.exercises.isEmpty()), role = Role.RadioButton,
                        onClick = { draft = draft.copy(detailed = detailed) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = draft.detailed == detailed, onClick = null)
                        Text(label, color = c.text)
                    }
                }
            }
            OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, enabled = !saving,
                label = { Text("Workout name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.date, { draft = draft.copy(date = it, offsetSeconds = null) }, enabled = !saving,
                label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = { TextButton(onClick = { datePicker = true }, enabled = !saving) { Text("Pick date") } })
            OutlinedTextField(draft.time, { draft = draft.copy(time = it, offsetSeconds = null) }, enabled = !saving,
                label = { Text("Start time (HH:MM)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Local time · ${zoneId.id}", color = c.muted)
            val offsets = historicalTimeOffsets(draft.date, draft.time, zoneId)
            if (offsets.size > 1) {
                Text("This time occurs twice. Choose the UTC offset:", color = c.subtext)
                FlowRow(horizontalArrangement = appSpacedBy(8.dp)) {
                    offsets.forEach { offset -> FilterChip(selected = draft.offsetSeconds == offset.totalSeconds,
                        onClick = { draft = draft.copy(offsetSeconds = offset.totalSeconds) }, enabled = !saving,
                        label = { Text("UTC$offset") }, modifier = Modifier.heightIn(min = 48.dp)) }
                }
            }
            OutlinedTextField(draft.durationMinutes, { draft = draft.copy(durationMinutes = it) }, enabled = !saving,
                label = { Text("Duration (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(draft.notes, { draft = draft.copy(notes = it) }, enabled = !saving,
                label = { Text("Workout notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text("Workout rating (optional)", color = c.subtext)
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = appSpacedBy(8.dp)) {
                (1..5).forEach { rating ->
                    val selected = draft.rating == rating
                    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .background(if (selected) c.accent else c.surface, CircleShape)
                        .semantics { contentDescription = "Workout rating $rating out of 5" }
                        .selectable(selected = selected, enabled = !saving, role = Role.RadioButton,
                            onClick = { draft = draft.copy(rating = rating) }), contentAlignment = Alignment.Center) {
                        Text(rating.toString(), color = if (selected) c.textOnAccent else c.text,
                            modifier = Modifier.clearAndSetSemantics { })
                    }
                }
                TextButton(onClick = { draft = draft.copy(rating = null) }, enabled = !saving && draft.rating != null) { Text("Clear rating") }
            }
            if (draft.detailed) {
                draft.exercises.forEach { exercise -> key(exercise.id) {
                    HorizontalDivider(color = c.cardBorder)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(exercise.name, color = c.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { draft = draft.copy(exercises = draft.exercises.filterNot { it.id == exercise.id }) }, enabled = !saving) { Text("Remove exercise") }
                    }
                    OutlinedTextField(exercise.notes, { value -> updateExercise(exercise.id) { it.copy(notes = value) } },
                        enabled = !saving, label = { Text("Exercise notes") }, modifier = Modifier.fillMaxWidth())
                    exercise.sets.forEachIndexed { index, set -> key(set.id) {
                        SetRow(set, index, 0, dispatch = { action -> if (!saving) draft = updateHistoricalSet(draft, exercise.id, set.id, action, weightUnit) },
                            effortTracking = "both", hapticFeedback = false, weightUnit = weightUnit, trackingType = exercise.trackingType)
                    } }
                    val timed = exercise.trackingType.startsWith("duration")
                    val distance = exercise.trackingType == "duration_distance"
                    if (exercise.trackingType !in setOf("duration", "bodyweight_reps")) {
                        OutlinedTextField(exercise.loadInput, { value -> updateExercise(exercise.id) { it.copy(loadInput = value) } }, enabled = !saving,
                            label = { Text(when (exercise.trackingType) {
                                "duration_distance" -> "Distance (km)"
                                "assisted_bodyweight" -> "Assistance ($weightUnit)"
                                "bodyweight_plus_weight_reps" -> "Added load ($weightUnit, optional)"
                                else -> "Load ($weightUnit)"
                            }) }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    OutlinedTextField(exercise.repsInput, { value -> updateExercise(exercise.id) { it.copy(repsInput = value) } }, enabled = !saving,
                        label = { Text(if (timed) "Seconds" else "Reps") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedButton(onClick = {
                        val rawLoad = exercise.loadInput.trim().replace(',', '.').let { if (it.isBlank()) 0.0 else it.toDoubleOrNull() }
                        val reps = exercise.repsInput.trim().replace(',', '.').toDoubleOrNull()
                        if (rawLoad == null || !rawLoad.isFinite() || rawLoad < 0 || reps == null || !reps.isFinite() || reps <= 0) {
                            error = "Enter a valid load and positive reps or seconds."
                        } else {
                            val load = if (distance || exercise.trackingType == "duration") rawLoad else convertUnitToKg(rawLoad, weightUnit)
                            updateExercise(exercise.id) { it.copy(loadInput = "", repsInput = "", sets = it.sets + LoggedSet(
                                id = UUID.randomUUID().toString(), weight = load, reps = reps, trackingType = exercise.trackingType,
                                durationSec = if (timed) reps else null)) }
                            error = null
                        }
                    }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add set") }
                } }
                HorizontalDivider(color = c.cardBorder)
                Text("Add exercise", color = c.text, fontWeight = FontWeight.Bold)
                OutlinedTextField(query, { query = it }, enabled = !saving, label = { Text("Search exercises") }, modifier = Modifier.fillMaxWidth())
                libraryStatus?.let { Text(it, color = c.muted) }
                val matches = remember(choices, query) { choices.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(30) }
                matches.forEach { choice -> TextButton(onClick = {
                    draft = draft.copy(exercises = draft.exercises + HistoricalExerciseDraft(exerciseId = choice.id,
                        name = choice.name, trackingType = choice.trackingType)); query = ""
                }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(choice.name, color = c.text) } }
                if (matches.isEmpty() && libraryStatus == null) Text("No exercises match this search.", color = c.muted)
            }
            error?.let { Text(it, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
            Button(onClick = { submit() }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent)) { Text(if (saving) "SAVING…" else "SAVE TO HISTORY") }
        }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, containerColor = c.card,
        title = { Text("Discard historical workout draft?") }, text = { Text("This workout has not been saved.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    conflict?.let { result -> AlertDialog(onDismissRequest = { conflict = null }, containerColor = c.card,
        title = { Text("A workout is already recorded on this date") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Keep existing workouts and add another, or cancel this save.")
            result.workouts.forEach { workout ->
                TextButton(onClick = { onViewExisting(workout.uid) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("View existing: ${workout.name} · ${Instant.ofEpochMilli(workout.startedAt).atZone(zoneId).toLocalTime()}")
                }
            }
        } }, confirmButton = { TextButton(onClick = { submit(true) }) { Text("Add another") } },
        dismissButton = { TextButton(onClick = { conflict = null; pending = null }) { Text("Cancel") } }) }
    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = runCatching { historicalPickerMillis(LocalDate.parse(draft.date)) }.getOrNull())
        DatePickerDialog(onDismissRequest = { datePicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { draft = draft.copy(date = historicalPickerDate(it).toString(), offsetSeconds = null) }; datePicker = false }) { Text("Set date") } },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}
