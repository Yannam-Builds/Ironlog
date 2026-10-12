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

class ExerciseRepository(
    private val context: Context? = null,
    private val boxStore: io.objectbox.BoxStore? = null,
) {
    private val store get() = boxStore ?: ObjectBox.store
    private val exercisesBox get() = store.boxFor(ExerciseEntity::class.java)
    private val musclesBox get() = store.boxFor(ExerciseMuscleEntity::class.java)

    suspend fun seedExercisesIfNeeded(): ExerciseSeed.SeedResult {
        val ctx = context ?: error("Context is required to seed exerciseLibrary.json")
        return ExerciseSeed(ctx).seedExercisesIfNeeded()
    }

    suspend fun backfillExerciseMusclesIfNeeded(): ExerciseSeed.BackfillResult {
        val ctx = context ?: error("Context is required to backfill exercise muscles")
        return ExerciseSeed(ctx).backfillExerciseMusclesIfNeeded()
    }

    private fun getExercisesFlow(filters: ExerciseFilters = ExerciseFilters()): Flow<List<ExerciseEntity>> =
        observeQuery { exercisesBox.query().build() }.map { rows -> filterRows(rows, filters) }

    suspend fun getExercisesSnapshot(filters: ExerciseFilters = ExerciseFilters()): List<LegacyExerciseShape> =
        withContext(Dispatchers.IO) { catalogRows(filters) }

    suspend fun getCompactCatalogMarkdown(): String = withContext(Dispatchers.IO) {
        val canonical = context?.let { catalogMarkdownFile(it) }
        if (canonical != null && canonical.exists()) {
            return@withContext canonical.readText()
        }
        val generated = buildCompactCatalogMarkdown(catalogRows())
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
        store.runInTx {
            requireUniqueName(normalized)
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
        val updatedName = input.name?.let { normalizeExerciseName(it).also { name -> requireNonEmpty(name, "name") } }
        input.primaryMuscle?.let { requireNonEmpty(it.trim(), "primary_muscle") }
        input.equipment?.let { requireNonEmpty(it.trim(), "equipment") }
        input.category?.let { requireNonEmpty(it.trim(), "category") }
        lateinit var exercise: ExerciseEntity
        store.runInTx {
            exercise = findExerciseByUidOrThrow(id)
            if (updatedName != null) {
                val normalized = normalizeExerciseNameKey(updatedName)
                requireUniqueName(normalized, exercise.objectBoxId)
                exercise.name = updatedName
                exercise.normalizedName = normalized
            }
            input.primaryMuscle?.let { exercise.primaryMuscle = it.trim() }
            input.equipment?.let { exercise.equipment = it.trim() }
            input.category?.let { exercise.category = it.trim() }
            input.notes?.let { exercise.notes = it }
            exercise.updatedAt = System.currentTimeMillis()
            exercisesBox.put(exercise)
        }
        syncCompactCatalogMarkdown()
        exercise
    }

    suspend fun deleteCustomExercise(id: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(id, "id")
        store.runInTx {
            val exercise = findExerciseByUidOrThrow(id)
            if (!exercise.isCustom) error("Only custom exercises can be deleted.")
            val planReferences = store.boxFor(PlanExerciseEntity::class.java)
                .query(PlanExerciseEntity_.exerciseUid.equal(id)).build().use { it.count() }
            val workoutReferences = store.boxFor(WorkoutExerciseEntity::class.java)
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
            val exercises = catalogRows()
            val target = exercises.find { it.id == exerciseId || it.exerciseId == exerciseId }
                ?: return@withContext SubstitutionEngine.AlternativesResult.EMPTY
            SubstitutionEngine.buildExerciseAlternatives(target, exercises, filters, filters.limit ?: 12)
        }

    internal fun findExerciseByUidOrThrow(uid: String): ExerciseEntity =
        exercisesBox.query(ExerciseEntity_.uid.equal(uid)).build().use { it.findFirst() }
            ?: error("Exercise not found: $uid")

    internal fun findExerciseByName(name: String): ExerciseEntity? =
        exercisesBox.query(ExerciseEntity_.name.equal(name)).build().use { it.findFirst() }

    /** Called inside the owning write transaction; duplicate legacy rows are not rewritten. */
    private fun requireUniqueName(normalized: String, exceptId: Long = 0) {
        val duplicate = exercisesBox.query(ExerciseEntity_.normalizedName.equal(normalized)).build().use { query ->
            query.find().any { it.objectBoxId != exceptId }
        }
        if (duplicate) error("Exercise with this name already exists.")
    }

    /** Batch-load relationships once, avoiding a lazy relation query per catalog entry. */
    private fun catalogRows(filters: ExerciseFilters = ExerciseFilters()): List<LegacyExerciseShape> = store.callInReadTx {
        mapExerciseRows(filterRows(exercisesBox.all, filters))
    }

    private fun mapExerciseRows(rows: List<ExerciseEntity>): List<LegacyExerciseShape> {
        val primaryByUid = musclesBox.all.filter { it.role.equals("primary", true) }.groupBy { it.exerciseUid }
        return rows.map { row -> mapExerciseRowToLegacyShape(row, primaryByUid[row.uid].orEmpty().map { it.muscle }) }
    }

    private fun metadataStrings(json: String?): List<String> = json?.let {
        runCatching {
            val array = org.json.JSONArray(it)
            (0 until array.length()).mapNotNull { index -> (array.opt(index) as? String)?.trim()?.takeIf(String::isNotBlank) }.distinct()
        }.getOrElse { emptyList() }
    }.orEmpty()

    internal fun mapExerciseRowToLegacyShape(row: ExerciseEntity, primaryRelationships: List<String> = emptyList()): LegacyExerciseShape {
        val primary = (listOf(row.primaryMuscle) + primaryRelationships).map(String::trim).filter(String::isNotBlank).distinct()
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
            secondaryMuscles = metadataStrings(row.secondaryMusclesJson),
            equipment = row.equipment.ifBlank { "Other" },
            category = category,
            trackingType = trackingType,
            isCustom = row.isCustom,
            aliases = metadataStrings(row.aliasesJson),
            isBodyweight = row.isBodyweight || row.equipment.lowercase() == "bodyweight",
            movementPattern = row.movementPattern,
            difficulty = row.difficulty,
            apparatus = row.apparatusJson,
            equipmentDetail = row.equipmentDetail,
            sourceTags = metadataStrings(row.sourceTagsJson),
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
        val rows = catalogRows()
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
