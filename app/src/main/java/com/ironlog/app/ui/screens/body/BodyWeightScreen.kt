package com.ironlog.app.ui.screens.body

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.ironlog.app.ui.theme.Text
import com.ironlog.app.ui.theme.rememberTypographyPaint
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.repository.BodyMeasurementInput
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.BodyWeightUiRow
import com.ironlog.app.services.ShareService
import com.ironlog.app.util.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.graphics.nativeCanvas
import java.util.Locale
import kotlin.math.abs

private const val CHART_HEIGHT = 240f
private data class PlotPad(val top: Float = 20f, val right: Float = 30f, val bottom: Float = 36f, val left: Float = 42f)
private val PLOT = PlotPad()

data class SummaryCard(val id: String, val label: String, val value: String, val raw: Double?)
private fun canonicalBodyWeightUnit(unit: String): String {
    val normalized = unit.trim().lowercase(Locale.US)
    return if (normalized == "lb" || normalized == "lbs") "lbs" else "kg"
}

/** Converts a user-entered display value into the canonical kilograms used by persistence. */
fun bodyWeightInputToKg(value: Double?, unit: String): Double? =
    value?.takeIf { it.isFinite() }?.let { convertUnitToKg(it, canonicalBodyWeightUnit(unit), decimals = 3) }

/** Converts canonical kilograms back to a stable one-decimal value for editable fields. */
fun bodyWeightInputTextFromKg(valueKg: Double?, unit: String): String {
    if (valueKg == null || !valueKg.isFinite()) return ""
    val displayValue = convertKgToUnit(valueKg, canonicalBodyWeightUnit(unit), decimals = 1)
    return java.lang.String.format(Locale.US, "%.1f", displayValue)
}

fun formatWeightValue(valueKg: Double?, unit: String): String =
    if (valueKg == null || !valueKg.isFinite()) "--"
    else formatWeightFromKg(valueKg, canonicalBodyWeightUnit(unit))

fun formatSigned(valueKg: Double?, unit: String): String =
    if (valueKg == null || !valueKg.isFinite()) "--"
    else "${if (valueKg > 0) "+" else ""}${formatWeightFromKg(valueKg, canonicalBodyWeightUnit(unit))}"
fun computeMovingAverage(entries: List<NormalizedBodyWeightEntry>, windowDays: Int = 7): List<Double> = entries.mapIndexed { i, entry ->
    val cutoff = entry.timestamp - windowDays * 24L * 60L * 60L * 1000L
    val window = entries.take(i + 1).filter { it.timestamp >= cutoff }
    window.sumOf { it.weight } / window.size
}
fun summaryCards(summary: BodyWeightSummary, unit: String) = listOf(
    SummaryCard("current", "Current Weight", formatWeightValue(summary.currentWeight, unit), summary.currentWeight),
    SummaryCard("prev", "Change vs Previous", formatSigned(summary.changeFromPrevious, unit), summary.changeFromPrevious),
    SummaryCard("trend", "Weekly Trend", formatSigned(summary.weeklyTrend, unit), summary.weeklyTrend),
    SummaryCard("week", "This Week Change", formatSigned(summary.weekChange, unit), summary.weekChange),
    SummaryCard("month", "This Month Change", formatSigned(summary.monthChange, unit), summary.monthChange),
    SummaryCard("total", "Total Change", formatSigned(summary.totalChange, unit), summary.totalChange),
)

