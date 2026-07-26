// app/src/main/java/com/ironlog/app/ui/screens/StatusWindowScreen.kt
package com.ironlog.app.ui.screens.gamification

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
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
import com.ironlog.app.assets.ForgeFoxExpression
import com.ironlog.app.domain.gamification.IronGrade
import com.ironlog.app.domain.gamification.IronGradeGate
import com.ironlog.app.domain.gamification.IronLedgerStats
import com.ironlog.app.ui.components.IronGradeBadge
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
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                DailyProofSection(
                    state = state,
                    onRecoveryCircuitTap = onRecoveryCircuitTap,
                )
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
                    unlockedBadges = state.unlockedBadges,
                    onOpenBrowser = { showGradeBrowser = true },
                )
            }

            item { Spacer(Modifier.height(16.dp).navigationBarsPadding()) }
        }
    }

    if (showGradeBrowser) {
        GradeBrowserDialog(
            currentRank = state.rank,
            onDismiss = { showGradeBrowser = false },
        )
    }
}

@Composable
private fun DailyProofSection(
    state: GamificationUiState,
    onRecoveryCircuitTap: () -> Unit,
) {
    val accent = when (state.dailyProofStatus) {
        com.ironlog.app.domain.gamification.DailyProofStatus.PROOF_LOGGED -> MaterialTheme.colorScheme.primary
        com.ironlog.app.domain.gamification.DailyProofStatus.TRAIN_TODAY -> MaterialTheme.colorScheme.tertiary
        com.ironlog.app.domain.gamification.DailyProofStatus.AT_RISK -> Color(0xFFE57373)
        com.ironlog.app.domain.gamification.DailyProofStatus.RECOVER_SMART -> Color(0xFF7EC8B8)
        com.ironlog.app.domain.gamification.DailyProofStatus.ACTIVE_WORKOUT -> Color(0xFFB39DDB)
        com.ironlog.app.domain.gamification.DailyProofStatus.SETUP -> MaterialTheme.colorScheme.secondary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
                ) {
                    Text(
                        text = "${state.dailyStreakDays} day streak",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = state.dailyProofPrimaryActionLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!state.latestBadgeTitle.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = state.latestBadgeTitle,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                TextButton(onClick = onRecoveryCircuitTap) {
                    Text("Recovery circuit")
                }
            }
            Image(
                painter = painterResource(ForgeFoxExpression.fromId(state.foxExpressionId).drawableRes),
                contentDescription = state.dailyProofHeadline,
                modifier = Modifier.size(132.dp),
                contentScale = ContentScale.Fit,
            )
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        IronGradeBadge(
            rank = rank,
            accent = rankColor,
            modifier = Modifier
                .size(108.dp)
                .clickable(onClick = onBadgeClick),
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = rankColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, rankColor.copy(alpha = 0.52f)),
        ) {
            Text(
                text = "$rank Grade",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = rankColor,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Level $level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Integrity ${(integrityScore * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
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
        Spacer(Modifier.height(4.dp))
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .padding(top = 5.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(Modifier.weight(1f)) {
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
private fun StreakSection(streakWeeks: Int, onRecoveryCircuitTap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "FC",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$streakWeeks qualifying weeks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("Ledger weeks that met your training standard", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRecoveryCircuitTap) {
                Text("Recovery circuit")
            }
        }
    }
}

@Composable
private fun TrainingSignalsSection(stats: IronLedgerStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Training Signals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SignalRow("STR", "Strength", stats.strength, MaterialTheme.colorScheme.primary)
            SignalRow("PWR", "Power", stats.power, MaterialTheme.colorScheme.tertiary)
            SignalRow("HYP", "Hypertrophy", stats.hypertrophy, MaterialTheme.colorScheme.secondary)
            SignalRow("END", "Endurance", stats.endurance, MaterialTheme.colorScheme.primary)
            SignalRow("AGI", "Agility", stats.agility, MaterialTheme.colorScheme.secondary)
            SignalRow("DSC", "Discipline", stats.discipline, MaterialTheme.colorScheme.tertiary)
            SignalRow("REC", "Recovery", stats.recovery, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SignalRow(code: String, label: String, value: Int, accent: Color) {
    val normalized = (value.coerceIn(1, 100) / 100f).coerceAtLeast(0.03f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun NextGradeSection(
    nextGradeLabel: String?,
    gates: List<IronGradeGate>,
    totalXp: Long,
) {
    val c = useTheme()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun BadgeShelf(unlockedBadges: List<String>, onOpenBrowser: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenBrowser) { Text("View all") }
        }
        Spacer(Modifier.height(8.dp))
        if (unlockedBadges.isEmpty()) {
            Text(
                "No badges unlocked yet. Tap View all to inspect locked grades and requirements.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(unlockedBadges) { badge ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .width(132.dp)
                            .clickable(onClick = onOpenBrowser),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                badge.take(2).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                badge.replace('_', ' ')
                                    .split(' ')
                                    .joinToString(" ") { token -> token.replaceFirstChar(Char::titlecase) },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeBrowserDialog(currentRank: String, onDismiss: () -> Unit) {
    val c = useTheme()
    val current = IronGrade.entries.firstOrNull { it.label == currentRank } ?: IronGrade.UNCALIBRATED
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Grade Atlas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Swipe sideways. Locked grades show the exact proof required.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(IronGrade.entries) { grade ->
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
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
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

