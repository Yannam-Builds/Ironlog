package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class AppearanceBackupTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val compact = AppearanceBackupSnapshot(
        fontId = "inter",
        typographyPresetId = "light",
        globalSpacing = 0.9f,
        cardSpacing = 0.85f,
        paddingSpacing = 0.95f,
        contentSpacing = 1.05f,
        cardShineEnabled = false,
        liquidGlassEnabled = true,
    )

    @Test
    fun `appearance codec is versioned and contains only portable presentation values`() {
        val encoded = AppearanceBackupCodec.encode(compact)

        assertEquals(1, encoded.getInt("version"))
        assertEquals(setOf("version", "typography", "spacing", "effects"), encoded.keys().asSequence().toSet())
        assertEquals(setOf("font_id", "preset_id"), encoded.getJSONObject("typography").keys().asSequence().toSet())
        assertEquals(setOf("global", "cards", "padding", "content"), encoded.getJSONObject("spacing").keys().asSequence().toSet())
        assertEquals(setOf("card_shine", "liquid_glass_navigation"), encoded.getJSONObject("effects").keys().asSequence().toSet())
        assertFalse(encoded.toString().contains("api_key", ignoreCase = true))
        assertEquals(compact, AppearanceBackupCodec.decode(encoded))
    }

    @Test
    fun `unknown appearance versions are ignored for forward compatible restores`() {
        val unsupported = AppearanceBackupCodec.encode(compact).put("version", 99)
        assertNull(AppearanceBackupCodec.decode(unsupported))
    }

    @Test
    fun `full export includes appearance and restore applies it`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("roundtrip-db")).build()
        try {
            val source = MemoryAppearanceStore(compact)
            val repository = ImportExportRepository(store, temporary.newFolder("roundtrip-recovery"), appearanceBackupStore = source)
            val exported = repository.exportDatabase()
            assertEquals(compact, AppearanceBackupCodec.decode(exported.getJSONObject("appearance")))

            source.value = AppearanceBackupSnapshot()
            repository.runConfirmedImport(exported.toString())
            assertEquals(compact, source.value)
        } finally {
            store.close()
        }
    }

    @Test
    fun `legacy backup without appearance leaves current appearance unchanged`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("legacy-db")).build()
        try {
            val appearance = MemoryAppearanceStore(compact)
            val repository = ImportExportRepository(store, temporary.newFolder("legacy-recovery"), appearanceBackupStore = appearance)
            val legacyPayload = repository.exportDatabase().apply { remove("appearance") }
            repository.runConfirmedImport(legacyPayload.toString())
            assertEquals(compact, appearance.value)
        } finally {
            store.close()
        }
    }

    @Test
    fun `appearance write failure rolls back appearance and database import`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("failure-db")).build()
        try {
            val original = AppearanceBackupSnapshot(fontId = "lexend", typographyPresetId = "original")
            val appearance = MemoryAppearanceStore(original)
            val repository = ImportExportRepository(store, temporary.newFolder("failure-recovery"), appearanceBackupStore = appearance)
            val payload = """{
                "type":"ironlog_watermelon_export",
                "version":1,
                "appearance":${AppearanceBackupCodec.encode(compact)},
                "data":{"workouts":[{"id":"incoming","name":"Incoming","status":"completed","started_at":1}]}
            }"""
            appearance.failNextWrite = true

            val failure = runCatching { repository.runConfirmedImport(payload) }.exceptionOrNull()

            assertNotNull(failure)
            assertTrue(failure is IOException || failure?.cause is IOException)
            assertEquals(original, appearance.value)
            assertTrue(store.boxFor(WorkoutEntity::class.java).all.isEmpty())
        } finally {
            store.close()
        }
    }

    private class MemoryAppearanceStore(initial: AppearanceBackupSnapshot) : AppearanceBackupStore {
        var value = initial
        var failNextWrite = false

        override fun snapshot(): AppearanceBackupSnapshot = value

        override fun replace(snapshot: AppearanceBackupSnapshot) {
            value = snapshot
            if (failNextWrite) {
                failNextWrite = false
                throw IOException("synthetic appearance write failure")
            }
        }
    }
}
