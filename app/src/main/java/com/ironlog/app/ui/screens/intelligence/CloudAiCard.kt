package com.ironlog.app.ui.screens.intelligence

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.domain.intelligence.CloudAiEngine
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.UiPlanDay
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.launch

// ── Process-scoped insight cache ──────────────────────────────────────────────
// Survives navigation (cache lives as long as the process). Cleared only when
// the most-recent workout ID changes — i.e. after a new workout is logged.

private object CloudAiCache {
    private var seenWorkoutId: String? = null
    private val store = mutableMapOf<CloudTab, String>()

    fun invalidateIfNewWorkout(workoutId: String?) {
        if (workoutId != seenWorkoutId) {
            store.clear()
            seenWorkoutId = workoutId
        }
    }

    operator fun get(tab: CloudTab): String? = store[tab]
    operator fun set(tab: CloudTab, value: String) { store[tab] = value }
    fun remove(tab: CloudTab) { store.remove(tab) }
}

// ── Insight tab model ─────────────────────────────────────────────────────────

private enum class CloudTab(val label: String, val eyebrow: String) {
    RECOVERY   ("Recovery",  "TODAY'S RECOMMENDATION"),
    SPLIT      ("Split",     "WEEKLY SPLIT ADVICE"),
    DAY_EVAL   ("Day",       "SESSION EVALUATION"),
    PROGRESSION("Progress",  "PROGRESSION INSIGHT"),
}

// ── Main card ─────────────────────────────────────────────────────────────────

