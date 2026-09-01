package com.ironlog.app.ui.screens.onboarding.steps

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.InfiniteNumberWheelSheet
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingInfoBanner
import com.ironlog.app.ui.screens.onboarding.OnboardingPageHeader
import com.ironlog.app.ui.screens.onboarding.OnboardingScrollablePage
import com.ironlog.app.ui.screens.onboarding.OnboardingTrainingProfilePreview
import com.ironlog.app.ui.screens.onboarding.baselinePickerFieldLayoutSpec
import java.time.Year
import kotlin.math.roundToInt

private data class PickerSpec(
    val title: String,
    val values: List<Int>,
    val selected: Int,
    val labelFor: (Int) -> String,
    val onSelect: (Int) -> Unit,
)

@Composable
fun Step3Baseline(
    yearOfBirth: Int,
    bodyweightKg: Int,
    trainingAgeMonths: Int,
    historicalTrainingDaysPerWeek: Int,
    hasPastTraining: Boolean,
    hasGymAccess: Boolean,
    pushups: Int,
    pullups: Int,
    benchKg: Int,
    latPulldownKg: Int,
    mileRunSeconds: Int,
    onYearOfBirthChange: (Int) -> Unit,
    onBodyweightChange: (Int) -> Unit,
    onTrainingAgeChange: (Int) -> Unit,
    onHistoricalTrainingDaysPerWeekChange: (Int) -> Unit,
    onPastTrainingChange: (Boolean) -> Unit,
    onGymAccessChange: (Boolean) -> Unit,
    onPushupsChange: (Int) -> Unit,
    onPullupsChange: (Int) -> Unit,
    onBenchChange: (Int) -> Unit,
    onLatPulldownChange: (Int) -> Unit,
    onMileRunChange: (Int) -> Unit,
    profilePreview: OnboardingTrainingProfilePreview,
    onNext: () -> Unit,
) {
    var picker by remember { mutableStateOf<PickerSpec?>(null) }
    var showMovementChecks by remember { mutableStateOf(false) }

    OnboardingScrollablePage(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingPageHeader(
            step = "Baseline",
            title = "Give IronLog a starting signal.",
            body = "These answers personalize initial recovery and load suggestions. Verified workouts refine and build on this estimate with your own training evidence.",
        )
        Spacer(Modifier.height(appGapDp(26.dp)))

        BaselineCard("Profile") {
            PickerField("Birth year", "$yearOfBirth · ${ageFromBirthYear(yearOfBirth)} yrs") {
                picker = PickerSpec(
                    title = "Birth year",
                    values = ((Year.now().value - 90)..(Year.now().value - 13)).toList().reversed(),
                    selected = yearOfBirth,
                    labelFor = { it.toString() },
                    onSelect = onYearOfBirthChange,
                )
            }
            PickerField("Body weight", "$bodyweightKg kg") {
                picker = PickerSpec(
                    title = "Body weight",
                    values = (30..250).toList(),
                    selected = bodyweightKg,
                    labelFor = { "$it kg" },
                    onSelect = onBodyweightChange,
                )
            }
            PickerField("Training age", formatTrainingAge(trainingAgeMonths)) {
                picker = PickerSpec(
                    title = "Training age",
                    values = trainingAgeValues(),
                    selected = trainingAgeMonths,
                    labelFor = ::formatTrainingAge,
                    onSelect = onTrainingAgeChange,
                )
            }
            PickerField("Average training days / week", formatHistoricalTrainingDays(historicalTrainingDaysPerWeek)) {
                picker = PickerSpec(
                    title = "Average training days / week",
                    values = (1..7).toList(),
                    selected = historicalTrainingDaysPerWeek,
                    labelFor = ::formatHistoricalTrainingDays,
                    onSelect = onHistoricalTrainingDaysPerWeekChange,
                )
            }
        }

        ToggleRow("Logged workouts before?", hasPastTraining, onPastTrainingChange)
        ToggleRow("Gym equipment access?", hasGymAccess, onGymAccessChange)

        BaselineCard(if (showMovementChecks) "Optional movement checks" else "Improve your starting estimate") {
            if (!showMovementChecks) {
                Text(
                    "Add a few recent best efforts for more tailored starting guidance. You can skip this and let verified workouts calibrate you.",
                    color = OnboardingConfig.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(appGapDp(12.dp)))
                OutlinedButton(
                    onClick = { showMovementChecks = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, OnboardingConfig.accentBlue.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Add movement checks", color = OnboardingConfig.accentBlue, fontWeight = FontWeight.Bold)
                }
            } else {
                PickerField("Pushups", "$pushups reps") {
                    picker = PickerSpec("Pushups", (0..120).toList(), pushups, { "$it reps" }, onPushupsChange)
                }
                PickerField("Pullups", "$pullups reps") {
                    picker = PickerSpec("Pullups", (0..40).toList(), pullups, { "$it reps" }, onPullupsChange)
                }
                PickerField("1-mile run", formatMileRun(mileRunSeconds)) {
                    picker = PickerSpec(
                        title = "1-mile run",
                        values = listOf(0) + (240..900 step 15).toList(),
                        selected = mileRunSeconds,
                        labelFor = ::formatMileRun,
                        onSelect = onMileRunChange,
                    )
                }
                if (hasGymAccess) {
                    PickerField("Bench press", "$benchKg kg") {
                        picker = PickerSpec("Bench press", (0..220 step 5).toList(), benchKg, { "$it kg" }, onBenchChange)
                    }
                    PickerField("Lat pulldown", "$latPulldownKg kg") {
                        picker = PickerSpec("Lat pulldown", (0..220 step 5).toList(), latPulldownKg, { "$it kg" }, onLatPulldownChange)
                    }
                }
            }
        }

        TrainingProfilePreviewCard(preview = profilePreview)

        OnboardingInfoBanner(
            text = "Self-reported onboarding rewards use reduced trust. Verified workouts add proof XP, refine and build on these signals; action badges still require matching evidence.",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(appGapDp(14.dp)))
        GlowButton(text = "Save baseline", onClick = onNext)
        Spacer(Modifier.height(appGapDp(24.dp)))
    }

    picker?.let { active ->
        InfiniteNumberWheelSheet(
            title = active.title,
            values = active.values,
            selected = active.selected,
            labelFor = active.labelFor,
            onConfirm = active.onSelect,
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun BaselineCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appPadding(bottom = 14.dp)
            .background(OnboardingConfig.surfaceDark, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Text(title, color = OnboardingConfig.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(appGapDp(12.dp)))
        content()
    }
}

@Composable
private fun PickerField(label: String, value: String, onClick: () -> Unit) {
    val layout = remember { baselinePickerFieldLayoutSpec() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(OnboardingConfig.bgDark.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = OnboardingConfig.textMuted,
            fontSize = 14.sp,
            maxLines = layout.labelMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(layout.labelWeight),
        )
        Row(
            modifier = Modifier.weight(layout.valueWeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.End,
                maxLines = layout.valueMaxLines,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(appGapDp(8.dp)))
            Text("›", color = OnboardingConfig.accentBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToggleRow(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appPadding(bottom = 12.dp),
        horizontalArrangement = appSpacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnboardingConfig.textMuted, modifier = Modifier.weight(1f), fontSize = 14.sp)
        ToggleChip("No", !selected) { onChange(false) }
        ToggleChip("Yes", selected) { onChange(true) }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) OnboardingConfig.accentBlue else OnboardingConfig.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) OnboardingConfig.accentBlue.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (selected) OnboardingConfig.accentBlue else OnboardingConfig.textMuted,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrainingProfilePreviewCard(preview: OnboardingTrainingProfilePreview) {
    BaselineCard("Training profile preview") {
        Text(
            "SELF-REPORTED ESTIMATE",
            color = OnboardingConfig.accentBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(appGapDp(6.dp)))
        Text(
            "Provisional profile rank",
            color = OnboardingConfig.textFaint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            preview.provisionalRankLabel,
            color = OnboardingConfig.accentBlue,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(appGapDp(6.dp)))
        Text(
            "Estimated from ${preview.estimatedLifetimeSessions} lifetime sessions using your training age, weekly rhythm, and movement checks.",
            color = OnboardingConfig.textMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(appGapDp(14.dp)))
        ProfilePreviewRow("Training age", preview.experienceLabel)
        ProfilePreviewRow("Usual rhythm", preview.weeklyRhythmLabel)
        ProfilePreviewRow("History", preview.historyLabel)
        ProfilePreviewRow("Equipment", preview.equipmentLabel)
        ProfilePreviewRow("Calibration", preview.calibrationLabel)
        Spacer(Modifier.height(appGapDp(14.dp)))

        preview.estimatedStats.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = appSpacedBy(10.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(OnboardingConfig.bgDark.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .appPadding(12.dp),
                    ) {
                        Text(label, color = OnboardingConfig.textFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(value.toString(), color = OnboardingConfig.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(appGapDp(10.dp)))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OnboardingConfig.bgDark.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .appPadding(14.dp),
        ) {
            Text("Baseline estimate", color = OnboardingConfig.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(appGapDp(4.dp)))
            Text(
                "Level ${preview.seededLevel} · ${preview.seededXp} XP",
                color = OnboardingConfig.accentGold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(appGapDp(8.dp)))
            Text(
                "Supported starting badges",
                color = OnboardingConfig.textFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                preview.supportedBadgeLabels.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                    ?: "No exposure milestone badges yet",
                color = OnboardingConfig.textMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(appGapDp(8.dp)))
            Text(
                "Verified workouts refine and build on every signal while adding proof-backed progress without duplicating this baseline.",
                color = OnboardingConfig.textMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun ProfilePreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = OnboardingConfig.textFaint, fontSize = 12.sp, modifier = Modifier.weight(0.42f))
        Spacer(Modifier.width(appGapDp(12.dp)))
        Text(
            value,
            color = OnboardingConfig.textPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}

private fun ageFromBirthYear(year: Int): Int = (Year.now().value - year).coerceIn(13, 90)

private fun trainingAgeValues(): List<Int> =
    (0..24).toList() + (30..120 step 6).toList() + (132..360 step 12).toList()

private fun formatTrainingAge(months: Int): String = when {
    months <= 0 -> "New"
    months < 12 -> "$months mo"
    months % 12 == 0 -> "${months / 12} yr"
    else -> "${months / 12}y ${months % 12}m"
}

private fun formatHistoricalTrainingDays(days: Int): String =
    if (days == 1) "1 day / week" else "$days days / week"

private fun formatMileRun(seconds: Int): String {
    if (seconds <= 0) return "Not tested"
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}
