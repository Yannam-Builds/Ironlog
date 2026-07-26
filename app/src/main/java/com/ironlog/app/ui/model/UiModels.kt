package com.ironlog.app.ui.model

import androidx.compose.runtime.Immutable
import com.ironlog.app.data.model.LegacyExerciseShape
import kotlinx.serialization.Serializable

// Flat UI models passed to Compose screens from ViewModels.
// @Immutable lets Compose skip recomposition when these objects haven't changed reference.
@Immutable
@Serializable
data class UiPlanExercise(
    val id: String,
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val supersetGroup: String = "",
    val isWarmup: Boolean = false,
    val notes: String = "",
)

@Immutable
@Serializable
data class UiPlanDay(
    val id: String,
    val name: String,
    val color: String = "#FF4500",
    val exercises: List<UiPlanExercise> = emptyList(),
)

@Immutable
@Serializable
data class UiPlan(
    val id: String,
    val name: String,
    val goal: String = "General Fitness",
    val description: String = "",
    val isActive: Boolean = false,
    val days: List<UiPlanDay> = emptyList(),
)

@Immutable
data class HistoryExerciseSet(
    val id: String = "",
    val weight: Double = 0.0,
    val reps: Double = 0.0,
    val type: String = "normal",
    val rpe: Double? = null,
    val rir: Double? = null,
    val note: String? = null,
    val restSeconds: Int = 0,
)

@Immutable
data class HistoryExercise(
    val id: String = "",
    val exerciseId: String = "",
    val name: String = "Unknown",
    val sets: List<HistoryExerciseSet> = emptyList(),
    val primaryMuscles: List<String> = emptyList(),
    val primaryMuscle: String? = null,
    val equipment: String? = null,
    val category: String? = null,
    val supersetGroup: String? = null,
    val note: String? = null,
)

@Immutable
data class HistoryEntry(
    val id: String,
    val date: String,
    val duration: Int = 0,
    val rating: Double? = null,
    val summaryText: String? = null,
    val name: String = "Workout",
    val dayName: String? = null,
    val planDayUid: String? = null,
    val imported: Boolean = false,
    val exercises: List<HistoryExercise> = emptyList(),
) {
    val sets: Int get() = exercises.sumOf { it.sets.size }
    val volume: Double get() = exercises.sumOf { ex -> ex.sets.filter { it.type != "warmup" }.sumOf { it.weight * it.reps } }
}

@Immutable
data class ChartPoint(val value: Int, val label: String = "")

@Immutable
data class PersonalBest(
    val exerciseName: String,
    val bestWeight: Double,
    val estOneRm: Double,
    // FIXED: 26 — date + previousOrm for enriched PB display
    val date: String? = null,       // ISO date string (yyyy-MM-dd) when PR was set
    val previousOrm: Double? = null, // second-best est 1RM for trend delta
)

@Immutable
data class StatsUiState(
    val history: List<HistoryEntry> = emptyList(),
    val pb: Map<String, Double> = emptyMap(),
    val personalBests: List<PersonalBest> = emptyList(),
    val pbGroups: Map<String, Double> = emptyMap(),
    val streak: Int = 0,
    val totalSets: Int = 0,
    val avgDurationMin: Int = 0,
    val chartData: List<ChartPoint> = emptyList(),
    val weightUnit: String = "kg",
)

@Immutable
data class IronLogSettings(
    val theme: String = com.ironlog.app.ui.theme.IronLogThemes.DEFAULT_THEME,
    val weightUnit: String = "kg",
    val hapticFeedback: Boolean = true,
    val effortTracking: String = "off",
    val defaultRestSeconds: Int = 90,
    val defaultRestHeavySeconds: Int = 180,
    val barWeightKg: Double = 20.0,
    val weeklyGoalDays: Int = 4,
    val goalMode: String = "hypertrophy",
    val progressionStyle: String = "balanced",
    val userName: String = "",
    val performanceMode: String = "balanced",
    /** "builtin" = existing TrainingIntelligenceEngine; "gemini_nano" = APEX ENGINE; "cloud_ai" = BYOK Cloud AI. */
    val intelligenceMode: String = "builtin",
    val cloudAiBaseUrl: String = "",
    val cloudAiModelName: String = "",
    val cloudAiDisplayName: String = "",
    val cloudAiProviderPreset: String = "",
    val cloudAiApiFormat: String = "openai",
)

@Immutable
data class AppDataState(
    val initialized: Boolean = false,
    /** True once the plan repository has emitted its first snapshot (even if empty).
     *  HomeScreen uses this to distinguish "still loading" from "no plans exist" —
     *  prevents the "No program selected" empty state from flashing on cold start. */
    val plansLoaded: Boolean = false,
    val plans: List<UiPlan> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val pb: Map<String, Double> = emptyMap(),
    val exerciseNotes: Map<String, String> = emptyMap(),
    val settings: IronLogSettings = IronLogSettings(),
    val onboardingComplete: Boolean = false,
    val exerciseIndex: List<LegacyExerciseShape> = emptyList(),
)
