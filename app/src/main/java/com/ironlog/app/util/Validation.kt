package com.ironlog.app.util

fun requireNonEmpty(value: Any?, fieldName: String) {
    val ok = when (value) {
        null -> false
        is String -> value.trim().isNotEmpty()
        else -> value.toString().trim().isNotEmpty()
    }
    if (!ok) error("$fieldName is required.")
}

fun requireNumberMin(value: Number?, min: Double, fieldName: String) {
    val number = value?.toDouble() ?: Double.NaN
    if (!number.isFinite() || number < min) error("$fieldName must be at least $min.")
}

fun validateContributionFraction(value: Number?) {
    val number = value?.toDouble() ?: Double.NaN
    if (!number.isFinite() || number < 0.0 || number > 1.0) {
        error("contribution_fraction must be between 0 and 1.")
    }
}

fun normalizeExerciseName(value: String?): String = value.orEmpty().trim().replace(Regex("\\s+"), " ")
fun normalizeExerciseNameKey(value: String?): String = normalizeExerciseName(value).lowercase()

fun jsTruthyString(value: String?): Boolean = value != null && value.isNotEmpty()
fun jsNumberOrZero(value: Number?): Double = value?.toDouble() ?: 0.0
fun jsBoolean(value: Boolean?): Boolean = value == true