@Composable
fun BodyWeightScreen(
    bodyWeight: List<BodyWeightUiRow>,
    weightUnit: String = "kg",
    goalWeight: Double? = null,
    hapticFeedback: Boolean = true,
    onBack: () -> Unit = {},
    onLogBodyWeight: suspend (BodyMeasurementInput) -> Unit,
    onDeleteBodyWeightEntry: suspend (String) -> Unit,
    onSetGoalWeight: suspend (Double?) -> Unit = {},
) {
    val colors = useTheme()
    val context = LocalContext.current
    var weightInput by remember { mutableStateOf("") }
    var goalInput by remember { mutableStateOf(bodyWeightInputTextFromKg(goalWeight, weightUnit)) }
    var range by remember { mutableStateOf("30D") }
    var error by remember { mutableStateOf<String?>(null) }
    val allEntriesAsc = remember(bodyWeight) { normalizeBodyWeightEntries(bodyWeight.map { RawBodyWeightEntry(it.id, it.weight, it.date) }) }
    val selectedEntriesAsc = remember(allEntriesAsc, range) { filterEntriesByRange(allEntriesAsc, range) }
    val chartEntries = remember(selectedEntriesAsc) { downsampleSeries(selectedEntriesAsc) }
    val summary = remember(allEntriesAsc, selectedEntriesAsc) { calculateBodyWeightSummary(allEntriesAsc, selectedEntriesAsc) }
    val cards = remember(summary, weightUnit) { summaryCards(summary, weightUnit) }
    val historyDesc = remember(allEntriesAsc) { allEntriesAsc.sortedByDescending { it.timestamp } }
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository() }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }  // GAP-09: delete confirmation
    var heightCm by remember { mutableStateOf<Double?>(null) }
    var heightInput by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val stored = settingsRepo.getString("user_height_cm")?.toDoubleOrNull()
        heightCm = stored
        if (stored != null) heightInput = java.lang.String.format(Locale.US, "%.0f", stored)
    }

    // Body-weight rows and analytics are canonical kilograms regardless of display unit.
    val currentWeightKg = summary.currentWeight
    val bmi = remember(currentWeightKg, heightCm) {
        val w = currentWeightKg; val h = heightCm
        if (w != null && h != null && h > 0) w / ((h / 100.0) * (h / 100.0)) else null
    }
    fun bmiCategory(b: Double) = when {
        b < 18.5 -> "Underweight"
        b < 25.0 -> "Normal"
        b < 30.0 -> "Overweight"
        else     -> "Obese"
    }

    // Sync goal input field when prop changes (e.g. initial load)
    LaunchedEffect(goalWeight, weightUnit) {
        goalInput = bodyWeightInputTextFromKg(goalWeight, weightUnit)
    }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {
        ScreenHeader(title = "BODY WEIGHT", onBack = onBack)
        CardBlock("LOG BODY WEIGHT") {
            Row(horizontalArrangement = appSpacedBy(10.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    placeholder = { Text("0.0 $weightUnit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(onClick = {
                    val weightKg = bodyWeightInputToKg(weightInput.toDoubleOrNull(), weightUnit)
                    if (weightKg == null || weightKg < 20.0 || weightKg > 400.0) {
                        error = "Please enter a value between ${formatWeightValue(20.0, weightUnit)} and ${formatWeightValue(400.0, weightUnit)}."
                        return@Button
                    }
                    scope.launch {
                        onLogBodyWeight(BodyMeasurementInput(measuredAt = System.currentTimeMillis(), bodyweight = weightKg))
                        weightInput = ""
                    }
                }) { Text("LOG") }
            }
            Spacer(Modifier.height(appGapDp(10.dp)))
            Row(horizontalArrangement = appSpacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    placeholder = { Text("Goal ($weightUnit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Goal weight") },
                )
                Button(onClick = {
                    val rawGoal = goalInput.trim()
                    val goalKg = bodyWeightInputToKg(rawGoal.toDoubleOrNull(), weightUnit)
                    if (rawGoal.isNotEmpty() && (goalKg == null || goalKg < 20.0 || goalKg > 400.0)) {
                        error = "Goal must be between ${formatWeightValue(20.0, weightUnit)} and ${formatWeightValue(400.0, weightUnit)}."
                        return@Button
                    }
                    scope.launch { onSetGoalWeight(goalKg) }
                }) { Text("SET") }
            }
            // GAP-13: predicted goal-reach date
            val current = summary.currentWeight
            val trend = summary.weeklyTrend
            if (current != null && goalWeight != null && trend != null && trend != 0.0) {
                val delta = goalWeight - current
                val weeksToGoal = if ((delta > 0) == (trend > 0)) (abs(delta / trend)).toInt().coerceAtLeast(1) else null
                Spacer(Modifier.height(appGapDp(6.dp)))
                if (weeksToGoal != null) {
                    Text(
                        "At current rate: ~$weeksToGoal week${if (weeksToGoal == 1) "" else "s"} to goal",
                        color = colors.success,
                        fontSize = IronLogType.meta.fontSize.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                } else {
                    Text(
                        "Trend moving away from goal",
                        color = colors.warning,
                        fontSize = IronLogType.meta.fontSize.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                }
            }
        }
        Row(horizontalArrangement = appSpacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BODY_WEIGHT_RANGES.forEach { option -> FilterChip(selected = option == range, onClick = { range = option }, label = { Text(option) }) }
        }
        Spacer(Modifier.height(appGapDp(12.dp)))
        Column(verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(12.dp)) {
            cards.chunked(2).forEach { row ->
                Row(horizontalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { card -> SummaryCardView(card, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(appGapDp(16.dp, com.ironlog.app.ui.theme.SpacingRole.CARDS)))
        // BMI card
        CardBlock("BMI") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = appSpacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = heightInput,
                    onValueChange = { heightInput = it },
                    placeholder = { Text("Height (cm)") },
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(onClick = {
                    val h = heightInput.trim().toDoubleOrNull()
                    if (h != null && h in 50.0..300.0) {
                        heightCm = h
                        scope.launch { settingsRepo.setString("user_height_cm", h.toString()) }
                    }
                }) { Text("SET") }
            }
            if (bmi != null) {
                Spacer(Modifier.height(appGapDp(12.dp)))
                val cat = bmiCategory(bmi)
                val bmiColor = when (cat) {
                    "Underweight" -> colors.warning
                    "Normal"      -> colors.success
                    "Overweight"  -> colors.warning
                    else          -> colors.danger
                }
                Row(horizontalArrangement = appSpacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("BMI", color = colors.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 1.5.sp)
                        Text(java.lang.String.format(Locale.US, "%.1f", bmi), color = bmiColor, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight.Black)
                    }
                    Column {
                        Text("CATEGORY", color = colors.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 1.5.sp)
                        Text(cat, color = bmiColor, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Spacer(Modifier.height(appGapDp(8.dp)))
                Text(
                    if (heightCm == null) "Enter your height to calculate BMI." else "Log a body weight entry to calculate BMI.",
                    color = colors.muted,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
        }
        TextButton(onClick = {
            ShareService.shareMinimalCardImage(
                context = context,
                title = "Ironlog Bodyweight",
                headline = formatWeightValue(summary.currentWeight, weightUnit),
                metrics = listOf(
                    ShareService.ShareMetric("Weekly trend", formatSigned(summary.weeklyTrend, weightUnit)),
                    ShareService.ShareMetric("Month change", formatSigned(summary.monthChange, weightUnit)),
                    ShareService.ShareMetric("Total change", formatSigned(summary.totalChange, weightUnit)),
                ),
            )
        }) { Text("Share Progress") }
        CardBlock("WEIGHT TREND ($range · ${canonicalBodyWeightUnit(weightUnit).uppercase(Locale.US)})") {
            WeightChart(chartEntries, weightUnit, goalWeight)
        }
        CardBlock("HISTORY") {
            if (historyDesc.isEmpty()) Text("No body weight entries logged yet.", color = colors.muted)
            historyDesc.forEach { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(formatWeightValue(entry.weight, weightUnit), color = colors.text); Text(formatHistoryDate(entry.timestamp), color = colors.subtext, fontSize = IronLogType.meta.fontSize.sp) }
                    // GAP-09: confirm before deleting
                    Text("Delete", color = colors.muted, modifier = Modifier.clickable { pendingDeleteId = entry.id })
                }
            }
        }
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton({ error = null }) { Text("OK") } }, title = { Text("Invalid weight") }, text = { Text(it) }) }

    // GAP-09: delete confirmation dialog
    pendingDeleteId?.let { idToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete entry?") },
            text = { Text("This body weight entry will be permanently removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = idToDelete
                    pendingDeleteId = null
                    scope.launch { onDeleteBodyWeightEntry(id) }
                }) { Text("DELETE", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("CANCEL") } },
        )
    }
}

@Composable private fun CardBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = useTheme()
    Column(Modifier.fillMaxWidth().padding(bottom = appGapDp(12.dp, com.ironlog.app.ui.theme.SpacingRole.CARDS)).background(colors.card, RoundedCornerShape(16.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp)).appPadding(16.dp)) {
        Text(title, color = colors.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp)
        Spacer(Modifier.height(appGapDp(12.dp))); content()
    }
}
@Composable private fun SummaryCardView(card: SummaryCard, modifier: Modifier = Modifier) {
    val colors = useTheme()
    val valueColor = if (card.raw == null || abs(card.raw) < 0.05) colors.subtext else if (card.raw < 0) colors.success else colors.warning
    Column(modifier.background(colors.card, RoundedCornerShape(14.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp)).padding(12.dp)) { Text(card.label, color = colors.muted, fontSize = IronLogType.eyebrow.fontSize.sp); Text(card.value, color = valueColor, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Black) }
}
private fun weightCatmullRomPath(pts: List<Offset>, tension: Float = 0.5f): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrElse(i - 1) { pts[i] }
        val p1 = pts[i]; val p2 = pts[i + 1]
        val p3 = pts.getOrElse(i + 2) { pts[i + 1] }
        val cp1x = p1.x + (p2.x - p0.x) * tension / 3f; val cp1y = p1.y + (p2.y - p0.y) * tension / 3f
        val cp2x = p2.x - (p3.x - p1.x) * tension / 3f; val cp2y = p2.y - (p3.y - p1.y) * tension / 3f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

private fun weightCatmullRomFillPath(pts: List<Offset>, baselineY: Float, tension: Float = 0.5f): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, baselineY); path.lineTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrElse(i - 1) { pts[i] }
        val p1 = pts[i]; val p2 = pts[i + 1]
        val p3 = pts.getOrElse(i + 2) { pts[i + 1] }
        val cp1x = p1.x + (p2.x - p0.x) * tension / 3f; val cp1y = p1.y + (p2.y - p0.y) * tension / 3f
        val cp2x = p2.x - (p3.x - p1.x) * tension / 3f; val cp2y = p2.y - (p3.y - p1.y) * tension / 3f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    path.lineTo(pts.last().x, baselineY); path.close()
    return path
}

