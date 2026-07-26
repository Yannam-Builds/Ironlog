package com.ironlog.app.ui.screens.stats

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.ui.viewmodel.ExerciseProgressViewModel
import com.ironlog.app.ui.viewmodel.ExerciseProgressViewModelFactory
import com.ironlog.app.services.ShareService
import com.ironlog.app.util.convertKgToUnit
import com.ironlog.app.util.formatVolumeFromKg
import com.ironlog.app.util.formatWeightFromKg
import java.time.Instant
import androidx.compose.runtime.rememberCoroutineScope
import com.ironlog.app.domain.gamification.parseHistoryInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.io.File
import kotlin.math.round

private val TABS = listOf("E1RM", "LOAD", "REPS", "VOLUME", "CONSISTENCY", "HISTORY")
private val RANGES = listOf("90D" to 90, "6M" to 180, "1Y" to 365, "ALL" to null)

data class ExerciseTrendRow(
    val date: String,
    val e1rm: Double,
    val load: Double,
    val reps: Double,
    val volume: Double,
    val consistency: Double = 1.0,
)

private data class HistorySessionRow(
    val date: String,
    val summary: String,
    val bestSetStr: String?,
    val volumeStr: String,
)

private data class MetricConfig(
    val label: String,
    val unit: String,
    val values: List<Double>,
    val stat: Double,
    val isBar: Boolean = false,
)

fun filterExerciseRowsByRange(rows: List<ExerciseTrendRow>, days: Int?): List<ExerciseTrendRow> {
    if (days == null) return rows
    val cutoff = System.currentTimeMillis() - days * 86400000L
    return rows.filter { runCatching { Instant.parse(it.date).toEpochMilli() >= cutoff }.getOrDefault(false) }
}

fun roundMetric(value: Double, digits: Int = 1): Double {
    if (!value.isFinite()) return 0.0
    val mult = Math.pow(10.0, digits.toDouble())
    return round(value * mult) / mult
}

fun buildExerciseTrendLocal(history: List<HistoryEntry>, exerciseName: String): List<ExerciseTrendRow> {
    val target = exerciseName.lowercase(Locale.US).trim()
    return history.flatMap { h ->
        h.exercises.filter { it.name.lowercase(Locale.US).trim() == target }.mapNotNull { ex ->
            val workingSets = ex.sets.filter { it.type != "warmup" }
            if (workingSets.isEmpty()) null else {
                val best = workingSets.maxByOrNull { it.weight * (1.0 + it.reps / 30.0) }!!
                ExerciseTrendRow(
                    date = h.date,
                    e1rm = best.weight * (1.0 + best.reps / 30.0),
                    load = workingSets.maxOf { it.weight },
                    reps = workingSets.maxOf { it.reps },
                    volume = workingSets.sumOf { it.weight * it.reps },
                    consistency = workingSets.size.toDouble(),
                )
            }
        }
    }.sortedBy { it.date }
}

private fun buildSessionHistoryRows(
    history: List<HistoryEntry>,
    exerciseName: String,
    weightUnit: String,
): List<HistorySessionRow> {
    val target = exerciseName.lowercase(Locale.US).trim()
    return history.mapNotNull { session ->
        val exercise = session.exercises.find { it.name.lowercase(Locale.US).trim() == target }
            ?: return@mapNotNull null
        val workingSets = exercise.sets.filter { it.type != "warmup" }
        val summary = if (workingSets.isEmpty()) "-" else
            workingSets.joinToString(", ") { "${it.reps.toInt()}×${formatWeightFromKg(it.weight, weightUnit)}" }
        val bestSet = workingSets.maxByOrNull { it.weight }
        val bestSetStr = bestSet?.let { "${it.reps.toInt()} × ${formatWeightFromKg(it.weight, weightUnit)}" }
        val volume = workingSets.sumOf { it.weight * it.reps }
        HistorySessionRow(
            date = session.date,
            summary = summary,
            bestSetStr = bestSetStr,
            volumeStr = formatVolumeFromKg(volume, weightUnit),
        )
    }.sortedByDescending { it.date }
}

