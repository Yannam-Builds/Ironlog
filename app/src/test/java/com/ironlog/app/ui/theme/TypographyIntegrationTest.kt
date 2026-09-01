package com.ironlog.app.ui.theme

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Guards text paths that bypass the Material theme; these are deliberate app-owned boundaries. */
class TypographyIntegrationTest {
    private val source = File("src/main/java/com/ironlog/app")

    @Test fun `all app material text uses the preference aware wrapper`() {
        val bypasses = source.walkTopDown().filter { it.extension == "kt" && it.name != "IronLogText.kt" }
            .filter { it.readText().contains("import androidx.compose.material3.Text\n") || it.readText().contains("import androidx.compose.material3.Text\r\n") }
            .map { it.relativeTo(source).path }.toList()
        assertEquals(emptyList<String>(), bypasses)
    }

    @Test fun `chart labels do not force system typeface`() {
        for (path in listOf("ui/screens/body/BodyWeightScreen.kt", "ui/screens/stats/VolumeAnalyticsScreen.kt")) {
            assertFalse(path, File(source, path).readText().contains("Typeface.DEFAULT"))
            assertTrue(path, File(source, path).readText().contains("rememberTypographyPaint"))
        }
    }

    @Test fun `font binaries and licenses are bundled offline`() {
        for (id in listOf("lexend", "inter", "manrope", "dmsans", "plusjakartasans", "outfit", "sora", "urbanist", "nunito", "nunitosans", "rubik", "worksans", "publicsans", "figtree", "assistant", "mulish", "quicksand", "raleway", "montserrat", "exo2", "sourcesans3")) {
            assertTrue(id, File("src/main/res/font/${id}_variable.ttf").isFile)
            assertTrue(id, File("src/main/assets/font_licenses/${id}.txt").isFile)
        }
    }

    @Test fun `appearance exposes an accessible typography picker`() {
        assertTrue(File(source, "ui/screens/settings/SettingsScreen.kt").readText().contains("TypographySettingsCard()"))
        val picker = File(source, "ui/screens/settings/TypographySettingsCard.kt")
        assertTrue(picker.isFile)
        val content = picker.readText()
        assertTrue(content.contains("selectableGroup"))
        assertTrue(content.contains("Role.RadioButton"))
        assertTrue(content.contains("LazyColumn"))
        assertTrue(content.contains("font_licenses/"))
    }
}
