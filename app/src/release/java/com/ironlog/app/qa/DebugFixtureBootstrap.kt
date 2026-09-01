package com.ironlog.app.qa

import android.content.Context

/** Compile-time release no-op. Release artifacts never reference or package a QA fixture. */
object DebugFixtureBootstrap {
    suspend fun importIfPristine(context: Context) = Unit
}
