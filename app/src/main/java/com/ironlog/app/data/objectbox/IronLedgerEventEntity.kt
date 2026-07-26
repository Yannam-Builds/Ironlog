package com.ironlog.app.data.objectbox

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

@Entity
data class IronLedgerEventEntity(
    @Id var id: Long = 0,
    @Unique var eventId: String = "",
    @Index var sourceType: String = "",
    @Index var sourceId: String = "",
    @Index var eventKind: String = "",
    @Index var occurredAt: Long = 0L,
    var xpDelta: Int = 0,
    var statDeltasJson: String = "{}",
    var trustScore: Double = 1.0,
    @Index var fingerprint: String = "",
    var metadataJson: String = "{}",
    @Index var invalidated: Boolean = false,
)
