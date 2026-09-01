package com.ironlog.app.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTypographySelection = staticCompositionLocalOf { TypographySelection() }

/** Isolated preferences: appearance changes never overwrite workout or ObjectBox settings. */
object TypographyRuntime {
    @Volatile private var instance: TypographyStore? = null

    fun store(context: Context): TypographyStore = instance ?: synchronized(this) {
        instance ?: TypographyStore(object : TypographyStorage {
            private val preferences = context.applicationContext.getSharedPreferences(AppearancePreferenceKeys.FILE, Context.MODE_PRIVATE)
            override fun read(): Pair<Any?, Any?> = preferences.all.let {
                it[AppearancePreferenceKeys.FONT_ID] to it[AppearancePreferenceKeys.TYPOGRAPHY_PRESET_ID]
            }
            override fun write(selection: TypographySelection) {
                // Called on IO. Publish the runtime selection only after the atomic disk write succeeds.
                val before = read().let { TypographySelection.fromStored(it.first, it.second) }
                val saved = preferences.edit()
                    .putString(AppearancePreferenceKeys.FONT_ID, selection.fontId)
                    .putString(AppearancePreferenceKeys.TYPOGRAPHY_PRESET_ID, selection.preset.id)
                    .commit()
                if (!saved) {
                    // commit updates SharedPreferences memory even when disk fails; restore that cache too.
                    preferences.edit()
                        .putString(AppearancePreferenceKeys.FONT_ID, before.fontId)
                        .putString(AppearancePreferenceKeys.TYPOGRAPHY_PRESET_ID, before.preset.id)
                        .apply()
                    error("Typography could not be saved. Please try again.")
                }
            }
        }).also { instance = it }
    }
}
