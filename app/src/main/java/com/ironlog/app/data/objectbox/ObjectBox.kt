package com.ironlog.app.data.objectbox

import android.content.Context
import io.objectbox.BoxStore

/**
 * ObjectBox store — singleton gateway to the on-device database.
 * Initialised once in IronLogApplication and shared across all repositories.
 */
object ObjectBox {
    lateinit var store: BoxStore
        private set

    @Synchronized
    fun init(context: Context) {
        if (::store.isInitialized) return
        val appCtx = context.applicationContext
        // Never turn an initialization failure into silent data loss. Schema migrations must
        // preserve the stable IDs in objectbox-models/default.json; disk/corruption failures
        // are surfaced to the caller so recovery can be explicit and backup-aware.
        store = MyObjectBox.builder()
            .androidContext(appCtx)
            .name("ironlog_objectbox")
            .build()
    }
}
