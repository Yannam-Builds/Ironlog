package com.ironlog.app.data.repository

import io.objectbox.BoxStore
import com.ironlog.app.ui.model.HistoryEntry

/** Derive from current persisted proof, not a screen's possibly stale invalidation hint.
 * All derived profile/event/migration writes share the same transaction and rollback boundary.
 * Must run off main. The callback must not suspend or swallow persistence failures.
 */
internal fun <T> recomputeGamificationAtomically(store: BoxStore, derive: (List<HistoryEntry>) -> T): T =
    store.callInTx {
        // This is the authoritative boundary: a refresh cannot observe/create `local` or
        // commit the legacy-bonus marker until singleton/provenance normalization completes.
        com.ironlog.app.data.objectbox.WorkoutImportProvenanceMigration.runInsideTransaction(store)
        derive(HistoryRepository(store).completedSnapshotBlocking())
    }
