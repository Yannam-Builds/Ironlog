package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.InfiniteNumberWheelSheet
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingPageHeader
import com.ironlog.app.ui.screens.onboarding.SetupReward
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
    seededGrade: String,
    seededStats: Map<String, Int>,
    onNext: () -> Unit,
) {
    var picker by remember { mutableStateOf<PickerSpec?>(null) }
    var showMovementChecks by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingPageHeader(
            step = "Baseline",
            title = "Give IronLog a starting signal.",
            body = "These values personalize recovery, load suggestions and your provisional ledger. Verified workouts always outrank self-reported numbers.",
        )
        Spacer(Modifier.height(26.dp))

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
                    "Add a few recent best efforts for a more accurate starting badge. You can skip this and let verified workouts calibrate you.",
                    color = OnboardingConfig.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
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

        BaselineResultCard(grade = seededGrade, stats = seededStats)

        SetupReward("Your badge remains provisional until training evidence confirms it", Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        GlowButton(text = "Save baseline", onClick = onNext)
        Spacer(Modifier.height(24.dp))
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
            .padding(bottom = 14.dp)
            .background(OnboardingConfig.surfaceDark, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Text(title, color = OnboardingConfig.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun PickerField(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(OnboardingConfig.bgDark.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnboardingConfig.textMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        Text("›", color = OnboardingConfig.accentBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
private fun BaselineResultCard(grade: String, stats: Map<String, Int>) {
    BaselineCard("Starting estimate") {
        Text(
            "Provisional $grade",
            color = OnboardingConfig.accentBlue,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Self-reported values are capped. Higher grades require verified sessions, consistency, and integrity.",
            color = OnboardingConfig.textMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        stats.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(OnboardingConfig.bgDark.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                    ) {
                        Text(label, color = OnboardingConfig.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(value.toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
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