@Composable private fun WeightChart(entries: List<NormalizedBodyWeightEntry>, unit: String, goalWeight: Double? = null) {
    val colors = useTheme()
    if (entries.isEmpty()) { Box(Modifier.fillMaxWidth().height(218.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("No body weight entries for this range yet.", color = colors.muted) }; return }
    val ma = remember(entries) { computeMovingAverage(entries) }

    fun colorToArgb(c: androidx.compose.ui.graphics.Color) = android.graphics.Color.argb(
        (c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()
    )
    // Hoist Paint objects out of the draw lambda — allocating them inside
    // Canvas.DrawScope causes a new object per frame which triggers garbage collection pressure.
    val axisPaint = rememberTypographyPaint().also {
        it.textSize = 28f
        it.color = colorToArgb(colors.muted)
    }
    // goalLabelPaint is mutated (color + align) inside the draw lambda — create once, update properties.
    val goalLabelPaint = rememberTypographyPaint().also { it.textSize = 24f }
    // formatChartAxisDate is a pure function — no need to remember it


    Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT.dp)) {
        val plotWidth = size.width - PLOT.left - PLOT.right
        val plotHeight = size.height - PLOT.top - PLOT.bottom
        val values = entries.map { it.weight } + ma + listOfNotNull(goalWeight)
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 1.0
        val baseRange = max - min
        val pad = if (baseRange < 0.6) 0.4 else baseRange * 0.15
        val yMin = min - pad
        val yMax = max + pad
        val yRange = (yMax - yMin).takeIf { it != 0.0 } ?: 1.0
        val points = entries.mapIndexed { i, e ->
            val x = if (entries.size == 1) PLOT.left + plotWidth / 2 else PLOT.left + (i.toFloat() / (entries.size - 1)) * plotWidth
            val y = PLOT.top + (((yMax - e.weight) / yRange).toFloat()) * plotHeight
            Offset(x, y)
        }
        val baselineY = PLOT.top + plotHeight

        // Horizontal grid lines
        val yTickCount = 4
        for (i in 0..yTickCount) {
            val frac = i.toFloat() / yTickCount
            val lineY = PLOT.top + plotHeight * (1f - frac)
            drawLine(colors.faint, Offset(PLOT.left, lineY), Offset(size.width - PLOT.right, lineY), 1f)
        }

        // Gradient fill under Catmull-Rom curve
        drawPath(
            weightCatmullRomFillPath(points, baselineY),
            brush = Brush.verticalGradient(
                colors = listOf(colors.accent.copy(alpha = 0.28f), Color.Transparent),
                startY = PLOT.top,
                endY = baselineY,
            ),
        )
        // Smooth Catmull-Rom main line
        drawPath(weightCatmullRomPath(points), colors.accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        points.forEach { drawCircle(colors.accent, radius = 4.5f, center = it) }
        // 7-day moving average line (dashed, also curved)
        if (ma.size >= 3) {
            val maPoints = ma.mapIndexed { i, v ->
                val x = if (entries.size == 1) PLOT.left + plotWidth / 2 else PLOT.left + (i.toFloat() / (entries.size - 1)) * plotWidth
                val y = PLOT.top + (((yMax - v) / yRange).toFloat()) * plotHeight
                Offset(x, y)
            }
            drawPath(weightCatmullRomPath(maPoints), colors.accent.copy(alpha = 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 4f))))
        }

        // Goal weight horizontal dashed line — always drawn, clamped to chart area if out of range
        if (goalWeight != null) {
            val inRange = goalWeight >= yMin && goalWeight <= yMax
            val clampedGoal = goalWeight.coerceIn(yMin, yMax)
            val goalY = PLOT.top + (((yMax - clampedGoal) / yRange).toFloat()) * plotHeight
            val lineAlpha = if (inRange) 0.85f else 0.50f
            drawLine(
                color = colors.success.copy(alpha = lineAlpha),
                start = Offset(PLOT.left, goalY),
                end = Offset(size.width - PLOT.right, goalY),
                strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
            )
            // Mutate the remembered goalLabelPaint rather than allocating a new Paint per draw frame.
            goalLabelPaint.color = android.graphics.Color.argb(
                (colors.success.alpha * 255 * lineAlpha).toInt(),
                (colors.success.red * 255).toInt(),
                (colors.success.green * 255).toInt(),
                (colors.success.blue * 255).toInt(),
            )
            goalLabelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            val suffix = when {
                goalWeight > yMax -> " ▲"
                goalWeight < yMin -> " ▼"
                else -> ""
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Goal: ${formatWeightFromKg(goalWeight, canonicalBodyWeightUnit(unit))}$suffix",
                size.width - PLOT.right - 4f,
                goalY - 6f,
                goalLabelPaint,
            )
        }

        // Y-axis labels (right-aligned against the left margin)
        val nc = drawContext.canvas.nativeCanvas
        axisPaint.textAlign = android.graphics.Paint.Align.RIGHT
        for (i in 0..yTickCount) {
            val frac = i.toFloat() / yTickCount
            val valueKg = yMin + (yMax - yMin) * frac
            val displayValue = convertKgToUnit(valueKg, canonicalBodyWeightUnit(unit), decimals = 1)
            val lineY = PLOT.top + plotHeight * (1f - frac)
            nc.drawText(
                String.format(Locale.US, "%.1f", displayValue),
                PLOT.left - 6f,
                lineY + axisPaint.textSize / 3f,
                axisPaint,
            )
        }

        // X-axis date labels: first, middle (if ≥3 pts), last
        axisPaint.textAlign = android.graphics.Paint.Align.LEFT
        val xLabelY = size.height - 2f
        if (entries.isNotEmpty()) {
            nc.drawText(
                formatChartAxisDate(Instant.fromEpochMilliseconds(entries.first().timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date),
                points.first().x,
                xLabelY,
                axisPaint,
            )
        }
        if (entries.size >= 3) {
            val midIdx = entries.size / 2
            axisPaint.textAlign = android.graphics.Paint.Align.CENTER
            nc.drawText(
                formatChartAxisDate(Instant.fromEpochMilliseconds(entries[midIdx].timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date),
                points[midIdx].x,
                xLabelY,
                axisPaint,
            )
        }
        if (entries.size >= 2) {
            axisPaint.textAlign = android.graphics.Paint.Align.RIGHT
            nc.drawText(
                formatChartAxisDate(Instant.fromEpochMilliseconds(entries.last().timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date),
                points.last().x,
                xLabelY,
                axisPaint,
            )
        }
    }
}

