package com.ironlog.app.ui.screens.plans

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.repository.PlanRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.seed.PROGRAM_TEMPLATES
import com.ironlog.app.data.seed.ProgramTemplate
import com.ironlog.app.data.seed.toPlanObject
import com.ironlog.app.domain.intelligence.ProgramRecommendation
import com.ironlog.app.domain.intelligence.ProgramRecommendationEngine
import com.ironlog.app.domain.intelligence.ProgramRecommendationProfile
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.theme.IronLogRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProgramPickerScreen(
    planRepo: PlanRepository = PlanRepository(),
    onBack: () -> Unit = {},
) {
    val c = useTheme()
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository() }
    var category by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<ProgramRecommendation?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(ProgramRecommendationProfile()) }

    LaunchedEffect(Unit) {
        val raw = settingsRepo.getString("ironlog_settings").orEmpty()
        val settings = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        profile = ProgramRecommendationProfile(
            goalMode = settings.optString("goalMode", "hypertrophy"),
            weeklyDays = settings.optInt("weeklyGoalDays", 3).coerceIn(1, 7),
            trainingAgeMonths = settingsRepo.getString("baseline_training_age_months")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            hasPastTraining = settingsRepo.getBoolean("baseline_has_past_training", false),
            hasGymAccess = settingsRepo.getBoolean("baseline_has_gym_access", true),
        )
    }

    val categories = remember {
        listOf("All") + PROGRAM_TEMPLATES.map { it.category }.distinct()
    }
    val ranked = remember(profile) { ProgramRecommendationEngine.rank(PROGRAM_TEMPLATES, profile) }
    val filtered = remember(category, searchQuery, ranked) {
        val byCat = if (category == "All") ranked else ranked.filter { it.template.category == category }
        if (searchQuery.isBlank()) byCat
        else {
            val q = searchQuery.trim().lowercase()
            byCat.filter { recommendation ->
                val template = recommendation.template
                template.name.lowercase().contains(q) || template.description.lowercase().contains(q) || template.category.lowercase().contains(q)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item { ScreenHeader(title = "PROGRAMS", onBack = onBack) }
        item {
            Column(Modifier.appPadding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "Ranked locally for your ${profile.weeklyDays}-day week, ${profile.goalMode.replace('_', ' ')} goal and equipment access.",
                    color    = c.subtext,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
        }

        // ── Horizontally scrollable category filter chips ─────────────────────
        item {
            LazyRow(
                horizontalArrangement = appSpacedBy(8.dp),
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
                Box(Modifier.fillMaxWidth().appPadding(horizontal = 16.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No programs match \"${searchQuery.trim()}\"",
                        color = c.muted,
                        fontSize = IronLogType.body.fontSize.sp,
                    )
                }
            }
        } else {
            items(filtered, key = { it.template.id }) { recommendation ->
                ProgramCard(
                    recommendation = recommendation,
                    isBestFit = recommendation.template.id == ranked.firstOrNull()?.template?.id,
                    onClick  = { selected = recommendation },
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
                    modifier = Modifier.appPadding(horizontal = 16.dp),
                )
            }
        }
    }

    // ── Program detail bottom sheet ───────────────────────────────────────────
    selected?.let { recommendation ->
        val tpl = recommendation.template
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor   = c.card,
            contentColor     = c.text,
        ) {
            ProgramDetailsContent(
                recommendation = recommendation,
                onAdd = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { planRepo.importFullPlan(tpl.toPlanObject()) }
                        }
                        result
                            .onSuccess { status = "Added '${tpl.name}' to your plans."; selected = null }
                            .onFailure { status = "Failed: ${it.message ?: "unknown error"}" }
                    }
                },
            )
        }
    }
}

@Composable
internal fun ProgramDetailsContent(
    recommendation: ProgramRecommendation,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = useTheme()
    val template = recommendation.template
    Column(
        modifier.fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .appPadding(horizontal = 20.dp)
            .appPadding(bottom = 32.dp),
        verticalArrangement = appSpacedBy(12.dp),
    ) {
        Box(
            Modifier.clip(CircleShape)
                .background(c.accentSoft)
                .border(1.dp, c.accentBorder, CircleShape)
                .appPadding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text("${template.days.size}x / week", color = c.accent,
                fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
        }
        Text(template.name, color = c.text, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight.Black)
        Text(template.description, color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
        Text(recommendation.reason, color = c.text,
            fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = c.cardBorder)
        template.days.forEach { day ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = appSpacedBy(12.dp)) {
                Text(day.name.orEmpty(), modifier = Modifier.weight(0.62f), color = c.text,
                    fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.SemiBold)
                Text("${day.exercises.size} exercises", modifier = Modifier.weight(0.38f),
                    textAlign = TextAlign.End, color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
            }
        }
        Spacer(Modifier.height(appGapDp(4.dp)))
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent),
        ) {
            Text("ADD TO MY PLANS", fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp, fontSize = IronLogType.body.fontSize.sp)
        }
    }
}

@Composable
private fun ProgramCard(
    recommendation: ProgramRecommendation,
    isBestFit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = useTheme()
    val template = recommendation.template
    Card(
        colors   = CardDefaults.cardColors(containerColor = c.card),
        border   = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
        shape    = RoundedCornerShape(IronLogRadius.lg.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = appSpacedBy(8.dp),
        ) {
            if (isBestFit) {
                Text("BEST FIT", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            // Badge row: frequency + difficulty + duration
            Row(horizontalArrangement = appSpacedBy(6.dp)) {
                @Composable
                fun Badge(label: String) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(IronLogRadius.full.dp))
                            .background(c.accentSoft)
                            .border(1.dp, c.accentBorder, RoundedCornerShape(IronLogRadius.full.dp))
                            .appPadding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(label, color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Badge("${template.days.size}x/week")
                Badge(recommendation.experienceLabel)
                Badge(recommendation.sessionLengthLabel)
            }
            Text(template.name,        color = c.text,    fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(template.description, color = c.subtext, fontSize = IronLogType.body.fontSize.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(recommendation.reason, color = c.text, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

