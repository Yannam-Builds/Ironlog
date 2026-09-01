package com.ironlog.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    @Test fun `native version must be the exact supported numeric value`() {
        listOf("1.5", "\"1\"", "true").forEach { version ->
            val preview = ImportExportRepository().previewImportPayload("""{"type":"ironlog_watermelon_export","version":$version,"data":{"workouts":[{"id":"w"}]}}""")
            assertFalse(preview.valid)
        }
    }
    private val repository = ImportExportRepository()
    private fun backup(data: String) = """{"type":"ironlog_watermelon_export","version":1,"data":$data}"""

    @Test fun `blank workout id cannot pass restore preview`() {
        assertFalse(repository.previewImportPayload(backup("""{"workouts":[{}]}""")).valid)
    }

    @Test fun `wrong section and row types cannot pass restore preview`() {
        assertFalse(repository.previewImportPayload(backup("""{"workouts":{}}""")).valid)
        assertFalse(repository.previewImportPayload(backup("""{"workouts":[42]}""")).valid)
    }

    @Test fun `duplicate stable IDs are rejected instead of inflated counts`() {
        assertFalse(repository.previewImportPayload(backup("""{"workouts":[{"id":"a"},{"id":"a"}]}""")).valid)
    }

    @Test fun `invalid numeric values cannot be silently converted to zero`() {
        assertFalse(repository.previewImportPayload(backup("""{"workouts":[{"id":"a","duration_seconds":"wrong"}]}""")).valid)
    }

    @Test fun `orphan rows are excluded from promised counts`() {
        val preview = repository.previewImportPayload(backup("""{"workouts":[{"id":"a"}],"workout_exercises":[{"id":"e","workout_id":"missing","exercise_id":"missing"}],"workout_sets":[{"id":"s","workout_exercise_id":"e","reps":8}]}"""))
        assertEquals(0, preview.counts.workoutExercises)
        assertEquals(0, preview.sets)
        assertTrue(preview.warnings.isNotEmpty())
    }

    @Test fun `empty envelope is not an intentional full empty backup`() {
        assertFalse(repository.previewImportPayload(backup("{}")).valid)
    }

    @Test fun `explicit empty full backup can be previewed`() {
        val data = FULL_BACKUP_DATA_SECTIONS.joinToString(",", "{", "}") { "\"$it\":[]" }
        assertTrue(repository.previewImportPayload(backup(data)).valid)
    }

    @Test fun `valid related rows preserve exact import counts`() {
        val data = """{"exercises":[{"id":"e","name":"Bench"}],"workouts":[{"id":"w","started_at":1,"completed_at":2}],"workout_exercises":[{"id":"we","exercise_id":"e","workout_id":"w"}],"workout_sets":[{"id":"s","workout_exercise_id":"we","weight":65,"reps":8}]}"""
        val preview = repository.previewImportPayload(backup(data))
        assertTrue(preview.errors.toString(), preview.valid)
        assertEquals(1, preview.workouts)
        assertEquals(1, preview.counts.workoutExercises)
        assertEquals(1, preview.sets)
    }

    @Test fun `malformed legacy arrays cannot normalize into an empty replacement`() {
        listOf("""{"history":{}}""", """{"history":[42]}""", """{"history":[{"exercises":{}}]}""").forEach {
            assertFalse(it, repository.previewImportPayload(it).valid)
        }
    }

    @Test fun `explicit unknown version cannot fall back to legacy detection`() {
        assertFalse(repository.previewImportPayload("""{"type":"ironlog_watermelon_export","version":2,"history":[]}""").valid)
        assertFalse(repository.previewImportPayload("""{"schema":"IRONLOG_SQLITE_EXPORT_V999","payload":{"history":[]}}""").valid)
    }

    @Test fun `integer overflow fractional index and unknown status are rejected`() {
        listOf("\"duration_seconds\":2147483648", "\"duration_seconds\":1.5", "\"status\":\"broken\"").forEach {
            assertFalse(it, repository.previewImportPayload(backup("""{"workouts":[{"id":"a",$it}]}""")).valid)
        }
    }

    @Test fun `legacy child ids are scoped to their parent`() {
        val workout = """{"date":"2026-08-01","exercises":[{"name":"Bench","sets":[{"weight":40,"reps":8}]}]}"""
        val preview = repository.previewImportPayload("""{"history":[$workout,$workout]}""")
        assertTrue(preview.errors.toString(), preview.valid)
        assertEquals(2, preview.counts.workoutExercises)
        assertEquals(2, preview.sets)
    }
}
