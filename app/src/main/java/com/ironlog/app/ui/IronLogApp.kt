package com.ironlog.app.ui

import com.ironlog.app.ui.theme.appPadding

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.ironlog.app.ui.theme.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.navigation.AppNavigator
import com.ironlog.app.qa.DebugFixtureBootstrap
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.context.ThemeRuntime
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.theme.ObsidianShapes
import com.ironlog.app.ui.theme.LocalTypographySelection
import com.ironlog.app.ui.theme.TypographyRuntime
import com.ironlog.app.ui.theme.buildIronLogTypography
import com.ironlog.app.ui.theme.LocalSpacingScale
import com.ironlog.app.ui.theme.SpacingRuntime
import com.ironlog.app.ui.theme.SpacingRole
import com.ironlog.app.ui.theme.LocalCardSpacingScale
import com.ironlog.app.ui.theme.LocalPaddingSpacingScale
import com.ironlog.app.ui.theme.LocalContentSpacingScale
import com.ironlog.app.ui.theme.CardShineRuntime
import com.ironlog.app.ui.theme.LocalCardShineEnabled
import com.ironlog.app.ui.theme.LiquidGlassRuntime
import com.ironlog.app.ui.theme.LocalLiquidGlassEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BootstrapUiState {
    data object Booting : BootstrapUiState
    data class StartupError(val throwable: Throwable) : BootstrapUiState
    data object WaitingForSplash : BootstrapUiState
    data object Ready : BootstrapUiState
}

class IronLogAppViewModel(application: Application) : AndroidViewModel(application) {
    private val exerciseRepository = ExerciseRepository(application.applicationContext)
    private val _state = MutableStateFlow<BootstrapUiState>(BootstrapUiState.Booting)
    val state: StateFlow<BootstrapUiState> = _state.asStateFlow()
    private var bootAttempt = 0
    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    init { bootstrap() }

    fun retry() {
        bootAttempt += 1
        bootstrap()
    }

    fun splashFinished() {
        if (_state.value is BootstrapUiState.WaitingForSplash) _state.value = BootstrapUiState.Ready
    }

    private fun bootstrap() {
        _state.value = BootstrapUiState.Booting
        viewModelScope.launch {
            try {
                DebugFixtureBootstrap.importIfPristine(getApplication())
                exerciseRepository.seedExercisesIfNeeded()
                exerciseRepository.backfillExerciseMusclesIfNeeded()
                val settingsRepository = SettingsRepository()
                val raw = settingsRepository.getString("ironlog_settings")
                val json = runCatching { org.json.JSONObject(raw ?: "{}") }.getOrDefault(org.json.JSONObject())
                ThemeRuntime.setTheme(json.optString("theme", com.ironlog.app.ui.theme.IronLogThemes.DEFAULT_THEME))
                _onboardingComplete.value = settingsRepository.getBoolean("onboarding_complete", false)
                _state.value = BootstrapUiState.WaitingForSplash
                // Safety net: if the splash composable is removed before onFinish fires
                // (e.g., configuration change without Activity recreation), force Ready
                // after 5 seconds so the app never stays stuck on the splash indefinitely.
                kotlinx.coroutines.delay(5_000)
                if (_state.value is BootstrapUiState.WaitingForSplash) {
                    _state.value = BootstrapUiState.Ready
                }
            } catch (t: Throwable) {
                _state.value = BootstrapUiState.StartupError(t)
            }
        }
    }
}

