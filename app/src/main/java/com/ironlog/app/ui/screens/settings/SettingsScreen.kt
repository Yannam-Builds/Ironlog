package com.ironlog.app.ui.screens.settings

import android.content.Intent
import androidx.core.content.FileProvider
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.ui.context.ThemeRuntime
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.IronLogSettings
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.services.ReminderScheduler
import com.ironlog.app.services.WorkoutNotificationBridge
import com.ironlog.app.util.HapticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.ImportExportRepository
import com.ironlog.app.domain.intelligence.GeminiNanoEngine
import com.ironlog.app.domain.intelligence.NanoAvailability
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.ironlog.app.domain.intelligence.CloudAiEngine
import com.ironlog.app.domain.intelligence.CloudAiKeyStore

private val EFFORT_CYCLE = listOf("off", "rpe", "rir", "both")
private val EFFORT_LABEL = mapOf("off" to "Off", "rpe" to "RPE", "rir" to "RIR", "both" to "Both")
private val GOAL_MODES = listOf("hypertrophy" to "Hypertrophy", "strength" to "Strength", "general_fitness" to "General Fitness")
private val PROGRESSION_STYLES = listOf("conservative" to "Conservative", "balanced" to "Balanced", "aggressive" to "Aggressive")
private val PERFORMANCE_MODES = listOf(
    "auto" to "Auto",
    "battery" to "Battery",
    "balanced" to "Balanced",
    "max" to "Max",
)

// ── Cloud AI provider presets ─────────────────────────────────────────────────

private data class ProviderPreset(
    val key: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String,
    val apiFormat: String,
    val knownModels: List<String> = emptyList(),
)

private val PROVIDER_PRESETS = listOf(
    ProviderPreset(
        key = "openai", displayName = "OpenAI", baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini", keyUrl = "https://platform.openai.com/api-keys", apiFormat = "openai",
        knownModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo", "o1", "o1-mini", "o3-mini"),
    ),
    ProviderPreset(
        key = "claude", displayName = "Claude", baseUrl = "https://api.anthropic.com",
        defaultModel = "claude-3-5-haiku-20241022", keyUrl = "https://platform.claude.com/settings/keys", apiFormat = "anthropic",
        knownModels = listOf("claude-opus-4-5", "claude-sonnet-4-5", "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229"),
    ),
    ProviderPreset(
        key = "gemini", displayName = "Gemini", baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-2.0-flash-lite", keyUrl = "https://aistudio.google.com/app/apikey", apiFormat = "openai",
        knownModels = listOf("gemini-2.0-flash-lite", "gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-1.5-pro"),
    ),
    ProviderPreset(
        key = "deepseek", displayName = "DeepSeek", baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat", keyUrl = "https://platform.deepseek.com/api_keys", apiFormat = "openai",
        knownModels = listOf("deepseek-chat", "deepseek-reasoner"),
    ),
    ProviderPreset(
        key = "kimi", displayName = "Kimi", baseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "moonshot-v1-8k", keyUrl = "https://platform.kimi.com/console/api-keys", apiFormat = "openai",
        knownModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
    ),
    ProviderPreset(
        key = "openrouter", displayName = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openai/gpt-4o-mini", keyUrl = "https://openrouter.ai/settings/keys", apiFormat = "openai",
        knownModels = listOf(
            "openai/gpt-4o", "openai/gpt-4o-mini", "openai/o3-mini",
            "anthropic/claude-3-5-sonnet", "anthropic/claude-3-5-haiku",
            "google/gemini-2.0-flash", "google/gemini-2.5-pro-preview",
            "deepseek/deepseek-chat", "deepseek/deepseek-r1",
            "meta-llama/llama-3.1-8b-instruct:free", "mistralai/mistral-7b-instruct:free",
        ),
    ),
    ProviderPreset(
        key = "custom", displayName = "Custom", baseUrl = "", defaultModel = "", keyUrl = "", apiFormat = "openai",
    ),
)

// FIXED: 30 — Theme preview tokens: static bg + accent per theme for visual preview cards
private data class ThemeToken(val id: String, val name: String, val bg: Color, val accent: Color)
private val THEME_TOKENS = listOf(
    ThemeToken("obsidian_silver",  "Obsidian Silver",  Color(0xFF131313), Color(0xFFFDFDFC)),
    ThemeToken("deep_forest",      "Deep Forest",      Color(0xFF121412), Color(0xFFB0CFAD)),
    ThemeToken("titanium_blue",    "Titanium Blue",    Color(0xFF11131C), Color(0xFFB8C3FF)),
    ThemeToken("monet",            "Monet (Dynamic)",  Color(0xFF1A1A1A), Color(0xFFB8C3FF)),
    ThemeToken("royal_amethyst",   "Royal Amethyst",   Color(0xFF0B1326), Color(0xFFD2BBFF)),
    ThemeToken("midnight_teal",    "Midnight Teal",    Color(0xFF0B1326), Color(0xFF6BD8CB)),
    ThemeToken("crimson_steel",    "Crimson Steel",    Color(0xFF141313), Color(0xFFFFB4AB)),
    ThemeToken("burnt_terracotta", "Burnt Terracotta", Color(0xFF161311), Color(0xFFFFB77D)),
    ThemeToken("electric_lemon",   "Electric Lemon",   Color(0xFF17130A), Color(0xFFFFD165)),
    ThemeToken("dark",             "Dark",             Color(0xFF121212), Color(0xFFFF4500)),
    ThemeToken("amoled",           "AMOLED",           Color(0xFF000000), Color(0xFFFF4500)),
    ThemeToken("light",            "Light",            Color(0xFFF2F2F7), Color(0xFFD42010)),
)

