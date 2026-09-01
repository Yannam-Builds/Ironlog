package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import com.ironlog.app.domain.gamification.*
import com.ironlog.app.ui.model.HistoryEntry
import io.objectbox.BoxStore
import java.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Merge durable earned state from the current row, never from a suspended reader's old copy. */
internal fun commitGamificationProfile(
    store: BoxStore, candidate: GamificationProfileEntity, ledgerXp: Long,
    history: List<HistoryEntry>, weeklyGoal: Int, clock: Clock = Clock.systemDefaultZone(),
): GamificationProfileEntity = store.callInTx {
    val profiles = store.boxFor(GamificationProfileEntity::class.java)
    val current = profiles.query(GamificationProfileEntity_.offlineUserId.equal("local")).build().use { it.findFirst() }
    val bonuses = store.boxFor(IronLedgerEventEntity::class.java).all.filter { it.sourceType == "bonus" && !it.invalidated }
    fun times(raw: String?) = runCatching { Json.decodeFromString<Map<String, Long>>(raw ?: "{}") }.getOrDefault(emptyMap())
    fun completions(raw: String?) = runCatching { Json.decodeFromString<Map<String, Int>>(raw ?: "{}") }.getOrDefault(emptyMap())
    val circuits = completions(candidate.makeupCompletionsJson).toMutableMap()
    completions(current?.makeupCompletionsJson).forEach { (key, count) -> circuits[key] = maxOf(circuits[key] ?: 0, count) }
    bonuses.filter { it.eventKind == "recovery_circuit" && it.eventId.startsWith("recovery:") }
        .forEach { circuits[it.eventId.removePrefix("recovery:")] = 1 }
    val durable = times(candidate.badgeUnlocksJson) + times(current?.badgeUnlocksJson)
    val badges = (candidate.unlockedBadges.split(',') + current?.unlockedBadges.orEmpty().split(',') + durable.keys)
        .map(String::trim).filter(String::isNotBlank).distinct()
    candidate.id = current?.id ?: 0L
    candidate.offlineUserId = "local"
    candidate.unlockedBadges = badges.joinToString(",")
    candidate.badgeUnlocksJson = Json.encodeToString(mergeBadgeUnlockTimes(badges, durable, emptyMap(), clock.millis()))
    candidate.activeTitle = latestEarnedBadgeId(candidate.unlockedBadges, candidate.badgeUnlocksJson)?.let(::earnedBadgeTitle)
        ?: gradeTitle(IronGrade.entries.firstOrNull { it.label == candidate.rank } ?: IronGrade.UNCALIBRATED)
    candidate.makeupCompletionsJson = Json.encodeToString(circuits.toMap())
    candidate.streakWeeks = StreakEngine(clock.zone).computeStreakWeeks(history, weeklyGoal, circuits, now = clock.instant())
    candidate.totalXp = ledgerXp.coerceAtLeast(0L) + bonuses.sumOf { it.xpDelta.toLong() }.coerceAtLeast(0L)
    val engine = IronLedgerEngine(clock.zone, clock)
    candidate.level = engine.levelFromTotalXp(candidate.totalXp)
    candidate.xpInLevel = engine.xpInCurrentLevel(candidate.totalXp)
    candidate.updatedAt = clock.millis()
    profiles.put(candidate)
    candidate
}
