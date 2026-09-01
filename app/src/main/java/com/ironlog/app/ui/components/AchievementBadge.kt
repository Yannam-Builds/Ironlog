package com.ironlog.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ironlog.app.R
import com.ironlog.app.domain.badges.BadgeDefinition
import com.ironlog.app.domain.badges.BadgeTier

/**
 * Shared achievement artwork used by Home, Iron Ledger and the Achievement Atlas.
 * Each canonical badge owns a transparent high-resolution emblem; locked badges
 * retain their silhouette while using a desaturated, lower-emphasis treatment.
 */
@Composable
fun AchievementBadge(
    definition: BadgeDefinition,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val lockedFilter = if (unlocked) {
        null
    } else {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    Image(
        painter = painterResource(achievementBadgeDrawable(definition.id)),
        contentDescription = definition.title,
        contentScale = ContentScale.Fit,
        colorFilter = lockedFilter,
        modifier = modifier.alpha(if (unlocked) 1f else 0.34f),
    )
}

fun badgeTierColor(tier: BadgeTier): Color = when (tier) {
    BadgeTier.BRONZE -> Color(0xFFC66A3D)
    BadgeTier.SILVER -> Color(0xFFB7C1CA)
    BadgeTier.GOLD -> Color(0xFFFFC247)
    BadgeTier.BLUE -> Color(0xFF4D9FFF)
}

@DrawableRes
internal fun achievementBadgeDrawable(id: String): Int = when (id) {
    "first_workout" -> R.drawable.ic_badge_dumbbell
    "streak_3" -> R.drawable.ic_badge_flame
    "first_rest_timer" -> R.drawable.ic_badge_hourglass
    "first_plan" -> R.drawable.ic_badge_twin_dumbbells
    "workouts_10" -> R.drawable.ic_badge_lightning
    "consistency_4w" -> R.drawable.ic_badge_calendar
    "first_pr" -> R.drawable.ic_badge_flexed_arm
    "ai_activated" -> R.drawable.ic_badge_atom
    "progressive_streak" -> R.drawable.ic_badge_chart
    "workouts_50" -> R.drawable.ic_badge_trophy
    "streak_30" -> R.drawable.ic_badge_shield
    "workouts_100" -> R.drawable.ic_badge_crown
    "volume_milestone" -> R.drawable.ic_badge_mountain
    "member_365" -> R.drawable.ic_badge_infinity
    "all_goal_modes" -> R.drawable.ic_badge_3stars
    else -> R.drawable.ic_badge_dumbbell
}
