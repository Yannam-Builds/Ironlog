package com.ironlog.app.data.repository

import org.json.JSONObject

/** Portable presentation settings. Credentials and runtime state are deliberately not representable here. */
data class AppearanceBackupSnapshot(
    val fontId: String = "lexend",
    val typographyPresetId: String = "original",
    val globalSpacing: Float = 1f,
    val cardSpacing: Float = 1f,
    val paddingSpacing: Float = 1f,
    val contentSpacing: Float = 1f,
    val cardShineEnabled: Boolean = true,
    val liquidGlassEnabled: Boolean = true,
)

interface AppearanceBackupStore {
    fun snapshot(): AppearanceBackupSnapshot
    fun replace(snapshot: AppearanceBackupSnapshot)
}

/** Process wiring keeps Android preferences out of repository tests while default repositories still include appearance. */
object AppearanceBackupRegistry {
    @Volatile
    private var installed: AppearanceBackupStore? = null

    fun install(store: AppearanceBackupStore) {
        installed = store
    }

    fun currentOrNull(): AppearanceBackupStore? = installed
}

object AppearanceBackupCodec {
    const val VERSION = 1

    fun encode(snapshot: AppearanceBackupSnapshot): JSONObject = JSONObject()
        .put("version", VERSION)
        .put(
            "typography",
            JSONObject()
                .put("font_id", snapshot.fontId)
                .put("preset_id", snapshot.typographyPresetId),
        )
        .put(
            "spacing",
            JSONObject()
                .put("global", snapshot.globalSpacing)
                .put("cards", snapshot.cardSpacing)
                .put("padding", snapshot.paddingSpacing)
                .put("content", snapshot.contentSpacing),
        )
        .put(
            "effects",
            JSONObject()
                .put("card_shine", snapshot.cardShineEnabled)
                .put("liquid_glass_navigation", snapshot.liquidGlassEnabled),
        )

    /** Missing or newer versions are ignored so older app builds can still restore all durable training data. */
    fun decode(payload: JSONObject?): AppearanceBackupSnapshot? {
        if (payload == null || payload.optInt("version", -1) != VERSION) return null
        val typography = payload.optJSONObject("typography") ?: JSONObject()
        val spacing = payload.optJSONObject("spacing") ?: JSONObject()
        val effects = payload.optJSONObject("effects") ?: JSONObject()
        return AppearanceBackupSnapshot(
            fontId = typography.optString("font_id", "lexend"),
            typographyPresetId = typography.optString("preset_id", "original"),
            globalSpacing = spacing.portableFloat("global"),
            cardSpacing = spacing.portableFloat("cards"),
            paddingSpacing = spacing.portableFloat("padding"),
            contentSpacing = spacing.portableFloat("content"),
            cardShineEnabled = effects.optBoolean("card_shine", true),
            liquidGlassEnabled = effects.optBoolean("liquid_glass_navigation", true),
        )
    }

    private fun JSONObject.portableFloat(key: String): Float =
        optDouble(key, 1.0).takeIf(Double::isFinite)?.toFloat() ?: 1f
}
