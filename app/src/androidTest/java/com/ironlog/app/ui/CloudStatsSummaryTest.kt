package com.ironlog.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ironlog.app.ui.screens.stats.CloudStatsSummaryHost
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CloudStatsSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun keyOnlySaveInvalidatesComposedSummaryWithoutChangingProviderOrModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = "qa-key-" + java.util.UUID.randomUUID().toString()
        var calls = 0
        try {
            CloudAiKeyStore.save(context, provider, "synthetic-first")
            compose.setContent {
                val revision by CloudAiKeyStore.revision.collectAsState()
                val key = remember(revision) { CloudAiKeyStore.load(context, provider) }
                val summary = CloudStatsSummaryHost(true, key, load = {
                    calls++
                    if (key == "synthetic-second") "Updated credential" else "Original credential"
                })
                Text(summary.text ?: "Loading")
            }
            compose.waitForIdle()
            compose.onNodeWithText("Original credential").assertExists()
            compose.runOnIdle { CloudAiKeyStore.save(context, provider, "synthetic-second") }
            compose.waitForIdle()
            compose.onNodeWithText("Updated credential").assertExists()
            compose.runOnIdle { assertEquals(2, calls) }
        } finally { CloudAiKeyStore.clear(context, provider) }
    }

    @Test fun switchingToBuiltinCancelsRequestAndRemovesCard() {
        var enabled by mutableStateOf(true)
        var started = 0
        var cancelled = 0
        compose.setContent {
            val summary = CloudStatsSummaryHost(enabled, "synthetic", load = {
                started++
                try { awaitCancellation() } finally { cancelled++ }
            })
            if (enabled) Text(if (summary.loading) "Cloud loading" else "Cloud result")
        }
        compose.waitForIdle()
        compose.onNodeWithText("Cloud loading").assertExists()
        compose.runOnIdle { assertEquals(1, started); enabled = false }
        compose.waitForIdle()
        compose.onNodeWithText("Cloud loading").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, cancelled); enabled = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, started) }
    }

    @Test fun disabledModeNeverLoadsAndConfigurationChangeDiscardsOldSummary() {
        var enabled by mutableStateOf(false)
        var model by mutableStateOf("first")
        var calls = 0
        compose.setContent {
            val summary = CloudStatsSummaryHost(enabled, model, load = { calls++; "$model answer" })
            if (enabled) Text(summary.text ?: "Loading")
        }
        compose.runOnIdle { assertEquals(0, calls); enabled = true }
        compose.waitForIdle()
        compose.onNodeWithText("first answer").assertExists()
        compose.runOnIdle { model = "second" }
        compose.waitForIdle()
        compose.onNodeWithText("first answer").assertDoesNotExist()
        compose.onNodeWithText("second answer").assertExists()
        compose.runOnIdle { assertEquals(2, calls); enabled = false }
        compose.waitForIdle()
        compose.onNodeWithText("second answer").assertDoesNotExist()
    }
}
