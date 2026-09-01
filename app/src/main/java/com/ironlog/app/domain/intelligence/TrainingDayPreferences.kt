package com.ironlog.app.domain.intelligence

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

data class TrainingDayStatus(
    val isTrainingDay: Boolean,
    val nextTrainingDate: LocalDate,
)

/** Canonical Monday=0 through Sunday=6 representation of the athlete's protected training days. */
object TrainingDayPreferences {
    const val SETTINGS_KEY = "trainingDayIndices"

    fun normalize(indices: Iterable<Int>, fallbackCount: Int): Set<Int> {
        val valid = indices.filter { it in 0..6 }.toSortedSet()
        return valid.ifEmpty { defaultForCount(fallbackCount) }
    }

    fun reconcileToCount(indices: Iterable<Int>, targetCount: Int): Set<Int> {
        val safeTarget = targetCount.coerceIn(1, 7)
        val valid = indices.filter { it in 0..6 }.distinct().sorted()
        if (valid.size == safeTarget) return valid.toSet()
        if (valid.size > safeTarget) return valid.take(safeTarget).toSet()

        return buildList {
            addAll(valid)
            defaultForCount(safeTarget).forEach { if (it !in this) add(it) }
            (0..6).forEach { if (size < safeTarget && it !in this) add(it) }
        }.take(safeTarget).sorted().toSet()
    }

    fun defaultForCount(count: Int): Set<Int> {
        val safeCount = count.coerceIn(1, 7)
        if (safeCount == 1) return setOf(0)
        return (0 until safeCount)
            .map { index -> (index * 6.0 / (safeCount - 1)).roundToInt().coerceIn(0, 6) }
            .toSet()
    }

    fun writeToSettings(json: JSONObject, indices: Iterable<Int>) {
        val normalized = normalize(indices, fallbackCount = 3)
        json.put(SETTINGS_KEY, JSONArray(normalized.sorted()))
    }

    fun readFromSettings(json: JSONObject, fallbackCount: Int): Set<Int> {
        val array = json.optJSONArray(SETTINGS_KEY) ?: return defaultForCount(fallbackCount)
        val decoded = buildList {
            for (index in 0 until array.length()) {
                array.optInt(index, -1).takeIf { it in 0..6 }?.let(::add)
            }
        }
        return normalize(decoded, fallbackCount)
    }

    /** Returns today's schedule state and the next protected date, including today when selected. */
    fun status(indices: Iterable<Int>, date: LocalDate): TrainingDayStatus {
        val normalized = normalize(indices, fallbackCount = 3)
        val todayIndex = date.dayOfWeek.value - 1
        val offset = (0..6).first { delta -> (todayIndex + delta) % 7 in normalized }
        return TrainingDayStatus(
            isTrainingDay = todayIndex in normalized,
            nextTrainingDate = date.plusDays(offset.toLong()),
        )
    }
}
