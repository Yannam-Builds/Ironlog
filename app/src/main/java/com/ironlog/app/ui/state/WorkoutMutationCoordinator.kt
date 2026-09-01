package com.ironlog.app.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WorkoutInitializationState(
    val loading: Boolean = false,
    val ready: Boolean = false,
    val error: String? = null,
)

/** Owns no extra scope. The ViewModel supplies its scope and the shared mutation lock. */
internal class WorkoutMutationCoordinator(
    private val scope: CoroutineScope,
    private val mutex: Mutex = Mutex(),
) {
    private val state = MutableStateFlow(WorkoutInitializationState())
    val initialization = state.asStateFlow()
    private var attempt: Deferred<Result<Unit>>? = null
    private var closed = false

    fun initialize(load: suspend () -> Unit): Deferred<Result<Unit>> {
        attempt?.takeIf { it.isActive || state.value.ready || closed }?.let { return it }
        val next = scope.async(start = CoroutineStart.LAZY) {
            try {
                load()
                state.value = WorkoutInitializationState(ready = true)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                state.value = WorkoutInitializationState()
                throw cancelled
            } catch (error: Exception) {
                state.value = WorkoutInitializationState(error = error.message ?: "Workout could not be loaded. Retry.")
                Result.failure(error)
            }
        }
        attempt = next
        state.value = WorkoutInitializationState(loading = true)
        next.start()
        return next
    }

    suspend fun awaitReady() {
        check(!closed) { "This workout has already ended." }
        checkNotNull(attempt) { "The workout is still loading. Try again." }.await().getOrThrow()
        check(!closed && state.value.ready) { "This workout is not ready." }
    }

    suspend fun <T> commit(write: suspend () -> T, publish: (T) -> Unit): T {
        awaitReady() // Never wait for initialization while holding the mutation lock.
        return mutex.withLock {
            check(!closed && state.value.ready) { "This workout has already ended." }
            val committed = write()
            publish(committed)
            committed
        }
    }

    fun close() {
        closed = true
        state.value = WorkoutInitializationState()
    }
}

/** Never substitutes a new session after a failed read or restoration of an existing one. */
internal suspend fun <T : Any> loadOrCreateWorkout(
    readActiveId: suspend () -> String?,
    resume: suspend (String) -> T?,
    create: suspend () -> T,
    restore: suspend (T) -> Unit,
): T {
    val activeId = readActiveId()?.takeIf { it.isNotBlank() }
    val workout = if (activeId == null) create() else checkNotNull(resume(activeId)) {
        "The saved active workout could not be loaded. Retry without starting another session."
    }
    restore(workout)
    return workout
}