@Composable
fun IronLogApp(viewModel: IronLogAppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val activeTheme by ThemeRuntime.themeName.collectAsState()
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()
    val context = LocalContext.current
    // Synchronous reads of small appearance preferences happen before any visible screen.
    val typographyStore = remember(context.applicationContext) { TypographyRuntime.store(context) }
    val typographySelection by typographyStore.selection.collectAsState()
    val typography = remember(typographySelection) { buildIronLogTypography(typographySelection) }
    val spacingStore = remember(context.applicationContext) { SpacingRuntime.store(context) }
    val spacingScale by spacingStore.scale.collectAsState()
    val cardSpacing by remember { SpacingRuntime.store(context, SpacingRole.CARDS) }.scale.collectAsState()
    val paddingSpacing by remember { SpacingRuntime.store(context, SpacingRole.PADDING) }.scale.collectAsState()
    val contentSpacing by remember { SpacingRuntime.store(context, SpacingRole.CONTENT) }.scale.collectAsState()
    val cardShineEnabled by remember { CardShineRuntime.store(context) }.enabled.collectAsState()
    val liquidGlassEnabled by remember { LiquidGlassRuntime.store(context) }.enabled.collectAsState()
    // Block AppNavigator from rendering until the onboarding flag has been read.
    // NavHost.startDestination is only consumed once — if we render before the flag
    // is loaded we'll always route already-onboarded users to Onboarding.

    ThemeProvider(themeName = activeTheme) {
        val colors = useTheme()
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography,
            shapes = ObsidianShapes,
        ) {
            CompositionLocalProvider(
                LocalTypographySelection provides typographySelection,
                LocalSpacingScale provides spacingScale,
                LocalCardSpacingScale provides cardSpacing,
                LocalPaddingSpacingScale provides paddingSpacing,
                LocalContentSpacingScale provides contentSpacing,
                LocalCardShineEnabled provides cardShineEnabled,
                LocalLiquidGlassEnabled provides liquidGlassEnabled,
            ) {
                when (val s = state) {
                    BootstrapUiState.Booting -> BootPlaceholder()
                    is BootstrapUiState.StartupError -> StartupErrorScreen(s.throwable, onRetry = viewModel::retry)
                    BootstrapUiState.WaitingForSplash -> com.ironlog.app.ui.screens.SplashScreen(onFinish = viewModel::splashFinished)
                    BootstrapUiState.Ready -> AppNavigator(onboardingComplete = onboardingComplete)
                }
            }
        }
    }
}

internal fun IronLogThemeTokens.toMaterialColorScheme() = ColorScheme(
    primary = accent,
    onPrimary = textOnAccent,
    primaryContainer = accentSoft,
    onPrimaryContainer = text,
    inversePrimary = accent,
    secondary = chartSecondary,
    onSecondary = textOnAccent,
    secondaryContainer = surface,
    onSecondaryContainer = text,
    tertiary = chartTertiary,
    onTertiary = textOnAccent,
    tertiaryContainer = surface,
    onTertiaryContainer = text,
    background = bg,
    onBackground = text,
    surface = card,
    onSurface = text,
    surfaceVariant = surface,
    onSurfaceVariant = subtext,
    surfaceTint = Color.Transparent,
    inverseSurface = text,
    inverseOnSurface = bg,
    error = danger,
    onError = onDanger,
    errorContainer = danger,
    onErrorContainer = onDanger,
    outline = cardBorder,
    outlineVariant = cardBorder,
    scrim = Color.Black,
    surfaceBright = faint,
    surfaceDim = bg,
    surfaceContainer = card,
    surfaceContainerHigh = surface,
    surfaceContainerHighest = faint,
    surfaceContainerLow = card,
    surfaceContainerLowest = bg,
)

@Composable
private fun BootPlaceholder() {
    val c = useTheme()
    Box(Modifier.fillMaxSize().background(c.bg))
}

@Composable
private fun StartupErrorScreen(error: Throwable, onRetry: () -> Unit) {
    val c = useTheme()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .appPadding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ironlog", color = c.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineLarge)
        Text("Startup failed", color = c.danger, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        Text(
            text = error.message ?: "Something went wrong while loading your data.",
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = c.accent)) {
            Text("TRY AGAIN", color = c.textOnAccent, fontWeight = FontWeight.ExtraBold)
        }
    }
}
