package com.ironlog.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import com.ironlog.app.data.repository.AppearanceBackupSnapshot
import com.ironlog.app.data.repository.AppearanceBackupStore
import java.io.IOException

internal object AppearancePreferenceKeys {
    const val FILE = "ironlog_typography"
    const val FONT_ID = "font_id"
    const val TYPOGRAPHY_PRESET_ID = "preset_id"
    const val GLOBAL_SPACING = "spacing_scale"
    const val CARD_SHINE = "animated_card_shine"
    const val LIQUID_GLASS = "liquid_glass_navigation"
}

/**
 * Bridges the versioned full-backup payload to the existing appearance preferences and flows.
 * All eight portable values use one SharedPreferences commit; no credential preference is read.
 */
class SharedPreferencesAppearanceBackupStore private constructor(
    private val appContext: Context,
    private val preferences: SharedPreferences,
) : AppearanceBackupStore {
    constructor(context: Context) : this(
        context.applicationContext,
        context.applicationContext.getSharedPreferences(AppearancePreferenceKeys.FILE, Context.MODE_PRIVATE),
    )

    override fun snapshot(): AppearanceBackupSnapshot = preferences.all.let { values ->
        val typography = TypographySelection.fromStored(
            values[AppearancePreferenceKeys.FONT_ID],
            values[AppearancePreferenceKeys.TYPOGRAPHY_PRESET_ID],
        )
        val global = SpacingSettings.normalize(values[AppearancePreferenceKeys.GLOBAL_SPACING])
        AppearanceBackupSnapshot(
            fontId = typography.fontId,
            typographyPresetId = typography.preset.id,
            globalSpacing = global,
            cardSpacing = SpacingRole.CARDS.initialValue(values),
            paddingSpacing = SpacingRole.PADDING.initialValue(values),
            contentSpacing = SpacingRole.CONTENT.initialValue(values),
            cardShineEnabled = values[AppearancePreferenceKeys.CARD_SHINE] as? Boolean ?: true,
            liquidGlassEnabled = values[AppearancePreferenceKeys.LIQUID_GLASS] as? Boolean ?: true,
        )
    }

    override fun replace(snapshot: AppearanceBackupSnapshot) {
        val before = this.snapshot()
        val normalized = snapshot.normalized()
        if (!preferences.edit().write(normalized).commit()) {
            val rollback = preferences.edit().write(before)
            if (!rollback.commit()) rollback.apply()
            throw IOException("Appearance could not be restored. Existing appearance was retained.")
        }
        publish(normalized)
    }

    private fun publish(snapshot: AppearanceBackupSnapshot) {
        TypographyRuntime.store(appContext).publishRestored(
            TypographySelection.fromStored(snapshot.fontId, snapshot.typographyPresetId),
        )
        SpacingRuntime.store(appContext).publishRestored(snapshot.globalSpacing)
        SpacingRuntime.store(appContext, SpacingRole.CARDS).publishRestored(snapshot.cardSpacing)
        SpacingRuntime.store(appContext, SpacingRole.PADDING).publishRestored(snapshot.paddingSpacing)
        SpacingRuntime.store(appContext, SpacingRole.CONTENT).publishRestored(snapshot.contentSpacing)
        CardShineRuntime.store(appContext).publishRestored(snapshot.cardShineEnabled)
        LiquidGlassRuntime.store(appContext).publishRestored(snapshot.liquidGlassEnabled)
    }

    private fun AppearanceBackupSnapshot.normalized(): AppearanceBackupSnapshot {
        val typography = TypographySelection.fromStored(fontId, typographyPresetId)
        return copy(
            fontId = typography.fontId,
            typographyPresetId = typography.preset.id,
            globalSpacing = SpacingSettings.normalize(globalSpacing),
            cardSpacing = SpacingSettings.normalize(cardSpacing),
            paddingSpacing = SpacingSettings.normalize(paddingSpacing),
            contentSpacing = SpacingSettings.normalize(contentSpacing),
        )
    }

    private fun SharedPreferences.Editor.write(snapshot: AppearanceBackupSnapshot): SharedPreferences.Editor =
        putString(AppearancePreferenceKeys.FONT_ID, snapshot.fontId)
            .putString(AppearancePreferenceKeys.TYPOGRAPHY_PRESET_ID, snapshot.typographyPresetId)
            .putFloat(AppearancePreferenceKeys.GLOBAL_SPACING, snapshot.globalSpacing)
            .putFloat(SpacingRole.CARDS.preferenceKey, snapshot.cardSpacing)
            .putFloat(SpacingRole.PADDING.preferenceKey, snapshot.paddingSpacing)
            .putFloat(SpacingRole.CONTENT.preferenceKey, snapshot.contentSpacing)
            .putBoolean(AppearancePreferenceKeys.CARD_SHINE, snapshot.cardShineEnabled)
            .putBoolean(AppearancePreferenceKeys.LIQUID_GLASS, snapshot.liquidGlassEnabled)
}
