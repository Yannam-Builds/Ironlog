package com.ironlog.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.ironlog.app.ui.theme.IronLogThemes
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassPaletteTest {
    @Test fun `quantized ink remains readable at rendered material bounds`() {
        for (name in listOf("dark", "light", "monet", "crimson_steel", "electric_lemon")) {
            val colors = IronLogThemes.byName(name)
            // Eight-bit capture / GPU roundoff may straddle the shader's float limit.
            val background = if (colors.tabBg.luminance() > .5f) .716f else .094f
            for (step in 0..10) {
                val foreground = Color(liquidGlassTabInk(colors, step / 10f).toArgb()).luminance()
                val contrast = (maxOf(foreground, background) + .05f) / (minOf(foreground, background) + .05f)
                assertTrue("$name/$step needs rounding headroom, got $contrast", contrast >= 4.5f)
            }
        }
    }

    @Test fun `all native palettes keep readable ink over the protected live backdrop`() {
        val themes = listOf("dark", "amoled", "light", "monet", "obsidian_silver", "deep_forest",
            "titanium_blue", "royal_amethyst", "midnight_teal", "crimson_steel", "burnt_terracotta", "electric_lemon")
        for (name in themes) {
            val colors = IronLogThemes.byName(name)
            val light = colors.tabBg.luminance() > .5f
            val background = if (light) GLASS_LIGHT_MIN_LUMINANCE else GLASS_DARK_MAX_LUMINANCE
            for (step in 0..10) {
                val ink = liquidGlassTabInk(colors, step / 10f)
                val foreground = ink.luminance()
                val contrast = (maxOf(foreground, background) + .05f) / (minOf(foreground, background) + .05f)
                assertTrue("$name at selection $step needs 4.5:1 text contrast, got $contrast", contrast >= 4.5f)
            }
        }
    }
}
