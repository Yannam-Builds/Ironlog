package com.ironlog.app.data.repository

import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.PlanBundle
import com.ironlog.app.data.model.PlanDayInput
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.data.model.PlanInput
import com.ironlog.app.data.model.PlanSnapshot
import com.ironlog.app.data.model.WorkoutPerformedExercise
import com.ironlog.app.data.plan.PlanImportResult
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.PlanDayEntity
import com.ironlog.app.data.objectbox.PlanDayEntity_
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.PlanEntity_
import com.ironlog.app.data.objectbox.PlanExerciseEntity
import com.ironlog.app.data.objectbox.PlanExerciseEntity_
import com.ironlog.app.util.requireNonEmpty
import com.ironlog.app.util.requireNumberMin
import com.ironlog.app.util.FuzzyExerciseMapper
import com.ironlog.app.util.ExerciseTrackingTypeNormalizer
import com.ironlog.app.util.normalizeExerciseName
import com.ironlog.app.util.normalizeExerciseNameKey
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.max
import kotlin.math.roundToInt

class PlanRepository(private val boxStore: BoxStore? = null) {
    private val store get() = boxStore ?: ObjectBox.store
    private val plansBox get() = store.boxFor(PlanEntity::class.java)
    private val daysBox get() = store.boxFor(PlanDayEntity::class.java)
    private val planExercisesBox get() = store.boxFor(PlanExerciseEntity::class.java)
    private val exercisesBox get() = store.boxFor(ExerciseEntity::class.java)

