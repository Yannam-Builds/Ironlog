package com.ironlog.app.data.repository

import io.objectbox.query.Query
import io.objectbox.BoxStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** A cold observation: every collector owns and closes its own native query. */
fun <T> observeQuery(createQuery: () -> Query<T>): Flow<List<T>> = callbackFlow {
    val query = createQuery()
    var subscription: io.objectbox.reactive.DataSubscription? = null
    try {
        subscription = query.subscribe().onError { close(it) }.observer { data ->
            trySend(data)
            Unit
        }
        awaitClose { }
    } finally {
        try {
            subscription?.cancel()
        } finally {
            query.close()
        }
    }
}

/** Invalidates without materializing table rows. The shared store itself is not owned here. */
fun observeEntityChanges(createStore: () -> BoxStore, vararg entityTypes: Class<*>): Flow<Unit> = callbackFlow {
    val types = entityTypes.toSet()
    val subscription = createStore().subscribe().onlyChanges().onError { close(it) }.observer { entityType ->
        if (entityType in types) trySend(Unit)
        Unit
    }
    // Subscribe before the initial read, so a concurrent committed write cannot be missed.
    trySend(Unit)
    awaitClose { subscription.cancel() }
}.conflate()
