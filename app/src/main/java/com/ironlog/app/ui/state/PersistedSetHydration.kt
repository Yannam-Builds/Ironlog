package com.ironlog.app.ui.state

import com.ironlog.app.data.objectbox.WorkoutSetEntity

/** Completed rows come only from the committed DB; draft-only targets/notes remain untouched. */
internal fun restorePersistedSetLog(
    current: WorkoutState,
    rows: List<WorkoutSetEntity>,
    exerciseRowsByIndex: Map<Int, String>,
    trackingTypes: Map<Int, String>,
): WorkoutState {
    val grouped = rows.groupBy { it.workoutExerciseUid }
    val restored = exerciseRowsByIndex.mapValues { (index, uid) ->
        val tracking = trackingTypes[index] ?: "weight_reps"
        grouped[uid].orEmpty().sortedWith(compareBy({ it.setIndex }, { it.uid })).map { row ->
            require(row.uid.isNotBlank()) { "A saved set has no stable ID. Restore was stopped to protect its data." }
            LoggedSet(
                id = row.uid, weight = row.weight, reps = row.reps,
                type = when { row.isWarmup -> "warmup"; row.isDropset -> "drop"; row.isAmrap -> "amrap"; row.toFailure -> "failure"; else -> "normal" },
                rpe = row.rpe, rir = row.rir?.toInt(), note = row.notes?.takeIf(String::isNotBlank),
                trackingType = tracking, durationSec = if (tracking.startsWith("duration")) row.reps else null,
            ).let { it.copy(orm = loggedSetEstimatedOneRm(it)) }
        }
    }
    val committedIds = restored.values.flatten().map { it.id }.toSet()
    val pending = current.pendingWarmups.mapValues { (_, queue) -> queue.filterNot { it.id in committedIds } }.filterValues { it.isNotEmpty() }
    return current.copy(setLog = restored, pendingWarmups = pending)
}
