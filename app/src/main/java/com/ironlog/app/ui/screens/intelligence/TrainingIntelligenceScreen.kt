package com.ironlog.app.ui.screens.intelligence

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.ironlog.app.domain.intelligence.FINE_MUSCLE_TO_RADAR
import com.ironlog.app.domain.intelligence.TrainingIntelligenceEngine
import com.ironlog.app.domain.intelligence.VolumeLandmark
import com.ironlog.app.domain.intelligence.foldContributions
import com.ironlog.app.domain.intelligence.resolveContribution
import com.ironlog.app.ui.model.HistoryExercise
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
import com.ironlog.app.ui.screens.plans.ProgramRules

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
    val state by vm.state.collectAsState()
    val plans by plansVm.plans.collectAsState()
    val activePlan = plans.firstOrNull { it.isActive }
    val recommendedDayId = remember(activePlan, state.history) {
        recommendedPlanDayId(activePlan, state.history)
    }
    val activeDraftContribution by produceState(initialValue = emptyMap<String, Int>()) {
        value = withContext(Dispatchers.IO) { loadActiveDraftContribution() }
    }
    val snapshot = remember(state.history, state.personalBests.size, activeDraftContribution) {
        TrainingIntelligenceEngine.build(state.history, state.personalBests.size, activeDraftContribution)
    }
    val deloadRules by produceState(initialValue = null as ProgramRules?, activePlan?.id) {
        val planId = activePlan?.id
        if (planId == null) {
            value = null
            return@produceState
        }
        val loadedRules = withContext(Dispatchers.IO) {
            val repo = SettingsRepository()
            val raw = repo.getString("program_rules:$planId")
            if (raw.isNullOrBlank()) null
            else runCatching { tiJson.decodeFromString<ProgramRules>(raw) }.getOrNull()
        }
        value = loadedRules
    }
    val isDeloadWeek = deloadRules?.let { r -> r.deloadEveryWeeks > 0 && r.currentWeek % r.deloadEveryWeeks == 0 } == true

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Training Intelligence", onBack = onBack)
            Spacer(Modifier.height(4.dp))
            Text("TRAINING COACH", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            Text("Intelligence", color = c.text, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight(IronLogType.display.fontWeight), lineHeight = IronLogType.title.lineHeight.sp)
        }
        item {
            IntelligenceCard("Quick Actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntelQuickAction("Start Workout", Modifier.weight(1f)) { onStartWorkout(recommendedDayId) }
                    IntelQuickAction("Recovery Map", Modifier.weight(1f), onOpenRecoveryMap)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntelQuickAction("Create With AI", Modifier.weight(1f), onOpenAIPlan)
                    IntelQuickAction("Program Insights", Modifier.weight(1f), onOpenProgramInsights)
                }
            }
        }
        item { TodayDirectiveCard(snapshot, c) }
        item {
            IntelligenceCard("Weekly Volume by Muscle") {
                Text("Based on the current ISO week (Mon–today).", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                if (snapshot.setsByMuscle.values.all { it == 0 }) {
                    Text("No workouts in the last 30 days.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
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
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Timeline, contentDescription = null, tint = c.accent)
                    Text("${snapshot.prLast30}", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.title.fontSize.sp)
                    Text("PRs in last 30 days", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                }
                val trendColor = when (snapshot.prTrend) {
                    "accelerating" -> c.success; "slowing" -> c.danger; else -> c.muted
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            IntelligenceCard("Neural Fatigue Indicator") {
                val nf = snapshot.neuralFatigue
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        if (nf.isFlagged) Icons.Outlined.Bedtime else Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = if (nf.isFlagged) c.warning else c.success,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (nf.isFlagged) "${nf.consecutiveDays} consecutive heavy days — consider a deload."
                            else "No consecutive heavy load detected. Readiness looks stable.",
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp).navigationBarsPadding())
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
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("• Reduce load to 60% of working weight", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                Text("• Reduce working sets by $setReduction (e.g. 3 → 2)", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                Text("• Keep rep ranges the same", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
            }
        }
    }
}

private suspend fun loadActiveDraftContribution(): Map<String, Int> {
    val repo = SettingsRepository()
    val activeId = repo.getString("active_workout_id").orEmpty()
    if (activeId.isBlank()) return emptyMap()
    val raw = repo.getString("active_workout_draft_$activeId").orEmpty()
    if (raw.isBlank()) return emptyMap()
    val accumulator = linkedMapOf("chest" to 0.0, "back" to 0.0, "arms" to 0.0, "shoulders" to 0.0, "legs" to 0.0, "core" to 0.0)
    return runCatching {
        val json = org.json.JSONObject(raw)
        val setLog = json.optJSONObject("setLog") ?: return@runCatching emptyMap<String, Int>()
        val keys = setLog.keys()
        while (keys.hasNext()) {
            val exName = keys.next()
            val arr = setLog.optJSONArray(exName) ?: continue
            val setCount = (0 until arr.length()).count { i ->
                arr.optJSONObject(i)?.optString("type", "normal") != "warmup"
            }
            if (setCount == 0) continue
            val pseudoEx = HistoryExercise(
                name = exName, sets = emptyList(),
                primaryMuscle = null, primaryMuscles = emptyList(), category = null,
            )
            val contrib = resolveContribution(pseudoEx)
            if (contrib.isNotEmpty()) {
                val radarFold = foldContributions(contrib, FINE_MUSCLE_TO_RADAR)
                radarFold.forEach { (bucket, frac) ->
                    val key = bucket.lowercase()
                    if (key in accumulator) accumulator[key] = accumulator.getValue(key) + setCount * frac
                }
            } else {
                val lower = exName.lowercase()
                val bucket = when {
                    lower.contains("chest") || lower.contains("pec") -> "chest"
                    lower.contains("back") || lower.contains("row") || lower.contains("lat") -> "back"
                    lower.contains("bicep") || lower.contains("tricep") || lower.contains("curl") -> "arms"
                    lower.contains("delt") || lower.contains("shoulder") -> "shoulders"
                    lower.contains("squat") || lower.contains("deadlift") || lower.contains("leg") ||
                    lower.contains("hamstring") || lower.contains("glute") || lower.contains("calf") -> "legs"
                    else -> "core"
                }
                accumulator[bucket] = accumulator.getValue(bucket) + setCount
            }
        }
        accumulator.mapValues { it.value.toInt() }
    }.getOrDefault(emptyMap())
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
            snapshot.volumeLandmarks.any { (_, lm) -> lm.status == "sub_mev" } -> {
                val lagging = snapshot.volumeLandmarks.filter { (_, lm) -> lm.status == "sub_mev" }.keys.firstOrNull() ?: "a muscle group"
                "Train $lagging today" to "Volume is below minimum effective dose — add a session this week."
            }
            snapshot.prTrend == "slowing" ->
                "Focus on progressive overload this week" to
                "PR velocity is slowing vs prior 30 days — push harder on compound lifts."
            else ->
                "You're on track — train as planned" to
                "Volume and recovery indicators are within healthy ranges."
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.accentBorder),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.ironlog.app.ui.theme.IronLogRadius.xl.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(muscle, color = c.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight(600))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${landmark.sets} sets", color = c.text, fontSize = IronLogType.meta.fontSize.sp)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(IronLogRadius.xs.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(landmark.status.uppercase(), color = statusColor, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
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
            Text("MEV ${landmark.min}", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
            Text("Target ${landmark.optimal}", color = c.accent, fontSize = IronLogType.micro.fontSize.sp)
            Text("MRV ${landmark.max}", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
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
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight(IronLogType.section.fontWeight))
            content()
        }
    }
}
