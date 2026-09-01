package com.ironlog.app.services

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** A paired wall/monotonic timestamp. Wall time survives reboot; elapsed time ignores clock edits. */
internal data class WorkoutClockSnapshot(
    val wallTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val bootCount: Int,
)

internal data class WorkoutTimerDeadline(
    val wallEndMs: Long,
    val elapsedEndMs: Long,
    val bootCount: Int,
) {
    fun remainingMs(now: WorkoutClockSnapshot): Long =
        if (elapsedEndMs > 0L && bootCount >= 0 && bootCount == now.bootCount) {
            elapsedEndMs - now.elapsedRealtimeMs
        } else {
            wallEndMs - now.wallTimeMs
        }

    fun effectiveWallEndMs(now: WorkoutClockSnapshot): Long =
        saturatedTimestampAdd(now.wallTimeMs, remainingMs(now).coerceAtLeast(0L))
}

/** One transactionally-read rest state. Paused time is durable and mutually exclusive with a deadline. */
internal data class WorkoutPersistedRestState(
    val deadline: WorkoutTimerDeadline? = null,
    val pausedRemainingMs: Long = 0L,
)

/** Pure timer math plus the Android clock snapshot used by the workout notification. */
internal object WorkoutTimerClock {
    fun now(context: Context): WorkoutClockSnapshot = WorkoutClockSnapshot(
        wallTimeMs = System.currentTimeMillis(),
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        bootCount = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.BOOT_COUNT,
            -1,
        ),
    )

    fun deadlineAfter(now: WorkoutClockSnapshot, durationMs: Long): WorkoutTimerDeadline {
        val safeDuration = durationMs.coerceAtLeast(0L)
        return WorkoutTimerDeadline(
            wallEndMs = saturatedTimestampAdd(now.wallTimeMs, safeDuration),
            elapsedEndMs = saturatedTimestampAdd(now.elapsedRealtimeMs, safeDuration),
            bootCount = now.bootCount,
        )
    }

    fun elapsedSinceStartMs(
        startWallMs: Long,
        startElapsedMs: Long,
        startBootCount: Int,
        now: WorkoutClockSnapshot,
    ): Long = if (startElapsedMs > 0L && startBootCount >= 0 && startBootCount == now.bootCount) {
        (now.elapsedRealtimeMs - startElapsedMs).coerceAtLeast(0L)
    } else {
        (now.wallTimeMs - startWallMs).coerceAtLeast(0L)
    }

    fun effectiveStartWallMs(
        startWallMs: Long,
        startElapsedMs: Long,
        startBootCount: Int,
        now: WorkoutClockSnapshot,
    ): Long = now.wallTimeMs - elapsedSinceStartMs(
        startWallMs = startWallMs,
        startElapsedMs = startElapsedMs,
        startBootCount = startBootCount,
        now = now,
    )
}

private fun saturatedTimestampAdd(timestampMs: Long, durationMs: Long): Long {
    val safeTimestamp = timestampMs.coerceAtLeast(0L)
    val safeDuration = durationMs.coerceAtLeast(0L)
    return if (safeTimestamp > Long.MAX_VALUE - safeDuration) Long.MAX_VALUE
    else safeTimestamp + safeDuration
}

internal object WorkoutTimerSettingKeys {
    const val REST_END_WALL_MS = "active_workout_rest_end_ms"
    const val REST_END_ELAPSED_MS = "active_workout_rest_end_elapsed_ms"
    const val REST_BOOT_COUNT = "active_workout_rest_boot_count"
    const val REST_PAUSED_REMAINING_MS = "active_workout_rest_paused_remaining_ms"
    const val START_WALL_MS = "active_workout_start_ms"
    const val START_ELAPSED_MS = "active_workout_start_elapsed_ms"
    const val START_BOOT_COUNT = "active_workout_start_boot_count"
}

/**
 * Chooses the next useful foreground-service wake-up. With no rest, Android's chronometer draws
 * elapsed workout time so only a coarse ownership check is needed. During rest, wake only for a
 * 30-second milestone, the final five-second haptics, or completion.
 */
internal fun workoutNotificationUpdaterDelayMs(
    restRemainingMs: Long?,
    ownershipRemainingMs: Long,
): Long {
    val ownershipDelay = ownershipRemainingMs.coerceIn(100L, 30_000L)
    val remaining = restRemainingMs ?: return ownershipDelay
    if (remaining <= 0L) return minOf(ownershipDelay, 100L)
    val seconds = ((remaining + 999L) / 1_000L).coerceAtLeast(1L)
    val restDelay = when {
        seconds <= 5L -> remaining - (seconds - 1L) * 1_000L
        seconds <= 30L -> remaining - 5_000L
        else -> {
            val nextThirtySecondMilestone = ((seconds - 1L) / 30L) * 30L
            remaining - nextThirtySecondMilestone * 1_000L
        }
    }.coerceAtLeast(100L)
    return minOf(ownershipDelay, restDelay)
}

/** Interruptible exponential backoff for persistent service/DB failures; capped at ownership cadence. */
internal fun workoutNotificationUpdaterFailureBackoffMs(consecutiveFailures: Int): Long {
    val exponent = (consecutiveFailures.coerceAtLeast(1) - 1).coerceAtMost(6)
    return (500L shl exponent).coerceAtMost(30_000L)
}
