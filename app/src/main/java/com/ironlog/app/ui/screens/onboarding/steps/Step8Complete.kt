package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
import com.ironlog.app.ui.screens.onboarding.SetupReward
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
    var revealDone by remember { mutableStateOf(false) }
    var slotBadge by remember { mutableStateOf(qualifiedBadge) }
    LaunchedEffect(qualifiedBadge) {
        slotBadge = qualifiedBadge
        delay(650L)
        revealDone = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark),
    ) {
        ParticleField()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 64.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.forgefox_25_trophy_medal),
                    contentDescription = "Forge Fox holding a trophy",
                    modifier = Modifier.size(104.dp),
                )
                Spacer(Modifier.height(8.dp))
                SetupBadge(
                    code = slotBadge.take(2).uppercase(),
                    accent = OnboardingConfig.accentBlue,
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (revealDone) "Provisional grade · $slotBadge" else "Preparing your ledger...",
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
                    text          = "Your training system is calibrated",
                    color         = OnboardingConfig.textPrimary,
                    fontSize      = 20.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    textAlign     = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text      = "Your first workouts will verify this baseline, tune recovery, and begin earning durable ledger XP.",
                    color     = OnboardingConfig.textMuted,
                    fontSize  = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(22.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OnboardingConfig.surfaceDark, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryLine("Goal", goalMode.toDisplayLabel())
                    SummaryLine("Progression", progressionStyle.toDisplayLabel())
                    SummaryLine("Weekly target", "$weeklyGoalDays days / week")
                    SummaryLine("Weight unit", weightUnit.uppercase())
                    SummaryLine("AI mode", intelligenceMode.toDisplayLabel())
                    SummaryLine("Recovery data", if (healthConnectGranted) "Connected" else "Not connected")
                    SummaryLine("Reminders", if (notificationsGranted) "Enabled" else "Not enabled")
                }

                Spacer(Modifier.height(18.dp))
                SetupReward("Next: choose a starter plan or enter the app with an empty workspace", Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))

                GlowButton(
                    text    = stringResource(R.string.onb_arise_cta),
                    onClick = onStartTraining,
                )
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
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = OnboardingConfig.textMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = OnboardingConfig.accentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

private fun String.toDisplayLabel(): String =
    lowercase()
        .split("_")
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
