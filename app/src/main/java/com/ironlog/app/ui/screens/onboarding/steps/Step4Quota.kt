package com.ironlog.app.ui.screens.onboarding.steps

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingPageHeader
import com.ironlog.app.ui.screens.onboarding.OnboardingSection
import com.ironlog.app.ui.screens.onboarding.SetupReward

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
@OptIn(ExperimentalLayoutApi::class)
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
            .verticalScroll(rememberScrollState())
            .appPadding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        OnboardingPageHeader(
            step = "Weekly rhythm",
            title = "Choose days you can actually protect.",
            body = "Consistency beats an ambitious schedule that collapses. These days shape Home's training rhythm and weekly target. Reminder delivery still follows the daily time you choose in Settings.",
        )

        Spacer(Modifier.height(appGapDp(26.dp)))

        OnboardingSection(
            title = "Training days",
            caption = "Tap at least one day. You can reschedule without losing history.",
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().semantics {
                    isTraversalGroup = true
                    contentDescription = "Training days"
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DAY_LABELS.forEachIndexed { index, label ->
                    val isSelected = index in selectedDayIndices
                    val bgColor by animateColorAsState(
                        if (isSelected) OnboardingConfig.accentBlue else OnboardingConfig.bgDark,
                        tween(200), label = "dot$index"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) Color.White else OnboardingConfig.textMuted,
                        tween(200), label = "dotText$index"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(CircleShape)
                            .semantics {
                                contentDescription = DAY_NAMES[index]
                                if (isSelected && selectedDayIndices.size == 1) {
                                    stateDescription = "Selected. At least one training day is required."
                                }
                            }
                            .toggleable(value = isSelected, role = Role.Checkbox,
                                onValueChange = {
                                    if (!isSelected || selectedDayIndices.size > 1) onDayToggle(index)
                                }),
                    ) {
                        Box(
                            Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                                .background(bgColor, CircleShape)
                                .border(if (isSelected) 2.dp else 0.dp,
                                    if (isSelected) Color.White else Color.Transparent, CircleShape)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = label, color = textColor, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { })
                        }
                    }
                }
            }
            Spacer(Modifier.height(appGapDp(18.dp)))
            Text(
                text = "${selectedDayIndices.size}-session weekly target",
                color = OnboardingConfig.accentGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(Modifier.height(appGapDp(14.dp)))

        OnboardingSection(title = "Weight display", caption = "This only changes display units; stored training data remains precise.") {
            Row(modifier = Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = appSpacedBy(10.dp)) {
                listOf("kg", "lbs").forEach { unit ->
                    val isActive = unit == weightUnit
                    val bg by animateColorAsState(
                        if (isActive) OnboardingConfig.accentBlue else OnboardingConfig.bgDark, tween(200), label = "unit$unit"
                    )
                    val tc by animateColorAsState(
                        if (isActive) Color.White else OnboardingConfig.textMuted, tween(200), label = "unitText$unit"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg)
                            .selectable(selected = isActive, role = Role.RadioButton,
                                onClick = { onWeightUnitChange(unit) })
                            .padding(vertical = 13.dp),
                    ) {
                        Text(unit.uppercase(), color = tc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(appGapDp(20.dp)))
        SetupReward("Weekly targets power streaks, makeup quests and widget states", Modifier.fillMaxWidth())
        Spacer(Modifier.height(appGapDp(14.dp)))

        GlowButton(text = stringResource(R.string.onb_quota_cta), onClick = onNext)
        Spacer(Modifier.height(appGapDp(24.dp)))
    }
}
