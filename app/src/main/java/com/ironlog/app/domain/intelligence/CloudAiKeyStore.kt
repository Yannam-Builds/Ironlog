package com.ironlog.app.domain.intelligence

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Per-provider encrypted API key storage.
 * Each provider (openai, claude, gemini, …) gets its own slot.
 * Keys are NEVER stored in IronLogSettings or ObjectBox.
 * Never log or print values returned by [load].
 */
object CloudAiKeyStore {
    private val changes = MutableStateFlow(0L)
    /** Invalidation signal only; API keys never enter observable app/settings state. */
    val revision = changes.asStateFlow()

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
    @Synchronized
    fun save(context: Context, provider: String, key: String) {
        val normalized = key.trim()
        require(normalized.isNotEmpty()) { "API key cannot be blank" }
        val committed = runCatching {
            prefs(context).edit().putString(keyFor(provider), normalized).remove(KEY_LEGACY).commit()
        }.getOrElse { throw IllegalStateException("Could not store the API key securely", it) }
        check(committed) { "Could not store the API key securely" }
        changes.update { it + 1 }
    }

    /**
     * Returns the stored API key for [provider], or "" if not set.
     * Legacy keys are migrated once at startup to their recorded provider, never used as fallback.
     */
    fun load(context: Context, provider: String): String {
        val p = runCatching { prefs(context) }.getOrNull() ?: return ""
        val providerKey = runCatching { p.getString(keyFor(provider), "") ?: "" }.getOrDefault("")
        if (providerKey.isNotBlank()) return providerKey.trim()
        return ""
    }

    @Synchronized
    fun migrateLegacy(context: Context, recordedProvider: String?) {
        val p = prefs(context)
        val migration = legacyKeyMigration(
            p.getString(KEY_LEGACY, "").orEmpty(),
            recordedProvider?.let { p.getString(keyFor(it), "") }.orEmpty(),
            recordedProvider,
        ) ?: return
        check(p.edit().putString(keyFor(migration.provider), migration.key).remove(KEY_LEGACY).commit()) {
            "Could not migrate the API key securely"
        }
        changes.update { it + 1 }
    }

    /** Deletes the stored API key for [provider]. */
    @Synchronized
    fun clear(context: Context, provider: String) {
        if (runCatching { prefs(context).edit().remove(keyFor(provider)).remove(KEY_LEGACY).commit() }.getOrDefault(false)) {
            changes.update { it + 1 }
        }
    }
}
