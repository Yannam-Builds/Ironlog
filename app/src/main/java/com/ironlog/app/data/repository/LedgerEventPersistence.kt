package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.IronLedgerEventEntity
import com.ironlog.app.domain.gamification.IronLedgerEvent
import com.ironlog.app.domain.gamification.parseHistoryInstant
import io.objectbox.BoxStore
import java.time.ZoneId
import org.json.JSONObject

/** One indexed-in-memory pass; unchanged snapshots do not rewrite every historical event. */
internal fun persistLedgerSnapshotEvents(store: BoxStore, events: List<IronLedgerEvent>, zone: ZoneId = ZoneId.systemDefault()): Int = store.callInTx {
    val box = store.boxFor(IronLedgerEventEntity::class.java)
    val existing = box.all.associateBy { it.eventId }
    val ids = events.map { "${it.sourceId}:${it.kind}" }.toSet()
    val changed = mutableListOf<IronLedgerEventEntity>()
    existing.values.filter { it.sourceType in setOf("workout", "exercise") && it.eventId !in ids && !it.invalidated }
        .forEach { changed += it.copy(invalidated = true) }
    events.forEach { event ->
        val id = "${event.sourceId}:${event.kind}"
        val old = existing[id]
        val next = (old ?: IronLedgerEventEntity()).copy(
            eventId = id, sourceType = if (event.kind == "pr") "exercise" else "workout",
            sourceId = event.sourceId, eventKind = event.kind, invalidated = false,
            occurredAt = parseHistoryInstant(event.occurredAt, zone)?.toEpochMilli() ?: 0L,
            xpDelta = event.xp, trustScore = event.trust,
            metadataJson = JSONObject().put("title", event.title).put("detail", event.detail).toString(),
        )
        if (old != next) changed += next
    }
    if (changed.isNotEmpty()) box.put(changed)
    changed.size
}
