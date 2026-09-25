package com.ironlog.app.ui.screens.home

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ironlog.app.ui.animatedCardShine
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.repository.BodyMeasurementRepository
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.components.IronGradeBadge
import com.ironlog.app.ui.components.AchievementBadge
import com.ironlog.app.ui.components.ironGradeColor
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
import com.ironlog.app.domain.intelligence.TrainingIntelligenceEngine
import com.ironlog.app.domain.intelligence.TrainingIntelligenceProfile
import com.ironlog.app.domain.intelligence.TrainingDayPreferences
import com.ironlog.app.domain.intelligence.WorkoutSuggestionEngine
import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.ironlog.app.assets.ForgeFoxExpression
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.ui.viewmodel.GamificationViewModel
import com.ironlog.app.ui.viewmodel.GamificationViewModelFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ironlog.app.util.formatWeightFromKg
import com.ironlog.app.services.ShareService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.ProgressionPolicySnapshot
import com.ironlog.app.data.repository.ProgressionPolicyStore
import com.ironlog.app.domain.gamification.DailyProofStatus
import com.ironlog.app.domain.gamification.dailyWorkoutStreakDays
import com.ironlog.app.domain.gamification.parseHistoryLocalDate
import com.ironlog.app.domain.gamification.IronGrade
import com.ironlog.app.domain.badges.BadgeDefinitions
import com.ironlog.app.domain.intelligence.ManualRecoveryInput
import com.ironlog.app.domain.intelligence.ProgressionAction
import com.ironlog.app.domain.intelligence.ProgressionAdvice
import com.ironlog.app.domain.intelligence.ProgressionRecommendationEngine
import com.ironlog.app.domain.intelligence.ResolvedProgressionPolicy
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.ironlog.app.ui.screens.intelligence.ApexEngineCard
import com.ironlog.app.ui.screens.intelligence.CloudAiCard
import com.ironlog.app.ui.screens.recovery.RecoveryHeatmapCard
import com.ironlog.app.ui.screens.workout.parseRepTarget

