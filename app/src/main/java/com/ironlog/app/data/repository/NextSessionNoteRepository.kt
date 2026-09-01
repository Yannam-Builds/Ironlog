package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Opt-in reminders, separate from plan instructions and immutable completed-session notes. */
internal class NextSessionNoteRepository(private val store: BoxStore = ObjectBox.store) {
    private val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
    private fun key(id: String): String { require(id.isNotBlank()); return "exercise_next_note:$id" }
    fun observe(id: String) = settings.observeStrings(setOf(key(id))).map { it[key(id)].orEmpty() }
    suspend fun read(id: String): String = settings.getString(key(id)).orEmpty()
    suspend fun save(id: String, note: String) = withContext(Dispatchers.IO) {
        require(note.length <= 4_000) { "Keep the reminder under 4,000 characters." }
        store.runInTx {
            require(store.boxFor(ExerciseEntity::class.java).query(ExerciseEntity_.uid.equal(id)).build().use { it.count() > 0L }) {
                "Save this exercise to the library before attaching a reminder."
            }
            settings.setStringBlocking(key(id), note.trim())
        }
    }
}
