package com.ironlog.app.ui.viewmodel

import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.StatsRepository
import com.ironlog.app.data.repository.projectStats
import com.ironlog.app.data.repository.PR_RESET_AT_KEY
import com.ironlog.app.data.repository.parsePersonalBestResetAt
import com.ironlog.app.ui.model.StatsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Owns no coroutine scope; callers control observation lifetime. */
interface StatsUiSource {
    fun observe(): Flow<StatsUiState>
    suspend fun snapshot(): StatsUiState
}

class RepositoryStatsUiSource(
    private val settingsRepo: SettingsRepository = SettingsRepository(),
    private val statsRepository: StatsRepository = StatsRepository(),
    private val clock: () -> Instant = { Instant.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : StatsUiSource {
    override fun observe(): Flow<StatsUiState> = combine(
        statsRepository.completedHistoryFlow(),
        settingsRepo.observeStrings(setOf("ironlog_settings", PR_RESET_AT_KEY))
            .map { rows ->
                StatsProjectionSettings(
                    weightUnit = parseWeightUnit(rows["ironlog_settings"]),
                    prResetAt = parsePersonalBestResetAt(rows[PR_RESET_AT_KEY]),
                )
            }.distinctUntilChanged(),
    ) { history, settings ->
        projectStats(history, settings.weightUnit, clock(), zoneId, settings.prResetAt)
    }.flowOn(Dispatchers.IO)

    override suspend fun snapshot(): StatsUiState = withContext(Dispatchers.IO) {
        projectStats(
            history = statsRepository.completedHistorySnapshot(),
            weightUnit = parseWeightUnit(settingsRepo.getString("ironlog_settings")),
            now = clock(),
            zoneId = zoneId,
            prResetAt = settingsRepo.getPersonalBestResetAt(),
        )
    }
}

private data class StatsProjectionSettings(
    val weightUnit: String,
    val prResetAt: Instant?,
)

private fun parseWeightUnit(raw: String?): String = try {
    raw?.takeIf { it.isNotBlank() }?.let { JSONObject(it).optString("weightUnit", "kg") } ?: "kg"
} catch (_: Exception) { "kg" }
