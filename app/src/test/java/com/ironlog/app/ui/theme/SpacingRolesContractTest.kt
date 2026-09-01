package com.ironlog.app.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacingRolesContractTest {
    @Test fun `settings offers separate card padding and text spacing`() {
        val settings = File("src/main/java/com/ironlog/app/ui/screens/settings/SpacingSettingsCard.kt").readText()
        for (label in listOf("Between cards", "Content padding", "Text & controls", "Compact", "Balanced", "Roomy")) {
            assertTrue("Missing independent spacing option: $label", settings.contains(label))
        }
    }

    @Test fun `stats gates cloud summary by selected engine not credentials alone`() {
        val screen = File("src/main/java/com/ironlog/app/ui/screens/stats/StatsScreen.kt").readText()
        val settings = File("src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt").readText()
        assertTrue("Canonical cloud mode is saved by Settings", settings.contains("intelligenceMode = \"cloud_ai\""))
        assertTrue("Stats must honor the canonical intelligence mode", screen.contains("cloudSettings.intelligenceMode == \"cloud_ai\""))
        assertTrue("Summary work must be disposed when cloud is disabled", screen.contains("CloudStatsSummaryHost("))
        assertTrue("Key-only saves must invalidate the summary", screen.contains("CloudAiKeyStore.revision.collectAsStateWithLifecycle()"))
    }
}
