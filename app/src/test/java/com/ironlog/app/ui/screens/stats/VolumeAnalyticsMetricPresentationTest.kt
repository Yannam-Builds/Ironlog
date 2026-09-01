package com.ironlog.app.ui.screens.stats

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeAnalyticsMetricPresentationTest {
    private val source = File(requireNotNull(System.getProperty("user.dir"))).resolve(
        "src/main/java/com/ironlog/app/ui/screens/stats/VolumeAnalyticsScreen.kt",
    ).readText()

    @Test
    fun `compact volume keeps the unit in its label instead of joining it to the value`() {
        assertTrue(source.contains("formattedVolume to \"Volume (\$weightUnit)\""))
        assertFalse(source.contains("k\$weightUnit"))
    }
}
