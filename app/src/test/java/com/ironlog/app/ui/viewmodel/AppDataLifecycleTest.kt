package com.ironlog.app.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.ui.model.StatsUiState
import com.ironlog.app.ui.model.UiPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class AppDataLifecycleTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `loaded empty plans are real data and clearing owner cancels both sources`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val database = MyObjectBox.builder().directory(temporaryFolder.newFolder("appdata")).build()
        val owner = ViewModelStore()
        val plans = TestPlans()
        val stats = TestStats()
        try {
            val settings = SettingsRepository(database.boxFor(AppSettingEntity::class.java))
            val vm = AppDataViewModel(
                exerciseRepo = ExerciseRepository(boxStore = database),
                settingsRepo = settings,
                workoutRepo = WorkoutRepository(settings, database),
                planSource = plans,
                statsSource = stats,
            )
            owner.put("appdata", vm)
            assertEquals(1, plans.active.get())
            assertEquals(1, stats.active.get())
            assertFalse(vm.state.value.plansLoaded)
            plans.rows.emit(emptyList())
            stats.rows.emit(StatsUiState())
            val loaded = withTimeout(5_000) { vm.state.first { it.initialized && it.plansLoaded } }
            assertTrue(loaded.plans.isEmpty())
            assertFalse(loaded.onboardingComplete)

            stats.rows.emit(StatsUiState(pb = mapOf("bench" to 80.0)))
            withTimeout(5_000) { vm.state.first { it.pb["bench"] == 80.0 } }
            stats.rows.emit(StatsUiState(prResetAtEpochMs = 1234L))
            withTimeout(5_000) { vm.state.first { it.prResetAtEpochMs == 1234L } }
            // Settings changes must refresh AppData even when the Stats inputs stay identical.
            settings.setString("ironlog_pb", "{\"bench\":90.0}", "json")
            withTimeout(5_000) { vm.state.first { it.pb["bench"] == 90.0 } }
            owner.clear()
            assertEquals(0, plans.active.get())
            assertEquals(0, stats.active.get())
            stats.rows.emit(StatsUiState(pb = mapOf("bench" to 100.0)))
            assertEquals(90.0, vm.state.value.pb["bench"])
        } finally {
            owner.clear()
            database.closeThreadResources()
            database.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `rapid settings mutations merge instead of overwriting each other`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val database = MyObjectBox.builder().directory(temporaryFolder.newFolder("settings-mutations")).build()
        val owner = ViewModelStore()
        val plans = TestPlans()
        val stats = TestStats()
        try {
            val settings = SettingsRepository(database.boxFor(AppSettingEntity::class.java))
            val vm = AppDataViewModel(
                exerciseRepo = ExerciseRepository(boxStore = database),
                settingsRepo = settings,
                workoutRepo = WorkoutRepository(settings, database),
                planSource = plans,
                statsSource = stats,
            )
            owner.put("appdata", vm)
            plans.rows.emit(emptyList())
            stats.rows.emit(StatsUiState())
            withTimeout(5_000) { vm.state.first { it.initialized } }

            val theme = vm.mutateSettingsAsync { it.copy(theme = "test-theme") }
            val units = vm.mutateSettingsAsync { it.copy(weightUnit = "lbs") }
            joinAll(theme, units)

            assertEquals("test-theme", vm.state.value.settings.theme)
            assertEquals("lbs", vm.state.value.settings.weightUnit)
            val persisted = JSONObject(requireNotNull(settings.getString("ironlog_settings")))
            assertEquals("test-theme", persisted.getString("theme"))
            assertEquals("lbs", persisted.getString("weightUnit"))
        } finally {
            owner.clear()
            database.closeThreadResources()
            database.close()
            Dispatchers.resetMain()
        }
    }

    private class TestPlans : PlanUiSource {
        val active = AtomicInteger()
        val rows = MutableSharedFlow<List<UiPlan>>(replay = 1)
        override fun observe(): Flow<List<UiPlan>> = flow {
            active.incrementAndGet()
            try { emitAll(rows) } finally { active.decrementAndGet() }
        }
        override suspend fun snapshot(): List<UiPlan> = rows.replayCache.lastOrNull().orEmpty()
    }

    private class TestStats : StatsUiSource {
        val active = AtomicInteger()
        val rows = MutableSharedFlow<StatsUiState>(replay = 1)
        override fun observe(): Flow<StatsUiState> = flow {
            active.incrementAndGet()
            try { emitAll(rows) } finally { active.decrementAndGet() }
        }
        override suspend fun snapshot(): StatsUiState = rows.replayCache.lastOrNull() ?: StatsUiState()
    }
}
