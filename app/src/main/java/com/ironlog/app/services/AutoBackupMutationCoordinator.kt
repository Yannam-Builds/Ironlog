package com.ironlog.app.services

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes auto-backup preference and WorkManager mutations across screen recreation. */
internal class LatestWinsMutationGate {
    private val latestToken = AtomicLong(0L)
    private val mutationMutex = Mutex()

    fun issueToken(): Long = latestToken.incrementAndGet()

    fun currentToken(): Long = latestToken.get()

    fun isCurrent(token: Long): Boolean = latestToken.get() == token

    suspend fun runIfCurrent(token: Long, mutation: suspend () -> Unit): Boolean =
        mutationMutex.withLock {
            if (!isCurrent(token)) return@withLock false
            mutation()
            isCurrent(token)
        }
}

internal object AutoBackupMutationCoordinator {
    private val gate = LatestWinsMutationGate()

    fun issueToken(): Long = gate.issueToken()

    fun currentToken(): Long = gate.currentToken()

    fun isCurrent(token: Long): Boolean = gate.isCurrent(token)

    suspend fun runIfCurrent(token: Long, mutation: suspend () -> Unit): Boolean =
        gate.runIfCurrent(token, mutation)
}

/** Once artifact persistence starts, finish its durable marker and ancillary cleanup together. */
internal suspend fun <T> commitBackupArtifactWithCleanup(
    commit: suspend () -> T,
    cleanup: suspend () -> Unit,
    onCleanupFailure: (Exception) -> Unit = {},
): T = withContext(Dispatchers.IO + NonCancellable) {
    val committed = commit()
    try {
        cleanup()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onCleanupFailure(error)
    }
    committed
}
