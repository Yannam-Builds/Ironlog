package com.ironlog.app.util

import kotlin.math.max

/** Direct port of calculateSetVolume(weight, reps). */
fun calculateSetVolume(weight: Number?, reps: Number?): Double =
    (weight?.toDouble() ?: 0.0) * (reps?.toDouble() ?: 0.0)

/** Epley-style estimated 1RM used by the RN stats layer. */
fun calculateEstimated1RM(weight: Number?, reps: Number?): Double {
    val w = max(0.0, weight?.toDouble() ?: 0.0)
    val r = max(0.0, reps?.toDouble() ?: 0.0)
    if (w <= 0.0 || r <= 0.0) return 0.0
    return if (r <= 1.0) w else w * (1.0 + r / 30.0)
}
