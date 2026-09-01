package com.ironlog.app.ui.screens.onboarding.steps

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.ironlog.app.ui.theme.Text
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
import com.ironlog.app.domain.intelligence.ProgramRecommendation
import com.ironlog.app.domain.intelligence.ProgramRecommendationEngine
import com.ironlog.app.domain.intelligence.ProgramRecommendationProfile
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingDraft
import com.ironlog.app.ui.screens.onboarding.OnboardingPageHeader
import com.ironlog.app.ui.screens.onboarding.SetupReward

@Composable
fun Step9ProgramSetup(
    draft: OnboardingDraft,
    onSkip: () -> Unit,
    onApplyTemplate: (ProgramTemplate) -> Unit,
) {
    var selected by remember { mutableStateOf<ProgramTemplate?>(null) }
    val recommendations = remember(
        draft.goalMode,
        draft.weeklyGoalDays,
        draft.trainingAgeMonths,
        draft.hasPastTraining,
        draft.hasGymAccess,
    ) {
        ProgramRecommendationEngine.rank(
            PROGRAM_TEMPLATES,
            ProgramRecommendationProfile(
                goalMode = draft.goalMode,
                weeklyDays = draft.weeklyGoalDays,
                trainingAgeMonths = draft.trainingAgeMonths,
                hasPastTraining = draft.hasPastTraining,
                hasGymAccess = draft.hasGymAccess,
            ),
        ).take(4)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .appPadding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 64.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        OnboardingPageHeader(
            step = "First plan",
            title = "Start with structure, not a blank page.",
            body = "Pick a proven template or enter IronLog without one. Plans stay fully editable and can be replaced at any time.",
        )
        Spacer(Modifier.height(appGapDp(22.dp)))

        Text(
            text = "RECOMMENDED FOR YOUR ${draft.weeklyGoalDays}-DAY WEEK",
            color = OnboardingConfig.accentBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(appGapDp(8.dp)))

        recommendations.forEachIndexed { index, recommendation ->
            val plan = recommendation.template
            val isSelected = selected?.id == plan.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .appPadding(vertical = 5.dp)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) OnboardingConfig.accentBlue else OnboardingConfig.cardBorder,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .background(
                        color = if (isSelected) OnboardingConfig.surfaceDark.copy(alpha = 0.95f) else OnboardingConfig.surfaceDark,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable { selected = plan }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (index == 0 || isSelected) {
                    Text(
                        text = if (isSelected) "SELECTED" else "BEST FIT",
                        color = OnboardingConfig.accentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(appGapDp(4.dp)))
                }
                Text(
                    text = plan.name,
                    color = if (isSelected) OnboardingConfig.accentBlue else OnboardingConfig.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(appGapDp(4.dp)))
                Text(
                    text = plan.description,
                    color = OnboardingConfig.textMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(appGapDp(6.dp)))
                Text(
                    text = recommendation.reason,
                    color = OnboardingConfig.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(appGapDp(8.dp)))
                Text(
                    text = "${plan.days.size} days/week · ${recommendation.experienceLabel} · ${recommendation.sessionLengthLabel}",
                    color = OnboardingConfig.textFaint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(appGapDp(16.dp)))
        SetupReward("Completed plan sessions feed recovery, stats, streaks, badges and widgets", Modifier.fillMaxWidth())
        Spacer(Modifier.height(appGapDp(14.dp)))
        GlowButton(
            text = if (selected != null) "Use selected plan" else "Skip for now",
            onClick = {
                val choice = selected
                if (choice != null) onApplyTemplate(choice) else onSkip()
            },
        )
        Spacer(Modifier.height(appGapDp(10.dp)))
        Text(
            text = "You can import or switch plans anytime in the Plans tab.",
            color = OnboardingConfig.textMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(appGapDp(20.dp)))
    }
}
