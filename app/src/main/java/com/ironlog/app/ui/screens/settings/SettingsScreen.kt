package com.ironlog.app.ui.screens.settings

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import com.ironlog.app.IronLogApplication
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.ui.context.ThemeRuntime
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.components.IronLogSwitch
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.model.IronLogSettings
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.services.NotificationCoordinator
import com.ironlog.app.services.NotificationDeliveryResult
import com.ironlog.app.services.NotificationMutationTarget
import com.ironlog.app.services.WorkoutNotificationBridge
import com.ironlog.app.util.HapticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.data.repository.ImportExportRepository
import com.ironlog.app.data.repository.SettingsRepository
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
private data class ReminderUiSnapshot(
    val enabled: Boolean,
    val workoutReminders: Boolean,
    val milestoneAlerts: Boolean,
    val quietStartMinutes: Int,
    val quietEndMinutes: Int,
    val reminderMinutes: Int,
    val profile: String,
)

private suspend fun loadReminderUiSnapshot(repo: SettingsRepository): ReminderUiSnapshot =
    withContext(Dispatchers.IO) {
        ReminderUiSnapshot(
            enabled = repo.getBoolean("notifications_enabled", false),
            workoutReminders = repo.getBoolean("training_reminders_enabled", true),
            milestoneAlerts = repo.getBoolean("milestone_alerts_enabled", true),
            quietStartMinutes = repo.getSettingNumber("quietHoursStartMinutes")?.toInt()
                ?.takeIf { it in 0 until 24 * 60 } ?: 22 * 60,
            quietEndMinutes = repo.getSettingNumber("quietHoursEndMinutes")?.toInt()
                ?.takeIf { it in 0 until 24 * 60 } ?: 8 * 60,
            reminderMinutes = repo.getSettingNumber("dailyReminderTimeMinutes")?.toInt()
                ?.takeIf { it in 0 until 24 * 60 } ?: 8 * 60,
            profile = repo.getString("notification_profile")?.takeIf { it.isNotBlank() }
                ?: "balanced",
        )
    }

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
    ThemeToken("monet",            "Monet (Fallback)", Color(0xFF1A1A1A), Color(0xFFB8C3FF)),
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
    val application = remember(context) { context.applicationContext as IronLogApplication }
    val acceptedMutationCount by application.acceptedMutationCount.collectAsStateWithLifecycle()
    val historyMutationPending = acceptedMutationCount > 0
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val settingsRepo = remember { SettingsRepository() }
    val workoutRepo = remember { WorkoutRepository() }
    val importExportRepo = remember { ImportExportRepository() }
    val scope = rememberCoroutineScope()
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }
    var settingsDestinationName by rememberSaveable {
        mutableStateOf(SettingsDestination.CONSOLE.name)
    }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    val settingsDestination = runCatching {
        SettingsDestination.valueOf(settingsDestinationName)
    }.getOrDefault(SettingsDestination.CONSOLE)

    BackHandler(enabled = settingsDestination != SettingsDestination.CONSOLE) {
        settingsDestinationName = SettingsDestination.CONSOLE.name
    }

    var confirmResetOnboarding by remember { mutableStateOf(false) }
    var notifProfileState by remember { mutableStateOf("balanced") }

    // FIXED: 34 — Clear All History confirmation state (moved from HistoryScreen)
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmResetPbs by remember { mutableStateOf(false) }
    var clearHistoryPending by remember { mutableStateOf(false) }
    var clearHistoryError by remember { mutableStateOf<String?>(null) }
    var resetPbsPending by remember { mutableStateOf(false) }
    var resetPbsError by remember { mutableStateOf<String?>(null) }
    var showNormalRestDialog by remember { mutableStateOf(false) }
    var showHeavyRestDialog by remember { mutableStateOf(false) }
    var showBarWeightDialog by remember { mutableStateOf(false) }
    var csvImportText by remember { mutableStateOf("") }
    var csvImportPreview by remember { mutableStateOf<CsvPreview?>(null) }
    var showCsvImportConfirm by remember { mutableStateOf(false) }
    var csvImportStatus by remember { mutableStateOf("") }
    var dataExportStatus by remember { mutableStateOf("") }
    var normalRestInput by remember { mutableStateOf("") }
    var heavyRestInput by remember { mutableStateOf("") }
    var barWeightInput by remember { mutableStateOf("") }
    var normalRestDirty by remember { mutableStateOf(false) }
    var heavyRestDirty by remember { mutableStateOf(false) }
    var barWeightDirty by remember { mutableStateOf(false) }
    var normalRestSaving by remember { mutableStateOf(false) }
    var heavyRestSaving by remember { mutableStateOf(false) }
    var barWeightSaving by remember { mutableStateOf(false) }
    var normalRestSaveError by remember { mutableStateOf<String?>(null) }
    var heavyRestSaveError by remember { mutableStateOf<String?>(null) }
    var barWeightSaveError by remember { mutableStateOf<String?>(null) }
    var notificationsAllowed by remember {
        mutableStateOf(NotificationCoordinator.isSystemDeliveryAvailable(context))
    }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var workoutReminders by remember { mutableStateOf(true) }
    var milestoneAlertsEnabled by remember { mutableStateOf(true) }
    var quietStart by remember { mutableStateOf("22:00") }
    var quietEnd by remember { mutableStateOf("08:00") }
    var dailyReminderInput by remember { mutableStateOf("") }
    var reminderStatus by remember { mutableStateOf("") }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var keepAwakeDuringWorkout by remember { mutableStateOf(true) }
    var keepAwakeDirty by remember { mutableStateOf(false) }
    var keepAwakeSaving by remember { mutableStateOf(false) }
    var keepAwakeSaveError by remember { mutableStateOf<String?>(null) }
    var dataMutationNotice by remember { mutableStateOf<String?>(null) }
    // Intelligence Engine section
    var nanoAvailability by remember { mutableStateOf<NanoAvailability?>(null) }
    var nanoDownloading by remember { mutableStateOf(false) }
    var nanoDownloadResult by remember { mutableStateOf("") }
    // Cloud AI config form state
    var cloudFormExpanded by remember { mutableStateOf(false) }
    var cloudPreset by remember { mutableStateOf("openai") }
    var cloudBaseUrl by remember { mutableStateOf("") }
    var cloudApiKey by remember { mutableStateOf("") }
    var cloudApiKeyVisible by remember { mutableStateOf(false) }
    var cloudModelName by remember { mutableStateOf("") }
    var cloudDisplayName by remember { mutableStateOf("") }
    var cloudFormDirty by remember { mutableStateOf(false) }
    var cloudApiKeyDirty by remember { mutableStateOf(false) }
    var cloudKeyLoading by remember { mutableStateOf(false) }
    var cloudSaving by remember { mutableStateOf(false) }
    var cloudSaveError by remember { mutableStateOf<String?>(null) }
    var cloudModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var cloudModelsLoading by remember { mutableStateOf(false) }
    var cloudModelsError by remember { mutableStateOf("") }
    var cloudShowModelSheet by remember { mutableStateOf(false) }
    var cloudModelSearch by remember { mutableStateOf("") }
    var cloudVerifyResult by remember { mutableStateOf("") }
    var cloudVerifying by remember { mutableStateOf(false) }

    suspend fun restoreReminderUiState(
        target: NotificationMutationTarget? = null,
        requestToken: Long = 0L,
    ): Boolean {
        val snapshot = loadReminderUiSnapshot(settingsRepo)
        val deliveryAvailable = NotificationCoordinator.isSystemDeliveryAvailable(context)
        if (target != null && !NotificationCoordinator.isMutationTokenCurrent(target, requestToken)) {
            return false
        }
        when (target) {
            null -> {
                notificationsEnabled = snapshot.enabled
                workoutReminders = snapshot.workoutReminders
                milestoneAlertsEnabled = snapshot.milestoneAlerts
                quietStart = formatQuietTime(snapshot.quietStartMinutes)
                quietEnd = formatQuietTime(snapshot.quietEndMinutes)
                dailyReminderInput = formatQuietTime(snapshot.reminderMinutes)
                notifProfileState = snapshot.profile
                notificationsAllowed = deliveryAvailable
            }
            NotificationMutationTarget.ENABLED -> {
                notificationsEnabled = snapshot.enabled
                notificationsAllowed = deliveryAvailable
            }
            NotificationMutationTarget.TRAINING_REMINDERS -> workoutReminders = snapshot.workoutReminders
            NotificationMutationTarget.MILESTONE_ALERTS -> milestoneAlertsEnabled = snapshot.milestoneAlerts
            NotificationMutationTarget.REMINDER_TIME -> dailyReminderInput = formatQuietTime(snapshot.reminderMinutes)
            NotificationMutationTarget.QUIET_HOURS -> {
                quietStart = formatQuietTime(snapshot.quietStartMinutes)
                quietEnd = formatQuietTime(snapshot.quietEndMinutes)
            }
            NotificationMutationTarget.FREQUENCY_PROFILE -> notifProfileState = snapshot.profile
            NotificationMutationTarget.RECONCILE -> notificationsAllowed = deliveryAvailable
        }
        return true
    }

    fun runReminderMutation(
        target: NotificationMutationTarget,
        successMessage: String? = null,
        failureMessage: String,
        mutation: suspend (requestToken: Long) -> Unit,
    ) {
        // Issue synchronously at the user-event boundary. If two coroutines reach IO in
        // reverse order, the older token is already stale before either write begins.
        val requestToken = NotificationCoordinator.issueMutationToken(target)
        scope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) { mutation(requestToken) }
                if (!NotificationCoordinator.isMutationTokenCurrent(target, requestToken)) return@launch
                val restored = restoreReminderUiState(target, requestToken)
                if (restored && NotificationCoordinator.isMutationTokenCurrent(target, requestToken) && successMessage != null) {
                    reminderStatus = successMessage
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!NotificationCoordinator.isMutationTokenCurrent(target, requestToken)) return@launch
                val restored = runCatching { restoreReminderUiState(target, requestToken) }.getOrDefault(false)
                if (restored && NotificationCoordinator.isMutationTokenCurrent(target, requestToken)) {
                    reminderStatus = failureMessage
                }
            }
        }
    }

    fun saveWeeklyGoal(days: Int) {
        val normalized = days.coerceIn(1, 7)
        if (normalized == settings.weeklyGoalDays.coerceIn(1, 7)) return
        scope.launch {
            try {
                // Await the settings write itself. A stale reminder must not be dismissed
                // for an optimistic value that never reached the authoritative store.
                vm.mutateSettings { it.copy(weeklyGoalDays = normalized) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Could not persist weekly workout goal")
                vm.refreshAwaited()
                return@launch
            }

            try {
                WorkoutNotificationBridge.cancelReminderAfterDataMutation(context.applicationContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Persistence succeeded; notification cleanup failure must not roll it back.
                Timber.w(error, "Could not dismiss stale reminder after weekly goal change")
            }
        }
    }

    suspend fun <T> commitHistoryMutation(
        mutation: suspend () -> T,
    ): HistoryMutationOutcome<T> {
        return commitHistoryMutationAcrossSurfaces(
            context = context,
            viewModel = vm,
            mutation = mutation,
        ).also { outcome ->
            outcome.surfaces.failures.forEach { (surface, error) ->
                Timber.w(error, "Could not refresh %s after history mutation", surface.name)
            }
        }
    }

    suspend fun reloadAuthoritativeSettings(): IronLogSettings {
        try {
            vm.refreshAwaited()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.w(error, "Could not reload authoritative settings after a failed save")
        }
        return vm.state.value.settings
    }

    fun openNormalRestEditor() {
        normalRestInput = settings.defaultRestSeconds.toString()
        normalRestDirty = false
        normalRestSaveError = null
        showNormalRestDialog = true
    }

    fun openHeavyRestEditor() {
        heavyRestInput = settings.defaultRestHeavySeconds.toString()
        heavyRestDirty = false
        heavyRestSaveError = null
        showHeavyRestDialog = true
    }

    fun openBarWeightEditor() {
        barWeightInput = settings.barWeightKg.toString()
        barWeightDirty = false
        barWeightSaveError = null
        showBarWeightDialog = true
    }

    fun saveNormalRest() {
        if (normalRestSaving) return
        val seconds = normalRestInput.toIntOrNull()
        if (seconds == null || seconds !in 15..600) {
            normalRestSaveError = "Enter a rest time from 15 to 600 seconds."
            return
        }
        normalRestSaving = true
        normalRestSaveError = null
        scope.launch {
            try {
                vm.mutateSettings { it.copy(defaultRestSeconds = seconds) }
                normalRestDirty = false
                showNormalRestDialog = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Could not save normal-set rest time")
                val authoritative = reloadAuthoritativeSettings()
                normalRestInput = authoritative.defaultRestSeconds.toString()
                normalRestDirty = false
                normalRestSaveError = "Normal-set rest could not be saved. Try again."
            } finally {
                normalRestSaving = false
            }
        }
    }

    fun saveHeavyRest() {
        if (heavyRestSaving) return
        val seconds = heavyRestInput.toIntOrNull()
        if (seconds == null || seconds !in 30..900) {
            heavyRestSaveError = "Enter a rest time from 30 to 900 seconds."
            return
        }
        heavyRestSaving = true
        heavyRestSaveError = null
        scope.launch {
            try {
                vm.mutateSettings { it.copy(defaultRestHeavySeconds = seconds) }
                heavyRestDirty = false
                showHeavyRestDialog = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Could not save heavy-set rest time")
                val authoritative = reloadAuthoritativeSettings()
                heavyRestInput = authoritative.defaultRestHeavySeconds.toString()
                heavyRestDirty = false
                heavyRestSaveError = "Heavy-set rest could not be saved. Try again."
            } finally {
                heavyRestSaving = false
            }
        }
    }

    fun saveBarWeight() {
        if (barWeightSaving) return
        val kilograms = barWeightInput.toDoubleOrNull()
        if (kilograms == null || kilograms !in 0.0..100.0) {
            barWeightSaveError = "Enter a bar weight from 0 to 100 kg."
            return
        }
        barWeightSaving = true
        barWeightSaveError = null
        scope.launch {
            try {
                vm.mutateSettings { it.copy(barWeightKg = kilograms) }
                barWeightDirty = false
                showBarWeightDialog = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Could not save bar weight")
                val authoritative = reloadAuthoritativeSettings()
                barWeightInput = authoritative.barWeightKg.toString()
                barWeightDirty = false
                barWeightSaveError = "Bar weight could not be saved. Try again."
            } finally {
                barWeightSaving = false
            }
        }
    }

    fun saveCloudConfiguration() {
        if (cloudSaving) return
        if (cloudApiKey.isBlank() || cloudBaseUrl.isBlank() || cloudModelName.isBlank()) return
        val requestedPreset = cloudPreset
        val requestedBaseUrl = cloudBaseUrl.trim()
        val requestedApiKey = cloudApiKey.trim()
        val requestedModel = cloudModelName.trim()
        val requestedDisplayName = cloudDisplayName.trim()
        val provider = PROVIDER_PRESETS.find { it.key == requestedPreset }
        val apiFormat = provider?.apiFormat ?: "openai"
        cloudSaving = true
        cloudSaveError = null
        scope.launch {
            try {
                // The key store and ObjectBox cannot share a native transaction. Once Save
                // is accepted, complete this short two-store operation even if the route leaves.
                withContext(NonCancellable) {
                    var previousRequestedKey = ""
                    var secureKeyWritten = false
                    try {
                        withContext(Dispatchers.IO) {
                            previousRequestedKey = CloudAiKeyStore.load(context.applicationContext, requestedPreset)
                            CloudAiKeyStore.save(context.applicationContext, requestedPreset, requestedApiKey)
                            secureKeyWritten = true
                        }
                        vm.mutateSettings {
                            it.copy(
                                intelligenceMode = "cloud_ai",
                                cloudAiBaseUrl = requestedBaseUrl,
                                cloudAiModelName = requestedModel,
                                cloudAiDisplayName = requestedDisplayName.ifBlank { provider?.displayName ?: "Cloud AI" },
                                cloudAiProviderPreset = requestedPreset,
                                cloudAiApiFormat = apiFormat,
                            )
                        }
                    } catch (error: Throwable) {
                        if (secureKeyWritten) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    if (previousRequestedKey.isBlank()) {
                                        CloudAiKeyStore.clear(context.applicationContext, requestedPreset)
                                    } else {
                                        CloudAiKeyStore.save(context.applicationContext, requestedPreset, previousRequestedKey)
                                    }
                                }.onFailure { rollbackError ->
                                    Timber.w(rollbackError, "Could not restore the prior Cloud AI key after a failed settings save")
                                }
                            }
                        }
                        throw error
                    }
                }
                cloudFormDirty = false
                cloudApiKeyDirty = false
                cloudFormExpanded = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Could not save Cloud AI configuration")
                val authoritative = reloadAuthoritativeSettings()
                val authoritativePreset = authoritative.cloudAiProviderPreset.ifBlank { "openai" }
                cloudPreset = authoritativePreset
                cloudBaseUrl = authoritative.cloudAiBaseUrl
                cloudModelName = authoritative.cloudAiModelName
                cloudDisplayName = authoritative.cloudAiDisplayName
                cloudApiKey = withContext(Dispatchers.IO) {
                    CloudAiKeyStore.load(context.applicationContext, authoritativePreset)
                }
                cloudFormDirty = false
                cloudApiKeyDirty = false
                cloudSaveError = "Cloud AI settings could not be saved. The last saved configuration was reloaded; review the API key before retrying."
            } finally {
                cloudSaving = false
            }
        }
    }

    fun saveKeepAwake(requested: Boolean) {
        if (keepAwakeSaving) return
        val previous = keepAwakeDuringWorkout
        keepAwakeDirty = true
        keepAwakeSaving = true
        keepAwakeSaveError = null
        keepAwakeDuringWorkout = requested
        scope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    settingsRepo.setBoolean("keep_screen_awake_active_workout", requested)
                }
                keepAwakeDirty = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Could not save keep-awake preference")
                keepAwakeDuringWorkout = withContext(Dispatchers.IO) {
                    runCatching {
                        settingsRepo.getBoolean("keep_screen_awake_active_workout", previous)
                    }.getOrDefault(previous)
                }
                keepAwakeDirty = false
                keepAwakeSaveError = "Keep-awake preference could not be saved. Try again."
            } finally {
                keepAwakeSaving = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = NotificationCoordinator.isSystemDeliveryAvailable(context)
        notificationsEnabled = granted
        reminderStatus = if (granted) "Notifications enabled" else "Notification permission was not granted"
        runReminderMutation(
            target = NotificationMutationTarget.ENABLED,
            successMessage = if (granted) "Notifications enabled" else "Smart notifications remain off",
            failureMessage = "Android could not update notification scheduling. Tap the switch to retry.",
        ) { requestToken -> NotificationCoordinator.setEnabled(context, granted, requestToken) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, notificationsEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val available = NotificationCoordinator.isSystemDeliveryAvailable(context)
                val changed = available != notificationsAllowed
                notificationsAllowed = available
                if (changed && notificationsEnabled) {
                    runReminderMutation(
                        target = NotificationMutationTarget.RECONCILE,
                        failureMessage = "Notification scheduling could not be refreshed. Try again.",
                    ) { _ -> NotificationCoordinator.reconcile(context) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || historyMutationPending) return@rememberLauncherForActivityResult
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
        runCatching { restoreReminderUiState() }.onFailure {
            reminderStatus = "Notification settings could not be loaded. Reopen Settings to retry."
        }
        val persistedKeepAwake = withContext(Dispatchers.IO) {
            settingsRepo.getBoolean("keep_screen_awake_active_workout", true)
        }
        if (!keepAwakeDirty) keepAwakeDuringWorkout = persistedKeepAwake
        nanoAvailability = GeminiNanoEngine.checkAvailability(context)
    }

    // AppDataViewModel starts with a default state and hydrates from ObjectBox asynchronously.
    // Only copy persisted values into local editors after that first authoritative snapshot, and
    // never overwrite text the user has already changed while the snapshot was arriving.
    LaunchedEffect(
        state.initialized,
        settings.defaultRestSeconds,
        settings.defaultRestHeavySeconds,
        settings.barWeightKg,
        showNormalRestDialog,
        showHeavyRestDialog,
        showBarWeightDialog,
        normalRestDirty,
        heavyRestDirty,
        barWeightDirty,
    ) {
        if (state.initialized && !showNormalRestDialog && !normalRestDirty) {
            normalRestInput = settings.defaultRestSeconds.toString()
        }
        if (state.initialized && !showHeavyRestDialog && !heavyRestDirty) {
            heavyRestInput = settings.defaultRestHeavySeconds.toString()
        }
        if (state.initialized && !showBarWeightDialog && !barWeightDirty) {
            barWeightInput = settings.barWeightKg.toString()
        }
    }

    LaunchedEffect(
        state.initialized,
        settings.cloudAiProviderPreset,
        settings.cloudAiBaseUrl,
        settings.cloudAiModelName,
        settings.cloudAiDisplayName,
        cloudFormDirty,
    ) {
        if (state.initialized && !cloudFormDirty) {
            cloudPreset = settings.cloudAiProviderPreset.ifBlank { "openai" }
            cloudBaseUrl = settings.cloudAiBaseUrl
            cloudModelName = settings.cloudAiModelName
            cloudDisplayName = settings.cloudAiDisplayName
            cloudApiKeyDirty = false
        }
    }

    LaunchedEffect(state.initialized, cloudPreset, cloudApiKeyDirty) {
        if (!state.initialized || cloudApiKeyDirty) return@LaunchedEffect
        val requestedProvider = cloudPreset
        cloudKeyLoading = true
        try {
            val loadedKey = withContext(Dispatchers.IO) { CloudAiKeyStore.load(context, requestedProvider) }
            if (cloudPreset == requestedProvider && !cloudApiKeyDirty) cloudApiKey = loadedKey
        } finally {
            if (cloudPreset == requestedProvider) cloudKeyLoading = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp),
    ) {
        if (settingsDestination == SettingsDestination.CONSOLE) {
            item {
                SettingsConsoleLanding(
                    query = settingsSearchQuery,
                    onQueryChange = { settingsSearchQuery = it },
                    initialized = state.initialized,
                    statuses = mapOf(
                        SettingsDestination.TRAINING to "${settings.weeklyGoalDays.coerceIn(1, 7)} days · ${settings.weightUnit.uppercase()} · ${EFFORT_LABEL[settings.effortTracking] ?: "Effort off"}",
                        SettingsDestination.INTELLIGENCE to intelligenceModeLabel(settings.intelligenceMode),
                        SettingsDestination.APPEARANCE to (THEME_TOKENS.firstOrNull { it.id == settings.theme }?.name ?: "Dark"),
                        SettingsDestination.NOTIFICATIONS to notificationConsoleStatus(
                            systemAllowed = notificationsAllowed,
                            enabled = notificationsEnabled,
                            workoutReminders = workoutReminders,
                        ),
                        SettingsDestination.DATA_PRIVACY to if (historyMutationPending) "Data change in progress" else "Stored locally · backup tools",
                        SettingsDestination.ABOUT to "IronLog $versionName",
                    ),
                    onOpen = { destination -> settingsDestinationName = destination.name },
                )
            }
        } else {
            item {
                SettingsDetailHeader(
                    destination = settingsDestination,
                    onBack = { settingsDestinationName = SettingsDestination.CONSOLE.name },
                )
            }
        }

        // ── 1. YOUR PROFILE ────────────────────────────────────────────────
        if (settingsDestination == SettingsDestination.TRAINING) item {
            SettingsSection("YOUR PROFILE") {
                // Display name — saves automatically on Done / focus lost
                val focusManager = LocalFocusManager.current
                var nameInput by remember(settings.userName) { mutableStateOf(settings.userName) }
                val commitName = {
                    val trimmed = nameInput.trim()
                    if (trimmed != settings.userName) {
                        vm.mutateSettingsAsync { it.copy(userName = trimmed) }
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
                        .appPadding(bottom = 12.dp)
                        .onFocusChanged { fs -> if (!fs.isFocused) commitName() },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitName() }),
                )
                // Workout days stepper
                SectionRow(borderBottom = true) {
                    Text("Workout days / week", color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepperBtn("-") { saveWeeklyGoal(max(1, settings.weeklyGoalDays - 1)) }
                        Text("${settings.weeklyGoalDays.coerceIn(1, 7)}", color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 32.dp).wrapContentWidth())
                        StepperBtn("+") { saveWeeklyGoal(min(7, settings.weeklyGoalDays + 1)) }
                    }
                }
                // Training focus
                Text("Training focus", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                GoalChipRow(GOAL_MODES, settings.goalMode, hapticsEnabled = settings.hapticFeedback) { goal -> vm.mutateSettingsAsync { it.copy(goalMode = goal) } }
                // Progression style
                Text("Progression style", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                GoalChipRow(PROGRESSION_STYLES, settings.progressionStyle, hapticsEnabled = settings.hapticFeedback) { style -> vm.mutateSettingsAsync { it.copy(progressionStyle = style) } }
                // Athlete Profile nav row
                SectionNavRow("Athlete Profile", onPress = onOpenTrainingIntelligence, borderBottom = false)
            }
        }

        // ── 1b. INTELLIGENCE ENGINE ────────────────────────────────────────
        if (settingsDestination == SettingsDestination.INTELLIGENCE) item {
            SettingsSection("INTELLIGENCE ENGINE") {
                Text(
                    "Choose what powers the intelligence card on your Home screen.",
                    color = c.subtext,
                    fontSize = IronLogType.meta.fontSize.sp,
                    modifier = Modifier.appPadding(bottom = 12.dp),
                )

                Column(Modifier.selectableGroup(), verticalArrangement = appSpacedBy(8.dp)) {

                    // ── Built-in card ──────────────────────────────────────
                    val builtinSelected = settings.intelligenceMode == "builtin"
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                            .background(if (builtinSelected) c.accent.copy(alpha = 0.10f) else c.surface)
                            .border(1.5.dp, if (builtinSelected) c.accent else c.faint, RoundedCornerShape(IronLogRadius.lg.dp))
                            .selectable(
                                selected = builtinSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    vm.mutateSettingsAsync { it.copy(intelligenceMode = "builtin") }
                                    cloudFormExpanded = false
                                },
                            )
                            .padding(12.dp),
                        verticalArrangement = appSpacedBy(4.dp),
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
                            .selectable(
                                selected = apexSelected,
                                enabled = apexEnabled,
                                role = Role.RadioButton,
                                onClick = {
                                    vm.mutateSettingsAsync { it.copy(intelligenceMode = "gemini_nano") }
                                    cloudFormExpanded = false
                                },
                            )
                            .padding(12.dp),
                        verticalArrangement = appSpacedBy(4.dp),
                    ) {
                        Text("✦", fontSize = 22.sp)
                        Text(
                            "APEX ENGINE",
                            color = when { apexSelected -> c.accent; !apexEnabled -> c.muted; else -> c.text },
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
                            color = c.muted,
                            fontSize = IronLogType.meta.fontSize.sp, lineHeight = IronLogType.meta.lineHeight.sp,
                        )
                        if (nanoAvailability == NanoAvailability.NEEDS_DOWNLOAD) {
                            Spacer(Modifier.height(appGapDp(6.dp)))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = !nanoDownloading) {
                                        scope.launch {
                                            nanoDownloading = true
                                            nanoDownloadResult = ""
                                            GeminiNanoEngine.downloadModel(context)
                                                .onSuccess {
                                                    nanoAvailability = NanoAvailability.SUPPORTED
                                                    nanoDownloadResult = "Download complete."
                                                    vm.mutateSettingsAsync { it.copy(intelligenceMode = "gemini_nano") }
                                                }
                                                .onFailure { nanoDownloadResult = "Download failed: ${it.message}" }
                                            nanoDownloading = false
                                        }
                                    }
                                    .appPadding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(if (nanoDownloading) "Downloading…" else "Download Model", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (nanoDownloadResult.isNotBlank()) {
                                Text(
                                    nanoDownloadResult,
                                    color = if (nanoDownloadResult.startsWith("Download complete")) c.success else c.danger,
                                    fontSize = IronLogType.meta.fontSize.sp,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                        if (nanoAvailability == NanoAvailability.NEEDS_SYSTEM_UPDATE) {
                            Spacer(Modifier.height(appGapDp(6.dp)))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .heightIn(min = 48.dp)
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
                            .heightIn(min = 48.dp)
                            .semantics {
                                selected = cloudSelected
                                stateDescription = if (cloudSelected) "Selected" else "Not selected; opens Cloud AI setup"
                            }
                            .clickable(role = Role.Button) { cloudFormExpanded = !cloudFormExpanded }
                            .padding(12.dp),
                        verticalArrangement = appSpacedBy(4.dp),
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
                    Column(Modifier.fillMaxWidth().appPadding(top = 12.dp), verticalArrangement = appSpacedBy(12.dp)) {

                        // Provider preset chips
                        Text("Provider", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
                            horizontalArrangement = appSpacedBy(6.dp),
                        ) {
                            PROVIDER_PRESETS.forEach { preset ->
                                val sel = cloudPreset == preset.key
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                        .background(if (sel) c.accent.copy(alpha = 0.18f) else c.surface)
                                        .border(1.dp, if (sel) c.accent else c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                        .heightIn(min = 48.dp)
                                        .selectable(
                                            selected = sel,
                                            enabled = !cloudSaving,
                                            role = Role.RadioButton,
                                            onClick = {
                                                cloudFormDirty = true
                                                cloudApiKeyDirty = false
                                                cloudPreset = preset.key
                                                cloudBaseUrl = preset.baseUrl
                                                cloudModelName = preset.defaultModel
                                                cloudDisplayName = if (preset.key == "custom") "" else preset.displayName
                                                cloudApiKey = ""
                                                cloudModels = emptyList()
                                                cloudModelsError = ""
                                                cloudVerifyResult = ""
                                                cloudSaveError = null
                                            },
                                        )
                                        .appPadding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(preset.displayName, color = if (sel) c.accent else c.muted, fontSize = IronLogType.micro.fontSize.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }

                        // Base URL
                        OutlinedTextField(
                            value = cloudBaseUrl,
                            onValueChange = {
                                cloudFormDirty = true
                                cloudSaveError = null
                                cloudBaseUrl = it
                            },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            enabled = !cloudSaving,
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
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activePreset.keyUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                    }
                                    .padding(vertical = 2.dp),
                            )
                        }

                        // API Key (masked)
                        OutlinedTextField(
                            value = cloudApiKey,
                            onValueChange = {
                                cloudApiKeyDirty = true
                                cloudSaveError = null
                                cloudApiKey = it
                            },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-…") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (cloudApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { cloudApiKeyVisible = !cloudApiKeyVisible }, enabled = !cloudSaving) {
                                    Icon(
                                        if (cloudApiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (cloudApiKeyVisible) "Hide key" else "Show key",
                                        tint = c.muted,
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            enabled = !cloudSaving && !cloudKeyLoading,
                        )

                        // Load Models / Browse preset models row
                        val activePresetForModels = PROVIDER_PRESETS.find { it.key == cloudPreset }
                        val hasKnownModels = (activePresetForModels?.knownModels?.isNotEmpty()) == true
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = appSpacedBy(8.dp, Alignment.End),
                        ) {
                            if (hasKnownModels) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                        .background(c.surface)
                                        .border(1.dp, c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            cloudModels = activePresetForModels!!.knownModels
                                            cloudModelsError = ""
                                            cloudShowModelSheet = true
                                        }
                                        .appPadding(horizontal = 14.dp, vertical = 8.dp),
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
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = !cloudSaving && !cloudModelsLoading && cloudApiKey.isNotBlank() && cloudBaseUrl.isNotBlank()) {
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
                                    .appPadding(horizontal = 14.dp, vertical = 8.dp),
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
                            Text(
                                cloudModelsError,
                                color = c.danger,
                                fontSize = IronLogType.meta.fontSize.sp,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }

                        // Model field — clickable if any models are loaded (from API or preset)
                        val hasAnyModels = cloudModels.isNotEmpty()
                        OutlinedTextField(
                            value = cloudModelName,
                            onValueChange = {
                                cloudFormDirty = true
                                cloudSaveError = null
                                cloudModelName = it
                            },
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
                            enabled = !cloudSaving,
                        )

                        // Display name
                        OutlinedTextField(
                            value = cloudDisplayName,
                            onValueChange = {
                                cloudFormDirty = true
                                cloudSaveError = null
                                cloudDisplayName = it
                            },
                            label = { Text("Display name (optional)") },
                            placeholder = { Text("OpenAI") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            enabled = !cloudSaving,
                        )

                        // Verify + Save row
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = appSpacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (cloudVerifyResult.isNotBlank()) {
                                Text(
                                    cloudVerifyResult,
                                    color = if (cloudVerifyResult.startsWith("✓")) c.success else c.danger,
                                    fontSize = IronLogType.meta.fontSize.sp,
                                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.surface)
                                    .border(1.dp, c.faint, RoundedCornerShape(IronLogRadius.full.dp))
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = !cloudSaving && !cloudVerifying && cloudApiKey.isNotBlank() && cloudBaseUrl.isNotBlank() && cloudModelName.isNotBlank()) {
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
                                    .appPadding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(if (cloudVerifying) "Verifying…" else "Verify", color = c.text, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(IronLogRadius.full.dp))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .heightIn(min = 48.dp)
                                    .clickable(
                                        enabled = !cloudSaving && cloudApiKey.isNotBlank() && cloudBaseUrl.isNotBlank() && cloudModelName.isNotBlank(),
                                        onClick = ::saveCloudConfiguration,
                                    )
                                    .appPadding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(if (cloudSaving) "Saving…" else "Save", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        cloudSaveError?.let { message ->
                            Text(
                                message,
                                color = c.danger,
                                fontSize = IronLogType.meta.fontSize.sp,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
            }
        }

        // ── 2. APPEARANCE ──────────────────────────────────────────────────
        if (settingsDestination == SettingsDestination.APPEARANCE) item {
            SettingsSection("APPEARANCE") {
                // Track the live theme via ThemeRuntime so the active border
                // updates instantly on tap (settings.theme can lag the async write)
                // FIXED: 30 — Visual preview cards (120×72dp) replacing pill chips
                val activeThemeName by ThemeRuntime.themeName.collectAsStateWithLifecycle()
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = appSpacedBy(8.dp),
                    verticalArrangement = appSpacedBy(8.dp),
                ) {
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
                                .selectable(
                                    selected = active,
                                    role = Role.RadioButton,
                                    onClick = {
                                        vm.mutateSettingsAsync { it.copy(theme = t.id) }
                                        ThemeRuntime.setTheme(t.id)
                                    },
                                )
                                .padding(10.dp),
                            verticalArrangement = appSpacedBy(6.dp),
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
                                        .appPadding(4.dp)
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
                CardShineSettingsCard()
                LiquidGlassSettingsCard()
                TypographySettingsCard()
                SpacingSettingsCard()
                Spacer(Modifier.height(appGapDp(4.dp)))
            }
        }

        // ── 3. WORKOUT ─────────────────────────────────────────────────────
        if (settingsDestination == SettingsDestination.TRAINING) item {
            SettingsSection("WORKOUT") {
                // Weight unit
                Text("Weight unit", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    horizontalArrangement = appSpacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().appPadding(bottom = 4.dp).selectableGroup(),
                ) {
                    listOf("kg", "lbs").forEach { unit ->
                        val active = settings.weightUnit == unit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) c.accent.copy(alpha = 0.18f) else c.surface)
                                .border(1.5.dp, if (active) c.accent else c.faint, RoundedCornerShape(10.dp))
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = active,
                                    role = Role.RadioButton,
                                    onClick = {
                                        if (settings.hapticFeedback) HapticsEngine.lightConfirm(context)
                                        vm.mutateSettingsAsync { it.copy(weightUnit = unit) }
                                    },
                                )
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
                SectionValueRow(
                    label = "Effort tracking",
                    value = EFFORT_LABEL[settings.effortTracking] ?: "Off",
                    onPress = { cycleEffort(settings, vm) },
                )
                // Rest timers
                SectionValueRow("Rest — normal sets", "${settings.defaultRestSeconds}s", onPress = ::openNormalRestEditor)
                SectionValueRow("Rest — heavy sets", "${settings.defaultRestHeavySeconds}s", onPress = ::openHeavyRestEditor)
                SectionValueRow("Bar weight", "${settings.barWeightKg} kg", onPress = ::openBarWeightEditor)
                // Haptics
                SettingsSwitchRow("Haptic feedback", settings.hapticFeedback, borderBottom = true) {
                    HapticsEngine.toggle(context, it)
                    vm.mutateSettingsAsync { current -> current.copy(hapticFeedback = it) }
                }
                // Keep awake
                SettingsSwitchRow(
                    label = "Keep screen awake during workout",
                    checked = keepAwakeDuringWorkout,
                    borderBottom = false,
                    enabled = !keepAwakeSaving,
                ) {
                    if (settings.hapticFeedback) HapticsEngine.toggle(context, it)
                    saveKeepAwake(it)
                }
                keepAwakeSaveError?.let { message ->
                    Text(
                        message,
                        color = c.danger,
                        fontSize = IronLogType.meta.fontSize.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }

        // ── 4. TOOLS ───────────────────────────────────────────────────────
        if (settingsDestination == SettingsDestination.TRAINING) item {
            SettingsSection("TOOLS") {
                SectionNavRow("Exercise Library", onPress = onOpenExerciseLibrary, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Gym Profiles", onPress = onOpenGymProfiles, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Body Weight Tracker", onPress = onOpenBodyWeight, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("AI Plan Creator", onPress = onOpenAIPlan, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Program Picker", onPress = onOpenProgramPicker, borderBottom = false, hapticsEnabled = settings.hapticFeedback)
            }
        }

        // ── 5. REMINDERS ───────────────────────────────────────────────────
        if (settingsDestination == SettingsDestination.NOTIFICATIONS) item {
            SettingsSection("REMINDERS") {
                if (!notificationsAllowed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                            .appPadding(vertical = 13.dp)
                            .drawBottomBorder(c.faint),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Notifications are disabled — tap to open settings",
                            color = c.warning,
                            fontSize = IronLogType.meta.fontSize.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = c.warning)
                    }
                }
                SettingsSwitchRow("Smart notifications", notificationsEnabled, borderBottom = true) { enabled ->
                    if (settings.hapticFeedback) HapticsEngine.toggle(context, enabled)
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        notificationsEnabled = enabled
                        runReminderMutation(
                            target = NotificationMutationTarget.ENABLED,
                            successMessage = if (enabled) "Smart notifications enabled" else "Smart notifications disabled",
                            failureMessage = "Android could not update notification scheduling. Tap the switch to retry.",
                        ) { requestToken -> NotificationCoordinator.setEnabled(context, enabled, requestToken) }
                    }
                }
                SettingsSwitchRow("Workout reminders", workoutReminders, borderBottom = true) {
                    if (settings.hapticFeedback) HapticsEngine.toggle(context, it)
                    val requested = it
                    workoutReminders = requested
                    runReminderMutation(
                        target = NotificationMutationTarget.TRAINING_REMINDERS,
                        successMessage = "Workout reminder preference saved",
                        failureMessage = "Workout reminder preference could not be saved. Try again.",
                    ) { requestToken ->
                        NotificationCoordinator.setTrainingRemindersEnabled(context, requested, requestToken)
                    }
                }
                SettingsSwitchRow("Milestone celebrations", milestoneAlertsEnabled, borderBottom = true) { enabled ->
                    if (settings.hapticFeedback) HapticsEngine.toggle(context, enabled)
                    milestoneAlertsEnabled = enabled
                    runReminderMutation(
                        target = NotificationMutationTarget.MILESTONE_ALERTS,
                        successMessage = "Milestone preference saved",
                        failureMessage = "Milestone preference could not be saved. Try again.",
                    ) { requestToken ->
                        NotificationCoordinator.setMilestoneAlertsEnabled(enabled, requestToken)
                    }
                }
                // Daily reminder time
                // FIXED: 32 — Replaced uppercase micro label with readable copy
                Text("Daily reminder time", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                Row(horizontalArrangement = appSpacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = dailyReminderInput, onValueChange = { dailyReminderInput = it },
                        placeholder = { Text("08:00", color = c.muted) },
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
                                val minutes = parseQuietTimeInput(dailyReminderInput.trim())
                                if (minutes == null) {
                                    reminderStatus = "Enter a valid 24-hour time such as 08:00"
                                } else {
                                    runReminderMutation(
                                        target = NotificationMutationTarget.REMINDER_TIME,
                                        successMessage = "Reminder time saved",
                                        failureMessage = "Reminder time could not be saved or scheduled. Try again.",
                                    ) { requestToken ->
                                        check(NotificationCoordinator.updateReminderTime(context, minutes, requestToken))
                                    }
                                }
                            }
                            .appPadding(horizontal = 16.dp, vertical = 14.dp)
                    ) { Text("Apply", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp) }
                }
                // FIXED: 33 — Context link between reminder and quiet hours
                Text(
                    "A reminder inside quiet hours is held until quiet hours end.",
                    color = c.muted,
                    fontSize = IronLogType.micro.fontSize.sp,
                    modifier = Modifier.appPadding(top = 4.dp, bottom = 2.dp),
                )
                // Quiet hours
                Text("Quiet hours", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(horizontalArrangement = appSpacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
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
                                runReminderMutation(
                                    target = NotificationMutationTarget.QUIET_HOURS,
                                    successMessage = "Reminder settings saved",
                                    failureMessage = "Quiet hours could not be saved or scheduled. Try again.",
                                ) { requestToken ->
                                    check(NotificationCoordinator.updateQuietHours(context, s, e, requestToken))
                                }
                            } else {
                                reminderStatus = "Use 24-hour times such as 22:00 and 08:00"
                            }
                        }
                        .appPadding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Save Reminder Settings", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp) }
                if (reminderStatus.isNotBlank()) {
                    Text(
                        reminderStatus,
                        color = c.accent,
                        fontSize = IronLogType.meta.fontSize.sp,
                        modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                // Notification frequency profile
                Text("Notification frequency", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                Row(
                    horizontalArrangement = appSpacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().appPadding(bottom = 4.dp).selectableGroup(),
                ) {
                    listOf("conservative" to "Conservative", "balanced" to "Balanced", "aggressive" to "Aggressive").forEach { (id, label) ->
                        val active = notifProfileState == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) c.accent.copy(alpha = 0.18f) else c.surface)
                                .border(1.5.dp, if (active) c.accent else c.faint, RoundedCornerShape(10.dp))
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = active,
                                    role = Role.RadioButton,
                                    onClick = {
                                        if (settings.hapticFeedback) HapticsEngine.lightConfirm(context)
                                        notifProfileState = id
                                        runReminderMutation(
                                            target = NotificationMutationTarget.FREQUENCY_PROFILE,
                                            successMessage = "Notification frequency saved",
                                            failureMessage = "Notification frequency could not be saved. Try again.",
                                        ) { requestToken ->
                                            NotificationCoordinator.setFrequencyProfile(context, id, requestToken)
                                        }
                                    },
                                )
                                .appPadding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (active) c.accent else c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal)
                        }
                    }
                }
                SectionNavRow(
                    label = "Send test notification",
                    hapticsEnabled = settings.hapticFeedback,
                    onPress = {
                        if (!notificationsAllowed) {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } else {
                            reminderStatus = runCatching {
                                when (WorkoutNotificationBridge.showTestNotification(context)) {
                                    NotificationDeliveryResult.SENT -> "Test notification sent"
                                    NotificationDeliveryResult.CHANNEL_BLOCKED -> "The reminder channel is disabled in Android settings"
                                    NotificationDeliveryResult.PERMISSION_DENIED -> "Notification permission is off"
                                    else -> "Test notification could not be sent"
                                }
                            }.getOrDefault("Test notification could not be sent")
                        }
                    },
                )
                SectionNavRow(
                    label = "Android notification channels",
                    borderBottom = false,
                    hapticsEnabled = settings.hapticFeedback,
                    onPress = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                )
            }
        }

        // ── 6. DATA & BACKUP ───────────────────────────────────────────────
        if (settingsDestination == SettingsDestination.DATA_PRIVACY) item {
            // FIXED: 34 — Clear All History moved here from HistoryScreen
            SettingsSection("DATA & BACKUP") {
                SectionNavRow("Privacy & Data", onPress = onOpenPrivacy, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Import from Other Apps", onPress = onOpenImportCenter, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Backup & Export", onPress = onOpenDataPortability, hapticsEnabled = settings.hapticFeedback)
                SectionNavRow("Export history as CSV", onPress = {
                    scope.launch {
                        dataExportStatus = ""
                        try {
                            val uri = withContext(Dispatchers.IO) {
                                val csv = buildHistoryCsv(state.history)
                                val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
                                val file = File(exportDirectory, "ironlog_history_export.csv")
                                file.writeText(csv)
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "IronLog history export")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    "Share workout history",
                                ),
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Timber.e(failure, "Could not export workout history")
                            dataExportStatus = "History could not be exported. Try again."
                        }
                    }
                }, hapticsEnabled = settings.hapticFeedback)
                if (dataExportStatus.isNotBlank()) {
                    Text(
                        dataExportStatus,
                        color = c.danger,
                        fontSize = IronLogType.meta.fontSize.sp,
                        modifier = Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                SectionNavRow(if (historyMutationPending) "History change in progress…" else "Import history from CSV", onPress = {
                    csvPicker.launch(arrayOf("text/csv", "text/plain", "*/*"))
                }, borderBottom = false, enabled = !historyMutationPending, hapticsEnabled = settings.hapticFeedback)
            }
        }

        if (settingsDestination == SettingsDestination.DATA_PRIVACY) item {
            SettingsDangerZone(
                historyMutationPending = historyMutationPending,
                hapticsEnabled = settings.hapticFeedback,
                onClearHistory = { confirmClearAll = true },
                onResetPersonalRecords = { confirmResetPbs = true },
            )
        }

        // ── 7. ABOUT ───────────────────────────────────────────────────────
        if (settingsDestination == SettingsDestination.ABOUT) item {
            SettingsSection("ABOUT") {
                SectionRow(borderBottom = true) {
                    Text("Version", color = c.muted, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                    Text("IronLog $versionName", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                }
                SectionNavRow(
                    label = "Restart app tutorial",
                    onPress = { confirmResetOnboarding = true },
                    borderBottom = false,
                    hapticsEnabled = settings.hapticFeedback,
                )
            }
        }

        item { Spacer(Modifier.height(appGapDp(40.dp))) }
    }

    // FIXED: 34 — Clear All History confirmation dialog
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { if (!clearHistoryPending) confirmClearAll = false },
            containerColor = c.card,
            title = { Text("Clear All History?") },
            text = {
                Column(verticalArrangement = appSpacedBy(8.dp)) {
                    Text("This will permanently delete completed workout history. An active workout is preserved. This action cannot be undone.")
                    clearHistoryError?.let {
                        Text(it, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !clearHistoryPending && !historyMutationPending,
                    onClick = {
                        clearHistoryPending = true
                        clearHistoryError = null
                        application.launchAcceptedMutation {
                            try {
                                val outcome = commitHistoryMutation { vm.clearAllHistoryNow() }
                                withContext(Dispatchers.Main) {
                                    if (!outcome.mutationSucceeded) {
                                        Timber.w(outcome.mutationError, "Could not clear completed workout history")
                                        clearHistoryError = "History could not be cleared. Try again."
                                    } else {
                                        confirmClearAll = false
                                        if (!outcome.surfaces.fullyRefreshed) {
                                            dataMutationNotice = "History was cleared, but one or more derived app surfaces could not refresh. They will reconcile when the app next starts."
                                        }
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Timber.w(error, "Unexpected failure while clearing history")
                                withContext(Dispatchers.Main) {
                                    clearHistoryError = "History could not be cleared. Nothing was reported as deleted; try again."
                                }
                            } finally {
                                withContext(Dispatchers.Main) { clearHistoryPending = false }
                            }
                        }
                    },
                ) {
                    Text("Clear All", color = c.danger)
                }
            },
            dismissButton = {
                TextButton(enabled = !clearHistoryPending, onClick = { confirmClearAll = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmResetPbs) {
        AlertDialog(
            onDismissRequest = { if (!resetPbsPending) confirmResetPbs = false },
            containerColor = c.card,
            title = { Text("Reset all PRs?") },
            text = {
                Column(verticalArrangement = appSpacedBy(8.dp)) {
                    Text("This starts a new PR baseline now. Workout history stays intact, and future completed sets can establish new records.")
                    resetPbsError?.let {
                        Text(it, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !resetPbsPending && !historyMutationPending,
                    onClick = {
                        resetPbsPending = true
                        resetPbsError = null
                        application.launchAcceptedMutation {
                            try {
                                val outcome = commitHistoryMutation { vm.clearPbsNow() }
                                withContext(Dispatchers.Main) {
                                    if (!outcome.mutationSucceeded) {
                                        Timber.w(outcome.mutationError, "Could not reset PR baseline")
                                        resetPbsError = "The PR baseline could not be reset. Try again."
                                    } else {
                                        confirmResetPbs = false
                                        if (!outcome.surfaces.fullyRefreshed) {
                                            dataMutationNotice = "The PR baseline was reset, but one or more derived app surfaces could not refresh. They will reconcile when the app next starts."
                                        }
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Timber.w(error, "Unexpected failure while resetting PR baseline")
                                withContext(Dispatchers.Main) {
                                    resetPbsError = "The PR baseline could not be reset. Try again."
                                }
                            } finally {
                                withContext(Dispatchers.Main) { resetPbsPending = false }
                            }
                        }
                    },
                ) {
                    Text("Reset PRs", color = c.danger)
                }
            },
            dismissButton = {
                TextButton(enabled = !resetPbsPending, onClick = { confirmResetPbs = false }) { Text("Cancel") }
            },
        )
    }

    if (showNormalRestDialog) {
        AlertDialog(
            onDismissRequest = { if (!normalRestSaving) showNormalRestDialog = false },
            containerColor = c.card,
            title = { Text("Normal set rest (seconds)") },
            text = {
                Column(verticalArrangement = appSpacedBy(8.dp)) {
                    OutlinedTextField(
                        value = normalRestInput,
                        onValueChange = { v ->
                            if (v.all(Char::isDigit)) {
                                normalRestDirty = true
                                normalRestSaveError = null
                                normalRestInput = v
                            }
                        },
                        label = { Text("Seconds") },
                        singleLine = true,
                        enabled = !normalRestSaving,
                        isError = normalRestSaveError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    normalRestSaveError?.let { message ->
                        Text(message, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !normalRestSaving, onClick = ::saveNormalRest) {
                    Text(if (normalRestSaving) "Saving…" else "Save", color = c.accent)
                }
            },
            dismissButton = {
                TextButton(enabled = !normalRestSaving, onClick = { showNormalRestDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showHeavyRestDialog) {
        AlertDialog(
            onDismissRequest = { if (!heavyRestSaving) showHeavyRestDialog = false },
            containerColor = c.card,
            title = { Text("Heavy set rest (seconds)") },
            text = {
                Column(verticalArrangement = appSpacedBy(8.dp)) {
                    OutlinedTextField(
                        value = heavyRestInput,
                        onValueChange = { v ->
                            if (v.all(Char::isDigit)) {
                                heavyRestDirty = true
                                heavyRestSaveError = null
                                heavyRestInput = v
                            }
                        },
                        label = { Text("Seconds") },
                        singleLine = true,
                        enabled = !heavyRestSaving,
                        isError = heavyRestSaveError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    heavyRestSaveError?.let { message ->
                        Text(message, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !heavyRestSaving, onClick = ::saveHeavyRest) {
                    Text(if (heavyRestSaving) "Saving…" else "Save", color = c.accent)
                }
            },
            dismissButton = {
                TextButton(enabled = !heavyRestSaving, onClick = { showHeavyRestDialog = false }) { Text("Cancel") }
            },
        )
    }
    if (showBarWeightDialog) {
        AlertDialog(
            onDismissRequest = { if (!barWeightSaving) showBarWeightDialog = false },
            containerColor = c.card,
            title = { Text("Bar weight (kg)") },
            text = {
                Column(verticalArrangement = appSpacedBy(8.dp)) {
                    OutlinedTextField(
                        value = barWeightInput,
                        onValueChange = {
                            barWeightDirty = true
                            barWeightSaveError = null
                            barWeightInput = it
                        },
                        label = { Text("Kilograms") },
                        singleLine = true,
                        enabled = !barWeightSaving,
                        isError = barWeightSaveError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    barWeightSaveError?.let { message ->
                        Text(message, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !barWeightSaving, onClick = ::saveBarWeight) {
                    Text(if (barWeightSaving) "Saving…" else "Save", color = c.accent)
                }
            },
            dismissButton = {
                TextButton(enabled = !barWeightSaving, onClick = { showBarWeightDialog = false }) { Text("Cancel") }
            },
        )
    }
    if (showCsvImportConfirm) {
        val pv = csvImportPreview
        AlertDialog(
            onDismissRequest = { showCsvImportConfirm = false },
            containerColor = c.card,
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
                    val payload = csvImportText
                    application.launchAcceptedMutation {
                        val outcome = commitHistoryMutation {
                            importText(
                                text = payload,
                                repo = workoutRepo,
                                importExportRepo = importExportRepo,
                                preview = preview,
                                sourceHint = "strong_csv",
                                replaceMode = false,
                            )
                        }
                        withContext(Dispatchers.Main) {
                            csvImportStatus = if (outcome.mutationSucceeded) {
                                "Imported ${outcome.value ?: 0} workout session(s) from CSV." +
                                    if (outcome.surfaces.fullyRefreshed) ""
                                    else " Some derived app surfaces will retry when the app next starts."
                            } else {
                                "CSV import stopped: ${outcome.mutationError?.message ?: "unknown error"}. " +
                                    "Any rows committed before the error were reconciled; review History before retrying."
                            }
                            if (csvImportText == payload && csvImportPreview == preview) {
                                csvImportText = ""
                                csvImportPreview = null
                            }
                        }
                    }
                }, enabled = !historyMutationPending) { Text("Import", color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCsvImportConfirm = false }) { Text("Cancel") }
            },
        )
    }
    if (csvImportStatus.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { csvImportStatus = "" },
            containerColor = c.card,
            title = { Text("CSV Import") },
            text = { Text(csvImportStatus) },
            confirmButton = { TextButton(onClick = { csvImportStatus = "" }) { Text("OK") } },
        )
    }
    dataMutationNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { dataMutationNotice = null },
            containerColor = c.card,
            title = { Text("Data updated") },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { dataMutationNotice = null }) { Text("OK") } },
        )
    }

    if (confirmResetOnboarding) {
        AlertDialog(
            onDismissRequest = { confirmResetOnboarding = false },
            containerColor = c.card,
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
        val initHour = parsed?.div(60) ?: 8
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
                    runReminderMutation(
                        target = NotificationMutationTarget.REMINDER_TIME,
                        successMessage = "Reminder time saved",
                        failureMessage = "Reminder time could not be saved or scheduled. Try again.",
                    ) { requestToken ->
                        @OptIn(ExperimentalMaterial3Api::class)
                        check(NotificationCoordinator.updateReminderTime(
                            context,
                            tpState.hour * 60 + tpState.minute,
                            requestToken,
                        ))
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
            containerColor = c.card,
            scrimColor = Color.Black.copy(alpha = 0.72f),
        ) {
            // Fixed-height column so the sheet is always fully expanded — prevents the
            // list's overscroll from bubbling into the sheet's drag-to-dismiss gesture.
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = appSpacedBy(8.dp),
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
                LazyColumn(Modifier.fillMaxWidth().weight(1f).selectableGroup()) {
                    items(filtered.size) { idx ->
                        val model = filtered[idx]
                        Text(
                            model,
                            color = if (model == cloudModelName) c.accent else c.text,
                            fontSize = IronLogType.body.fontSize.sp,
                            fontWeight = if (model == cloudModelName) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = model == cloudModelName,
                                    role = Role.RadioButton,
                                    onClick = {
                                        cloudFormDirty = true
                                        cloudSaveError = null
                                        cloudModelName = model
                                        cloudShowModelSheet = false
                                        cloudModelSearch = ""
                                    },
                                )
                                .appPadding(vertical = 10.dp, horizontal = 4.dp),
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
private fun SettingsConsoleLanding(
    query: String,
    onQueryChange: (String) -> Unit,
    initialized: Boolean,
    statuses: Map<SettingsDestination, String>,
    onOpen: (SettingsDestination) -> Unit,
) {
    val c = useTheme()
    val destinations = remember(query) { filterSettingsConsoleDestinations(query) }
    Column(verticalArrangement = appSpacedBy(16.dp)) {
        Column(Modifier.appPadding(start = 4.dp, end = 4.dp, bottom = 2.dp)) {
            Text(
                "SETTINGS",
                color = c.muted,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
            Text(
                "Training Console",
                color = c.text,
                fontWeight = FontWeight(IronLogType.metric.fontWeight),
                fontSize = IronLogType.metric.fontSize.sp,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Your training setup, integrations and local data in six focused areas.",
                color = c.subtext,
                fontSize = IronLogType.body.fontSize.sp,
                lineHeight = IronLogType.body.lineHeight.sp,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                .background(c.surface)
                .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
                .appPadding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = appSpacedBy(2.dp)) {
                Text("LOCAL TRAINING RECORD", color = c.muted, fontSize = IronLogType.micro.fontSize.sp, letterSpacing = 1.2.sp)
                Text(
                    if (initialized) "Ready on this device" else "Loading local settings…",
                    color = c.text,
                    fontSize = IronLogType.body.fontSize.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("6 AREAS", color = c.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search settings") },
            placeholder = { Text("Try rest timer, cloud AI, backup…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        SettingsSection(if (query.isBlank()) "DESTINATIONS" else "SEARCH RESULTS") {
            if (destinations.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().appPadding(vertical = 12.dp),
                    verticalArrangement = appSpacedBy(4.dp),
                ) {
                    Text("No matching setting", color = c.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
                    Text("Try a feature name such as rest, theme, reminder or backup.", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                }
            } else {
                destinations.forEachIndexed { index, spec ->
                    SettingsConsoleDestinationRow(
                        spec = spec,
                        status = statuses[spec.destination].orEmpty(),
                        borderBottom = index != destinations.lastIndex,
                        onPress = { onOpen(spec.destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsConsoleDestinationRow(
    spec: SettingsDestinationSpec,
    status: String,
    borderBottom: Boolean,
    onPress: () -> Unit,
) {
    val c = useTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onPress)
            .appPadding(vertical = 11.dp)
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier),
        horizontalArrangement = appSpacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = appSpacedBy(2.dp)) {
            Text(spec.title, color = c.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
            Text(
                spec.description,
                color = c.subtext,
                fontSize = IronLogType.meta.fontSize.sp,
                lineHeight = IronLogType.meta.lineHeight.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (status.isNotBlank()) {
                Text(
                    status,
                    color = when {
                        status.startsWith("Blocked") -> c.danger
                        status.contains(" off", ignoreCase = true) -> c.muted
                        else -> c.accent
                    },
                    fontSize = IronLogType.micro.fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.muted)
    }
}

@Composable
private fun SettingsDetailHeader(
    destination: SettingsDestination,
    onBack: () -> Unit,
) {
    val spec = settingsConsoleDestinations.firstOrNull { it.destination == destination } ?: return
    ScreenHeader(title = spec.title, subtitle = spec.description, onBack = onBack)
}

@Composable
private fun SettingsDangerZone(
    historyMutationPending: Boolean,
    hapticsEnabled: Boolean,
    onClearHistory: () -> Unit,
    onResetPersonalRecords: () -> Unit,
) {
    val c = useTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.danger.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(IronLogRadius.xl.dp),
    ) {
        Column(Modifier.fillMaxWidth().appPadding(16.dp)) {
            Text(
                "DANGER AREA",
                color = c.danger,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.4.sp,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "These changes permanently alter your training record. Back up first if you may need to restore it.",
                color = c.subtext,
                fontSize = IronLogType.meta.fontSize.sp,
                lineHeight = IronLogType.meta.lineHeight.sp,
                modifier = Modifier.appPadding(top = 6.dp, bottom = 8.dp),
            )
            DangerActionRow(
                title = "Clear completed history",
                consequence = "Deletes completed workouts; the active workout is preserved.",
                enabled = !historyMutationPending,
                borderBottom = true,
                hapticsEnabled = hapticsEnabled,
                onPress = onClearHistory,
            )
            DangerActionRow(
                title = "Reset all personal records",
                consequence = "Starts new PR baselines without deleting workout history.",
                enabled = !historyMutationPending,
                borderBottom = false,
                hapticsEnabled = hapticsEnabled,
                onPress = onResetPersonalRecords,
            )
        }
    }
}

@Composable
private fun DangerActionRow(
    title: String,
    consequence: String,
    enabled: Boolean,
    borderBottom: Boolean,
    hapticsEnabled: Boolean,
    onPress: () -> Unit,
) {
    val c = useTheme()
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button) {
                if (hapticsEnabled) HapticsEngine.lightConfirm(context)
                onPress()
            }
            .appPadding(vertical = 11.dp)
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier),
        horizontalArrangement = appSpacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = appSpacedBy(2.dp)) {
            Text(title, color = if (enabled) c.danger else c.muted, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Bold)
            Text(consequence, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp, lineHeight = IronLogType.meta.lineHeight.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = if (enabled) c.danger else c.faint)
    }
}

internal fun intelligenceModeLabel(mode: String): String = when (mode) {
    "gemini_nano" -> "APEX on-device"
    "cloud_ai" -> "Cloud AI"
    else -> "Built-in intelligence"
}

internal fun notificationConsoleStatus(
    systemAllowed: Boolean,
    enabled: Boolean,
    workoutReminders: Boolean,
): String = when {
    !systemAllowed -> "Blocked by Android"
    !enabled -> "Smart notifications off"
    workoutReminders -> "On · workout reminders enabled"
    else -> "On · workout reminders off"
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = useTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.cardBorder),
        shape = RoundedCornerShape(IronLogRadius.xl.dp),
    ) {
        Column(Modifier.fillMaxWidth().appPadding(16.dp)) {
            Text(
                title,
                color = c.muted,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
            )
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
            .heightIn(min = 48.dp)
            .then(if (borderBottom) Modifier.border(BorderStroke(0.dp, Color.Transparent)) else Modifier)
            .appPadding(vertical = 13.dp)
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SectionValueRow(
    label: String,
    value: String,
    onPress: () -> Unit,
    borderBottom: Boolean = true,
) {
    val c = useTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onPress)
            .appPadding(vertical = 13.dp)
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    borderBottom: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = useTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .appPadding(vertical = 13.dp)
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
        IronLogSwitch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
private fun SectionNavRow(
    label: String,
    onPress: () -> Unit,
    borderBottom: Boolean = true,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
) {
    val c = useTheme()
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled) {
                if (hapticsEnabled) HapticsEngine.lightConfirm(context)
                onPress()
            }
            .padding(vertical = 13.dp)
            .then(if (borderBottom) Modifier.drawBottomBorder(c.faint) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (enabled) c.text else c.muted, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = if (enabled) c.muted else c.faint)
    }
}

// A single 48dp target keeps the visual control compact while meeting Android touch guidance.
@Composable
private fun StepperBtn(label: String, onClick: () -> Unit) {
    val c = useTheme()
    Box(
        modifier = Modifier
            .size(48.dp)
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
    FlowChipRow(modifier = Modifier.selectableGroup()) {
        options.forEach { (id, label) ->
            val active = selected == id
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    // Use a direct accent fill + solid accent ring so the selected state is
                    // always visible regardless of theme (accentSoft is too faint on some themes).
                    .background(if (active) c.accent.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.5.dp, if (active) c.accent else c.faint, CircleShape)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = active,
                        role = Role.RadioButton,
                        onClick = {
                            if (hapticsEnabled) HapticsEngine.selection(context)
                            onSelect(id)
                        },
                    )
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
fun FlowChipRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = appSpacedBy(8.dp),
        verticalArrangement = appSpacedBy(8.dp),
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
    vm.mutateSettingsAsync { it.copy(effortTracking = next) }
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


