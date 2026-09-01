package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class IronLedgerBaselineSeedingTest {
    private val engine = IronLedgerEngine()

    @Test
    fun `onboarding calibration seeds xp rank and stats while workout proof stays empty`() {
        val snapshot = engine.rebuild(
            history = emptyList(),
            weeklyGoal = 5,
            calibration = AthleteCalibration(
                trainingAgeMonths = 19,
                weeklyGoalDays = 5,
                historicalTrainingDaysPerWeek = 4,
                bodyweightKg = 70.0,
                hasPastTraining = true,
                hasGymAccess = true,
                baselinePushups = 40,
                baselinePullups = 14,
                baselineBenchKg = 65,
                baselineLatPulldownKg = 110,
                baselineMileRunSeconds = 570,
            ),
        )

        assertEquals(IronGrade.TITANIUM, snapshot.grade)
        assertEquals(6_600L, snapshot.totalXp)
        assertEquals(5, snapshot.level)
        assertEquals(0, snapshot.verifiedSessions)
        assertTrue(snapshot.events.isEmpty())
        assertTrue(snapshot.stats.strength > 0)
        assertTrue(snapshot.stats.power > 0)
        assertTrue(snapshot.stats.hypertrophy > 0)
        assertTrue(snapshot.stats.endurance > 0)
        assertTrue(snapshot.stats.agility > 0)
        assertTrue(snapshot.stats.discipline > 0)
        assertTrue(snapshot.stats.recovery > 0)
    }

    @Test
    fun `historical training frequency changes the single authoritative baseline`() {
        val lowFrequency = engine.rebuild(
            history = emptyList(),
            weeklyGoal = 5,
            calibration = AthleteCalibration(
                trainingAgeMonths = 24,
                weeklyGoalDays = 5,
                historicalTrainingDaysPerWeek = 1,
                bodyweightKg = 70.0,
                hasPastTraining = true,
                hasGymAccess = true,
                baselinePushups = 18,
                baselinePullups = 5,
                baselineBenchKg = 45,
                baselineLatPulldownKg = 60,
            ),
        )
        val highFrequency = engine.rebuild(
            history = emptyList(),
            weeklyGoal = 5,
            calibration = AthleteCalibration(
                trainingAgeMonths = 24,
                weeklyGoalDays = 5,
                historicalTrainingDaysPerWeek = 5,
                bodyweightKg = 70.0,
                hasPastTraining = true,
                hasGymAccess = true,
                baselinePushups = 18,
                baselinePullups = 5,
                baselineBenchKg = 45,
                baselineLatPulldownKg = 60,
            ),
        )

        assertTrue(highFrequency.grade.ordinal >= lowFrequency.grade.ordinal)
        assertTrue(highFrequency.stats.discipline > lowFrequency.stats.discipline)
        assertTrue(highFrequency.stats.recovery > lowFrequency.stats.recovery)
        assertTrue(highFrequency.totalXp > lowFrequency.totalXp)
    }

    @Test
    fun `verified workout xp is additive and repeated rebuild does not double count baseline`() {
        val calibration = AthleteCalibration(
            trainingAgeMonths = 19,
            weeklyGoalDays = 5,
            historicalTrainingDaysPerWeek = 4,
            bodyweightKg = 70.0,
            hasPastTraining = true,
            hasGymAccess = true,
            baselinePushups = 40,
            baselinePullups = 14,
            baselineBenchKg = 65,
            baselineLatPulldownKg = 110,
            baselineMileRunSeconds = 570,
        )
        val workout = HistoryEntry(
            id = "verified-1",
            date = "2026-08-01T10:00:00Z",
            duration = 3_600,
            exercises = listOf(
                HistoryExercise(
                    id = "bench-block",
                    exerciseId = "bench",
                    name = "Bench Press",
                    primaryMuscle = "Chest",
                    category = "strength",
                    sets = List(8) { index ->
                        HistoryExerciseSet(
                            id = "set-$index",
                            weight = 60.0,
                            reps = 8.0,
                            type = "normal",
                            rpe = 8.0,
                        )
                    },
                ),
            ),
        )

        val first = engine.rebuild(listOf(workout), weeklyGoal = 5, calibration = calibration)
        val retry = engine.rebuild(listOf(workout), weeklyGoal = 5, calibration = calibration)

        assertEquals(56, first.events.single().xp)
        assertEquals(6_656L, first.totalXp)
        assertEquals(first.totalXp, retry.totalXp)
        assertEquals(first.events, retry.events)
    }

    @Test
    fun `proof at or before onboarding replaces matching estimated session xp`() {
        val calibration = richCalibration()
        val baseline = BaselineCalibrationEngine().calculate(calibration)
        val proof = creditedWorkout("old-proof", "2026-08-01T10:00:00Z")
        val baselineAt = Instant.parse("2026-08-15T10:00:00Z")
        val award = effectiveOnboardingBaselineAward(
            baseline = baseline,
            history = listOf(proof),
            baselineOccurredAt = baselineAt,
            zoneId = ZoneId.of("UTC"),
        )
        val engine = IronLedgerEngine(
            ZoneId.of("UTC"),
            Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneId.of("UTC")),
        )

        val snapshot = engine.rebuild(
            history = listOf(proof),
            weeklyGoal = 5,
            calibration = calibration,
            effectiveBaselineXp = award.xp,
        )

        assertEquals(1, award.overlappingProofCount)
        assertEquals(baseline.xp - 20L, award.xp)
        assertEquals(award.xp + snapshot.events.sumOf { it.xp.toLong() }, snapshot.totalXp)
    }

    @Test
    fun `proof after onboarding adds full xp without reducing baseline`() {
        val calibration = richCalibration()
        val baseline = BaselineCalibrationEngine().calculate(calibration)
        val proof = creditedWorkout("new-proof", "2026-08-20T10:00:00Z")
        val award = effectiveOnboardingBaselineAward(
            baseline = baseline,
            history = listOf(proof),
            baselineOccurredAt = Instant.parse("2026-08-15T10:00:00Z"),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(0, award.overlappingProofCount)
        assertEquals(baseline.xp, award.xp)
    }

    @Test
    fun `iridium balanced gate reports verified stats rather than provisional baseline stats`() {
        val zone = ZoneId.of("UTC")
        val history = (0 until 160).map { index ->
            creditedCardioWorkout(
                id = "cardio-$index",
                occurredAt = Instant.parse("2025-01-01T10:00:00Z").plusSeconds(index * 3L * 86_400L),
            )
        }
        val snapshot = IronLedgerEngine(
            zone,
            Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), zone),
        ).rebuild(history, weeklyGoal = 4, calibration = richCalibration())

        assertEquals(IronGrade.OBSIDIAN, snapshot.grade)
        val balance = snapshot.nextGradeGates.single { it.label == "Balanced signals" }
        assertTrue(balance.current < balance.required)
        assertEquals(false, balance.met)
    }

    private fun richCalibration() = AthleteCalibration(
        trainingAgeMonths = 19,
        weeklyGoalDays = 5,
        historicalTrainingDaysPerWeek = 4,
        bodyweightKg = 70.0,
        hasPastTraining = true,
        hasGymAccess = true,
        baselinePushups = 40,
        baselinePullups = 14,
        baselineBenchKg = 65,
        baselineLatPulldownKg = 110,
        baselineMileRunSeconds = 570,
    )

    private fun creditedWorkout(id: String, date: String) = HistoryEntry(
        id = id,
        date = date,
        duration = 3_600,
        exercises = listOf(
            HistoryExercise(
                id = "$id-exercise",
                exerciseId = "bench",
                name = "Bench Press",
                primaryMuscle = "Chest",
                category = "strength",
                sets = List(8) { index ->
                    HistoryExerciseSet(
                        id = "$id-set-$index",
                        weight = 60.0,
                        reps = 8.0,
                        type = "normal",
                        rpe = 8.0,
                    )
                },
            ),
        ),
    )

    private fun creditedCardioWorkout(id: String, occurredAt: Instant) = HistoryEntry(
        id = id,
        date = occurredAt.toString(),
        duration = 1_800,
        exercises = listOf(
            HistoryExercise(
                id = "$id-exercise",
                exerciseId = "run",
                name = "Running",
                category = "cardio",
                trackingType = "cardio",
                sets = listOf(HistoryExerciseSet(id = "$id-set", reps = 600.0, type = "normal")),
            ),
        ),
    )
}
