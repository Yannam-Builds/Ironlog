package com.ironlog.app.widget

import android.content.Context
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity_
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseEntity_
import com.ironlog.app.data.objectbox.GamificationProfileEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity_
import com.ironlog.app.data.objectbox.IronLedgerEventEntity
import com.ironlog.app.data.objectbox.PlanDayEntity
import com.ironlog.app.data.objectbox.PlanDayEntity_
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.PlanEntity_
import com.ironlog.app.data.objectbox.PlanExerciseEntity
import com.ironlog.app.data.objectbox.PlanExerciseEntity_
import com.ironlog.app.domain.gamification.AthleteCalibration
import com.ironlog.app.domain.gamification.DailyProofStatus
import com.ironlog.app.domain.gamification.IronLedgerEngine
import com.ironlog.app.domain.gamification.IronGrade
import com.ironlog.app.domain.gamification.StreakEngine
import com.ironlog.app.domain.badges.BadgeDefinitions
import com.ironlog.app.domain.gamification.buildDailyProofSummary
import com.ironlog.app.domain.gamification.dailyWorkoutStreakDays
import com.ironlog.app.domain.gamification.parseHistoryLocalDate
import com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
import com.ironlog.app.domain.intelligence.WorkoutSuggestionEngine
import com.ironlog.app.ui.model.HistoryEntry
import io.objectbox.BoxStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlinx.serialization.json.Json

/**
 * Canonical widget snapshot builder using Iron Ledger as source of truth.
 */
