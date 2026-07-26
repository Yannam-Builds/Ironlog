package com.ironlog.app.data.seed

import android.content.Context
import com.ironlog.app.util.ExerciseTrackingTypeNormalizer
import com.ironlog.app.data.model.ExerciseSeedEntry
import com.ironlog.app.data.model.ExerciseSeedPayload
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.util.normalizeExerciseNameKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.max

class ExerciseSeed(
    private val context: Context,
    private val settingsRepository: SettingsRepository = SettingsRepository(),
) {
    private val exerciseBox get() = ObjectBox.store.boxFor(ExerciseEntity::class.java)
    private val muscleBox get() = ObjectBox.store.boxFor(ExerciseMuscleEntity::class.java)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    data class SeedResult(val seeded: Boolean, val reason: String? = null, val count: Int? = null)
    data class BackfillResult(val backfilled: Boolean, val reason: String? = null, val count: Int? = null)

    suspend fun seedExercisesIfNeeded(): SeedResult = withContext(Dispatchers.IO) {
        if (settingsRepository.isExerciseSeedComplete()) {
            val repaired = repairExerciseTrackingTypes()
            return@withContext SeedResult(
                seeded = false,
                reason = if (repaired > 0) "already_seeded_repaired_tracking_types" else "already_seeded",
                count = repaired.takeIf { it > 0 },
            )
        }

        val rows = loadPayload().exercises.filter { !it.name.isNullOrBlank() }
        val existing = exerciseBox.all
        val existingKeys = existing.map { it.normalizedName }.toMutableSet()
        val insertedByKey = linkedMapOf<String, Pair<ExerciseEntity, ExerciseSeedEntry>>()
        val now = System.currentTimeMillis()

        val exercisesToPut = mutableListOf<ExerciseEntity>()
        for (entry in rows) {
            val name = entry.name.orEmpty().trim()
            val normalizedName = normalizeName(name)
            if (normalizedName.isBlank() || existingKeys.contains(normalizedName)) continue
            existingKeys.add(normalizedName)
            val entity = ExerciseEntity().apply {
                uid = entry.id?.takeIf { it.isNotBlank() } ?: uid
                this.name = name
                this.normalizedName = normalizedName
                primaryMuscle = toTitleCase(entry.primaryMuscle ?: entry.primaryMuscles?.firstOrNull() ?: "Other")
                equipment = normalizeEquipment(entry.equipment)
                category = normalizeCategory(entry)
                isCustom = false
                source = "built_in"
                notes = entry.notes?.toString() ?: ""
                createdAt = now
                updatedAt = now
                equipmentDetail = entry.equipmentDetail
                apparatusJson = entry.apparatus?.let { json.encodeToString(it) }
                trackingType = ExerciseTrackingTypeNormalizer.normalize(
                    name = name,
                    category = category,
                    equipment = equipment,
                    explicitTrackingType = entry.trackingType,
                )
                isBodyweight = entry.isBodyweight == true
                requiresExternalLoad = entry.requiresExternalLoad == true
                movementPattern = entry.movementPattern
                difficulty = entry.difficulty
                aliasesJson = entry.aliases?.let { json.encodeToString(it) }
                sourceTagsJson = entry.sourceTags?.let { json.encodeToString(it) }
            }
            exercisesToPut += entity
            insertedByKey[normalizedName] = entity to entry
        }

        exercisesToPut.chunked(BATCH_SIZE).forEach { exerciseBox.put(it) }

        val muscleRows = mutableListOf<ExerciseMuscleEntity>()
        for ((_, pair) in insertedByKey) {
            val (exercise, source) = pair
            buildMuscleRows(source).forEach { muscleRow ->
                muscleRows += ExerciseMuscleEntity().apply {
                    this.exercise.target = exercise
                    exerciseUid = exercise.uid
                    muscle = muscleRow.muscle
                    role = muscleRow.role
                    contributionFraction = muscleRow.contribution
                    createdAt = now
                    updatedAt = now
                }
            }
        }
        muscleRows.chunked(BATCH_SIZE).forEach { muscleBox.put(it) }

        settingsRepository.markExerciseSeedComplete()
        repairExerciseTrackingTypes()
        SeedResult(seeded = true, count = rows.size)
    }

    suspend fun backfillExerciseMusclesIfNeeded(): BackfillResult = withContext(Dispatchers.IO) {
        if (muscleBox.count() > 0) {
            return@withContext BackfillResult(backfilled = false, reason = "already_populated")
        }

        val libraryByName = loadPayload().exercises
            .filter { !it.name.isNullOrBlank() }
            .associateBy { normalizeName(it.name) }

        val allExercises = exerciseBox.all
        val now = System.currentTimeMillis()
        val muscleRows = mutableListOf<ExerciseMuscleEntity>()
        for (exercise in allExercises) {
            val sourceEntry = libraryByName[exercise.normalizedName] ?: continue
            buildMuscleRows(sourceEntry).forEach { row ->
                muscleRows += ExerciseMuscleEntity().apply {
                    this.exercise.target = exercise
                    exerciseUid = exercise.uid
                    muscle = row.muscle
                    role = row.role
                    contributionFraction = row.contribution
                    createdAt = now
                    updatedAt = now
                }
            }
        }

        if (muscleRows.isEmpty()) return@withContext BackfillResult(backfilled = false, reason = "no_exercises_matched")
        muscleRows.chunked(BATCH_SIZE).forEach { muscleBox.put(it) }
        BackfillResult(backfilled = true, count = muscleRows.size)
    }

    private fun loadPayload(): ExerciseSeedPayload {
        val text = context.assets.open("exerciseLibrary.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(ExerciseSeedPayload.serializer(), text)
    }

    private fun repairExerciseTrackingTypes(): Int {
        val changed = exerciseBox.all.mapNotNull { exercise ->
            val normalized = ExerciseTrackingTypeNormalizer.normalize(
                name = exercise.name,
                category = exercise.category,
                equipment = exercise.equipment,
                explicitTrackingType = exercise.trackingType,
            )
            if (exercise.trackingType == normalized) null else {
                exercise.trackingType = normalized
                exercise.updatedAt = System.currentTimeMillis()
                exercise
            }
        }
        if (changed.isNotEmpty()) changed.chunked(BATCH_SIZE).forEach { exerciseBox.put(it) }
        return changed.size
    }

    private data class MuscleSeedRow(val muscle: String, val role: String, val contribution: Double)

    private fun buildMuscleRows(exercise: ExerciseSeedEntry): List<MuscleSeedRow> {
        val primary = (exercise.primaryMuscles?.filter { it.isNotBlank() }
            ?: listOfNotNull(exercise.primaryMuscle?.takeIf { it.isNotBlank() }))
        val secondary = exercise.secondaryMuscles?.filter { it.isNotBlank() }.orEmpty()
        if (primary.isEmpty() && secondary.isEmpty()) return emptyList()
        if (secondary.isEmpty()) {
            val each = 1.0 / max(primary.size, 1)
            return primary.map { MuscleSeedRow(toTitleCase(it), "primary", each) }
        }
        val primaryWeight = 0.7 / max(primary.size, 1)
        val secondaryWeight = 0.3 / max(secondary.size, 1)
        return primary.map { MuscleSeedRow(toTitleCase(it), "primary", primaryWeight) } +
            secondary.map { MuscleSeedRow(toTitleCase(it), "secondary", secondaryWeight) }
    }

    private fun toTitleCase(value: String?): String = value.orEmpty()
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase(Locale.ROOT).replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) }
        }

    // Uses the String overload of normalizeExerciseNameKey (underscores format, from ExerciseUiFilters)
    // so that stored normalizedName values are consistent with what createCustomExercise stores
    // and what the duplicate-name guard queries.
    private fun normalizeName(value: String?): String = normalizeExerciseNameKey(value.orEmpty().trim())

    private fun normalizeEquipment(value: String?): String {
        val raw = value.orEmpty().trim()
        return if (raw.isBlank()) "Other" else toTitleCase(raw)
    }

    private fun normalizeCategory(exercise: ExerciseSeedEntry): String {
        val fromCategory = exercise.category.orEmpty().lowercase(Locale.ROOT)
        return when {
            fromCategory.contains("cardio") || fromCategory.contains("conditioning") -> "cardio"
            fromCategory.contains("stretch") || fromCategory.contains("mobility") -> "mobility"
            else -> "strength"
        }
    }

    companion object { private const val BATCH_SIZE = 150 }
}
