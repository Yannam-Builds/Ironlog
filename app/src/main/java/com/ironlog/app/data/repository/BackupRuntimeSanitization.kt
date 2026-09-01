package com.ironlog.app.data.repository

import org.json.JSONArray
import org.json.JSONObject

internal data class BackupRuntimeSanitization(
    val data: JSONObject,
    val skippedRows: Int,
)

/** Runtime ownership, clocks, queued commands, and delivery bookkeeping are device-local state. */
internal fun isTransientBackupRuntimeSetting(key: String): Boolean {
    val normalized = key.trim()
    return normalized.startsWith("active_workout_") ||
        normalized.startsWith("pending_workout_action") ||
        normalized.startsWith("notification_schedule_") ||
        normalized in setOf(
            "decision_log_json",
            "snooze_until",
            "notification_last_app_foreground_epoch_ms",
            "pending_nav_route",
            "last_auto_backup_ms",
            "last_successful_backup_ms",
        )
}

internal fun isTransferableBackupWorkoutStatus(status: String): Boolean =
    !status.equals("active", ignoreCase = true)

/**
 * Removes legacy runtime rows before preview and import. Active workouts are intentionally not
 * transferable: their timer and mutation ownership belongs to the originating process/device.
 */
internal fun sanitizeBackupRuntimeData(source: JSONObject): BackupRuntimeSanitization {
    val data = JSONObject(source.toString())
    var skipped = 0

    val excludedWorkoutIds = mutableSetOf<String>()
    data.optJSONArray("workouts")?.let { rows ->
        data.put("workouts", rows.filterObjects { row ->
            val transferable = isTransferableBackupWorkoutStatus(row.optString("status", "completed"))
            if (!transferable) {
                row.optString("id").takeIf(String::isNotBlank)?.let(excludedWorkoutIds::add)
                skipped++
            }
            transferable
        })
    }

    val excludedWorkoutExerciseIds = mutableSetOf<String>()
    data.optJSONArray("workout_exercises")?.let { rows ->
        data.put("workout_exercises", rows.filterObjects { row ->
            val transferable = row.optString("workout_id") !in excludedWorkoutIds
            if (!transferable) {
                row.optString("id").takeIf(String::isNotBlank)?.let(excludedWorkoutExerciseIds::add)
                skipped++
            }
            transferable
        })
    }

    data.optJSONArray("workout_sets")?.let { rows ->
        data.put("workout_sets", rows.filterObjects { row ->
            val transferable = row.optString("workout_exercise_id") !in excludedWorkoutExerciseIds
            if (!transferable) skipped++
            transferable
        })
    }

    data.optJSONArray("app_settings")?.let { rows ->
        data.put("app_settings", rows.filterObjects { row ->
            val transferable = !isTransientBackupRuntimeSetting(row.optString("key"))
            if (!transferable) skipped++
            transferable
        })
    }

    return BackupRuntimeSanitization(data, skipped)
}

private inline fun JSONArray.filterObjects(predicate: (JSONObject) -> Boolean): JSONArray =
    JSONArray().also { output ->
        for (index in 0 until length()) {
            val row = optJSONObject(index) ?: continue
            if (predicate(row)) output.put(row)
        }
    }
