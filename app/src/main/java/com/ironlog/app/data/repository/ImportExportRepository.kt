package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity_
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.BodyMeasurementEntity_
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity_
import com.ironlog.app.data.objectbox.GamificationProfileEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity_
import com.ironlog.app.data.objectbox.IronLedgerEventEntity
import com.ironlog.app.data.objectbox.IronLedgerEventEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.PlanDayEntity
import com.ironlog.app.data.objectbox.PlanDayEntity_
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.PlanEntity_
import com.ironlog.app.data.objectbox.PlanExerciseEntity
import com.ironlog.app.data.objectbox.PlanExerciseEntity_
import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import com.ironlog.app.data.objectbox.ProgressPhotoEntity_
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutImportProvenanceMigration
import com.ironlog.app.data.objectbox.WorkoutSetEntity
import com.ironlog.app.data.objectbox.WorkoutSetEntity_
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max

internal val FULL_BACKUP_DATA_SECTIONS = setOf(
    "exercises",
    "exercise_muscles",
    "plans",
    "plan_days",
    "plan_exercises",
    "workouts",
    "workout_exercises",
    "workout_sets",
    "body_measurements",
    "progress_photos",
    "app_settings",
    "athlete_calibrations",
    "gamification_profiles",
    "iron_ledger_events",
)

internal fun parseImportEpochMillis(
    value: Any?,
    fallback: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long = when (value) {
    is Number -> value.toLong()
    is String -> value.trim().takeIf { it.isNotEmpty() }?.let { raw ->
        raw.toLongOrNull()
            ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(raw).atStartOfDay(zoneId).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw.replace(" ", "T")).atZone(zoneId).toInstant().toEpochMilli() }.getOrNull()
    } ?: fallback
    else -> fallback
}

data class ImportCounts(
    val exercises: Int = 0,
    val exerciseMuscles: Int = 0,
    val plans: Int = 0,
    val planDays: Int = 0,
    val planExercises: Int = 0,
    val workouts: Int = 0,
    val workoutExercises: Int = 0,
    val sets: Int = 0,
    val bodyMeasurements: Int = 0,
    val progressPhotos: Int = 0,
    val settings: Int = 0,
    val athleteCalibrations: Int = 0,
    val gamificationProfiles: Int = 0,
    val ironLedgerEvents: Int = 0,
)

data class ImportPreview(
    val valid: Boolean,
    val format: String = "unknown",
    val convertedFrom: String? = null,
    val reason: String? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val counts: ImportCounts = ImportCounts(),
    val workouts: Int = 0,
    val plans: Int = 0,
    val sets: Int = 0,
    val bodyMeasurements: Int = 0,
    val settings: Int = 0,
    val customExercises: List<String> = emptyList(),
    val duplicateExerciseNames: List<String> = emptyList(),
    val relationshipWarnings: List<String> = emptyList(),
    val unsupportedRows: Int = 0,
)

