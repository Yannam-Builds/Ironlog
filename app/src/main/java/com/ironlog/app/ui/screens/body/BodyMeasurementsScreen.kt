package com.ironlog.app.ui.screens.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironlog.app.data.repository.BodyMeasurementInput
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.BodyMeasurementUiRow
import com.ironlog.app.ui.viewmodel.BodyWeightUiRow
import com.ironlog.app.util.convertUnitToKg
import com.ironlog.app.util.formatWeightFromKg
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class MeasurementField(val key: String, val label: String, val unit: String)
private val MEASUREMENT_FIELDS = listOf(
    MeasurementField("chest", "Chest", "cm"),
    MeasurementField("waist", "Waist", "cm"),
    MeasurementField("hips", "Hips", "cm"),
    MeasurementField("shoulders", "Shoulders", "cm"),
    MeasurementField("neck", "Neck", "cm"),
    MeasurementField("arms", "Arms", "cm"),
    MeasurementField("thighs", "Thighs", "cm"),
    MeasurementField("calves", "Calves", "cm"),
    MeasurementField("bodyFat", "Body Fat", "%"),
)

private val INCREASE_GOOD = setOf("chest", "shoulders", "arms", "thighs", "calves")

data class MeasurementDisplayRow(
    val id: String,
    val date: String,
    val values: Map<String, Double?>,
)

fun parseMeasurementExtras(notes: String?): Map<String, Double> {
    if (notes.isNullOrBlank() || !notes.startsWith("{")) return emptyMap()
    return runCatching {
        Json.parseToJsonElement(notes).jsonObject.mapNotNull { (k, v) ->
            v.jsonPrimitive.doubleOrNull?.let { k to it }
        }.toMap()
    }.getOrDefault(emptyMap())
}

fun mapMeasurementRow(row: BodyMeasurementUiRow): MeasurementDisplayRow {
    val extras = parseMeasurementExtras(row.notes)
    return MeasurementDisplayRow(
        id = row.id,
        date = row.date,
        values = mapOf(
            "chest" to row.chest,
            "waist" to row.waist,
            "arms" to row.arm,
            "thighs" to row.thigh,
            "hips" to extras["hips"],
            "shoulders" to extras["shoulders"],
            "neck" to extras["neck"],
            "calves" to extras["calves"],
            "bodyFat" to extras["bodyFat"],
        ),
    )
}

private fun getCurrentAndDelta(fieldKey: String, rows: List<MeasurementDisplayRow>): Pair<Double?, Double?> {
    val entries = rows.filter { it.values[fieldKey] != null }
    val current = entries.firstOrNull()?.values?.get(fieldKey)
    val prev = if (entries.size >= 2) entries[1].values[fieldKey] else null
    val delta = if (current != null && prev != null) current - prev else null
    return Pair(current, delta)
}

private fun getMiniChartData(fieldKey: String, rows: List<MeasurementDisplayRow>): List<Double> =
    rows.filter { it.values[fieldKey] != null }.take(20).reversed().mapNotNull { it.values[fieldKey] }

private fun formatBWHistoryDate(dateStr: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd MMM", Locale.UK)
        .format(Instant.parse(dateStr).atZone(ZoneId.systemDefault()))
}.getOrElse { dateStr.substringBefore('T') }

