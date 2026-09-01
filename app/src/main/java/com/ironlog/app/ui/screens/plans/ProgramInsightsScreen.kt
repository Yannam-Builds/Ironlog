package com.ironlog.app.ui.screens.plans

import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.domain.gamification.parseHistoryInstant
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ProgramInsightsScreen(
    vm: AppDataViewModel = viewModel(),
    onBack: () -> Unit = {},
    onOpenPlans: () -> Unit = {},
) {
    val c = useTheme()
    val state by vm.state.collectAsStateWithLifecycle()

    // GAP-02: use the active plan, not just firstOrNull()
    val activePlan = state.plans.firstOrNull { it.isActive } ?: state.plans.firstOrNull()
    val workouts = state.history
    val weeklyGoalDays = state.settings.weeklyGoalDays.coerceAtLeast(1)

    // GAP-01: real sessions-per-week from date range
    val (sessionsPerWeek, adherence, consistency, weekCount) = remember(workouts, weeklyGoalDays) {
        computeInsightsMetrics(workouts, weeklyGoalDays)
    }

    // GAP-17: per-day adherence breakdown for active plan
    val perDayStats = remember(workouts, activePlan) {
        if (activePlan == null) emptyList()
        else computePerDayAdherence(workouts, activePlan.days.map { it.name })
    }

    // PRs set since plan creation (approximate: since earliest workout)
    val prSincePlan = remember(workouts) {
        if (workouts.isEmpty()) emptyList<Pair<String, Double?>>()
        else {
            val cutoff = workouts.minByOrNull { it.date }?.date ?: return@remember emptyList()
            workouts.filter { it.date >= cutoff }
                .flatMap { it.exercises }
                .groupBy { it.name }
                .mapValues { (_, exList) ->
                    exList.flatMap { it.sets }
                        .filter { it.type != "warmup" && it.weight > 0 }
                        .maxByOrNull { it.weight }?.weight
                }
                .filterValues { it != null }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(16.dp),
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "PROGRAM INSIGHTS", onBack = onBack) }

        // ── Current Program ────────────────────────────────────────────────
        item {
            InsightsCard("Current Program") {
                Text(activePlan?.name ?: "No active program selected.", color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (activePlan != null) {
                    Text("${activePlan.days.size} training days", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                } else {
                    Button(onClick = onOpenPlans, modifier = Modifier.padding(top = 8.dp)) { Text("GO TO PLANS") }
                }
            }
        }

        // ── Adherence ─────────────────────────────────────────────────────
        item {
            InsightsCard("Adherence") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$adherence%", color = c.accent, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight.Black)
                    Text("$weeklyGoalDays days/wk goal", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                }
                LinearProgressIndicator(
                    progress = { (adherence / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(IronLogRadius.full.dp)),
                    color = when {
                        adherence >= 85 -> c.success
                        adherence >= 50 -> c.warning
                        else -> c.danger
                    },
                    trackColor = c.faint,
                )
                Text(
                    java.lang.String.format(java.util.Locale.US, "Based on %d week%s · %.1f sessions/wk", weekCount, if (weekCount != 1) "s" else "", sessionsPerWeek),
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
            }
        }

        // ── Consistency ───────────────────────────────────────────────────
        item {
            InsightsCard("Consistency") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$consistency%", color = c.accent, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight.Black)
                    Text("of weeks hit goal", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                }
                LinearProgressIndicator(
                    progress = { (consistency / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(IronLogRadius.full.dp)),
                    color = if (consistency >= 70) c.success else c.warning,
                    trackColor = c.faint,
                )
                Text(
                    java.lang.String.format(java.util.Locale.US, "%.1f sessions/wk average over %d week%s", sessionsPerWeek, weekCount, if (weekCount != 1) "s" else ""),
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
            }
        }

        // ── Per-day adherence ─────────────────────────────────────────────
        if (perDayStats.isNotEmpty()) {
            item {
                InsightsCard("Sessions by Day") {
                    perDayStats.forEach { (dayName, count) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(dayName, color = c.text, fontSize = IronLogType.body.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = appSpacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                repeat(count.coerceAtMost(8)) {
                                    Box(Modifier.size(8.dp).background(c.accent, CircleShape))
                                }
                                Text("×$count", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Top PRs ───────────────────────────────────────────────────────
        if (prSincePlan.isNotEmpty()) {
            item {
                InsightsCard("Top Lifts (All Time)") {
                    prSincePlan.forEach { (name, weight) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${weight?.let { "%.1f".format(it) }} kg", color = c.accent, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Recommendation ────────────────────────────────────────────────
        item {
            InsightsCard("Recommendation") {
                val recommendation = when {
                    activePlan == null -> "Create or import a plan to receive progression guidance."
                    adherence < 50 -> "Adherence is low. Reduce planned days or shorten sessions to build the habit first."
                    adherence < 85 -> "Stay steady. Focus on showing up consistently before adding volume."
                    consistency >= 80 -> "Consistency is strong. Progress a key lift only when target reps and technique are repeatable; schedule easier work when fatigue is accumulating."
                    else -> "Keep the current structure and use rep quality, performance and recovery trends before changing load or weekly volume."
                }
                Text(recommendation, color = c.subtext, lineHeight = IronLogType.body.lineHeight.sp)
            }
        }
    }
}

// ── Metric computation ────────────────────────────────────────────────────────

internal data class InsightsMetrics(
    val sessionsPerWeek: Double,
    val adherencePct: Int,
    val consistencyPct: Int,
    val weekCount: Int,
)

internal fun computeInsightsMetrics(workouts: List<HistoryEntry>, weeklyGoalDays: Int): InsightsMetrics {
    if (workouts.isEmpty()) return InsightsMetrics(0.0, 0, 0, 0)
    val zone = ZoneId.systemDefault()
    val weekFields = WeekFields.ISO
    val datedWorkouts = workouts.mapNotNull { entry ->
        parseHistoryInstant(entry.date)?.atZone(zone)?.toLocalDate()?.let { it to entry }
    }
    if (datedWorkouts.isEmpty()) return InsightsMetrics(0.0, 0, 0, 0)

    val currentWeekStart = LocalDate.now().with(weekFields.dayOfWeek(), 1L)
    val earliestWeekStart = datedWorkouts.minOf { it.first }.with(weekFields.dayOfWeek(), 1L)
    val rollingWindowStart = currentWeekStart.minusWeeks(11)
    val startWeek = maxOf(earliestWeekStart, rollingWindowStart)
    val weekStarts = generateSequence(startWeek) { previous ->
        previous.plusWeeks(1).takeIf { !it.isAfter(currentWeekStart) }
    }.toList()
    val byWeek = datedWorkouts
        .filter { (date, _) -> !date.isBefore(startWeek) && !date.isAfter(LocalDate.now()) }
        .groupBy { (date, _) -> date.with(weekFields.dayOfWeek(), 1L) }
    val weekCount = weekStarts.size.coerceAtLeast(1)
    val totalSessions = byWeek.values.sumOf { it.size }
    val sessionsPerWeek = totalSessions.toDouble() / weekCount
    val adherence = ((sessionsPerWeek / weeklyGoalDays) * 100.0).toInt().coerceIn(0, 100)
    val weeksHit = weekStarts.count { week -> (byWeek[week]?.size ?: 0) >= weeklyGoalDays }
    val consistency = ((weeksHit.toDouble() / weekCount) * 100.0).roundToInt().coerceIn(0, 100)
    return InsightsMetrics(sessionsPerWeek, adherence, consistency, weekCount)
}

private fun computePerDayAdherence(workouts: List<HistoryEntry>, planDayNames: List<String>): List<Pair<String, Int>> {
    // Count how many times each plan day name appears in workout names
    return planDayNames.map { dayName ->
        val count = workouts.count { it.name.contains(dayName, ignoreCase = true) || it.dayName?.contains(dayName, ignoreCase = true) == true }
        dayName to count
    }.filter { it.first.isNotBlank() }
}

// ── Card composable ───────────────────────────────────────────────────────────

@Composable
private fun InsightsCard(title: String, content: @Composable () -> Unit) {
    val c = useTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
    ) {
        Column(
            Modifier.fillMaxWidth().appPadding(14.dp),
            verticalArrangement = appSpacedBy(8.dp),
        ) {
            Text(title, color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}


