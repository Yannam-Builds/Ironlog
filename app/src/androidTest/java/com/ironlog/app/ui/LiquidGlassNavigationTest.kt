package com.ironlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ironlog.app.navigation.IronLogTabBar
import com.ironlog.app.navigation.createLiquidGlassRefraction
import com.ironlog.app.navigation.liquidGlassLens
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.LocalLiquidGlassEnabled
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LiquidGlassNavigationTest {
    // Compose's test recomposer replaces the window recomposer. Supply the
    // platform's disabled-motion state explicitly instead of its default 1x clock.
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = if (android.animation.ValueAnimator.areAnimatorsEnabled()) 1f else 0f
    })
    private val tabs = listOf("Home", "Plans", "Log", "Stats", "Settings")
    private var enabled by mutableStateOf(false)
    private var selected by mutableIntStateOf(0)
    private var palette by mutableStateOf("dark")
    private var width by mutableIntStateOf(320)
    private var scale by mutableFloatStateOf(1f)
    private var backgroundClicks = 0
    private var blurEnabled by mutableStateOf(true)
    private var backdropPhase by mutableIntStateOf(0)
    private val drawCalls = java.util.concurrent.atomic.AtomicInteger()
    private var observedMotionScale = Float.NaN

    @Test fun effectDoesNotChangeGeometryAcrossThemesSizesAndFontScales() {
        render()
        val themes = listOf("dark", "amoled", "light", "monet", "obsidian_silver", "deep_forest",
            "titanium_blue", "royal_amethyst", "midnight_teal", "crimson_steel", "burnt_terracotta", "electric_lemon")
        for (theme in themes) for (screenWidth in listOf(320, 411)) for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { palette = theme; width = screenWidth; scale = fontScale; enabled = false }
            val before = bounds()
            assertEquals(64f, (before.first().bottom - before.first().top).value, .1f)
            compose.runOnIdle { enabled = true }
            assertEquals("Glass must not change geometry: $theme/$screenWidth/$fontScale", before, bounds())
        }
    }

    @Test fun glassChangesPixelsButOffRemovesEffectAndSettledFramesStopChanging() {
        compose.mainClock.autoAdvance = false
        render()
        compose.mainClock.advanceTimeBy(32)
        val original = pixels()
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("liquid-glass-background").assertExists()
        assertFalse("Glass must visibly change the original solid bar", original.contentEquals(pixels()))
        capture("glass-navigation-dark.png")
        compose.onNodeWithTag("nav-tab-4").performClick()
        compose.mainClock.advanceTimeBy(96)
        capture("glass-navigation-travel.png")
        val travelling = pixels()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("nav-tab-4").assertIsSelected()
        assertFalse(travelling.contentEquals(pixels()))
        val settled = pixels()
        compose.mainClock.advanceTimeBy(2_000)
        assertArrayEquals("No perpetual decorative animation", settled, pixels())
        compose.runOnIdle { enabled = false; selected = 0 }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("liquid-glass-background").assertDoesNotExist()
        assertArrayEquals("Off restores original pixels", original, pixels())
    }

    @Test fun iconsRemain21dpAndTabsBlockUnderlyingClicks() {
        enabled = true
        render()
        for (i in tabs.indices) {
            val icon = compose.onNodeWithContentDescription(tabs[i], useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(21f, (icon.right - icon.left).value, .1f)
            assertEquals(21f, (icon.bottom - icon.top).value, .1f)
            compose.onNodeWithTag("nav-tab-$i").performTouchInput { click() }
            compose.onNodeWithTag("nav-tab-$i").assertIsSelected()
            compose.runOnIdle { assertEquals(i, selected); assertEquals(0, backgroundClicks) }
        }
        compose.runOnIdle { palette = "light" }
        capture("glass-navigation-light.png")
    }

    @Test fun shaderCompilesOnSupportedEmulator() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val refraction = createLiquidGlassRefraction()
            assertNotNull("AGSL must compile, not silently use fallback", refraction)
            assertNotNull("Uniform and effect construction must also succeed",
                refraction!!.effect(840f, 168f, liquidGlassLens(840f, 168f, 5, 2f, 2, 2.625f), 2.625f))
        }
    }

    @Test fun noBlurFallbackIsOpaqueAndReadable() {
        blurEnabled = false
        enabled = true
        render()
        val before = pixels(insideCapsuleOnly = true)
        compose.runOnIdle { backdropPhase = 1 }
        assertArrayEquals("Fallback must not show unblurred content through the bar", before, pixels(insideCapsuleOnly = true))
        tabs.indices.forEach { compose.onNodeWithTag("nav-tab-$it").assertIsDisplayed() }
        capture("glass-navigation-fallback.png")
    }

    @Test fun liveGlassUpdatesWhenBackdropChanges() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 32)
        enabled = true
        render()
        val before = pixels(insideCapsuleOnly = true)
        compose.runOnIdle { backdropPhase = 1 }
        assertFalse("Glass must sample live backdrop pixels, not just paint a gradient",
            before.contentEquals(pixels(insideCapsuleOnly = true)))
    }

    @Test fun enabledAtStartupDoesNotContinuouslyRedrawWhileIdle() {
        enabled = true
        render()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        // Observe native draws, not just equal screenshots: Haze 1.6.7 repeatedly
        // redrew identical frames when source and effect attached together.
        val start = android.os.SystemClock.elapsedRealtime()
        val before = drawCalls.get()
        compose.waitUntil(2_000) { android.os.SystemClock.elapsedRealtime() - start >= 600 }
        assertTrue("Idle glass must not keep the GPU drawing", drawCalls.get() - before <= 3)
    }

    @Test fun systemDisabledAnimationsSettleWithoutTravel() {
        org.junit.Assume.assumeFalse(android.animation.ValueAnimator.areAnimatorsEnabled())
        enabled = true
        compose.mainClock.autoAdvance = false
        render()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals("Test recomposer must use disabled system motion", 0f, observedMotionScale, 0f) }
        compose.runOnIdle { selected = 4 }
        compose.mainClock.advanceTimeBy(64)
        val immediatelySelected = pixels()
        compose.mainClock.advanceTimeBy(3_000)
        assertArrayEquals("System animations off must snap the lens", immediatelySelected, pixels())
    }

    private fun render() {
        compose.setContent {
            val effects = rememberCoroutineScope()
            SideEffect { observedMotionScale = effects.coroutineContext[MotionDurationScale]?.scaleFactor ?: -1f }
            val view = LocalView.current
            DisposableEffect(view) {
                val listener = android.view.ViewTreeObserver.OnDrawListener { drawCalls.incrementAndGet() }
                view.viewTreeObserver.addOnDrawListener(listener)
                onDispose { view.viewTreeObserver.removeOnDrawListener(listener) }
            }
            CompositionLocalProvider(LocalLiquidGlassEnabled provides enabled,
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                ThemeProvider(palette) { MaterialTheme(colorScheme = useTheme().toMaterialColorScheme()) {
                    val haze = rememberHazeState(blurEnabled)
                    Box(Modifier.width(width.dp).height(160.dp).background(useTheme().bg)) {
                        Canvas(Modifier.fillMaxSize().hazeSource(haze).clickable { backgroundClicks++ }) {
                            drawRect(Color(0xFF213E67))
                            for (i in 0..12) drawRect(if ((i + backdropPhase) % 2 == 0) Color(0xFFEA6640) else Color(0xFF8FAAD6),
                                topLeft = Offset(i * size.width / 12, 0f), size = Size(size.width / 24, size.height))
                        }
                        IronLogTabBar(selected, { selected = it }, haze,
                            Modifier.align(Alignment.Center).padding(horizontal = 16.dp).testTag("navigation"))
                    }
                } }
            }
        }
    }

    private fun bounds() = listOf(compose.onNodeWithTag("navigation").getUnclippedBoundsInRoot()) +
        tabs.indices.map { compose.onNodeWithTag("nav-tab-$it").getUnclippedBoundsInRoot() } +
        tabs.map { compose.onNodeWithText(it, useUnmergedTree = true).getUnclippedBoundsInRoot() }

    private fun pixels(insideCapsuleOnly: Boolean = false): IntArray {
        val image = compose.onNodeWithTag("navigation").captureToImage()
        val values = IntArray(image.width * image.height).also { image.readPixels(it) }
        if (!insideCapsuleOnly) return values
        // Outside the capsule is intentionally transparent, including its AA fringe.
        val radius = image.height / 2f
        val straightHalf = (image.width - image.height) / 2f
        return values.filterIndexed { index, _ ->
            val dx = (kotlin.math.abs(index % image.width + .5f - image.width / 2f) - straightHalf).coerceAtLeast(0f)
            val dy = index / image.width + .5f - radius
            dx * dx + dy * dy < (radius - 2f) * (radius - 2f)
        }.toIntArray()
    }

    private fun capture(name: String) {
        val image = compose.onNodeWithTag("navigation").captureToImage().asAndroidBitmap()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.externalCacheDir, name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
