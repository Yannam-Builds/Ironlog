package com.ironlog.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.data.model.PlanInput
import com.ironlog.app.data.plan.PlanImportResult
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.PlanRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.model.UiPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PlanNoteMutationCoordinator {
    private val mutex = Mutex()

    fun launch(scope: CoroutineScope, block: suspend () -> Unit): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            run(block)
        }

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}

/** Manages training plan data including CRUD and program scheduling. */
class PlansViewModel(
    private val plansRepo: PlanRepository = PlanRepository(),
    private val exerciseRepo: ExerciseRepository = ExerciseRepository(),
    private val settingsRepo: SettingsRepository = SettingsRepository(),
) : ViewModel() {
    private val planSource = RepositoryPlanUiSource(plansRepo, exerciseRepo)
    private val planNoteMutations = PlanNoteMutationCoordinator()
    private val _plans = MutableStateFlow<List<UiPlan>>(emptyList())
    val plans: StateFlow<List<UiPlan>> = _plans.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        observePlanChanges()
        refresh()
    }

    private fun observePlanChanges() {
        viewModelScope.launch {
            plansRepo.getPlansFlow().collectLatest {
                _plans.value = assemblePlans()
                _loading.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshNow()
        }
    }

    suspend fun refreshNow() {
        _loading.value = true
        _plans.value = assemblePlans()
        _loading.value = false
    }

    private suspend fun assemblePlans(): List<UiPlan> = planSource.snapshot()

    fun addPlan(name: String) = viewModelScope.launch {
        plansRepo.createPlan(PlanInput(name = name.trim(), goal = "General Fitness", description = "", isActive = false))
        settingsRepo.setBoolean("gamification_user_created_plan", true)
        refresh()
    }

    fun renamePlan(planId: String, name: String) = viewModelScope.launch {
        plansRepo.updatePlan(planId, PlanInput(name = name.trim()))
        refresh()
    }

    fun removePlan(planId: String) = viewModelScope.launch {
        plansRepo.deletePlan(planId)
        refresh()
    }

    /** Deactivates all plans, then marks [planId] as active. */
    fun setActivePlan(planId: String) = viewModelScope.launch {
        val all = plansRepo.getPlansFlowReplayOnce()
        all.forEach { plan ->
            val shouldBeActive = plan.uid == planId
            if (plan.isActive != shouldBeActive) {
                plansRepo.updatePlan(plan.uid, PlanInput(isActive = shouldBeActive))
            }
        }
        refresh()
    }

    fun addDay(planId: String, name: String, color: String = "#FF4500") = viewModelScope.launch {
        plansRepo.createPlanDay(planId, com.ironlog.app.data.model.PlanDayInput(name = name, color = color))
        refresh()
    }

    /**
     * Creates a new plan day and returns its generated ID.
     * Unlike [addDay], this suspend function waits for the write to complete
     * and returns the persisted day's UID, which callers need to immediately
     * add exercises to the new day (e.g. deload day generation).
     */
    suspend fun addDayAndGetId(planId: String, name: String, color: String = "#FF4500"): String =
        withContext(Dispatchers.IO) {
            val created = plansRepo.createPlanDay(planId, com.ironlog.app.data.model.PlanDayInput(name = name, color = color))
            created.uid
        }.also { viewModelScope.launch { refresh() } }

    fun renameDay(dayId: String, name: String) = viewModelScope.launch {
        plansRepo.updatePlanDay(dayId, com.ironlog.app.data.model.PlanDayInput(name = name))
        refresh()
    }

    fun updateDayColor(dayId: String, color: String) = viewModelScope.launch {
        plansRepo.updatePlanDay(dayId, com.ironlog.app.data.model.PlanDayInput(color = color))
        refresh()
    }

    fun removeDay(dayId: String) = viewModelScope.launch {
        plansRepo.deletePlanDay(dayId)
        refresh()
    }

    fun addExerciseToDay(dayId: String, exerciseId: String, meta: PlanExerciseInput = PlanExerciseInput()) = viewModelScope.launch {
        plansRepo.addExerciseToPlanDay(dayId, PlanExerciseInput(
            exerciseId = exerciseId,
            sets = meta.sets ?: 3,
            reps = meta.reps ?: "8-12",
            restSeconds = meta.restSeconds ?: 90,
            supersetGroup = meta.supersetGroup ?: "",
            isWarmup = meta.isWarmup ?: false,
            notes = meta.notes ?: "",
        ))
        refresh()
    }

    fun updateExercise(planExerciseId: String, updates: PlanExerciseInput): Job =
        if (updates.notes != null) {
            planNoteMutations.launch(viewModelScope) {
                plansRepo.updatePlanExercise(planExerciseId, updates)
                refreshNow()
            }
        } else {
            viewModelScope.launch {
                plansRepo.updatePlanExercise(planExerciseId, updates)
                refreshNow()
            }
        }

    suspend fun clearPlanNotesNow(planId: String) {
        planNoteMutations.run {
            plansRepo.clearPlanNotes(planId)
            refreshNow()
        }
    }

    fun clearPlanNotes(planId: String): Job =
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            clearPlanNotesNow(planId)
        }

    fun removeExercise(planExerciseId: String) = viewModelScope.launch {
        plansRepo.removePlanExercise(planExerciseId)
        refresh()
    }

    fun reorderExercises(dayId: String, orderedIds: List<String>) = viewModelScope.launch {
        reorderExercisesAndAwait(dayId, orderedIds)
    }

    suspend fun reorderExercisesAndAwait(dayId: String, orderedIds: List<String>) {
        plansRepo.reorderPlanExercises(dayId, orderedIds)
        refreshNow()
    }

    fun reorderPlans(orderedIds: List<String>) = viewModelScope.launch {
        plansRepo.reorderPlans(orderedIds)
        refresh()
    }

    fun importPlan(planObject: FullPlanObject) = viewModelScope.launch {
        plansRepo.importFullPlan(planObject)
        refresh()
    }

    fun importPlans(planObjects: List<FullPlanObject>) = viewModelScope.launch {
        importPlansNow(planObjects)
    }

    suspend fun importPlansNow(
        planObjects: List<FullPlanObject>,
        initiallySkipped: Int = 0,
    ): PlanImportResult {
        val result = plansRepo.importPlansAtomically(planObjects, initiallySkipped)
        refreshNow()
        return result
    }

    // GAP-12: copy all exercises from one day to another day
    fun copyDay(sourceDayId: String, targetDayId: String) = viewModelScope.launch {
        val allPlans = _plans.value
        val sourceDay = allPlans.flatMap { it.days }.firstOrNull { it.id == sourceDayId } ?: return@launch
        allPlans.flatMap { it.days }.firstOrNull { it.id == targetDayId } ?: return@launch
        sourceDay.exercises.forEach { ex ->
            plansRepo.addExerciseToPlanDay(
                targetDayId,
                PlanExerciseInput(
                    exerciseId = ex.exerciseId,
                    sets = ex.sets,
                    reps = ex.reps,
                    restSeconds = ex.restSeconds,
                    supersetGroup = ex.supersetGroup,
                    isWarmup = ex.isWarmup,
                    notes = ex.notes,
                )
            )
        }
        refresh()
    }

    // GAP-10: duplicate a plan (deep-copies all days + exercises, "(Copy)" name suffix, not active)
    fun duplicatePlan(plan: UiPlan) = viewModelScope.launch {
        val copy = FullPlanObject(
            name = "${plan.name} (Copy)",
            goal = plan.goal,
            description = plan.description,
            days = plan.days.map { day ->
                FullPlanDay(
                    name = day.name,
                    color = day.color,
                    exercises = day.exercises.map { ex ->
                        PlanExerciseInput(
                            exerciseId = ex.exerciseId,
                            sets = ex.sets,
                            reps = ex.reps,
                            restSeconds = ex.restSeconds,
                            supersetGroup = ex.supersetGroup,
                            isWarmup = ex.isWarmup,
                            notes = ex.notes,
                        )
                    },
                )
            },
        )
        plansRepo.importFullPlan(copy)
        refresh()
    }
}

private suspend fun PlanRepository.getPlansFlowReplayOnce(): List<com.ironlog.app.data.objectbox.PlanEntity> = getPlansFlow().first()
