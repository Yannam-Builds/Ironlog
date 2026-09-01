package com.ironlog.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.components.SetRow
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.buildIronLogTypography
import com.ironlog.app.ui.theme.TypographySelection
import com.ironlog.app.ui.state.LoggedSet
import com.ironlog.app.ui.state.WorkoutAction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactSetRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun assistedBodyweightUsesAssistanceRatherThanAddedLoadWording() {
        compose.setContent {
            ThemeProvider("dark") { MaterialTheme {
                SetRow(LoggedSet(id = "assisted-set", weight = 20.0, reps = 8.0), 0, 0, {}, "off", false,
                    weightUnit = "kg", trackingType = "assisted_bodyweight")
            } }
        }
        compose.onNodeWithText("BW − 20 kg × 8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.onNode(hasSetTextAction() and hasText("ASSISTANCE (KG)")).assertIsDisplayed()
    }

    @Test fun poundsEditStartsWithDisplayUnitsInsteadOfStoredKilograms() {
        compose.setContent {
            ThemeProvider("dark") { MaterialTheme {
                SetRow(LoggedSet(id = "pounds-set", weight = 100.0, reps = 8.0), 0, 0, {}, "off", false, "lbs")
            } }
        }
        compose.onNodeWithContentDescription("Edit").performClick()
        val field = compose.onNode(hasSetTextAction() and hasText("LBS"))
        val values = field.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.toDouble()
        assertEquals(220.462, values, 0.01)
    }

    @Test fun loggedSetUsesCompactChipsInsteadOfPermanentEffortTextFields() {
        render()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        val bounds = compose.onNodeWithTag("set-row").getUnclippedBoundsInRoot()
        assertTrue("Normal set row should be compact", bounds.bottom - bounds.top <= 112.dp)
        compose.onNodeWithContentDescription("RIR for set 1").assertHasClickAction()
        compose.onNodeWithContentDescription("RPE for set 1").assertHasClickAction()
        capture("compact-set-normal.png")
    }

    @Test fun effortSavesOnlyOnConfirmationAndCanBeCleared() {
        val actions = mutableListOf<WorkoutAction>()
        render(actions = actions)
        compose.onNodeWithContentDescription("RIR for set 1").performClick()
        compose.onNodeWithContentDescription("RIR value").performTextReplacement("2")
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(WorkoutAction.SetRir(0, 0, 2), actions.single()) }
        compose.onNodeWithText("RIR 2").assertExists()
        compose.onNodeWithContentDescription("RIR for set 1").performClick()
        compose.onNodeWithContentDescription("RIR value").performTextClearance()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(WorkoutAction.SetRir(0, 0, null), actions.last()) }
        compose.onNodeWithContentDescription("RPE for set 1").performClick()
        compose.onNodeWithContentDescription("RPE value").performTextReplacement("7.5")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(WorkoutAction.SetRpe(0, 0, 7.5), actions.last()) }
    }

    @Test fun controlsStaySeparateAndTappableAtNarrowWidthWithLargeText() {
        render(fontScale = 2f, width = 272)
        val nodes = listOf("RIR for set 1", "RPE for set 1", "Note", "Edit", "Delete")
            .map { compose.onNodeWithContentDescription(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
        nodes.forEach { assertTrue(it.right - it.left >= 48.dp); assertTrue(it.bottom - it.top >= 48.dp) }
        for (i in nodes.indices) for (j in i + 1 until nodes.size) {
            val a = nodes[i]; val b = nodes[j]
            assertFalse("Controls overlap", a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom)
        }
        capture("compact-set-large-text.png")
    }

    @Test fun invalidEffortCannotSaveAndCancelDoesNotMutate() {
        val actions = mutableListOf<WorkoutAction>()
        render(actions = actions)
        compose.onNodeWithContentDescription("RIR for set 1").performClick()
        compose.onNodeWithContentDescription("RIR value").performTextReplacement("2.5")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithContentDescription("RIR for set 1").performClick()
        compose.onNodeWithContentDescription("RIR value").assertTextEquals("RIR", "")
    }

    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("set-row").captureToImage().asAndroidBitmap()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.externalCacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun render(fontScale: Float = 1f, width: Int = 288, actions: MutableList<WorkoutAction> = mutableListOf()) {
        compose.setContent {
            var set by remember { mutableStateOf(LoggedSet(id = "synthetic-set", weight = 10.0, reps = 12.0, orm = 14.0)) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ThemeProvider("Dark") { MaterialTheme(colorScheme = useTheme().toMaterialColorScheme(),
                    typography = buildIronLogTypography(TypographySelection())) {
                    Box(Modifier.width(width.dp).background(useTheme().card).testTag("set-row")) {
                        SetRow(set, 0, 0, dispatch = { action ->
                            actions += action
                            set = when (action) {
                                is WorkoutAction.SetRir -> set.copy(rir = action.rir)
                                is WorkoutAction.SetRpe -> set.copy(rpe = action.rpe)
                                else -> set
                            }
                        }, effortTracking = "both", hapticFeedback = false, trackingType = "bodyweight_weight_reps")
                    }
                } }
            }
        }
    }
}
