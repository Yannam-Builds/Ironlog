package com.ironlog.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.screens.home.StartWorkoutCard
import com.ironlog.app.ui.screens.settings.CardShineSettingsCard
import com.ironlog.app.ui.theme.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardShineInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchStopsAndRestartsShineWithoutChangingCardSize() {
        val store = CardShineStore(object : CardShineStorage {
            var saved: Boolean? = null
            override fun read(): Any? = saved
            override fun write(enabled: Boolean) { saved = enabled }
        })
        val committedEnabled = AtomicReference<Boolean?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val enabled by store.enabled.collectAsState()
            CompositionLocalProvider(LocalCardShineEnabled provides enabled) {
                val appliedEnabled = LocalCardShineEnabled.current
                SideEffect { committedEnabled.set(appliedEnabled) }
                MaterialTheme {
                    ThemeProvider("Dark") {
                        Column(Modifier.width(320.dp)) {
                            CardShineSettingsCard(store)
                            Box(Modifier.testTag("shine-card")) {
                                StartWorkoutCard(
                                    goalModeLabel = "Hypertrophy", activePlanName = "Shine QA",
                                    avgDurationMin = 45,
                                    planDays = listOf(UiPlanDay(id = "legs", name = "Legs")),
                                    recommendedDayId = "legs",
                                )
                            }
                        }
                    }
                }
            }
        }
        awaitAppliedToggle(true, committedEnabled)
        val originalBounds = compose.onNodeWithTag("shine-card").getUnclippedBoundsInRoot()
        val initial = pixels()
        compose.mainClock.advanceTimeBy(1_500)
        assertFalse("Default-on shine must move", initial.contentEquals(pixels()))

        compose.onNodeWithText("Animated card shine").assertIsOn().performClick()
        awaitAppliedToggle(false, committedEnabled)
        compose.onNodeWithText("Animated card shine").assertIsOff()
        val off = pixels()
        compose.mainClock.advanceTimeBy(1_500)
        assertArrayEquals("Off must stop changing card pixels", off, pixels())
        assertEquals(originalBounds, compose.onNodeWithTag("shine-card").getUnclippedBoundsInRoot())

        compose.onNodeWithText("Animated card shine").performClick()
        awaitAppliedToggle(true, committedEnabled)
        val restarted = pixels()
        compose.mainClock.advanceTimeBy(1_500)
        assertFalse("Turning on must restart the shine", restarted.contentEquals(pixels()))
        assertEquals(originalBounds, compose.onNodeWithTag("shine-card").getUnclippedBoundsInRoot())
    }

    private fun awaitAppliedToggle(enabled: Boolean, committedEnabled: AtomicReference<Boolean?>) {
        // Persistence runs on IO. Observing its StateFlow alone does not mean
        // both Compose collectors have applied it or the save has finished.
        // Keep manual time: autoAdvance cancels infinite animations in tests.
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            val config = compose.onNodeWithText("Animated card shine").fetchSemanticsNode().config
            committedEnabled.get() == enabled &&
                config[SemanticsProperties.ToggleableState] == (if (enabled) ToggleableState.On else ToggleableState.Off) &&
                !config.contains(SemanticsProperties.Disabled)
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    @Test fun savedPreferenceSurvivesAReopenedPreferenceStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "card_shine_test_${UUID.randomUUID()}"
        try {
            fun reopen() = CardShineStore(SharedPreferencesCardShineStorage(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
            val first = reopen()
            assertTrue(first.enabled.value)
            first.update(false)
            val second = reopen()
            assertFalse(second.enabled.value)
            second.update(true)
            assertTrue(reopen().enabled.value)
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    private fun pixels(): IntArray {
        val image = compose.onNodeWithTag("shine-card").captureToImage()
        return IntArray(image.width * image.height).also { image.readPixels(it) }
    }
}