@Composable
fun BodyMeasurementsScreen(
    measurements: List<BodyMeasurementUiRow>,
    bodyWeight: List<BodyWeightUiRow>,
    weightUnit: String = "kg",
    hapticFeedback: Boolean = true,
    onBack: () -> Unit = {},
    onLogBodyWeight: suspend (BodyMeasurementInput) -> Unit,
    onAddMeasurement: suspend (BodyMeasurementInput) -> Unit,
) {
    val colors = useTheme()
    var activeTab by remember { mutableIntStateOf(0) }
    var weightInput by remember { mutableStateOf("") }
    var bodyFatInput by remember { mutableStateOf("") }
    var showAddMeasurements by remember { mutableStateOf(false) }
    var alert by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val measurementRows = remember(measurements) {
        measurements.filter { it.bodyweight == null }.map { mapMeasurementRow(it) }
    }
    val recentWeights = remember(bodyWeight) { bodyWeight.take(30).reversed() }
    val weightDelta = remember(bodyWeight) {
        val current = bodyWeight.firstOrNull()
        if (current == null) {
            null
        } else {
            // Find the entry whose date is closest to 7 calendar days before the most recent entry,
            // within a ±3-day window. Using index-7 is wrong when the user logs infrequently.
            val currentMs = runCatching { Instant.parse(current.date).toEpochMilli() }.getOrNull()
            if (currentMs == null) {
                null
            } else {
                val sevenDaysAgoMs = currentMs - 7L * 24 * 60 * 60 * 1000
                val threeDaysMs = 3L * 24 * 60 * 60 * 1000
                val weekOld = bodyWeight.drop(1)
                    .filter { row ->
                        runCatching {
                            val ms = Instant.parse(row.date).toEpochMilli()
                            kotlin.math.abs(ms - sevenDaysAgoMs) <= threeDaysMs
                        }.getOrDefault(false)
                    }
                    .minByOrNull { row ->
                        runCatching {
                            val ms = Instant.parse(row.date).toEpochMilli()
                            kotlin.math.abs(ms - sevenDaysAgoMs)
                        }.getOrDefault(Long.MAX_VALUE)
                    }
                if (weekOld != null) current.weight - weekOld.weight else null
            }
        }
    }
    val currentBodyFat = remember(measurementRows) { measurementRows.firstOrNull()?.values?.get("bodyFat") }

    // GAP-28: expanded measurement full chart
    var expandedMeasurement by remember { mutableStateOf<String?>(null) }
    var expandedRange by remember { mutableStateOf("All") }

    // Goal state for measurement fields
    var measurementGoals by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(Unit) {
        val settingsRepo = SettingsRepository()
        val goals = mutableMapOf<String, Double>()
        MEASUREMENT_FIELDS.forEach { field ->
            val g = settingsRepo.getString("measure_goal_${field.key}")?.toDoubleOrNull()
            if (g != null) goals[field.key] = g
        }
        measurementGoals = goals
    }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.accent)
            }
            Text(
                "Body tracking",
                color = colors.text,
                fontWeight = FontWeight.Bold,
                fontSize = IronLogType.section.fontSize.sp,
            )
        }
        // Tab bar
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = colors.bg,
            contentColor = colors.accent,
        ) {
            listOf("WEIGHT", "MEASUREMENTS").forEachIndexed { idx, label ->
                Tab(
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    text = {
                        Text(
                            label,
                            fontSize = IronLogType.meta.fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = if (activeTab == idx) colors.accent else colors.muted,
                        )
                    },
                )
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        ) {
            if (activeTab == 0) {
                // ── WEIGHT TAB ───────────────────────────────────────────────────────────

                // Input card
                Column(
                    Modifier.fillMaxWidth()
                        .background(colors.card, RoundedCornerShape(14.dp))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                ) {
                    Text(
                        "TODAY'S WEIGHT (${weightUnit.uppercase()})",
                        color = colors.muted,
                        fontSize = IronLogType.eyebrow.fontSize.sp,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    currentBodyFat?.let { bf ->
                        Text(
                            "Body fat: ${java.lang.String.format(Locale.US, "%.1f", bf)}%",
                            color = colors.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("0.0") },
                            textStyle = TextStyle(fontSize = IronLogType.metric.fontSize.sp, fontWeight = FontWeight(IronLogType.metric.fontWeight)),
                        )
                        Button(
                            onClick = {
                                val inputWeight = weightInput.toDoubleOrNull()
                                val w = inputWeight?.let { convertUnitToKg(it, weightUnit, 2) }
                                if (inputWeight == null || w == null || w < 20.0 || w > 300.0) {
                                    alert = "Enter a value between ${formatWeightFromKg(20.0, weightUnit)} and ${formatWeightFromKg(300.0, weightUnit)}."
                                    return@Button
                                }
                                val bfValue = bodyFatInput.trim().toDoubleOrNull()
                                scope.launch {
                                    onLogBodyWeight(BodyMeasurementInput(measuredAt = System.currentTimeMillis(), bodyweight = w))
                                    weightInput = ""
                                    if (bfValue != null && bfValue > 0.0) {
                                        onAddMeasurement(BodyMeasurementInput(measuredAt = System.currentTimeMillis(), notes = "{\"bodyFat\": $bfValue}"))
                                        bodyFatInput = ""
                                    }
                                }
                            },
                            modifier = Modifier.height(56.dp),
                        ) {
                            Text("LOG", fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bodyFatInput,
                        onValueChange = { bodyFatInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("0.0") },
                        label = { Text("Body fat % (optional)") },
                    )
                    weightDelta?.let { delta ->
                        Spacer(Modifier.height(8.dp))
                        val sign = if (delta > 0) "+" else ""
                        Text(
                            "$sign${formatWeightFromKg(abs(delta), weightUnit)} vs last week",
                            color = if (delta <= 0) colors.success else colors.warning,
                            fontSize = IronLogType.body.fontSize.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Chart card
                if (recentWeights.size >= 2) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(colors.card, RoundedCornerShape(14.dp))
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("LAST 30 DAYS", color = colors.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        BMMiniWeightChart(recentWeights, weightUnit)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // History rows (last 14)
                bodyWeight.take(14).forEach { e ->
                    Row(
                        Modifier.fillMaxWidth()
                            .border(width = 1.dp, color = colors.faint, shape = RoundedCornerShape(0.dp))
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatBWHistoryDate(e.date), color = colors.subtext, fontSize = IronLogType.body.fontSize.sp)
                        Text(
                            formatWeightFromKg(e.weight, weightUnit),
                            color = colors.text,
                            fontSize = IronLogType.section.fontSize.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                // ── MEASUREMENTS TAB ─────────────────────────────────────────────────────

                Button(
                    onClick = { showAddMeasurements = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("+ ADD MEASUREMENT", fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(20.dp))

                // 2-column grid
                MEASUREMENT_FIELDS.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        row.forEach { field ->
                            MeasurementFieldCard(
                                field = field,
                                rows = measurementRows,
                                goal = measurementGoals[field.key],
                                onGoalSaved = { newGoal ->
                                    measurementGoals = measurementGoals.toMutableMap().also { map ->
                                        if (newGoal != null) map[field.key] = newGoal else map.remove(field.key)
                                    }
                                },
                                onExpandTap = {
                                    expandedRange = "All"
                                    expandedMeasurement = field.key
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                // Last logged
                if (measurementRows.isNotEmpty()) {
                    Text(
                        "Last logged: ${formatBWHistoryDate(measurementRows.first().date)}",
                        color = colors.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }

    if (showAddMeasurements) {
        AddMeasurementBottomSheet(
            onDismiss = { showAddMeasurements = false },
            onSave = { values ->
                val hasAny = MEASUREMENT_FIELDS.any { !values[it.key].isNullOrBlank() }
                if (!hasAny) {
                    alert = "Enter at least one measurement value."
                    return@AddMeasurementBottomSheet
                }
                val parsed = values
                    .mapValues { it.value.toDoubleOrNull() }
                    .filterValues { it != null && it > 0.0 }
                    .mapValues { it.value!! }
                val extrasEntries = listOf("hips", "shoulders", "neck", "calves", "bodyFat")
                    .mapNotNull { key -> parsed[key]?.let { key to JsonPrimitive(it) } }
                    .toMap()
                val notes = if (extrasEntries.isNotEmpty()) JsonObject(extrasEntries).toString() else ""
                scope.launch {
                    onAddMeasurement(
                        BodyMeasurementInput(
                            measuredAt = System.currentTimeMillis(),
                            bodyweight = null,
                            chest = parsed["chest"],
                            waist = parsed["waist"],
                            arm = parsed["arms"],
                            thigh = parsed["thighs"],
                            notes = notes,
                        ),
                    )
                    showAddMeasurements = false
                }
            },
        )
    }

    alert?.let {
        AlertDialog(
            onDismissRequest = { alert = null },
            confirmButton = { TextButton({ alert = null }) { Text("OK") } },
            title = { Text(if (activeTab == 0) "Invalid weight" else "No data") },
            text = { Text(it) },
        )
    }

    // GAP-28: Full trend chart dialog for expanded measurement
    expandedMeasurement?.let { fieldKey ->
        val field = MEASUREMENT_FIELDS.find { it.key == fieldKey }
        if (field != null) {
            MeasurementTrendDialog(
                field = field,
                rows = measurementRows,
                selectedRange = expandedRange,
                onRangeChange = { expandedRange = it },
                onDismiss = { expandedMeasurement = null },
            )
        }
    }
}

@Composable
private fun MeasurementFieldCard(
    field: MeasurementField,
    rows: List<MeasurementDisplayRow>,
    goal: Double?,
    onGoalSaved: (Double?) -> Unit,
    onExpandTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = useTheme()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // Hoist SettingsRepository so it is created once per card, not on every keypress / focus change.
    val settingsRepo = remember { SettingsRepository() }
    val (current, delta) = remember(field.key, rows) { getCurrentAndDelta(field.key, rows) }
    val chartData = remember(field.key, rows) { getMiniChartData(field.key, rows) }
    val deltaColor = when {
        delta == null || abs(delta) < 0.05 -> colors.subtext
        INCREASE_GOOD.contains(field.key) -> if (delta > 0) colors.success else colors.warning
        else -> if (delta < 0) colors.success else colors.warning
    }

    // Goal input state — pre-fill from the persisted goal
    var goalInput by remember(goal) { mutableStateOf(goal?.let { java.lang.String.format(Locale.US, "%.1f", it) } ?: "") }

    fun persistGoal(input: String) {
        val parsed = input.trim().toDoubleOrNull()
        scope.launch {
            if (parsed != null && parsed > 0.0) {
                settingsRepo.setString("measure_goal_${field.key}", parsed.toString())
                onGoalSaved(parsed)
            } else if (input.trim().isEmpty()) {
                settingsRepo.setString("measure_goal_${field.key}", "")
                onGoalSaved(null)
            }
        }
    }

    Column(
        modifier
            .background(colors.card, RoundedCornerShape(14.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .defaultMinSize(minHeight = 90.dp),
    ) {
        Text(field.label.uppercase(), color = colors.muted, fontSize = IronLogType.micro.fontSize.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        if (current == null) {
            Text("—", color = colors.faint, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight.Light)
        } else {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    java.lang.String.format(Locale.US, "%.1f", current),
                    color = colors.text,
                    fontSize = IronLogType.title.fontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(field.unit, color = colors.subtext, fontSize = IronLogType.meta.fontSize.sp)
                delta?.let {
                    val sign = if (it > 0) "+" else ""
                    Text(
                        "$sign${java.lang.String.format(Locale.US, "%.1f", it)}",
                        color = deltaColor,
                        fontSize = IronLogType.meta.fontSize.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (chartData.size >= 2) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().clickable { onExpandTap() }) {
                    MeasurementMiniChart(chartData, colors.accent)
                    // Subtle expand hint
                    Text(
                        "Tap to expand",
                        color = colors.muted.copy(alpha = 0.5f),
                        fontSize = (IronLogType.micro.fontSize - 1).sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                    )
                }
            }
        }

        // ── Goal row ────────────────────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = goalInput,
            onValueChange = { goalInput = it },
            singleLine = true,
            placeholder = { Text("Goal", fontSize = IronLogType.meta.fontSize.sp, color = colors.faint) },
            label = { Text("Goal (${field.unit})", fontSize = IronLogType.micro.fontSize.sp, color = colors.muted) },
            textStyle = TextStyle(fontSize = IronLogType.meta.fontSize.sp, color = colors.text),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    persistGoal(goalInput)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        persistGoal(goalInput)
                    }
                },
        )

        // Delta-to-goal label
        if (current != null && goal != null) {
            val deltaToGoal = goal - current
            val withinFivePercent = goal != 0.0 && abs(deltaToGoal) / abs(goal) <= 0.05
            val goalLabelColor = if (withinFivePercent) colors.success else colors.muted
            val sign = if (deltaToGoal > 0) "+" else ""
            Spacer(Modifier.height(4.dp))
            Text(
                "${sign}${deltaToGoal.roundToInt()} ${field.unit} to goal",
                color = goalLabelColor,
                fontSize = IronLogType.micro.fontSize.sp,
            )
        }
    }
}

@Composable
private fun MeasurementMiniChart(data: List<Double>, accentColor: Color) {
    // Need at least 2 points; a single point causes division-by-zero in the x-position formula.
    if (data.size < 2) return
    val minV = data.minOrNull() ?: 0.0
    val maxV = data.maxOrNull() ?: 1.0
    val range = (maxV - minV).takeIf { it != 0.0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
        val pad = 6f
        val pts = data.mapIndexed { i, v ->
            val x = pad + (i.toFloat() / (data.size - 1)) * (size.width - pad * 2)
            val y = size.height - pad - (((v - minV) / range).toFloat()) * (size.height - pad * 2)
            Offset(x, y)
        }
        val baselineY = size.height - pad
        // Gradient fill under the line
        val fillPath = Path().apply {
            moveTo(pts.first().x, baselineY)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, baselineY)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent),
                startY = pad,
                endY = baselineY,
            ),
        )
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, accentColor, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        drawCircle(accentColor, 3f, pts.last())
    }
}

/** GAP-28: Full-size trend chart dialog for a single measurement field with date-range filter. */
@Composable
private fun MeasurementTrendDialog(
    field: MeasurementField,
    rows: List<MeasurementDisplayRow>,
    selectedRange: String,
    onRangeChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = useTheme()
    val rangeOptions = listOf("30D", "90D", "1Y", "All")
    val cutoffMs = remember(selectedRange) {
        val now = System.currentTimeMillis()
        when (selectedRange) {
            "30D" -> now - 30L * 86_400_000L
            "90D" -> now - 90L * 86_400_000L
            "1Y"  -> now - 365L * 86_400_000L
            else  -> 0L
        }
    }
    val filteredData = remember(field.key, rows, cutoffMs) {
        rows.filter { row ->
            val rowMs = runCatching { java.time.Instant.parse(row.date).toEpochMilli() }.getOrDefault(0L)
            rowMs >= cutoffMs && row.values[field.key] != null
        }.reversed().mapNotNull { it.values[field.key] }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(16.dp))
                .background(c.card)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(field.label.uppercase(), color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 2.sp)
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("CLOSE", color = c.muted) }
            }

            // Range filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rangeOptions.forEach { opt ->
                    val selected = opt == selectedRange
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(IronLogRadius.full.dp))
                            .background(if (selected) c.accent else c.surface)
                            .border(1.dp, if (selected) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                            .clickable { onRangeChange(opt) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(opt, color = if (selected) c.textOnAccent else c.subtext, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Full chart
            if (filteredData.size >= 2) {
                MeasurementMiniChart(filteredData, c.accent)
                Text("${filteredData.size} data points", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
            } else {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("Not enough data for selected range", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                }
            }
        }
    }
}

@Composable
private fun BMMiniWeightChart(rows: List<BodyWeightUiRow>, unit: String) {
    val colors = useTheme()
    if (rows.size < 2) return
    val weights = rows.map { it.weight }
    val minW = (weights.minOrNull() ?: 0.0) - 1.0
    val maxW = (weights.maxOrNull() ?: 1.0) + 1.0
    val range = (maxW - minW).takeIf { it != 0.0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val pad = 20f
        val pts = rows.mapIndexed { i, e ->
            val x = pad + (i.toFloat() / (rows.size - 1)) * (size.width - pad * 2)
            val y = size.height - pad - (((e.weight - minW) / range).toFloat()) * (size.height - pad * 2)
            Offset(x, y)
        }
        val baselineY = size.height - pad
        drawLine(colors.faint, Offset(pad, baselineY), Offset(size.width - pad, baselineY), strokeWidth = 1f)
        // Gradient fill
        val fillPath = Path().apply {
            moveTo(pts.first().x, baselineY)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, baselineY)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(colors.accent.copy(alpha = 0.28f), Color.Transparent),
                startY = pad,
                endY = baselineY,
            ),
        )
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, colors.accent, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawCircle(colors.accent, 4f, pts.last())
    }
}

@Composable
private fun AddMeasurementBottomSheet(
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    val colors = useTheme()
    val values = remember { mutableStateMapOf<String, String>() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(colors.card),
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth()
                        .border(width = 1.dp, color = colors.faint, shape = RoundedCornerShape(0.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "ADD MEASUREMENT",
                        color = colors.text,
                        fontSize = IronLogType.meta.fontSize.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        "✕",
                        color = colors.muted,
                        fontSize = IronLogType.section.fontSize.sp,
                        modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
                    )
                }

                // Field rows
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    MEASUREMENT_FIELDS.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${f.label} (${f.unit})",
                                color = colors.subtext,
                                fontSize = IronLogType.body.fontSize.sp,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = values[f.key] ?: "",
                                onValueChange = { values[f.key] = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                placeholder = { Text("—", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                                textStyle = TextStyle(
                                    fontSize = IronLogType.section.fontSize.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                ),
                            )
                        }
                        HorizontalDivider(color = colors.faint)
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { onSave(values) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("SAVE", fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

