package com.ironlog.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import timber.log.Timber

internal fun canReadAnyHealthContext(
    grantedPermissions: Set<String>,
    supportedReadPermissions: Set<String>,
): Boolean = grantedPermissions.any(supportedReadPermissions::contains)

/**
 * Wraps the read-only Health Connect context supported by this build.
 *
 * IronLog displays recent sleep, resting heart rate, and HRV values in Settings. Those records do
 * not alter recovery/readiness calculations because the app does not yet maintain the personal
 * baselines and measurement-quality metadata needed to interpret them responsibly.
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

    /** Every permission requested by this build has an immediate read consumer. */
    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    )

    val requiredPermissions: Set<String> = readPermissions

    /** Returns which of [requiredPermissions] have been granted. */
    suspend fun grantedPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return c.permissionController.getGrantedPermissions()
    }

    /** Returns true if all read permissions used by this build are granted. */
    suspend fun hasAllPermissions(): Boolean =
        grantedPermissions().containsAll(requiredPermissions)

    /**
     * Read recent raw context for display. Missing, invalid, or inaccessible fields stay null.
     * No value returned here is interpreted as a clinical or training-readiness score.
     */
    suspend fun readBiometricSnapshot(): BiometricSnapshot {
        val c = client ?: return BiometricSnapshot()

        val now = Instant.now()
        val cutoff = now.minus(Duration.ofHours(36))
        val timeRange = TimeRangeFilter.between(cutoff, now)

        val sleepHours: Double? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRange),
            )
            result.records.maxByOrNull { it.endTime }?.let { session ->
                Duration.between(session.startTime, session.endTime).toMinutes() / 60.0
            }
        }.onFailure { Timber.w(it, "HealthConnect: readBiometricSnapshot sleepHours failed") }
            .getOrNull()

        val restingHr: Long? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, timeRange),
            )
            result.records.maxByOrNull { it.time }?.beatsPerMinute
        }.onFailure { Timber.w(it, "HealthConnect: readBiometricSnapshot restingHr failed") }
            .getOrNull()

        val hrv: Double? = runCatching {
            val result = c.readRecords(
                ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange),
            )
            result.records.maxByOrNull { it.time }?.heartRateVariabilityMillis
        }.onFailure { Timber.w(it, "HealthConnect: readBiometricSnapshot hrv failed") }
            .getOrNull()

        return BiometricSnapshot(
            sleepHours = sleepHours,
            restingHrBpm = restingHr,
            hrvRmssd = hrv,
        ).validatedForDisplay()
    }
}
