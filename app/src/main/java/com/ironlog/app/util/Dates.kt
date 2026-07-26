package com.ironlog.app.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Mirrors JS startOfWeek() behavior as Monday-local week start. */
fun startOfWeekMillis(tz: TimeZone = TimeZone.currentSystemDefault()): Long {
    val today = Clock.System.now().toLocalDateTime(tz).date
    // +7 before % 7 ensures the result is never negative (e.g. Sunday ordinal=6 gives 6 days back)
    val daysFromMonday = (today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
    val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
    return monday.atStartOfDayIn(tz).toEpochMilliseconds()
}

/** Formats an epoch-millisecond timestamp as "dd MMM yyyy" (UK locale, e.g. "05 Jan 2025"). */
fun formatHistoryDate(timestamp: Long): String {
    val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth.toString().padStart(2, '0')} ${monthAbbr(date.month)} ${date.year}"
}

/** Formats a LocalDate as "d MMM" for chart axis labels (e.g. "5 Jan"). */
fun formatChartAxisDate(date: LocalDate): String = "${date.dayOfMonth} ${monthAbbr(date.month)}"

private fun monthAbbr(month: Month): String =
    month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
