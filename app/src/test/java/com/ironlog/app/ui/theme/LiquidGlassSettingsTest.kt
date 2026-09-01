package com.ironlog.app.ui.theme

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassSettingsTest {
    private class MemoryStorage(var saved: Any? = null) : LiquidGlassStorage {
        var writes = 0
        var fail = false
        var duringWrite: (() -> Unit)? = null
        override fun read(): Any? = saved
        override fun write(enabled: Boolean) {
            writes++
            duringWrite?.invoke()
            if (fail) throw IOException("Full storage")
            saved = enabled
        }
    }

    @Test fun `glass defaults on for missing and invalid preferences`() {
        for (raw in listOf(null, "false", 0, Float.NaN, emptySet<String>())) {
            assertTrue(LiquidGlassStore(MemoryStorage(raw)).enabled.value)
        }
    }

    @Test fun `both saved boolean values are restored`() {
        assertFalse(LiquidGlassStore(MemoryStorage(false)).enabled.value)
        assertTrue(LiquidGlassStore(MemoryStorage(true)).enabled.value)
    }

    @Test fun `off and on persist across store reloads`() {
        val storage = MemoryStorage()
        val store = LiquidGlassStore(storage)
        store.update(false)
        assertFalse(store.enabled.value)
        assertFalse(LiquidGlassStore(storage).enabled.value)
        store.update(true)
        assertTrue(store.enabled.value)
        assertTrue(LiquidGlassStore(storage).enabled.value)
    }

    @Test fun `failed writes preserve previous state and allow retry in both directions`() {
        for (initial in listOf(true, false)) {
            val storage = MemoryStorage(initial).apply { fail = true }
            val store = LiquidGlassStore(storage)
            assertThrows(IOException::class.java) { store.update(!initial) }
            assertEquals(initial, store.enabled.value)
            assertEquals(initial, storage.saved)
            storage.fail = false
            store.update(!initial)
            assertEquals(!initial, store.enabled.value)
            assertEquals(!initial, LiquidGlassStore(storage).enabled.value)
        }
    }

    @Test fun `new state is not published before its durable write`() {
        val storage = MemoryStorage(true)
        val store = LiquidGlassStore(storage)
        storage.duringWrite = { assertTrue(store.enabled.value) }
        store.update(false)
        assertEquals(1, storage.writes)
        assertFalse(store.enabled.value)
    }

    @Test fun `repeating current state avoids redundant writes`() {
        val storage = MemoryStorage()
        val store = LiquidGlassStore(storage)
        store.update(true)
        assertEquals(0, storage.writes)
        store.update(false)
        store.update(false)
        assertEquals(1, storage.writes)
    }

    @Test fun `successive toggles preserve the final accepted value`() {
        val storage = MemoryStorage()
        val store = LiquidGlassStore(storage)
        listOf(false, true, false).forEach(store::update)
        assertEquals(3, storage.writes)
        assertFalse(store.enabled.value)
        assertFalse(LiquidGlassStore(storage).enabled.value)
    }

    @Test fun `glass and card shine state stay independent`() {
        val glass = LiquidGlassStore(MemoryStorage())
        val shine = CardShineStore(object : CardShineStorage {
            var saved: Boolean? = null
            override fun read(): Any? = saved
            override fun write(enabled: Boolean) { saved = enabled }
        })
        glass.update(false)
        assertTrue(shine.enabled.value)
        shine.update(false)
        glass.update(true)
        assertTrue(glass.enabled.value)
        assertFalse(shine.enabled.value)
    }

    @Test fun `appearance and root share the persisted glass setting`() {
        val settings = File("src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt").readText()
        val root = File("src/main/java/com/ironlog/app/ui/IronLogApp.kt").readText()
        assertTrue("Appearance must expose the glass preference", settings.contains("LiquidGlassSettingsCard()"))
        assertTrue("Root must supply the saved preference", root.contains("LocalLiquidGlassEnabled provides liquidGlassEnabled"))
    }
}
