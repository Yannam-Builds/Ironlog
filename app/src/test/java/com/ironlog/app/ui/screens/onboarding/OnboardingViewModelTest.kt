package com.ironlog.app.ui.screens.onboarding

import org.junit.Assert.*
import org.junit.Test

class OnboardingViewModelTest {

    @Test fun `initial draft has defaults`() {
        val vm = OnboardingViewModel()
        assertEquals("", vm.draft.value.userName)
        assertEquals(3, vm.draft.value.weeklyGoalDays)
        assertEquals(3, vm.draft.value.historicalTrainingDaysPerWeek)
        assertEquals("kg", vm.draft.value.weightUnit)
        assertEquals("LOCAL", vm.draft.value.intelligenceMode)
        assertFalse(vm.draft.value.cameraGranted)
        assertFalse(vm.draft.value.healthConnectGranted)
        assertFalse(vm.draft.value.notificationsGranted)
    }

    @Test fun `updateUserName preserves spaces and updates`() {
        val vm = OnboardingViewModel()
        vm.updateUserName("  Pranav  ")
        assertEquals("  Pranav  ", vm.draft.value.userName)
    }

    @Test fun `updateWeeklyGoalDays clamps to 1-7`() {
        val vm = OnboardingViewModel()
        vm.updateWeeklyGoalDays(0)
        assertEquals(1, vm.draft.value.weeklyGoalDays)
        vm.updateWeeklyGoalDays(8)
        assertEquals(7, vm.draft.value.weeklyGoalDays)
        vm.updateWeeklyGoalDays(5)
        assertEquals(5, vm.draft.value.weeklyGoalDays)
    }

    @Test fun `setting a valid API key sets intelligenceMode to AUTO`() {
        val vm = OnboardingViewModel()
        vm.updateCloudApiKey("AIzaFakeKeyForTest1234567890")
        assertEquals("AUTO", vm.draft.value.intelligenceMode)
    }

    @Test fun `clearing API key reverts intelligenceMode to LOCAL`() {
        val vm = OnboardingViewModel()
        vm.updateCloudApiKey("AIzaFakeKeyForTest1234567890")
        vm.updateCloudApiKey("")
        assertEquals("LOCAL", vm.draft.value.intelligenceMode)
    }

    @Test fun `setClassification seeds goalMode default`() {
        val vm = OnboardingViewModel()
        vm.setClassification(progressionStyle = "UNDULATING", defaultGoalMode = "PERFORMANCE")
        assertEquals("UNDULATING", vm.draft.value.progressionStyle)
        assertEquals("PERFORMANCE", vm.draft.value.goalMode)
    }

    @Test fun `setGoalMode overrides seeded default`() {
        val vm = OnboardingViewModel()
        vm.setClassification(progressionStyle = "LINEAR", defaultGoalMode = "STRENGTH")
        vm.setGoalMode("HYPERTROPHY")
        assertEquals("HYPERTROPHY", vm.draft.value.goalMode)
    }

    @Test fun `permission flags update independently`() {
        val vm = OnboardingViewModel()
        vm.setCameraGranted(true)
        assertTrue(vm.draft.value.cameraGranted)
        assertFalse(vm.draft.value.healthConnectGranted)
        vm.setHealthConnectGranted(true)
        assertTrue(vm.draft.value.healthConnectGranted)
        assertTrue(vm.draft.value.cameraGranted)
    }

    @Test fun `toggleDayIndex adds and removes days, min 1 day`() {
        val vm = OnboardingViewModel()
        // Default is {0,2,4}
        vm.toggleDayIndex(1)
        assertTrue(1 in vm.draft.value.selectedDayIndices)
        // Remove one of the defaults
        vm.toggleDayIndex(0)
        assertFalse(0 in vm.draft.value.selectedDayIndices)
        // weeklyGoalDays follows set size
        assertEquals(vm.draft.value.selectedDayIndices.size, vm.draft.value.weeklyGoalDays)
    }

    @Test fun `toggleDayIndex cannot remove last selected day`() {
        val vm = OnboardingViewModel()
        // Remove all but one
        vm.toggleDayIndex(0)
        vm.toggleDayIndex(4)
        // Now only index 2 remains. Try to remove it.
        vm.toggleDayIndex(2)
        assertTrue(2 in vm.draft.value.selectedDayIndices)
        assertEquals(1, vm.draft.value.selectedDayIndices.size)
    }

    @Test fun `historical training days per week clamps to 1 through 7`() {
        val vm = OnboardingViewModel()
        vm.updateHistoricalTrainingDaysPerWeek(0)
        assertEquals(1, vm.draft.value.historicalTrainingDaysPerWeek)
        vm.updateHistoricalTrainingDaysPerWeek(9)
        assertEquals(7, vm.draft.value.historicalTrainingDaysPerWeek)
        vm.updateHistoricalTrainingDaysPerWeek(5)
        assertEquals(5, vm.draft.value.historicalTrainingDaysPerWeek)
    }

    @Test fun `baseline inputs are clamped before preview and persistence`() {
        val vm = OnboardingViewModel()
        vm.updateBodyweightKg(-10)
        vm.updateTrainingAgeMonths(-3)
        vm.updateBaselinePushups(-1)
        vm.updateBaselineMileRunSeconds(99_999)

        assertEquals(20, vm.draft.value.bodyweightKg)
        assertEquals(0, vm.draft.value.trainingAgeMonths)
        assertEquals(0, vm.draft.value.baselinePushups)
        assertEquals(7_200, vm.draft.value.baselineMileRunSeconds)
    }
}
