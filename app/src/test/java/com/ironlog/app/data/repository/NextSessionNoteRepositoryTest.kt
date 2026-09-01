package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NextSessionNoteRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun notesBelongToStableExerciseIdAndClearWithoutChangingWorkoutNotes() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            try {
                store.boxFor(ExerciseEntity::class.java).put(listOf("one", "two").map { ExerciseEntity().apply { uid = it; name = "Same name" } })
                val repo = NextSessionNoteRepository(store)
                repo.save("one", "Pause next session")
                assertEquals("Pause next session", repo.read("one"))
                assertEquals("", repo.read("two"))
                repo.save("one", "")
                assertEquals("", repo.read("one"))
                assertEquals(0L, store.boxFor(WorkoutExerciseEntity::class.java).count())
                assertTrue(runCatching { repo.save("missing", "No orphan note") }.isFailure)
            } finally { store.closeThreadResources() }
        }
    }
}
