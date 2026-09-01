package com.ironlog.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.screens.settings.LiquidGlassSettingsCard
import com.ironlog.app.ui.theme.CardShineStore
import com.ironlog.app.ui.theme.LiquidGlassStorage
import com.ironlog.app.ui.theme.LiquidGlassStore
import com.ironlog.app.ui.theme.SharedPreferencesCardShineStorage
import com.ironlog.app.ui.theme.SharedPreferencesLiquidGlassStorage
import java.io.IOException
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiquidGlassPreferenceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preferenceReloadsWithoutChangingCardShineInTheSameFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "liquid_glass_test_${UUID.randomUUID()}"
        try {
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            fun reopen() = LiquidGlassStore(SharedPreferencesLiquidGlassStorage(preferences))
            val shine = CardShineStore(SharedPreferencesCardShineStorage(preferences))
            val glass = reopen()
            assertTrue(glass.enabled.value)
            glass.update(false)
            assertFalse(reopen().enabled.value)
            assertTrue(shine.enabled.value)
            assertFalse(preferences.getBoolean("liquid_glass_navigation", true))
            assertFalse(preferences.contains("animated_card_shine"))
            shine.update(false)
            glass.update(true)
            assertTrue(reopen().enabled.value)
            assertFalse(CardShineStore(SharedPreferencesCardShineStorage(preferences)).enabled.value)
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    @Test fun fullRowIsAnAccessibleSwitchAndFailedSaveIsVisibleAndRetryable() {
        val storage = object : LiquidGlassStorage {
            var saved = true
            @Volatile var fail = true
            override fun read(): Any? = saved
            override fun write(enabled: Boolean) {
                if (fail) throw IOException("Full storage")
                saved = enabled
            }
        }
        val store = LiquidGlassStore(storage)
        compose.setContent {
            MaterialTheme {
                ThemeProvider("Dark") {
                    Column(Modifier.width(320.dp)) { LiquidGlassSettingsCard(store) }
                }
            }
        }
        val row = compose.onNodeWithText("Liquid glass navigation")
        row.assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertHeightIsAtLeast(64.dp)
            .performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Liquid glass navigation could not be saved. Please try again.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        row.assertIsOn().assertIsEnabled()
        storage.fail = false
        row.performClick()
        compose.waitUntil(5_000) { !store.enabled.value }
        row.assertIsOff()
        compose.onNodeWithText("Liquid glass navigation could not be saved. Please try again.").assertDoesNotExist()
    }
}
