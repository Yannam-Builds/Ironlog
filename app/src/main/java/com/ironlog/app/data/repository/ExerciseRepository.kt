package com.ironlog.app.data.repository

import android.content.Context
import com.ironlog.app.util.ExerciseTrackingTypeNormalizer
import com.ironlog.app.data.model.CreateExerciseInput
import com.ironlog.app.data.model.ExerciseFilters
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.model.UpdateExerciseInput
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.PlanExerciseEntity
import com.ironlog.app.data.objectbox.PlanExerciseEntity_
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity
import com.ironlog.app.data.objectbox.WorkoutExerciseEntity_
import com.ironlog.app.data.seed.ExerciseSeed
import com.ironlog.app.domain.intelligence.SubstitutionEngine
import com.ironlog.app.util.normalizeExerciseName
import com.ironlog.app.util.normalizeExerciseNameKey
import com.ironlog.app.util.requireNonEmpty
import com.ironlog.app.util.validateContributionFraction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExerciseRepository(private val context: Context? = null) {
    private val exercisesBox get() = ObjectBox.store.boxFor(ExerciseEntity::class.java)
    private val musclesBox get() = ObjectBox.store.boxFor(ExerciseMuscleEntity::class.java)

    suspend fun seedExercisesIfNeeded(): ExerciseSeed.SeedResult {
        val ctx = context ?: error("Context is required to seed exerciseLibrary.json")
        return ExerciseSeed(ctx).seedExercisesIfNeeded()
    }

    suspend fun backfillExerciseMusclesIfNeeded(): ExerciseSeed.BackfillResult {
        val ctx = context ?: error("Context is required to backfill exercise muscles")
        return ExerciseSeed(ctx).backfillExerciseMusclesIfNeeded()
    }

    private fun getExercisesFlow(filters: ExerciseFilters = ExerciseFilters()): Flow<List<ExerciseEntity>> =
        exercisesBox.query().build().asFlow().map { rows -> filterRows(rows, filters) }

    suspend fun getExercisesSnapshot(filters: ExerciseFilters = ExerciseFilters()): List<LegacyExerciseShape> =
        withContext(Dispatchers.IO) { filterRows(exercisesBox.all, filters).map(::mapExerciseRowToLegacyShape) }

    suspend fun getCompactCatalogMarkdown(): String = withContext(Dispatchers.IO) {
        val canonical = context?.let { catalogMarkdownFile(it) }
        if (canonical != null && canonical.exists()) {
            return@withContext canonical.readText()
        }
        val generated = buildCompactCatalogMarkdown(filterRows(exercisesBox.all, ExerciseFilters()).map(::mapExerciseRowToLegacyShape))
        canonical?.let {
            it.parentFile?.mkdirs()
            it.writeText(generated)
        }
        generated
    }

    fun searchExercisesFlow(query: String?, filters: ExerciseFilters = ExerciseFilters()): Flow<List<ExerciseEntity>> {
        val normalized = normalizeExerciseNameKey(query.orEmpty())
        return kotlinx.coroutines.flow.flow {
            getExercisesFlow(filters).collect { rows ->
                emit(if (normalized.isBlank()) rows else rows.filter { it.normalizedName.contains(normalized) })
            }
        }
    }

    suspend fun createCustomExercise(input: CreateExerciseInput): ExerciseEntity = withContext(Dispatchers.IO) {
        val name = normalizeExerciseName(input.name)
        requireNonEmpty(name, "name")
        requireNonEmpty(input.primaryMuscle, "primary_muscle")
        requireNonEmpty(input.equipment, "equipment")
        requireNonEmpty(input.category, "category")
        val normalized = normalizeExerciseNameKey(name)
        val duplicate = exercisesBox.query(ExerciseEntity_.normalizedName.equal(normalized)).build().use { it.find() }
        if (duplicate.isNotEmpty()) error("Exercise with this name already exists.")

        // Validate every child before the parent is written so a bad contribution cannot leave
        // behind a partially-created exercise.
        input.muscles.orEmpty().forEach { validateContributionFraction(it.contribution_fraction) }

        val now = System.currentTimeMillis()
        val created = ExerciseEntity().apply {
            this.name = name
            normalizedName = normalized
            primaryMuscle = input.primaryMuscle!!
            equipment = input.equipment!!
            category = input.category!!
            isCustom = true
            source = "user_custom"
            notes = input.notes?.toString() ?: ""
            createdAt = now
            updatedAt = now
            isBodyweight = input.isBodyweight ?: (equipment.lowercase() == "bodyweight")
            trackingType = ExerciseTrackingTypeNormalizer.normalize(
                name = name,
                category = category,
                equipment = equipment,
                explicitTrackingType = input.trackingType,
            )
            movementPattern = input.movementPattern
            difficulty = input.difficulty
            secondaryMusclesJson = input.secondaryMuscles?.let { org.json.JSONArray(it).toString() }
        }
        ObjectBox.store.runInTx {
            exercisesBox.put(created)
            val muscleCreates = input.muscles.orEmpty().map { m ->
                ExerciseMuscleEntity().apply {
                    exercise.target = created
                    exerciseUid = created.uid
                    muscle = m.muscle.orEmpty().trim()
                    role = (m.role ?: "secondary").trim()
                    contributionFraction = m.contribution_fraction ?: 0.0
                    createdAt = now
                    updatedAt = now
                }
            }
            if (muscleCreates.isNotEmpty()) musclesBox.put(muscleCreates)
        }
        syncCompactCatalogMarkdown()
        created
    }

    suspend fun updateExercise(id: String, input: UpdateExerciseInput): ExerciseEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(id, "id")
        val exercise = findExerciseByUidOrThrow(id)
        val now = System.currentTimeMillis()
        if (!input.name.isNullOrEmpty()) {
            val updatedName = normalizeExerciseName(input.name)
            exercise.name = updatedName
            // Use normalizeExerciseName first to get a non-null String, then call the
            // String overload of normalizeExerciseNameKey (underscore format) — consistent
            // with createCustomExercise which also stores in underscore format.
            exercise.normalizedName = normalizeExerciseNameKey(updatedName)
        }
        if (!input.primaryMuscle.isNullOrEmpty()) exercise.primaryMuscle = input.primaryMuscle
        if (!input.equipment.isNullOrEmpty()) exercise.equipment = input.equipment
        if (!input.category.isNullOrEmpty()) exercise.category = input.category
        if (input.notes != null) exercise.notes = input.notes.ifEmpty { "" }
        exercise.updatedAt = now
        exercisesBox.put(exercise)
        syncCompactCatalogMarkdown()
        exercise
    }

    suspend fun deleteCustomExercise(id: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(id, "id")
        ObjectBox.store.runInTx {
            val exercise = findExerciseByUidOrThrow(id)
            if (!exercise.isCustom) error("Only custom exercises can be deleted.")
            val planReferences = ObjectBox.store.boxFor(PlanExerciseEntity::class.java)
                .query(PlanExerciseEntity_.exerciseUid.equal(id)).build().use { it.count() }
            val workoutReferences = ObjectBox.store.boxFor(WorkoutExerciseEntity::class.java)
                .query(WorkoutExerciseEntity_.exerciseUid.equal(id)).build().use { it.count() }
            if (planReferences > 0 || workoutReferences > 0) {
                error("This exercise is used by a plan or workout and cannot be deleted. Edit it instead.")
            }
            val muscles = musclesBox.query(ExerciseMuscleEntity_.exerciseUid.equal(id)).build().use { it.find() }
            musclesBox.remove(muscles)
            exercisesBox.remove(exercise)
        }
        syncCompactCatalogMarkdown()
        Unit
    }

    suspend fun getExerciseById(id: String): ExerciseEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(id, "id")
        findExerciseByUidOrThrow(id)
    }

    suspend fun getExerciseMuscles(exerciseId: String): List<ExerciseMuscleEntity> = withContext(Dispatchers.IO) {
        requireNonEmpty(exerciseId, "exerciseId")
        musclesBox.query(ExerciseMuscleEntity_.exerciseUid.equal(exerciseId)).build().use { it.find() }
    }

    suspend fun buildExerciseAlternatives(exerciseId: String, filters: ExerciseFilters = ExerciseFilters()): SubstitutionEngine.AlternativesResult =
        withContext(Dispatchers.IO) {
            requireNonEmpty(exerciseId, "exerciseId")
            val exercises = exercisesBox.all.map(::mapExerciseRowToLegacyShape)
            val target = exercises.find { it.id == exerciseId || it.exerciseId == exerciseId }
                ?: return@withContext SubstitutionEngine.AlternativesResult.EMPTY
            SubstitutionEngine.buildExerciseAlternatives(target, exercises, filters, filters.limit ?: 12)
        }

    internal fun findExerciseByUidOrThrow(uid: String): ExerciseEntity =
        exercisesBox.query(ExerciseEntity_.uid.equal(uid)).build().use { it.findFirst() }
            ?: error("Exercise not found: $uid")

    internal fun findExerciseByName(name: String): ExerciseEntity? =
        exercisesBox.query(ExerciseEntity_.name.equal(name)).build().use { it.findFirst() }

    internal fun mapExerciseRowToLegacyShape(row: ExerciseEntity): LegacyExerciseShape {
        val primary = if (row.primaryMuscle.isNotBlank()) listOf(row.primaryMuscle) else emptyList()
        val category = row.category.ifBlank { "strength" }
        val trackingType = ExerciseTrackingTypeNormalizer.normalize(
            name = row.name,
            category = category,
            equipment = row.equipment,
            explicitTrackingType = row.trackingType,
        )
        return LegacyExerciseShape(
            id = row.uid,
            exerciseId = row.uid,
            name = row.name,
            primaryMuscles = primary,
            primaryMuscle = row.primaryMuscle.ifBlank { null },
            secondaryMuscles = row.secondaryMusclesJson?.let { json ->
                runCatching {
                    val arr = org.json.JSONArray(json)
                    (0 until arr.length()).map { arr.getString(it) }
                }.getOrElse { emptyList() }
            } ?: emptyList(),
            equipment = row.equipment.ifBlank { "Other" },
            category = category,
            trackingType = trackingType,
            isCustom = row.isCustom,
            aliases = emptyList(),
            isBodyweight = row.isBodyweight || row.equipment.lowercase() == "bodyweight",
            movementPattern = row.movementPattern,
            difficulty = row.difficulty,
            apparatus = row.apparatusJson,
            equipmentDetail = row.equipmentDetail,
            sourceTags = emptyList(),
            notes = row.notes,
        )
    }

    private fun filterRows(rows: List<ExerciseEntity>, filters: ExerciseFilters): List<ExerciseEntity> = rows.filter { row ->
        val primaryOk = filters.primaryMuscle.isNullOrBlank() || filters.primaryMuscle == "all" || row.primaryMuscle == filters.primaryMuscle
        val equipmentOk = filters.equipment.isNullOrBlank() || filters.equipment == "all" || row.equipment == filters.equipment
        val categoryOk = filters.category.isNullOrBlank() || filters.category == "all" || row.category == filters.category
        val customOk = !filters.customOnly || row.isCustom
        primaryOk && equipmentOk && categoryOk && customOk
    }

    private fun syncCompactCatalogMarkdown() {
        val ctx = context ?: return
        val file = catalogMarkdownFile(ctx)
        val rows = filterRows(exercisesBox.all, ExerciseFilters()).map(::mapExerciseRowToLegacyShape)
        file.parentFile?.mkdirs()
        file.writeText(buildCompactCatalogMarkdown(rows))
    }

    private fun catalogMarkdownFile(ctx: Context): File =
        File(ctx.filesDir, "ai/ironlog_exercise_catalog_compact.md")

    private fun buildCompactCatalogMarkdown(exercises: List<LegacyExerciseShape>): String {
        val header = """
# Ironlog Exercise Catalog (Compact)

Use this catalog as the source of truth for exercise naming and metadata.
Format: `Exercise Name | Primary Muscle | Equipment | Category | Tracking Type | Movement Pattern`
""".trimIndent()
        val lines = exercises
            .sortedBy { it.name.lowercase() }
            .map { ex ->
                val primary = ex.primaryMuscle ?: ex.primaryMuscles.firstOrNull() ?: "Unknown"
                "${ex.name} | $primary | ${ex.equipment} | ${ex.category} | ${ex.trackingType} | ${ex.movementPattern}"
            }
        return buildString {
            appendLine(header)
            appendLine()
            appendLine("```text")
            lines.forEach { appendLine(it) }
            appendLine("```")
        }
    }
}
