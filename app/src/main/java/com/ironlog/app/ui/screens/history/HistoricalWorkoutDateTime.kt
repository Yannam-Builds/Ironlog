package com.ironlog.app.ui.screens.history

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

internal fun historicalStartInstant(date: String, time: String, zone: ZoneId, offset: ZoneOffset? = null): Instant {
    val local = try { LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim())) }
    catch (_: Exception) { throw IllegalArgumentException("Enter a valid date and 24-hour start time.") }
    val offsets = zone.rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) { "This time does not exist because the clocks change. Choose another time." }
    require(offsets.size == 1 || offset in offsets) { "This time occurs twice. Choose its UTC offset below." }
    return local.toInstant(offset?.takeIf { it in offsets } ?: offsets.single())
}

internal fun historicalTimeOffsets(date: String, time: String, zone: ZoneId): List<ZoneOffset> = runCatching {
    zone.rules.getValidOffsets(LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim())))
}.getOrDefault(emptyList())

internal fun historicalPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun historicalPickerDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
