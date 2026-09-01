package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity_
import com.ironlog.app.data.objectbox.IronLedgerEventEntity
import com.ironlog.app.data.objectbox.IronLedgerEventEntity_
import com.ironlog.app.domain.gamification.AthleteCalibration
import com.ironlog.app.domain.gamification.BaselineCalibrationEngine
import com.ironlog.app.domain.gamification.BaselineCalibrationResult
import com.ironlog.app.domain.gamification.EffectiveBaselineAward
import com.ironlog.app.domain.gamification.IronGrade
import com.ironlog.app.domain.gamification.IronLedgerEngine
import com.ironlog.app.domain.gamification.IronLedgerStats
import com.ironlog.app.domain.gamification.ONBOARDING_BASELINE_EVENT_ID
import com.ironlog.app.domain.gamification.ONBOARDING_BASELINE_FORMULA
import com.ironlog.app.domain.gamification.ONBOARDING_BASELINE_PROVENANCE
import com.ironlog.app.domain.gamification.RpgStats
import com.ironlog.app.domain.gamification.effectiveOnboardingBaselineAward
import com.ironlog.app.domain.gamification.historicalBadgeUnlocks
import com.ironlog.app.domain.gamification.historicalPrBadgeUnlocks
import io.objectbox.BoxStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/** Calibration, provenance event, rank/stats/XP, and inferred badges share one retry-safe write. */
internal fun persistOnboardingBaselineAtomically(
    store: BoxStore,
    calibration: AthleteCalibrationEntity,
    bodyweightKg: Double?,
    occurredAtMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): BaselineCalibrationResult = store.callInTx {
    require(occurredAtMs > 0L)
    // Capture the self-reported value before current-bodyweight reconciliation denormalizes the
    // athlete row from any existing history.
    val baselineCalibrationEntity = calibration.copy(
        bodyweightKg = bodyweightKg?.takeIf(::isCanonicalAthleteBodyweightKg),
    )
    persistOnboardingAthleteBodyweightInTransaction(
        store = store,
        calibration = calibration,
        bodyweightKg = bodyweightKg,
        measuredAt = occurredAtMs,
    )
    val domainCalibration = baselineCalibrationEntity.toDomainCalibration()
    val result = BaselineCalibrationEngine().calculate(domainCalibration)
    val history = HistoryRepository(store).completedSnapshotBlocking()
    val clock = Clock.fixed(Instant.ofEpochMilli(occurredAtMs), zoneId)
    val previousEvent = findOnboardingBaselineEvent(store)
    val baselineOccurredAtMs = previousEvent?.occurredAt?.takeIf { it > 0L } ?: occurredAtMs
    val effectiveAward = effectiveOnboardingBaselineAward(
        baseline = result,
        history = history,
        baselineOccurredAt = Instant.ofEpochMilli(baselineOccurredAtMs),
        zoneId = zoneId,
    )
    val previousBaselineRewards = baselineRewardIds(previousEvent)
    upsertOnboardingBaselineEvent(
        store = store,
        calibration = domainCalibration,
        result = result,
        effectiveAward = effectiveAward,
        occurredAtMs = baselineOccurredAtMs,
    )

    val snapshot = IronLedgerEngine(zoneId, clock).rebuild(
        history = history,
        weeklyGoal = domainCalibration.weeklyGoalDays,
        calibration = domainCalibration,
        effectiveBaselineXp = effectiveAward.xp,
    )
    val verifiedSnapshot = IronLedgerEngine(zoneId, clock).rebuild(
        history = history,
        weeklyGoal = domainCalibration.weeklyGoalDays,
        calibration = AthleteCalibration(weeklyGoalDays = domainCalibration.weeklyGoalDays),
        effectiveBaselineXp = 0L,
    )
    val independentlyProvenBadges = historicalBadgeUnlocks(history, clock.instant(), zoneId).keys +
        historicalPrBadgeUnlocks(history, verifiedSnapshot.events, clock.instant(), zoneId).keys
    val independentlyProvenGradeBadges = IronGrade.entries
        .filter { it != IronGrade.UNCALIBRATED && it.ordinal <= verifiedSnapshot.grade.ordinal }
        .map(IronGrade::label)
        .toSet()
    val profiles = store.boxFor(GamificationProfileEntity::class.java)
    val profile = profiles.query(GamificationProfileEntity_.offlineUserId.equal("local"))
        .build().use { it.findFirst() }
        ?: GamificationProfileEntity(offlineUserId = "local")
    migrateLegacyBonusXpBlocking(
        store = store,
        profileXp = profile.totalXp,
        ledgerXp = (snapshot.totalXp - effectiveAward.xp).coerceAtLeast(0L),
        nowEpochMs = occurredAtMs,
    )
    val currentBaselineGradeBadges = IronGrade.entries
        .filter { it != IronGrade.UNCALIBRATED && it.ordinal <= result.grade.ordinal }
        .map(IronGrade::label)
        .toSet()
    val currentBaselineRewards = result.supportedBadgeIds + currentBaselineGradeBadges
    val revokedBaselineRewards = previousBaselineRewards - independentlyProvenBadges -
        independentlyProvenGradeBadges - currentBaselineRewards
    val existingBadges = profile.unlockedBadges.split(',')
        .map(String::trim)
        .filter { it.isNotBlank() && it !in revokedBaselineRewards }
    val baselineGradeBadges = IronGrade.entries
        .filter { it != IronGrade.UNCALIBRATED && it.ordinal <= result.grade.ordinal }
        .map(IronGrade::label)
    val badges = (existingBadges + baselineGradeBadges + result.supportedBadgeIds).distinct()
    val unlocks = runCatching {
        Json.decodeFromString<Map<String, Long>>(profile.badgeUnlocksJson ?: "{}")
    }.getOrDefault(emptyMap())
        .filterKeys { it !in revokedBaselineRewards }
        .toMutableMap()
    badges.forEach { badge -> unlocks.putIfAbsent(badge, occurredAtMs) }
    profile.unlockedBadges = badges.joinToString(",")
    profile.badgeUnlocksJson = Json.encodeToString(unlocks.toMap())
    profile.rank = snapshot.grade.label
    profile.statsJson = Json.encodeToString(snapshot.stats.toRpgStats())

    commitGamificationProfile(
        store = store,
        candidate = profile,
        ledgerXp = snapshot.totalXp,
        history = history,
        weeklyGoal = domainCalibration.weeklyGoalDays,
        clock = clock,
        revokedBadges = revokedBaselineRewards,
    )
    result
}

