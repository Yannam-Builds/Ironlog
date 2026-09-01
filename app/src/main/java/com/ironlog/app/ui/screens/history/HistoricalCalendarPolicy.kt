package com.ironlog.app.ui.screens.history

import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.domain.gamification.parseHistoryLocalDate
import com.ironlog.app.ui.model.HistoryEntry
import java.time.ZoneId

internal fun calendarSessionsByLocalDate(history: List<HistoryEntry>, zone: ZoneId): Map<String, List<HistoryEntry>> =
    history.mapNotNull { entry -> parseHistoryLocalDate(entry.date, zone)?.toString()?.let { it to entry } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, sessions) -> sessions.sortedBy { parseHistoryInstant(it.date, zone) } }
