package com.ironlog.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.IOException

val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

internal class SharedPreferencesLiquidGlassStorage(private val preferences: SharedPreferences) : LiquidGlassStorage {
    override fun read(): Any? = preferences.all[AppearancePreferenceKeys.LIQUID_GLASS]

    override fun write(enabled: Boolean) {
        val before = read()
        if (!preferences.edit().putBoolean(AppearancePreferenceKeys.LIQUID_GLASS, enabled).commit()) {
            // Failed commits may have updated SharedPreferences' in-memory cache already.
            val rollback = preferences.edit()
            if (before is Boolean) rollback.putBoolean(AppearancePreferenceKeys.LIQUID_GLASS, before)
            else rollback.remove(AppearancePreferenceKeys.LIQUID_GLASS)
            rollback.apply()
            throw IOException("Liquid glass navigation could not be saved. Please try again.")
        }
    }

}

object LiquidGlassRuntime {
    @Volatile private var instance: LiquidGlassStore? = null

    fun store(context: Context): LiquidGlassStore = instance ?: synchronized(this) {
        instance ?: LiquidGlassStore(SharedPreferencesLiquidGlassStorage(
            context.applicationContext.getSharedPreferences(AppearancePreferenceKeys.FILE, Context.MODE_PRIVATE),
        )).also { instance = it }
    }
}
