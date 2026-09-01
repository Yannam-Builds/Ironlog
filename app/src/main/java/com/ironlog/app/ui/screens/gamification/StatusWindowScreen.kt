// app/src/main/java/com/ironlog/app/ui/screens/StatusWindowScreen.kt
package com.ironlog.app.ui.screens.gamification
import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.ironlog.app.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironlog.app.R
import com.ironlog.app.assets.ForgeFoxExpression
import com.ironlog.app.domain.badges.BadgeDefinitions
import com.ironlog.app.domain.gamification.IronGrade
import com.ironlog.app.domain.gamification.IronGradeGate
import com.ironlog.app.domain.gamification.IronLedgerStats
import com.ironlog.app.ui.components.IronGradeBadge
import com.ironlog.app.ui.components.AchievementBadge
import com.ironlog.app.ui.components.badgeTierColor
import com.ironlog.app.ui.components.ironGradeColor
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.viewmodel.GamificationUiState
import com.ironlog.app.ui.viewmodel.XpLogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusWindowScreen(
    state: GamificationUiState,
    onBack: () -> Unit,
    onRecoveryCircuitTap: () -> Unit,
    onDailyProofAction: () -> Unit = onBack,
    onRetryRefresh: () -> Unit = {},
) {
    val rankColor = ironGradeColor(state.rank)
    var showGradeBrowser by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iron Ledger", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .appPadding(padding)
                .appPadding(horizontal = 16.dp),
            verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(16.dp),
        ) {
            state.refreshError?.let { message ->
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetryRefresh) { Text("Retry refresh") }
                    }
                }
            }
            // Rank badge + level
            item {
                GradeBadgeSection(
                    rank = state.rank,
                    rankColor = rankColor,
                    level = state.level,
                    title = state.activeTitle,
                    integrityScore = state.integrityScore,
                    onBadgeClick = { showGradeBrowser = true },
                )
            }

            item {
                DailyProofSection(state = state, onPrimaryAction = onDailyProofAction)
            }

            // XP progress bar
            item {
                XpProgressSection(
                    totalXp = state.totalXp,
                    xpInLevel = state.xpInLevel,
                    xpForNextLevel = state.xpForNextLevel,
                    level = state.level,
                )
            }

            item {
                XpLogSection(logs = state.xpLogs)
            }

            // Streak
            item {
                StreakSection(
                    streakWeeks = state.streakWeeks,
                    recoveryCompleted = state.recoveryCircuitCompletedThisWeek,
                    onRecoveryCircuitTap = onRecoveryCircuitTap,
                )
            }

            item {
                NextGradeSection(state.nextGradeLabel, state.nextGradeGates, state.totalXp)
            }

            item {
                TrainingSignalsSection(stats = state.ledgerStats)
            }

            item {
                BadgeShelf(
                    currentRank = state.rank,
                    unlockedBadges = state.unlockedBadges,
                    onOpenBrowser = { showGradeBrowser = true },
                )
            }

            item { Spacer(Modifier.height(appGapDp(16.dp)).navigationBarsPadding()) }
        }
    }

    if (showGradeBrowser) {
        GradeBrowserDialog(
            currentRank = state.rank,
            unlockedBadges = state.unlockedBadges,
            onDismiss = { showGradeBrowser = false },
        )
    }
}

