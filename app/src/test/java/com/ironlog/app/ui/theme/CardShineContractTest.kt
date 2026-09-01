package com.ironlog.app.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CardShineContractTest {
    @Test fun `workout card restores a controllable animated shine`() {
        val source = File("src/main/java/com/ironlog/app/ui/screens/home/HomeScreen.kt").readText()
        assertTrue("Workout card must draw its animated shine", source.contains(".animatedCardShine(c.accent)"))
    }

    @Test fun `appearance and root share the saved shine setting`() {
        val settings = File("src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt").readText()
        val root = File("src/main/java/com/ironlog/app/ui/IronLogApp.kt").readText()
        assertTrue("Appearance needs a shine switch", settings.contains("CardShineSettingsCard()"))
        assertTrue("Root needs the saved preference", root.contains("LocalCardShineEnabled provides cardShineEnabled"))
    }
}
