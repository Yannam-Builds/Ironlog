package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.ParticleField
import com.ironlog.app.ui.screens.onboarding.SetupBadge
import kotlin.math.max
import kotlinx.coroutines.delay

@Composable
fun Step8Complete(
    userName: String,
    weeklyGoalDays: Int,
    weightUnit: String,
    goalMode: String,
    progressionStyle: String,
    intelligenceMode: String,
    healthConnectGranted: Boolean,
    notificationsGranted: Boolean,
    qualifiedBadge: String,
    onStartTraining: () -> Unit,
) {
    var showContent by remember { mutableStateOf(true) }
    var revealDone by remember { mutableStateOf(false) }
    var slotBadge by remember { mutableStateOf("Uncalibrated") }
    val badgeOrder = remember {
        listOf("Uncalibrated", "Graphite", "Iron", "Steel", "Titanium", "Obsidian", "Iridium", "Aether", "Apex")
    }
    LaunchedEffect(Unit) {
        // Content is visible immediately; the qualification badge still animates.
    }
    LaunchedEffect(showContent, qualifiedBadge) {
        if (!showContent) return@LaunchedEffect
        val targetIndex = badgeOrder.indexOf(qualifiedBadge).coerceAtLeast(0)
        val totalTicks = max(16, targetIndex + 12)
        for (i in 0 until totalTicks) {
            slotBadge = badgeOrder[i % badgeOrder.size]
            delay((55L + (i * 5L)).coerceAtMost(170L))
        }
        slotBadge = qualifiedBadge
        revealDone = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark),
    ) {
        ParticleField()

        if (showContent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 64.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SetupBadge(
                    code = slotBadge.take(2).uppercase(),
                    accent = OnboardingConfig.accentBlue,
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (revealDone) "Qualified badge: $slotBadge" else "Evaluating your badge...",
                    color = OnboardingConfig.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                val displayName = userName.ifBlank { "Athlete" }
                Text(
                    text          = displayName,
                    color         = OnboardingConfig.accentBlue,
                    fontSize      = 30.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    textAlign     = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text          = stringResource(R.string.onb_arise_reg_complete),
                    color         = OnboardingConfig.textMuted,
                    fontSize      = 18.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    textAlign     = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text      = stringResource(R.string.onb_arise_line1),
                    color     = OnboardingConfig.textMuted,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = stringResource(R.string.onb_arise_line2),
                    color     = OnboardingConfig.textMuted,
                    fontSize  = 13.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(22.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OnboardingConfig.surfaceDark, RoundedCornerShape(16.dp))
                        .border(1.dp, OnboardingConfig.cardBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryLine("Goal", goalMode.toDisplayLabel())
                    SummaryLine("Progression", progressionStyle.toDisplayLabel())
                    SummaryLine("Weekly target", "$weeklyGoalDays days / week")
                    SummaryLine("Weight unit", weightUnit.uppercase())
                    SummaryLine("AI mode", intelligenceMode.toDisplayLabel())
                    SummaryLine(
                        "Permissions",
                        buildList {
                            add(if (healthConnectGranted) "Health connected" else "Health skipped")
                            add(if (notificationsGranted) "Notifications on" else "Notifications skipped")
                        }.joinToString(" / "),
                    )
                }

                Spacer(Modifier.height(34.dp))

                GlowButton(
                    text    = stringResource(R.string.onb_arise_cta),
                    onClick = onStartTraining,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnboardingConfig.textMuted, fontSize = 12.sp)
        Text(value, color = OnboardingConfig.accentBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

private fun String.toDisplayLabel(): String =
    lowercase()
        .split("_")
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
