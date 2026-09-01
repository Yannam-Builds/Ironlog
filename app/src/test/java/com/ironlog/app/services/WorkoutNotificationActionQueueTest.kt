package com.ironlog.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutNotificationActionQueueTest {
    @Test fun `rapid actions are consumed in order instead of overwriting`() {
        val now = 2_000_000L
        var raw = WorkoutNotificationActionQueue.enqueue(
            null, "session-a", NotificationActionRouter.Actions.ADD_30S, now,
        )
        raw = WorkoutNotificationActionQueue.enqueue(
            raw, "session-a", NotificationActionRouter.Actions.SKIP_REST, now + 1,
        )

        val first = WorkoutNotificationActionQueue.consume(raw, "session-a", now + 2)
        val second = WorkoutNotificationActionQueue.consume(first.remainingJson, "session-a", now + 3)

        assertEquals(NotificationActionRouter.Actions.ADD_30S, first.actionId)
        assertEquals(NotificationActionRouter.Actions.SKIP_REST, second.actionId)
        assertNull(second.remainingJson)
    }

    @Test fun `an old session action cannot be consumed by a newer workout`() {
        val now = 3_000_000L
        val raw = WorkoutNotificationActionQueue.enqueue(
            null, "old-session", NotificationActionRouter.Actions.FINISH_WORKOUT, now,
        )

        val newer = WorkoutNotificationActionQueue.consume(raw, "new-session", now + 1)
        val old = WorkoutNotificationActionQueue.consume(newer.remainingJson, "old-session", now + 2)

        assertNull(newer.actionId)
        assertEquals(NotificationActionRouter.Actions.FINISH_WORKOUT, old.actionId)
    }

    @Test fun `expired actions are discarded`() {
        val raw = WorkoutNotificationActionQueue.enqueue(
            null, "session-a", NotificationActionRouter.Actions.SKIP_REST, 1_000L,
        )

        val result = WorkoutNotificationActionQueue.consume(raw, "session-a", 10L * 60L * 1_000L)

        assertNull(result.actionId)
        assertNull(result.remainingJson)
    }
}
