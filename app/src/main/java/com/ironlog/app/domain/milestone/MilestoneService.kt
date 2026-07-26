package com.ironlog.app.domain.milestone

import com.ironlog.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MilestoneService(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
) {
    suspend fun registerPostWorkout(totalVolumeKg: Double, streakDays: Int, newPr: Boolean) = withContext(Dispatchers.IO) {
        val unlocked = load().toMutableSet()
        if (newPr) unlocked += "MILESTONE_PR"
        if (totalVolumeKg >= 1000) unlocked += "MILESTONE_LIFT_1T"
        if (totalVolumeKg >= 5000) unlocked += "MILESTONE_LIFT_5T"
        if (totalVolumeKg >= 10000) unlocked += "MILESTONE_LIFT_10T"
        if (streakDays >= 7) unlocked += "MILESTONE_STREAK_7"
        if (streakDays >= 30) unlocked += "MILESTONE_STREAK_30"
        if (streakDays >= 100) unlocked += "MILESTONE_STREAK_100"
        save(unlocked.toList().sorted())
    }

    suspend fun load(): Set<String> = withContext(Dispatchers.IO) {
        val raw = settingsRepository.getString("milestone_unlocks") ?: return@withContext emptySet()
        runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }.filter { it.isNotBlank() }.toSet()
        }.getOrDefault(emptySet())
    }

    private suspend fun save(values: List<String>) {
        settingsRepository.setString("milestone_unlocks", JSONArray(values).toString(), "json")
    }
}
