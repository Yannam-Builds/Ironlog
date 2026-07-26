package com.ironlog.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity_
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.domain.intelligence.PrLinkingEngine
import com.ironlog.app.ui.model.ChartPoint
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import com.ironlog.app.ui.model.PersonalBest
import com.ironlog.app.ui.model.StatsUiState
import com.ironlog.app.domain.intelligence.ManualRecoveryInput
import com.ironlog.app.util.calculateEstimated1RM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Provides statistics and analytics data for the Stats screen. */
class StatsViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()
    
    private val _manualRecoveryInput = MutableStateFlow<ManualRecoveryInput?>(null)
    val manualRecoveryInput: StateFlow<ManualRecoveryInput?> = _manualRecoveryInput.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            var lastSignature: String? = null
            while (true) {
                val signature = runCatching {
                    withContext(Dispatchers.IO) {
                        val workoutsBox = ObjectBox.store.boxFor(WorkoutEntity::class.java)
                        val completed = workoutsBox.query(WorkoutEntity_.status.equal("completed"))
                            .build().use { it.find() }
                        val latest = completed.maxOfOrNull { it.startedAt } ?: 0L
                        "${completed.size}:$latest"
                    }
                }.getOrNull()
                if (signature != null && signature != lastSignature) {
                    lastSignature = signature
                    refresh()
                }
                kotlinx.coroutines.delay(1200)
            }
        }
    }

    fun refresh() = viewModelScope.launch { 
        _state.value = buildStats() 
        _manualRecoveryInput.value = loadManualRecovery()
    }

    suspend fun clearPbs() { settingsRepo.setSetting("ironlog_pb", "{}", "json"); refresh() }
    
    suspend fun saveManualRecovery(input: ManualRecoveryInput) {
        val stamped = input.copy(recordedAt = System.currentTimeMillis())
        val jsonString = kotlinx.serialization.json.Json.encodeToString(ManualRecoveryInput.serializer(), stamped)
        settingsRepo.setSetting("manual_recovery_input", jsonString, "json")
        _manualRecoveryInput.value = stamped
    }
    
    private suspend fun loadManualRecovery(): ManualRecoveryInput? {
        val raw = settingsRepo.getSettingString("manual_recovery_input") ?: return null
        return runCatching { kotlinx.serialization.json.Json.decodeFromString(ManualRecoveryInput.serializer(), raw) }.getOrNull()
    }

    private suspend fun buildStats(): StatsUiState = withContext(Dispatchers.IO) {
        val workoutsBox = ObjectBox.store.boxFor(WorkoutEntity::class.java)
        val wesBox = ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java)
        val setsBox = ObjectBox.store.boxFor(WorkoutSetEntity::class.java)
        val exercisesBox = ObjectBox.store.boxFor(ExerciseEntity::class.java)

        val workouts = workoutsBox.query(WorkoutEntity_.status.equal("completed"))
            .orderDesc(WorkoutEntity_.startedAt)
            .build().use { it.find() }
        val workoutIds = workouts.map { it.uid }.toTypedArray()
        val wes = if (workoutIds.isEmpty()) emptyList() else {
            wesBox.query(WorkoutExerciseEntity_.workoutUid.oneOf(workoutIds))
                .order(WorkoutExerciseEntity_.orderIndex)
                .build().use { it.find() }
        }
        val weIds = wes.map { it.uid }.toTypedArray()
        val sets = if (weIds.isEmpty()) emptyList() else {
            setsBox.query(WorkoutSetEntity_.workoutExerciseUid.oneOf(weIds))
                .order(WorkoutSetEntity_.setIndex)
                .build().use { it.find() }
        }
        val exerciseIds = wes.map { it.exerciseUid }.distinct().toTypedArray()
        val exercises = if (exerciseIds.isEmpty()) emptyList() else {
            exercisesBox.query(ExerciseEntity_.uid.oneOf(exerciseIds))
                .build().use { it.find() }
        }
        val weightUnit = parseWeightUnit(settingsRepo.getString("ironlog_settings"))

        val weByWorkout = wes.groupBy { it.workoutUid }
        val setsByWe = sets.groupBy { it.workoutExerciseUid }
        val exById = exercises.associateBy { it.uid }

        val history = workouts.map { w ->
            val exRows = weByWorkout[w.uid].orEmpty().sortedBy { it.orderIndex }
            HistoryEntry(
                id = w.uid,
                date = Instant.ofEpochMilli(w.startedAt).toString(),
                duration = w.durationSeconds,
                rating = w.rating,
                summaryText = w.notes.ifBlank { null },
                name = w.name,
                planDayUid = w.planDayUid?.ifBlank { null },
                imported = w.imported,
                exercises = exRows.map { we ->
                    val ex = exById[we.exerciseUid]
                    HistoryExercise(
                        id = we.uid,
                        exerciseId = we.exerciseUid,
                        name = ex?.name ?: "Unknown",
                        primaryMuscle = ex?.primaryMuscle,
                        equipment = ex?.equipment,
                        category = ex?.category,
                        supersetGroup = we.supersetGroup.ifBlank { null },
                        note = we.notes.ifBlank { null },
                        sets = setsByWe[we.uid].orEmpty().sortedBy { it.setIndex }.map { s ->
                            HistoryExerciseSet(
                                id = s.uid,
                                weight = s.weight,
                                reps = s.reps,
                                type = when {
                                    s.isWarmup -> "warmup"
                                    s.isDropset -> "drop"
                                    s.isAmrap -> "amrap"
                                    s.toFailure -> "failure"
                                    else -> "normal"
                                },
                                rpe = s.rpe,
                                rir = s.rir?.roundToInt()?.toDouble(),
                                restSeconds = s.restSeconds,
                            )
                        },
                    )
                },
            )
        }

        val datestamps = history.map { it.date.substringBefore('T') }
        val streak = buildStreak(datestamps)
        val completedWeIds = wes.map { it.uid }.toSet()
        val totalSets = sets.size
        val avgDurationMin = if (workouts.isNotEmpty()) (workouts.sumOf { it.durationSeconds }.toDouble() / workouts.size / 60.0).roundToInt() else 0
        val chartData = build14DayChart(datestamps)

        // FIXED: 26 — track date + previousOrm per PB entry
        val weToEx = wes.associate { it.uid to it.exerciseUid }
        val weToWorkout = wes.associate { it.uid to it.workoutUid }
        val workoutDateByUid = workouts.associate { it.uid to Instant.ofEpochMilli(it.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }
        // Data: (weight, bestOrm, exName, date, secondBestOrm)
        val bestByExerciseUid = linkedMapOf<String, Array<Any?>>()
        sets.forEach { set ->
            if (set.workoutExerciseUid !in completedWeIds || set.isWarmup) return@forEach
            val exerciseUid = weToEx[set.workoutExerciseUid] ?: return@forEach
            val ex = exById[exerciseUid] ?: return@forEach
            val orm = calculateEstimated1RM(set.weight, set.reps)
            val date = workoutDateByUid[weToWorkout[set.workoutExerciseUid]] ?: ""
            val prev = bestByExerciseUid[exerciseUid]
            if (prev == null || orm > (prev[1] as Double)) {
                val prevOrm = prev?.get(1) as? Double
                bestByExerciseUid[exerciseUid] = arrayOf(set.weight, orm, ex.name, date, prevOrm)
            } else {
                // Track second-best if current is between prev and current best
                val prevSecond = prev[4] as? Double
                if (prevSecond == null || orm > prevSecond) {
                    prev[4] = orm
                }
            }
        }
        val personalBests = bestByExerciseUid.values
            .map { PersonalBest(exerciseName = it[2] as String, bestWeight = it[0] as Double, estOneRm = it[1] as Double, date = it[3] as? String, previousOrm = it[4] as? Double) }
            .sortedByDescending { it.estOneRm }
        val pb = personalBests.associate { it.exerciseName to it.estOneRm }
        val pbByGroup = linkedMapOf<String, Double>()
        personalBests.forEach { best ->
            val ex = exercises.firstOrNull { it.name == best.exerciseName } ?: return@forEach
            val groupId = PrLinkingEngine.getPrGroupId(ex.name, ex.primaryMuscle, ex.equipment, ex.category, "safe") ?: "exact_${ex.name}"
            val prev = pbByGroup[groupId] ?: Double.NEGATIVE_INFINITY
            if (best.estOneRm > prev) pbByGroup[groupId] = best.estOneRm
        }

        StatsUiState(
            history = history,
            pb = pb,
            personalBests = personalBests,
            pbGroups = pbByGroup,
            streak = streak,
            totalSets = totalSets,
            avgDurationMin = avgDurationMin,
            chartData = chartData,
            weightUnit = weightUnit,
        )
    }
}

