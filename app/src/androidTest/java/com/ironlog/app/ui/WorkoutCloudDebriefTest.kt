package com.ironlog.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.screens.workout.WorkoutCloudDebrief
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkoutCloudDebriefTest {
    @get:Rule val compose = createComposeRule()

    @Test fun builtInNeverRendersOrRequestsDespiteSavedCredentials() {
        var mode by mutableStateOf("training_intelligence")
        var configured by mutableStateOf(true)
        var calls = 0
        compose.setContent {
            MaterialTheme { ThemeProvider("Dark") {
                WorkoutCloudDebrief(mode, configured, "synthetic", load = { calls++; "Synthetic debrief" })
            } }
        }
        compose.onNodeWithText("AI SESSION DEBRIEF").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls); mode = "apex_engine" }
        compose.onNodeWithText("AI SESSION DEBRIEF").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls); mode = "cloud_ai"; configured = false }
        compose.onNodeWithText("AI SESSION DEBRIEF").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls); configured = true }
        compose.onNodeWithText("Synthetic debrief").assertExists()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun switchingModeCancelsAndReopeningStartsFresh() {
        var mode by mutableStateOf("cloud_ai")
        var calls = 0
        var cancelled = 0
        compose.setContent {
            MaterialTheme { ThemeProvider("Dark") {
                WorkoutCloudDebrief(mode, true, "synthetic", load = {
                    calls++
                    try { awaitCancellation() } finally { cancelled++ }
                })
            } }
        }
        compose.onNodeWithText("Preparing your debrief…").assertExists()
        compose.runOnIdle { mode = "training_intelligence" }
        compose.onNodeWithText("AI SESSION DEBRIEF").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, cancelled); mode = "cloud_ai" }
        compose.onNodeWithText("Preparing your debrief…").assertExists()
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun blankAnswerExplainsFailureAndOffersRetry() {
        var calls = 0
        compose.setContent {
            MaterialTheme { ThemeProvider("Dark") {
                WorkoutCloudDebrief("cloud_ai", true, "synthetic", load = {
                    calls++
                    if (calls == 1) "  " else "Retry succeeded"
                })
            } }
        }
        compose.onNodeWithText("No debrief available. You can still save this workout.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Retry succeeded").assertExists()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }
}
