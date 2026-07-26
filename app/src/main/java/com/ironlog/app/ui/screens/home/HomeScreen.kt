package com.ironlog.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import com.ironlog.app.ui.rememberAnimatedCardBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.data.repository.BodyMeasurementRepository
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.components.IronGradeBadge
import com.ironlog.app.ui.components.ironGradeColor
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
import com.ironlog.app.domain.intelligence.TrainingIntelligenceEngine
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
import com.ironlog.app.data.health.BiometricSnapshot
import com.ironlog.app.data.health.HealthConnectRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.domain.gamification.DailyProofStatus
import com.ironlog.app.domain.intelligence.ManualRecoveryInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.health.connect.client.PermissionController
import com.ironlog.app.ui.screens.settings.HealthConnectPermissionSheet
import com.ironlog.app.ui.screens.intelligence.ApexEngineCard
import com.ironlog.app.ui.screens.intelligence.CloudAiCard
import com.ironlog.app.ui.screens.recovery.RecoveryHeatmapCard
import com.ironlog.app.ui.screens.workout.parseRepTarget

@Composable
fun HomeScreen(
    vm: AppDataViewModel = viewModel(),
    onStartWorkout: (planId: String, dayId: String) -> Unit = { _, _ -> },
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
    val gamState by gamificationVm.uiState.collectAsState()
    val state by vm.state.collectAsState()
    LaunchedEffect(state.history, state.settings.weeklyGoalDays) {
        gamificationVm.refreshFromHistory(state.history, state.settings.weeklyGoalDays)
    }
    var latestBodyweight by remember { mutableStateOf<Double?>(null) }
    // FIXED: 10 — also fetch date and weekly delta for Body Weight card
    var latestBodyweightDate by remember { mutableStateOf<String?>(null) }
    var weeklyWeightDelta by remember { mutableStateOf<Double?>(null) }
    val bodyRepo = remember { BodyMeasurementRepository() }
    LaunchedEffect(Unit) {
        latestBodyweight = runCatching { bodyRepo.getLatestBodyweight() }.getOrNull()
        latestBodyweightDate = runCatching { bodyRepo.getLatestBodyweightDate() }.getOrNull()
        weeklyWeightDelta = runCatching { bodyRepo.getWeeklyBodyweightDelta() }.getOrNull()
    }

    // Active workout live state — polls settings to check if a workout is in progress
    val homeSettingsRepo = remember { SettingsRepository() }
    var activeWorkoutDayName by remember { mutableStateOf<String?>(null) }
    var activeWorkoutStartMs by remember { mutableLongStateOf(0L) }
    var manualRecoveryInput by remember { mutableStateOf<ManualRecoveryInput?>(null) }
    var painFlags by remember { mutableStateOf(setOf<String>()) }
    val painRegions = remember { listOf("Push", "Pull", "Legs", "Core", "Arms", "Shoulders") }
    LaunchedEffect(Unit) {
        while (true) {
            val (dayName, startMs) = withContext(Dispatchers.IO) {
                val dn = homeSettingsRepo.getString("active_workout_day_name")
                val sm = homeSettingsRepo.getString("active_workout_start_ms")?.toLongOrNull() ?: 0L
                dn to sm
            }
            val nextDayName = if (dayName.isNullOrBlank()) null else dayName
            if (activeWorkoutDayName != nextDayName) {
                activeWorkoutDayName = nextDayName
            }
            if (activeWorkoutStartMs != startMs) {
                activeWorkoutStartMs = startMs
            }
            delay(1500)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val (recovery, pain) = withContext(Dispatchers.IO) {
                val r = homeSettingsRepo.getString("manual_recovery_input")
                    ?.let { raw -> runCatching { Json.decodeFromString(ManualRecoveryInput.serializer(), raw) }.getOrNull() }
                val p = painRegions
                    .filter { region -> homeSettingsRepo.getString("pain_flag_${region}") == "true" }
                    .toSet()
                r to p
            }
            manualRecoveryInput = recovery
            painFlags = pain
            delay(1200)
        }
    }

    // Health Connect — biometric enrichment for recovery readiness
    val healthRepo = remember { HealthConnectRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    var showHealthConnectSheet by remember { mutableStateOf(false) }
    var biometricSnapshot: BiometricSnapshot by remember { mutableStateOf(BiometricSnapshot()) }
    LaunchedEffect(Unit) {
        if (healthRepo.isAvailable()) {
            if (!healthRepo.hasAllPermissions()) {
                val prefs = context.getSharedPreferences("hc_prefs", 0)
                if (!prefs.getBoolean("hc_sheet_shown", false)) {
                    showHealthConnectSheet = true
                    prefs.edit().putBoolean("hc_sheet_shown", true).apply()
                }
            } else {
                biometricSnapshot = withContext(Dispatchers.IO) { healthRepo.readBiometricSnapshot() }
            }
        }
    }

    val streak = remember(state.history) { getStreak(state.history) }
    val recentSessions = remember(state.history) {
        val cutoff = LocalDate.now().minusDays(30).toString()
        state.history.count { it.date.substringBefore('T') >= cutoff }
    }
    val avgDurationMin = remember(state.history) {
        if (state.history.isEmpty()) 0
        else (state.history.take(10).sumOf { it.duration }.toDouble() / state.history.take(10).size / 60.0).roundToInt().coerceAtLeast(1)
    }
    val weeklyGoalDays = state.settings.weeklyGoalDays.coerceIn(1, 7)
    val thisWeekSessions = remember(state.history) { countSessionsThisWeek(state.history) }
    val thisWeekSets = remember(state.history) {
        val now = LocalDate.now()
        val fields = WeekFields.ISO
        val y = now.get(fields.weekBasedYear())
        val w = now.get(fields.weekOfWeekBasedYear())
        state.history
            .filter {
                runCatching { LocalDate.parse(it.date.substringBefore('T')) }.getOrNull()?.let { d ->
                    d.get(fields.weekBasedYear()) == y && d.get(fields.weekOfWeekBasedYear()) == w
                } == true
            }
            .sumOf { it.sets }
    }
    val thisWeekVolumeKg = remember(state.history) {
        val now = LocalDate.now()
        val fields = WeekFields.ISO
        val y = now.get(fields.weekBasedYear())
        val w = now.get(fields.weekOfWeekBasedYear())
        state.history
            .filter {
                runCatching { LocalDate.parse(it.date.substringBefore('T')) }.getOrNull()?.let { d ->
                    d.get(fields.weekBasedYear()) == y && d.get(fields.weekOfWeekBasedYear()) == w
                } == true
            }
            .sumOf { it.volume }
            .roundToInt()
    }
    val goalStreak = remember(state.history, weeklyGoalDays) { getGoalStreak(state.history, weeklyGoalDays) }
    // Prefer the plan explicitly marked active; fall back to first (most-recently-updated) plan
    val activePlan = state.plans.firstOrNull { it.isActive } ?: state.plans.firstOrNull()
    val planDays = activePlan?.days ?: emptyList()
    val goalModeLabel = state.settings.goalMode
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { ch -> ch.titlecase() } }

    val filteredHistory30d = remember(state.history) { state.history.filter { homeScreenAgeDays(it.date) <= 30 } }
    val readiness = remember(filteredHistory30d, painFlags) {
        RecoveryReadinessEngine.readinessByRegion(filteredHistory30d, painFlags)
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
    val recoveryBlurb: String? = remember(readiness, orderedPlanDays) {
        orderedPlanDays.firstOrNull()?.let { day ->
            if (planDays.size > 1) suggestionEngine.recommendationBlurb(readiness, day.name) else null
        }
    }

    val intelligenceSnapshot = remember(state.history) {
        TrainingIntelligenceEngine.build(state.history)
    }

    // Step 7: thisWeekSets/thisWeekVolumeKg/totalSessions/unlockedMilestones removed (cards moved to StatsScreen)

    // Health Connect permission sheet — shown once on first open if HC is available but not yet authorized
    if (showHealthConnectSheet && healthRepo.isAvailable()) {
        val permLauncher = rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { grantedPerms ->
            showHealthConnectSheet = false
            if (grantedPerms.containsAll(healthRepo.requiredPermissions)) {
                coroutineScope.launch {
                    biometricSnapshot = withContext(Dispatchers.IO) { healthRepo.readBiometricSnapshot() }
                }
            }
        }
        HealthConnectPermissionSheet(
            onRequestPermissions = { permLauncher.launch(healthRepo.requiredPermissions) },
            onDismiss = { showHealthConnectSheet = false },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        item {
            val xpAnim by animateFloatAsState(
                targetValue = if (gamState.xpForNextLevel > 0L)
                    (gamState.xpInLevel.toFloat() / gamState.xpForNextLevel.toFloat()).coerceIn(0f, 1f)
                else 0f,
                label = "xpBarAnim",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenStatusWindow() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Rank + Level badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IronGradeBadge(
                            rank = gamState.rank,
                            accent = ironGradeColor(gamState.rank),
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "${gamState.rank} · Lv.${gamState.level}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // XP progress bar
                Box(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { xpAnim },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                // XP label
                Text(
                    text = "${gamState.xpInLevel}/${gamState.xpForNextLevel}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )

                // Streak badge
                if (gamState.streakWeeks > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "${gamState.streakWeeks}w streak",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            if (activePlan == null) {
                // Only show the empty state once the plan repository has confirmed it has no plans.
                // Until then, render a placeholder skeleton so we never flash "No program selected"
                // on cold start while plans are still loading from ObjectBox.
                if (state.plansLoaded) {
                    NoPlanCard(onOpenPlans = onOpenProgramPicker)
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(IronLogRadius.xl.dp))
                            .background(c.faint)
                            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.xl.dp)),
                    )
                }
            } else {
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
                    onSwitchToBuiltin = {
                        vm.updateSettingsAsync(state.settings.copy(intelligenceMode = "builtin"))
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
                    onSwitchToBuiltin = {
                        vm.updateSettingsAsync(state.settings.copy(intelligenceMode = "builtin"))
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.MonitorWeight, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
        // Milestones card — only shown when at least one PB has been unlocked
        if (state.pb.keys.isNotEmpty()) {
            item { MilestonesCard(unlocked = state.pb.keys) }
        }
    }
}

@Composable
private fun DailyProofCard(
    gamState: com.ironlog.app.ui.viewmodel.GamificationUiState,
    onPrimaryAction: () -> Unit,
    onOpenLedger: () -> Unit,
) {
    val c = useTheme()
    val xpAnim by animateFloatAsState(
        targetValue = if (gamState.xpForNextLevel > 0L) {
            (gamState.xpInLevel.toFloat() / gamState.xpForNextLevel.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        },
        label = "dailyProofXp",
    )
    val accent = when (gamState.dailyProofStatus) {
        DailyProofStatus.PROOF_LOGGED -> c.success
        DailyProofStatus.RECOVER_SMART -> c.info
        DailyProofStatus.AT_RISK -> c.warning
        DailyProofStatus.SETUP -> c.accent
        else -> c.accent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.xl.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.xl.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "DAILY PROOF",
                    color = c.muted,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
                Text(
                    gamState.dailyProofHeadline,
                    color = c.text,
                    fontSize = IronLogType.title.fontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = IronLogType.title.lineHeight.sp,
                )
                Text(
                    gamState.dailyProofDetail,
                    color = c.subtext,
                    fontSize = IronLogType.body.fontSize.sp,
                    lineHeight = IronLogType.body.lineHeight.sp,
                )
            }

            Image(
                painter = painterResource(ForgeFoxExpression.fromId(gamState.foxExpressionId).drawableRes),
                contentDescription = gamState.dailyProofHeadline,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(112.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IronGradeBadge(
                        rank = gamState.rank,
                        accent = ironGradeColor(gamState.rank),
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "${gamState.rank} · Lv.${gamState.level}",
                        color = c.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (gamState.dailyStreakDays > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.surface,
                ) {
                    Text(
                        text = "${gamState.dailyStreakDays}d streak",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = c.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (gamState.latestBadgeTitle != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.surface,
                ) {
                    Text(
                        text = gamState.latestBadgeTitle,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = c.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(
                progress = { xpAnim },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accent,
                trackColor = c.faint,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${gamState.xpInLevel}/${gamState.xpForNextLevel} XP",
                    color = c.subtext,
                    fontSize = 11.sp,
                )
                Text(
                    text = "${gamState.streakWeeks} qualifying weeks",
                    color = c.subtext,
                    fontSize = 11.sp,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.weight(1f),
            ) {
                Text(gamState.dailyProofPrimaryActionLabel.uppercase())
            }
            TextButton(
                onClick = onOpenLedger,
                modifier = Modifier.weight(1f),
            ) {
                Text("OPEN IRON LEDGER")
            }
        }
    }
}

@Composable
private fun ThisWeekDayChips(
    planDays: List<com.ironlog.app.ui.model.UiPlanDay>,
    history: List<HistoryEntry>,
) {
    val c = useTheme()
    val fields = remember { WeekFields.ISO }
    val now = LocalDate.now()
    val nowYear = now.get(fields.weekBasedYear())
    val nowWeek = now.get(fields.weekOfWeekBasedYear())
    val thisWeekHistory = remember(history) {
        history.filter {
            runCatching { LocalDate.parse(it.date.substringBefore('T')) }.getOrNull()?.let { d ->
                d.get(fields.weekBasedYear()) == nowYear && d.get(fields.weekOfWeekBasedYear()) == nowWeek
            } == true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "THIS WEEK",
            color = c.muted,
            fontSize = IronLogType.eyebrow.fontSize.sp,
            fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
            letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lazyItems(planDays) { day ->
                val hit = thisWeekHistory.any {
                    (it.planDayUid != null && it.planDayUid == day.id) ||
                        ((it.dayName ?: it.name).trim().equals(day.name.trim(), ignoreCase = true))
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (hit) c.accent.copy(alpha = 0.18f) else c.surface)
                        .border(1.dp, if (hit) c.accent else c.cardBorder, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
    val iso = lastWorkoutDate?.substringBefore('T') ?: return timeGreeting()
    val days = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(iso), LocalDate.now()).toInt()
    }.getOrDefault(-1)
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
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Goal streak: ${goalStreak} week${if (goalStreak == 1) "" else "s"}",
                color = c.muted,
                fontSize = IronLogType.meta.fontSize.sp,
            )
        }
    }
}

@Composable
private fun NoPlanCard(onOpenPlans: () -> Unit) {
    val c = useTheme()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.xl.dp))
            .background(c.faint)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.xl.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = c.muted, modifier = Modifier.size(32.dp))
            Text(
                "No program selected",
                color = c.subtext,
                fontWeight = FontWeight(IronLogType.section.fontWeight),
                fontSize = IronLogType.section.fontSize.sp,
            )
            Text(
                "Pick a plan and start building.",
                color = c.muted,
                fontSize = IronLogType.body.fontSize.sp,
            )
            Button(onClick = onOpenPlans) {
                Text("BROWSE PROGRAMS")
            }
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
private fun StartWorkoutCard(
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
    activeWorkoutDayName: String? = null,
    activeWorkoutStartMs: Long = 0L,
    onResumeWorkout: () -> Unit = {},
    onDiscardWorkout: () -> Unit = {},
) {
    val c = useTheme()
    // A workout is active as soon as its day name is persisted — elapsed may still be 0 if no
    // sets have been logged yet (timer only starts on first set).
    val isWorkoutActive = !activeWorkoutDayName.isNullOrBlank()
    val animatedBrush = rememberAnimatedCardBrush(c.accent)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.xl.dp))
            .background(c.card)
            .background(animatedBrush)
            .border(2.dp, c.accent.copy(alpha = 0.50f), RoundedCornerShape(IronLogRadius.xl.dp)),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Eyebrow
            Text(
                if (isWorkoutActive) "IN PROGRESS" else "TODAY'S WORKOUT",
                color = c.accent,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
            // Top row: play icon (left) + arrow button (right)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.textOnAccent),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { if (isWorkoutActive) onResumeWorkout() else onClick() },
                    )
                    Icon(
                        if (isWorkoutActive) Icons.Outlined.Loop else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = c.bg,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = 0.12f))
                        .border(1.dp, c.accent.copy(alpha = 0.20f), CircleShape)
                        .clickable { if (isWorkoutActive) onResumeWorkout() else onClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // Plan / workout name + badges row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (isWorkoutActive) (activeWorkoutDayName ?: "Workout") else activePlanName.ifBlank { "Start Workout" },
                    color = c.text,
                    fontWeight = FontWeight(IronLogType.display.fontWeight),
                    fontSize = IronLogType.display.fontSize.sp,
                    lineHeight = IronLogType.display.lineHeight.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isWorkoutActive) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                .background(c.success.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
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
                                .padding(horizontal = 8.dp, vertical = 3.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val sidePx = 20.dp.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(maxWidth = constraints.maxWidth + sidePx * 2)
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.place(-sidePx, 0)
                            }
                        },
                ) {
                    lazyItems(planDays) { day ->
                        val isRecommended = day.id == recommendedDayId
                        // Pulsing glow on the recommended card
                        val infiniteTransition = rememberInfiniteTransition(label = "glow_${day.id}")
                        val glowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.45f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 900),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "glowAlpha_${day.id}",
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isRecommended) c.accent.copy(alpha = glowAlpha)
                                          else c.text.copy(alpha = 0.26f),
                            animationSpec = tween(400),
                            label = "borderColor_${day.id}",
                        )
                        val borderWidth = if (isRecommended) 2.dp else 1.dp
                        val cardBg = if (isRecommended) c.accent.copy(alpha = 0.10f)
                                     else c.text.copy(alpha = 0.08f)

                        Box(
                            Modifier
                                .width(128.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(cardBg)
                                .border(borderWidth, borderColor, RoundedCornerShape(22.dp))
                                .clickable { onStartDay(day.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    day.name.uppercase(),
                                    color = if (isRecommended) c.accent else c.text,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                )
                                Text(
                                    "${day.exercises.size} ex",
                                    color = c.muted,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.1.sp,
                                )
                                if (isRecommended) {
                                    Text(
                                        "⚡ Best Today",
                                        color = c.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp,
                                    )
                                }
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
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }

                // Quick-start without plan
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.sm.dp))
                        .border(1.dp, c.accent.copy(alpha = 0.13f), RoundedCornerShape(IronLogRadius.sm.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
            .padding(vertical = 18.dp),
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
            Spacer(Modifier.height(2.dp))
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(sup, color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            content()
        }
    }
}

@Composable
private fun TrainingIntelligenceCard(
    activePlanName: String?,
    sessionsPerWeek: Int,
    adherencePct: Int,
    recommendedDay: UiPlanDay?,
    history: List<HistoryEntry>,
    weightUnit: String,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
        val suggestions = remember(recommendedDay, history) {
            buildAdaptiveTargets(recommendedDay, history)
        }
        if (suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestions.forEach { s ->
                    val base =
                        if (s.suggestedWeightKg != null) {
                            "${s.name} → ${formatWeightFromKg(s.suggestedWeightKg, weightUnit)} × ${s.suggestedReps}"
                        } else {
                            "${s.name} → Build to a working weight"
                        }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            base,
                            color = c.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                        )
                        if (s.plateau) {
                            Text(
                                "⟳ plateau",
                                color = c.warning,
                                fontSize = IronLogType.meta.fontSize.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
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

private data class AdaptiveTarget(
    val name: String,
    val suggestedWeightKg: Double?,
    val suggestedReps: Int,
    val plateau: Boolean,
)

private fun buildAdaptiveTargets(day: UiPlanDay?, history: List<HistoryEntry>): List<AdaptiveTarget> {
    if (day == null) return emptyList()
    return day.exercises.take(3).map { ex ->
        val allSets = history
            .flatMap { it.exercises }
            .filter { hx ->
                hx.exerciseId == ex.exerciseId || hx.name.equals(ex.name, ignoreCase = true)
            }
            .flatMap { it.sets.filter { st -> st.type != "warmup" && st.weight > 0 && st.reps > 0 } }
        if (allSets.isEmpty()) {
            AdaptiveTarget(ex.name, null, parseRepTarget(ex.reps, 8), plateau = false)
        } else {
            val latest = allSets.last()
            val e1rm = latest.weight * (1.0 + latest.reps / 30.0)
            val suggested = ((e1rm * 0.85) / 2.5).roundToInt() * 2.5
            val recentE1rm = allSets.takeLast(3).map { it.weight * (1.0 + it.reps / 30.0) }
            val plateau = recentE1rm.size >= 3 && recentE1rm.zipWithNext().all { (a, b) -> b <= a + 0.25 }
            AdaptiveTarget(
                name = ex.name,
                suggestedWeightKg = suggested.coerceAtLeast(2.5),
                suggestedReps = parseRepTarget(ex.reps, 8),
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
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

fun getStreak(history: List<HistoryEntry>): Int {
    if (history.isEmpty()) return 0
    val days = history.map { it.date.substringBefore('T') }.toSet().sortedDescending()
    val today = LocalDate.now().toString()
    val yesterday = LocalDate.now().minusDays(1).toString()
    if (days.first() != today && days.first() != yesterday) return 0
    var streak = 0
    var prev = LocalDate.parse(days.first())
    for (d in days) {
        val cur = LocalDate.parse(d)
        val diff = java.time.Duration.between(cur.atStartOfDay(), prev.atStartOfDay()).toDays()
        if (diff <= 1) { streak++; prev = cur } else break
    }
    return streak
}

fun getWeekKey(dateString: String): String {
    val date = LocalDate.parse(dateString.substringBefore('T'))
    val fields = WeekFields.ISO
    return "${date.get(fields.weekBasedYear())}-W${date.get(fields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
}

fun getGoalStreak(history: List<HistoryEntry>, weeklyGoalDays: Int): Int {
    val target = max(1, min(7, weeklyGoalDays))
    if (history.isEmpty()) return 0
    val byWeek = linkedMapOf<String, MutableSet<String>>()
    history.forEach { session ->
        val weekKey = getWeekKey(session.date)
        val dayKey = session.date.substringBefore('T')
        byWeek.getOrPut(weekKey) { linkedSetOf() }.add(dayKey)
    }
    var streak = 0
    byWeek.keys.sortedDescending().forEach { week ->
        val count = byWeek[week]?.size ?: 0
        if (count >= target) streak++ else if (streak > 0) return streak
    }
    return streak
}

fun countSessionsThisWeek(history: List<HistoryEntry>): Int {
    val now = LocalDate.now()
    val fields = WeekFields.ISO
    val y = now.get(fields.weekBasedYear())
    val w = now.get(fields.weekOfWeekBasedYear())
    return history.mapNotNull {
        runCatching { LocalDate.parse(it.date.substringBefore('T')) }.getOrNull()
    }.count { d -> d.get(fields.weekBasedYear()) == y && d.get(fields.weekOfWeekBasedYear()) == w }
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("WEEKLY SUMMARY", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            TextButton(onClick = onShare) { Text("SHARE", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
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
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.section.fontSize.sp)
        Text(label, color = c.muted, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = 0.5.sp)
    }
}

@Composable
private fun AthleteProfileCard(totalSessions: Int, streak: Int, onOpen: () -> Unit) {
    val c = useTheme()
    // Tier thresholds: (label, emoji, minSessions, nextTierMin)
    val (tier, tierEmoji, tierMin, tierNext) = when {
        totalSessions >= 200 -> TierInfo("Elite", "⚡", 200, Int.MAX_VALUE)
        totalSessions >= 76  -> TierInfo("Advanced", "🔥", 76, 200)
        totalSessions >= 21  -> TierInfo("Intermediate", "💪", 21, 76)
        else                 -> TierInfo("Beginner", "🌱", 0, 21)
    }
    val xpProgress = if (tierNext == Int.MAX_VALUE) 1f
    else ((totalSessions - tierMin).toFloat() / (tierNext - tierMin)).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ATHLETE PROFILE", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
                Text("$tierEmoji $tier", color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.section.fontSize.sp)
                Text("$totalSessions sessions · $streak-day streak", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
            }
            Text("→", color = c.accent, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight.Bold)
        }
        // XP progress bar toward next tier
        LinearProgressIndicator(
            progress = { xpProgress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(IronLogRadius.full.dp)),
            color = c.accent,
            trackColor = c.faint,
        )
        if (tierNext != Int.MAX_VALUE) {
            Text(
                "${totalSessions - tierMin} / ${tierNext - tierMin} sessions to ${ when(tierNext) { 76 -> "Intermediate"; 200 -> "Advanced"; else -> "Elite" } }",
                color = c.muted,
                fontSize = IronLogType.meta.fontSize.sp,
            )
        }
    }
}

private data class TierInfo(val tier: String, val tierEmoji: String, val tierMin: Int, val tierNext: Int)

private val MILESTONE_LABELS = mapOf(
    "MILESTONE_PR" to ("🏆" to "First PR"),
    "MILESTONE_LIFT_1T" to ("💪" to "1 Ton Lifted"),
    "MILESTONE_LIFT_5T" to ("🔥" to "5 Tons Lifted"),
    "MILESTONE_LIFT_10T" to ("⚡" to "10 Tons Lifted"),
    "MILESTONE_STREAK_7" to ("🔥" to "7-Day Streak"),
    "MILESTONE_STREAK_30" to ("📅" to "30-Day Streak"),
    "MILESTONE_STREAK_100" to ("🌟" to "100-Day Streak"),
)

@Composable
private fun MilestonesCard(unlocked: Set<String>) {
    val c = useTheme()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("MILESTONES", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MILESTONE_LABELS.entries.filter { it.key in unlocked }.forEach { (_, pair) ->
                val (emoji, label) = pair
                Column(
                    Modifier
                        .clip(RoundedCornerShape(IronLogRadius.md.dp))
                        .background(c.accentSoft)
                        .border(1.dp, c.accent.copy(alpha = 0.3f), RoundedCornerShape(IronLogRadius.md.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(emoji, fontSize = IronLogType.title.fontSize.sp)
                    Text(label, color = c.text, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


