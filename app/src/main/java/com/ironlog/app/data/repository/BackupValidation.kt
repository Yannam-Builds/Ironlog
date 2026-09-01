package com.ironlog.app.data.repository

import org.json.JSONArray
import org.json.JSONObject

internal data class ValidatedBackupData(
    val data: JSONObject,
    val errors: List<String>,
    val skipped: Int,
    val warnings: List<String>,
) {
    val replacementSafe get() = errors.isEmpty() && skipped == 0
}

/** Validate once before a write transaction. Never let optInt/optString coerce corrupt rows. */
internal fun validateBackupData(source: JSONObject, existing: JSONObject? = null): ValidatedBackupData {
    val clean = JSONObject()
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val acceptedIds = mutableMapOf<String, Set<String>>()
    var skipped = 0
    var rowCount = 0
    val numberFields = setOf(
        "created_at", "updated_at", "started_at", "completed_at", "measured_at", "taken_at",
        "duration_seconds", "rating", "order_index", "set_index", "weight", "rpe", "rir",
        "rest_seconds", "bodyweight", "waist", "chest", "arm", "thigh", "contribution_fraction",
        "training_age_months", "historical_training_days_per_week", "first_verified_session_at",
        "bodyweight_kg", "weekly_goal_days", "confidence", "baseline_pushups", "baseline_pullups",
        "baseline_bench_kg", "baseline_lat_pulldown_kg", "baseline_mile_run_seconds", "total_xp",
        "level", "xp_in_level", "weekly_xp", "streak_weeks", "occurred_at", "xp_delta", "trust_score",
    )
    val booleans = setOf("is_custom", "is_bodyweight", "requires_external_load", "is_active",
        "is_warmup", "is_dropset", "is_amrap", "to_failure", "imported", "imported_history",
        "has_past_training", "has_gym_access", "invalidated")
    val longFields = setOf("created_at", "updated_at", "started_at", "completed_at", "measured_at", "taken_at",
        "first_verified_session_at", "total_xp", "xp_in_level", "occurred_at")
    val doubleFields = setOf("weight", "reps", "rating", "rpe", "rir", "bodyweight", "waist", "chest", "arm", "thigh", "contribution_fraction", "bodyweight_kg", "confidence", "trust_score")
    val parents = mapOf(
        "exercise_muscles" to listOf("exercise_id" to "exercises"),
        "plan_days" to listOf("plan_id" to "plans"),
        "plan_exercises" to listOf("plan_day_id" to "plan_days", "exercise_id" to "exercises"),
        "workout_exercises" to listOf("workout_id" to "workouts", "exercise_id" to "exercises"),
        "workout_sets" to listOf("workout_exercise_id" to "workout_exercises"),
    )
    FULL_BACKUP_DATA_SECTIONS.forEach { table ->
        val output = JSONArray()
        clean.put(table, output)
        val ids = mutableSetOf<String>()
        (existing?.optJSONArray(table) ?: JSONArray()).let { rows ->
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }?.let(ids::add)
        }
        acceptedIds[table] = ids
        if (!source.has(table)) return@forEach
        val rows = source.opt(table) as? JSONArray
        if (rows == null) {
            errors += "$table must be an array."
            return@forEach
        }
        val seenIds = mutableSetOf<String>()
        for (index in 0 until rows.length()) {
            rowCount++
            val row = rows.opt(index) as? JSONObject
            val location = "$table row ${index + 1}"
            if (row == null) { errors += "$location must be an object."; continue }
            val idField = when (table) {
                "app_settings" -> "key"
                "athlete_calibrations", "gamification_profiles" -> "offline_user_id"
                "iron_ledger_events" -> "event_id"
                else -> "id"
            }
            val id = (row.opt(idField) as? String)?.trim()
            if (id.isNullOrBlank()) { errors += "$location needs a nonempty $idField."; continue }
            if (!seenIds.add(id)) { errors += "$location repeats a stable ID."; continue }
            if (table in setOf("athlete_calibrations", "gamification_profiles") && index > 0) {
                errors += "$table must contain at most one athlete."; continue
            }
            val fields = numberFields + when (table) {
                "workout_sets" -> setOf("reps")
                "plan_exercises" -> setOf("sets")
                else -> emptySet()
            }
            val badNumbers = fields.filter { field ->
                row.has(field) && !row.isNull(field) &&
                    ((row.opt(field) as? Number)?.toDouble()?.let { value ->
                        !value.isFinite() || value < 0.0 ||
                            (field !in doubleFields && (value % 1.0 != 0.0 ||
                                if (field in longFields) value >= Long.MAX_VALUE.toDouble() else value > Int.MAX_VALUE))
                    } ?: true)
            }
            val badBooleans = booleans.filter { row.has(it) && row.opt(it) !is Boolean }
            val badStrings = row.keys().asSequence().filter { field ->
                field !in fields && field !in booleans && !row.isNull(field) && row.opt(field) !is String
            }.toList()
            if (badNumbers.isNotEmpty() || badBooleans.isNotEmpty() || badStrings.isNotEmpty()) {
                errors += "$location has invalid field types or values: ${(badNumbers + badBooleans + badStrings).joinToString()}."
                continue
            }
            if (table == "workouts" && row.has("status") && row.optString("status") !in setOf("completed", "active", "abandoned")) {
                errors += "$location has an unknown workout status."; continue
            }
            if (table == "app_settings" && row.has("value_type") && row.optString("value_type") !in setOf("string", "json", "number", "boolean")) {
                errors += "$location has an unknown setting type."; continue
            }
            if (parents[table].orEmpty().any { (field, parent) ->
                    (row.opt(field) as? String)?.trim() !in acceptedIds[parent].orEmpty()
                }) {
                skipped++
                continue
            }
            val copy = JSONObject(row.toString())
            copy.put(idField, id)
            parents[table].orEmpty().forEach { (field, _) -> copy.put(field, copy.getString(field).trim()) }
            ids.add(id)
            output.put(copy)
        }
    }
    if (rowCount == 0 && !FULL_BACKUP_DATA_SECTIONS.all(source::has)) {
        errors += "No importable data. An intentional empty backup must include every data section."
    }
    if (skipped > 0) warnings += "$skipped orphan rows excluded. Merge can import the valid rows; replacement is blocked."
    return ValidatedBackupData(clean, errors.take(30), skipped, warnings)
}
