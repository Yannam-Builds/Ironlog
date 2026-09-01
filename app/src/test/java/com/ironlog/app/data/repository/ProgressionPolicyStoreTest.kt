package com.ironlog.app.data.repository

import com.ironlog.app.domain.intelligence.ProgressionPolicySource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionPolicyStoreTest {
    @Test
    fun `store reads existing schemas once and resolves every plan exercise`() = runTest {
        val reads = mutableListOf<String>()
        val values = mapOf(
            "ironlog_settings" to """{"progressionStyle":"aggressive"}""",
            "program_rules:plan-1" to """{"progressionModel":"linear"}""",
            "ex_prog:row-1" to "double_progression",
        )
        val store = ProgressionPolicyStore { key -> reads += key; values[key] }

        val snapshot = store.load("plan-1", setOf("row-1", "row-2"))

        assertEquals(ProgressionPolicySource.PLAN_RULES, snapshot.fallback.source)
        assertEquals(ProgressionPolicySource.EXERCISE_OVERRIDE, snapshot.forPlanExercise("row-1").source)
        assertEquals(ProgressionPolicySource.PLAN_RULES, snapshot.forPlanExercise("row-2").source)
        assertEquals(1, reads.count { it == "ironlog_settings" })
        assertEquals(1, reads.count { it == "program_rules:plan-1" })
        assertTrue(reads.contains("ex_prog:row-1"))
        assertTrue(reads.contains("ex_prog:row-2"))
    }

    @Test
    fun `missing persisted global style uses conservative fallback`() = runTest {
        val store = ProgressionPolicyStore { null }

        val snapshot = store.load(planId = null, planExerciseIds = emptySet())

        assertEquals(ProgressionPolicySource.CONSERVATIVE_DEFAULT, snapshot.fallback.source)
    }

    @Test
    fun `malformed settings and program rules do not block a valid exercise override`() = runTest {
        val values = mapOf(
            "ironlog_settings" to "not-json",
            "program_rules:plan-1" to "{",
            "ex_prog:row-1" to "rpe_rir",
        )
        val store = ProgressionPolicyStore { values[it] }

        val snapshot = store.load("plan-1", setOf("row-1"))

        assertEquals(ProgressionPolicySource.EXERCISE_OVERRIDE, snapshot.forPlanExercise("row-1").source)
        assertEquals(ProgressionPolicySource.CONSERVATIVE_DEFAULT, snapshot.fallback.source)
    }
}
