package com.ironlog.app.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LiquidGlassStorage {
    fun read(): Any?
    fun write(enabled: Boolean)
}

/** Navigation material only; independent of card shine and other appearance preferences. */
class LiquidGlassStore(private val storage: LiquidGlassStorage) {
    private val mutableEnabled = MutableStateFlow(storage.read() as? Boolean ?: true)
    val enabled = mutableEnabled.asStateFlow()

    /** Called on IO through the application-lifetime appearance queue. */
    @Synchronized
    fun update(enabled: Boolean) {
        if (enabled == mutableEnabled.value) return
        storage.write(enabled)
        mutableEnabled.value = enabled
    }

    /** Publishes a value already committed atomically by the full-backup appearance store. */
    @Synchronized
    internal fun publishRestored(enabled: Boolean) {
        mutableEnabled.value = enabled
    }
}
