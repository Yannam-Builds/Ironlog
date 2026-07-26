// app/src/main/java/com/ironlog/app/data/health/HealthConnectRepository.kt
package com.ironlog.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import com.ironlog.app.ui.model.HistoryEntry
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wraps the Health Connect client for IronLog read/write operations.
 *
 * All public methods are suspend functions and must be called from a coroutine.
 * Callers should check [isAvailable] before calling any other method.
 */
class HealthConnectRepository(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }
            .onFailure { Timber.e(it, "HealthConnect client init failed") }
            .getOrNull()
    }

    /** Returns true if Health Connect is installed and available on this device. */
    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** The permission set IronLog requests from Health Connect. */
    val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    )

    val requiredPermissions: Set<String> = writePermissions + readPermissions

    /** Returns which of [requiredPermissions] have been granted. */
    suspend fun grantedPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return c.permissionController.getGrantedPermissions()
    }

    /** Returns true if all required permissions are granted. */
    suspend fun hasAllPermissions(): Boolean =
        grantedPermissions().containsAll(requiredPermissions)

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Write a completed workout session from a [HistoryEntry] to Health Connect.
     * No-op if Health Connect is unavailable or permissions are missing.
     */
    suspend fun writeWorkoutSession(entry: HistoryEntry) {
        val c = client ?: return
        runCatching {
            val date = LocalDate.parse(entry.date.take(10))
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = start.plusSeconds(entry.duration.toLong().coerceAtLeast(60))

            val record = ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = zone.rules.getOffset(start),
                endTime = end,
                endZoneOffset = zone.rules.getOffset(end),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = entry.name,
                metadata = Metadata(),
            )
            c.insertRecords(listOf(record))
        }.onFailure { Timber.e(it, "HealthConnect: writeWorkoutSession failed") }
    }

    /**
     * Write the user's body weight to Health Connect.
     * @param weightKg Weight in kilograms.
     */
    suspend fun writeWeight(weightKg: Double) {
        val c = client ?: return
        runCatching {
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val record = WeightRecord(
                time = now,
                zoneOffset = zone.rules.getOffset(now),
                weight = Mass.kilograms(weightKg),
                metadata = Metadata(),
            )
            c.insertRecords(listOf(record))
        }.onFailure { Timber.e(it, "HealthConnect: writeWeight failed") }
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Read a [BiometricSnapshot] for the last 36 hours.
     * Returns a snapshot with null fields where data is unavailable.
     */
    suspend fun readBiometricSnapshot(): BiometricSnapshot {
        val c = client ?: return BiometricSnapshot()

        val now = Instant.now()
        val cutoff = now.minus(Duration.ofHours(36))
        val timeRange = TimeRangeFilter.between(cutoff, now)

        val sleepHours: Double? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRange)
            )
            result.records.lastOrNull()?.let { session ->
                Duration.between(session.startTime, session.endTime).toMinutes() / 60.0
            }
        }.onFailure { Timber.w(it, "HealthConnect: readBiometricSnapshot sleepHours failed") }
         .getOrNull()

        val restingHr: Long? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, timeRange)
            )
            result.records.lastOrNull()?.beatsPerMinute
        }.onFailure { Timber.w(it, "HealthConnect: readBiometricSnapshot restingHr failed") }
         .getOrNull()

        val hrv: Double? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange)
            )
            result.records.lastOrNull()?.heartRateVariabilityMillis
        }.onFailure { Timber.w(it, "HealthConnect: readBiometricSnapshot hrv failed") }
         .getOrNull()

        return BiometricSnapshot(
            sleepHours = sleepHours,
            restingHrBpm = restingHr,
            hrvRmssd = hrv,
        )
    }
}
