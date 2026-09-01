package com.ironlog.app.ui.screens.home

import com.ironlog.app.domain.intelligence.ProgressionPolicyResolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCardPresentationTest {
    private val source = File(requireNotNull(System.getProperty("user.dir"))).resolve(
        "src/main/java/com/ironlog/app/ui/screens/home/HomeScreen.kt",
    ).readText()

    @Test
    fun `adaptive target keeps recommendation provenance and plateau status separate`() {
        val policy = ProgressionPolicyResolver.resolve(null, null, "balanced")
        val target = AdaptiveTarget(
            name = "Single-Arm Kneeling Cable Lat Pulldown",
            advice = null,
            policy = policy,
            plateau = true,
        )
        val presentation = adaptiveTargetPresentation(target, "kg")
        assertEquals(
            "Single-Arm Kneeling Cable Lat Pulldown → Build to a working weight",
            presentation.recommendation,
        )
        assertEquals(
            "${policy.label} · ${policy.source.label}",
            presentation.provenance,
        )
        assertEquals("⟳ Plateau", presentation.statusLabel)
    }

    @Test
    fun `adaptive target card gives status a non collapsing wrapping chip`() {
        assertTrue(source.contains("adaptiveTargetPresentation(s, weightUnit)"))
        assertTrue(source.contains("presentation.statusLabel?.let"))
        assertTrue(source.contains("softWrap = false"))
    }

    @Test
    fun `daily proof visual spec reserves a larger bounded slot on compact phones`() {
        val compact = dailyProofVisualSpec(280f)
        val expanded = dailyProofVisualSpec(480f)

        assertTrue("Compact mascot should be larger than the old 56 dp image", compact.imageSizeDp > 56)
        assertTrue(compact.imageSizeDp <= compact.slotWidthDp)
        assertTrue(compact.imageSizeDp <= compact.slotHeightDp)
        assertTrue(expanded.imageSizeDp > compact.imageSizeDp)
    }

    @Test
    fun `daily proof card uses the bounded visual spec and fit scaling`() {
        assertTrue(source.contains("dailyProofVisualSpec(maxWidth.value)"))
        assertTrue(source.contains("visualSpec.slotWidthDp.dp"))
        assertTrue(source.contains("contentScale = ContentScale.Fit"))
    }
}
