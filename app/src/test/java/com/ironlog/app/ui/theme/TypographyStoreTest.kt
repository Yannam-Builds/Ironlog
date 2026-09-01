package com.ironlog.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class TypographyStoreTest {
    private class MemoryStorage(var values: Pair<Any?, Any?> = null to null) : TypographyStorage {
        override fun read() = values
        override fun write(selection: TypographySelection) { values = selection.fontId to selection.preset.id }
    }

    @Test fun `saved selection is available before first screen`() {
        val store = TypographyStore(MemoryStorage("inter" to "light"))
        assertEquals(TypographySelection("inter", TypographyPreset.LIGHT), store.selection.value)
    }

    @Test fun `update is immediately observable and survives a fresh store`() {
        val storage = MemoryStorage()
        val store = TypographyStore(storage)
        val wanted = TypographySelection("quicksand", TypographyPreset.BOLD)
        store.update(wanted)
        assertEquals(wanted, store.selection.value)
        assertEquals(wanted, TypographyStore(storage).selection.value)
    }

    @Test fun `invalid updates normalize before saving`() {
        val storage = MemoryStorage()
        TypographyStore(storage).update(TypographySelection("bad", TypographyPreset.LIGHT))
        assertEquals("lexend" to "light", storage.values)
    }

    @Test fun `reset persists the original typography`() {
        val storage = MemoryStorage("sora" to "bold")
        TypographyStore(storage).update(TypographySelection())
        assertEquals(TypographySelection(), TypographyStore(storage).selection.value)
    }
}