@Composable
private fun DailyProofSection(
    state: GamificationUiState,
    onPrimaryAction: () -> Unit,
) {
    val accent = when (state.dailyProofStatus) {
        com.ironlog.app.domain.gamification.DailyProofStatus.PROOF_LOGGED -> MaterialTheme.colorScheme.primary
        com.ironlog.app.domain.gamification.DailyProofStatus.TRAIN_TODAY -> MaterialTheme.colorScheme.tertiary
        com.ironlog.app.domain.gamification.DailyProofStatus.AT_RISK -> Color(0xFFE57373)
        com.ironlog.app.domain.gamification.DailyProofStatus.RECOVER_SMART -> Color(0xFF7EC8B8)
        com.ironlog.app.domain.gamification.DailyProofStatus.ACTIVE_WORKOUT -> Color(0xFFB39DDB)
        com.ironlog.app.domain.gamification.DailyProofStatus.SETUP -> MaterialTheme.colorScheme.secondary
        com.ironlog.app.domain.gamification.DailyProofStatus.FIRST_PROOF -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appPadding(16.dp),
            verticalArrangement = appSpacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
                ) {
                    Text(
                        text = "${state.dailyStreakDays} day streak",
                        modifier = Modifier.appPadding(horizontal = 12.dp, vertical = 6.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(ForgeFoxExpression.fromId(state.foxExpressionId).drawableRes),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = state.dailyProofHeadline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.dailyProofDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPrimaryAction),
            ) {
                Text(
                    text = state.dailyProofPrimaryActionLabel,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }
            if (!state.latestBadgeTitle.isNullOrBlank()) {
                Text(
                    text = "Latest milestone · ${state.latestBadgeTitle}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GradeBadgeSection(
    rank: String,
    rankColor: Color,
    level: Int,
    title: String,
    integrityScore: Double,
    onBadgeClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onBadgeClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = appSpacedBy(16.dp),
        ) {
            IronGradeBadge(
                rank = rank,
                accent = rankColor,
                modifier = Modifier.size(88.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = appSpacedBy(4.dp)) {
                Text("CURRENT GRADE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$rank · Level $level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = rankColor)
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Integrity ${(integrityScore * 100).toInt()}%  ·  Open achievement atlas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun XpProgressSection(totalXp: Long, xpInLevel: Long, xpForNextLevel: Long, level: Int) {
    val fraction = if (xpForNextLevel > 0) (xpInLevel.toFloat() / xpForNextLevel.toFloat()) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "xpBar",
    )
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("XP - $totalXp all-time", style = MaterialTheme.typography.labelMedium)
            Text("$xpInLevel / $xpForNextLevel", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(appGapDp(4.dp)))
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
        )
        Text(
            "Next level: ${level + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun XpLogSection(logs: List<XpLogEntry>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.appPadding(16.dp), verticalArrangement = appSpacedBy(10.dp)) {
            Text("Ledger Event Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (logs.isEmpty()) {
                Text(
                    "No ledger events yet. Finish a qualifying workout to start building your proof trail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                logs.take(20).forEach { log ->
                    XpLogRow(log)
                }
            }
        }
    }
}

@Composable
private fun XpLogRow(log: XpLogEntry) {
    val c = useTheme()
    val accent = if (log.kind == "level" || log.kind == "gate") c.gold else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = appSpacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .padding(top = 5.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(Modifier.weight(1f), verticalArrangement = appSpacedBy(3.dp)) {
            Text(log.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                log.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (log.xp > 0) "+${log.xp} XP" else log.kind.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StreakSection(
    streakWeeks: Int,
    recoveryCompleted: Boolean,
    onRecoveryCircuitTap: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().appPadding(16.dp),
            verticalArrangement = appSpacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = appSpacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.recovery_circuit_emblem),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "$streakWeeks qualifying weeks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Weeks that met your personal training target",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !recoveryCompleted, onClick = onRecoveryCircuitTap),
            ) {
                Text(
                    if (recoveryCompleted) "Recovery proof saved" else "Open Recovery Circuit",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (recoveryCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun TrainingSignalsSection(stats: IronLedgerStats) {
    val values = listOf(stats.strength, stats.power, stats.hypertrophy, stats.endurance, stats.agility, stats.discipline, stats.recovery)
    val scale = trainingSignalScale(values)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.appPadding(16.dp),
            verticalArrangement = appSpacedBy(12.dp),
        ) {
            Text(
                "Training Signals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Relative profile · shared scale 0–$scale",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignalRow("STR", "Strength", stats.strength, scale, MaterialTheme.colorScheme.primary)
            SignalRow("PWR", "Power", stats.power, scale, MaterialTheme.colorScheme.tertiary)
            SignalRow("HYP", "Hypertrophy", stats.hypertrophy, scale, MaterialTheme.colorScheme.secondary)
            SignalRow("END", "Endurance", stats.endurance, scale, MaterialTheme.colorScheme.primary)
            SignalRow("AGI", "Agility", stats.agility, scale, MaterialTheme.colorScheme.secondary)
            SignalRow("DSC", "Discipline", stats.discipline, scale, MaterialTheme.colorScheme.tertiary)
            SignalRow("REC", "Recovery", stats.recovery, scale, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SignalRow(code: String, label: String, value: Int, scale: Int, accent: Color) {
    val normalized = (value.coerceAtLeast(0).toFloat() / scale.coerceAtLeast(1).toFloat()).coerceIn(0.02f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = appSpacedBy(10.dp),
    ) {
        Text(
            code,
            modifier = Modifier.width(44.dp),
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.labelLarge,
        )
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { normalized },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Text(
            value.toString(),
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

internal fun trainingSignalScale(values: List<Int>): Int {
    val largest = maxOf(100, values.maxOrNull() ?: 0)
    return (((largest.toLong() + 24L) / 25L) * 25L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

@Composable
private fun NextGradeSection(
    nextGradeLabel: String?,
    gates: List<IronGradeGate>,
    totalXp: Long,
) {
    val c = useTheme()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.appPadding(16.dp), verticalArrangement = appSpacedBy(8.dp)) {
            Text("Next Grade", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                nextGradeLabel ?: if (totalXp <= 0L) "Start with first workout" else "Apex maintained",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (gates.isEmpty()) {
                Text(
                    if (totalXp <= 0L) "Finish a qualifying workout to unlock your first grade gate."
                    else "No remaining gates.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                gates.forEach { gate ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(gate.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (gate.met) "Met" else "${gate.current}/${gate.required}",
                            color = if (gate.met) c.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeShelf(currentRank: String, unlockedBadges: List<String>, onOpenBrowser: () -> Unit) {
    val current = IronGrade.entries.firstOrNull { it.label.equals(currentRank, ignoreCase = true) } ?: IronGrade.UNCALIBRATED
    val grades = IronGrade.entries.filter { it != IronGrade.UNCALIBRATED && it.ordinal <= current.ordinal }
    val achievements = BadgeDefinitions.all.filter { it.id in unlockedBadges }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenBrowser) { Text("View all") }
        }
        Spacer(Modifier.height(appGapDp(8.dp)))
        if (grades.isEmpty() && achievements.isEmpty()) {
            Text(
                "No badges unlocked yet. Tap View all to inspect locked grades and requirements.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            if (grades.isNotEmpty()) {
                Text("Earned grades", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(appGapDp(8.dp)))
                LazyRow(horizontalArrangement = appSpacedBy(10.dp)) {
                    items(grades) { grade -> GradeShelfTile(grade, onOpenBrowser) }
                }
            }
            if (achievements.isNotEmpty()) {
                Spacer(Modifier.height(appGapDp(14.dp)))
                Text("Achievements", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(appGapDp(8.dp)))
                LazyRow(horizontalArrangement = appSpacedBy(10.dp)) {
                    items(achievements) { badge -> AchievementShelfTile(badge, true, onOpenBrowser) }
                }
            }
        }
    }
}

@Composable
private fun GradeShelfTile(grade: IronGrade, onClick: () -> Unit) {
    val accent = ironGradeColor(grade.label)
    Column(
        modifier = Modifier.width(104.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = appSpacedBy(6.dp),
    ) {
        IronGradeBadge(rank = grade.label, accent = accent, modifier = Modifier.size(68.dp))
        Text(grade.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun AchievementShelfTile(
    badge: com.ironlog.app.domain.badges.BadgeDefinition,
    unlocked: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(104.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = appSpacedBy(6.dp),
    ) {
        AchievementBadge(definition = badge, unlocked = unlocked, modifier = Modifier.size(64.dp))
        Text(badge.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GradeBrowserDialog(
    currentRank: String,
    unlockedBadges: List<String>,
    onDismiss: () -> Unit,
) {
    val c = useTheme()
    val current = IronGrade.entries.firstOrNull { it.label == currentRank } ?: IronGrade.UNCALIBRATED
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.appPadding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = appSpacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Achievement Atlas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Swipe sideways to inspect every grade and app badge.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyRow(horizontalArrangement = appSpacedBy(12.dp)) {
                    items(IronGrade.entries.filter { it != IronGrade.UNCALIBRATED }) { grade ->
                        val unlocked = current.ordinal >= grade.ordinal
                        val accent = ironGradeColor(grade.label)
                        Card(
                            modifier = Modifier.width(240.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (unlocked) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            ),
                            border = BorderStroke(1.dp, if (unlocked) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        ) {
                            Column(
                                modifier = Modifier.appPadding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = appSpacedBy(10.dp),
                            ) {
                                IronGradeBadge(
                                    rank = grade.label,
                                    accent = accent,
                                    modifier = Modifier.size(84.dp),
                                )
                                Text(
                                    grade.label,
                                    color = if (unlocked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    if (unlocked) "Unlocked" else "Locked",
                                    color = if (unlocked) c.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                GradeRequirement("Verified sessions", grade.minVerifiedSessions)
                                GradeRequirement("Qualifying weeks", grade.minQualifyingWeeks)
                                GradeRequirement("Training tenure", "${grade.minTenureDays} days")
                                if (grade.ordinal >= IronGrade.OBSIDIAN.ordinal) {
                                    GradeRequirement("Integrity", "88%+")
                                }
                                if (grade.ordinal >= IronGrade.APEX.ordinal) {
                                    GradeRequirement("Discipline window", "4+ years")
                                }
                            }
                        }
                    }
                }
                Text("App badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = appSpacedBy(12.dp)) {
                    items(BadgeDefinitions.all) { badge ->
                        val unlocked = badge.id in unlockedBadges
                        Card(
                            modifier = Modifier.width(220.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (unlocked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.appPadding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = appSpacedBy(8.dp),
                            ) {
                                AchievementBadge(
                                    definition = badge,
                                    unlocked = unlocked,
                                    modifier = Modifier.size(72.dp),
                                )
                                Text(
                                    badge.tier.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeTierColor(badge.tier),
                                    fontWeight = FontWeight.Black,
                                )
                                Text(badge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (unlocked) "Unlocked" else "Locked",
                                    color = if (unlocked) c.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    badge.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeRequirement(label: String, value: Any) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