@Composable
fun CloudAiCard(
    activePlanDay: UiPlanDay?,
    history: List<HistoryEntry>,
    lastWorkoutId: String?,
    goalMode: String,
    sessionsPerWeek: Int,
    adherencePct: Int,
    weeklyGoalDays: Int,
    prTrend: String,
    readiness: Map<String, Double>,
    displayName: String,
    modelName: String,
    baseUrl: String,
    apiFormat: String,
    providerPreset: String,
    onSwitchToBuiltin: () -> Unit,
    onOpenTrainingIntelligence: () -> Unit,
) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load the API key for the active provider — lives in EncryptedSharedPreferences, not ViewModel state.
    val apiKey = remember(providerPreset) { CloudAiKeyStore.load(context, providerPreset) }
    val configured = apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()

    var activeTab by remember { mutableStateOf(CloudTab.RECOVERY) }
    var isLoading by remember { mutableStateOf(false) }
    var currentText by remember { mutableStateOf<String?>(null) }

    // Invalidate cache only when a new workout is logged; navigation-back hits the cache.
    CloudAiCache.invalidateIfNewWorkout(lastWorkoutId)

    LaunchedEffect(activeTab, lastWorkoutId) {
        if (!configured) { isLoading = false; return@LaunchedEffect }
        val cached = CloudAiCache[activeTab]
        if (cached != null) { currentText = cached; isLoading = false; return@LaunchedEffect }
        isLoading = true
        currentText = null
        val result = fetchCloudInsight(
            tab = activeTab,
            baseUrl = baseUrl, apiKey = apiKey, modelName = modelName, apiFormat = apiFormat,
            readiness = readiness, history = history, weeklyGoalDays = weeklyGoalDays,
            goalMode = goalMode, activePlanDay = activePlanDay, prTrend = prTrend,
        )
        CloudAiCache[activeTab] = result
        currentText = result
        isLoading = false
    }

    val cardBg = c.accent.copy(alpha = 0.08f)
    val accentBorder = c.accent.copy(alpha = 0.30f)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(cardBg)
            .border(1.dp, accentBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text(
                    displayName.uppercase(),
                    color = c.accent,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
            }
            Text(modelName.ifBlank { "Cloud AI" }, color = c.muted, fontSize = IronLogType.micro.fontSize.sp, letterSpacing = 0.5.sp)
        }

        // ── Tab row ───────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CloudTab.entries.forEach { tab ->
                val selected = tab == activeTab
                Box(
                    Modifier
                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                        .background(if (selected) c.accent.copy(alpha = 0.18f) else c.surface)
                        .border(1.dp, if (selected) c.accent else c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                        .clickable { activeTab = tab }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        tab.label,
                        color = if (selected) c.accent else c.muted,
                        fontSize = IronLogType.micro.fontSize.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        // ── Insight panel ─────────────────────────────────────────────────────
        when {
            !configured -> {
                Text(
                    "Set up your Cloud AI provider in Settings → Intelligence Engine to see insights.",
                    color = c.muted,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
            isLoading || currentText == null -> {
                Column(Modifier.shimmer(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { idx ->
                        Box(
                            Modifier
                                .fillMaxWidth(if (idx == 2) 0.6f else 1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.faint),
                        )
                    }
                }
            }
            else -> {
                AnimatedContent(
                    targetState = currentText,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "cloud_insight",
                ) { text ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            activeTab.eyebrow,
                            color = c.muted,
                            fontSize = IronLogType.micro.fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Text(text ?: "", color = c.text, fontSize = IronLogType.body.fontSize.sp, lineHeight = IronLogType.body.lineHeight.sp)
                    }
                }
                Row(
                    Modifier
                        .clickable {
                            CloudAiCache.remove(activeTab)
                            scope.launch {
                                isLoading = true
                                currentText = null
                                val result = fetchCloudInsight(
                                    tab = activeTab,
                                    baseUrl = baseUrl, apiKey = apiKey, modelName = modelName, apiFormat = apiFormat,
                                    readiness = readiness, history = history, weeklyGoalDays = weeklyGoalDays,
                                    goalMode = goalMode, activePlanDay = activePlanDay, prTrend = prTrend,
                                )
                                CloudAiCache[activeTab] = result
                                currentText = result
                                isLoading = false
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, null, tint = c.muted, modifier = Modifier.size(12.dp))
                    Text("Regenerate", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                }
            }
        }

        // ── Stats row ─────────────────────────────────────────────────────────
        Text(
            "$sessionsPerWeek sessions/wk · $adherencePct% adherence · $displayName",
            color = c.subtext,
            fontSize = IronLogType.micro.fontSize.sp,
        )

        // ── Footer links ──────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Switch to Built-in →",
                color = c.muted,
                fontSize = IronLogType.meta.fontSize.sp,
                modifier = Modifier.clickable(onClick = onSwitchToBuiltin),
            )
            Text(
                "Training Intelligence →",
                color = c.accent,
                fontSize = IronLogType.meta.fontSize.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onOpenTrainingIntelligence),
            )
        }
    }
}

// ── Private helper ────────────────────────────────────────────────────────────

private suspend fun fetchCloudInsight(
    tab: CloudTab,
    baseUrl: String,
    apiKey: String,
    modelName: String,
    apiFormat: String,
    readiness: Map<String, Double>,
    history: List<HistoryEntry>,
    weeklyGoalDays: Int,
    goalMode: String,
    activePlanDay: UiPlanDay?,
    prTrend: String,
): String = when (tab) {
    CloudTab.RECOVERY    -> CloudAiEngine.askRecovery(baseUrl, apiKey, modelName, apiFormat, readiness)
    CloudTab.SPLIT       -> CloudAiEngine.askSplitSuggestion(baseUrl, apiKey, modelName, apiFormat, history, weeklyGoalDays, goalMode)
    CloudTab.DAY_EVAL    -> {
        val exNames = activePlanDay?.exercises?.map { it.name } ?: emptyList()
        val dayName = activePlanDay?.name ?: "your next session"
        CloudAiEngine.askDayEvaluation(baseUrl, apiKey, modelName, apiFormat, dayName, exNames, goalMode)
    }
    CloudTab.PROGRESSION -> {
        val lastEx = history.firstOrNull()?.exercises?.firstOrNull()
        if (lastEx == null) {
            "Log a session to unlock personalised progression advice."
        } else {
            val bestSet = lastEx.sets.filter { it.type != "warmup" }.maxByOrNull { it.weight }
            CloudAiEngine.askProgressionExplanation(
                baseUrl, apiKey, modelName, apiFormat,
                exerciseName = lastEx.name,
                recentWeightKg = bestSet?.weight ?: 0.0,
                recentReps = bestSet?.reps?.toInt() ?: 0,
                trend = prTrend,
            )
        }
    }
}

