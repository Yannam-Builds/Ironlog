package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakEngineTest {

    private val engine = StreakEngine()

    private fun entry(date: LocalDate) = HistoryEntry(
        id = date.toString(),
        date = date.toString(),
    )

    // Week of 2026-05-18 is ISO week 21 (Mon May 18 - Sun May 24).
    private val monday = LocalDate.of(2026, 5, 18)

    @Test fun `empty history gives streak 0`() {
        assertEquals(0, engine.computeStreakWeeks(emptyList(), weeklyGoal = 4, recoveryCircuitCompletions = emptyMap()))
    }

    @Test fun `one qualifying week gives streak 1`() {
        val history = (0..3).map { entry(monday.plusDays(it.toLong())) }
        assertEquals(1, engine.computeStreakWeeks(history, 4, emptyMap(), monday.plusDays(3)))
    }

    @Test fun `two consecutive qualifying weeks give streak 2`() {
        val week1 = (0..3).map { entry(monday.plusDays(it.toLong())) }
        val week2 = (7..10).map { entry(monday.plusDays(it.toLong())) }
        val history = week1 + week2
        assertEquals(2, engine.computeStreakWeeks(history, 4, emptyMap(), monday.plusDays(10)))
    }

    @Test fun `gap week without recovery circuit breaks streak`() {
        val week1 = (0..3).map { entry(monday.plusDays(it.toLong())) }
        val week2 = (7..9).map { entry(monday.plusDays(it.toLong())) }
        val week3 = (14..17).map { entry(monday.plusDays(it.toLong())) }
        val history = week1 + week2 + week3
        val result = engine.computeStreakWeeks(history, 4, emptyMap(), monday.plusDays(17))
        assertEquals(1, result)
    }

    @Test fun `gap week with recovery circuit saves streak`() {
        val week1 = (0..3).map { entry(monday.plusDays(it.toLong())) }
        val week2 = (7..9).map { entry(monday.plusDays(it.toLong())) }
        val week3 = (14..17).map { entry(monday.plusDays(it.toLong())) }
        val history = week1 + week2 + week3
        val recoveryCircuits = mapOf("2026-W22" to 1)
        val result = engine.computeStreakWeeks(history, 4, recoveryCircuits, monday.plusDays(17))
        assertEquals(3, result)
    }

    @Test fun `recovery circuit does not save when sessions is goal minus 2 or more short`() {
        val week1 = (0..3).map { entry(monday.plusDays(it.toLong())) }
        val week2 = (7..8).map { entry(monday.plusDays(it.toLong())) }
        val week3 = (14..17).map { entry(monday.plusDays(it.toLong())) }
        val history = week1 + week2 + week3
        val recoveryCircuits = mapOf("2026-W22" to 1)
        val result = engine.computeStreakWeeks(history, 4, recoveryCircuits, monday.plusDays(17))
        assertEquals(1, result)
    }

    @Test fun `old qualifying history is not reported as a current streak`() {
        val history = (0..3).map { entry(monday.plusDays(it.toLong())) }
        assertEquals(0, engine.computeStreakWeeks(history, 4, emptyMap(), monday.plusWeeks(3)))
    }

    @Test fun `unfinished current week preserves immediately previous streak`() {
        val previousWeek = (0..3).map { entry(monday.plusDays(it.toLong())) }
        val currentWeekPartial = listOf(entry(monday.plusWeeks(1)))
        assertEquals(
            1,
            engine.computeStreakWeeks(previousWeek + currentWeekPartial, 4, emptyMap(), monday.plusWeeks(1).plusDays(1)),
        )
    }
}
