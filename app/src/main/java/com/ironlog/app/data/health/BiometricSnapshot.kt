// app/src/main/java/com/ironlog/app/data/health/BiometricSnapshot.kt
package com.ironlog.app.data.health

/**
 * Latest biometric data read from Health Connect.
 * All values are nullable — null means no recent data available.
 *
 * @param sleepHours     Duration of the most recent sleep session in the read window.
 * @param restingHrBpm   Resting heart rate in bpm (Health Connect RestingHeartRateRecord).
 * @param hrvRmssd       Heart Rate Variability RMSSD in ms (Health Connect HRVRecord).
 * @param weightKg       Reserved for backward source compatibility. This read-only integration
 *                       does not request weight access and never populates it.
 */
data class BiometricSnapshot(
    val sleepHours: Double? = null,
    val restingHrBpm: Long? = null,
    val hrvRmssd: Double? = null,
    val weightKg: Double? = null,
)

/**
 * Keep raw Health Connect records as user-visible context, without converting population
 * thresholds into an IronLog readiness score. Values outside broad physiological/display bounds
 * are quarantined instead of being rendered as credible evidence.
 */
fun BiometricSnapshot.validatedForDisplay(): BiometricSnapshot = copy(
    sleepHours = sleepHours?.takeIf { it.isFinite() && it in 0.0..24.0 },
    restingHrBpm = restingHrBpm?.takeIf { it in 20L..250L },
    hrvRmssd = hrvRmssd?.takeIf { it.isFinite() && it in 0.1..1_000.0 },
    weightKg = null,
)

fun BiometricSnapshot.hasAnySignal(): Boolean =
    sleepHours != null || restingHrBpm != null || hrvRmssd != null
