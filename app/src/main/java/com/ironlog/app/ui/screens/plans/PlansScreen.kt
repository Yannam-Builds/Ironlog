package com.ironlog.app.ui.screens.plans

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.core.content.FileProvider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.services.ShareService
import com.ironlog.app.ui.components.PageHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.UiPlan
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.PlansViewModel
import com.ironlog.app.ui.viewmodel.StatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlansScreen(
    vm: PlansViewModel = viewModel(),
    statsVm: StatsViewModel = viewModel(),
    onOpenPlan: (String) -> Unit = {},
    onStartWorkout: (planId: String, dayId: String) -> Unit = { _, _ -> },
    onOpenProgramPicker: () -> Unit = {},
    onOpenAIPlan: () -> Unit = {},
) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rawPlans by vm.plans.collectAsState()
    val statsState by statsVm.state.collectAsState()
    val history = statsState.history

    // Local state for dragging overrides.
    // Keep this stable across upstream emissions so reorder interaction doesn't snap/reset.
    var orderedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(rawPlans) {
        val rawIds = rawPlans.map { it.id }
        orderedIds = when {
            orderedIds.isEmpty() -> rawIds
            else -> {
                val existing = orderedIds.filter { it in rawIds }
                val added = rawIds.filter { it !in existing }
                existing + added
            }
        }
    }
    val plans = remember(rawPlans, orderedIds) {
        if (orderedIds.isEmpty()) rawPlans
        else orderedIds.mapNotNull { id -> rawPlans.find { it.id == id } }
    }

    var showImport by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var planToDelete by remember { mutableStateOf<UiPlan?>(null) }

    // File picker for plan import
    val planImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            }.getOrElse { e ->
                withContext(Dispatchers.Main) { status = "Could not read file: ${e.message}" }
                return@launch
            }
            val result = runCatching { parsePlansJson(text) }
            withContext(Dispatchers.Main) {
                result
                    .onFailure { status = "Import failed: ${it.message}" }
                    .onSuccess { list ->
                        if (list.isEmpty()) status = "No valid plans found in file."
                        else { vm.importPlans(list); showImport = false; status = "Imported ${list.size} plan(s)." }
                    }
            }
        }
    }

    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    var showRenamePlan by remember { mutableStateOf<UiPlan?>(null) }
    var renameVal by remember { mutableStateOf("") }

    val lazyListState = rememberLazyListState()
    var reorderPersistJob by remember { mutableStateOf<Job?>(null) }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Offsets before plan items:
        // 1) PageHeader item
        // 2) Header-actions/import/status item
        val offset = 2
        val fromIdx = from.index - offset
        val toIdx = to.index - offset
        if (fromIdx in orderedIds.indices && toIdx in orderedIds.indices) {
            orderedIds = orderedIds.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            reorderPersistJob?.cancel()
            reorderPersistJob = scope.launch {
                delay(220)
                vm.reorderPlans(orderedIds)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // FIXED: 6 — PageHeader for tab screen
            item {
                PageHeader(
                    eyebrow = "TRAINING",
                    title = "Plans",
                    subtitle = "${plans.size} program${if (plans.size == 1) "" else "s"}",
                )
            }
            // Header actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    ActionTile("BROWSE PROGRAMS", Icons.Outlined.LibraryBooks, c, onOpenProgramPicker)
                    ActionTile("IMPORT PLAN", Icons.Outlined.Download, c) { showImport = !showImport }
                    ActionTile("CREATE WITH AI", Icons.Outlined.AutoAwesome, c, onOpenAIPlan)
                }

                if (showImport) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { planImportPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    ) { Text("PICK JSON FILE") }
                }

                if (status.isNotBlank()) {
                    Text(status, color = c.accent, modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            if (plans.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.List, null, tint = c.muted, modifier = Modifier.size(24.dp))
                        Text("No plans yet", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.section.fontSize.sp)
                        Text("Pick a template or build your own routine.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.accentSoft)
                                .border(1.dp, c.accentBorder, RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpenProgramPicker)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text("BROWSE TEMPLATES", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp) }
                    }
                }
            }

            items(plans, key = { it.id }) { plan ->
                ReorderableItem(reorderState, key = plan.id) { isDragging ->
                    PlanCard(
                        plan = plan,
                        history = history,
                        isDragging = isDragging,
                        onOpen = { onOpenPlan(plan.id) },
                        onRename = { showRenamePlan = plan; renameVal = plan.name },
                        onDelete = { planToDelete = plan },
                        onDuplicate = { vm.duplicatePlan(plan) },
                        onSetActive = { vm.setActivePlan(plan.id) },
                        onShare = {
                            scope.launch(Dispatchers.IO) {
                                val json = uiPlanToJson(plan).toString(2)
                                val safeName = plan.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                val dir = java.io.File(context.filesDir, "plan_exports").also { it.mkdirs() }
                                val file = java.io.File(dir, "$safeName.json").also { it.writeText(json) }
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                withContext(Dispatchers.Main) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "${plan.name}.json"))
                                }
                            }
                        },
                        onStart = { dayId -> onStartWorkout(plan.id, dayId) },
                        dragModifier = Modifier.draggableHandle(),
                    )
                }
            }

            // Footer
            item {
                DashedNewPlanButton(c) { showNew = true }
            }
        }
    }

    // Delete Plan Confirmation Dialog
    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("Delete \"${plan.name}\"?", color = c.text) },
            text = { Text("This will permanently delete the plan and all its days. This cannot be undone.", color = c.subtext) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removePlan(plan.id)
                    planToDelete = null
                }) {
                    Text("DELETE", color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("CANCEL", color = c.muted)
                }
            },
            containerColor = c.card,
        )
    }

    // New Plan Modal
    if (showNew) {
        Dialog(onDismissRequest = { showNew = false }) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).padding(24.dp), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = c.surface), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, c.cardBorder)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("NEW PLAN", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.body.fontSize.sp, letterSpacing = 3.sp, modifier = Modifier.padding(bottom = 16.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("Plan name", color = c.muted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 20.dp)) {
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, c.faint, RoundedCornerShape(14.dp)).clickable { showNew = false }.padding(14.dp), contentAlignment = Alignment.Center) {
                                Text("Cancel", color = c.muted)
                            }
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.accent).clickable {
                                if (newName.isNotBlank()) { vm.addPlan(newName.trim()); newName = ""; showNew = false }
                            }.padding(14.dp), contentAlignment = Alignment.Center) {
                                Text("CREATE", color = c.textOnAccent, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Modal
    val planToRename = showRenamePlan
    if (planToRename != null) {
        Dialog(onDismissRequest = { showRenamePlan = null }) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).padding(24.dp), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = c.surface), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, c.cardBorder)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("RENAME PLAN", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.body.fontSize.sp, letterSpacing = 3.sp, modifier = Modifier.padding(bottom = 16.dp))
                        OutlinedTextField(
                            value = renameVal,
                            onValueChange = { renameVal = it },
                            placeholder = { Text("Plan name", color = c.muted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 20.dp)) {
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, c.faint, RoundedCornerShape(14.dp)).clickable { showRenamePlan = null }.padding(14.dp), contentAlignment = Alignment.Center) {
                                Text("Cancel", color = c.muted)
                            }
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.accent).clickable {
                                if (renameVal.isNotBlank()) { vm.renamePlan(planToRename.id, renameVal.trim()); showRenamePlan = null }
                            }.padding(14.dp), contentAlignment = Alignment.Center) {
                                Text("SAVE", color = c.textOnAccent, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: UiPlan,
    history: List<HistoryEntry> = emptyList(),
    isDragging: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDuplicate: () -> Unit = {},
    onSetActive: () -> Unit = {},
    onStart: (dayId: String) -> Unit = {},
    dragModifier: Modifier = Modifier,
) {
    val c = useTheme()
    var expandedMenu by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }
    val exCount = plan.days.sumOf { it.exercises.count { !it.isWarmup } }
    // GAP-19: plan session stats — count sessions matching any day in this plan
    val planDayIds = remember(plan.days) { plan.days.map { it.id }.toSet() }
    val planSessions = remember(history, planDayIds) {
        history.filter { it.planDayUid != null && it.planDayUid in planDayIds }
    }
    val sessionCount = planSessions.size
    val lastUsedDaysAgo = remember(planSessions) {
        if (planSessions.isEmpty()) null
        else {
            val latestMs = planSessions.maxOfOrNull {
                com.ironlog.app.domain.gamification.parseHistoryInstant(it.date)?.toEpochMilli() ?: return@remember null
            } ?: return@remember null
            val diffDays = ((System.currentTimeMillis() - latestMs) / (1000L * 60 * 60 * 24)).toInt()
            diffDays
        }
    }

    // Static diagonal gradient — no animation on Plans to keep the list smooth
    val cardGradient = Brush.linearGradient(
        colors = if (isDragging)
            listOf(c.accent.copy(alpha = 0.36f), c.accent.copy(alpha = 0.10f))
        else
            listOf(c.accent.copy(alpha = 0.22f), c.accent.copy(alpha = 0.03f)),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    // FIXED: 35 — Active plan gets accent border + ACTIVE badge
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.card)
            .background(cardGradient)
            .border(
                width = if (plan.isActive) 2.dp else 1.dp,
                color = if (isDragging) c.accent.copy(alpha = 0.55f) else if (plan.isActive) c.accent.copy(alpha = 0.50f) else c.accent.copy(alpha = 0.20f),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header: play icon + plan name + menu/drag
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Play icon button
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accent.copy(alpha = 0.16f))
                        .border(1.dp, c.accent.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = c.accent, modifier = Modifier.size(22.dp))
                }
                // Plan info — fills remaining space
                Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(plan.name, color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.section.fontSize.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (plan.isActive) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(IronLogRadius.full.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("ACTIVE", color = c.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight(IronLogType.button.fontWeight), letterSpacing = IronLogType.micro.letterSpacing.sp)
                            }
                        }
                    }
                    Text(
                        "${plan.days.size} days · $exCount exercises",
                        color = c.subtext,
                        fontSize = IronLogType.meta.fontSize.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // GAP-19: session stats sub-label
                    val statsLabel = if (sessionCount == 0) {
                        "No sessions yet"
                    } else {
                        val lastLabel = when {
                            lastUsedDaysAgo == null -> ""
                            lastUsedDaysAgo == 0 -> " · last used today"
                            lastUsedDaysAgo == 1 -> " · last used yesterday"
                            else -> " · last used $lastUsedDaysAgo days ago"
                        }
                        "$sessionCount session${if (sessionCount == 1) "" else "s"}$lastLabel"
                    }
                    Text(
                        statsLabel,
                        color = c.muted,
                        fontSize = IronLogType.micro.fontSize.sp,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                // 3-dot menu
                Box {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.accent.copy(alpha = 0.10f))
                            .clickable { expandedMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = c.accent.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expandedMenu, { expandedMenu = false }, modifier = Modifier.background(c.surface)) {
                        DropdownMenuItem(text = { Text("Edit Plan", color = c.text) }, onClick = { expandedMenu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Rename", color = c.text) }, onClick = { expandedMenu = false; onRename() })
                        if (!plan.isActive) {
                            DropdownMenuItem(text = { Text("Set as Active", color = c.text) }, onClick = { expandedMenu = false; onSetActive() })
                        }
                        DropdownMenuItem(text = { Text("Duplicate", color = c.text) }, onClick = { expandedMenu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Share Plan", color = c.text) }, onClick = { expandedMenu = false; onShare() })
                        DropdownMenuItem(text = { Text("Delete", color = c.danger) }, onClick = { expandedMenu = false; onDelete() })
                    }
                }
                // Drag handle
                Box(dragModifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Reorder", tint = c.accent.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
                }
            }

            // Colored day chips
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.days.forEach { d ->
                    val safeColorStr = if (d.color.startsWith("#") && d.color.length == 7) d.color else "#E53935"
                    val parsedColor = Color(android.graphics.Color.parseColor(safeColorStr))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(parsedColor.copy(alpha = 0.16f))
                            .border(1.dp, parsedColor.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(d.name, color = parsedColor, fontWeight = FontWeight.Bold, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // START button
            if (plan.days.size == 1) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accent)
                        .clickable { onStart(plan.days.first().id) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.PlayArrow, null, tint = c.textOnAccent, modifier = Modifier.size(14.dp))
                        Text("START WORKOUT", color = c.textOnAccent, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.meta.fontSize.sp, letterSpacing = 1.sp)
                    }
                }
            } else if (plan.days.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (showDayPicker) c.accent.copy(alpha = 0.85f) else c.accent)
                        .clickable { showDayPicker = !showDayPicker }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.PlayArrow, null, tint = c.textOnAccent, modifier = Modifier.size(14.dp))
                        Text(
                            if (showDayPicker) "CHOOSE SESSION ▲" else "START WORKOUT ▼",
                            color = c.textOnAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = IronLogType.meta.fontSize.sp,
                            letterSpacing = 1.sp,
                        )
                    }
                }
                if (showDayPicker) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        plan.days.forEach { day ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.accent.copy(alpha = 0.08f))
                                    .border(1.dp, c.accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                                    .clickable { onStart(day.id); showDayPicker = false }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.PlayArrow, null, tint = c.accent, modifier = Modifier.size(13.dp))
                                    Text(day.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp)
                                }
                                Text("${day.exercises.count { !it.isWarmup }} exercises", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    c: IronLogThemeTokens,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = if (label == "CREATE WITH AI") c.text else c.accent, modifier = Modifier.size(18.dp))
            Text(label, color = if (label == "CREATE WITH AI") c.text else c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.meta.fontSize.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun DashedNewPlanButton(c: IronLogThemeTokens, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.accentSoft)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Draw dashed border natively using Modifier.drawBehind if wanted, or just standard border
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
            Text("NEW PLAN", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.meta.fontSize.sp, letterSpacing = 2.sp)
        }
    }
}

