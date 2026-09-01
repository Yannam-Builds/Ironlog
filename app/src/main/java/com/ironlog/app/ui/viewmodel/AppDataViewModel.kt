package com.ironlog.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.PlanDayInput
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.PlanRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.PR_RESET_AT_KEY
import com.ironlog.app.domain.intelligence.TrainingDayPreferences
import com.ironlog.app.domain.intelligence.canonicalIntelligenceMode
import com.ironlog.app.ui.model.AppDataState
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.IronLogSettings
import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.model.StatsUiState
import com.ironlog.app.domain.training.PersonalBestPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class HandledRefreshTask(
    val job: Job,
    val completion: CompletableDeferred<Unit>,
)

/**
 * A ViewModel-scope launch normally reports an uncaught exception even if another observer mirrors
 * its completion. Catch the root failure here, then expose it only through the awaited completion.
 */
internal fun CoroutineScope.launchHandledRefreshTask(
    onFailure: (Throwable) -> Unit = {},
    block: suspend () -> Unit,
): HandledRefreshTask {
    val completion = CompletableDeferred<Unit>()
    val job = launch {
        try {
            block()
            completion.complete(Unit)
        } catch (cancelled: CancellationException) {
            completion.cancel(cancelled)
            throw cancelled
        } catch (error: Throwable) {
            runCatching { onFailure(error) }
            completion.completeExceptionally(error)
        }
    }
    job.invokeOnCompletion { cause ->
        if (!completion.isCompleted) {
            when (cause) {
                null -> completion.complete(Unit)
                is CancellationException -> completion.cancel(cause)
                else -> completion.completeExceptionally(cause)
            }
        }
    }
    return HandledRefreshTask(job, completion)
}

