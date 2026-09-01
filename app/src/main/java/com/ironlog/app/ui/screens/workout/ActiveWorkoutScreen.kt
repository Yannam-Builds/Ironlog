package com.ironlog.app.ui.screens.workout

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.SystemClock
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import com.ironlog.app.ui.theme.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.model.CreateExerciseInput
import com.ironlog.app.data.model.SetInput
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.ProgressionPolicySnapshot
import com.ironlog.app.data.repository.ProgressionPolicyStore
import com.ironlog.app.data.repository.observePlanExerciseNotesVisible
import com.ironlog.app.data.repository.setPlanExerciseNotesVisible
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.LastExerciseSession
import com.ironlog.app.data.repository.historicalPrBaselines
import com.ironlog.app.data.repository.PR_RESET_AT_KEY
import com.ironlog.app.domain.intelligence.CloudAiEngine
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import com.ironlog.app.domain.intelligence.TrainingIntelligenceEngine
import com.ironlog.app.domain.intelligence.ResolvedProgressionPolicy
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.valentinilk.shimmer.shimmer
import com.ironlog.app.services.WorkoutForegroundService
import com.ironlog.app.services.WorkoutNotificationBridge
import com.ironlog.app.services.WorkoutClockSnapshot
import com.ironlog.app.services.WorkoutTimerClock
import com.ironlog.app.services.WorkoutTimerSettingKeys
import com.ironlog.app.services.ShareService
import com.ironlog.app.ui.components.IronLogDropdownMenu
import com.ironlog.app.ui.components.SetRow
import com.ironlog.app.ui.components.NextSessionNoteControl
import com.ironlog.app.ui.components.ExerciseNotesSettingsDialog
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.model.UiPlanExercise
import timber.log.Timber
import com.ironlog.app.ui.state.AddedExerciseEntry
import com.ironlog.app.ui.state.LoggedSet
import com.ironlog.app.ui.state.GhostLoadTarget
import com.ironlog.app.ui.state.WorkoutGhostLoader
import com.ironlog.app.ui.state.PendingWarmup
import com.ironlog.app.ui.state.WorkoutAction
import com.ironlog.app.ui.state.WorkoutState
import com.ironlog.app.ui.state.workoutReducer
import com.ironlog.app.ui.state.WorkoutMutationCoordinator
import com.ironlog.app.ui.state.WorkoutDraftWriteGate
import com.ironlog.app.ui.state.afterWorkoutCommit
import com.ironlog.app.ui.state.commitWorkoutTerminalMutation
import com.ironlog.app.ui.state.canonicalSetLoad
import com.ironlog.app.ui.state.canonicalSetType
import com.ironlog.app.ui.state.loadOrCreateWorkout
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.viewmodel.PlansViewModel
import com.ironlog.app.util.formatDurationShort
import com.ironlog.app.util.formatWeightFromKg
import com.ironlog.app.util.convertUnitToKg
import com.ironlog.app.util.HapticsEngine
import com.ironlog.app.util.calculatePlates
import com.ironlog.app.ui.screens.settings.GymProfileDto
import com.ironlog.app.ui.screens.settings.DEFAULT_PLATES
import com.ironlog.app.ui.screens.settings.PlateDto
import com.ironlog.app.ui.screens.stats.estimateOneRM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val isBodyweight: Boolean = false,
)

private data class PendingExerciseSwap(
    val exIndex: Int,
    val from: NormalizedSessionExercise,
    val to: LegacyExerciseShape,
)

private fun LastExerciseSession.toGhostData() = com.ironlog.app.ui.state.GhostData(
    date = date,
    previousNote = notes,
    sets = sets.map { set ->
        com.ironlog.app.ui.state.GhostSet(
            weight = set.weight,
            reps = set.reps,
            rpe = set.rpe,
            rir = set.rir,
            type = when {
                set.isWarmup -> "warmup"
                set.toFailure -> "failure"
                set.isDropset -> "dropset"
                set.isAmrap -> "amrap"
                else -> "normal"
            },
        )
    },
)

