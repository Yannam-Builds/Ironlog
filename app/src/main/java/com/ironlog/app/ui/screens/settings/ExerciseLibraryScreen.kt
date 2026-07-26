package com.ironlog.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.util.buildFilterChipOptions
import com.ironlog.app.util.matchesExerciseFilter
import com.ironlog.app.util.normalizeExerciseNameKey
import com.ironlog.app.util.queryExerciseSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val EQUIP_TAGS = setOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Band", "Kettlebell", "Other")

@Composable
fun ExerciseLibraryScreen(
    repo: ExerciseRepository = ExerciseRepository(),
    history: List<com.ironlog.app.ui.model.HistoryEntry> = emptyList(),
    onBack: () -> Unit = {},
    onCreateExercise: ((String) -> Unit)? = null,
    onExerciseClick: ((LegacyExerciseShape) -> Unit)? = null,
    onOpenExerciseProgress: ((String) -> Unit)? = null,
) {
    val c = useTheme()
    val context = LocalContext.current

    // Fix: use applicationContext for repo so seeding works
    val repoWithCtx = remember { ExerciseRepository(context.applicationContext) }

    var exercises by remember { mutableStateOf<List<LegacyExerciseShape>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var debouncedSearch by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf<String?>(null) }
    var equip by remember { mutableStateOf<String?>(null) }
    var movement by remember { mutableStateOf<String?>(null) }
    var difficulty by remember { mutableStateOf<String?>(null) }
    var bwOnly by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf("all") }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var videoMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirmDeleteExercise by remember { mutableStateOf<LegacyExerciseShape?>(null) }
    var activeProfileUnavailableEquipment by remember { mutableStateOf<Set<String>>(emptySet()) }
    // FIXED: 27 — filter bottom sheet state
    var showFilterSheet by remember { mutableStateOf(false) }
    // FIXED: 28 — search focus state
    var searchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository() }

    val recentlyUsed = remember(history) {
        history.flatMap { entry -> entry.exercises.map { it.name } }
            .distinct()
            .take(5)
    }

    LaunchedEffect(Unit) {
        exercises = repoWithCtx.getExercisesSnapshot()
        favoriteIds = settingsRepo.loadFavorites()
        videoMap = withContext(Dispatchers.IO) { loadExerciseVideoLinks(context) }
        // Load unavailable equipment from active gym profile
        runCatching {
            val activeId = settingsRepo.getString("active_gym_profile_id").orEmpty()
            val raw = settingsRepo.getString("gym_profiles_json").orEmpty()
            if (raw.isNotBlank() && activeId.isNotBlank()) {
                val profiles = org.json.JSONArray(raw)
                for (i in 0 until profiles.length()) {
                    val p = profiles.getJSONObject(i)
                    if (p.optString("id") == activeId) {
                        val arr = p.optJSONArray("unavailableEquipment")
                        if (arr != null) {
                            val set = mutableSetOf<String>()
                            for (j in 0 until arr.length()) set.add(arr.getString(j))
                            activeProfileUnavailableEquipment = set
                        }
                        break
                    }
                }
            }
        }
    }

    LaunchedEffect(search) {
        delay(180)
        debouncedSearch = search
    }

    // Filter chip option sets (derived from full list, not scoped, matching RN)
    val muscles = remember(exercises) {
        buildFilterChipOptions(exercises, includeCategory = false, includeEquipment = false)
    }
    val equipmentOptions = remember(exercises) {
        buildFilterChipOptions(exercises, includeCategory = false, includeEquipment = true)
            .filter { it in EQUIP_TAGS }
    }
    val categories = remember(exercises) {
        exercises.mapNotNull { it.category.takeIf(String::isNotBlank)?.replaceFirstChar { ch -> ch.titlecase() } }
            .distinct().sorted()
    }
    val movementOptions = remember(exercises) {
        exercises.mapNotNull { it.movementPattern }.distinct().sorted()
    }
    val difficultyOptions = remember(exercises) {
        listOf("beginner", "intermediate", "advanced", "expert")
            .filter { d -> exercises.any { it.difficulty?.lowercase() == d } }
    }

    val filtered = remember(exercises, muscle, cat, equip, movement, difficulty, bwOnly, scope, favoriteIds, debouncedSearch, activeProfileUnavailableEquipment) {
        val base = exercises.filter { ex ->
            (muscle == null || matchesExerciseFilter(ex, muscle)) &&
            (cat == null || ex.category.equals(cat, ignoreCase = true)) &&
            (equip == null || ex.equipment == equip) &&
            (movement == null || ex.movementPattern == movement) &&
            (difficulty == null || ex.difficulty?.lowercase() == difficulty) &&
            (!bwOnly || ex.isBodyweight) &&
            (scope != "favorites" || favoriteIds.contains(ex.id)) &&
            (scope != "custom" || ex.isCustom) &&
            // Respect active gym profile: hide exercises requiring unavailable equipment
            (activeProfileUnavailableEquipment.isEmpty() || !activeProfileUnavailableEquipment.contains(ex.equipment))
        }
        queryExerciseSearch(base, debouncedSearch)
            .sortedWith(compareByDescending<LegacyExerciseShape> { favoriteIds.contains(it.id) }.thenBy { it.name })
    }

    val missingExerciseSeed = search.trim()
    val hasExactMatch = exercises.any { it.name.equals(missingExerciseSeed, ignoreCase = true) }

    // FIXED: 27 — Hoisted so it's accessible by both the Column and the ModalBottomSheet
    val hasActiveFilters = muscle != null || cat != null || equip != null || movement != null || difficulty != null || bwOnly || scope != "all"

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {

        ScreenHeader(
            title = "EXERCISE LIBRARY",
            onBack = onBack,
            subtitle = "${filtered.size} exercise${if (filtered.size == 1) "" else "s"}",
        )

        // FIXED: 27 — Single filter trigger row with active chips + FILTERS button
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (scope == "favorites") item { ActiveFilterChip("★ Favorites") { scope = "all" } }
                if (scope == "custom") item { ActiveFilterChip("Custom") { scope = "all" } }
                if (cat != null) item { ActiveFilterChip(cat!!) { cat = null } }
                if (muscle != null) item { ActiveFilterChip(muscle!!) { muscle = null } }
                if (equip != null) item { ActiveFilterChip(equip!!) { equip = null } }
                if (movement != null) item { ActiveFilterChip(movement!!) { movement = null } }
                if (difficulty != null) item { ActiveFilterChip(difficulty!!) { difficulty = null } }
                if (bwOnly) item { ActiveFilterChip("BW Only") { bwOnly = false } }
                if (activeProfileUnavailableEquipment.isNotEmpty()) item {
                    Text("🏋 Gym filtered", color = c.warning, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 5.dp))
                }
                if (!hasActiveFilters && activeProfileUnavailableEquipment.isEmpty()) item {
                    Text("All exercises", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(vertical = 5.dp))
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                    .background(if (hasActiveFilters) c.accentSoft else c.surface)
                    .border(1.dp, if (hasActiveFilters) c.accentBorder else c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                    .clickable { showFilterSheet = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.FilterList, null, tint = if (hasActiveFilters) c.accent else c.muted, modifier = Modifier.size(14.dp))
                    Text("FILTERS", color = if (hasActiveFilters) c.accent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight(IronLogType.button.fontWeight))
                }
            }
        }

        // FIXED: 28 — Custom styled search field
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                .background(c.card)
                .border(1.dp, if (searchFocused) c.accent.copy(alpha = 0.6f) else c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = if (searchFocused) c.accent else c.muted, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (search.isEmpty()) {
                    Text("Search exercises…", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                }
                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { searchFocused = it.isFocused },
                    textStyle = TextStyle(color = c.text, fontSize = IronLogType.body.fontSize.sp),
                    singleLine = true,
                )
            }
            if (search.isNotBlank()) {
                Box(Modifier.size(44.dp).clickable { search = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, null, tint = c.muted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            // FIXED: 1
            "${filtered.size} exercise${if (filtered.size == 1) "" else "s"}",
            color = c.muted,
            fontSize = IronLogType.meta.fontSize.sp,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp)
        )

        // ── Exercise list ─────────────────────────────────────────────────────
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // GAP-16: Recently Used chips — shown only when no search/filters are active
            if (recentlyUsed.isNotEmpty() && debouncedSearch.isBlank() && muscle == null && cat == null && equip == null && movement == null && difficulty == null && !bwOnly && scope == "all") {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "RECENTLY USED",
                            color = c.muted,
                            fontSize = IronLogType.eyebrow.fontSize.sp,
                            fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                            letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(recentlyUsed) { name ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                        .background(c.surface)
                                        .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                        .clickable {
                                            search = name
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(name, color = c.text, fontSize = IronLogType.body.fontSize.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No exercises found", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.section.fontSize.sp)
                        Text("Try a different filter or search term.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                        if (missingExerciseSeed.isNotBlank() && !hasExactMatch) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(c.accentSoft)
                                        .border(1.dp, c.accentBorder, RoundedCornerShape(10.dp))
                                        .clickable { onCreateExercise?.invoke(missingExerciseSeed) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) { Text("+ Add Exercise", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp) }
                            }
                        }
                    }
                }
            }

            items(filtered, key = { it.id }) { ex ->
                val hasVideo = videoMap.containsKey(normalizeExerciseNameKey(ex.name))
                ExerciseRow(
                    exercise = ex,
                    isFavorite = favoriteIds.contains(ex.id),
                    onFavorite = {
                        favoriteIds = if (favoriteIds.contains(ex.id)) favoriteIds - ex.id else favoriteIds + ex.id
                        coroutineScope.launch { settingsRepo.saveFavorites(favoriteIds) }
                    },
                    onClick = { onExerciseClick?.invoke(ex) },
                    hasVideo = hasVideo,
                    onVideo = {
                        val link = videoMap[normalizeExerciseNameKey(ex.name)] ?: return@ExerciseRow
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    onDeleteCustom = if (ex.isCustom) ({ confirmDeleteExercise = ex }) else null,
                    // GAP-09: progress icon — uses dedicated callback if provided, else falls through to onExerciseClick
                    onOpenProgress = onOpenExerciseProgress?.let { cb -> { cb(ex.name) } },
                )
            }

            // Footer: "Can't find it?" if searching
            if (missingExerciseSeed.isNotBlank() && !hasExactMatch && filtered.isNotEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Can't find it?", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.accentSoft)
                                .border(1.dp, c.accentBorder, RoundedCornerShape(10.dp))
                                .clickable { onCreateExercise?.invoke(missingExerciseSeed) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text("+ Add Exercise", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp) }
                    }
                }
            }
        }
    }

    // FIXED: 27 — Filter bottom sheet
    if (showFilterSheet) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = c.card,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("FILTERS", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
                // Scope
                Text("Scope", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseFilterChip("All", scope == "all" && !bwOnly, onClick = { scope = "all"; bwOnly = false })
                    ExerciseFilterChip("★ Favorites", scope == "favorites", onClick = { scope = if (scope == "favorites") "all" else "favorites" })
                    ExerciseFilterChip("Custom", scope == "custom", onClick = { scope = if (scope == "custom") "all" else "custom" })
                    ExerciseFilterChip("BW Only", bwOnly, onClick = { bwOnly = !bwOnly })
                }
                // Muscle
                if (muscles.isNotEmpty()) {
                    Text("Muscle Group", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        muscles.forEach { m -> ExerciseFilterChip(m, muscle == m) { muscle = if (muscle == m) null else m } }
                    }
                }
                // Equipment
                if (equipmentOptions.isNotEmpty()) {
                    Text("Equipment", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        equipmentOptions.forEach { e -> ExerciseFilterChip(e, equip == e) { equip = if (equip == e) null else e } }
                    }
                }
                // Category
                if (categories.isNotEmpty()) {
                    Text("Category", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { catOpt -> ExerciseFilterChip(catOpt, cat == catOpt) { cat = if (cat == catOpt) null else catOpt } }
                    }
                }
                // Movement
                if (movementOptions.isNotEmpty()) {
                    Text("Movement Pattern", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        movementOptions.forEach { m -> ExerciseFilterChip(m, movement == m) { movement = if (movement == m) null else m } }
                    }
                }
                // Difficulty
                if (difficultyOptions.isNotEmpty()) {
                    Text("Difficulty", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        difficultyOptions.forEach { d -> ExerciseFilterChip(d.replaceFirstChar { it.titlecase() }, difficulty == d) { difficulty = if (difficulty == d) null else d } }
                    }
                }
                // Clear all
                if (hasActiveFilters) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(IronLogRadius.md.dp))
                            .border(1.dp, c.faint, RoundedCornerShape(IronLogRadius.md.dp))
                            .clickable {
                                scope = "all"; cat = null; muscle = null; equip = null
                                movement = null; difficulty = null; bwOnly = false
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Clear Filters", color = c.muted, fontSize = IronLogType.body.fontSize.sp) }
                }
            }
        }
    }

    // Confirm delete dialog for custom exercises
    confirmDeleteExercise?.let { ex ->
        AlertDialog(
            onDismissRequest = { confirmDeleteExercise = null },
            containerColor = c.card,
            title = { Text("Delete \"${ex.name}\"?", color = c.text) },
            text = { Text("This will permanently remove this custom exercise.", color = c.muted) },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        repoWithCtx.deleteCustomExercise(ex.id)
                        exercises = repoWithCtx.getExercisesSnapshot()
                        confirmDeleteExercise = null
                    }
                }) {
                    Text("DELETE", color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteExercise = null }) { Text("CANCEL", color = c.muted) }
            },
        )
    }
}