/** Aggregates plans, history, settings, and personal bests for the entire app. */
class AppDataViewModel(
    plansVm: PlansViewModel? = null,
    statsVm: StatsViewModel? = null,
    private val planRepo: PlanRepository = PlanRepository(),
    private val workoutRepo: WorkoutRepository = WorkoutRepository(),
    private val exerciseRepo: ExerciseRepository = ExerciseRepository(),
    private val settingsRepo: SettingsRepository = SettingsRepository(),
    planSource: PlanUiSource? = null,
    statsSource: StatsUiSource? = null,
) : ViewModel() {
    // Optional injected VMs are borrowed, never privately constructed or cleared here.
    private val planSource = planSource ?: if (plansVm == null) {
        RepositoryPlanUiSource(planRepo, exerciseRepo)
    } else object : PlanUiSource {
        override fun observe() = combine(plansVm.plans, plansVm.loading) { plans, loading ->
            plans.takeUnless { loading }
        }.filterNotNull()
        override suspend fun snapshot() = observe().first()
    }
    private val statsSource = statsSource ?: if (statsVm == null) {
        RepositoryStatsUiSource(settingsRepo)
    } else object : StatsUiSource {
        override fun observe() = statsVm.state
        override suspend fun snapshot() = statsVm.state.value
    }
    private val _state = MutableStateFlow(AppDataState())
    val state: StateFlow<AppDataState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var manualPb: Map<String, Double> = emptyMap()
    private var exerciseNotes: Map<String, String> = emptyMap()
    /** In-flight write guard: prevents concurrent refresh() calls from reading a stale DB value
     *  and overwriting the optimistic UI state set in updateSettingsAsync. */
    @Volatile private var settingsWriteCache: IronLogSettings? = null
    private val settingsMutationLock = Any()
    private val settingsMutationVersion = AtomicLong(0L)
    private val settingsMutations = Channel<SettingsMutationRequest>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (request in settingsMutations) {
                try {
                    settingsRepo.setString("ironlog_settings", request.settings.toJson().toString(), "json")
                    runCatching { flagDirty("settings_changed") }
                        .onFailure { Timber.w(it, "Settings saved but backup dirty marker failed") }
                    synchronized(settingsMutationLock) {
                        if (settingsMutationVersion.get() == request.version) {
                            settingsWriteCache = null
                        }
                    }
                    request.completion.complete(request.settings)
                } catch (cancelled: CancellationException) {
                    request.completion.cancel(cancelled)
                    throw cancelled
                } catch (error: Throwable) {
                    synchronized(settingsMutationLock) {
                        if (settingsMutationVersion.get() == request.version) {
                            settingsWriteCache = null
                            refresh()
                        }
                    }
                    request.completion.completeExceptionally(error)
                }
            }
        }
        viewModelScope.launch {
            combine(
                this@AppDataViewModel.planSource.observe(),
                this@AppDataViewModel.statsSource.observe(),
                settingsRepo.observeStrings(setOf(
                    "ironlog_settings", "ironlog_pb", PR_RESET_AT_KEY, EXERCISE_NOTES_KEY,
                    ONBOARDING_KEY, ONBOARDING_MIGRATION_KEY,
                )),
            ) { plans, stats, _ ->
                plans to stats
            }.collect { snapshots ->
                refreshFrom { snapshots }
            }
        }
    }

    fun refresh(plansOverride: List<UiPlan>? = null): Job = refreshFrom {
        (plansOverride ?: planSource.snapshot()) to statsSource.snapshot()
    }

    /** Await a fresh authoritative snapshot and propagate repository failures to the caller. */
    suspend fun refreshAwaited(plansOverride: List<UiPlan>? = null) {
        startRefresh {
            (plansOverride ?: planSource.snapshot()) to statsSource.snapshot()
        }.completion.await()
    }

    private fun refreshFrom(snapshot: suspend () -> Pair<List<UiPlan>, StatsUiState>): Job =
        startRefresh(snapshot).job

    private fun startRefresh(snapshot: suspend () -> Pair<List<UiPlan>, StatsUiState>): HandledRefreshTask {
        refreshJob?.cancel()
        val task = viewModelScope.launchHandledRefreshTask(
            onFailure = { Timber.e(it, "Could not refresh authoritative app state") },
        ) {
            performRefresh(snapshot)
        }
        refreshJob = task.job
        return task
    }

    private suspend fun performRefresh(snapshot: suspend () -> Pair<List<UiPlan>, StatsUiState>) {
        val (plans, stats) = snapshot()
        val exercises = exerciseRepo.getExercisesSnapshot()
        val settings = readIronLogSettings()
        val storedOnboarding = settingsRepo.getBoolean(ONBOARDING_KEY, false)
        val onboardingMigrationDone = settingsRepo.getBoolean(ONBOARDING_MIGRATION_KEY, false)
        val onboarding = if (!onboardingMigrationDone && !storedOnboarding && plans.isNotEmpty()) {
            // One-time compatibility for installations created before the explicit
            // onboarding marker. Never repeat this inference: users must be able to
            // intentionally restart onboarding while keeping their plans.
            settingsRepo.setBoolean(ONBOARDING_KEY, true)
            settingsRepo.setBoolean(ONBOARDING_MIGRATION_KEY, true)
            true
        } else {
            storedOnboarding
        }
        manualPb = readDoubleMap(settingsRepo.getString("ironlog_pb"))
        exerciseNotes = readStringMap(settingsRepo.getString(EXERCISE_NOTES_KEY))
        // Cold sources emit real loaded snapshots, including a legitimately empty database.
        synchronized(settingsMutationLock) {
            _state.value = AppDataState(
                initialized = true,
                plansLoaded = true,
                plans = plans,
                history = stats.history,
                pb = stats.pb + manualPb,
                prResetAtEpochMs = stats.prResetAtEpochMs,
                exerciseNotes = exerciseNotes,
                settings = settingsWriteCache ?: settings,
                onboardingComplete = onboarding,
                exerciseIndex = exercises,
            )
        }
    }

    suspend fun updateSettings(updates: IronLogSettings): IronLogSettings =
        mutateSettings { updates }

    suspend fun mutateSettings(transform: (IronLogSettings) -> IronLogSettings): IronLogSettings {
        val request = acceptSettingsMutation(transform)
        return request.completion.await()
    }

    fun updateSettingsAsync(updates: IronLogSettings): Job = mutateSettingsAsync { updates }

    /**
     * Accepts a field mutation synchronously so rapid controls all build on the latest optimistic
     * snapshot. A single FIFO writer then commits complete snapshots in user-event order.
     */
    fun mutateSettingsAsync(transform: (IronLogSettings) -> IronLogSettings): Job {
        val request = acceptSettingsMutation(transform)
        return viewModelScope.launch {
            try {
                request.completion.await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Timber.e(error, "Could not persist IronLog settings mutation")
            }
        }
    }

    private fun acceptSettingsMutation(
        transform: (IronLogSettings) -> IronLogSettings,
    ): SettingsMutationRequest = synchronized(settingsMutationLock) {
        val current = _state.value.settings
        val transformed = transform(current)
        val normalizedWeeklyGoal = normalizeWeeklyGoalDays(
            transformed.weeklyGoalDays,
            current.weeklyGoalDays,
        )
        val next = transformed.copy(
            weeklyGoalDays = normalizedWeeklyGoal,
            trainingDayIndices = TrainingDayPreferences.reconcileToCount(
                transformed.trainingDayIndices,
                normalizedWeeklyGoal,
            ),
            intelligenceMode = canonicalIntelligenceMode(transformed.intelligenceMode),
        )
        val request = SettingsMutationRequest(
            version = settingsMutationVersion.incrementAndGet(),
            settings = next,
        )
        settingsWriteCache = next
        _state.value = _state.value.copy(settings = next)
        check(settingsMutations.trySend(request).isSuccess) { "Settings writer is unavailable" }
        request
    }

    fun addHistory(entry: HistoryEntry) = viewModelScope.launch {
        workoutRepo.createCompletedWorkout(
            CreateCompletedWorkoutInput(
                name = entry.dayName ?: entry.name,
                startedAt = com.ironlog.app.domain.gamification.parseHistoryInstant(entry.date)?.toEpochMilli()
                    ?: System.currentTimeMillis(),
                durationSeconds = entry.duration,
                rating = entry.rating,
                notes = entry.summaryText.orEmpty(),
                exerciseData = entry.exercises.map { ex ->
                    com.ironlog.app.data.model.CompletedExerciseInput(
                        exerciseId = ex.exerciseId,
                        name = ex.name,
                        primaryMuscles = ex.primaryMuscles,
                        primaryMuscle = ex.primaryMuscle,
                        equipment = ex.equipment,
                        category = ex.category,
                        secondaryMuscles = ex.secondaryMuscles,
                        muscleContributions = ex.muscleContributions,
                        trackingType = ex.trackingType,
                        isBodyweight = ex.isBodyweight,
                        requiresExternalLoad = ex.requiresExternalLoad,
                        supersetGroup = ex.supersetGroup,
                        note = ex.note,
                        sets = ex.sets.map { set ->
                            com.ironlog.app.data.model.SetInput(
                                weight = set.weight,
                                reps = set.reps,
                                type = set.type,
                                rpe = set.rpe,
                                rir = set.rir?.toDouble(),
                                restSeconds = set.restSeconds,
                                isWarmup = set.type == "warmup",
                                isDropset = set.type == "drop",
                                isAmrap = set.type == "amrap",
                                toFailure = set.type == "failure",
                            )
                        },
                    )
                },
            ),
        )
        buildIndexUpdatesForSession(entry)
        flagDirty("workout_completion")
        refresh()
    }

    fun updatePb(key: String, value: Double) = viewModelScope.launch {
        manualPb = manualPb + (key to value)
        settingsRepo.setString("ironlog_pb", JSONObject(manualPb).toString(), "json")
        flagDirty("pb_changed")
        refresh()
    }

    suspend fun clearPbsNow() {
        settingsRepo.resetPersonalBests()
        manualPb = emptyMap()
        runCatching { flagDirty("pb_cleared") }
            .onFailure { Timber.w(it, "PR baseline reset succeeded but backup dirty marker failed") }
        refresh().join()
    }

    /** FIXED: 34 — Clear all completed workout history */
    suspend fun clearAllHistoryNow() {
        workoutRepo.clearCompletedWorkouts()
        runCatching { flagDirty("history_cleared") }
            .onFailure { Timber.w(it, "History clear succeeded but backup dirty marker failed") }
        refresh().join()
    }

    fun saveExerciseNotes(exName: String, note: String) = viewModelScope.launch {
        val key = exName.trim()
        if (key.isBlank()) return@launch
        exerciseNotes = exerciseNotes + (key to note)
        settingsRepo.setString(EXERCISE_NOTES_KEY, JSONObject(exerciseNotes).toString(), "json")
        flagDirty("notes_changed")
        refresh()
    }

    fun completeOnboarding(weeklyGoalDays: Int? = null) = viewModelScope.launch {
        weeklyGoalDays?.let { requested -> mutateSettings { it.copy(weeklyGoalDays = requested) } }
        settingsRepo.setBoolean(ONBOARDING_MIGRATION_KEY, true)
        settingsRepo.setBoolean(ONBOARDING_KEY, true)
        flagDirty("onboarding_changed")
        refresh()
    }

    fun resetOnboarding() = viewModelScope.launch {
        settingsRepo.setBoolean(ONBOARDING_MIGRATION_KEY, true)
        settingsRepo.setBoolean(ONBOARDING_KEY, false)
        flagDirty("onboarding_changed")
        refresh()
    }

    fun updatePlanDay(planId: String, dayId: String, patch: UiPlan) = viewModelScope.launch {
        // Source hook rebuilt full plans and persisted them. Prefer repository-level update when possible.
        val plan = _state.value.plans.firstOrNull { it.id == planId } ?: return@launch
        val day = plan.days.firstOrNull { it.id == dayId } ?: return@launch
        planRepo.updatePlanDay(dayId, PlanDayInput(name = day.name, color = day.color))
        refresh()
    }

    fun isHeavy(name: String): Boolean = HEAVY.contains(name) || Regex("deadlift|squat|shrug|weighted pull|romanian|front squat|back squat", RegexOption.IGNORE_CASE).containsMatchIn(name)

    private suspend fun readIronLogSettings(): IronLogSettings {
        // If a write is in-flight, return the cached (authoritative) value rather than reading
        // the not-yet-committed DB row, which would cause UI selection flicker.
        settingsWriteCache?.let { return it }
        val raw = settingsRepo.getString("ironlog_settings") ?: return IronLogSettings()
        return try {
            val json = JSONObject(raw)
            val weeklyGoalDays = normalizeWeeklyGoalDays(json.optInt("weeklyGoalDays", 4), 4)
            val rawIntelligenceMode = json.optString("intelligenceMode", "builtin")
            val intelligenceMode = canonicalIntelligenceMode(rawIntelligenceMode)
            val trainingDayIndices = TrainingDayPreferences.readFromSettings(json, weeklyGoalDays)
            if (rawIntelligenceMode != intelligenceMode || !json.has(TrainingDayPreferences.SETTINGS_KEY)) {
                json.put("intelligenceMode", intelligenceMode)
                TrainingDayPreferences.writeToSettings(json, trainingDayIndices)
                settingsRepo.setString("ironlog_settings", json.toString(), "json")
            }
            IronLogSettings(
                theme = json.optString("theme", com.ironlog.app.ui.theme.IronLogThemes.DEFAULT_THEME),
                weightUnit = json.optString("weightUnit", "kg"),
                hapticFeedback = json.optBoolean("hapticFeedback", true),
                effortTracking = json.optString("effortTracking", "off"),
                defaultRestSeconds = json.optInt("defaultRestSeconds", 90),
                defaultRestHeavySeconds = json.optInt("defaultRestHeavySeconds", 180),
                barWeightKg = json.optDouble("barWeightKg", 20.0),
                weeklyGoalDays = weeklyGoalDays,
                trainingDayIndices = trainingDayIndices,
                goalMode = normalizeStoredGoalMode(json.optString("goalMode", "hypertrophy")),
                progressionStyle = normalizeStoredProgressionStyle(json.optString("progressionStyle", "balanced")),
                userName = json.optString("userName", ""),
                performanceMode = json.optString("performanceMode", "balanced"),
                intelligenceMode = intelligenceMode,
                cloudAiBaseUrl = json.optString("cloudAiBaseUrl", ""),
                cloudAiModelName = json.optString("cloudAiModelName", ""),
                cloudAiDisplayName = json.optString("cloudAiDisplayName", ""),
                cloudAiProviderPreset = json.optString("cloudAiProviderPreset", ""),
                cloudAiApiFormat = json.optString("cloudAiApiFormat", "openai"),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            IronLogSettings()
        }
    }

    private suspend fun buildIndexUpdatesForSession(session: HistoryEntry) {
        if (session.exercises.isEmpty()) return
        val prIndex = JSONObject(settingsRepo.getString("pr_index_v1") ?: "{}")
        val prResetAt = settingsRepo.getPersonalBestResetAt()
        val volumeIndex = JSONObject(settingsRepo.getString("volume_index_v1") ?: "{}")
        val lastPerf = JSONObject(settingsRepo.getString("last_performance_v1") ?: "{}")
        val dateStr = session.date.substringBefore('T')
        val weekKey = isoWeekKey(session.date)
        session.exercises.forEach { ex ->
            val exId = ex.exerciseId.ifBlank { ex.name }
            val workingSets = ex.sets.filter { it.type.ifBlank { "normal" } != "warmup" }
            lastPerf.put(exId, JSONObject().put("date", dateStr).put("workoutId", session.id))
            ex.sets.filter { set ->
                set.type in setOf("normal", "failure", "amrap", "drop") &&
                    PersonalBestPolicy.isAfterReset(set, session.date, prResetAt)
            }.forEach { set ->
                val weight = set.weight
                val reps = set.reps
                if (weight > 0 && reps > 0) {
                    val arr = prIndex.optJSONArray(exId) ?: org.json.JSONArray().also { prIndex.put(exId, it) }
                    arr.put(JSONObject().put("date", dateStr).put("weight", weight).put("reps", reps).put("e1rm", kotlin.math.round(weight * (1 + reps / 30.0) * 10.0) / 10.0))
                }
            }
            if (workingSets.isNotEmpty()) {
                val weekObj = volumeIndex.optJSONObject(weekKey) ?: JSONObject().also { volumeIndex.put(weekKey, it) }
                val buckets = resolveSessionMuscleBuckets(ex)
                buckets.forEach { muscle ->
                    val normalized = normalizeMuscleKey(muscle)
                    weekObj.put(normalized, weekObj.optInt(normalized, 0) + workingSets.size)
                }
            }
        }
        settingsRepo.setString("pr_index_v1", prIndex.toString(), "json")
        settingsRepo.setString("volume_index_v1", volumeIndex.toString(), "json")
        settingsRepo.setString("last_performance_v1", lastPerf.toString(), "json")
    }

    private suspend fun flagDirty(reason: String) {
        settingsRepo.setString("backup_dirty_reason", reason, "string")
        settingsRepo.setString("backup_dirty_at", System.currentTimeMillis().toString(), "number")
    }
}

