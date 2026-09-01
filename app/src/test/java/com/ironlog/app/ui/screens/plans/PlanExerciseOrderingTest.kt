package com.ironlog.app.ui.screens.plans

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlanExerciseOrderingTest {
    private val ids = listOf("exercise-A", "exercise-B", "exercise-C", "exercise-D")

    @Test fun movesNonAdjacentStableIdByInsertionRatherThanSwap() {
        assertEquals(listOf("exercise-B", "exercise-C", "exercise-A", "exercise-D"),
            movePlanExerciseIds(ids, "exercise-A", "exercise-C"))
        assertEquals(listOf("exercise-D", "exercise-A", "exercise-B", "exercise-C"),
            movePlanExerciseIds(ids, "exercise-D", "exercise-A"))
        assertEquals(listOf("exercise-A", "exercise-B", "exercise-C", "exercise-D"), ids)
    }

    @Test fun headersAndUnknownKeysNeverBecomeExerciseIndices() {
        assertEquals(ids, movePlanExerciseIds(ids, "plan-header", "exercise-C"))
        assertEquals(ids, movePlanExerciseIds(ids, "exercise-A", 3))
        assertEquals(ids, movePlanExerciseIds(ids, null, "exercise-A"))
        assertEquals(ids, movePlanExerciseIds(ids, "exercise-B", "exercise-B"))
        assertEquals(emptyList<String>(), movePlanExerciseIds(emptyList(), "A", "B"))
    }

    @Test fun accessibilityAdjacentMovesHaveIdenticalOrderingRules() {
        assertEquals(listOf("exercise-A", "exercise-C", "exercise-B", "exercise-D"),
            movePlanExerciseIds(ids, "exercise-C", "exercise-B"))
        assertEquals(listOf("exercise-A", "exercise-C", "exercise-B", "exercise-D"),
            movePlanExerciseIds(ids, "exercise-B", "exercise-C"))
    }

    @Test fun reorderDoesNotReportSuccessUntilPersistenceFinishes() = runBlocking {
        val write = CompletableDeferred<Unit>()
        var persisted: List<String>? = null
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            persistPlanExerciseMove(ids, "exercise-A", "exercise-C") {
                persisted = it
                write.await()
            }
        }
        assertFalse(result.isCompleted)
        assertEquals(listOf("exercise-B", "exercise-C", "exercise-A", "exercise-D"), persisted)
        assertEquals("exercise-A", ids.first())
        write.complete(Unit)
        assertTrue(result.await())
    }

    @Test fun failedReorderPropagatesAndDoesNotChangeDisplayedSourceOrder() = runBlocking {
        val failure = IllegalStateException("Synthetic persistence failure")
        val result = runCatching {
            persistPlanExerciseMove(ids, "exercise-A", "exercise-C") { throw failure }
        }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(listOf("exercise-A", "exercise-B", "exercise-C", "exercise-D"), ids)
    }

    @Test fun invalidTargetDoesNotWriteOrClaimSuccess() = runBlocking {
        assertFalse(persistPlanExerciseMove(ids, "exercise-A", "header") {
            fail("Header drops must not write plan rows")
        })
    }
}
