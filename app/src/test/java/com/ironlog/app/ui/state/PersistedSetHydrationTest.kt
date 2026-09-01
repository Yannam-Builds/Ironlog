package com.ironlog.app.ui.state

import com.ironlog.app.data.objectbox.WorkoutSetEntity
import org.junit.Assert.*
import org.junit.Test

class PersistedSetHydrationTest {
    @Test fun `database deletion wins over longer stale draft without completing pending warmups`() {
        val current = WorkoutState(setLog = mapOf(0 to listOf(LoggedSet(id = "deleted"), LoggedSet(id = "kept"))), exerciseNotes = mapOf(0 to "keep note"))
        val restored = restorePersistedSetLog(current, listOf(WorkoutSetEntity().apply { uid = "kept"; workoutExerciseUid = "we"; setIndex = 0; weight = 60.0; reps = 8.0 }), mapOf(0 to "we"), mapOf(0 to "weight_reps"))
        assertEquals(listOf("kept"), restored.setLog[0]!!.map { it.id })
        assertEquals(current.exerciseNotes, restored.exerciseNotes)
        assertEquals(current.pendingWarmups, restored.pendingWarmups)
        assertTrue(restorePersistedSetLog(current, emptyList(), mapOf(0 to "we"), emptyMap()).setLog[0].orEmpty().isEmpty())
    }
    @Test fun `timed tracking and stable note survive resume without a one rep max`() {
        val row = WorkoutSetEntity().apply { uid = "hold"; workoutExerciseUid = "we"; setIndex = 0; weight = 10.0; reps = 30.0; notes = "brace" }
        val set = restorePersistedSetLog(WorkoutState(), listOf(row), mapOf(0 to "we"), mapOf(0 to "duration_weight")).setLog[0]!!.single()
        assertEquals("hold", set.id)
        assertEquals("brace", set.note)
        assertEquals(30.0, set.durationSec!!, 0.0)
        assertEquals(0.0, set.orm, 0.0)
    }
    @Test fun `committed warmup is removed from pending queue after interrupted save`() {
        val current = WorkoutState(pendingWarmups = mapOf(0 to listOf(PendingWarmup("saved", 20.0, 5), PendingWarmup("pending", 30.0, 3))))
        val row = WorkoutSetEntity().apply { uid = "saved"; workoutExerciseUid = "we"; weight = 20.0; reps = 5.0; isWarmup = true }
        val restored = restorePersistedSetLog(current, listOf(row), mapOf(0 to "we"), mapOf(0 to "weight_reps"))
        assertEquals(listOf("pending"), restored.pendingWarmups[0]!!.map { it.id })
        assertEquals(listOf("saved"), restored.setLog[0]!!.map { it.id })
    }
}
