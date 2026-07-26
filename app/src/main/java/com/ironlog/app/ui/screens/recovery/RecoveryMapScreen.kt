package com.ironlog.app.ui.screens.recovery

import com.ironlog.app.ui.screens.body.BodyHalfCanvas
import com.ironlog.app.ui.screens.body.BodyMapDataset
import com.ironlog.app.ui.screens.body.ViewBox
import com.ironlog.app.ui.screens.body.BodyPathPiece
import com.ironlog.app.ui.screens.body.loadBodyMapDataset
import com.ironlog.app.ui.screens.body.buildDisplayReadiness
import com.ironlog.app.ui.screens.body.computeBodyTransform
import com.ironlog.app.ui.screens.body.VerticalAnchor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ironlog.app.ui.theme.IronLogRadius
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.domain.intelligence.ManualRecoveryInput
import com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.StatsViewModel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.launch

private val RECOVERY_WINDOWS = listOf("Workout" to 3, "7D" to 7, "30D" to 30, "Program" to 90)
private val LEGEND = listOf(
    "Back Off (<72%)" to "danger",
    "Maintain (72-90%)" to "warning",
    "Train (>90%)" to "success",
    "Unknown" to "faint",
)

@Composable
fun RecoveryMapScreen(
    vm: StatsViewModel = viewModel(),
    onBack: () -> Unit = {},
    onOpenVolumeAnalytics: () -> Unit = {},
) {
    val c = useTheme()
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val manualInput by vm.manualRecoveryInput.collectAsState()
    val bodyMapDataset = remember { loadBodyMapDataset(context) }
    var windowKey by remember { mutableStateOf("30D") }
    val rangeDays = remember(windowKey) { RECOVERY_WINDOWS.firstOrNull { it.first == windowKey }?.second ?: 30 }
    var selected by remember { mutableStateOf<String?>(null) }
    var showManualModal by remember { mutableStateOf(false) }
    val filtered = remember(state.history, rangeDays) { state.history.filter { recoveryAgeDays(it.date) <= rangeDays } }

    // GAP-20: Pain flags — loaded from SettingsRepository on entry; saved on toggle
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { com.ironlog.app.data.repository.SettingsRepository() }
    val painRegions = listOf("Push", "Pull", "Legs", "Core", "Arms", "Shoulders")
    var painFlags by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) {
        val loaded = painRegions.filter { region ->
            settingsRepo.getString("pain_flag_${region}") == "true"
        }.toSet()
        painFlags = loaded
    }

    val readiness = remember(filtered, painFlags) { RecoveryReadinessEngine.readinessByRegion(filtered, painFlags) }
    val displayReadiness = remember(readiness) { buildDisplayReadiness(readiness) }
    val recoveryScore = remember(readiness, manualInput) { RecoveryReadinessEngine.score(readiness, manualInput) }
    val score = recoveryScore.score
    val suggestions = remember(readiness) { RecoveryReadinessEngine.suggestions(readiness) }

    // GAP-15: 14-day readiness trend — compute one score per day.
    val trend14 = remember(state.history) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = System.currentTimeMillis()
        (0 until 14).map { daysBack ->
            val dayMs = now - daysBack * 86_400_000L
            val dayKey = sdf.format(java.util.Date(dayMs))
            val dayHistory = state.history.filter { it.date.startsWith(dayKey) }
            // Use sessions up to and including this day (last 7 days of that point) for readiness
            val window = state.history.filter {
                val t = com.ironlog.app.domain.gamification.parseHistoryInstant(it.date)?.toEpochMilli()
                    ?: return@filter false
                t <= dayMs && t >= dayMs - 7 * 86_400_000L
            }
            val r = RecoveryReadinessEngine.readinessByRegion(window, nowEpochMs = dayMs)
            val score = RecoveryReadinessEngine.score(r, nowEpochMs = dayMs).score
            Pair(dayKey, score)
        }.reversed() // oldest first
    }

    val confidenceLabel = remember(filtered.size, manualInput) {
        val base = when {
            filtered.size >= 16 -> "High confidence"
            filtered.size >= 8 -> "Moderate confidence"
            filtered.size >= 3 -> "Low confidence"
            else -> "Very low confidence"
        }
        if (manualInput != null && System.currentTimeMillis() - manualInput!!.recordedAt < 48 * 3600_000L) {
            "$base + manual check-in"
        } else base
    }

    if (showManualModal) {
        ManualRecoveryCheckInModal(
            initial = manualInput ?: ManualRecoveryInput(),
            painFlags = painFlags,
            painRegions = painRegions,
            onDismiss = { showManualModal = false },
            onSave = { updatedInput, updatedFlags ->
                vm.viewModelScope.launch {
                    vm.saveManualRecovery(updatedInput)
                    showManualModal = false
                }
                // Persist pain flags
                scope.launch {
                    painRegions.forEach { region ->
                        settingsRepo.setString("pain_flag_${region}", if (region in updatedFlags) "true" else "false")
                    }
                    painFlags = updatedFlags
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Recovery Map", onBack = onBack)
            Text("MUSCLE RECOVERY", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            Text("Recovery", color = c.text, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight(IronLogType.display.fontWeight), lineHeight = IronLogType.title.lineHeight.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RECOVERY_WINDOWS.forEach { (label, _) ->
                    val active = windowKey == label
                    Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (active) c.accent.copy(alpha = 0.18f) else c.surface)
                            .border(1.5.dp, if (active) c.accent else c.cardBorder, androidx.compose.foundation.shape.CircleShape)
                            .clickable { windowKey = label }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color      = if (active) c.accent else c.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Overall readiness", color = c.muted)
                    Text("$score", color = if (score >= 70) c.success else if (score >= 40) c.warning else c.danger, fontSize = IronLogType.display.fontSize.sp)
                    // Action directive chip
                    val actionDirective = if (score >= 78) "Train" else if (score >= 55) "Maintain" else "Back Off"
                    val directiveColor = if (score >= 78) c.success else if (score >= 55) c.warning else c.danger
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(directiveColor.copy(alpha = 0.15f))
                            .border(1.dp, directiveColor.copy(alpha = 0.45f), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text(actionDirective, color = directiveColor, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.meta.fontSize.sp, letterSpacing = 0.5.sp)
                    }
                    Text(confidenceLabel, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                    Text(recoveryScore.state.replaceFirstChar { it.titlecase() }, color = c.text, fontSize = IronLogType.section.fontSize.sp)
                    Text(recoveryScore.explanation, color = c.muted, fontSize = IronLogType.meta.fontSize.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    
                    Button(
                        onClick = { showManualModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = c.accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.accent),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("UPDATE MANUAL CHECK-IN", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 1.2.sp)
                    }

                    Text("Tap a region to view details.", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(vertical = 8.dp))
                    
                    RecoveryBodyMap(
                        readiness  = displayReadiness,
                        dataset    = bodyMapDataset,
                        onSelect   = { selected = it },
                        painFlags  = painFlags,
                    )
                }
            }
        }
        // GAP-15: 14-day readiness trend chart
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("READINESS TREND — 14 DAYS", color = c.muted,
                        fontSize = IronLogType.eyebrow.fontSize.sp,
                        fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                        letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
                    ReadinessTrendChart(trend14 = trend14, modifier = Modifier.fillMaxWidth().height(120.dp))
                    // X-axis labels: first and last date only to avoid crowding
                    if (trend14.size >= 2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(trend14.first().first.substring(5), color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                            Text(trend14.last().first.substring(5), color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.surface), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Suggestions", color = c.text, fontSize = IronLogType.section.fontSize.sp)
                    if (suggestions.isEmpty()) {
                        Text("Not enough recent data to generate suggestions.", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    } else {
                        suggestions.forEach { tip ->
                            Text("- $tip", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                        }
                    }
                }
            }
        }
        item {
            androidx.compose.material3.TextButton(onClick = onOpenVolumeAnalytics, modifier = Modifier.fillMaxWidth()) {
                Text("OPEN VOLUME ANALYTICS", color = c.accent, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }

    selected?.let { region ->
            RecoveryRegionSheet(
                region = region,
                readiness = displayReadiness[region] ?: 1.0,
                filtered = filtered,
                sourceLabel = confidenceLabel,
                onDismiss = { selected = null },
            )
        }
}

@Composable
private fun RecoveryBodyMap(readiness: Map<String, Double>, dataset: BodyMapDataset?, onSelect: (String) -> Unit, painFlags: Set<String> = emptySet()) {
    val c = useTheme()
    val frontPieces = dataset?.front?.pieces.orEmpty()
    val backPieces = dataset?.back?.pieces.orEmpty()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    val frontVb = dataset?.front?.alignmentBox ?: ViewBox(-20f, 95f, 740f, 1300f)
    val backVb  = dataset?.back?.alignmentBox  ?: ViewBox(740f, 95f, 680f, 1320f)
    val fitWidth = maxOf(frontVb.width, backVb.width)
    val fitHeight = maxOf(frontVb.height, backVb.height)
    val sharedAspect = fitWidth / fitHeight
    val mapViewportHeight = 470.dp

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("FRONT", "BACK").forEachIndexed { index, label ->
                val active = pagerState.currentPage == index
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (active) c.accent.copy(alpha = 0.16f) else c.surface)
                        .border(1.dp, if (active) c.accent.copy(alpha = 0.55f) else c.cardBorder, CircleShape)
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        .padding(horizontal = 18.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (active) c.accent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(mapViewportHeight)
                .clipToBounds(),
        ) { page ->
            val isFront = page == 0
            val activePieces = if (isFront) frontPieces else backPieces
            val activeVb = if (isFront) frontVb else backVb
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val pageLayout = computeRecoveryMapPageLayout(
                    maxWidthDp = maxWidth.value,
                    maxHeightDp = maxHeight.value,
                    aspect = sharedAspect,
                )
                Box(
                    Modifier
                        .padding(top = pageLayout.topPaddingDp.dp)
                        .size(pageLayout.canvasWidthDp.dp, pageLayout.canvasHeightDp.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BodyHalfCanvasInteractive(
                        pieces = activePieces,
                        viewBox = activeVb,
                        readiness = readiness,
                        modifier = Modifier.matchParentSize(),
                        fitWidth = fitWidth,
                        fitHeight = fitHeight,
                        onRegionTap = onSelect,
                        painFlags = painFlags,
                    )
                }
            }
        }
        RecoveryLegendFlow()
    }
}

internal data class RecoveryBodyMapPageLayout(
    val canvasWidthDp: Float,
    val canvasHeightDp: Float,
    val topPaddingDp: Float,
)

internal fun computeRecoveryMapPageLayout(
    maxWidthDp: Float,
    maxHeightDp: Float,
    aspect: Float,
): RecoveryBodyMapPageLayout {
    val topPaddingDp = 12f
    val availableHeightDp = (maxHeightDp - topPaddingDp).coerceAtLeast(1f)
    val widthLimitedHeightDp = (maxWidthDp * 0.80f) / aspect.coerceAtLeast(0.01f)
    val canvasHeightDp = minOf(availableHeightDp, widthLimitedHeightDp, 420f)
    return RecoveryBodyMapPageLayout(
        canvasWidthDp = canvasHeightDp * aspect,
        canvasHeightDp = canvasHeightDp,
        topPaddingDp = topPaddingDp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecoveryLegendFlow() {
    val c = useTheme()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LEGEND.forEach { (label, key) ->
            val dotColor = when (key) {
                "danger" -> c.danger
                "warning" -> c.warning
                "success" -> c.success
                else -> c.faint
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(dotColor),
                )
                Text(label, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
            }
        }
    }
}

@Composable
private fun RecoveryLegendDot(label: String, color: Color) {
    val c = useTheme()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color),
        )
        Text(label, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
    }
}

@Composable
private fun BodyHalfCanvasInteractive(
    pieces: List<BodyPathPiece>,
    viewBox: ViewBox,
    readiness: Map<String, Double>,
    modifier: Modifier,
    fitWidth: Float = viewBox.width,
    fitHeight: Float = viewBox.height,
    onRegionTap: (String) -> Unit,
    painFlags: Set<String> = emptySet(),   // GAP-20
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Box(modifier = modifier.pointerInput(pieces, viewBox, canvasSize) {
        detectTapGestures { offset ->
            if (canvasSize == Size.Zero) return@detectTapGestures
            // Use the same uniform-scale + centred transform as BodyHalfCanvas
            val t = computeBodyTransform(
                canvasSize.width,
                canvasSize.height,
                viewBox,
                fitWidth = fitWidth,
                fitHeight = fitHeight,
                verticalAnchor = VerticalAnchor.TOP,
            )
            val translationX = t.offsetX - viewBox.x * t.scale
            val translationY = t.offsetY - viewBox.y * t.scale
            val svgX = (offset.x - translationX) / t.scale
            val svgY = (offset.y - translationY) / t.scale
            var bestHitRegion: String? = null
            var bestHitArea = Float.MAX_VALUE
            for (piece in pieces) {
                if (piece.region.isBlank()) continue
                val androidPath = piece.path.asAndroidPath()
                val r = android.graphics.RectF()
                androidPath.computeBounds(r, true)
                if (r.isEmpty) continue
                val region = android.graphics.Region()
                region.setPath(
                    androidPath,
                    android.graphics.Region(
                        floor(r.left).toInt(),
                        floor(r.top).toInt(),
                        ceil(r.right).toInt(),
                        ceil(r.bottom).toInt(),
                    ),
                )
                if (region.contains(svgX.toInt(), svgY.toInt())) {
                    val area = r.width() * r.height()
                    if (area < bestHitArea) {
                        bestHitArea = area
                        bestHitRegion = piece.region
                    }
                }
            }
            bestHitRegion?.let(onRegionTap)
        }
    }) {
        BodyHalfCanvas(
            pieces    = pieces,
            viewBox   = viewBox,
            readiness = readiness,
            modifier  = Modifier.matchParentSize(),
            fitWidth  = fitWidth,
            fitHeight = fitHeight,
            painFlags = painFlags,
        )
        // Capture real canvas size so hit-test transform matches render transform
        Canvas(Modifier.matchParentSize()) { canvasSize = size }
    }
}

// ViewBox, BodyPathPiece, BodySide, BodyMapDataset, loadBodyMapDataset,
// buildDisplayReadiness, and BodyHalfCanvas now live in BodyMapCanvas.kt

private fun recoveryAgeDays(iso: String): Long {
    // Use tolerant parser so both ISO-8601 instants and bare YYYY-MM-DD dates work.
    // Without runCatching an unrecognised format would crash the remember block and
    // bring down the entire RecoveryMapScreen composable.
    val d = runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(iso.substringBefore('T')) }.getOrNull()
        ?: return Long.MAX_VALUE   // unknown format → treat as too old, filter out
    return java.time.temporal.ChronoUnit.DAYS.between(d, java.time.LocalDate.now())
}

/**
 * Canvas line chart showing 14-day readiness trend with coloured zone bands:
 *   Red   0–40%  (low)
 *   Yellow 40–70% (moderate)
 *   Green  70–100% (good)
 * Missing-data days are represented as gaps (no interpolation).
 */
@Composable
private fun ReadinessTrendChart(
    trend14: List<Pair<String, Int>>,   // (dateKey, score 0-100), oldest first
    modifier: Modifier = Modifier,
) {
    if (trend14.isEmpty()) return
    val c = useTheme()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padTop = 8f
        val padBottom = 4f
        val chartH = h - padTop - padBottom

        fun yForScore(score: Int) = padTop + chartH * (1f - score / 100f)

        // Zone bands
        drawRect(color = c.danger.copy(alpha = 0.10f),   topLeft = Offset(0f, yForScore(40)),  size = Size(w, yForScore(0)  - yForScore(40)))
        drawRect(color = c.warning.copy(alpha = 0.08f),  topLeft = Offset(0f, yForScore(70)),  size = Size(w, yForScore(40) - yForScore(70)))
        drawRect(color = c.success.copy(alpha = 0.07f),  topLeft = Offset(0f, yForScore(100)), size = Size(w, yForScore(70) - yForScore(100)))

        // Zone divider lines
        drawLine(c.danger.copy(alpha = 0.25f),  Offset(0f, yForScore(40)), Offset(w, yForScore(40)),  strokeWidth = 1f)
        drawLine(c.warning.copy(alpha = 0.25f), Offset(0f, yForScore(70)), Offset(w, yForScore(70)), strokeWidth = 1f)

        // Line segments (skip gaps where score == 0 and no history)
        val stepX = w / (trend14.size - 1).coerceAtLeast(1).toFloat()
        val path = Path()
        var penDown = false
        trend14.forEachIndexed { i, (_, score) ->
            val x = i * stepX
            val y = yForScore(score)
            if (!penDown) {
                path.moveTo(x, y)
                penDown = true
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(path, color = c.accent, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        // Dot on each point
        trend14.forEachIndexed { i, (_, score) ->
            val x = i * stepX
            val y = yForScore(score)
            val dotColor = when {
                score >= 70 -> c.success
                score >= 40 -> c.warning
                else        -> c.danger
            }
            drawCircle(dotColor, radius = 4f, center = Offset(x, y))
            drawCircle(c.card,    radius = 2f, center = Offset(x, y))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryRegionSheet(
    region: String,
    readiness: Double,
    filtered: List<HistoryEntry>,
    sourceLabel: String,
    onDismiss: () -> Unit,
) {
    val c = useTheme()
    val action = when {
        readiness >= 0.90 -> "Train"
        readiness >= 0.72 -> "Maintain"
        else -> "Back Off"
    }
    val hits = remember(region, filtered) {
        filtered.flatMap { it.exercises }
            .filter {
                val m = (it.primaryMuscle ?: it.primaryMuscles.firstOrNull().orEmpty()).lowercase()
                when (region) {
                    "chest" -> m in setOf("chest", "pectorals")
                    "back" -> m in setOf("back", "lats", "rhomboids", "traps", "trapezius")
                    "shoulders", "rearDelts" -> m in setOf("shoulders", "delts", "rear delts", "rear_delts")
                    "arms" -> m in setOf("arms", "forearms", "biceps", "triceps")
                    "quads" -> m in setOf("quads", "quadriceps")
                    "hamstrings" -> m in setOf("hamstrings", "glutes", "adductors")
                    "calves" -> m in setOf("calves", "tibialis")
                    "core" -> m in setOf("core", "abs", "obliques")
                    else -> true
                }
            }
            .groupBy { it.name }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { "${it.key} (${it.value.size} sessions)" }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val displayRegion = region
                .replace(Regex("([A-Z])"), " $1")
                .trim()
                .replaceFirstChar { it.titlecase() }
            Text(displayRegion, color = c.text, fontSize = IronLogType.title.fontSize.sp)
            Text("Readiness ${(readiness * 100).toInt()}%", color = c.subtext)
            Text("Source: $sourceLabel", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
            Text(action, color = c.accent)
            Text("Recent contributing exercises", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(top = 4.dp))
            if (hits.isEmpty()) Text("Not enough recent data.", color = c.muted) else hits.forEach { Text("- $it", color = c.text) }
        }
    }
}

@Composable
private fun ManualRecoveryCheckInModal(
    initial: ManualRecoveryInput,
    painFlags: Set<String> = emptySet(),
    painRegions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ManualRecoveryInput, Set<String>) -> Unit,
) {
    val c = useTheme()
    var soreness by remember { mutableStateOf(initial.soreness) }
    var sleep by remember { mutableStateOf(initial.sleepQuality) }
    var energy by remember { mutableStateOf(initial.energy) }
    var notes by remember { mutableStateOf(initial.notes) }
    // GAP-20: pain flags editable in this modal
    var localPainFlags by remember { mutableStateOf(painFlags) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Manual Recovery Input", color = c.text, fontSize = IronLogType.title.fontSize.sp)

                ScoreRow(label = "Soreness (1-5)", value = soreness, c = c) { soreness = it }
                ScoreRow(label = "Sleep (1-5)", value = sleep, c = c) { sleep = it }
                ScoreRow(label = "Energy (1-5)", value = energy, c = c) { energy = it }

                // GAP-20: Pain flags per muscle region
                if (painRegions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⚠️ Pain / Injury Flags", color = c.danger, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                        Text("Flagged muscles show 0% readiness regardless of training history.", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            painRegions.forEach { region ->
                                val flagged = region in localPainFlags
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                        .background(if (flagged) c.danger.copy(alpha = 0.15f) else c.surface)
                                        .border(1.dp, if (flagged) c.danger else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                        .clickable {
                                            localPainFlags = if (flagged) localPainFlags - region else localPainFlags + region
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (flagged) "⚠ $region" else region,
                                        color = if (flagged) c.danger else c.subtext,
                                        fontSize = IronLogType.meta.fontSize.sp,
                                        fontWeight = if (flagged) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Notes (optional)", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("How are you feeling?", color = c.muted) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = c.muted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.faint)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(ManualRecoveryInput(soreness, sleep, energy, notes = notes), localPainFlags) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent.copy(alpha = 0.15f), contentColor = c.accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.accent)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// buildDisplayReadiness → BodyMapCanvas.kt

@Composable
private fun ScoreRow(label: String, value: Int, c: IronLogThemeTokens, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { i ->
                val active = value == i
                val bg = if (active) c.accent.copy(alpha = 0.15f) else Color.Transparent
                val borderColor = if (active) c.accent else c.faint
                val tc = if (active) c.accent else c.muted
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(bg, RoundedCornerShape(8.dp))
                        .border(1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
                        .clickable { onChange(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$i", color = tc, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
