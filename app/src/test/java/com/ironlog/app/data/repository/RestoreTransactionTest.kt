package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreTransactionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val payload = """{"type":"ironlog_watermelon_export","version":1,"data":{"workouts":[{"id":"old","name":"Old","status":"completed","started_at":1}]}}"""

    @Test fun `imported photo paths are unavailable but merging preserves an existing local match`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("db")).build()
        try {
            val photos = store.boxFor(com.ironlog.app.data.objectbox.ProgressPhotoEntity::class.java)
            photos.put(com.ironlog.app.data.objectbox.ProgressPhotoEntity().apply { uid = "existing"; fileUri = "file:///owned/local.jpg" })
            val repository = ImportExportRepository(store, temporary.newFolder("recovery"))
            val imported = """{"type":"ironlog_watermelon_export","version":1,"data":{"progress_photos":[{"id":"existing","file_uri":"file:///untrusted/other.jpg"},{"id":"new","file_uri":"file:///untrusted/backup.json"}]}}"""
            repository.runConfirmedImport(imported)
            assertEquals("file:///owned/local.jpg", photos.all.first { it.uid == "existing" }.fileUri)
            assertEquals("", photos.all.first { it.uid == "new" }.fileUri)
        } finally { store.close() }
    }

    @Test fun `private replacement checkpoint restores its independent photo copy`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("db")).build()
        try {
            val files = temporary.newFolder("files")
            val photoDirectory = File(files, "progress_photos").apply { mkdirs() }
            val original = File(photoDirectory, "photo_123.jpg").apply { writeText("synthetic image bytes") }
            val recovery = File(files, "restore-recovery")
            val local = com.ironlog.app.data.photos.LocalRestoreSnapshotStore(files, recovery, "synthetic.fileprovider")
            val photos = store.boxFor(com.ironlog.app.data.objectbox.ProgressPhotoEntity::class.java)
            photos.put(com.ironlog.app.data.objectbox.ProgressPhotoEntity().apply { uid = "photo"; fileUri = original.toURI().toString() })
            val repository = ImportExportRepository(store, recovery, local)
            val impact = repository.previewRestore(payload, "replace")
            val replaced = repository.runConfirmedImport(payload, "replace", impact.databaseFingerprint)
            assertEquals(0L, photos.count())
            assertTrue(original.delete())
            val checkpoint = File(replaced.recoverySnapshot!!)
            val restoreText = checkpoint.readText()
            val restoreImpact = repository.previewRestore(restoreText, "replace")
            repository.runConfirmedImport(restoreText, "replace", restoreImpact.databaseFingerprint, checkpoint)
            assertEquals("synthetic image bytes", File(java.net.URI(photos.all.single().fileUri)).readText())
        } finally { store.close() }
    }

    @Test fun `merge keeps newer workout and replace checkpoints it before removal`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("db")).build()
        try {
            val box = store.boxFor(WorkoutEntity::class.java)
            box.put(WorkoutEntity().apply { uid = "new"; name = "New"; status = "completed" })
            val repository = ImportExportRepository(store, temporary.newFolder("recovery"))
            repository.runConfirmedImport(payload)
            assertEquals(setOf("old", "new"), box.all.map { it.uid }.toSet())
            val impact = repository.previewRestore(payload, "replace")
            assertEquals(1, impact.removed["workouts"])
            val result = repository.runConfirmedImport(payload, "replace", impact.databaseFingerprint)
            assertEquals(listOf("old"), box.all.map { it.uid })
            assertTrue(File(result.recoverySnapshot!!).readText().contains("\"new\""))
        } finally { store.close() }
    }

    @Test fun `active workout or stale confirmation cannot restore`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("db")).build()
        try {
            val box = store.boxFor(WorkoutEntity::class.java)
            val repository = ImportExportRepository(store, temporary.newFolder("recovery"))
            val impact = repository.previewRestore(payload, "replace")
            box.put(WorkoutEntity().apply { uid = "new"; status = "completed" })
            assertTrue(runCatching { repository.runConfirmedImport(payload, "replace", impact.databaseFingerprint) }.isFailure)
            box.put(WorkoutEntity().apply { uid = "active"; status = "active" })
            assertTrue(runCatching { repository.runConfirmedImport(payload) }.isFailure)
            assertEquals(setOf("new", "active"), box.all.map { it.uid }.toSet())
        } finally { store.close() }
    }

    @Test fun `partial merge resolves parents from the same current database snapshot`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("db")).build()
        try {
            val exercises = store.boxFor(com.ironlog.app.data.objectbox.ExerciseEntity::class.java)
            val workouts = store.boxFor(WorkoutEntity::class.java)
            val workoutExercises = store.boxFor(com.ironlog.app.data.objectbox.WorkoutExerciseEntity::class.java)
            val exercise = com.ironlog.app.data.objectbox.ExerciseEntity().apply { uid = "ex"; name = "Bench" }
            exercises.put(exercise)
            val workout = WorkoutEntity().apply { uid = "w"; status = "completed" }
            workouts.put(workout)
            workoutExercises.put(com.ironlog.app.data.objectbox.WorkoutExerciseEntity().apply {
                uid = "we"; exerciseUid = "ex"; workoutUid = "w"; this.exercise.target = exercise; this.workout.target = workout
            })
            val partial = """{"type":"ironlog_watermelon_export","version":1,"data":{"workout_sets":[{"id":"new-set","workout_exercise_id":"we","weight":40,"reps":8}]}}"""
            val repository = ImportExportRepository(store, temporary.newFolder("recovery"))
            val impact = repository.previewRestore(partial, "merge")
            assertEquals(1, impact.preview.sets)
            assertEquals(1, repository.runConfirmedImport(partial, "merge", impact.databaseFingerprint).sets)
            assertEquals("new-set", store.boxFor(com.ironlog.app.data.objectbox.WorkoutSetEntity::class.java).all.single().uid)
            assertFalse(repository.previewRestore(partial, "replace").preview.replacementSafe)
        } finally { store.close() }
    }

    @Test fun `unwritable checkpoint or malformed replacement leaves database intact`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("db")).build()
        try {
            val box = store.boxFor(WorkoutEntity::class.java)
            box.put(WorkoutEntity().apply { uid = "new"; status = "completed" })
            val repository = ImportExportRepository(store, temporary.newFile("not-a-directory"))
            val impact = repository.previewRestore(payload, "replace")
            assertTrue(runCatching { repository.runConfirmedImport(payload, "replace", impact.databaseFingerprint) }.isFailure)
            assertTrue(runCatching { repository.runConfirmedImport(payload.replace("\"id\":\"old\"", "\"id\":\"\""), "replace", impact.databaseFingerprint) }.isFailure)
            assertEquals(listOf("new"), box.all.map { it.uid })
        } finally { store.close() }
    }
}
