package com.ironlog.app.domain.intelligence

import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionExplanationPromptTest {
    @Test
    fun `AI progression explanation is constrained by the resolved policy and evidence limits`() {
        val policy = ProgressionPolicyResolver.resolve(
            exerciseOverride = "rpe_rir",
            planRules = ProgramRules(rpeTarget = 8, rirTarget = 2),
            globalSetting = "aggressive",
        )

        val prompt = buildProgressionExplanationPrompt(
            exerciseName = "Bench Press",
            recentWeightKg = 100.0,
            recentReps = 8,
            trend = "stable",
            policy = policy,
        )

        assertTrue(prompt.contains(policy.label))
        assertTrue(prompt.contains(policy.source.label))
        assertTrue(prompt.contains("Do not invent"))
        assertTrue(prompt.contains("RPE/RIR"))
    }
}
