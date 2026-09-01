package com.ironlog.app.ui.screens.onboarding

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUiSourceContractTest {
    private val sourceRoot = File(requireNotNull(System.getProperty("user.dir"))).resolve(
        "src/main/java/com/ironlog/app/ui/screens",
    )

    private fun source(relativePath: String): String = sourceRoot.resolve(relativePath).readText()

    @Test
    fun `flow shell gives the pager real space instead of a fixed header overlay`() {
        val flow = source("OnboardingScreen.kt")
        val pagerDeclaration = flow
            .substringAfter("HorizontalPager(")
            .substringBefore(") { page ->")

        assertTrue(pagerDeclaration.contains("Modifier.weight(1f)"))
        assertFalse(flow.contains("else 62.dp"))
        assertTrue(flow.contains("navigationBarsPadding()"))
    }

    @Test
    fun `welcome keeps mascot and evidence banner in separate responsive slots`() {
        val welcome = source("onboarding/steps/Step1Awakening.kt")

        assertTrue(welcome.contains("welcomeHeroLayoutSpec("))
        assertTrue(welcome.contains("WelcomeMascotViewport("))
        assertFalse(welcome.contains("Modifier.align(Alignment.BottomCenter)"))
    }

    @Test
    fun `onboarding background is gradient only without decorative circular blobs`() {
        val components = source("onboarding/OnboardingComponents.kt")

        assertTrue(components.contains("Brush.verticalGradient"))
        assertFalse(components.contains("drawCircle("))
    }

    @Test
    fun `completion mascot uses the shared responsive supporting size`() {
        val completion = source("onboarding/steps/Step8Complete.kt")

        assertTrue(completion.contains("supportingMascotSizeDp("))
        assertFalse(completion.contains("modifier = Modifier.size(104.dp)"))
    }

    @Test
    fun `wheel header applies the single line equal slot contract`() {
        val wheel = source("onboarding/InfiniteWheelPicker.kt")

        assertTrue(wheel.contains("wheelSheetHeaderLayoutSpec()"))
        assertTrue(wheel.contains("maxLines = headerSpec.actionMaxLines"))
        assertTrue(wheel.contains("maxLines = headerSpec.titleMaxLines"))
        assertTrue(wheel.contains("wheelRowLayoutSpec("))
        assertTrue(wheel.contains("clearAndSetSemantics"))
        assertTrue(wheel.contains("progressBarRangeInfo"))
        assertTrue(wheel.contains("setProgress"))
    }

    @Test
    fun `baseline picker bounds both text regions for compact large text layouts`() {
        val baseline = source("onboarding/steps/Step3Baseline.kt")

        assertTrue(baseline.contains("baselinePickerFieldLayoutSpec()"))
        assertTrue(baseline.contains("maxLines = layout.valueMaxLines"))
        assertTrue(baseline.contains("Modifier.weight(layout.labelWeight)"))
        assertTrue(baseline.contains("Modifier.weight(layout.valueWeight)"))
    }

    @Test
    fun `baseline and completion distinguish seeded self report from verified workout proof`() {
        val flow = source("OnboardingScreen.kt")
        val baseline = source("onboarding/steps/Step3Baseline.kt")
        val completion = source("onboarding/steps/Step8Complete.kt")

        assertTrue(flow.contains("BaselineCalibrationEngine"))
        assertTrue(baseline.contains("Training profile preview"))
        assertTrue(baseline.contains("Provisional profile rank"))
        assertTrue(baseline.contains("SELF-REPORTED ESTIMATE"))
        assertTrue(baseline.contains("Verified workouts refine and build on"))
        assertTrue(baseline.contains("estimatedStats.entries"))
        assertTrue(baseline.contains("Supported starting badges"))
        assertFalse(baseline.contains("Ledger starts clean"))
        assertFalse(baseline.contains("Only completed workouts earn XP and badges"))

        assertTrue(baseline.contains("Baseline estimate"))
        assertTrue(completion.contains("Baseline ready to save"))
        assertFalse(baseline.contains("Baseline seeded"))
        assertFalse(completion.contains("Baseline seeded"))
        assertTrue(completion.contains("self-reported onboarding baseline"))
        assertTrue(completion.contains("Verified workouts add proof XP"))
        assertTrue(completion.contains("refine and build on rank and stats"))
        assertFalse(completion.contains("fontSize = 10.sp"))
        assertFalse(completion.contains("Level 1 · 0 XP"))
        assertFalse(completion.contains("Provisional grade"))
        assertFalse(completion.contains("IronGradeBadge"))
    }
}
