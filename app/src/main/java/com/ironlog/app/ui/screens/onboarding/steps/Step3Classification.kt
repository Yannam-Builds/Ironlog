package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text          = stringResource(R.string.onb_class_header),
            color         = OnboardingConfig.accentBlue,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign     = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = stringResource(R.string.onb_class_subtext),
            color     = OnboardingConfig.textMuted,
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
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
                        onNext()
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
    }
}