@Composable
fun HomeScreen(
    vm: AppDataViewModel = viewModel(),
    onStartWorkout: (planId: String, dayId: String) -> Unit = { _, _ -> },
    onStartEmptyWorkout: () -> Unit = {},
    onOpenBodyWeight: () -> Unit = {},
    onOpenRecovery: () -> Unit = {},
    onOpenTrainingIntelligence: () -> Unit = {},
    onOpenProgramPicker: () -> Unit = {},
    onOpenProgramInsights: () -> Unit = {},
    onOpenVolumeAnalytics: () -> Unit = {},
    onResumeWorkout: () -> Unit = {},
    onDiscardWorkout: () -> Unit = {},
    onOpenStatusWindow: () -> Unit = {},
) {
    val c = useTheme()
    val context = LocalContext.current
    val gamificationVm: GamificationViewModel = viewModel(
        factory = GamificationViewModelFactory(
            context.applicationContext as Application,
            ObjectBox.store,
        )
    )
    val nowEpochMs by com.ironlog.app.ui.state.rememberPresentationTime()
    val gamState by gamificationVm.uiState.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var latestBodyweight by remember { mutableStateOf<Double?>(null) }
    // FIXED: 10 — also fetch date and weekly delta for Body Weight card
    var latestBodyweightDate by remember { mutableStateOf<String?>(null) }
    var weeklyWeightDelta by remember { mutableStateOf<Double?>(null) }
    val bodyRepo = remember { BodyMeasurementRepository() }
    val bodyMeasurements by remember(bodyRepo) { bodyRepo.getBodyMeasurementsFlow() }.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(bodyMeasurements, nowEpochMs) {
            latestBodyweight = runCatching { bodyRepo.getCurrentBodyweightKg() }.getOrNull()
        latestBodyweightDate = runCatching { bodyRepo.getLatestBodyweightDate() }.getOrNull()
        weeklyWeightDelta = runCatching { bodyRepo.getWeeklyBodyweightDelta() }.getOrNull()
    }

    val homeSettingsRepo = remember { SettingsRepository() }
    val painRegions = com.ironlog.app.domain.intelligence.RECOVERY_REGIONS
    val observedSettings by remember(homeSettingsRepo) {
        homeSettingsRepo.observeStrings(
            setOf("active_workout_day_name", "active_workout_start_ms", "manual_recovery_input") +
                painRegions.map { "pain_flag_$it" },
        )
    }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val activeWorkoutDayName = observedSettings["active_workout_day_name"]?.takeIf(String::isNotBlank)
    val activeWorkoutStartMs = observedSettings["active_workout_start_ms"]?.toLongOrNull() ?: 0L
    val manualRecoveryInput = com.ironlog.app.domain.intelligence.RecoveryCheckInCodec.decode(observedSettings["manual_recovery_input"], nowEpochMs)
    val painFlags = painRegions.filter { observedSettings["pain_flag_$it"] == "true" }.toSet()
    LaunchedEffect(state.history, state.settings, activeWorkoutDayName, manualRecoveryInput, painFlags, nowEpochMs) {
        gamificationVm.refreshFromHistory(state.history, state.settings.weeklyGoalDays)
    }

    val streak = gamState.dailyStreakDays
    val recentSessions = remember(state.history, nowEpochMs) {
        val now = Instant.ofEpochMilli(nowEpochMs)
        state.history.count { com.ironlog.app.domain.gamification.CreditedProof.qualifies(it, now) &&
            com.ironlog.app.domain.gamification.parseHistoryInstant(it.date)?.isBefore(now.minusSeconds(30L * 86400)) == false }
    }
    val avgDurationMin = remember(state.history) {
        if (state.history.isEmpty()) 0
        else (state.history.take(10).sumOf { it.duration }.toDouble() / state.history.take(10).size / 60.0).roundToInt().coerceAtLeast(1)
    }
    val weeklyGoalDays = state.settings.weeklyGoalDays.coerceIn(1, 7)
    val trainingDayStatus = remember(state.settings.trainingDayIndices, nowEpochMs) {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        TrainingDayPreferences.status(state.settings.trainingDayIndices, today)
    }
    val weeklyHistory = remember(state.history, nowEpochMs) {
        val now = Instant.ofEpochMilli(nowEpochMs)
        val zone = ZoneId.systemDefault()
        val week = com.ironlog.app.domain.gamification.proofWeekKey(now.atZone(zone).toLocalDate())
        state.history.filter { com.ironlog.app.domain.gamification.CreditedProof.qualifies(it, now, zone) &&
            com.ironlog.app.domain.gamification.parseHistoryLocalDate(it.date, zone)?.let { date -> com.ironlog.app.domain.gamification.proofWeekKey(date) } == week }
    }
    val thisWeekSessions = weeklyHistory.size
    val thisWeekSets = weeklyHistory.sumOf { com.ironlog.app.domain.gamification.CreditedProof.hardSetCount(it) }
    val thisWeekVolumeKg = weeklyHistory.sumOf { it.volume }.roundToInt()
    val goalStreak = gamState.streakWeeks
    // Prefer the plan explicitly marked active; fall back to first (most-recently-updated) plan
    val activePlan = state.plans.firstOrNull { it.isActive } ?: state.plans.firstOrNull()
    val planDays = activePlan?.days ?: emptyList()
    val progressionPlanExerciseIds = remember(activePlan) {
        activePlan?.days.orEmpty().flatMap { it.exercises }.map { it.id }.filter(String::isNotBlank).toSet()
    }
    val conservativeProgressionSnapshot = remember {
        val fallback = ResolvedProgressionPolicy.conservativeDefault()
        ProgressionPolicySnapshot(fallback = fallback, byPlanExerciseId = emptyMap())
    }
    val progressionPolicyStore = remember(homeSettingsRepo) { ProgressionPolicyStore(homeSettingsRepo) }
    val progressionPolicySnapshot by produceState(
        initialValue = conservativeProgressionSnapshot,
        activePlan?.id,
        progressionPlanExerciseIds,
        state.settings.progressionStyle,
    ) {
        value = try {
            progressionPolicyStore.load(activePlan?.id, progressionPlanExerciseIds)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            conservativeProgressionSnapshot
        }
    }
    val progressionInsightPolicy = remember(progressionPolicySnapshot, activePlan, state.history) {
        val latestExercise = state.history.firstOrNull()?.exercises?.firstOrNull()
        val matchingPlanRow = activePlan?.days.orEmpty().asSequence().flatMap { it.exercises.asSequence() }
            .firstOrNull { planExercise ->
                latestExercise != null &&
                    (planExercise.exerciseId == latestExercise.exerciseId || planExercise.name.equals(latestExercise.name, ignoreCase = true))
            }
        progressionPolicySnapshot.forPlanExercise(matchingPlanRow?.id)
    }
    val goalModeLabel = state.settings.goalMode
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { ch -> ch.titlecase() } }

    val readiness = remember(state.history, painFlags, nowEpochMs) {
        RecoveryReadinessEngine.readinessByRegion(state.history, painFlags, nowEpochMs)
    }

    // Fatigue-based workout suggestion — reorder plan days so the freshest is first
    val suggestionEngine = remember { WorkoutSuggestionEngine() }
    val orderedPlanDays = remember(planDays, readiness) {
        if (planDays.size <= 1) planDays
        else {
            val dayExerciseNames = planDays.map { day -> day.exercises.map { it.name } }
            val bestIdx = suggestionEngine.suggestDayIndex(readiness, dayExerciseNames)
            val recommended = planDays[bestIdx]
            listOf(recommended) + planDays.filterIndexed { i, _ -> i != bestIdx }
        }
    }
    val recommendedDayId: String? = orderedPlanDays.firstOrNull()?.id
    val dailyProofAction = remember(
        gamState.dailyProofPrimaryRoute,
        activePlan?.id,
        recommendedDayId,
        planDays,
    ) {
        {
            when (gamState.dailyProofPrimaryRoute) {
                "ActiveWorkout" -> onResumeWorkout()
                "RecoveryMap" -> onOpenRecovery()
                "statusWindow" -> onOpenStatusWindow()
                "ProgramPicker" -> onOpenProgramPicker()
                "Home" -> {
                    val targetDayId = recommendedDayId ?: planDays.firstOrNull()?.id.orEmpty()
                    if (activePlan != null && targetDayId.isNotBlank()) {
                        onStartWorkout(activePlan.id, targetDayId)
                    } else {
                        onOpenProgramPicker()
                    }
                }
                else -> onOpenStatusWindow()
            }
        }
    }
    val recoveryBlurb: String? = remember(readiness, orderedPlanDays, painFlags, trainingDayStatus) {
        when {
            painFlags.isNotEmpty() -> "Pain flagged: review Recovery before choosing your session."
            !trainingDayStatus.isTrainingDay -> {
                val nextDay = trainingDayStatus.nextTrainingDate.format(DateTimeFormatter.ofPattern("EEEE"))
                "Today is outside your protected training rhythm. Next protected day: $nextDay. You can still train if recovery supports it."
            }
            else -> orderedPlanDays.firstOrNull()?.let { day ->
                if (planDays.size > 1) {
                    suggestionEngine.recommendationBlurb(readiness, day.name, day.exercises.map { it.name })
                } else null
            }
        }
    }

    val intelligenceSnapshot = remember(
        state.history,
        state.settings.goalMode,
        state.settings.weeklyGoalDays,
        state.prResetAtEpochMs,
        nowEpochMs,
    ) {
        TrainingIntelligenceEngine.build(
            history = state.history,
            profile = TrainingIntelligenceProfile(
                goalMode = state.settings.goalMode,
                weeklyGoalDays = state.settings.weeklyGoalDays,
            ),
            clock = java.time.Clock.fixed(Instant.ofEpochMilli(nowEpochMs), ZoneId.systemDefault()),
            prResetAt = state.prResetAtEpochMs?.let(Instant::ofEpochMilli),
        )
    }

    // Step 7: thisWeekSets/thisWeekVolumeKg/totalSessions/unlockedMilestones removed (cards moved to StatsScreen)

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(16.dp),
    ) {
        item {
            // FIXED: 8 — date eyebrow + context-aware subtitle
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase(),
                color = c.muted,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
            Text(
                streakContextSubtitle(streak, state.history.firstOrNull()?.date),
                color = c.muted,
                fontSize = IronLogType.body.fontSize.sp,
            )
            Text(
                state.settings.userName.ifBlank { "Athlete" },
                color = c.accent,
                fontWeight = FontWeight(IronLogType.display.fontWeight),
                fontSize = IronLogType.display.fontSize.sp,
                lineHeight = IronLogType.display.lineHeight.sp,
            )
        }
        // Task 10 — compact XP / rank bar
        item {
            DailyProofCard(
                gamState = gamState,
                onPrimaryAction = dailyProofAction,
                onOpenLedger = onOpenStatusWindow,
            )
        }
        if (activePlan != null) {
            item {
                StartWorkoutCard(
                    goalModeLabel = goalModeLabel,
                    activePlanName = activePlan.name,
                    avgDurationMin = avgDurationMin,
                    planDays = orderedPlanDays,
                    recommendedDayId = recommendedDayId,
                    recoveryBlurb = recoveryBlurb,
                    history = state.history,
                    pbKeys = state.pb.keys,
                    onClick = { onStartWorkout(activePlan.id, orderedPlanDays.firstOrNull()?.id.orEmpty()) },
                    onStartDay = { dayId -> onStartWorkout(activePlan.id, dayId) },
                    onStartEmptyWorkout = onStartEmptyWorkout,
                    activeWorkoutDayName = activeWorkoutDayName,
                    activeWorkoutStartMs = activeWorkoutStartMs,
                    onResumeWorkout = onResumeWorkout,
                    onDiscardWorkout = onDiscardWorkout,
                )
            }
        }
        item { HomeStatsRow(sessions = recentSessions, streak = streak, avgDurationMin = avgDurationMin) }
        item {
            WeeklyGoalCard(
                sessions = thisWeekSessions,
                goalDays = weeklyGoalDays,
                goalStreak = goalStreak,
            )
        }
        if (planDays.isNotEmpty()) {
            item {
                ThisWeekDayChips(
                    planDays = planDays,
                    history = state.history,
                    nowEpochMs = nowEpochMs,
                )
            }
        }
        item {
            WeeklySummaryCard(
                sessions = thisWeekSessions,
                sets = thisWeekSets,
                volumeKg = thisWeekVolumeKg,
                weightUnit = state.settings.weightUnit,
                streak = streak,
                onShare = {
                    ShareService.shareMinimalCardImage(
                        context = context,
                        title = "Ironlog Weekly Summary",
                        headline = "This Week",
                        metrics = listOf(
                            ShareService.ShareMetric("Workouts", thisWeekSessions.toString()),
                            ShareService.ShareMetric("Sets", thisWeekSets.toString()),
                            ShareService.ShareMetric("Volume", formatWeightFromKg(thisWeekVolumeKg.toDouble(), state.settings.weightUnit)),
                            ShareService.ShareMetric("Streak", "$streak d"),
                        ),
                    )
                },
            )
        }
        // Training Intelligence / APEX ENGINE card — switches based on intelligenceMode setting
        item {
            val sessionsPerWeek = (recentSessions / 4.3f).roundToInt().coerceAtLeast(0)
            val adherencePct = if (weeklyGoalDays > 0) (thisWeekSessions * 100 / weeklyGoalDays).coerceAtMost(100) else 0
            when (state.settings.intelligenceMode) {
                "gemini_nano" -> ApexEngineCard(
                    activePlanDay   = activePlan?.days?.firstOrNull(),
                    history         = state.history,
                    lastWorkoutId   = state.history.firstOrNull()?.id,
                    goalMode        = state.settings.goalMode,
                    sessionsPerWeek = sessionsPerWeek,
                    adherencePct    = adherencePct,
                    weeklyGoalDays  = state.settings.weeklyGoalDays,
                    prTrend         = intelligenceSnapshot.prTrend,
                    readiness       = readiness,
                    progressionPolicy = progressionInsightPolicy,
                    onSwitchToBuiltin = {
                        vm.mutateSettingsAsync { it.copy(intelligenceMode = "builtin") }
                    },
                    onOpenTrainingIntelligence = onOpenTrainingIntelligence,
                )
                "cloud_ai" -> CloudAiCard(
                    activePlanDay   = activePlan?.days?.firstOrNull(),
                    history         = state.history,
                    lastWorkoutId   = state.history.firstOrNull()?.id,
                    goalMode        = state.settings.goalMode,
                    sessionsPerWeek = sessionsPerWeek,
                    adherencePct    = adherencePct,
                    weeklyGoalDays  = state.settings.weeklyGoalDays,
                    prTrend         = intelligenceSnapshot.prTrend,
                    readiness       = readiness,
                    displayName     = state.settings.cloudAiDisplayName.ifBlank { "Cloud AI" },
                    modelName       = state.settings.cloudAiModelName,
                    baseUrl         = state.settings.cloudAiBaseUrl,
                    apiFormat       = state.settings.cloudAiApiFormat,
                    providerPreset  = state.settings.cloudAiProviderPreset,
                    progressionPolicy = progressionInsightPolicy,
                    onSwitchToBuiltin = {
                        vm.mutateSettingsAsync { it.copy(intelligenceMode = "builtin") }
                    },
                    onOpenTrainingIntelligence = onOpenTrainingIntelligence,
                )
                else -> TrainingIntelligenceCard(
                    activePlanName = activePlan?.name,
                    sessionsPerWeek = sessionsPerWeek,
                    adherencePct   = adherencePct,
                    recommendedDay = activePlan?.days?.firstOrNull(),
                    history        = state.history,
                    weightUnit     = state.settings.weightUnit,
                    progressionPolicies = progressionPolicySnapshot,
                    onOpenInsights = onOpenProgramInsights,
                    onOpenTrainingIntelligence = onOpenTrainingIntelligence,
                )
            }
        }
        // Recovery card — full width so the body map isn't squished
        item {
            RecoveryHeatmapCard(
                groupReadiness = readiness,
                manualRecoveryInput = manualRecoveryInput,
                painFlags = painFlags,
                nowEpochMs = nowEpochMs,
                onTapExpand = onOpenRecovery,
                onOpenVolumeAnalytics = onOpenVolumeAnalytics,
            )
        }
        // Body Weight card — compact full-width row
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                    .background(c.card)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
                    .clickable(onClick = onOpenBodyWeight)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = appSpacedBy(10.dp)) {
                    Icon(Icons.Outlined.MonitorWeight, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = appSpacedBy(1.dp)) {
                        Text(
                            "BODY WEIGHT",
                            color = c.muted,
                            fontSize = IronLogType.eyebrow.fontSize.sp,
                            fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                            letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                        )
                        val weightText = latestBodyweight?.takeIf { it > 0.0 }
                            ?.let { formatWeightFromKg(it, state.settings.weightUnit) }
                            ?: "-- ${state.settings.weightUnit}"
                        Text(
                            weightText,
                            color = c.text,
                            fontSize = IronLogType.title.fontSize.sp,
                            fontWeight = FontWeight(IronLogType.display.fontWeight),
                            letterSpacing = (-0.5).sp,
                        )
                        latestBodyweightDate?.let { date ->
                            Text("Logged $date", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                        }
                    }
                }
                weeklyWeightDelta?.let { delta ->
                    if (kotlin.math.abs(delta) >= 0.05) {
                        val sign = if (delta >= 0) "+" else ""
                        Text(
                            "$sign${formatWeightFromKg(kotlin.math.abs(delta), state.settings.weightUnit)} wk",
                            color = if (delta <= 0) c.success else c.warning,
                            fontSize = IronLogType.meta.fontSize.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        // Athlete profile summary card
        item {
            AthleteProfileCard(
                totalSessions = state.history.size,
                streak = streak,
                onOpen = onOpenTrainingIntelligence,
            )
        }
        if (gamState.unlockedBadges.isNotEmpty()) {
            item { MilestonesCard(unlocked = gamState.unlockedBadges.toSet()) }
        }
    }
}

internal fun showDailyProofAction(status: DailyProofStatus): Boolean =
    status == DailyProofStatus.SETUP || status == DailyProofStatus.RECOVER_SMART

internal data class DailyProofVisualSpec(
    val slotWidthDp: Int,
    val slotHeightDp: Int,
    val imageSizeDp: Int,
)

internal fun dailyProofVisualSpec(availableWidthDp: Float): DailyProofVisualSpec = when {
    availableWidthDp < 340f -> DailyProofVisualSpec(slotWidthDp = 76, slotHeightDp = 104, imageSizeDp = 72)
    availableWidthDp < 420f -> DailyProofVisualSpec(slotWidthDp = 92, slotHeightDp = 112, imageSizeDp = 88)
    else -> DailyProofVisualSpec(slotWidthDp = 108, slotHeightDp = 124, imageSizeDp = 104)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyProofCard(
    gamState: com.ironlog.app.ui.viewmodel.GamificationUiState,
    onPrimaryAction: () -> Unit,
    onOpenLedger: () -> Unit,
) {
    val c = useTheme()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(IronLogRadius.xl.dp)).background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.xl.dp)).appPadding(14.dp),
        verticalArrangement = appSpacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val visualSpec = dailyProofVisualSpec(maxWidth.value)
            Row(
                Modifier.fillMaxWidth().heightIn(min = visualSpec.slotHeightDp.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = appSpacedBy(4.dp),
                ) {
                    Text("DAILY PROOF", color = c.muted, fontSize = 12.sp, letterSpacing = 1.5.sp)
                    Text(gamState.dailyProofHeadline, color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold)
                    Text(gamState.dailyProofDetail, color = c.subtext, fontSize = 12.sp)
                }
                Box(
                    Modifier.width(visualSpec.slotWidthDp.dp).height(visualSpec.slotHeightDp.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(ForgeFoxExpression.fromId(gamState.foxExpressionId).drawableRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(visualSpec.imageSizeDp.dp),
                    )
                }
            }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = appSpacedBy(2.dp),
        ) {
            Text("${gamState.rank} · Level ${gamState.level}", color = c.subtext, fontSize = 12.sp)
            TextButton(onClick = onOpenLedger, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    if (gamState.dailyStreakDays > 0) "${gamState.dailyStreakDays}d streak · Ledger" else "Open Ledger",
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (showDailyProofAction(gamState.dailyProofStatus)) {
            Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(gamState.dailyProofPrimaryActionLabel)
            }
        }
    }
}