private fun computeMetricConfig(rows: List<ExerciseTrendRow>, activeTab: Int, weightUnit: String): MetricConfig =
    when (TABS.getOrNull(activeTab)) {
        "E1RM" -> {
            val values = rows.map { convertKgToUnit(it.e1rm, weightUnit, 1) }
            MetricConfig("BEST EST. 1RM", weightUnit, values, values.maxOrNull() ?: 0.0)
        }
        "LOAD" -> {
            val values = rows.map { convertKgToUnit(it.load, weightUnit, 1) }
            MetricConfig("TOP LOAD", weightUnit, values, values.maxOrNull() ?: 0.0)
        }
        "REPS" -> {
            val values = rows.map { it.reps }
            MetricConfig("AVG REPS/SET", "", values,
                if (values.isEmpty()) 0.0 else values.average().let { if (it.isFinite()) it else 0.0 })
        }
        "VOLUME" -> {
            val values = rows.map { convertKgToUnit(it.volume, weightUnit, 0) }
            MetricConfig("SESSION VOLUME", weightUnit, values, values.sumOf { it }, isBar = true)
        }
        "CONSISTENCY" -> {
            // Group sessions by ISO week and count sessions per week
            val zone = ZoneId.systemDefault()
            val weekFields = WeekFields.ISO
            val byWeek = rows.groupBy { row ->
                val d = parseHistoryInstant(row.date)?.atZone(zone)?.toLocalDate() ?: return@groupBy "unknown"
                "${d.get(weekFields.weekBasedYear())}-W${d.get(weekFields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
            }
            // Sort weeks chronologically and emit one value per week
            val sortedWeeks = byWeek.keys.sorted()
            val values = sortedWeeks.map { byWeek[it]!!.size.toDouble() }
            val avgPerWeek = if (values.isEmpty()) 0.0 else values.average().let { if (it.isFinite()) it else 0.0 }
            MetricConfig("SESSIONS / WEEK", "×", values, avgPerWeek, isBar = true)
        }
        else -> MetricConfig("", "", emptyList(), 0.0)
    }

// ── Main Screen ───────────────────────────────────────────────────────────────

@Composable
fun ExerciseProgressScreen(
    exerciseName: String,
    weightUnit: String = "kg",
    onBack: () -> Unit = {},
    vm: ExerciseProgressViewModel = viewModel(factory = ExerciseProgressViewModelFactory(exerciseName)),
) {
    val colors = useTheme()
    val context = LocalContext.current
    val csvScope = rememberCoroutineScope()
    val history by vm.history.collectAsState()
    var activeTab by remember { mutableIntStateOf(0) }
    var range by remember { mutableStateOf("ALL") }
    var showTrainingMax by remember { mutableStateOf(false) }

    val trend = remember(history, exerciseName) { buildExerciseTrendLocal(history, exerciseName) }
    val rows = remember(trend, range) {
        filterExerciseRowsByRange(trend, RANGES.first { it.first == range }.second)
    }
    val historyRows = remember(history, exerciseName, weightUnit) {
        buildSessionHistoryRows(history, exerciseName, weightUnit)
    }
    val activeMetric = remember(rows, activeTab, weightUnit) {
        computeMetricConfig(rows, activeTab, weightUnit)
    }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {

        // ── Header ──────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = colors.faint,
                    shape = RoundedCornerShape(0.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text(
                exerciseName,
                color = colors.text,
                fontWeight = FontWeight(IronLogType.section.fontWeight),
                fontSize = IronLogType.section.fontSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // TM Calculator button
            TextButton(onClick = { showTrainingMax = true }) {
                Text("CALC TM", color = colors.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            // CSV export button
            IconButton(onClick = {
                val snapshot = rows.toList()
                csvScope.launch {
                    val sb = StringBuilder("Date,Weight ($weightUnit),Reps,Volume ($weightUnit),e1RM ($weightUnit)\n")
                    snapshot.forEach { r ->
                        sb.append("${r.date.substringBefore('T')},")
                        sb.append("${convertKgToUnit(r.load, weightUnit)},")
                        sb.append("${r.reps.toInt()},")
                        sb.append("${convertKgToUnit(r.volume, weightUnit)},")
                        sb.append("${convertKgToUnit(r.e1rm, weightUnit)}\n")
                    }
                    val file = withContext(Dispatchers.IO) {
                        File(context.cacheDir, "ironlog_${exerciseName.replace(" ", "_")}.csv")
                            .also { it.writeText(sb.toString()) }
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    context.startActivity(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "IronLog – $exerciseName history")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                }
            }) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "Export CSV", tint = colors.accent)
            }
            // Share button
            IconButton(onClick = {
                val latest = rows.lastOrNull()
                ShareService.shareMinimalCardImage(
                    context = context,
                    title = "Ironlog Exercise Progress",
                    headline = exerciseName,
                    metrics = listOf(
                        ShareService.ShareMetric("Best e1RM", latest?.let { formatWeightFromKg(it.e1rm, weightUnit) } ?: "-"),
                        ShareService.ShareMetric("Top load", latest?.let { formatWeightFromKg(it.load, weightUnit) } ?: "-"),
                        ShareService.ShareMetric("Sessions", rows.size.toString()),
                    ),
                )
            }) {
                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = colors.accent)
            }
        }

        // ── Tab bar (underline indicator, no filter chips) ───────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = colors.faint, shape = RoundedCornerShape(0.dp)),
        ) {
            TABS.forEachIndexed { i, tab ->
                val active = activeTab == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = i }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tab,
                            color = if (active) colors.accent else colors.muted,
                            fontSize = IronLogType.micro.fontSize.sp,
                            fontWeight = FontWeight(if (active) 800 else 600),
                            letterSpacing = 0.6.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.6f)
                                .height(2.dp)
                                .background(
                                    if (active) colors.accent else Color.Transparent,
                                    RoundedCornerShape(1.dp),
                                ),
                        )
                    }
                }
            }
        }

        // ── Content ─────────────────────────────────────────────────────────
        if (TABS[activeTab] == "HISTORY") {
            if (historyRows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data for this exercise yet.", color = colors.muted, fontSize = IronLogType.body.fontSize.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(historyRows) { row -> SessionHistoryRow(row) }
                }
            }
        } else {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Hero card
                HeroMetricCard(activeMetric, rows.size)

                // Chart — bar for VOLUME, line for everything else
                if (activeMetric.isBar) {
                    BarChartCard(activeMetric.values, rows.map { it.date })
                } else {
                    val prIndices = remember(rows, activeTab) {
                        if (TABS[activeTab] != "E1RM") emptySet()
                        else {
                            val prSet = mutableSetOf<Int>()
                            var maxSoFar = Double.MIN_VALUE
                            rows.forEachIndexed { i, row ->
                                if (row.e1rm > maxSoFar) {
                                    maxSoFar = row.e1rm
                                    prSet.add(i)
                                }
                            }
                            prSet
                        }
                    }
                    LineChartCard(activeMetric.values, rows.map { it.date }, prIndices)
                }

                // Range selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RANGES.forEach { (label, _) ->
                        FilterChip(
                            selected = range == label,
                            onClick = { range = label },
                            label = { Text(label, fontSize = IronLogType.meta.fontSize.sp) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp).navigationBarsPadding())
            }
        }
    }

    // ── Training Max dialog ─────────────────────────────────────────────────
    if (showTrainingMax) {
        val latest = rows.lastOrNull()
        val baseKg = latest?.e1rm ?: 0.0
        AlertDialog(
            onDismissRequest = { showTrainingMax = false },
            title = { Text("Training Max Calculator") },
            text = {
                if (baseKg <= 0.0) {
                    Text("No e1RM data yet for $exerciseName.")
                } else {
                    Text(
                        "Latest e1RM: ${formatWeightFromKg(baseKg, weightUnit)}\n" +
                            "85% TM: ${formatWeightFromKg(baseKg * 0.85, weightUnit)}\n" +
                            "90% TM: ${formatWeightFromKg(baseKg * 0.90, weightUnit)}\n" +
                            "95% TM: ${formatWeightFromKg(baseKg * 0.95, weightUnit)}",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrainingMax = false }) { Text("Done") }
            },
        )
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun HeroMetricCard(metric: MetricConfig, sessionCount: Int) {
    val c = useTheme()
    val statStr = if (metric.unit.isNotEmpty())
        "${roundMetric(metric.stat, if (metric.unit == "%") 0 else 1)} ${metric.unit}"
    else
        roundMetric(metric.stat, 1).toString()
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(IronLogRadius.lg.dp))
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(metric.label, color = c.muted, fontSize = IronLogType.micro.fontSize.sp, letterSpacing = 2.6.sp)
        Text(statStr, color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.display.fontSize.sp)
        Text(
            "$sessionCount session${if (sessionCount == 1) "" else "s"} in selected range",
            color = c.muted,
            fontSize = IronLogType.meta.fontSize.sp,
        )
    }
}

