package com.ironlog.app.domain.badges

enum class BadgeTier { BRONZE, SILVER, GOLD, BLUE }

/**
 * Snapshot of user progress used to evaluate badge unlock conditions.
 * Extend this as new metrics are tracked.
 */
data class AppStats(
    val totalWorkouts: Int = 0,
    val currentStreak: Int = 0,
    val daysSinceFirstWorkout: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val hasLoggedPR: Boolean = false,
    val usedRestTimer: Boolean = false,
    val createdPlan: Boolean = false,
    val cloudAiActivated: Boolean = false,
    val goalModesUsed: Set<String> = emptySet(),
    val currentRank: String = "E",
    val weeksConsistent: Int = 0,
    val consecutiveProgressionWorkouts: Int = 0,
)

/**
 * Describes an achievement badge.
 * Add new badges to [BadgeDefinitions.all] — the UI reads this list at runtime.
 * [iconResName] is the drawable resource name (e.g. "ic_badge_dumbbell").
 */
data class BadgeDefinition(
    val id: String,
    val title: String,
    val description: String,
    val tier: BadgeTier,
    val iconResName: String,
    val unlockCondition: (AppStats) -> Boolean,
)

object BadgeDefinitions {

    val all: List<BadgeDefinition> = listOf(
        // ── BRONZE ────────────────────────────────────────────────────────────
        BadgeDefinition(
            id = "first_workout", title = "Iron Initiate",
            description = "Complete your first workout", tier = BadgeTier.BRONZE,
            iconResName = "ic_badge_dumbbell",
            unlockCondition = { it.totalWorkouts >= 1 },
        ),
        BadgeDefinition(
            id = "streak_3", title = "Spark",
            description = "Achieve a 3-day workout streak", tier = BadgeTier.BRONZE,
            iconResName = "ic_badge_flame",
            unlockCondition = { it.currentStreak >= 3 },
        ),
        BadgeDefinition(
            id = "first_rest_timer", title = "Patience",
            description = "Use the rest timer for the first time", tier = BadgeTier.BRONZE,
            iconResName = "ic_badge_hourglass",
            unlockCondition = { it.usedRestTimer },
        ),
        BadgeDefinition(
            id = "first_plan", title = "Architect",
            description = "Create your first training plan", tier = BadgeTier.BRONZE,
            iconResName = "ic_badge_twin_dumbbells",
            unlockCondition = { it.createdPlan },
        ),
        // ── SILVER ────────────────────────────────────────────────────────────
        BadgeDefinition(
            id = "workouts_10", title = "Charged",
            description = "Complete 10 workouts", tier = BadgeTier.SILVER,
            iconResName = "ic_badge_lightning",
            unlockCondition = { it.totalWorkouts >= 10 },
        ),
        BadgeDefinition(
            id = "consistency_4w", title = "Clockwork",
            description = "Train consistently for 4 weeks", tier = BadgeTier.SILVER,
            iconResName = "ic_badge_calendar",
            unlockCondition = { it.weeksConsistent >= 4 },
        ),
        BadgeDefinition(
            id = "first_pr", title = "Muscle Memory",
            description = "Log your first personal record", tier = BadgeTier.SILVER,
            iconResName = "ic_badge_flexed_arm",
            unlockCondition = { it.hasLoggedPR },
        ),
        BadgeDefinition(
            id = "ai_activated", title = "Augmented",
            description = "Activate Cloud AI coaching", tier = BadgeTier.SILVER,
            iconResName = "ic_badge_atom",
            unlockCondition = { it.cloudAiActivated },
        ),
        BadgeDefinition(
            id = "progressive_streak", title = "Growth Curve",
            description = "Progress in 4 consecutive workouts", tier = BadgeTier.SILVER,
            iconResName = "ic_badge_chart",
            unlockCondition = { it.consecutiveProgressionWorkouts >= 4 },
        ),
        // ── GOLD ──────────────────────────────────────────────────────────────
        BadgeDefinition(
            id = "workouts_50", title = "Champion",
            description = "Complete 50 workouts", tier = BadgeTier.GOLD,
            iconResName = "ic_badge_trophy",
            unlockCondition = { it.totalWorkouts >= 50 },
        ),
        BadgeDefinition(
            id = "streak_30", title = "Ironclad",
            description = "Achieve a 30-day workout streak", tier = BadgeTier.GOLD,
            iconResName = "ic_badge_shield",
            unlockCondition = { it.currentStreak >= 30 },
        ),
        BadgeDefinition(
            id = "workouts_100", title = "Sovereign",
            description = "Complete 100 workouts", tier = BadgeTier.GOLD,
            iconResName = "ic_badge_crown",
            unlockCondition = { it.totalWorkouts >= 100 },
        ),
        BadgeDefinition(
            id = "volume_milestone", title = "Summit",
            description = "Lift 100,000 kg total volume", tier = BadgeTier.GOLD,
            iconResName = "ic_badge_mountain",
            unlockCondition = { it.totalVolumeKg >= 100_000.0 },
        ),
        // ── BLUE ──────────────────────────────────────────────────────────────
        BadgeDefinition(
            id = "member_365", title = "Eternal",
            description = "365 days since your first workout", tier = BadgeTier.BLUE,
            iconResName = "ic_badge_infinity",
            unlockCondition = { it.daysSinceFirstWorkout >= 365 },
        ),
        BadgeDefinition(
            id = "all_goal_modes", title = "Multiclass",
            description = "Train with all 3 available goal modes", tier = BadgeTier.BLUE,
            iconResName = "ic_badge_3stars",
            unlockCondition = { it.goalModesUsed.size >= 3 },
        ),
        BadgeDefinition(
            id = "s_rank", title = "Diamond",
            description = "Reach S-Rank status", tier = BadgeTier.BLUE,
            iconResName = "ic_badge_diamond",
            unlockCondition = { it.currentRank == "S" },
        ),
    )

    /** Returns the set of badge IDs that are unlocked for the given [stats]. */
    fun evaluate(stats: AppStats): Set<String> =
        all.filter { it.unlockCondition(stats) }.map { it.id }.toSet()
}
