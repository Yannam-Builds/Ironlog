package com.ironlog.app.ui.screens.stats

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import android.app.Application
import com.ironlog.app.IronLogApplication
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.domain.intelligence.CloudAiEngine
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import com.ironlog.app.ui.components.PageHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.GamificationUiState
import com.ironlog.app.ui.viewmodel.GamificationViewModel
import com.ironlog.app.ui.viewmodel.GamificationViewModelFactory
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.ui.viewmodel.StatsViewModel
import com.ironlog.app.ui.screens.settings.commitHistoryMutationAcrossSurfaces
import com.ironlog.app.util.formatWeightFromKg
import com.valentinilk.shimmer.shimmer
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import com.ironlog.app.domain.gamification.parseHistoryInstant
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ironlog.app.ui.screens.home.getStreak
import timber.log.Timber

@Composable
fun StatsScreen(
    vm: StatsViewModel = viewModel(),
    onOpenExerciseProgress: (String) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenBodyTracker: () -> Unit = {},
    onOpenVolumeAnalytics: () -> Unit = {},
    onOpenRecoveryMap: () -> Unit = {},
    onOpenTrainingIntelligence: () -> Unit = {},
    onOpenProgressPhotos: () -> Unit = {},
    onOpenStatusWindow: () -> Unit = {},
) {
    val c = useTheme()
    val state by vm.state.collectAsStateWithLifecycle()
    val pbEntries = state.personalBests
    var showClearPbsConfirm by remember { mutableStateOf(false) }
    var clearPbsPending by remember { mutableStateOf(false) }
    var clearPbsError by remember { mutableStateOf<String?>(null) }
    var pbShowLimit by remember { mutableIntStateOf(25) }

    val appVm: AppDataViewModel = viewModel()
    val appState by appVm.state.collectAsStateWithLifecycle()
    val cloudSettings = appState.settings
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as IronLogApplication }
    val acceptedMutationCount by application.acceptedMutationCount.collectAsStateWithLifecycle()
    val historyMutationPending = acceptedMutationCount > 0
    val gamificationVm: GamificationViewModel = viewModel(
        factory = GamificationViewModelFactory(
            context.applicationContext as Application,
            ObjectBox.store,
        )
    )
    val gamState by gamificationVm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(appState.history, appState.settings.weeklyGoalDays) {
        gamificationVm.refreshFromHistory(appState.history, appState.settings.weeklyGoalDays)
    }
    val credentialRevision by CloudAiKeyStore.revision.collectAsStateWithLifecycle()
    val cloudApiKey = remember(credentialRevision, cloudSettings.cloudAiProviderPreset, cloudSettings.intelligenceMode, cloudSettings.cloudAiBaseUrl, cloudSettings.cloudAiModelName) {
        CloudAiKeyStore.load(context, cloudSettings.cloudAiProviderPreset)
    }
    val cloudConfigured = cloudApiKey.isNotBlank()
        && cloudSettings.cloudAiBaseUrl.isNotBlank()
        && cloudSettings.cloudAiModelName.isNotBlank()
    val cloudEnabled = cloudSettings.intelligenceMode == "cloud_ai" && cloudConfigured
    val statsSummary = CloudStatsSummaryHost(
        enabled = cloudEnabled,
        requestKey = listOf(cloudSettings.cloudAiProviderPreset, cloudSettings.cloudAiBaseUrl,
            cloudSettings.cloudAiModelName, cloudSettings.cloudAiApiFormat, cloudApiKey,
            state.history, state.streak, state.totalSets, state.avgDurationMin, cloudSettings.weightUnit),
        load = {
        val topExercise = state.history
            .flatMap { it.exercises }
            .groupingBy { it.name }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "your main lift"
        CloudAiEngine.askStatsSummary(
            baseUrl        = cloudSettings.cloudAiBaseUrl,
            apiKey         = cloudApiKey,
            modelName      = cloudSettings.cloudAiModelName,
            apiFormat      = cloudSettings.cloudAiApiFormat,
            totalSessions  = state.history.size,
            streak         = state.streak,
            totalSets      = state.totalSets,
            avgDurationMin = state.avgDurationMin,
            topExercise    = topExercise,
            weightUnit     = cloudSettings.weightUnit,
        )
        },
    )
    var chartRange by remember { mutableStateOf("14D") }
    val rangedChartPoints = remember(state.history, chartRange) {
        val datestamps = state.history.map { it.date.substringBefore('T') }
        val counts = datestamps.groupingBy { it }.eachCount()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM")
        when (chartRange) {
            "30D" -> {
                val labelIndices = setOf(0, 9, 19, 29)
                (0 until 30).map { i ->
                    val day = java.time.LocalDate.now().minusDays((29 - i).toLong())
                    com.ironlog.app.ui.model.ChartPoint(value = counts[day.toString()] ?: 0, label = if (i in labelIndices) day.format(formatter) else "")
                }
            }
            "90D" -> {
                val labelIndices = setOf(0, 29, 59, 89)
                (0 until 90).map { i ->
                    val day = java.time.LocalDate.now().minusDays((89 - i).toLong())
                    com.ironlog.app.ui.model.ChartPoint(value = counts[day.toString()] ?: 0, label = if (i in labelIndices) day.format(formatter) else "")
                }
            }
            "All" -> {
                if (datestamps.isEmpty()) emptyList()
                else {
                    val earliest = datestamps.minOrNull()?.let { java.time.LocalDate.parse(it) } ?: java.time.LocalDate.now()
                    val totalDays = java.time.temporal.ChronoUnit.DAYS.between(earliest, java.time.LocalDate.now()).toInt() + 1
                    val step = maxOf(1, totalDays / 30)
                    (0 until totalDays step step).map { i ->
                        val day = earliest.plusDays(i.toLong())
                        val windowCount = (0 until step).sumOf { offset -> counts[earliest.plusDays((i + offset).toLong()).toString()] ?: 0 }
                        com.ironlog.app.ui.model.ChartPoint(value = windowCount, label = if (i == 0 || i >= totalDays - step) day.format(formatter) else "")
                    }
                }
            }
            else -> state.chartData // "14D" — use pre-built
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
    ) {
        // FIXED: 6 + 22 — PageHeader for tab screen
        item {
            PageHeader(
                eyebrow = "ANALYTICS",
                title = "Stats",
                subtitle = "${state.history.size} sessions · ${gamState.dailyStreakDays}-day streak",
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = appSpacedBy(10.dp),
            ) {
                QuickNavButton("CALENDAR", Icons.Outlined.CalendarMonth, Modifier.weight(1f), onOpenCalendar)
                QuickNavButton("VOLUME", Icons.Outlined.Analytics, Modifier.weight(1f), onOpenVolumeAnalytics)
                QuickNavButton("BODY", Icons.Outlined.MonitorWeight, Modifier.weight(1f), onOpenBodyTracker)
            }
        }
        item {
            Row(
                horizontalArrangement = appSpacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().appPadding(horizontal = 12.dp),
            ) {
                StatCard("Sessions", state.history.size.toString(), Modifier.weight(1f))
                StatCard("Streak", gamState.dailyStreakDays.toString(), Modifier.weight(1f))
                StatCard("Sets", state.totalSets.toString(), Modifier.weight(1f))
                StatCard("Avg Min", state.avgDurationMin.toString(), Modifier.weight(1f))
            }
        }
        item {
            StatusWindowStatsCard(
                state = gamState,
                totalSessions = state.history.size,
                onOpen = onOpenStatusWindow,
            )
        }
        // ── Cloud AI stats summary ────────────────────────────────────────
        if (cloudEnabled) {
            item {
                AiStatsSummaryCard(
                    isLoading    = statsSummary.loading,
                    text         = statsSummary.text,
                    displayName  = cloudSettings.cloudAiDisplayName,
                    onRegenerate = statsSummary.regenerate,
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth().appPadding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("PERFORMANCE STATS", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight))
                Row(horizontalArrangement = appSpacedBy(8.dp), modifier = Modifier.fillMaxWidth().appPadding(top = 10.dp)) {
                    MiniAction("RECOVERY", Modifier.weight(1f), onOpenRecoveryMap)
                    CoachMiniAction(Modifier.weight(1f), onOpenTrainingIntelligence)
                    MiniAction("PROGRESS", Modifier.weight(1f), onOpenProgressPhotos)
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(IronLogRadius.xl.dp),
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                modifier = Modifier.appPadding(horizontal = 16.dp),
            ) {
                Column(Modifier.appPadding(16.dp), verticalArrangement = appSpacedBy(12.dp)) {
                    Text(
                        "14-DAY FREQUENCY",
                        color = c.text,
                        fontWeight = FontWeight(IronLogType.section.fontWeight),
                        fontSize = IronLogType.section.fontSize.sp,
                    )
                    FrequencyBars(
                        history = state.history,
                        barColor = c.accent,
                        trackColor = c.cardBorder,
                        labelColor = c.muted,
                    )
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(IronLogRadius.xl.dp),
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                modifier = Modifier.appPadding(horizontal = 16.dp),
            ) {
                Column(Modifier.appPadding(16.dp), verticalArrangement = appSpacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (chartRange) { "14D" -> "LAST 14 DAYS"; "30D" -> "LAST 30 DAYS"; "90D" -> "LAST 90 DAYS"; else -> "ALL TIME" },
                            color = c.text,
                            fontWeight = FontWeight(IronLogType.section.fontWeight),
                            fontSize = IronLogType.section.fontSize.sp,
                        )
                        Text("sessions / day", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                    }
                    // Time-range filter chips (GAP-02)
                    Row(horizontalArrangement = appSpacedBy(6.dp)) {
                        listOf("14D", "30D", "90D", "All").forEach { range ->
                            val active = chartRange == range
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(if (active) c.accent.copy(alpha = 0.18f) else c.surface)
                                    .border(1.dp, if (active) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                    .clickable { chartRange = range }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    range,
                                    color = if (active) c.accent else c.subtext,
                                    fontSize = IronLogType.micro.fontSize.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    if (rangedChartPoints.isEmpty()) {
                        Text(
                            "No sessions logged yet. Start training to see your chart.",
                            color = c.muted,
                            fontSize = IronLogType.body.fontSize.sp,
                        )
                    } else {
                        // Chart with Y-axis labels on left and X-axis date labels below
                        val maxVal = rangedChartPoints.maxOf { it.value.toFloat() }.coerceAtLeast(1f)
                        // Round up to a clean axis tick (1, 2, 4, 5, 10, 20, ...)
                        val yMax = niceAxisMax(maxVal)
                        Row(
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Y-axis labels (left column)
                            Column(
                                modifier = Modifier.width(28.dp).fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End,
                            ) {
                                listOf(yMax, yMax * 0.75f, yMax * 0.5f, yMax * 0.25f, 0f).forEach { v ->
                                    Text(
                                        formatAxisValue(v),
                                        color = c.muted,
                                        fontSize = IronLogType.micro.fontSize.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.width(appGapDp(6.dp)))
                            // The chart itself
                            SessionLineChart(
                                points = rangedChartPoints,
                                lineColor = c.chartPrimary,
                                dotColor = c.accent,
                                gridColor = c.faint,
                                yMax = yMax,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        // X-axis date labels (first, middle, last)
                        Row(
                            Modifier.fillMaxWidth().appPadding(start = 34.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val firstLabel = rangedChartPoints.firstOrNull()?.label.orEmpty()
                            val midIdx     = rangedChartPoints.size / 2
                            val midLabel   = rangedChartPoints.getOrNull(midIdx)?.label.orEmpty()
                            val lastLabel  = rangedChartPoints.lastOrNull()?.label.orEmpty()
                            listOf(firstLabel, midLabel, lastLabel).forEach { lbl ->
                                if (lbl.isNotBlank()) {
                                    Text(lbl, color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                                } else {
                                    Spacer(Modifier.width(appGapDp(1.dp)))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().appPadding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Personal Bests",
                    color = c.text,
                    fontWeight = FontWeight(IronLogType.title.fontWeight),
                    fontSize = IronLogType.title.fontSize.sp,
                )
                TextButton(
                    enabled = !historyMutationPending,
                    onClick = { showClearPbsConfirm = true },
                ) { Text("Clear") }
            }
        }
        if (pbEntries.isEmpty()) {
            item {
                Spacer(Modifier.height(appGapDp(8.dp)))
                Text(
                    "No PRs yet. Log your first workout and claim them.",
                    color = c.muted,
                    fontSize = IronLogType.body.fontSize.sp,
                    modifier = Modifier.appPadding(horizontal = 16.dp),
                )
            }
        }
        items(pbEntries.take(pbShowLimit), key = { it.exerciseName }) { pb ->
            Card(
                shape = RoundedCornerShape(IronLogRadius.lg.dp),
                colors = CardDefaults.cardColors(containerColor = c.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onOpenExerciseProgress(pb.exerciseName) },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    // FIXED: 26 — enriched PB: date + trend delta
                    androidx.compose.foundation.layout.Column(
                        Modifier.weight(1f),
                        verticalArrangement = appSpacedBy(2.dp),
                    ) {
                        Text(
                            pb.exerciseName,
                            color = c.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = IronLogType.body.fontSize.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        val dateLabel = pb.date?.let { "Set $it" }
                        val prevLabel = pb.previousOrm?.let { "from ~${formatWeightFromKg(it, state.weightUnit)}" }
                        val metaLine = listOfNotNull(dateLabel, prevLabel).joinToString(" · ")
                        if (metaLine.isNotBlank()) {
                            Text(metaLine, color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        }
                        Text(
                            "Est. 1RM: ~${formatWeightFromKg(pb.estOneRm, state.weightUnit)}",
                            color = c.muted,
                            fontSize = IronLogType.meta.fontSize.sp,
                        )
                    }
                    // Right: PR weight + trend arrow
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End, verticalArrangement = appSpacedBy(2.dp)) {
                        Text(
                            "PR ${formatWeightFromKg(pb.bestWeight, state.weightUnit)}",
                            color = c.accent,
                            fontWeight = FontWeight(IronLogType.section.fontWeight),
                            fontSize = IronLogType.body.fontSize.sp,
                        )
                        pb.previousOrm?.let { prevOrm ->
                            val delta = pb.estOneRm - prevOrm
                            val sign = if (delta >= 0) "↑ +" else "↓ "
                            Text(
                                "$sign${formatWeightFromKg(kotlin.math.abs(delta), state.weightUnit)}",
                                color = if (delta >= 0) c.success else c.danger,
                                fontSize = IronLogType.micro.fontSize.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        // Show more / show less for PBs
        if (pbEntries.size > pbShowLimit) {
            item {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    TextButton(onClick = { pbShowLimit += 25 }) {
                        Text("SHOW MORE (${pbEntries.size - pbShowLimit} remaining)", color = c.accent, fontSize = IronLogType.meta.fontSize.sp)
                    }
                }
            }
        } else if (pbShowLimit > 25 && pbEntries.isNotEmpty()) {
            item {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    TextButton(onClick = { pbShowLimit = 25 }) {
                        Text("SHOW LESS", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    }
                }
            }
        }
        item {
            val topExercises = remember(state.history, state.weightUnit) {
                state.history
                    .flatMap { entry -> entry.exercises }
                    .groupBy { it.name }
                    .mapValues { (_, exercises) ->
                        exercises.sumOf { ex -> ex.sets.filter { it.type != "warmup" }.sumOf { it.weight * it.reps } }
                    }
                    .entries
                    .sortedByDescending { it.value }
                    .take(5)
            }
            if (topExercises.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(IronLogRadius.lg.dp),
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                    modifier = Modifier.fillMaxWidth().appPadding(horizontal = 16.dp),
                ) {
                    Column(Modifier.appPadding(16.dp), verticalArrangement = appSpacedBy(10.dp)) {
                        Text(
                            "Top Exercises by Volume",
                            color = c.text,
                            fontWeight = FontWeight(IronLogType.title.fontWeight),
                            fontSize = IronLogType.title.fontSize.sp,
                        )
                        topExercises.forEachIndexed { index, (name, totalKg) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenExerciseProgress(name) },
                                horizontalArrangement = appSpacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = c.muted,
                                    fontSize = IronLogType.meta.fontSize.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(20.dp),
                                )
                                Text(
                                    name,
                                    color = c.text,
                                    fontSize = IronLogType.body.fontSize.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    formatWeightFromKg(totalKg, state.weightUnit),
                                    color = c.accent,
                                    fontSize = IronLogType.meta.fontSize.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearPbsConfirm) {
        AlertDialog(
            onDismissRequest = { if (!clearPbsPending) showClearPbsConfirm = false },
            containerColor = c.card,
            title = { Text("Clear Personal Bests?", color = c.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This starts a new PR baseline now. Workout history stays intact, and future completed sets can establish new records.", color = c.muted)
                    clearPbsError?.let { Text(it, color = c.danger) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !clearPbsPending && !historyMutationPending,
                    onClick = {
                        clearPbsPending = true
                        clearPbsError = null
                        application.launchAcceptedMutation {
                            try {
                                val outcome = commitHistoryMutationAcrossSurfaces(
                                    context = context,
                                    viewModel = appVm,
                                ) { appVm.clearPbsNow() }
                                withContext(Dispatchers.Main) {
                                    if (outcome.mutationSucceeded) {
                                        showClearPbsConfirm = false
                                    } else {
                                        Timber.w(outcome.mutationError, "Could not reset PR baseline from Stats")
                                        clearPbsError = "The PR baseline could not be reset. Try again."
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Timber.w(error, "Unexpected failure while resetting PR baseline from Stats")
                                withContext(Dispatchers.Main) {
                                    clearPbsError = "The PR baseline could not be reset. Try again."
                                }
                            } finally {
                                withContext(Dispatchers.Main) { clearPbsPending = false }
                            }
                        }
                    },
                ) { Text("CLEAR ALL", color = c.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !clearPbsPending, onClick = { showClearPbsConfirm = false }) { Text("CANCEL", color = c.muted) }
            },
        )
    }
}

@Composable
private fun AiStatsSummaryCard(
    isLoading: Boolean,
    text: String?,
    displayName: String,
    onRegenerate: () -> Unit,
) {
    val c = useTheme()
    val cardBg = c.accent.copy(alpha = 0.07f)
    val accentBorder = c.accent.copy(alpha = 0.25f)

    Column(
        Modifier
            .fillMaxWidth()
            .appPadding(horizontal = 16.dp)
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(cardBg)
            .border(1.dp, accentBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .appPadding(14.dp),
        verticalArrangement = appSpacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = appSpacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = c.accent, modifier = Modifier.size(13.dp))
            Text(
                displayName.uppercase().ifBlank { "CLOUD AI" },
                color = c.accent,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
        }
        if (isLoading) {
            Column(Modifier.shimmer(), verticalArrangement = appSpacedBy(5.dp)) {
                repeat(2) { idx ->
                    Box(
                        Modifier
                            .fillMaxWidth(if (idx == 1) 0.65f else 1f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.faint)
                    )
                }
            }
        } else {
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "stats_summary",
            ) { t ->
                Text(t ?: "", color = c.text, fontSize = IronLogType.body.fontSize.sp, lineHeight = IronLogType.body.lineHeight.sp)
            }
            Row(
                Modifier.clickable(onClick = onRegenerate).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = appSpacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.Refresh, null, tint = c.muted, modifier = Modifier.size(11.dp))
                Text("Regenerate", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
            }
        }
    }
}

@Composable
private fun StatusWindowStatsCard(
    state: GamificationUiState,
    totalSessions: Int,
    onOpen: () -> Unit,
) {
    val c = useTheme()
    val xpFraction = if (state.xpForNextLevel > 0L) {
        (state.xpInLevel.toFloat() / state.xpForNextLevel.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(IronLogRadius.xl.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.accent.copy(alpha = 0.36f)),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = appSpacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "STATUS WINDOW",
                        color = c.muted,
                        fontSize = IronLogType.eyebrow.fontSize.sp,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        "${state.rank} grade - Level ${state.level}",
                        color = c.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = IronLogType.section.fontSize.sp,
                    )
                    Text(
                        "${state.activeTitle} • $totalSessions logged sessions",
                        color = c.subtext,
                        fontSize = IronLogType.meta.fontSize.sp,
                    )
                }
                Text(
                    "OPEN",
                    color = c.accent,
                    fontWeight = FontWeight(IronLogType.button.fontWeight),
                    fontSize = IronLogType.meta.fontSize.sp,
                )
            }

            LinearProgressIndicator(
                progress = { xpFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = c.accent,
                trackColor = c.accent.copy(alpha = 0.14f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${state.xpInLevel} / ${state.xpForNextLevel} XP", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                Text("${state.streakWeeks}w streak", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FrequencyBars(
    history: List<HistoryEntry>,
    barColor: androidx.compose.ui.graphics.Color,
    trackColor: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color,
) {
    val today = LocalDate.now()
    val counts = remember(history) {
        val byDay = mutableMapOf<LocalDate, Int>()
        history.forEach { entry ->
            val day = parseHistoryInstant(entry.date)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            if (day != null) byDay[day] = (byDay[day] ?: 0) + 1
        }
        (13 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            d to (byDay[d] ?: 0)
        }
    }
    val maxCount = counts.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Row(Modifier.fillMaxWidth(), horizontalArrangement = appSpacedBy(4.dp)) {
        counts.forEach { (date, count) ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = appSpacedBy(6.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(trackColor, RoundedCornerShape(6.dp)),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((56f * (count.toFloat() / maxCount)).dp)
                            .background(barColor, RoundedCornerShape(6.dp)),
                    )
                }
                // FIXED: 25 — 2-char day labels: Mo, Tu, We, Th, Fr, Sa, Su
                Text(
                    date.dayOfWeek.name.take(2).let { "${it[0].uppercaseChar()}${it[1].lowercaseChar()}" },
                    color = labelColor,
                    fontSize = IronLogType.micro.fontSize.sp,
                )
            }
        }
    }
}

@Composable
private fun QuickNavButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = useTheme()
    Column(
        modifier
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card, RoundedCornerShape(IronLogRadius.lg.dp))
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = appSpacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.accent)
        Text(label, color = c.text, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight(IronLogType.button.fontWeight))
    }
}

@Composable
private fun MiniAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = useTheme()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(IronLogRadius.full.dp))
            .background(c.card, RoundedCornerShape(IronLogRadius.full.dp))
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = c.text,
            fontSize = IronLogType.meta.fontSize.sp,
            fontWeight = FontWeight(IronLogType.button.fontWeight),
            textAlign = TextAlign.Center,
        )
    }
}

// FIXED: 24 — Remove shimmer animation; static accent-tinted pill with ✦ star
@Composable
private fun CoachMiniAction(modifier: Modifier, onClick: () -> Unit) {
    val c = useTheme()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(IronLogRadius.full.dp))
            .background(c.accent.copy(alpha = 0.12f), RoundedCornerShape(IronLogRadius.full.dp))
            .border(1.dp, c.accent.copy(alpha = 0.40f), RoundedCornerShape(IronLogRadius.full.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = appSpacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✦", color = c.accent, fontSize = IronLogType.micro.fontSize.sp)
            Text("COACH", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight(IronLogType.button.fontWeight))
        }
    }
}

/** Catmull-Rom spline → list of cubic Bezier segments for smooth chart curves. */
private fun catmullRomPath(pts: List<Offset>, tension: Float = 0.5f): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return path
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrElse(i - 1) { pts[i] }
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts.getOrElse(i + 2) { pts[i + 1] }
        val cp1x = p1.x + (p2.x - p0.x) * tension / 3f
        val cp1y = p1.y + (p2.y - p0.y) * tension / 3f
        val cp2x = p2.x - (p3.x - p1.x) * tension / 3f
        val cp2y = p2.y - (p3.y - p1.y) * tension / 3f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

/** Catmull-Rom fill path: starts at baseline, traces the curve, closes back at baseline. */
private fun catmullRomFillPath(pts: List<Offset>, baselineY: Float, tension: Float = 0.5f): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, baselineY)
    path.lineTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrElse(i - 1) { pts[i] }
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts.getOrElse(i + 2) { pts[i + 1] }
        val cp1x = p1.x + (p2.x - p0.x) * tension / 3f
        val cp1y = p1.y + (p2.y - p0.y) * tension / 3f
        val cp2x = p2.x - (p3.x - p1.x) * tension / 3f
        val cp2y = p2.y - (p3.y - p1.y) * tension / 3f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    path.lineTo(pts.last().x, baselineY)
    path.close()
    return path
}

@Composable
private fun SessionLineChart(
    points: List<com.ironlog.app.ui.model.ChartPoint>,
    lineColor: androidx.compose.ui.graphics.Color,
    dotColor: androidx.compose.ui.graphics.Color,
    gridColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.25f),
    yMax: Float? = null,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val values = points.map { it.value.toFloat() }
    val maxVal = (yMax ?: values.max()).coerceAtLeast(1f)
    Canvas(modifier) {
        val pad = 6f
        val innerW = size.width - pad * 2
        val innerH = size.height - pad * 2
        val baselineY = pad + innerH
        // 4 horizontal grid lines (matches 5 Y-axis tick labels: max, 75%, 50%, 25%, 0)
        for (i in 0..4) {
            val y = pad + innerH * i / 4f
            drawLine(
                color = gridColor,
                start = Offset(pad, y),
                end   = Offset(pad + innerW, y),
                strokeWidth = 1f,
            )
        }
        val pts = values.mapIndexed { i, v ->
            val x = pad + if (values.size == 1) innerW / 2 else (i.toFloat() / (values.size - 1)) * innerW
            val y = pad + innerH - (v / maxVal) * innerH
            Offset(x, y)
        }
        if (pts.size > 1) {
            // Gradient fill under the curve
            drawPath(
                catmullRomFillPath(pts, baselineY),
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                    startY = pad,
                    endY = baselineY,
                ),
            )
            // Smooth Catmull-Rom curve
            drawPath(catmullRomPath(pts), color = lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        }
        pts.forEach { p ->
            drawCircle(color = dotColor, radius = 4f, center = p)
        }
    }
}

/** Round up to a clean axis tick (1, 2, 4, 5, 10, 20, …). */
private fun niceAxisMax(value: Float): Float {
    if (value <= 1f) return 1f
    if (value <= 2f) return 2f
    if (value <= 4f) return 4f
    if (value <= 5f) return 5f
    if (value <= 10f) return 10f
    // For larger values, round up to next multiple of 5 or 10
    val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(value.toDouble()))).toFloat()
    val q = value / mag
    val nice = when {
        q <= 2f -> 2f
        q <= 4f -> 4f
        q <= 5f -> 5f
        else    -> 10f
    }
    return nice * mag
}

private fun formatAxisValue(v: Float): String = when {
    v >= 1000f -> "${(v / 1000f).let { if (it == it.toInt().toFloat()) it.toInt().toString() else String.format(java.util.Locale.US, "%.1f", it) }}k"
    v == v.toInt().toFloat() -> v.toInt().toString()
    else -> String.format(java.util.Locale.US, "%.1f", v)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val c = useTheme()
    Card(
        modifier,
        shape = RoundedCornerShape(IronLogRadius.lg.dp),
        colors = CardDefaults.cardColors(containerColor = c.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
    ) {
        Column(Modifier.appPadding(12.dp)) {
            Text(
                label,
                color = c.muted,
                fontSize = IronLogType.micro.fontSize.sp,
                fontWeight = FontWeight(IronLogType.meta.fontWeight),
            )
            // FIXED: 23 — title (20sp) fits 4-col layout; display (32sp) was too large
            Text(
                value,
                color = c.text,
                fontWeight = FontWeight(IronLogType.display.fontWeight),
                fontSize = IronLogType.title.fontSize.sp,
                lineHeight = IronLogType.title.lineHeight.sp,
            )
        }
    }
}

fun parseLocalDate(dateStr: String): LocalDate = LocalDate.parse(dateStr)
fun estimateOneRM(weight: Double, reps: Int = 5): Int = round(weight * (1 + kotlin.math.max(1, reps) / 30.0)).toInt()
fun statsGetStreak(history: List<HistoryEntry>): Int = getStreak(history)
