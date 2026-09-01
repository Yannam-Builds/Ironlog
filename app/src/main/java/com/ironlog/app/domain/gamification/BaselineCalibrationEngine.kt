package com.ironlog.app.domain.gamification

import com.ironlog.app.ui.model.HistoryEntry
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.roundToInt

const val ONBOARDING_BASELINE_EVENT_ID = "onboarding:baseline:v1"
const val ONBOARDING_BASELINE_PROVENANCE = "onboarding_self_report"
const val ONBOARDING_BASELINE_FORMULA =
    "trainingAgeMonths*4.345*historicalTrainingDaysPerWeek"

data class BaselineCalibrationResult(
    val estimatedLifetimeSessions: Int,
    val xp: Long,
    val grade: IronGrade,
    val stats: IronLedgerStats,
    val supportedBadgeIds: Set<String>,
    val fingerprint: String,
    val trustScore: Double = 0.5,
)

data class EffectiveBaselineAward(
    val xp: Long,
    val overlappingProofCount: Int,
)

internal fun effectiveOnboardingBaselineAward(
    baseline: BaselineCalibrationResult,
    history: List<HistoryEntry>,
    baselineOccurredAt: Instant,
    zoneId: ZoneId,
): EffectiveBaselineAward {
    val overlappingProofCount = history.count { workout ->
        val occurredAt = parseHistoryInstant(workout.date, zoneId)
        occurredAt != null &&
            !occurredAt.isAfter(baselineOccurredAt) &&
            CreditedProof.qualifies(workout, baselineOccurredAt, zoneId)
    }
    val unprovenEstimatedSessions =
        (baseline.estimatedLifetimeSessions - overlappingProofCount).coerceAtLeast(0)
    return EffectiveBaselineAward(
        xp = unprovenEstimatedSessions.toLong() * 20L,
        overlappingProofCount = overlappingProofCount,
    )
}

/**
 * One authoritative projection for onboarding's self-reported baseline.
 *
 * The stat and rank curves preserve IronLog's original calibration seeding model. A baseline is
 * deliberately capped at Titanium; higher grades still require verified workout proof.
 */
class BaselineCalibrationEngine {
    fun calculate(calibration: AthleteCalibration): BaselineCalibrationResult {
        val normalized = calibration.normalizedForBaseline()
        val sessions = (
            normalized.trainingAgeMonths * 4.345 * normalized.historicalTrainingDaysPerWeek
            ).roundToInt().coerceAtLeast(0)
        val stats = baselineStats(normalized, sessions)
        val grade = baselineGrade(normalized, sessions, stats)
        return BaselineCalibrationResult(
            estimatedLifetimeSessions = sessions,
            // Minimum verified workout proof is 40 XP. Self-report enters at half trust.
            xp = sessions.toLong() * 20L,
            grade = grade,
            stats = stats,
            supportedBadgeIds = supportedBadges(normalized, sessions),
            fingerprint = fingerprint(normalized),
        )
    }

    private fun baselineStats(
        calibration: AthleteCalibration,
        sessions: Int,
    ): IronLedgerStats {
        if (!hasMeaningfulBaseline(calibration)) return IronLedgerStats()
        val exposure = sessions.toDouble()
        val bodyweight = calibration.bodyweightKg ?: 0.0
        val push = calibration.baselinePushups.toDouble()
        val pull = calibration.baselinePullups.toDouble()
        val bench = calibration.baselineBenchKg.toDouble()
        val lat = calibration.baselineLatPulldownKg.toDouble()
        val mile = calibration.baselineMileRunSeconds.takeIf { it > 0 }?.toDouble()
        val paceFactor = mile?.let { (900.0 / it).coerceIn(0.0, 2.0) } ?: 0.0

        val strengthRaw = bench * 2.4 + lat * 1.5 + pull * 6.0 + push * 1.2 +
            bodyweight * 0.45 + exposure * 0.30
        val powerRaw = bench * 1.6 + pull * 3.5 + push * 0.8 + exposure * 0.12
        val hypertrophyRaw = bench * 1.4 + lat * 1.1 + push * 1.6 + pull * 2.2 +
            exposure * 0.22
        val enduranceRaw = push * 0.9 + exposure * 0.08 + paceFactor * 30.0 +
            calibration.weeklyGoalDays * 2.5
        val agilityRaw = pull + paceFactor * 55.0 + exposure * 0.05
        val disciplineRaw = exposure * 0.16 + calibration.weeklyGoalDays * 8.0 +
            if (calibration.hasPastTraining) 18.0 else 0.0
        val recoveryRaw = exposure * 0.10 + calibration.weeklyGoalDays * 6.0 +
            if (calibration.hasGymAccess) 8.0 else 4.0

        return IronLedgerStats(
            strength = toStat(strengthRaw, 45.0),
            power = toStat(powerRaw, 28.0),
            hypertrophy = toStat(hypertrophyRaw, 55.0),
            endurance = toStat(enduranceRaw, 18.0),
            agility = toStat(agilityRaw, 16.0),
            discipline = toStat(disciplineRaw, 22.0),
            recovery = toStat(recoveryRaw, 24.0),
        )
    }

