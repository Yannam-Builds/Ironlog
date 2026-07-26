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
import com.ironlog.app.ui.model.AppDataState
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.IronLogSettings
import com.ironlog.app.ui.model.UiPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Aggregates plans, history, settings, and personal bests for the entire app. */
class AppDataViewModel(
    private val plansVm: PlansViewModel = PlansViewModel(),
    private val statsVm: StatsViewModel = StatsViewModel(),
    private val planRepo: PlanRepository = PlanRepository(),
    private val workoutRepo: WorkoutRepository = WorkoutRepository(),
    private val exerciseRepo: ExerciseRepository = ExerciseRepository(),
    private val settingsRepo: SettingsRepository = SettingsRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(AppDataState())
    val state: StateFlow<AppDataState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var manualPb: Map<String, Double> = emptyMap()
    private var exerciseNotes: Map<String, String> = emptyMap()
    /** In-flight write guard: prevents concurrent refresh() calls from reading a stale DB value
     *  and overwriting the optimistic UI state set in updateSettingsAsync. */
    @Volatile private var settingsWriteCache: IronLogSettings? = null

    init {
        observePlanMutations()
        refresh()
    }

    private fun observePlanMutations() = viewModelScope.launch {
        launch {
            // Pass the emitted plans directly so refresh() always uses the latest snapshot,
            // not a potentially-stale re-read of plansVm.plans.value.
            plansVm.plans.collect { latestPlans -> refresh(latestPlans) }
        }
    }

    fun refresh(plansOverride: List<UiPlan>? = null): Job {
        refreshJob?.cancel()
        val job = viewModelScope.launch {
            val plans = plansOverride ?: plansVm.plans.value
            val stats = statsVm.state.value
            val exercises = exerciseRepo.getExercisesSnapshot()
            val settings = readIronLogSettings()
            val onboarding = settingsRepo.getBoolean(ONBOARDING_KEY, false)
            manualPb = readDoubleMap(settingsRepo.getString("ironlog_pb"))
            exerciseNotes = readStringMap(settingsRepo.getString(EXERCISE_NOTES_KEY))
            if (!onboarding && plans.isNotEmpty()) settingsRepo.setBoolean(ONBOARDING_KEY, true)
            // plansLoaded becomes true once plansVm has finished its initial DB read (!loading),
            // or once we receive a non-empty override (plansVm emitted real data).
            // An empty override list is the StateFlow's initial emission — NOT a real "loaded" signal.
            // Sticky: once true, stays true so re-refreshes don't bounce the UI back to "loading".
            val plansLoaded = _state.value.plansLoaded ||
                (plansOverride != null && plansOverride.isNotEmpty()) ||
                !plansVm.loading.value
            _state.value = AppDataState(
                initialized = true,
                plansLoaded = plansLoaded,
                plans = plans,
                history = stats.history,
                pb = stats.pb + manualPb,
                exerciseNotes = exerciseNotes,
                settings = settings,
                onboardingComplete = onboarding || plans.isNotEmpty(),
                exerciseIndex = exercises,
            )
        }
        refreshJob = job
        return job
    }

    suspend fun updateSettings(updates: IronLogSettings): IronLogSettings {
        val next = updates.copy(weeklyGoalDays = normalizeWeeklyGoalDays(updates.weeklyGoalDays, _state.value.settings.weeklyGoalDays))
        settingsRepo.setString("ironlog_settings", next.toJson().toString(), "json")
        flagDirty("settings_changed")
        refresh()
        return next
    }

    fun updateSettingsAsync(updates: IronLogSettings) = viewModelScope.launch {
        // Optimistic: update state immediately so the UI responds on this frame.
        val normalized = updates.copy(weeklyGoalDays = normalizeWeeklyGoalDays(updates.weeklyGoalDays, _state.value.settings.weeklyGoalDays))
        // Cache before DB write — any concurrent refresh() triggered by observePlanMutations
        // will read this value instead of the not-yet-written DB row, preventing the
        // selection-reverts-to-previous-value bug.
        settingsWriteCache = normalized
        _state.value = _state.value.copy(settings = normalized)
        // Persist and refresh (refresh will also read cache while write is in-flight)
        updateSettings(updates)
        // DB write complete — future refresh() calls can read straight from DB again.
        settingsWriteCache = null
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

    fun clearPbs() = viewModelScope.launch {
        manualPb = emptyMap()
        settingsRepo.setString("ironlog_pb", "{}", "json")
        flagDirty("pb_cleared")
        refresh()
    }

    /** FIXED: 34 — Clear all completed workout history */
    fun clearAllHistory() = viewModelScope.launch {
        runCatching { workoutRepo.clearCompletedWorkouts() }
        flagDirty("history_cleared")
        refresh()
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
        weeklyGoalDays?.let { updateSettings(_state.value.settings.copy(weeklyGoalDays = normalizeWeeklyGoalDays(it, _state.value.settings.weeklyGoalDays))) }
        settingsRepo.setBoolean(ONBOARDING_KEY, true)
        flagDirty("onboarding_changed")
        refresh()
    }

    fun resetOnboarding() = viewModelScope.launch {
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
            IronLogSettings(
                theme = json.optString("theme", com.ironlog.app.ui.theme.IronLogThemes.DEFAULT_THEME),
                weightUnit = json.optString("weightUnit", "kg"),
                hapticFeedback = json.optBoolean("hapticFeedback", true),
                effortTracking = json.optString("effortTracking", "off"),
                defaultRestSeconds = json.optInt("defaultRestSeconds", 90),
                defaultRestHeavySeconds = json.optInt("defaultRestHeavySeconds", 180),
                barWeightKg = json.optDouble("barWeightKg", 20.0),
                weeklyGoalDays = normalizeWeeklyGoalDays(json.optInt("weeklyGoalDays", 4), 4),
                goalMode = json.optString("goalMode", "hypertrophy"),
                progressionStyle = json.optString("progressionStyle", "balanced"),
                userName = json.optString("userName", ""),
                performanceMode = json.optString("performanceMode", "balanced"),
                intelligenceMode = json.optString("intelligenceMode", "builtin"),
                cloudAiBaseUrl = json.optString("cloudAiBaseUrl", ""),
                cloudAiModelName = json.optString("cloudAiModelName", ""),
                cloudAiDisplayName = json.optString("cloudAiDisplayName", ""),
                cloudAiProviderPreset = json.optString("cloudAiProviderPreset", ""),
                cloudAiApiFormat = json.optString("cloudAiApiFormat", "openai"),
            )
        } catch (_: Throwable) { IronLogSettings() }
    }

    private suspend fun buildIndexUpdatesForSession(session: HistoryEntry) {
        if (session.exercises.isEmpty()) return
        val prIndex = JSONObject(settingsRepo.getString("pr_index_v1") ?: "{}")
        val volumeIndex = JSONObject(settingsRepo.getString("volume_index_v1") ?: "{}")
        val lastPerf = JSONObject(settingsRepo.getString("last_performance_v1") ?: "{}")
        val dateStr = session.date.substringBefore('T')
        val weekKey = isoWeekKey(session.date)
        session.exercises.forEach { ex ->
            val exId = ex.exerciseId.ifBlank { ex.name }
            val workingSets = ex.sets.filter { it.type.ifBlank { "normal" } != "warmup" }
            lastPerf.put(exId, JSONObject().put("date", dateStr).put("workoutId", session.id))
            ex.sets.filter { it.type in setOf("normal", "failure", "amrap", "drop") }.forEach { set ->
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

private const val EXERCISE_NOTES_KEY = "exercise_notes_v1"
private const val ONBOARDING_KEY = "onboarding_complete"

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
    .put("goalMode", goalMode)
    .put("progressionStyle", progressionStyle)
    .put("userName", userName)
    .put("performanceMode", performanceMode)
    .put("intelligenceMode", intelligenceMode)
    .put("cloudAiBaseUrl", cloudAiBaseUrl)
    .put("cloudAiModelName", cloudAiModelName)
    .put("cloudAiDisplayName", cloudAiDisplayName)
    .put("cloudAiProviderPreset", cloudAiProviderPreset)
    .put("cloudAiApiFormat", cloudAiApiFormat)

