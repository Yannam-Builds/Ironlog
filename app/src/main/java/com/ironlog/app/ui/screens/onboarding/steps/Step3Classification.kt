package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowCard
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingPageHeader
import com.ironlog.app.ui.screens.onboarding.SetupReward
import com.ironlog.app.ui.screens.onboarding.SetupBadge

@Composable
fun Step3Classification(
    selectedProgressionStyle: String,
    onSelect: (progressionStyle: String, defaultGoalMode: String) -> Unit,
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
            step = "Training level",
            title = "How should progression begin?",
            body = "Choose the closest fit. IronLog will replace this estimate with verified training evidence over time.",
        )
        Spacer(Modifier.height(28.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingConfig.baselineOptions.forEach { option ->
                val isSelected = option.progressionStyle == selectedProgressionStyle
                GlowCard(
                    selected  = isSelected,
                    glowColor = option.accent,
                    onClick   = {
                        onSelect(option.progressionStyle, option.defaultGoalMode)
                    },
                    modifier  = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SetupBadge(code = option.code, accent = option.accent)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                color = if (isSelected) option.accent else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = option.description,
                                color = OnboardingConfig.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        SetupReward(
            text = "This sets your starting difficulty — it does not grant unearned XP",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        GlowButton(text = "Use this progression", onClick = onNext)
        Spacer(Modifier.height(24.dp))
    }
}
