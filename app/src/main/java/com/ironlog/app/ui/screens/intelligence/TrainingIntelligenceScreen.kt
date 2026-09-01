package com.ironlog.app.ui.screens.intelligence

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.intelligence.TrainingIntelligenceEngine
import com.ironlog.app.domain.intelligence.TrainingIntelligenceProfile
import com.ironlog.app.domain.intelligence.VolumeLandmark
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.StatsViewModel
import com.ironlog.app.ui.viewmodel.PlansViewModel
import com.ironlog.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.ironlog.app.domain.intelligence.ProgramRules
import com.ironlog.app.data.repository.programProgressionRulesKey

private val tiJson = Json { ignoreUnknownKeys = true }

@Composable
fun TrainingIntelligenceScreen(
    vm: StatsViewModel = viewModel(),
    plansVm: PlansViewModel = viewModel(),
    onBack: () -> Unit = {},
    onStartWorkout: (dayId: String?) -> Unit = {},
    onOpenRecoveryMap: () -> Unit = {},
    onOpenAIPlan: () -> Unit = {},
    onOpenProgramInsights: () -> Unit = {},
) {
    val c = useTheme()
    val state by vm.state.collectAsStateWithLifecycle()
    val plans by plansVm.plans.collectAsStateWithLifecycle()
    val activePlan = plans.firstOrNull { it.isActive }
    val recommendedDayId = remember(activePlan, state.history) {
        recommendedPlanDayId(activePlan, state.history)
    }
    val intelligenceProfile by produceState(initialValue = TrainingIntelligenceProfile()) {
        value = withContext(Dispatchers.IO) {
            val raw = SettingsRepository().getString("ironlog_settings").orEmpty()
            val settings = runCatching { org.json.JSONObject(raw) }.getOrDefault(org.json.JSONObject())
            TrainingIntelligenceProfile(
                goalMode = settings.optString("goalMode", "hypertrophy"),
                weeklyGoalDays = settings.optInt("weeklyGoalDays", 3).coerceIn(1, 7),
            )
        }
    }
    val snapshot = remember(state.history, state.personalBests.size, state.prResetAtEpochMs, intelligenceProfile) {
        TrainingIntelligenceEngine.build(
            history = state.history,
            prCount = state.personalBests.size,
            profile = intelligenceProfile,
            prResetAt = state.prResetAtEpochMs?.let(java.time.Instant::ofEpochMilli),
        )
    }
    val deloadRules by produceState(initialValue = null as ProgramRules?, activePlan?.id) {
        val planId = activePlan?.id
        if (planId == null) {
            value = null
            return@produceState
        }
        val loadedRules = withContext(Dispatchers.IO) {
            val repo = SettingsRepository()
            val raw = repo.getString(programProgressionRulesKey(planId))
            if (raw.isNullOrBlank()) null
            else runCatching { tiJson.decodeFromString<ProgramRules>(raw) }.getOrNull()
        }
        value = loadedRules
    }
    val isDeloadWeek = deloadRules?.let { r -> r.deloadEveryWeeks > 0 && r.currentWeek % r.deloadEveryWeeks == 0 } == true

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Training Intelligence", onBack = onBack)
            Spacer(Modifier.height(appGapDp(4.dp)))
            Text("TRAINING COACH", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            Text("Intelligence", color = c.text, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight(IronLogType.display.fontWeight), lineHeight = IronLogType.title.lineHeight.sp)
        }
        item {
            IntelligenceCard("Quick Actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = appSpacedBy(8.dp)) {
                    IntelQuickAction("Start Workout", Modifier.weight(1f)) { onStartWorkout(recommendedDayId) }
                    IntelQuickAction("Recovery Map", Modifier.weight(1f), onOpenRecoveryMap)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = appSpacedBy(8.dp)) {
                    IntelQuickAction("Create With AI", Modifier.weight(1f), onOpenAIPlan)
                    IntelQuickAction("Program Insights", Modifier.weight(1f), onOpenProgramInsights)
                }
            }
        }
        item { TodayDirectiveCard(snapshot, c) }
        item {
            IntelligenceCard("Weekly muscle exposure") {
                Text("Completed workouts only · weighted set equivalents, Mon–today. Reference bands are coaching estimates, not personal minimums. A partial week is not a deficit.", color = c.subtext, fontSize = 12.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(appGapDp(4.dp)))
                if (snapshot.setsByMuscle.values.all { it == 0 }) {
                    Text("No mapped working sets this week yet.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                } else {
                    snapshot.volumeLandmarks.forEach { (muscle, landmark) ->
                        VolumeLandmarkRow(muscle = muscle, landmark = landmark)
                    }
                }
            }
        }
        if (isDeloadWeek) {
            item { DeloadRecommendationCard(deloadRules!!, c) }
        }
        item {
            IntelligenceCard("Movement Balance") {
                if (snapshot.movementBalance.values.all { it == 0 }) {
                    Text("No data yet.", color = c.muted)
                } else {
                    snapshot.movementBalance.forEach { (k, pct) ->
                        Row(Modifier.fillMaxWidth().appPadding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(k, color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(0.3f))
                            Box(
                                Modifier
                                    .weight(0.55f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.faint),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(pct / 100f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                        .background(c.chartPrimary),
                                )
                            }
                            Text("$pct%", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.weight(0.15f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
        }
        item {
            IntelligenceCard("PR Velocity (30D)") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = appSpacedBy(8.dp)) {
                    Icon(Icons.Outlined.Timeline, contentDescription = null, tint = c.accent)
                    Text("${snapshot.prLast30}", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.title.fontSize.sp)
                    Text("PRs in last 30 days", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                }
                val trendColor = when (snapshot.prTrend) {
                    "accelerating" -> c.success; "slowing" -> c.danger; else -> c.muted
                }
                Row(horizontalArrangement = appSpacedBy(6.dp)) {
                    Text(
                        snapshot.prTrend.replaceFirstChar { it.titlecase() },
                        color = trendColor,
                        fontSize = IronLogType.meta.fontSize.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("vs ${snapshot.prPrev30} in prior 30 days", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                }
            }
        }
        item {
            IntelligenceCard("Heavy-load density") {
                val nf = snapshot.neuralFatigue
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = appSpacedBy(10.dp)) {
                    Icon(
                        if (nf.isFlagged) Icons.Outlined.Bedtime else Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = if (nf.isFlagged) c.warning else c.success,
                    )
                    Column(verticalArrangement = appSpacedBy(4.dp)) {
                        Text(
                            if (nf.isFlagged) "${nf.consecutiveDays} consecutive recent heavy days — consider easier loading or recovery."
                            else "No recent cluster of heavy compound days detected.",
                            color = c.text,
                            fontSize = IronLogType.body.fontSize.sp,
                        )
                        if (nf.isFlagged && nf.lastHeavyExercises.isNotEmpty()) {
                            Text(
                                nf.lastHeavyExercises.joinToString(", "),
                                color = c.muted,
                                fontSize = IronLogType.meta.fontSize.sp,
                            )
                        }
                    }
                }
                if (nf.isFlagged) {
                    TextButton(onClick = onOpenRecoveryMap, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "VIEW RECOVERY MAP",
                            color = c.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = IronLogType.eyebrow.fontSize.sp,
                            letterSpacing = 1.2.sp,
                        )
                    }
                }
            }
        }
        item {
            IntelligenceCard("Best Performance Window") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = appSpacedBy(8.dp)) {
                    Icon(Icons.Outlined.SelfImprovement, contentDescription = null, tint = c.accent)
                    Text(snapshot.bestWindow, color = c.text, fontSize = IronLogType.body.fontSize.sp)
                }
            }
        }
        item {
            IntelligenceCard("Training Age") {
                Text(String.format(java.util.Locale.US, "%.1f years", snapshot.trainingAgeYears), color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.title.fontSize.sp)
                Text(snapshot.trainingAgeLabel, color = c.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight(600))
                Text(snapshot.trainingAgeTip, color = c.muted, fontSize = IronLogType.body.fontSize.sp)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(appGapDp(16.dp)).navigationBarsPadding())
        }
    }
}

internal fun recommendedPlanDayId(activePlan: UiPlan?, history: List<HistoryEntry>): String? {
    val days = activePlan?.days.orEmpty()
    if (days.isEmpty()) return null
    val dayIds = days.map { it.id }
    val lastCompletedDay = history
        .sortedByDescending { it.date }
        .firstOrNull { it.planDayUid in dayIds }
        ?.planDayUid
    val lastIndex = dayIds.indexOf(lastCompletedDay)
    return if (lastIndex >= 0) dayIds[(lastIndex + 1) % dayIds.size] else dayIds.first()
}

@Composable
private fun DeloadRecommendationCard(rules: ProgramRules, c: IronLogThemeTokens) {
    val setReduction = "~40%"
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.warning.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().appPadding(14.dp), verticalArrangement = appSpacedBy(8.dp)) {
            Text(
                "DELOAD WEEK",
                color = c.warning,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
            Text(
                "Week ${rules.currentWeek} — Scheduled deload",
                color = c.text,
                fontWeight = FontWeight.Bold,
                fontSize = IronLogType.section.fontSize.sp,
            )
            Column(verticalArrangement = appSpacedBy(4.dp)) {
                Text("• Reduce load to 60% of working weight", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                Text("• Reduce working sets by $setReduction (e.g. 3 → 2)", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                Text("• Keep rep ranges the same", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
            }
        }
    }
}

@Composable
private fun TodayDirectiveCard(
    snapshot: com.ironlog.app.domain.intelligence.TrainingIntelligenceSnapshot,
    c: com.ironlog.app.ui.theme.IronLogThemeTokens,
) {
    // Build directive from snapshot data
    val (directive, reason) = remember(snapshot) {
        when {
            snapshot.neuralFatigue.isFlagged ->
                "Rest or light cardio today" to
                "${snapshot.neuralFatigue.consecutiveDays} consecutive heavy days detected — recovery first."
            snapshot.setsByMuscle.values.all { it == 0 } ->
                "Follow your next planned session" to "No mapped working sets this week yet. Build a consistent log before adjusting your program."
            snapshot.prTrend == "slowing" ->
                "Review progression inputs this week" to
                "PR frequency is lower than the prior 30 days; check technique, rep quality, recovery and load selection before adding stress."
            else ->
                "You're on track — train as planned" to
                "Use your plan, technique and current check-in. Review a complete week before changing volume."
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.accentBorder),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.ironlog.app.ui.theme.IronLogRadius.xl.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().appPadding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = appSpacedBy(6.dp),
        ) {
            Text(
                "TODAY",
                color = c.accent,
                fontSize = com.ironlog.app.ui.theme.IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(com.ironlog.app.ui.theme.IronLogType.eyebrow.fontWeight),
                letterSpacing = com.ironlog.app.ui.theme.IronLogType.eyebrow.letterSpacing.sp,
            )
            Text(
                directive,
                color = c.text,
                fontSize = com.ironlog.app.ui.theme.IronLogType.section.fontSize.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                reason,
                color = c.subtext,
                fontSize = com.ironlog.app.ui.theme.IronLogType.body.fontSize.sp,
            )
        }
    }
}

@Composable
private fun VolumeLandmarkRow(muscle: String, landmark: VolumeLandmark) {
    val c = useTheme()
    val statusColor = when (landmark.status) {
        "optimal" -> c.success
        "high" -> c.warning
        else -> c.muted
    }
    val barMax = (landmark.max + 4).coerceAtLeast(landmark.sets + 2)
    val optMinFrac = landmark.min.toFloat() / barMax
    val optMaxFrac = landmark.max.toFloat() / barMax
    val fillFrac = (landmark.sets.toFloat() / barMax).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth().appPadding(vertical = 4.dp), verticalArrangement = appSpacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(muscle, color = c.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight(600))
            Row(horizontalArrangement = appSpacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("~${landmark.sets} eq.", color = c.text, fontSize = 12.sp)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(IronLogRadius.xs.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .appPadding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(when (landmark.status) { "optimal" -> "IN BAND"; "high" -> "ABOVE"; else -> "SO FAR" }, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // Zone bar: track â†’ optimal zone highlight â†’ fill bar
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(IronLogRadius.full.dp)).background(c.faint)) {
            // Green optimal zone backdrop
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    val zoneLeft = optMinFrac * size.width
                    val zoneWidth = (optMaxFrac - optMinFrac) * size.width
                    drawRect(
                        color = c.success.copy(alpha = 0.12f),
                        topLeft = androidx.compose.ui.geometry.Offset(zoneLeft, 0f),
                        size = androidx.compose.ui.geometry.Size(zoneWidth, size.height),
                    )
                }
            }
            // Filled sets bar
            Box(
                Modifier
                    .fillMaxWidth(fillFrac)
                    .height(8.dp)
                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                    .background(statusColor.copy(alpha = 0.75f)),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reference ${landmark.min}–${landmark.max} eq.", color = c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun IntelQuickAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = useTheme()
    Box(
        modifier = modifier
            .background(c.surface, RoundedCornerShape(IronLogRadius.md.dp))
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.md.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = c.accent,
            fontSize = IronLogType.meta.fontSize.sp,
            fontWeight = FontWeight(IronLogType.button.fontWeight),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun IntelligenceCard(title: String, content: @Composable () -> Unit) {
    val c = useTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.cardBorder),
        shape = RoundedCornerShape(IronLogRadius.lg.dp),
    ) {
        Column(Modifier.fillMaxWidth().appPadding(14.dp), verticalArrangement = appSpacedBy(8.dp)) {
            Text(title, color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight(IronLogType.section.fontWeight))
            content()
        }
    }
}
