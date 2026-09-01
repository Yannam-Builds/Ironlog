package com.ironlog.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ironlog.app.ui.components.ironLogSwitchPalette
import com.ironlog.app.ui.theme.IronLogThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IronLogMaterialColorSchemeTest {
    private val themes = listOf(
        IronLogThemes.BurntTerracotta,
        IronLogThemes.CrimsonSteel,
        IronLogThemes.DeepForest,
        IronLogThemes.ElectricLemon,
        IronLogThemes.MidnightTeal,
        IronLogThemes.ObsidianSilver,
        IronLogThemes.RoyalAmethyst,
        IronLogThemes.TitaniumBlue,
        IronLogThemes.Amoled,
        IronLogThemes.Dark,
        IronLogThemes.MonetFallback,
        IronLogThemes.Light,
    )

    @Test
    fun `every theme supplies opaque Material 3 popup surfaces`() {
        themes.forEach { theme ->
            val scheme = theme.toMaterialColorScheme()
            val popupSurfaces = listOf(
                scheme.surface,
                scheme.surfaceBright,
                scheme.surfaceDim,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainerLowest,
            )
            popupSurfaces.forEach { color ->
                assertNotEquals("${theme.name} left a popup role unspecified", Color.Unspecified, color)
                assertEquals("${theme.name} popup role is translucent", 1f, color.alpha, 0.001f)
            }
        }
    }

    @Test
    fun `off switch handle remains distinct from its track in every theme`() {
        themes.forEach { theme ->
            val palette = ironLogSwitchPalette(theme)
            val handleContrast = contrastRatio(
                palette.uncheckedThumb.luminance(),
                palette.uncheckedTrack.luminance(),
            )
            val borderContrast = contrastRatio(
                palette.uncheckedBorder.luminance(),
                palette.uncheckedTrack.luminance(),
            )

            assertTrue(
                "${theme.name} off switch handle blends into its track: $handleContrast:1",
                handleContrast >= 3f,
            )
            assertTrue(
                "${theme.name} off switch border blends into its track: $borderContrast:1",
                borderContrast >= 3f,
            )
        }
    }

    private fun contrastRatio(firstLuminance: Float, secondLuminance: Float): Float =
        (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
}
