package com.ironlog.app.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.LocalLiquidGlassEnabled
import com.ironlog.app.ui.theme.Text
import dev.chrisbanes.haze.HazeState

// Drawing-only upgrade: layout constants and icon/label styles match the original bar.
@Composable
internal fun IronLogTabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors   = useTheme()
    val glassEnabled = LocalLiquidGlassEnabled.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(140), label = "glass_press")
    val tabCount = TabScreens.size
    val barHeight = 64.dp
    val pillPadH  = 5.dp
    val pillPadV  = 5.dp

    // Animated slide: 0f = first tab, (tabCount-1).toFloat() = last tab.
    // Spring is slightly underdamped so it overshoots and bounces back — the
    // liquid-glass feel the user asked for.
    val pillOffset by animateFloatAsState(
        targetValue  = selectedIndex.toFloat(),
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.82f), // subtle glide, no bounce
        label        = "pill_slide",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(if (glassEnabled) Color.Transparent else colors.tabBg),
    ) {
        val tabWidth = maxWidth / tabCount

        if (glassEnabled) {
            LiquidGlassBackground(hazeState, colors, pillOffset, selectedIndex, tabCount, press)
        } else {
            // Original appearance is retained when glass is off.
            Box(
                Modifier
                    .absoluteOffset(
                        x = tabWidth * pillOffset + pillPadH,
                        y = pillPadV,
                    )
                    .size(
                        width  = tabWidth - pillPadH * 2,
                        height = barHeight - pillPadV * 2,
                    )
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.10f),
                            )
                        )
                    )
            )
        }

        // ── Tab columns (drawn on top of pill)
        Row(Modifier.fillMaxSize()) {
            TabScreens.forEachIndexed { index, tab ->
                val selected = index == selectedIndex

                // Independent tint spring — settles a touch later than the pill
                // so the colour shift trails the glass, mimicking a light-leak.
                val tintFraction by animateFloatAsState(
                    targetValue  = if (selected) 1f else 0f,
                    animationSpec = spring(stiffness = 360f, dampingRatio = 0.80f),
                    label        = "tint_$index",
                )
                val tint = if (glassEnabled) liquidGlassTabInk(colors, tintFraction)
                    else lerp(colors.muted, colors.accent, tintFraction)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("nav-tab-$index")
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onTabSelected(index) },
                        ),
                    horizontalAlignment  = Alignment.CenterHorizontally,
                    // The 1dp gap keeps icon and label tightly together as one unit,
                    // then Arrangement.Center places that unit in the middle of the 64dp bar
                    verticalArrangement  = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        imageVector        = tab.icon,
                        contentDescription = tab.name,
                        tint               = tint,
                        modifier           = Modifier.size(21.dp),
                    )
                    Text(
                        text          = tab.name,
                        color         = tint,
                        fontSize      = 12.sp,
                        lineHeight    = 12.sp,  // readable metadata while preserving the 64dp bar geometry
                        fontWeight    = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines      = 1,
                        letterSpacing = 0.1.sp,
                    )
                }
            }
        }
    }
}
