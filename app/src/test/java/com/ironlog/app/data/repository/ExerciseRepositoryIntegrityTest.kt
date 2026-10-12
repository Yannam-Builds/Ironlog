package com.ironlog.app.data.repository

import com.ironlog.app.data.model.CreateExerciseInput
import com.ironlog.app.data.model.UpdateExerciseInput
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExerciseRepositoryIntegrityTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun input(name: String) = CreateExerciseInput(name = name, primaryMuscle = "Chest", equipment = "Cable", category = "strength")

    @Test fun `concurrent normalized creates persist exactly one identity`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder()).build()
        try {
            val repo = ExerciseRepository(boxStore = store)
            val results = coroutineScope {
                (0 until 32).map { index -> async { runCatching { repo.createCustomExercise(input(if (index % 2 == 0) "  Cable   Press " else "Cable Press")) } } }.awaitAll()
            }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1L, store.boxFor(ExerciseEntity::class.java).count())
        } finally { store.close() }
    }

    @Test fun `blank and colliding renames fail without modifying stored row`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder()).build()
        try {
            val repo = ExerciseRepository(boxStore = store)
            val first = repo.createCustomExercise(input("Cable Press"))
            repo.createCustomExercise(input("Cable Fly"))
            for (name in listOf("", "   ", " cable   fly ")) {
                assertTrue(runCatching { repo.updateExercise(first.uid, UpdateExerciseInput(name = name, notes = "should not save")) }.isFailure)
                val unchanged = repo.getExerciseById(first.uid)
                assertEquals("Cable Press", unchanged.name)
                assertEquals("", unchanged.notes)
                assertEquals(first.updatedAt, unchanged.updatedAt)
            }
            repo.updateExercise(first.uid, UpdateExerciseInput(name = " CABLE   PRESS ", notes = "valid edit"))
            assertEquals("valid edit", repo.getExerciseById(first.uid).notes)
        } finally { store.close() }
    }

    @Test fun `catalog preserves aliases source tags and all primary muscle relationships`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder()).build()
        try {
            val row = ExerciseEntity().apply {
                uid = "multi"; name = "Compound Press"; primaryMuscle = "Chest"; equipment = "Cable"
                aliasesJson = "[\"Cable Compound\",\"  Alternate press  \"]"; sourceTagsJson = "[\"seed\",\"compound\"]"
                secondaryMusclesJson = "[\"Triceps\"]"
            }
            store.boxFor(ExerciseEntity::class.java).put(row)
            store.boxFor(ExerciseMuscleEntity::class.java).put(listOf("Chest", "Shoulders").map { name -> ExerciseMuscleEntity().apply { exercise.target = row; exerciseUid = row.uid; muscle = name; role = "primary"; contributionFraction = 0.5 } })
            val mapped = ExerciseRepository(boxStore = store).getExercisesSnapshot().single()
            assertEquals(listOf("Chest", "Shoulders"), mapped.primaryMuscles)
            assertEquals(listOf("Cable Compound", "Alternate press"), mapped.aliases)
            assertEquals(listOf("seed", "compound"), mapped.sourceTags)
            assertEquals(listOf("Triceps"), mapped.secondaryMuscles)
            val exported = ImportExportRepository(store, temporary.newFolder()).exportDatabase().getJSONObject("data")
            assertEquals(2, exported.getJSONArray("exercise_muscles").length())
            assertEquals(2, org.json.JSONArray(exported.getJSONArray("exercises").getJSONObject(0).getString("aliases_json")).length())
        } finally { store.close() }
    }
}
