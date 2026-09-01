package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CurrentAthleteBodyweightRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `legacy reconciliation prefers latest measurement then calibration then old setting`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-precedence")).build().use { store ->
                store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
                    key = "baseline_bodyweight_kg"
                    value = "91.0"
                    valueType = "string"
                    updatedAt = 100L
                })
                store.boxFor(AthleteCalibrationEntity::class.java).put(AthleteCalibrationEntity(
                    offlineUserId = "local",
                    bodyweightKg = 88.0,
                    updatedAt = 200L,
                ))
                store.boxFor(BodyMeasurementEntity::class.java).put(listOf(
                    bodyweightRow("older", 84.0, measuredAt = 1_000L, updatedAt = 1_100L),
                    bodyweightRow("latest", 81.5, measuredAt = 2_000L, updatedAt = 2_100L),
                    bodyweightRow("future-null", null, measuredAt = 3_000L, updatedAt = 3_100L),
                ))

                assertEquals(81.5, reconcileCurrentAthleteBodyweight(store)!!, 0.0)
                assertEquals(81.5, currentAthleteBodyweightKg(store)!!, 0.0)
                assertEquals(
                    81.5,
                    store.boxFor(AthleteCalibrationEntity::class.java).all.single().bodyweightKg!!,
                    0.0,
                )
                assertEquals(3L, store.boxFor(BodyMeasurementEntity::class.java).count())
            }
        }

    @Test
    fun `legacy calibration is seeded into history once and remains canonical kilograms`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-seed")).build().use { store ->
                store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
                    key = "baseline_bodyweight_kg"
                    value = "95"
                    updatedAt = 10L
                })
                store.boxFor(AthleteCalibrationEntity::class.java).put(AthleteCalibrationEntity(
                    offlineUserId = "local",
                    weightUnit = "lbs",
                    bodyweightKg = 70.25,
                    updatedAt = 20L,
                ))

                assertEquals(70.25, reconcileCurrentAthleteBodyweight(store)!!, 0.0)
                assertEquals(70.25, reconcileCurrentAthleteBodyweight(store)!!, 0.0)

                val seeded = store.boxFor(BodyMeasurementEntity::class.java).all.single()
                assertEquals(LEGACY_BODYWEIGHT_BASELINE_UID, seeded.uid)
                assertEquals(70.25, seeded.bodyweight!!, 0.0)
                assertEquals(1L, store.boxFor(BodyMeasurementEntity::class.java).count())
            }
        }

    @Test
    fun `bodyweight history mutations transactionally advance revert and clear canonical current value`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-mutations")).build().use { store ->
                val repository = BodyMeasurementRepository(store)
                val older = repository.addBodyMeasurement(BodyMeasurementInput(measuredAt = 1_000L, bodyweight = 80.0))
                val newer = repository.addBodyMeasurement(BodyMeasurementInput(measuredAt = 2_000L, bodyweight = 78.0))

                assertEquals(78.0, repository.getCurrentBodyweightKg()!!, 0.0)
                assertEquals(78.0, repository.getCurrentBodyweightKgFlow().first()!!, 0.0)

                repository.updateBodyMeasurement(older.uid, BodyMeasurementInput(measuredAt = 3_000L, bodyweight = 79.0))
                assertEquals(79.0, repository.getCurrentBodyweightKg()!!, 0.0)

                repository.deleteBodyMeasurement(older.uid)
                assertEquals(78.0, repository.getCurrentBodyweightKg()!!, 0.0)

                repository.deleteBodyMeasurement(newer.uid)
                assertNull(repository.getCurrentBodyweightKg())
                assertNull(store.boxFor(AthleteCalibrationEntity::class.java).all.single().bodyweightKg)

                // A later process-start reconciliation must not resurrect the onboarding setting.
                store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
                    key = "baseline_bodyweight_kg"
                    value = "99"
                    updatedAt = 100L
                })
                assertNull(reconcileCurrentAthleteBodyweight(store))
                assertEquals(0L, store.boxFor(BodyMeasurementEntity::class.java).count())
            }
        }

    @Test
    fun `non-bodyweight measurement never erases a migrated current weight`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-unrelated-measurement")).build().use { store ->
                store.boxFor(AthleteCalibrationEntity::class.java).put(AthleteCalibrationEntity(
                    offlineUserId = "local",
                    bodyweightKg = 75.0,
                    updatedAt = 20L,
                ))
                val repository = BodyMeasurementRepository(store)
                repository.addBodyMeasurement(BodyMeasurementInput(measuredAt = 2_000L, waist = 80.0))

                assertEquals(75.0, repository.getCurrentBodyweightKg()!!, 0.0)
            }
        }

    @Test
    fun `onboarding persists one baseline history row and canonical calibration together`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-onboarding")).build().use { store ->
                val calibration = AthleteCalibrationEntity(
                    offlineUserId = "local",
                    weightUnit = "lbs",
                    bodyweightKg = 72.5,
                    updatedAt = 9_000L,
                )

                persistOnboardingAthleteBodyweight(store, calibration, bodyweightKg = 72.5, measuredAt = 9_000L)
                persistOnboardingAthleteBodyweight(store, calibration, bodyweightKg = 72.5, measuredAt = 9_000L)

                assertEquals(72.5, currentAthleteBodyweightKg(store)!!, 0.0)
                assertEquals(72.5, store.boxFor(BodyMeasurementEntity::class.java).all.single().bodyweight!!, 0.0)
                assertEquals(1L, store.boxFor(BodyMeasurementEntity::class.java).count())
            }
        }

    @Test
    fun `onboarding baseline weight remains frozen when current measurements change`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-frozen-baseline")).build().use { store ->
                store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
                    key = "baseline_bodyweight_kg"
                    value = "70"
                    valueType = "string"
                    updatedAt = 1_000L
                })
                val calibration = AthleteCalibrationEntity(
                    offlineUserId = "local",
                    bodyweightKg = 70.0,
                    updatedAt = 1_000L,
                )
                store.boxFor(AthleteCalibrationEntity::class.java).put(calibration)
                BodyMeasurementRepository(store).addBodyMeasurement(
                    BodyMeasurementInput(measuredAt = 2_000L, bodyweight = 82.0),
                )

                val currentCalibration = store.boxFor(AthleteCalibrationEntity::class.java).all.single()
                assertEquals(82.0, currentAthleteBodyweightKg(store)!!, 0.0)
                assertEquals(70.0, onboardingBaselineBodyweightKg(store, currentCalibration)!!, 0.0)

                store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
                    key = "baseline_bodyweight_kg"
                    value = "0"
                    valueType = "string"
                    updatedAt = 3_000L
                })
                assertNull(onboardingBaselineBodyweightKg(store, currentCalibration))
            }
        }

    @Test
    fun `corrected onboarding weight updates only its generated baseline row`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-onboarding-retry")).build().use { store ->
                val calibration = AthleteCalibrationEntity(offlineUserId = "local")
                persistOnboardingAthleteBodyweight(store, calibration, bodyweightKg = 70.0, measuredAt = 1_000L)
                BodyMeasurementRepository(store).addBodyMeasurement(
                    BodyMeasurementInput(measuredAt = 2_000L, bodyweight = 82.0),
                )

                persistOnboardingAthleteBodyweight(store, calibration, bodyweightKg = 75.0, measuredAt = 3_000L)

                val afterCorrection = store.boxFor(BodyMeasurementEntity::class.java).all
                assertEquals(2, afterCorrection.size)
                assertEquals(
                    75.0,
                    afterCorrection.single { it.uid == LEGACY_BODYWEIGHT_BASELINE_UID }.bodyweight!!,
                    0.0,
                )
                assertEquals(82.0, currentAthleteBodyweightKg(store)!!, 0.0)
                assertEquals(75.0, onboardingBaselineBodyweightKg(store)!!, 0.0)

                persistOnboardingAthleteBodyweight(store, calibration, bodyweightKg = null, measuredAt = 4_000L)

                assertEquals(
                    listOf(82.0),
                    store.boxFor(BodyMeasurementEntity::class.java).all.mapNotNull { it.bodyweight },
                )
                assertEquals(82.0, currentAthleteBodyweightKg(store)!!, 0.0)
                assertNull(onboardingBaselineBodyweightKg(store))
            }
        }

    @Test
    fun `invalid legacy weights are ignored rather than becoming athlete context`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-invalid")).build().use { store ->
                store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
                    key = "baseline_bodyweight_kg"
                    value = "NaN"
                    updatedAt = 1L
                })
                store.boxFor(AthleteCalibrationEntity::class.java).put(AthleteCalibrationEntity(
                    offlineUserId = "local",
                    bodyweightKg = -10.0,
                    updatedAt = 2L,
                ))

                assertNull(reconcileCurrentAthleteBodyweight(store))
                assertNull(currentAthleteBodyweightKg(store))
                assertTrue(store.boxFor(BodyMeasurementEntity::class.java).all.isEmpty())
            }
        }

    @Test
    fun `new bodyweight writes cannot persist values outside canonical kg bounds`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-write-bounds")).build().use { store ->
                val repository = BodyMeasurementRepository(store)

                assertTrue(runCatching {
                    repository.addBodyMeasurement(BodyMeasurementInput(bodyweight = 1_000.1))
                }.isFailure)
                assertTrue(runCatching {
                    repository.addBodyMeasurement(BodyMeasurementInput(bodyweight = Double.POSITIVE_INFINITY))
                }.isFailure)
                assertEquals(0L, store.boxFor(BodyMeasurementEntity::class.java).count())
                assertNull(repository.getCurrentBodyweightKg())
            }
        }

    @Test
    fun `restore commits imported bodyweight history and canonical athlete context together`() =
        runBlocking(Dispatchers.IO) {
            MyObjectBox.builder().directory(temporary.newFolder("bodyweight-restore")).build().use { store ->
                val repository = ImportExportRepository(store, temporary.newFolder("bodyweight-restore-snapshots"))
                val payload = """{
                    "type":"ironlog_watermelon_export",
                    "version":1,
                    "data":{
                        "body_measurements":[
                            {"id":"older","measured_at":1000,"bodyweight":85.0},
                            {"id":"newer","measured_at":2000,"bodyweight":79.5}
                        ],
                        "athlete_calibrations":[
                            {"offline_user_id":"local","bodyweight_kg":92.0,"updated_at":3000}
                        ]
                    }
                }""".trimIndent()

                repository.runConfirmedImport(payload, mode = "merge")

                assertEquals(79.5, currentAthleteBodyweightKg(store)!!, 0.0)
                assertEquals(
                    79.5,
                    store.boxFor(AthleteCalibrationEntity::class.java).all.single().bodyweightKg!!,
                    0.0,
                )
                assertEquals(2L, store.boxFor(BodyMeasurementEntity::class.java).count())
            }
        }

    private fun bodyweightRow(
        uid: String,
        bodyweightKg: Double?,
        measuredAt: Long,
        updatedAt: Long,
    ) = BodyMeasurementEntity().apply {
        this.uid = uid
        bodyweight = bodyweightKg
        this.measuredAt = measuredAt
        createdAt = measuredAt
        this.updatedAt = updatedAt
    }
}
