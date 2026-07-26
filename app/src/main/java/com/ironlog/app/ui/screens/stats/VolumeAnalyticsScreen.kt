package com.ironlog.app.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.domain.intelligence.FINE_MUSCLE_ABBREV
import com.ironlog.app.domain.intelligence.FINE_MUSCLE_DISPLAY
import com.ironlog.app.domain.intelligence.TrainingIntelligenceEngine
import com.ironlog.app.domain.intelligence.computeGranularVolume
import com.ironlog.app.domain.intelligence.foldGranularToRadar
import com.ironlog.app.services.ShareService
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.ui.viewmodel.StatsViewModel
import java.time.Instant
import com.ironlog.app.domain.gamification.parseHistoryInstant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ── Constants ─────────────────────────────────────────────────────────────────

private enum class MuscleTab(val label: String, val days: Int) {
    WEEK("WEEK", 7), DAYS7("7D", 7), DAYS30("30D", 30), PROGRAM("PROGRAM", 42)
}

private val MUSCLE_AXES = listOf("Core", "Arms", "Chest", "Legs", "Back", "Shoulders")

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeAnalyticsScreen(
    vm: StatsViewModel = viewModel(),
    appVm: AppDataViewModel = viewModel(),
    onBack: () -> Unit = {},
    onOpenBodyWeight: () -> Unit = {},
) {
    val c = useTheme()
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val appState by appVm.state.collectAsState()

    var activeTab by remember { mutableStateOf(MuscleTab.DAYS30) }
    var drillMuscle by remember { mutableStateOf<String?>(null) }
    val activePlan = appState.plans.firstOrNull { it.isActive }
    val weightUnit = appState.settings.weightUnit

    // ── Filtering ──────────────────────────────────────────────────────────
    val filtered = remember(state.history, activeTab) {
        when (activeTab) {
            MuscleTab.WEEK -> {
                val startOfWeek = LocalDate.now().with(
                    WeekFields.ISO.dayOfWeek(), 1L
                )
                state.history.filter {
                    val d = parseHistoryInstant(it.date)?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: return@filter false
                    !d.isBefore(startOfWeek)
                }
            }
            MuscleTab.DAYS7 -> state.history.filter { ageDays(it.date) <= 7 }
            // GAP-04: PROGRAM uses active-plan block duration (weeks × days), falling back to 42 days
            MuscleTab.PROGRAM -> {
                val planDays = (activePlan?.days?.size ?: 0)
                    .let { if (it >= 3) it * 6 else 42 } // e.g. 4-day plan → 4×6=24 days ≈ 4 weeks
                    .coerceAtLeast(28)
                state.history.filter { ageDays(it.date) <= planDays }
            }
            MuscleTab.DAYS30 -> state.history.filter { ageDays(it.date) <= 30 }
        }
    }
    val rangeDays = activeTab.days
    val previousFiltered = remember(state.history, rangeDays) {
        state.history.filter { d -> ageDays(d.date) in (rangeDays + 1).toLong()..(rangeDays * 2L) }
    }

    // ── Aggregations ───────────────────────────────────────────────────────
    val granularMuscles = remember(filtered) { computeGranularVolume(filtered) }
    val volumeByMuscle = remember(granularMuscles) { foldGranularToRadar(granularMuscles, MUSCLE_AXES) }
    // GAP-22: Previous period radar overlay
    val previousGranularMuscles = remember(previousFiltered) { computeGranularVolume(previousFiltered) }
    val previousVolumeByMuscle = remember(previousGranularMuscles) { foldGranularToRadar(previousGranularMuscles, MUSCLE_AXES) }
    val weeklyVolume = remember(filtered) { computeWeeklyVolume(filtered) }
    val snapshot = remember(filtered) { TrainingIntelligenceEngine.build(filtered, 0) }

    val currentTotalSets = volumeByMuscle.values.sum()
    val weeksInRange = (rangeDays / 7f).coerceAtLeast(1f)

    val musclesHit = granularMuscles.size
    val totalVolumeKg = filtered.sumOf { it.volume }
    val actualWorkingSets = remember(filtered) {
        filtered.sumOf { session -> session.exercises.sumOf { ex -> ex.sets.count { it.type != "warmup" } } }
    }
    val previousTotalKg = remember(previousFiltered) { previousFiltered.sumOf { it.volume } }
    val deltaKg = totalVolumeKg - previousTotalKg
    val deltaPctKg = if (previousTotalKg <= 0.0) null else (deltaKg * 100.0 / previousTotalKg)
    val trendLabel = if (previousTotalKg <= 0.0) "Baseline" else when {
        (deltaPctKg ?: 0.0) > 3.0 -> "Progressing"
        (deltaPctKg ?: 0.0) < -3.0 -> "Regressing"
        else -> "Stable"
    }

    // Push/Pull/Legs balance from current tab's folded data
    val pushSets = (volumeByMuscle["Chest"] ?: 0) + (volumeByMuscle["Shoulders"] ?: 0)
    val pullSets = volumeByMuscle["Back"] ?: 0
    val legsSets = volumeByMuscle["Legs"] ?: 0
    val pplTotal = (pushSets + pullSets + legsSets).coerceAtLeast(1)
    val pushPct = (pushSets * 100f / pplTotal).toInt()
    val pullPct = (pullSets * 100f / pplTotal).toInt()
    val legsPct = 100 - pushPct - pullPct
    val pushWeekly = pushSets / weeksInRange
    val pullWeekly = pullSets / weeksInRange
    val legsWeekly = legsSets / weeksInRange

    // Next week actions
    val actions = remember(pushPct, pullPct, legsPct, pushWeekly, pullWeekly, legsWeekly) {
        buildNextWeekActions(pushPct, pullPct, legsPct, pushWeekly, pullWeekly, legsWeekly)
    }

    // Session counts
    val sessionCount = filtered.size
    val prevSessionCount = previousFiltered.size

    // Share text
    val shareText = buildString {
        appendLine("IronLog Volume Analytics")
        appendLine("Range: ${activeTab.label}")
        appendLine("Total working sets: $currentTotalSets")
        appendLine("Muscles hit: $musclesHit")
        volumeByMuscle.forEach { (k, v) -> appendLine("$k: $v sets") }
        appendLine("Push: ${String.format(Locale.US, "%.1f", pushWeekly)}/wk  Pull: ${String.format(Locale.US, "%.1f", pullWeekly)}/wk  Legs: ${String.format(Locale.US, "%.1f", legsWeekly)}/wk")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ── 1. Header ──────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack, "Back",
                            tint = c.accent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column {
                        Text(
                            "MUSCLE ANALYTICS",
                            color = c.text,
                            fontSize = IronLogType.section.fontSize.sp,
                            fontWeight = FontWeight(IronLogType.section.fontWeight),
                            letterSpacing = IronLogType.section.letterSpacing.sp,
                        )
                        if (activePlan != null) {
                            Text(
                                "${activePlan.name} / ${if (activeTab == MuscleTab.PROGRAM) "program view" else "${activeTab.label} view"}",
                                color = c.accent,
                                fontSize = IronLogType.meta.fontSize.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    ShareService.shareMinimalCardImage(
                        context = context,
                        title = "Ironlog Volume Analytics",
                        headline = activeTab.label,
                        metrics = listOf(
                            ShareService.ShareMetric("Working sets", currentTotalSets.toString()),
                            ShareService.ShareMetric("Muscles hit", musclesHit.toString()),
                            ShareService.ShareMetric("Trend", trendLabel),
                        ),
                        footnote = "Push $pushPct% · Pull $pullPct% · Legs $legsPct%",
                    )
                }) {
                    Icon(Icons.Outlined.Share, "Share", tint = c.muted)
                }
            }
        }

        // ── 2. Tab switcher ────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MuscleTab.entries.forEach { tab ->
                    val active = activeTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(IronLogRadius.md.dp))
                            .background(if (active) c.accent else c.surface)
                            .border(1.dp, if (active) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.md.dp))
                            .clickable { activeTab = tab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tab.label,
                            color = if (active) c.textOnAccent else c.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                            fontWeight = FontWeight(IronLogType.button.fontWeight),
                            letterSpacing = IronLogType.button.letterSpacing.sp,
                        )
                    }
                }
            }
        }

        // ── 3. Summary card ────────────────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                shape = RoundedCornerShape(IronLogRadius.lg.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "IRONLOG",
                        color = c.text,
                        fontSize = IronLogType.title.fontSize.sp,
                        fontWeight = FontWeight(IronLogType.display.fontWeight),
                        letterSpacing = IronLogType.title.letterSpacing.sp,
                    )
                    Text(
                        "VOLUME ANALYTICS",
                        color = c.muted,
                        fontSize = IronLogType.eyebrow.fontSize.sp,
                        fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                        letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                    )
                    if (activePlan != null) {
                        Text(
                            "${activePlan.name} / ${if (activeTab == MuscleTab.PROGRAM) "program view" else "${activeTab.label} view"}",
                            color = c.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (filtered.isEmpty()) "Log training to unlock your volume interpretation."
                        else "${currentTotalSets} working sets logged across ${sessionCount} sessions.",
                        color = c.accent,
                        fontSize = IronLogType.body.fontSize.sp,
                        fontWeight = FontWeight(500),
                    )
                }
            }
        }

        // ── 4. Stats row ───────────────────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                shape = RoundedCornerShape(IronLogRadius.lg.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // GAP-07: convert to user's weight unit before display
                    val totalVolumeDisplay = if (weightUnit == "lbs") totalVolumeKg * 2.2046226218 else totalVolumeKg
                    val formattedVolume = if (totalVolumeDisplay >= 1000.0)
                        "${String.format(Locale.US, "%.1f", totalVolumeDisplay / 1000.0)}k$weightUnit"
                    else "${String.format(Locale.US, "%.0f", totalVolumeDisplay)}$weightUnit"
                    listOf(
                        sessionCount.toString() to "Sessions",
                        actualWorkingSets.toString() to "Sets",
                        musclesHit.toString() to "Muscles Hit",
                        formattedVolume to "Volume ($weightUnit)",
                    ).forEachIndexed { idx, (value, label) ->
                        if (idx > 0) Box(Modifier.width(1.dp).height(40.dp).background(c.faint))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                value,
                                color = c.accent,
                                fontSize = IronLogType.title.fontSize.sp,
                                fontWeight = FontWeight(IronLogType.display.fontWeight),
                            )
                            Text(label, color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                        }
                    }
                }
            }
        }

        // ── 5. Performance Signals ─────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "PERFORMANCE SIGNALS",
                    color = c.muted,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Volume trend card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = c.card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                        shape = RoundedCornerShape(IronLogRadius.lg.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Volume trend", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                            val trendColor = when (trendLabel) {
                                "Progressing" -> c.success
                                "Regressing" -> c.danger
                                else -> c.text
                            }
                            Text(
                                trendLabel,
                                color = trendColor,
                                fontSize = IronLogType.body.fontSize.sp,
                                fontWeight = FontWeight(700),
                            )
                            Text(
                                if (deltaPctKg == null) "No prior data"
                                else "${if (deltaKg >= 0) "+" else ""}${String.format(Locale.US, "%.1f", deltaPctKg)}% vs prior",
                                color = c.muted,
                                fontSize = IronLogType.meta.fontSize.sp,
                            )
                        }
                    }
                    // Consistency card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = c.card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                        shape = RoundedCornerShape(IronLogRadius.lg.dp),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Workout consistency", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                            Text(
                                if (sessionCount == 0) "Program baseline only" else "$sessionCount sessions",
                                color = c.text,
                                fontSize = IronLogType.body.fontSize.sp,
                                fontWeight = FontWeight(700),
                            )
                            Text(
                                "$sessionCount vs $prevSessionCount sessions",
                                color = c.subtext,
                                fontSize = IronLogType.meta.fontSize.sp,
                            )
                        }
                    }
                }
                Text(
                    "Radar is summary only. Use bars and trend signals for precision.",
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
                OutlinedButton(
                    onClick = onOpenBodyWeight,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(IronLogRadius.md.dp),
                ) {
                    Icon(Icons.Outlined.BarChart, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open body composition stats", color = c.accent, fontSize = IronLogType.body.fontSize.sp)
                }
            }
        }

        // ── 6. Volume Trend (Weeks) ────────────────────────────────────────
        item {
            AnalyticsCard("VOLUME TREND (WEEKS)") {
                WeeklyLineChart(weeklyVolume)
                Text(
                    "Use this with imbalance insights to decide what to increase, hold, or reduce next week.",
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
            }
        }

        // ── 7. Muscle Volume Radar ─────────────────────────────────────────
        item {
            AnalyticsCard("MUSCLE VOLUME RADAR") {
                RadarChart(
                    data = volumeByMuscle,
                    previousData = previousVolumeByMuscle.takeIf { it.values.any { v -> v > 0 } },
                )
            }
        }

        // ── 8. Effective Sets Per Muscle ───────────────────────────────────
        item {
            AnalyticsCard("EFFECTIVE SETS PER MUSCLE") {
                EffectiveSetsChart(granularMuscles)
                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf("0-9" to 0.35f, "10-20" to 0.6f, "21-25" to 0.85f, "25+" to 1f).forEach { (label, alpha) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(c.chartSecondary.copy(alpha = alpha))
                            )
                            Text(label, color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                        }
                    }
                }
            }
        }

        // ── 9. Push / Pull / Legs Balance ─────────────────────────────────
        item {
            AnalyticsCard("PUSH / PULL / LEGS BALANCE") {
                BalanceBar(pushPct, pullPct, legsPct)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        Triple("Push", pushWeekly, c.chartPrimary),
                        Triple("Pull", pullWeekly, c.chartSecondary),
                        Triple("Legs", legsWeekly, c.accent),
                    ).forEach { (label, weekly, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(
                                "$label ${String.format(Locale.US, "%.1f", weekly)}",
                                color = c.text,
                                fontSize = IronLogType.meta.fontSize.sp,
                                fontWeight = FontWeight(600),
                            )
                            val pct = when (label) { "Push" -> pushPct; "Pull" -> pullPct; else -> legsPct }
                            Text("$pct%", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        }
                    }
                }
                val balanceText = when {
                    abs(pushPct - pullPct) <= 18 -> "Balance is healthy. Keep volume distribution close to current."
                    pushPct > pullPct + 18 -> "Push is far ahead. Add more pulling movements."
                    pullPct > pushPct + 18 -> "Pull is far ahead. Add more pressing movements."
                    legsPct < 20 -> "Leg volume is low. Consider adding leg sessions."
                    else -> "Distribution looks reasonable. Monitor week to week."
                }
                Text(balanceText, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
            }
        }

        // ── 9b. Imbalance Insights ────────────────────────────────────────
        if (granularMuscles.isNotEmpty()) {
            item {
                val chestTotal = (granularMuscles["upperChest"] ?: 0f) +
                    (granularMuscles["midChest"] ?: 0f) +
                    (granularMuscles["lowerChest"] ?: 0f)
                val rearDeltSets  = granularMuscles["rearDelts"]  ?: 0f
                val frontDeltSets = granularMuscles["frontDelts"] ?: 0f
                val hamSets       = granularMuscles["hamstrings"] ?: 0f
                val quadSets      = granularMuscles["quads"]      ?: 0f
                val insights = mutableListOf<String>()
                if (chestTotal > 0f && rearDeltSets < chestTotal * 0.35f)
                    insights += "Rear delts are low relative to chest — add face pulls or rear delt flyes."
                if (quadSets > 0f && hamSets < quadSets * 0.55f)
                    insights += "Hamstrings volume is low relative to quads — add Romanian deadlifts or leg curls."
                if (rearDeltSets > 0f && frontDeltSets > rearDeltSets * 1.6f)
                    insights += "Front delts far outpace rear delts — common with heavy pressing programs."
                if (insights.isEmpty())
                    insights += "Muscle balance looks good. No major imbalances detected."
                AnalyticsCard("IMBALANCE INSIGHTS") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        insights.forEach { tip ->
                            Text("• $tip", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                        }
                    }
                }
            }
        }

        // ── 10. Next Week Actions ──────────────────────────────────────────
        item {
            AnalyticsCard("NEXT WEEK ACTIONS") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEachIndexed { i, action ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .border(1.dp, c.accentBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${i + 1}",
                                    color = c.accent,
                                    fontSize = IronLogType.micro.fontSize.sp,
                                    fontWeight = FontWeight(700),
                                )
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = c.surface),
                                shape = RoundedCornerShape(IronLogRadius.md.dp),
                            ) {
                                Text(
                                    action,
                                    color = c.text,
                                    fontSize = IronLogType.body.fontSize.sp,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 11. Muscle Breakdown header ────────────────────────────────────
        item {
            Text(
                "MUSCLE BREAKDOWN",
                color = c.muted,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
        }

        // ── 11. Muscle Breakdown rows ──────────────────────────────────────
        if (granularMuscles.isEmpty()) {
            item {
                Text("Log workouts to see muscle breakdown.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
            }
        } else {
            val maxVal = granularMuscles.values.maxOrNull()?.coerceAtLeast(0.1f) ?: 1f
            val cardShape = RoundedCornerShape(IronLogRadius.lg.dp)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                    shape = cardShape,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        granularMuscles.entries.toList().forEach { (muscle, setsF) ->
                            val ratio = setsF / maxVal
                            val dotColor = when {
                                setsF >= 25f -> c.chartSecondary
                                setsF >= 21f -> c.chartSecondary.copy(alpha = 0.85f)
                                setsF >= 10f -> c.chartSecondary.copy(alpha = 0.6f)
                                else -> c.chartSecondary.copy(alpha = 0.4f)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { drillMuscle = muscle }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                                Text(
                                    FINE_MUSCLE_DISPLAY[muscle] ?: muscle,
                                    color = c.text,
                                    fontSize = IronLogType.body.fontSize.sp,
                                    modifier = Modifier.width(110.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Box(
                                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(c.faint),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(ratio)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(c.chartPrimary),
                                    )
                                }
                                Text(
                                    String.format(Locale.US, "%.1f", setsF),
                                    color = c.accent,
                                    fontSize = IronLogType.meta.fontSize.sp,
                                    fontWeight = FontWeight(700),
                                    modifier = Modifier.width(32.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // ── Muscle Drill-Down Sheet ────────────────────────────────────────────
    if (drillMuscle != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val targetMuscle = drillMuscle!!
        val displayName = FINE_MUSCLE_DISPLAY[targetMuscle] ?: targetMuscle

        // Compute exercises targeting this muscle, aggregated over current window
        val drillExercises = remember(filtered, targetMuscle) {
            val accumulator = mutableMapOf<String, Triple<String, Int, Double>>() // exerciseId -> (name, sets, volume)
            filtered.forEach { session ->
                session.exercises.forEach { ex ->
                    val targets = when {
                        ex.primaryMuscles.isNotEmpty() -> ex.primaryMuscles
                        ex.primaryMuscle != null -> listOf(ex.primaryMuscle)
                        else -> emptyList()
                    }
                    // Match by direct muscle name (camelCase key) or by lowercased display name
                    val displayLower = FINE_MUSCLE_DISPLAY[targetMuscle]?.lowercase() ?: targetMuscle.lowercase()
                    val matches = targets.any { m ->
                        m.equals(targetMuscle, ignoreCase = true) ||
                        m.replace(" ", "").equals(targetMuscle, ignoreCase = true) ||
                        m.lowercase() == displayLower
                    }
                    if (matches) {
                        val workingSets = ex.sets.count { it.type != "warmup" }
                        val vol = ex.sets.filter { it.type != "warmup" }.sumOf { it.weight * it.reps }
                        val existing = accumulator[ex.exerciseId.ifBlank { ex.name }]
                        if (existing != null) {
                            accumulator[ex.exerciseId.ifBlank { ex.name }] = Triple(
                                existing.first,
                                existing.second + workingSets,
                                existing.third + vol,
                            )
                        } else {
                            accumulator[ex.exerciseId.ifBlank { ex.name }] = Triple(ex.name, workingSets, vol)
                        }
                    }
                }
            }
            accumulator.values.sortedByDescending { it.second }
        }

        ModalBottomSheet(
            onDismissRequest = { drillMuscle = null },
            sheetState = sheetState,
            containerColor = c.card,
            contentColor = c.text,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    displayName.uppercase(),
                    color = c.text,
                    fontSize = IronLogType.section.fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = IronLogType.section.letterSpacing.sp,
                )
                Text(
                    "Exercises targeting this muscle in the current window",
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
                if (drillExercises.isEmpty()) {
                    Text(
                        "No exercises found for $displayName in the current period.",
                        color = c.subtext,
                        fontSize = IronLogType.body.fontSize.sp,
                    )
                } else {
                    // Column header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Exercise",
                            color = c.muted,
                            fontSize = IronLogType.meta.fontSize.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Sets",
                            color = c.muted,
                            fontSize = IronLogType.meta.fontSize.sp,
                            modifier = Modifier.width(36.dp),
                            fontWeight = FontWeight(600),
                        )
                        Text(
                            "Volume",
                            color = c.muted,
                            fontSize = IronLogType.meta.fontSize.sp,
                            modifier = Modifier.width(72.dp),
                            fontWeight = FontWeight(600),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.cardBorder))
                    drillExercises.forEach { (name, sets, volume) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                name,
                                color = c.text,
                                fontSize = IronLogType.body.fontSize.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                sets.toString(),
                                color = c.accent,
                                fontSize = IronLogType.body.fontSize.sp,
                                fontWeight = FontWeight(700),
                                modifier = Modifier.width(36.dp),
                            )
                            val formattedVol = if (volume >= 1000.0)
                                "${String.format(Locale.US, "%.1f", volume / 1000.0)}k"
                            else
                                String.format(Locale.US, "%.0f", volume)
                            Text(
                                formattedVol,
                                color = c.subtext,
                                fontSize = IronLogType.meta.fontSize.sp,
                                modifier = Modifier.width(72.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Card wrapper ──────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsCard(title: String, content: @Composable () -> Unit) {
    val c = useTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
        shape = RoundedCornerShape(IronLogRadius.lg.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                color = c.muted,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
            content()
        }
    }
}

// ── Effective Sets Per Muscle (horizontally scrollable vertical bar chart) ────

@Composable
private fun EffectiveSetsChart(muscles: Map<String, Float>) {
    val c = useTheme()
    if (muscles.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("Log workouts to see muscle chart", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
        }
        return
    }

    // Raw totals for the selected window, sorted descending
    val sortedData = muscles.entries.sortedByDescending { it.value }

    val maxSets = sortedData.firstOrNull()?.value ?: 0f
    val yMax = listOf(10f, 20f, 30f, 50f, 75f, 100f, 150f).firstOrNull { it >= maxSets } ?: 150f
    val yTicks = listOf(0f, yMax * 0.25f, yMax * 0.5f, yMax * 0.75f, yMax)

    fun toArgb(col: androidx.compose.ui.graphics.Color) = android.graphics.Color.argb(
        (col.alpha * 255).toInt(), (col.red * 255).toInt(), (col.green * 255).toInt(), (col.blue * 255).toInt()
    )
    val valuePaint = remember(c) {
        android.graphics.Paint().apply {
            textSize = 26f; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }.also { it.color = toArgb(c.text) }
    val labelPaint = remember(c) {
        android.graphics.Paint().apply {
            textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
    }.also { it.color = toArgb(c.muted) }

    val barW = 44.dp
    val chartH = 160.dp

    Row(modifier = Modifier.fillMaxWidth().height(chartH), verticalAlignment = Alignment.Top) {
        // Y-axis
        Column(
            modifier = Modifier.width(28.dp).height(chartH),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            yTicks.reversed().forEach { v ->
                Text(
                    if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(Locale.US, "%.0f", v),
                    color = c.muted,
                    fontSize = IronLogType.micro.fontSize.sp,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        // Scrollable bars
        Row(
            modifier = Modifier.weight(1f).height(chartH).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sortedData.forEach { (muscle, rawVal) ->
                val ratio = (rawVal / yMax).coerceIn(0f, 1f)
                val barColor = when {
                    rawVal >= 25f -> c.chartSecondary
                    rawVal >= 21f -> c.chartSecondary.copy(alpha = 0.85f)
                    rawVal >= 10f -> c.chartSecondary.copy(alpha = 0.6f)
                    else -> c.chartSecondary.copy(alpha = 0.4f)
                }
                Canvas(
                    modifier = Modifier.width(barW).height(chartH),
                ) {
                    val topPad = 28f
                    val botPad = 32f
                    val plotH = size.height - topPad - botPad
                    val bH = plotH * ratio
                    val bL = (size.width - barW.toPx() * 0.8f) / 2f
                    val bR = size.width - bL
                    val bW = bR - bL
                    val top = topPad + plotH - bH
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(bL, top),
                        size = Size(bW, bH.coerceAtLeast(2f)),
                        cornerRadius = CornerRadius(5f, 5f),
                    )
                    val nc = drawContext.canvas.nativeCanvas
                    nc.drawText(
                        String.format(Locale.US, "%.1f", rawVal),
                        size.width / 2f, top - 6f, valuePaint,
                    )
                    nc.drawText(
                        FINE_MUSCLE_ABBREV[muscle] ?: muscle.take(7),
                        size.width / 2f, size.height - 4f, labelPaint,
                    )
                }
            }
        }
    }
}

// ── Push/Pull/Legs Balance Bar ────────────────────────────────────────────────

@Composable
private fun BalanceBar(pushPct: Int, pullPct: Int, legsPct: Int) {
    val c = useTheme()
    Box(
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(c.faint),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.weight(pushPct.coerceAtLeast(1).toFloat()).fillMaxSize().background(c.chartPrimary))
            Box(Modifier.weight(pullPct.coerceAtLeast(1).toFloat()).fillMaxSize().background(c.chartSecondary))
            Box(Modifier.weight(legsPct.coerceAtLeast(1).toFloat()).fillMaxSize().background(c.accent))
        }
    }
}

// ── Radar Chart ───────────────────────────────────────────────────────────────

@Composable
private fun RadarChart(data: Map<String, Int>, previousData: Map<String, Int>? = null) {
    val c = useTheme()
    val hasData = data.values.any { it > 0 }
    val maxVal = (((data.values.maxOrNull() ?: 0) + (previousData?.values?.maxOrNull() ?: 0)).coerceAtLeast(1))

    if (!hasData) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text("Log workouts to see muscle radar", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
        }
        return
    }

    fun toArgb(col: androidx.compose.ui.graphics.Color) = android.graphics.Color.argb(
        255, (col.red * 255).toInt(), (col.green * 255).toInt(), (col.blue * 255).toInt()
    )
    val labelPaint = remember(c) {
        android.graphics.Paint().apply {
            textSize = 34f; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }.also { it.color = toArgb(c.text) }
    val valuePaint = remember(c) {
        android.graphics.Paint().apply {
            textSize = 28f; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
    }.also { it.color = toArgb(c.muted) }

    Canvas(Modifier.fillMaxWidth().height(280.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.32f
        val labelR = radius + 44f
        val n = MUSCLE_AXES.size

        repeat(4) { ring ->
            val rr = radius * ((ring + 1) / 4f)
            val pts = (0 until n).map { i ->
                val a = (2 * PI * i / n - PI / 2).toFloat()
                Offset(center.x + rr * cos(a), center.y + rr * sin(a))
            }
            for (i in pts.indices) drawLine(c.faint, pts[i], pts[(i + 1) % n], 1f)
        }
        repeat(n) { i ->
            val a = (2 * PI * i / n - PI / 2).toFloat()
            drawLine(c.faint, center, Offset(center.x + radius * cos(a), center.y + radius * sin(a)), 1f)
        }

        // GAP-22: Previous period polygon (drawn first so current overlays it)
        if (previousData != null) {
            val prevPath = Path()
            MUSCLE_AXES.forEachIndexed { i, key ->
                val ratio = (previousData[key] ?: 0).toFloat() / maxVal
                val a = (2 * PI * i / n - PI / 2).toFloat()
                val p = Offset(center.x + radius * ratio * cos(a), center.y + radius * ratio * sin(a))
                if (i == 0) prevPath.moveTo(p.x, p.y) else prevPath.lineTo(p.x, p.y)
            }
            prevPath.close()
            drawPath(prevPath, c.muted.copy(alpha = 0.15f))
            drawPath(prevPath, c.muted.copy(alpha = 0.6f), style = Stroke(
                width = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f),
            ))
        }

        val path = Path()
        MUSCLE_AXES.forEachIndexed { i, key ->
            val ratio = (data[key] ?: 0).toFloat() / maxVal
            val a = (2 * PI * i / n - PI / 2).toFloat()
            val p = Offset(center.x + radius * ratio * cos(a), center.y + radius * ratio * sin(a))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, c.chartPrimary.copy(alpha = 0.35f))
        drawPath(path, c.chartPrimary, style = Stroke(2.5f))

        MUSCLE_AXES.forEachIndexed { i, key ->
            val ratio = (data[key] ?: 0).toFloat() / maxVal
            val a = (2 * PI * i / n - PI / 2).toFloat()
            val p = Offset(center.x + radius * ratio * cos(a), center.y + radius * ratio * sin(a))
            drawCircle(c.accent, 5f, p)
        }

        val nc = drawContext.canvas.nativeCanvas
        MUSCLE_AXES.forEachIndexed { i, label ->
            val a = (2 * PI * i / n - PI / 2).toFloat()
            val lx = center.x + labelR * cos(a)
            val ly = center.y + labelR * sin(a) + labelPaint.textSize / 3f
            nc.drawText(label, lx, ly, labelPaint)
            nc.drawText((data[label] ?: 0).toString(), lx, ly + valuePaint.textSize + 4f, valuePaint)
        }
    }

    // GAP-22: Legend row (only shown when previous period data is available)
    if (previousData != null) {
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Current period swatch
            Box(Modifier.size(width = 20.dp, height = 3.dp).background(c.chartPrimary))
            Spacer(Modifier.width(4.dp))
            Text("Current", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
            Spacer(Modifier.width(16.dp))
            // Previous period dashed swatch (simulated with two boxes)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) {
                    Box(Modifier.size(width = 5.dp, height = 2.dp).background(c.muted.copy(alpha = 0.6f)))
                }
            }
            Spacer(Modifier.width(4.dp))
            Text("Previous period", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
        }
    }
}

// ── Weekly Line Chart ─────────────────────────────────────────────────────────

@Composable
private fun WeeklyLineChart(points: List<Int>) {
    val c = useTheme()
    if (points.isEmpty()) {
        Text("No weekly data yet", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
        return
    }
    val maxV = points.maxOrNull()?.coerceAtLeast(1) ?: 1
    val yMax = niceVolumeAxisMax(maxV.toFloat())

    Row(modifier = Modifier.fillMaxWidth().height(160.dp), verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.width(44.dp).height(160.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            listOf(yMax, yMax * 0.75f, yMax * 0.5f, yMax * 0.25f, 0f).forEach { v ->
                Text(formatVolumeAxisValue(v), color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
            }
        }
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.weight(1f).height(160.dp)) {
            val pad = 6f
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            for (i in 0..4) {
                val y = pad + h * i / 4f
                val dashW = w / 48f
                for (d in 0 until 24) {
                    val sx = pad + d * dashW * 2
                    drawLine(c.faint, Offset(sx, y), Offset(sx + dashW, y), 1f)
                }
            }
            val offsets = points.mapIndexed { i, v ->
                val x = pad + if (points.size == 1) w / 2 else i.toFloat() / (points.size - 1) * w
                val y = pad + h - (v / yMax) * h
                Offset(x, y)
            }
            if (offsets.size > 1) drawPath(catmullRomPath(offsets), c.chartPrimary, style = Stroke(3f))
            offsets.forEach { drawCircle(c.accent, 3.5f, it) }
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

private fun niceVolumeAxisMax(value: Float): Float {
    if (value <= 0f) return 10f
    val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(value.toDouble()))).toFloat()
    val q = value / mag
    val nice = when { q <= 1f -> 1f; q <= 2f -> 2f; q <= 4f -> 4f; q <= 5f -> 5f; else -> 10f }
    return nice * mag
}

private fun formatVolumeAxisValue(v: Float): String = when {
    v >= 1000f -> "${(v / 1000f).toInt()}k"
    v == v.toInt().toFloat() -> v.toInt().toString()
    else -> String.format(Locale.US, "%.1f", v)
}

private fun catmullRomPath(pts: List<Offset>): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    p.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return p
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrNull(i - 1) ?: pts[i]
        val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts.getOrNull(i + 2) ?: p2
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        p.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return p
}

private fun ageDays(iso: String): Long {
    val t = parseHistoryInstant(iso)?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: return Long.MAX_VALUE
    return java.time.temporal.ChronoUnit.DAYS.between(t, LocalDate.now())
}

private fun computeWeeklyVolume(history: List<HistoryEntry>): List<Int> {
    val byWeek = linkedMapOf<String, Int>()
    history.forEach { h ->
        val d = parseHistoryInstant(h.date)?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: return@forEach
        val wf = WeekFields.ISO
        val key = "${d.get(wf.weekBasedYear())}-${d.get(wf.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
        byWeek[key] = (byWeek[key] ?: 0) + h.exercises.sumOf { ex -> ex.sets.count { s -> s.type != "warmup" } }
    }
    return byWeek.entries.sortedBy { it.key }.takeLast(12).map { it.value }
}

private fun buildNextWeekActions(
    pushPct: Int, pullPct: Int, legsPct: Int,
    pushW: Float, pullW: Float, legsW: Float,
): List<String> {
    val list = mutableListOf<String>()
    val diff = abs(pushPct - pullPct)
    when {
        diff <= 18 -> list.add("Balance is healthy. Keep volume distribution close to current.")
        pushPct > pullPct + 18 -> list.add("Push is far ahead. Add more pulling movements (rows, pull-ups).")
        else -> list.add("Pull is far ahead. Add more pressing movements (bench, overhead).")
    }
    if (legsPct < 20) list.add("Leg volume is low ($legsPct%). Consider adding leg sessions next week.")
    if (pushW < 10f) list.add("Push volume (${String.format(Locale.US, "%.1f", pushW)} sets/wk) is below MEV of 10. Aim higher.")
    if (pullW < 10f) list.add("Pull volume (${String.format(Locale.US, "%.1f", pullW)} sets/wk) is below MEV of 10. Aim higher.")
    if (legsW < 12f) list.add("Leg volume (${String.format(Locale.US, "%.1f", legsW)} sets/wk) is below MEV of 12. Add leg work.")
    if (list.size == 1 && diff <= 5 && pushW >= 10f && pullW >= 10f && legsW >= 12f) {
        list.add("All muscle groups are in productive ranges. Consider progressive overload.")
    }
    return list
}


