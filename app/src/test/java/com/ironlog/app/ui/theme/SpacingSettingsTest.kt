package com.ironlog.app.ui.theme

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SpacingSettingsTest {
    @Test fun `missing invalid and nonfinite values preserve default spacing`() {
        for (raw in listOf(null, "compact", true, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(1f, SpacingSettings.normalize(raw), 0.0001f)
        }
    }

    @Test fun `scale is bounded and snapped to five percent increments`() {
        assertEquals(0.85f, SpacingSettings.normalize(0.4f), 0.0001f)
        assertEquals(1.25f, SpacingSettings.normalize(8f), 0.0001f)
        assertEquals(0.95f, SpacingSettings.normalize(0.93), 0.0001f)
        assertEquals(1.1f, SpacingSettings.normalize(1.11), 0.0001f)
    }

    @Test fun `only positive layout gaps scale and default leaves dimensions identical`() {
        assertEquals(16f, SpacingSettings.scaleGapDp(16f, 1f), 0.0001f)
        assertEquals(13.6f, SpacingSettings.scaleGapDp(16f, 0.85f), 0.0001f)
        assertEquals(20f, SpacingSettings.scaleGapDp(16f, 1.25f), 0.0001f)
        assertEquals(0f, SpacingSettings.scaleGapDp(0f, 1.25f), 0.0001f)
        assertEquals(-4f, SpacingSettings.scaleGapDp(-4f, 1.25f), 0.0001f)
        assertTrue(SpacingSettings.scaleGapDp(Float.NaN, 1.25f).isNaN())
    }

    private class MemoryStorage(var saved: Any? = null) : SpacingStorage {
        override fun read() = saved
        override fun write(scale: Float) { saved = scale }
    }

    @Test fun `spacing is loaded before rendering and updates survive reopening`() {
        val storage = MemoryStorage(1.2f)
        val store = SpacingStore(storage)
        assertEquals(1.2f, store.scale.value, 0.0001f)
        store.update(0.9f)
        assertEquals(0.9f, store.scale.value, 0.0001f)
        assertEquals(0.9f, SpacingStore(storage).scale.value, 0.0001f)
        store.update(SpacingSettings.DEFAULT)
        assertEquals(1f, SpacingStore(storage).scale.value, 0.0001f)
    }

    @Test fun `spacing reaches broad app layout without density or bodymap overrides`() {
        val source = File("src/main/java/com/ironlog/app/ui")
        val content = source.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue("Arrangement coverage", Regex("appSpacedBy\\(").findAll(content).count() >= 200)
        assertTrue("Padding coverage", Regex("appPadding\\(").findAll(content).count() >= 60)
        assertTrue("Spacer coverage", Regex("appGapDp\\(").findAll(content).count() >= 50)
        assertTrue(File(source, "screens/settings/SettingsScreen.kt").readText().contains("SpacingSettingsCard()"))
        for (name in listOf("BodyMapCanvas.kt", "RecoveryMapScreen.kt", "RecoveryHeatmapCard.kt")) {
            val file = source.walkTopDown().first { it.name == name }
            assertFalse(name, file.readText().contains("appGapDp"))
            assertFalse(name, file.readText().contains("appPadding"))
        }
        val helper = File(source, "theme/AppSpacing.kt")
        assertTrue(helper.isFile)
        assertFalse(helper.readText().contains("LocalDensity"))
    }

    @Test fun `drag preview is live without disk writes until released`() {
        val storage = MemoryStorage(1f)
        val store = SpacingStore(storage)
        store.preview(1.2f)
        assertEquals(1.2f, store.scale.value, 0.0001f)
        assertEquals(1f, storage.saved as Float, 0.0001f)
        store.persistPreview(store.prepareSave())
        assertEquals(1.2f, SpacingStore(storage).scale.value, 0.0001f)
    }

    @Test fun `failed spacing save restores last persisted scale`() {
        val store = SpacingStore(object : SpacingStorage {
            override fun read(): Any = 1.1f
            override fun write(scale: Float) { throw IllegalStateException("Disk full") }
        })
        store.preview(0.85f)
        assertEquals(0.85f, store.scale.value, 0.0001f)
        assertThrows(IllegalStateException::class.java) { store.persistPreview(store.prepareSave()) }
        assertEquals(1.1f, store.scale.value, 0.0001f)
    }

    @Test fun `failed older save does not roll back newer preview returning to same value`() {
        lateinit var store: SpacingStore
        var saved = 1f
        var writes = 0
        val queued = mutableListOf<SpacingSaveRequest>()
        store = SpacingStore(object : SpacingStorage {
            override fun read(): Any = saved
            override fun write(scale: Float) {
                if (++writes == 1) {
                    // New drag events arrive while the older disk write is in flight: A -> B -> A.
                    store.preview(1.25f)
                    queued += store.prepareSave()
                    store.preview(0.85f)
                    queued += store.prepareSave()
                    throw IllegalStateException("Delayed disk failure")
                }
                saved = scale
            }
        })
        store.preview(0.85f)
        assertThrows(IllegalStateException::class.java) { store.persistPreview(store.prepareSave()) }
        assertEquals(0.85f, store.scale.value, 0.0001f)
        queued.forEach(store::persistPreview)
        assertEquals(0.85f, saved, 0.0001f)
        assertEquals(saved, store.scale.value, 0.0001f)
    }

    @Test fun `native persistence checks disk result and slider saves on release`() {
        val source = File("src/main/java/com/ironlog/app/ui")
        assertTrue(File(source, "theme/TypographyRuntime.kt").readText().contains(".commit()"))
        assertTrue(File(source, "theme/AppSpacing.kt").readText().contains(".commit()"))
        assertTrue(File(source, "screens/settings/SpacingSettingsCard.kt").readText().contains("onValueChangeFinished"))
    }

    @Test fun `spacing labels wrap at large text size and slider announces its purpose`() {
        val source = File("src/main/java/com/ironlog/app/ui/screens/settings/SpacingSettingsCard.kt").readText()
        assertTrue(source.contains("FlowRow("))
        assertFalse(source.contains("Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)"))
        assertTrue(source.contains("contentDescription = \"UI spacing\""))
    }
}
