package com.ironlog.app.domain.gamification

import com.ironlog.app.domain.training.TrainingSetPolicy
import com.ironlog.app.ui.model.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

internal fun proofWeekKey(date: LocalDate): String = WeekFields.ISO.let {
    "${date.get(it.weekBasedYear())}-W${date.get(it.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
}

internal fun mergeBadgeUnlockTimes(badges: List<String>, durable: Map<String, Long>, history: Map<String, Long>, fallback: Long): Map<String, Long> =
    (durable.keys + badges).sorted().associateWith { badge ->
        durable[badge]?.takeIf { it > 0L } ?: history[badge]?.takeIf { it > 0L } ?: fallback.coerceAtLeast(1L)
    }

internal fun latestEarnedBadgeId(csv: String, timestampJson: String?): String? {
    val times = runCatching { org.json.JSONObject(timestampJson ?: "{}") }.getOrDefault(org.json.JSONObject())
    return csv.split(',').map(String::trim).filter(String::isNotBlank).distinct()
        .maxWithOrNull(compareBy<String>({ times.optLong(it, 0L) }, { it }))
}

internal fun historicalPrBadgeUnlocks(history: List<HistoryEntry>, events: List<IronLedgerEvent>, now: Instant, zone: ZoneId): Map<String, Long> {
    val ordered = history.filter { CreditedProof.qualifies(it, now, zone) }
        .sortedWith(compareBy({ parseHistoryInstant(it.date, zone) }, { it.id }))
    val owners = ordered.map { it.id }.sortedByDescending(String::length)
    val prIds = events.filter { it.kind == "pr" }.mapNotNull { event ->
        event.workoutId ?: owners.firstOrNull { event.sourceId.startsWith("$it:") }
    }.toSet()
    val result = linkedMapOf<String, Long>()
    var run = 0
    ordered.forEach { entry ->
        run = if (entry.id in prIds) run + 1 else 0
        val time = parseHistoryInstant(entry.date, zone)!!.toEpochMilli()
        if (run > 0) result.putIfAbsent("first_pr", time)
        if (run >= 4) result.putIfAbsent("progressive_streak", time)
    }
    return result
}

fun creditedSessionsThisWeek(history: List<HistoryEntry>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Int {
    val week = proofWeekKey(now.atZone(zone).toLocalDate())
    return history.count { CreditedProof.qualifies(it, now, zone) &&
        parseHistoryLocalDate(it.date, zone)?.let(::proofWeekKey) == week }
}

/** Earliest provable history-only milestones, independent of import provenance or today's streak. */
fun historicalBadgeUnlocks(history: List<HistoryEntry>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Map<String, Long> {
    val ordered = history.filter { CreditedProof.qualifies(it, now, zone) }
        .mapNotNull { entry -> parseHistoryInstant(entry.date, zone)?.let { entry to it } }
        .sortedWith(compareBy({ it.second }, { it.first.id }))
    val unlocks = linkedMapOf<String, Long>()
    var lastDay: LocalDate? = null
    var streak = 0
    var volume = 0.0
    val weeks = mutableSetOf<String>()
    ordered.forEachIndexed { index, (entry, instant) ->
        val date = instant.atZone(zone).toLocalDate()
        fun unlock(id: String) { unlocks.putIfAbsent(id, instant.toEpochMilli()) }
        if (date != lastDay) {
            streak = if (lastDay?.plusDays(1) == date) streak + 1 else 1
            lastDay = date
        }
        unlock("first_workout")
        if (streak >= 3) unlock("streak_3")
        if (streak >= 30) unlock("streak_30")
        listOf(10, 50, 100).filter { index + 1 >= it }.forEach { unlock("workouts_$it") }
        weeks += proofWeekKey(date)
        if (weeks.size >= 4) unlock("consistency_4w")
        volume += entry.exercises.sumOf { ex -> ex.sets.sumOf { TrainingSetPolicy.externalLoadVolume(ex, it) } }
        if (volume >= 100_000.0) unlock("volume_milestone")
    }
    ordered.firstOrNull()?.second?.atZone(zone)?.toLocalDate()?.plusDays(365)?.atStartOfDay(zone)?.toInstant()
        ?.takeUnless { it.isAfter(now) }?.let { unlocks["member_365"] = it.toEpochMilli() }
    return unlocks
}
