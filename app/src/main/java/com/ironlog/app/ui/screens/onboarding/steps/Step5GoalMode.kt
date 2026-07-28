package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

private data class GoalOption(val mode: String, val label: String, val subtitle: String)

private val GOAL_OPTIONS = listOf(
    GoalOption("STRENGTH",    "STRENGTH",    "Max weight, low reps"),
    GoalOption("HYPERTROPHY", "HYPERTROPHY", "Size & muscle growth"),
    GoalOption("GENERAL_FITNESS", "GENERAL FITNESS", "Strength, conditioning & health"),
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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text          = stringResource(R.string.onb_goal_header),
            color         = OnboardingConfig.accentBlue,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign     = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        GOAL_OPTIONS.chunked(2).forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { option ->
                    val isSelected = option.mode == selectedGoalMode
                    GlowCard(
                        selected  = isSelected,
                        glowColor = OnboardingConfig.accentGold,
                        onClick   = { onSelect(option.mode) },
                        modifier  = if (row.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    ) {
                        Text(
                            text          = option.label,
                            color         = if (isSelected) OnboardingConfig.accentGold else OnboardingConfig.accentBlue,
                            fontSize      = 12.sp,
                            fontWeight    = FontWeight.Black,
                            letterSpacing = 2.sp,
                            textAlign     = TextAlign.Center,
                            modifier      = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text      = option.subtitle,
                            color     = OnboardingConfig.textMuted,
                            fontSize  = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(24.dp))

        GlowButton(text = stringResource(R.string.onb_goal_cta), onClick = onNext)
    }
}
