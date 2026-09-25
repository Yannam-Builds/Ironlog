package com.ironlog.app.ui.screens.stats

import com.ironlog.app.domain.gamification.parseHistoryLocalDate
import java.time.LocalDate
import java.time.ZoneId

/** Group recorded instants by the athlete's calendar day, retaining date-only imports. */
internal fun countSessionsByLocalDate(
    timestamps: List<String>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Map<LocalDate, Int> =
    timestamps.mapNotNull { parseHistoryLocalDate(it, zoneId) }
        .groupingBy { it }
        .eachCount()
