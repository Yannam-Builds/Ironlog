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
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.ironlog.app.domain.intelligence.GeminiNanoEngine
import com.ironlog.app.domain.intelligence.NanoAvailability
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

private object ApexCache {
    private var seenWorkoutId: String? = null
    private val store = mutableMapOf<ApexTab, String>()

    fun invalidateIfNewWorkout(workoutId: String?) {
        if (workoutId != seenWorkoutId) {
            store.clear()
            seenWorkoutId = workoutId
        }
    }

    operator fun get(tab: ApexTab): String? = store[tab]
    operator fun set(tab: ApexTab, value: String) { store[tab] = value }
    fun remove(tab: ApexTab) { store.remove(tab) }
}

// ── Insight tab model ─────────────────────────────────────────────────────────

private enum class ApexTab(val label: String, val eyebrow: String) {
    RECOVERY   ("Recovery",   "TODAY'S RECOMMENDATION"),
    SPLIT      ("Split",      "WEEKLY SPLIT ADVICE"),
    DAY_EVAL   ("Day",        "SESSION EVALUATION"),
    PROGRESSION("Progress",   "PROGRESSION INSIGHT"),
}

// ── Main card ─────────────────────────────────────────────────────────────────

@Composable
fun ApexEngineCard(
    activePlanDay: UiPlanDay?,
    history: List<HistoryEntry>,
    lastWorkoutId: String?,
    goalMode: String,
    sessionsPerWeek: Int,
    adherencePct: Int,
    weeklyGoalDays: Int,
    prTrend: String,
    readiness: Map<String, Double>,
    onSwitchToBuiltin: () -> Unit,
    onOpenTrainingIntelligence: () -> Unit,
) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(ApexTab.RECOVERY) }
    var isLoading by remember { mutableStateOf(false) }
    var currentText by remember { mutableStateOf<String?>(null) }
    var availability by remember { mutableStateOf<NanoAvailability?>(null) }

    // Invalidate cache only when a new workout is logged; navigation-back hits the cache.
    ApexCache.invalidateIfNewWorkout(lastWorkoutId)

    LaunchedEffect(Unit) {
        availability = GeminiNanoEngine.checkAvailability(context)
    }

    LaunchedEffect(activeTab, availability, lastWorkoutId) {
        if (availability != NanoAvailability.SUPPORTED) { isLoading = false; return@LaunchedEffect }
        val cached = ApexCache[activeTab]
        if (cached != null) { currentText = cached; isLoading = false; return@LaunchedEffect }
        isLoading = true
        currentText = null
        val result = fetchInsight(context, activeTab, readiness, history, weeklyGoalDays, goalMode, activePlanDay, prTrend)
        ApexCache[activeTab] = result
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "APEX ENGINE",
                    color = c.accent,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
            }
            Text(
                "On-Device AI",
                color = c.muted,
                fontSize = IronLogType.micro.fontSize.sp,
                letterSpacing = 0.5.sp,
            )
        }

        // ── Tab row ───────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ApexTab.entries.forEach { tab ->
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
            availability == NanoAvailability.UNSUPPORTED -> {
                Text(
                    "APEX ENGINE is not supported on this device. Switch to Built-in Intelligence below.",
                    color = c.muted,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
            availability == NanoAvailability.NEEDS_SYSTEM_UPDATE -> {
                Text(
                    "Gemini Nano hasn't been deployed to your device yet. Check for Google Play system updates, then return here.",
                    color = c.muted,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
            availability == NanoAvailability.NEEDS_DOWNLOAD -> {
                Text(
                    "Gemini Nano model needs a one-time download. Go to Settings → Intelligence Engine to download it.",
                    color = c.muted,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
            isLoading || currentText == null -> {
                Column(
                    Modifier.shimmer(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                    label = "apex_insight",
                ) { text ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            activeTab.eyebrow,
                            color = c.muted,
                            fontSize = IronLogType.micro.fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            text ?: "",
                            color = c.text,
                            fontSize = IronLogType.body.fontSize.sp,
                            lineHeight = IronLogType.body.lineHeight.sp,
                        )
                    }
                }
                Row(
                    Modifier
                        .clickable {
                            ApexCache.remove(activeTab)
                            scope.launch {
                                isLoading = true
                                currentText = null
                                val result = fetchInsight(context, activeTab, readiness, history, weeklyGoalDays, goalMode, activePlanDay, prTrend)
                                ApexCache[activeTab] = result
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
            "$sessionsPerWeek sessions/wk · $adherencePct% adherence · Gemini Nano",
            color = c.subtext,
            fontSize = IronLogType.micro.fontSize.sp,
        )

        // ── Footer links ──────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

// ── Private helper ─────────────────────────────────────────────────────────────

private suspend fun fetchInsight(
    context: android.content.Context,
    tab: ApexTab,
    readiness: Map<String, Double>,
    history: List<HistoryEntry>,
    weeklyGoalDays: Int,
    goalMode: String,
    activePlanDay: UiPlanDay?,
    prTrend: String,
): String = when (tab) {
    ApexTab.RECOVERY    -> GeminiNanoEngine.askRecovery(context, readiness)
    ApexTab.SPLIT       -> GeminiNanoEngine.askSplitSuggestion(context, history, weeklyGoalDays, goalMode)
    ApexTab.DAY_EVAL    -> {
        val exNames = activePlanDay?.exercises?.map { it.name } ?: emptyList()
        val dayName = activePlanDay?.name ?: "your next session"
        GeminiNanoEngine.askDayEvaluation(context, dayName, exNames, goalMode)
    }
    ApexTab.PROGRESSION -> {
        val lastEx = history.firstOrNull()?.exercises?.firstOrNull()
        if (lastEx == null) {
            // No history yet — skip the AI call and surface a clear CTA instead.
            "Log a session to unlock personalised progression advice."
        } else {
            val bestSet = lastEx.sets.filter { it.type != "warmup" }.maxByOrNull { it.weight }
            GeminiNanoEngine.askProgressionExplanation(
                context,
                exerciseName = lastEx.name,
                recentWeightKg = bestSet?.weight ?: 0.0,
                recentReps = bestSet?.reps?.toInt() ?: 0,
                trend = prTrend,
            )
        }
    }
}

