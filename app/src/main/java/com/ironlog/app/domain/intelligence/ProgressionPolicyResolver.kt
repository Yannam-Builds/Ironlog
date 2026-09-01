package com.ironlog.app.domain.intelligence

import kotlinx.serialization.Serializable

/** Persisted program-rules schema shared by Plan Editor and progression consumers. */
@Serializable
data class ProgramRules(
    val progressionModel: String = "double_progression",
    val blockLengthWeeks: Int = 4,
    val currentWeek: Int = 1,
    val deloadEveryWeeks: Int = 4,
    val percent1RM: Int = 75,
    val rpeTarget: Int = 8,
    val rirTarget: Int = 2,
)

enum class ProgressionStrategy {
    DOUBLE_PROGRESSION,
    LINEAR,
    PERCENT_1RM,
    RPE_RIR,
}

enum class ProgressionPolicySource(
    val label: String,
    val description: String,
) {
    EXERCISE_OVERRIDE(
        label = "Exercise override",
        description = "This exercise's saved progression override.",
    ),
    PLAN_RULES(
        label = "Plan rules",
        description = "The saved progression model for this plan.",
    ),
    GLOBAL_SETTING(
        label = "Global setting",
        description = "Your app-wide progression style from Settings.",
    ),
    CONSERVATIVE_DEFAULT(
        label = "Conservative fallback",
        description = "IronLog's conservative fallback because no saved policy was available.",
    ),
}

/**
 * One concrete policy that the recommendation engine can apply and explain.
 * Values are coaching guardrails, not validated physiological predictions.
 */
data class ResolvedProgressionPolicy(
    val id: String,
    val label: String,
    val strategy: ProgressionStrategy,
    val source: ProgressionPolicySource,
    val minimumEffortMargin: Double,
    val maximumLoadIncreaseRatio: Double,
    val percent1RM: Int = 75,
    val rpeTarget: Int = 8,
    val rirTarget: Int = 2,
) {
    companion object {
        fun conservativeDefault() = ResolvedProgressionPolicy(
            id = "conservative-double-progression-v1",
            label = "Conservative double progression",
            strategy = ProgressionStrategy.DOUBLE_PROGRESSION,
            source = ProgressionPolicySource.CONSERVATIVE_DEFAULT,
            minimumEffortMargin = 2.0,
            maximumLoadIncreaseRatio = 0.05,
        )
    }
}

/** Resolves saved choices without mutating or migrating any existing settings rows. */
object ProgressionPolicyResolver {
    fun resolve(
        exerciseOverride: String?,
        planRules: ProgramRules?,
        globalSetting: String?,
    ): ResolvedProgressionPolicy {
        modelPolicy(exerciseOverride, ProgressionPolicySource.EXERCISE_OVERRIDE, planRules)?.let { return it }
        planRules?.let { rules ->
            modelPolicy(rules.progressionModel, ProgressionPolicySource.PLAN_RULES, rules)?.let { return it }
        }
        globalPolicy(globalSetting)?.let { return it }
        return ResolvedProgressionPolicy.conservativeDefault()
    }

