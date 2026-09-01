package com.ironlog.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.IOException

val LocalCardShineEnabled = staticCompositionLocalOf { true }

internal class SharedPreferencesCardShineStorage(private val preferences: SharedPreferences) : CardShineStorage {
    override fun read(): Any? = preferences.all[AppearancePreferenceKeys.CARD_SHINE]
    override fun write(enabled: Boolean) {
        val before = read()
        if (!preferences.edit().putBoolean(AppearancePreferenceKeys.CARD_SHINE, enabled).commit()) {
            // A failed commit may already have changed SharedPreferences' in-memory value.
            val rollback = preferences.edit()
            if (before is Boolean) rollback.putBoolean(AppearancePreferenceKeys.CARD_SHINE, before)
            else rollback.remove(AppearancePreferenceKeys.CARD_SHINE)
            rollback.apply()
            throw IOException("Card shine could not be saved. Please try again.")
        }
    }

}

object CardShineRuntime {
    @Volatile private var instance: CardShineStore? = null
    fun store(context: Context): CardShineStore = instance ?: synchronized(this) {
        instance ?: CardShineStore(SharedPreferencesCardShineStorage(
            context.applicationContext.getSharedPreferences(AppearancePreferenceKeys.FILE, Context.MODE_PRIVATE),
        )).also { instance = it }
    }
}
