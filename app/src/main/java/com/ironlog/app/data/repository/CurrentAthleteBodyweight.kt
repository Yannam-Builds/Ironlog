package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity_
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.BodyMeasurementEntity_
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal const val LOCAL_ATHLETE_ID = "local"
internal const val LEGACY_BODYWEIGHT_BASELINE_UID = "local_bodyweight_baseline_v1"
internal const val CURRENT_BODYWEIGHT_RECONCILED_KEY = "current_bodyweight_reconciled_v1"
private const val LEGACY_BASELINE_BODYWEIGHT_KEY = "baseline_bodyweight_kg"
private const val MAX_CANONICAL_BODYWEIGHT_KG = 1_000.0

private data class HistoricalBodyweight(
    val valueKg: Double,
    val measuredAt: Long,
    val updatedAt: Long,
    val uid: String,
)

internal fun isCanonicalAthleteBodyweightKg(value: Double): Boolean =
    value.isFinite() && value > 0.0 && value <= MAX_CANONICAL_BODYWEIGHT_KG

private fun canonicalBodyweightKg(value: Double?): Double? =
    value?.takeIf(::isCanonicalAthleteBodyweightKg)

private fun latestHistoricalBodyweight(store: BoxStore): HistoricalBodyweight? =
    store.boxFor(BodyMeasurementEntity::class.java).all.asSequence()
        .mapNotNull { row ->
            canonicalBodyweightKg(row.bodyweight)?.let { value ->
                HistoricalBodyweight(value, row.measuredAt, row.updatedAt, row.uid)
            }
        }
        .maxWithOrNull(compareBy<HistoricalBodyweight>({ it.measuredAt }, { it.updatedAt }, { it.uid }))

private fun localCalibration(store: BoxStore): AthleteCalibrationEntity? =
    store.boxFor(AthleteCalibrationEntity::class.java)
        .query(AthleteCalibrationEntity_.offlineUserId.equal(LOCAL_ATHLETE_ID))
        .build().use { it.findFirst() }

private fun setting(store: BoxStore, key: String): AppSettingEntity? =
    store.boxFor(AppSettingEntity::class.java)
        .query(AppSettingEntity_.key.equal(key))
        .build().use { it.findFirst() }

private fun legacyBaselineBodyweight(store: BoxStore): Pair<Double, Long>? =
    setting(store, LEGACY_BASELINE_BODYWEIGHT_KEY)?.let { row ->
        canonicalBodyweightKg(row.value.toDoubleOrNull())?.let { it to row.updatedAt }
    }

private fun reconciliationCompleted(store: BoxStore): Boolean =
    setting(store, CURRENT_BODYWEIGHT_RECONCILED_KEY)?.value == "true"

/**
 * Reads the single athlete-facing bodyweight value, always in kilograms.
 *
 * Precedence is deterministic: newest valid history row, then the local athlete snapshot, then
 * the pre-migration onboarding setting. The legacy setting is considered only until the one-time
 * reconciliation marker exists, so deleting the final history row cannot resurrect stale data.
 */
internal fun currentAthleteBodyweightKg(store: BoxStore): Double? {
    latestHistoricalBodyweight(store)?.let { return it.valueKg }
    canonicalBodyweightKg(localCalibration(store)?.bodyweightKg)?.let { return it }
    if (reconciliationCompleted(store)) return null
    return legacyBaselineBodyweight(store)?.first
}

private fun putReconciliationMarker(store: BoxStore, nowMs: Long) {
    val box = store.boxFor(AppSettingEntity::class.java)
    val row = setting(store, CURRENT_BODYWEIGHT_RECONCILED_KEY) ?: AppSettingEntity().apply {
        key = CURRENT_BODYWEIGHT_RECONCILED_KEY
    }
    row.value = "true"
    row.valueType = "boolean"
    row.updatedAt = nowMs
    box.put(row)
}

private fun putCanonicalCalibrationBodyweight(
    store: BoxStore,
    valueKg: Double?,
    nowMs: Long,
): AthleteCalibrationEntity {
    val box = store.boxFor(AthleteCalibrationEntity::class.java)
    val row = localCalibration(store) ?: AthleteCalibrationEntity(offlineUserId = LOCAL_ATHLETE_ID)
    row.bodyweightKg = canonicalBodyweightKg(valueKg)
    row.updatedAt = maxOf(row.updatedAt, nowMs)
    box.put(row)
    putReconciliationMarker(store, nowMs)
    return row
}

/** Existing transaction only: makes the newest historical row the denormalized athlete snapshot. */
internal fun syncCurrentAthleteBodyweightFromHistoryInTransaction(
    store: BoxStore,
    clearWhenNoHistory: Boolean,
    nowMs: Long = System.currentTimeMillis(),
): Double? {
    val historical = latestHistoricalBodyweight(store)
    if (historical != null) {
        putCanonicalCalibrationBodyweight(store, historical.valueKg, nowMs)
        return historical.valueKg
    }
    if (clearWhenNoHistory) {
        putCanonicalCalibrationBodyweight(store, null, nowMs)
        return null
    }
    return currentAthleteBodyweightKg(store)
}

