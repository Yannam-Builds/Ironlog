package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun Step4Quota(
    selectedDayIndices: Set<Int>,
    onDayToggle: (index: Int) -> Unit,
    weightUnit: String,
    onWeightUnitChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text          = stringResource(R.string.onb_quota_header),
            color         = OnboardingConfig.accentBlue,
            fontSize      = 20.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign     = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            DAY_LABELS.forEachIndexed { index, label ->
                val isSelected = index in selectedDayIndices
                val bgColor by animateColorAsState(
                    if (isSelected) OnboardingConfig.accentBlue else Color.Transparent,
                    tween(200), label = "dot$index"
                )
                val textColor by animateColorAsState(
                    if (isSelected) Color.White else OnboardingConfig.textMuted,
                    tween(200), label = "dotText$index"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(
                            1.5.dp,
                            OnboardingConfig.accentBlue.copy(alpha = if (isSelected) 0f else 0.3f),
                            CircleShape
                        )
                        .clickable {
                            if (!isSelected || selectedDayIndices.size > 1) onDayToggle(index)
                        },
                ) {
                    Text(text = label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text          = stringResource(R.string.onb_quota_sessions, selectedDayIndices.size),
            color         = OnboardingConfig.accentGold,
            fontSize      = 24.sp,
            fontWeight    = FontWeight.Black,
            letterSpacing = 2.sp,
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, OnboardingConfig.accentBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        ) {
            listOf("kg", "lbs").forEach { unit ->
                val isActive = unit == weightUnit
                val bg by animateColorAsState(
                    if (isActive) OnboardingConfig.accentBlue else Color.Transparent, tween(200), label = "unit$unit"
                )
                val tc by animateColorAsState(
                    if (isActive) Color.White else OnboardingConfig.textMuted, tween(200), label = "unitText$unit"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .clickable { onWeightUnitChange(unit) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(unit.uppercase(), color = tc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        GlowButton(text = stringResource(R.string.onb_quota_cta), onClick = onNext)
    }
}