@Composable
private fun ThisWeekDayChips(
    planDays: List<com.ironlog.app.ui.model.UiPlanDay>,
    history: List<HistoryEntry>,
    nowEpochMs: Long,
) {
    val c = useTheme()
    val now = Instant.ofEpochMilli(nowEpochMs)

    Column(verticalArrangement = appSpacedBy(8.dp)) {
        Text(
            "THIS WEEK",
            color = c.muted,
            fontSize = IronLogType.eyebrow.fontSize.sp,
            fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
            letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
        )
        LazyRow(horizontalArrangement = appSpacedBy(8.dp)) {
            lazyItems(planDays) { day ->
                val hit = com.ironlog.app.domain.gamification.hasPlanDayProofThisWeek(history, day.id, day.name, now)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (hit) c.accent.copy(alpha = 0.18f) else c.surface)
                        .border(1.dp, if (hit) c.accent else c.cardBorder, CircleShape)
                        .appPadding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.name,
                        color = if (hit) c.accent else c.subtext,
                        fontWeight = if (hit) FontWeight.Bold else FontWeight.Medium,
                        fontSize = IronLogType.meta.fontSize.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


private fun timeGreeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 5  -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        hour < 22 -> "Good evening"
        else      -> "Good night"
    }
}

// FIXED: 8 — context-aware subtitle based on streak/recency
private fun streakContextSubtitle(streak: Int, lastWorkoutDate: String?): String {
    if (streak >= 30) return "🔥 $streak day streak — legendary!"
    if (streak >= 14) return "🔥 $streak day streak — unstoppable!"
    if (streak >= 7)  return "🔥 $streak day streak — keep it going!"
    if (streak >= 3)  return "💪 $streak days strong — don't stop now!"
    val lastDate = lastWorkoutDate?.let { parseHistoryLocalDate(it) } ?: return timeGreeting()
    val days = ChronoUnit.DAYS.between(lastDate, LocalDate.now()).toInt()
    return when (days) {
        0    -> "You trained today — amazing!"
        1    -> "Pick up where you left off"
        in 2..3 -> "$days days since last workout"
        in 4..7 -> "A week away — come back strong"
        else -> timeGreeting()
    }
}

@Composable
private fun WeeklyGoalCard(sessions: Int, goalDays: Int, goalStreak: Int) {
    val c = useTheme()
    val progress = (sessions.toFloat() / goalDays.toFloat()).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .appPadding(14.dp),
        verticalArrangement = appSpacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly Goal", color = c.text, fontWeight = FontWeight(IronLogType.section.fontWeight), fontSize = IronLogType.section.fontSize.sp)
            Text(
                "$sessions / $goalDays",
                color = if (progress >= 1f) c.success else c.accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = IronLogType.body.fontSize.sp,
            )
        }
        // Progress track
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(IronLogRadius.full.dp))
                .background(c.faint),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(c.accent, c.accent.copy(alpha = 0.7f)),
                        ),
                    ),
            )
        }
        Row(horizontalArrangement = appSpacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Goal streak: ${goalStreak} week${if (goalStreak == 1) "" else "s"}",
                color = c.muted,
                fontSize = IronLogType.meta.fontSize.sp,
            )
        }
    }
}

