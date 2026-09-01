package com.ironlog.app.ui.screens.history

import com.ironlog.app.data.model.CompletedExerciseInput
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.data.model.SetInput
import com.ironlog.app.ui.state.LoggedSet
import com.ironlog.app.ui.state.WorkoutAction
import com.ironlog.app.util.convertUnitToKg
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class HistoricalExerciseChoice(
    val id: String, val name: String, val trackingType: String = "weight_reps",
    val category: String = "strength", val equipment: String = "", val isBodyweight: Boolean = false,
)

internal data class HistoricalExerciseDraft(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val name: String,
    val trackingType: String = "weight_reps",
    val notes: String = "",
    val sets: List<LoggedSet> = emptyList(),
    val loadInput: String = "",
    val repsInput: String = "",
)

internal data class HistoricalWorkoutDraft(
    val id: String = UUID.randomUUID().toString(),
    val date: String = "",
    val time: String = "",
    val durationMinutes: String = "",
    val name: String = "",
    val notes: String = "",
    val detailed: Boolean = true,
    val rating: Int? = null,
    val offsetSeconds: Int? = null,
    val exercises: List<HistoricalExerciseDraft> = emptyList(),
) {
    fun toCompletedInput(zone: ZoneId, now: Instant): CreateCompletedWorkoutInput {
        val started = historicalStartInstant(date, time, zone, offsetSeconds?.let(ZoneOffset::ofTotalSeconds))
        require(!started.isAfter(now)) { "Historical workouts cannot start in the future." }
        val minutes = durationMinutes.trim().toIntOrNull()
        require(minutes != null && minutes in 0..(Int.MAX_VALUE / 60)) { "Enter a nonnegative whole-number duration in minutes." }
        require(!started.plusSeconds(minutes * 60L).isAfter(now)) { "The workout must have finished before now." }
        require(rating == null || rating in 1..5) { "Rating must be between 1 and 5." }
        if (detailed) {
            require(exercises.isNotEmpty() && exercises.all { it.sets.isNotEmpty() }) { "Add an exercise and at least one set for each exercise." }
            require(exercises.all { it.loadInput.isBlank() && it.repsInput.isBlank() }) { "Add or clear the unlogged set values before saving." }
        }
        val completed = if (!detailed) emptyList() else exercises.map { exercise ->
            CompletedExerciseInput(exerciseId = exercise.exerciseId, name = exercise.name,
                trackingType = exercise.trackingType, notes = exercise.notes,
                sets = exercise.sets.map { set ->
                    require(set.weight.isFinite() && set.weight >= 0 && set.reps.isFinite() && set.reps > 0) { "Every set needs a valid load and positive reps or seconds." }
                    require(set.rpe == null || (set.rpe.isFinite() && set.rpe in 1.0..10.0)) { "RPE must be between 1 and 10." }
                    require(set.rir == null || set.rir in 0..10) { "RIR must be between 0 and 10." }
                    SetInput(uid = set.id, weight = set.weight, reps = set.reps, type = set.type,
                        rpe = set.rpe, rir = set.rir?.toDouble(), notes = set.note)
                })
        }
        return CreateCompletedWorkoutInput(uid = id, name = name.trim().ifBlank { "Historical workout" },
            startedAt = started.toEpochMilli(), durationSeconds = minutes * 60, notes = notes.trim(),
            rating = rating?.toDouble(), exerciseData = completed)
    }
}

/** Accept only editing actions; this adapter never invokes the live workout reducer or persistence. */
internal fun updateHistoricalSet(draft: HistoricalWorkoutDraft, exerciseId: String, setId: String,
    action: WorkoutAction, weightUnit: String): HistoricalWorkoutDraft = draft.copy(exercises = draft.exercises.map { exercise ->
    if (exercise.id != exerciseId) exercise else exercise.copy(sets = exercise.sets.mapNotNull { set ->
        if (set.id != setId) set else when (action) {
            is WorkoutAction.DeleteSet -> null
            is WorkoutAction.SetType -> set.copy(type = action.type)
            is WorkoutAction.SetNote -> set.copy(note = action.note)
            is WorkoutAction.SetRpe -> set.copy(rpe = action.rpe)
            is WorkoutAction.SetRir -> set.copy(rir = action.rir)
            is WorkoutAction.UpdateSet -> set.copy(
                weight = action.weight?.let { if (exercise.trackingType == "duration_distance") it else convertUnitToKg(it, weightUnit) } ?: set.weight,
                reps = action.reps ?: set.reps,
                durationSec = if (exercise.trackingType.startsWith("duration")) action.reps ?: set.reps else set.durationSec,
            )
            else -> set
        }
    })
})

internal fun encodeHistoricalDraft(draft: HistoricalWorkoutDraft): String = JSONObject().apply {
    put("id", draft.id); put("date", draft.date); put("time", draft.time); put("duration", draft.durationMinutes)
    put("name", draft.name); put("notes", draft.notes); put("detailed", draft.detailed); put("offset", draft.offsetSeconds)
    put("rating", draft.rating)
    put("exercises", JSONArray().apply { draft.exercises.forEach { exercise -> put(JSONObject().apply {
        put("id", exercise.id); put("exerciseId", exercise.exerciseId); put("name", exercise.name); put("tracking", exercise.trackingType)
        put("notes", exercise.notes); put("loadInput", exercise.loadInput); put("repsInput", exercise.repsInput)
        put("sets", JSONArray().apply { exercise.sets.forEach { set -> put(JSONObject().apply {
            // Drafts retain invalid pasted edits for correction; JSON numeric fields reject NaN/Infinity.
            // getDouble accepts both strings and legacy numbers. Completed-input validation stays strict.
            put("id", set.id); put("weight", set.weight.toString()); put("reps", set.reps.toString()); put("type", set.type)
            put("rpe", set.rpe?.toString()); put("rir", set.rir); put("note", set.note); put("orm", set.orm.toString())
            put("tracking", set.trackingType); put("durationSec", set.durationSec?.toString())
        }) } })
    }) } })
}.toString()

internal fun decodeHistoricalDraft(raw: String): HistoricalWorkoutDraft {
    val root = JSONObject(raw)
    val rows = root.getJSONArray("exercises")
    return HistoricalWorkoutDraft(id = root.getString("id"), date = root.getString("date"), time = root.getString("time"),
        durationMinutes = root.getString("duration"), name = root.getString("name"), notes = root.getString("notes"),
        detailed = root.getBoolean("detailed"), offsetSeconds = root.optionalNumber("offset")?.toInt(),
        rating = root.optionalNumber("rating")?.toInt(),
        exercises = List(rows.length()) { index ->
            val row = rows.getJSONObject(index); val sets = row.getJSONArray("sets")
            HistoricalExerciseDraft(id = row.getString("id"), exerciseId = row.getString("exerciseId"), name = row.getString("name"),
                trackingType = row.getString("tracking"), notes = row.getString("notes"), loadInput = row.getString("loadInput"), repsInput = row.getString("repsInput"),
                sets = List(sets.length()) { si -> val set = sets.getJSONObject(si)
                    LoggedSet(id = set.getString("id"), weight = set.getDouble("weight"), reps = set.getDouble("reps"),
                        type = set.getString("type"), rpe = set.optionalNumber("rpe"), rir = set.optionalNumber("rir")?.toInt(),
                        note = if (set.isNull("note")) null else set.getString("note"), orm = set.getDouble("orm"),
                        trackingType = set.getString("tracking"), durationSec = set.optionalNumber("durationSec"))
                })
        })
}

private fun JSONObject.optionalNumber(key: String): Double? = if (isNull(key)) null else getDouble(key)
