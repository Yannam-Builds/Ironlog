package com.ironlog.app.ui.viewmodel

import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.PlanRepository
import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.model.UiPlanExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Scope-free reader. Observation starts only when its owning ViewModel collects it. */
interface PlanUiSource {
    fun observe(): Flow<List<UiPlan>>
    suspend fun snapshot(): List<UiPlan>
}

class RepositoryPlanUiSource(
    private val plansRepo: PlanRepository = PlanRepository(),
    private val exerciseRepo: ExerciseRepository = ExerciseRepository(),
) : PlanUiSource {
    override fun observe(): Flow<List<UiPlan>> = plansRepo.getPlansFlow()
        .map { snapshot() }.flowOn(Dispatchers.IO)

    override suspend fun snapshot(): List<UiPlan> = withContext(Dispatchers.IO) {
        plansRepo.ensureActivePlanIfNeeded()
        val planRows = plansRepo.getPlansFlow().first()
        val exerciseIndex = exerciseRepo.getExercisesSnapshot().associateBy { it.id }
        planRows.map { plan ->
            val days = plansRepo.getPlanDaysSnapshot(plan.uid).sortedBy { it.orderIndex }
            UiPlan(
                id = plan.uid,
                name = plan.name,
                goal = plan.goal,
                description = plan.description,
                isActive = plan.isActive,
                days = days.map { day ->
                    val peRows = plansRepo.getPlanExercisesSnapshot(day.uid).sortedBy { it.orderIndex }
                    UiPlanDay(
                        id = day.uid,
                        name = day.name,
                        color = day.color,
                        exercises = peRows.map { pe ->
                            val ex = exerciseIndex[pe.exerciseUid]
                            UiPlanExercise(
                                id = pe.uid,
                                exerciseId = pe.exerciseUid,
                                name = ex?.name ?: "Unknown",
                                sets = pe.sets,
                                reps = pe.reps,
                                restSeconds = pe.restSeconds,
                                supersetGroup = pe.supersetGroup,
                                isWarmup = pe.isWarmup,
                                notes = pe.notes,
                                isCustom = ex?.isCustom == true,
                                primaryMuscle = ex?.primaryMuscle.orEmpty(),
                                equipment = ex?.equipment.orEmpty(),
                                category = ex?.category ?: "strength",
                                trackingType = ex?.trackingType ?: "weight_reps",
                                isBodyweight = ex?.isBodyweight == true,
                                movementPattern = ex?.movementPattern,
                                difficulty = ex?.difficulty,
                                exerciseNotes = ex?.notes.orEmpty(),
                                secondaryMuscles = ex?.secondaryMuscles.orEmpty(),
                            )
                        },
                    )
                },
            )
        }
    }
}
