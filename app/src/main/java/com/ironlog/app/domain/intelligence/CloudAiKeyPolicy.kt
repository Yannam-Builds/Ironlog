package com.ironlog.app.domain.intelligence

internal data class LegacyKeyMigration(val provider: String, val key: String)

/** Never guess a legacy credential's destination from the provider being opened now. */
internal fun legacyKeyMigration(legacy: String, existing: String, recordedProvider: String?): LegacyKeyMigration? {
    val provider = recordedProvider?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (legacy.isBlank()) return null
    return LegacyKeyMigration(provider, existing.trim().ifEmpty { legacy.trim() })
}