/** Fixed event identity makes refreshes and onboarding retries updates, never additional awards. */
internal fun upsertOnboardingBaselineEvent(
    store: BoxStore,
    calibration: AthleteCalibration,
    result: BaselineCalibrationResult,
    effectiveAward: EffectiveBaselineAward,
    occurredAtMs: Long,
): IronLedgerEventEntity = store.callInTx {
    val box = store.boxFor(IronLedgerEventEntity::class.java)
    val previous = box.query(IronLedgerEventEntity_.eventId.equal(ONBOARDING_BASELINE_EVENT_ID))
        .build().use { it.findFirst() }
    val event = (previous ?: IronLedgerEventEntity()).apply {
        eventId = ONBOARDING_BASELINE_EVENT_ID
        sourceType = "onboarding"
        sourceId = "local"
        eventKind = "baseline_calibration"
        occurredAt = previous?.occurredAt?.takeIf { it > 0L } ?: occurredAtMs
        xpDelta = effectiveAward.xp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        statDeltasJson = result.stats.toStatJson().toString()
        trustScore = result.trustScore
        fingerprint = result.fingerprint
        metadataJson = JSONObject()
            .put("title", "Onboarding baseline seeded")
            .put(
                "detail",
                "Self-reported calibration; verified workouts add proof XP, refine, and build on signals",
            )
            .put("provenance", ONBOARDING_BASELINE_PROVENANCE)
            .put("formula", ONBOARDING_BASELINE_FORMULA)
            .put("estimatedLifetimeSessions", result.estimatedLifetimeSessions)
            .put("overlappingProofCount", effectiveAward.overlappingProofCount)
            .put("rawBaselineXp", result.xp)
            .put("effectiveBaselineXp", effectiveAward.xp)
            .put("trainingAgeMonths", calibration.trainingAgeMonths)
            .put("historicalTrainingDaysPerWeek", calibration.historicalTrainingDaysPerWeek)
            .put("grade", result.grade.label)
            .put("supportedBadgeIds", JSONArray(result.supportedBadgeIds.sorted()))
            .toString()
        invalidated = false
    }
    box.put(event)
    event
}

