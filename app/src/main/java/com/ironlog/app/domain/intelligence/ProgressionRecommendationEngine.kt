package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.state.GhostSet
import kotlin.math.abs

enum class ProgressionAction { HOLD, ADD_REPS, ADD_LOAD }
data class ProgressionAdvice(
    val action: ProgressionAction, val weightKg: Double, val reps: Int, val reason: String,
    val sourceDate: String? = null,
    val policyId: String = "conservative-double-progression-v1",
    val policyLabel: String = "Conservative double progression",
    val policySource: ProgressionPolicySource = ProgressionPolicySource.CONSERVATIVE_DEFAULT,
    val workingSets: Int = 0,
    val effortRecordedSets: Int = 0,
    val missingEvidence: Set<String> = emptySet(),
    val loadStepKg: Double? = null,
    val provenance: String = "Rule-based coaching heuristic; not an individualized physiological prediction.",
)

/** Conservative coaching heuristics, not a physiological model or an automatic plan edit. */
object ProgressionRecommendationEngine {
    fun recommend(
        previous: List<GhostSet>,
        trackingType: String,
        targetSets: Int,
        targetReps: Int,
        loadStepKg: Double = 2.5,
        sourceDate: String? = null,
        policy: ResolvedProgressionPolicy = ResolvedProgressionPolicy.conservativeDefault(),
    ): ProgressionAdvice? {
        val tracking = trackingType.trim().lowercase()
        if (tracking !in setOf("weight_reps", "reps", "bodyweight_reps", "bodyweight_plus_weight_reps", "weighted_bodyweight")) return null
        val sets = previous.filter { !it.type.equals("warmup", true) }
        if (sets.isEmpty() || sets.any { !it.weight.isFinite() || it.weight < 0 || !it.reps.isFinite() || it.reps !in 1.0..1000.0 }) return null
        val baseline = sets.first().weight
        val margins = sets.map { set ->
            val rir = set.rir?.takeIf { it.isFinite() && it in 0.0..10.0 }
            val rpeMargin = set.rpe?.takeIf { it.isFinite() && it in 1.0..10.0 }?.let { 10.0 - it }
            listOfNotNull(rir, rpeMargin).minOrNull()
        }
        val missing = buildSet {
            if (margins.any { it == null }) add("effort")
            if (targetSets <= 0 || targetReps <= 0) add("targets")
        }
        fun advice(action: ProgressionAction, weight: Double, reps: Int, reason: String) = ProgressionAdvice(
            action, weight, reps, reason, sourceDate = sourceDate,
            policyId = policy.id, policyLabel = policy.label, policySource = policy.source,
            workingSets = sets.size, effortRecordedSets = margins.count { it != null }, missingEvidence = missing,
            loadStepKg = loadStepKg.takeIf { it.isFinite() && it > 0.0 },
        )
        fun hold(reason: String) = advice(ProgressionAction.HOLD, baseline, targetReps, reason)
        if (targetSets <= 0 || targetReps <= 0) return hold("Repeat and assess: set a rep target before increasing difficulty.")
        if (sets.size < targetSets || sets.any { it.reps < targetReps }) {
            return hold("Repeat the load and build toward all planned reps first.")
        }
        if (sets.any { !it.type.equals("normal", true) }) {
            return hold("Repeat and assess: special set techniques need an individual progression decision.")
        }
        if (margins.any { it == null }) return hold("Repeat and assess: record RPE or reps in reserve before increasing difficulty.")
        if (margins.any { it!! < policy.minimumEffortMargin }) {
            return hold("Repeat the load; the last session was already closer to your limit than this policy allows.")
        }
        if (sets.any { abs(it.weight - baseline) > .01 }) return hold("Repeat your planned loading pattern; mixed loads need a set-by-set review.")
        val supportsLoad = tracking in setOf("weight_reps", "bodyweight_plus_weight_reps", "weighted_bodyweight") && baseline > 0.0
        val loadStepAllowed = supportsLoad && loadStepKg.isFinite() && loadStepKg > 0 &&
            loadStepKg / baseline <= policy.maximumLoadIncreaseRatio
        if (policy.strategy == ProgressionStrategy.PERCENT_1RM && supportsLoad) {
            val estimatedOneRm = sets.maxOf { it.weight * (1.0 + it.reps / 30.0) }
            val policyTarget = estimatedOneRm * (policy.percent1RM / 100.0)
            if (policyTarget <= baseline + .01) {
                return hold("Repeat and assess: the current load already meets this policy's ${policy.percent1RM}% estimated 1RM target.")
            }
            if (!loadStepAllowed || baseline + loadStepKg > policyTarget * 1.02) {
                return advice(ProgressionAction.ADD_REPS, baseline, sets.minOf { it.reps }.toInt() + 1,
                    "The next available load exceeds this policy's ${policy.percent1RM}% estimated 1RM target. Add one controlled rep instead.")
            }
        }
        if (!loadStepAllowed) {
            return advice(ProgressionAction.ADD_REPS, baseline, sets.minOf { it.reps }.toInt() + 1, "Targets met with effort in reserve. Try one more rep with the same technique.")
        }
        return advice(ProgressionAction.ADD_LOAD, baseline + loadStepKg, targetReps,
            "Targets met within the ${policy.label.lowercase()} guardrails. Try the smallest available increase only if technique stays solid.")
    }
}
