package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import io.objectbox.query.Query
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ObjectBoxFlowLifetimeTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val store by lazy { MyObjectBox.builder().directory(temporaryFolder.newFolder("queries")).build() }
    private val box get() = store.boxFor(AppSettingEntity::class.java)

    @After fun closeStore() { store.closeThreadResources(); store.close() }

    @Test fun `query creation is lazy and first closes each independently recollected query`() = runBlocking {
        val queries = mutableListOf<Query<AppSettingEntity>>()
        val flow = observeQuery { box.query().build().also(queries::add) }
        assertTrue(queries.isEmpty())
        withTimeout(5_000) { repeat(3) { assertTrue(flow.first().isEmpty()) } }
        assertEquals(3, queries.size)
        queries.forEach(::assertClosed)
        assertNotSame(queries[0], queries[1])
    }

    @Test fun `canceling one collector closes only its query and another still receives writes`() = runBlocking {
        val queries = mutableListOf<Query<AppSettingEntity>>()
        val flow = observeQuery { box.query().build().also(queries::add) }
        val firstReady = CompletableDeferred<Unit>()
        val secondRows = Channel<List<AppSettingEntity>>(Channel.UNLIMITED)
        val first = launch { flow.collect { firstReady.complete(Unit) } }
        withTimeout(5_000) { firstReady.await() }
        val second = launch { flow.collect { secondRows.send(it) } }
        try {
            withTimeout(5_000) { secondRows.receive() }
            assertEquals(2, queries.size)
            first.cancelAndJoin()
            assertClosed(queries[0])
            assertEquals(0L, queries[1].count())
            box.put(AppSettingEntity().apply { key = "test-only"; value = "updated" })
            val updated = withTimeout(5_000) { var rows = secondRows.receive(); while (rows.isEmpty()) rows = secondRows.receive(); rows }
            assertEquals("updated", updated.single().value)
        } finally {
            first.cancelAndJoin()
            second.cancelAndJoin()
            secondRows.close()
        }
        queries.forEach(::assertClosed)
    }

    @Test fun `downstream failure closes query and permits a fresh collection`() = runBlocking {
        val queries = mutableListOf<Query<AppSettingEntity>>()
        val flow = observeQuery { box.query().build().also(queries::add) }
        val failure = IllegalStateException("consumer failed")
        val actual = runCatching { withTimeout(5_000) { flow.collect { throw failure } } }.exceptionOrNull()
        // Coroutine stack-trace recovery may copy the throwable, retaining it as a cause.
        assertEquals(failure.javaClass, actual?.javaClass)
        assertEquals(failure.message, actual?.message)
        assertTrue(generateSequence(actual) { it.cause }.any { it === failure })
        assertClosed(queries.single())
        withTimeout(5_000) { flow.first() }
        assertEquals(2, queries.size)
        queries.forEach(::assertClosed)
    }

    private fun assertClosed(query: Query<*>) {
        // Query has no public isClosed; count checks its handle before touching native state.
        assertTrue("query must release its native handle", runCatching { query.count() }.isFailure)
    }
}
