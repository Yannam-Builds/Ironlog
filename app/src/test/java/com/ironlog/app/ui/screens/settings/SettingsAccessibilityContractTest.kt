package com.ironlog.app.ui.screens.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static wiring guard; TalkBack, IME and large-text behavior still require instrumentation. */
class SettingsAccessibilityContractTest {
    private val projectRoot = File(requireNotNull(System.getProperty("user.dir")))
    private val settings = projectRoot.resolve(
        "src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt",
    ).readText()
    private val restoreDialog = projectRoot.resolve(
        "src/main/java/com/ironlog/app/ui/screens/settings/BackupRestoreDialog.kt",
    ).readText()

    @Test
    fun `persisted editor values hydrate only after app state is initialized and keys load on IO`() {
        assertTrue(settings.contains("LaunchedEffect(state.initialized"))
        assertTrue(settings.contains("if (state.initialized && !cloudFormDirty)"))
        assertTrue(settings.contains("withContext(Dispatchers.IO) { CloudAiKeyStore.load"))
        assertFalse(settings.contains("cloudApiKey = CloudAiKeyStore.load(context, preset.key)"))
    }

    @Test
    fun `high impact workout and cloud saves are awaited and keep visible failure state`() {
        assertTrue(settings.contains("cloudSaveError"))
        assertTrue(settings.contains("normalRestSaveError"))
        assertTrue(settings.contains("heavyRestSaveError"))
        assertTrue(settings.contains("barWeightSaveError"))
        assertTrue(settings.contains("vm.mutateSettings {"))
    }

    @Test
    fun `choice controls expose group and selected semantics with full size targets`() {
        assertTrue(settings.contains("Modifier.selectableGroup()"))
        assertTrue(settings.contains("role = Role.RadioButton"))
        assertTrue(settings.contains(".heightIn(min = 48.dp)"))
        assertTrue(settings.contains("IconButton(onClick = { cloudApiKeyVisible"))
        assertTrue(settings.contains("LiveRegionMode.Polite"))
    }

    @Test
    fun `backup restore mode is one radio target per option`() {
        assertTrue(restoreDialog.contains("Modifier.selectableGroup()"))
        assertTrue(restoreDialog.contains(".selectable("))
        assertTrue(restoreDialog.contains("role = Role.RadioButton"))
        assertTrue(restoreDialog.contains("RadioButton(selected = mode == value, onClick = null"))
        assertFalse(restoreDialog.contains("Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable"))
    }

    @Test
    fun `Monet preview is honest about browser independent fallback palette`() {
        assertTrue(settings.contains("Monet (Fallback)"))
        assertFalse(settings.contains("Monet (Dynamic)"))
    }
}
