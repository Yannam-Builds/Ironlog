package com.ironlog.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.seed.ProgramTemplate
import com.ironlog.app.domain.intelligence.ProgramRecommendation
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.screens.onboarding.steps.Step4Quota
import com.ironlog.app.ui.screens.plans.PlanExerciseMoveActions
import com.ironlog.app.ui.screens.plans.ProgramDetailsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanAndOnboardingAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun weekdaysHaveDistinctNamesStatesAndFortyEightDpTargetsAtLargeFont() {
        content(fontScale = 2f) {
            var days by remember { mutableStateOf(setOf(0)) }
            Column(Modifier.width(320.dp).height(460.dp)) {
                Step4Quota(days, { day -> days = if (day in days) days - day else days + day }, "kg", {}, {})
            }
        }
        val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        weekdays.forEach { day ->
            compose.onNodeWithContentDescription(day).performScrollTo().assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        }
        compose.onNodeWithContentDescription("Tuesday").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Thursday").assertIsOff()
        compose.onNodeWithContentDescription("Saturday").assertIsOff()
        compose.onNodeWithContentDescription("Sunday").assertIsOff()
    }

    @Test fun finalTrainingDayCannotBeDeselected() {
        var toggles = 0
        content {
            Step4Quota(setOf(0), { toggles++ }, "kg", {}, {})
        }
        compose.onNodeWithContentDescription("Monday").performScrollTo().assertIsOn().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(0, toggles) }
    }

    @Test fun moveActionsExposeBoundaryAndPendingDisabledStates() {
        var moves = 0
        var pending by mutableStateOf(false)
        content {
            Column {
                PlanExerciseMoveActions(canMoveUp = false, canMoveDown = true, isSaving = pending,
                    onMoveUp = { moves-- }, onMoveDown = { moves++ })
            }
        }
        compose.onNodeWithText("Move up").assertIsNotEnabled()
        compose.onNodeWithText("Move down").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, moves); pending = true }
        compose.onNodeWithText("Move down").assertIsNotEnabled()
    }

    @Test fun longProgramDetailsAndAddRemainReachableInShortLargeFontViewport() {
        var adds = 0
        val recommendation = ProgramRecommendation(
            template = ProgramTemplate("synthetic-program", "Synthetic long program title for wrapping",
                "Strength", "Synthetic explanatory program description. ".repeat(10),
                List(7) { FullPlanDay(name = "Training day ${it + 1} with a long synthetic name") }),
            score = 0, reason = "Synthetic recommendation rationale. ".repeat(5),
            experienceLabel = "Beginner", sessionLengthLabel = "45 min", equipmentLabel = "Gym",
        )
        content(fontScale = 2f) {
            ProgramDetailsContent(recommendation, onAdd = { adds++ },
                modifier = Modifier.width(320.dp).height(260.dp))
        }
        compose.onNodeWithText("Training day 7 with a long synthetic name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ADD TO MY PLANS").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, adds) }
    }

    private fun content(fontScale: Float = 1f, block: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ThemeProvider(themeName = "dark") { MaterialTheme { block() } }
            }
        }
    }
}
