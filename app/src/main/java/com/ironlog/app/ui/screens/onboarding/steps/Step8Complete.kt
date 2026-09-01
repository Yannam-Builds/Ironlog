package com.ironlog.app.ui.screens.onboarding.steps

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingScrollablePage
import com.ironlog.app.ui.screens.onboarding.OnboardingTrainingProfilePreview
import com.ironlog.app.ui.screens.onboarding.ParticleField
import com.ironlog.app.ui.screens.onboarding.SetupReward
import com.ironlog.app.ui.screens.onboarding.supportingMascotSizeDp
import kotlin.math.roundToInt

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
    profilePreview: OnboardingTrainingProfilePreview,
    onStartTraining: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark),
    ) {
        val mascotSize = supportingMascotSizeDp(
            availableHeightDp = maxHeight.value.roundToInt(),
            fontScale = LocalDensity.current.fontScale,
        )
        ParticleField()

            OnboardingScrollablePage(
                backgroundColor = Color.Transparent,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.forgefox_20_clipboard),
                    contentDescription = "Forge Fox holding a training profile",
                    modifier = Modifier.size(mascotSize.dp),
                )
                Spacer(Modifier.height(appGapDp(8.dp)))
                Text(
                    text = "TRAINING PROFILE READY",
                    color = OnboardingConfig.accentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(appGapDp(20.dp)))

                val displayName = userName.ifBlank { "Athlete" }
                Text(
                    text          = displayName,
                    color         = OnboardingConfig.accentBlue,
                    fontSize      = 30.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    textAlign     = TextAlign.Center,
                )

                Spacer(Modifier.height(appGapDp(8.dp)))

                Text(
                    text          = "Your provisional profile is ready",
                    color         = OnboardingConfig.textPrimary,
                    fontSize      = 20.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    textAlign     = TextAlign.Center,
                )

                Spacer(Modifier.height(appGapDp(24.dp)))

                Text(
                    text      = "IronLog will use your self-reported onboarding baseline to seed a reduced-trust starting profile. Verified training will refine it.",
                    color     = OnboardingConfig.textMuted,
                    fontSize  = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(appGapDp(22.dp)))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OnboardingConfig.surfaceDark, RoundedCornerShape(20.dp))
                        .border(1.dp, OnboardingConfig.cardBorder, RoundedCornerShape(20.dp))
                        .appPadding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Baseline ready to save", color = OnboardingConfig.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(appGapDp(6.dp)))
                    Text(
                        "${profilePreview.provisionalRankLabel} · Level ${profilePreview.seededLevel} · ${profilePreview.seededXp} XP",
                        color = OnboardingConfig.accentGold,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(appGapDp(6.dp)))
                    Text(
                        "Estimated from ${profilePreview.estimatedLifetimeSessions} lifetime sessions",
                        color = OnboardingConfig.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(appGapDp(14.dp)))
                    profilePreview.estimatedStats.entries.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = appSpacedBy(10.dp),
                        ) {
                            row.forEach { (label, value) ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(OnboardingConfig.bgDark.copy(alpha = 0.58f), RoundedCornerShape(14.dp))
                                        .appPadding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(label, color = OnboardingConfig.textFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(value.toString(), color = OnboardingConfig.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(appGapDp(10.dp)))
                    }
                    Text(
                        "Supported starting badges",
                        color = OnboardingConfig.textFaint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(appGapDp(4.dp)))
                    Text(
                        profilePreview.supportedBadgeLabels.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                            ?: "No exposure milestone badges yet",
                        color = OnboardingConfig.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(appGapDp(10.dp)))
                    Text(
                        "Verified workouts add proof XP, preserve this one-time baseline, and refine and build on rank and stats without double counting.",
                        color = OnboardingConfig.accentBlue,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(appGapDp(18.dp)))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OnboardingConfig.surfaceDark, RoundedCornerShape(20.dp))
                        .appPadding(18.dp),
                    verticalArrangement = appSpacedBy(12.dp),
                ) {
                    SummaryLine("Goal", goalMode.toDisplayLabel())
                    SummaryLine("Progression", progressionStyle.toDisplayLabel())
                    SummaryLine("Weekly target", "$weeklyGoalDays days / week")
                    SummaryLine("Weight unit", weightUnit.uppercase())
                    SummaryLine("AI mode", intelligenceMode.toDisplayLabel())
                    SummaryLine("Health context", if (healthConnectGranted) "Read-only access" else "Not connected")
                    SummaryLine("Reminders", if (notificationsGranted) "Enabled" else "Not enabled")
                }

                Spacer(Modifier.height(appGapDp(18.dp)))
                SetupReward("Next: choose a starter plan or enter the app with an empty workspace", Modifier.fillMaxWidth())
                Spacer(Modifier.height(appGapDp(18.dp)))

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
        Spacer(Modifier.width(appGapDp(16.dp)))
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
