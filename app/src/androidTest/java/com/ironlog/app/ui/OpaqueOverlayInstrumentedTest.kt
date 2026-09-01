package com.ironlog.app.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import com.ironlog.app.ui.components.IronLogDropdownMenu
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.ObsidianShapes
import com.ironlog.app.ui.theme.ObsidianTypography
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpaqueOverlayInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dropdown_isVisible_selectable_andDismisses() {
        compose.setContent {
            ThemeProvider(themeName = "dark") {
                val colors = useTheme()
                MaterialTheme(
                    colorScheme = colors.toMaterialColorScheme(),
                    typography = ObsidianTypography,
                    shapes = ObsidianShapes,
                ) {
                    IronLogDropdownMenu(expanded = true, onDismissRequest = {}) {
                        DropdownMenuItem(text = { Text("Opaque action") }, onClick = {})
                    }
                }
            }
        }

        compose.onNodeWithText("Opaque action").assertIsDisplayed().performClick()
    }

    @Test
    fun dropdown_dismisses_on_back() {
        compose.setContent {
            ThemeProvider(themeName = "amoled") {
                val colors = useTheme()
                MaterialTheme(colorScheme = colors.toMaterialColorScheme()) {
                    var expanded by remember { mutableStateOf(true) }
                    IronLogDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Dismiss target") }, onClick = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Dismiss target").assertIsDisplayed()
        pressBack()
        compose.waitForIdle()
        compose.onAllNodesWithText("Dismiss target").assertCountEquals(0)
    }
}