    private fun modelPolicy(
        raw: String?,
        source: ProgressionPolicySource,
        rules: ProgramRules?,
    ): ResolvedProgressionPolicy? {
        val percent = rules?.percent1RM?.coerceIn(50, 95) ?: 75
        val rpe = rules?.rpeTarget?.coerceIn(1, 10) ?: 8
        val rir = rules?.rirTarget?.coerceIn(0, 10) ?: (10 - rpe).coerceAtLeast(0)
        return when (raw.canonicalToken()) {
            "double_progression", "double" -> ResolvedProgressionPolicy(
                id = "double-progression-v1",
                label = "Double progression",
                strategy = ProgressionStrategy.DOUBLE_PROGRESSION,
                source = source,
                minimumEffortMargin = 2.0,
                maximumLoadIncreaseRatio = 0.05,
                percent1RM = percent,
                rpeTarget = rpe,
                rirTarget = rir,
            )
            "linear" -> ResolvedProgressionPolicy(
                id = "linear-progression-v1",
                label = "Linear progression",
                strategy = ProgressionStrategy.LINEAR,
                source = source,
                minimumEffortMargin = 2.0,
                maximumLoadIncreaseRatio = 0.05,
                percent1RM = percent,
                rpeTarget = rpe,
                rirTarget = rir,
            )
            "percent_1rm", "percent1rm", "1rm_percentage" -> ResolvedProgressionPolicy(
                id = "percent-1rm-v1",
                label = "$percent% estimated 1RM",
                strategy = ProgressionStrategy.PERCENT_1RM,
                source = source,
                minimumEffortMargin = 2.0,
                maximumLoadIncreaseRatio = 0.05,
                percent1RM = percent,
                rpeTarget = rpe,
                rirTarget = rir,
            )
            "rpe_rir", "rpe", "rir" -> ResolvedProgressionPolicy(
                id = "rpe-rir-progression-v1",
                label = "RPE/RIR progression",
                strategy = ProgressionStrategy.RPE_RIR,
                source = source,
                minimumEffortMargin = maxOf(rir.toDouble(), (10 - rpe).toDouble()),
                maximumLoadIncreaseRatio = 0.05,
                percent1RM = percent,
                rpeTarget = rpe,
                rirTarget = rir,
            )
            else -> null
        }
    }

    private fun globalPolicy(raw: String?): ResolvedProgressionPolicy? = when (raw.canonicalToken()) {
        "conservative", "linear" -> ResolvedProgressionPolicy(
            id = "conservative-double-progression-v1",
            label = "Conservative double progression",
            strategy = ProgressionStrategy.DOUBLE_PROGRESSION,
            source = ProgressionPolicySource.GLOBAL_SETTING,
            minimumEffortMargin = 2.0,
            maximumLoadIncreaseRatio = 0.05,
        )
        "balanced" -> ResolvedProgressionPolicy(
            id = "balanced-double-progression-v1",
            label = "Balanced double progression",
            strategy = ProgressionStrategy.DOUBLE_PROGRESSION,
            source = ProgressionPolicySource.GLOBAL_SETTING,
            minimumEffortMargin = 1.5,
            maximumLoadIncreaseRatio = 0.075,
        )
        "aggressive", "undulating" -> ResolvedProgressionPolicy(
            id = "aggressive-double-progression-v1",
            label = "Aggressive double progression",
            strategy = ProgressionStrategy.DOUBLE_PROGRESSION,
            source = ProgressionPolicySource.GLOBAL_SETTING,
            minimumEffortMargin = 1.0,
            maximumLoadIncreaseRatio = 0.10,
        )
        else -> null
    }

    private fun String?.canonicalToken(): String = this
        .orEmpty()
        .trim()
        .lowercase()
        .replace('%', ' ')
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
}

internal fun buildProgressionExplanationPrompt(
    exerciseName: String,
    recentWeightKg: Double,
    recentReps: Int,
    trend: String,
    policy: ResolvedProgressionPolicy,
): String {
    fun safe(value: String, max: Int): String = value
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(max)
    val effortRule = when (policy.strategy) {
        ProgressionStrategy.RPE_RIR -> "Use the saved RPE ${policy.rpeTarget} / RIR ${policy.rirTarget} target."
        ProgressionStrategy.PERCENT_1RM -> "Use the saved ${policy.percent1RM}% estimated-1RM model."
        ProgressionStrategy.LINEAR -> "Use linear progression guardrails."
        ProgressionStrategy.DOUBLE_PROGRESSION -> "Use double progression guardrails."
    }
    return """You are a concise personal trainer AI inside the IronLog workout app.
${safe(exerciseName, 120)}: recent working set ${recentWeightKg.coerceAtLeast(0.0)} kg × ${recentReps.coerceAtLeast(0)} reps; recorded trend: ${safe(trend, 80)}.
Resolved policy: ${policy.label}. Policy source: ${policy.source.label}. $effortRule
Do not invent missing RPE/RIR, technique quality, recovery, set completion, or equipment increments. Do not override the resolved policy.
In 1–2 sentences explain the next evidence to check and the policy-consistent option. Under 50 words. Plain text only."""
}
