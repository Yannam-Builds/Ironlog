package com.ironlog.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.repository.BodyMeasurementInput
import com.ironlog.app.data.repository.BodyMeasurementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/** Tracks body measurement entries and history. */
data class BodyMeasurementUiRow(
    val id: String,
    val measuredAt: Long,
    val date: String,
    val bodyweight: Double?,
    val waist: Double?,
    val chest: Double?,
    val arm: Double?,
    val thigh: Double?,
    val notes: String,
)

data class BodyWeightUiRow(
    val id: String,
    val weight: Double,
    val date: String,
)

data class BodyMeasurementsState(
    val measurements: List<BodyMeasurementUiRow> = emptyList(),
) {
    val bodyWeight: List<BodyWeightUiRow>
        get() = measurements
            .filter { it.bodyweight != null }
            .map { BodyWeightUiRow(id = it.id, weight = it.bodyweight ?: 0.0, date = it.date) }
}

class BodyMeasurementsViewModel(
    private val repository: BodyMeasurementRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BodyMeasurementsState())
    val state: StateFlow<BodyMeasurementsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getBodyMeasurementsFlow().collect { rows ->
                _state.value = BodyMeasurementsState(rows.map { it.toUiRow() })
            }
        }
    }

    fun add(input: BodyMeasurementInput) = viewModelScope.launch { repository.addBodyMeasurement(input) }
    fun update(id: String, input: BodyMeasurementInput) = viewModelScope.launch { repository.updateBodyMeasurement(id, input) }
    fun remove(id: String) = viewModelScope.launch { repository.deleteBodyMeasurement(id) }
}

class BodyMeasurementsViewModelFactory(
    private val repository: BodyMeasurementRepository = BodyMeasurementRepository(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BodyMeasurementsViewModel::class.java)) {
            return BodyMeasurementsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun BodyMeasurementEntity.toUiRow(): BodyMeasurementUiRow = BodyMeasurementUiRow(
    id = uid,
    measuredAt = measuredAt,
    date = Instant.ofEpochMilli(measuredAt).toString(),
    bodyweight = bodyweight,
    waist = waist,
    chest = chest,
    arm = arm,
    thigh = thigh,
    notes = notes,
)

