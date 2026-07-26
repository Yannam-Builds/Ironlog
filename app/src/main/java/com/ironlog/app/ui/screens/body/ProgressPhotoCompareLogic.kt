package com.ironlog.app.ui.screens.body

import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun updateDateCompareSelection(
    selectedA: LocalDate?,
    selectedB: LocalDate?,
    tappedDate: LocalDate,
): Pair<LocalDate?, LocalDate?> = when {
    selectedA == null -> tappedDate to null
    selectedA == tappedDate && selectedB == null -> null to null
    selectedA == tappedDate -> selectedB to null
    selectedB == tappedDate -> selectedA to null
    selectedB == null -> selectedA to tappedDate
    else -> tappedDate to null
}

internal fun resolveDateComparePhotos(
    rows: List<ProgressPhotoEntity>,
    selectedA: LocalDate?,
    selectedB: LocalDate?,
): Pair<ProgressPhotoEntity?, ProgressPhotoEntity?> =
    latestPhotoForDate(rows, selectedA) to latestPhotoForDate(rows, selectedB)

internal fun progressPhotoLocalDate(
    takenAt: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate = Instant.ofEpochMilli(takenAt).atZone(zoneId).toLocalDate()

private fun latestPhotoForDate(
    rows: List<ProgressPhotoEntity>,
    date: LocalDate?,
): ProgressPhotoEntity? {
    if (date == null) return null
    return rows
        .asSequence()
        .filter { progressPhotoLocalDate(it.takenAt) == date }
        .maxByOrNull { it.takenAt }
}
