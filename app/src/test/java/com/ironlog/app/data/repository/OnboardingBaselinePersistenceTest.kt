package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity
import com.ironlog.app.data.objectbox.IronLedgerEventEntity
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.domain.gamification.ONBOARDING_BASELINE_EVENT_ID
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingBaselinePersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `retry upserts one provenance event without double xp or overwriting badge times`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val profileBox = store.boxFor(GamificationProfileEntity::class.java)
            profileBox.put(
                GamificationProfileEntity(
                    offlineUserId = "local",
                    unlockedBadges = "first_pr,first_workout",
                    badgeUnlocksJson = "{\"first_pr\":111,\"first_workout\":5000}",
                ),
            )
            val calibration = richCalibrationEntity()

            val first = persistOnboardingBaselineAtomically(
                store = store,
                calibration = calibration,
                bodyweightKg = 70.0,
                occurredAtMs = 1_000L,
                zoneId = ZoneId.of("UTC"),
            )
            val retry = persistOnboardingBaselineAtomically(
                store = store,
                calibration = calibration,
                bodyweightKg = 70.0,
                occurredAtMs = 2_000L,
                zoneId = ZoneId.of("UTC"),
            )

            assertEquals(first.fingerprint, retry.fingerprint)
            val events = store.boxFor(IronLedgerEventEntity::class.java).all
            assertEquals(1, events.size)
            val event = events.single()
            assertEquals(ONBOARDING_BASELINE_EVENT_ID, event.eventId)
            assertEquals("onboarding", event.sourceType)
            assertEquals("baseline_calibration", event.eventKind)
            assertEquals(6_600, event.xpDelta)
            assertEquals(0.5, event.trustScore, 0.0)
            assertEquals(1_000L, event.occurredAt)
            assertEquals(first.fingerprint, event.fingerprint)
            val metadata = JSONObject(event.metadataJson)
            assertEquals("onboarding_self_report", metadata.getString("provenance"))
            assertEquals(330, metadata.getInt("estimatedLifetimeSessions"))
            assertEquals("trainingAgeMonths*4.345*historicalTrainingDaysPerWeek", metadata.getString("formula"))
            assertEquals(0, metadata.getInt("overlappingProofCount"))
            assertEquals(6_600L, metadata.getLong("rawBaselineXp"))
            assertEquals(6_600L, metadata.getLong("effectiveBaselineXp"))

            val profile = profileBox.all.single()
            assertEquals(6_600L, profile.totalXp)
            assertEquals("Titanium", profile.rank)
            assertTrue("first_pr" in profile.unlockedBadges)
            assertFalse("first_rest_timer" in profile.unlockedBadges)
            val unlocks = JSONObject(profile.badgeUnlocksJson!!)
            assertEquals(111L, unlocks.getLong("first_pr"))
            assertEquals(5_000L, unlocks.getLong("first_workout"))
            assertEquals(1_000L, unlocks.getLong("workouts_100"))
        }
    }

    @Test
    fun `outer transaction failure rolls back calibration event and profile together`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            assertTrue(
                runCatching {
                    store.runInTx {
                        persistOnboardingBaselineAtomically(
                            store = store,
                            calibration = richCalibrationEntity(),
                            bodyweightKg = 70.0,
                            occurredAtMs = 1_000L,
                            zoneId = ZoneId.of("UTC"),
                        )
                        error("injected failure")
                    }
                }.isFailure,
            )

            assertEquals(0L, store.boxFor(AthleteCalibrationEntity::class.java).count())
            assertEquals(0L, store.boxFor(IronLedgerEventEntity::class.java).count())
            assertEquals(0L, store.boxFor(GamificationProfileEntity::class.java).count())
            assertEquals(0L, store.boxFor(AppSettingEntity::class.java).count())
            assertEquals(0L, store.boxFor(BodyMeasurementEntity::class.java).count())
        }
    }

    @Test
    fun `unclaimed onboarding baseline persists no xp badges or bodyweight history`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            persistOnboardingBaselineAtomically(
                store = store,
                calibration = AthleteCalibrationEntity(
                    offlineUserId = "local",
                    trainingAgeMonths = 0,
                    bodyweightKg = null,
                    hasPastTraining = false,
                ),
                bodyweightKg = null,
                occurredAtMs = 1_000L,
                zoneId = ZoneId.of("UTC"),
            )

            val profile = store.boxFor(GamificationProfileEntity::class.java).all.single()
            assertEquals(0L, profile.totalXp)
            assertEquals("Uncalibrated", profile.rank)
            assertTrue(profile.unlockedBadges.isBlank())
            assertEquals(0L, store.boxFor(BodyMeasurementEntity::class.java).count())
        }
    }

    @Test
    fun `first baseline commit preserves preexisting legacy bonus xp`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val profileBox = store.boxFor(GamificationProfileEntity::class.java)
            profileBox.put(
                GamificationProfileEntity(
                    offlineUserId = "local",
                    totalXp = 1_000L,
                ),
            )

            persistOnboardingBaselineAtomically(
                store = store,
                calibration = richCalibrationEntity(),
                bodyweightKg = 70.0,
                occurredAtMs = 1_000L,
                zoneId = ZoneId.of("UTC"),
            )

            assertEquals(7_600L, profileBox.all.single().totalXp)
            val events = store.boxFor(IronLedgerEventEntity::class.java).all
            assertEquals(2, events.size)
            assertEquals(
                1_000,
                events.single { it.eventId == "legacy:bonus-v1" }.xpDelta,
            )
            assertEquals(
                "true",
                SettingsRepository(store.boxFor(AppSettingEntity::class.java))
                    .getStringBlocking("gamification_bonus_events_v2_migrated"),
            )
        }
    }

    @Test
    fun `corrected lower retry removes only prior provisional rewards`() {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val profileBox = store.boxFor(GamificationProfileEntity::class.java)
            profileBox.put(
                GamificationProfileEntity(
                    offlineUserId = "local",
                    unlockedBadges = "first_pr,founder",
                    badgeUnlocksJson = "{\"first_pr\":111,\"founder\":222}",
                ),
            )
            persistOnboardingBaselineAtomically(
                store,
                richCalibrationEntity(),
                bodyweightKg = 70.0,
                occurredAtMs = 1_000L,
                zoneId = ZoneId.of("UTC"),
            )
            persistOnboardingBaselineAtomically(
                store,
                AthleteCalibrationEntity(
                    offlineUserId = "local",
                    trainingAgeMonths = 0,
                    historicalTrainingDaysPerWeek = 1,
                    bodyweightKg = null,
                    weeklyGoalDays = 1,
                    hasPastTraining = false,
                    hasGymAccess = false,
                    updatedAt = 2_000L,
                ),
                bodyweightKg = null,
                occurredAtMs = 2_000L,
                zoneId = ZoneId.of("UTC"),
            )

            val profile = profileBox.all.single()
            val badges = profile.unlockedBadges.split(',').filter(String::isNotBlank).toSet()
            assertEquals(setOf("first_pr", "founder"), badges)
            assertEquals(setOf("first_pr", "founder"), JSONObject(profile.badgeUnlocksJson!!).keySet())
            assertEquals(0L, profile.totalXp)
            assertEquals("Uncalibrated", profile.rank)
            assertEquals(0L, store.boxFor(BodyMeasurementEntity::class.java).count())
            assertEquals(
                "0",
                SettingsRepository(store.boxFor(AppSettingEntity::class.java))
                    .getStringBlocking("baseline_bodyweight_kg"),
            )
        }
    }

    private fun richCalibrationEntity() = AthleteCalibrationEntity(
        offlineUserId = "local",
        trainingAgeMonths = 19,
        historicalTrainingDaysPerWeek = 4,
        weightUnit = "kg",
        bodyweightKg = 70.0,
        weeklyGoalDays = 5,
        hasPastTraining = true,
        hasGymAccess = true,
        baselinePushups = 40,
        baselinePullups = 14,
        baselineBenchKg = 65,
        baselineLatPulldownKg = 110,
        baselineMileRunSeconds = 570,
        updatedAt = 1_000L,
    )
}
