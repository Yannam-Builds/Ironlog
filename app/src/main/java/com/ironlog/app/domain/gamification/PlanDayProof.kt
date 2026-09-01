package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import java.time.Instant
import java.time.ZoneId

fun hasPlanDayProofThisWeek(history: List<HistoryEntry>, dayUid: String, dayName: String,
    now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val week = proofWeekKey(now.atZone(zone).toLocalDate())
    return history.any { entry ->
        CreditedProof.qualifies(entry, now, zone) &&
            parseHistoryLocalDate(entry.date, zone)?.let(::proofWeekKey) == week &&
            if (!entry.planDayUid.isNullOrBlank()) entry.planDayUid == dayUid
            else (entry.dayName ?: entry.name).trim().equals(dayName.trim(), ignoreCase = true)
    }
}
