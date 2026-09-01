package com.ironlog.app.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMutationTokenTest {
    @Test
    fun `new request invalidates only older requests for the same setting`() {
        val enabledFirst = NotificationCoordinator.issueMutationToken(NotificationMutationTarget.ENABLED)
        val training = NotificationCoordinator.issueMutationToken(NotificationMutationTarget.TRAINING_REMINDERS)

        assertTrue(NotificationCoordinator.isMutationTokenCurrent(NotificationMutationTarget.ENABLED, enabledFirst))
        assertTrue(NotificationCoordinator.isMutationTokenCurrent(NotificationMutationTarget.TRAINING_REMINDERS, training))

        val enabledLatest = NotificationCoordinator.issueMutationToken(NotificationMutationTarget.ENABLED)

        assertFalse(NotificationCoordinator.isMutationTokenCurrent(NotificationMutationTarget.ENABLED, enabledFirst))
        assertTrue(NotificationCoordinator.isMutationTokenCurrent(NotificationMutationTarget.ENABLED, enabledLatest))
        assertTrue(NotificationCoordinator.isMutationTokenCurrent(NotificationMutationTarget.TRAINING_REMINDERS, training))
    }

    @Test
    fun `non-positive tokens are never current`() {
        assertFalse(NotificationCoordinator.isMutationTokenCurrent(NotificationMutationTarget.REMINDER_TIME, 0L))
        assertFalse(NotificationCoordinator.isMutationTokenCurrent(NotificationMutationTarget.REMINDER_TIME, -1L))
    }
}