private data class SettingsMutationRequest(
    val version: Long,
    val settings: IronLogSettings,
    val completion: CompletableDeferred<IronLogSettings> = CompletableDeferred(),
)

private const val EXERCISE_NOTES_KEY = "exercise_notes_v1"
private const val ONBOARDING_KEY = "onboarding_complete"
private const val ONBOARDING_MIGRATION_KEY = "onboarding_state_migrated_v1"

private val HEAVY = setOf("Weighted Pull-Up", "Weighted Pull-Up or Lat Pulldown", "Romanian Deadlift", "Bulgarian Split Squat", "DB Shrugs", "Incline Smith Press", "Barbell Shrugs", "Deadlift", "Back Squat", "Front Squat")

private fun normalizeWeeklyGoalDays(value: Int, fallback: Int = 4): Int = if (value in 1..7) value else fallback.coerceIn(1, 7)
private fun normalizeMuscleKey(value: String): String = value.trim().lowercase().replace(Regex("[_\\s]+"), "_")
private fun resolveSessionPrimaryMuscle(exercise: com.ironlog.app.ui.model.HistoryExercise): String = exercise.primaryMuscle ?: exercise.primaryMuscles.firstOrNull { it.isNotBlank() } ?: "other"
private fun resolveSessionMuscleBuckets(exercise: com.ironlog.app.ui.model.HistoryExercise): List<String> {
    val raw = buildList {
        add(exercise.primaryMuscle.orEmpty())
        addAll(exercise.primaryMuscles)
        add(exercise.name)
        add(exercise.category.orEmpty())
    }
    val buckets = linkedSetOf<String>()
    raw.forEach { token ->
        val t = token.lowercase()
        when {
            t.contains("chest") || t.contains("tricep") || t.contains("shoulder") || t.contains("delt") || t.contains("press") -> buckets += "push"
            t.contains("back") || t.contains("lat") || t.contains("bicep") || t.contains("row") || t.contains("pull") -> buckets += "pull"
            t.contains("quad") || t.contains("hamstring") || t.contains("glute") || t.contains("calf") || t.contains("leg") || t.contains("squat") || t.contains("deadlift") || t.contains("hinge") -> buckets += "legs"
            t.contains("core") || t.contains("abs") || t.contains("ab ") || t.contains("crunch") || t.contains("plank") -> buckets += "core"
        }
    }
    if (buckets.isEmpty()) buckets += normalizeMuscleKey(resolveSessionPrimaryMuscle(exercise))
    return buckets.toList()
}