private fun uiPlanToJson(plan: UiPlan): JSONObject {
    val days = JSONArray()
    plan.days.forEach { d ->
        val exercises = JSONArray()
        d.exercises.forEach { ex ->
            exercises.put(
                JSONObject()
                    .put("exerciseId", ex.exerciseId)
                    .put("name", ex.name)
                    .put("sets", ex.sets)
                    .put("reps", ex.reps)
                    .put("restSeconds", ex.restSeconds)
                    .put("supersetGroup", ex.supersetGroup)
                    .put("isWarmup", ex.isWarmup)
                    .put("notes", ex.notes),
            )
        }
        days.put(JSONObject().put("name", d.name).put("color", d.color).put("exercises", exercises))
    }
    return JSONObject().put("name", plan.name).put("goal", plan.goal).put("description", plan.description).put("days", days)
}

private fun parsePlansJson(raw: String): List<FullPlanObject> {
    val text = raw.trim()
    if (text.isBlank()) return emptyList()
    val arr = if (text.startsWith("[")) JSONArray(text) else JSONArray().put(JSONObject(text))
    val out = mutableListOf<FullPlanObject>()
    for (i in 0 until arr.length()) {
        val p = arr.optJSONObject(i) ?: continue
        val name = p.optString("name").takeIf { it.isNotBlank() } ?: continue
        val goal = p.optString("goal").ifBlank { "General Fitness" }
        val description = p.optString("description")
        val daysJson = p.optJSONArray("days") ?: JSONArray()
        val days = mutableListOf<FullPlanDay>()
        for (di in 0 until daysJson.length()) {
            val d = daysJson.optJSONObject(di) ?: continue
            val exJson = d.optJSONArray("exercises") ?: JSONArray()
            val exercises = mutableListOf<PlanExerciseInput>()
            for (ei in 0 until exJson.length()) {
                val e = exJson.optJSONObject(ei) ?: continue
                exercises += PlanExerciseInput(
                    exerciseId = e.optString("exerciseId").takeIf { it.isNotBlank() },
                    name = e.optString("name").takeIf { it.isNotBlank() },
                    sets = e.optInt("sets", 3),
                    reps = e.optString("reps").ifBlank { "8-12" },
                    restSeconds = e.optInt("restSeconds", 90),
                    supersetGroup = e.optString("supersetGroup"),
                    isWarmup = false,
                    notes = e.optString("notes"),
                )
            }
            days += FullPlanDay(name = d.optString("name").ifBlank { "Day ${di + 1}" }, color = d.optString("color").ifBlank { "#FF4500" }, exercises = exercises)
        }
        out += FullPlanObject(name = name, goal = goal, description = description, days = days)
    }
    return out
}

fun matchExercise(name: String?, exerciseIndex: List<LegacyExerciseShape>): String? {
    if (name.isNullOrBlank()) return null
    val lower = name.lowercase()
    val exact = exerciseIndex.firstOrNull { it.name.lowercase() == lower }
    if (exact != null) return exact.id
    val partial = exerciseIndex.firstOrNull { it.name.lowercase().contains(lower) || lower.contains(it.name.lowercase()) }
    return partial?.id
}


