package com.ironlog.app.ui.screens

import com.ironlog.app.ui.screens.intelligence.recommendedPlanDayId
import com.ironlog.app.ui.screens.stats.parseHistoryEditTimestampOrNull
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.model.UiPlanDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParityClosureLogicTest {
    @Test
    fun recommendedPlanDayStartsFirstDayWhenNoHistoryMatches() {
        val plan = planOf("push", "pull", "legs")

        val result = recommendedPlanDayId(plan, emptyList())

        assertEquals("push", result)
    }

    @Test
    fun recommendedPlanDayAdvancesAfterLastCompletedPlanDay() {
        val plan = planOf("push", "pull", "legs")
        val history = listOf(
            HistoryEntry(id = "w2", date = "2026-05-18T12:00:00Z", planDayUid = "pull"),
            HistoryEntry(id = "w1", date = "2026-05-17T12:00:00Z", planDayUid = "push"),
        )

        val result = recommendedPlanDayId(plan, history)

        assertEquals("legs", result)
    }

    @Test
    fun recommendedPlanDayWrapsToFirstDay() {
        val plan = planOf("push", "pull", "legs")
        val history = listOf(
            HistoryEntry(id = "w3", date = "2026-05-18T12:00:00Z", planDayUid = "legs"),
        )

        val result = recommendedPlanDayId(plan, history)

        assertEquals("push", result)
    }

    @Test
    fun strictHistoryEditTimestampRejectsInvalidInput() {
        assertNull(parseHistoryEditTimestampOrNull("2026-99-99", "25:99"))
    }

    @Test
    fun strictHistoryEditTimestampParsesValidLocalInput() {
        val parsed = parseHistoryEditTimestampOrNull("2026-05-18", "09:30")

        requireNotNull(parsed)
        assert(parsed.contains("T"))
    }

    private fun planOf(vararg ids: String) = UiPlan(
        id = "plan",
        name = "Plan",
        days = ids.map { UiPlanDay(id = it, name = it) },
    )
}
