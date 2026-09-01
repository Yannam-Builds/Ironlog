package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.BodyMeasurementEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for body measurement entries.
 * Reactive updates are emitted as Flow<List<BodyMeasurementEntity>> via ObjectBoxFlow.observeQuery.
 */
data class BodyMeasurementInput(
    val measuredAt: Long? = null,
    val bodyweight: Double? = null,
    val waist: Double? = null,
    val chest: Double? = null,
    val arm: Double? = null,
    val thigh: Double? = null,
    val notes: String? = null,
)

class BodyMeasurementRepository(
    private val store: BoxStore = ObjectBox.store,
) {
    private val bodyBox get() = store.boxFor(BodyMeasurementEntity::class.java)

    fun getBodyMeasurementsFlow() =
        observeQuery { bodyBox.query().orderDesc(BodyMeasurementEntity_.measuredAt).build() }

    /** The current local athlete bodyweight in canonical kilograms. */
    fun getCurrentBodyweightKgFlow() = currentAthleteBodyweightKgFlow(store)

    suspend fun addBodyMeasurement(input: BodyMeasurementInput = BodyMeasurementInput()): BodyMeasurementEntity =
        withContext(Dispatchers.IO) {
            input.bodyweight?.let {
                require(isCanonicalAthleteBodyweightKg(it)) { "bodyweight must be finite and in (0, 1000] kg" }
            }
            val now = System.currentTimeMillis()
            val row = BodyMeasurementEntity().apply {
                measuredAt = input.measuredAt ?: now
                bodyweight = input.bodyweight
                waist = input.waist
                chest = input.chest
                arm = input.arm
                thigh = input.thigh
                notes = input.notes?.takeIf { it.isNotEmpty() } ?: ""
                createdAt = now
                updatedAt = now
            }
            store.runInTx {
                bodyBox.put(row)
                if (row.bodyweight != null) {
                    syncCurrentAthleteBodyweightFromHistoryInTransaction(store, clearWhenNoHistory = true, nowMs = now)
                }
            }
            row
        }

    suspend fun updateBodyMeasurement(slug: String, input: BodyMeasurementInput = BodyMeasurementInput()): BodyMeasurementEntity =
        withContext(Dispatchers.IO) {
            input.bodyweight?.let {
                require(isCanonicalAthleteBodyweightKg(it)) { "bodyweight must be finite and in (0, 1000] kg" }
            }
            store.callInTx {
                val row = bodyBox.query(BodyMeasurementEntity_.uid.equal(slug)).build().use { it.findFirst() }
                    ?: error("Body measurement not found: $slug")
                input.measuredAt?.let { row.measuredAt = it }
                // JS updates nullable values when property is present. Kotlin input cannot represent undefined separately;
                // call clearBodyWeight/explicit nullable variants if that distinction is needed during integration.
                if (input.bodyweight != null) row.bodyweight = input.bodyweight
                if (input.waist != null) row.waist = input.waist
                if (input.chest != null) row.chest = input.chest
                if (input.arm != null) row.arm = input.arm
                if (input.thigh != null) row.thigh = input.thigh
                if (input.notes != null) row.notes = input.notes.takeIf { it.isNotEmpty() } ?: ""
                row.updatedAt = System.currentTimeMillis()
                bodyBox.put(row)
                if (row.bodyweight != null) {
                    syncCurrentAthleteBodyweightFromHistoryInTransaction(store, clearWhenNoHistory = true, nowMs = row.updatedAt)
                }
                row
            }
        }

    suspend fun deleteBodyMeasurement(slug: String) = withContext(Dispatchers.IO) {
        store.runInTx {
            val row = bodyBox.query(BodyMeasurementEntity_.uid.equal(slug)).build().use { it.findFirst() }
                ?: return@runInTx
            bodyBox.remove(row)
            if (row.bodyweight != null) {
                syncCurrentAthleteBodyweightFromHistoryInTransaction(
                    store,
                    clearWhenNoHistory = true,
                    nowMs = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun getCurrentBodyweightKg(): Double? = withContext(Dispatchers.IO) {
        store.callInReadTx { currentAthleteBodyweightKg(store) }
    }

    @Deprecated("Use getCurrentBodyweightKg; values are canonical kg")
    suspend fun getLatestBodyweight(): Double? = getCurrentBodyweightKg()

    /** Returns the date of the latest body weight entry as a formatted string (e.g. "May 3"). */
    suspend fun getLatestBodyweightDate(): String? = withContext(Dispatchers.IO) {
        bodyBox.query().orderDesc(BodyMeasurementEntity_.measuredAt).build().use { query ->
            query.find().firstOrNull { (it.bodyweight ?: 0.0) > 0.0 }?.measuredAt?.let { ms ->
                java.time.Instant.ofEpochMilli(ms)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
            }
        }
    }

    /** Returns the delta in kg between the most recent entry and the entry closest to 7 days ago. Null if <2 entries. */
    suspend fun getWeeklyBodyweightDelta(): Double? = withContext(Dispatchers.IO) {
        val entries = bodyBox.query().orderDesc(BodyMeasurementEntity_.measuredAt).build().use { q ->
            q.find().filter { (it.bodyweight ?: 0.0) > 0.0 }
        }
        if (entries.size < 2) return@withContext null
        val latest = entries.first()
        val sevenDaysAgo = latest.measuredAt - 7L * 24 * 60 * 60 * 1000
        // Cap look-back: only consider entries within ±3 days of the 7-days-ago target.
        // Without this cap, if the user logs infrequently the delta could compare against
        // an entry that is months old, producing a misleading result.
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000
        val weekOldEntry = entries.drop(1)
            .filter { kotlin.math.abs(it.measuredAt - sevenDaysAgo) <= threeDaysMs }
            .minByOrNull { kotlin.math.abs(it.measuredAt - sevenDaysAgo) }
            ?: return@withContext null
        (latest.bodyweight ?: 0.0) - (weekOldEntry.bodyweight ?: 0.0)
    }
}
