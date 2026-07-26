// app/src/main/java/com/ironlog/app/data/health/BiometricSnapshot.kt
package com.ironlog.app.data.health

/**
 * Latest biometric data read from Health Connect.
 * All values are nullable — null means no recent data available.
 *
 * @param sleepHours     Total sleep hours from last night (Health Connect SleepSessionRecord).
 * @param restingHrBpm   Resting heart rate in bpm (Health Connect RestingHeartRateRecord).
 * @param hrvRmssd       Heart Rate Variability RMSSD in ms (Health Connect HRVRecord).
 * @param weightKg       Latest body weight in kg (Health Connect WeightRecord).
 */
data class BiometricSnapshot(
    val sleepHours: Double? = null,
    val restingHrBpm: Long? = null,
    val hrvRmssd: Double? = null,
    val weightKg: Double? = null,
)
