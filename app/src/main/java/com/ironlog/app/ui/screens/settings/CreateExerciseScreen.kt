package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.ironlog.app.data.model.CreateExerciseInput
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import kotlinx.coroutines.launch

private val MUSCLE_OPTIONS = listOf(
    "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms",
    "Abs", "Obliques", "Glutes", "Quads", "Hamstrings", "Calves", "Lower Back",
)
private val EQUIPMENT_OPTIONS = listOf(
    "Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Band", "Kettlebell", "Other",
)

private data class TrackingOption(val value: String, val label: String)
private val TRACKING_OPTIONS = listOf(
    TrackingOption("weight_reps",  "Weight × Reps"),
    TrackingOption("bodyweight",   "Bodyweight"),
    TrackingOption("duration",     "Duration"),
)
private val MOVEMENT_PATTERN_OPTIONS = listOf(
    "Push", "Pull", "Hinge", "Squat", "Carry", "Rotation", "Isolation",
)
private val DIFFICULTY_OPTIONS = listOf("beginner", "intermediate", "advanced", "expert")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateExerciseScreen(
    initialName: String = "",
    repo: ExerciseRepository = ExerciseRepository(),
    onSaved: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val c = useTheme()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var primaryMuscle by remember { mutableStateOf(MUSCLE_OPTIONS.first()) }
    var equipment by remember { mutableStateOf(EQUIPMENT_OPTIONS.first()) }
    var trackingType by remember { mutableStateOf(TRACKING_OPTIONS.first().value) }
    var movementPattern by remember { mutableStateOf<String?>(null) }
    var difficulty by remember { mutableStateOf<String?>(null) }
    var secondaryMuscles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var allExercises by remember { mutableStateOf<List<LegacyExerciseShape>>(emptyList()) }
    var showCopyFrom by remember { mutableStateOf(false) }
    var copySearch by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { allExercises = repo.getExercisesSnapshot() }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = c.accent)
                }
            }
            Text(
                "Create Exercise",
                color = c.text,
                fontWeight = FontWeight.Bold,
                fontSize = IronLogType.section.fontSize.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showCopyFrom = true; copySearch = "" }) {
                Text("Copy from...", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Copy from exercise picker
        if (showCopyFrom) {
            ModalBottomSheet(
                onDismissRequest = { showCopyFrom = false },
                containerColor = c.card,
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Copy from Exercise", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.section.fontSize.sp)
                    OutlinedTextField(
                        value = copySearch,
                        onValueChange = { copySearch = it },
                        placeholder = { Text("Search...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    val copyFiltered = remember(allExercises, copySearch) {
                        if (copySearch.isBlank()) allExercises.take(40)
                        else allExercises.filter { it.name.contains(copySearch, ignoreCase = true) }.take(40)
                    }
                    LazyColumn(Modifier.height(320.dp)) {
                        items(copyFiltered, key = { it.id }) { ex ->
                            Column {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            name = ex.name
                                            val matchedMuscle = MUSCLE_OPTIONS.firstOrNull { m -> m.equals(ex.primaryMuscle, ignoreCase = true) }
                                            if (matchedMuscle != null) primaryMuscle = matchedMuscle
                                            val matchedEquip = EQUIPMENT_OPTIONS.firstOrNull { e -> e.equals(ex.equipment, ignoreCase = true) }
                                            if (matchedEquip != null) equipment = matchedEquip
                                            val matchedTrack = TRACKING_OPTIONS.firstOrNull { t -> t.value == ex.trackingType }
                                            if (matchedTrack != null) trackingType = matchedTrack.value
                                            val matchedMove = MOVEMENT_PATTERN_OPTIONS.firstOrNull { p -> p.equals(ex.movementPattern, ignoreCase = true) }
                                            movementPattern = matchedMove
                                            val matchedDiff = DIFFICULTY_OPTIONS.firstOrNull { d -> d.equals(ex.difficulty, ignoreCase = true) }
                                            difficulty = matchedDiff
                                            showCopyFrom = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ex.name, color = c.text, fontSize = IronLogType.body.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (ex.primaryMuscle != null) {
                                            Text("${ex.primaryMuscle} · ${ex.equipment}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                HorizontalDivider(color = c.faint, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        // ── Scrollable body ──────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Exercise name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Primary muscle
            SelectorSection(label = "Primary Muscle") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MUSCLE_OPTIONS.forEach { m ->
                        SelectChip(
                            label = m,
                            selected = primaryMuscle == m,
                            onClick = { primaryMuscle = m },
                        )
                    }
                }
            }

            // Secondary muscles (GAP-05)
            SelectorSection(label = "Secondary Muscles (optional)") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MUSCLE_OPTIONS.forEach { m ->
                        // Can't select same as primary; toggle off primary if selected as secondary
                        val isDisabled = m == primaryMuscle
                        SelectChip(
                            label = m,
                            selected = m in secondaryMuscles && !isDisabled,
                            onClick = {
                                if (!isDisabled) {
                                    secondaryMuscles = if (m in secondaryMuscles) secondaryMuscles - m else secondaryMuscles + m
                                }
                            },
                        )
                    }
                }
            }

            // Equipment
            SelectorSection(label = "Equipment") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EQUIPMENT_OPTIONS.forEach { e ->
                        SelectChip(
                            label = e,
                            selected = equipment == e,
                            onClick = { equipment = e },
                        )
                    }
                }
            }

            // Tracking type
            SelectorSection(label = "Tracking Type") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TRACKING_OPTIONS.forEach { opt ->
                        SelectChip(
                            label = opt.label,
                            selected = trackingType == opt.value,
                            onClick = { trackingType = opt.value },
                        )
                    }
                }
            }

            // Movement Pattern
            SelectorSection(label = "Movement Pattern (optional)") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MOVEMENT_PATTERN_OPTIONS.forEach { pattern ->
                        SelectChip(
                            label = pattern,
                            selected = movementPattern == pattern,
                            onClick = { movementPattern = if (movementPattern == pattern) null else pattern },
                        )
                    }
                }
            }

            // Difficulty
            SelectorSection(label = "Difficulty (optional)") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DIFFICULTY_OPTIONS.forEach { diff ->
                        SelectChip(
                            label = diff.replaceFirstChar { it.titlecase() },
                            selected = difficulty == diff,
                            onClick = { difficulty = if (difficulty == diff) null else diff },
                        )
                    }
                }
            }

            // Error
            error?.let {
                Text(it, color = c.danger, fontSize = IronLogType.body.fontSize.sp)
            }

            // Save button
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            repo.createCustomExercise(
                                CreateExerciseInput(
                                    name = name.trim(),
                                    primaryMuscle = primaryMuscle,
                                    equipment = equipment,
                                    category = "strength",
                                    trackingType = trackingType,
                                    movementPattern = movementPattern,
                                    difficulty = difficulty,
                                    secondaryMuscles = secondaryMuscles.toList().takeIf { it.isNotEmpty() },
                                )
                            )
                        }.onSuccess {
                            onSaved()
                        }.onFailure {
                            error = it.message ?: "Could not create exercise"
                        }
                    }
                },
                enabled = name.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("SAVE EXERCISE", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SelectorSection(label: String, content: @Composable () -> Unit) {
    val c = useTheme()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        content()
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = useTheme()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) c.accentSoft else c.surface)
            .border(1.dp, if (selected) c.accentBorder else c.faint, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = if (selected) c.accent else c.subtext,
            fontSize = IronLogType.body.fontSize.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}


