package com.ironlog.app.domain.training

import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.ui.model.HistoryExerciseSet
import java.time.Instant
import java.time.ZoneId

/** One timestamp policy for resettable PR projections. */
object PersonalBestPolicy {
    fun occurredAt(
        set: HistoryExerciseSet,
        workoutDate: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Instant? = set.completedAt
        ?.takeIf { it > 0L }
        ?.let { runCatching { Instant.ofEpochMilli(it) }.getOrNull() }
        ?: parseHistoryInstant(workoutDate, zoneId)

    fun isAfterReset(
        set: HistoryExerciseSet,
        workoutDate: String,
        resetAt: Instant?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean = resetAt == null || occurredAt(set, workoutDate, zoneId)?.isAfter(resetAt) == true
}
