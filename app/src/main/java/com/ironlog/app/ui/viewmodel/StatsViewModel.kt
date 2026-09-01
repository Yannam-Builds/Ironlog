package com.ironlog.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.StatsRepository
import com.ironlog.app.ui.model.ChartPoint
import com.ironlog.app.ui.model.StatsUiState
import com.ironlog.app.domain.intelligence.ManualRecoveryInput
import com.ironlog.app.domain.intelligence.RecoveryCheckInCodec
import com.ironlog.app.ui.state.presentationClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Clock
import java.time.format.DateTimeFormatter

/** Provides statistics and analytics data for the Stats screen. */
class StatsViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepository(),
    private val statsRepository: StatsRepository = StatsRepository(),
) : ViewModel() {
    private val statsSource = RepositoryStatsUiSource(settingsRepo, statsRepository)
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()
    
    private val _manualRecoveryInput = MutableStateFlow<ManualRecoveryInput?>(null)
    val manualRecoveryInput: StateFlow<ManualRecoveryInput?> = _manualRecoveryInput.asStateFlow()

    init {
        viewModelScope.launch {
            statsSource.observe().collect { _state.value = it }
        }
        viewModelScope.launch {
            manualRecoveryInputFlow(
                settingsRepo.observeStrings(setOf("manual_recovery_input")).map { it["manual_recovery_input"] },
                presentationClock(Clock.systemUTC()),
            ).collect { _manualRecoveryInput.value = it }
        }
    }

    private suspend fun reload() {
        _state.value = buildStats() 
        _manualRecoveryInput.value = loadManualRecovery()
    }

    fun refresh() = viewModelScope.launch { reload() }

    suspend fun saveManualRecovery(input: ManualRecoveryInput) {
        val stamped = input.copy(recordedAt = System.currentTimeMillis())
        val jsonString = com.ironlog.app.domain.intelligence.RecoveryCheckInCodec.encode(stamped, stamped.recordedAt)
        settingsRepo.setString("manual_recovery_input", jsonString, "json")
        _manualRecoveryInput.value = stamped
    }
    
    private suspend fun loadManualRecovery(): ManualRecoveryInput? {
        return com.ironlog.app.domain.intelligence.RecoveryCheckInCodec.decode(
            settingsRepo.getString("manual_recovery_input"), System.currentTimeMillis()
        )
    }

    private suspend fun buildStats(): StatsUiState = statsSource.snapshot()
}

/** A clock tick rechecks cached input; it never polls the database or rebuilds history. */
internal fun manualRecoveryInputFlow(raw: Flow<String?>, time: Flow<Long>): Flow<ManualRecoveryInput?> =
    combine(raw, time) { value, now -> RecoveryCheckInCodec.decode(value, now) }.distinctUntilChanged()

fun buildStreak(datestamps: List<String>): Int {
    if (datestamps.isEmpty()) return 0
    val days = datestamps.toSet().sortedDescending()
    val today = LocalDate.now().toString()
    val yesterday = LocalDate.now().minusDays(1).toString()
    if (days.first() != today && days.first() != yesterday) return 0
    var streak = 0
    var prev = LocalDate.parse(days.first())
    for (d in days) {
        val current = LocalDate.parse(d)
        val diff = java.time.Duration.between(current.atStartOfDay(), prev.atStartOfDay()).toDays()
        if (diff <= 1) { streak += 1; prev = current } else break
    }
    return streak
}

fun build14DayChart(datestamps: List<String>): List<ChartPoint> {
    val counts = datestamps.groupingBy { it }.eachCount()
    val labelIndices = setOf(0, 4, 9, 13)
    val formatter = DateTimeFormatter.ofPattern("dd/MM")
    return (0 until 14).map { i ->
        val day = LocalDate.now().minusDays((13 - i).toLong())
        ChartPoint(value = counts[day.toString()] ?: 0, label = if (i in labelIndices) day.format(formatter) else "")
    }
}
