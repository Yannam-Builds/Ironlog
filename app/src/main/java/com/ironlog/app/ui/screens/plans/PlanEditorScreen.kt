package com.ironlog.app.ui.screens.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalContext
import com.ironlog.app.domain.intelligence.CloudAiEngine
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.valentinilk.shimmer.shimmer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.UiPlanExercise
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.PlansViewModel
import com.ironlog.app.util.buildFilterChipOptions
import com.ironlog.app.util.getExerciseFilterSummary
import com.ironlog.app.util.matchesExerciseFilter
import com.ironlog.app.util.queryExerciseSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val DAY_COLORS = listOf("#FF4500", "#0080FF", "#00C170", "#A020F0", "#FFD700", "#FF6B35", "#00BCD4")

@Serializable
data class ProgramRules(
    val progressionModel: String = "double_progression",
    val blockLengthWeeks: Int = 4,
    val currentWeek: Int = 1,
    val deloadEveryWeeks: Int = 4,
    val percent1RM: Int = 75,
    val rpeTarget: Int = 8,
    val rirTarget: Int = 2,
)

private val rulesJson = Json { ignoreUnknownKeys = true }
private fun rulesKey(planId: String) = "program_rules:$planId"

@Composable
fun PlanEditorScreen(
    planId: String,
    vm: PlansViewModel = viewModel(),
    exerciseRepo: ExerciseRepository = ExerciseRepository(),
    onBack: () -> Unit = {},
    onStartWorkout: (dayId: String) -> Unit = {}
) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository() }
    val appVm: AppDataViewModel = viewModel()
    val appState by appVm.state.collectAsState()
    val cloudSettings = appState.settings
    val cloudApiKey = remember(cloudSettings.cloudAiProviderPreset) {
        CloudAiKeyStore.load(context, cloudSettings.cloudAiProviderPreset)
    }
    val cloudConfigured = cloudApiKey.isNotBlank()
        && cloudSettings.cloudAiBaseUrl.isNotBlank()
        && cloudSettings.cloudAiModelName.isNotBlank()
    val plans by vm.plans.collectAsState()
    val plan = plans.firstOrNull { it.id == planId }

    var editDayIdx by remember(planId) { mutableStateOf(0) }
    var showAddDay by remember { mutableStateOf(false) }
    var dayName by remember { mutableStateOf("") }
    var libSearch by remember { mutableStateOf("") }
    var debouncedLibSearch by remember { mutableStateOf("") }
    var libMuscle by remember { mutableStateOf<String?>(null) }
    var allExercises by remember { mutableStateOf<List<LegacyExerciseShape>>(emptyList()) }

    var editingPlanName by remember { mutableStateOf(false) }
    var planNameInput by remember { mutableStateOf("") }

    var editingDayId by remember { mutableStateOf<String?>(null) }
    var dayNameInput by remember { mutableStateOf("") }

    var rules by remember { mutableStateOf(ProgramRules()) }

    LaunchedEffect(Unit) { allExercises = exerciseRepo.getExercisesSnapshot() }
    LaunchedEffect(libSearch) { delay(170); debouncedLibSearch = libSearch }
    LaunchedEffect(planId) {
        val raw = settingsRepo.getString(rulesKey(planId))
        if (!raw.isNullOrBlank()) {
            runCatching { rules = rulesJson.decodeFromString<ProgramRules>(raw) }
        }
    }

    fun saveRules(next: ProgramRules) {
        rules = next
        scope.launch {
            runCatching { settingsRepo.setSetting(rulesKey(planId), rulesJson.encodeToString(next)) }
        }
    }

    if (plan == null) {
        Column(Modifier.fillMaxSize().background(c.bg).padding(16.dp)) { Text("Plan not found", color = c.text) }
        return
    }

    val activeDay = plan.days.getOrNull(editDayIdx)
    val libMuscles = remember(allExercises) { buildFilterChipOptions(allExercises, includeCategory = false, includeEquipment = false) }
    val filteredExercises = remember(allExercises, debouncedLibSearch, libMuscle) {
        val base = allExercises.filter { ex -> libMuscle == null || matchesExerciseFilter(ex, libMuscle) }
        queryExerciseSearch(base, debouncedLibSearch)
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val offset = 3
        val fromIdx = from.index - offset
        val toIdx = to.index - offset
        if (activeDay != null && fromIdx in activeDay.exercises.indices && toIdx in activeDay.exercises.indices) {
            val ids = activeDay.exercises.map { it.id }.toMutableList()
            java.util.Collections.swap(ids, fromIdx, toIdx)
            vm.reorderExercises(activeDay.id, ids)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        item { ScreenHeader(title = "EDIT PLAN", onBack = onBack) }
        item {
            if (editingPlanName) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = planNameInput,
                        onValueChange = { planNameInput = it },
                        label = { Text("Plan name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(onClick = {
                        if (planNameInput.isNotBlank()) vm.renamePlan(plan.id, planNameInput.trim())
                        editingPlanName = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = c.accent)) { Text("SAVE", color = c.textOnAccent) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    planNameInput = plan.name; editingPlanName = true
                }) {
                    Text(plan.name, color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.title.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text("✏", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            val isDeloadWeek = rules.deloadEveryWeeks > 0 && rules.currentWeek % rules.deloadEveryWeeks == 0
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(plan.days) { idx, day ->
                    AssistChip(
                        onClick = { editDayIdx = idx },
                        label = {
                            if (isDeloadWeek) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(day.name, color = if (idx == editDayIdx) c.accent else c.text)
                                    Text(
                                        "DELOAD",
                                        color = c.warning,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp,
                                    )
                                }
                            } else {
                                Text(day.name, color = if (idx == editDayIdx) c.accent else c.text)
                            }
                        },
                    )
                }
                item {
                    AssistChip(onClick = { showAddDay = !showAddDay }, label = { Text("+ Day") })
                }
            }
            if (showAddDay) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(dayName, { dayName = it }, placeholder = { Text("Day name") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = {
                        if (dayName.trim().isNotEmpty()) {
                            vm.addDay(plan.id, dayName.trim(), DAY_COLORS[plan.days.size % DAY_COLORS.size])
                            dayName = ""; showAddDay = false
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = c.accent)) { Text("ADD", color = c.textOnAccent) }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                shape = RoundedCornerShape(IronLogRadius.xl.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("PROGRAM RULES", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(700), letterSpacing = 1.2.sp)
                            Text("Week ${rules.currentWeek} of ${rules.blockLengthWeeks}", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.title.fontSize.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier
                                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                    .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                    .clickable {
                                        val max = rules.blockLengthWeeks
                                        val prev = ((rules.currentWeek - 2 + max) % max) + 1
                                        saveRules(rules.copy(currentWeek = prev))
                                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("−", color = c.accent, fontWeight = FontWeight.Bold) }
                            Box(
                                Modifier
                                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                    .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                    .clickable {
                                        val max = rules.blockLengthWeeks
                                        val next = (rules.currentWeek % max) + 1
                                        saveRules(rules.copy(currentWeek = next))
                                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("+", color = c.accent, fontWeight = FontWeight.Bold) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("double_progression" to "Double", "linear" to "Linear", "percent_1rm" to "%1RM", "rpe_rir" to "RPE/RIR").forEach { (id, label) ->
                            val active = rules.progressionModel == id
                            Box(
                                Modifier
                                    .background(if (active) c.accent else c.surface, RoundedCornerShape(IronLogRadius.full.dp))
                                    .border(1.dp, if (active) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                    .clickable { saveRules(rules.copy(progressionModel = id)) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text(label, color = if (active) c.textOnAccent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight(600)) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 4, 5, 6).forEach { weeks ->
                            val active = rules.blockLengthWeeks == weeks
                            Box(
                                Modifier
                                    .background(if (active) c.accent else c.surface, RoundedCornerShape(IronLogRadius.full.dp))
                                    .border(1.dp, if (active) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                    .clickable { saveRules(rules.copy(blockLengthWeeks = weeks, currentWeek = minOf(rules.currentWeek, weeks), deloadEveryWeeks = weeks)) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text("${weeks}wk", color = if (active) c.textOnAccent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight(600)) }
                        }
                    }

                    if (rules.progressionModel == "percent_1rm") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(65, 70, 75, 80, 85).forEach { pct ->
                                val active = rules.percent1RM == pct
                                Box(
                                    Modifier
                                        .background(if (active) c.accent else c.surface, RoundedCornerShape(IronLogRadius.full.dp))
                                        .border(1.dp, if (active) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                        .clickable { saveRules(rules.copy(percent1RM = pct)) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) { Text("$pct%", color = if (active) c.textOnAccent else c.muted, fontSize = IronLogType.meta.fontSize.sp) }
                            }
                        }
                    }

                    if (rules.progressionModel == "rpe_rir") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 8, 9).forEach { rpe ->
                                val active = rules.rpeTarget == rpe
                                Box(
                                    Modifier
                                        .background(if (active) c.accent else c.surface, RoundedCornerShape(IronLogRadius.full.dp))
                                        .border(1.dp, if (active) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                        .clickable { saveRules(rules.copy(rpeTarget = rpe, rirTarget = maxOf(0, 10 - rpe))) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) { Text("RPE $rpe", color = if (active) c.textOnAccent else c.muted, fontSize = IronLogType.meta.fontSize.sp) }
                            }
                        }
                    }

                    Text("Active workouts use these as suggested targets only. Logged sets are never rewritten.", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                }
            }
        }

        if (activeDay != null) {
            item {
                val dayId = activeDay.id
                var dayMenuExpanded by remember(dayId) { mutableStateOf(false) }
                var showCopyDayPicker by remember(dayId) { mutableStateOf(false) }
                var showAiReviewSheet by remember(dayId) { mutableStateOf(false) }
                var aiReviewText by remember(dayId) { mutableStateOf<String?>(null) }
                var aiReviewLoading by remember(dayId) { mutableStateOf(false) }
                var showDeloadConfirm by remember(dayId) { mutableStateOf(false) }
                var deloadCreating by remember(dayId) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (editingDayId == activeDay.id) {
                            OutlinedTextField(
                                value = dayNameInput,
                                onValueChange = { dayNameInput = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Day name") },
                            )
                            Spacer(Modifier.width(6.dp))
                            Button(onClick = {
                                if (dayNameInput.isNotBlank()) vm.renameDay(activeDay.id, dayNameInput.trim())
                                editingDayId = null
                            }, colors = ButtonDefaults.buttonColors(containerColor = c.accent)) { Text("SAVE", color = c.textOnAccent) }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(activeDay.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.section.fontSize.sp)
                                Spacer(Modifier.width(4.dp))
                                Box {
                                    IconButton(onClick = { dayMenuExpanded = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = c.muted)
                                    }
                                    DropdownMenu(dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }, modifier = Modifier.background(c.surface)) {
                                        DropdownMenuItem(text = { Text("Rename Day", color = c.text) }, onClick = { dayMenuExpanded = false; dayNameInput = activeDay.name; editingDayId = activeDay.id })
                                        DropdownMenuItem(text = { Text("Copy Day to…", color = c.text) }, onClick = { dayMenuExpanded = false; showCopyDayPicker = true })
                                        DropdownMenuItem(text = { Text("Delete Day", color = c.danger) }, onClick = {
                                            dayMenuExpanded = false
                                            vm.removeDay(activeDay.id)
                                            if (editDayIdx >= plan.days.size - 1) editDayIdx = maxOf(0, plan.days.size - 2)
                                        })
                                        if (cloudConfigured) {
                                            DropdownMenuItem(
                                                text = { Text("Review with Cloud AI", color = c.accent) },
                                                onClick = {
                                                    dayMenuExpanded = false
                                                    aiReviewText = null
                                                    showAiReviewSheet = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Generate Deload Day", color = c.accent) },
                                                onClick = {
                                                    dayMenuExpanded = false
                                                    showDeloadConfirm = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (showCopyDayPicker) {
                                val otherDays = plan.days.filter { it.id != activeDay.id }
                                AlertDialog(
                                    onDismissRequest = { showCopyDayPicker = false },
                                    title = { Text("Copy Day to…", color = c.text) },
                                    text = {
                                        if (otherDays.isEmpty()) {
                                            Text("No other days available.", color = c.muted)
                                        } else {
                                            Column {
                                                otherDays.forEach { targetDay ->
                                                    Text(
                                                        text = targetDay.name,
                                                        color = c.text,
                                                        fontSize = IronLogType.body.fontSize.sp,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                vm.copyDay(activeDay.id, targetDay.id)
                                                                showCopyDayPicker = false
                                                            }
                                                            .padding(vertical = 12.dp)
                                                    )
                                                    HorizontalDivider(color = c.cardBorder.copy(alpha = 0.5f))
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {},
                                    dismissButton = {
                                        TextButton(onClick = { showCopyDayPicker = false }) {
                                            Text("Cancel", color = c.muted)
                                        }
                                    },
                                    containerColor = c.surface,
                                )
                            }
                            Button(
                                onClick = { onStartWorkout(activeDay.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                                shape = RoundedCornerShape(IronLogRadius.full.dp)
                            ) { Text("START", color = c.textOnAccent, fontWeight = FontWeight.Black) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        DAY_COLORS.forEach { hex ->
                            val swatch = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(c.accent)
                            val selected = activeDay.color.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { vm.updateDayColor(activeDay.id, hex) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) c.text else c.cardBorder,
                                            shape = CircleShape,
                                        )
                                )
                            }
                        }
                    }
                }

                @OptIn(ExperimentalMaterial3Api::class)
                if (showAiReviewSheet) {
                    LaunchedEffect(showAiReviewSheet) {
                        if (aiReviewText != null) return@LaunchedEffect
                        aiReviewLoading = true
                        aiReviewText = CloudAiEngine.askDayEvaluation(
                            baseUrl       = cloudSettings.cloudAiBaseUrl,
                            apiKey        = cloudApiKey,
                            modelName     = cloudSettings.cloudAiModelName,
                            apiFormat     = cloudSettings.cloudAiApiFormat,
                            dayName       = activeDay.name,
                            exerciseNames = activeDay.exercises.map { it.name },
                            goalMode      = cloudSettings.goalMode,
                        )
                        aiReviewLoading = false
                    }

                    ModalBottomSheet(
                        onDismissRequest = { showAiReviewSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = c.card,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "CLOUD AI REVIEW",
                                color = c.accent,
                                fontSize = IronLogType.eyebrow.fontSize.sp,
                                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                            )
                            Text(activeDay.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.section.fontSize.sp)
                            Text(
                                activeDay.exercises.joinToString(" · ") { it.name },
                                color = c.muted,
                                fontSize = IronLogType.meta.fontSize.sp,
                            )
                            if (aiReviewLoading) {
                                Column(Modifier.shimmer(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    repeat(3) { idx ->
                                        Box(
                                            Modifier
                                                .fillMaxWidth(if (idx == 2) 0.6f else 1f)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(c.faint)
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    aiReviewText ?: "No evaluation available.",
                                    color = c.text,
                                    fontSize = IronLogType.body.fontSize.sp,
                                    lineHeight = IronLogType.body.lineHeight.sp,
                                )
                                Row(
                                    Modifier
                                        .clickable {
                                            aiReviewText = null
                                            aiReviewLoading = true
                                            scope.launch {
                                                aiReviewText = CloudAiEngine.askDayEvaluation(
                                                    baseUrl       = cloudSettings.cloudAiBaseUrl,
                                                    apiKey        = cloudApiKey,
                                                    modelName     = cloudSettings.cloudAiModelName,
                                                    apiFormat     = cloudSettings.cloudAiApiFormat,
                                                    dayName       = activeDay.name,
                                                    exerciseNames = activeDay.exercises.map { it.name },
                                                    goalMode      = cloudSettings.goalMode,
                                                )
                                                aiReviewLoading = false
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Outlined.Refresh, null, tint = c.muted, modifier = Modifier.size(12.dp))
                                    Text("Regenerate", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                if (showDeloadConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeloadConfirm = false },
                        title = { Text("Generate Deload Day", color = c.text) },
                        text = {
                            Text(
                                "This will create \"${activeDay.name} (Deload)\" with the same exercises at half the sets and a 60%-weight note.",
                                color = c.subtext,
                                fontSize = IronLogType.body.fontSize.sp,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeloadConfirm = false
                                    if (!deloadCreating) {
                                        deloadCreating = true
                                        scope.launch {
                                            val newDayId = vm.addDayAndGetId(
                                                planId = planId,
                                                name   = "${activeDay.name} (Deload)",
                                                color  = activeDay.color.ifBlank { "#555555" },
                                            )
                                            try {
                                                activeDay.exercises.forEach { ex ->
                                                    if (ex.exerciseId.isBlank()) return@forEach
                                                    vm.addExerciseToDay(
                                                        dayId      = newDayId,
                                                        exerciseId = ex.exerciseId,
                                                        meta       = PlanExerciseInput(
                                                            sets        = maxOf(2, ex.sets / 2),
                                                            reps        = ex.reps,
                                                            restSeconds = ex.restSeconds,
                                                            notes       = "Deload: use 60% of working weight",
                                                        ),
                                                    )
                                                }
                                            } finally {
                                                deloadCreating = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                            ) {
                                Text("Generate", color = c.textOnAccent)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeloadConfirm = false }) {
                                Text("Cancel", color = c.muted)
                            }
                        },
                        containerColor = c.card,
                    )
                }
            }

            if (activeDay.exercises.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No exercises yet", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.section.fontSize.sp)
                        Text("Search the library below to start building your routine.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                    }
                }
            }

            itemsIndexed(activeDay.exercises, key = { _, ex -> ex.id }) { index, ex ->
                val alternatives = remember(ex.id, allExercises) { buildAlternativesForPlanExercise(ex, allExercises) }
                ReorderableItem(reorderState, key = ex.id) { isDragging ->
                    PlanExerciseRow(
                        ex = ex,
                        alternatives = alternatives,
                        index = index,
                        isDragging = isDragging,
                        onRemove = { vm.removeExercise(ex.id) },
                        onSuperset = { group -> vm.updateExercise(ex.id, PlanExerciseInput(supersetGroup = group ?: "")) },
                        onSetsChange = { sets -> vm.updateExercise(ex.id, PlanExerciseInput(sets = sets)) },
                        onRepsChange = { reps -> vm.updateExercise(ex.id, PlanExerciseInput(reps = reps)) },
                        onRestChange = { rest -> vm.updateExercise(ex.id, PlanExerciseInput(restSeconds = rest)) },
                        onNotesChange = { notes -> vm.updateExercise(ex.id, PlanExerciseInput(notes = notes)) },
                        onSwapExercise = { alt -> vm.updateExercise(ex.id, PlanExerciseInput(exerciseId = alt.id)) },
                        dragModifier = Modifier.draggableHandle()
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
                ) {
                    Column {
                        // Search row
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(15.dp))
                            BasicTextField(
                                value = libSearch,
                                onValueChange = { libSearch = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = TextStyle(color = c.text, fontSize = IronLogType.body.fontSize.sp),
                                cursorBrush = SolidColor(c.accent),
                                decorationBox = { inner ->
                                    if (libSearch.isEmpty()) Text("Search exercises…", color = c.faint, fontSize = IronLogType.body.fontSize.sp)
                                    inner()
                                },
                            )
                            if (libSearch.isNotEmpty()) {
                                Text("✕", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.clickable { libSearch = "" }.padding(8.dp))
                            }
                            Text("ADD EXERCISE", color = c.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                        HorizontalDivider(color = c.cardBorder)
                        // Filter chips
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item {
                                val isAll = libMuscle == null
                                Box(
                                    Modifier
                                        .background(if (isAll) c.accent.copy(alpha = 0.18f) else c.surface, RoundedCornerShape(IronLogRadius.full.dp))
                                        .border(1.dp, if (isAll) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                        .clickable { libMuscle = null }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) { Text("All", color = if (isAll) c.accent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal) }
                            }
                            items(libMuscles, key = { it }) { m ->
                                val isActive = libMuscle == m
                                Box(
                                    Modifier
                                        .background(if (isActive) c.accent.copy(alpha = 0.18f) else c.surface, RoundedCornerShape(IronLogRadius.full.dp))
                                        .border(1.dp, if (isActive) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                        .clickable { libMuscle = if (isActive) null else m }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) { Text(m, color = if (isActive) c.accent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                        HorizontalDivider(color = c.cardBorder)
                        // Compact exercise rows
                        val displayExercises = filteredExercises.take(60)
                        if (displayExercises.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("No exercises found", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                            }
                        } else {
                            displayExercises.forEachIndexed { idx, ex ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            vm.addExerciseToDay(
                                                activeDay.id,
                                                ex.id,
                                                PlanExerciseInput(exerciseId = ex.id, sets = 3, reps = if (inferTrackingType(ex) == "weight_reps") "10" else "60", restSeconds = 90),
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(ex.name, color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(8.dp))
                                    Text(getExerciseFilterSummary(ex), color = c.muted, fontSize = IronLogType.meta.fontSize.sp, maxLines = 1)
                                }
                                if (idx < displayExercises.lastIndex) {
                                    HorizontalDivider(color = c.cardBorder.copy(alpha = 0.45f), modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanExerciseRow(
    ex: UiPlanExercise,
    alternatives: List<ExerciseAlternative>,
    index: Int,
    isDragging: Boolean,
    onRemove: () -> Unit,
    onSuperset: (String?) -> Unit,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (String) -> Unit,
    onRestChange: (Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onSwapExercise: (LegacyExerciseShape) -> Unit,
    dragModifier: Modifier = Modifier
) {
    val c = useTheme()
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository() }
    var setsInput by remember(ex.id, ex.sets) { mutableStateOf(ex.sets.toString()) }
    var repsInput by remember(ex.id, ex.reps) { mutableStateOf(ex.reps) }
    var restInput by remember(ex.id, ex.restSeconds) { mutableStateOf(ex.restSeconds.toString()) }
    var notesInput by remember(ex.id, ex.notes) { mutableStateOf(ex.notes) }
    var notesExpanded by remember { mutableStateOf(ex.notes.isNotBlank()) }
    var alternativesExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    // Per-exercise progression override (empty string = use plan default)
    var progressionOverride by remember(ex.id) { mutableStateOf("") }
    LaunchedEffect(ex.id) {
        progressionOverride = settingsRepo.getString("ex_prog:${ex.id}").orEmpty()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isDragging) c.surface else c.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDragging) c.accent else c.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}. ${ex.name}", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ex.supersetGroup.isNotBlank()) {
                            Box(
                                Modifier
                                    .background(c.accent.copy(alpha = 0.15f), RoundedCornerShape(IronLogRadius.full.dp))
                                    .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(IronLogRadius.full.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) { Text(ex.supersetGroup, color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(700)) }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = c.muted)
                    }
                    DropdownMenu(showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(c.surface)) {
                        DropdownMenuItem(text = { Text(if (alternativesExpanded) "Hide Alternatives" else "Replace Exercise", color = c.text) }, onClick = { showMenu = false; alternativesExpanded = !alternativesExpanded })
                        DropdownMenuItem(text = { Text("Remove", color = c.danger) }, onClick = { showMenu = false; onRemove() })
                    }
                }
                Box(dragModifier.padding(4.dp)) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Reorder", tint = c.faint, modifier = Modifier.size(22.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = setsInput,
                    onValueChange = { setsInput = it; it.toIntOrNull()?.takeIf { v -> v > 0 }?.let(onSetsChange) },
                    label = { Text("Sets", fontSize = IronLogType.meta.fontSize.sp) },
                    textStyle = TextStyle(fontSize = IronLogType.body.fontSize.sp, color = c.text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = repsInput,
                    onValueChange = { repsInput = it; if (it.isNotBlank()) onRepsChange(it) },
                    label = { Text("Reps", fontSize = IronLogType.meta.fontSize.sp) },
                    textStyle = TextStyle(fontSize = IronLogType.body.fontSize.sp, color = c.text),
                    modifier = Modifier.weight(1.2f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = restInput,
                    onValueChange = { restInput = it; it.toIntOrNull()?.takeIf { v -> v >= 0 }?.let(onRestChange) },
                    label = { Text("Rest s", fontSize = IronLogType.meta.fontSize.sp) },
                    textStyle = TextStyle(fontSize = IronLogType.body.fontSize.sp, color = c.text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(null, "A", "B", "C").forEach { group ->
                    Text(
                        group ?: "NO SS",
                        color = if ((ex.supersetGroup.ifBlank { null }) == group) c.accent else c.text,
                        fontSize = IronLogType.meta.fontSize.sp,
                        modifier = Modifier.clickable { onSuperset(group) }.padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }

            // Per-exercise progression model override
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text("Prog:", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                listOf("" to "Plan", "double_progression" to "2x", "linear" to "Lin", "percent_1rm" to "%1RM", "rpe_rir" to "RPE").forEach { (id, label) ->
                    val isActive = progressionOverride == id
                    Box(
                        Modifier
                            .background(if (isActive) c.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(IronLogRadius.full.dp))
                            .border(1.dp, if (isActive) c.accent else c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                            .clickable {
                                progressionOverride = id
                                scope.launch { settingsRepo.setSetting("ex_prog:${ex.id}", id) }
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            label,
                            color = if (isActive) c.accent else c.muted,
                            fontSize = 9.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (notesExpanded) {
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it; onNotesChange(it) },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    maxLines = 2,
                )
            } else {
                Text(
                    if (notesInput.isBlank()) "+ Add note" else "📝 $notesInput",
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                    modifier = Modifier.clickable { notesExpanded = true }.padding(top = 4.dp, bottom = 10.dp),
                )
            }

            if (alternativesExpanded && alternatives.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                alternatives.take(8).forEach { alt ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSwapExercise(alt.exercise); alternativesExpanded = false }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(alt.exercise.name, color = c.text, fontSize = IronLogType.body.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(alt.reason, color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    }
                }
            }
        }
    }
}

private data class ExerciseAlternative(val exercise: LegacyExerciseShape, val reason: String)

private fun buildAlternativesForPlanExercise(
    ex: UiPlanExercise,
    allExercises: List<LegacyExerciseShape>,
): List<ExerciseAlternative> {
    val current = allExercises.firstOrNull { it.id == ex.exerciseId } ?: return emptyList()
    val sameMuscle = allExercises
        .filter { it.id != current.id && it.primaryMuscle.equals(current.primaryMuscle, ignoreCase = true) }
        .take(3)
        .map { ExerciseAlternative(it, "Same Muscle") }
    val sameEquipment = allExercises
        .filter { it.id != current.id && it.equipment.equals(current.equipment, ignoreCase = true) }
        .take(3)
        .map { ExerciseAlternative(it, "Same Equipment") }
    val fallback = allExercises
        .filter { it.id != current.id && it.category.equals(current.category, ignoreCase = true) }
        .take(3)
        .map { ExerciseAlternative(it, "Fallback") }
    return (sameMuscle + sameEquipment + fallback)
        .distinctBy { it.exercise.id }
}

fun inferTrackingType(exercise: LegacyExerciseShape): String {
    val tracking = exercise.trackingType.trim()
    if (tracking.isNotBlank()) return tracking
    val signal = "${exercise.category.lowercase()} ${exercise.name.lowercase()} ${exercise.movementPattern.orEmpty().lowercase()}"
    return when {
        Regex("stretch|hold|plank|mobility|isometric").containsMatchIn(signal) -> "duration"
        Regex("treadmill|bike|erg|rower|run|walk|ski|carry|conditioning|cardio|distance|locomotion").containsMatchIn(signal) -> "duration_distance"
        else -> "weight_reps"
    }
}

fun buildPlanExerciseFromLibrary(exercise: LegacyExerciseShape, previous: UiPlanExercise? = null): UiPlanExercise {
    val trackingType = inferTrackingType(exercise)
    return UiPlanExercise(
        id = previous?.id ?: exercise.id,
        exerciseId = exercise.id,
        name = exercise.name,
        sets = previous?.sets ?: 3,
        reps = previous?.reps ?: if (trackingType == "weight_reps") "10" else "60",
        restSeconds = previous?.restSeconds ?: 90,
        supersetGroup = previous?.supersetGroup ?: "",
        isWarmup = previous?.isWarmup ?: false,
        notes = previous?.notes ?: "",
    )
}


