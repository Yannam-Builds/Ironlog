package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
        Spacer(Modifier.height(40.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 8.dp),
        ) {
            items(OnboardingConfig.baselineOptions) { option ->
                val isSelected = option.progressionStyle == selectedProgressionStyle
                GlowCard(
                    selected  = isSelected,
                    glowColor = option.accent,
                    onClick   = {
                        onSelect(option.progressionStyle, option.defaultGoalMode)
                        onNext()
                    },
                    modifier  = Modifier.width(184.dp),
                ) {
                    SetupBadge(
                        code     = option.code,
                        accent   = option.accent,
                        modifier  = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text          = option.label,
                        color         = if (isSelected) option.accent else OnboardingConfig.textMuted,
                        fontSize      = 14.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        textAlign     = TextAlign.Center,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text      = option.description,
                        color     = OnboardingConfig.textMuted,
                        fontSize  = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
