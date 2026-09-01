package com.ironlog.app.services

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.PlanDayEntity
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise
import com.ironlog.app.ui.model.HistoryExerciseSet
import com.ironlog.app.navigation.isAllowedPendingRoute
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONArray
import org.json.JSONObject

class NotificationCandidatePolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val store by lazy {
        MyObjectBox.builder().directory(temporaryFolder.newFolder("objectbox")).build()
    }

    @After
    fun closeStore() {
        if (!store.isClosed) store.close()
    }

    @Test
    fun `completed no-delivery evaluations clear stale reminders but stale workers do not`() {
        val authoritativeNoDeliveryResults = listOf(
            NotificationDeliveryResult.NO_CANDIDATE,
            NotificationDeliveryResult.APP_DISABLED,
            NotificationDeliveryResult.QUIET_HOURS,
            NotificationDeliveryResult.PERMISSION_DENIED,
            NotificationDeliveryResult.CHANNEL_BLOCKED,
            NotificationDeliveryResult.POLICY_SUPPRESSED,
            NotificationDeliveryResult.APP_FOREGROUND,
            NotificationDeliveryResult.APP_OPEN_ACKNOWLEDGED,
        )

        assertTrue(authoritativeNoDeliveryResults.all(::shouldClearVisibleReminderAfterEvaluation))
        assertFalse(shouldClearVisibleReminderAfterEvaluation(NotificationDeliveryResult.SENT))
        assertFalse(shouldClearVisibleReminderAfterEvaluation(NotificationDeliveryResult.STALE_SCHEDULE))
    }

    @Test
    fun `opening the app acknowledges only occurrences that were already due`() {
        assertFalse(reminderOccurrenceAcknowledgedByAppOpen(0L, 2_000L, 2_000L))
        assertFalse(reminderOccurrenceAcknowledgedByAppOpen(3_000L, 2_000L, 3_000L))
        assertTrue(reminderOccurrenceAcknowledgedByAppOpen(2_000L, 2_000L, 2_000L))
        assertTrue(reminderOccurrenceAcknowledgedByAppOpen(2_000L, 3_000L, 3_000L))
    }

    @Test
    fun `future app-open timestamp after clock rollback cannot suppress reminders indefinitely`() {
        val now = 2_000L
        val implausibleFutureAck = now + MAX_APP_OPEN_ACK_FUTURE_SKEW_MS + 1L

        assertFalse(reminderOccurrenceAcknowledgedByAppOpen(1_000L, implausibleFutureAck, now))
    }

    @Test
    fun `expired occurrence clears only its own or an older visible reminder`() {
        assertTrue(expiredOccurrenceMayClearVisibleReminder(
            requestGeneration = 7L,
            currentGeneration = 7L,
            expiredOccurrenceEpochMillis = 2_000L,
            visibleGeneration = 7L,
            visibleOccurrenceEpochMillis = 2_000L,
        ))
        assertTrue(expiredOccurrenceMayClearVisibleReminder(
            requestGeneration = 7L,
            currentGeneration = 7L,
            expiredOccurrenceEpochMillis = 2_000L,
            visibleGeneration = 7L,
            visibleOccurrenceEpochMillis = 1_000L,
        ))
        assertFalse(expiredOccurrenceMayClearVisibleReminder(
            requestGeneration = 7L,
            currentGeneration = 7L,
            expiredOccurrenceEpochMillis = 1_000L,
            visibleGeneration = 7L,
            visibleOccurrenceEpochMillis = 2_000L,
        ))
        assertFalse(expiredOccurrenceMayClearVisibleReminder(
            requestGeneration = 6L,
            currentGeneration = 7L,
            expiredOccurrenceEpochMillis = 2_000L,
            visibleGeneration = 7L,
            visibleOccurrenceEpochMillis = 1_000L,
        ))
        assertFalse(expiredOccurrenceMayClearVisibleReminder(
            requestGeneration = 0L,
            currentGeneration = 0L,
            expiredOccurrenceEpochMillis = 0L,
            visibleGeneration = 0L,
            visibleOccurrenceEpochMillis = 0L,
        ))
    }

    @Test
    fun `training reminder opt out removes training and streak candidates`() {
        val planBox = store.boxFor(PlanEntity::class.java)
        val plan = PlanEntity().apply {
            uid = "seven-day-plan"
            name = "Every day"
            isActive = true
            createdAt = 1L
            updatedAt = 1L
        }
        planBox.put(plan)
        repeat(7) { index ->
            store.boxFor(PlanDayEntity::class.java).put(PlanDayEntity().apply {
                uid = "day-$index"
                this.plan.target = plan
                planUid = plan.uid
                name = "Day ${index + 1}"
                orderIndex = index
                createdAt = 1L
                updatedAt = 1L
            })
        }
        putSetting("training_reminders_enabled", "false", "boolean")
        putSetting("ironlog_settings", "{\"weeklyGoalDays\":7}", "json")

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
        )

        assertFalse(candidates.any { it.topic == "training" || it.topic == "streak" })
    }

    @Test
    fun `an authoritative active workout suppresses every re engagement candidate`() {
        val nowMs = System.currentTimeMillis()
        store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply {
            uid = "active-session"
            name = "Legs"
            status = "active"
            startedAt = nowMs - 10 * 60_000L
            createdAt = startedAt
            updatedAt = startedAt
        })
        putSetting("active_workout_id", "active-session", "string")
        putSetting("auto_backup_enabled", "true", "boolean")
        putSetting("ironlog_settings", "{\"weeklyGoalDays\":7}", "json")

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
            nowMs = nowMs,
            historyOverride = listOf(meaningfulHistory(
                "old-workout",
                nowMs - 5L * 24L * 60L * 60L * 1_000L,
            )),
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `future decision log timestamps do not suppress reminders after clock rollback`() {
        val nowMs = System.currentTimeMillis()
        putSetting(
            "decision_log_json",
            JSONArray().put(JSONObject()
                .put("outcome", "sent")
                .put("key", "train_reminder_plan_aware")
                .put("topic", "training")
                .put("at", java.time.Instant.ofEpochMilli(nowMs + 3_600_000L).toString()))
                .toString(),
            "json",
        )

        val decision = NotificationPolicyEngine.shouldSend(
            settingsBox = store.boxFor(AppSettingEntity::class.java),
            key = "train_reminder_plan_aware",
            topic = "training",
            nowMs = nowMs,
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun `empty accidental completion does not trigger bodyweight reminders`() {
        val nowMs = System.currentTimeMillis()
        store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply {
            uid = "empty-completion"
            name = "Empty"
            status = "completed"
            startedAt = nowMs - 3_600_000L
            completedAt = nowMs - 3_500_000L
            createdAt = startedAt
            updatedAt = completedAt ?: nowMs
        })

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
            nowMs = nowMs,
            historyOverride = listOf(HistoryEntry(
                id = "empty-completion",
                name = "Empty",
                date = java.time.Instant.ofEpochMilli(nowMs - 3_600_000L).toString(),
                duration = 100,
                exercises = emptyList(),
            )),
        )

        assertFalse(candidates.any { it.key == "bw_reminder" })
    }

    @Test
    fun `backup reminder is absent when automatic backups are disabled`() {
        store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply {
            uid = "completed-workout"
            name = "Push"
            status = "completed"
            startedAt = System.currentTimeMillis() - 4L * 24L * 60L * 60L * 1000L
            completedAt = startedAt + 3_600_000L
            createdAt = startedAt
            updatedAt = startedAt
        })
        putSetting("auto_backup_enabled", "false", "boolean")

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
        )

        assertFalse(candidates.any { it.key == "backup_integrity" })
    }

    @Test
    fun `candidate destinations match the action promised in their copy`() {
        val old = System.currentTimeMillis() - 5L * 24L * 60L * 60L * 1000L
        store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply {
            uid = "route-workout"
            name = "Pull"
            status = "completed"
            startedAt = old
            completedAt = old + 3_600_000L
            createdAt = old
            updatedAt = old
        })
        putSetting("auto_backup_enabled", "true", "boolean")
        putSetting("training_reminders_enabled", "true", "boolean")
        putSetting("ironlog_settings", "{\"weeklyGoalDays\":7}", "json")

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
            historyOverride = listOf(meaningfulHistory("route-workout", old)),
        )

        assertEquals("BodyWeight", candidates.first { it.key == "bw_reminder" }.route)
        assertEquals("BackupCenter", candidates.first { it.key == "backup_integrity" }.route)
        assertEquals("Home", candidates.first { it.key == "train_reminder_plan_aware" }.route)
        assertTrue(candidates.all { isAllowedPendingRoute(it.route) })
    }

    @Test
    fun `a measurement without bodyweight does not suppress the bodyweight reminder`() {
        val zone = java.time.ZoneId.of("UTC")
        val now = java.time.ZonedDateTime.of(2026, 9, 2, 12, 0, 0, 0, zone).toInstant()
        store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply {
            uid = "prior-workout"
            name = "Pull"
            status = "completed"
            startedAt = now.minusSeconds(86_400L).toEpochMilli()
            completedAt = now.minusSeconds(82_800L).toEpochMilli()
            createdAt = startedAt
            updatedAt = now.minusSeconds(82_800L).toEpochMilli()
        })
        store.boxFor(BodyMeasurementEntity::class.java).put(BodyMeasurementEntity().apply {
            measuredAt = now.minusSeconds(300L).toEpochMilli()
            waist = 80.0
            bodyweight = null
            createdAt = measuredAt
            updatedAt = measuredAt
        })

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
            nowMs = now.toEpochMilli(),
            zoneId = zone,
            historyOverride = listOf(meaningfulHistory(
                id = "prior-workout",
                startedAtMs = now.minusSeconds(86_400L).toEpochMilli(),
            )),
        )

        assertTrue(candidates.any { it.key == "bw_reminder" })
    }

    @Test
    fun `canonical conservative profile enforces its weekly cap`() {
        val now = java.time.ZonedDateTime.of(
            2026, 9, 2, 12, 0, 0, 0, java.time.ZoneId.systemDefault(),
        ).toInstant()
        val decisions = org.json.JSONArray()
            .put(org.json.JSONObject().put("outcome", "sent").put("key", "older-a").put("topic", "a").put("at", now.minusSeconds(7_200).toString()))
            .put(org.json.JSONObject().put("outcome", "sent").put("key", "older-b").put("topic", "b").put("at", now.minusSeconds(3_600).toString()))
        putSetting("decision_log_json", decisions.toString(), "json")
        putSetting("maxNotificationsPerDayOverride", "3", "number")
        putSetting("notification_profile", "conservative", "string")

        assertFalse(NotificationPolicyEngine.shouldSend(
            store.boxFor(AppSettingEntity::class.java), "new-key", "new-topic", now.toEpochMilli(),
        ).allowed)

        putSetting("notification_profile", "aggressive", "string")
        assertTrue(NotificationPolicyEngine.shouldSend(
            store.boxFor(AppSettingEntity::class.java), "new-key", "new-topic", now.toEpochMilli(),
        ).allowed)
    }

    @Test
    fun `empty completed row does not suppress a needed training reminder`() {
        val zone = java.time.ZoneId.of("UTC")
        val now = java.time.ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, zone).toInstant()
        store.boxFor(WorkoutEntity::class.java).put(WorkoutEntity().apply {
            uid = "empty-row"
            name = "Accidental finish"
            status = "completed"
            startedAt = now.minusSeconds(3_600).toEpochMilli()
            completedAt = now.minusSeconds(3_000).toEpochMilli()
            createdAt = startedAt
            updatedAt = now.minusSeconds(3_000).toEpochMilli()
        })
        putSetting("ironlog_settings", "{\"weeklyGoalDays\":1}", "json")

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
            nowMs = now.toEpochMilli(),
            zoneId = zone,
            historyOverride = listOf(HistoryEntry(
                id = "empty-row",
                name = "Accidental finish",
                date = now.minusSeconds(3_600).toString(),
                duration = 600,
                exercises = emptyList(),
            )),
        )

        assertTrue(candidates.any { it.key == "train_reminder_plan_aware" })
    }

    @Test
    fun `streak reminder uses the canonical weekly goal streak through last week`() {
        val zone = java.time.ZoneId.of("UTC")
        val now = java.time.ZonedDateTime.of(2026, 9, 8, 12, 0, 0, 0, zone).toInstant()
        val proofDate = now.minusSeconds(6L * 24L * 60L * 60L)
        putSetting("ironlog_settings", "{\"weeklyGoalDays\":1}", "json")
        val proof = HistoryEntry(
            id = "last-week-proof",
            name = "Full body",
            date = proofDate.toString(),
            duration = 1_800,
            exercises = listOf(HistoryExercise(
                id = "exercise-row",
                exerciseId = "squat",
                name = "Squat",
                sets = List(8) { index ->
                    HistoryExerciseSet(id = "set-$index", weight = 50.0, reps = 8.0)
                },
            )),
        )

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
            nowMs = now.toEpochMilli(),
            zoneId = zone,
            historyOverride = listOf(proof),
        )

        assertTrue(candidates.any { it.key == "streak_preserve" })
    }

    @Test
    fun `streak reminder states the actual sessions remaining`() {
        val zone = java.time.ZoneId.of("UTC")
        val now = java.time.ZonedDateTime.of(2026, 9, 8, 12, 0, 0, 0, zone).toInstant()
        putSetting("ironlog_settings", "{\"weeklyGoalDays\":2}", "json")
        val history = listOf(
            creditedProof("last-week-a", now.minusSeconds(6L * 86_400L)),
            creditedProof("last-week-b", now.minusSeconds(5L * 86_400L)),
        )

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
            nowMs = now.toEpochMilli(),
            zoneId = zone,
            historyOverride = history,
        )

        assertEquals(
            "2 sessions remain to protect this week's streak.",
            candidates.first { it.key == "streak_preserve" }.body,
        )
    }

    @Test
    fun `recovery circuit protected week does not emit a false streak warning`() {
        val zone = java.time.ZoneId.of("UTC")
        val now = java.time.ZonedDateTime.of(2026, 9, 8, 12, 0, 0, 0, zone).toInstant()
        putSetting("ironlog_settings", "{\"weeklyGoalDays\":2}", "json")
        store.boxFor(GamificationProfileEntity::class.java).put(GamificationProfileEntity().apply {
            offlineUserId = "local"
            makeupCompletionsJson = "{\"2026-W37\":1}"
        })
        val history = listOf(
            creditedProof("last-week-a", now.minusSeconds(6L * 86_400L)),
            creditedProof("last-week-b", now.minusSeconds(5L * 86_400L)),
            creditedProof("this-week", now.minusSeconds(86_400L)),
        )

        val candidates = SmartCandidateGenerator.buildCandidates(
            store = store,
            nowMs = now.toEpochMilli(),
            zoneId = zone,
            historyOverride = history,
        )

        assertFalse(candidates.any { it.key == "streak_preserve" })
    }

    private fun creditedProof(id: String, at: java.time.Instant): HistoryEntry = HistoryEntry(
        id = id,
        name = "Qualifying session",
        date = at.toString(),
        duration = 1_800,
        exercises = listOf(HistoryExercise(
            id = "exercise-$id",
            exerciseId = "squat",
            name = "Squat",
            sets = List(8) { index ->
                HistoryExerciseSet(id = "$id-set-$index", weight = 50.0, reps = 8.0)
            },
        )),
    )

    private fun putSetting(key: String, value: String, valueType: String) {
        store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
            this.key = key
            this.value = value
            this.valueType = valueType
            updatedAt = System.currentTimeMillis()
        })
    }

    private fun meaningfulHistory(id: String, startedAtMs: Long): HistoryEntry = HistoryEntry(
        id = id,
        name = "Pull",
        date = java.time.Instant.ofEpochMilli(startedAtMs).toString(),
        duration = 3_600,
        exercises = listOf(HistoryExercise(
            id = "$id-exercise",
            exerciseId = "pull-up",
            name = "Pull-up",
            sets = listOf(HistoryExerciseSet(
                id = "$id-set",
                weight = 20.0,
                reps = 5.0,
                isWarmup = false,
            )),
        )),
    )
}