@Composable
private fun LineChartCard(values: List<Double>, dates: List<String>, prIndices: Set<Int> = emptySet()) {
    // prIndices (PR gold-ring markers) are not rendered — Vico 2.x decoration markers
    // require significant custom Renderer work; dropped as acceptable trade-off.
    val c = useTheme()
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        if (values.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries { series(y = values.map { it.toFloat() }) }
            }
        }
    }
    if (values.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(c.card, RoundedCornerShape(IronLogRadius.lg.dp))
                .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("No data for this exercise yet.", color = c.muted) }
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp)),
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, x, _ ->
                        dates.getOrElse(x.toInt()) { "" }.substringBefore('T')
                    }
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

@Composable
private fun BarChartCard(values: List<Double>, dates: List<String>) {
    val c = useTheme()
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        if (values.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries { series(y = values.map { it.toFloat() }) }
            }
        }
    }
    if (values.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(c.card, RoundedCornerShape(IronLogRadius.lg.dp))
                .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("No data for this exercise yet.", color = c.muted) }
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp)),
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, x, _ ->
                        dates.getOrElse(x.toInt()) { "" }.substringBefore('T')
                    }
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

@Composable
private fun SessionHistoryRow(row: HistorySessionRow) {
    val c = useTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(IronLogRadius.lg.dp))
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                formatDateFull(row.date),
                color = c.text,
                fontWeight = FontWeight.Bold,
                fontSize = IronLogType.body.fontSize.sp,
            )
            Text(row.summary, color = c.muted, fontSize = IronLogType.meta.fontSize.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Volume: ${row.volumeStr}", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp)
        }
        if (row.bestSetStr != null) {
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .border(1.dp, c.accent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("BEST", color = c.muted, fontSize = IronLogType.micro.fontSize.sp, letterSpacing = 2.sp)
                Text(row.bestSetStr, color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.meta.fontSize.sp)
            }
        }
    }
}

private fun formatDateFull(dateStr: String): String = runCatching {
    DateTimeFormatter.ofPattern("MMM d, yyyy")
        .format(Instant.parse(dateStr).atZone(ZoneId.systemDefault()))
}.getOrDefault(dateStr)

