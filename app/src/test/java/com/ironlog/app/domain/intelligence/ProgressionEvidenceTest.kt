package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.state.GhostSet
import org.junit.Assert.*
import org.junit.Test

class ProgressionEvidenceTest {
    @Test fun `advice records source policy effort coverage and missing evidence`() {
        val advice = ProgressionRecommendationEngine.recommend(listOf(GhostSet(60.0, 8.0, rir = 3.0), GhostSet(60.0, 8.0)),
            "weight_reps", 2, 8, sourceDate = "2026-08-31T10:00:00Z")!!
        assertEquals(ProgressionAction.HOLD, advice.action)
        assertEquals("2026-08-31T10:00:00Z", advice.sourceDate)
        assertEquals(1, advice.effortRecordedSets)
        assertEquals(2, advice.workingSets)
        assertEquals("conservative-double-progression-v1", advice.policyId)
        assertTrue(advice.missingEvidence.contains("effort"))
    }
    @Test fun `complete evidence reports actual configured increase without claiming validation`() {
        val advice = ProgressionRecommendationEngine.recommend(List(3) { GhostSet(100.0, 8.0, rpe = 7.0) },
            "weight_reps", 3, 8, loadStepKg = 1.0)!!
        assertEquals(101.0, advice.weightKg, .0001)
        assertEquals(1.0, advice.loadStepKg!!, .0001)
        assertTrue(advice.missingEvidence.isEmpty())
        assertTrue(advice.provenance.contains("heuristic"))
    }

    @Test fun `recommendation records and applies the resolved policy`() {
        val policy = ProgressionPolicyResolver.resolve(
            exerciseOverride = null,
            planRules = null,
            globalSetting = "aggressive",
        )
        val advice = ProgressionRecommendationEngine.recommend(
            previous = List(3) { GhostSet(100.0, 8.0, rir = 1.0) },
            trackingType = "weight_reps",
            targetSets = 3,
            targetReps = 8,
            loadStepKg = 7.5,
            policy = policy,
        )!!

        assertEquals(ProgressionAction.ADD_LOAD, advice.action)
        assertEquals(policy.id, advice.policyId)
        assertEquals(ProgressionPolicySource.GLOBAL_SETTING, advice.policySource)
        assertEquals(policy.label, advice.policyLabel)
    }
}
