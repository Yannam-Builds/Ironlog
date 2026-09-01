package com.ironlog.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.repository.PR_RESET_AT_KEY
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.parsePersonalBestResetAt
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.util.normalizeExerciseNameKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExerciseProgressViewModel(
    private val exerciseName: String,
    private val settingsRepository: SettingsRepository = SettingsRepository(),
) : ViewModel() {
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()
    private val _prResetAtEpochMs = MutableStateFlow<Long?>(null)
    val prResetAtEpochMs: StateFlow<Long?> = _prResetAtEpochMs.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            settingsRepository.observeStrings(setOf(PR_RESET_AT_KEY)).collect { rows ->
                _prResetAtEpochMs.value = parsePersonalBestResetAt(rows[PR_RESET_AT_KEY])?.toEpochMilli()
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        _prResetAtEpochMs.value = settingsRepository.getPersonalBestResetAt()?.toEpochMilli()
        _history.value = withContext(Dispatchers.IO) {
            val exercisesBox = ObjectBox.store.boxFor(ExerciseEntity::class.java)
            val byName = exercisesBox.query(ExerciseEntity_.normalizedName.equal(normalizeExerciseNameKey(exerciseName)))
                .build().use { it.findFirst() }
                ?: exercisesBox.query(ExerciseEntity_.name.equal(exerciseName))
                    .build().use { it.findFirst() }
                ?: return@withContext emptyList()
            com.ironlog.app.data.repository.projectExerciseHistory(
                com.ironlog.app.data.repository.HistoryRepository().completedSnapshotBlocking(),
                byName.uid,
            )
        }
    }
}

class ExerciseProgressViewModelFactory(
    private val exerciseName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExerciseProgressViewModel::class.java)) {
            return ExerciseProgressViewModel(exerciseName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
