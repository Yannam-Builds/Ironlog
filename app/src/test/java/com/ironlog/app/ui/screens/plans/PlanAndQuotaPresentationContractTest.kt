package com.ironlog.app.ui.screens.plans

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PlanAndQuotaPresentationContractTest {
    private fun source(path: String): String {
        val relative = "src/main/java/com/ironlog/app/$path"
        return listOf(File(relative), File("app/$relative")).first { it.isFile }.readText()
    }

    @Test fun planDragUsesStableKeysAndAwaitsThePersistedOrder() {
        val screen = source("ui/screens/plans/PlanEditorScreen.kt")
        assertFalse("Header counts cannot identify exercise rows", screen.contains("from.index"))
        assertFalse("Non-adjacent moves must insert, not swap", screen.contains("Collections.swap"))
        assertTrue(screen.contains("from.key"))
        assertTrue(screen.contains("to.key"))
        assertTrue(screen.contains("reorderExercisesAndAwait"))
        assertTrue("Failure must remain visible", screen.contains("Could not reorder exercises"))
        val vm = source("ui/viewmodel/PlansViewModel.kt")
        assertTrue(vm.contains("suspend fun reorderExercisesAndAwait"))
    }

    @Test fun weekdaysWrapAndExposeToggleSemantics() {
        val screen = source("ui/screens/onboarding/steps/Step4Quota.kt")
        assertTrue("Seven 48dp targets must wrap on narrow screens", screen.contains("FlowRow("))
        assertTrue(screen.contains("toggleable("))
        assertTrue(screen.contains("Role.Checkbox"))
        assertTrue(screen.contains("Monday") && screen.contains("Thursday") && screen.contains("Sunday"))
    }

    @Test fun programDetailsHaveScrollRecoveryAndNavigationInsets() {
        val screen = source("ui/screens/plans/ProgramPickerScreen.kt")
        assertTrue("Details and Add need scroll recovery at large font sizes", screen.contains("verticalScroll("))
        assertTrue(screen.contains("navigationBarsPadding("))
    }
}
