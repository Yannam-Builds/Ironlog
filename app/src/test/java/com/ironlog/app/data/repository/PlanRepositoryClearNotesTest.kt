package com.ironlog.app.data.repository

import com.ironlog.app.data.model.PlanDayInput
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.data.model.PlanInput
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.PlanExerciseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlanRepositoryClearNotesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `clear plan notes clears only the selected plan and preserves library notes`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("clear-plan-notes")).build().use { store ->
                val repository = PlanRepository(store)
                val exerciseBox = store.boxFor(ExerciseEntity::class.java)
                val planBox = store.boxFor(PlanEntity::class.java)
                val planExerciseBox = store.boxFor(PlanExerciseEntity::class.java)

                val libraryExercise = ExerciseEntity().apply {
                    name = "Bench Press"
                    normalizedName = "bench_press"
                    primaryMuscle = "chest"
                    equipment = "barbell"
                    category = "strength"
                    notes = "Permanent exercise-library coaching note"
                    createdAt = 11L
                    updatedAt = 12L
                }
                exerciseBox.put(libraryExercise)

                val targetPlan = repository.createPlan(
                    PlanInput(
                        name = "Target",
                        goal = "Strength",
                        description = "Clear this plan description",
                    ),
                )
                val firstDay = repository.createPlanDay(targetPlan.uid, PlanDayInput(name = "Push A"))
                val secondDay = repository.createPlanDay(targetPlan.uid, PlanDayInput(name = "Push B"))
                val firstTargetExercise = repository.addExerciseToPlanDay(
                    firstDay.uid,
                    PlanExerciseInput(exerciseId = libraryExercise.uid, sets = 3, reps = "5", notes = "First plan note"),
                )
                val secondTargetExercise = repository.addExerciseToPlanDay(
                    secondDay.uid,
                    PlanExerciseInput(exerciseId = libraryExercise.uid, sets = 4, reps = "8", notes = "Second plan note"),
                )

                val otherPlan = repository.createPlan(
                    PlanInput(
                        name = "Other",
                        goal = "Hypertrophy",
                        description = "Keep this plan description",
                    ),
                )
                val otherDay = repository.createPlanDay(otherPlan.uid, PlanDayInput(name = "Other day"))
                val otherPlanExercise = repository.addExerciseToPlanDay(
                    otherDay.uid,
                    PlanExerciseInput(exerciseId = libraryExercise.uid, sets = 2, reps = "12", notes = "Keep this plan note"),
                )

                targetPlan.updatedAt = 1L
                planBox.put(targetPlan)
                listOf(firstTargetExercise, secondTargetExercise).forEach { it.updatedAt = 1L }
                planExerciseBox.put(listOf(firstTargetExercise, secondTargetExercise))
                val otherPlanUpdatedAt = otherPlan.updatedAt
                val otherExerciseUpdatedAt = otherPlanExercise.updatedAt

                repository.clearPlanNotes(targetPlan.uid)

                val plansById = planBox.all.associateBy { it.uid }
                val exercisesById = planExerciseBox.all.associateBy { it.uid }
                val clearedPlan = plansById.getValue(targetPlan.uid)
                assertEquals("", clearedPlan.description)
                assertTrue(clearedPlan.updatedAt > 1L)
                listOf(firstTargetExercise.uid, secondTargetExercise.uid).forEach { uid ->
                    val row = exercisesById.getValue(uid)
                    assertEquals("", row.notes)
                    assertTrue(row.updatedAt > 1L)
                }

                assertEquals("Keep this plan description", plansById.getValue(otherPlan.uid).description)
                assertEquals(otherPlanUpdatedAt, plansById.getValue(otherPlan.uid).updatedAt)
                assertEquals("Keep this plan note", exercisesById.getValue(otherPlanExercise.uid).notes)
                assertEquals(otherExerciseUpdatedAt, exercisesById.getValue(otherPlanExercise.uid).updatedAt)

                val unchangedLibraryExercise = exerciseBox.all.single()
                assertEquals("Permanent exercise-library coaching note", unchangedLibraryExercise.notes)
                assertEquals(12L, unchangedLibraryExercise.updatedAt)
            }
        }
}
