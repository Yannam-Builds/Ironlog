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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.ironlog.app.ui.theme.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    "<72% Back off" to "danger",
    "72-90% Maintain" to "warning",
    ">90% Train" to "success",
    "No data" to "faint",
)

@Composable
fun RecoveryMapScreen(
    vm: StatsViewModel = viewModel(),
    onBack: () -> Unit = {},
    onOpenVolumeAnalytics: () -> Unit = {},
) {
    val c = useTheme()
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val nowEpochMs by com.ironlog.app.ui.state.rememberPresentationTime()
    val bodyMapDataset = com.ironlog.app.ui.screens.body.rememberBodyMapDataset(context)
    var windowKey by remember { mutableStateOf("30D") }
    val rangeDays = remember(windowKey) { RECOVERY_WINDOWS.firstOrNull { it.first == windowKey }?.second ?: 30 }
    var selected by remember { mutableStateOf<String?>(null) }
    var showManualModal by remember { mutableStateOf(false) }
    var savingCheckIn by remember { mutableStateOf(false) }
    var checkInError by remember { mutableStateOf<String?>(null) }
    val filtered = remember(state.history, rangeDays, nowEpochMs) { state.history.filter {
        com.ironlog.app.domain.gamification.parseHistoryInstant(it.date)?.toEpochMilli()?.let { t -> t <= nowEpochMs && nowEpochMs - t <= rangeDays * 86_400_000L } == true
    } }

    val scope = rememberCoroutineScope()
    val settingsRepo = remember { com.ironlog.app.data.repository.SettingsRepository() }
    val painRegions = com.ironlog.app.domain.intelligence.RECOVERY_REGIONS
    val observedSettings by remember(settingsRepo) {
        settingsRepo.observeStrings(setOf("manual_recovery_input") + painRegions.map { "pain_flag_$it" })
    }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val painFlags = painRegions.filter { observedSettings["pain_flag_$it"] == "true" }.toSet()
    val manualInput = com.ironlog.app.domain.intelligence.RecoveryCheckInCodec.decode(observedSettings["manual_recovery_input"], nowEpochMs)
    // The range filters explanatory history only, never the current recovery calculation.
    val snapshot = remember(state.history, painFlags, manualInput, nowEpochMs) {
        RecoveryReadinessEngine.snapshot(state.history, painFlags, manualInput, nowEpochMs)
    }
    val readiness = snapshot.readiness
    val displayReadiness = remember(readiness) { buildDisplayReadiness(readiness) }
    val recoveryScore = snapshot.score
    val score = recoveryScore.score
    val suggestions = remember(readiness, painFlags) {
        if (painFlags.isEmpty()) RecoveryReadinessEngine.suggestions(readiness) else listOf("Review pain flags before training; avoid painful movements.")
    }
    val trend14 = remember(state.history, nowEpochMs) {
        val localNow = Instant.ofEpochMilli(nowEpochMs).atZone(ZoneId.systemDefault())
        (13 downTo 0).map { daysBack ->
            val sample = localNow.minusDays(daysBack.toLong())
            sample.toLocalDate().toString() to RecoveryReadinessEngine.snapshot(state.history, nowEpochMs = sample.toInstant().toEpochMilli()).score.scoreOrNull
        }
    }
    val confidenceLabel = remember(snapshot.workloadEvidence, manualInput) {
        val count = snapshot.workloadEvidence.values.flatten().map { it.workoutId }.distinct().size
        val base = when {
            count >= 16 -> "More recorded history — unvalidated estimate"
            count >= 3 -> "Limited recorded history"
            count > 0 -> "Very limited recorded history"
            else -> "No mapped workout evidence"
        }
        if (manualInput != null) "$base + manual check-in" else base
    }

    if (showManualModal) {
        ManualRecoveryCheckInModal(
            initial = manualInput ?: ManualRecoveryInput(),
            painFlags = painFlags,
            painRegions = painRegions,
            saving = savingCheckIn,
            error = checkInError,
            onDismiss = { if (!savingCheckIn) showManualModal = false },
            onSave = { updatedInput, updatedFlags ->
                if (!savingCheckIn) {
                    savingCheckIn = true
                    checkInError = null
                    scope.launch {
                        try {
                            settingsRepo.saveRecoveryCheckIn(updatedInput, updatedFlags)
                            showManualModal = false
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { checkInError = "Could not save the check-in. Your entries are retained; try again." }
                        finally { savingCheckIn = false }
                    }
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
                    Text("Estimated readiness", color = c.muted)
                    Text(recoveryScore.scoreOrNull?.toString() ?: "—", color = if (!recoveryScore.hasEvidence) c.muted else if (score >= 85) c.success else if (score >= 60) c.warning else c.danger, fontSize = IronLogType.display.fontSize.sp)
                    // Action directive chip
                    val actionDirective = if (painFlags.isNotEmpty()) "Review pain" else if (!recoveryScore.hasEvidence) "Establish a baseline" else if (score >= 85) "Ready" else if (score >= 60) "Recovering" else "Reduce Load"
                    val directiveColor = if (!recoveryScore.hasEvidence) c.muted else if (score >= 85) c.success else if (score >= 60) c.warning else c.danger
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
                    Text("Muscle details", color = c.text, fontWeight = FontWeight.Bold)
                    com.ironlog.app.ui.screens.body.BODY_REGIONS.forEach { region ->
                        val value = displayReadiness[region.key]
                        val flagged = region.key in com.ironlog.app.ui.screens.body.bodyPainRegions(painFlags)
                        val status = if (flagged) "Pain flagged" else value?.let { "${(it * 100).toInt()}% estimated" } ?: "No data"
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .semantics(mergeDescendants = true) { contentDescription = "${region.label}, $status"; this.selected = selected == region.key }
                                .clickable(onClickLabel = "Open ${region.label} details") { selected = region.key }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(region.label, color = c.text, modifier = Modifier.weight(1f))
                            Text(status, color = if (flagged) c.danger else c.subtext, modifier = Modifier.weight(1f))
                        }
                    }
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
                readiness = displayReadiness[region],
                painFlagged = region in com.ironlog.app.ui.screens.body.bodyPainRegions(painFlags),
                evidence = snapshot.workloadEvidence[com.ironlog.app.ui.screens.body.BODY_REGIONS.firstOrNull { it.key == region }?.group].orEmpty().filter { evidence -> filtered.any { it.id == evidence.workoutId } },
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
 *   Red   0–60%  (low)
 *   Yellow 60–85% (moderate)
 *   Green  85–100% (good)
 * Missing-data days are represented as gaps (no interpolation).
 */
@Composable
private fun ReadinessTrendChart(
    trend14: List<Pair<String, Int?>>,   // Unknown days are gaps, never fabricated zeroes.
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
        drawRect(color = c.danger.copy(alpha = 0.10f),   topLeft = Offset(0f, yForScore(60)),  size = Size(w, yForScore(0)  - yForScore(60)))
        drawRect(color = c.warning.copy(alpha = 0.08f),  topLeft = Offset(0f, yForScore(85)),  size = Size(w, yForScore(60) - yForScore(85)))
        drawRect(color = c.success.copy(alpha = 0.07f),  topLeft = Offset(0f, yForScore(100)), size = Size(w, yForScore(85) - yForScore(100)))

        // Zone divider lines
        drawLine(c.danger.copy(alpha = 0.25f),  Offset(0f, yForScore(60)), Offset(w, yForScore(60)),  strokeWidth = 1f)
        drawLine(c.warning.copy(alpha = 0.25f), Offset(0f, yForScore(85)), Offset(w, yForScore(85)), strokeWidth = 1f)

        // Line segments (skip gaps where score == 0 and no history)
        val stepX = w / (trend14.size - 1).coerceAtLeast(1).toFloat()
        val path = Path()
        var penDown = false
        trend14.forEachIndexed { i, (_, score) ->
            if (score == null) { penDown = false; return@forEachIndexed }
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
            if (score == null) return@forEachIndexed
            val x = i * stepX
            val y = yForScore(score)
            val dotColor = when {
                score >= 85 -> c.success
                score >= 60 -> c.warning
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
    readiness: Double?,
    painFlagged: Boolean,
    evidence: List<com.ironlog.app.domain.intelligence.RegionWorkloadEvidence>,
    sourceLabel: String,
    onDismiss: () -> Unit,
) {
    val c = useTheme()
    val action = when {
        painFlagged -> "Pain flagged: avoid painful movements; readiness is not medical clearance."
        readiness == null -> "Not enough recorded workload to estimate this region."
        readiness >= 0.90 -> "Train"
        readiness >= 0.72 -> "Maintain"
        else -> "Back Off"
    }
    val hits = remember(evidence) {
        evidence.groupBy { it.exerciseName }.entries
            .sortedByDescending { it.value.sumOf { row -> row.workingSets * row.contribution } }
            .take(5).map { (name, rows) ->
                "$name (${rows.map { it.workoutId }.distinct().size} sessions · ${rows.sumOf { it.workingSets }} working sets)"
            }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val displayRegion = region
                .replace(Regex("([A-Z])"), " $1")
                .trim()
                .replaceFirstChar { it.titlecase() }
            Text(displayRegion, color = c.text, fontSize = IronLogType.title.fontSize.sp)
            Text(readiness?.let { "Estimated readiness ${(it * 100).toInt()}%" } ?: "No data", color = c.subtext)
            Text("Source: $sourceLabel", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
            Text(action, color = c.accent)
            Text("Recent contributing exercises", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(top = 4.dp))
            if (hits.isEmpty()) Text("Not enough recent data.", color = c.muted) else hits.forEach { Text("- $it", color = c.text) }
        }
    }
}

@Composable
internal fun ManualRecoveryCheckInModal(
    initial: ManualRecoveryInput,
    painFlags: Set<String> = emptySet(),
    painRegions: List<String> = emptyList(),
    saving: Boolean = false,
    error: String? = null,
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

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Recovery check-in", color = c.text, fontSize = IronLogType.title.fontSize.sp)
                error?.let { Text(it, color = c.danger, modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) }

                ScoreRow(label = "Soreness (1-5)", value = soreness, c = c, enabled = !saving) { soreness = it }
                ScoreRow(label = "Sleep (1-5)", value = sleep, c = c, enabled = !saving) { sleep = it }
                ScoreRow(label = "Energy (1-5)", value = energy, c = c, enabled = !saving) { energy = it }

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
                                        .heightIn(min = 48.dp)
                                        .semantics { contentDescription = "Pain flag $region" }
                                        .toggleable(value = flagged, enabled = !saving, role = Role.Checkbox) { checked ->
                                            localPainFlags = if (checked) localPainFlags + region else localPainFlags - region
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
                        enabled = !saving,
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
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = c.muted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.faint)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(ManualRecoveryInput(soreness, sleep, energy, notes = notes), localPainFlags) },
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent.copy(alpha = 0.15f), contentColor = c.accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.accent)
                    ) {
                        Text(if (saving) "Saving…" else "Save")
                    }
                }
            }
        }
    }
}

// buildDisplayReadiness → BodyMapCanvas.kt

@Composable
private fun ScoreRow(label: String, value: Int, c: IronLogThemeTokens, enabled: Boolean = true, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { i ->
                val active = value == i
                val bg = if (active) c.accent.copy(alpha = 0.15f) else Color.Transparent
                val borderColor = if (active) c.accent else c.faint
                val tc = if (active) c.accent else c.muted
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(bg, RoundedCornerShape(8.dp))
                        .border(1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
                        .semantics { contentDescription = "$label, $i" }
                        .selectable(selected = active, enabled = enabled, role = Role.RadioButton) { onChange(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$i", color = tc, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
