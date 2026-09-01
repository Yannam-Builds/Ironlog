package com.ironlog.app.ui.theme

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AppearanceSaveQueueTest {
    @Test fun `queued changes read latest selection after delayed first save`() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var writes = 0
        val store = TypographyStore(object : TypographyStorage {
            override fun read() = null to null
            override fun write(selection: TypographySelection) {
                if (++writes == 1) { entered.countDown(); check(release.await(3, TimeUnit.SECONDS)) }
            }
        })
        val queue = AppearanceSaveQueue(owner)
        try {
            val first = queue.enqueue { store.update { it.copy(fontId = "inter") } }
            assertTrue("First queued save should execute", entered.await(2, TimeUnit.SECONDS))
            val second = queue.enqueue { store.update { it.copy(preset = TypographyPreset.LIGHT) } }
            release.countDown()
            withTimeout(3000) { joinAll(first, second) }
            assertEquals(TypographySelection("inter", TypographyPreset.LIGHT), store.selection.value)
        } finally { release.countDown(); owner.cancel() }
    }

    @Test fun `failed font write does not publish a new selection`() {
        val store = TypographyStore(object : TypographyStorage {
            override fun read() = "manrope" to "light"
            override fun write(selection: TypographySelection) { throw IllegalStateException("Disk full") }
        })
        assertThrows(IllegalStateException::class.java) { store.update { it.copy(fontId = "inter") } }
        assertEquals(TypographySelection("manrope", TypographyPreset.LIGHT), store.selection.value)
    }

    @Test fun `saves belong to the app rather than a departing settings screen`() {
        for (name in listOf("TypographySettingsCard.kt", "SpacingSettingsCard.kt")) {
            val source = File("src/main/java/com/ironlog/app/ui/screens/settings/$name").readText()
            assertTrue(name, source.contains("AppearancePersistence.enqueue"))
            assertFalse(name, source.contains("rememberCoroutineScope"))
        }
    }
}
