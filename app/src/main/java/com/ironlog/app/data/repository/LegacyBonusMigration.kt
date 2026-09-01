package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import io.objectbox.BoxStore
import org.json.JSONObject

/** Called with canonical ledger XP, in the same transaction as the reward rebuild. */
internal fun migrateLegacyBonusXpBlocking(store: BoxStore, profileXp: Long, ledgerXp: Long, nowEpochMs: Long) = store.runInTx {
    val settings = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
    val key = "gamification_bonus_events_v2_migrated"
    if (settings.getStringBlocking(key)?.toBooleanStrictOrNull() == true) return@runInTx
    val events = store.boxFor(IronLedgerEventEntity::class.java)
    val existing = events.query(IronLedgerEventEntity_.eventId.equal("legacy:bonus-v1")).build().use { it.findFirst() }
    val delta = (profileXp - ledgerXp).coerceAtLeast(0L)
    if (existing == null && delta > 0L) events.put(IronLedgerEventEntity(
        eventId = "legacy:bonus-v1", sourceType = "bonus", sourceId = "legacy-profile", eventKind = "legacy_bonus",
        occurredAt = nowEpochMs, xpDelta = delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        metadataJson = JSONObject().put("title", "Legacy bonus XP")
            .put("detail", "Preserved XP earned outside workout proof").toString(),
    ))
    // Restored events can exist without the marker. Never duplicate or resurrect an invalidated event.
    settings.setStringBlocking(key, "true", "boolean")
}