/**
 * Existing transaction only: reconciles legacy installs and restore payloads.
 * A legacy calibration/settings-only value is materialized as one historical baseline row so all
 * future edits, deletes, analytics, exports and restores operate on the same kg history.
 */
internal fun reconcileCurrentAthleteBodyweightInTransaction(
    store: BoxStore,
    nowMs: Long = System.currentTimeMillis(),
): Double? {
    latestHistoricalBodyweight(store)?.let { latest ->
        putCanonicalCalibrationBodyweight(store, latest.valueKg, nowMs)
        return latest.valueKg
    }

    val calibration = localCalibration(store)
    val calibrationValue = canonicalBodyweightKg(calibration?.bodyweightKg)
    val legacy = if (reconciliationCompleted(store)) null else legacyBaselineBodyweight(store)
    val selected = calibrationValue ?: legacy?.first
    if (selected == null) {
        if (calibration != null || legacy != null || reconciliationCompleted(store)) {
            putCanonicalCalibrationBodyweight(store, null, nowMs)
        }
        return null
    }

    val sourceTimestamp = when {
        calibrationValue != null && (calibration?.updatedAt ?: 0L) > 0L -> calibration!!.updatedAt
        legacy != null && legacy.second > 0L -> legacy.second
        else -> nowMs
    }
    val bodyBox = store.boxFor(BodyMeasurementEntity::class.java)
    val baseline = bodyBox.query(BodyMeasurementEntity_.uid.equal(LEGACY_BODYWEIGHT_BASELINE_UID))
        .build().use { it.findFirst() }
        ?: BodyMeasurementEntity().apply {
            uid = LEGACY_BODYWEIGHT_BASELINE_UID
            measuredAt = sourceTimestamp
            createdAt = sourceTimestamp
        }
    baseline.bodyweight = selected
    baseline.updatedAt = maxOf(baseline.updatedAt, sourceTimestamp)
    bodyBox.put(baseline)
    putCanonicalCalibrationBodyweight(store, selected, nowMs)
    return selected
}

/** Idempotent startup/import migration. */
internal fun reconcileCurrentAthleteBodyweight(
    store: BoxStore,
    nowMs: Long = System.currentTimeMillis(),
): Double? = store.callInTx {
    reconcileCurrentAthleteBodyweightInTransaction(store, nowMs)
}

/**
 * Persists onboarding calibration and its first kg measurement in the same ObjectBox transaction.
 * Existing history wins, which makes this safe to retry after a partially completed onboarding.
 */
internal fun persistOnboardingAthleteBodyweight(
    store: BoxStore,
    calibration: AthleteCalibrationEntity,
    bodyweightKg: Double?,
    measuredAt: Long = System.currentTimeMillis(),
) {
    val canonicalKg = canonicalBodyweightKg(bodyweightKg)
    store.runInTx {
        val calibrationBox = store.boxFor(AthleteCalibrationEntity::class.java)
        localCalibration(store)?.let { existing ->
            if (calibration.id == 0L) calibration.id = existing.id
        }
        calibration.offlineUserId = LOCAL_ATHLETE_ID
        calibration.bodyweightKg = canonicalKg
        calibration.updatedAt = maxOf(calibration.updatedAt, measuredAt)
        calibrationBox.put(calibration)

        if (latestHistoricalBodyweight(store) == null && canonicalKg != null) {
            val bodyBox = store.boxFor(BodyMeasurementEntity::class.java)
            val baseline = bodyBox.query(BodyMeasurementEntity_.uid.equal(LEGACY_BODYWEIGHT_BASELINE_UID))
                .build().use { it.findFirst() }
                ?: BodyMeasurementEntity().apply {
                    uid = LEGACY_BODYWEIGHT_BASELINE_UID
                    this.measuredAt = measuredAt
                    createdAt = measuredAt
                }
            baseline.bodyweight = canonicalKg
            baseline.updatedAt = maxOf(baseline.updatedAt, measuredAt)
            bodyBox.put(baseline)
        }
        syncCurrentAthleteBodyweightFromHistoryInTransaction(
            store = store,
            clearWhenNoHistory = canonicalKg == null,
            nowMs = measuredAt,
        )
        putReconciliationMarker(store, measuredAt)
    }
}

internal fun currentAthleteBodyweightKgFlow(store: BoxStore): Flow<Double?> =
    observeEntityChanges(
        createStore = { store },
        BodyMeasurementEntity::class.java,
        AthleteCalibrationEntity::class.java,
        AppSettingEntity::class.java,
    ).map { store.callInReadTx { currentAthleteBodyweightKg(store) } }
        .distinctUntilChanged()
