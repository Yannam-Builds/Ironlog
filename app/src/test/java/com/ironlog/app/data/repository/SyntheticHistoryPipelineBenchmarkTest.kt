package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import com.ironlog.app.domain.gamification.*
import com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
import java.time.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Opt-in backend baseline, not a frame-rate or physical-device performance claim. */
class SyntheticHistoryPipelineBenchmarkTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun measureSyntheticPipelines() {
        assumeTrue(System.getenv("IRONLOG_RUN_BENCHMARK") == "1")
        val clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC)
        listOf(0, 1_000, 10_000).forEach { count ->
            MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
                val library = listOf("weight_reps", "bodyweight_reps", "assisted_bodyweight", "duration").mapIndexed { i, mode ->
                    ExerciseEntity().apply { uid = "exercise-$i"; name = "Synthetic $i"; trackingType = mode; primaryMuscle = "chest"; category = "strength" }
                }
                store.runInTx {
                    store.boxFor(ExerciseEntity::class.java).put(library)
                    repeat(count) { wi ->
                        val started = clock.millis() - (count - wi) * 86_400_000L
                        store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply {
                            uid = "workout-$wi"; status = "completed"; startedAt = started; durationSeconds = 1800
                        })
                        library.forEachIndexed { ei, ex ->
                            val rowId = "row-$wi-$ei"
                            store.boxFor(WorkoutExerciseEntity::class.java).put(WorkoutExerciseEntity().apply {
                                uid = rowId; workoutUid = "workout-$wi"; exerciseUid = ex.uid; orderIndex = ei
                            })
                            store.boxFor(WorkoutSetEntity::class.java).put(List(3) { si -> WorkoutSetEntity().apply {
                                uid = "set-$wi-$ei-$si"; workoutExerciseUid = rowId; setIndex = si
                                weight = if (ei == 0) 60.0 else if (ei == 2) 10.0 else 0.0
                                reps = if (ei == 3) 30.0 else 8.0; rir = 2.0
                            } })
                        }
                    }
                }
                val samples = mutableListOf<List<Double>>()
                repeat(13) { iteration ->
                    val begin = System.nanoTime()
                    val history = HistoryRepository(store).completedSnapshotBlocking()
                    val projected = System.nanoTime()
                    val recovery = RecoveryReadinessEngine.snapshot(history, nowEpochMs = clock.millis(), zoneId = clock.zone)
                    val recovered = System.nanoTime()
                    val ledger = IronLedgerEngine(clock.zone, clock).rebuild(history, 4, AthleteCalibration())
                    val end = System.nanoTime()
                    assertEquals(count, history.size)
                    assertTrue(ledger.totalXp >= 0L)
                    assertTrue(recovery.readiness.values.all { it.isFinite() })
                    if (iteration >= 3) samples += listOf(projected - begin, recovered - projected, end - recovered, end - begin).map { it / 1_000_000.0 }
                }
                listOf("query+projection", "recovery", "ledger", "combined").forEachIndexed { i, label ->
                    val sorted = samples.map { it[i] }.sorted()
                    println("SYNTHETIC_BENCH workouts=$count links=${count * 4} sets=${count * 12} stage=$label medianMs=${sorted[sorted.size / 2]} p95Ms=${sorted.last()} rawMs=$sorted")
                }
                store.closeThreadResources()
            }
        }
    }
}
