package com.ironlog.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.ironlog.app.ui.theme.IronLogThemeTokens

// Leave enough tonal range to see refracted fine detail; glass ink is lifted to
// match this limit rather than making the backdrop increasingly muddy.
internal const val GLASS_DARK_MAX_LUMINANCE = .09f
internal const val GLASS_LIGHT_MIN_LUMINANCE = .72f

/** Foreground ink for the glass material; geometry and icon glyphs are unchanged. */
internal fun liquidGlassTabInk(colors: IronLogThemeTokens, selectedFraction: Float): Color {
    val fraction = (selectedFraction.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f)
    // Correct the animated color, not just endpoints: interpolation can reduce
    // luminance. Opaque ink also avoids Monet's muted alpha weakening contrast.
    val preferred = lerp(colors.muted, colors.accent, fraction).copy(alpha = 1f)
    val light = colors.tabBg.luminance() > .5f
    val backdrop = if (light) GLASS_LIGHT_MIN_LUMINANCE else GLASS_DARK_MAX_LUMINANCE
    fun readable(color: Color): Boolean {
        val ink = color.luminance()
        // Reserve headroom for eight-bit GPU capture / display quantization.
        return (maxOf(ink, backdrop) + .05f) / (minOf(ink, backdrop) + .05f) >= 4.7f
    }
    if (readable(preferred)) return preferred
    val limit = if (light) Color.Black else Color.White
    var low = 0f
    var high = 1f
    repeat(12) {
        val middle = (low + high) / 2
        if (readable(lerp(preferred, limit, middle))) high = middle else low = middle
    }
    return lerp(preferred, limit, high)
}