class WidgetDataRepository(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val boxStore: BoxStore,
) {
    private val ledgerEngine = IronLedgerEngine()
    private val streakEngine = StreakEngine()
    private val suggestionEngine = WorkoutSuggestionEngine()

    fun buildWidgetState(
        history: List<HistoryEntry>,
        weeklyGoal: Int,
    ): WidgetState {
        val weeklyGoalSafe = weeklyGoal.coerceAtLeast(1)
        val calibration = readCalibration(weeklyGoalSafe)
        val snapshot = ledgerEngine.rebuild(
            history = history,
            weeklyGoal = weeklyGoalSafe,
            calibration = calibration,
        )

        // Rebuild total XP from workout proof plus durable, idempotent bonus events.
        // Do not use cached profile.totalXp: it must be able to decrease after history deletion.
        val storedProfile = boxStore.boxFor(GamificationProfileEntity::class.java)
            .query(GamificationProfileEntity_.offlineUserId.equal("local"))
            .build().use { it.findFirst() }
        val bonusXp = boxStore.boxFor(IronLedgerEventEntity::class.java).all
            .filter { !it.invalidated && it.sourceType == "bonus" }
            .sumOf { it.xpDelta.toLong() }
            .coerceAtLeast(0L)
        val reconciledXp = (snapshot.totalXp + bonusXp).coerceAtLeast(0L)
        val reconciledLevel = ledgerEngine.levelFromTotalXp(reconciledXp)
        val reconciledXpInLevel = ledgerEngine.xpInCurrentLevel(reconciledXp)
        val reconciledXpForNextLevel = ledgerEngine.xpForLevel(reconciledLevel)

        val recentHistory = history.sortedByDescending { parseHistoryLocalDate(it.date) }.take(60)
        val (recommendedDayName, recommendedDayBlurb) = buildRecommendation(recentHistory)
        val weekSessions = countIsoWeekSessions(history)
        val readinessScore = RecoveryReadinessEngine.score(
            RecoveryReadinessEngine.readinessByRegion(recentHistory)
        ).score
        val latestBadgeTitle = readLatestBadgeTitle(storedProfile, snapshot.grade.label)
        val recoveryCircuitCompletions = runCatching {
            Json.decodeFromString<Map<String, Int>>(storedProfile?.makeupCompletionsJson ?: "{}")
        }.getOrDefault(emptyMap())
        val currentStreakWeeks = streakEngine.computeStreakWeeks(
            history = history,
            weeklyGoal = weeklyGoalSafe,
            recoveryCircuitCompletions = recoveryCircuitCompletions,
        )
        val dailyProof = buildDailyProofSummary(
            history = history,
            hasActivePlan = recommendedDayName != "No Plan",
            activeWorkoutDayName = readActiveWorkoutDayName(),
            readinessScore = readinessScore,
        )
        val nowMs = System.currentTimeMillis()
        val localNow = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
        val today = localNow.toLocalDate()
        val historyDates = history.mapNotNull { parseHistoryLocalDate(it.date) }.toSet()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val weeklyCompletion = buildWeeklyCompletion(historyDates, weekStart)
        val todayCompleted = today in historyDates
        val dailyStreakDays = dailyWorkoutStreakDays(history, nowMs)
        val previousWorkoutDate = historyDates.filter { it.isBefore(today) }.maxOrNull()
        val returnedAfterGap = todayCompleted &&
            previousWorkoutDate?.let { ChronoUnit.DAYS.between(it, today) >= 2 } == true
        val reminderMinutesOfDay = readDailyReminderMinutesOfDay()
        val notificationsEnabled = readSettingString("notifications_enabled")
            ?.equals("true", ignoreCase = true) ?: false
        val minutesUntilWorkout = if (!todayCompleted && notificationsEnabled && reminderMinutesOfDay != null) {
            minutesUntilScheduledTime(localNow, reminderMinutesOfDay)
        } else null
        val scheduledWorkoutHour = reminderMinutesOfDay?.div(60)
        val isAtRisk = dailyProof.status == DailyProofStatus.AT_RISK ||
            (dailyStreakDays > 0 && !todayCompleted && localNow.hour >= 18)
        val isRecoveryDay = dailyProof.status == DailyProofStatus.RECOVER_SMART
        val hasNewPb = isRecentWidgetEvent(
            eventEpochMs = readSettingString("widget_last_new_pb_ms")?.toLongOrNull(),
            nowEpochMs = nowMs,
            maxAgeHours = 24,
        )
        val visualState = resolveWidgetVisualState(
            WidgetVisualInputs(
                streakDays = dailyStreakDays,
                todayCompleted = todayCompleted,
                weekSessionsCount = weekSessions,
                weeklyGoal = weeklyGoalSafe,
                minutesUntilWorkout = minutesUntilWorkout,
                scheduledWorkoutHour = scheduledWorkoutHour,
                isAtRisk = isAtRisk,
                isRecoveryDay = isRecoveryDay,
                hasRecentNewPb = hasNewPb,
                returnedAfterGap = returnedAfterGap,
            )
        )

        return WidgetState(
            grade = snapshot.grade.label,
            level = reconciledLevel,
            title = storedProfile?.activeTitle?.takeIf(String::isNotBlank)
                ?: titleForGrade(snapshot.grade.label),
            dailyStreakDays = dailyStreakDays,
            streakWeeks = currentStreakWeeks,
            integrityScore = snapshot.integrityScore,
            totalXp = reconciledXp,
            xpInLevel = reconciledXpInLevel,
            xpForNextLevel = reconciledXpForNextLevel,
            signalStrength = snapshot.stats.strength,
            signalPower = snapshot.stats.power,
            signalHypertrophy = snapshot.stats.hypertrophy,
            signalEndurance = snapshot.stats.endurance,
            signalAgility = snapshot.stats.agility,
            signalDiscipline = snapshot.stats.discipline,
            signalRecovery = snapshot.stats.recovery,
            recommendedDayName = recommendedDayName,
            recommendedDayBlurb = recommendedDayBlurb,
            weekSessionsCount = weekSessions,
            weeklyGoal = weeklyGoalSafe,
            nextGradeLabel = snapshot.nextGrade?.label ?: "Apex",
            nextGradeOutstanding = snapshot.nextGradeGates.count { !it.met },
            dailyProofHeadline = dailyProof.headline,
            dailyProofDetail = dailyProof.detail,
            primaryActionLabel = dailyProof.primaryActionLabel,
            foxExpressionId = dailyProof.foxExpressionId,
            latestBadgeTitle = latestBadgeTitle,
            weeklyCompletion = weeklyCompletion,
            todayCompleted = todayCompleted,
            minutesUntilWorkout = minutesUntilWorkout,
            isAtRisk = isAtRisk,
            isRecoveryDay = isRecoveryDay,
            hasNewPb = hasNewPb,
            visualState = visualState,
            primaryActionRoute = routeForVisualState(visualState, dailyProof.primaryRoute),
            lastUpdatedEpochMs = nowMs,
        )
    }

    private fun readCalibration(weeklyGoal: Int): AthleteCalibration {
        val box = boxStore.boxFor(AthleteCalibrationEntity::class.java)
        val entity = box.query(AthleteCalibrationEntity_.offlineUserId.equal("local"))
            .build().use { it.findFirst() }
        return AthleteCalibration(
            trainingAgeMonths = entity?.trainingAgeMonths ?: 0,
            historicalTrainingDaysPerWeek = entity?.historicalTrainingDaysPerWeek?.takeIf { it in 1..7 } ?: 3,
            importedHistory = entity?.importedHistory ?: false,
            weeklyGoalDays = entity?.weeklyGoalDays ?: weeklyGoal,
            bodyweightKg = entity?.bodyweightKg,
            hasPastTraining = entity?.hasPastTraining ?: false,
            hasGymAccess = entity?.hasGymAccess ?: true,
            baselinePushups = entity?.baselinePushups ?: 0,
            baselinePullups = entity?.baselinePullups ?: 0,
            baselineBenchKg = entity?.baselineBenchKg ?: 0,
            baselineLatPulldownKg = entity?.baselineLatPulldownKg ?: 0,
            baselineMileRunSeconds = entity?.baselineMileRunSeconds ?: 0,
        )
    }

    private fun buildRecommendation(history: List<HistoryEntry>): Pair<String, String> {
        val readiness = RecoveryReadinessEngine.readinessByRegion(history)

        val planBox = boxStore.boxFor(PlanEntity::class.java)
        val dayBox = boxStore.boxFor(PlanDayEntity::class.java)
        val planExerciseBox = boxStore.boxFor(PlanExerciseEntity::class.java)
        val exerciseBox = boxStore.boxFor(ExerciseEntity::class.java)

        val activePlan = planBox.query(PlanEntity_.isActive.equal(true))
            .build().use { it.findFirst() }
            ?: return "No Plan" to ""

        val days = dayBox.query(PlanDayEntity_.planUid.equal(activePlan.uid))
            .order(PlanDayEntity_.orderIndex)
            .build().use { it.find() }
        if (days.isEmpty()) return activePlan.name to ""

        // Collect all exercise UIDs across all days in one pass, then batch-load names.
        val allPlanExercises = planExerciseBox.query(
            PlanExerciseEntity_.planDayUid.oneOf(days.map { it.uid }.toTypedArray())
        ).order(PlanExerciseEntity_.orderIndex).build().use { it.find() }
        val allUids = allPlanExercises.map { it.exerciseUid }.distinct()
        val exerciseNameByUid: Map<String, String> = if (allUids.isEmpty()) emptyMap()
        else exerciseBox.query(ExerciseEntity_.uid.oneOf(allUids.toTypedArray()))
            .build().use { q -> q.find() }
            .associate { it.uid to it.name }
        val dayExerciseNames = days.map { day ->
            allPlanExercises
                .filter { it.planDayUid == day.uid }
                .mapNotNull { exerciseNameByUid[it.exerciseUid] }
        }
        val idx = if (days.size > 1) suggestionEngine.suggestDayIndex(readiness, dayExerciseNames) else 0
        val bestDay = days.getOrNull(idx) ?: days.first()
        val blurb = if (days.size > 1) suggestionEngine.recommendationBlurb(readiness, bestDay.name) else ""
        return bestDay.name to blurb
    }

    private fun countIsoWeekSessions(history: List<HistoryEntry>): Int {
        val iso = WeekFields.ISO
        val now = LocalDate.now()
        val year = now.get(iso.weekBasedYear())
        val week = now.get(iso.weekOfWeekBasedYear())
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        return history.count { entry ->
            runCatching {
                val date = LocalDate.parse(entry.date.take(10), fmt)
                date.get(iso.weekBasedYear()) == year &&
                    date.get(iso.weekOfWeekBasedYear()) == week
            }.getOrDefault(false)
        }
    }

    private fun titleForGrade(grade: String): String = when (grade) {
        "Uncalibrated" -> "Ledger Initiate"
        "Graphite" -> "Graphite Trainee"
        "Iron" -> "Iron Regular"
        "Steel" -> "Steel Builder"
        "Titanium" -> "Titanium Athlete"
        "Obsidian" -> "Obsidian Veteran"
        "Iridium" -> "Iridium Specialist"
        "Aether" -> "Aether Standard"
        "Apex" -> "Apex Ledger"
        else -> "Ledger Initiate"
    }

    private fun readLatestBadgeTitle(profile: GamificationProfileEntity?, fallback: String): String {
        val badgeId = profile?.unlockedBadges
            ?.split(",")
            ?.map(String::trim)
            ?.lastOrNull(String::isNotBlank)
            ?: return fallback
        BadgeDefinitions.all.firstOrNull { it.id == badgeId }?.let { return it.title }
        IronGrade.entries.firstOrNull { it.label == badgeId }?.let { return "${it.label} grade" }
        return badgeId.replace('_', ' ').split(' ')
            .joinToString(" ") { token -> token.replaceFirstChar(Char::titlecase) }
    }

    private fun readActiveWorkoutDayName(): String? =
        boxStore.boxFor(AppSettingEntity::class.java)
            .query(com.ironlog.app.data.objectbox.AppSettingEntity_.key.equal("active_workout_day_name"))
            .build().use { it.findFirst()?.value?.takeIf(String::isNotBlank) }

    private fun readSettingString(key: String): String? =
        boxStore.boxFor(AppSettingEntity::class.java)
            .query(com.ironlog.app.data.objectbox.AppSettingEntity_.key.equal(key))
            .build().use { it.findFirst()?.value?.takeIf(String::isNotBlank) }

    private fun readDailyReminderMinutesOfDay(): Int? {
        return readSettingString("dailyReminderTimeMinutes")
            ?.toDoubleOrNull()
            ?.toInt()
            ?.takeIf { it in 0 until 24 * 60 }
    }

    private fun routeForVisualState(
        visualState: WidgetVisualState,
        fallback: String,
    ): String = when (visualState) {
        WidgetVisualState.AT_RISK,
        WidgetVisualState.WORKOUT_SOON,
        WidgetVisualState.EARLY_WORKOUT,
        WidgetVisualState.NO_EXCUSES -> "Home"
        WidgetVisualState.RECOVERY_DAY -> "RecoveryMap"
        WidgetVisualState.WORKOUT_DONE,
        WidgetVisualState.NEW_PB,
        WidgetVisualState.COMEBACK,
        WidgetVisualState.MISSION_COMPLETE -> "statusWindow"
        WidgetVisualState.ACTIVE_STREAK -> fallback
    }
}
