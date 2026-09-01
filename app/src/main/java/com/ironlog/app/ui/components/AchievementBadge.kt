package com.ironlog.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.badges.BadgeDefinition
import com.ironlog.app.domain.badges.BadgeTier

/**
 * Scalable artwork for app achievements. The previous UI reduced every badge to
 * two letters or a diamond character, even though each definition has a distinct
 * visual identity. Keeping the artwork native also makes locked, dark-theme and
 * compact Home variants readable without maintaining many raster sizes.
 */
@Composable
fun AchievementBadge(
    definition: BadgeDefinition,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val tierColor = badgeTierColor(definition.tier)
    val contentColor = if (unlocked) tierColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.alpha(if (unlocked) 1f else 0.48f),
        shape = CircleShape,
        color = if (unlocked) tierColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, contentColor.copy(alpha = if (unlocked) 0.82f else 0.35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .border(1.dp, contentColor.copy(alpha = 0.3f), CircleShape)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = achievementIcon(definition.id),
                contentDescription = definition.title,
                tint = contentColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun badgeTierColor(tier: BadgeTier): Color = when (tier) {
    BadgeTier.BRONZE -> Color(0xFFC66A3D)
    BadgeTier.SILVER -> Color(0xFFB7C1CA)
    BadgeTier.GOLD -> Color(0xFFFFC247)
    BadgeTier.BLUE -> Color(0xFF4D9FFF)
}

private fun achievementIcon(id: String): ImageVector = when (id) {
    "first_workout" -> Icons.Filled.FitnessCenter
    "streak_3" -> Icons.Filled.LocalFireDepartment
    "first_rest_timer" -> Icons.Filled.HourglassBottom
    "first_plan" -> Icons.Filled.AccountTree
    "workouts_10" -> Icons.Filled.Bolt
    "consistency_4w" -> Icons.Filled.CalendarMonth
    "first_pr" -> Icons.AutoMirrored.Filled.TrendingUp
    "ai_activated" -> Icons.Filled.AutoAwesome
    "progressive_streak" -> Icons.AutoMirrored.Filled.ShowChart
    "workouts_50" -> Icons.Filled.EmojiEvents
    "streak_30" -> Icons.Filled.Security
    "workouts_100" -> Icons.Filled.WorkspacePremium
    "volume_milestone" -> Icons.Filled.Landscape
    "member_365" -> Icons.Filled.AllInclusive
    "all_goal_modes" -> Icons.Filled.Stars
    "s_rank" -> Icons.Filled.Diamond
    else -> Icons.Filled.FitnessCenter
}
