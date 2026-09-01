package com.ironlog.app.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface TypographyStorage {
    fun read(): Pair<Any?, Any?>
    fun write(selection: TypographySelection)
}

class TypographyStore(private val storage: TypographyStorage) {
    private val mutableSelection = MutableStateFlow(storage.read().let { TypographySelection.fromStored(it.first, it.second) })
    val selection = mutableSelection.asStateFlow()
    @Synchronized
    fun update(change: (TypographySelection) -> TypographySelection) {
        update(change(mutableSelection.value))
    }
    @Synchronized
    fun update(selection: TypographySelection) {
        val normalized = TypographySelection.fromStored(selection.fontId, selection.preset.id)
        storage.write(normalized)
        mutableSelection.value = normalized
    }

    /** Publishes a value already committed atomically by the full-backup appearance store. */
    @Synchronized
    internal fun publishRestored(selection: TypographySelection) {
        mutableSelection.value = TypographySelection.fromStored(selection.fontId, selection.preset.id)
    }
}