internal fun shouldReconcileWorkoutForegroundService(
    currentWorkoutId: String?,
    timerStarted: Boolean,
    currentStartMs: Long?,
    durableWorkoutId: String?,
    durableStartMs: Long?,
): Boolean =
    timerStarted &&
        !currentWorkoutId.isNullOrBlank() &&
        currentWorkoutId == durableWorkoutId &&
        currentStartMs != null &&
        currentStartMs > 0L &&
        currentStartMs == durableStartMs

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
    private var timerStartElapsedMs: Long? = null
    private var timerStartBootCount: Int = -1
    private val processBootCount: Int = WorkoutTimerClock.now(application).bootCount
    // UI rows can repeat the same exercise id, so persistence must bind by row index.
    private var workoutExerciseIdByIndex: Map<Int, String> = emptyMap()
    private var exerciseIdByIndex: Map<Int, String> = emptyMap()
    private val ghostLoader = WorkoutGhostLoader(
        scope = viewModelScope,
        loadOne = { exerciseId -> workoutRepo.getLastExerciseSession(exerciseId)?.toGhostData() },
        isCurrent = { targets ->
            targets.withIndex().all { (index, target) ->
                workoutExerciseIdByIndex[index] == target.rowUid &&
                    exerciseIdByIndex[index] == target.exerciseId
            }
        },
        publish = { dispatch(WorkoutAction.LoadGhost(it)) },
        onFailure = { Timber.w(it, "Previous-session data could not load") },
    )
    // Historical best 1RM per exerciseId
    private var historicalBest1rm: MutableMap<String, Double> = mutableMapOf()
    private var sessionBest1rm: MutableMap<String, Double> = mutableMapOf()
    private var hadPrThisSession: Boolean = false
    /** Sets, rest controls, finish, and discard share one ordering boundary. */
    private val mutationMutex = Mutex()
    private val mutations = WorkoutMutationCoordinator(viewModelScope, mutationMutex)
    private val draftWrites = WorkoutDraftWriteGate()
    val initialization = mutations.initialization
    private val _mutationError = MutableStateFlow<String?>(null)
    val mutationError: StateFlow<String?> = _mutationError.asStateFlow()
    private val _restControlPending = MutableStateFlow(false)
    val restControlPending: StateFlow<Boolean> = _restControlPending.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }
    // Ordered indices from the composable — persisted in draft so minimize/resume restores order.
    private var persistedOrderedIndices: List<Int> = emptyList()
    private val _restoredOrderedIndices = MutableStateFlow<List<Int>>(emptyList())
    val restoredOrderedIndices: StateFlow<List<Int>> = _restoredOrderedIndices.asStateFlow()

    fun updateOrderedIndices(indices: List<Int>) {
        val rowIds = indices.mapNotNull(workoutExerciseIdByIndex::get)
        if (rowIds.size == indices.size && rowIds.isNotEmpty()) {
            launchMutation {
                mutations.commit<List<Int>>(
                    write = {
                        val currentIndices = rowIds.map { uid -> currentExerciseIndex(-1, uid) }
                        workoutRepo.persistExerciseOrder(rowIds)
                        currentIndices
                    },
                    publish = { committed ->
                        persistedOrderedIndices = committed
                        persistDraft(_workoutState.value)
                    },
                )
            }
        }
    }

    init {
        // Load weight unit so the PR banner displays in the user's chosen unit.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val raw = settingsRepo.getString("ironlog_settings")
            val json = runCatching { org.json.JSONObject(raw ?: "{}") }.getOrDefault(org.json.JSONObject())
            vmWeightUnit = json.optString("weightUnit", "kg").ifBlank { "kg" }
        }
        // A reset can happen while this workout is minimized. Rebase immediately so resuming the
        // same ViewModel cannot keep comparing new sets against records the user cleared.
        viewModelScope.launch {
            settingsRepo.observeStrings(setOf(PR_RESET_AT_KEY)).collect {
                historicalBest1rm = withContext(Dispatchers.IO) { loadHistoricalPrBaselines() }
                hadPrThisSession = recomputeHadPrFromRetainedSets()
            }
        }
        // Tick elapsed timer from a stable epoch so minimize/background/screen-off
        // never drifts or resets the counter.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val start = timerStartEpochMs
                if (_timerStarted.value && start != null) {
                    _elapsedSeconds.value = (WorkoutTimerClock.elapsedSinceStartMs(
                        startWallMs = start,
                        startElapsedMs = timerStartElapsedMs ?: 0L,
                        startBootCount = timerStartBootCount,
                        now = currentClockSnapshot(),
                    ) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
            }
        }
    }

    /**
     * Called when the very first set of a session is logged.
     * Idempotent — safe to call multiple times (noop after first call).
     */
    private suspend fun startTimerOnFirstSet() {
        if (_timerStarted.value) return
        val workoutId = activeWorkoutId ?: return
        val now = currentClockSnapshot()
        val nowMs = now.wallTimeMs
        val persisted = settingsRepo.setLongSettingsIfStringMatches(
            matchKey = "active_workout_id",
            expectedValue = workoutId,
            values = mapOf(
                WorkoutTimerSettingKeys.START_WALL_MS to nowMs,
                WorkoutTimerSettingKeys.START_ELAPSED_MS to now.elapsedRealtimeMs,
                WorkoutTimerSettingKeys.START_BOOT_COUNT to now.bootCount.toLong(),
            ),
        )
        if (!persisted) return
        // The start belongs to the same serialized mutation as the first set. Re-check the
        // durable identity so a terminal mutation can never be followed by a delayed restart.
        if (activeWorkoutId != workoutId || settingsRepo.getActiveWorkoutId() != workoutId) {
            return
        }
        timerStartEpochMs = nowMs
        timerStartElapsedMs = now.elapsedRealtimeMs
        timerStartBootCount = now.bootCount
        _elapsedSeconds.value = 0
        _timerStarted.value = true
        requestForegroundService(workoutId, nowMs)
    }

    private fun syncForegroundNotification() {
        val workoutId = activeWorkoutId ?: return
        val startMs = timerStartEpochMs ?: return
        requestForegroundService(workoutId, startMs)
    }

    private fun requestForegroundService(workoutId: String, startMs: Long): Boolean {
        val started = WorkoutForegroundService.start(
            getApplication(),
            workoutId,
            activeWorkoutName ?: "Workout",
            startMs,
        )
        if (!started) {
            _mutationError.value =
                "Workout saved, but Android blocked the status notification. Re-open IronLog to retry."
        }
        return started
    }

    fun reconcileForegroundNotificationOnResume() {
        if (!_timerStarted.value) return
        viewModelScope.launch(Dispatchers.IO) {
            mutationMutex.withLock {
                val workoutId = activeWorkoutId?.takeIf { it.isNotBlank() } ?: return@withLock
                val startMs = timerStartEpochMs?.takeIf { it > 0L } ?: return@withLock
                val durableWorkoutId = settingsRepo.getActiveWorkoutId()
                val durableStartMs = settingsRepo.getString(WorkoutTimerSettingKeys.START_WALL_MS)
                    ?.toLongOrNull()
                if (!shouldReconcileWorkoutForegroundService(
                        currentWorkoutId = workoutId,
                        timerStarted = _timerStarted.value,
                        currentStartMs = startMs,
                        durableWorkoutId = durableWorkoutId,
                        durableStartMs = durableStartMs,
                    )
                ) {
                    return@withLock
                }
                requestForegroundService(workoutId, startMs)
            }
        }
    }

    /** The name shown in the foreground-service notification. Derived lazily once [initWorkout] runs. */
    private var activeWorkoutName: String? = null

    fun initWorkout(dayId: String, startEmpty: Boolean = false) {
        mutations.initialize {
            historicalBest1rm = withContext(Dispatchers.IO) { loadHistoricalPrBaselines() }
            sessionBest1rm.clear()
            val loaded = loadOrCreateWorkout(
                readActiveId = { activeWorkoutId ?: settingsRepo.getActiveWorkoutId() },
                resume = { id -> workoutRepo.getWorkoutDetailSnapshot(id).also {
                    check(it.workout.status == "active") { "The saved workout has already ended." }
                } },
                create = {
                    val workout = if (startEmpty) {
                        workoutRepo.startEmptyWorkout("Open Workout")
                    } else {
                        check(dayId.isNotBlank()) { "No active workout was found. Return to Plans to start one." }
                        workoutRepo.startWorkoutFromPlanDay(dayId)
                    }
                    // Preserve the committed identity even if a subsequent read fails; Retry resumes it.
                    activeWorkoutId = workout.uid
                    workoutRepo.getWorkoutDetailSnapshot(workout.uid)
                },
                restore = { detail ->
                    activeWorkoutId = detail.workout.uid
                    activeWorkoutName = detail.workout.name.ifBlank { "Workout in progress" }
                    bindWorkoutExerciseRows(detail.exercises)
                    val persistedStart = settingsRepo.getString(WorkoutTimerSettingKeys.START_WALL_MS)?.toLongOrNull()
                    val persistedStartElapsed = settingsRepo.getString(WorkoutTimerSettingKeys.START_ELAPSED_MS)?.toLongOrNull()
                    val persistedStartBoot = settingsRepo.getString(WorkoutTimerSettingKeys.START_BOOT_COUNT)?.toIntOrNull() ?: -1
                    if (persistedStart != null && persistedStart > 0L) {
                        timerStartEpochMs = persistedStart
                        timerStartElapsedMs = persistedStartElapsed
                        timerStartBootCount = persistedStartBoot
                        _timerStarted.value = true
                        _elapsedSeconds.value = (WorkoutTimerClock.elapsedSinceStartMs(
                            startWallMs = persistedStart,
                            startElapsedMs = persistedStartElapsed ?: 0L,
                            startBootCount = persistedStartBoot,
                            now = currentClockSnapshot(),
                        ) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    }
                    if (persistedStart != null && persistedStart > 0L) {
                        requestForegroundService(detail.workout.uid, persistedStart)
                    }
                    restoreDraftIfAny()
                },
            )
            _activeWorkoutIdSignal.value = loaded.workout.uid
        }
    }

    private fun currentClockSnapshot(): WorkoutClockSnapshot = WorkoutClockSnapshot(
        wallTimeMs = System.currentTimeMillis(),
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        bootCount = processBootCount,
    )

    /** Replaces ghost data from one stable row/exercise generation. */
    fun loadGhostData(exerciseIds: List<String>) {
        val targets = exerciseIds.mapIndexed { index, exerciseId ->
            GhostLoadTarget(workoutExerciseIdByIndex[index].orEmpty(), exerciseId)
        }
        ghostLoader.load(targets)
    }

    fun dispatch(action: WorkoutAction) {
        if (action is WorkoutAction.RemoveExercise) {
            removeExercise(action)
            return
        }
        if (action is WorkoutAction.Add30s || action is WorkoutAction.SkipRest ||
            action is WorkoutAction.PauseRest || action is WorkoutAction.ResumeRest
        ) {
            applyRestControl(action)
            return
        }
        publishAction(action)
    }

    private fun publishAction(action: WorkoutAction) {
        val next = workoutReducer(_workoutState.value, action)
        _workoutState.value = next
        // Persist immediately so minimize/background cannot drop the latest typed input.
        if (activeWorkoutId != null) persistDraft(next)
    }

    private fun applyRestControl(action: WorkoutAction) {
        val workoutId = activeWorkoutId ?: return
        if (_restControlPending.value) return
        _restControlPending.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mutationMutex.withLock {
                    val appliedAction: WorkoutAction? = when (action) {
                    WorkoutAction.Add30s -> {
                        val result = com.ironlog.app.services.WorkoutNotificationActionInbox
                            .commitRestControl(
                                sessionId = workoutId,
                                actionId = com.ironlog.app.services.NotificationActionRouter.Actions.ADD_30S,
                                now = com.ironlog.app.services.WorkoutTimerClock.now(getApplication()),
                                enqueueUiSync = false,
                            )
                        if (result.applied) {
                            runCatching {
                                WorkoutNotificationBridge.clearRestTimer(getApplication())
                                WorkoutForegroundService.onExternalRestStateChanged(getApplication(), workoutId)
                            }.onFailure { Timber.w(it, "Rest deadline committed; notification refresh will retry") }
                            if (result.pausedRemainingMs > 0L) {
                                WorkoutAction.SyncPausedRest(
                                    remainingMs = result.pausedRemainingMs,
                                    addedSeconds = 30,
                                )
                            } else {
                                WorkoutAction.SyncRestDeadline(
                                    endTime = result.wallEndMs,
                                    endElapsedTime = result.elapsedEndMs,
                                    bootCount = result.bootCount,
                                    addedSeconds = 30,
                                )
                            }
                        } else {
                            WorkoutAction.RestExpired
                        }
                    }
                    WorkoutAction.SkipRest, is WorkoutAction.PauseRest -> {
                        val controlAction = if (action is WorkoutAction.PauseRest) {
                            com.ironlog.app.services.NotificationActionRouter.Actions.PAUSE_REST
                        } else {
                            com.ironlog.app.services.NotificationActionRouter.Actions.SKIP_REST
                        }
                        val result = com.ironlog.app.services.WorkoutNotificationActionInbox
                            .commitRestControl(
                                sessionId = workoutId,
                                actionId = controlAction,
                                now = com.ironlog.app.services.WorkoutTimerClock.now(getApplication()),
                                enqueueUiSync = false,
                            )
                        if (result.applied) {
                            runCatching {
                                WorkoutNotificationBridge.clearRestTimer(getApplication())
                                WorkoutForegroundService.onExternalRestStateChanged(getApplication(), workoutId)
                            }.onFailure { Timber.w(it, "Rest deadline cleared; notification refresh will retry") }
                            if (action is WorkoutAction.PauseRest) {
                                WorkoutAction.SyncPausedRest(result.pausedRemainingMs)
                            } else {
                                WorkoutAction.SkipRest
                            }
                        } else if (action is WorkoutAction.SkipRest) {
                            WorkoutAction.SkipRest
                        } else {
                            WorkoutAction.RestExpired
                        }
                    }
                    is WorkoutAction.ResumeRest -> {
                        val deadline = com.ironlog.app.services.WorkoutTimerDeadline(
                            wallEndMs = action.newEndTime,
                            elapsedEndMs = action.newEndElapsedTime ?: 0L,
                            bootCount = action.bootCount,
                        )
                        if (WorkoutForegroundService.setRestDeadline(
                                getApplication(), workoutId, deadline
                            )
                        ) action else WorkoutAction.RestExpired
                    }
                    else -> null
                }
                    if (appliedAction != null) {
                        withContext(Dispatchers.Main.immediate) { publishAction(appliedAction) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Could not apply rest control")
                _mutationError.value = "The rest timer could not be updated. Try again."
            } finally {
                _restControlPending.value = false
            }
        }
    }

    fun syncRestFromPersistence() {
        val workoutId = activeWorkoutId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mutationMutex.withLock {
                    val persisted = WorkoutForegroundService.readRestStateSnapshot(getApplication(), workoutId)
                    withContext(Dispatchers.Main.immediate) {
                        if ((persisted?.pausedRemainingMs ?: 0L) > 0L) {
                            publishAction(WorkoutAction.SyncPausedRest(persisted!!.pausedRemainingMs))
                        } else {
                            publishAction(WorkoutAction.SyncRestDeadline(
                                endTime = persisted?.deadline?.wallEndMs,
                                endElapsedTime = persisted?.deadline?.elapsedEndMs,
                                bootCount = persisted?.deadline?.bootCount ?: -1,
                            ))
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Could not synchronize the rest timer")
                _mutationError.value = "The rest timer state could not be refreshed."
            }
        }
    }

    fun logSet(exIndex: Int, exerciseId: String, weightText: String, repsText: String, trackingType: String, restSeconds: Int, weightUnit: String) {
        val requestedRow = workoutExerciseIdByIndex[exIndex]
        val displayWeight = weightText.toDoubleOrNull() ?: 0.0
        val weight = canonicalSetLoad(displayWeight, trackingType, weightUnit)
        val reps = repsText.toDoubleOrNull() ?: 0.0
        if (!weight.isFinite() || weight < 0.0 || !reps.isFinite() || reps <= 0.0) {
            _mutationError.value = "Enter a valid load and positive reps or duration."
            return
        }
        val stableSet = LoggedSet(weight = weight, reps = reps, trackingType = trackingType, durationSec = if (trackingType.startsWith("duration")) reps else null)
        launchMutation {
            mutationMutex.withLock {
                val exIndex = currentExerciseIndex(exIndex, requestedRow)
                val workoutExId = resolveWorkoutExerciseId(exIndex, exerciseId) ?: return@withLock
                try {
                    workoutRepo.addSet(workoutExId, SetInput(uid = stableSet.id, weight = weight, reps = reps, restSeconds = restSeconds))
                    val totalSetsBefore = _workoutState.value.setLog.values.sumOf { it.size }
                    dispatch(WorkoutAction.LogSet(exIndex, stableSet))
                    if (restSeconds > 0) {
                        val deadline = WorkoutTimerClock.deadlineAfter(
                            currentClockSnapshot(),
                            restSeconds * 1_000L,
                        )
                        if (WorkoutForegroundService.setRestDeadline(
                                getApplication(),
                                workoutId = activeWorkoutId.orEmpty(),
                                deadline = deadline,
                            )
                        ) {
                            dispatch(WorkoutAction.StartRest(
                                endTime = deadline.wallEndMs,
                                endElapsedTime = deadline.elapsedEndMs,
                                bootCount = deadline.bootCount,
                                total = restSeconds,
                                triggerExIndex = exIndex,
                            ))
                            settingsRepo.setBoolean("gamification_rest_timer_used", true)
                        }
                    }
                    val setCountForExercise = _workoutState.value.setLog[exIndex]?.size ?: 0
                    settingsRepo.setString("active_workout_set_label", "Set $setCountForExercise")
                    if (totalSetsBefore == 0) startTimerOnFirstSet() else syncForegroundNotification()
                    checkForPr(exerciseId, stableSet)
                    persistDraftNow(_workoutState.value)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Timber.e(error, "Failed to log set")
                    _mutationError.value = error.message ?: "The set was not saved. Try again."
                }
            }
        }
    }

    fun persistSetRpe(exIndex: Int, exerciseId: String, setId: String, rpe: Double?) {
        persistSetUpdate(exIndex, exerciseId, setId, SetInput(rpeUpdate = rpe?.let { com.ironlog.app.data.model.EffortUpdate.Value(it) } ?: com.ironlog.app.data.model.EffortUpdate.Clear))
    }

    fun persistSetRir(exIndex: Int, exerciseId: String, setId: String, rir: Int?) {
        persistSetUpdate(exIndex, exerciseId, setId, SetInput(rirUpdate = rir?.let { com.ironlog.app.data.model.EffortUpdate.Value(it.toDouble()) } ?: com.ironlog.app.data.model.EffortUpdate.Clear))
    }

    fun persistSetType(exIndex: Int, exerciseId: String, setId: String, type: String) {
        val key = canonicalSetType(type)
        val input = SetInput(
            isWarmup = key == "warmup",
            isDropset = key == "drop",
            isAmrap = key == "amrap",
            toFailure = key == "failure",
        )
        persistSetUpdate(exIndex, exerciseId, setId, input)
    }

    fun persistSetValues(exIndex: Int, exerciseId: String, setId: String, weight: Double?, reps: Double?, weightUnit: String) {
        val tracking = _workoutState.value.setLog.values.flatten().firstOrNull { it.id == setId }?.trackingType ?: "weight_reps"
        persistSetUpdate(exIndex, exerciseId, setId, SetInput(weight = weight?.let { canonicalSetLoad(it, tracking, weightUnit) }, reps = reps))
    }

    fun persistSetNote(exIndex: Int, exerciseId: String, setId: String, note: String?) {
        persistSetUpdate(exIndex, exerciseId, setId, SetInput(notes = note.orEmpty()))
    }

    fun deleteSet(exIndex: Int, exerciseId: String, setId: String) {
        val requestedRow = workoutExerciseIdByIndex[exIndex]
        launchMutation {
            mutationMutex.withLock {
                val exIndex = currentExerciseIndex(exIndex, requestedRow)
                val workoutExId = resolveWorkoutExerciseId(exIndex, exerciseId) ?: return@withLock
                try {
                    workoutRepo.deleteSetAndCompact(workoutExId, setId)
                    val setIndex = _workoutState.value.setLog[exIndex].orEmpty().indexOfFirst { it.id == setId }
                    if (setIndex >= 0) dispatch(WorkoutAction.DeleteSet(exIndex, setIndex))
                    hadPrThisSession = recomputeHadPrFromRetainedSets()
                    persistDraftNow(_workoutState.value)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Timber.e(error, "Failed to delete set")
                    _mutationError.value = error.message ?: "The set was not deleted. Try again."
                }
            }
        }
    }

    fun logPendingWarmup(exIndex: Int, exerciseId: String, pending: PendingWarmup, restSeconds: Int = 45) {
        val requestedRow = workoutExerciseIdByIndex[exIndex]
        launchMutation {
            mutationMutex.withLock {
                val exIndex = currentExerciseIndex(exIndex, requestedRow)
                val workoutExId = resolveWorkoutExerciseId(exIndex, exerciseId) ?: return@withLock
                try {
                    val warmupCount = _workoutState.value.setLog[exIndex].orEmpty().count { it.type == "warmup" }
                    workoutRepo.insertSetAt(
                        workoutExId,
                        warmupCount + 1,
                        SetInput(uid = pending.id, weight = pending.weightKg, reps = pending.reps.toDouble(), restSeconds = restSeconds, isWarmup = true),
                    )
                    dispatch(WorkoutAction.LogPendingWarmup(exIndex, pending.id))
                    persistDraftNow(_workoutState.value)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Timber.e(error, "Failed to log warmup")
                    _mutationError.value = error.message ?: "The warmup was not saved. Try again."
                }
            }
        }
    }

    private fun persistSetUpdate(exIndex: Int, exerciseId: String, setId: String, input: SetInput) {
        launchMutation {
            mutations.commit<WorkoutSetEntity>(
                write = {
                    check(_workoutState.value.setLog.values.any { sets -> sets.any { it.id == setId } }) {
                        "This set was removed before the edit could be saved."
                    }
                    workoutRepo.updateSet(setId, input)
                },
                publish = { saved ->
                    val current = _workoutState.value
                    _workoutState.value = current.copy(setLog = current.setLog.mapValues { (_, sets) ->
                        sets.map { old -> if (old.id != saved.uid) old else old.copy(
                            weight = saved.weight, reps = saved.reps, rpe = saved.rpe, rir = saved.rir?.toInt(),
                            note = saved.notes?.takeIf { it.isNotBlank() },
                            type = when { saved.isWarmup -> "warmup"; saved.isDropset -> "drop"; saved.isAmrap -> "amrap"; saved.toFailure -> "failure"; else -> "normal" },
                            durationSec = if (old.trackingType.startsWith("duration")) saved.reps else old.durationSec,
                        ).let { it.copy(orm = com.ironlog.app.ui.state.loggedSetEstimatedOneRm(it)) } }
                    })
                    hadPrThisSession = recomputeHadPrFromRetainedSets()
                    persistDraft(_workoutState.value)
                },
            )
        }
    }

    fun clearMutationError() { _mutationError.value = null }

    private fun launchMutation(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            mutations.awaitReady()
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.e(error, "Workout mutation failed")
            _mutationError.value = error.message ?: "The change was not saved. Try again."
        }
    }

    private suspend fun resolveWorkoutExerciseId(exIndex: Int, exerciseId: String): String? {
        check(activeWorkoutId != null) { "This workout has already ended." }
        check(exerciseIdByIndex[exIndex] == exerciseId) { "This exercise changed. Retry from its current card." }
        return checkNotNull(workoutExerciseIdByIndex[exIndex]) { "This exercise is not saved yet. Retry after it loads." }
    }

    private fun currentExerciseIndex(fallback: Int, rowUid: String?): Int {
        if (rowUid == null) return fallback
        return checkNotNull(workoutExerciseIdByIndex.entries.firstOrNull { it.value == rowUid }?.key) {
            "This exercise was removed before the change could be saved."
        }
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
        exerciseIdByIndex = workoutExerciseIdByIndex.mapNotNull { (index, rowUid) ->
            orderedRows.firstOrNull { it.uid == rowUid }?.exerciseUid?.let { index to it }
        }.toMap()
        val rowsByUid = orderedRows.associateBy { it.uid }
        val current = _workoutState.value
        val defaultNotes = workoutExerciseIdByIndex.mapNotNull { (index, uid) ->
            rowsByUid[uid]?.notes?.takeIf { it.isNotBlank() }?.let { index to it }
        }.toMap()
        val defaultSupersets = workoutExerciseIdByIndex.mapNotNull { (index, uid) ->
            rowsByUid[uid]?.supersetGroup?.takeIf { it.isNotBlank() }?.let { index to it }
        }.toMap()
        _workoutState.value = current.copy(
            exerciseNotes = defaultNotes + current.exerciseNotes,
            supersetGroups = defaultSupersets + current.supersetGroups,
        )
        // Binding may complete after the first Compose exercise emission. It owns the initial
        // load so an early unbound request cannot permanently suppress previous-session data.
        loadGhostData(ids)
    }

    private fun checkForPr(exerciseId: String, set: LoggedSet) {
        val oneRm = com.ironlog.app.ui.state.loggedSetEstimatedOneRm(set)
        if (oneRm <= 0.0) return
        val historical = historicalBest1rm[exerciseId]
        val sessionBest = _workoutState.value.setLog.flatMap { (index, sets) ->
            if (exerciseIdByIndex[index] == exerciseId) sets else emptyList()
        }.filterNot { it.id == set.id }.maxOfOrNull { com.ironlog.app.ui.state.loggedSetEstimatedOneRm(it) }
        val threshold = maxOf(historical ?: Double.NEGATIVE_INFINITY, sessionBest ?: Double.NEGATIVE_INFINITY)
        sessionBest1rm[exerciseId] = maxOf(sessionBest ?: Double.NEGATIVE_INFINITY, oneRm)
        hadPrThisSession = recomputeHadPrFromRetainedSets()
        if (historical != null && oneRm > threshold) {
            val oneRmDisplay = com.ironlog.app.util.formatWeightFromKg(oneRm, vmWeightUnit)
            _prBanner.value = "🏆 New PR! ~$oneRmDisplay 1RM"
            viewModelScope.launch {
                delay(4000)
                _prBanner.value = null
            }
        }
    }

    private fun loadHistoricalPrBaselines(): MutableMap<String, Double> {
        val now = Instant.now()
        return historicalPrBaselines(
            history = com.ironlog.app.data.repository.HistoryRepository().completedSnapshotBlocking(),
            prResetAt = settingsRepo.getPersonalBestResetAtBlocking(),
            now = now,
            zoneId = ZoneId.systemDefault(),
        ).toMutableMap()
    }

    private fun recomputeHadPrFromRetainedSets(): Boolean = _workoutState.value.setLog.any { (index, sets) ->
        val exerciseId = exerciseIdByIndex[index] ?: return@any false
        val baseline = historicalBest1rm[exerciseId] ?: return@any false
        sets.any { set -> com.ironlog.app.ui.state.loggedSetEstimatedOneRm(set) > baseline }
    }

    fun finishWorkout() {
        _showCompletionSheet.value = true
    }

    /**
     * Durably clears exercise-level notes for this active session without touching set notes,
     * session notes, previous-session reminders, the source plan, or completed workouts.
     */
    suspend fun clearExerciseNotesNow() {
        mutations.awaitReady()
        mutationMutex.withLock {
            val workoutId = checkNotNull(activeWorkoutId) { "No active workout was found." }
            val clearedState = workoutReducer(_workoutState.value, WorkoutAction.ClearExerciseNotes)
            val sanitizedDraft = json.encodeToString(draftPayload(clearedState))

            // Invalidate any queued draft captured before this destructive edit. pause() also
            // waits for an in-flight gated write, so it cannot overwrite the sanitized draft.
            draftWrites.pause()
            try {
                withContext(NonCancellable) {
                    workoutRepo.clearActiveWorkoutExerciseNotes(workoutId, sanitizedDraft)
                    _workoutState.value = clearedState
                }
            } finally {
                draftWrites.resume()
            }
        }
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
            try {
                mutations.awaitReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _showCompletionSheet.value = false
                onError(error.message ?: "The workout is not ready.")
                return@launch
            }
            mutationMutex.withLock {
            val id = activeWorkoutId
            if (id.isNullOrBlank()) {
                _showCompletionSheet.value = false
                onError("No active workout was found.")
                return@launch
            }
            var streakDays = 0
            val hadPr = recomputeHadPrFromRetainedSets()
            draftWrites.pause()
            try {
                commitWorkoutTerminalMutation {
                    workoutRepo.completeWorkout(
                        workoutId = id,
                        durationStartEpochMs = timerStartEpochMs,
                        durationSecondsOverride = timerStartEpochMs?.let { startWall ->
                            (WorkoutTimerClock.elapsedSinceStartMs(
                                startWallMs = startWall,
                                startElapsedMs = timerStartElapsedMs ?: 0L,
                                startBootCount = timerStartBootCount,
                                now = currentClockSnapshot(),
                            ) / 1_000L).coerceIn(0L, 86_400L).toInt()
                        },
                        metadata = com.ironlog.app.data.model.WorkoutMetadataInput(
                            rating = if (rating in 1..5) rating.toDouble() else null,
                            notes = notes.ifBlank { null },
                        ),
                        exerciseNotesByUid = _workoutState.value.exerciseNotes.map { (index, note) ->
                            checkNotNull(workoutExerciseIdByIndex[index]) {
                                "An exercise note is not bound to a saved row. Reopen this session and retry."
                            } to note
                        }.toMap(),
                    )
                }
            } catch (e: Exception) {
                draftWrites.resume()
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to complete workout %s", id)
                persistDraft(_workoutState.value)
                _showCompletionSheet.value = false
                onError(e.message ?: "The workout could not be completed. Your active session was preserved.")
                return@launch
            }

            // The durable workout and active-session keys are committed atomically. Clear the
            // in-memory ID immediately so screen disposal cannot recreate the deleted draft.
            activeWorkoutId = null
            _activeWorkoutIdSignal.value = null
            mutations.close()
            timerStartEpochMs = null
            timerStartElapsedMs = null
            timerStartBootCount = -1
            _elapsedSeconds.value = 0
            _timerStarted.value = false
            _showCompletionSheet.value = false
            var milestoneAlertsEnabled = false
            afterWorkoutCommit(
                cleanup = listOf(
                    { WorkoutForegroundService.stop(getApplication(), id) },
                    { com.ironlog.app.services.WorkoutNotificationActionInbox.clearSession(id) },
                    { WorkoutNotificationBridge.clearWorkout(getApplication()) },
                    { WorkoutNotificationBridge.cancelReminderAfterDataMutation(getApplication()) },
                    { streakDays = withContext(Dispatchers.IO) { computeDailyWorkoutStreakDays() } },
                    { workoutRepo.recordPostWorkoutMetrics(id, totalVolumeKg, hadPr); Unit },
                    { com.ironlog.app.widget.WidgetUpdateWorker.enqueueOneTime(getApplication()); Unit },
                    { milestoneAlertsEnabled = settingsRepo.getBoolean("milestone_alerts_enabled", true) },
                ),
                onCleanupFailure = { Timber.e(it, "Post-completion cleanup failed for %s", id) },
                onDone = {
                    onDone(WorkoutCompletionCelebration(
                        hasPrCelebration = milestoneAlertsEnabled && hadPr,
                        hasStreak30Celebration = milestoneAlertsEnabled && streakDays >= 30,
                    ))
                },
            )
            }
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
            try {
                mutations.awaitReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "The workout is not ready.")
                return@launch
            }
            mutationMutex.withLock {
            val id = activeWorkoutId
            draftWrites.pause()
            if (!id.isNullOrBlank()) {
                try {
                    commitWorkoutTerminalMutation { workoutRepo.abandonWorkout(id) }
                } catch (e: Exception) {
                    draftWrites.resume()
                    if (e is CancellationException) throw e
                    Timber.e(e, "Failed to abandon workout %s", id)
                    persistDraft(_workoutState.value)
                    onError(e.message ?: "The workout could not be discarded. Your session was preserved.")
                    return@launch
                }
            }
            activeWorkoutId = null
            _activeWorkoutIdSignal.value = null
            mutations.close()
            timerStartEpochMs = null
            timerStartElapsedMs = null
            timerStartBootCount = -1
            _elapsedSeconds.value = 0
            _timerStarted.value = false
            dispatch(WorkoutAction.HydrateState(WorkoutState()))
            afterWorkoutCommit(
                cleanup = listOf(
                    { if (!id.isNullOrBlank()) WorkoutForegroundService.stop(getApplication(), id) },
                    { if (!id.isNullOrBlank()) com.ironlog.app.services.WorkoutNotificationActionInbox.clearSession(id) },
                    { WorkoutNotificationBridge.clearWorkout(getApplication()) },
                    { WorkoutNotificationBridge.cancelReminderAfterDataMutation(getApplication()) },
                ),
                onCleanupFailure = { Timber.e(it, "Post-discard cleanup failed for %s", id) },
                onDone = onDone,
            )
            }
        }
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
            endElapsedTime = state.restTimer.endElapsedTime,
            bootCount = state.restTimer.bootCount,
            total = state.restTimer.total,
            paused = state.restTimer.paused,
            pausedAt = state.restTimer.pausedAt,
            pausedRemainingMs = state.restTimer.pausedRemainingMs,
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
        targetOverrides = state.targetOverrides.mapKeys { it.key.toString() }.mapValues { (_, target) ->
            TargetOverrideDto(target.sets, target.reps)
        },
        pendingWarmups = state.pendingWarmups.mapKeys { it.key.toString() }.mapValues { (_, warmups) ->
            warmups.map { PendingWarmupDto(it.id, it.weightKg, it.reps) }
        },
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

    private data class DraftWrite(val workoutId: String, val revision: Long, val payload: String)

    private fun captureDraftWrite(): DraftWrite? {
        // Never overwrite a saved draft while its initialization/restoration is in flight.
        if (!initialization.value.ready) return null
        val id = activeWorkoutId ?: return null
        val revision = draftWrites.capture() ?: return null
        return DraftWrite(id, revision, json.encodeToString(draftPayload(_workoutState.value)))
    }

    private fun writeDraft(write: DraftWrite) {
        draftWrites.writeIfCurrent(write.revision) {
            // A terminal transaction may have committed immediately before coroutine cancellation.
            // Check its durable active identity in the same transaction as the draft write.
            ObjectBox.store.runInTx {
                if (settingsRepo.getStringBlocking("active_workout_id") == write.workoutId) {
                    settingsRepo.setStringBlocking("active_workout_draft_${write.workoutId}", write.payload, "json")
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER") // Preserve callers; the authoritative VM snapshot may be newer than composition.
    fun persistDraft(state: WorkoutState) {
        val write = captureDraftWrite() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { writeDraft(write) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _mutationError.value = error.message ?: "The workout draft was not saved. Try again."
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun persistDraftNow(state: WorkoutState) {
        val write = captureDraftWrite() ?: return
        withContext(Dispatchers.IO) { writeDraft(write) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun persistDraftBlocking(state: WorkoutState) {
        val write = captureDraftWrite() ?: return
        runCatching { writeDraft(write) }
            .onFailure { _mutationError.value = it.message ?: "The workout draft was not saved. Try again." }
    }

    private suspend fun restoreDraftIfAny() {
            val id = activeWorkoutId ?: return
            val raw = settingsRepo.getString("active_workout_draft_$id").orEmpty()
            if (raw.isBlank()) return
            val dto = json.decodeFromString(WorkoutDraftDto.serializer(), raw)
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
                    endElapsedTime = dto.restTimer.endElapsedTime,
                    bootCount = dto.restTimer.bootCount,
                    total = dto.restTimer.total,
                    paused = dto.restTimer.paused,
                    pausedAt = dto.restTimer.pausedAt,
                    pausedRemainingMs = dto.restTimer.pausedRemainingMs,
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
                targetOverrides = dto.targetOverrides.mapNotNull { (key, value) ->
                    key.toIntOrNull()?.let { it to com.ironlog.app.ui.state.TargetOverride(value.sets, value.reps) }
                }.toMap(),
                pendingWarmups = dto.pendingWarmups.mapNotNull { (key, warmups) ->
                    key.toIntOrNull()?.let { index ->
                        index to warmups.map { PendingWarmup(it.id, it.weightKg, it.reps) }
                    }
                }.toMap(),
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

            _workoutState.value = restored.copy(swappedExercises = restoredSwaps)

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

    fun dismissCompletionSheet() {
        _showCompletionSheet.value = false
    }

    fun swapExercise(exIndex: Int, activeExerciseId: String, newExercise: LegacyExerciseShape) {
        val requestedRow = workoutExerciseIdByIndex[exIndex]
        launchMutation {
            mutationMutex.withLock {
                val exIndex = currentExerciseIndex(exIndex, requestedRow)
                if (_workoutState.value.setLog[exIndex].orEmpty().isNotEmpty()) {
                    _mutationError.value = "Remove this exercise's logged sets before swapping it."
                    return@withLock
                }
                val workoutExId = resolveWorkoutExerciseId(exIndex, activeExerciseId) ?: return@withLock
                val newExerciseId = newExercise.id.ifBlank { newExercise.exerciseId }
                runCatching { workoutRepo.swapWorkoutExercise(workoutExId, newExerciseId) }
                    .onSuccess {
                        workoutExerciseIdByIndex = workoutExerciseIdByIndex + (exIndex to workoutExId)
                        exerciseIdByIndex = exerciseIdByIndex + (exIndex to newExerciseId)
                        dispatch(WorkoutAction.SwapExercise(exIndex, newExercise))
                        persistDraftNow(_workoutState.value)
                    }
                    .onFailure { if (it is CancellationException) throw it; _mutationError.value = it.message ?: "Exercise swap was not saved." }
            }
        }
    }

    fun addExerciseToWorkout(entry: AddedExerciseEntry, onCommitted: () -> Unit = {}) {
        launchMutation {
            mutations.commit<WorkoutExerciseEntity>(
                write = { workoutRepo.addExerciseToWorkout(checkNotNull(activeWorkoutId), entry.exerciseId) },
                publish = { row ->
                    val index = (workoutExerciseIdByIndex.keys.maxOrNull() ?: -1) + 1
                    workoutExerciseIdByIndex = workoutExerciseIdByIndex + (index to row.uid)
                    exerciseIdByIndex = exerciseIdByIndex + (index to entry.exerciseId)
                    dispatch(WorkoutAction.AddExercise(entry))
                    onCommitted()
                },
            )
        }
    }

    fun persistSuperset(exIndex: Int, exerciseId: String, group: String?) {
        val rowUid = workoutExerciseIdByIndex[exIndex]
        launchMutation {
            mutations.commit<Pair<Int, WorkoutExerciseEntity>>(
                write = {
                    val index = currentExerciseIndex(exIndex, rowUid)
                    val uid = checkNotNull(resolveWorkoutExerciseId(index, exerciseId))
                    index to workoutRepo.updateWorkoutExerciseSuperset(uid, group)
                },
                publish = { (index, saved) -> dispatch(WorkoutAction.AssignSuperset(index, saved.supersetGroup.takeIf { it.isNotBlank() })) },
            )
        }
    }

    fun removeExercise(action: WorkoutAction.RemoveExercise, onCommitted: (Int) -> Unit = {}) {
        val rowUid = workoutExerciseIdByIndex[action.exIndex]
        launchMutation {
            mutations.commit<Int>(
                write = {
                    val uid = checkNotNull(rowUid) { "This exercise is still loading. Retry." }
                    val index = currentExerciseIndex(action.exIndex, uid)
                    workoutRepo.deleteWorkoutExercise(uid)
                    index
                },
                publish = { index ->
                    val currentBaseCount = workoutExerciseIdByIndex.size - _workoutState.value.addedExercises.size
                    onCommitted(index)
                    workoutExerciseIdByIndex = workoutExerciseIdByIndex.filterKeys { it != index }
                        .mapKeys { (key, _) -> if (key > index) key - 1 else key }
                    exerciseIdByIndex = exerciseIdByIndex.filterKeys { it != index }
                        .mapKeys { (key, _) -> if (key > index) key - 1 else key }
                    persistedOrderedIndices = persistedOrderedIndices.filter { it != index }.map { if (it > index) it - 1 else it }
                    _workoutState.value = workoutReducer(_workoutState.value, action.copy(exIndex = index, baseExercisesCount = currentBaseCount))
                    hadPrThisSession = recomputeHadPrFromRetainedSets()
                    persistDraft(_workoutState.value)
                },
            )
        }
    }

    suspend fun rehydrateSetLogFromDatabase(exerciseIdsInUiOrder: List<String>): Boolean {
        mutations.awaitReady()
        val workoutId = activeWorkoutId ?: return false
        if (exerciseIdsInUiOrder.isEmpty()) return false
        return mutationMutex.withLock {
                if (activeWorkoutId != workoutId) return@withLock false
                try {
                    val detail = workoutRepo.getWorkoutDetailSnapshot(workoutId) ?: return@withLock false
                    val catalog = withContext(Dispatchers.IO) {
                        ObjectBox.store.boxFor(com.ironlog.app.data.objectbox.ExerciseEntity::class.java).all.associateBy { it.uid }
                    }
                    bindWorkoutExerciseRows(detail.exercises, exerciseIdsInUiOrder)
                    val tracking = exerciseIdsInUiOrder.mapIndexed { index, id ->
                        val row = catalog[id]
                        val metadata = com.ironlog.app.ui.model.HistoryExercise(
                            name = row?.name.orEmpty(), trackingType = row?.trackingType,
                            category = row?.category, equipment = row?.equipment,
                            isBodyweight = row?.isBodyweight ?: false,
                        )
                        index to when (com.ironlog.app.domain.training.TrainingSetPolicy.tracking(metadata)) {
                            com.ironlog.app.domain.training.TrackingMode.BODYWEIGHT_REPS -> "bodyweight_reps"
                            com.ironlog.app.domain.training.TrackingMode.ADDED_LOAD_REPS -> "bodyweight_plus_weight_reps"
                            com.ironlog.app.domain.training.TrackingMode.ASSISTED_REPS -> "assisted_bodyweight"
                            com.ironlog.app.domain.training.TrackingMode.DURATION -> "duration"
                            com.ironlog.app.domain.training.TrackingMode.WEIGHTED_DURATION -> "duration_weight"
                            com.ironlog.app.domain.training.TrackingMode.DURATION_DISTANCE -> "duration_distance"
                            else -> row?.trackingType ?: "weight_reps"
                        }
                    }.toMap()
                    _workoutState.value = com.ironlog.app.ui.state.restorePersistedSetLog(
                        _workoutState.value, detail.sets, workoutExerciseIdByIndex, tracking,
                    )
                    hadPrThisSession = recomputeHadPrFromRetainedSets()
                    persistDraftNow(_workoutState.value)
                    true
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _mutationError.value = error.message ?: "Saved sets could not be restored. Try again."
                    false
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    dayId: String = "",
    startEmpty: Boolean = false,
    weightUnitOverride: String? = null,
    effortTrackingOverride: String? = null,
    hapticFeedbackOverride: Boolean? = null,
    onFinish: (() -> Unit)? = null,
    onMinimize: (() -> Unit)? = null,
    vm: ActiveWorkoutViewModel = viewModel(),
    plansVm: PlansViewModel = viewModel(),
) {
    val c = useTheme()
    val context = LocalContext.current
    val initialization by vm.initialization.collectAsStateWithLifecycle()
    val appVm: AppDataViewModel = viewModel()
    val appState by appVm.state.collectAsStateWithLifecycle()
    val appSettings = appState.settings
    val weightUnit = weightUnitOverride ?: appSettings.weightUnit
    val effortTracking = effortTrackingOverride ?: appSettings.effortTracking
    val hapticFeedback = hapticFeedbackOverride ?: appSettings.hapticFeedback
    val credentialRevision by CloudAiKeyStore.revision.collectAsStateWithLifecycle()
    val cloudApiKey = remember(credentialRevision, appSettings.cloudAiProviderPreset) {
        CloudAiKeyStore.load(context, appSettings.cloudAiProviderPreset)
    }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsRepo = remember { SettingsRepository() }
    val planNotesVisible by remember(settingsRepo) {
        settingsRepo.observePlanExerciseNotesVisible()
    }.collectAsStateWithLifecycle(initialValue = true)
    val workoutDtoJson = remember { Json { ignoreUnknownKeys = true } }
    val state by vm.workoutState.collectAsStateWithLifecycle()
    val timerStarted by vm.timerStarted.collectAsStateWithLifecycle()
    val prBanner by vm.prBanner.collectAsStateWithLifecycle()
    val showCompletionSheet by vm.showCompletionSheet.collectAsStateWithLifecycle()
    val activeWorkoutId by vm.activeWorkoutIdSignal.collectAsStateWithLifecycle()
    val plans by plansVm.plans.collectAsStateWithLifecycle()
    val restoredOrderedIndices by vm.restoredOrderedIndices.collectAsStateWithLifecycle()
    val mutationError by vm.mutationError.collectAsStateWithLifecycle()
    val restControlPending by vm.restControlPending.collectAsStateWithLifecycle()
    var workoutStatusNotificationsAvailable by remember {
        mutableStateOf(WorkoutForegroundService.isWorkoutNotificationAvailable(context))
    }
    var restCompletionNotificationsAvailable by remember {
        mutableStateOf(WorkoutForegroundService.isRestCompletionNotificationAvailable(context))
    }
    val exerciseRepo = remember { ExerciseRepository(context.applicationContext) }
    var swapTargetIndex by remember { mutableStateOf<Int?>(null) }
    var swapQuery by remember { mutableStateOf("") }
    var isCreatingSwapExercise by remember { mutableStateOf(false) }
    var swapCreateError by remember { mutableStateOf<String?>(null) }
    var pendingExerciseSwap by remember { mutableStateOf<PendingExerciseSwap?>(null) }
    var isApplyingPlanSwap by remember { mutableStateOf(false) }
    var showSaveToPlanPrompt by remember { mutableStateOf(false) }
    var pendingOnFinish by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCelebration by remember { mutableStateOf<WorkoutCompletionCelebration?>(null) }
    var isFinalizingWorkout by remember { mutableStateOf(false) }
    var isSavingAddedExercises by remember { mutableStateOf(false) }
    var workoutActionError by remember { mutableStateOf<String?>(null) }
    var showCompletionConfetti by remember { mutableStateOf(false) }
    var confettiBurstId by remember { mutableStateOf(0) }
    var showNotesSettings by remember { mutableStateOf(false) }
    var deletingWorkoutNotes by remember { mutableStateOf(false) }
    var notesSettingsError by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(mutationError) {
        mutationError?.let {
            workoutActionError = it
            vm.clearMutationError()
        }
    }
    LaunchedEffect(scrollToNextSupersetIndex) {
        scrollToNextSupersetIndex?.let { pos ->
            try {
                lazyListState.animateScrollToItem(pos)
            } finally {
                if (scrollToNextSupersetIndex == pos) scrollToNextSupersetIndex = null
            }
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
    val progressionPolicyStore = remember(settingsRepo) { ProgressionPolicyStore(settingsRepo) }
    val progressionPlanExerciseIds = remember(resolvedDay) {
        resolvedDay?.exercises.orEmpty().map { it.id }.filter(String::isNotBlank).toSet()
    }
    val conservativeProgressionSnapshot = remember {
        val fallback = ResolvedProgressionPolicy.conservativeDefault()
        ProgressionPolicySnapshot(fallback = fallback, byPlanExerciseId = emptyMap())
    }
    val progressionPolicySnapshot by produceState(
        initialValue = conservativeProgressionSnapshot,
        resolvedPlan?.id,
        progressionPlanExerciseIds,
        appSettings.progressionStyle,
    ) {
        value = try {
            progressionPolicyStore.load(resolvedPlan?.id, progressionPlanExerciseIds)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.w(error, "Progression policy settings could not be loaded")
            conservativeProgressionSnapshot
        }
    }

    val baseExerciseEntries = remember(resolvedDay, state.removedBaseExerciseIndices, exercisePool) {
        (resolvedDay?.exercises.orEmpty()).mapIndexedNotNull { originalIndex, planEx ->
            if (originalIndex in state.removedBaseExerciseIndices) {
                null
            } else {
                val libraryExercise = exercisePool.firstOrNull { candidate ->
                    candidate.id == planEx.exerciseId || candidate.exerciseId == planEx.exerciseId ||
                        candidate.name.equals(planEx.name, ignoreCase = true)
                }
                originalIndex to normalizeSessionExercise(planEx, libraryExercise)
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
                isBodyweight = entry.trackingType.contains("bodyweight") || entry.equipment.orEmpty().contains("bodyweight", ignoreCase = true),
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
                isBodyweight = isBodyweightExercise(sw),
            )
        }
        swapped + addedAsNormalized
    }

    LaunchedEffect(dayId, startEmpty) {
        vm.initWorkout(dayId, startEmpty)
    }
    LaunchedEffect(timerStarted) {
        workoutStatusNotificationsAvailable = WorkoutForegroundService.isWorkoutNotificationAvailable(context)
        restCompletionNotificationsAvailable =
            WorkoutForegroundService.isRestCompletionNotificationAvailable(context)
    }
    // Sync orderedIndices from the ViewModel when a draft is restored after minimize/resume.
    LaunchedEffect(restoredOrderedIndices) {
        if (restoredOrderedIndices.isNotEmpty() && restoredOrderedIndices.size == exercises.size) {
            orderedIndices = restoredOrderedIndices
        }
    }
    LaunchedEffect(exercises) {
        // Load ghost (last-session) data whenever the exercise list changes
        vm.loadGhostData(exercises.map { it.exerciseId })
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
    val hydrationGate = remember(vm) { com.ironlog.app.ui.state.WorkoutHydrationGate() }
    LaunchedEffect(activeWorkoutId, exercises.map { it.exerciseId }) {
        hydrationGate.reconcile(activeWorkoutId, exercises.map { it.exerciseId }) { ids ->
            vm.rehydrateSetLogFromDatabase(ids)
        }
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
        val profiles = runCatching { workoutDtoJson.decodeFromString<List<GymProfileDto>>(raw) }.getOrDefault(emptyList())
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
    LaunchedEffect(activeWorkoutId) {
        if (!activeWorkoutId.isNullOrBlank()) vm.syncRestFromPersistence()
    }
    val latestState by rememberUpdatedState(state)
    DisposableEffect(lifecycleOwner, activeWorkoutId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !activeWorkoutId.isNullOrBlank()) {
                vm.persistDraftBlocking(latestState)
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                workoutStatusNotificationsAvailable =
                    WorkoutForegroundService.isWorkoutNotificationAvailable(context)
                restCompletionNotificationsAvailable =
                    WorkoutForegroundService.isRestCompletionNotificationAvailable(context)
                vm.syncRestFromPersistence()
                vm.reconcileForegroundNotificationOnResume()
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
    LaunchedEffect(activeWorkoutId) {
        val sessionId = activeWorkoutId ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            com.ironlog.app.services.WorkoutNotificationActionInbox
                .signalsForSession(sessionId)
                .collect {
                    while (true) {
                        val action = withContext(Dispatchers.IO) {
                            com.ironlog.app.services.WorkoutNotificationActionInbox
                                .consumeForSession(sessionId)
                                .orEmpty()
                        }
                        if (action.isBlank()) break
                        when (action) {
                            com.ironlog.app.services.NotificationActionRouter.Actions.ADD_30S -> {
                                vm.dispatch(WorkoutAction.Add30s)
                            }
                            com.ironlog.app.services.NotificationActionRouter.Actions.SKIP_REST -> {
                                vm.dispatch(WorkoutAction.SkipRest)
                            }
                            com.ironlog.app.services.NotificationActionRouter.Actions.REST_STATE_CHANGED -> {
                                vm.syncRestFromPersistence()
                            }
                            com.ironlog.app.services.NotificationActionRouter.Actions.FINISH_WORKOUT -> {
                                vm.finishWorkout()
                            }
                        }
                    }
                }
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

    if (initialization.loading || initialization.error != null) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (initialization.loading) {
                CircularProgressIndicator(color = c.accent)
                Text("Loading saved workout…", color = c.text, modifier = Modifier.padding(top = 12.dp))
            } else {
                Text(initialization.error.orEmpty(), color = c.text)
                Button(onClick = { vm.initWorkout(dayId, startEmpty) }, modifier = Modifier.padding(top = 12.dp)) { Text("RETRY") }
            }
        }
        return
    }

    ActiveWorkoutViewport(
        bottomBar = {
            RestTimerPanel(
                restTimer = state.restTimer,
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                dispatch = vm::dispatch,
                controlsEnabled = !restControlPending,
                hapticFeedback = hapticFeedback,
            )
        },
    ) { inputViewport ->
        // Rest controls have their own measured footer; retain ordinary end spacing.
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(16.dp)
                .onGloballyPositioned(inputViewport::capture),
            verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp),
        ) {
            if (timerStarted && (!workoutStatusNotificationsAvailable || !restCompletionNotificationsAvailable)) {
                item {
                    val bothUnavailable = !workoutStatusNotificationsAvailable && !restCompletionNotificationsAvailable
                    val warningCopy = when {
                        bothUnavailable -> "Workout shade controls and rest-complete alerts are off. Tap to review Android notifications."
                        !workoutStatusNotificationsAvailable -> "Workout shade controls are off. Rest-complete alerts remain separate. Tap to enable the workout channel."
                        else -> "Rest-complete alerts are off. Workout shade controls remain available. Tap to enable rest alerts."
                    }
                    Surface(
                        color = c.warning.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(IronLogRadius.md.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            val channelId = when {
                                bothUnavailable -> null
                                !workoutStatusNotificationsAvailable -> WorkoutForegroundService.CHANNEL_ID
                                else -> WorkoutNotificationBridge.REST_COMPLETE_CHANNEL_ID
                            }
                            context.startActivity(Intent(
                                if (channelId != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                                } else {
                                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                }
                            ).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                if (channelId != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                                }
                            })
                        },
                    ) {
                        Text(
                            warningCopy,
                            color = c.warning,
                            fontSize = IronLogType.meta.fontSize.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.appPadding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                    shape = RoundedCornerShape(IronLogRadius.xl.dp),
                ) {
                    Column(Modifier.fillMaxWidth().appPadding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = appSpacedBy(10.dp)) {
                        // ── Row 1: name + close/menu ──────────────────────────
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f).appPadding(end = 8.dp)) {
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
                                    IronLogDropdownMenu(showHeaderMenu, onDismissRequest = { showHeaderMenu = false }) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Minimize", color = c.text) },
                                            onClick = { showHeaderMenu = false; onMinimize?.invoke() },
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("Exercise notes", color = c.text)
                                                    Text(
                                                        if (planNotesVisible) "Shown" else "Hidden",
                                                        color = c.muted,
                                                        fontSize = IronLogType.meta.fontSize.sp,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showHeaderMenu = false
                                                notesSettingsError = null
                                                showNotesSettings = true
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Outlined.NoteAlt,
                                                    contentDescription = null,
                                                    tint = if (planNotesVisible) c.accent else c.muted,
                                                )
                                            },
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = appSpacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .background(c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                        .appPadding(horizontal = 12.dp, vertical = 6.dp),
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
                            horizontalArrangement = appSpacedBy(8.dp),
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
                val planExerciseId = baseOriginalIndex?.let { resolvedDay?.exercises?.getOrNull(it)?.id }
                val progressionPolicy = progressionPolicySnapshot.forPlanExercise(planExerciseId)
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
                        listState = lazyListState,
                        inputViewport = inputViewport,
                        inputStructuralKey = state.setLog[exIndex].orEmpty().map { it.id } to prBanner,
                        preserveInputAnchor = scrollToNextSupersetIndex == null,
                        dispatch = vm::dispatch,
                        baseExercisesCount = baseExercises.size,
                        onRemoveExercise = {
                            vm.removeExercise(
                                WorkoutAction.RemoveExercise(
                                    exIndex = exIndex,
                                    baseExercisesCount = baseExercises.size,
                                    removedBaseIndex = baseOriginalIndex,
                                )
                            ) { removedIndex ->
                                orderedIndices = orderedIndices.filter { it != removedIndex }
                                    .map { if (it > removedIndex) it - 1 else it }
                                restOverride = restOverride.filterKeys { it != removedIndex }
                                    .mapKeys { (key, _) -> if (key > removedIndex) key - 1 else key }
                            }
                        },
                        onLogSet = { weight, reps ->
                            vm.logSet(exIndex, ex.exerciseId, weight, reps, ex.trackingType, restForLogSet, weightUnit)
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
                        onSetRpeChanged = { setId, rpe -> vm.persistSetRpe(exIndex, ex.exerciseId, setId, rpe) },
                        onSetRirChanged = { setId, rir -> vm.persistSetRir(exIndex, ex.exerciseId, setId, rir) },
                        onSetTypeChanged = { setId, type -> vm.persistSetType(exIndex, ex.exerciseId, setId, type) },
                        onSetValuesChanged = { setId, w, r -> vm.persistSetValues(exIndex, ex.exerciseId, setId, w, r, weightUnit) },
                        onSetNoteChanged = { setId, note -> vm.persistSetNote(exIndex, ex.exerciseId, setId, note) },
                        onDeleteSet = { setId -> vm.deleteSet(exIndex, ex.exerciseId, setId) },
                        pendingWarmups = state.pendingWarmups[exIndex].orEmpty(),
                        onLogWarmup = { pending -> vm.logPendingWarmup(exIndex, ex.exerciseId, pending) },
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
                        showExerciseNotes = planNotesVisible,
                        supersetGroup = supersetGroup,
                        onSupersetChange = { group ->
                            vm.persistSuperset(exIndex, ex.exerciseId, group)
                        },
                        isDragging = isDragging,
                        dragHandleModifier = Modifier.draggableHandle(),
                        activeProfile = activeGymProfile,
                        settingsBarWeightKg = settingsBarWeightKg,
                        targetOverride = state.targetOverrides[exIndex],
                        progressionPolicy = progressionPolicy,
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
                Spacer(Modifier.height(appGapDp(12.dp)))
                val totalVolume = state.setLog.values.flatten().filter { it.type != "warmup" }.sumOf { it.weight * it.reps }
                val comparison = getFunComparison(totalVolume)
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                    shape = RoundedCornerShape(IronLogRadius.lg.dp),
                ) {
                    Column(Modifier.appPadding(14.dp)) {
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
                Spacer(Modifier.height(appGapDp(16.dp)))
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
                Spacer(Modifier.height(appGapDp(8.dp)))
            }
        }
    }

    if (showNotesSettings) {
        ExerciseNotesSettingsDialog(
            title = "Workout exercise notes",
            deleteLabel = "DELETE NOTES FROM THIS SESSION",
            deleteWarning = "This removes every exercise-level note from the active workout. The source plan is unchanged.",
            notesVisible = planNotesVisible,
            isDeleting = deletingWorkoutNotes,
            error = notesSettingsError,
            onNotesVisibleChange = { visible ->
                notesSettingsError = null
                scope.launch {
                    runCatching { settingsRepo.setPlanExerciseNotesVisible(visible) }
                        .onFailure { notesSettingsError = it.message ?: "Could not update note visibility." }
                }
            },
            onDeleteConfirmed = {
                if (!deletingWorkoutNotes) {
                    deletingWorkoutNotes = true
                    notesSettingsError = null
                    scope.launch {
                        runCatching { vm.clearExerciseNotesNow() }
                            .onSuccess { showNotesSettings = false }
                            .onFailure { notesSettingsError = it.message ?: "Could not delete the workout notes." }
                        deletingWorkoutNotes = false
                    }
                }
            },
            onDismiss = { if (!deletingWorkoutNotes) showNotesSettings = false },
        )
    }

    if (showCompletionSheet) {
        val elapsedSecondsForCompletion by vm.elapsedSeconds.collectAsStateWithLifecycle()
        WorkoutCompletionSheet(
            totalSets = state.setLog.values.sumOf { it.size },
            totalVolume = state.setLog.values.flatten().filter { it.type != "warmup" }.sumOf { it.weight * it.reps },
            durationSeconds = elapsedSecondsForCompletion,
            weightUnit = weightUnit,
            exerciseNames  = exercises.map { it.name },
            planDayName    = resolvedDay?.name ?: "Free Session",
            goalMode       = appSettings.goalMode,
            intelligenceMode = appSettings.intelligenceMode,
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
                modifier = Modifier.fillMaxSize().appPadding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = c.accent)
                Spacer(Modifier.height(appGapDp(16.dp)))
                Text(
                    "Finalizing workout...",
                    color = c.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = IronLogType.section.fontSize.sp,
                )
                Spacer(Modifier.height(appGapDp(8.dp)))
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
        val trimmedSwapName = swapQuery.trim()
        val filtered = exercisePool.filter {
            val q = trimmedSwapName.lowercase()
            q.isBlank() || it.name.lowercase().contains(q) || it.primaryMuscle.orEmpty().lowercase().contains(q)
        }.take(40)
        val hasExactMatch = exercisePool.any { it.name.equals(trimmedSwapName, ignoreCase = true) }
        val targetLibraryExercise = targetExercise?.let { target ->
            exercisePool.firstOrNull { it.id == target.exerciseId || it.exerciseId == target.exerciseId }
        }
        ModalBottomSheet(
            onDismissRequest = { if (!isCreatingSwapExercise) swapTargetIndex = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.card,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = appSpacedBy(10.dp)) {
                Text("Swap Exercise", color = c.text, fontWeight = FontWeight(IronLogType.title.fontWeight), fontSize = IronLogType.title.fontSize.sp)
                Text(targetExercise?.name ?: "", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                OutlinedTextField(
                    value = swapQuery,
                    onValueChange = { swapQuery = it },
                    label = { Text("Search exercise") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (trimmedSwapName.isNotBlank() && !hasExactMatch) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = c.accent.copy(alpha = 0.10f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.accent.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().clickable(
                            enabled = !isCreatingSwapExercise && state.setLog[targetIndex].orEmpty().isEmpty(),
                        ) {
                            isCreatingSwapExercise = true
                            swapCreateError = null
                            scope.launch {
                                runCatching {
                                    val created = exerciseRepo.createCustomExercise(
                                        CreateExerciseInput(
                                            name = trimmedSwapName,
                                            primaryMuscle = targetLibraryExercise?.primaryMuscle ?: "Other",
                                            equipment = targetLibraryExercise?.equipment ?: targetExercise?.equipment ?: "Other",
                                            category = targetLibraryExercise?.category ?: "strength",
                                            trackingType = targetExercise?.trackingType ?: "weight_reps",
                                            notes = "Created during an active-workout swap.",
                                        ),
                                    )
                                    val refreshed = exerciseRepo.getExercisesSnapshot()
                                    val custom = refreshed.first { it.id == created.uid || it.exerciseId == created.uid }
                                    exercisePool = refreshed
                                    if (targetExercise != null) {
                                        pendingExerciseSwap = PendingExerciseSwap(targetIndex, targetExercise, custom)
                                    }
                                }.onSuccess {
                                    swapTargetIndex = null
                                    swapQuery = ""
                                }.onFailure { error ->
                                    swapCreateError = error.message ?: "Could not create this exercise."
                                }
                                isCreatingSwapExercise = false
                            }
                        },
                    ) {
                        Column(Modifier.fillMaxWidth().appPadding(14.dp), verticalArrangement = appSpacedBy(4.dp)) {
                            Text(
                                if (isCreatingSwapExercise) "CREATING…" else "CREATE ‘$trimmedSwapName’",
                                color = c.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                            Text(
                                if (state.setLog[targetIndex].orEmpty().isNotEmpty())
                                    "Delete this exercise's logged sets before swapping."
                                else "Saved as a custom exercise using this movement's tracking profile.",
                                color = c.subtext,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                swapCreateError?.let { Text(it, color = c.danger, fontSize = 12.sp) }
                LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = appSpacedBy(6.dp)) {
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
                                    pendingExerciseSwap = PendingExerciseSwap(targetIndex, targetExercise, item)
                                }
                                swapTargetIndex = null
                            },
                        ) {
                            Column(Modifier.fillMaxWidth().appPadding(12.dp)) {
                                Text(item.name, color = c.text, fontWeight = FontWeight(IronLogType.section.fontWeight), fontSize = IronLogType.section.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.primaryMuscle ?: "Other"} · ${item.equipment}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingExerciseSwap?.let { pending ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!isApplyingPlanSwap) pendingExerciseSwap = null },
            containerColor = c.card,
            title = { Text("Apply this swap", color = c.text, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = appSpacedBy(8.dp)) {
                    Text("${pending.from.name}  →  ${pending.to.name}", color = c.text)
                    Text("Choose whether this change is only for today or also updates the plan for future sessions.", color = c.subtext)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !isApplyingPlanSwap,
                    onClick = {
                        isApplyingPlanSwap = true
                        scope.launch {
                            runCatching {
                                require(dayId.isNotBlank()) { "This workout is not linked to a plan day." }
                                val planRows = planRepo.getPlanExercisesSnapshot(dayId)
                                val row = planRows.firstOrNull { it.orderIndex == pending.exIndex }
                                    ?: planRows.firstOrNull { it.exerciseUid == pending.from.exerciseId }
                                    ?: error("The original exercise is no longer in this plan day.")
                                planRepo.updatePlanExercise(
                                    row.uid,
                                    com.ironlog.app.data.model.PlanExerciseInput(
                                        exerciseId = pending.to.id.ifBlank { pending.to.exerciseId },
                                    ),
                                )
                            }.onSuccess {
                                vm.swapExercise(pending.exIndex, pending.from.exerciseId, pending.to)
                                pendingExerciseSwap = null
                            }.onFailure { error ->
                                workoutActionError = error.message ?: "The plan was not updated."
                            }
                            isApplyingPlanSwap = false
                        }
                    },
                ) {
                    Text(if (isApplyingPlanSwap) "UPDATING…" else "SESSION + PLAN", color = c.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !isApplyingPlanSwap,
                    onClick = {
                        vm.swapExercise(pending.exIndex, pending.from.exerciseId, pending.to)
                        pendingExerciseSwap = null
                    },
                ) {
                    Text("THIS SESSION", color = c.text, fontWeight = FontWeight.Bold)
                }
            },
        )
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
            Column(Modifier.fillMaxWidth().appPadding(16.dp), verticalArrangement = appSpacedBy(12.dp)) {
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
                    horizontalArrangement = appSpacedBy(8.dp),
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
                    horizontalArrangement = appSpacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MIN", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = minState,
                            flingBehavior = rememberSnapFlingBehavior(minState),
                            modifier = Modifier.height(120.dp),
                            verticalArrangement = appSpacedBy(8.dp),
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
                            verticalArrangement = appSpacedBy(8.dp),
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
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = appSpacedBy(10.dp)) {
                Text("Add Exercise", color = c.text, fontWeight = FontWeight(IronLogType.title.fontWeight), fontSize = IronLogType.title.fontSize.sp)
                Text("${exercises.size} exercises in session", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                OutlinedTextField(
                    value = addExerciseQuery,
                    onValueChange = { addExerciseQuery = it },
                    label = { Text("Search exercise") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().height(400.dp), verticalArrangement = appSpacedBy(6.dp)) {
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
                                vm.addExerciseToWorkout(entry) { showAddExerciseSheet = false }
                            },
                        ) {
                            Column(Modifier.fillMaxWidth().appPadding(12.dp)) {
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
    intelligenceMode: String = "training_intelligence",
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
            verticalArrangement = appSpacedBy(20.dp),
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = appSpacedBy(4.dp)) {
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
            WorkoutCloudDebrief(
                intelligenceMode = intelligenceMode,
                configured = cloudConfigured,
                requestKey = listOf(cloudBaseUrl, cloudApiKey, cloudModelName, cloudApiFormat,
                    planDayName, exerciseNames, goalMode),
                load = {
                    CloudAiEngine.askDayEvaluation(cloudBaseUrl, cloudApiKey, cloudModelName,
                        cloudApiFormat, planDayName, exerciseNames, goalMode)
                },
            )

            // ── Star rating ─────────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = appSpacedBy(8.dp),
            ) {
                Text(
                    "RATE THIS SESSION",
                    color = c.muted,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
                Row(horizontalArrangement = appSpacedBy(16.dp)) {
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
            Spacer(Modifier.height(appGapDp(12.dp)))
        }
    }
}

@Composable
private fun CompletionStat(label: String, value: String) {
    val c = useTheme()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = appSpacedBy(4.dp)) {
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
    listState: LazyListState,
    inputViewport: WorkoutInputViewport,
    inputStructuralKey: Any,
    preserveInputAnchor: Boolean,
    dispatch: (WorkoutAction) -> Unit,
    baseExercisesCount: Int = 0,
    onRemoveExercise: (() -> Unit)? = null,
    onLogSet: (String, String) -> Unit,
    weightUnit: String,
    effortTracking: String,
    hapticFeedback: Boolean,
    onSetRpeChanged: (setId: String, rpe: Double?) -> Unit,
    onSetRirChanged: (setId: String, rir: Int?) -> Unit,
    onSetTypeChanged: (setId: String, type: String) -> Unit,
    onSetValuesChanged: (setId: String, weight: Double?, reps: Double?) -> Unit,
    onSetNoteChanged: (setId: String, note: String?) -> Unit,
    onDeleteSet: (setId: String) -> Unit,
    pendingWarmups: List<PendingWarmup>,
    onLogWarmup: (PendingWarmup) -> Unit,
    onSwapRequest: () -> Unit,
    restSec: Int = 90,
    onEditRest: () -> Unit = {},
    ghost: com.ironlog.app.ui.state.GhostData? = null,
    exerciseNote: String = "",
    showExerciseNotes: Boolean = true,
    supersetGroup: String? = null,
    onSupersetChange: (String?) -> Unit = {},
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    activeProfile: GymProfileDto? = null,
    settingsBarWeightKg: Double = 20.0,
    targetOverride: com.ironlog.app.ui.state.TargetOverride? = null,  // GAP-23
    progressionPolicy: ResolvedProgressionPolicy = ResolvedProgressionPolicy.conservativeDefault(),
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
    var showProgressionDetails by remember { mutableStateOf(false) }
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
        val w = convertUnitToKg(weight.toDoubleOrNull() ?: 0.0, weightUnit)
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
            Spacer(Modifier.width(appGapDp(6.dp)))
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDragging) c.accent.copy(alpha = 0.5f) else c.cardBorder),
            shape = RoundedCornerShape(IronLogRadius.lg.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = appSpacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.DragHandle,
                    contentDescription = "Drag",
                    tint = c.muted,
                    modifier = dragHandleModifier.size(20.dp).padding(end = 0.dp),
                )
                Spacer(Modifier.width(appGapDp(8.dp)))
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = appSpacedBy(4.dp)) {
                            Text(
                                "${targetOverride.sets} × ${targetOverride.reps} · ${formatTrackingType(exercise.trackingType)}",
                                color = c.warning,
                                fontSize = IronLogType.meta.fontSize.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Box(
                                Modifier.clip(RoundedCornerShape(IronLogRadius.full.dp)).background(c.warning.copy(alpha = 0.15f)).appPadding(horizontal = 4.dp, vertical = 1.dp)
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
                                .appPadding(top = 4.dp)
                                .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                .background(supColor.copy(alpha = 0.2f))
                                .border(1.dp, supColor, RoundedCornerShape(IronLogRadius.full.dp))
                                .appPadding(horizontal = 8.dp, vertical = 3.dp),
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
                    IronLogDropdownMenu(showExerciseMenu, onDismissRequest = { showExerciseMenu = false }) {
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
                        when (action) {
                            is WorkoutAction.DeleteSet -> onDeleteSet(set.id)
                            is WorkoutAction.SetRpe -> onSetRpeChanged(set.id, action.rpe)
                            is WorkoutAction.SetRir -> onSetRirChanged(set.id, action.rir)
                            is WorkoutAction.SetType -> onSetTypeChanged(set.id, action.type)
                            is WorkoutAction.SetNote -> onSetNoteChanged(set.id, action.note)
                            is WorkoutAction.UpdateSet -> onSetValuesChanged(set.id, action.weight, action.reps)
                            else -> dispatch(action)
                        }
                    },
                    effortTracking = effortTracking,
                    hapticFeedback = hapticFeedback,
                    weightUnit = weightUnit,
                    trackingType = exercise.trackingType,
                )
            }
            val supportsWarmups = exercise.trackingType == "weight_reps" && !exercise.isBodyweight
            if (supportsWarmups) {
                val hasWarmups = loggedSets.any { it.type == "warmup" }
                val displayTarget = weight.toDoubleOrNull()
                val topWorkingKg = displayTarget?.let { convertUnitToKg(it, weightUnit) }
                    ?: loggedSets.lastOrNull { it.type != "warmup" }?.weight
                    ?: 0.0
                val generatedWarmups = remember(topWorkingKg, activeProfile?.barWeightKg, settingsBarWeightKg) {
                    buildWarmupSets(topWorkingKg, activeProfile?.barWeightKg ?: settingsBarWeightKg)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = appSpacedBy(4.dp),
                    modifier = Modifier
                        .clickable(enabled = !hasWarmups && pendingWarmups.isEmpty() && generatedWarmups.isNotEmpty()) {
                            dispatch(
                                WorkoutAction.QueueWarmups(
                                    exIndex,
                                    generatedWarmups.map { PendingWarmup(id = it.id, weightKg = it.weight, reps = it.reps.roundToInt()) },
                                ),
                            )
                        }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 8.dp),
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Warmup options", tint = if (generatedWarmups.isNotEmpty()) c.accent else c.subtext, modifier = Modifier.size(20.dp))
                    Text(
                        when {
                            hasWarmups -> "Warmups logged"
                            pendingWarmups.isNotEmpty() -> "Warmups ready to log"
                            generatedWarmups.isEmpty() && topWorkingKg <= 0.0 -> "Enter a working weight for warmups"
                            generatedWarmups.isEmpty() -> "No warmups needed for this load"
                            else -> "+ Queue warmups"
                        },
                        color = if (generatedWarmups.isNotEmpty() && !hasWarmups) c.accent else c.subtext,
                        fontSize = 12.sp,
                    )
                }
                pendingWarmups.firstOrNull()?.let { pending ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.info.copy(alpha = 0.10f), RoundedCornerShape(IronLogRadius.md.dp))
                            .border(1.dp, c.info.copy(alpha = 0.35f), RoundedCornerShape(IronLogRadius.md.dp))
                            .appPadding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = appSpacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("NEXT WARMUP", color = c.info, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${formatWeightFromKg(pending.weightKg, weightUnit)} × ${pending.reps}", color = c.text, fontWeight = FontWeight.Bold)
                        }
                        androidx.compose.material3.TextButton(onClick = { dispatch(WorkoutAction.SkipPendingWarmup(exIndex, pending.id)) }) {
                            Text("SKIP", color = c.muted)
                        }
                        Button(onClick = { onLogWarmup(pending) }, colors = ButtonDefaults.buttonColors(containerColor = c.info)) {
                            Text("LOG WARMUP", color = c.bg, fontWeight = FontWeight.Bold)
                        }
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { dispatch(WorkoutAction.DismissPendingWarmups(exIndex)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("DISMISS WARMUP QUEUE", color = c.subtext, fontSize = 12.sp)
                    }
                }
            } else if (!exercise.trackingType.startsWith("duration")) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = appSpacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = c.subtext, modifier = Modifier.size(20.dp))
                    Text("Warmup loading is not used for bodyweight movements", color = c.subtext, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = appSpacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    weight,
                    { weight = it; dispatch(WorkoutAction.SetInput(exIndex, weight = it)) },
                    label = { Text(if (exercise.isBodyweight) "ADDED ${weightUnit.uppercase()}" else weightUnit.uppercase()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                        .preserveWorkoutInputAnchor(listState, inputStructuralKey, inputViewport, preserveInputAnchor),
                )
                OutlinedTextField(
                    reps,
                    { reps = it; dispatch(WorkoutAction.SetInput(exIndex, reps = it)) },
                    label = { Text(if (exercise.trackingType.startsWith("duration")) "SECONDS" else "REPS") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                        .preserveWorkoutInputAnchor(listState, inputStructuralKey, inputViewport, preserveInputAnchor),
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
                        plateTarget = convertUnitToKg(weight.toDoubleOrNull() ?: 0.0, weightUnit)
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
                        horizontalArrangement = appSpacedBy(6.dp),
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
                                if (s.weight > 0) "${formatWeightFromKg(s.weight, weightUnit)} × ${s.reps.toInt()}"
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
                val progressionStep = if (supportsPlateBreakdown(exercise)) {
                    com.ironlog.app.domain.intelligence.progressionBarbellStep(
                        ghost.sets.firstOrNull { !it.type.equals("warmup", true) }?.weight ?: 0.0,
                        activeProfile?.barWeightKg ?: settingsBarWeightKg,
                        activeProfile?.plates ?: DEFAULT_PLATES,
                    )
                } else null
                val suggestion = buildProgressionSuggestion(ghost, weightUnit, exercise.trackingType,
                    targetOverride?.sets ?: exercise.sets, targetOverride?.reps ?: exercise.reps, progressionStep,
                    progressionPolicy)
                if (suggestion != null) {
                    Text(
                        suggestion,
                        color = c.subtext,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.appPadding(top = 2.dp),
                    )
                    Text(
                        "${progressionPolicy.label} · ${progressionPolicy.source.label}",
                        color = c.muted,
                        fontSize = 12.sp,
                    )
                    androidx.compose.material3.TextButton(onClick = { showProgressionDetails = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Why this suggestion?", fontSize = 12.sp)
                    }
                    if (showProgressionDetails) {
                        val advice = com.ironlog.app.domain.intelligence.ProgressionRecommendationEngine.recommend(
                            ghost.sets, exercise.trackingType, targetOverride?.sets ?: exercise.sets,
                            targetOverride?.reps ?: exercise.reps,
                            loadStepKg = progressionStep ?: if (weightUnit.lowercase().startsWith("lb")) 5.0 / 2.20462262185 else 2.5,
                            sourceDate = ghost.date,
                            policy = progressionPolicy,
                        )
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showProgressionDetails = false }, containerColor = c.card,
                            title = { Text("Progression evidence") },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = appSpacedBy(12.dp)) {
                                    Text(advice?.reason.orEmpty())
                                    Text("Evidence session: ${ghost.date?.let { com.ironlog.app.domain.gamification.parseHistoryLocalDate(it)?.toString() } ?: "last recorded session; date unavailable"}")
                                    Text("Effort recorded: ${advice?.effortRecordedSets ?: 0} of ${advice?.workingSets ?: 0} working sets.")
                                    if (progressionStep != null) Text(if (progressionStep > 0) "Smallest plate-pair increase: ${formatWeightFromKg(progressionStep, weightUnit)}." else "The current plate inventory cannot load a small increase; use rep progression or review the inventory.")
                                    else Text("Suggested increment assumes standard equipment. Check the available load before using it.")
                                    Text("Policy: ${progressionPolicy.label}.")
                                    Text("Policy source: ${progressionPolicy.source.label}. ${progressionPolicy.source.description}")
                                    val effortMargin = progressionPolicy.minimumEffortMargin.let { value ->
                                        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
                                    }
                                    val loadCapPercent = (progressionPolicy.maximumLoadIncreaseRatio * 100).let { value ->
                                        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
                                    }
                                    Text("Guardrails: at least $effortMargin reps in reserve (or RPE equivalent) across all working sets; one load increase is capped at $loadCapPercent%.")
                                    Text(advice?.provenance.orEmpty(), fontSize = 12.sp, color = c.subtext)
                                }
                            },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = { showProgressionDetails = false }) { Text("Done") } },
                        )
                    }
                }
            }
            if (showExerciseNotes) {
                ghost?.previousNote?.takeIf { it.isNotBlank() }?.let { previousNote ->
                    Text("Previous session note", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    Text(previousNote, color = c.text, fontSize = IronLogType.meta.fontSize.sp)
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
                        horizontalArrangement = appSpacedBy(4.dp),
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
                NextSessionNoteControl(exercise.exerciseId, localNote) {
                    localNote = it
                    dispatch(WorkoutAction.SetExerciseNote(exIndex, it))
                    noteExpanded = true
                }
            }
            // Rest timer display — tappable to override
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = appSpacedBy(4.dp),
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
                Column(verticalArrangement = appSpacedBy(12.dp)) {
                    Text("Override sets × reps for this session only.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                    Row(horizontalArrangement = appSpacedBy(12.dp)) {
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
        Column(Modifier.appPadding(horizontal = 20.dp).appPadding(bottom = 24.dp), verticalArrangement = appSpacedBy(16.dp)) {
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
        Column(Modifier.appPadding(20.dp), verticalArrangement = appSpacedBy(12.dp)) {
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
            Spacer(Modifier.height(appGapDp(12.dp)))
        }
    }
}

@Composable
private fun RestTimerPanel(
    restTimer: com.ironlog.app.ui.state.RestTimerState,
    modifier: Modifier,
    dispatch: (WorkoutAction) -> Unit,
    controlsEnabled: Boolean,
    hapticFeedback: Boolean,
) {
    val c = useTheme()
    val context = LocalContext.current
    if (!restTimer.active || restTimer.endTime == null) return
    val currentBootCount = remember { WorkoutTimerClock.now(context).bootCount }
    fun remainingMs(): Long {
        if (restTimer.paused) return restTimer.pausedRemainingMs?.coerceAtLeast(0L) ?: 0L
        return com.ironlog.app.services.WorkoutTimerDeadline(
            wallEndMs = restTimer.endTime,
            elapsedEndMs = restTimer.endElapsedTime ?: 0L,
            bootCount = restTimer.bootCount,
        ).remainingMs(WorkoutClockSnapshot(
            wallTimeMs = System.currentTimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            bootCount = currentBootCount,
        )).coerceAtLeast(0L)
    }
    var remaining by remember(
        restTimer.endTime,
        restTimer.endElapsedTime,
        restTimer.bootCount,
        restTimer.paused,
        restTimer.pausedRemainingMs,
    ) {
        mutableStateOf(((remainingMs() + 999L) / 1_000L).toInt())
    }
    LaunchedEffect(
        restTimer.endTime,
        restTimer.endElapsedTime,
        restTimer.bootCount,
        restTimer.paused,
        restTimer.pausedRemainingMs,
    ) {
        while (restTimer.active && !restTimer.paused) {
            remaining = ((remainingMs() + 999L) / 1_000L).toInt()
            if (remaining <= 0) {
                // Do not cancel or rewrite the deadline here. The service posts exactly one
                // completion alert, then compare-and-clears the persisted deadline.
                dispatch(WorkoutAction.RestExpired)
                break
            }
            delay(500)
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(IronLogRadius.lg.dp))
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .appPadding(14.dp),
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
        Row(horizontalArrangement = appSpacedBy(8.dp)) {
            Text(
                "+30s",
                color = if (controlsEnabled) c.text else c.muted,
                modifier = Modifier.clickable(enabled = controlsEnabled) {
                    if (hapticFeedback) HapticsEngine.selection(context)
                    dispatch(WorkoutAction.Add30s)
                }.padding(8.dp),
            )
            Text(
                if (restTimer.paused) "RESUME" else "PAUSE",
                color = if (controlsEnabled) c.text else c.muted,
                modifier = Modifier.clickable(enabled = controlsEnabled) {
                    if (hapticFeedback) HapticsEngine.lightConfirm(context)
                    if (restTimer.paused) {
                        val now = WorkoutClockSnapshot(
                            wallTimeMs = System.currentTimeMillis(),
                            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                            bootCount = currentBootCount,
                        )
                        val deadline = WorkoutTimerClock.deadlineAfter(
                            now,
                            restTimer.pausedRemainingMs?.coerceAtLeast(0L) ?: 0L,
                        )
                        dispatch(WorkoutAction.ResumeRest(
                            newEndTime = deadline.wallEndMs,
                            newEndElapsedTime = deadline.elapsedEndMs,
                            bootCount = deadline.bootCount,
                        ))
                    } else {
                        dispatch(WorkoutAction.PauseRest(
                            pausedAt = System.currentTimeMillis(),
                            remainingMs = remainingMs(),
                        ))
                    }
                }.appPadding(8.dp),
            )
            Text(
                "SKIP",
                color = if (controlsEnabled) c.accent else c.muted,
                fontWeight = FontWeight(IronLogType.button.fontWeight),
                modifier = Modifier.clickable(enabled = controlsEnabled) {
                    if (hapticFeedback) HapticsEngine.mediumStrong(context)
                    dispatch(WorkoutAction.SkipRest)
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
    Row(horizontalArrangement = appSpacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = appSpacedBy(16.dp),
        ) {
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
                    "Available plates reach ${formatWeightFromKg(result.achievedWeightKg, weightUnit)} · ${formatWeightFromKg(result.remainderKg, weightUnit)} short of the requested load.",
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
                    Column(Modifier.fillMaxWidth().appPadding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = appSpacedBy(4.dp)) {
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
                Column(Modifier.fillMaxWidth().appPadding(16.dp), verticalArrangement = appSpacedBy(12.dp)) {
                    Text("BAR (${formatWeightFromKg(barWeight, weightUnit)})", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = IronLogType.eyebrow.letterSpacing.sp)

                    if (result.platesPerSide.isEmpty()) {
                        Text("Bar only", color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold)
                    } else {
                        result.platesPerSide.forEach { p ->
                            val assignedHex = inventory.firstOrNull { kotlin.math.abs(it.weightKg - p.weightKg) < 0.001 }?.color.orEmpty()
                            val plateColor = plateFillColor(p.weightKg, assignedHex)
                            Column(Modifier.fillMaxWidth().appPadding(vertical = 4.dp), verticalArrangement = appSpacedBy(6.dp)) {
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
                                        .border(1.dp, c.text.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
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
            Spacer(Modifier.height(appGapDp(40.dp)))
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
    val isBodyweight = lib?.let(::isBodyweightExercise) == true
    val rawTrackingType = lib?.trackingType ?: "weight_reps"
    val trackingType = resolveSessionTrackingType(rawTrackingType, isBodyweight)
    return NormalizedSessionExercise(
        name = lib?.name ?: planEx.name.ifBlank { "Custom Exercise" },
        exerciseId = planEx.exerciseId.ifBlank { lib?.id ?: planEx.name },
        sets = planEx.sets.takeIf { it > 0 } ?: 3,
        reps = parseRepTarget(planEx.reps, if (trackingType.startsWith("duration")) 60 else 8),
        trackingType = trackingType,
        isWarmup = planEx.isWarmup,
        equipment = lib?.equipment,
        isBodyweight = isBodyweight,
    )
}

internal fun resolveSessionTrackingType(rawTrackingType: String, isBodyweight: Boolean): String =
    if (isBodyweight && rawTrackingType == "weight_reps") "bodyweight_plus_weight_reps" else rawTrackingType

@Serializable
private data class WorkoutDraftDto(
    val version: Int = 3,
    val inputs: Map<String, WorkoutInputDto> = emptyMap(),
    val setLog: Map<String, List<LoggedSetDto>> = emptyMap(),
    val exerciseNotes: Map<String, String> = emptyMap(),
    val supersetGroups: Map<String, String?> = emptyMap(),
    val restTimer: RestTimerDto = RestTimerDto(),
    val addedExercises: List<AddedExerciseDto> = emptyList(),
    val removedBaseExerciseIndices: List<Int> = emptyList(),
    val targetOverrides: Map<String, TargetOverrideDto> = emptyMap(),
    val pendingWarmups: Map<String, List<PendingWarmupDto>> = emptyMap(),
    val orderedIndices: List<Int> = emptyList(),
    /** Persists mid-workout exercise swaps so they survive minimize/resume. Key = exIndex (String). */
    val swappedExercises: Map<String, SwappedExerciseDto> = emptyMap(),
)

@Serializable
private data class TargetOverrideDto(val sets: Int = 0, val reps: Int = 0)

@Serializable
private data class PendingWarmupDto(val id: String = "", val weightKg: Double = 0.0, val reps: Int = 0)

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
    val endElapsedTime: Long? = null,
    val bootCount: Int = -1,
    val total: Int = 0,
    val paused: Boolean = false,
    val pausedAt: Long? = null,
    val pausedRemainingMs: Long? = null,
    val triggerExIndex: Int? = null,
)

/**
 * Builds a short progression suggestion string based on the last session's ghost data.
 * Returns null if there's not enough data to make a suggestion.
 *
 * The engine checks completed targets, tracking type and effort; weights remain in kg.
 */
internal fun buildProgressionSuggestion(
    ghost: com.ironlog.app.ui.state.GhostData,
    weightUnit: String,
    trackingType: String = "weight_reps",
    targetSets: Int = 0,
    targetReps: Int = 0,
    availableLoadStepKg: Double? = null,
    policy: ResolvedProgressionPolicy = ResolvedProgressionPolicy.conservativeDefault(),
): String? {
    val isLb = weightUnit.lowercase().trimEnd('s') == "lb"
    val advice = com.ironlog.app.domain.intelligence.ProgressionRecommendationEngine.recommend(
        ghost.sets, trackingType, targetSets, targetReps,
        loadStepKg = availableLoadStepKg ?: if (isLb) 5.0 / 2.20462262185 else 2.5,
        sourceDate = ghost.date,
        policy = policy,
    ) ?: return null
    return when (advice.action) {
        com.ironlog.app.domain.intelligence.ProgressionAction.HOLD -> advice.reason
        com.ironlog.app.domain.intelligence.ProgressionAction.ADD_LOAD ->
            "Next option: ${formatWeightFromKg(advice.weightKg, if (isLb) "lbs" else "kg")} × ${advice.reps}. Keep technique and effort in reserve."
        com.ironlog.app.domain.intelligence.ProgressionAction.ADD_REPS ->
            "Next option: ${advice.reps} reps at the same load, if technique stays solid."
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
        weightKg >= 2.5  -> Color(0xFF8E44EC)   // violet: visible on every IronLog surface
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
        val barH = 3.dp.toPx()
        val sleeveW = 28.dp.toPx()
        val sleeveH = 9.dp.toPx()
        val collarW = 4.dp.toPx()
        val collarH = 15.dp.toPx()
        val availablePerSide = (size.width / 2f - sleeveW / 2f - collarW - 6.dp.toPx()).coerceAtLeast(1f)
        val plateW = (availablePerSide / flatPlates.size.coerceAtLeast(1)).coerceIn(7.dp.toPx(), 13.dp.toPx())
        val outlineWidth = 1.dp.toPx()

        // Bar (full width)
        drawRect(barColor, topLeft = Offset(0f, cy - barH / 2f), size = androidx.compose.ui.geometry.Size(size.width, barH))

        // Sleeve (center, thicker)
        drawRect(sleeveColor, topLeft = Offset(cx - sleeveW / 2f, cy - sleeveH / 2f), size = androidx.compose.ui.geometry.Size(sleeveW, sleeveH))

        // Plates — right side (stack outward from sleeve)
        var rightX = cx + sleeveW / 2f + collarW
        flatPlates.forEach { (wkg, col) ->
            val pH = (size.height * (0.36f + (wkg / 25.0).toFloat() * 0.54f)).coerceIn(28.dp.toPx(), size.height * 0.92f)
            drawRect(col, topLeft = Offset(rightX, cy - pH / 2f), size = androidx.compose.ui.geometry.Size(plateW - outlineWidth, pH))
            drawRect(c.text.copy(alpha = 0.55f), topLeft = Offset(rightX, cy - pH / 2f), size = androidx.compose.ui.geometry.Size(plateW - outlineWidth, pH), style = Stroke(outlineWidth))
            rightX += plateW
        }

        // Plates — left side (mirror; heaviest still closest to sleeve)
        var leftX = cx - sleeveW / 2f - collarW
        flatPlates.forEach { (wkg, col) ->
            val pH = (size.height * (0.36f + (wkg / 25.0).toFloat() * 0.54f)).coerceIn(28.dp.toPx(), size.height * 0.92f)
            leftX -= plateW
            drawRect(col, topLeft = Offset(leftX + outlineWidth, cy - pH / 2f), size = androidx.compose.ui.geometry.Size(plateW - outlineWidth, pH))
            drawRect(c.text.copy(alpha = 0.55f), topLeft = Offset(leftX + outlineWidth, cy - pH / 2f), size = androidx.compose.ui.geometry.Size(plateW - outlineWidth, pH), style = Stroke(outlineWidth))
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
    val elapsedSeconds by vm.elapsedSeconds.collectAsStateWithLifecycle()
    val timerStarted by vm.timerStarted.collectAsStateWithLifecycle()
    RollingTimerText(
        value = if (timerStarted) formatDurationShort(elapsedSeconds) else "--:--",
        color = c.accent,
        fontWeight = FontWeight.Bold,
        fontSizeSp = IronLogType.body.fontSize,
    )
}