class ImportExportRepository(
) {
    companion object {
        const val EXPORT_TYPE = "ironlog_watermelon_export"
        const val EXPORT_VERSION = 1
    }

    suspend fun exportDatabase(): JSONObject = withContext(Dispatchers.IO) {
        ObjectBox.store.callInReadTx {
        val exercises = ObjectBox.store.boxFor(ExerciseEntity::class.java).all
        val exerciseMuscles = ObjectBox.store.boxFor(ExerciseMuscleEntity::class.java).all
        val plans = ObjectBox.store.boxFor(PlanEntity::class.java).all
        val planDays = ObjectBox.store.boxFor(PlanDayEntity::class.java).all
        val planExercises = ObjectBox.store.boxFor(PlanExerciseEntity::class.java).all
        val workouts = ObjectBox.store.boxFor(WorkoutEntity::class.java).all
        val workoutExercises = ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java).all
        val workoutSets = ObjectBox.store.boxFor(WorkoutSetEntity::class.java).all
        val body = ObjectBox.store.boxFor(BodyMeasurementEntity::class.java).all
        val photos = ObjectBox.store.boxFor(ProgressPhotoEntity::class.java).all
        val settings = ObjectBox.store.boxFor(AppSettingEntity::class.java).all
        val athleteCalibrations = ObjectBox.store.boxFor(AthleteCalibrationEntity::class.java).all
        val gamificationProfiles = ObjectBox.store.boxFor(GamificationProfileEntity::class.java).all
        val ironLedgerEvents = ObjectBox.store.boxFor(IronLedgerEventEntity::class.java).all

        JSONObject()
            .put("version", EXPORT_VERSION)
            .put("type", EXPORT_TYPE)
            .put("exportedAt", java.time.Instant.now().toString())
            .put(
                "data",
                JSONObject()
                    .put("exercises", JSONArray().apply { exercises.forEach { put(JSONObject().put("id", it.uid).put("name", it.name).put("normalized_name", it.normalizedName).put("primary_muscle", it.primaryMuscle).put("equipment", it.equipment).put("category", it.category).put("is_custom", it.isCustom).put("source", it.source).put("notes", it.notes).put("equipment_detail", it.equipmentDetail ?: JSONObject.NULL).put("apparatus_json", it.apparatusJson ?: JSONObject.NULL).put("tracking_type", it.trackingType ?: JSONObject.NULL).put("is_bodyweight", it.isBodyweight).put("requires_external_load", it.requiresExternalLoad).put("movement_pattern", it.movementPattern ?: JSONObject.NULL).put("difficulty", it.difficulty ?: JSONObject.NULL).put("aliases_json", it.aliasesJson ?: JSONObject.NULL).put("source_tags_json", it.sourceTagsJson ?: JSONObject.NULL).put("secondary_muscles_json", it.secondaryMusclesJson ?: JSONObject.NULL).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("exercise_muscles", JSONArray().apply { exerciseMuscles.forEach { put(JSONObject().put("id", it.uid).put("exercise_id", it.exerciseUid).put("muscle", it.muscle).put("role", it.role).put("contribution_fraction", it.contributionFraction).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("plans", JSONArray().apply { plans.forEach { put(JSONObject().put("id", it.uid).put("name", it.name).put("goal", it.goal).put("description", it.description).put("is_active", it.isActive).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("plan_days", JSONArray().apply { planDays.forEach { put(JSONObject().put("id", it.uid).put("plan_id", it.planUid).put("name", it.name).put("color", it.color).put("order_index", it.orderIndex).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("plan_exercises", JSONArray().apply { planExercises.forEach { put(JSONObject().put("id", it.uid).put("plan_day_id", it.planDayUid).put("exercise_id", it.exerciseUid).put("order_index", it.orderIndex).put("sets", it.sets).put("reps", it.reps).put("rest_seconds", it.restSeconds).put("superset_group", it.supersetGroup).put("is_warmup", it.isWarmup).put("notes", it.notes).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("workouts", JSONArray().apply { workouts.forEach { put(JSONObject().put("id", it.uid).put("plan_id", it.planUid ?: "").put("plan_day_id", it.planDayUid ?: "").put("name", it.name).put("started_at", it.startedAt).put("completed_at", it.completedAt ?: JSONObject.NULL).put("duration_seconds", it.durationSeconds).put("rating", it.rating ?: JSONObject.NULL).put("notes", it.notes).put("status", it.status).put("imported", it.imported).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("workout_exercises", JSONArray().apply { workoutExercises.forEach { put(JSONObject().put("id", it.uid).put("workout_id", it.workoutUid).put("exercise_id", it.exerciseUid).put("order_index", it.orderIndex).put("superset_group", it.supersetGroup).put("notes", it.notes).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("workout_sets", JSONArray().apply { workoutSets.forEach { put(JSONObject().put("id", it.uid).put("workout_exercise_id", it.workoutExerciseUid).put("set_index", it.setIndex).put("weight", it.weight).put("reps", it.reps).put("rpe", it.rpe ?: JSONObject.NULL).put("rir", it.rir ?: JSONObject.NULL).put("rest_seconds", it.restSeconds).put("is_warmup", it.isWarmup).put("is_dropset", it.isDropset).put("is_amrap", it.isAmrap).put("to_failure", it.toFailure).put("completed_at", it.completedAt ?: JSONObject.NULL).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("body_measurements", JSONArray().apply { body.forEach { put(JSONObject().put("id", it.uid).put("measured_at", it.measuredAt).put("bodyweight", it.bodyweight ?: JSONObject.NULL).put("waist", it.waist ?: JSONObject.NULL).put("chest", it.chest ?: JSONObject.NULL).put("arm", it.arm ?: JSONObject.NULL).put("thigh", it.thigh ?: JSONObject.NULL).put("notes", it.notes).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("progress_photos", JSONArray().apply { photos.forEach { put(JSONObject().put("id", it.uid).put("file_uri", it.fileUri).put("taken_at", it.takenAt).put("bodyweight", it.bodyweight ?: JSONObject.NULL).put("notes", it.notes).put("created_at", it.createdAt).put("updated_at", it.updatedAt)) } })
                    .put("app_settings", JSONArray().apply { settings.forEach { put(JSONObject().put("id", it.key).put("key", it.key).put("value", it.value).put("value_type", it.valueType).put("updated_at", it.updatedAt)) } })
                    .put("athlete_calibrations", JSONArray().apply {
                        athleteCalibrations.forEach {
                            put(
                                JSONObject()
                                    .put("offline_user_id", it.offlineUserId)
                                    .put("training_age_months", it.trainingAgeMonths)
                                    .put("historical_training_days_per_week", it.historicalTrainingDaysPerWeek)
                                    .put("first_verified_session_at", it.firstVerifiedSessionAt)
                                    .put("weight_unit", it.weightUnit)
                                    .put("bodyweight_kg", it.bodyweightKg ?: JSONObject.NULL)
                                    .put("goal_mode", it.goalMode)
                                    .put("weekly_goal_days", it.weeklyGoalDays)
                                    .put("imported_history", it.importedHistory)
                                    .put("confidence", it.confidence)
                                    .put("updated_at", it.updatedAt)
                                    .put("has_past_training", it.hasPastTraining)
                                    .put("has_gym_access", it.hasGymAccess)
                                    .put("baseline_pushups", it.baselinePushups)
                                    .put("baseline_pullups", it.baselinePullups)
                                    .put("baseline_bench_kg", it.baselineBenchKg)
                                    .put("baseline_lat_pulldown_kg", it.baselineLatPulldownKg)
                                    .put("baseline_mile_run_seconds", it.baselineMileRunSeconds)
                            )
                        }
                    })
                    .put("gamification_profiles", JSONArray().apply {
                        gamificationProfiles.forEach {
                            put(
                                JSONObject()
                                    .put("offline_user_id", it.offlineUserId)
                                    .put("total_xp", it.totalXp)
                                    .put("level", it.level)
                                    .put("xp_in_level", it.xpInLevel)
                                    .put("rank", it.rank)
                                    .put("active_title", it.activeTitle)
                                    .put("stats_json", it.statsJson)
                                    .put("weekly_xp", it.weeklyXp)
                                    .put("weekly_xp_reset_week", it.weeklyXpResetWeek)
                                    .put("streak_weeks", it.streakWeeks)
                                    .put("recovery_circuit_completions_json", it.makeupCompletionsJson)
                                    .put("unlocked_badges", it.unlockedBadges)
                            )
                        }
                    })
                    .put("iron_ledger_events", JSONArray().apply {
                        ironLedgerEvents.forEach {
                            put(
                                JSONObject()
                                    .put("event_id", it.eventId)
                                    .put("source_type", it.sourceType)
                                    .put("source_id", it.sourceId)
                                    .put("event_kind", it.eventKind)
                                    .put("occurred_at", it.occurredAt)
                                    .put("xp_delta", it.xpDelta)
                                    .put("stat_deltas_json", it.statDeltasJson)
                                    .put("trust_score", it.trustScore)
                                    .put("fingerprint", it.fingerprint)
                                    .put("metadata_json", it.metadataJson)
                                    .put("invalidated", it.invalidated)
                            )
                        }
                    }),
            )
        }
    }

    private fun isIronLogExport(payload: JSONObject): Boolean {
        return payload.optString("type") == EXPORT_TYPE && payload.optInt("version") == 1
    }

    private fun isLegacySQLiteBundle(payload: JSONObject): Boolean {
        val schema = payload.optString("schema", "")
        return schema.startsWith("IRONLOG_SQLITE_EXPORT_V") && payload.optJSONObject("payload") != null
    }

    private fun detectImportFormat(payload: JSONObject): String {
        if (isIronLogExport(payload)) return "ironlog_v1"
        if (isLegacySQLiteBundle(payload)) return "sqlite_v1"
        if (payload.has("domains") || payload.has("plans") || payload.has("history") || payload.has("bodyWeight") || payload.has("customExercises") || payload.optJSONObject("payload")?.has("plans") == true) {
            return "legacy_async"
        }
        return "unknown"
    }

    private fun countRows(data: JSONObject?, table: String): Int {
        return data?.optJSONArray(table)?.length() ?: 0
    }

    private fun buildImportCounts(data: JSONObject?): ImportCounts {
        return ImportCounts(
            exercises = countRows(data, "exercises"),
            exerciseMuscles = countRows(data, "exercise_muscles"),
            plans = countRows(data, "plans"),
            planDays = countRows(data, "plan_days"),
            planExercises = countRows(data, "plan_exercises"),
            workouts = countRows(data, "workouts"),
            workoutExercises = countRows(data, "workout_exercises"),
            sets = countRows(data, "workout_sets"),
            bodyMeasurements = countRows(data, "body_measurements"),
            progressPhotos = countRows(data, "progress_photos"),
            settings = countRows(data, "app_settings"),
            athleteCalibrations = countRows(data, "athlete_calibrations"),
            gamificationProfiles = countRows(data, "gamification_profiles"),
            ironLedgerEvents = countRows(data, "iron_ledger_events"),
        )
    }

    private fun findDuplicateExerciseNames(exercises: JSONArray?): List<String> {
        if (exercises == null) return emptyList()
        val seen = mutableMapOf<String, MutableList<String>>()
        for (i in 0 until exercises.length()) {
            val row = exercises.optJSONObject(i) ?: continue
            val norm = row.optString("normalized_name").ifEmpty { row.optString("name") }
            val key = norm.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
            if (key.isEmpty()) continue
            if (!seen.containsKey(key)) seen[key] = mutableListOf()
            seen[key]!!.add(row.optString("name").ifEmpty { row.optString("normalized_name", key) })
        }
        return seen.values.filter { it.size > 1 }.map { it.first() }.take(12)
    }

    private fun buildRelationshipWarnings(data: JSONObject?): List<String> {
        val warnings = mutableListOf<String>()
        if (data == null) return warnings
        fun ids(table: String) = (data.optJSONArray(table) ?: JSONArray()).let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id") }.toSet()
        }
        val planIds = ids("plans")
        val planDayIds = ids("plan_days")
        val exerciseIds = ids("exercises")
        val workoutIds = ids("workouts")
        val workoutExerciseIds = ids("workout_exercises")

        val missingPlanDays = (data.optJSONArray("plan_days") ?: JSONArray()).let { arr ->
            (0 until arr.length()).count { arr.optJSONObject(it)?.optString("plan_id")?.isNotBlank() == true && !planIds.contains(arr.optJSONObject(it)?.optString("plan_id")) }
        }
        if (missingPlanDays > 0) warnings.add("$missingPlanDays plan day${if(missingPlanDays==1) "" else "s"} reference missing plans.")

        val missingPlanExerciseDays = (data.optJSONArray("plan_exercises") ?: JSONArray()).let { arr ->
            (0 until arr.length()).count { arr.optJSONObject(it)?.optString("plan_day_id")?.isNotBlank() == true && !planDayIds.contains(arr.optJSONObject(it)?.optString("plan_day_id")) }
        }
        if (missingPlanExerciseDays > 0) warnings.add("$missingPlanExerciseDays planned exercise${if(missingPlanExerciseDays==1) "" else "s"} reference missing days.")

        val missingPlanExerciseExercises = (data.optJSONArray("plan_exercises") ?: JSONArray()).let { arr ->
            if (exerciseIds.isEmpty()) 0 else (0 until arr.length()).count { arr.optJSONObject(it)?.optString("exercise_id")?.isNotBlank() == true && !exerciseIds.contains(arr.optJSONObject(it)?.optString("exercise_id")) }
        }
        if (missingPlanExerciseExercises > 0) warnings.add("$missingPlanExerciseExercises planned exercise${if(missingPlanExerciseExercises==1) "" else "s"} reference missing exercises.")

        val missingWorkoutExerciseWorkouts = (data.optJSONArray("workout_exercises") ?: JSONArray()).let { arr ->
            (0 until arr.length()).count { arr.optJSONObject(it)?.optString("workout_id")?.isNotBlank() == true && !workoutIds.contains(arr.optJSONObject(it)?.optString("workout_id")) }
        }
        if (missingWorkoutExerciseWorkouts > 0) warnings.add("$missingWorkoutExerciseWorkouts workout exercise${if(missingWorkoutExerciseWorkouts==1) "" else "s"} reference missing workouts.")

        val missingWorkoutExerciseExercises = (data.optJSONArray("workout_exercises") ?: JSONArray()).let { arr ->
            if (exerciseIds.isEmpty()) 0 else (0 until arr.length()).count { arr.optJSONObject(it)?.optString("exercise_id")?.isNotBlank() == true && !exerciseIds.contains(arr.optJSONObject(it)?.optString("exercise_id")) }
        }
        if (missingWorkoutExerciseExercises > 0) warnings.add("$missingWorkoutExerciseExercises workout exercise${if(missingWorkoutExerciseExercises==1) "" else "s"} reference missing exercises.")

        val missingSetParents = (data.optJSONArray("workout_sets") ?: JSONArray()).let { arr ->
            (0 until arr.length()).count { arr.optJSONObject(it)?.optString("workout_exercise_id")?.isNotBlank() == true && !workoutExerciseIds.contains(arr.optJSONObject(it)?.optString("workout_exercise_id")) }
        }
        if (missingSetParents > 0) warnings.add("$missingSetParents set${if(missingSetParents==1) "" else "s"} reference missing workout exercises.")

        return warnings
    }

    private fun getUnsupportedRows(data: JSONObject?): Int {
        if (data == null) return 0
        var count = 0
        data.keys().forEach { if (it !in FULL_BACKUP_DATA_SECTIONS) count++ }
        return count
    }

    fun previewImportPayload(text: String): ImportPreview {
        val raw = runCatching { JSONObject(text) }
            .onFailure { Timber.w(it, "previewImportPayload: invalid JSON") }
            .getOrNull() ?: return ImportPreview(valid = false, format = "invalid_json", reason = "Invalid JSON", errors = listOf("Invalid JSON"))
        val format = detectImportFormat(raw)
        if (format == "unknown") return ImportPreview(valid = false, format = format, reason = "Unsupported payload format.", errors = listOf("Unsupported payload format."))

        var normalizedPayload = raw
        var convertedFrom: String? = null
        val warnings = mutableListOf<String>()

        try {
            if (format != "ironlog_v1") {
                val conv = convertLegacyToIronLogExport(raw)
                normalizedPayload = conv
                convertedFrom = if (isLegacySQLiteBundle(raw)) "sqlite_v1" else "legacy_async"
                warnings.add("Legacy $convertedFrom backup will be normalized into IronLog format.")
            }
        } catch (e: Exception) {
            return ImportPreview(valid = false, format = format, reason = e.message ?: "Normalization failed", errors = listOf(e.message ?: "Normalization failed"))
        }

        if (normalizedPayload.optString("type") != EXPORT_TYPE || normalizedPayload.optInt("version") != EXPORT_VERSION) {
            return ImportPreview(valid = false, format = format, reason = "Unsupported backup format", errors = listOf("Unsupported backup format"))
        }
        val data = normalizedPayload.optJSONObject("data") ?: return ImportPreview(valid = false, format = format, reason = "Missing data object", errors = listOf("Missing data object"))
        
        val counts = buildImportCounts(data)
        val duplicateExerciseNames = findDuplicateExerciseNames(data.optJSONArray("exercises"))
        val relationshipWarnings = buildRelationshipWarnings(data)
        val unsupportedRows = getUnsupportedRows(data)
        
        val customExercisesArr = data.optJSONArray("exercises") ?: JSONArray()
        val customExercises = mutableListOf<String>()
        for (i in 0 until customExercisesArr.length()) {
            val ex = customExercisesArr.optJSONObject(i) ?: continue
            if (ex.optBoolean("is_custom", false) || ex.optString("source") == "user_custom") {
                val name = ex.optString("name")
                if (name.isNotBlank()) customExercises.add(name)
            }
        }

        if (duplicateExerciseNames.isNotEmpty()) warnings.add("${duplicateExerciseNames.size} duplicate exercise name group${if (duplicateExerciseNames.size == 1) "" else "s"} detected in this file.")
        warnings.addAll(relationshipWarnings)
        if (unsupportedRows > 0) warnings.add("$unsupportedRows unsupported data section${if (unsupportedRows == 1) "" else "s"} will be ignored.")

        return ImportPreview(
            valid = true,
            format = format,
            convertedFrom = convertedFrom,
            reason = null,
            errors = emptyList(),
            warnings = warnings,
            counts = counts,
            workouts = counts.workouts,
            plans = counts.plans,
            sets = counts.sets,
            bodyMeasurements = counts.bodyMeasurements,
            settings = counts.settings,
            customExercises = customExercises.take(20),
            duplicateExerciseNames = duplicateExerciseNames,
            relationshipWarnings = relationshipWarnings,
            unsupportedRows = unsupportedRows
        )
    }

    suspend fun runConfirmedImport(text: String, mode: String = "replace"): ImportPreview = withContext(Dispatchers.IO) {
        val raw = runCatching { JSONObject(text) }
            .onFailure { Timber.e(it, "runConfirmedImport: failed to parse backup JSON") }
            .getOrElse { throw IllegalArgumentException("Corrupt or empty backup file: ${it.message}", it) }
        val preview = previewImportPayload(text)
        require(preview.valid) { preview.reason ?: "Invalid payload" }
        val normalized = normalizePayloadForImport(raw)
        backfillMissingAthleteStateRows(normalized.getJSONObject("data"))
        val hasImportedExercises = (normalized.optJSONObject("data")?.optJSONArray("exercises")?.length() ?: 0) > 0
        ObjectBox.store.runInTx {
            if (mode == "replace") clearUserDataTables(clearExercises = hasImportedExercises)
            importData(normalized.getJSONObject("data"), mergeSingletons = mode != "replace")
        }
        // Only flag as imported-history when actual workout rows were present
        val importedWorkouts = (normalized.optJSONObject("data")?.optJSONArray("workouts")?.length() ?: 0)
        if (importedWorkouts > 0) {
            ObjectBox.store.runInTx { markLedgerImportedHistory() }
        }
        WorkoutImportProvenanceMigration.run()
        preview
    }

    private fun markLedgerImportedHistory() {
        val now = System.currentTimeMillis()
        val settingsBox = ObjectBox.store.boxFor(AppSettingEntity::class.java)
        settingsBox.put(AppSettingEntity().apply {
            key = "ledger_imported_history"
            value = "true"
            valueType = "boolean"
            updatedAt = now
        })
    }

    private fun normalizePayloadForImport(raw: JSONObject): JSONObject {
        if (raw.optString("type") == EXPORT_TYPE && raw.optInt("version") == EXPORT_VERSION && raw.optJSONObject("data") != null) return raw
        return convertLegacyToIronLogExport(raw)
    }

    private fun convertLegacyToIronLogExport(raw: JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        val data = JSONObject()
            .put("exercises", JSONArray())
            .put("exercise_muscles", JSONArray())
            .put("plans", JSONArray())
            .put("plan_days", JSONArray())
            .put("plan_exercises", JSONArray())
            .put("workouts", JSONArray())
            .put("workout_exercises", JSONArray())
            .put("workout_sets", JSONArray())
            .put("body_measurements", JSONArray())
            .put("progress_photos", JSONArray())
            .put("app_settings", JSONArray())
            .put("athlete_calibrations", JSONArray())
            .put("gamification_profiles", JSONArray())
            .put("iron_ledger_events", JSONArray())

        val (snapshot, appState) = extractLegacySnapshot(raw)
        val exerciseByNorm = linkedMapOf<String, String>()
        var exCounter = 0
        var planCounter = 0
        var workoutCounter = 0
        var bodyCounter = 0

        fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
        fun normalizeKey(name: String): String = normalizeName(name).lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        fun stableId(prefix: String, rawId: String?, index: Int): String {
            val cleaned = rawId?.trim().orEmpty()
            return if (cleaned.isNotBlank()) "legacy_${prefix}_${cleaned.replace(Regex("[^a-zA-Z0-9_-]"), "_")}" else "legacy_${prefix}_${prefix.hashCode().and(0xFFFFFF).toString(16)}_${index}"
        }
        fun ensureExercise(obj: JSONObject): String {
            val name = normalizeName(obj.optString("name", obj.optString("exerciseName", obj.optString("title", "Exercise"))))
            val norm = normalizeKey(name)
            exerciseByNorm[norm]?.let { return it }
            exCounter++
            val id = stableId("exercise", obj.optString("id"), exCounter)
            val primary = obj.optString("primaryMuscle", obj.optString("muscleGroup", obj.optString("muscle", "Other")))
            val equipment = obj.optString("equipment", "Other")
            val category = obj.optString("category", obj.optString("type", "strength"))
            val isCustom = if (obj.has("isCustom")) obj.optBoolean("isCustom", true) else true
            data.getJSONArray("exercises").put(
                JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("normalized_name", norm)
                    .put("primary_muscle", primary)
                    .put("equipment", equipment)
                    .put("category", category)
                    .put("is_custom", isCustom)
                    .put("source", if (isCustom) "user_custom" else "import")
                    .put("notes", obj.optString("notes"))
                    .put("created_at", now)
                    .put("updated_at", now),
            )
            data.getJSONArray("exercise_muscles").put(
                JSONObject()
                    .put("id", stableId("exercise_muscle", "${id}_primary", exCounter))
                    .put("exercise_id", id)
                    .put("muscle", primary)
                    .put("role", "primary")
                    .put("contribution_fraction", 1.0)
                    .put("created_at", now)
                    .put("updated_at", now),
            )
            exerciseByNorm[norm] = id
            return id
        }

        (snapshot.optJSONArray("customExercises") ?: JSONArray()).forEachObject { ensureExercise(it) }

        (snapshot.optJSONArray("plans") ?: JSONArray()).forEachObject { plan ->
            planCounter++
            val planId = stableId("plan", plan.optString("id"), planCounter)
            data.getJSONArray("plans").put(
                JSONObject()
                    .put("id", planId)
                    .put("name", plan.optString("name", "Imported Plan $planCounter"))
                    .put("goal", plan.optString("goal", "General Fitness"))
                    .put("description", plan.optString("description"))
                    .put("is_active", plan.optBoolean("isActive", false))
                    .put("created_at", now)
                    .put("updated_at", now),
            )
            val days = if (plan.optJSONArray("days") != null) plan.optJSONArray("days")!! else (plan.optJSONArray("workoutDays") ?: JSONArray())
            for (dIdx in 0 until days.length()) {
                val day = days.optJSONObject(dIdx) ?: continue
                val dayId = stableId("plan_day", day.optString("id"), dIdx + 1)
                data.getJSONArray("plan_days").put(
                    JSONObject()
                        .put("id", dayId)
                        .put("plan_id", planId)
                        .put("name", day.optString("name", day.optString("title", "Day ${dIdx + 1}")))
                        .put("color", day.optString("color", "#FF4500"))
                        .put("order_index", dIdx)
                        .put("created_at", now)
                        .put("updated_at", now),
                )
                val exercises = if (day.optJSONArray("exercises") != null) day.optJSONArray("exercises")!! else (day.optJSONArray("items") ?: JSONArray())
                for (eIdx in 0 until exercises.length()) {
                    val ex = exercises.optJSONObject(eIdx) ?: continue
                    val exId = ensureExercise(ex)
                    data.getJSONArray("plan_exercises").put(
                        JSONObject()
                            .put("id", stableId("plan_exercise", ex.optString("id"), eIdx + 1))
                            .put("plan_day_id", dayId)
                            .put("exercise_id", exId)
                            .put("order_index", eIdx)
                            .put("sets", max(1, ex.optInt("sets", 3)))
                            .put("reps", ex.optString("reps", ex.optString("repRange", "8-12")))
                            .put("rest_seconds", max(0, ex.optInt("restSeconds", ex.optInt("rest", 90))))
                            .put("superset_group", ex.optString("supersetGroup"))
                            .put("is_warmup", ex.optBoolean("isWarmup", false))
                            .put("notes", ex.optString("notes"))
                            .put("created_at", now)
                            .put("updated_at", now),
                    )
                }
            }
        }

        (snapshot.optJSONArray("history") ?: JSONArray()).forEachObject { workout ->
            workoutCounter++
            val dateFallback = parseImportEpochMillis(workout.opt("date"), now)
            val startedAt = parseImportEpochMillis(
                workout.opt("startedAt").takeUnless { it == null || it == JSONObject.NULL }
                    ?: workout.opt("timestamp"),
                dateFallback,
            )
            val completedAt = parseImportEpochMillis(workout.opt("completedAt"), startedAt)
            val workoutId = stableId("workout", workout.optString("id"), workoutCounter)
            data.getJSONArray("workouts").put(
                JSONObject()
                    .put("id", workoutId)
                    .put("plan_id", "")
                    .put("plan_day_id", "")
                    .put("name", workout.optString("name", workout.optString("day", "Imported Workout $workoutCounter")))
                    .put("started_at", startedAt)
                    .put("completed_at", completedAt)
                    .put("duration_seconds", max(0, workout.optInt("durationSeconds", workout.optInt("duration", 0))))
                    .put("rating", if (workout.has("rating")) workout.optDouble("rating") else JSONObject.NULL)
                    .put("notes", workout.optString("notes"))
                    .put("status", workout.optString("status", "completed"))
                    .put("imported", true)
                    .put("created_at", startedAt)
                    .put("updated_at", completedAt),
            )
            val wExercises = if (workout.optJSONArray("exercises") != null) workout.optJSONArray("exercises")!! else (workout.optJSONArray("items") ?: JSONArray())
            for (eIdx in 0 until wExercises.length()) {
                val ex = wExercises.optJSONObject(eIdx) ?: continue
                val workoutExerciseId = stableId("workout_exercise", ex.optString("id"), eIdx + 1)
                val exerciseId = ensureExercise(ex)
                data.getJSONArray("workout_exercises").put(
                    JSONObject()
                        .put("id", workoutExerciseId)
                        .put("workout_id", workoutId)
                        .put("exercise_id", exerciseId)
                        .put("order_index", eIdx)
                        .put("superset_group", ex.optString("supersetGroup"))
                        .put("notes", ex.optString("notes"))
                        .put("created_at", startedAt)
                        .put("updated_at", completedAt),
                )
                val sets = if (ex.optJSONArray("sets") != null) ex.optJSONArray("sets")!! else (ex.optJSONArray("logs") ?: JSONArray())
                for (sIdx in 0 until sets.length()) {
                    val set = sets.optJSONObject(sIdx) ?: continue
                    data.getJSONArray("workout_sets").put(
                        JSONObject()
                            .put("id", stableId("workout_set", set.optString("id"), sIdx + 1))
                            .put("workout_exercise_id", workoutExerciseId)
                            .put("set_index", sIdx + 1)
                            .put("weight", max(0.0, set.optDouble("weight", 0.0)))
                            .put("reps", max(0.0, set.optDouble("reps", 0.0)))
                            .put("rpe", if (set.has("rpe")) set.optDouble("rpe") else JSONObject.NULL)
                            .put("rir", if (set.has("rir")) set.optDouble("rir") else JSONObject.NULL)
                            .put("rest_seconds", max(0, set.optInt("restSeconds", set.optInt("rest", 0))))
                            .put("is_warmup", set.optBoolean("isWarmup", false))
                            .put("is_dropset", set.optBoolean("isDropset", false))
                            .put("is_amrap", set.optBoolean("isAmrap", set.optBoolean("isAMRAP", false)))
                            .put("to_failure", set.optBoolean("toFailure", false))
                            .put("completed_at", completedAt)
                            .put("created_at", startedAt)
                            .put("updated_at", completedAt),
                    )
                }
            }
        }

        (snapshot.optJSONArray("bodyWeight") ?: JSONArray()).forEachObject { row ->
            bodyCounter++
            val measuredAt = parseImportEpochMillis(
                row.opt("measuredAt").takeUnless { it == null || it == JSONObject.NULL } ?: row.opt("date"),
                now + bodyCounter,
            )
            data.getJSONArray("body_measurements").put(
                JSONObject()
                    .put("id", stableId("body", row.optString("id"), bodyCounter))
                    .put("measured_at", measuredAt)
                    .put("bodyweight", row.optDouble("weight", row.optDouble("bodyweight", Double.NaN)).takeIf { it.isFinite() } ?: JSONObject.NULL)
                    .put("waist", JSONObject.NULL)
                    .put("chest", JSONObject.NULL)
                    .put("arm", JSONObject.NULL)
                    .put("thigh", JSONObject.NULL)
                    .put("notes", row.optString("notes"))
                    .put("created_at", measuredAt)
                    .put("updated_at", measuredAt),
            )
        }
        (snapshot.optJSONArray("bodyMeasurements") ?: JSONArray()).forEachObject { row ->
            bodyCounter++
            val measuredAt = parseImportEpochMillis(
                row.opt("measuredAt").takeUnless { it == null || it == JSONObject.NULL } ?: row.opt("date"),
                now + bodyCounter,
            )
            data.getJSONArray("body_measurements").put(
                JSONObject()
                    .put("id", stableId("body_measure", row.optString("id"), bodyCounter))
                    .put("measured_at", measuredAt)
                    .put("bodyweight", row.optDouble("bodyweight", Double.NaN).takeIf { it.isFinite() } ?: JSONObject.NULL)
                    .put("waist", row.optDouble("waist", Double.NaN).takeIf { it.isFinite() } ?: JSONObject.NULL)
                    .put("chest", row.optDouble("chest", Double.NaN).takeIf { it.isFinite() } ?: JSONObject.NULL)
                    .put("arm", row.optDouble("arm", Double.NaN).takeIf { it.isFinite() } ?: JSONObject.NULL)
                    .put("thigh", row.optDouble("thigh", Double.NaN).takeIf { it.isFinite() } ?: JSONObject.NULL)
                    .put("notes", row.optString("notes"))
                    .put("created_at", measuredAt)
                    .put("updated_at", measuredAt),
            )
        }

        appState.keys().forEach { key ->
            val value = appState.opt(key)
            val type = when (value) {
                is Boolean -> "boolean"
                is Number -> "number"
                is String -> "string"
                else -> "json"
            }
            data.getJSONArray("app_settings").put(
                JSONObject()
                    .put("id", "setting_$key")
                    .put("key", key)
                    .put("value", if (type == "json") JSONObject.wrap(value)?.toString().orEmpty() else value?.toString().orEmpty())
                    .put("value_type", type)
                    .put("updated_at", now),
            )
        }

        return JSONObject()
            .put("version", EXPORT_VERSION)
            .put("type", EXPORT_TYPE)
            .put("exportedAt", java.time.Instant.now().toString())
            .put("data", data)
    }

    private fun extractLegacySnapshot(raw: JSONObject): Pair<JSONObject, JSONObject> {
        val schema = raw.optString("schema")
        val isSqlite = schema.startsWith("IRONLOG_SQLITE_EXPORT_V") && raw.optJSONObject("payload") != null
        if (isSqlite) {
            return Pair(raw.optJSONObject("payload") ?: JSONObject(), raw.optJSONObject("appState") ?: JSONObject())
        }
        val root = if (raw.optJSONObject("payload") != null) raw.optJSONObject("payload")!! else raw
        val snapshot = JSONObject()
            .put("plans", legacyArray(root, raw, "plans", "ironlog_plans"))
            .put("history", legacyArray(root, raw, "history", "ironlog_history"))
            .put("bodyWeight", legacyArray(root, raw, "bodyWeight", "ironlog_bw"))
            .put("bodyMeasurements", legacyArray(root, raw, "bodyMeasurements", "@ironlog/bodyMeasurements"))
            .put("customExercises", legacyArray(root, raw, "customExercises", "@ironlog/customExercises"))
        val appState = when {
            raw.optJSONObject("appState") != null -> raw.optJSONObject("appState")!!
            root.optJSONObject("settings") != null -> root.optJSONObject("settings")!!
            else -> legacyDomainObject(raw, "ironlog_settings")
        }
        return Pair(snapshot, appState)
    }

    private fun legacyArray(root: JSONObject, raw: JSONObject, rootKey: String, domainKey: String): JSONArray {
        val direct = root.opt(rootKey)
        if (direct is JSONArray) return direct
        if (direct is String) runCatching { JSONArray(direct) }.getOrNull()?.let { return it }
        return legacyDomainItem(raw, domainKey) ?: JSONArray()
    }

    private fun legacyDomainObject(raw: JSONObject, key: String): JSONObject {
        val domains = raw.optJSONObject("domains") ?: return JSONObject()
        val names = domains.keys()
        while (names.hasNext()) {
            val d = domains.optJSONObject(names.next()) ?: continue
            val items = d.optJSONObject("items") ?: continue
            val v = items.opt(key) ?: continue
            if (v is JSONObject) return v
            if (v is String) {
                runCatching { JSONObject(v) }.getOrNull()?.let { return it }
            }
        }
        return JSONObject()
    }

    private fun legacyDomainItem(raw: JSONObject, key: String): JSONArray? {
        val domains = raw.optJSONObject("domains") ?: return null
        val names = domains.keys()
        while (names.hasNext()) {
            val d = domains.optJSONObject(names.next()) ?: continue
            val items = d.optJSONObject("items") ?: continue
            val v = items.opt(key) ?: continue
            if (v is JSONArray) return v
            if (v is String) {
                runCatching { JSONArray(v) }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun clearUserDataTables(clearExercises: Boolean) {
        ObjectBox.store.boxFor(WorkoutSetEntity::class.java).removeAll()
        ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java).removeAll()
        ObjectBox.store.boxFor(WorkoutEntity::class.java).removeAll()
        ObjectBox.store.boxFor(PlanExerciseEntity::class.java).removeAll()
        ObjectBox.store.boxFor(PlanDayEntity::class.java).removeAll()
        ObjectBox.store.boxFor(PlanEntity::class.java).removeAll()
        if (clearExercises) {
            ObjectBox.store.boxFor(ExerciseMuscleEntity::class.java).removeAll()
            ObjectBox.store.boxFor(ExerciseEntity::class.java).removeAll()
        }
        ObjectBox.store.boxFor(BodyMeasurementEntity::class.java).removeAll()
        ObjectBox.store.boxFor(ProgressPhotoEntity::class.java).removeAll()
        ObjectBox.store.boxFor(AppSettingEntity::class.java).removeAll()
        ObjectBox.store.boxFor(AthleteCalibrationEntity::class.java).removeAll()
        ObjectBox.store.boxFor(GamificationProfileEntity::class.java).removeAll()
        ObjectBox.store.boxFor(IronLedgerEventEntity::class.java).removeAll()
    }

    private fun importData(data: JSONObject, mergeSingletons: Boolean) {
        val exercisesBox = ObjectBox.store.boxFor(ExerciseEntity::class.java)
        val exerciseMusclesBox = ObjectBox.store.boxFor(ExerciseMuscleEntity::class.java)
        val plansBox = ObjectBox.store.boxFor(PlanEntity::class.java)
        val planDaysBox = ObjectBox.store.boxFor(PlanDayEntity::class.java)
        val planExercisesBox = ObjectBox.store.boxFor(PlanExerciseEntity::class.java)
        val workoutsBox = ObjectBox.store.boxFor(WorkoutEntity::class.java)
        val workoutExercisesBox = ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java)
        val workoutSetsBox = ObjectBox.store.boxFor(WorkoutSetEntity::class.java)
        val bodyBox = ObjectBox.store.boxFor(BodyMeasurementEntity::class.java)
        val photosBox = ObjectBox.store.boxFor(ProgressPhotoEntity::class.java)
        val settingsBox = ObjectBox.store.boxFor(AppSettingEntity::class.java)
        val athleteCalibrationsBox = ObjectBox.store.boxFor(AthleteCalibrationEntity::class.java)
        val gamificationProfilesBox = ObjectBox.store.boxFor(GamificationProfileEntity::class.java)
        val ironLedgerEventsBox = ObjectBox.store.boxFor(IronLedgerEventEntity::class.java)

        val planByUid = mutableMapOf<String, PlanEntity>()
        val dayByUid = mutableMapOf<String, PlanDayEntity>()
        val exerciseByUid = mutableMapOf<String, ExerciseEntity>()
        val workoutByUid = mutableMapOf<String, WorkoutEntity>()
        val workoutExerciseByUid = mutableMapOf<String, WorkoutExerciseEntity>()

        (data.optJSONArray("exercises") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val row = ExerciseEntity().apply {
                this.uid = uid
                name = o.optString("name")
                normalizedName = o.optString("normalized_name")
                primaryMuscle = o.optString("primary_muscle")
                equipment = o.optString("equipment")
                category = o.optString("category", "strength")
                isCustom = o.optBoolean("is_custom", false)
                source = o.optString("source")
                notes = o.optString("notes")
                equipmentDetail = o.optNullableString("equipment_detail")
                apparatusJson = o.optNullableString("apparatus_json")
                trackingType = o.optNullableString("tracking_type")
                isBodyweight = o.optBoolean("is_bodyweight", equipment.equals("bodyweight", true))
                requiresExternalLoad = o.optBoolean("requires_external_load", false)
                movementPattern = o.optNullableString("movement_pattern")
                difficulty = o.optNullableString("difficulty")
                aliasesJson = o.optNullableString("aliases_json")
                sourceTagsJson = o.optNullableString("source_tags_json")
                secondaryMusclesJson = o.optNullableString("secondary_muscles_json")
                createdAt = o.optLong("created_at", 0L)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            exercisesBox.query(ExerciseEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            exercisesBox.put(row)
            exerciseByUid[row.uid] = row
        }

        (data.optJSONArray("exercise_muscles") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            val exerciseUid = o.optString("exercise_id").trim()
            if (uid.isBlank() || exerciseUid.isBlank()) return@forEachObject
            val row = ExerciseMuscleEntity().apply {
                this.uid = uid
                this.exerciseUid = exerciseUid
                exerciseByUid[exerciseUid]?.let { exercise.target = it }
                muscle = o.optString("muscle")
                role = o.optString("role", "secondary")
                contributionFraction = o.optDouble("contribution_fraction", 0.0)
                createdAt = o.optLong("created_at", 0L)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            exerciseMusclesBox.query(ExerciseMuscleEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            exerciseMusclesBox.put(row)
        }

        (data.optJSONArray("plans") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val row = PlanEntity().apply {
                this.uid = uid
                name = o.optString("name")
                goal = o.optString("goal")
                description = o.optString("description")
                isActive = o.optBoolean("is_active", false)
                createdAt = o.optLong("created_at", 0L)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            plansBox.query(PlanEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            plansBox.put(row)
            planByUid[row.uid] = row
        }

        (data.optJSONArray("plan_days") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val planUid = o.optString("plan_id")
            val row = PlanDayEntity().apply {
                this.uid = uid
                this.planUid = planUid
                planByUid[planUid]?.let { plan.target = it }
                name = o.optString("name")
                color = o.optString("color", "#FF4500")
                orderIndex = o.optInt("order_index", 0)
                createdAt = o.optLong("created_at", 0L)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            planDaysBox.query(PlanDayEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            planDaysBox.put(row)
            dayByUid[row.uid] = row
        }

        (data.optJSONArray("plan_exercises") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val dayUid = o.optString("plan_day_id")
            val exerciseUid = o.optString("exercise_id")
            val row = PlanExerciseEntity().apply {
                this.uid = uid
                this.planDayUid = dayUid
                dayByUid[dayUid]?.let { planDay.target = it }
                this.exerciseUid = exerciseUid
                exerciseByUid[exerciseUid]?.let { exercise.target = it }
                orderIndex = o.optInt("order_index", 0)
                sets = max(1, o.optInt("sets", 1))
                reps = o.optString("reps")
                restSeconds = max(0, o.optInt("rest_seconds", 0))
                supersetGroup = o.optString("superset_group")
                isWarmup = o.optBoolean("is_warmup", false)
                notes = o.optString("notes")
                createdAt = o.optLong("created_at", 0L)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            planExercisesBox.query(PlanExerciseEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            planExercisesBox.put(row)
        }

        (data.optJSONArray("workouts") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val planUid = o.optString("plan_id").ifBlank { null }
            val dayUid = o.optString("plan_day_id").ifBlank { null }
            val row = WorkoutEntity().apply {
                this.uid = uid
                this.planUid = planUid
                planUid?.let { pu -> planByUid[pu]?.let { plan.target = it } }
                this.planDayUid = dayUid
                dayUid?.let { du -> dayByUid[du]?.let { planDay.target = it } }
                name = o.optString("name")
                startedAt = o.optLong("started_at", 0L)
                completedAt = if (o.isNull("completed_at")) null else o.optLong("completed_at")
                durationSeconds = o.optInt("duration_seconds", 0)
                rating = if (o.isNull("rating")) null else o.optDouble("rating")
                notes = o.optString("notes")
                status = o.optString("status", "completed")
                // New IronLog backups preserve provenance. Older/foreign payloads lack the
                // field and are conservatively treated as imported proof.
                imported = o.optBoolean("imported", true)
                createdAt = o.optLong("created_at", startedAt)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            workoutsBox.query(WorkoutEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            workoutsBox.put(row)
            workoutByUid[row.uid] = row
        }

        (data.optJSONArray("workout_exercises") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val workoutUid = o.optString("workout_id")
            val exerciseUid = o.optString("exercise_id")
            val row = WorkoutExerciseEntity().apply {
                this.uid = uid
                this.workoutUid = workoutUid
                workoutByUid[workoutUid]?.let { workout.target = it }
                this.exerciseUid = exerciseUid
                exerciseByUid[exerciseUid]?.let { exercise.target = it }
                orderIndex = o.optInt("order_index", 0)
                supersetGroup = o.optString("superset_group")
                notes = o.optString("notes")
                createdAt = o.optLong("created_at", 0L)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            workoutExercisesBox.query(WorkoutExerciseEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            workoutExercisesBox.put(row)
            workoutExerciseByUid[row.uid] = row
        }

        (data.optJSONArray("workout_sets") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val workoutExerciseUid = o.optString("workout_exercise_id")
            val row = WorkoutSetEntity().apply {
                this.uid = uid
                this.workoutExerciseUid = workoutExerciseUid
                workoutExerciseByUid[workoutExerciseUid]?.let { workoutExercise.target = it }
                setIndex = o.optInt("set_index", 1)
                weight = o.optDouble("weight", 0.0)
                reps = o.optDouble("reps", 0.0)
                rpe = if (o.isNull("rpe")) null else o.optDouble("rpe")
                rir = if (o.isNull("rir")) null else o.optDouble("rir")
                restSeconds = o.optInt("rest_seconds", 0)
                isWarmup = o.optBoolean("is_warmup", false)
                isDropset = o.optBoolean("is_dropset", false)
                isAmrap = o.optBoolean("is_amrap", false)
                toFailure = o.optBoolean("to_failure", false)
                completedAt = if (o.isNull("completed_at")) null else o.optLong("completed_at")
                createdAt = o.optLong("created_at", 0L)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            workoutSetsBox.query(WorkoutSetEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            workoutSetsBox.put(row)
        }

        (data.optJSONArray("body_measurements") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val row = BodyMeasurementEntity().apply {
                this.uid = uid
                measuredAt = o.optLong("measured_at", 0L)
                bodyweight = if (o.isNull("bodyweight")) null else o.optDouble("bodyweight")
                waist = if (o.isNull("waist")) null else o.optDouble("waist")
                chest = if (o.isNull("chest")) null else o.optDouble("chest")
                arm = if (o.isNull("arm")) null else o.optDouble("arm")
                thigh = if (o.isNull("thigh")) null else o.optDouble("thigh")
                notes = o.optString("notes")
                createdAt = o.optLong("created_at", measuredAt)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            bodyBox.query(BodyMeasurementEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            bodyBox.put(row)
        }

        (data.optJSONArray("progress_photos") ?: JSONArray()).forEachObject { o ->
            val uid = o.optString("id").trim()
            if (uid.isBlank()) return@forEachObject
            val row = ProgressPhotoEntity().apply {
                this.uid = uid
                fileUri = o.optString("file_uri")
                takenAt = o.optLong("taken_at", 0L)
                bodyweight = if (o.isNull("bodyweight")) null else o.optDouble("bodyweight")
                notes = o.optString("notes")
                createdAt = o.optLong("created_at", takenAt)
                updatedAt = o.optLong("updated_at", createdAt)
            }
            photosBox.query(ProgressPhotoEntity_.uid.equal(uid)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            photosBox.put(row)
        }

        (data.optJSONArray("app_settings") ?: JSONArray()).forEachObject { o ->
            val key = o.optString("key").trim()
            if (key.isBlank()) return@forEachObject
            val row = AppSettingEntity().apply {
                this.key = key
                value = o.optString("value")
                valueType = o.optString("value_type", "string")
                updatedAt = o.optLong("updated_at", 0L)
            }
            settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { q ->
                q.findFirst()?.let { row.objectBoxId = it.objectBoxId }
            }
            settingsBox.put(row)
        }

        (data.optJSONArray("athlete_calibrations") ?: JSONArray()).forEachObject { o ->
            val offlineUserId = "local"
            val row = AthleteCalibrationEntity().apply {
                this.offlineUserId = offlineUserId
                trainingAgeMonths = o.optInt("training_age_months", 0)
                historicalTrainingDaysPerWeek = o.optInt("historical_training_days_per_week", 3).coerceIn(1, 7)
                firstVerifiedSessionAt = o.optLong("first_verified_session_at", 0L)
                weightUnit = o.optString("weight_unit", "kg")
                bodyweightKg = if (o.isNull("bodyweight_kg")) null else o.optDouble("bodyweight_kg")
                goalMode = o.optString("goal_mode", "hypertrophy")
                weeklyGoalDays = o.optInt("weekly_goal_days", 4)
                importedHistory = o.optBoolean("imported_history", false)
                confidence = o.optDouble("confidence", 0.5)
                updatedAt = o.optLong("updated_at", 0L)
                hasPastTraining = o.optBoolean("has_past_training", false)
                hasGymAccess = o.optBoolean("has_gym_access", true)
                baselinePushups = o.optInt("baseline_pushups", 0)
                baselinePullups = o.optInt("baseline_pullups", 0)
                baselineBenchKg = o.optInt("baseline_bench_kg", 0)
                baselineLatPulldownKg = o.optInt("baseline_lat_pulldown_kg", 0)
                baselineMileRunSeconds = o.optInt("baseline_mile_run_seconds", 0)
            }
            athleteCalibrationsBox.query(AthleteCalibrationEntity_.offlineUserId.equal(offlineUserId)).build().use { q ->
                q.findFirst()?.let { existing ->
                    row.id = existing.id
                    if (mergeSingletons) {
                        row.trainingAgeMonths = maxOf(row.trainingAgeMonths, existing.trainingAgeMonths)
                        row.historicalTrainingDaysPerWeek = existing.historicalTrainingDaysPerWeek.takeIf { it in 1..7 }
                            ?: row.historicalTrainingDaysPerWeek
                        row.firstVerifiedSessionAt = listOf(row.firstVerifiedSessionAt, existing.firstVerifiedSessionAt)
                            .filter { it > 0L }.minOrNull() ?: 0L
                        row.weightUnit = existing.weightUnit
                        row.bodyweightKg = existing.bodyweightKg ?: row.bodyweightKg
                        row.goalMode = existing.goalMode
                        row.weeklyGoalDays = existing.weeklyGoalDays
                        row.importedHistory = row.importedHistory || existing.importedHistory
                        row.confidence = maxOf(row.confidence, existing.confidence)
                        row.updatedAt = maxOf(row.updatedAt, existing.updatedAt)
                        row.hasPastTraining = row.hasPastTraining || existing.hasPastTraining
                        row.hasGymAccess = existing.hasGymAccess
                        row.baselinePushups = maxOf(row.baselinePushups, existing.baselinePushups)
                        row.baselinePullups = maxOf(row.baselinePullups, existing.baselinePullups)
                        row.baselineBenchKg = maxOf(row.baselineBenchKg, existing.baselineBenchKg)
                        row.baselineLatPulldownKg = maxOf(row.baselineLatPulldownKg, existing.baselineLatPulldownKg)
                        row.baselineMileRunSeconds = maxOf(row.baselineMileRunSeconds, existing.baselineMileRunSeconds)
                    }
                }
            }
            athleteCalibrationsBox.put(row)
        }

        (data.optJSONArray("gamification_profiles") ?: JSONArray()).forEachObject { o ->
            val offlineUserId = "local"
            val row = GamificationProfileEntity().apply {
                this.offlineUserId = offlineUserId
                totalXp = o.optLong("total_xp", 0L)
                level = o.optInt("level", 1)
                xpInLevel = o.optLong("xp_in_level", 0L)
                rank = o.optString("rank", "E")
                activeTitle = o.optString("active_title", "Ledger Initiate")
                statsJson = o.optString("stats_json", "{}")
                weeklyXp = o.optInt("weekly_xp", 0)
                weeklyXpResetWeek = o.optString("weekly_xp_reset_week")
                streakWeeks = o.optInt("streak_weeks", 0)
                makeupCompletionsJson = o.optString(
                    "recovery_circuit_completions_json",
                    o.optString("makeup_completions_json", "{}"),
                )
                unlockedBadges = o.optString("unlocked_badges")
            }
            gamificationProfilesBox.query(GamificationProfileEntity_.offlineUserId.equal(offlineUserId)).build().use { q ->
                q.findFirst()?.let { existing ->
                    row.id = existing.id
                    if (mergeSingletons) {
                        val importedWins = row.totalXp >= existing.totalXp
                        row.totalXp = maxOf(row.totalXp, existing.totalXp)
                        row.level = maxOf(row.level, existing.level)
                        if (!importedWins) {
                            row.xpInLevel = existing.xpInLevel
                            row.rank = existing.rank
                            row.activeTitle = existing.activeTitle
                            row.statsJson = existing.statsJson
                        }
                        row.weeklyXp = maxOf(row.weeklyXp, existing.weeklyXp)
                        if (existing.weeklyXpResetWeek > row.weeklyXpResetWeek) {
                            row.weeklyXpResetWeek = existing.weeklyXpResetWeek
                        }
                        row.streakWeeks = maxOf(row.streakWeeks, existing.streakWeeks)
                        row.makeupCompletionsJson = mergeIntJsonMaps(
                            existing.makeupCompletionsJson,
                            row.makeupCompletionsJson,
                        )
                        row.unlockedBadges = (existing.unlockedBadges.split(',') + row.unlockedBadges.split(','))
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .distinct()
                            .joinToString(",")
                    }
                }
            }
            gamificationProfilesBox.put(row)
        }

        (data.optJSONArray("iron_ledger_events") ?: JSONArray()).forEachObject { o ->
            val eventId = o.optString("event_id").trim()
            if (eventId.isBlank()) return@forEachObject
            val row = IronLedgerEventEntity().apply {
                this.eventId = eventId
                sourceType = o.optString("source_type")
                sourceId = o.optString("source_id")
                eventKind = o.optString("event_kind")
                occurredAt = o.optLong("occurred_at", 0L)
                xpDelta = o.optInt("xp_delta", 0)
                statDeltasJson = o.optString("stat_deltas_json", "{}")
                trustScore = o.optDouble("trust_score", 1.0)
                fingerprint = o.optString("fingerprint")
                metadataJson = o.optString("metadata_json", "{}")
                invalidated = o.optBoolean("invalidated", false)
            }
            ironLedgerEventsBox.query(IronLedgerEventEntity_.eventId.equal(eventId)).build().use { q ->
                q.findFirst()?.let { row.id = it.id }
            }
            ironLedgerEventsBox.put(row)
        }

        // keep seed flag for first app open consistency after restore
        ObjectBox.store.boxFor(AppSettingEntity::class.java).query(AppSettingEntity_.key.equal("exercise_seed_complete")).build().use { q ->
            if (q.findFirst() == null) {
                ObjectBox.store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
                    key = "exercise_seed_complete"
                    value = "true"
                    valueType = "boolean"
                    updatedAt = System.currentTimeMillis()
                })
            }
        }
    }
}

internal fun backfillMissingAthleteStateRows(
    data: JSONObject,
    nowMs: Long = System.currentTimeMillis(),
) {
    val settingsRows = data.optJSONArray("app_settings") ?: JSONArray()
    val calibrationRows = data.optJSONArray("athlete_calibrations") ?: JSONArray().also { data.put("athlete_calibrations", it) }
    val profileRows = data.optJSONArray("gamification_profiles") ?: JSONArray().also { data.put("gamification_profiles", it) }
    if (calibrationRows.length() > 0 && profileRows.length() > 0) return

    val settings = linkedMapOf<String, Pair<String, String>>()
    for (i in 0 until settingsRows.length()) {
        val row = settingsRows.optJSONObject(i) ?: continue
        val key = row.optString("key").trim()
        if (key.isBlank()) continue
        settings[key] = row.optString("value") to row.optString("value_type", "string")
    }
    fun stringSetting(key: String): String? = settings[key]?.first?.takeIf { it.isNotBlank() }

    if (calibrationRows.length() == 0) {
        val calibration = deriveFallbackAthleteCalibration(settings, data.optJSONArray("workouts")?.length()?.let { it > 0 } == true, nowMs)
        calibrationRows.put(
            JSONObject()
                .put("offline_user_id", calibration.offlineUserId)
                .put("training_age_months", calibration.trainingAgeMonths)
                .put("historical_training_days_per_week", calibration.historicalTrainingDaysPerWeek)
                .put("first_verified_session_at", calibration.firstVerifiedSessionAt)
                .put("weight_unit", calibration.weightUnit)
                .put("bodyweight_kg", calibration.bodyweightKg ?: JSONObject.NULL)
                .put("goal_mode", calibration.goalMode)
                .put("weekly_goal_days", calibration.weeklyGoalDays)
                .put("imported_history", calibration.importedHistory)
                .put("confidence", calibration.confidence)
                .put("updated_at", calibration.updatedAt)
                .put("has_past_training", calibration.hasPastTraining)
                .put("has_gym_access", calibration.hasGymAccess)
                .put("baseline_pushups", calibration.baselinePushups)
                .put("baseline_pullups", calibration.baselinePullups)
                .put("baseline_bench_kg", calibration.baselineBenchKg)
                .put("baseline_lat_pulldown_kg", calibration.baselineLatPulldownKg)
                .put("baseline_mile_run_seconds", calibration.baselineMileRunSeconds)
        )
    }
    if (profileRows.length() == 0) {
        val profile = fallbackGamificationProfileRow()
        profileRows.put(
            JSONObject()
                .put("offline_user_id", profile.offlineUserId)
                .put("total_xp", profile.totalXp)
                .put("level", profile.level)
                .put("xp_in_level", profile.xpInLevel)
                .put("rank", profile.rank)
                .put("active_title", profile.activeTitle)
                .put("stats_json", profile.statsJson)
                .put("weekly_xp", profile.weeklyXp)
                .put("weekly_xp_reset_week", profile.weeklyXpResetWeek)
                .put("streak_weeks", profile.streakWeeks)
                .put("recovery_circuit_completions_json", profile.recoveryCircuitCompletionsJson)
                .put("unlocked_badges", profile.unlockedBadges)
        )
    }
}

internal data class FallbackAthleteCalibrationRow(
    val offlineUserId: String = "local",
    val trainingAgeMonths: Int,
    val historicalTrainingDaysPerWeek: Int,
    val firstVerifiedSessionAt: Long,
    val weightUnit: String,
    val bodyweightKg: Double?,
    val goalMode: String,
    val weeklyGoalDays: Int,
    val importedHistory: Boolean,
    val confidence: Double,
    val updatedAt: Long,
    val hasPastTraining: Boolean,
    val hasGymAccess: Boolean,
    val baselinePushups: Int,
    val baselinePullups: Int,
    val baselineBenchKg: Int,
    val baselineLatPulldownKg: Int,
    val baselineMileRunSeconds: Int,
)

internal data class FallbackGamificationProfileRow(
    val offlineUserId: String = "local",
    val totalXp: Long = 0L,
    val level: Int = 1,
    val xpInLevel: Long = 0L,
    val rank: String = "E",
    val activeTitle: String = "Ledger Initiate",
    val statsJson: String = "{}",
    val weeklyXp: Int = 0,
    val weeklyXpResetWeek: String = "",
    val streakWeeks: Int = 0,
    val recoveryCircuitCompletionsJson: String = "{}",
    val unlockedBadges: String = "",
)

internal fun deriveFallbackAthleteCalibration(
    settings: Map<String, Pair<String, String>>,
    hasImportedWorkouts: Boolean,
    nowMs: Long = System.currentTimeMillis(),
): FallbackAthleteCalibrationRow {
    fun stringSetting(key: String): String? = settings[key]?.first?.takeIf { it.isNotBlank() }
    fun intSetting(key: String, default: Int = 0): Int = stringSetting(key)?.toIntOrNull() ?: default
    fun longSetting(key: String, default: Long = 0L): Long = stringSetting(key)?.toLongOrNull() ?: default
    fun doubleSetting(key: String): Double? = stringSetting(key)?.toDoubleOrNull()
    fun booleanSetting(key: String, default: Boolean = false): Boolean = stringSetting(key)?.equals("true", ignoreCase = true) ?: default

    return FallbackAthleteCalibrationRow(
        trainingAgeMonths = intSetting("baseline_training_age_months"),
        historicalTrainingDaysPerWeek = intSetting("baseline_historical_training_days_per_week", 3).coerceIn(1, 7),
        firstVerifiedSessionAt = longSetting("first_verified_session_at", 0L),
        weightUnit = stringSetting("weightUnit") ?: "kg",
        bodyweightKg = doubleSetting("baseline_bodyweight_kg"),
        goalMode = stringSetting("goalMode") ?: "hypertrophy",
        weeklyGoalDays = intSetting("weeklyGoalDays", 4).coerceIn(1, 7),
        importedHistory = booleanSetting("ledger_imported_history", hasImportedWorkouts),
        confidence = 0.5,
        updatedAt = nowMs,
        hasPastTraining = booleanSetting("baseline_has_past_training"),
        hasGymAccess = booleanSetting("baseline_has_gym_access", true),
        baselinePushups = intSetting("baseline_pushups"),
        baselinePullups = intSetting("baseline_pullups"),
        baselineBenchKg = intSetting("baseline_bench_kg"),
        baselineLatPulldownKg = intSetting("baseline_lat_pulldown_kg"),
        baselineMileRunSeconds = intSetting("baseline_mile_run_seconds"),
    )
}

internal fun fallbackGamificationProfileRow(): FallbackGamificationProfileRow = FallbackGamificationProfileRow()

private fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        block(o)
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun mergeIntJsonMaps(first: String, second: String): String {
    val merged = linkedMapOf<String, Int>()
    listOf(first, second).forEach { raw ->
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
        json.keys().forEach { key ->
            merged[key] = maxOf(merged[key] ?: 0, json.optInt(key, 0))
        }
    }
    return JSONObject(merged as Map<*, *>).toString()
}
