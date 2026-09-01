package com.ironlog.app.data.repository

import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.PlanExerciseDefinition
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.PlanDayEntity
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.PlanExerciseEntity
import com.ironlog.app.data.plan.PlanJsonCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlanImportIntegrityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `unknown named exercise becomes durable custom exercise and plan metadata survives`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("custom-import")).build().use { store ->
                val repository = PlanRepository(store)
                val result = repository.importPlansAtomically(
                    listOf(
                        FullPlanObject(
                            name = "Imported plan",
                            goal = "Hypertrophy",
                            description = "Plan-level coaching context",
                            days = listOf(
                                FullPlanDay(
                                    name = "Pull",
                                    exercises = listOf(
                                        PlanExerciseInput(
                                            exerciseId = "remote-custom-id",
                                            name = "Custom cable pullover",
                                            sets = 4,
                                            reps = "8-12",
                                            restSeconds = 90,
                                            supersetGroup = "A",
                                            notes = "Keep ribs down",
                                            definition = PlanExerciseDefinition(
                                                isCustom = true,
                                                primaryMuscle = "lats",
                                                equipment = "cable",
                                                category = "strength",
                                                trackingType = "weight_reps",
                                                movementPattern = "pull",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

                assertEquals(1, result.imported)
                assertEquals(1, result.importedExercises)
                assertEquals(1, result.createdCustomExercises)
                assertEquals(0, result.unresolved)
                assertEquals(0, result.skipped)

                val custom = store.boxFor(ExerciseEntity::class.java).all.single()
                assertEquals("remote-custom-id", custom.uid)
                assertEquals("Custom cable pullover", custom.name)
                assertEquals("lats", custom.primaryMuscle)
                assertEquals("cable", custom.equipment)
                assertEquals("weight_reps", custom.trackingType)
                assertTrue(custom.isCustom)

                val plan = store.boxFor(PlanEntity::class.java).all.single()
                assertEquals("Plan-level coaching context", plan.description)
                val planExercise = store.boxFor(PlanExerciseEntity::class.java).all.single()
                assertEquals(custom.uid, planExercise.exerciseUid)
                assertEquals("A", planExercise.supersetGroup)
                assertEquals("Keep ribs down", planExercise.notes)
            }
        }

    @Test
    fun `legacy unknown exercise name is imported as a custom exercise instead of disappearing`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("legacy-custom")).build().use { store ->
                val decoded = PlanJsonCodec.decode(
                    """[{"name":"Legacy","days":[{"name":"Day","exercises":[{"name":"Garage atlas lever 9417","sets":3,"reps":"10"}]}]}]""",
                )
                val result = PlanRepository(store).importPlansAtomically(
                    decoded,
                )

                assertEquals(1, result.importedExercises)
                assertEquals(1, result.createdCustomExercises)
                assertEquals(0, result.unresolved)
                assertEquals("Garage atlas lever 9417", store.boxFor(ExerciseEntity::class.java).all.single().name)
                assertEquals(1, store.boxFor(PlanExerciseEntity::class.java).count())
            }
        }

    @Test
    fun `import honors explicit exercise order with stable ties and compact stored indices`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("explicit-order")).build().use { store ->
                val repository = PlanRepository(store)
                val result = repository.importPlansAtomically(
                    listOf(
                        FullPlanObject(
                            name = "Ordered",
                            days = listOf(
                                FullPlanDay(
                                    name = "Day",
                                    exercises = listOf(
                                        PlanExerciseInput(name = "Third", orderIndex = 2),
                                        PlanExerciseInput(name = "First tie", orderIndex = 0),
                                        PlanExerciseInput(name = "Second tie", orderIndex = 0),
                                        PlanExerciseInput(name = "Array fallback"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

                assertEquals(1, result.imported)
                val namesByExerciseId = store.boxFor(ExerciseEntity::class.java).all.associate { it.uid to it.name }
                val persisted = store.boxFor(PlanExerciseEntity::class.java).all.sortedBy { it.orderIndex }
                assertEquals(listOf(0, 1, 2, 3), persisted.map { it.orderIndex })
                assertEquals(
                    listOf("First tie", "Second tie", "Third", "Array fallback"),
                    persisted.map { namesByExerciseId.getValue(it.exerciseUid) },
                )
            }
        }

    @Test
    fun `whole batch validates before writes so a later invalid plan cannot leave a partial import`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("atomic-validation")).build().use { store ->
                val repository = PlanRepository(store)

                val failure = runCatching {
                    repository.importPlansAtomically(
                        listOf(
                            FullPlanObject(name = "Would otherwise import"),
                            FullPlanObject(name = "  "),
                        ),
                    )
                }

                assertTrue(failure.isFailure)
                assertEquals(0, store.boxFor(PlanEntity::class.java).count())
                assertEquals(0, store.boxFor(PlanDayEntity::class.java).count())
                assertEquals(0, store.boxFor(PlanExerciseEntity::class.java).count())
            }
        }

    @Test
    fun `unknown id without a name is reported unresolved while malformed exercises are skipped`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("counts")).build().use { store ->
                val result = PlanRepository(store).importPlansAtomically(
                    plans = listOf(
                        FullPlanObject(
                            name = "Counts",
                            days = listOf(
                                FullPlanDay(
                                    name = "Day",
                                    exercises = listOf(
                                        PlanExerciseInput(exerciseId = "missing-remote-id"),
                                        PlanExerciseInput(),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    initiallySkipped = 2,
                )

                assertEquals(1, result.imported)
                assertEquals(0, result.importedExercises)
                assertEquals(1, result.unresolved)
                assertEquals(3, result.skipped)
                assertEquals(listOf("missing-remote-id"), result.unresolvedExercises)
                assertFalse(store.boxFor(PlanExerciseEntity::class.java).all.any())
            }
        }
}
