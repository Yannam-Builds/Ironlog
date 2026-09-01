package com.ironlog.app.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalBodyweightConsumerContractTest {
    @Test
    fun `cloud plan generation reads canonical athlete bodyweight instead of onboarding baseline setting`() {
        val source = File("src/main/java/com/ironlog/app/ui/screens/plans/AIPlanScreen.kt").readText()

        assertTrue(source.contains("getCurrentBodyweightKg()"))
        assertFalse(source.contains("getString(\"baseline_bodyweight_kg\")"))
    }

    @Test
    fun `gamification reads the immutable onboarding baseline instead of mutable current weight`() {
        val source = File("src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt").readText()

        assertTrue(source.contains("onboardingBaselineBodyweightKg(boxStore, entity)"))
        assertFalse(source.contains("bodyweightKg = currentAthleteBodyweightKg(boxStore)"))
        assertFalse(source.contains("bodyweightKg = entity?.bodyweightKg ?: settingInt(\"baseline_bodyweight_kg\")"))
    }
}
