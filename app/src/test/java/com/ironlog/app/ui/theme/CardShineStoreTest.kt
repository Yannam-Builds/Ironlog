package com.ironlog.app.ui.theme

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class CardShineStoreTest {
    private class MemoryStorage(var saved: Any? = null) : CardShineStorage {
        var fail = false
        var duringWrite: (() -> Unit)? = null
        override fun read(): Any? = saved
        override fun write(enabled: Boolean) {
            duringWrite?.invoke()
            if (fail) throw IOException("Full storage")
            saved = enabled
        }
    }

    @Test fun `shine defaults on and ignores malformed preferences`() {
        for (raw in listOf(null, "false", 0, Float.NaN)) {
            assertTrue(CardShineStore(MemoryStorage(raw)).enabled.value)
        }
    }

    @Test fun `off and on survive a new store`() {
        val storage = MemoryStorage()
        val store = CardShineStore(storage)
        store.update(false)
        assertFalse(store.enabled.value)
        assertFalse(CardShineStore(storage).enabled.value)
        store.update(true)
        assertTrue(CardShineStore(storage).enabled.value)
    }

    @Test fun `failed save preserves displayed and persisted state and is retryable`() {
        val storage = MemoryStorage(true).apply { fail = true }
        val store = CardShineStore(storage)
        assertThrows(IOException::class.java) { store.update(false) }
        assertTrue(store.enabled.value)
        assertEquals(true, storage.saved)
        storage.fail = false
        store.update(false)
        assertFalse(store.enabled.value)
    }

    @Test fun `setting is published only after persistence`() {
        val storage = MemoryStorage(true)
        val store = CardShineStore(storage)
        storage.duringWrite = { assertTrue(store.enabled.value) }
        store.update(false)
        assertFalse(store.enabled.value)
    }

    @Test fun `successive toggles retain the last accepted value`() {
        val storage = MemoryStorage()
        val store = CardShineStore(storage)
        listOf(false, true, false).forEach(store::update)
        assertFalse(store.enabled.value)
        assertFalse(CardShineStore(storage).enabled.value)
    }
}
