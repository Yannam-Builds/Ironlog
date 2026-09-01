package com.ironlog.app.ui.screens.stats

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsHistoryMutationHarmonyContractTest {
    private val source = File(requireNotNull(System.getProperty("user.dir"))).resolve(
        "src/main/java/com/ironlog/app/ui/screens/stats/StatsScreen.kt",
    ).readText()

    @Test
    fun `stats pr reset uses process serialization and terminal surface reconciliation`() {
        val resetCall = source.indexOf("appVm.clearPbsNow()")
        val blockStart = source.lastIndexOf("application.launchAcceptedMutation", resetCall)
        val blockEnd = source.indexOf("showClearPbsConfirm = false", resetCall)
        val block = source.substring(blockStart, blockEnd)

        assertTrue(block.contains("commitHistoryMutationAcrossSurfaces"))
        assertTrue(source.contains("application.acceptedMutationCount.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("enabled = !clearPbsPending && !historyMutationPending"))
    }
}
