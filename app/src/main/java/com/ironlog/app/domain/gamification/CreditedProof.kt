package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import java.time.Instant
import java.util.Locale
import com.ironlog.app.domain.training.TrainingSetPolicy
import java.time.ZoneId

/** One reward contract shared by the Ledger, streaks, Home, recovery and widgets. */
object CreditedProof {
    fun qualifies(workout: HistoryEntry, now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val occurredAt = parseHistoryInstant(workout.date, zoneId) ?: return false
        if (occurredAt.isAfter(now)) return false
        val hardSets = hardSetCount(workout)
        val cardioMinutes = workout.exercises.sumOf { exercise -> exercise.sets.sumOf { TrainingSetPolicy.cardioSeconds(exercise, it) } / 60.0 }
        if (hardSets == 0 && cardioMinutes <= 0.0) return false
        return hardSets >= 8 || (hardSets >= 3 && workout.duration >= 20 * 60) || cardioMinutes >= 10.0
    }

    fun hardSetCount(workout: HistoryEntry): Int =
        workout.exercises.sumOf { exercise -> exercise.sets.count { TrainingSetPolicy.isValidWorkingSet(exercise, it) } }

    fun isWorkingSet(set: HistoryExerciseSet): Boolean =
        TrainingSetPolicy.isValidWorkingSet(HistoryExercise(), set)

    fun isCardio(exercise: HistoryExercise): Boolean = TrainingSetPolicy.isCardio(exercise)
}
