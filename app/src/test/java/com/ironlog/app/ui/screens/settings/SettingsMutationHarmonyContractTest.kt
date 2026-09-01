package com.ironlog.app.ui.screens.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the cross-store and cross-surface boundaries that are easy to regress in Settings. */
class SettingsMutationHarmonyContractTest {
    private val source = File(requireNotNull(System.getProperty("user.dir"))).resolve(
        "src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt",
    ).readText()
    private val bridgeSource = File(requireNotNull(System.getProperty("user.dir"))).resolve(
        "src/main/java/com/ironlog/app/ui/screens/settings/HistoryMutationSurfaceBridge.kt",
    ).readText()

    @Test
    fun `cloud key and provider settings finish or roll back despite route cancellation`() {
        val block = source.substring(
            source.indexOf("fun saveCloudConfiguration()"),
            source.indexOf("val notificationPermissionLauncher"),
        )

        assertTrue(block.contains("withContext(NonCancellable)"))
        assertTrue(block.contains("CloudAiKeyStore.save"))
        assertTrue(block.contains("CloudAiKeyStore.clear"))
        assertTrue(block.contains("vm.mutateSettings"))
    }

    @Test
    fun `keep awake save is awaited and restores authoritative state on failure`() {
        assertTrue(source.contains("keepAwakeSaving"))
        assertTrue(source.contains("keepAwakeSaveError"))
        assertTrue(source.contains("enabled = !keepAwakeSaving"))
        assertTrue(source.contains("settingsRepo.setBoolean(\"keep_screen_awake_active_workout\""))
        assertTrue(source.contains("settingsRepo.getBoolean(\"keep_screen_awake_active_workout\""))
    }

    @Test
    fun `history deletion and direct csv import enter the terminal mutation boundary`() {
        val clearMutation = source.indexOf("vm.clearAllHistoryNow()")
        val clearStart = source.lastIndexOf("commitHistoryMutation", clearMutation)
        val clearAcceptedStart = source.lastIndexOf("application.launchAcceptedMutation", clearMutation)
        val clearEnd = source.indexOf("confirmClearAll = false", clearStart)
        assertTrue(source.substring(clearStart, clearEnd).contains("commitHistoryMutation"))
        assertTrue(clearAcceptedStart in 0 until clearMutation)

        val resetMutation = source.indexOf("vm.clearPbsNow()")
        val resetAcceptedStart = source.lastIndexOf("application.launchAcceptedMutation", resetMutation)
        assertTrue(resetAcceptedStart in 0 until resetMutation)

        val importCall = source.indexOf("importText(")
        val importStart = source.lastIndexOf("commitHistoryMutation", importCall)
        val acceptedStart = source.lastIndexOf("application.launchAcceptedMutation", importCall)
        val clickBoundary = source.lastIndexOf("showCsvImportConfirm = false", importCall)
        val importEnd = source.indexOf("csvImportText = \"\"", importStart)
        val importBlock = source.substring(clickBoundary, importEnd)
        assertTrue(importBlock.contains("commitHistoryMutation"))
        assertTrue(acceptedStart in clickBoundary until importCall)
        assertTrue(importBlock.contains("val payload = csvImportText"))
        assertTrue(importBlock.contains("text = payload"))
        assertTrue(source.contains("historyMutationPending"))
        assertTrue(source.contains("application.acceptedMutationCount.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("enabled = !historyMutationPending"))
        assertTrue(source.contains("commitHistoryMutationAcrossSurfaces"))
        assertTrue(bridgeSource.contains("refreshAppState = { viewModel.refreshAwaited() }"))
    }
}