fun buildStreak(datestamps: List<String>): Int {
    if (datestamps.isEmpty()) return 0
    val days = datestamps.toSet().sortedDescending()
    val today = LocalDate.now().toString()
    val yesterday = LocalDate.now().minusDays(1).toString()
    if (days.first() != today && days.first() != yesterday) return 0
    var streak = 0
    var prev = LocalDate.parse(days.first())
    for (d in days) {
        val current = LocalDate.parse(d)
        val diff = java.time.Duration.between(current.atStartOfDay(), prev.atStartOfDay()).toDays()
        if (diff <= 1) { streak += 1; prev = current } else break
    }
    return streak
}

fun build14DayChart(datestamps: List<String>): List<ChartPoint> {
    val counts = datestamps.groupingBy { it }.eachCount()
    val labelIndices = setOf(0, 4, 9, 13)
    val formatter = DateTimeFormatter.ofPattern("dd/MM")
    return (0 until 14).map { i ->
        val day = LocalDate.now().minusDays((13 - i).toLong())
        ChartPoint(value = counts[day.toString()] ?: 0, label = if (i in labelIndices) day.format(formatter) else "")
    }
}

private fun parseWeightUnit(raw: String?): String = try {
    raw?.takeIf { it.isNotBlank() }?.let { JSONObject(it).optString("weightUnit", "kg") } ?: "kg"
} catch (_: Throwable) { "kg" }

