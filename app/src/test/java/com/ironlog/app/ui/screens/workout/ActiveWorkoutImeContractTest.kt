package com.ironlog.app.ui.screens.workout

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiring guard; real IME / first-set placement must also be verified on Android. */
class ActiveWorkoutImeContractTest {
    @Test fun `workout viewport consumes keyboard insets before measuring list and rest overlay`() {
        val source = File("src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt").readText()
        val viewport = File("src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutViewport.kt").readText()
        assertTrue("The actual workout screen must use the tested viewport", source.contains("ActiveWorkoutViewport("))
        assertTrue("Measure the actual scroll viewport after padding", source.contains(".onGloballyPositioned(inputViewport::capture)"))
        assertTrue("Suppress anchoring only during actual superset navigation", source.contains("preserveInputAnchor = scrollToNextSupersetIndex == null"))
        assertTrue("Cancelled auto-navigation must release its own gate without clearing a newer target", source.contains("if (scrollToNextSupersetIndex == pos) scrollToNextSupersetIndex = null"))

        assertTrue(
            "The workout viewport must consume IME insets, so focused set inputs and the rest overlay share the visible area above the keyboard",
            viewport.contains(".windowInsetsPadding(imeInsets)"),
        )
    }
}
