package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.GlowCard
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingPageHeader
import com.ironlog.app.ui.screens.onboarding.SetupReward

private data class GoalOption(val mode: String, val label: String, val subtitle: String, val programming: String)

private val GOAL_OPTIONS = listOf(
    GoalOption("STRENGTH", "Strength", "Move more weight with repeatable technique", "Lower rep ranges · longer rest · load progression"),
    GoalOption("HYPERTROPHY", "Muscle growth", "Build size through productive weekly volume", "Moderate reps · volume balance · fatigue control"),
    GoalOption("GENERAL_FITNESS", "General fitness", "Blend strength, conditioning and health", "Mixed reps · conditioning · sustainable variety"),
)

@Composable
fun Step5GoalMode(
    selectedGoalMode: String,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        OnboardingPageHeader(
            step = "Primary goal",
            title = "What should the plan optimize first?",
            body = "Your choice tunes rep ranges, rest periods, progression suggestions and how training insights are framed.",
        )

        Spacer(Modifier.height(26.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GOAL_OPTIONS.forEachIndexed { index, option ->
                val isSelected = option.mode == selectedGoalMode
                GlowCard(
                    selected = isSelected,
                    glowColor = OnboardingConfig.accentGold,
                    onClick = { onSelect(option.mode) },
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "0${index + 1}",
                            color = if (isSelected) OnboardingConfig.accentGold else OnboardingConfig.textFaint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = OnboardingConfig.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(4.dp))
                            Text(option.subtitle, color = OnboardingConfig.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(option.programming, color = OnboardingConfig.accentGold, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SetupReward("Completing workouts can later unlock the Multiclass badge across goal modes", Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        GlowButton(text = stringResource(R.string.onb_goal_cta), onClick = onNext)
        Spacer(Modifier.height(24.dp))
    }
}
