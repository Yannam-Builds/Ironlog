package com.ironlog.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.screens.home.StartWorkoutCard
import com.ironlog.app.ui.theme.LocalSpacingScale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeWorkoutPillLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pillsAndCardKeepTheirSizeWhenRecommendationScrollsAway() = checkLayout(1f, 1f)

    @Test fun equalSizesSurviveLargeTextAndSpaciousLayout() = checkLayout(2f, 1.25f)

    @Test
    fun startWithoutPlanDoesNotStartTheHighlightedProgramDay() {
        var highlightedDayStarts = 0
        var emptyWorkoutStarts = 0
        compose.setContent {
            MaterialTheme {
                ThemeProvider("dark") {
                    Box(Modifier.width(320.dp)) {
                        StartWorkoutCard(
                            goalModeLabel = "Hypertrophy",
                            activePlanName = "Synthetic plan",
                            avgDurationMin = 60,
                            planDays = listOf(UiPlanDay(id = "highlighted-day", name = "Push")),
                            recommendedDayId = "highlighted-day",
                            onClick = { highlightedDayStarts += 1 },
                            onStartEmptyWorkout = { emptyWorkoutStarts += 1 },
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("START WITHOUT PLAN").performClick()

        compose.runOnIdle {
            assertEquals(
                "Quick start must not invoke the highlighted plan-day action",
                0,
                highlightedDayStarts,
            )
            assertEquals("Quick start must request one empty workout", 1, emptyWorkoutStarts)
        }
    }

    private fun checkLayout(fontScale: Float, spacing: Float) {
        var cardHeight = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalSpacingScale provides spacing,
            ) {
                MaterialTheme {
                    ThemeProvider("royal_amethyst") {
                        Box(Modifier.verticalScroll(rememberScrollState())) {
                            Box(Modifier.width(320.dp).onSizeChanged { cardHeight = it.height }) {
                                StartWorkoutCard(
                                    goalModeLabel = "Hypertrophy",
                                    activePlanName = "Synthetic carousel QA",
                                    avgDurationMin = 60,
                                    planDays = listOf("Legs", "Push", "Pull", "Upper", "Lower").map {
                                        UiPlanDay(id = it, name = it)
                                    },
                                    recommendedDayId = "Legs",
                                )
                            }
                        }
                    }
                }
            }
        }
        val recommended = compose.onNodeWithText("LEGS").getUnclippedBoundsInRoot()
        val ordinary = compose.onNodeWithText("PUSH").getUnclippedBoundsInRoot()
        assertEquals("Pill widths", (recommended.right - recommended.left).value, (ordinary.right - ordinary.left).value, 0.5f)
        assertEquals("Pill heights", (recommended.bottom - recommended.top).value, (ordinary.bottom - ordinary.top).value, 0.5f)
        var originalHeight = 0
        compose.runOnIdle { originalHeight = cardHeight }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        val last = compose.onNodeWithText("LOWER").getUnclippedBoundsInRoot()
        assertEquals("Offscreen recommendation must not affect height", (recommended.bottom - recommended.top).value, (last.bottom - last.top).value, 0.5f)
        compose.runOnIdle { assertEquals("Outer workout card height after scroll", originalHeight, cardHeight) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.runOnIdle { assertEquals("Outer workout card height after return", originalHeight, cardHeight) }
        compose.onAllNodesWithText("⚡ Best Today").assertCountEquals(0)
    }
}