    suspend fun createPlan(input: PlanInput): PlanEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(input.name, "name")
        requireNonEmpty(input.goal, "goal")
        val now = System.currentTimeMillis()
        // Auto-activate the plan if it's the first one, or if the caller says so.
        val noActivePlanExists = plansBox.query(PlanEntity_.isActive.equal(true)).build().use { it.count() == 0L }
        val created = PlanEntity().apply {
            name = input.name!!.trim()
            goal = input.goal!!.trim()
            description = input.description?.toString() ?: ""
            isActive = input.isActive == true || noActivePlanExists
            createdAt = now
            updatedAt = now
        }
        plansBox.put(created)
        created
    }

    suspend fun updatePlan(planId: String, input: PlanInput): PlanEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(planId, "planId")
        val plan = findPlanByUidOrThrow(planId)
        if (!input.name.isNullOrEmpty()) plan.name = input.name.trim()
        if (!input.goal.isNullOrEmpty()) plan.goal = input.goal.trim()
        if (input.description != null) plan.description = input.description.ifEmpty { "" }
        if (input.isActive != null) plan.isActive = input.isActive
        plan.updatedAt = System.currentTimeMillis()
        plansBox.put(plan)
        plan
    }

    /** Clears plan-owned notes without modifying reusable exercise-library notes. */
    suspend fun clearPlanNotes(planId: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(planId, "planId")
        store.runInTx {
            val plan = findPlanByUidOrThrow(planId)
            val dayIds = getPlanDaysSnapshot(planId).map { it.uid }
            val planExercises = if (dayIds.isEmpty()) {
                emptyList()
            } else {
                planExercisesBox.query(PlanExerciseEntity_.planDayUid.oneOf(dayIds.toTypedArray()))
                    .build().use { it.find() }
            }
            val now = System.currentTimeMillis()
            plan.description = ""
            plan.updatedAt = now
            planExercises.forEach { row ->
                row.notes = ""
                row.updatedAt = now
            }
            plansBox.put(plan)
            if (planExercises.isNotEmpty()) planExercisesBox.put(planExercises)
        }
    }

    suspend fun deletePlan(planId: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(planId, "planId")
        val plan = findPlanByUidOrThrow(planId)
        val days = getPlanDaysSnapshot(planId)
        val dayIds = days.map { it.uid }
        val exercises = if (dayIds.isEmpty()) emptyList() else {
            planExercisesBox.query(PlanExerciseEntity_.planDayUid.oneOf(dayIds.toTypedArray()))
                .build().use { it.find() }
        }
        planExercisesBox.remove(exercises)
        daysBox.remove(days)
        plansBox.remove(plan)
        Unit
    }

    fun getPlansFlow(): Flow<List<PlanEntity>> =
        observeQuery { plansBox.query().orderDesc(PlanEntity_.updatedAt).build() }

    suspend fun ensureActivePlanIfNeeded(): PlanEntity? = withContext(Dispatchers.IO) {
        val plans = plansBox.query().orderDesc(PlanEntity_.updatedAt).build().use { it.find() }
        if (plans.isEmpty()) return@withContext null

        val selected = plans.firstOrNull { it.isActive } ?: plans.first()
        var changed = false
        plans.forEach { plan ->
            val shouldBeActive = plan.uid == selected.uid
            if (plan.isActive != shouldBeActive) {
                plan.isActive = shouldBeActive
                changed = true
            }
        }
        if (changed) plansBox.put(plans)
        selected
    }

    fun getPlanFlow(planId: String): Flow<List<PlanEntity>> {
        requireNonEmpty(planId, "planId")
        return observeQuery { plansBox.query(PlanEntity_.uid.equal(planId)).build() }
    }

    fun getPlanDaysFlow(planId: String): Flow<List<PlanDayEntity>> {
        requireNonEmpty(planId, "planId")
        return observeQuery { daysBox.query(PlanDayEntity_.planUid.equal(planId)).order(PlanDayEntity_.orderIndex).build() }
    }

    fun getPlanExercisesFlow(planDayId: String): Flow<List<PlanExerciseEntity>> {
        requireNonEmpty(planDayId, "planDayId")
        return observeQuery { planExercisesBox.query(PlanExerciseEntity_.planDayUid.equal(planDayId)).order(PlanExerciseEntity_.orderIndex).build() }
    }

    fun getPlanBundleFlow(planId: String, activeDayId: String? = null): Flow<PlanBundle> {
        requireNonEmpty(planId, "planId")
        return combine(getPlanFlow(planId), getPlanDaysFlow(planId)) { planRows, days ->
            val plan = planRows.firstOrNull() ?: error("Plan not found: $planId")
            PlanBundle(plan, days, activeDayId ?: days.firstOrNull()?.uid)
        }
    }

    suspend fun getPlanById(planId: String): PlanSnapshot = withContext(Dispatchers.IO) {
        requireNonEmpty(planId, "planId")
        val plan = findPlanByUidOrThrow(planId)
        val days = getPlanDaysSnapshot(planId)
        val dayIds = days.map { it.uid }.toSet()
        val exercises = if (dayIds.isEmpty()) emptyList() else {
            planExercisesBox.query(PlanExerciseEntity_.planDayUid.oneOf(dayIds.toTypedArray()))
                .order(PlanExerciseEntity_.orderIndex)
                .build().use { it.find() }
        }
        PlanSnapshot(plan, days, exercises)
    }

    suspend fun createPlanDay(planId: String, input: PlanDayInput): PlanDayEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(planId, "planId")
        requireNonEmpty(input.name, "name")
        val plan = findPlanByUidOrThrow(planId)
        val now = System.currentTimeMillis()
        val count = getPlanDaysSnapshot(planId).size
        val created = PlanDayEntity().apply {
            this.plan.target = plan
            planUid = plan.uid
            name = input.name!!.trim()
            color = input.color ?: "#FF4500"
            orderIndex = input.orderIndex ?: count
            createdAt = now
            updatedAt = now
        }
        daysBox.put(created)
        created
    }

    suspend fun updatePlanDay(dayId: String, input: PlanDayInput): PlanDayEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(dayId, "dayId")
        val day = findDayByUidOrThrow(dayId)
        if (!input.name.isNullOrEmpty()) day.name = input.name.trim()
        if (!input.color.isNullOrEmpty()) day.color = input.color
        if (input.orderIndex != null) day.orderIndex = input.orderIndex
        day.updatedAt = System.currentTimeMillis()
        daysBox.put(day)
        day
    }

    suspend fun deletePlanDay(dayId: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(dayId, "dayId")
        val day = findDayByUidOrThrow(dayId)
        val exercises = getPlanExercisesSnapshot(dayId)
        planExercisesBox.remove(exercises)
        daysBox.remove(day)
        Unit
    }

    suspend fun addExerciseToPlanDay(dayId: String, input: PlanExerciseInput): PlanExerciseEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(dayId, "dayId")
        requireNonEmpty(input.exerciseId, "exerciseId")
        requireNumberMin(input.sets ?: 1, 1.0, "sets")
        requireNonEmpty(input.reps, "reps")
        requireNumberMin(input.restSeconds ?: 0, 0.0, "restSeconds")
        val day = findDayByUidOrThrow(dayId)
        val exercise = findExerciseByUidOrThrow(input.exerciseId!!)
        val now = System.currentTimeMillis()
        val count = getPlanExercisesSnapshot(dayId).size
        val created = PlanExerciseEntity().apply {
            planDay.target = day
            planDayUid = day.uid
            this.exercise.target = exercise
            exerciseUid = exercise.uid
            orderIndex = input.orderIndex ?: count
            sets = input.sets ?: 1
            reps = input.reps!!.trim()
            restSeconds = input.restSeconds ?: 0
            supersetGroup = input.supersetGroup?.toString() ?: ""
            isWarmup = input.isWarmup == true
            notes = input.notes?.toString() ?: ""
            createdAt = now
            updatedAt = now
        }
        planExercisesBox.put(created)
        created
    }

    suspend fun updatePlanExercise(planExerciseId: String, input: PlanExerciseInput): PlanExerciseEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(planExerciseId, "planExerciseId")
        val row = findPlanExerciseByUidOrThrow(planExerciseId)
        if (!input.exerciseId.isNullOrEmpty()) {
            val ex = findExerciseByUidOrThrow(input.exerciseId)
            row.exercise.target = ex
            row.exerciseUid = ex.uid
        }
        if (input.orderIndex != null) row.orderIndex = input.orderIndex
        if (input.sets != null) row.sets = input.sets
        if (input.reps != null) row.reps = input.reps
        if (input.restSeconds != null) row.restSeconds = input.restSeconds
        if (input.supersetGroup != null) row.supersetGroup = input.supersetGroup.ifEmpty { "" }
        if (input.isWarmup != null) row.isWarmup = input.isWarmup
        if (input.notes != null) row.notes = input.notes.ifEmpty { "" }
        row.updatedAt = System.currentTimeMillis()
        planExercisesBox.put(row)
        row
    }

    suspend fun removePlanExercise(planExerciseId: String) = withContext(Dispatchers.IO) {
        requireNonEmpty(planExerciseId, "planExerciseId")
        planExercisesBox.remove(findPlanExerciseByUidOrThrow(planExerciseId))
        Unit
    }

    suspend fun syncPlanDayFromWorkout(dayId: String, exerciseData: List<WorkoutPerformedExercise>) = withContext(Dispatchers.IO) {
        requireNonEmpty(dayId, "dayId")
        val day = findDayByUidOrThrow(dayId)
        val now = System.currentTimeMillis()
        val existing = getPlanExercisesSnapshot(dayId)
        planExercisesBox.remove(existing)

        val creates = mutableListOf<PlanExerciseEntity>()
        var order = 0
        for (ex in exerciseData) {
            val exerciseId = ex.exerciseId ?: continue
            val exercise = findExerciseByUidOrNull(exerciseId) ?: continue
            val workSets = ex.sets.orEmpty().filter { it.isWarmup != true && it.type != "warmup" }
            val setCount = workSets.size.takeIf { it > 0 } ?: 1
            val avgReps = if (workSets.isNotEmpty()) {
                (workSets.sumOf { it.reps ?: 0.0 } / workSets.size).roundToInt()
            } else {
                when (val pr = ex.prescribedReps) {
                    is Number -> pr.toInt()
                    is String -> pr.toIntOrNull() ?: 8
                    else -> 8
                }
            }
            val restValues = workSets.map { (it.restSeconds ?: it.rest ?: 0) }.filter { it > 0 }
            val avgRestSeconds = if (restValues.isNotEmpty()) max(0, restValues.average().roundToInt()) else 90
            creates += PlanExerciseEntity().apply {
                planDay.target = day
                planDayUid = day.uid
                this.exercise.target = exercise
                exerciseUid = exercise.uid
                orderIndex = order
                sets = setCount
                reps = (avgReps.takeIf { it != 0 } ?: 8).toString()
                restSeconds = avgRestSeconds
                supersetGroup = ""
                isWarmup = false
                notes = ""
                createdAt = now
                updatedAt = now
            }
            order++
        }
        if (creates.isNotEmpty()) planExercisesBox.put(creates)
    }

    suspend fun reorderPlanExercises(dayId: String, orderedIds: List<String>) = withContext(Dispatchers.IO) {
        requireNonEmpty(dayId, "dayId")
        val rowMap = getPlanExercisesSnapshot(dayId).associateBy { it.uid }
        val now = System.currentTimeMillis()
        val toUpdate = mutableListOf<com.ironlog.app.data.objectbox.PlanExerciseEntity>()
        orderedIds.forEachIndexed { index, id ->
            val row = rowMap[id] ?: return@forEachIndexed
            row.orderIndex = index
            row.updatedAt = now
            toUpdate.add(row)
        }
        if (toUpdate.isNotEmpty()) planExercisesBox.put(toUpdate)
    }

    suspend fun importFullPlan(planObject: FullPlanObject): PlanEntity {
        val result = importPlansAtomically(listOf(planObject))
        return withContext(Dispatchers.IO) { findPlanByUidOrThrow(result.importedPlanIds.single()) }
    }

    /**
     * Validates the complete batch before opening one ObjectBox transaction. Unknown named
     * movements are recreated as custom exercises instead of being silently omitted.
     */
    suspend fun importPlansAtomically(
        plans: List<FullPlanObject>,
        initiallySkipped: Int = 0,
    ): PlanImportResult = withContext(Dispatchers.IO) {
        validateImportBatch(plans)
        store.callInTx { writeImportedPlans(plans, initiallySkipped) }
    }

    suspend fun reorderPlans(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        val rowMap = plansBox.all.associateBy { it.uid }
        val now = System.currentTimeMillis()
        val toUpdate = mutableListOf<com.ironlog.app.data.objectbox.PlanEntity>()
        orderedIds.forEachIndexed { index, id ->
            val row = rowMap[id] ?: return@forEachIndexed
            // updatedAt ordering: first item gets highest value so orderDesc puts it first.
            row.updatedAt = now - (index * 1000L)
            toUpdate.add(row)
        }
        if (toUpdate.isNotEmpty()) plansBox.put(toUpdate)
    }

    suspend fun replaceAllPlans(nextPlans: List<FullPlanObject> = emptyList()): Unit = withContext(Dispatchers.IO) {
        validateImportBatch(nextPlans)
        store.runInTx {
            val exerciseRows = planExercisesBox.all
            val dayRows = daysBox.all
            val planRows = plansBox.all
            if (exerciseRows.isNotEmpty()) planExercisesBox.remove(exerciseRows)
            if (dayRows.isNotEmpty()) daysBox.remove(dayRows)
            if (planRows.isNotEmpty()) plansBox.remove(planRows)
            writeImportedPlans(nextPlans, initiallySkipped = 0)
        }
    }

    private fun validateImportBatch(plans: List<FullPlanObject>) {
        plans.forEachIndexed { planIndex, plan ->
            requireNonEmpty(plan.name, "plan ${planIndex + 1} name")
            plan.days.forEachIndexed { dayIndex, day ->
                day.exercises.forEachIndexed { exerciseIndex, exercise ->
                    val label = "plan ${planIndex + 1}, day ${dayIndex + 1}, exercise ${exerciseIndex + 1}"
                    exercise.sets?.let { require(it >= 1) { "$label sets must be at least 1" } }
                    exercise.restSeconds?.let { require(it >= 0) { "$label restSeconds cannot be negative" } }
                }
            }
        }
    }

    private fun writeImportedPlans(
        plans: List<FullPlanObject>,
        initiallySkipped: Int,
    ): PlanImportResult {
        val now = System.currentTimeMillis()
        val mapper = FuzzyExerciseMapper(exercisesBox.all)
        var shouldActivate = plansBox.query(PlanEntity_.isActive.equal(true)).build().use { it.count() == 0L }
        val importedPlanIds = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        var importedExercises = 0
        var createdCustomExercises = 0
        var skipped = initiallySkipped

        plans.forEach { planObject ->
            val plan = PlanEntity().apply {
                name = planObject.name!!.trim()
                goal = planObject.goal?.trim()?.ifBlank { "General Fitness" } ?: "General Fitness"
                description = planObject.description.orEmpty().trim()
                isActive = shouldActivate
                createdAt = now
                updatedAt = now
            }
            plansBox.put(plan)
            importedPlanIds += plan.uid
            shouldActivate = false

            planObject.days.forEachIndexed { dayIndex, day ->
                val planDay = PlanDayEntity().apply {
                    this.plan.target = plan
                    planUid = plan.uid
                    name = day.name?.trim()?.ifBlank { "Day ${dayIndex + 1}" } ?: "Day ${dayIndex + 1}"
                    color = day.color?.trim()?.ifBlank { "#FF4500" } ?: "#FF4500"
                    orderIndex = dayIndex
                    createdAt = now
                    updatedAt = now
                }
                daysBox.put(planDay)
                var storedOrder = 0

                day.exercises.withIndex()
                    .sortedWith(
                        compareBy<IndexedValue<PlanExerciseInput>>(
                            { indexed -> indexed.value.orderIndex ?: indexed.index },
                            { indexed -> indexed.index },
                        ),
                    )
                    .forEach exerciseLoop@ { indexed ->
                        val input = indexed.value
                        if (input.exerciseId.isNullOrBlank() && input.name.isNullOrBlank()) {
                            skipped++
                            return@exerciseLoop
                        }
                        val resolution = resolveOrCreateExercise(input, mapper, now)
                        val exercise = resolution.exercise
                        if (exercise == null) {
                            unresolved += input.name?.trim().takeUnless { it.isNullOrBlank() }
                                ?: input.exerciseId.orEmpty().trim()
                            return@exerciseLoop
                        }
                        if (resolution.createdCustom) createdCustomExercises++

                        val row = PlanExerciseEntity().apply {
                            this.planDay.target = planDay
                            planDayUid = planDay.uid
                            this.exercise.target = exercise
                            exerciseUid = exercise.uid
                            orderIndex = storedOrder
                            sets = input.sets ?: 3
                            reps = input.reps?.trim()?.ifBlank { "8-12" } ?: "8-12"
                            restSeconds = input.restSeconds ?: 90
                            supersetGroup = input.supersetGroup.orEmpty()
                            isWarmup = input.isWarmup == true
                            notes = input.notes.orEmpty()
                            createdAt = now
                            updatedAt = now
                        }
                        planExercisesBox.put(row)
                        storedOrder++
                        importedExercises++
                    }
            }
        }

        return PlanImportResult(
            importedPlanIds = importedPlanIds,
            importedExercises = importedExercises,
            createdCustomExercises = createdCustomExercises,
            unresolvedExercises = unresolved,
            skipped = skipped,
        )
    }

    private data class ExerciseResolution(
        val exercise: ExerciseEntity?,
        val createdCustom: Boolean = false,
    )

    private fun resolveOrCreateExercise(
        input: PlanExerciseInput,
        mapper: FuzzyExerciseMapper,
        now: Long,
    ): ExerciseResolution {
        input.exerciseId?.trim()?.takeIf { it.isNotBlank() }?.let { uid ->
            findExerciseByUidOrNull(uid)?.let { return ExerciseResolution(it) }
        }

        val requestedName = normalizeExerciseName(input.name).takeIf { it.isNotBlank() }
            ?: return ExerciseResolution(null)
        findExerciseByNormalizedName(requestedName)?.let { return ExerciseResolution(it) }

        if (input.definition?.isCustom != true) {
            mapper.match(requestedName)?.let(::findExerciseByUidOrNull)?.let { return ExerciseResolution(it) }
        }

        val definition = input.definition
        val equipment = definition?.equipment?.trim()?.ifBlank { "other" } ?: "other"
        val category = definition?.category?.trim()?.ifBlank { "strength" } ?: "strength"
        val isBodyweight = definition?.isBodyweight ?: equipment.equals("bodyweight", ignoreCase = true)
        val trackingType = ExerciseTrackingTypeNormalizer.normalize(
            name = requestedName,
            category = category,
            equipment = equipment,
            explicitTrackingType = definition?.trackingType,
        )
        val created = ExerciseEntity().apply {
            input.exerciseId?.trim()?.takeIf { it.isNotBlank() }?.let { uid = it }
            name = requestedName
            normalizedName = normalizeExerciseNameKey(requestedName)
            primaryMuscle = definition?.primaryMuscle?.trim()?.ifBlank { "other" } ?: "other"
            this.equipment = equipment
            this.category = category
            isCustom = true
            source = "plan_import"
            notes = definition?.notes.orEmpty()
            createdAt = now
            updatedAt = now
            this.isBodyweight = isBodyweight
            requiresExternalLoad = !isBodyweight && trackingType in setOf("weight_reps", "duration_weight")
            this.trackingType = trackingType
            movementPattern = definition?.movementPattern?.trim()?.ifBlank { null }
            difficulty = definition?.difficulty?.trim()?.ifBlank { null }
            secondaryMusclesJson = definition?.secondaryMuscles?.takeIf { it.isNotEmpty() }
                ?.let { JSONArray(it).toString() }
        }
        exercisesBox.put(created)
        return ExerciseResolution(created, createdCustom = true)
    }

    private fun findExerciseByNormalizedName(name: String): ExerciseEntity? {
        val normalized = normalizeExerciseNameKey(name)
        return exercisesBox.query(ExerciseEntity_.normalizedName.equal(normalized)).build().use { it.findFirst() }
            ?: exercisesBox.query(ExerciseEntity_.name.equal(name)).build().use { it.findFirst() }
    }

    internal fun getPlanDaysSnapshot(planId: String): List<PlanDayEntity> =
        daysBox.query(PlanDayEntity_.planUid.equal(planId)).order(PlanDayEntity_.orderIndex).build().use { it.find() }

    internal fun getPlanExercisesSnapshot(dayId: String): List<PlanExerciseEntity> =
        planExercisesBox.query(PlanExerciseEntity_.planDayUid.equal(dayId)).order(PlanExerciseEntity_.orderIndex).build().use { it.find() }

    internal fun findPlanByUidOrThrow(uid: String): PlanEntity =
        plansBox.query(PlanEntity_.uid.equal(uid)).build().use { it.findFirst() } ?: error("Plan not found: $uid")

    internal fun findDayByUidOrThrow(uid: String): PlanDayEntity =
        daysBox.query(PlanDayEntity_.uid.equal(uid)).build().use { it.findFirst() } ?: error("Plan day not found: $uid")

    internal fun findPlanExerciseByUidOrThrow(uid: String): PlanExerciseEntity =
        planExercisesBox.query(PlanExerciseEntity_.uid.equal(uid)).build().use { it.findFirst() } ?: error("Plan exercise not found: $uid")

    private fun findExerciseByUidOrThrow(uid: String): ExerciseEntity =
        findExerciseByUidOrNull(uid) ?: error("Exercise not found: $uid")

    private fun findExerciseByUidOrNull(uid: String): ExerciseEntity? =
        exercisesBox.query(ExerciseEntity_.uid.equal(uid)).build().use { it.findFirst() }
}
