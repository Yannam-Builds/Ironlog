package com.ironlog.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ironlog.app.domain.intelligence.ManualRecoveryInput
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.screens.recovery.ManualRecoveryCheckInModal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecoveryCheckInUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun controlsHaveNamedSelectedStatesAndSaveFailureKeepsEdits() {
        var saved: ManualRecoveryInput? = null
        var savedFlags = emptySet<String>()
        compose.setContent {
            ThemeProvider { MaterialTheme {
                var error by remember { mutableStateOf<String?>(null) }
                ManualRecoveryCheckInModal(ManualRecoveryInput(2, 3, 4), painRegions = listOf("Push"),
                    error = error, onDismiss = {}, onSave = { input, flags -> saved = input; savedFlags = flags; error = "Could not save. Try again." })
            } }
        }
        compose.onNodeWithContentDescription("Soreness (1-5), 2").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Soreness (1-5), 4").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Pain flag Push").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.onNodeWithText("Could not save. Try again.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(4, saved?.soreness); assertEquals(setOf("Push"), savedFlags) }
        compose.onNodeWithContentDescription("Soreness (1-5), 4").performScrollTo().assertIsSelected()
    }
    @Test fun pendingSaveDisablesAllEditableInputsAndDismissal() {
        var closed = false
        compose.setContent { ThemeProvider { MaterialTheme {
            ManualRecoveryCheckInModal(ManualRecoveryInput(2, 3, 4), painRegions = listOf("Push"),
                saving = true, onDismiss = { closed = true }, onSave = { _, _ -> fail("Duplicate save") })
        } } }
        compose.onNodeWithContentDescription("Soreness (1-5), 2").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Pain flag Push").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Saving…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.runOnIdle { assertFalse(closed) }
    }
}
