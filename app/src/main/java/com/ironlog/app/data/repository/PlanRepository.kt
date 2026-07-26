package com.ironlog.app.data.repository

import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.PlanBundle
import com.ironlog.app.data.model.PlanDayInput
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.data.model.PlanInput
import com.ironlog.app.data.model.PlanSnapshot
import com.ironlog.app.data.model.WorkoutPerformedExercise
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class PlanRepository {
    private val plansBox get() = ObjectBox.store.boxFor(PlanEntity::class.java)
    private val daysBox get() = ObjectBox.store.boxFor(PlanDayEntity::class.java)
    private val planExercisesBox get() = ObjectBox.store.boxFor(PlanExerciseEntity::class.java)
    private val exercisesBox get() = ObjectBox.store.boxFor(ExerciseEntity::class.java)

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
        plansBox.query().orderDesc(PlanEntity_.updatedAt).build().asFlow()

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
        return plansBox.query(PlanEntity_.uid.equal(planId)).build().asFlow()
    }

    fun getPlanDaysFlow(planId: String): Flow<List<PlanDayEntity>> {
        requireNonEmpty(planId, "planId")
        return daysBox.query(PlanDayEntity_.planUid.equal(planId)).order(PlanDayEntity_.orderIndex).build().asFlow()
    }

    fun getPlanExercisesFlow(planDayId: String): Flow<List<PlanExerciseEntity>> {
        requireNonEmpty(planDayId, "planDayId")
        return planExercisesBox.query(PlanExerciseEntity_.planDayUid.equal(planDayId)).order(PlanExerciseEntity_.orderIndex).build().asFlow()
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

    suspend fun importFullPlan(planObject: FullPlanObject): PlanEntity = withContext(Dispatchers.IO) {
        requireNonEmpty(planObject.name, "plan name")
        val now = System.currentTimeMillis()
        val mapper = FuzzyExerciseMapper(exercisesBox.all)
        val shouldActivate = plansBox.query(PlanEntity_.isActive.equal(true)).build().use { it.count() == 0L }

        val resolvedDays = planObject.days.mapIndexed { index, day ->
            val resolved = day.exercises.map { ex -> ex to resolveExerciseId(ex, mapper) }
            Triple(day, index, resolved)
        }

        val wmPlan = PlanEntity().apply {
            name = planObject.name!!.trim()
            goal = (planObject.goal ?: "General Fitness").trim()
            description = (planObject.description ?: "").trim()
            isActive = shouldActivate
            createdAt = now
            updatedAt = now
        }
        plansBox.put(wmPlan)

        for ((day, index, resolvedExercises) in resolvedDays) {
            val wmDay = PlanDayEntity().apply {
                plan.target = wmPlan
                planUid = wmPlan.uid
                name = (day.name ?: "Day ${index + 1}").trim()
                color = day.color ?: "#FF4500"
                orderIndex = index
                createdAt = now
                updatedAt = now
            }
            daysBox.put(wmDay)
            var exOrder = 0
            for ((ex, resolvedId) in resolvedExercises) {
                val exercise = resolvedId?.let(::findExerciseByUidOrNull) ?: continue
                val row = PlanExerciseEntity().apply {
                    planDay.target = wmDay
                    planDayUid = wmDay.uid
                    this.exercise.target = exercise
                    exerciseUid = exercise.uid
                    orderIndex = exOrder
                    sets = ex.sets ?: 3
                    reps = (ex.reps ?: "8-12").trim()
                    restSeconds = ex.restSeconds ?: 90
                    supersetGroup = ex.supersetGroup ?: ""
                    isWarmup = ex.isWarmup == true
                    notes = ex.notes ?: ""
                    createdAt = now
                    updatedAt = now
                }
                planExercisesBox.put(row)
                exOrder++
            }
        }
        wmPlan
    }

    suspend fun reorderPlans(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        val rowMap = plansBox.all.associateBy { it.uid }
        val now = System.currentTimeMillis()
        val toUpdate = mutableListOf<com.ironlog.app.data.objectbox.PlanEntity>()
        orderedIds.forEachIndexed { index, id ->
            val row = rowMap[id] ?: return@forEachIndexed
            // updatedAt ordering: first item gets highest value so orderDesc puts it first.
            row.updatedAt = now - (index * 1000L)
            // Explicitly mark the top plan as active so that after a restart
            // firstOrNull { it.isActive } reliably picks the right plan instead
            // of relying on the fragile updatedAt ordering heuristic.
            row.isActive = (index == 0)
            toUpdate.add(row)
        }
        if (toUpdate.isNotEmpty()) plansBox.put(toUpdate)
    }

    suspend fun replaceAllPlans(nextPlans: List<FullPlanObject> = emptyList()) = withContext(Dispatchers.IO) {
        val exerciseRows = planExercisesBox.all
        val dayRows = daysBox.all
        val planRows = plansBox.all
        if (exerciseRows.isNotEmpty()) planExercisesBox.remove(exerciseRows)
        if (dayRows.isNotEmpty()) daysBox.remove(dayRows)
        if (planRows.isNotEmpty()) plansBox.remove(planRows)
        nextPlans.forEach { plan ->
            importFullPlan(
                FullPlanObject(
                    name = plan.name ?: "Plan",
                    goal = plan.goal ?: "General Fitness",
                    description = plan.description ?: "",
                    days = plan.days,
                )
            )
        }
    }

    private fun resolveExerciseId(ex: PlanExerciseInput, mapper: FuzzyExerciseMapper? = null): String? {
        ex.exerciseId?.let { if (findExerciseByUidOrNull(it) != null) return it }
        ex.name?.let { name ->
            val exact = exercisesBox.query(ExerciseEntity_.name.equal(name)).build().use { q -> q.findFirst()?.let { it.uid } }
            if (exact != null) return exact
            return mapper?.match(name)
        }
        return null
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
