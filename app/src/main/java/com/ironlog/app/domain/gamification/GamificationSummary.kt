package com.ironlog.app.domain.gamification

import com.ironlog.app.assets.ForgeFoxExpression
import com.ironlog.app.ui.model.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

enum class DailyProofStatus {
    SETUP,
    FIRST_PROOF,
    ACTIVE_WORKOUT,
    PROOF_LOGGED,
    TRAIN_TODAY,
    AT_RISK,
    RECOVER_SMART,
}

data class DailyProofSummary(
    val status: DailyProofStatus,
    val headline: String,
    val detail: String,
    val primaryActionLabel: String,
    val primaryRoute: String,
    val foxExpressionId: String,
)

internal fun parseHistoryInstant(value: String, zoneId: ZoneId = ZoneId.systemDefault()): Instant? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null

    return runCatching { Instant.parse(trimmed) }.getOrNull()
        ?: runCatching {
            LocalDate.parse(trimmed).atStartOfDay(zoneId).toInstant()
        }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(trimmed.replace(" ", "T"))
                .atZone(zoneId)
                .toInstant()
        }.getOrNull()
}

internal fun parseHistoryLocalDate(value: String, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
    parseHistoryInstant(value, zoneId)?.atZone(zoneId)?.toLocalDate()

fun dailyWorkoutStreakDays(
    history: List<HistoryEntry>,
    nowEpochMs: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Int {
    if (history.isEmpty()) return 0
    val now = Instant.ofEpochMilli(nowEpochMs)
    val dates = history.filter { CreditedProof.qualifies(it, now, zoneId) }.mapNotNull { parseHistoryLocalDate(it.date, zoneId) }.toSet()
    if (dates.isEmpty()) return 0

    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
    val yesterday = today.minusDays(1)
    if (today !in dates && yesterday !in dates) return 0

    var streak = 0
    var cursor = if (today in dates) today else yesterday
    while (cursor in dates) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

fun buildDailyProofSummary(
    history: List<HistoryEntry>,
    hasActivePlan: Boolean,
    activeWorkoutDayName: String?,
    readinessScore: Int?,
    nowEpochMs: Long = System.currentTimeMillis(),
    painFlags: Set<String> = emptySet(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): DailyProofSummary {
    if (!activeWorkoutDayName.isNullOrBlank()) {
        return DailyProofSummary(
            status = DailyProofStatus.ACTIVE_WORKOUT,
            headline = activeWorkoutDayName,
            detail = if (painFlags.isEmpty()) "Current session is still in progress." else "Pain flagged: review affected movements before continuing.",
            primaryActionLabel = "Resume workout",
            primaryRoute = "ActiveWorkout",
            foxExpressionId = ForgeFoxExpression.Determined.id,
        )
    }

    if (painFlags.isNotEmpty()) {
        return DailyProofSummary(
            status = DailyProofStatus.RECOVER_SMART,
            headline = "Check in before training",
            detail = "Pain flagged in ${painFlags.sorted().joinToString()}. A readiness estimate does not clear painful movements.",
            primaryActionLabel = "Review recovery",
            primaryRoute = "RecoveryMap",
            foxExpressionId = ForgeFoxExpression.RestBlanket.id,
        )
    }

    if (history.isEmpty() && !hasActivePlan) {
        return DailyProofSummary(
            status = DailyProofStatus.SETUP,
            headline = "Set your proof loop",
            detail = "Pick a plan so Home can drive the next session automatically.",
            primaryActionLabel = "Choose a program",
            primaryRoute = "ProgramPicker",
            foxExpressionId = ForgeFoxExpression.Clipboard.id,
        )
    }

    val now = Instant.ofEpochMilli(nowEpochMs)
    val creditedHistory = history.filter { CreditedProof.qualifies(it, now, zoneId) }
    if (creditedHistory.isEmpty()) {
        return DailyProofSummary(
            status = DailyProofStatus.FIRST_PROOF,
            headline = "First proof awaits",
            detail = "Complete your first qualifying session to start the Iron Ledger.",
            primaryActionLabel = "Start workout",
            primaryRoute = "Home",
            foxExpressionId = ForgeFoxExpression.Determined.id,
        )
    }

    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
    val lastWorkoutDate = creditedHistory.mapNotNull { parseHistoryLocalDate(it.date, zoneId) }.maxOrNull()
    val daysSinceWorkout = lastWorkoutDate?.let { ChronoUnit.DAYS.between(it, today).toInt() } ?: Int.MAX_VALUE

    if (daysSinceWorkout <= 0) {
        return DailyProofSummary(
            status = DailyProofStatus.PROOF_LOGGED,
            headline = "Proof saved",
            detail = "Today's session already counts toward your ledger.",
            primaryActionLabel = "Open Iron Ledger",
            primaryRoute = "statusWindow",
            foxExpressionId = ForgeFoxExpression.Proud.id,
        )
    }

    if ((readinessScore ?: 0) >= 85) {
        return DailyProofSummary(
            status = DailyProofStatus.TRAIN_TODAY,
            headline = "Fresh enough to press",
            detail = "Recovery is high enough to push the next proof session.",
            primaryActionLabel = "Train today",
            primaryRoute = "Home",
            foxExpressionId = ForgeFoxExpression.Flexing.id,
        )
    }

    if (daysSinceWorkout >= 2) {
        return DailyProofSummary(
            status = DailyProofStatus.AT_RISK,
            headline = "Your rhythm is slipping",
            detail = "The next workout matters more than another scroll.",
            primaryActionLabel = "Train today",
            primaryRoute = "Home",
            foxExpressionId = ForgeFoxExpression.CheckingWatch.id,
        )
    }

    return DailyProofSummary(
        status = DailyProofStatus.RECOVER_SMART,
        headline = "Recover on purpose",
        detail = "Use today's readiness before forcing extra fatigue.",
        primaryActionLabel = "Open recovery",
        primaryRoute = "RecoveryMap",
        foxExpressionId = ForgeFoxExpression.RestBlanket.id,
    )
}
