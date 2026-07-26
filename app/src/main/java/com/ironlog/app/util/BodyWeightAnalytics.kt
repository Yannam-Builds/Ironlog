package com.ironlog.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MS_PER_DAY: Long = 24L * 60L * 60L * 1000L
val BODY_WEIGHT_RANGES = listOf("7D", "30D", "3M", "1Y", "All")
private val RANGE_DAYS = mapOf("7D" to 7, "30D" to 30, "3M" to 90, "1Y" to 365)

data class RawBodyWeightEntry(val id: String? = null, val weight: Any? = null, val date: String? = null)
data class NormalizedBodyWeightEntry(
    val id: String,
    val weight: Double,
    val timestamp: Long,
    val date: String,
    val sourceIndex: Int,
)
data class BodyWeightSummary(
    val currentWeight: Double?,
    val changeFromPrevious: Double?,
    val weeklyTrend: Double?,
    val weekChange: Double?,
    val monthChange: Double?,
    val totalChange: Double?,
)

private fun toDayKey(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun average(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

private fun startOfIsoWeek(timestamp: Long): Long {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val diff = if (d.dayOfWeek.value == 7) -6 else 1 - d.dayOfWeek.value
    return d.plusDays(diff.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
}

private fun startOfMonth(timestamp: Long): Long {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().withDayOfMonth(1)
    return d.atStartOfDay(zone).toInstant().toEpochMilli()
}

private fun getAnchorTimestamp(entriesAsc: List<NormalizedBodyWeightEntry>): Long =
    entriesAsc.lastOrNull()?.timestamp ?: System.currentTimeMillis()

fun normalizeBodyWeightEntries(rawEntries: List<RawBodyWeightEntry>?): List<NormalizedBodyWeightEntry> {
    if (rawEntries == null) return emptyList()
    return rawEntries.mapIndexedNotNull { sourceIndex, entry ->
        val weight = when (val w = entry.weight) {
            is Number -> w.toDouble()
            is String -> w.toDoubleOrNull()
            else -> null
        }
        val timestamp = entry.date?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        if (weight == null || timestamp == null || !weight.isFinite()) null
        else NormalizedBodyWeightEntry(
            id = entry.id ?: "$timestamp-$sourceIndex",
            weight = weight,
            timestamp = timestamp,
            date = entry.date,
            sourceIndex = sourceIndex,
        )
    }.sortedBy { it.timestamp }
}

fun filterEntriesByRange(entriesAsc: List<NormalizedBodyWeightEntry>, range: String): List<NormalizedBodyWeightEntry> {
    if (entriesAsc.isEmpty() || range == "All") return entriesAsc
    val days = RANGE_DAYS[range] ?: return entriesAsc
    // Anchored to now, matching the JS fix.
    val cutoff = System.currentTimeMillis() - days * MS_PER_DAY
    return entriesAsc.filter { it.timestamp >= cutoff }
}

fun dedupeLatestEntryByDay(entriesAsc: List<NormalizedBodyWeightEntry>): List<NormalizedBodyWeightEntry> {
    val byDay = LinkedHashMap<String, NormalizedBodyWeightEntry>()
    entriesAsc.forEach { byDay[toDayKey(it.timestamp)] = it }
    return byDay.values.sortedBy { it.timestamp }
}

fun <T> downsampleSeries(points: List<T>, maxPoints: Int = 140): List<T> {
    if (points.size <= maxPoints) return points
    val stride = (points.size - 1).toDouble() / (maxPoints - 1).toDouble()
    return List(maxPoints) { i -> points[(i * stride).roundToInt().coerceIn(points.indices)] }
}

fun calculateBodyWeightSummary(
    allEntriesAsc: List<NormalizedBodyWeightEntry>,
    selectedEntriesAsc: List<NormalizedBodyWeightEntry>,
): BodyWeightSummary {
    val latestOverall = allEntriesAsc.lastOrNull()
    val firstOverall = allEntriesAsc.firstOrNull()
    val latestSelected = selectedEntriesAsc.lastOrNull() ?: latestOverall
    val latestSelectedIndex = latestSelected?.let { ls -> allEntriesAsc.indexOfFirst { it.id == ls.id } } ?: -1
    val previousEntry = if (latestSelectedIndex > 0) allEntriesAsc[latestSelectedIndex - 1] else null
    val anchorTs = latestSelected?.timestamp ?: getAnchorTimestamp(allEntriesAsc)

    fun getWindowAverage(windowStart: Long, windowEnd: Long): Double? {
        return average(allEntriesAsc.filter { it.timestamp > windowStart && it.timestamp <= windowEnd }.map { it.weight })
    }

    val weekStart = startOfIsoWeek(anchorTs)
    val monthStart = startOfMonth(anchorTs)
    val weekEntries = allEntriesAsc.filter { it.timestamp >= weekStart && it.timestamp <= anchorTs }
    val monthEntries = allEntriesAsc.filter { it.timestamp >= monthStart && it.timestamp <= anchorTs }
    val recentAvg = getWindowAverage(anchorTs - 7 * MS_PER_DAY, anchorTs)
    val previousAvg = getWindowAverage(anchorTs - 14 * MS_PER_DAY, anchorTs - 7 * MS_PER_DAY)

    return BodyWeightSummary(
        currentWeight = latestSelected?.weight,
        changeFromPrevious = if (latestSelected != null && previousEntry != null) latestSelected.weight - previousEntry.weight else null,
        weeklyTrend = if (recentAvg != null && previousAvg != null) recentAvg - previousAvg else null,
        weekChange = if (weekEntries.size >= 2) weekEntries.last().weight - weekEntries.first().weight else null,
        monthChange = if (monthEntries.size >= 2) monthEntries.last().weight - monthEntries.first().weight else null,
        totalChange = if (latestOverall != null && firstOverall != null) latestOverall.weight - firstOverall.weight else null,
    )
}
