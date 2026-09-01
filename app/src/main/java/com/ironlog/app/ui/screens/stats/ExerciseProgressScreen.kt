package com.ironlog.app.ui.screens.stats

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.training.TrainingSetPolicy
import com.ironlog.app.domain.training.PersonalBestPolicy
import com.ironlog.app.domain.training.TrackingMode
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
import com.ironlog.app.ui.theme.Text
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

private val RANGES = listOf("90D" to 90, "6M" to 180, "1Y" to 365, "ALL" to null)

data class ExerciseTrendRow(
    val date: String,
    val e1rm: Double,
    val load: Double,
    val reps: Double,
    val volume: Double,
    val consistency: Double = 1.0,
    val hasEstimate: Boolean = e1rm > 0.0,
    val durationSeconds: Double? = null,
    val loadAvailable: Boolean = true,
    val volumeAvailable: Boolean = true,
)

internal data class HistorySessionRow(
    val date: String,
    val summary: String,
    val bestSetStr: String?,
    val volumeStr: String,
)

internal data class MetricConfig(
    val label: String,
    val unit: String,
    val values: List<Double>,
    val stat: Double,
    val isBar: Boolean = false,
    val chartLabels: List<String>? = null,
)

fun filterExerciseRowsByRange(
    rows: List<ExerciseTrendRow>, days: Int?,
    now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(),
): List<ExerciseTrendRow> {
    val cutoff = days?.let { now.atZone(zone).minusDays(it.toLong()).toInstant() }
    return rows.filter {
        val date = parseHistoryInstant(it.date, zone)
        date != null && !date.isAfter(now) && (cutoff == null || !date.isBefore(cutoff))
    }
}

fun roundMetric(value: Double, digits: Int = 1): Double {
    if (!value.isFinite()) return 0.0
    val mult = Math.pow(10.0, digits.toDouble())
    return round(value * mult) / mult
}