// FIXED: 27 — Dismissable active filter chip shown in the trigger row
@Composable
private fun ActiveFilterChip(label: String, onDismiss: () -> Unit) {
    val c = useTheme()
    Row(
        Modifier
            .clip(RoundedCornerShape(IronLogRadius.full.dp))
            .background(c.accentSoft)
            .border(1.dp, c.accentBorder, RoundedCornerShape(IronLogRadius.full.dp))
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.size(32.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Text("×", color = c.accent, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilterRow(borderBottom: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val c = useTheme()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier)
            .padding(vertical = 5.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content) }
    }
}

@Composable
private fun ExerciseFilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = useTheme()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) c.accentSoft else Color.Transparent)
            .border(1.dp, if (active) c.accentBorder else c.faint, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (active) c.accent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ExerciseRow(
    exercise: LegacyExerciseShape,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
    hasVideo: Boolean,
    onVideo: () -> Unit,
    onDeleteCustom: (() -> Unit)? = null,
    onOpenProgress: (() -> Unit)? = null,
) {
    val c = useTheme()
    // FIXED: 29
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .drawBottomBorder(c.faint)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // FIXED: 1, 29
                Text(exercise.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (exercise.isCustom) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.accentSoft)
                            .border(1.dp, c.accentBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) { Text("CUSTOM", color = c.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Black, letterSpacing = IronLogType.micro.letterSpacing.sp) }
                }
            }
            // FIXED: 29 — combined subtitle: muscle · equipment · difficulty
            val subtitle = listOfNotNull(
                exercise.primaryMuscle?.takeIf { it.isNotBlank() },
                exercise.equipment?.takeIf { it.isNotBlank() },
                exercise.difficulty?.trim()?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.titlecase() }
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = c.muted, fontSize = IronLogType.meta.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // FIXED: 29 — 44dp touch target
            IconButton(onClick = onFavorite, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (isFavorite) c.accent else c.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
            // GAP-09: progress trend icon — navigates to ExerciseProgressScreen
            if (onOpenProgress != null) {
                IconButton(onClick = onOpenProgress, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.TrendingUp,
                        contentDescription = "View progress",
                        tint = c.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Custom exercise delete button — 44dp touch target
            if (exercise.isCustom && onDeleteCustom != null) {
                IconButton(onClick = onDeleteCustom, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        // FIXED: 3
                        tint = c.danger,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (hasVideo) {
                Text("▶ VIDEO", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.eyebrow.fontSize.sp,
                    modifier = Modifier.clickable(onClick = onVideo).padding(horizontal = 8.dp, vertical = 10.dp))
            }
        }
    }
}

private fun loadExerciseVideoLinks(context: android.content.Context): Map<String, String> {
    return runCatching {
        val text = context.assets.open("ironlog/exercise_youtube_by_normalized_name.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        buildMap {
            root.keys().forEach { key ->
                val obj = root.optJSONObject(key) ?: return@forEach
                val link = obj.optString("youtubeLink").takeIf { it.isNotBlank() } ?: return@forEach
                put(key, link)
            }
        }
    }.getOrDefault(emptyMap())
}


