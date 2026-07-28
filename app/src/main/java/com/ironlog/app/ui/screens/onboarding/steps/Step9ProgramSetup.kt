package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.seed.PROGRAM_TEMPLATES
import com.ironlog.app.data.seed.ProgramTemplate
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig

@Composable
fun Step9ProgramSetup(
    onSkip: () -> Unit,
    onApplyTemplate: (ProgramTemplate) -> Unit,
) {
    var selected by remember { mutableStateOf<ProgramTemplate?>(null) }
    val templates = remember { PROGRAM_TEMPLATES.take(8) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 64.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Pick a starter plan",
            color = OnboardingConfig.accentBlue,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Select one preloaded plan now, or skip and choose later in Plans.",
            color = OnboardingConfig.textMuted,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))

        templates.forEach { plan ->
            val isSelected = selected?.id == plan.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) OnboardingConfig.accentBlue else OnboardingConfig.cardBorder,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .background(
                        color = if (isSelected) OnboardingConfig.surfaceDark.copy(alpha = 0.95f) else OnboardingConfig.surfaceDark,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { selected = plan }
                    .padding(14.dp),
            ) {
                Text(
                    text = plan.name,
                    color = OnboardingConfig.accentBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = plan.description,
                    color = OnboardingConfig.textMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanChip("${plan.days.size} days/week")
                    PlanChip(plan.category)
                    PlanChip("${plan.durationWeeks} weeks")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        GlowButton(
            text = if (selected != null) "Use selected plan" else "Skip for now",
            onClick = {
                val choice = selected
                if (choice != null) onApplyTemplate(choice) else onSkip()
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "You can import or switch plans anytime in the Plans tab.",
            color = OnboardingConfig.textMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PlanChip(text: String) {
    Text(
        text = text,
        color = OnboardingConfig.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .border(1.dp, OnboardingConfig.cardBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
