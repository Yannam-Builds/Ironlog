package com.ironlog.app.ui.screens.stats

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.data.repository.HistoryRepository
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.StatsViewModel
import com.ironlog.app.util.formatVolumeFromKg
import com.ironlog.app.util.formatWeightFromKg
import java.time.Instant
import com.ironlog.app.domain.gamification.parseHistoryInstant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: StatsViewModel = viewModel(),
    onOpenProgressPhotos: () -> Unit = {},
    onGoHome: () -> Unit = {},
) {
    val c = useTheme()
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val historyRepository = remember { HistoryRepository() }
    val weightUnit = state.weightUnit
    var editingLog by remember { mutableStateOf<HistoryEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeExerciseFilter by remember { mutableStateOf<String?>(null) }
    var showExerciseFilterSheet by remember { mutableStateOf(false) }
    // GAP-21: Bulk select/delete
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    // FIXED: 20 — confirmClearAll moved to SettingsScreen DATA & BACKUP section

    // Filter history by search query (matches workout name or any exercise name)
    val filteredHistory = remember(state.history, searchQuery, activeExerciseFilter) {
        var result = if (searchQuery.isBlank()) state.history
        else {
            val q = searchQuery.trim().lowercase()
            state.history.filter { entry ->
                entry.name.lowercase().contains(q) ||
                    entry.exercises.any { it.name.lowercase().contains(q) } ||
                    entry.date.substringBefore('T').contains(q)
            }
        }
        activeExerciseFilter?.let { filter ->
            result = result.filter { entry -> entry.exercises.any { it.name == filter } }
        }
        result
    }

    val allExerciseNames = remember(state.history) {
        state.history.flatMap { it.exercises.map { ex -> ex.name } }.distinct().sorted()
    }

    LazyColumn(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "WORKOUT LOG",
                color = c.accent,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
            Text(
                "History",
                color = c.text,
                fontWeight = FontWeight(IronLogType.display.fontWeight),
                fontSize = IronLogType.title.fontSize.sp,
                lineHeight = IronLogType.title.lineHeight.sp,
            )
            Text(
                if (searchQuery.isBlank()) "${state.history.size} session${if (state.history.size == 1) "" else "s"}"
                else "${filteredHistory.size} of ${state.history.size} sessions",
                color = c.muted,
                fontSize = IronLogType.body.fontSize.sp,
            )
            Spacer(Modifier.height(12.dp))
            // Search bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search workouts or exercises…", color = c.muted, fontSize = IronLogType.body.fontSize.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = c.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = { showExerciseFilterSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (activeExerciseFilter != null) c.accentSoft else c.surface,
                            androidx.compose.foundation.shape.RoundedCornerShape(IronLogRadius.md.dp)
                        )
                        .border(1.dp, if (activeExerciseFilter != null) c.accentBorder else c.cardBorder, androidx.compose.foundation.shape.RoundedCornerShape(IronLogRadius.md.dp)),
                ) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = "Filter by exercise",
                        tint = if (activeExerciseFilter != null) c.accent else c.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // Active exercise filter chip
            activeExerciseFilter?.let { filter ->
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(IronLogRadius.full.dp))
                        .background(c.accentSoft)
                        .border(1.dp, c.accentBorder, androidx.compose.foundation.shape.RoundedCornerShape(IronLogRadius.full.dp))
                        .clickable { activeExerciseFilter = null }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(filter, color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Outlined.Close, contentDescription = "Remove filter", tint = c.accent, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            // Progress Photos — styled card row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(IronLogRadius.md.dp))
                    .background(c.card)
                    .clickable { onOpenProgressPhotos() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Text("PROGRESS PHOTOS", color = c.text, fontWeight = FontWeight(IronLogType.body.fontWeight), fontSize = IronLogType.body.fontSize.sp, letterSpacing = 0.8.sp)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(4.dp))
        }
        if (filteredHistory.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    headline = if (searchQuery.isBlank()) "No workouts yet" else "No results for \"$searchQuery\"",
                    body = if (searchQuery.isBlank()) "Start your first session to see your history here." else "Try a different search term.",
                    ctaLabel = if (searchQuery.isBlank()) "GO TO TODAY" else "CLEAR SEARCH",
                    onCta = if (searchQuery.isBlank()) onGoHome else { { searchQuery = "" } },
                )
            }
        }
        // FIXED: 18 — month-grouped sticky headers
        val grouped = filteredHistory.groupBy { entry ->
            val instant = parseHistoryInstant(entry.date) ?: Instant.EPOCH
            if (instant == Instant.EPOCH) "Unknown"
            else DateTimeFormatter.ofPattern("MMMM yyyy").withZone(ZoneId.systemDefault()).format(instant)
        }
        grouped.forEach { (monthLabel, unsortedEntries) ->
            val entries = unsortedEntries.sortedByDescending { it.date }
            stickyHeader(key = "header_$monthLabel") {
                Box(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        monthLabel.uppercase(),
                        color = c.muted,
                        fontSize = IronLogType.eyebrow.fontSize.sp,
                        fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                        letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                    )
                }
            }
            itemsIndexed(entries, key = { _, h -> h.id }) { index, h ->
                val previousVolume = entries.getOrNull(index + 1)?.volume
                val isSelected = h.id in selectedIds
                HistoryCard(
                    h = h,
                    weightUnit = weightUnit,
                    previousVolume = previousVolume,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onTap = {
                        if (isSelectionMode) {
                            selectedIds = if (isSelected) selectedIds - h.id else selectedIds + h.id
                        } else {
                            editingLog = h
                        }
                    },
                    onLongPress = {
                        selectedIds = selectedIds + h.id
                    },
                    onShare = if (isSelectionMode) null else { shareText ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share workout"))
                    },
                )
            }
        }
        if (state.history.isNotEmpty()) {
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    // GAP-21: Floating bulk-delete bar
    if (isSelectionMode) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.danger)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { selectedIds = emptySet() }) {
                    Text("CANCEL", color = c.textOnAccent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp)
                }
                Text(
                    "${selectedIds.size} selected",
                    color = c.textOnAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = IronLogType.body.fontSize.sp,
                )
                TextButton(onClick = { showBulkDeleteConfirm = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = c.textOnAccent, modifier = Modifier.size(18.dp))
                        Text("DELETE (${selectedIds.size})", color = c.textOnAccent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp)
                    }
                }
            }
        }
    }

    // GAP-21: Bulk-delete confirmation dialog
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} workout${if (selectedIds.size == 1) "" else "s"}?", color = c.text) },
            text = { Text("This cannot be undone.", color = c.subtext) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = selectedIds.toList()
                    showBulkDeleteConfirm = false
                    scope.launch {
                        try {
                            historyRepository.deleteWorkouts(toDelete)
                            vm.refresh()
                        } finally {
                            // Always clear selection — even if delete throws —
                            // so the selection mode is never permanently stuck.
                            selectedIds = emptySet()
                        }
                    }
                }) {
                    Text("DELETE", color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("CANCEL", color = c.muted)
                }
            },
            containerColor = c.card,
        )
    }

    editingLog?.let { entry ->
        EditHistoryPanel(
            entry,
            onClose = { editingLog = null },
            onSave = { updated ->
                scope.launch {
                    historyRepository.updateWorkout(
                        uid = updated.id,
                        startedAt = parseHistoryDateTimeMillis(updated.date),
                        durationSeconds = updated.duration,
                        rating = updated.rating,
                        notes = updated.summaryText,
                    )
                    editingLog = null
                    vm.refresh()
                }
            },
            onDelete = {
                scope.launch {
                    historyRepository.deleteWorkout(entry.id)
                    editingLog = null
                    vm.refresh()
                }
            },
        )
    }

    // FIXED: 20 — Clear All History AlertDialog moved to SettingsScreen

    if (showExerciseFilterSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showExerciseFilterSheet = false },
            containerColor = c.card,
            contentColor = c.text,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "FILTER BY EXERCISE",
                    color = c.muted,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (allExerciseNames.isEmpty()) {
                    Text("No exercises in history yet.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                } else {
                    allExerciseNames.forEach { name ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeExerciseFilter = name
                                    showExerciseFilterSheet = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, color = c.text, fontSize = IronLogType.body.fontSize.sp)
                            if (activeExerciseFilter == name) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    h: HistoryEntry,
    weightUnit: String,
    previousVolume: Double? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onShare: ((String) -> Unit)? = null,
) {
    val c = useTheme()
    val instant = runCatching { Instant.parse(h.date) }.getOrDefault(Instant.now())
    val dateStr = DateTimeFormatter.ofPattern("dd MMM yy").withZone(ZoneId.systemDefault()).format(instant)
    val timeStr = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)
    val volumeDelta = previousVolume?.let { h.volume - it }

    // FIXED: 16, 38 — Tonal background (no left bar per step 38 audit); IntrinsicSize.Min retained
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(if (isSelected) c.accentSoft else c.card)
            .border(1.dp, if (isSelected) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            ),
    ) {
        // Card body (no left bar — date shown top-right per step 38)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    // Day name / workout name in accent
                    Text(
                        (h.name).uppercase(),
                        color = c.accent,
                        fontWeight = FontWeight(IronLogType.section.fontWeight),
                        fontSize = IronLogType.section.fontSize.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${h.sets} sets · ${h.duration / 60}min",
                        color = c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                    )
                    // Volume + delta
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            formatVolumeFromKg(h.volume, weightUnit),
                            color = c.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                        )
                        if (volumeDelta != null) {
                            val sign = if (volumeDelta >= 0) "+" else ""
                            // FIXED: 21 — use theme tokens instead of raw Color
                            Text(
                                "$sign${formatVolumeFromKg(abs(volumeDelta), weightUnit)} vs prev",
                                color = if (volumeDelta >= 0) c.success else c.danger,
                                fontSize = IronLogType.meta.fontSize.sp,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isSelectionMode) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(
                                    if (isSelected) c.accent else c.surface,
                                    CircleShape
                                )
                                .border(1.5.dp, if (isSelected) c.accent else c.cardBorder, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = c.textOnAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(dateStr, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                            Text(timeStr, color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        }
                    }
                    if (onShare != null) {
                        IconButton(
                            onClick = {
                                val exerciseLines = h.exercises.joinToString("\n") { ex ->
                                    val setsSummary = ex.sets.joinToString(", ") { s ->
                                        val w = if (s.weight > 0) formatWeightFromKg(s.weight, weightUnit) else "BW"
                                        "${w}×${s.reps.roundToInt()}"
                                    }
                                    "  ${ex.name}: $setsSummary"
                                }
                                val shareText = buildString {
                                    append("🏋️ ${h.name} — $dateStr $timeStr\n")
                                    append("${h.sets} sets · ${h.duration / 60}min · ${formatVolumeFromKg(h.volume, weightUnit)}\n")
                                    if (exerciseLines.isNotEmpty()) append("\n$exerciseLines\n")
                                    h.summaryText?.let { append("\n$it\n") }
                                    append("\nLogged with IronLog")
                                }
                                onShare(shareText)
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share workout", tint = c.muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            // Exercise breakdown: all exercises with set details
            if (h.exercises.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                h.exercises.forEach { ex ->
                    val setsSummary = ex.sets.joinToString(", ") { s ->
                        val w = if (s.weight > 0) formatWeightFromKg(s.weight, weightUnit) else "BW"
                        "${w}×${s.reps.roundToInt()}"
                    }
                    Text(
                        "${ex.name}: $setsSummary",
                        color = c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            h.summaryText?.let {
                Text(it, color = c.muted, fontSize = IronLogType.meta.fontSize.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            // FIXED: 19 — removed "Tap to edit" hint; card is tappable by its visible treatment
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditHistoryPanel(
    entry: HistoryEntry,
    onClose: () -> Unit,
    onSave: (HistoryEntry) -> Unit,
    onDelete: () -> Unit = {},
) {
    val c = useTheme()
    var date by remember(entry.id) { mutableStateOf(entry.date.substringBefore('T')) }
    var time by remember(entry.id) { mutableStateOf(entry.date.substringAfter('T', "00:00").take(5)) }
    var durationMinutes by remember(entry.id) { mutableStateOf(max(0, (entry.duration / 60.0).roundToInt()).toString()) }
    var rating by remember(entry.id) { mutableStateOf(entry.rating?.toString().orEmpty()) }
    var summaryText by remember(entry.id) { mutableStateOf(entry.summaryText.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var dateTimeError by remember(entry.id) { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.card,
        title = { Text("EDIT LOG", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.section.fontSize.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it; dateTimeError = null },
                        label = { Text("YYYY-MM-DD") },
                        isError = dateTimeError != null,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Pick date")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it; dateTimeError = null },
                        label = { Text("HH:MM") },
                        isError = dateTimeError != null,
                        trailingIcon = {
                            Text(
                                "PICK",
                                color = c.accent,
                                fontSize = IronLogType.meta.fontSize.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showTimePicker = true }.padding(end = 8.dp),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                dateTimeError?.let {
                    Text(it, color = c.danger, fontSize = IronLogType.meta.fontSize.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(durationMinutes, { durationMinutes = it }, label = { Text("MIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    // Star rating selector
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rating", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val currentRating = rating.toIntOrNull() ?: 0
                            (1..5).forEach { star ->
                                Text(
                                    text = if (star <= currentRating) "★" else "☆",
                                    color = if (star <= currentRating) c.warning else c.muted,
                                    fontSize = 22.sp,
                                    modifier = Modifier.clickable { rating = star.toString() },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(summaryText, { summaryText = it }, label = { Text("Summary") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                // DELETE LOG — destructive, prominent
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(IronLogRadius.md.dp))
                        .background(c.danger.copy(alpha = 0.10f))
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "DELETE LOG",
                        color = c.danger,
                        fontWeight = FontWeight(800),
                        fontSize = IronLogType.body.fontSize.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mergedIso = parseHistoryEditTimestampOrNull(date.trim(), time.trim())
                    if (mergedIso == null) {
                        dateTimeError = "Pick or enter a valid date and 24-hour time."
                        return@Button
                    }
                    onSave(
                        entry.copy(
                            date = mergedIso,
                            duration = (durationMinutes.toDoubleOrNull() ?: 0.0).roundToInt() * 60,
                            rating = rating.toDoubleOrNull()?.takeIf { it > 0 }?.let { min(5.0, max(1.0, it)) },
                            summaryText = summaryText.trim().ifBlank { null },
                        ),
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
            ) { Text("SAVE") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("CANCEL", color = c.muted) }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.card,
            title = { Text("Delete this workout?", color = c.text) },
            text = { Text("This cannot be undone.", color = c.muted) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("DELETE", color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("CANCEL", color = c.muted) }
            },
        )
    }

    if (showDatePicker) {
        val initialMillis = remember(date) {
            runCatching {
                LocalDate.parse(date)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(parseHistoryDateTimeMillis(entry.date))
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                        dateTimeError = null
                    }
                    showDatePicker = false
                }) { Text("SET", color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("CANCEL", color = c.muted) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val currentTime = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.MIDNIGHT)
        val timePickerState = rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = c.card,
            title = { Text("Pick time", color = c.text) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    dateTimeError = null
                    showTimePicker = false
                }) { Text("SET", color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("CANCEL", color = c.muted) }
            },
        )
    }
}

internal fun parseHistoryEditTimestampOrNull(date: String, time: String): String? =
    runCatching {
        val parsedDate = LocalDate.parse(date.trim())
        val parsedTime = LocalTime.parse(time.trim())
        LocalDateTime.of(parsedDate, parsedTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()
    }.getOrNull()

private fun parseHistoryDateTimeMillis(iso: String): Long {
    val existing = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
    if (existing != null) return existing
    return runCatching {
        val local = LocalDateTime.parse(iso.replace(" ", "T"))
        local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }
}


