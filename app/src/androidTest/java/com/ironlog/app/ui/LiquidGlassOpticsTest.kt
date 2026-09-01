package com.ironlog.app.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ironlog.app.navigation.LiquidGlassBackground
import com.ironlog.app.navigation.GlassLens
import com.ironlog.app.navigation.GlassRefraction
import com.ironlog.app.navigation.GLASS_DARK_MAX_LUMINANCE
import com.ironlog.app.navigation.GLASS_LIGHT_MIN_LUMINANCE
import com.ironlog.app.navigation.createLiquidGlassRefraction
import com.ironlog.app.navigation.liquidGlassTabInk
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogThemeTokens
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Optical acceptance uses actual backdrop pixels, not a source-code/shader-presence check. */
class LiquidGlassOpticsTest {
    @get:Rule val compose = createComposeRule()
    private var outsideBand by mutableStateOf(false)
    private var backdropPhase by mutableIntStateOf(0)
    private var palette by mutableStateOf("dark")
    private var pressFraction by mutableFloatStateOf(0f)
    private lateinit var resolvedColors: IronLogThemeTokens

    @Test fun unavailableRefractionUsesOpaqueCompatibilityMaterial() {
        render { null }
        val before = centerPixels()
        compose.runOnIdle { backdropPhase = 1 }
        assertArrayEquals("Missing shader must not leave a clear, unrefracted backdrop", before, centerPixels())
    }

    @Test fun effectFailureUsesOpaqueCompatibilityMaterial() {
        render { object : GlassRefraction {
            override fun effect(width: Float, height: Float, lens: GlassLens, density: Float,
                sampleMargin: Float, light: Boolean, press: Float): RenderEffect? = null
        } }
        val before = centerPixels()
        compose.runOnIdle { backdropPhase = 1 }
        assertArrayEquals("A failed effect must explicitly switch to the opaque material", before, centerPixels())
    }

    @Test fun protectedBackdropKeepsAllThemeControlsReadableIncludingPressedLens() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        render()
        val themes = listOf("dark", "amoled", "light", "monet", "obsidian_silver", "deep_forest",
            "titanium_blue", "royal_amethyst", "midnight_teal", "crimson_steel", "burnt_terracotta", "electric_lemon")
        for (theme in themes) for (pressure in listOf(0f, 1f)) {
            compose.runOnIdle { palette = theme; pressFraction = pressure }
            val values = centerPixels().map { Color(it).luminance() }
            val light = resolvedColors.tabBg.luminance() > .5f
            if (!light) assertTrue("Bright content must not overpower $theme controls: ${values.max()}",
                values.max() <= GLASS_DARK_MAX_LUMINANCE + .004f)
            else assertTrue("Dark content must not overpower $theme controls: ${values.min()}",
                values.min() >= GLASS_LIGHT_MIN_LUMINANCE - .004f)
            // Pair actual rendered background pixels (including the selected
            // lens) with quantized foreground ink throughout the color transition.
            for (step in 0..10) {
                val foreground = Color(liquidGlassTabInk(resolvedColors, step / 10f).toArgb()).luminance()
                val contrast = listOf(values.min(), values.max()).minOf { background ->
                    (maxOf(foreground, background) + .05f) / (minOf(foreground, background) + .05f)
                }
                assertTrue("$theme/$pressure/$step rendered text contrast=$contrast", contrast >= 4.5f)
            }
        }
    }

    @Test fun clearCenterKeepsFineBackdropStructure() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        render()
        val bitmap = image()
        val density = bitmap.height / 64f
        // Sample away from the selected lens, beveled perimeter and every foreground control.
        val y = (32 * density).toInt()
        val reds = (100..240).map { x -> android.graphics.Color.red(bitmap.getPixel((x * density).toInt(), y)) / 255f }
        val contrast = reds.max() - reds.min()
        assertTrue("The center should retain visible 12dp backdrop stripes; measured contrast=$contrast", contrast > .25f)
        capture("glass-optics-clear-center.png")
    }

    @Test fun curvedRimRefractsPixelsFromOutsideTheBar() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        render()
        val before = image()
        compose.runOnIdle { outsideBand = true }
        val after = image()
        val density = after.height / 64f
        // The red source band is wholly ABOVE the capsule, never painted underneath it.
        // A curved edge must pull it into a narrow, legible rim, not only diffuse it as blur.
        var largestRedChange = 0f
        for (x in 120..220) for (y in 1..7) {
            val px = (x * density).toInt()
            val py = (y * density).toInt()
            val change = (android.graphics.Color.red(after.getPixel(px, py)) -
                android.graphics.Color.red(before.getPixel(px, py))) / 255f
            largestRedChange = maxOf(largestRedChange, change)
        }
        assertTrue("The bevel should visibly sample the surrounding backdrop; delta=$largestRedChange", largestRedChange > .25f)
        capture("glass-optics-curved-rim.png")
    }

    private fun render(refractionFactory: () -> GlassRefraction? = ::createLiquidGlassRefraction) {
        compose.setContent {
            ThemeProvider(palette) { MaterialTheme(colorScheme = useTheme().toMaterialColorScheme()) {
                val haze = rememberHazeState()
                val colors = useTheme()
                SideEffect { resolvedColors = colors }
                Box(Modifier.width(320.dp).height(160.dp)) {
                    Canvas(Modifier.fillMaxSize().hazeSource(haze)) {
                        drawRect(Color(0xFF151922))
                        val barTop = 48.dp.toPx()
                        for (i in 0..28) {
                            drawRect(if ((i + backdropPhase) % 2 == 0) Color(0xFFF0DABC) else Color(0xFF192B43),
                                Offset(i * 12.dp.toPx(), barTop + 14.dp.toPx()), Size(12.dp.toPx(), 36.dp.toPx()))
                        }
                        if (outsideBand) drawRect(Color(0xFFFF160C),
                            Offset(0f, barTop - 10.dp.toPx()), Size(size.width, 8.dp.toPx()))
                    }
                    Box(Modifier.align(Alignment.Center).width(288.dp).height(64.dp).testTag("optical-bar")) {
                        LiquidGlassBackground(haze, colors, 0f, 0, 5, pressFraction, refractionFactory)
                    }
                }
            } }
        }
    }

    private fun image() = compose.onNodeWithTag("optical-bar").captureToImage().asAndroidBitmap()

    private fun centerPixels(): IntArray {
        val bitmap = image()
        val density = bitmap.height / 64f
        return ((14..42) + (100..240)).flatMap { x -> (18..45).map { y ->
            bitmap.getPixel((x * density).toInt(), (y * density).toInt())
        } }.toIntArray()
    }

    private fun capture(name: String) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.externalCacheDir, name).outputStream().use {
            image().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