internal fun onboardingEffectiveBaselineXp(
    store: BoxStore,
    fallbackXp: Long,
): Long {
    val event = findOnboardingBaselineEvent(store) ?: return fallbackXp.coerceAtLeast(0L)
    val metadata = runCatching { JSONObject(event.metadataJson) }.getOrDefault(JSONObject())
    return if (metadata.has("effectiveBaselineXp")) {
        metadata.optLong("effectiveBaselineXp", event.xpDelta.toLong())
    } else {
        event.xpDelta.toLong()
    }.coerceAtLeast(0L)
}

internal fun onboardingBaselineOccurredAtMs(store: BoxStore, fallbackMs: Long): Long =
    findOnboardingBaselineEvent(store)?.occurredAt?.takeIf { it > 0L } ?: fallbackMs

private fun findOnboardingBaselineEvent(store: BoxStore): IronLedgerEventEntity? =
    store.boxFor(IronLedgerEventEntity::class.java)
        .query(IronLedgerEventEntity_.eventId.equal(ONBOARDING_BASELINE_EVENT_ID))
        .build().use { it.findFirst() }

private fun baselineRewardIds(event: IronLedgerEventEntity?): Set<String> {
    event ?: return emptySet()
    val metadata = runCatching { JSONObject(event.metadataJson) }.getOrDefault(JSONObject())
    val supported = metadata.optJSONArray("supportedBadgeIds")
    val supportedIds = buildSet {
        if (supported != null) {
            for (index in 0 until supported.length()) {
                supported.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
    val previousGrade = IronGrade.entries.firstOrNull { it.label == metadata.optString("grade") }
    val gradeIds = IronGrade.entries
        .filter { grade ->
            previousGrade != null && grade != IronGrade.UNCALIBRATED && grade.ordinal <= previousGrade.ordinal
        }
        .map(IronGrade::label)
    return supportedIds + gradeIds
}

internal fun AthleteCalibrationEntity.toDomainCalibration(): AthleteCalibration = AthleteCalibration(
    trainingAgeMonths = trainingAgeMonths,
    historicalTrainingDaysPerWeek = historicalTrainingDaysPerWeek,
    importedHistory = importedHistory,
    weeklyGoalDays = weeklyGoalDays,
    bodyweightKg = bodyweightKg,
    hasPastTraining = hasPastTraining,
    hasGymAccess = hasGymAccess,
    baselinePushups = baselinePushups,
    baselinePullups = baselinePullups,
    baselineBenchKg = baselineBenchKg,
    baselineLatPulldownKg = baselineLatPulldownKg,
    baselineMileRunSeconds = baselineMileRunSeconds,
)

private fun IronLedgerStats.toRpgStats(): RpgStats = RpgStats(
    str = strength,
    vit = recovery,
    end = endurance,
    agi = agility,
    wis = discipline,
    luk = power,
)

private fun IronLedgerStats.toStatJson(): JSONObject = JSONObject()
    .put("STR", strength)
    .put("PWR", power)
    .put("HYP", hypertrophy)
    .put("END", endurance)
    .put("AGI", agility)
    .put("DISC", discipline)
    .put("REC", recovery)
