package com.ironlog.app.domain.sharing

import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.model.UiPlanExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanQrCodecTest {

    private val codec = PlanQrCodec()

    private fun makePlan(dayCount: Int = 3, exercisesPerDay: Int = 5) = UiPlan(
        id = "plan-1",
        name = "Test Plan",
        goal = "Strength",
        description = "Test",
        days = (1..dayCount).map { d ->
            UiPlanDay(
                id = "day-$d",
                name = "Day $d",
                exercises = (1..exercisesPerDay).map { e ->
                    UiPlanExercise(
                        id = "ex-$d-$e",
                        exerciseId = "exercise-$e",
                        name = "Exercise $e",
                        sets = 4,
                        reps = "8-12",
                        restSeconds = 90,
                    )
                },
            )
        },
    )

    @Test fun `encodeToPaylod produces non-empty string`() {
        val payload = codec.encodeToPayload(makePlan())
        assertTrue(payload.isNotBlank())
    }

    @Test fun `decodeFromPayload round-trips the plan`() {
        val original = makePlan()
        val payload = codec.encodeToPayload(original)
        val decoded = codec.decodeFromPayload(payload)
        assertNotNull(decoded)
        assertEquals(original.name, decoded!!.name)
        assertEquals(original.days.size, decoded.days.size)
        assertEquals(original.days[0].exercises.size, decoded.days[0].exercises.size)
        assertEquals(original.days[0].exercises[0].name, decoded.days[0].exercises[0].name)
    }

    @Test fun `payload for 7-day plan fits QR v40 limit (4296 chars)`() {
        val bigPlan = makePlan(dayCount = 7, exercisesPerDay = 8)
        val payload = codec.encodeToPayload(bigPlan)
        assertTrue("Payload length ${payload.length} exceeds QR v40 limit of 4296",
            payload.length <= 4296)
    }

    @Test fun `decodeFromPayload returns null for garbage input`() {
        val decoded = codec.decodeFromPayload("not-valid-base64!@#$")
        assertEquals(null, decoded)
    }

    @Test fun `decodeFromPayload returns null for empty string`() {
        assertEquals(null, codec.decodeFromPayload(""))
    }

    @Test fun `decodeFromPayload rejects oversized encoded input before decoding`() {
        assertEquals(null, codec.decodeFromPayload("A".repeat(8_193)))
    }

    @Test fun `decodeFromPayload rejects unsafe plan structure`() {
        val invalid = makePlan().copy(days = makePlan().days.map { day ->
            day.copy(exercises = day.exercises.map { it.copy(sets = 0) })
        })
        assertEquals(null, codec.decodeFromPayload(codec.encodeToPayload(invalid)))
    }
}