private fun isoWeekKey(dateStr: String): String {
    // Use tolerant parser so both ISO-8601 instants and YYYY-MM-DD bare dates work.
    val date = com.ironlog.app.domain.gamification.parseHistoryInstant(dateStr)
        ?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()
        ?: java.time.LocalDate.now()
    val fields = java.time.temporal.WeekFields.ISO
    return "${date.get(fields.weekBasedYear())}-W${date.get(fields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
}

private fun readDoubleMap(raw: String?): Map<String, Double> = try {
    val json = JSONObject(raw ?: "{}")
    json.keys().asSequence().associateWith { json.optDouble(it) }
} catch (_: Throwable) { emptyMap() }

private fun readStringMap(raw: String?): Map<String, String> = try {
    val json = JSONObject(raw ?: "{}")
    json.keys().asSequence().associateWith { json.optString(it) }
} catch (_: Throwable) { emptyMap() }

private fun IronLogSettings.toJson(): JSONObject = JSONObject()
    .put("theme", theme)
    .put("weightUnit", weightUnit)
    .put("hapticFeedback", hapticFeedback)
    .put("effortTracking", effortTracking)
    .put("defaultRestSeconds", defaultRestSeconds)
    .put("defaultRestHeavySeconds", defaultRestHeavySeconds)
    .put("barWeightKg", barWeightKg)
    .put("weeklyGoalDays", weeklyGoalDays)
    .put(TrainingDayPreferences.SETTINGS_KEY, org.json.JSONArray(trainingDayIndices.sorted()))
    .put("goalMode", goalMode)
    .put("progressionStyle", progressionStyle)
    .put("userName", userName)
    .put("performanceMode", performanceMode)
    .put("intelligenceMode", canonicalIntelligenceMode(intelligenceMode))
    .put("cloudAiBaseUrl", cloudAiBaseUrl)
    .put("cloudAiModelName", cloudAiModelName)
    .put("cloudAiDisplayName", cloudAiDisplayName)
    .put("cloudAiProviderPreset", cloudAiProviderPreset)
    .put("cloudAiApiFormat", cloudAiApiFormat)

internal fun normalizeStoredGoalMode(value: String): String = when (value.trim().lowercase()) {
    "strength" -> "strength"
    "general_fitness", "general fitness", "performance", "endurance" -> "general_fitness"
    else -> "hypertrophy"
}

internal fun normalizeStoredProgressionStyle(value: String): String = when (value.trim().lowercase()) {
    "conservative", "linear" -> "conservative"
    "aggressive", "undulating" -> "aggressive"
    else -> "balanced"
}
