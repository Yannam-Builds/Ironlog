package com.ironlog.app.ui.state

import androidx.compose.runtime.Immutable
import com.ironlog.app.util.convertKgToUnit
import com.ironlog.app.util.epley
import java.util.UUID
import kotlin.math.roundToInt

@Immutable
data class WorkoutInput(val weight: String = "", val reps: String = "")

@Immutable
data class LoggedSet(
    val id: String = genId(),
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

@Immutable
data class GhostSet(val weight: Double = 0.0, val reps: Double = 0.0, val type: String = "normal", val rpe: Double? = null)

@Immutable
data class GhostData(val sets: List<GhostSet> = emptyList(), val date: String? = null)

@Immutable
data class RestTimerState(
    val active: Boolean = false,
    val endTime: Long? = null,
    val total: Int = 0,
    val paused: Boolean = false,
    val pausedAt: Long? = null,
    val triggerExIndex: Int? = null,
)

/** Session-only override of the plan target (sets × reps) for a single exercise. */
@Immutable
data class TargetOverride(val sets: Int, val reps: Int)

@Immutable
data class WorkoutState(
    val inputs: Map<Int, WorkoutInput> = emptyMap(),
    val setLog: Map<Int, List<LoggedSet>> = emptyMap(),
    val ghostData: Map<Int, GhostData> = emptyMap(),
    val exerciseNotes: Map<Int, String> = emptyMap(),
    val supersetGroups: Map<Int, String?> = emptyMap(),
    val restTimer: RestTimerState = RestTimerState(),
    val swappedExercises: Map<Int, Any> = emptyMap(),
    val addedExercises: List<AddedExerciseEntry> = emptyList(),
    val pbNotif: String? = null,
    val copiedPrevious: Boolean = false,
    val targetOverrides: Map<Int, TargetOverride> = emptyMap(),   // GAP-23
    val removedBaseExerciseIndices: Set<Int> = emptySet(),
)

@Immutable
data class AddedExerciseEntry(
    val exerciseId: String,
    val name: String,
    val trackingType: String = "weight_reps",
    val equipment: String? = null,
    val sets: Int = 3,
    val reps: Int = 8,
)

sealed interface WorkoutAction {
    data class SetInput(val exIndex: Int, val weight: String? = null, val reps: String? = null) : WorkoutAction
    data class LogSet(val exIndex: Int, val set: LoggedSet) : WorkoutAction
    data class SetType(val exIndex: Int, val setIndex: Int, val type: String) : WorkoutAction
    data class SetNote(val exIndex: Int, val setIndex: Int, val note: String?) : WorkoutAction
    data class SetRpe(val exIndex: Int, val setIndex: Int, val rpe: Double?) : WorkoutAction
    data class SetRir(val exIndex: Int, val setIndex: Int, val rir: Int?) : WorkoutAction
    data class UpdateSet(val exIndex: Int, val setIndex: Int, val weight: Double?, val reps: Double?) : WorkoutAction
    data class DeleteSet(val exIndex: Int, val setIndex: Int) : WorkoutAction
    data class LoadGhost(val ghostData: Map<Int, GhostData>) : WorkoutAction
    data class SetExerciseNote(val exIndex: Int, val note: String) : WorkoutAction
    data class AssignSuperset(val exIndex: Int, val group: String?) : WorkoutAction
    data class StartRest(val endTime: Long, val total: Int, val triggerExIndex: Int?) : WorkoutAction
    data class PauseRest(val pausedAt: Long) : WorkoutAction
    data class ResumeRest(val newEndTime: Long) : WorkoutAction
    data object SkipRest : WorkoutAction
    data object Add30s : WorkoutAction
    data class QuickAddSet(val exIndex: Int) : WorkoutAction
    data class SwapExercise(val exIndex: Int, val exercise: Any) : WorkoutAction
    data class SetPbNotif(val message: String?) : WorkoutAction
    data class InsertWarmups(val exIndex: Int, val warmupSets: List<LoggedSet>) : WorkoutAction
    data class UpdateGhost(val exIndex: Int, val ghost: GhostData) : WorkoutAction
    data class CopyPrevious(val weightUnit: String = "kg") : WorkoutAction
    data class AddExercise(val entry: AddedExerciseEntry) : WorkoutAction
    data class RemoveExercise(val exIndex: Int, val baseExercisesCount: Int = 0, val removedBaseIndex: Int? = null) : WorkoutAction
    data class HydrateState(val payload: WorkoutState?) : WorkoutAction
    /** GAP-23: Override the plan target for this exercise for the current session only. */
    data class OverrideTarget(val exIndex: Int, val sets: Int, val reps: Int) : WorkoutAction
}

fun genId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

fun workoutReducer(state: WorkoutState, action: WorkoutAction, nowMs: Long = System.currentTimeMillis()): WorkoutState = when (action) {
    is WorkoutAction.SetInput -> {
        val prev = state.inputs[action.exIndex] ?: WorkoutInput()
        val next = WorkoutInput(action.weight ?: prev.weight, action.reps ?: prev.reps)
        if (prev == next) state else state.copy(inputs = state.inputs + (action.exIndex to next))
    }

    is WorkoutAction.LogSet -> {
        val isTimeBased = action.set.trackingType.startsWith("duration")
        val orm = if (!isTimeBased && action.set.weight > 0 && action.set.reps > 0) epley(action.set.weight, action.set.reps) else 0.0
        val newSet = action.set.copy(id = genId(), orm = orm, type = action.set.type.ifBlank { "normal" }, rpe = action.set.rpe, rir = action.set.rir, note = action.set.note)
        val existing = state.setLog[action.exIndex].orEmpty()
        state.copy(setLog = state.setLog + (action.exIndex to (existing + newSet)))
    }

    is WorkoutAction.SetType -> updateLoggedSet(state, action.exIndex, action.setIndex) { it.copy(type = action.type) }
    is WorkoutAction.SetNote -> updateLoggedSet(state, action.exIndex, action.setIndex) { it.copy(note = action.note) }
    is WorkoutAction.SetRpe -> updateLoggedSet(state, action.exIndex, action.setIndex) { it.copy(rpe = action.rpe) }
    is WorkoutAction.SetRir -> updateLoggedSet(state, action.exIndex, action.setIndex) { it.copy(rir = action.rir) }

    is WorkoutAction.UpdateSet -> updateLoggedSet(state, action.exIndex, action.setIndex) { current ->
        val isTimeBased = current.trackingType.startsWith("duration")
        val nextWeight = action.weight?.takeIf { it.isFinite() } ?: current.weight
        val nextReps = action.reps?.takeIf { it.isFinite() } ?: current.reps
        val orm = if (!isTimeBased && nextWeight > 0 && nextReps > 0) epley(nextWeight, nextReps) else 0.0
        current.copy(weight = nextWeight, reps = nextReps, orm = orm, durationSec = if (isTimeBased) nextReps else current.durationSec)
    }

    is WorkoutAction.DeleteSet -> {
        val sets = state.setLog[action.exIndex].orEmpty().toMutableList()
        if (action.setIndex !in sets.indices) state else {
            sets.removeAt(action.setIndex)
            state.copy(setLog = state.setLog + (action.exIndex to sets))
        }
    }

    is WorkoutAction.LoadGhost -> state.copy(ghostData = action.ghostData)
    is WorkoutAction.SetExerciseNote -> state.copy(exerciseNotes = state.exerciseNotes + (action.exIndex to action.note))
    is WorkoutAction.AssignSuperset -> state.copy(supersetGroups = state.supersetGroups + (action.exIndex to action.group))
    is WorkoutAction.StartRest -> state.copy(restTimer = RestTimerState(active = true, endTime = action.endTime, total = action.total, paused = false, pausedAt = null, triggerExIndex = action.triggerExIndex))
    is WorkoutAction.PauseRest -> if (!state.restTimer.active || state.restTimer.paused) state else state.copy(restTimer = state.restTimer.copy(paused = true, pausedAt = action.pausedAt))
    is WorkoutAction.ResumeRest -> if (!state.restTimer.paused) state else state.copy(restTimer = state.restTimer.copy(paused = false, pausedAt = null, endTime = action.newEndTime))
    WorkoutAction.SkipRest -> state.copy(restTimer = RestTimerState())
    WorkoutAction.Add30s -> {
        if (!state.restTimer.active) state else state.copy(
            restTimer = state.restTimer.copy(
                endTime = (state.restTimer.endTime ?: nowMs) + 30_000L,
                total = state.restTimer.total + 30,
            ),
        )
    }
    is WorkoutAction.QuickAddSet -> {
        val last = state.setLog[action.exIndex].orEmpty().lastOrNull()
        if (last == null) state else state.copy(inputs = state.inputs + (action.exIndex to WorkoutInput(last.weight.toCleanString(), last.reps.toCleanString())))
    }
    is WorkoutAction.SwapExercise -> state.copy(swappedExercises = state.swappedExercises + (action.exIndex to action.exercise))
    is WorkoutAction.SetPbNotif -> state.copy(pbNotif = action.message)
    is WorkoutAction.InsertWarmups -> {
        val workingSets = state.setLog[action.exIndex].orEmpty().filter { (it.type.ifBlank { "normal" }) != "warmup" }
        state.copy(setLog = state.setLog + (action.exIndex to (action.warmupSets + workingSets)))
    }
    is WorkoutAction.UpdateGhost -> state.copy(ghostData = state.ghostData + (action.exIndex to action.ghost))
    is WorkoutAction.CopyPrevious -> {
        val newInputs = state.inputs.toMutableMap()
        state.ghostData.forEach { (idx, ghost) ->
            val first = ghost.sets.firstOrNull()
            if (first != null) {
                newInputs[idx] = WorkoutInput(
                    weight = if (first.weight > 0) convertKgToUnit(first.weight, action.weightUnit, if (action.weightUnit == "lbs") 0 else 1).toCleanString() else "",
                    reps = if (first.reps > 0) first.reps.toCleanString() else "",
                )
            }
        }
        state.copy(inputs = newInputs, copiedPrevious = true)
    }
    is WorkoutAction.AddExercise -> state.copy(addedExercises = state.addedExercises + action.entry)
    is WorkoutAction.RemoveExercise -> {
        val exIdx = action.exIndex
        val addedLocalIdx = exIdx - action.baseExercisesCount
        val newAddedExercises = if (addedLocalIdx >= 0 && addedLocalIdx in state.addedExercises.indices) {
            state.addedExercises.toMutableList().also { it.removeAt(addedLocalIdx) }
        } else {
            state.addedExercises
        }
        // Re-index all maps: drop the removed entry, shift keys above it down by 1.
        fun <V> reIndex(map: Map<Int, V>): Map<Int, V> =
            map.filterKeys { it != exIdx }.mapKeys { (k, _) -> if (k > exIdx) k - 1 else k }
        val newRemovedBaseIndices = if (action.removedBaseIndex != null) {
            state.removedBaseExerciseIndices + action.removedBaseIndex
        } else {
            state.removedBaseExerciseIndices
        }
        state.copy(
            addedExercises = newAddedExercises,
            inputs = reIndex(state.inputs),
            setLog = reIndex(state.setLog),
            ghostData = reIndex(state.ghostData),
            exerciseNotes = reIndex(state.exerciseNotes),
            supersetGroups = reIndex(state.supersetGroups),
            swappedExercises = reIndex(state.swappedExercises),
            targetOverrides = reIndex(state.targetOverrides),
            removedBaseExerciseIndices = newRemovedBaseIndices,
        )
    }
    is WorkoutAction.HydrateState -> (action.payload ?: WorkoutState()).copy(ghostData = state.ghostData, pbNotif = null, targetOverrides = state.targetOverrides)
    is WorkoutAction.OverrideTarget -> state.copy(targetOverrides = state.targetOverrides + (action.exIndex to TargetOverride(action.sets, action.reps)))
}

private fun updateLoggedSet(state: WorkoutState, exIndex: Int, setIndex: Int, transform: (LoggedSet) -> LoggedSet): WorkoutState {
    val sets = state.setLog[exIndex].orEmpty().toMutableList()
    if (setIndex !in sets.indices) return state
    sets[setIndex] = transform(sets[setIndex])
    return state.copy(setLog = state.setLog + (exIndex to sets))
}

private fun Double.toCleanString(): String = if (this % 1.0 == 0.0) this.roundToInt().toString() else this.toString()
