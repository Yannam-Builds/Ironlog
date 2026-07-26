// app/src/main/java/com/ironlog/app/domain/gamification/StatEngine.kt
package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.roundToInt

@Serializable
data class RpgStats(
    val str: Int = 1,  // Strength - best estimated 1RM across all exercises
    val vit: Int = 1,  // Vitality - streak + total sessions
    val end: Int = 1,  // Endurance - average workout duration
    val agi: Int = 1,  // Agility - exercise variety per session
    val wis: Int = 1,  // Wisdom - RPE/effort tracking frequency (future; now = 1)
    val luk: Int = 1,  // Luck - rare events such as PRs, streaks, and high-effort proof
)

class StatEngine {

    /** Epley formula: 1RM ~= weight * (1 + reps/30). */
    private fun epley1rm(weight: Double, reps: Double): Double =
        weight * (1.0 + reps / 30.0)

    /**
     * Map a raw metric to a 1-999 stat value using logarithmic scaling.
     * [value] is the raw metric; [scale] controls how quickly stat grows.
     */
    private fun toStat(value: Double, scale: Double): Int =
        (1 + (ln(1.0 + value / scale) * 200.0)).roundToInt().coerceIn(1, 999)

    fun compute(
        history: List<HistoryEntry>,
        streak: Int,
        totalSessions: Int,
    ): RpgStats {
        if (history.isEmpty()) return RpgStats()

        // STR - best estimated 1RM across all sets in all history.
        val bestOrm: Double = history.flatMap { it.exercises }
            .flatMap { it.sets }
            .filter { it.type != "warmup" && it.reps > 0 && it.weight > 0 }
            .maxOfOrNull { epley1rm(it.weight, it.reps) } ?: 0.0

        // END - average workout duration in minutes.
        val avgDurationMin = history.map { it.duration / 60.0 }.average()

        // AGI - average distinct exercises per session.
        val avgExercises = history.map { it.exercises.size.toDouble() }.average()

        // VIT - blend of streak weeks and total sessions.
        val vitRaw = streak * 5.0 + totalSessions.toDouble()

        // WIS - fraction of sets that have RPE logged, scaled to encourage consistent tracking.
        val totalSets = history.sumOf { it.exercises.sumOf { ex -> ex.sets.size } }.toDouble()
        val rpeTrackedSets = history.sumOf { entry ->
            entry.exercises.sumOf { ex -> ex.sets.count { it.rpe != null } }
        }.toDouble()
        val wisRaw = if (totalSets > 0) (rpeTrackedSets / totalSets) * 100.0 else 0.0
        val wis = toStat(wisRaw, scale = 20.0)

        // LUK - high-effort proof events, approximated as high-RPE sets for now.
        val luk = toStat(
            value = history.sumOf { entry ->
                entry.exercises.sumOf { ex ->
                    ex.sets.count { it.rpe != null && (it.rpe ?: 0.0) >= 9.0 }.toDouble()
                }
            },
            scale = 20.0,
        )

        return RpgStats(
            str = toStat(bestOrm, scale = 100.0),
            vit = toStat(vitRaw, scale = 50.0),
            end = toStat(avgDurationMin, scale = 30.0),
            agi = toStat(avgExercises, scale = 5.0),
            wis = wis,
            luk = luk,
        )
    }
}
