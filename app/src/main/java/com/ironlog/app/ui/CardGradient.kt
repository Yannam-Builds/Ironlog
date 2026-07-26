package com.ironlog.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Returns a [Brush] that slowly sweeps a diagonal gradient highlight across the card — the
 * "live wallpaper" breathing effect.  Works for any theme because the only input is [accent]
 * which is already the current-theme accent colour.
 *
 * The sweep period is 5 s (forward) + 5 s (reverse) = 10 s full cycle, which feels organic
 * without being distracting during a workout.
 */
@Composable
fun rememberAnimatedCardBrush(accent: Color): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "cardGradient")
    val shift by infiniteTransition.animateFloat(
        initialValue = -600f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradientShift",
    )
    return Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to accent.copy(alpha = 0.03f),
            0.35f to accent.copy(alpha = 0.20f),
            0.65f to accent.copy(alpha = 0.28f),
            1.00f to accent.copy(alpha = 0.04f),
        ),
        start = Offset(shift, 0f),
        end = Offset(shift + 900f, Float.POSITIVE_INFINITY),
    )
}
