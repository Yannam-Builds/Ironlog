package com.ironlog.app.util

import java.util.Locale
import kotlin.math.round

private const val KG_TO_LBS = 2.20462262185

fun convertKgToUnit(weightKg: Double, unit: String = "kg", decimals: Int = if (unit == "lbs") 0 else 1): Double {
    val converted = if (unit.lowercase(Locale.US) == "lbs") weightKg * KG_TO_LBS else weightKg
    val scale = Math.pow(10.0, decimals.coerceAtLeast(0).toDouble())
    return round(converted * scale) / scale
}

fun convertUnitToKg(value: Double, unit: String = "kg", decimals: Int = 3): Double {
    val kg = if (unit.lowercase(Locale.US) == "lbs") value / KG_TO_LBS else value
    val scale = Math.pow(10.0, decimals.coerceAtLeast(0).toDouble())
    return round(kg * scale) / scale
}

fun formatWeightFromKg(weightKg: Double, unit: String = "kg"): String {
    val value = convertKgToUnit(weightKg, unit, if (unit == "lbs") 0 else 1)
    val clean = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    return "$clean ${if (unit.lowercase(Locale.US) == "lbs") "lb" else "kg"}"
}

fun formatVolumeFromKg(volumeKg: Double, unit: String = "kg"): String = formatWeightFromKg(volumeKg, unit)

fun epley(weight: Double, reps: Double): Double = if (weight > 0.0 && reps > 0.0) weight * (1.0 + reps / 30.0) else 0.0

fun formatDurationShort(secondsValue: Number?): String {
    val total = kotlin.math.max(0, kotlin.math.round((secondsValue?.toDouble() ?: 0.0)).toInt())
    val mm = (total / 60).toString().padStart(2, '0')
    val ss = (total % 60).toString().padStart(2, '0')
    return "$mm:$ss"
}
