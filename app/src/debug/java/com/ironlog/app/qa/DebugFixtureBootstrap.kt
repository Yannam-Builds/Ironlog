package com.ironlog.app.qa

import android.content.Context
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.repository.ImportExportRepository

/** Imports the generated, sanitized QA backup once, and only into a pristine debug database. */
object DebugFixtureBootstrap {
    private const val PREFS = "ironlog_qa_bootstrap"
    private const val COMPLETE = "fixture_import_complete_v1"
    private const val ASSET = "ironlog_qa_fixture.json"

    suspend fun importIfPristine(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(COMPLETE, false)) return
        if (ASSET !in context.assets.list("").orEmpty()) return

        val pristine = ObjectBox.store.boxFor(WorkoutEntity::class.java).isEmpty &&
            ObjectBox.store.boxFor(PlanEntity::class.java).isEmpty &&
            ObjectBox.store.boxFor(ExerciseEntity::class.java).isEmpty
        if (!pristine) return

        val payload = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val repository = ImportExportRepository()
        val impact = repository.previewRestore(payload, mode = "replace")
        val result = repository.runConfirmedImport(
            text = payload,
            mode = "replace",
            expectedDatabaseFingerprint = impact.databaseFingerprint,
        )
        check(result.valid) { result.reason ?: "QA fixture import failed" }
        prefs.edit().putBoolean(COMPLETE, true).commit()
    }
}
