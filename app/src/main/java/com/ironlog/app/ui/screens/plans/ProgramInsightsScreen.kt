package com.ironlog.app.ui.screens.plans

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import java.time.Instant
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
    val state by vm.state.collectAsState()

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    "Based on $weekCount week${if (weekCount != 1) "s" else ""} of data · %.1f sessions/wk average".format(sessionsPerWeek),
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
                    "%.1f sessions/wk average over $weekCount week${if (weekCount != 1) "s" else ""}".format(sessionsPerWeek),
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
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    consistency >= 80 -> "Excellent consistency! Consider a progressive overload block or deload week."
                    else -> "Good work. Increase one key lift by 2.5–5% next week and track the response."
                }
                Text(recommendation, color = c.subtext, lineHeight = IronLogType.body.lineHeight.sp)
            }
        }
    }
}

// ── Metric computation ────────────────────────────────────────────────────────

private data class InsightsMetrics(
    val sessionsPerWeek: Double,
    val adherencePct: Int,
    val consistencyPct: Int,
    val weekCount: Int,
)

private fun computeInsightsMetrics(workouts: List<HistoryEntry>, weeklyGoalDays: Int): InsightsMetrics {
    if (workouts.isEmpty()) return InsightsMetrics(0.0, 0, 0, 0)
    val zone = ZoneId.systemDefault()
    val weekFields = WeekFields.ISO
    // Group sessions by ISO week key
    val byWeek = workouts.groupBy { entry ->
        // Use tolerant two-step parse so bare YYYY-MM-DD dates (e.g. from widget
        // or imported data) don't throw an unchecked DateTimeParseException and
        // crash the entire composable.
        val d = runCatching { Instant.parse(entry.date).atZone(zone).toLocalDate() }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(entry.date.substringBefore('T')) }.getOrNull()
            ?: return@groupBy "unknown"
        "${d.get(weekFields.weekBasedYear())}-W${d.get(weekFields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
    }.filter { (k, _) -> k != "unknown" }
    val weekCount = max(1, byWeek.size)
    val totalSessions = workouts.size
    val sessionsPerWeek = totalSessions.toDouble() / weekCount
    val adherence = ((sessionsPerWeek / weeklyGoalDays) * 100.0).toInt().coerceIn(0, 100)
    // Consistency: % of weeks where user hit their goal
    val weeksHit = byWeek.values.count { it.size >= weeklyGoalDays }
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
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}


