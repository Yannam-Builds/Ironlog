package com.ironlog.app.navigation

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassContractTest {
    private val root = File("src/main/java/com/ironlog/app/navigation")

    @Test fun `glass is a background only renderer with native refraction`() {
        val renderer = File(root, "LiquidGlassRenderer.kt")
        assertTrue("A separately testable glass background is required", renderer.exists())
        val source = renderer.readText()
        assertTrue(source.contains("hazeEffect"))
        assertTrue(source.contains("createRuntimeShaderEffect"))
        assertTrue("New Android API must be guarded", source.contains("if (Build.VERSION.SDK_INT >= 33)"))
        assertFalse("Icons must not be rendered into the refracted layer", source.contains("Icon("))
    }

    @Test fun `disabled glass does not record the pager backdrop`() {
        val source = File(root, "AppNavigator.kt").readText()
        assertTrue("Backdrop capture must be conditional", source.contains("if (liquidGlassEnabled) Modifier.hazeSource(hazeState) else Modifier"))
    }
}
