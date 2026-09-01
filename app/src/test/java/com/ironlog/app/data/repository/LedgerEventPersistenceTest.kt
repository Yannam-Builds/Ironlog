package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import com.ironlog.app.domain.gamification.IronLedgerEvent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LedgerEventPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun unchangedRebuildWritesNothingAndRemovedProofIsInvalidatedWithoutTouchingBonuses() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val box = store.boxFor(IronLedgerEventEntity::class.java)
            box.put(IronLedgerEventEntity(eventId = "bonus", sourceType = "bonus", xpDelta = 25))
            val event = IronLedgerEvent("w", "workout", "Saved", "Proof", 80, "2026-09-01T10:00:00Z", 1.0)
            assertEquals(1, persistLedgerSnapshotEvents(store, listOf(event)))
            assertEquals(0, persistLedgerSnapshotEvents(store, listOf(event)))
            assertEquals(1, persistLedgerSnapshotEvents(store, listOf(event.copy(xp = 90))))
            assertEquals(1, persistLedgerSnapshotEvents(store, emptyList()))
            assertTrue(box.all.first { it.eventId == "w:workout" }.invalidated)
            assertFalse(box.all.first { it.eventId == "bonus" }.invalidated)
        }
    }
}
