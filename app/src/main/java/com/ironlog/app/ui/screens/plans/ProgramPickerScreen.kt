package com.ironlog.app.ui.screens.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.repository.PlanRepository
import com.ironlog.app.data.seed.PROGRAM_TEMPLATES
import com.ironlog.app.data.seed.ProgramTemplate
import com.ironlog.app.data.seed.toPlanObject
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.theme.IronLogRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProgramPickerScreen(
    planRepo: PlanRepository = PlanRepository(),
    onBack: () -> Unit = {},
) {
    val c = useTheme()
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<ProgramTemplate?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember {
        listOf("All") + PROGRAM_TEMPLATES.map { it.category }.distinct()
    }
    val filtered = remember(category, searchQuery) {
        val byCat = if (category == "All") PROGRAM_TEMPLATES else PROGRAM_TEMPLATES.filter { it.category == category }
        if (searchQuery.isBlank()) byCat
        else {
            val q = searchQuery.trim().lowercase()
            byCat.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.category.lowercase().contains(q) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item { ScreenHeader(title = "PROGRAMS", onBack = onBack) }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "Programs",
                    color      = c.text,
                    fontSize = IronLogType.title.fontSize.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose a built-in program by category, or build your own from scratch.",
                    color    = c.subtext,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
        }

        // ── Horizontally scrollable category filter chips ─────────────────────
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(categories) { cat ->
                    val active = category == cat
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) c.accent.copy(alpha = 0.18f) else c.surface)
                            .border(1.5.dp, if (active) c.accent else c.cardBorder, CircleShape)
                            .clickable { category = cat }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            cat.uppercase(),
                            color      = if (active) c.accent else c.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }
            }
        }

        // ── Search bar ────────────────────────────────────────────────────────
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search programs…", color = c.muted, fontSize = IronLogType.body.fontSize.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = c.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
            )
        }

        // ── Program cards ──────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No programs match \"${searchQuery.trim()}\"",
                        color = c.muted,
                        fontSize = IronLogType.body.fontSize.sp,
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { p ->
                ProgramCard(
                    template = p,
                    onClick  = { selected = p },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Status message ────────────────────────────────────────────────────
        status?.let { msg ->
            item {
                Text(
                    msg,
                    color    = if (msg.startsWith("Failed")) c.danger else c.success,
                    fontSize = IronLogType.body.fontSize.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    // ── Program detail bottom sheet ───────────────────────────────────────────
    selected?.let { tpl ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor   = c.card,
            contentColor     = c.text,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Badge + name
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(c.accentSoft)
                        .border(1.dp, c.accentBorder, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("${tpl.days.size}x / week", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                }
                Text(tpl.name,        color = c.text,    fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight.Black)
                Text(tpl.description, color = c.subtext, fontSize = IronLogType.body.fontSize.sp)

                HorizontalDivider(color = c.cardBorder)

                // Day list
                tpl.days.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(d.name ?: "",                     color = c.text,   fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.SemiBold)
                        Text("${d.exercises.size} exercises",  color = c.muted,  fontSize = IronLogType.meta.fontSize.sp)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { planRepo.importFullPlan(tpl.toPlanObject()) }
                            }
                            result
                                .onSuccess { status = "Added '${tpl.name}' to your plans."; selected = null }
                                .onFailure { status = "Failed: ${it.message ?: "unknown error"}" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = c.accent),
                ) {
                    Text(
                        "ADD TO MY PLANS",
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        fontSize = IronLogType.body.fontSize.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(
    template: ProgramTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = useTheme()
    Card(
        colors   = CardDefaults.cardColors(containerColor = c.card),
        border   = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
        shape    = RoundedCornerShape(IronLogRadius.xl.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Badge row: frequency + difficulty + duration
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                @Composable
                fun Badge(label: String) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(IronLogRadius.full.dp))
                            .background(c.accentSoft)
                            .border(1.dp, c.accentBorder, RoundedCornerShape(IronLogRadius.full.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(label, color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Badge("${template.days.size}x/week")
                Badge(template.difficulty)
                Badge("${template.durationWeeks}w")
            }
            Text(template.name,        color = c.text,    fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(template.description, color = c.subtext, fontSize = IronLogType.body.fontSize.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

