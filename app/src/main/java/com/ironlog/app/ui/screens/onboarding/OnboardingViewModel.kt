package com.ironlog.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Year

data class OnboardingDraft(
    val userName: String = "",
    val progressionStyle: String = "LINEAR",
    val goalMode: String = "STRENGTH",
    val weeklyGoalDays: Int = 3,
    val historicalTrainingDaysPerWeek: Int = 3,
    val selectedDayIndices: Set<Int> = setOf(0, 2, 4),
    val weightUnit: String = "kg",
    val cloudAiApiKey: String = "",
    val cloudAiModelName: String = "gemini-2.0-flash-lite",
    val cloudAiProviderPreset: String = "gemini",
    val intelligenceMode: String = "LOCAL",
    val cameraGranted: Boolean = false,
    val healthConnectGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    // Step 3 — Baseline calibration fields
    val yearOfBirth: Int = 2000,
    val bodyweightKg: Int = 70,
    val trainingAgeMonths: Int = 6,
    val hasPastTraining: Boolean = false,
    val hasGymAccess: Boolean = true,
    val baselinePushups: Int = 0,
    val baselinePullups: Int = 0,
    val baselineBenchKg: Int = 0,
    val baselineLatPulldownKg: Int = 0,
    val baselineMileRunSeconds: Int = 0,
)

class OnboardingViewModel : ViewModel() {

    private val _draft = MutableStateFlow(OnboardingDraft())
    val draft: StateFlow<OnboardingDraft> = _draft.asStateFlow()

    fun updateUserName(name: String) {
        _draft.update { it.copy(userName = name.take(30)) }
    }

    fun updateWeeklyGoalDays(days: Int) {
        _draft.update { it.copy(weeklyGoalDays = days.coerceIn(1, 7)) }
    }

    fun updateHistoricalTrainingDaysPerWeek(days: Int) {
        _draft.update { it.copy(historicalTrainingDaysPerWeek = days.coerceIn(1, 7)) }
    }

    fun toggleDayIndex(index: Int) {
        val current = _draft.value.selectedDayIndices
        val updated = if (index in current && current.size > 1) current - index else current + index
        _draft.update { it.copy(selectedDayIndices = updated, weeklyGoalDays = updated.size) }
    }

    fun updateWeightUnit(unit: String) {
        _draft.update { it.copy(weightUnit = if (unit.equals("lbs", ignoreCase = true)) "lbs" else "kg") }
    }

    fun setClassification(progressionStyle: String, defaultGoalMode: String) {
        _draft.update { it.copy(progressionStyle = progressionStyle, goalMode = defaultGoalMode) }
    }

    fun setGoalMode(mode: String) {
        _draft.update { it.copy(goalMode = mode) }
    }

    fun updateCloudProvider(provider: String, defaultModel: String) {
        _draft.update { it.copy(cloudAiProviderPreset = provider.lowercase(), cloudAiModelName = defaultModel) }
    }

    fun updateCloudApiKey(key: String) {
        _draft.update {
            it.copy(
                cloudAiApiKey    = key.trim(),
                intelligenceMode = if (key.isNotBlank()) "AUTO" else "LOCAL",
            )
        }
    }

    fun updateCloudModelName(model: String) {
        _draft.update { it.copy(cloudAiModelName = model) }
    }

    fun setCameraGranted(granted: Boolean) {
        _draft.update { it.copy(cameraGranted = granted) }
    }

    fun setHealthConnectGranted(granted: Boolean) {
        _draft.update { it.copy(healthConnectGranted = granted) }
    }

    fun setNotificationsGranted(granted: Boolean) {
        _draft.update { it.copy(notificationsGranted = granted) }
    }

    // Baseline calibration update methods
    fun updateYearOfBirth(year: Int) { _draft.update { it.copy(yearOfBirth = year.coerceIn(1900, Year.now().value)) } }
    fun updateBodyweightKg(kg: Int) { _draft.update { it.copy(bodyweightKg = kg.coerceIn(20, 500)) } }
    fun updateTrainingAgeMonths(months: Int) { _draft.update { it.copy(trainingAgeMonths = months.coerceIn(0, 960)) } }
    fun setHasPastTraining(has: Boolean) { _draft.update { it.copy(hasPastTraining = has) } }
    fun setHasGymAccess(has: Boolean) { _draft.update { it.copy(hasGymAccess = has) } }
    fun updateBaselinePushups(n: Int) { _draft.update { it.copy(baselinePushups = n.coerceIn(0, 1_000)) } }
    fun updateBaselinePullups(n: Int) { _draft.update { it.copy(baselinePullups = n.coerceIn(0, 500)) } }
    fun updateBaselineBenchKg(kg: Int) { _draft.update { it.copy(baselineBenchKg = kg.coerceIn(0, 1_000)) } }
    fun updateBaselineLatPulldownKg(kg: Int) { _draft.update { it.copy(baselineLatPulldownKg = kg.coerceIn(0, 1_000)) } }
    fun updateBaselineMileRunSeconds(seconds: Int) { _draft.update { it.copy(baselineMileRunSeconds = seconds.coerceIn(0, 7_200)) } }
}
