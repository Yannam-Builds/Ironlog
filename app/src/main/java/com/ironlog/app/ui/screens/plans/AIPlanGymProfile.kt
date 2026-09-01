package com.ironlog.app.ui.screens.plans

import com.ironlog.app.ui.screens.settings.GymProfileDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal const val GYM_PROFILES_SETTINGS_KEY = "gym_profiles_json"
internal const val ACTIVE_GYM_PROFILE_ID_SETTINGS_KEY = "active_gym_profile_id"

internal val AI_PLAN_EQUIPMENT_OPTIONS = listOf(
    "Barbell",
    "Dumbbell",
    "Cable",
    "Machine",
    "Kettlebell",
    "Band",
    "Bodyweight",
    "Other",
)

/** Resolves the canonical active profile's deny-list into the equipment allow-list sent to AI. */
internal fun resolveAiPlanEquipment(
    profilesJson: String?,
    activeProfileId: String?,
): List<String> {
    if (profilesJson.isNullOrBlank() || activeProfileId.isNullOrBlank()) {
        return AI_PLAN_EQUIPMENT_OPTIONS
    }
    val json = Json { ignoreUnknownKeys = true }
    val profiles = runCatching {
        json.decodeFromString(ListSerializer(GymProfileDto.serializer()), profilesJson)
    }.getOrNull() ?: return AI_PLAN_EQUIPMENT_OPTIONS
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
        ?: return AI_PLAN_EQUIPMENT_OPTIONS
    val unavailable = activeProfile.unavailableEquipment
        .map { it.trim().lowercase() }
        .toSet()
    return AI_PLAN_EQUIPMENT_OPTIONS.filterNot { it.lowercase() in unavailable }
}
