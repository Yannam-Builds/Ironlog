package com.ironlog.app.ui.screens.plans

import com.ironlog.app.ui.screens.settings.GymProfileDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AIPlanGymProfileTest {
    @Test
    fun `AI plan reads the canonical gym profile settings namespace`() {
        assertEquals("gym_profiles_json", GYM_PROFILES_SETTINGS_KEY)
        assertEquals("active_gym_profile_id", ACTIVE_GYM_PROFILE_ID_SETTINGS_KEY)
    }

    @Test
    fun `active canonical gym profile removes unavailable equipment from AI context`() {
        val profilesJson = Json.encodeToString(
            listOf(
                GymProfileDto(
                    id = "home",
                    name = "Home",
                    barWeightKg = 20.0,
                    unavailableEquipment = listOf("Cable", "Machine", "Barbell"),
                ),
                GymProfileDto(
                    id = "full",
                    name = "Full Gym",
                    barWeightKg = 20.0,
                ),
            ),
        )

        assertEquals(
            listOf("Dumbbell", "Kettlebell", "Band", "Bodyweight", "Other"),
            resolveAiPlanEquipment(profilesJson, activeProfileId = "home"),
        )
    }

    @Test
    fun `missing or malformed active profile uses the explicit full gym fallback`() {
        assertEquals(AI_PLAN_EQUIPMENT_OPTIONS, resolveAiPlanEquipment(null, null))
        assertEquals(AI_PLAN_EQUIPMENT_OPTIONS, resolveAiPlanEquipment("not-json", "missing"))
    }
}