@Composable
private fun ActiveWorkoutTimerText(startMs: Long, c: IronLogThemeTokens) {
    var elapsedSeconds by remember(startMs) {
        mutableLongStateOf(
            if (startMs > 0L) (System.currentTimeMillis() - startMs) / 1000L else 0L
        )
    }
    LaunchedEffect(startMs) {
        if (startMs > 0L) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - startMs) / 1000L
                delay(1000)
            }
        }
    }
    val timerText = remember(elapsedSeconds) {
        val s = elapsedSeconds
        val m = s / 60; val h = m / 60
        if (h > 0) "$h:${(m % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
        else "${m.toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }
    Text(timerText, color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.body.fontSize.sp)
}

@Composable
internal fun StartWorkoutCard(
    goalModeLabel: String,
    activePlanName: String,
    avgDurationMin: Int,
    planDays: List<UiPlanDay> = emptyList(),
    recommendedDayId: String? = null,
    recoveryBlurb: String? = null,
    history: List<HistoryEntry> = emptyList(),
    pbKeys: Set<String> = emptySet(),
    onClick: () -> Unit = {},
    onStartDay: (dayId: String) -> Unit = {},
    onStartEmptyWorkout: () -> Unit = {},
    activeWorkoutDayName: String? = null,
    activeWorkoutStartMs: Long = 0L,
    onResumeWorkout: () -> Unit = {},
    onDiscardWorkout: () -> Unit = {},
) {
    val c = useTheme()
    // A workout is active as soon as its day name is persisted — elapsed may still be 0 if no
    // sets have been logged yet (timer only starts on first set).
    val isWorkoutActive = !activeWorkoutDayName.isNullOrBlank()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.xl.dp))
            .background(c.card)
            .animatedCardShine(c.accent)
            .border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(IronLogRadius.xl.dp)),
    ) {
        Column(
            Modifier.appPadding(16.dp),
            verticalArrangement = appSpacedBy(12.dp),
        ) {
            // One header/action row; no empty row between the eyebrow and plan name.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isWorkoutActive) "IN PROGRESS" else "TODAY'S WORKOUT",
                    color = c.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = 0.12f))
                        .border(1.dp, c.accent.copy(alpha = 0.20f), CircleShape)
                        .clickable { if (isWorkoutActive) onResumeWorkout() else onClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isWorkoutActive) Icons.Outlined.Loop else Icons.Outlined.PlayArrow,
                        contentDescription = if (isWorkoutActive) "Resume workout" else "Start workout",
                        tint = c.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Plan / workout name + badges row
            Column(verticalArrangement = appSpacedBy(6.dp)) {
                Text(
                    if (isWorkoutActive) (activeWorkoutDayName ?: "Workout") else activePlanName.ifBlank { "Start Workout" },
                    color = c.text,
                    fontWeight = FontWeight(IronLogType.display.fontWeight),
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = appSpacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isWorkoutActive) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                .background(c.success.copy(alpha = 0.18f))
                                .appPadding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            // FIXED: 1
                            Text("● IN PROGRESS", color = c.success, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = IronLogType.micro.letterSpacing.sp)
                        }
                        ActiveWorkoutTimerText(activeWorkoutStartMs, c)
                    } else {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                .background(c.accent.copy(alpha = 0.14f))
                                .appPadding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(goalModeLabel, color = c.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                        if (planDays.isNotEmpty()) {
                            Text("${planDays.size} days" + if (avgDurationMin > 0) " · avg ${avgDurationMin}m" else "", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                        }
                    }
                }
            }

            if (isWorkoutActive) {
                // Resume CTA
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.md.dp))
                        .background(c.accent)
                        .clickable(onClick = onResumeWorkout)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("RESUME WORKOUT", color = c.textOnAccent, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.button.fontSize.sp, letterSpacing = IronLogType.button.letterSpacing.sp)
                }
                // Discard stale workout — clears stuck state after a crash
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.sm.dp))
                        .border(1.dp, c.danger.copy(alpha = 0.30f), RoundedCornerShape(IronLogRadius.sm.dp))
                        .clickable(onClick = onDiscardWorkout)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("DISCARD WORKOUT", color = c.danger.copy(alpha = 0.75f), fontWeight = FontWeight.Bold, fontSize = IronLogType.micro.fontSize.sp, letterSpacing = IronLogType.button.letterSpacing.sp)
                }
            } else if (planDays.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = appSpacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    lazyItems(planDays) { day ->
                        val isRecommended = day.id == recommendedDayId
                        val borderColor = if (isRecommended) c.accent else c.cardBorder
                        val cardBg = if (isRecommended) c.accent.copy(alpha = 0.14f) else c.surface

                        Box(
                            Modifier
                                .width(116.dp)
                                .heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                                .semantics { if (isRecommended) stateDescription = "Recommended for today" }
                                .clickable { onStartDay(day.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.appPadding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = appSpacedBy(4.dp),
                            ) {
                                Text(
                                    day.name.uppercase(),
                                    color = if (isRecommended) c.accent else c.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${day.exercises.size} exercises",
                                    color = c.subtext,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.1.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                // Recovery blurb — shown when engine has a recommendation
                if (recoveryBlurb != null) {
                    Text(
                        text = recoveryBlurb,
                        color = c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                        modifier = Modifier.appPadding(horizontal = 2.dp),
                    )
                }

                // Quick-start without plan
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.sm.dp))
                        .border(1.dp, c.accent.copy(alpha = 0.13f), RoundedCornerShape(IronLogRadius.sm.dp))
                        .clickable(onClick = onStartEmptyWorkout)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = appSpacedBy(5.dp)) {
                        Icon(Icons.Outlined.Add, null, tint = c.muted, modifier = Modifier.size(12.dp))
                        // FIXED: 1
                        Text("START WITHOUT PLAN", color = c.muted, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = IronLogType.micro.letterSpacing.sp)
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.md.dp))
                        .background(c.accent)
                        .clickable(onClick = onClick)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // FIXED: 1
                    Text("START WORKOUT", color = c.textOnAccent, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.button.fontSize.sp, letterSpacing = IronLogType.button.letterSpacing.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeStatsRow(sessions: Int, streak: Int, avgDurationMin: Int) {
    val c = useTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .appPadding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeStatMetric(sessions.toString(), "Sessions")
        Box(Modifier.width(1.dp).height(36.dp).background(c.faint))
        HomeStatMetric(streak.toString(), "Streak")
        Box(Modifier.width(1.dp).height(36.dp).background(c.faint))
        HomeStatMetric(if (avgDurationMin > 0) "${avgDurationMin}m" else "--", "Avg Min")
    }
}

@Composable
private fun HomeStatMetric(value: String, label: String) {
    val c = useTheme()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (label == "Streak") {
            Icon(
                Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.height(appGapDp(2.dp)))
        }
        Text(
            value,
            color = c.text,
            fontWeight = FontWeight(IronLogType.display.fontWeight),
            fontSize = IronLogType.title.fontSize.sp,
        )
        Text(
            label,
            color = c.muted,
            fontSize = IronLogType.eyebrow.fontSize.sp,
            fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
            letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
        )
    }
}

@Composable
private fun FullWidthIntelCard(
    sup: String,
    accentColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val c = useTheme()
    val barColor = accentColor ?: c.accent
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(IronLogRadius.md.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.md.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(barColor))
        Column(
            Modifier.weight(1f).padding(16.dp),
            verticalArrangement = appSpacedBy(4.dp),
        ) {
            Text(sup, color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingIntelligenceCard(
    activePlanName: String?,
    sessionsPerWeek: Int,
    adherencePct: Int,
    recommendedDay: UiPlanDay?,
    history: List<HistoryEntry>,
    weightUnit: String,
    progressionPolicies: ProgressionPolicySnapshot,
    onOpenInsights: () -> Unit,
    onOpenTrainingIntelligence: () -> Unit,
) {
    val c = useTheme()
    val cardBg = c.accent.copy(alpha = 0.10f)
    val accentBorder = c.accent.copy(alpha = 0.35f)
    val nextLabel = activePlanName?.let { name -> "Your next session from \"$name\" is ready." }
        ?: "Build adaptive targets around your schedule."

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(cardBg)
            .border(1.dp, accentBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .appPadding(16.dp),
        verticalArrangement = appSpacedBy(10.dp),
    ) {
        // Eyebrow
        Text(
            "TRAINING INTELLIGENCE",
            color = c.accent,
            fontSize = IronLogType.eyebrow.fontSize.sp,
            fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
            letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
        )
        // Dynamic headline
        Text(
            nextLabel,
            color = c.text,
            fontSize = IronLogType.section.fontSize.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = IronLogType.section.lineHeight.sp,
        )
        // Subtext
        Text(
            "Log sessions to unlock adaptive targets that respond to your progress.",
            color = c.muted,
            fontSize = IronLogType.meta.fontSize.sp,
        )
        // Stats row
        Text(
            "$sessionsPerWeek sessions/wk · $adherencePct% adherence",
            color = c.subtext,
            fontSize = IronLogType.micro.fontSize.sp,
            fontWeight = FontWeight.Medium,
        )
        val suggestions = remember(recommendedDay, history, progressionPolicies) {
            buildAdaptiveTargets(recommendedDay, history, progressionPolicies)
        }
        if (suggestions.isNotEmpty()) {
            Column(verticalArrangement = appSpacedBy(8.dp)) {
                suggestions.forEach { s ->
                    val presentation = adaptiveTargetPresentation(s, weightUnit)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = appSpacedBy(4.dp)) {
                        Text(
                            presentation.recommendation,
                            color = c.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = appSpacedBy(6.dp),
                            verticalArrangement = appSpacedBy(4.dp),
                        ) {
                            Text(
                                presentation.provenance,
                                color = c.muted,
                                fontSize = 12.sp,
                            )
                            presentation.statusLabel?.let { status ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                        .background(c.warning.copy(alpha = 0.12f))
                                        .border(1.dp, c.warning.copy(alpha = 0.45f), RoundedCornerShape(IronLogRadius.full.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        status,
                                        color = c.warning,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // CTA row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Outline CTA button
            Box(
                Modifier
                    .border(1.dp, c.accent, RoundedCornerShape(IronLogRadius.full.dp))
                    .clickable(onClick = onOpenInsights)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    "OPEN PROGRAM INSIGHTS →",
                    color = c.accent,
                    fontSize = IronLogType.meta.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.button.fontWeight),
                )
            }
        }
        // Recommended plan link
        Text(
            "✦ Open recommended plan →",
            color = c.accent,
            fontSize = IronLogType.meta.fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onOpenTrainingIntelligence),
        )
    }
}

internal data class AdaptiveTarget(
    val name: String,
    val advice: ProgressionAdvice?,
    val policy: ResolvedProgressionPolicy,
    val plateau: Boolean,
)

internal data class AdaptiveTargetPresentation(
    val recommendation: String,
    val provenance: String,
    val statusLabel: String?,
)

internal fun adaptiveTargetPresentation(
    target: AdaptiveTarget,
    weightUnit: String,
): AdaptiveTargetPresentation {
    val recommendation = when (target.advice?.action) {
        ProgressionAction.ADD_LOAD ->
            "${target.name} → ${formatWeightFromKg(target.advice.weightKg, weightUnit)} × ${target.advice.reps}"
        ProgressionAction.ADD_REPS -> "${target.name} → ${target.advice.reps} reps at the same load"
        ProgressionAction.HOLD -> "${target.name} → Hold the current target"
        null -> "${target.name} → Build to a working weight"
    }
    return AdaptiveTargetPresentation(
        recommendation = recommendation,
        provenance = "${target.policy.label} · ${target.policy.source.label}",
        statusLabel = if (target.plateau) "⟳ Plateau" else null,
    )
}

internal fun buildAdaptiveTargets(
    day: UiPlanDay?,
    history: List<HistoryEntry>,
    policies: ProgressionPolicySnapshot,
): List<AdaptiveTarget> {
    if (day == null) return emptyList()
    return day.exercises.take(3).map { ex ->
        val policy = policies.forPlanExercise(ex.id)
        val matchingSession = history.firstOrNull { workout ->
            workout.exercises.any { hx ->
                hx.exerciseId == ex.exerciseId || hx.name.equals(ex.name, ignoreCase = true)
            }
        }
        val matchingHistory = matchingSession?.exercises?.firstOrNull { hx ->
            hx.exerciseId == ex.exerciseId || hx.name.equals(ex.name, ignoreCase = true)
        }
        val workingSets = matchingHistory?.sets.orEmpty().filter { set ->
            !set.isWarmup && !set.type.equals("warmup", ignoreCase = true) && set.reps > 0
        }
        if (workingSets.isEmpty()) {
            AdaptiveTarget(ex.name, advice = null, policy = policy, plateau = false)
        } else {
            val ghostSets = workingSets.map { set ->
                com.ironlog.app.ui.state.GhostSet(
                    weight = set.weight,
                    reps = set.reps,
                    type = set.type,
                    rpe = set.rpe,
                    rir = set.rir,
                )
            }
            val advice = ProgressionRecommendationEngine.recommend(
                previous = ghostSets,
                trackingType = matchingHistory?.trackingType ?: ex.trackingType,
                targetSets = ex.sets,
                targetReps = parseRepTarget(ex.reps, 8),
                sourceDate = matchingSession?.date,
                policy = policy,
            )
            val recentE1rm = workingSets.takeLast(3).map { it.weight * (1.0 + it.reps / 30.0) }
            val plateau = recentE1rm.size >= 3 && recentE1rm.zipWithNext().all { (a, b) -> b <= a + 0.25 }
            AdaptiveTarget(
                name = ex.name,
                advice = advice,
                policy = policy,
                plateau = plateau,
            )
        }
    }
}

@Composable
private fun AccentBorderCard(modifier: Modifier, onClick: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val c = useTheme()
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(IronLogRadius.md.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.md.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(c.accent))
        Column(
            Modifier.weight(1f).padding(12.dp),
            verticalArrangement = appSpacedBy(4.dp),
        ) {
            content()
        }
    }
}

private fun homeScreenAgeDays(iso: String): Long = runCatching {
    // Instant.parse requires a full ISO-8601 instant (with time + Z).
    // Bare date strings like "2025-01-01" will throw DateTimeParseException;
    // fall back to LocalDate.parse for those, and return MAX_VALUE on any failure
    // so the entry is treated as too old to show (filtered out of the 30-day window).
    val date = try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    } catch (_: Exception) {
        LocalDate.parse(iso.substringBefore('T'))
    }
    ChronoUnit.DAYS.between(date, LocalDate.now())
}.getOrDefault(Long.MAX_VALUE)

fun getStreak(
    history: List<HistoryEntry>,
    nowEpochMs: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Int = dailyWorkoutStreakDays(history, nowEpochMs, zoneId)

fun getWeekKey(dateString: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val date = parseHistoryLocalDate(dateString, zoneId) ?: return ""
    val fields = WeekFields.ISO
    return "${date.get(fields.weekBasedYear())}-W${date.get(fields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
}

fun getGoalStreak(history: List<HistoryEntry>, weeklyGoalDays: Int, zoneId: ZoneId = ZoneId.systemDefault()): Int {
    val target = max(1, min(7, weeklyGoalDays))
    if (history.isEmpty()) return 0
    val byWeek = linkedMapOf<String, MutableSet<String>>()
    history.forEach { session ->
        val day = parseHistoryLocalDate(session.date, zoneId) ?: return@forEach
        val weekKey = getWeekKey(session.date, zoneId)
        byWeek.getOrPut(weekKey) { linkedSetOf() }.add(day.toString())
    }
    var streak = 0
    byWeek.keys.sortedDescending().forEach { week ->
        val count = byWeek[week]?.size ?: 0
        if (count >= target) streak++ else if (streak > 0) return streak
    }
    return streak
}

fun countSessionsThisWeek(
    history: List<HistoryEntry>,
    now: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Int {
    val fields = WeekFields.ISO
    val y = now.get(fields.weekBasedYear())
    val w = now.get(fields.weekOfWeekBasedYear())
    return history.mapNotNull { parseHistoryLocalDate(it.date, zoneId) }
        .count { d -> d.get(fields.weekBasedYear()) == y && d.get(fields.weekOfWeekBasedYear()) == w }
}

@Composable
private fun WeeklySummaryCard(
    sessions: Int,
    sets: Int,
    volumeKg: Int,
    weightUnit: String,
    streak: Int,
    onShare: () -> Unit,
) {
    val c = useTheme()
    val displayVol = if (weightUnit == "lbs") (volumeKg * 2.2046226218).roundToInt() else volumeKg
    val volStr = if (displayVol >= 1000) "%.1fk".format(displayVol / 1000.0) else displayVol.toString()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .appPadding(16.dp),
        verticalArrangement = appSpacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("WEEKLY SUMMARY", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            TextButton(onClick = onShare) { Text("SHARE", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = appSpacedBy(0.dp)) {
            SummaryStatPill("Workouts", sessions.toString(), Modifier.weight(1f))
            SummaryStatPill("Sets", sets.toString(), Modifier.weight(1f))
            SummaryStatPill("Volume", "$volStr $weightUnit", Modifier.weight(1f))
            SummaryStatPill("Streak", "$streak d", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    val c = useTheme()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = appSpacedBy(2.dp)) {
        Text(value, color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.section.fontSize.sp)
        Text(label, color = c.muted, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = 0.5.sp)
    }
}

@Composable
private fun AthleteProfileCard(totalSessions: Int, streak: Int, onOpen: () -> Unit) {
    val c = useTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = appSpacedBy(4.dp)) {
            Text("TRAINING PROFILE", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            Text("Program intelligence", color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.section.fontSize.sp)
            Text("$totalSessions logged sessions · $streak-day streak", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
        }
        Text("→", color = c.accent, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MilestonesCard(unlocked: Set<String>) {
    val c = useTheme()
    val grades = IronGrade.entries.filter { it != IronGrade.UNCALIBRATED && it.label in unlocked }
    val achievements = BadgeDefinitions.all.filter { it.id in unlocked }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .appPadding(16.dp),
        verticalArrangement = appSpacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("MILESTONES", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            Text("${grades.size + achievements.size} earned", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = appSpacedBy(10.dp)) {
            grades.forEach { grade -> HomeGradeMilestone(grade) }
            achievements.forEach { badge -> HomeAchievementMilestone(badge) }
        }
    }
}

@Composable
private fun HomeGradeMilestone(grade: IronGrade) {
    val c = useTheme()
    val accent = ironGradeColor(grade.label)
    Column(
        Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(IronLogRadius.md.dp))
            .background(c.card)
            .border(1.dp, accent.copy(alpha = 0.36f), RoundedCornerShape(IronLogRadius.md.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = appSpacedBy(6.dp),
    ) {
        IronGradeBadge(rank = grade.label, accent = accent, modifier = Modifier.size(58.dp))
        Text("${grade.label} grade", color = c.text, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun HomeAchievementMilestone(badge: com.ironlog.app.domain.badges.BadgeDefinition) {
    val c = useTheme()
    Column(
        Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(IronLogRadius.md.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.md.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = appSpacedBy(6.dp),
    ) {
        AchievementBadge(definition = badge, unlocked = true, modifier = Modifier.size(56.dp))
        Text(badge.title, color = c.text, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
