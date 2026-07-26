package com.ironlog.app.domain.intelligence

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Per-provider encrypted API key storage.
 * Each provider (openai, claude, gemini, …) gets its own slot.
 * Keys are NEVER stored in IronLogSettings or ObjectBox.
 * Never log or print values returned by [load].
 */
object CloudAiKeyStore {

    private const val PREFS_FILE = "cloud_ai_key_store"
    // Legacy single-key name kept for migration reads.
    private const val KEY_LEGACY = "api_key"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun keyFor(provider: String) = "api_key_${provider.ifBlank { "custom" }}"

    /** Stores the API key for [provider] encrypted on-device. */
    fun save(context: Context, provider: String, key: String) {
        runCatching { prefs(context).edit().putString(keyFor(provider), key).apply() }
    }

    /**
     * Returns the stored API key for [provider], or "" if not set.
     * Falls back to the legacy single-key slot so existing users keep their key after upgrade.
     */
    fun load(context: Context, provider: String): String {
        val p = runCatching { prefs(context) }.getOrNull() ?: return ""
        val providerKey = runCatching { p.getString(keyFor(provider), "") ?: "" }.getOrDefault("")
        if (providerKey.isNotBlank()) return providerKey
        // Migration: return legacy key (set when there was only one slot)
        return runCatching { p.getString(KEY_LEGACY, "") ?: "" }.getOrDefault("")
    }

    /** Deletes the stored API key for [provider]. */
    fun clear(context: Context, provider: String) {
        runCatching { prefs(context).edit().remove(keyFor(provider)).apply() }
    }
}
