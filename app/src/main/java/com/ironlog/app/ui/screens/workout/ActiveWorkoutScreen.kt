package com.ironlog.app.ui.screens.workout

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.model.SetInput
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.domain.intelligence.CloudAiEngine
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import com.ironlog.app.domain.intelligence.TrainingIntelligenceEngine
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.valentinilk.shimmer.shimmer
import com.ironlog.app.services.WorkoutForegroundService
import com.ironlog.app.services.WorkoutNotificationBridge
import com.ironlog.app.services.ShareService
import com.ironlog.app.ui.components.SetRow
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.model.UiPlanExercise
import timber.log.Timber
import com.ironlog.app.ui.state.AddedExerciseEntry
import com.ironlog.app.ui.state.LoggedSet
import com.ironlog.app.ui.state.WorkoutAction
import com.ironlog.app.ui.state.WorkoutState
import com.ironlog.app.ui.state.workoutReducer
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.viewmodel.PlansViewModel
import com.ironlog.app.util.formatDurationShort
import com.ironlog.app.util.formatWeightFromKg
import com.ironlog.app.util.HapticsEngine
import com.ironlog.app.util.calculatePlates
import com.ironlog.app.ui.screens.settings.GymProfileDto
import com.ironlog.app.ui.screens.settings.DEFAULT_PLATES
import com.ironlog.app.ui.screens.settings.PlateDto
import com.ironlog.app.ui.screens.stats.estimateOneRM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.round
import kotlin.math.roundToInt
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.Position
import io.github.vinceglb.confettikit.core.Rotation
import io.github.vinceglb.confettikit.core.emitter.Emitter
import io.github.vinceglb.confettikit.core.models.Shape
import io.github.vinceglb.confettikit.core.models.Size

private const val ACTIVE_WORKOUT_SESSION_PREFIX = "@ironlog/activeWorkoutSession/"
private const val COMPARISON_USAGE_KEY = "comparison_usage_v1"
private val RATE_THRESHOLDS = listOf(10, 25, 50)
private val FUN_COMPARISONS = listOf(
    FunComparison(0, "a house cat", "🐈"),
    FunComparison(500, "a baby goat", "🐐"),
    FunComparison(1000, "a large pumpkin", "🎃"),
    FunComparison(2000, "a baby elephant", "🐘"),
    FunComparison(3500, "a baby hippo", "🦛"),
    FunComparison(5000, "a grand piano", "🎹"),
    FunComparison(7500, "a polar bear", "🐻‍❄️"),
    FunComparison(10000, "a small car", "🚗"),
    FunComparison(15000, "a T-Rex", "🦖"),
    FunComparison(20000, "a rhino", "🦏"),
    FunComparison(25000, "an orca whale", "🐋"),
    FunComparison(35000, "an elephant", "🐘"),
    FunComparison(40000, "a school bus", "🚌"),
    FunComparison(60000, "a space shuttle", "🚀"),
    FunComparison(100000, "a blue whale", "🐋"),
)

data class FunComparison(val threshold: Int, val text: String, val icon: String)
data class WorkoutCompletionCelebration(
    val hasPrCelebration: Boolean = false,
    val hasStreak30Celebration: Boolean = false,
)

data class NormalizedSessionExercise(
    val name: String,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val trackingType: String,
    val isWarmup: Boolean,
    val equipment: String? = null,
)

class ActiveWorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val workoutRepo = WorkoutRepository()
    private val settingsRepo = SettingsRepository()
    // Read weight unit once from settings so the PR banner uses the correct unit.
    private var vmWeightUnit: String = "kg"
    private val _workoutState = MutableStateFlow(WorkoutState())
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    // Timer only starts when the first set is logged — until then the display shows "--:--".
    private val _timerStarted = MutableStateFlow(false)
    val timerStarted: StateFlow<Boolean> = _timerStarted.asStateFlow()

    private val _prBanner = MutableStateFlow<String?>(null)
    val prBanner: StateFlow<String?> = _prBanner.asStateFlow()

    private val _showCompletionSheet = MutableStateFlow(false)
    val showCompletionSheet: StateFlow<Boolean> = _showCompletionSheet.asStateFlow()
    private val _activeWorkoutIdSignal = MutableStateFlow<String?>(null)
    val activeWorkoutIdSignal: StateFlow<String?> = _activeWorkoutIdSignal.asStateFlow()

    // ObjectBox workout tracking
    private var activeWorkoutId: String? = null
    private var timerStartEpochMs: Long? = null
    // UI rows can repeat the same exercise id, so persistence must bind by row index.
    private var workoutExerciseIdByIndex: Map<Int, String> = emptyMap()
    // Historical best 1RM per exerciseId
    private var historicalBest1rm: MutableMap<String, Double> = mutableMapOf()
    private var hadPrThisSession: Boolean = false
    private val json = Json { ignoreUnknownKeys = true }
    // Ordered indices from the composable — persisted in draft so minimize/resume restores order.
    private var persistedOrderedIndices: List<Int> = emptyList()
    private val _restoredOrderedIndices = MutableStateFlow<List<Int>>(emptyList())
    val restoredOrderedIndices: StateFlow<List<Int>> = _restoredOrderedIndices.asStateFlow()

    fun updateOrderedIndices(indices: List<Int>) {
        persistedOrderedIndices = indices
    }

    init {
        // Load weight unit so the PR banner displays in the user's chosen unit.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val raw = settingsRepo.getString("ironlog_settings")
            val json = runCatching { org.json.JSONObject(raw ?: "{}") }.getOrDefault(org.json.JSONObject())
            vmWeightUnit = json.optString("weightUnit", "kg").ifBlank { "kg" }
        }
        // Tick elapsed timer from a stable epoch so minimize/background/screen-off
        // never drifts or resets the counter.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val start = timerStartEpochMs
                if (_timerStarted.value && start != null) {
                    _elapsedSeconds.value = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L).toInt()
                }
            }
        }
    }

    /**
     * Called when the very first set of a session is logged.
     * Idempotent — safe to call multiple times (noop after first call).
     */
    private fun startTimerOnFirstSet() {
        if (_timerStarted.value) return
        _timerStarted.value = true
        val nowMs = System.currentTimeMillis()
        timerStartEpochMs = nowMs
        _elapsedSeconds.value = ((System.currentTimeMillis() - nowMs) / 1000L).coerceAtLeast(0L).toInt()
        viewModelScope.launch {
            settingsRepo.setString("active_workout_start_ms", nowMs.toString())
            WorkoutForegroundService.start(
                getApplication(),
                activeWorkoutName ?: "Workout",
                nowMs,
            )
        }
    }

    /** The name shown in the foreground-service notification. Derived lazily once [initWorkout] runs. */
    private var activeWorkoutName: String? = null

    fun initWorkout(dayId: String) {
        if (activeWorkoutId != null) return
        viewModelScope.launch {
            // Resume existing active workout first (survives process/background recreation).
            val persistedActiveId = runCatching { settingsRepo.getActiveWorkoutId() }.getOrNull()
            if (!persistedActiveId.isNullOrBlank()) {
                val resumed = runCatching { workoutRepo.getWorkoutDetailSnapshot(persistedActiveId) }.getOrNull()
                if (resumed != null) {
                    activeWorkoutId = resumed.workout.uid
                    activeWorkoutName = resumed.workout.name.ifBlank { "Workout in progress" }
                    _activeWorkoutIdSignal.value = resumed.workout.uid
                    bindWorkoutExerciseRows(resumed.exercises)
                    val persistedStart = settingsRepo.getString("active_workout_start_ms")?.toLongOrNull()
                    if (persistedStart != null && persistedStart > 0L) {
                        timerStartEpochMs = persistedStart
                        _timerStarted.value = true
                        _elapsedSeconds.value = ((System.currentTimeMillis() - persistedStart) / 1000L).coerceAtLeast(0L).toInt()
                    }
                    if (persistedStart != null && persistedStart > 0L) {
                        WorkoutForegroundService.start(
                            getApplication(),
                            activeWorkoutName ?: "Workout",
                            persistedStart,
                        )
                    }
                    restoreDraftIfAny()
                    return@launch
                }
            }
            if (dayId.isBlank()) {
                // Re-open path without a day id should resume only.
                // If there is no persisted active workout, avoid creating a new one implicitly.
                return@launch
            }
            try {
                val workout = workoutRepo.startWorkoutFromPlanDay(dayId)
                activeWorkoutId = workout.uid
                activeWorkoutName = workout.name.ifBlank { "Workout in progress" }
                _activeWorkoutIdSignal.value = workout.uid
                // Note: foreground service shows the live notification (with rest timer inline).
                // Do NOT call WorkoutNotificationBridge.showActiveWorkout — it creates a duplicate loud notification.
                val detail = workoutRepo.getWorkoutDetailSnapshot(workout.uid)
                bindWorkoutExerciseRows(detail.exercises)
                persistDraft(_workoutState.value)
                restoreDraftIfAny()
            } catch (_: Exception) {
                // If day not in ObjectBox yet, start empty workout
                try {
                    val workout = workoutRepo.startEmptyWorkout("Quick Workout")
                    activeWorkoutId = workout.uid
                    activeWorkoutName = workout.name
                    _activeWorkoutIdSignal.value = workout.uid
                    // Foreground service handles the live notification — no duplicate from WorkoutNotificationBridge.
                    persistDraft(_workoutState.value)
                    restoreDraftIfAny()
                } catch (_: Exception) { /* silent — UI still works in-memory */ }
            }
        }
    }

    /** Loads last-session ghost data for a list of exercise IDs, dispatching UpdateGhost actions. */
    fun loadGhostData(exerciseIds: List<String>) {
        if (exerciseIds.isEmpty()) return
        viewModelScope.launch {
            exerciseIds.forEachIndexed { idx, exerciseId ->
                runCatching {
                    val sets = workoutRepo.getLastSessionSetsForExercise(exerciseId)
                    if (sets.isNotEmpty()) {
                        val ghost = com.ironlog.app.ui.state.GhostData(
                            sets = sets.map { s ->
                                com.ironlog.app.ui.state.GhostSet(
                                    weight = s.weight,
                                    reps = s.reps,
                                    rpe = s.rpe,
                                )
                            },
                        )
                        dispatch(WorkoutAction.UpdateGhost(idx, ghost))
                    }
                }
            }
        }
    }

    fun dispatch(action: WorkoutAction) {
        // Clean up DB row when an exercise is removed mid-workout, then re-index the binding map.
        if (action is WorkoutAction.RemoveExercise && activeWorkoutId != null) {
            val uid = workoutExerciseIdByIndex[action.exIndex]
            if (!uid.isNullOrBlank()) {
                viewModelScope.launch {
                    runCatching { workoutRepo.deleteWorkoutExercise(uid) }
                }
            }
            // Re-index: remove the entry for exIndex and shift all higher indices down by 1.
            workoutExerciseIdByIndex = workoutExerciseIdByIndex
                .filterKeys { it != action.exIndex }
                .mapKeys { (k, _) -> if (k > action.exIndex) k - 1 else k }
        }
        val next = workoutReducer(_workoutState.value, action)
        _workoutState.value = next
        // Persist immediately so minimize/background cannot drop the latest typed input.
        if (activeWorkoutId != null) persistDraft(next)
    }

    fun logSet(exIndex: Int, exerciseId: String, weightText: String, repsText: String, trackingType: String, restSeconds: Int) {
        val weight = weightText.toDoubleOrNull() ?: 0.0
        val reps = repsText.toDoubleOrNull() ?: 0.0
        if (weight <= 0.0 && reps <= 0.0) return

        // Start the workout timer the first time any set is logged.
        val totalSetsBefore = _workoutState.value.setLog.values.sumOf { it.size }
        if (totalSetsBefore == 0) startTimerOnFirstSet()

        dispatch(WorkoutAction.LogSet(exIndex, LoggedSet(weight = weight, reps = reps, trackingType = trackingType, durationSec = if (trackingType.startsWith("duration")) reps else null)))
        val endTime = System.currentTimeMillis() + restSeconds * 1000L
        dispatch(WorkoutAction.StartRest(endTime = endTime, total = restSeconds, triggerExIndex = exIndex))
        val setCountForExercise = _workoutState.value.setLog[exIndex]?.size ?: 0
        viewModelScope.launch {
            settingsRepo.setString("active_workout_set_label", "Set $setCountForExercise")
            settingsRepo.setString("active_workout_rest_end_ms", endTime.toString())
        }
        // No separate rest timer notification — the foreground service notification shows rest seconds inline
        // (see WorkoutForegroundService.buildNotification which reads active_workout_rest_end_ms).

        viewModelScope.launch {
            val workoutExId = resolveWorkoutExerciseId(exIndex, exerciseId)
            if (workoutExId != null) {
                try {
                    workoutRepo.addSet(workoutExId, SetInput(weight = weight, reps = reps, restSeconds = restSeconds))
                    checkForPr(exerciseId, weight, reps.roundToInt())
                } catch (_: Exception) { /* persist failure is non-fatal */ }
            }
            persistDraft(_workoutState.value)
        }
    }

    fun persistSetRpe(exIndex: Int, exerciseId: String, setIndexZeroBased: Int, rpe: Double?) {
        persistSetUpdate(exIndex, exerciseId, setIndexZeroBased, SetInput(rpe = rpe))
    }

    fun persistSetRir(exIndex: Int, exerciseId: String, setIndexZeroBased: Int, rir: Int?) {
        persistSetUpdate(exIndex, exerciseId, setIndexZeroBased, SetInput(rir = rir?.toDouble()))
    }

    fun persistSetType(exIndex: Int, exerciseId: String, setIndexZeroBased: Int, type: String) {
        val key = type.lowercase()
        val input = SetInput(
            isWarmup = key == "warmup",
            isDropset = key == "drop",
            isAmrap = key == "amrap",
            toFailure = key == "failure",
        )
        persistSetUpdate(exIndex, exerciseId, setIndexZeroBased, input)
    }

    fun persistSetValues(exIndex: Int, exerciseId: String, setIndexZeroBased: Int, weight: Double?, reps: Double?) {
        persistSetUpdate(exIndex, exerciseId, setIndexZeroBased, SetInput(weight = weight, reps = reps))
    }

    fun persistWarmupSets(exIndex: Int, exerciseId: String, warmups: List<LoggedSet>, restSeconds: Int = 45) {
        if (warmups.isEmpty()) return
        viewModelScope.launch {
            val workoutExId = resolveWorkoutExerciseId(exIndex, exerciseId) ?: return@launch
            warmups.forEach { ws ->
                runCatching {
                    workoutRepo.addSet(
                        workoutExId,
                        SetInput(
                            weight = ws.weight,
                            reps = ws.reps,
                            restSeconds = restSeconds,
                            isWarmup = true,
                            rpe = ws.rpe,
                            rir = ws.rir?.toDouble(),
                        ),
                    )
                }
            }
        }
    }

    private fun persistSetUpdate(exIndex: Int, exerciseId: String, setIndexZeroBased: Int, input: SetInput) {
        val setOrder = setIndexZeroBased + 1
        viewModelScope.launch {
            val workoutExId = resolveWorkoutExerciseId(exIndex, exerciseId) ?: return@launch
            runCatching {
                workoutRepo.updateSetByWorkoutExerciseAndOrder(workoutExId, setOrder, input)
            }
        }
    }

    private suspend fun resolveWorkoutExerciseId(exIndex: Int, exerciseId: String): String? {
        workoutExerciseIdByIndex[exIndex]?.let { return it }
        val workoutId = waitForActiveWorkoutId() ?: return null
        val existing = runCatching {
            val detail = workoutRepo.getWorkoutDetailSnapshot(workoutId)
            bindWorkoutExerciseRows(detail.exercises)
            workoutExerciseIdByIndex[exIndex]
        }.getOrNull()
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = runCatching { workoutRepo.addExerciseToWorkout(workoutId, exerciseId) }.getOrNull()
        if (created != null) {
            workoutExerciseIdByIndex = workoutExerciseIdByIndex + (exIndex to created.uid)
            return created.uid
        }
        return null
    }

    private suspend fun waitForActiveWorkoutId(): String? {
        activeWorkoutId?.let { return it }
        repeat(20) {
            delay(50)
            activeWorkoutId?.let { return it }
        }
        return null
    }

    private fun bindWorkoutExerciseRows(rows: List<WorkoutExerciseEntity>, exerciseIdsInUiOrder: List<String>? = null) {
        val orderedRows = rows.sortedBy { it.orderIndex }
        val ids = exerciseIdsInUiOrder ?: orderedRows.map { it.exerciseUid }
        workoutExerciseIdByIndex = buildWorkoutExerciseIndexMap(
            exerciseIdsInUiOrder = ids,
            workoutExerciseRows = orderedRows.map {
                WorkoutExerciseBinding(
                    workoutExerciseId = it.uid,
                    exerciseId = it.exerciseUid,
                    orderIndex = it.orderIndex,
                )
            },
        )
    }

    private fun checkForPr(exerciseId: String, weight: Double, reps: Int) {
        val oneRm = estimateOneRM(weight, reps).toDouble()
        val prev = historicalBest1rm[exerciseId]
        if (prev == null || oneRm > prev) {
            historicalBest1rm[exerciseId] = oneRm
            hadPrThisSession = true
            if (prev != null) {
                val oneRmDisplay = com.ironlog.app.util.formatWeightFromKg(oneRm, vmWeightUnit)
                _prBanner.value = "🏆 New PR! ~$oneRmDisplay 1RM"
                viewModelScope.launch {
                    delay(4000)
                    _prBanner.value = null
                }
            }
        }
    }

    fun finishWorkout() {
        _showCompletionSheet.value = true
    }

    /**
     * Persist workout completion, then call [onDone].
     *
     * [onDone] must be invoked INSIDE the coroutine, AFTER all DB work completes.
     * Calling it outside (as the previous code did) would destroy the NavBackStackEntry /
     * ViewModel mid-coroutine, cancelling the clearActiveWorkoutId() call and leaving the
     * floating pill stuck on-screen.
     */
    fun completeWorkout(
        rating: Int,
        totalVolumeKg: Double,
        notes: String = "",
        onError: (String) -> Unit = {},
        onDone: (WorkoutCompletionCelebration) -> Unit = {},
    ) {
        viewModelScope.launch {
            val id = activeWorkoutId
            if (id.isNullOrBlank()) {
                _showCompletionSheet.value = false
                onError("No active workout was found.")
                return@launch
            }
            var streakDays = 0
            val hadPr = hadPrThisSession
            try {
                workoutRepo.completeWorkout(
                    workoutId = id,
                    durationStartEpochMs = timerStartEpochMs,
                    metadata = com.ironlog.app.data.model.WorkoutMetadataInput(
                        rating = if (rating in 1..5) rating.toDouble() else null,
                        notes = notes.ifBlank { null },
                    ),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to complete workout %s", id)
                _showCompletionSheet.value = false
                onError(e.message ?: "The workout could not be completed. Your active session was preserved.")
                return@launch
            }

            // The durable workout and active-session keys are committed atomically. Clear the
            // in-memory ID immediately so screen disposal cannot recreate the deleted draft.
            activeWorkoutId = null
            _activeWorkoutIdSignal.value = null
            streakDays = computeDailyWorkoutStreakDays()

            runCatching {
                workoutRepo.recordPostWorkoutMetrics(id, totalVolumeKg, hadPr)
            }.onFailure { Timber.e(it, "Failed to record post-workout metrics for %s", id) }
            runCatching { WorkoutNotificationBridge.clearWorkout(getApplication()) }
            runCatching { WorkoutForegroundService.stop(getApplication()) }
            runCatching { com.ironlog.app.widget.WidgetUpdateWorker.enqueueOneTime(getApplication()) }
            timerStartEpochMs = null
            _elapsedSeconds.value = 0
            _timerStarted.value = false
            _showCompletionSheet.value = false
            val milestoneAlertsEnabled = settingsRepo.getBoolean("milestone_alerts_enabled", true)
            onDone(
                WorkoutCompletionCelebration(
                    hasPrCelebration = milestoneAlertsEnabled && hadPr,
                    hasStreak30Celebration = milestoneAlertsEnabled && streakDays >= 30,
                ),
            )
        }
    }

    private fun computeDailyWorkoutStreakDays(): Int {
        val completed = ObjectBox.store.boxFor(WorkoutEntity::class.java).query(WorkoutEntity_.status.equal("completed"))
            .build().use { it.find() }
        if (completed.isEmpty()) return 0
        val dates = completed.map {
            Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSet()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        if (!dates.contains(today) && !dates.contains(yesterday)) return 0
        var streak = 0
        var cursor = if (dates.contains(today)) today else yesterday
        while (dates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /**
     * Mark the active workout as abandoned (clears active_workout_id and related settings keys),
     * then call [onDone] once the DB write is confirmed.
     */
    fun discardWorkout(onError: (String) -> Unit = {}, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val id = activeWorkoutId
            if (!id.isNullOrBlank()) {
                try {
                    workoutRepo.abandonWorkout(id)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to abandon workout %s", id)
                    onError(e.message ?: "The workout could not be discarded. Your session was preserved.")
                    return@launch
                }
            }
            activeWorkoutId = null
            _activeWorkoutIdSignal.value = null
            WorkoutNotificationBridge.clearWorkout(getApplication())
            WorkoutForegroundService.stop(getApplication())
            settingsRepo.removeSetting("active_workout_set_label")
            settingsRepo.removeSetting("active_workout_rest_end_ms")
            settingsRepo.removeSetting("active_workout_start_ms")
            timerStartEpochMs = null
            _elapsedSeconds.value = 0
            _timerStarted.value = false
            dispatch(WorkoutAction.HydrateState(WorkoutState()))
            onDone()
        }
    }

    fun onRestCleared() {
        WorkoutNotificationBridge.clearRestTimer(getApplication())
        viewModelScope.launch { settingsRepo.setString("active_workout_rest_end_ms", "0") }
    }

    private fun draftPayload(state: WorkoutState): WorkoutDraftDto = WorkoutDraftDto(
        inputs = state.inputs.mapKeys { it.key.toString() }.mapValues { (_, v) -> WorkoutInputDto(v.weight, v.reps) },
        setLog = state.setLog.mapKeys { it.key.toString() }.mapValues { (_, sets) ->
            sets.map {
                LoggedSetDto(
                    id = it.id,
                    weight = it.weight,
                    reps = it.reps,
                    type = it.type,
                    rpe = it.rpe,
                    rir = it.rir,
                    note = it.note,
                    orm = it.orm,
                    trackingType = it.trackingType,
                    durationSec = it.durationSec,
                )
            }
        },
        exerciseNotes = state.exerciseNotes.mapKeys { it.key.toString() },
        supersetGroups = state.supersetGroups.mapKeys { it.key.toString() },
        restTimer = RestTimerDto(
            active = state.restTimer.active,
            endTime = state.restTimer.endTime,
            total = state.restTimer.total,
            paused = state.restTimer.paused,
            pausedAt = state.restTimer.pausedAt,
            triggerExIndex = state.restTimer.triggerExIndex,
        ),
        addedExercises = state.addedExercises.map {
            AddedExerciseDto(
                exerciseId = it.exerciseId,
                name = it.name,
                trackingType = it.trackingType,
                equipment = it.equipment,
                sets = it.sets,
                reps = it.reps,
            )
        },
        removedBaseExerciseIndices = state.removedBaseExerciseIndices.toList().sorted(),
        orderedIndices = persistedOrderedIndices,
        swappedExercises = state.swappedExercises.mapNotNull { (k, v) ->
            val ex = v as? com.ironlog.app.data.model.LegacyExerciseShape ?: return@mapNotNull null
            k.toString() to SwappedExerciseDto(
                id = ex.id,
                name = ex.name,
                trackingType = ex.trackingType,
                equipment = ex.equipment,
            )
        }.toMap(),
    )

    fun persistDraft(state: WorkoutState) {
        viewModelScope.launch {
            val id = activeWorkoutId ?: return@launch
            val payload = draftPayload(state)
            settingsRepo.setString("active_workout_draft_$id", json.encodeToString(payload), "json")
        }
    }

    fun persistDraftBlocking(state: WorkoutState) {
        val id = activeWorkoutId ?: return
        val payload = draftPayload(state)
        runCatching {
            settingsRepo.setStringBlocking("active_workout_draft_$id", json.encodeToString(payload), "json")
        }
    }

    private fun restoreDraftIfAny() {
        viewModelScope.launch {
            val id = activeWorkoutId ?: return@launch
            val raw = settingsRepo.getString("active_workout_draft_$id").orEmpty()
            if (raw.isBlank()) return@launch
            val dto = runCatching { json.decodeFromString(WorkoutDraftDto.serializer(), raw) }.getOrNull() ?: return@launch
            val restored = WorkoutState(
                inputs = dto.inputs.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to com.ironlog.app.ui.state.WorkoutInput(v.weight, v.reps) } }.toMap(),
                setLog = dto.setLog.mapNotNull { (k, sets) ->
                    k.toIntOrNull()?.let { idx ->
                        idx to sets.map {
                            com.ironlog.app.ui.state.LoggedSet(
                                id = it.id,
                                weight = it.weight,
                                reps = it.reps,
                                type = it.type,
                                rpe = it.rpe,
                                rir = it.rir,
                                note = it.note,
                                orm = it.orm,
                                trackingType = it.trackingType,
                                durationSec = it.durationSec,
                            )
                        }
                    }
                }.toMap(),
                exerciseNotes = dto.exerciseNotes.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
                supersetGroups = dto.supersetGroups.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
                restTimer = com.ironlog.app.ui.state.RestTimerState(
                    active = dto.restTimer.active,
                    endTime = dto.restTimer.endTime,
                    total = dto.restTimer.total,
                    paused = dto.restTimer.paused,
                    pausedAt = dto.restTimer.pausedAt,
                    triggerExIndex = dto.restTimer.triggerExIndex,
                ),
                addedExercises = dto.addedExercises.map {
                    AddedExerciseEntry(
                        exerciseId = it.exerciseId,
                        name = it.name,
                        trackingType = it.trackingType,
                        equipment = it.equipment,
                        sets = it.sets,
                        reps = it.reps,
                    )
                },
                removedBaseExerciseIndices = dto.removedBaseExerciseIndices.toSet(),
            )
            // Restore swapped-exercise overlays. Reconstruct a minimal LegacyExerciseShape so the UI
            // shows the swapped exercise name/trackingType immediately on resume.
            val restoredSwaps: Map<Int, Any> = dto.swappedExercises.mapNotNull { (idxStr, sw) ->
                val idx = idxStr.toIntOrNull() ?: return@mapNotNull null
                idx to com.ironlog.app.data.model.LegacyExerciseShape(
                    id = sw.id,
                    exerciseId = sw.id,
                    name = sw.name,
                    primaryMuscles = emptyList(),
                    primaryMuscle = null,
                    secondaryMuscles = emptyList(),
                    equipment = sw.equipment,
                    category = "",
                    trackingType = sw.trackingType,
                    isCustom = false,
                    aliases = emptyList(),
                    isBodyweight = false,
                    movementPattern = null,
                    difficulty = null,
                    apparatus = null,
                    equipmentDetail = null,
                    sourceTags = emptyList(),
                    notes = "",
                )
            }.toMap()

            dispatch(WorkoutAction.HydrateState(restored.copy(swappedExercises = restoredSwaps)))

            // Restore exercise display order if persisted.
            if (dto.orderedIndices.isNotEmpty()) {
                persistedOrderedIndices = dto.orderedIndices
                _restoredOrderedIndices.value = dto.orderedIndices
            }

            // If the draft already contains sets the timer should already be running
            // (the user logged sets before backgrounding the app).
            val hasExistingSets = restored.setLog.values.any { it.isNotEmpty() }
            if (hasExistingSets) startTimerOnFirstSet()
        }
    }

    fun dismissCompletionSheet() {
        _showCompletionSheet.value = false
    }

    fun swapExercise(exIndex: Int, activeExerciseId: String, newExerciseId: String) {
        viewModelScope.launch {
            val workoutExId = resolveWorkoutExerciseId(exIndex, activeExerciseId) ?: return@launch
            runCatching { workoutRepo.swapWorkoutExercise(workoutExId, newExerciseId) }
            workoutExerciseIdByIndex = workoutExerciseIdByIndex + (exIndex to workoutExId)
        }
    }

    fun addExerciseToWorkout(exIndex: Int, exerciseId: String) {
        viewModelScope.launch {
            val wId = waitForActiveWorkoutId() ?: return@launch
            runCatching {
                val we = workoutRepo.addExerciseToWorkout(wId, exerciseId)
                workoutExerciseIdByIndex = workoutExerciseIdByIndex + (exIndex to we.uid)
            }
        }
    }

    fun persistSuperset(exIndex: Int, exerciseId: String, group: String?) {
        viewModelScope.launch {
            val workoutExId = resolveWorkoutExerciseId(exIndex, exerciseId) ?: return@launch
            runCatching { workoutRepo.updateWorkoutExerciseSuperset(workoutExId, group) }
        }
    }

    fun rehydrateSetLogFromDatabase(exerciseIdsInUiOrder: List<String>) {
        val workoutId = activeWorkoutId ?: return
        if (exerciseIdsInUiOrder.isEmpty()) return
        viewModelScope.launch {
            val detail = runCatching { workoutRepo.getWorkoutDetailSnapshot(workoutId) }.getOrNull() ?: return@launch
            bindWorkoutExerciseRows(detail.exercises, exerciseIdsInUiOrder)
            if (detail.sets.isEmpty()) return@launch

            val grouped = detail.sets
                .filter { !it.isWarmup || it.weight > 0.0 || it.reps > 0.0 }
                .groupBy { it.workoutExerciseUid }
            if (grouped.isEmpty()) return@launch

            val mergedSetLog = _workoutState.value.setLog.toMutableMap()
            workoutExerciseIdByIndex.forEach { (uiIndex, workoutExerciseId) ->
                val dbSets = grouped[workoutExerciseId]
                    .orEmpty()
                    .sortedBy { it.setIndex }
                    .map { row ->
                        LoggedSet(
                            id = row.uid.ifBlank { UUID.randomUUID().toString().replace("-", "").take(12) },
                            weight = row.weight,
                            reps = row.reps,
                            type = when {
                                row.isWarmup -> "warmup"
                                row.isDropset -> "drop"
                                row.isAmrap -> "amrap"
                                row.toFailure -> "failure"
                                else -> "normal"
                            },
                            rpe = row.rpe,
                            rir = row.rir?.roundToInt(),
                            trackingType = "weight_reps",
                            durationSec = null,
                            orm = if (row.weight > 0.0 && row.reps > 0.0) row.weight * (1.0 + (row.reps / 30.0)) else 0.0,
                        )
                    }
                if (dbSets.isEmpty()) return@forEach

                val current = mergedSetLog[uiIndex].orEmpty()
                if (dbSets.size >= current.size) {
                    mergedSetLog[uiIndex] = dbSets
                }
            }

            val currentState = _workoutState.value
            if (mergedSetLog != currentState.setLog) {
                _workoutState.value = currentState.copy(setLog = mergedSetLog)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    dayId: String = "",
    weightUnit: String = "kg",
    effortTracking: String = "off",
    hapticFeedback: Boolean = true,
    onFinish: (() -> Unit)? = null,
    onMinimize: (() -> Unit)? = null,
    vm: ActiveWorkoutViewModel = viewModel(),
    plansVm: PlansViewModel = viewModel(),
) {
    val c = useTheme()
    val context = LocalContext.current
    val appVm: AppDataViewModel = viewModel()
    val appState by appVm.state.collectAsState()
    val appSettings = appState.settings
    val cloudApiKey = remember(appSettings.cloudAiProviderPreset) {
        CloudAiKeyStore.load(context, appSettings.cloudAiProviderPreset)
    }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsRepo = remember { SettingsRepository() }
    val state by vm.workoutState.collectAsState()
    val timerStarted by vm.timerStarted.collectAsState()
    val prBanner by vm.prBanner.collectAsState()
    val showCompletionSheet by vm.showCompletionSheet.collectAsState()
    val activeWorkoutId by vm.activeWorkoutIdSignal.collectAsState()
    val plans by plansVm.plans.collectAsState()
    val restoredOrderedIndices by vm.restoredOrderedIndices.collectAsState()
    val exerciseRepo = remember { ExerciseRepository(context.applicationContext) }
    var swapTargetIndex by remember { mutableStateOf<Int?>(null) }
    var swapQuery by remember { mutableStateOf("") }
    var showSaveToPlanPrompt by remember { mutableStateOf(false) }
    var pendingOnFinish by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCelebration by remember { mutableStateOf<WorkoutCompletionCelebration?>(null) }
    var isFinalizingWorkout by remember { mutableStateOf(false) }
    var isSavingAddedExercises by remember { mutableStateOf(false) }
    var workoutActionError by remember { mutableStateOf<String?>(null) }
    var showCompletionConfetti by remember { mutableStateOf(false) }
    var confettiBurstId by remember { mutableStateOf(0) }
    val planRepo = remember { com.ironlog.app.data.repository.PlanRepository() }
    val scope = rememberCoroutineScope()
    var exercisePool by remember { mutableStateOf<List<LegacyExerciseShape>>(emptyList()) }
    var showAddExerciseSheet by remember { mutableStateOf(false) }
    var addExerciseQuery by remember { mutableStateOf("") }
    var restOverride by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var editingRestExIndex by remember { mutableStateOf<Int?>(null) }
    var restOverrideInput by remember { mutableStateOf("") }
    var restPickerMinutes by remember { mutableStateOf(1) }
    var restPickerSeconds by remember { mutableStateOf(30) }
    var defaultRestNormalSec by remember { mutableStateOf(90) }
    var defaultRestHeavySec by remember { mutableStateOf(180) }
    var settingsBarWeightKg by remember { mutableStateOf(20.0) }
    var keepAwakeDuringWorkout by remember { mutableStateOf(true) }
    var activeGymProfile by remember { mutableStateOf<GymProfileDto?>(null) }
    // Reorder state — orderedIndices mirrors RN's orderedIndices pattern
    var orderedIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    val lazyListState = rememberLazyListState()
    var scrollToNextSupersetIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(scrollToNextSupersetIndex) {
        scrollToNextSupersetIndex?.let { pos ->
            lazyListState.animateScrollToItem(pos)
            scrollToNextSupersetIndex = null
        }
    }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // The list has 2 non-exercise items before exercises (header + PR banner),
        // so adjust index by subtracting that offset.
        val offset = 2
        val fromIdx = from.index - offset
        val toIdx = to.index - offset
        if (fromIdx in orderedIndices.indices && toIdx in orderedIndices.indices) {
            orderedIndices = orderedIndices.toMutableList().apply {
                add(toIdx, removeAt(fromIdx))
            }
        }
    }

    // Resolve day and plan from plans VM using dayId
    val resolvedPair: Pair<UiPlanDay?, UiPlan?> = remember(dayId, plans) {
        if (dayId.isBlank()) {
            Pair(null, null)
        } else {
            var result: Pair<UiPlanDay?, UiPlan?> = Pair(null, null)
            for (plan in plans) {
                val day = plan.days.firstOrNull { it.id == dayId }
                if (day != null) { result = Pair(day, plan); break }
            }
            result
        }
    }
    val resolvedDay = resolvedPair.first
    val resolvedPlan = resolvedPair.second

    val baseExerciseEntries = remember(resolvedDay, state.removedBaseExerciseIndices) {
        (resolvedDay?.exercises.orEmpty()).mapIndexedNotNull { originalIndex, planEx ->
            if (originalIndex in state.removedBaseExerciseIndices) {
                null
            } else {
                originalIndex to normalizeSessionExercise(planEx, null)
            }
        }
    }
    val baseOriginalIndices = remember(baseExerciseEntries) { baseExerciseEntries.map { it.first } }
    val baseExercises = remember(baseExerciseEntries) { baseExerciseEntries.map { it.second } }
    val addedAsNormalized = remember(state.addedExercises) {
        state.addedExercises.map { entry ->
            NormalizedSessionExercise(
                name = entry.name,
                exerciseId = entry.exerciseId,
                sets = entry.sets,
                reps = entry.reps,
                trackingType = entry.trackingType,
                isWarmup = false,
                equipment = entry.equipment,
            )
        }
    }
    val exercises = remember(baseExercises, state.swappedExercises, addedAsNormalized) {
        val swapped = baseExercises.mapIndexed { idx, base ->
            val sw = state.swappedExercises[idx] as? LegacyExerciseShape
            if (sw == null) base else base.copy(
                name = sw.name,
                exerciseId = sw.id.ifBlank { sw.exerciseId },
                trackingType = sw.trackingType.ifBlank { base.trackingType },
                equipment = sw.equipment ?: base.equipment,
            )
        }
        swapped + addedAsNormalized
    }

    LaunchedEffect(dayId) {
        vm.initWorkout(dayId)
    }
    // Sync orderedIndices from the ViewModel when a draft is restored after minimize/resume.
    LaunchedEffect(restoredOrderedIndices) {
        if (restoredOrderedIndices.isNotEmpty() && restoredOrderedIndices.size == exercises.size) {
            orderedIndices = restoredOrderedIndices
        }
    }
    LaunchedEffect(exercises) {
        // Load ghost (last-session) data whenever the exercise list changes
        if (exercises.isNotEmpty()) {
            vm.loadGhostData(exercises.map { it.exerciseId })
        }
        when {
            // Initial setup: exercises just appeared for the first time.
            orderedIndices.isEmpty() && exercises.isNotEmpty() -> {
                orderedIndices = exercises.indices.toList()
            }
            // Exercise(s) added mid-workout: append the new index at the end.
            exercises.size > orderedIndices.size -> {
                val newIndices = (orderedIndices.size until exercises.size).toList()
                orderedIndices = orderedIndices + newIndices
            }
            // Removal is handled at the call site (onRemove lambda) to preserve order.
        }
    }
    // Keep VM in sync with the latest orderedIndices so persistDraft always saves them.
    LaunchedEffect(orderedIndices) {
        vm.updateOrderedIndices(orderedIndices)
    }
    // Rehydrate set log from DB only on first load or when NEW exercises are added/swapped.
    // Removals are handled exclusively by the reducer+dispatch path — re-running rehydrate
    // on removal races with the async DB delete and can re-insert stale workout exercise
    // bindings, making the exercise appear to "not delete".
    var lastHydratedExerciseIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(activeWorkoutId, exercises) {
        val currentIds = exercises.map { it.exerciseId }.toSet()
        val hasNewIds = currentIds.any { it !in lastHydratedExerciseIds }
        val isFirstLoad = lastHydratedExerciseIds.isEmpty()
        if (!activeWorkoutId.isNullOrBlank() && exercises.isNotEmpty() && (isFirstLoad || hasNewIds)) {
            vm.rehydrateSetLogFromDatabase(exercises.map { it.exerciseId })
        }
        lastHydratedExerciseIds = currentIds
    }
    LaunchedEffect(Unit) {
        exercisePool = runCatching { exerciseRepo.getExercisesSnapshot() }.getOrElse { emptyList() }
        keepAwakeDuringWorkout = settingsRepo.getBoolean("keep_screen_awake_active_workout", true)
        val rawSettings = settingsRepo.getString("ironlog_settings")
        runCatching {
            val json = org.json.JSONObject(rawSettings ?: "{}")
            defaultRestNormalSec = json.optInt("defaultRestSeconds", 90).coerceIn(15, 600)
            defaultRestHeavySec = json.optInt("defaultRestHeavySeconds", 180).coerceIn(30, 900)
            settingsBarWeightKg = json.optDouble("barWeightKg", 20.0).coerceIn(0.0, 100.0)
        }
        val raw = settingsRepo.getString("gym_profiles_json").orEmpty()
        val profiles = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<List<GymProfileDto>>(raw) }.getOrDefault(emptyList())
        val activeId = settingsRepo.getString("active_gym_profile_id")
        activeGymProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    }
    LaunchedEffect(activeWorkoutId) {
        val id = activeWorkoutId ?: return@LaunchedEffect
        val raw = settingsRepo.getString("active_workout_rest_override_$id").orEmpty()
        if (raw.isNotBlank()) {
            val restored = runCatching {
                val obj = org.json.JSONObject(raw)
                obj.keys().asSequence().mapNotNull { k ->
                    k.toIntOrNull()?.let { idx -> idx to obj.optInt(k, 0).coerceIn(15, 900) }
                }.toMap()
            }.getOrDefault(emptyMap())
            if (restored.isNotEmpty()) restOverride = restored
        }
    }
    LaunchedEffect(activeWorkoutId, restOverride) {
        val id = activeWorkoutId ?: return@LaunchedEffect
        val obj = org.json.JSONObject()
        restOverride.forEach { (k, v) -> obj.put(k.toString(), v) }
        settingsRepo.setString("active_workout_rest_override_$id", obj.toString(), "json")
    }
    val latestState by rememberUpdatedState(state)
    DisposableEffect(lifecycleOwner, activeWorkoutId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !activeWorkoutId.isNullOrBlank()) {
                vm.persistDraftBlocking(latestState)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(activeWorkoutId) {
        val idForEffect = activeWorkoutId
        onDispose {
            if (!idForEffect.isNullOrBlank()) vm.persistDraftBlocking(latestState)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val action = withContext(Dispatchers.IO) {
                val a = settingsRepo.getString("pending_workout_action").orEmpty()
                if (a.isNotBlank()) settingsRepo.removeSetting("pending_workout_action")
                a
            }
            if (action.isNotBlank()) {
                when (action) {
                    com.ironlog.app.services.NotificationActionRouter.Actions.ADD_30S -> {
                        vm.dispatch(WorkoutAction.Add30s)
                    }
                    com.ironlog.app.services.NotificationActionRouter.Actions.SKIP_REST -> {
                        vm.dispatch(WorkoutAction.SkipRest)
                        vm.onRestCleared()
                    }
                    com.ironlog.app.services.NotificationActionRouter.Actions.FINISH_WORKOUT -> {
                        vm.finishWorkout()
                    }
                }
            }
            delay(900)
        }
    }
    BackHandler(enabled = true) {
        if (showCompletionSheet) vm.dismissCompletionSheet() else onMinimize?.invoke()
    }
    DisposableEffect(view, keepAwakeDuringWorkout) {
        val prev = view.keepScreenOn
        if (keepAwakeDuringWorkout) view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        // FIXED: 14 — contentPadding bottom expands when rest timer is active so it doesn't cover FINISH WORKOUT
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (state.restTimer.active) 130.dp else 80.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                    shape = RoundedCornerShape(IronLogRadius.xl.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // ── Row 1: name + close/menu ──────────────────────────
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    resolvedDay?.name ?: resolvedPlan?.name ?: "Workout",
                                    color = c.text,
                                    fontWeight = FontWeight(IronLogType.section.fontWeight),
                                    fontSize = IronLogType.section.fontSize.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${exercises.size} exercise${if (exercises.size == 1) "" else "s"}",
                                    color = c.muted,
                                    fontSize = IronLogType.meta.fontSize.sp,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                var showHeaderMenu by remember { mutableStateOf(false) }
                                Box {
                                    androidx.compose.material3.IconButton(onClick = { showHeaderMenu = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = c.muted)
                                    }
                                    androidx.compose.material3.DropdownMenu(showHeaderMenu, onDismissRequest = { showHeaderMenu = false }) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Minimize", color = c.text) },
                                            onClick = { showHeaderMenu = false; onMinimize?.invoke() },
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Discard Workout", color = c.danger) },
                                        onClick = {
                                            showHeaderMenu = false
                                            vm.discardWorkout(
                                                onError = { workoutActionError = it },
                                                onDone = { onFinish?.invoke() },
                                            )
                                        },
                                        )
                                    }
                                }
                                // FIXED: 12 — Duplicate FINISH button removed from header; only FINISH WORKOUT at bottom
                            }
                        }
                        // ── Row 2: timer + elapsed + minimize ─────────────────
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .background(c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    ActiveWorkoutRollingTimerText(vm, c)
                                }
                                Text("elapsed", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                            }
                            Text(
                                "MINIMIZE",
                                color = c.accent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = IronLogType.button.fontSize.sp,
                                letterSpacing = 1.sp,
                                modifier = Modifier.clickable { onMinimize?.invoke() }.padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                        // FIXED: 15 — Live volume comparison vs previous session
                        val currentVolumeKg = remember(state.setLog) {
                            state.setLog.values.flatten()
                                .filter { it.type.ifBlank { "normal" } != "warmup" }
                                .sumOf { it.weight * it.reps }
                        }
                        val previousVolumeKg = remember(state.ghostData) {
                            state.ghostData.values
                                .flatMap { it.sets }
                                .filter { it.type.ifBlank { "normal" } != "warmup" }
                                .sumOf { it.weight * it.reps }
                        }
                        if (currentVolumeKg > 0) {
                            val delta = currentVolumeKg - previousVolumeKg
                            val arrow = if (delta >= 0) "↑" else "↓"
                            val volStr = formatWeightFromKg(currentVolumeKg, weightUnit)
                            val deltaColor = if (previousVolumeKg <= 0 || delta >= 0) c.success else c.danger
                            Text(
                                "You've lifted $arrow $volStr${if (previousVolumeKg > 0) " (${if (delta >= 0) "+" else ""}${formatWeightFromKg(delta, weightUnit)} vs prev)" else ""}",
                                color = deltaColor,
                                fontSize = IronLogType.meta.fontSize.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            item {
                AnimatedVisibility(visible = prBanner != null, enter = fadeIn(), exit = fadeOut()) {
                    prBanner?.let { msg ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(c.gold.copy(alpha = 0.15f), RoundedCornerShape(IronLogRadius.lg.dp))
                                .border(1.dp, c.gold.copy(alpha = 0.4f), RoundedCornerShape(IronLogRadius.lg.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = c.gold, modifier = Modifier.size(20.dp))
                            Text(
                                msg,
                                color = c.gold,
                                fontWeight = FontWeight(IronLogType.section.fontWeight),
                                fontSize = IronLogType.section.fontSize.sp,
                            )
                        }
                    }
                }
            }
            val displayIndices = if (orderedIndices.size == exercises.size) orderedIndices else exercises.indices.toList()
            items(displayIndices, key = { idx ->
                val ex = exercises.getOrNull(idx)
                if (ex != null) "${ex.exerciseId}::$idx" else idx.toString()
            }) { exIndex ->
                val ex = exercises.getOrNull(exIndex) ?: return@items
                val baseOriginalIndex = baseOriginalIndices.getOrNull(exIndex)
                val defaultRestSec = baseOriginalIndex?.let { resolvedDay?.exercises?.getOrNull(it)?.restSeconds }
                    ?: if (isHeavyCompoundExercise(ex.name)) defaultRestHeavySec else defaultRestNormalSec
                val effectiveRest = restOverride[exIndex] ?: defaultRestSec.coerceIn(15, 900)
                val ghost = state.ghostData[exIndex]
                val exerciseNote = state.exerciseNotes[exIndex].orEmpty()
                val supersetGroup = state.supersetGroups[exIndex]?.ifBlank { null }
                // Coordinated superset rest: only the last exercise in a group triggers the rest timer.
                // Mid-superset exercises pass restSec=0 so the timer stays quiet between exercises.
                val isLastInSuperset = if (supersetGroup.isNullOrBlank()) true else {
                    val groupIndices = displayIndices.filter { state.supersetGroups[it]?.ifBlank { null } == supersetGroup }
                    groupIndices.lastOrNull() == exIndex
                }
                val restForLogSet = if (isLastInSuperset) effectiveRest else 0
                ReorderableItem(reorderState, key = "${ex.exerciseId}::$exIndex") { isDragging ->
                    ExerciseCard(
                        exIndex = exIndex,
                        exercise = ex,
                        loggedSets = state.setLog[exIndex].orEmpty(),
                        input = state.inputs[exIndex]?.weight.orEmpty() to state.inputs[exIndex]?.reps.orEmpty(),
                        dispatch = vm::dispatch,
                        baseExercisesCount = baseExercises.size,
                        onRemoveExercise = {
                            // Update orderedIndices before dispatching so the LazyColumn
                            // key map stays consistent: drop the removed index and shift the rest.
                            orderedIndices = orderedIndices
                                .filter { it != exIndex }
                                .map { if (it > exIndex) it - 1 else it }
                            // Also re-index the rest-override map.
                            restOverride = restOverride
                                .filterKeys { it != exIndex }
                                .mapKeys { (k, _) -> if (k > exIndex) k - 1 else k }
                            vm.dispatch(
                                WorkoutAction.RemoveExercise(
                                    exIndex = exIndex,
                                    baseExercisesCount = baseExercises.size,
                                    removedBaseIndex = baseOriginalIndex,
                                )
                            )
                        },
                        onLogSet = { weight, reps ->
                            vm.logSet(exIndex, ex.exerciseId, weight, reps, ex.trackingType, restForLogSet)
                            // GAP-01: superset auto-rotation — scroll to next exercise in group
                            if (!isLastInSuperset && !supersetGroup.isNullOrBlank()) {
                                val currentPosInDisplay = displayIndices.indexOf(exIndex)
                                val nextExIndex = displayIndices.drop(currentPosInDisplay + 1)
                                    .firstOrNull { state.supersetGroups[it]?.ifBlank { null } == supersetGroup }
                                if (nextExIndex != null) {
                                    val nextPosInDisplay = displayIndices.indexOf(nextExIndex)
                                    // +2 for: header card (item 0) + PR banner (item 1)
                                    scrollToNextSupersetIndex = nextPosInDisplay + 2
                                }
                            }
                        },
                        weightUnit = weightUnit,
                        effortTracking = effortTracking,
                        hapticFeedback = hapticFeedback,
                        onSetRpeChanged = { setIndex, rpe -> vm.persistSetRpe(exIndex, ex.exerciseId, setIndex, rpe) },
                        onSetRirChanged = { setIndex, rir -> vm.persistSetRir(exIndex, ex.exerciseId, setIndex, rir) },
                        onSetTypeChanged = { setIndex, type -> vm.persistSetType(exIndex, ex.exerciseId, setIndex, type) },
                        onSetValuesChanged = { setIndex, w, r -> vm.persistSetValues(exIndex, ex.exerciseId, setIndex, w, r) },
                        onInsertWarmups = { warmups -> vm.persistWarmupSets(exIndex, ex.exerciseId, warmups) },
                        onSwapRequest = { swapTargetIndex = exIndex; swapQuery = "" },
                        restSec = effectiveRest,
                        onEditRest = {
                            editingRestExIndex = exIndex
                            restOverrideInput = effectiveRest.toString()
                            restPickerMinutes = (effectiveRest / 60).coerceIn(0, 10)
                            val rem = effectiveRest % 60
                            restPickerSeconds = when {
                                rem < 8 -> 0
                                rem < 23 -> 15
                                rem < 38 -> 30
                                else -> 45
                            }
                        },
                        ghost = ghost,
                        exerciseNote = exerciseNote,
                        supersetGroup = supersetGroup,
                        onSupersetChange = { group ->
                            vm.dispatch(WorkoutAction.AssignSuperset(exIndex, group))
                            vm.persistSuperset(exIndex, ex.exerciseId, group)
                        },
                        isDragging = isDragging,
                        dragHandleModifier = Modifier.draggableHandle(),
                        activeProfile = activeGymProfile,
                        settingsBarWeightKg = settingsBarWeightKg,
                        targetOverride = state.targetOverrides[exIndex],
                    )
                }
            }
            item {
                // FIXED: 13 — ADD EXERCISE button styled interactive (accent tint)
                Button(
                    onClick = {
                        if (hapticFeedback) HapticsEngine.lightConfirm(context)
                        addExerciseQuery = ""
                        showAddExerciseSheet = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent.copy(alpha = 0.10f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(IronLogRadius.lg.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)),
                ) {
                    Text(
                        "+ ADD EXERCISE",
                        color = c.accent,
                        fontWeight = FontWeight(IronLogType.button.fontWeight),
                        fontSize = IronLogType.meta.fontSize.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val totalVolume = state.setLog.values.flatten().filter { it.type != "warmup" }.sumOf { it.weight * it.reps }
                val comparison = getFunComparison(totalVolume)
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                    shape = RoundedCornerShape(IronLogRadius.lg.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Volume: ${formatWeightFromKg(totalVolume, weightUnit)}",
                            color = c.text,
                            fontWeight = FontWeight(IronLogType.section.fontWeight),
                            fontSize = IronLogType.section.fontSize.sp,
                        )
                        Text(
                            "About ${comparison.text}",
                            color = c.muted,
                            fontSize = IronLogType.body.fontSize.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (hapticFeedback) HapticsEngine.lightConfirm(context)
                        vm.finishWorkout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(IronLogRadius.lg.dp),
                ) {
                    Text(
                        "FINISH WORKOUT",
                        color = c.textOnAccent,
                        fontWeight = FontWeight(IronLogType.button.fontWeight),
                        fontSize = IronLogType.section.fontSize.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        RestTimerPanel(
            restTimer = state.restTimer,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            dispatch = vm::dispatch,
            onRestCleared = vm::onRestCleared,
            hapticFeedback = hapticFeedback,
        )
    }

    if (showCompletionSheet) {
        val elapsedSecondsForCompletion by vm.elapsedSeconds.collectAsState()
        WorkoutCompletionSheet(
            totalSets = state.setLog.values.sumOf { it.size },
            totalVolume = state.setLog.values.flatten().filter { it.type != "warmup" }.sumOf { it.weight * it.reps },
            durationSeconds = elapsedSecondsForCompletion,
            weightUnit = weightUnit,
            exerciseNames  = exercises.map { it.name },
            planDayName    = resolvedDay?.name ?: "Free Session",
            goalMode       = appSettings.goalMode,
            cloudBaseUrl   = appSettings.cloudAiBaseUrl,
            cloudApiKey    = cloudApiKey,
            cloudModelName = appSettings.cloudAiModelName,
            cloudApiFormat = appSettings.cloudAiApiFormat,
            onComplete = { rating, notes ->
                if (hapticFeedback) HapticsEngine.success(context)
                isFinalizingWorkout = true
                val volume = state.setLog.values.flatten().filter { it.type != "warmup" }.sumOf { it.weight * it.reps }
                val hasAddedExercises = state.addedExercises.isNotEmpty() && !dayId.isBlank()
                vm.completeWorkout(
                    rating = rating,
                    totalVolumeKg = volume,
                    notes = notes,
                    onError = { message ->
                        isFinalizingWorkout = false
                        workoutActionError = message
                    },
                ) { celebration ->
                    if (hasAddedExercises) {
                        pendingCelebration = celebration
                        pendingOnFinish = onFinish
                        showSaveToPlanPrompt = true
                        isFinalizingWorkout = false
                    } else {
                        isFinalizingWorkout = false
                        if (celebration.hasPrCelebration || celebration.hasStreak30Celebration) {
                            showCompletionConfetti = true
                            confettiBurstId++
                            scope.launch {
                                delay(1750)
                                showCompletionConfetti = false
                                onFinish?.invoke()
                            }
                        } else {
                            onFinish?.invoke()
                        }
                    }
                }
            },
            onDismiss = { vm.dismissCompletionSheet() },
        )
    }

    if (showSaveToPlanPrompt && state.addedExercises.isNotEmpty()) {
        val addedNames = state.addedExercises.joinToString(", ") { it.name }
        fun continueAfterSavePrompt() {
            val celebration = pendingCelebration
            pendingCelebration = null
            if (celebration?.hasPrCelebration == true || celebration?.hasStreak30Celebration == true) {
                showCompletionConfetti = true
                confettiBurstId++
                scope.launch {
                    delay(1750)
                    showCompletionConfetti = false
                    pendingOnFinish?.invoke()
                    pendingOnFinish = null
                }
            } else {
                pendingOnFinish?.invoke()
                pendingOnFinish = null
            }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!isSavingAddedExercises) {
                    showSaveToPlanPrompt = false
                    continueAfterSavePrompt()
                }
            },
            title = { Text("Save to Plan?", color = c.text) },
            text = {
                Text(
                    "You added exercises during this session ($addedNames). Add them to this plan day for next time?",
                    color = c.subtext,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !isSavingAddedExercises,
                    onClick = {
                        isSavingAddedExercises = true
                        scope.launch {
                            try {
                                state.addedExercises.forEach { entry ->
                                    planRepo.addExerciseToPlanDay(
                                        dayId,
                                        com.ironlog.app.data.model.PlanExerciseInput(
                                            exerciseId = entry.exerciseId,
                                            sets = entry.sets,
                                            reps = entry.reps.toString(),
                                        ),
                                    )
                                }
                                showSaveToPlanPrompt = false
                                continueAfterSavePrompt()
                            } catch (error: Throwable) {
                                Timber.e(error, "Failed to save workout-added exercises to plan day %s", dayId)
                                workoutActionError = "Your workout was saved, but the added exercises could not be added to the plan. You can retry or choose Not Now."
                            } finally {
                                isSavingAddedExercises = false
                            }
                        }
                    },
                ) { Text(if (isSavingAddedExercises) "SAVING…" else "SAVE TO PLAN", color = c.accent) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !isSavingAddedExercises,
                    onClick = {
                        showSaveToPlanPrompt = false
                        continueAfterSavePrompt()
                    },
                ) {
                    Text("NOT NOW", color = c.muted)
                }
            },
            containerColor = c.card,
        )
    }

    workoutActionError?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { workoutActionError = null },
            title = { Text("Couldn’t finish that action", color = c.text) },
            text = { Text(message, color = c.subtext) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { workoutActionError = null }) {
                    Text("OK", color = c.accent)
                }
            },
            containerColor = c.card,
        )
    }

    if (showCompletionConfetti) {
        ConfettiOverlay(
            modifier = Modifier.fillMaxSize(),
            burstId = confettiBurstId,
            accent = c.accent,
        )
    }

    if (isFinalizingWorkout) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = c.bg.copy(alpha = 0.92f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = c.accent)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Finalizing workout...",
                    color = c.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = IronLogType.section.fontSize.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Logging history and refreshing all insights.",
                    color = c.subtext,
                    textAlign = TextAlign.Center,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
        }
    }

    val targetIndex = swapTargetIndex
    if (targetIndex != null) {
        val targetExercise = exercises.getOrNull(targetIndex)
        val filtered = exercisePool.filter {
            val q = swapQuery.trim().lowercase()
            q.isBlank() || it.name.lowercase().contains(q) || it.primaryMuscle.orEmpty().lowercase().contains(q)
        }.take(40)
        ModalBottomSheet(
            onDismissRequest = { swapTargetIndex = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.card,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Swap Exercise", color = c.text, fontWeight = FontWeight(IronLogType.title.fontWeight), fontSize = IronLogType.title.fontSize.sp)
                Text(targetExercise?.name ?: "", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                OutlinedTextField(
                    value = swapQuery,
                    onValueChange = { swapQuery = it },
                    label = { Text("Search exercise") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No exercises found", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                            }
                        }
                    }
                    itemsIndexed(filtered) { _, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = c.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (targetExercise != null) {
                                    vm.dispatch(WorkoutAction.SwapExercise(targetIndex, item))
                                    vm.swapExercise(targetIndex, targetExercise.exerciseId, item.id.ifBlank { item.exerciseId })
                                }
                                swapTargetIndex = null
                            },
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(item.name, color = c.text, fontWeight = FontWeight(IronLogType.section.fontWeight), fontSize = IronLogType.section.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.primaryMuscle ?: "Other"} · ${item.equipment}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    val restExIdx = editingRestExIndex
    if (restExIdx != null) {
        val presets = listOf(30, 60, 90, 120, 180, 300)
        val secOptions = listOf(0, 15, 30, 45)
        val minState = rememberLazyListState(initialFirstVisibleItemIndex = restPickerMinutes)
        val secState = rememberLazyListState(initialFirstVisibleItemIndex = secOptions.indexOf(restPickerSeconds).coerceAtLeast(0))
        ModalBottomSheet(
            onDismissRequest = { editingRestExIndex = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.card,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Rest Timer",
                    color = c.text,
                    fontWeight = FontWeight(IronLogType.title.fontWeight),
                    fontSize = IronLogType.title.fontSize.sp,
                )
                Text(
                    "Override rest duration for this exercise",
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(presets) { _, sec ->
                        val isSelected = (restPickerMinutes * 60 + restPickerSeconds) == sec
                        Box(
                            Modifier
                                .background(
                                    if (isSelected) c.accent else c.surface,
                                    RoundedCornerShape(IronLogRadius.full.dp),
                                )
                                .border(1.dp, if (isSelected) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                .clickable {
                                    restPickerMinutes = sec / 60
                                    restPickerSeconds = sec % 60
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${sec}s",
                                color = if (isSelected) c.textOnAccent else c.text,
                                fontSize = IronLogType.body.fontSize.sp,
                                fontWeight = FontWeight(IronLogType.button.fontWeight),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MIN", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = minState,
                            flingBehavior = rememberSnapFlingBehavior(minState),
                            modifier = Modifier.height(120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(11) { m ->
                                val active = m == restPickerMinutes
                                Text(
                                    m.toString(),
                                    color = if (active) c.accent else c.subtext,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = if (active) IronLogType.title.fontSize.sp else IronLogType.section.fontSize.sp,
                                    modifier = Modifier.clickable { restPickerMinutes = m },
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SEC", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = secState,
                            flingBehavior = rememberSnapFlingBehavior(secState),
                            modifier = Modifier.height(120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(secOptions.size) { i ->
                                val value = secOptions[i]
                                val active = value == restPickerSeconds
                                Text(
                                    value.toString().padStart(2, '0'),
                                    color = if (active) c.accent else c.subtext,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = if (active) IronLogType.title.fontSize.sp else IronLogType.section.fontSize.sp,
                                    modifier = Modifier.clickable { restPickerSeconds = value },
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val sec = (restPickerMinutes * 60 + restPickerSeconds).coerceIn(15, 600)
                        restOverride = restOverride + (restExIdx to sec)
                        editingRestExIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(IronLogRadius.lg.dp),
                ) {
                    Text("APPLY", color = c.textOnAccent, fontWeight = FontWeight(IronLogType.button.fontWeight))
                }
            }
        }
    }

    if (showAddExerciseSheet) {
        val filteredAdd = exercisePool.filter {
            val q = addExerciseQuery.trim().lowercase()
            q.isBlank() || it.name.lowercase().contains(q) || it.primaryMuscle.orEmpty().lowercase().contains(q)
        }.take(50)
        ModalBottomSheet(
            onDismissRequest = { showAddExerciseSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.card,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Exercise", color = c.text, fontWeight = FontWeight(IronLogType.title.fontWeight), fontSize = IronLogType.title.fontSize.sp)
                Text("${exercises.size} exercises in session", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                OutlinedTextField(
                    value = addExerciseQuery,
                    onValueChange = { addExerciseQuery = it },
                    label = { Text("Search exercise") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().height(400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(filteredAdd) { _, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = c.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                            modifier = Modifier.fillMaxWidth().clickable {
                                val entry = AddedExerciseEntry(
                                    exerciseId = item.id.ifBlank { item.exerciseId },
                                    name = item.name,
                                    trackingType = item.trackingType.ifBlank { "weight_reps" },
                                    equipment = item.equipment,
                                )
                                vm.dispatch(WorkoutAction.AddExercise(entry))
                                vm.addExerciseToWorkout(exercises.size, entry.exerciseId)
                                showAddExerciseSheet = false
                            },
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(item.name, color = c.text, fontWeight = FontWeight(IronLogType.section.fontWeight), fontSize = IronLogType.section.fontSize.sp)
                                Text("${item.primaryMuscle ?: "Other"} · ${item.equipment}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutCompletionSheet(
    totalSets: Int,
    totalVolume: Double,
    durationSeconds: Int,
    weightUnit: String,
    exerciseNames: List<String> = emptyList(),
    planDayName: String = "Free Session",
    goalMode: String = "hypertrophy",
    cloudBaseUrl: String = "",
    cloudApiKey: String = "",
    cloudModelName: String = "",
    cloudApiFormat: String = "openai",
    onComplete: (rating: Int, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = useTheme()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedRating by remember { mutableStateOf(0) }
    var workoutNotes by remember { mutableStateOf("") }
    val cloudConfigured = cloudApiKey.isNotBlank() && cloudBaseUrl.isNotBlank() && cloudModelName.isNotBlank()
    var debriefText by remember { mutableStateOf<String?>(null) }
    var debriefLoading by remember { mutableStateOf(cloudConfigured) }

    LaunchedEffect(Unit) {
        if (!cloudConfigured) return@LaunchedEffect
        debriefLoading = true
        debriefText = CloudAiEngine.askDayEvaluation(
            baseUrl       = cloudBaseUrl,
            apiKey        = cloudApiKey,
            modelName     = cloudModelName,
            apiFormat     = cloudApiFormat,
            dayName       = planDayName,
            exerciseNames = exerciseNames,
            goalMode      = goalMode,
        )
        debriefLoading = false
    }

    // Fun comparison — pick highest threshold that doesn't exceed totalVolume (in kg)
    val funComparison = remember(totalVolume) {
        FUN_COMPARISONS.lastOrNull { it.threshold <= totalVolume.toInt() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Trophy icon in accent circle ────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(72.dp)
                        .background(c.accent.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, c.accent.copy(alpha = 0.35f), CircleShape),
                )
                Icon(
                    Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(36.dp),
                )
            }

            // ── Eyebrow + title ─────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "SESSION COMPLETE",
                    color = c.accent,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
                Text(
                    "Great work. Keep it up.",
                    color = c.text,
                    fontWeight = FontWeight(IronLogType.title.fontWeight),
                    fontSize = IronLogType.title.fontSize.sp,
                )
            }

            // ── Stats row ───────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(IronLogRadius.lg.dp))
                    .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CompletionStat("Duration", formatDurationShort(durationSeconds))
                Box(Modifier.width(1.dp).height(36.dp).background(c.cardBorder))
                CompletionStat("Sets", totalSets.toString())
                Box(Modifier.width(1.dp).height(36.dp).background(c.cardBorder))
                CompletionStat("Volume", formatWeightFromKg(totalVolume, weightUnit))
            }

            // ── Fun comparison ──────────────────────────────────────────────
            if (funComparison != null) {
                Text(
                    "That's the weight of ${funComparison.text}!",
                    color = c.subtext,
                    fontSize = IronLogType.meta.fontSize.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // ── AI Session Debrief ──────────────────────────────────────────
            if (cloudConfigured) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                        .background(c.accent.copy(alpha = 0.07f))
                        .border(1.dp, c.accent.copy(alpha = 0.25f), RoundedCornerShape(IronLogRadius.lg.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "AI SESSION DEBRIEF",
                        color = c.accent,
                        fontSize = IronLogType.eyebrow.fontSize.sp,
                        fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                        letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                    )
                    if (debriefLoading) {
                        Column(Modifier.shimmer(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(2) { idx ->
                                Box(
                                    Modifier
                                        .fillMaxWidth(if (idx == 1) 0.7f else 1f)
                                        .height(13.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(c.faint)
                                )
                            }
                        }
                    } else {
                        Text(
                            debriefText ?: "No evaluation available.",
                            color = c.text,
                            fontSize = IronLogType.body.fontSize.sp,
                            lineHeight = IronLogType.body.lineHeight.sp,
                        )
                    }
                }
            }

            // ── Star rating ─────────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "RATE THIS SESSION",
                    color = c.muted,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    (1..5).forEach { star ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clickable { selectedRating = star },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (star <= selectedRating) "★" else "☆",
                                color = if (star <= selectedRating) c.gold else c.faint,
                                fontWeight = FontWeight(IronLogType.display.fontWeight),
                                fontSize = IronLogType.metric.fontSize.sp,
                            )
                        }
                    }
                }
            }

            // ── Workout notes ────────────────────────────────────────────────
            OutlinedTextField(
                value = workoutNotes,
                onValueChange = { workoutNotes = it },
                placeholder = { Text("Add session notes (optional)…", color = c.muted, fontSize = IronLogType.body.fontSize.sp) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            // ── Primary CTA ─────────────────────────────────────────────────
            Button(
                onClick = { onComplete(selectedRating, workoutNotes.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(IronLogRadius.lg.dp),
            ) {
                Text(
                    "SAVE & FINISH",
                    color = c.textOnAccent,
                    fontWeight = FontWeight(IronLogType.button.fontWeight),
                    fontSize = IronLogType.section.fontSize.sp,
                    letterSpacing = IronLogType.button.letterSpacing.sp,
                )
            }

            // ── Share link ──────────────────────────────────────────────────
            Text(
                "SHARE SUMMARY",
                color = c.accent,
                fontWeight = FontWeight(IronLogType.button.fontWeight),
                fontSize = IronLogType.meta.fontSize.sp,
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                modifier = Modifier.clickable {
                    ShareService.shareMinimalCardImage(
                        context = context,
                        title = "Ironlog Workout",
                        headline = "Session Complete",
                        metrics = listOf(
                            ShareService.ShareMetric("Duration", formatDurationShort(durationSeconds)),
                            ShareService.ShareMetric("Sets", totalSets.toString()),
                            ShareService.ShareMetric("Volume", formatWeightFromKg(totalVolume, weightUnit)),
                            ShareService.ShareMetric("Rating", if (selectedRating > 0) "$selectedRating/5" else "-"),
                        ),
                        footnote = funComparison?.let { "Equivalent of ${it.text}" },
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CompletionStat(label: String, value: String) {
    val c = useTheme()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            color = c.text,
            fontWeight = FontWeight(IronLogType.title.fontWeight),
            fontSize = IronLogType.title.fontSize.sp,
        )
        Text(
            label,
            color = c.muted,
            fontSize = IronLogType.meta.fontSize.sp,
            fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
        )
    }
}

@Composable
private fun ExerciseCard(
    exIndex: Int,
    exercise: NormalizedSessionExercise,
    loggedSets: List<LoggedSet>,
    input: Pair<String, String>,
    dispatch: (WorkoutAction) -> Unit,
    baseExercisesCount: Int = 0,
    onRemoveExercise: (() -> Unit)? = null,
    onLogSet: (String, String) -> Unit,
    weightUnit: String,
    effortTracking: String,
    hapticFeedback: Boolean,
    onSetRpeChanged: (setIndex: Int, rpe: Double?) -> Unit,
    onSetRirChanged: (setIndex: Int, rir: Int?) -> Unit,
    onSetTypeChanged: (setIndex: Int, type: String) -> Unit,
    onSetValuesChanged: (setIndex: Int, weight: Double?, reps: Double?) -> Unit,
    onInsertWarmups: (warmups: List<LoggedSet>) -> Unit,
    onSwapRequest: () -> Unit,
    restSec: Int = 90,
    onEditRest: () -> Unit = {},
    ghost: com.ironlog.app.ui.state.GhostData? = null,
    exerciseNote: String = "",
    supersetGroup: String? = null,
    onSupersetChange: (String?) -> Unit = {},
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    activeProfile: GymProfileDto? = null,
    settingsBarWeightKg: Double = 20.0,
    targetOverride: com.ironlog.app.ui.state.TargetOverride? = null,  // GAP-23
) {
    val c = useTheme()
    val context = LocalContext.current
    var weight by remember(exIndex, input.first) { mutableStateOf(input.first) }
    var reps by remember(exIndex, input.second) { mutableStateOf(input.second) }
    var noteExpanded by remember { mutableStateOf(exerciseNote.isNotBlank()) }
    var localNote by remember(exerciseNote) { mutableStateOf(exerciseNote) }
    val cardElevation = if (isDragging) 8.dp else 0.dp
    
    var showPlateModal by remember { mutableStateOf(false) }
    var plateTarget by remember { mutableStateOf(0.0) }
    var showCopyModal by remember { mutableStateOf(false) }
    var showSupersetModal by remember { mutableStateOf(false) }
    // GAP-23: target override dialog state
    var showTargetOverrideDialog by remember { mutableStateOf(false) }
    var overrideSetsInput by remember(targetOverride) { mutableStateOf(targetOverride?.sets?.toString() ?: exercise.sets.toString()) }
    var overrideRepsInput by remember(targetOverride) { mutableStateOf(targetOverride?.reps?.toString() ?: exercise.reps.toString()) }
    val supColor = when (supersetGroup) {
        "A" -> Color(0xFFFF7043)
        "B" -> Color(0xFF42A5F5)
        "C" -> Color(0xFF66BB6A)
        else -> c.accent
    }

    val plateText = remember(weight, weightUnit, activeProfile) {
        val w = weight.toDoubleOrNull() ?: 0.0
        val barWeight = activeProfile?.barWeightKg ?: settingsBarWeightKg
        if (w > barWeight && supportsPlateBreakdown(exercise)) getPlateText(w, barWeight, activeProfile, weightUnit = weightUnit) else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        if (!supersetGroup.isNullOrBlank()) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(supColor),
            )
            Spacer(Modifier.width(6.dp))
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDragging) c.accent.copy(alpha = 0.5f) else c.cardBorder),
            shape = RoundedCornerShape(IronLogRadius.lg.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.DragHandle,
                    contentDescription = "Drag",
                    tint = c.muted,
                    modifier = dragHandleModifier.size(20.dp).padding(end = 0.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showSupersetModal = true },
                        ),
                ) {
                    Text(
                        exercise.name,
                        color = c.text,
                        fontWeight = FontWeight(IronLogType.section.fontWeight),
                        fontSize = IronLogType.section.fontSize.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // GAP-23: show override badge or normal target
                    if (targetOverride != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${targetOverride.sets} × ${targetOverride.reps} · ${formatTrackingType(exercise.trackingType)}",
                                color = c.warning,
                                fontSize = IronLogType.meta.fontSize.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Box(
                                Modifier.clip(RoundedCornerShape(IronLogRadius.full.dp)).background(c.warning.copy(alpha = 0.15f)).padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("CUSTOM", color = c.warning, fontSize = (IronLogType.meta.fontSize - 2).sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            "${exercise.sets} × ${exercise.reps} · ${formatTrackingType(exercise.trackingType)}",
                            color = c.muted,
                            fontSize = IronLogType.meta.fontSize.sp,
                        )
                    }
                    if (!supersetGroup.isNullOrBlank()) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                .background(supColor.copy(alpha = 0.2f))
                                .border(1.dp, supColor, RoundedCornerShape(IronLogRadius.full.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "SUPERSET $supersetGroup",
                                color = supColor,
                                fontSize = IronLogType.micro.fontSize.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                            )
                        }
                    }
                }
                var showExerciseMenu by remember { mutableStateOf(false) }
                Box {
                    androidx.compose.material3.IconButton(onClick = { showExerciseMenu = true }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Menu", tint = c.muted)
                    }
                    androidx.compose.material3.DropdownMenu(showExerciseMenu, onDismissRequest = { showExerciseMenu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Swap Exercise", color = c.text) },
                            onClick = { showExerciseMenu = false; onSwapRequest() }
                        )
                        // GAP-23: Change target for this session
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Change target", color = c.text) },
                            onClick = {
                                showExerciseMenu = false
                                overrideSetsInput = targetOverride?.sets?.toString() ?: exercise.sets.toString()
                                overrideRepsInput = targetOverride?.reps?.toString() ?: exercise.reps.toString()
                                showTargetOverrideDialog = true
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Watch on YouTube", color = c.text) },
                            onClick = {
                                showExerciseMenu = false
                                val query = Uri.encode(exercise.name)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query+exercise+tutorial"))
                                context.startActivity(intent)
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Remove Exercise", color = c.danger) },
                            onClick = {
                                showExerciseMenu = false
                                onRemoveExercise?.invoke()
                                    ?: dispatch(WorkoutAction.RemoveExercise(exIndex, baseExercisesCount))
                            },
                        )
                    }
                }
            }
            loggedSets.forEachIndexed { setIndex, set ->
                SetRow(
                    set = set,
                    setIndex = setIndex,
                    exIndex = exIndex,
                    dispatch = { action ->
                        dispatch(action)
                        when (action) {
                            is WorkoutAction.SetRpe -> onSetRpeChanged(setIndex, action.rpe)
                            is WorkoutAction.SetRir -> onSetRirChanged(setIndex, action.rir)
                            is WorkoutAction.SetType -> onSetTypeChanged(setIndex, action.type)
                            is WorkoutAction.UpdateSet -> onSetValuesChanged(setIndex, action.weight, action.reps)
                            else -> Unit
                        }
                    },
                    effortTracking = effortTracking,
                    hapticFeedback = hapticFeedback,
                    weightUnit = weightUnit,
                    trackingType = exercise.trackingType,
                )
            }
            if (!exercise.trackingType.startsWith("duration")) {
                val hasWarmups = loggedSets.any { it.type == "warmup" }
                val topWorking = (weight.toDoubleOrNull()
                    ?: loggedSets.lastOrNull { it.type != "warmup" }?.weight
                    ?: 0.0)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clickable(enabled = !hasWarmups && topWorking > 40.0) {
                            val warmups = buildWarmupSets(topWorking, activeProfile?.barWeightKg ?: settingsBarWeightKg)
                            if (warmups.isNotEmpty()) {
                                dispatch(WorkoutAction.InsertWarmups(exIndex, warmups))
                                onInsertWarmups(warmups)
                            }
                        }
                        .padding(vertical = 2.dp),
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
                    Text(
                        if (hasWarmups) "Warmups inserted" else "+ Insert warmups",
                        color = if (hasWarmups || topWorking <= 40.0) c.faint else c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    weight,
                    { weight = it; dispatch(WorkoutAction.SetInput(exIndex, weight = it)) },
                    label = { Text(weightUnit.uppercase()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    reps,
                    { reps = it; dispatch(WorkoutAction.SetInput(exIndex, reps = it)) },
                    label = { Text(if (exercise.trackingType.startsWith("duration")) "SECONDS" else "REPS") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (hapticFeedback) HapticsEngine.mediumStrong(context)
                        onLogSet(weight, reps)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                ) { Text("LOG", color = c.textOnAccent, fontWeight = FontWeight(IronLogType.button.fontWeight)) }
            }
            if (plateText != null) {
                Text(
                    "PLATES: $plateText",
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                    modifier = Modifier.clickable { 
                        plateTarget = weight.toDoubleOrNull() ?: 0.0
                        showPlateModal = true 
                    }
                )
            }
            if (ghost != null && ghost.sets.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "LAST SESSION:",
                            color = c.muted,
                            fontSize = IronLogType.micro.fontSize.sp,
                            fontWeight = FontWeight(IronLogType.micro.fontWeight),
                            letterSpacing = IronLogType.micro.letterSpacing.sp,
                        )
                        Text(
                            ghost.sets.take(3).joinToString("  ") { s ->
                                if (s.weight > 0) "${s.weight.toInt()} × ${s.reps.toInt()}"
                                else "BW × ${s.reps.toInt()}"
                            },
                            color = c.muted,
                            fontSize = IronLogType.meta.fontSize.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "COPY",
                        color = c.accent,
                        fontSize = IronLogType.micro.fontSize.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.clickable { showCopyModal = true }.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                // Progression suggestion based on last session
                val suggestion = buildProgressionSuggestion(ghost, weightUnit)
                if (suggestion != null) {
                    Text(
                        "↑ $suggestion",
                        color = c.success.copy(alpha = 0.85f),
                        fontSize = IronLogType.micro.fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // Exercise note — tap to expand/add
            if (noteExpanded) {
                OutlinedTextField(
                    value = localNote,
                    onValueChange = { localNote = it; dispatch(WorkoutAction.SetExerciseNote(exIndex, it)) },
                    label = { Text("Exercise note") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { noteExpanded = true }.padding(top = 2.dp),
                ) {
                    Icon(Icons.Outlined.NoteAlt, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
                    Text(
                        if (localNote.isBlank()) "Add note" else localNote,
                        color = c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Rest timer display — tappable to override
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { onEditRest() }.padding(top = 2.dp),
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
                Text(
                    "${restSec}s rest · tap to edit",
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
            }
        }
        }
    }

    if (showPlateModal) {
        PlateModal(
            targetKg = plateTarget,
            activeProfile = activeProfile,
            settingsBarWeightKg = settingsBarWeightKg,
            weightUnit = weightUnit,
            onClose = { showPlateModal = false }
        )
    }
    if (showCopyModal && ghost != null && ghost.sets.isNotEmpty()) {
        CopyPreviousModal(
            ghostSets = ghost.sets,
            weightUnit = weightUnit,
            onCopy = { ghostSet ->
                weight = if (ghostSet.weight > 0) ghostSet.weight.roundToInt().toString() else ""
                reps = ghostSet.reps.roundToInt().toString()
                dispatch(WorkoutAction.SetInput(exIndex, weight = weight, reps = reps))
                showCopyModal = false
            },
            onDismiss = { showCopyModal = false },
        )
    }
    if (showSupersetModal) {
        SupersetModal(
            selected = supersetGroup,
            onSelect = {
                onSupersetChange(it)
                showSupersetModal = false
            },
            onDismiss = { showSupersetModal = false },
        )
    }

    // GAP-23: Change target dialog
    if (showTargetOverrideDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTargetOverrideDialog = false },
            title = { Text("Change Target", color = c.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Override sets × reps for this session only.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = overrideSetsInput,
                            onValueChange = { overrideSetsInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Sets", color = c.muted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.cardBorder, focusedTextColor = c.text, unfocusedTextColor = c.text),
                        )
                        OutlinedTextField(
                            value = overrideRepsInput,
                            onValueChange = { overrideRepsInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Reps", color = c.muted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.cardBorder, focusedTextColor = c.text, unfocusedTextColor = c.text),
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val s = overrideSetsInput.toIntOrNull()?.coerceIn(1, 20) ?: return@TextButton
                    val r = overrideRepsInput.toIntOrNull()?.coerceIn(1, 50) ?: return@TextButton
                    dispatch(WorkoutAction.OverrideTarget(exIndex, s, r))
                    showTargetOverrideDialog = false
                }) {
                    Text("APPLY", color = c.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTargetOverrideDialog = false }) {
                    Text("CANCEL", color = c.muted)
                }
            },
            containerColor = c.card,
        )
    }
}

private fun buildWarmupSets(targetWeight: Double, barWeightKg: Double): List<LoggedSet> {
    return TrainingIntelligenceEngine.generateWarmupSets(targetWeight, barWeightKg).map { (w, r) ->
        LoggedSet(
            weight = w,
            reps = r.toDouble(),
            type = "warmup",
            trackingType = "weight_reps",
        )
    }
}

private fun Double.roundToNearest(step: Double): Double = if (step <= 0.0) this else (round(this / step) * step).coerceAtLeast(step)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyPreviousModal(
    ghostSets: List<com.ironlog.app.ui.state.GhostSet>,
    weightUnit: String,
    onCopy: (com.ironlog.app.ui.state.GhostSet) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = useTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Copy Previous",
                color = c.text,
                fontWeight = FontWeight(IronLogType.title.fontWeight),
                fontSize = IronLogType.title.fontSize.sp,
            )
            Text(
                "Tap a set to fill in the weight and reps fields.",
                color = c.muted,
                fontSize = IronLogType.body.fontSize.sp,
            )
            ghostSets.forEachIndexed { idx, gs ->
                val wStr = if (gs.weight > 0) formatWeightFromKg(gs.weight, weightUnit) else "BW"
                val rStr = "${gs.reps.roundToInt()} reps"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.md.dp))
                        .background(c.surface)
                        .border(1.dp, c.faint, RoundedCornerShape(IronLogRadius.md.dp))
                        .clickable { onCopy(gs) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Set ${idx + 1}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    Text("$wStr  ×  $rStr", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = IronLogType.body.fontSize.sp)
                    Text("USE →", color = c.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupersetModal(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = useTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = listOf("" to "No superset", "A" to "Group A", "B" to "Group B", "C" to "Group C")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.card) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Superset", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.title.fontSize.sp)
            options.forEach { (value, label) ->
                val isActive = (selected ?: "") == value
                val color = when (value) {
                    "A" -> Color(0xFFFF7043)
                    "B" -> Color(0xFF42A5F5)
                    "C" -> Color(0xFF66BB6A)
                    else -> c.muted
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.md.dp))
                        .background(if (isActive) color.copy(alpha = 0.18f) else c.surface)
                        .border(1.dp, if (isActive) color else c.cardBorder, RoundedCornerShape(IronLogRadius.md.dp))
                        .clickable { onSelect(value.ifBlank { null }) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = if (isActive) color else c.text, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                    if (isActive) Text("✓", color = color, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RestTimerPanel(
    restTimer: com.ironlog.app.ui.state.RestTimerState,
    modifier: Modifier,
    dispatch: (WorkoutAction) -> Unit,
    onRestCleared: () -> Unit,
    hapticFeedback: Boolean,
) {
    val c = useTheme()
    val context = LocalContext.current
    if (!restTimer.active || restTimer.endTime == null) return
    var remaining by remember(restTimer.endTime, restTimer.paused) { mutableStateOf(((restTimer.endTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()) }
    var lastHapticSecond by remember(restTimer.endTime) { mutableStateOf(Int.MIN_VALUE) }
    LaunchedEffect(restTimer.endTime, restTimer.paused) {
        while (restTimer.active && !restTimer.paused) {
            remaining = ((restTimer.endTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()
            if (hapticFeedback && remaining != lastHapticSecond) {
                // Final 5-second countdown (existing behaviour) — increasingly intense.
                when (remaining) {
                    5 -> { HapticsEngine.strong(context);       lastHapticSecond = remaining }
                    4 -> { HapticsEngine.mediumStrong(context); lastHapticSecond = remaining }
                    3 -> { HapticsEngine.medium(context);       lastHapticSecond = remaining }
                    2 -> { HapticsEngine.low(context);          lastHapticSecond = remaining }
                    1 -> { HapticsEngine.strong(context);       lastHapticSecond = remaining }
                    else -> {
                        // 30-second milestone pulse — fires at 30s, 60s, 90s, ... remaining.
                        // Skip the very start (remaining == total) so it doesn't fire instantly.
                        if (remaining in 6..(restTimer.total - 1) && remaining % 30 == 0) {
                            HapticsEngine.medium(context)
                            lastHapticSecond = remaining
                        }
                    }
                }
            }
            if (remaining <= 0) { dispatch(WorkoutAction.SkipRest); onRestCleared(); break }
            delay(500)
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(IronLogRadius.lg.dp))
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "REST",
                color = c.muted,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                fontSize = IronLogType.eyebrow.fontSize.sp,
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
            RollingTimerText(
                value = formatDurationShort(remaining),
                color = c.text,
                fontWeight = FontWeight(IronLogType.display.fontWeight),
                fontSizeSp = IronLogType.title.fontSize,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "+30s",
                color = c.text,
                modifier = Modifier.clickable {
                    if (hapticFeedback) HapticsEngine.selection(context)
                    dispatch(WorkoutAction.Add30s)
                }.padding(8.dp),
            )
            Text(
                if (restTimer.paused) "RESUME" else "PAUSE",
                color = c.text,
                modifier = Modifier.clickable {
                    if (hapticFeedback) HapticsEngine.lightConfirm(context)
                    if (restTimer.paused) {
                        val pausedAt = restTimer.pausedAt ?: System.currentTimeMillis()
                        val pausedMs = System.currentTimeMillis() - pausedAt
                        dispatch(WorkoutAction.ResumeRest((restTimer.endTime ?: System.currentTimeMillis()) + pausedMs))
                    } else dispatch(WorkoutAction.PauseRest(System.currentTimeMillis()))
                }.padding(8.dp),
            )
            Text(
                "SKIP",
                color = c.accent,
                fontWeight = FontWeight(IronLogType.button.fontWeight),
                modifier = Modifier.clickable {
                    if (hapticFeedback) HapticsEngine.mediumStrong(context)
                    dispatch(WorkoutAction.SkipRest); onRestCleared()
                }.padding(8.dp),
            )
        }
    }
}

fun toTitleCase(value: String?): String = value.orEmpty().replace(Regex("[_-]+"), " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { it.lowercase().replaceFirstChar { ch -> ch.titlecase() } }
fun parseRepTarget(value: Any?, fallback: Int = 8): Int = when (value) { is Number -> value.toInt(); else -> Regex("\\d+").find(value?.toString().orEmpty())?.value?.toIntOrNull() ?: fallback }
fun normalizeExerciseLookupKey(value: String?): String = value.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
fun isTimeBasedExercise(trackingType: String?): Boolean = trackingType.orEmpty().startsWith("duration")

private fun formatTrackingType(trackingType: String): String = when (trackingType.lowercase().trim()) {
    "weight_reps"          -> "Weight × Reps"
    "bodyweight_reps"      -> "Bodyweight"
    "bodyweight_plus_weight_reps",
    "weighted_bodyweight"  -> "Bodyweight + Weight"
    "assisted_bodyweight"  -> "Assisted Bodyweight"
    "reps_only"            -> "Reps Only"
    "duration"             -> "Duration"
    "duration_weight"      -> "Duration + Weight"
    "cardio"               -> "Cardio"
    else                   -> trackingType.replace("_", " ").split(" ")
                                 .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
}
fun isBodyweightExercise(exercise: LegacyExerciseShape): Boolean = exercise.isBodyweight || exercise.equipment.lowercase().contains("bodyweight") || Regex("pull.?up|chin.?up|push.?up|\\bdip\\b|plank|crunch|sit.?up|leg raise|mountain climber|muscle.?up|handstand|pistol|nordic", RegexOption.IGNORE_CASE).containsMatchIn(exercise.name)
fun supportsPlateBreakdown(exercise: NormalizedSessionExercise): Boolean = exercise.equipment.equals("Barbell", ignoreCase = true) || exercise.name.contains("barbell", ignoreCase = true)
fun getFunComparison(totalKg: Double): FunComparison = FUN_COMPARISONS.lastOrNull { totalKg >= it.threshold } ?: FUN_COMPARISONS.first()
fun getPlateText(targetKg: Double, barWeight: Double, profile: GymProfileDto?, weightUnit: String = "kg"): String {
    val inventory = profile?.plates?.takeIf { it.isNotEmpty() } ?: DEFAULT_PLATES
    val result = calculatePlates(targetKg, barWeight, inventory)
    if (result.platesPerSide.isEmpty()) return "Bar only"
    return result.platesPerSide.flatMap { p -> List(p.quantity) { p.weightKg } }
        .joinToString(" + ") { formatWeightFromKg(it, weightUnit) } + " each side"
}

private fun isHeavyCompoundExercise(name: String): Boolean {
    val n = name.lowercase()
    return listOf(
        "squat",
        "deadlift",
        "bench press",
        "overhead press",
        "romanian deadlift",
        "barbell row",
        "hip thrust",
        "weighted pull",
        "leg press",
    ).any { n.contains(it) }
}

@Composable
private fun RollingTimerText(
    value: String,
    color: Color,
    fontWeight: FontWeight,
    fontSizeSp: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
        value.forEachIndexed { idx, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    slideInVertically { full -> full } togetherWith slideOutVertically { full -> -full }
                },
                label = "timer_digit_$idx",
            ) { animated ->
                Text(
                    animated.toString(),
                    color = color,
                    fontWeight = fontWeight,
                    fontSize = fontSizeSp.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateModal(
    targetKg: Double,
    activeProfile: GymProfileDto?,
    settingsBarWeightKg: Double,
    weightUnit: String,
    onClose: () -> Unit
) {
    val c = useTheme()
    val barWeight = activeProfile?.barWeightKg ?: settingsBarWeightKg
    val inventory = activeProfile?.plates?.takeIf { it.isNotEmpty() } ?: DEFAULT_PLATES
    val result = remember(targetKg, barWeight, inventory) { calculatePlates(targetKg, barWeight, inventory) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = c.bg,
        dragHandle = null
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // FIXED: 1
            Text("PLATE CALCULATOR", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            Text(
                "Load ${formatWeightFromKg(targetKg, weightUnit)}",
                color = c.text,
                fontSize = IronLogType.title.fontSize.sp,
                fontWeight = FontWeight.Black
            )

            if (!result.isValid) {
                Text(
                    "Cannot exactly load ${formatWeightFromKg(targetKg, weightUnit)} with available plates.",
                    color = c.danger,
                    fontSize = IronLogType.body.fontSize.sp
                )
            }

            // GAP-11: Visual barbell diagram
            if (result.platesPerSide.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("BARBELL VIEW", color = c.muted, fontSize = IronLogType.micro.fontSize.sp, letterSpacing = 2.sp)
                        BarbellDiagram(
                            platesPerSide = result.platesPerSide,   // List<PlateDto>
                            inventory = inventory,
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("BAR (${formatWeightFromKg(barWeight, weightUnit)})", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = IronLogType.eyebrow.letterSpacing.sp)

                    if (result.platesPerSide.isEmpty()) {
                        Text("Bar only", color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold)
                    } else {
                        result.platesPerSide.forEach { p ->
                            val assignedHex = inventory.firstOrNull { kotlin.math.abs(it.weightKg - p.weightKg) < 0.001 }?.color.orEmpty()
                            val defaultPlateColor = when {
                                p.weightKg >= 20 -> Color(0xFFD32F2F)
                                p.weightKg >= 15 -> Color(0xFF1976D2)
                                p.weightKg >= 10 -> Color(0xFFFFB300)
                                p.weightKg >= 5 -> Color(0xFF43A047)
                                else -> Color(0xFF8E24AA)
                            }
                            val plateColor = if (assignedHex.isNotBlank()) {
                                try { Color(android.graphics.Color.parseColor(assignedHex)) } catch (_: Exception) { defaultPlateColor }
                            } else defaultPlateColor
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${formatWeightFromKg(p.weightKg, weightUnit)} plate",
                                        color = c.text,
                                        fontSize = IronLogType.section.fontSize.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("x${p.quantity} per side", color = c.accent, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    Modifier
                                        .width((8 + p.weightKg * 3).dp)
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(plateColor.copy(alpha = 0.85f))
                                )
                                Text(
                                    "per side visual",
                                    color = c.muted,
                                    fontSize = IronLogType.micro.fontSize.sp,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent)
            ) {
                Text("DONE", color = c.bg, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    burstId: Int,
    accent: Color,
) {
    val colors: List<Int> = remember(accent) {
        listOf(
            accent.toArgb(),
            Color(0xFFFFC857).toArgb(),
            Color(0xFF3DDC97).toArgb(),
            Color(0xFF64B5F6).toArgb(),
            Color(0xFFFF8A65).toArgb(),
            Color(0xFFE1BEE7).toArgb(),
            Color.White.toArgb(),
        )
    }
    val parties = remember(burstId, colors) {
        val sizes = listOf(
            Size(sizeInDp = 5, mass = 1.6f, massVariance = 0.35f),
            Size(sizeInDp = 7, mass = 2.3f, massVariance = 0.4f),
            Size(sizeInDp = 10, mass = 3.0f, massVariance = 0.45f),
        )
        val shapes = listOf(
            Shape.Circle,
            Shape.Square,
            Shape.Rectangle(0.48f),
        )
        val rotation = Rotation(
            enabled = true,
            speed = 9.5f,
            variance = 0.75f,
            multiplier2D = 1.0f,
            multiplier3D = 0.55f,
        )
        listOf(
            Party(
                speed = 12f,
                maxSpeed = 26f,
                damping = 0.88f,
                angle = 270,
                spread = 54,
                size = sizes,
                colors = colors,
                shapes = shapes,
                emitter = Emitter(duration = 260.milliseconds).max(90),
                position = Position.Relative(0.5, 0.72),
                timeToLive = 2600L,
                fadeOutEnabled = true,
                rotation = rotation,
            ),
            Party(
                speed = 13f,
                maxSpeed = 29f,
                damping = 0.87f,
                angle = 315,
                spread = 42,
                size = sizes,
                colors = colors,
                shapes = shapes,
                emitter = Emitter(duration = 220.milliseconds).max(55),
                position = Position.Relative(0.10, 0.84),
                timeToLive = 2500L,
                fadeOutEnabled = true,
                rotation = rotation,
            ),
            Party(
                speed = 13f,
                maxSpeed = 29f,
                damping = 0.87f,
                angle = 225,
                spread = 42,
                size = sizes,
                colors = colors,
                shapes = shapes,
                emitter = Emitter(duration = 220.milliseconds).max(55),
                position = Position.Relative(0.90, 0.84),
                timeToLive = 2500L,
                fadeOutEnabled = true,
                rotation = rotation,
            ),
        )
    }

    ConfettiKit(
        modifier = modifier,
        parties = parties,
    )
}

private fun normalizeSessionExercise(planEx: UiPlanExercise, lib: LegacyExerciseShape?): NormalizedSessionExercise {
    val trackingType = lib?.trackingType ?: "weight_reps"
    return NormalizedSessionExercise(
        name = lib?.name ?: planEx.name.ifBlank { "Custom Exercise" },
        exerciseId = planEx.exerciseId.ifBlank { lib?.id ?: planEx.name },
        sets = planEx.sets.takeIf { it > 0 } ?: 3,
        reps = parseRepTarget(planEx.reps, if (trackingType.startsWith("duration")) 60 else 8),
        trackingType = trackingType,
        isWarmup = planEx.isWarmup,
        equipment = lib?.equipment,
    )
}

@Serializable
private data class WorkoutDraftDto(
    val inputs: Map<String, WorkoutInputDto> = emptyMap(),
    val setLog: Map<String, List<LoggedSetDto>> = emptyMap(),
    val exerciseNotes: Map<String, String> = emptyMap(),
    val supersetGroups: Map<String, String?> = emptyMap(),
    val restTimer: RestTimerDto = RestTimerDto(),
    val addedExercises: List<AddedExerciseDto> = emptyList(),
    val removedBaseExerciseIndices: List<Int> = emptyList(),
    val orderedIndices: List<Int> = emptyList(),
    /** Persists mid-workout exercise swaps so they survive minimize/resume. Key = exIndex (String). */
    val swappedExercises: Map<String, SwappedExerciseDto> = emptyMap(),
)

/** Minimal snapshot of a swapped exercise — enough to rebuild the UI overlay after resume. */
@Serializable
private data class SwappedExerciseDto(
    val id: String = "",
    val name: String = "",
    val trackingType: String = "weight_reps",
    val equipment: String = "",
)

@Serializable
private data class WorkoutInputDto(val weight: String = "", val reps: String = "")

@Serializable
private data class LoggedSetDto(
    val id: String = "",
    val weight: Double = 0.0,
    val reps: Double = 0.0,
    val type: String = "normal",
    val rpe: Double? = null,
    val rir: Int? = null,
    val note: String? = null,
    val orm: Double = 0.0,
    val trackingType: String = "weight_reps",
    val durationSec: Double? = null,
)

@Serializable
private data class RestTimerDto(
    val active: Boolean = false,
    val endTime: Long? = null,
    val total: Int = 0,
    val paused: Boolean = false,
    val pausedAt: Long? = null,
    val triggerExIndex: Int? = null,
)

/**
 * Builds a short progression suggestion string based on the last session's ghost data.
 * Returns null if there's not enough data to make a suggestion.
 *
 * Logic:
 *  - If ghost sets have weight > 0: suggest the same reps at +2.5kg (or +5lb).
 *  - If bodyweight only: suggest +2 reps on the top set.
 */
private fun buildProgressionSuggestion(
    ghost: com.ironlog.app.ui.state.GhostData,
    weightUnit: String,
): String? {
    val sets = ghost.sets.ifEmpty { return null }
    val isLb = weightUnit.lowercase().trimEnd('s') == "lb"
    val increment = if (isLb) 5.0 else 2.5
    val topSet = sets.maxByOrNull { it.weight }
    return if (topSet != null && topSet.weight > 0) {
        val suggested = topSet.weight + increment
        val repStr = if (topSet.reps > 0) " × ${topSet.reps.toInt()}" else ""
        java.lang.String.format(java.util.Locale.US, "%.1f $weightUnit$repStr", suggested)
    } else {
        // Bodyweight: suggest +2 reps on top set
        val topReps = sets.maxOfOrNull { it.reps }?.toInt() ?: return null
        "BW × ${topReps + 2} reps"
    }
}

// ── GAP-11: Barbell diagram ───────────────────────────────────────────────────

/** International-standard plate fill color, overridden by profile hex if set. */
private fun plateFillColor(weightKg: Double, hexOverride: String): Color {
    if (hexOverride.isNotBlank()) {
        runCatching { return Color(android.graphics.Color.parseColor(hexOverride)) }
    }
    return when {
        weightKg >= 25.0 -> Color(0xFFD32F2F)   // red
        weightKg >= 20.0 -> Color(0xFF1565C0)   // blue
        weightKg >= 15.0 -> Color(0xFFF9A825)   // yellow
        weightKg >= 10.0 -> Color(0xFF2E7D32)   // green
        weightKg >= 5.0  -> Color(0xFFF5F5F5)   // white / light
        weightKg >= 2.5  -> Color(0xFF212121)   // black
        else             -> Color(0xFFBDBDBD)   // chrome
    }
}

/**
 * Canvas barbell diagram: bar + sleeve + plates stacked from center outward on both sides.
 * Heaviest plates are closest to the sleeve; lighter plates at the ends.
 * Plate height scales with weight so heavier plates are visually taller.
 */
@Composable
private fun BarbellDiagram(
    platesPerSide: List<PlateDto>,  // heaviest first (from calculatePlates / PlateCalculationResult)
    inventory: List<PlateDto>,
    modifier: Modifier = Modifier,
) {
    val c = useTheme()
    val barColor = c.subtext.copy(alpha = 0.7f)
    val sleeveColor = c.subtext

    // Flatten plate list: each PlateDto(wkg, qty) → qty individual plates
    val flatPlates: List<Pair<Double, Color>> = platesPerSide.flatMap { pd ->
        val hex = inventory.firstOrNull { kotlin.math.abs(it.weightKg - pd.weightKg) < 0.001 }?.color.orEmpty()
        val col = plateFillColor(pd.weightKg, hex)
        List(pd.quantity) { pd.weightKg to col }
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val barH        = 8f
        val sleeveW     = 72f
        val sleeveH     = 24f
        val collarW     = 12f
        val collarH     = 30f
        val plateW      = 13f

        // Bar (full width)
        drawRect(barColor, topLeft = Offset(0f, cy - barH / 2f), size = androidx.compose.ui.geometry.Size(size.width, barH))

        // Sleeve (center, thicker)
        drawRect(sleeveColor, topLeft = Offset(cx - sleeveW / 2f, cy - sleeveH / 2f), size = androidx.compose.ui.geometry.Size(sleeveW, sleeveH))

        // Plates — right side (stack outward from sleeve)
        var rightX = cx + sleeveW / 2f + collarW
        flatPlates.forEach { (wkg, col) ->
            val pH = (wkg.toFloat() * 2.4f).coerceIn(18f, 62f)
            drawRect(col, topLeft = Offset(rightX, cy - pH / 2f), size = androidx.compose.ui.geometry.Size(plateW - 1f, pH))
            drawRect(col.copy(alpha = 0.35f), topLeft = Offset(rightX, cy - pH / 2f), size = androidx.compose.ui.geometry.Size(plateW - 1f, pH), style = Stroke(1f))
            rightX += plateW
        }

        // Plates — left side (mirror; heaviest still closest to sleeve)
        var leftX = cx - sleeveW / 2f - collarW
        flatPlates.forEach { (wkg, col) ->
            val pH = (wkg.toFloat() * 2.4f).coerceIn(18f, 62f)
            leftX -= plateW
            drawRect(col, topLeft = Offset(leftX + 1f, cy - pH / 2f), size = androidx.compose.ui.geometry.Size(plateW - 1f, pH))
        }

        // Collars (drawn on top of plates so they're always visible)
        val collarColor = barColor.copy(alpha = 0.9f)
        drawRect(collarColor, topLeft = Offset(cx + sleeveW / 2f, cy - collarH / 2f), size = androidx.compose.ui.geometry.Size(collarW, collarH))
        drawRect(collarColor, topLeft = Offset(cx - sleeveW / 2f - collarW, cy - collarH / 2f), size = androidx.compose.ui.geometry.Size(collarW, collarH))
    }
}

@Serializable
private data class AddedExerciseDto(
    val exerciseId: String = "",
    val name: String = "",
    val trackingType: String = "weight_reps",
    val equipment: String? = null,
    val sets: Int = 3,
    val reps: Int = 8,
)

@Composable
private fun ActiveWorkoutRollingTimerText(vm: ActiveWorkoutViewModel, c: IronLogThemeTokens) {
    val elapsedSeconds by vm.elapsedSeconds.collectAsState()
    val timerStarted by vm.timerStarted.collectAsState()
    RollingTimerText(
        value = if (timerStarted) formatDurationShort(elapsedSeconds) else "--:--",
        color = c.accent,
        fontWeight = FontWeight.Bold,
        fontSizeSp = IronLogType.body.fontSize,
    )
}