@Composable
fun SettingsScreen(
    vm: AppDataViewModel = viewModel(),
    onOpenAIPlan: () -> Unit = {},
    onOpenDataPortability: () -> Unit = {},
    onOpenProgramPicker: () -> Unit = {},
    onOpenTrainingIntelligence: () -> Unit = {},
    onOpenExerciseLibrary: () -> Unit = {},
    onOpenGymProfiles: () -> Unit = {},
    onOpenBodyWeight: () -> Unit = {},
    onOpenImportCenter: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    val c = useTheme()
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val settings = state.settings
    val settingsRepo = remember { com.ironlog.app.data.repository.SettingsRepository() }
    val workoutRepo = remember { WorkoutRepository() }
    val importExportRepo = remember { ImportExportRepository() }
    val scope = rememberCoroutineScope()
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }

    var confirmResetOnboarding by remember { mutableStateOf(false) }
    var notifProfileState by remember { mutableStateOf("balanced") }

    // FIXED: 34 — Clear All History confirmation state (moved from HistoryScreen)
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmResetPbs by remember { mutableStateOf(false) }
    var showNormalRestDialog by remember { mutableStateOf(false) }
    var showHeavyRestDialog by remember { mutableStateOf(false) }
    var showBarWeightDialog by remember { mutableStateOf(false) }
    var csvImportText by remember { mutableStateOf("") }
    var csvImportPreview by remember { mutableStateOf<CsvPreview?>(null) }
    var showCsvImportConfirm by remember { mutableStateOf(false) }
    var csvImportStatus by remember { mutableStateOf("") }
    var normalRestInput by remember { mutableStateOf("") }
    var heavyRestInput by remember { mutableStateOf("") }
    var barWeightInput by remember { mutableStateOf("") }
    val notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()

    var notificationsEnabled by remember { mutableStateOf(true) }
    var workoutReminders by remember { mutableStateOf(true) }
    var milestoneAlertsEnabled by remember { mutableStateOf(true) }
    var quietStart by remember { mutableStateOf("22:00") }
    var quietEnd by remember { mutableStateOf("08:00") }
    var dailyReminderInput by remember { mutableStateOf("") }
    var reminderStatus by remember { mutableStateOf("") }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var keepAwakeDuringWorkout by remember { mutableStateOf(true) }
    // Intelligence Engine section
    var nanoAvailability by remember { mutableStateOf<NanoAvailability?>(null) }
    var nanoDownloading by remember { mutableStateOf(false) }
    var nanoDownloadResult by remember { mutableStateOf("") }
    // Cloud AI config form state
    var cloudFormExpanded by remember { mutableStateOf(false) }
    var cloudPreset by remember { mutableStateOf(settings.cloudAiProviderPreset.ifBlank { "openai" }) }
    var cloudBaseUrl by remember { mutableStateOf(settings.cloudAiBaseUrl) }
    var cloudApiKey by remember { mutableStateOf("") }
    var cloudApiKeyVisible by remember { mutableStateOf(false) }
    var cloudModelName by remember { mutableStateOf(settings.cloudAiModelName) }
    var cloudDisplayName by remember { mutableStateOf(settings.cloudAiDisplayName) }
    var cloudModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var cloudModelsLoading by remember { mutableStateOf(false) }
    var cloudModelsError by remember { mutableStateOf("") }
    var cloudShowModelSheet by remember { mutableStateOf(false) }
    var cloudModelSearch by remember { mutableStateOf("") }
    var cloudVerifyResult by remember { mutableStateOf("") }
    var cloudVerifying by remember { mutableStateOf(false) }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            }.getOrElse { e ->
                withContext(Dispatchers.Main) { csvImportStatus = "Could not read CSV: ${e.message}" }
                return@launch
            }
            val preview = buildImportPreview(text, "strong_csv")
            withContext(Dispatchers.Main) {
                csvImportText = text
                csvImportPreview = preview
                csvImportStatus = if (preview.error != null) "CSV preview failed: ${preview.error}" else ""
                if (preview.error == null && preview.validRows > 0) showCsvImportConfirm = true
            }
        }
    }

    LaunchedEffect(Unit) {
        notificationsEnabled = settingsRepo.getBoolean("notifications_enabled", true)
        milestoneAlertsEnabled = settingsRepo.getBoolean("milestone_alerts_enabled", true)
        workoutReminders = settingsRepo.getBoolean("training_reminders_enabled", true)
        val start = settingsRepo.getSettingNumber("quietHoursStartMinutes")?.toInt() ?: (22 * 60)
        val end = settingsRepo.getSettingNumber("quietHoursEndMinutes")?.toInt() ?: (8 * 60)
        quietStart = formatQuietTime(start)
        quietEnd = formatQuietTime(end)
        val reminderMin = settingsRepo.getSettingNumber("dailyReminderTimeMinutes")?.toInt()
        dailyReminderInput = if (reminderMin != null) formatQuietTime(reminderMin) else ""
        keepAwakeDuringWorkout = settingsRepo.getBoolean("keep_screen_awake_active_workout", true)
        normalRestInput = settings.defaultRestSeconds.toString()
        heavyRestInput = settings.defaultRestHeavySeconds.toString()
        barWeightInput = settings.barWeightKg.toString()
        notifProfileState = settingsRepo.getString("notification_profile") ?: "balanced"
        nanoAvailability = GeminiNanoEngine.checkAvailability(context)
        cloudApiKey = withContext(Dispatchers.IO) { CloudAiKeyStore.load(context, cloudPreset) }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                // FIXED: 1
                Text("Settings", color = c.text, fontWeight = FontWeight(IronLogType.metric.fontWeight), fontSize = IronLogType.metric.fontSize.sp)
                Text("Refine the app to match your training rhythm.", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
            }
        }

        // ── 1. YOUR PROFILE ────────────────────────────────────────────────
        item {
            SettingsSection("YOUR PROFILE") {
                // Display name — saves automatically on Done / focus lost
                val focusManager = LocalFocusManager.current
                var nameInput by remember(settings.userName) { mutableStateOf(settings.userName) }
                val commitName = {
                    val trimmed = nameInput.trim()
                    if (trimmed != settings.userName) {
                        vm.updateSettingsAsync(settings.copy(userName = trimmed))
                    }
                    focusManager.clearFocus()
                }
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Your name") },
                    placeholder = { Text("Athlete") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .onFocusChanged { fs -> if (!fs.isFocused) commitName() },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitName() }),
                )
                // Workout days stepper
                SectionRow(borderBottom = true) {
                    Text("Workout days / week", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepperBtn("-") { vm.updateSettingsAsync(settings.copy(weeklyGoalDays = max(1, (settings.weeklyGoalDays) - 1))) }
                        Text("${settings.weeklyGoalDays.coerceIn(1, 7)}", color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 32.dp).wrapContentWidth())
                        StepperBtn("+") { vm.updateSettingsAsync(settings.copy(weeklyGoalDays = min(7, (settings.weeklyGoalDays) + 1))) }
                    }
                }
                // Training focus
                Text("Training focus", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                GoalChipRow(GOAL_MODES, settings.goalMode, hapticsEnabled = settings.hapticFeedback) { vm.updateSettingsAsync(settings.copy(goalMode = it)) }
                // Progression style
                Text("Progression style", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                GoalChipRow(PROGRESSION_STYLES, settings.progressionStyle, hapticsEnabled = settings.hapticFeedback) { vm.updateSettingsAsync(settings.copy(progressionStyle = it)) }
                // Athlete Profile nav row
                SectionNavRow("Athlete Profile", onPress = onOpenTrainingIntelligence, borderBottom = false)
            }
        }

        // ── 1b. INTELLIGENCE ENGINE ────────────────────────────────────────
        item {
            SettingsSection("INTELLIGENCE ENGINE") {
                Text(
                    "Choose what powers the intelligence card on your Home screen.",
                    color = c.subtext,
                    fontSize = IronLogType.meta.fontSize.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // ── Built-in card ──────────────────────────────────────
                    val builtinSelected = settings.intelligenceMode == "builtin"
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                            .background(if (builtinSelected) c.accent.copy(alpha = 0.10f) else c.surface)
                            .border(1.5.dp, if (builtinSelected) c.accent else c.faint, RoundedCornerShape(IronLogRadius.lg.dp))
                            .clickable {
                                vm.updateSettingsAsync(settings.copy(intelligenceMode = "builtin"))
                                cloudFormExpanded = false
                            }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("⚡", fontSize = 22.sp)
                        Text("Built-in", color = if (builtinSelected) c.accent else c.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
                        Text("Deterministic rule-based engine. Always works, no key required.", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, lineHeight = IronLogType.meta.lineHeight.sp)
                    }

                    // ── APEX ENGINE card ───────────────────────────────────
                    val apexSelected = settings.intelligenceMode == "gemini_nano"
                    val apexEnabled = nanoAvailability == NanoAvailability.SUPPORTED
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                            .background(when { apexSelected -> c.accent.copy(alpha = 0.10f); !apexEnabled -> c.surface.copy(alpha = 0.5f); else -> c.surface })
                            .border(1.5.dp, when { apexSelected -> c.accent; !apexEnabled -> c.faint.copy(alpha = 0.5f); else -> c.faint }, RoundedCornerShape(IronLogRadius.lg.dp))
                            .clickable(enabled = apexEnabled) {
                                vm.updateSettingsAsync(settings.copy(intelligenceMode = "gemini_nano"))
                                cloudFormExpanded = false
                            }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("✦", fontSize = 22.sp)
                        Text(
                            "APEX ENGINE",
                            color = when { apexSelected -> c.accent; !apexEnabled -> c.muted.copy(alpha = 0.5f); else -> c.text },
                            fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (nanoAvailability) {
                                NanoAvailability.SUPPORTED           -> "Gemini Nano AI, runs fully on-device."
                                NanoAvailability.NEEDS_DOWNLOAD      -> "Gemini Nano AI — model download required."
                                NanoAvailability.NEEDS_SYSTEM_UPDATE -> "Awaiting Google Play system update to deploy Gemini Nano."
                                NanoAvailability.UNSUPPORTED         -> "Not supported on this device."
                                null                                 -> "Checking device support…"
                            },
                            color = if (apexEnabled) c.muted else c.muted.copy(alpha = 0.6f),
                            fontSize = IronLogType.meta.fontSize.sp, lineHeight = IronLogType.meta.lineHeight.sp,
                        )
                        if (nanoAvailability == NanoAvailability.NEEDS_DOWNLOAD) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .clickable(enabled = !nanoDownloading) {
                                        scope.launch {
                                            nanoDownloading = true
                                            nanoDownloadResult = ""
                                            GeminiNanoEngine.downloadModel(context)
                                                .onSuccess {
                                                    nanoAvailability = NanoAvailability.SUPPORTED
                                                    nanoDownloadResult = "Download complete."
                                                    vm.updateSettingsAsync(settings.copy(intelligenceMode = "gemini_nano"))
                                                }
                                                .onFailure { nanoDownloadResult = "Download failed: ${it.message}" }
                                            nanoDownloading = false
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(if (nanoDownloading) "Downloading…" else "Download Model", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (nanoDownloadResult.isNotBlank()) {
                                Text(nanoDownloadResult, color = if (nanoDownloadResult.startsWith("Download complete")) c.success else c.danger, fontSize = IronLogType.meta.fontSize.sp)
                            }
                        }
                        if (nanoAvailability == NanoAvailability.NEEDS_SYSTEM_UPDATE) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .clickable {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text("Check for Updates →", color = c.accent.copy(alpha = 0.8f), fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // ── Cloud AI card ──────────────────────────────────────
                    val cloudSelected = settings.intelligenceMode == "cloud_ai"
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                            .background(if (cloudSelected) c.accent.copy(alpha = 0.10f) else c.surface)
                            .border(1.5.dp, if (cloudSelected) c.accent else c.faint, RoundedCornerShape(IronLogRadius.lg.dp))
                            .clickable { cloudFormExpanded = !cloudFormExpanded }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("☁", fontSize = 22.sp)
                        Text("Cloud AI", color = if (cloudSelected) c.accent else c.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (cloudSelected && settings.cloudAiDisplayName.isNotBlank())
                                "Active: ${settings.cloudAiDisplayName} · ${settings.cloudAiModelName}"
                            else
                                "Use your own API key for any OpenAI-compatible provider or Claude.",
                            color = c.muted, fontSize = IronLogType.meta.fontSize.sp, lineHeight = IronLogType.meta.lineHeight.sp,
                        )
                    }
                }

                // ── Cloud AI config form ───────────────────────────────────
                AnimatedVisibility(visible = cloudFormExpanded) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        // Provider preset chips
                        Text("Provider", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PROVIDER_PRESETS.forEach { preset ->
                                val sel = cloudPreset == preset.key
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                        .background(if (sel) c.accent.copy(alpha = 0.18f) else c.surface)
                                        .border(1.dp, if (sel) c.accent else c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                        .clickable {
                                            cloudPreset = preset.key
                                            if (preset.key != "custom") {
                                                cloudBaseUrl = preset.baseUrl
                                                cloudModelName = preset.defaultModel
                                                cloudDisplayName = preset.displayName
                                            }
                                            // Load this provider's saved key (empty string if not yet set)
                                            cloudApiKey = CloudAiKeyStore.load(context, preset.key)
                                            cloudModels = emptyList()
                                            cloudModelsError = ""
                                            cloudVerifyResult = ""
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(preset.displayName, color = if (sel) c.accent else c.muted, fontSize = IronLogType.micro.fontSize.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }

                        // Base URL
                        OutlinedTextField(
                            value = cloudBaseUrl,
                            onValueChange = { cloudBaseUrl = it },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        )

                        // "Get API key →" link (shown for non-custom presets)
                        val activePreset = PROVIDER_PRESETS.find { it.key == cloudPreset }
                        if (activePreset != null && activePreset.keyUrl.isNotBlank()) {
                            Text(
                                "Get API key →",
                                color = c.accent,
                                fontSize = IronLogType.meta.fontSize.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activePreset.keyUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                    }
                                    .padding(vertical = 2.dp),
                            )
                        }

                        // API Key (masked)
                        OutlinedTextField(
                            value = cloudApiKey,
                            onValueChange = { cloudApiKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-…") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (cloudApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Icon(
                                    if (cloudApiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (cloudApiKeyVisible) "Hide key" else "Show key",
                                    tint = c.muted,
                                    modifier = Modifier.clickable { cloudApiKeyVisible = !cloudApiKeyVisible },
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        )

                        // Load Models / Browse preset models row
                        val activePresetForModels = PROVIDER_PRESETS.find { it.key == cloudPreset }
                        val hasKnownModels = (activePresetForModels?.knownModels?.isNotEmpty()) == true
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            if (hasKnownModels) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                        .background(c.surface)
                                        .border(1.dp, c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                        .clickable {
                                            cloudModels = activePresetForModels!!.knownModels
                                            cloudModelsError = ""
                                            cloudShowModelSheet = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "Browse ${activePresetForModels!!.knownModels.size} Models",
                                        color = c.muted,
                                        fontSize = IronLogType.meta.fontSize.sp,
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.surface)
                                    .border(1.dp, c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                    .clickable(enabled = !cloudModelsLoading && cloudApiKey.isNotBlank() && cloudBaseUrl.isNotBlank()) {
                                        scope.launch {
                                            cloudModelsLoading = true
                                            cloudModelsError = ""
                                            val fmt = PROVIDER_PRESETS.find { it.key == cloudPreset }?.apiFormat ?: "openai"
                                            CloudAiEngine.fetchModels(cloudBaseUrl, cloudApiKey, fmt)
                                                .onSuccess { models ->
                                                    cloudModels = models.sortedBy { it.lowercase() }
                                                    cloudShowModelSheet = true
                                                }
                                                .onFailure { e ->
                                                    cloudModelsError = when {
                                                        e.message?.contains("401") == true || e.message?.contains("403") == true -> "Invalid API key."
                                                        else -> e.message?.take(120) ?: "Couldn't load models."
                                                    }
                                                }
                                            cloudModelsLoading = false
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    if (cloudModelsLoading) "Loading…" else "Load Models",
                                    color = c.text,
                                    fontSize = IronLogType.meta.fontSize.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (cloudModelsError.isNotBlank()) {
                            Text(cloudModelsError, color = c.danger, fontSize = IronLogType.meta.fontSize.sp)
                        }

                        // Model field — clickable if any models are loaded (from API or preset)
                        val hasAnyModels = cloudModels.isNotEmpty()
                        OutlinedTextField(
                            value = cloudModelName,
                            onValueChange = { cloudModelName = it },
                            label = { Text("Model") },
                            placeholder = { Text("gpt-4o-mini") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = hasAnyModels) { cloudShowModelSheet = true },
                            singleLine = true,
                            readOnly = hasAnyModels,
                            trailingIcon = if (hasAnyModels) {
                                { Text("▾", color = c.muted, fontSize = 16.sp) }
                            } else null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )

                        // Display name
                        OutlinedTextField(
                            value = cloudDisplayName,
                            onValueChange = { cloudDisplayName = it },
                            label = { Text("Display name (optional)") },
                            placeholder = { Text("OpenAI") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )

                        // Verify + Save row
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (cloudVerifyResult.isNotBlank()) {
                                Text(
                                    cloudVerifyResult,
                                    color = if (cloudVerifyResult.startsWith("✓")) c.success else c.danger,
                                    fontSize = IronLogType.meta.fontSize.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.surface)
                                    .border(1.dp, c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                    .clickable(enabled = !cloudVerifying && cloudApiKey.isNotBlank() && cloudBaseUrl.isNotBlank() && cloudModelName.isNotBlank()) {
                                        scope.launch {
                                            cloudVerifying = true
                                            cloudVerifyResult = ""
                                            val fmt = PROVIDER_PRESETS.find { it.key == cloudPreset }?.apiFormat ?: "openai"
                                            CloudAiEngine.verify(cloudBaseUrl, cloudApiKey, cloudModelName, fmt)
                                                .onSuccess { cloudVerifyResult = "✓ Connection verified" }
                                                .onFailure { e -> cloudVerifyResult = "✗ ${e.message?.take(80) ?: "Failed"}" }
                                            cloudVerifying = false
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(if (cloudVerifying) "Verifying…" else "Verify", color = c.text, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .clickable(enabled = cloudApiKey.isNotBlank() && cloudBaseUrl.isNotBlank() && cloudModelName.isNotBlank()) {
                                        scope.launch {
                                            val fmt = PROVIDER_PRESETS.find { it.key == cloudPreset }?.apiFormat ?: "openai"
                                            val keySaved = runCatching {
                                                withContext(Dispatchers.IO) { CloudAiKeyStore.save(context, cloudPreset, cloudApiKey) }
                                            }
                                            if (keySaved.isFailure) {
                                                cloudVerifyResult = "✗ API key could not be stored securely"
                                                return@launch
                                            }
                                            vm.updateSettingsAsync(
                                                settings.copy(
                                                    intelligenceMode = "cloud_ai",
                                                    cloudAiBaseUrl = cloudBaseUrl,
                                                    cloudAiModelName = cloudModelName,
                                                    cloudAiDisplayName = cloudDisplayName.ifBlank { activePreset?.displayName ?: "Cloud AI" },
                                                    cloudAiProviderPreset = cloudPreset,
                                                    cloudAiApiFormat = fmt,
                                                )
                                            )
                                            cloudFormExpanded = false
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text("Save", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── 2. APPEARANCE ──────────────────────────────────────────────────
        item {
            SettingsSection("APPEARANCE") {
                // Track the live theme via ThemeRuntime so the active border
                // updates instantly on tap (settings.theme can lag the async write)
                // FIXED: 30 — Visual preview cards (120×72dp) replacing pill chips
                val activeThemeName by ThemeRuntime.themeName.collectAsState()
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    THEME_TOKENS.forEach { t ->
                        val active = activeThemeName == t.id
                        // Determine if this theme's background is light (luminance > 0.5)
                        // so we can use dark text/borders instead of white ones
                        val bgLuminance = (0.299f * t.bg.red + 0.587f * t.bg.green + 0.114f * t.bg.blue)
                        val isLightBg = bgLuminance > 0.5f
                        val onBgColor = if (isLightBg) Color(0xFF1A1A1A) else Color.White
                        val cardOverlayColor = if (isLightBg) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f)
                        val borderInactiveColor = if (isLightBg) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(IronLogRadius.md.dp))
                                .background(t.bg)
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (active) t.accent else borderInactiveColor,
                                    shape = RoundedCornerShape(IronLogRadius.md.dp),
                                )
                                .clickable { vm.updateSettingsAsync(settings.copy(theme = t.id)); ThemeRuntime.setTheme(t.id) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Mini screen preview
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(t.bg),
                            ) {
                                // Inner card simulation
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.7f)
                                        .fillMaxHeight(0.6f)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(cardOverlayColor)
                                        .align(Alignment.TopStart),
                                )
                                // Accent dot
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(t.accent)
                                        .align(Alignment.BottomEnd),
                                )
                            }
                            Text(
                                t.name,
                                color = if (active) t.accent else onBgColor.copy(alpha = 0.75f),
                                fontSize = IronLogType.micro.fontSize.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // ── 3. WORKOUT ─────────────────────────────────────────────────────
        item {
            SettingsSection("WORKOUT") {
                // Weight unit
                Text("Weight unit", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    listOf("kg", "lbs").forEach { unit ->
                        val active = settings.weightUnit == unit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) c.accent.copy(alpha = 0.18f) else c.surface)
                                .border(1.5.dp, if (active) c.accent else c.faint, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (settings.hapticFeedback) HapticsEngine.lightConfirm(context)
                                    vm.updateSettingsAsync(settings.copy(weightUnit = unit))
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                unit.uppercase(),
                                color = if (active) c.accent else c.subtext,
                                fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                                fontSize = IronLogType.section.fontSize.sp,
                                letterSpacing = 2.sp,
                            )
                        }
                    }
                }
                // Effort tracking
                SectionRow(borderBottom = true) {
                    Text("Effort tracking", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Text(EFFORT_LABEL[settings.effortTracking] ?: "Off", color = c.subtext, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.clickable { cycleEffort(settings, vm) })
                }
                Text("Performance mode", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                GoalChipRow(PERFORMANCE_MODES, settings.performanceMode, hapticsEnabled = settings.hapticFeedback) {
                    vm.updateSettingsAsync(settings.copy(performanceMode = it))
                }
                // Rest timers
                SectionRow(borderBottom = true) {
                    Text(
                        "Rest — normal sets",
                        color = c.text,
                        fontSize = IronLogType.body.fontSize.sp,
                        modifier = Modifier.weight(1f).clickable { showNormalRestDialog = true },
                    )
                    Text(
                        "${settings.defaultRestSeconds}s",
                        color = c.subtext,
                        fontSize = IronLogType.body.fontSize.sp,
                        modifier = Modifier.clickable { showNormalRestDialog = true },
                    )
                }
                SectionRow(borderBottom = true) {
                    Text(
                        "Rest — heavy sets",
                        color = c.text,
                        fontSize = IronLogType.body.fontSize.sp,
                        modifier = Modifier.weight(1f).clickable { showHeavyRestDialog = true },
                    )
                    Text(
                        "${settings.defaultRestHeavySeconds}s",
                        color = c.subtext,
                        fontSize = IronLogType.body.fontSize.sp,
                        modifier = Modifier.clickable { showHeavyRestDialog = true },
                    )
                }
                SectionRow(borderBottom = true) {
                    Text(
                        "Bar weight",
                        color = c.text,
                        fontSize = IronLogType.body.fontSize.sp,
                        modifier = Modifier.weight(1f).clickable { showBarWeightDialog = true },
                    )
                    Text(
                        "${settings.barWeightKg} kg",
                        color = c.subtext,
                        fontSize = IronLogType.body.fontSize.sp,
                        modifier = Modifier.clickable { showBarWeightDialog = true },
                    )
                }
                // Haptics
                SectionRow(borderBottom = true) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptic feedback", color = c.text, fontSize = IronLogType.body.fontSize.sp)
                    }
                    Switch(
                        checked = settings.hapticFeedback,
                        onCheckedChange = {
                            HapticsEngine.toggle(context, it)
                            vm.updateSettingsAsync(settings.copy(hapticFeedback = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = c.accent, checkedTrackColor = c.accentSoft)
                    )
                }
                // Keep awake
                SectionRow(borderBottom = false) {
                    Text("Keep screen awake during workout", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = keepAwakeDuringWorkout,
                        onCheckedChange = {
                            if (settings.hapticFeedback) HapticsEngine.toggle(context, it)
                            keepAwakeDuringWorkout = it
                            scope.launch(Dispatchers.IO) { settingsRepo.setBoolean("keep_screen_awake_active_workout", it) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = c.accent, checkedTrackColor = c.accentSoft)
                    )
                }
            }
        }

        // ── 4. TOOLS ───────────────────────────────────────────────────────
        item {
            SettingsSection("TOOLS") {
                SectionNavRow("Exercise Library", onPress = onOpenExerciseLibrary, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Gym Profiles", onPress = onOpenGymProfiles, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Body Weight Tracker", onPress = onOpenBodyWeight, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("AI Plan Creator", onPress = onOpenAIPlan, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Program Picker", onPress = onOpenProgramPicker, borderBottom = false, hapticsEnabled = settings.hapticFeedback)
            }
        }

        // ── 5. REMINDERS ───────────────────────────────────────────────────
        item {
            SettingsSection("REMINDERS") {
                if (!notificationsAllowed) {
                    SectionRow(borderBottom = true) {
                        Text(
                            "Notifications are disabled — tap to open settings",
                            color = c.warning,
                            fontSize = IronLogType.meta.fontSize.sp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = c.warning)
                    }
                }
                SectionRow(borderBottom = true) {
                    Text("Smart notifications", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            if (settings.hapticFeedback) HapticsEngine.toggle(context, it)
                            notificationsEnabled = it
                            scope.launch(Dispatchers.IO) {
                                settingsRepo.setBoolean("notifications_enabled", it)
                                if (it) {
                                    val minutes = parseQuietTimeInput(dailyReminderInput.trim())
                                        ?: settingsRepo.getSettingNumber("dailyReminderTimeMinutes")?.toInt()
                                        ?: (8 * 60)
                                    ReminderScheduler.scheduleDailyReminder(context, minutes / 60, minutes % 60)
                                } else {
                                    ReminderScheduler.cancelDailyReminder(context)
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = c.accent, checkedTrackColor = c.accentSoft)
                    )
                }
                SectionRow(borderBottom = true) {
                    Text("Workout reminders", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = workoutReminders,
                        onCheckedChange = {
                            if (settings.hapticFeedback) HapticsEngine.toggle(context, it)
                            workoutReminders = it
                            scope.launch(Dispatchers.IO) { settingsRepo.setBoolean("training_reminders_enabled", it) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = c.accent, checkedTrackColor = c.accentSoft)
                    )
                }
                SectionRow(borderBottom = true) {
                    Text("Milestone alerts", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = milestoneAlertsEnabled,
                        onCheckedChange = {
                            if (settings.hapticFeedback) HapticsEngine.toggle(context, it)
                            milestoneAlertsEnabled = it
                            scope.launch(Dispatchers.IO) { settingsRepo.setBoolean("milestone_alerts_enabled", it) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = c.accent, checkedTrackColor = c.accentSoft)
                    )
                }
                // Daily reminder time
                // FIXED: 32 — Replaced uppercase micro label with readable copy
                Text("Daily reminder time", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = dailyReminderInput, onValueChange = { dailyReminderInput = it },
                        placeholder = { Text("18:30", color = c.muted) },
                        label = { Text("Time (HH:MM)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showReminderTimePicker = true }) {
                                Icon(Icons.Outlined.Schedule, contentDescription = "Pick time", tint = c.muted)
                            }
                        },
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.accentSoft)
                            .border(1.dp, c.accentBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    val minutes = parseQuietTimeInput(dailyReminderInput.trim())
                                    if (minutes != null) {
                                        settingsRepo.setSetting("dailyReminderTimeMinutes", minutes, "number")
                                        if (notificationsEnabled) {
                                            ReminderScheduler.scheduleDailyReminder(context, minutes / 60, minutes % 60)
                                        }
                                    } else {
                                        settingsRepo.setSetting("dailyReminderTimeMinutes", null, "number")
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) { Text("Apply", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp) }
                }
                // FIXED: 33 — Context link between reminder and quiet hours
                Text(
                    "Reminders are suppressed during quiet hours.",
                    color = c.muted,
                    fontSize = IronLogType.micro.fontSize.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                // Quiet hours
                Text("Quiet hours", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Start", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(bottom = 6.dp))
                        OutlinedTextField(value = quietStart, onValueChange = { quietStart = it }, placeholder = { Text("22:00", color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        Text("End", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(bottom = 6.dp))
                        OutlinedTextField(value = quietEnd, onValueChange = { quietEnd = it }, placeholder = { Text("08:00", color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.accentSoft)
                        .border(1.dp, c.accentBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            val s = parseQuietTimeInput(quietStart)
                            val e = parseQuietTimeInput(quietEnd)
                            if (s != null && e != null) {
                                scope.launch(Dispatchers.IO) {
                                    settingsRepo.setSetting("quietHoursStartMinutes", s, "number")
                                    settingsRepo.setSetting("quietHoursEndMinutes", e, "number")
                                    val reminderParsed = parseQuietTimeInput(dailyReminderInput.trim()) ?: (8 * 60)
                                    if (notificationsEnabled) ReminderScheduler.scheduleDailyReminder(context, reminderParsed / 60, reminderParsed % 60)
                                    else ReminderScheduler.cancelDailyReminder(context)
                                }
                                reminderStatus = "Saved"
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Save Reminder Settings", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp) }
                if (reminderStatus.isNotBlank()) Text(reminderStatus, color = c.accent, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(top = 6.dp))
                // Notification frequency profile
                Text("Notification frequency", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    listOf("conservative" to "Conservative", "balanced" to "Balanced", "aggressive" to "Aggressive").forEach { (id, label) ->
                        val active = notifProfileState == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) c.accent.copy(alpha = 0.18f) else c.surface)
                                .border(1.5.dp, if (active) c.accent else c.faint, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (settings.hapticFeedback) HapticsEngine.lightConfirm(context)
                                    notifProfileState = id
                                    scope.launch(Dispatchers.IO) {
                                        settingsRepo.setSetting("notification_profile", id, "string")
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (active) c.accent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal)
                        }
                    }
                }
                // Test notification
                SectionRow(borderBottom = false, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Send test notification", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f).clickable {
                        if (!notificationsAllowed) {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } else {
                            WorkoutNotificationBridge.showTestNotification(context)
                        }
                    })
                    Icon(Icons.Filled.ChevronRight, null, tint = c.muted)
                }
            }
        }

        // ── 6. DATA & BACKUP ───────────────────────────────────────────────
        item {
            // FIXED: 34 — Clear All History moved here from HistoryScreen
            SettingsSection("DATA & BACKUP") {
                SectionNavRow("Import from Other Apps", onPress = onOpenImportCenter, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Backup & Export", onPress = onOpenDataPortability, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Export history as CSV", onPress = {
                    val csv = buildHistoryCsv(state.history)
                    val file = File(context.cacheDir, "ironlog_history_export.csv")
                    file.writeText(csv)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    context.startActivity(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "IronLog history export")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                }, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Import history from CSV", onPress = {
                    csvPicker.launch(arrayOf("text/csv", "text/plain", "*/*"))
                }, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Clear All History", onPress = { confirmClearAll = true }, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Reset All PRs", onPress = { confirmResetPbs = true }, borderBottom = false, hapticsEnabled = settings.hapticFeedback)
            }
        }

        // ── 7. ABOUT ───────────────────────────────────────────────────────
        item {
            SettingsSection("ABOUT") {
                SectionNavRow("Privacy & Data", onPress = onOpenPrivacy, hapticsEnabled = settings.hapticFeedback)
                SectionRow(borderBottom = true) {
                    Text("Version", color = c.muted, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Text("IronLog $versionName", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                }
                SectionRow(borderBottom = false) {
                    Text("Restart app tutorial", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f).clickable { confirmResetOnboarding = true })
                    Icon(Icons.Filled.ChevronRight, null, tint = c.muted)
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    // FIXED: 34 — Clear All History confirmation dialog
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently delete all workout history. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.clearAllHistory(); confirmClearAll = false }) {
                    Text("Clear All", color = c.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmResetPbs) {
        AlertDialog(
            onDismissRequest = { confirmResetPbs = false },
            title = { Text("Reset all PRs?") },
            text = { Text("This will clear all personal best records and PR summaries.") },
            confirmButton = {
                TextButton(onClick = { vm.clearPbs(); confirmResetPbs = false }) {
                    Text("Reset PRs", color = c.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetPbs = false }) { Text("Cancel") }
            },
        )
    }

    if (showNormalRestDialog) {
        AlertDialog(
            onDismissRequest = { showNormalRestDialog = false },
            title = { Text("Normal set rest (seconds)") },
            text = {
                OutlinedTextField(
                    value = normalRestInput,
                    onValueChange = { v -> if (v.all(Char::isDigit)) normalRestInput = v },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val sec = normalRestInput.toIntOrNull()?.coerceIn(15, 600) ?: settings.defaultRestSeconds
                    vm.updateSettingsAsync(settings.copy(defaultRestSeconds = sec))
                    showNormalRestDialog = false
                }) { Text("Save", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { showNormalRestDialog = false }) { Text("Cancel") } },
        )
    }

    if (showHeavyRestDialog) {
        AlertDialog(
            onDismissRequest = { showHeavyRestDialog = false },
            title = { Text("Heavy set rest (seconds)") },
            text = {
                OutlinedTextField(
                    value = heavyRestInput,
                    onValueChange = { v -> if (v.all(Char::isDigit)) heavyRestInput = v },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val sec = heavyRestInput.toIntOrNull()?.coerceIn(30, 900) ?: settings.defaultRestHeavySeconds
                    vm.updateSettingsAsync(settings.copy(defaultRestHeavySeconds = sec))
                    showHeavyRestDialog = false
                }) { Text("Save", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { showHeavyRestDialog = false }) { Text("Cancel") } },
        )
    }
    if (showBarWeightDialog) {
        AlertDialog(
            onDismissRequest = { showBarWeightDialog = false },
            title = { Text("Bar weight (kg)") },
            text = {
                OutlinedTextField(
                    value = barWeightInput,
                    onValueChange = { barWeightInput = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = barWeightInput.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: settings.barWeightKg
                    vm.updateSettingsAsync(settings.copy(barWeightKg = next))
                    showBarWeightDialog = false
                }) { Text("Save", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { showBarWeightDialog = false }) { Text("Cancel") } },
        )
    }
    if (showCsvImportConfirm) {
        val pv = csvImportPreview
        AlertDialog(
            onDismissRequest = { showCsvImportConfirm = false },
            title = { Text("Import CSV history?") },
            text = {
                Text(
                    if (pv == null) "No preview available."
                    else "Import ${pv.validRows} rows (${pv.domainCounts["workouts"] ?: 0} workouts, ${pv.domainCounts["sets"] ?: 0} sets)?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCsvImportConfirm = false
                    val preview = csvImportPreview ?: return@TextButton
                    scope.launch(Dispatchers.IO) {
                        val imported = runCatching {
                            importText(
                                text = csvImportText,
                                repo = workoutRepo,
                                importExportRepo = importExportRepo,
                                preview = preview,
                                sourceHint = "strong_csv",
                                replaceMode = false,
                            )
                        }
                        withContext(Dispatchers.Main) {
                            csvImportStatus = imported.fold(
                                onSuccess = { "Imported $it workout session(s) from CSV." },
                                onFailure = { "CSV import failed: ${it.message}" },
                            )
                            csvImportText = ""
                            csvImportPreview = null
                        }
                    }
                }) { Text("Import", color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCsvImportConfirm = false }) { Text("Cancel") }
            },
        )
    }
    if (csvImportStatus.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { csvImportStatus = "" },
            title = { Text("CSV Import") },
            text = { Text(csvImportStatus) },
            confirmButton = { TextButton(onClick = { csvImportStatus = "" }) { Text("OK") } },
        )
    }

    if (confirmResetOnboarding) {
        AlertDialog(
            onDismissRequest = { confirmResetOnboarding = false },
            title = { Text("Restart tutorial?") },
            text = { Text("This will restart the onboarding flow on next app open.") },
            confirmButton = {
                TextButton(onClick = { confirmResetOnboarding = false; vm.resetOnboarding() }) {
                    Text("Restart", color = c.accent)
                }
            },
            dismissButton = { TextButton(onClick = { confirmResetOnboarding = false }) { Text("Cancel") } },
        )
    }

    // Daily reminder time picker dialog
    if (showReminderTimePicker) {
        val parsed = parseQuietTimeInput(dailyReminderInput.trim())
        val initHour = parsed?.div(60) ?: 18
        val initMin  = parsed?.rem(60) ?: 0
        @OptIn(ExperimentalMaterial3Api::class)
        val tpState = rememberTimePickerState(initialHour = initHour, initialMinute = initMin, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showReminderTimePicker = false },
            containerColor = c.card,
            title = { Text("Set Reminder Time", color = c.text) },
            text = {
                @OptIn(ExperimentalMaterial3Api::class)
                TimePicker(state = tpState)
            },
            confirmButton = {
                TextButton(onClick = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    dailyReminderInput = "%02d:%02d".format(tpState.hour, tpState.minute)
                    scope.launch(Dispatchers.IO) {
                        @OptIn(ExperimentalMaterial3Api::class)
                        settingsRepo.setSetting("dailyReminderTimeMinutes", tpState.hour * 60 + tpState.minute, "number")
                    }
                    showReminderTimePicker = false
                }) { Text("SET", color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showReminderTimePicker = false }) { Text("CANCEL", color = c.muted) }
            },
        )
    }

    // ── Cloud AI model picker bottom sheet ─────────────────────────────────
    if (cloudShowModelSheet) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { cloudShowModelSheet = false; cloudModelSearch = "" },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            // Fixed-height column so the sheet is always fully expanded — prevents the
            // list's overscroll from bubbling into the sheet's drag-to-dismiss gesture.
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Select Model", color = c.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = cloudModelSearch,
                    onValueChange = { cloudModelSearch = it },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                val filtered = cloudModels.filter { it.contains(cloudModelSearch, ignoreCase = true) }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(filtered.size) { idx ->
                        val model = filtered[idx]
                        Text(
                            model,
                            color = if (model == cloudModelName) c.accent else c.text,
                            fontSize = IronLogType.body.fontSize.sp,
                            fontWeight = if (model == cloudModelName) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cloudModelName = model
                                    cloudShowModelSheet = false
                                    cloudModelSearch = ""
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }

}

private fun buildHistoryCsv(history: List<com.ironlog.app.ui.model.HistoryEntry>): String {
    val header = "date,workout_name,exercise_name,set,weight_kg,reps,type"
    val rows = history.flatMap { h ->
        h.exercises.flatMap { ex ->
            ex.sets.mapIndexed { idx, st ->
                listOf(
                    h.date.substringBefore('T'),
                    h.name,
                    ex.name,
                    (idx + 1).toString(),
                    st.weight.toString(),
                    st.reps.toString(),
                    st.type,
                ).joinToString(",") { csvEscape(it) }
            }
        }
    }
    return (listOf(header) + rows).joinToString("\n")
}

private fun csvEscape(v: String): String {
    val mustQuote = v.contains(",") || v.contains("\"") || v.contains("\n")
    val cleaned = v.replace("\"", "\"\"")
    return if (mustQuote) "\"$cleaned\"" else cleaned
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = useTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.cardBorder),
        shape = RoundedCornerShape(IronLogRadius.xl.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 3.sp, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun SectionRow(borderBottom: Boolean = true, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = useTheme()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (borderBottom) Modifier.border(BorderStroke(0.dp, Color.Transparent)) else Modifier)
            .padding(vertical = 13.dp)
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SectionNavRow(label: String, onPress: () -> Unit, borderBottom: Boolean = true, hapticsEnabled: Boolean = true) {
    val c = useTheme()
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (hapticsEnabled) HapticsEngine.lightConfirm(context)
                onPress()
            }
            .padding(vertical = 13.dp)
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = c.muted)
    }
}

// FIXED: 31 — StepperBtn enlarged to 44dp for touch-target compliance
@Composable
private fun StepperBtn(label: String, onClick: () -> Unit) {
    val c = useTheme()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, c.faint, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalChipRow(options: List<Pair<String, String>>, selected: String, hapticsEnabled: Boolean = true, onSelect: (String) -> Unit) {
    val c = useTheme()
    val context = LocalContext.current
    FlowChipRow {
        options.forEach { (id, label) ->
            val active = selected == id
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    // Use a direct accent fill + solid accent ring so the selected state is
                    // always visible regardless of theme (accentSoft is too faint on some themes).
                    .background(if (active) c.accent.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.5.dp, if (active) c.accent else c.faint, CircleShape)
                    .clickable {
                        if (hapticsEnabled) HapticsEngine.selection(context)
                        onSelect(id)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = if (active) c.accent else c.subtext,
                    fontSize = IronLogType.meta.fontSize.sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowChipRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

internal fun Modifier.drawBottomBorder(color: Color): Modifier = this.drawBehind {
        val strokeWidth = 1.dp.toPx()
        drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth)
    }

private fun cycleEffort(settings: IronLogSettings, vm: AppDataViewModel) {
    val idx = EFFORT_CYCLE.indexOf(settings.effortTracking).let { if (it < 0) 0 else it }
    val next = EFFORT_CYCLE[(idx + 1) % EFFORT_CYCLE.size]
    vm.updateSettingsAsync(settings.copy(effortTracking = next))
}

fun formatBytes(bytes: Long): String = if (bytes < 1024L * 1024L) "${"%.1f".format(bytes / 1024.0)} KB" else "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
fun formatQuietTime(totalMinutes: Int): String { val minutes = max(0, min(1439, totalMinutes)); return "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}" }
fun parseQuietTimeInput(value: String): Int? {
    val m = Regex("^(\\d{1,2})(?::?(\\d{1,2}))?$").matchEntire(value.trim()) ?: return null
    val hours = m.groupValues[1].toIntOrNull() ?: return null
    val minutes = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}
fun getQuietMinutes(settings: Map<String, Any?>, key: String = "quietHoursStartMinutes", fallbackHour: Int = 22): Int {
    val direct = (settings[key] as? Number)?.toInt()
    if (direct != null) return max(0, min(1439, direct))
    val fallbackKey = if (key == "quietHoursStartMinutes") "quietHoursStart" else "quietHoursEnd"
    val hour = (settings[fallbackKey] as? Number)?.toDouble() ?: fallbackHour.toDouble()
    return max(0, min(1439, (hour * 60).roundToInt()))
}
fun formatDecisionLabel(entry: Map<String, Any?>): String {
    val topic = (entry["topic"] ?: entry["key"] ?: "system").toString().replace("_", " ").replaceFirstChar { it.titlecase() }
    val reason = entry["reason"]?.toString()
    val suppressedReasonMap = mapOf("disabled_or_no_candidates" to "Nothing worth sending right now", "snoozed" to "Notifications are snoozed", "daily_cap" to "Daily cap reached", "weekly_cap" to "Weekly cap reached", "cooldown_or_topic_gate" to "Cooldown is active", "already_actioned" to "Action already completed", "permission_denied" to "Notification permission is off")
    return when (entry["outcome"]?.toString()) { "suppressed" -> "SUPPRESSED - ${suppressedReasonMap[reason] ?: topic}"; "sent" -> "SCHEDULED - $topic"; else -> "${entry["outcome"]?.toString()?.uppercase() ?: "EVENT"} - $topic" }
}
fun formatDecisionTime(entry: Map<String, Any?>): String = ((if (entry["outcome"] == "sent") entry["scheduledFor"] else entry["at"]) ?: "").toString().replace("T", " ").take(16)


