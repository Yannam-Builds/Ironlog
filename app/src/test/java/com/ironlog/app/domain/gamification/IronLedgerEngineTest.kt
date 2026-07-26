package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IronLedgerEngineTest {
    private val engine = IronLedgerEngine()

    private fun set(
        id: String,
        weight: Double = 100.0,
        reps: Double = 8.0,
        type: String = "normal",
        rpe: Double? = 8.0,
    ) = HistoryExerciseSet(
        id = id,
        weight = weight,
        reps = reps,
        type = type,
        rpe = rpe,
    )

    private fun workout(
        id: String,
        date: String,
        durationMin: Int = 60,
        imported: Boolean = false,
        exercises: List<HistoryExercise> = listOf(
            HistoryExercise(
                id = "$id-ex1",
                exerciseId = "bench",
                name = "Bench Press",
                primaryMuscle = "Chest",
                category = "strength",
                sets = listOf(set("$id-s1"), set("$id-s2"), set("$id-s3")),
            ),
            HistoryExercise(
                id = "$id-ex2",
                exerciseId = "row",
                name = "Cable Row",
                primaryMuscle = "Back",
                category = "strength",
                sets = listOf(set("$id-s4"), set("$id-s5"), set("$id-s6")),
            ),
            HistoryExercise(
                id = "$id-ex3",
                exerciseId = "squat",
                name = "Squat",
                primaryMuscle = "Quads",
                category = "strength",
                sets = listOf(set("$id-s7"), set("$id-s8")),
            ),
        ),
    ) = HistoryEntry(
        id = id,
        date = date,
        duration = durationMin * 60,
        imported = imported,
        exercises = exercises,
    )

    @Test
    fun `warmups do not create qualifying workout xp`() {
        val history = listOf(
            workout(
                id = "w1",
                date = "2026-05-01T10:00:00Z",
                exercises = listOf(
                    HistoryExercise(
                        id = "warmups",
                        exerciseId = "bench",
                        name = "Bench Press",
                        primaryMuscle = "Chest",
                        category = "strength",
                        sets = List(10) { set("warmup-$it", type = "warmup") },
                    )
                ),
            )
        )

        val snapshot = engine.rebuild(history, weeklyGoal = 4, calibration = AthleteCalibration())

        assertEquals(0L, snapshot.totalXp)
        assertEquals(IronGrade.UNCALIBRATED, snapshot.grade)
    }

    @Test
    fun `same day session spam is capped and lowers integrity`() {
        val spam = (1..6).map {
            workout(
                id = "w$it",
                date = "2026-05-01T1$it:00:00Z",
                durationMin = 45,
            )
        }

        val snapshot = engine.rebuild(spam, weeklyGoal = 4, calibration = AthleteCalibration())

        assertTrue("Daily cap should keep XP below six full sessions", snapshot.totalXp < 6L * 40L)
        assertTrue("Session spam should reduce integrity", snapshot.integrityScore < 0.9)
    }

    @Test
    fun `imported history does not count as verified grade proof`() {
        val imported = (1..800).map {
            workout(
                id = "w$it",
                date = "2026-05-${((it - 1) % 28 + 1).toString().padStart(2, '0')}T10:00:00Z",
                imported = true,
            )
        }

        val snapshot = engine.rebuild(
            history = imported,
            weeklyGoal = 4,
            calibration = AthleteCalibration(importedHistory = true),
        )

        assertTrue(snapshot.grade.ordinal < IronGrade.APEX.ordinal)
        assertEquals(0, snapshot.verifiedSessions)
    }

    @Test
    fun `imported history does not reduce trust for later native workouts`() {
        val imported = workout(
            id = "imported",
            date = "2026-04-01T10:00:00Z",
            imported = true,
        )
        val native = workout(
            id = "native",
            date = "2026-05-01T10:00:00Z",
        )

        val snapshot = engine.rebuild(
            history = listOf(imported, native),
            weeklyGoal = 1,
            calibration = AthleteCalibration(importedHistory = true),
        )

        val importedTrust = snapshot.events.first { it.sourceId == "imported" }.trust
        val nativeTrust = snapshot.events.first { it.sourceId == "native" }.trust
        assertEquals(0.55, importedTrust, 0.0001)
        assertEquals(1.0, nativeTrust, 0.0001)
        assertEquals(1, snapshot.verifiedSessions)
    }

    @Test
    fun `stat attribution rewards cardio as endurance not strength`() {
        val cardio = workout(
            id = "run",
            date = "2026-05-01T10:00:00Z",
            exercises = listOf(
                HistoryExercise(
                    id = "run-ex",
                    exerciseId = "running",
                    name = "Running",
                    primaryMuscle = "Cardio",
                    category = "cardio",
                    sets = listOf(set("run-set", weight = 0.0, reps = 1800.0)),
                )
            ),
        )

        val snapshot = engine.rebuild(listOf(cardio), weeklyGoal = 3, calibration = AthleteCalibration())

        assertTrue(snapshot.stats.endurance > snapshot.stats.strength)
    }

    @Test
    fun `material grade names replace solo ranks`() {
        assertEquals("Graphite", IronGrade.GRAPHITE.label)
        assertEquals("Apex", IronGrade.APEX.label)
    }

    @Test
    fun `repeated exercise blocks create distinct ledger source ids`() {
        val repeatedBlocks = workout(
            id = "w-repeat",
            date = "2026-05-01T10:00:00Z",
            exercises = listOf(
                HistoryExercise(
                    id = "bench-a",
                    exerciseId = "bench",
                    name = "Bench Press",
                    primaryMuscle = "Chest",
                    category = "strength",
                    sets = listOf(set("a1", weight = 100.0), set("a2", weight = 100.0), set("a3", weight = 100.0)),
                ),
                HistoryExercise(
                    id = "bench-b",
                    exerciseId = "bench",
                    name = "Bench Press",
                    primaryMuscle = "Chest",
                    category = "strength",
                    sets = listOf(set("b1", weight = 110.0), set("b2", weight = 110.0), set("b3", weight = 110.0)),
                ),
                HistoryExercise(
                    id = "bench-c",
                    exerciseId = "bench",
                    name = "Bench Press",
                    primaryMuscle = "Chest",
                    category = "strength",
                    sets = listOf(set("c1", weight = 120.0), set("c2", weight = 120.0), set("c3", weight = 120.0)),
                ),
            ),
        )

        val prSourceIds = engine.rebuild(listOf(repeatedBlocks), weeklyGoal = 4, calibration = AthleteCalibration())
            .events
            .filter { it.kind == "pr" }
            .map { it.sourceId }

        assertTrue(prSourceIds.size >= 2)
        assertEquals(prSourceIds.size, prSourceIds.distinct().size)
    }

    @Test
    fun `local date strings still count toward tenure and qualifying weeks`() {
        val history = listOf(
            workout(id = "w1", date = "2026-05-01"),
            workout(id = "w2", date = "2026-05-08"),
            workout(id = "w3", date = "2026-05-15"),
            workout(id = "w4", date = "2026-05-22"),
        )

        val snapshot = engine.rebuild(
            history = history,
            weeklyGoal = 1,
            calibration = AthleteCalibration(),
        )

        assertTrue(snapshot.qualifyingWeeks >= 4)
        assertTrue(snapshot.tenureDays >= 21)
    }
}
