package com.ironlog.app.data.objectbox

import org.json.JSONObject

/**
 * One-time bridge from the legacy account-wide imported-history flag to per-workout
 * provenance. Existing databases cannot identify mixed native/imported rows, so the safe
 * migration marks the completed history present at upgrade time as imported. Workouts logged
 * after the migration keep the entity default (`imported = false`).
 */
object WorkoutImportProvenanceMigration {
    private const val MIGRATION_KEY = "workout_import_provenance_v1"
    private const val LEGACY_IMPORTED_KEY = "ledger_imported_history"

    fun run() {
        val store = ObjectBox.store
        normalizeAthleteSingletons()
        val settingsBox = store.boxFor(AppSettingEntity::class.java)
        val alreadyMigrated = settingsBox.query(AppSettingEntity_.key.equal(MIGRATION_KEY))
            .build().use { it.findFirst() }
            ?.value
            ?.toBooleanStrictOrNull() == true
        if (alreadyMigrated) return

        store.runInTx {
            val legacySetting = settingsBox.query(AppSettingEntity_.key.equal(LEGACY_IMPORTED_KEY))
                .build().use { it.findFirst() }
                ?.value
                ?.toBooleanStrictOrNull() == true
            val legacyCalibration = store.boxFor(AthleteCalibrationEntity::class.java)
                .query(AthleteCalibrationEntity_.importedHistory.equal(true))
                .build().use { it.count() > 0 }

            if (legacySetting || legacyCalibration) {
                val workoutBox = store.boxFor(WorkoutEntity::class.java)
                val completed = workoutBox.query(WorkoutEntity_.status.equal("completed"))
                    .build().use { it.find() }
                completed.forEach { it.imported = true }
                workoutBox.put(completed)
            }

            settingsBox.put(AppSettingEntity().apply {
                key = MIGRATION_KEY
                value = "true"
                valueType = "boolean"
                updatedAt = System.currentTimeMillis()
            })
        }
    }

    private fun normalizeAthleteSingletons() {
        val store = ObjectBox.store
        store.runInTx {
            val profileBox = store.boxFor(GamificationProfileEntity::class.java)
            val profiles = profileBox.all
            if (profiles.isNotEmpty() && (profiles.size > 1 || profiles.single().offlineUserId != "local")) {
                val primary = profiles.maxBy { it.totalXp }
                val badges = profiles.flatMap { it.unlockedBadges.split(',') }
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                val mergedCompletions = mergeCompletionMaps(profiles.map { it.makeupCompletionsJson })
                profileBox.remove(profiles)
                primary.id = 0
                primary.offlineUserId = "local"
                primary.totalXp = profiles.maxOf { it.totalXp }
                primary.level = profiles.maxOf { it.level }
                primary.weeklyXp = profiles.maxOf { it.weeklyXp }
                primary.streakWeeks = profiles.maxOf { it.streakWeeks }
                primary.unlockedBadges = badges.joinToString(",")
                primary.makeupCompletionsJson = mergedCompletions
                profileBox.put(primary)
            }

            val calibrationBox = store.boxFor(AthleteCalibrationEntity::class.java)
            val calibrations = calibrationBox.all
            if (calibrations.isNotEmpty() && (calibrations.size > 1 || calibrations.single().offlineUserId != "local")) {
                val latest = calibrations.maxBy { it.updatedAt }
                val nonZeroFirstSession = calibrations.map { it.firstVerifiedSessionAt }
                    .filter { it > 0L }
                    .minOrNull() ?: 0L
                calibrationBox.remove(calibrations)
                latest.id = 0
                latest.offlineUserId = "local"
                latest.trainingAgeMonths = calibrations.maxOf { it.trainingAgeMonths }
                latest.firstVerifiedSessionAt = nonZeroFirstSession
                latest.importedHistory = calibrations.any { it.importedHistory }
                latest.confidence = calibrations.maxOf { it.confidence }
                latest.hasPastTraining = calibrations.any { it.hasPastTraining }
                latest.baselinePushups = calibrations.maxOf { it.baselinePushups }
                latest.baselinePullups = calibrations.maxOf { it.baselinePullups }
                latest.baselineBenchKg = calibrations.maxOf { it.baselineBenchKg }
                latest.baselineLatPulldownKg = calibrations.maxOf { it.baselineLatPulldownKg }
                latest.baselineMileRunSeconds = calibrations.maxOf { it.baselineMileRunSeconds }
                calibrationBox.put(latest)
            }
        }
    }

    private fun mergeCompletionMaps(values: List<String>): String {
        val merged = linkedMapOf<String, Int>()
        values.forEach { raw ->
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            json.keys().forEach { key ->
                merged[key] = maxOf(merged[key] ?: 0, json.optInt(key, 0))
            }
        }
        return JSONObject(merged as Map<*, *>).toString()
    }
}
