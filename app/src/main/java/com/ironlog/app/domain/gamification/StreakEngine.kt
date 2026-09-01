// app/src/main/java/com/ironlog/app/domain/gamification/StreakEngine.kt
package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

class StreakEngine(private val zoneId: ZoneId = ZoneId.systemDefault()) {

    private val isoWeek = WeekFields.ISO

    /**
     * ISO week key, e.g. "2026-W21".
     */
    private fun LocalDate.isoWeekKey(): String {
        val week = get(isoWeek.weekOfWeekBasedYear())
        val year = get(isoWeek.weekBasedYear())
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    /**
     * Computes the current streak in qualifying weeks.
     *
     * A week qualifies if:
     *   - sessions in that week >= [weeklyGoal], OR
     *   - sessions == weeklyGoal - 1 AND [recoveryCircuitCompletions][weekKey] >= 1
     *
     * The streak counts backward from the most recent qualifying week.
     * A missing week (no workouts at all, not even goal-1+recovery circuit) breaks the streak.
     *
     * @param history      All workout history entries.
     * @param weeklyGoal   Sessions required per week (from IronLogSettings.weeklyGoalDays).
     * @param recoveryCircuitCompletions  Map of ISO-week-key -> number of recovery circuits completed.
     */
    fun computeStreakWeeks(
        history: List<HistoryEntry>,
        weeklyGoal: Int,
        recoveryCircuitCompletions: Map<String, Int>,
        asOfDate: LocalDate? = null,
        now: Instant = Instant.now(),
    ): Int {
        if (history.isEmpty()) return 0

        // Group workouts by ISO week key
        // Explicit date is a historical end-of-day query; live callers use the exact instant.
        val evaluationDate = asOfDate ?: now.atZone(zoneId).toLocalDate()
        val asOfInstant = asOfDate?.plusDays(1)?.atStartOfDay(zoneId)?.toInstant()?.minusMillis(1) ?: now
        val sessionsByWeek: Map<String, Int> = history
            .filter { CreditedProof.qualifies(it, asOfInstant, zoneId) }
            .mapNotNull { entry ->
                parseHistoryLocalDate(entry.date, zoneId)?.isoWeekKey()
            }
            .groupingBy { it }
            .eachCount()

        if (sessionsByWeek.isEmpty()) return 0

        // Start from the most recent week that has any sessions
        val goal = weeklyGoal.coerceAtLeast(1)
        val currentMonday = evaluationDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun qualifies(monday: LocalDate): Boolean {
            val weekKey = monday.isoWeekKey()
            val sessions = sessionsByWeek[weekKey] ?: 0
            val recoveryCircuits = recoveryCircuitCompletions[weekKey] ?: 0
            return sessions >= goal || (sessions == goal - 1 && recoveryCircuits >= 1)
        }

        var checkMonday = if (qualifies(currentMonday)) currentMonday else currentMonday.minusWeeks(1)
        if (!qualifies(checkMonday)) return 0

        // Walk backward one ISO week at a time — gaps in sessionsByWeek break the streak
        var streak = 0
        while (qualifies(checkMonday)) {
            streak++
            checkMonday = checkMonday.minusWeeks(1)
        }

        return streak
    }
}
