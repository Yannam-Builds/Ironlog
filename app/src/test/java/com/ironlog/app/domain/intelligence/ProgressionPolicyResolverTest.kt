package com.ironlog.app.domain.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionPolicyResolverTest {
    @Test
    fun `exercise override wins over plan rules and global setting`() {
        val resolved = ProgressionPolicyResolver.resolve(
            exerciseOverride = "rpe_rir",
            planRules = ProgramRules(progressionModel = "percent_1rm", rpeTarget = 7, rirTarget = 3),
            globalSetting = "aggressive",
        )

        assertEquals(ProgressionPolicySource.EXERCISE_OVERRIDE, resolved.source)
        assertEquals(ProgressionStrategy.RPE_RIR, resolved.strategy)
        assertEquals(7, resolved.rpeTarget)
        assertEquals(3, resolved.rirTarget)
    }

    @Test
    fun `plan rules win over the global setting when no exercise override exists`() {
        val resolved = ProgressionPolicyResolver.resolve(
            exerciseOverride = "",
            planRules = ProgramRules(progressionModel = "percent_1rm", percent1RM = 80),
            globalSetting = "aggressive",
        )

        assertEquals(ProgressionPolicySource.PLAN_RULES, resolved.source)
        assertEquals(ProgressionStrategy.PERCENT_1RM, resolved.strategy)
        assertEquals(80, resolved.percent1RM)
    }

    @Test
    fun `global setting is used when exercise and plan policies are absent`() {
        val resolved = ProgressionPolicyResolver.resolve(
            exerciseOverride = null,
            planRules = null,
            globalSetting = "balanced",
        )

        assertEquals(ProgressionPolicySource.GLOBAL_SETTING, resolved.source)
        assertEquals("balanced-double-progression-v1", resolved.id)
        assertEquals(ProgressionStrategy.DOUBLE_PROGRESSION, resolved.strategy)
    }

    @Test
    fun `unknown persisted values fall through to the conservative default`() {
        val resolved = ProgressionPolicyResolver.resolve(
            exerciseOverride = "future_model",
            planRules = ProgramRules(progressionModel = "also_unknown"),
            globalSetting = "mystery",
        )

        assertEquals(ProgressionPolicySource.CONSERVATIVE_DEFAULT, resolved.source)
        assertEquals("conservative-double-progression-v1", resolved.id)
        assertEquals("Conservative double progression", resolved.label)
    }

    @Test
    fun `resolved policy source is exposed as honest user facing provenance`() {
        ProgressionPolicySource.entries.forEach { source ->
            assertTrue(source.label.isNotBlank())
            assertTrue(source.description.isNotBlank())
        }
    }
}