fun buildExerciseTrendLocal(
    history: List<HistoryEntry>,
    exerciseName: String,
    prResetAt: Instant? = null,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ExerciseTrendRow> {
    val target = exerciseName.lowercase(Locale.US).trim()
    return history.flatMap { h ->
        h.exercises.filter { it.name.lowercase(Locale.US).trim() == target }.mapNotNull { ex ->
            val sets = ex.sets.filter { TrainingSetPolicy.isValidWorkingSet(ex, it) }
            if (sets.isEmpty()) return@mapNotNull null
            val mode = TrainingSetPolicy.tracking(ex)
            val timed = mode in setOf(TrackingMode.DURATION, TrackingMode.WEIGHTED_DURATION, TrackingMode.DURATION_DISTANCE)
            val estimate = sets
                .filter { PersonalBestPolicy.isAfterReset(it, h.date, prResetAt, zoneId) }
                .mapNotNull { TrainingSetPolicy.estimatedOneRm(ex, it) }
                .maxOrNull()
            val load = mode in setOf(TrackingMode.LOAD_REPS, TrackingMode.ADDED_LOAD_REPS, TrackingMode.ASSISTED_REPS, TrackingMode.WEIGHTED_DURATION) ||
                (mode == TrackingMode.UNKNOWN && ex.trackingType.isNullOrBlank())
            ExerciseTrendRow(
                date = h.date, e1rm = estimate ?: 0.0, hasEstimate = estimate != null,
                load = if (load) sets.maxOf { it.weight } else 0.0,
                reps = if (timed) 0.0 else sets.map { it.reps }.average(),
                volume = sets.sumOf { TrainingSetPolicy.externalLoadVolume(ex, it) },
                consistency = sets.size.toDouble(),
                durationSeconds = if (timed) sets.sumOf { it.reps } else null,
                loadAvailable = load,
                volumeAvailable = mode in setOf(TrackingMode.LOAD_REPS, TrackingMode.ADDED_LOAD_REPS) ||
                    (mode == TrackingMode.UNKNOWN && ex.trackingType.isNullOrBlank() && !TrainingSetPolicy.isCardio(ex)),
            )
        }
    }.sortedBy { parseHistoryInstant(it.date) }
}

internal fun exerciseProgressTabs(rows: List<ExerciseTrendRow>): List<String> = buildList {
    if (rows.any { it.hasEstimate }) add("E1RM")
    if (rows.any { it.loadAvailable }) add("LOAD")
    if (rows.any { it.durationSeconds == null }) add("REPS")
    if (rows.any { it.durationSeconds != null }) add("DURATION")
    if (rows.any { it.volumeAvailable }) add("VOLUME")
    add("CONSISTENCY")
    add("HISTORY")
}

internal fun buildSessionHistoryRows(history: List<HistoryEntry>, exerciseName: String, weightUnit: String,
    now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<HistorySessionRow> {
    val target = exerciseName.lowercase(Locale.US).trim()
    return history.mapNotNull { session ->
        val occurredAt = parseHistoryInstant(session.date, zone) ?: return@mapNotNull null
        if (occurredAt.isAfter(now)) return@mapNotNull null
        val exercise = session.exercises.find { it.name.lowercase(Locale.US).trim() == target } ?: return@mapNotNull null
        val sets = exercise.sets.filter { TrainingSetPolicy.isValidWorkingSet(exercise, it) }
        if (sets.isEmpty()) return@mapNotNull null
        val mode = TrainingSetPolicy.tracking(exercise)
        fun label(set: com.ironlog.app.ui.model.HistoryExerciseSet): String = when (mode) {
            TrackingMode.DURATION -> "${roundMetric(set.reps)} s"
            TrackingMode.DURATION_DISTANCE -> "${roundMetric(set.reps)} s · ${roundMetric(set.weight)} km"
            TrackingMode.WEIGHTED_DURATION -> "${roundMetric(set.reps)} s · ${formatWeightFromKg(set.weight, weightUnit)}"
            TrackingMode.BODYWEIGHT_REPS -> "BW × ${roundMetric(set.reps)}"
            TrackingMode.ADDED_LOAD_REPS -> "BW + ${formatWeightFromKg(set.weight, weightUnit)} × ${roundMetric(set.reps)}"
            TrackingMode.ASSISTED_REPS -> "${formatWeightFromKg(set.weight, weightUnit)} assistance × ${roundMetric(set.reps)}"
            else -> "${formatWeightFromKg(set.weight, weightUnit)} × ${roundMetric(set.reps)}"
        }
        val bestSet = sets.filter { TrainingSetPolicy.estimatedOneRm(exercise, it) != null }
            .maxByOrNull { TrainingSetPolicy.estimatedOneRm(exercise, it)!! }
        val volume = sets.sumOf { TrainingSetPolicy.externalLoadVolume(exercise, it) }
        HistorySessionRow(session.date, sets.joinToString(", ", transform = ::label),
            bestSet?.let(::label), if (volume > 0) formatVolumeFromKg(volume, weightUnit) else "${sets.size} working sets")
    }.sortedByDescending { parseHistoryInstant(it.date) }
}

internal fun computeMetricConfig(rows: List<ExerciseTrendRow>, activeTab: String, weightUnit: String): MetricConfig =
    when (activeTab) {
        "E1RM" -> {
            val values = rows.map { convertKgToUnit(it.e1rm, weightUnit, 1) }
            MetricConfig("BEST EST. 1RM", weightUnit, values, values.maxOrNull() ?: 0.0)
        }
        "LOAD" -> {
            val values = rows.map { convertKgToUnit(it.load, weightUnit, 1) }
            MetricConfig("TOP LOAD", weightUnit, values, values.maxOrNull() ?: 0.0)
        }
        "DURATION" -> {
            val values = rows.map { it.durationSeconds ?: 0.0 }
            MetricConfig("SESSION DURATION", "s", values, values.sum(), isBar = true)
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
            MetricConfig("SESSIONS / ACTIVE WEEK", "×", values, avgPerWeek, isBar = true, chartLabels = sortedWeeks)
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
    val history by vm.history.collectAsStateWithLifecycle()
    val prResetAtEpochMs by vm.prResetAtEpochMs.collectAsStateWithLifecycle()
    val nowEpochMs by com.ironlog.app.ui.state.rememberPresentationTime()
    var activeTab by remember { mutableStateOf("E1RM") }
    var range by remember { mutableStateOf("ALL") }
    var showTrainingMax by remember { mutableStateOf(false) }

    val trend = remember(history, exerciseName, prResetAtEpochMs) {
        buildExerciseTrendLocal(
            history = history,
            exerciseName = exerciseName,
            prResetAt = prResetAtEpochMs?.let(Instant::ofEpochMilli),
        )
    }
    val rows = remember(trend, range, nowEpochMs) {
        filterExerciseRowsByRange(trend, RANGES.first { it.first == range }.second, Instant.ofEpochMilli(nowEpochMs))
    }
    val historyRows = remember(history, exerciseName, weightUnit, nowEpochMs) {
        buildSessionHistoryRows(history, exerciseName, weightUnit, Instant.ofEpochMilli(nowEpochMs))
    }
    val tabs = remember(rows) { exerciseProgressTabs(rows) }
    val selectedTab = activeTab.takeIf { it in tabs } ?: tabs.first()
    val metricRows = remember(rows, selectedTab) {
        rows.filter { when (selectedTab) {
            "E1RM" -> it.hasEstimate
            "LOAD" -> it.loadAvailable
            "REPS" -> it.durationSeconds == null
            "DURATION" -> it.durationSeconds != null
            "VOLUME" -> it.volumeAvailable
            else -> true
        } }
    }
    val activeMetric = remember(metricRows, selectedTab, weightUnit) {
        computeMetricConfig(metricRows, selectedTab, weightUnit)
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
            if (rows.any { it.hasEstimate }) TextButton(onClick = { showTrainingMax = true }) {
                Text("CALC TM", color = colors.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            // CSV export button
            IconButton(onClick = {
                val snapshot = rows.toList()
                csvScope.launch {
                    val sb = StringBuilder("Date,Load ($weightUnit),Mean reps,External volume ($weightUnit),e1RM ($weightUnit),Duration (s)\n")
                    snapshot.forEach { r ->
                        sb.append("${r.date.substringBefore('T')},")
                        sb.append("${if (r.loadAvailable) convertKgToUnit(r.load, weightUnit).toString() else ""},")
                        sb.append("${if (r.durationSeconds == null) r.reps.toString() else ""},")
                        sb.append("${if (r.volumeAvailable) convertKgToUnit(r.volume, weightUnit).toString() else ""},")
                        sb.append("${if (r.hasEstimate) convertKgToUnit(r.e1rm, weightUnit).toString() else ""},${r.durationSeconds ?: ""}\n")
                    }
                    val file = withContext(Dispatchers.IO) {
                        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
                        File(exportDirectory, "ironlog_exercise_progress.csv")
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
                        ShareService.ShareMetric("Best e1RM", rows.filter { it.hasEstimate }.maxByOrNull { it.e1rm }?.let { formatWeightFromKg(it.e1rm, weightUnit) } ?: "Not estimated"),
                        ShareService.ShareMetric("Top load", latest?.takeIf { it.loadAvailable }?.let { formatWeightFromKg(it.load, weightUnit) } ?: "Not applicable"),
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
                .horizontalScroll(rememberScrollState())
                .border(width = 1.dp, color = colors.faint, shape = RoundedCornerShape(0.dp)),
        ) {
            tabs.forEach { tab ->
                val active = selectedTab == tab
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { activeTab = tab }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
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
                        Spacer(Modifier.height(appGapDp(6.dp)))
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
        if (selectedTab == "HISTORY") {
            if (historyRows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data for this exercise yet.", color = colors.muted, fontSize = IronLogType.body.fontSize.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(8.dp),
                ) {
                    items(historyRows) { row -> SessionHistoryRow(row) }
                }
            }
        } else {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .appPadding(16.dp),
                verticalArrangement = appSpacedBy(12.dp),
            ) {
                // Hero card
                HeroMetricCard(activeMetric, metricRows.size)

                // Chart — bar for VOLUME, line for everything else
                if (activeMetric.isBar) {
                    BarChartCard(activeMetric.values, activeMetric.chartLabels ?: metricRows.map { it.date })
                } else {
                    val prIndices = remember(metricRows, selectedTab) {
                        if (selectedTab != "E1RM") emptySet()
                        else {
                            val prSet = mutableSetOf<Int>()
                            var maxSoFar = Double.MIN_VALUE
                            metricRows.forEachIndexed { i, row ->
                                if (row.e1rm > maxSoFar) {
                                    maxSoFar = row.e1rm
                                    prSet.add(i)
                                }
                            }
                            prSet
                        }
                    }
                    LineChartCard(activeMetric.values, metricRows.map { it.date }, prIndices)
                }

                // Range selector
                Row(horizontalArrangement = appSpacedBy(8.dp)) {
                    RANGES.forEach { (label, _) ->
                        FilterChip(
                            selected = range == label,
                            onClick = { range = label },
                            label = { Text(label, fontSize = IronLogType.meta.fontSize.sp) },
                        )
                    }
                }

                Spacer(Modifier.height(appGapDp(16.dp)).navigationBarsPadding())
            }
        }
    }

    // ── Training Max dialog ─────────────────────────────────────────────────
    if (showTrainingMax) {
        val latest = rows.lastOrNull { it.hasEstimate }
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
            .appPadding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = appSpacedBy(4.dp),
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
            .appPadding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = appSpacedBy(3.dp)) {
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
            Spacer(Modifier.width(appGapDp(12.dp)))
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
        .format(requireNotNull(parseHistoryInstant(dateStr)).atZone(ZoneId.systemDefault()))
}.getOrDefault(dateStr)