    private fun baselineGrade(
        calibration: AthleteCalibration,
        sessions: Int,
        stats: IronLedgerStats,
    ): IronGrade {
        if (!hasMeaningfulBaseline(calibration)) return IronGrade.UNCALIBRATED
        val score = listOf(
            stats.strength,
            stats.power,
            stats.hypertrophy,
            stats.endurance,
            stats.agility,
            stats.discipline,
            stats.recovery,
        ).average()
        val months = calibration.trainingAgeMonths
        return when {
            score >= 150.0 && sessions >= 180 && months >= 16 -> IronGrade.TITANIUM
            score >= 110.0 && sessions >= 90 && months >= 10 -> IronGrade.STEEL
            score >= 80.0 && sessions >= 28 && months >= 6 -> IronGrade.IRON
            score >= 45.0 && sessions >= 8 && months >= 2 -> IronGrade.GRAPHITE
            else -> IronGrade.UNCALIBRATED
        }
    }

    private fun supportedBadges(
        calibration: AthleteCalibration,
        sessions: Int,
    ): Set<String> = buildSet {
        if (sessions >= 1) add("first_workout")
        if (sessions >= 10) add("workouts_10")
        if (sessions >= 50) add("workouts_50")
        if (sessions >= 100) add("workouts_100")
        if (calibration.trainingAgeMonths * 4.345 >= 4.0 && sessions >= 4) {
            add("consistency_4w")
        }
        if (calibration.trainingAgeMonths * 30.4375 >= 365.0 && sessions >= 1) {
            add("member_365")
        }
    }

    private fun fingerprint(calibration: AthleteCalibration): String {
        val canonical = listOf(
            calibration.trainingAgeMonths,
            calibration.historicalTrainingDaysPerWeek,
            calibration.weeklyGoalDays,
            calibration.bodyweightKg ?: 0.0,
            calibration.hasPastTraining,
            calibration.hasGymAccess,
            calibration.baselinePushups,
            calibration.baselinePullups,
            calibration.baselineBenchKg,
            calibration.baselineLatPulldownKg,
            calibration.baselineMileRunSeconds,
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun hasMeaningfulBaseline(calibration: AthleteCalibration): Boolean =
        calibration.trainingAgeMonths > 0 ||
            calibration.bodyweightKg != null ||
            calibration.hasPastTraining ||
            calibration.baselinePushups > 0 ||
            calibration.baselinePullups > 0 ||
            calibration.baselineBenchKg > 0 ||
            calibration.baselineLatPulldownKg > 0 ||
            calibration.baselineMileRunSeconds > 0

    private fun toStat(value: Double, scale: Double): Int =
        (1 + ln(1.0 + value / scale) * 180.0).roundToInt().coerceIn(1, 999)
}

private fun AthleteCalibration.normalizedForBaseline(): AthleteCalibration = copy(
    trainingAgeMonths = trainingAgeMonths.coerceAtLeast(0),
    historicalTrainingDaysPerWeek = historicalTrainingDaysPerWeek.coerceIn(1, 7),
    weeklyGoalDays = weeklyGoalDays.coerceIn(1, 7),
    bodyweightKg = bodyweightKg?.takeIf { it > 0.0 },
    baselinePushups = baselinePushups.coerceAtLeast(0),
    baselinePullups = baselinePullups.coerceAtLeast(0),
    baselineBenchKg = baselineBenchKg.coerceAtLeast(0),
    baselineLatPulldownKg = baselineLatPulldownKg.coerceAtLeast(0),
    baselineMileRunSeconds = baselineMileRunSeconds.coerceAtLeast(0),
)

internal fun mergeBaselineAndVerifiedStats(
    baseline: IronLedgerStats,
    verified: IronLedgerStats,
): IronLedgerStats = IronLedgerStats(
    strength = maxOf(baseline.strength, verified.strength),
    power = maxOf(baseline.power, verified.power),
    hypertrophy = maxOf(baseline.hypertrophy, verified.hypertrophy),
    endurance = maxOf(baseline.endurance, verified.endurance),
    agility = maxOf(baseline.agility, verified.agility),
    discipline = maxOf(baseline.discipline, verified.discipline),
    recovery = maxOf(baseline.recovery, verified.recovery),
)
