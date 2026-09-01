package com.ironlog.app.ui.screens.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutElapsedPresentationTest {
    private val source = File(requireNotNull(System.getProperty("user.dir"))).resolve(
        "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt",
    ).readText()

    @Test
    fun `elapsed header renders zero consistently before the first set`() {
        assertTrue(source.contains("value = formatDurationShort(elapsedSeconds)"))
        assertFalse(source.contains("else \"--:--\""))
    }
}
