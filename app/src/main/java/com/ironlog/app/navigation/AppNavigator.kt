package com.ironlog.app.navigation

import android.net.Uri
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity_
import com.ironlog.app.data.repository.BodyMeasurementRepository
import com.ironlog.app.data.repository.persistOnboardingBaselineAtomically
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.screens.workout.ActiveWorkoutScreen
import com.ironlog.app.ui.screens.plans.AIPlanScreen
import com.ironlog.app.ui.screens.settings.BackupCenterScreen
import com.ironlog.app.ui.screens.body.BodyMeasurementsScreen
import com.ironlog.app.ui.screens.body.BodyWeightScreen
import com.ironlog.app.ui.screens.settings.CreateExerciseScreen
import com.ironlog.app.ui.screens.settings.DataPortabilityScreen
import com.ironlog.app.ui.screens.settings.ExerciseLibraryScreen
import com.ironlog.app.ui.screens.stats.ExerciseProgressScreen
import com.ironlog.app.ui.screens.settings.GymProfileEditorScreen
import com.ironlog.app.ui.screens.settings.GymProfilesScreen
import com.ironlog.app.ui.screens.stats.HistoryScreen
import com.ironlog.app.ui.screens.home.HomeScreen
import com.ironlog.app.ui.screens.settings.ImportCenterScreen
import com.ironlog.app.ui.screens.recovery.RecoveryCircuitSheet
import com.ironlog.app.ui.screens.gamification.StatusWindowScreen
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.screens.OnboardingScreen
import com.ironlog.app.ui.screens.plans.PlanEditorScreen
import com.ironlog.app.ui.screens.plans.PlansScreen
import com.ironlog.app.ui.screens.settings.PrivacyScreen
import com.ironlog.app.ui.screens.plans.ProgramInsightsScreen
import com.ironlog.app.ui.screens.plans.ProgramPickerScreen
import com.ironlog.app.ui.screens.body.ProgressPhotosScreen
import com.ironlog.app.ui.screens.recovery.RecoveryMapScreen
import com.ironlog.app.ui.screens.settings.RestoreDataScreen
import com.ironlog.app.ui.screens.settings.SettingsScreen
import com.ironlog.app.ui.screens.stats.StatsScreen
import com.ironlog.app.ui.screens.intelligence.TrainingIntelligenceScreen
import com.ironlog.app.ui.screens.stats.VolumeAnalyticsScreen
import com.ironlog.app.ui.screens.workout.WorkoutCalendarScreen
import android.app.Application
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import com.ironlog.app.domain.intelligence.TrainingDayPreferences
import com.ironlog.app.domain.intelligence.canonicalIntelligenceMode
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.GamificationViewModel
import com.ironlog.app.ui.viewmodel.GamificationViewModelFactory
import com.ironlog.app.ui.viewmodel.BodyMeasurementsViewModel
import com.ironlog.app.ui.viewmodel.BodyMeasurementsViewModelFactory
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.ui.viewmodel.PlansViewModel
import com.ironlog.app.ui.viewmodel.StatsViewModel
import com.ironlog.app.widget.WidgetUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.ironlog.app.ui.state.commitWorkoutTerminalMutation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

internal val TabScreens = listOf(
    TabSpec("Home",     Icons.Outlined.Home,                        null),
    TabSpec("Plans",    Icons.Outlined.FitnessCenter,               "PLANS"),
    TabSpec("Log",      Icons.AutoMirrored.Outlined.Assignment,     "LOG"),
    TabSpec("Stats",    Icons.AutoMirrored.Outlined.ShowChart,      "STATS"),
    TabSpec("Settings", Icons.Outlined.Settings,                    "SETTINGS"),
)

private val StackScreens = listOf(
    StackSpec("ActiveWorkout",                     "ACTIVE WORKOUT", transparentModal = true, gesturesEnabled = false),
    StackSpec("PlanEditor",                        "EDIT PLAN"),
    StackSpec("ExerciseLibrary",                   "EXERCISE LIBRARY"),
    StackSpec("BodyWeight",                        "BODY WEIGHT"),
    StackSpec("CreateExercise",                    "CREATE EXERCISE"),
    StackSpec("ProgressPhotos",                    "PROGRESS PHOTOS"),
    StackSpec("ProgramPicker",                     "PROGRAMS"),
    StackSpec("ExerciseProgress/{exerciseName}",   "PROGRESS"),
    StackSpec("WorkoutCalendar",                   "CALENDAR"),
    StackSpec("VolumeAnalytics",                   "VOLUME ANALYTICS"),
    StackSpec("RecoveryMap",                       "MUSCLE RECOVERY"),
    StackSpec("ProgramInsights",                   "PROGRAM INSIGHTS"),
    StackSpec("BodyMeasurements",                  "BODY TRACKER"),
    StackSpec("GymProfiles",                       "GYM PROFILES"),
    StackSpec("GymProfileEditor",                  "EDIT PROFILE"),
    StackSpec("BackupCenter",                      "BACKUP CENTER"),
    StackSpec("Privacy",                           "PRIVACY"),
    StackSpec("RestoreData",                       "RESTORE DATA"),
    StackSpec("ImportCenter",                      "IMPORT CENTER"),
    StackSpec("AIPlan",                            "AI PLAN CREATOR"),
    StackSpec("TrainingIntelligence",              "ATHLETE PROFILE"),
    StackSpec("DataPortability",                   "BACKUP & EXPORT"),
)

data class TabSpec(val name: String, val icon: ImageVector, val title: String?)
data class StackSpec(val route: String, val title: String, val transparentModal: Boolean = false, val gesturesEnabled: Boolean = true)

/**
 * Compose Navigation translation of AppNavigator.js.
 * React Navigation's stack + PagerView tab setup becomes NavHost + HorizontalPager.
 */
@Composable
fun AppNavigator(
    navController: NavHostController = rememberNavController(),
    onboardingComplete: Boolean = true,
) {
    val colors = useTheme()
    // Owned by the NavHost, so navigation work survives disposal of a nested destination.
    val navigationScope = rememberCoroutineScope()
    val bodyVm: BodyMeasurementsViewModel =
        viewModel(factory = BodyMeasurementsViewModelFactory(BodyMeasurementRepository()))
    val statsVm: StatsViewModel = viewModel()
    val rootSettingsRepo = remember { SettingsRepository() }
    val pendingRouteFlow = remember(rootSettingsRepo) {
        rootSettingsRepo.observeStrings(setOf("pending_nav_route"))
    }
    val pendingRouteValues by pendingRouteFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val pendingRoute = pendingRouteValues["pending_nav_route"].orEmpty()
    var pendingTabRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingRoute, onboardingComplete) {
        if (pendingRoute.isBlank() || !onboardingComplete) {
            if (pendingRoute.isBlank()) pendingTabRoute = null
            return@LaunchedEffect
        }
        if (pendingTabIndex(pendingRoute) != null) {
            val tabsVisible = navController.currentDestination?.route == "Tabs" ||
                navController.popBackStack("Tabs", inclusive = false)
            if (tabsVisible) pendingTabRoute = pendingRoute
            return@LaunchedEffect
        }
        if (!isAllowedPendingStackRoute(pendingRoute)) {
            // Exported activities must not pass arbitrary route strings into Navigation Compose.
            rootSettingsRepo.consumeString("pending_nav_route", pendingRoute)
            return@LaunchedEffect
        }
        navController.navigate(pendingRoute) { launchSingleTop = true }
        rootSettingsRepo.consumeString("pending_nav_route", pendingRoute)
    }
    NavHost(
        navController    = navController,
        startDestination = if (onboardingComplete) "Tabs" else "Onboarding",
        modifier         = Modifier.fillMaxSize().background(colors.bg),
    ) {
        composable("Onboarding") {
            val context = LocalContext.current
            OnboardingScreen(
                onComplete = { draft ->
                    settingsRepoSaveOnboardingDataFull(context, draft)
                    navController.navigate("Tabs") {
                        popUpTo("Onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("Tabs") {
            Tabs(
                navController = navController,
                pendingTabRoute = pendingTabRoute,
                onPendingTabHandled = { handled ->
                    navigationScope.launch {
                        rootSettingsRepo.consumeString("pending_nav_route", handled)
                    }
                },
            )
        }
        // Active workout with optional dayId arg
        composable("ActiveWorkout/{dayId}") { backStack ->
            val app = LocalContext.current.applicationContext as Application
            val finishScope = rememberCoroutineScope()
            val tabsEntry = remember(navController) {
                runCatching { navController.getBackStackEntry("Tabs") }.getOrNull()
            }
            val tabsAppVm: AppDataViewModel? = tabsEntry?.let { viewModel(it) }
            val tabsStatsVm: StatsViewModel? = tabsEntry?.let { viewModel(it) }
            val tabsGamificationVm: GamificationViewModel? = tabsEntry?.let {
                viewModel(viewModelStoreOwner = it, factory = GamificationViewModelFactory(app, ObjectBox.store))
            }
            ActiveWorkoutScreen(
                dayId     = Uri.decode(backStack.arguments?.getString("dayId").orEmpty()),
                onFinish  = {
                    finishScope.launch {
                        tabsAppVm?.refresh()?.join()
                        tabsStatsVm?.refresh()?.join()
                        if (tabsAppVm != null && tabsStatsVm != null && tabsGamificationVm != null) {
                            tabsGamificationVm.refreshFromHistory(
                                tabsStatsVm.state.value.history,
                                tabsAppVm.state.value.settings.weeklyGoalDays,
                            )
                            WidgetUpdateWorker.enqueueOneTime(app)
                        }
                        navController.popBackStack()
                    }
                },
                onMinimize = { navController.popBackStack() },
            )
        }
        composable(
            route = "ActiveWorkout?startEmpty={startEmpty}",
            arguments = listOf(
                navArgument("startEmpty") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStack ->
            // Pass empty dayId — the ViewModel reads active_workout_day_id from SettingsRepository
            // during initWorkout and resumes the persisted workout. The old LaunchedEffect approach
            // caused a render-before-read race where initWorkout(dayId="") was called first,
            // which the VM handles correctly via persistedActiveId lookup.
            val app = LocalContext.current.applicationContext as Application
            val finishScope = rememberCoroutineScope()
            val tabsEntry = remember(navController) {
                runCatching { navController.getBackStackEntry("Tabs") }.getOrNull()
            }
            val tabsAppVm: AppDataViewModel? = tabsEntry?.let { viewModel(it) }
            val tabsStatsVm: StatsViewModel? = tabsEntry?.let { viewModel(it) }
            val tabsGamificationVm: GamificationViewModel? = tabsEntry?.let {
                viewModel(viewModelStoreOwner = it, factory = GamificationViewModelFactory(app, ObjectBox.store))
            }
            ActiveWorkoutScreen(
                dayId     = "",
                startEmpty = backStack.arguments?.getBoolean("startEmpty") == true,
                onFinish  = {
                    finishScope.launch {
                        tabsAppVm?.refresh()?.join()
                        tabsStatsVm?.refresh()?.join()
                        if (tabsAppVm != null && tabsStatsVm != null && tabsGamificationVm != null) {
                            tabsGamificationVm.refreshFromHistory(
                                tabsStatsVm.state.value.history,
                                tabsAppVm.state.value.settings.weeklyGoalDays,
                            )
                            WidgetUpdateWorker.enqueueOneTime(app)
                        }
                        navController.popBackStack()
                    }
                },
                onMinimize = { navController.popBackStack() },
            )
        }
        composable("PlanEditor/{planId}") { backStack ->
            PlanEditorScreen(
                planId         = Uri.decode(backStack.arguments?.getString("planId").orEmpty()),
                onBack         = { navController.popBackStack() },
                onStartWorkout = { dayId ->
                    navController.navigate(if (dayId.isNotBlank()) "ActiveWorkout/${Uri.encode(dayId)}" else "ActiveWorkout")
                },
            )
        }
        composable("PlanEditor") {
            PlanEditorScreen(
                planId         = "",
                onBack         = { navController.popBackStack() },
                onStartWorkout = { dayId ->
                    navController.navigate(if (dayId.isNotBlank()) "ActiveWorkout/${Uri.encode(dayId)}" else "ActiveWorkout")
                },
            )
        }
        composable("ExerciseLibrary") {
val statsState by statsVm.state.collectAsStateWithLifecycle()
            ExerciseLibraryScreen(
                history                = statsState.history,
                onBack                 = { navController.popBackStack() },
                onCreateExercise       = { seed -> navController.navigate("CreateExercise/${Uri.encode(seed)}") },
                onExerciseClick        = { ex  -> navController.navigate("ExerciseProgress/${Uri.encode(ex.name)}") },
                onOpenExerciseProgress = { name -> navController.navigate("ExerciseProgress/${Uri.encode(name)}") },
            )
        }
        composable("CreateExercise/{seedName}") { backStack ->
            CreateExerciseScreen(
                initialName = Uri.decode(backStack.arguments?.getString("seedName").orEmpty()),
                onSaved     = { navController.popBackStack() },
                onBack      = { navController.popBackStack() },
            )
        }
        composable("CreateExercise") {
            CreateExerciseScreen(
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() },
            )
        }
        composable("Privacy") { PrivacyScreen(onBack = { navController.popBackStack() }) }
        composable("ExerciseProgress/{exerciseName}") { backStack ->
            ExerciseProgressScreen(
                exerciseName = Uri.decode(backStack.arguments?.getString("exerciseName").orEmpty()),
                onBack       = { navController.popBackStack() },
            )
        }
        composable("BodyWeight") {
            val bodyMutationContext = LocalContext.current.applicationContext
            val bodyState  by bodyVm.state.collectAsStateWithLifecycle()
            val statsState by statsVm.state.collectAsStateWithLifecycle()
            val bwSettingsRepo = remember { SettingsRepository() }
            var goalWeight by remember { mutableStateOf<Double?>(null) }
            LaunchedEffect(Unit) {
                goalWeight = bwSettingsRepo.getSettingNumber("bodyweight_goal")
            }
            BodyWeightScreen(
                bodyWeight             = bodyState.bodyWeight,
                weightUnit             = statsState.weightUnit,
                goalWeight             = goalWeight,
                onBack                 = { navController.popBackStack() },
                onLogBodyWeight        = {
                    bodyVm.addAndAwait(it)
                    com.ironlog.app.services.WorkoutNotificationBridge
                        .cancelReminderAfterDataMutation(bodyMutationContext)
                },
                onDeleteBodyWeightEntry = {
                    bodyVm.removeAndAwait(it)
                    com.ironlog.app.services.WorkoutNotificationBridge
                        .cancelReminderAfterDataMutation(bodyMutationContext)
                },
                onSetGoalWeight        = { g ->
                    goalWeight = g
                    if (g != null) bwSettingsRepo.setSetting("bodyweight_goal", g, "number")
                    else bwSettingsRepo.setSetting("bodyweight_goal", null, "number")
                },
            )
        }
        composable("WorkoutCalendar") {
            val statsState by statsVm.state.collectAsStateWithLifecycle()
            WorkoutCalendarScreen(
                history         = statsState.history,
                weightUnit      = statsState.weightUnit,
                onBack          = { navController.popBackStack() },
                onLogHistorical = { dateKey -> navController.navigate("HistoricalWorkout/$dateKey") },
            )
        }
        composable("HistoricalWorkout/{date}") { entry ->
            val statsState by statsVm.state.collectAsStateWithLifecycle()
            com.ironlog.app.ui.screens.history.HistoricalWorkoutEntryHost(
                initialDate = entry.arguments?.getString("date").orEmpty(),
                weightUnit = statsState.weightUnit,
                onDismiss = { navController.popBackStack() },
            )
        }
        composable("BodyMeasurements") {
            val bodyMutationContext = LocalContext.current.applicationContext
            val bodyState  by bodyVm.state.collectAsStateWithLifecycle()
            val statsState by statsVm.state.collectAsStateWithLifecycle()
            BodyMeasurementsScreen(
                measurements    = bodyState.measurements,
                bodyWeight      = bodyState.bodyWeight,
                weightUnit      = statsState.weightUnit,
                onBack          = { navController.popBackStack() },
                onLogBodyWeight = {
                    bodyVm.addAndAwait(it)
                    com.ironlog.app.services.WorkoutNotificationBridge
                        .cancelReminderAfterDataMutation(bodyMutationContext)
                },
                onAddMeasurement = { input ->
                    bodyVm.addAndAwait(input)
                    if (input.bodyweight != null) {
                        com.ironlog.app.services.WorkoutNotificationBridge
                            .cancelReminderAfterDataMutation(bodyMutationContext)
                    }
                },
            )
        }
        composable("VolumeAnalytics") {
            VolumeAnalyticsScreen(
                onBack           = { navController.popBackStack() },
                onOpenBodyWeight = { navController.navigate("BodyWeight") },
            )
        }
        composable("RecoveryMap") {
            RecoveryMapScreen(
                onBack                 = { navController.popBackStack() },
                onOpenVolumeAnalytics  = { navController.navigate("VolumeAnalytics") },
            )
        }
        composable("TrainingIntelligence") {
            TrainingIntelligenceScreen(
                onBack               = { navController.popBackStack() },
                onStartWorkout       = { dayId ->
                    navController.navigate(if (!dayId.isNullOrBlank()) "ActiveWorkout/${Uri.encode(dayId)}" else "ActiveWorkout")
                },
                onOpenRecoveryMap    = { navController.navigate("RecoveryMap") },
                onOpenAIPlan         = { navController.navigate("AIPlan") },
                onOpenProgramInsights = { navController.navigate("ProgramInsights") },
            )
        }
        composable("ProgramPicker")  { ProgramPickerScreen(onBack = { navController.popBackStack() }) }
        composable("ProgramInsights") {
            ProgramInsightsScreen(
                onBack      = { navController.popBackStack() },
                // Set pending_nav_route then pop back — the Tabs polling loop scrolls to Plans.
                // Avoids the phantom "Plans" composable which created duplicate Tabs back-stack entries.
                onOpenPlans = {
                    navigationScope.launch {
                        persistPendingRouteAndRevealTabs(
                            route = "Plans",
                            persist = ::settingsRepoSetPendingNavRoute,
                            revealTabs = { navController.popBackStack("Tabs", inclusive = false) },
                        )
                    }
                },
            )
        }
        composable("ProgressPhotos") { ProgressPhotosScreen(onBack = { navController.popBackStack() }) }
        composable("GymProfiles") {
            GymProfilesScreen(
                onBack   = { navController.popBackStack() },
                onCreate = { navController.navigate("GymProfileEditor") },
                onEdit   = { id -> navController.navigate("GymProfileEditor/${Uri.encode(id)}") },
            )
        }
        composable("GymProfileEditor/{profileId}") { backStack ->
            GymProfileEditorScreen(
                profileId = Uri.decode(backStack.arguments?.getString("profileId").orEmpty()),
                onSaved   = { navController.popBackStack() },
                onBack    = { navController.popBackStack() },
            )
        }
        composable("GymProfileEditor") {
            GymProfileEditorScreen(
                onSaved = { navController.popBackStack() },
                onBack  = { navController.popBackStack() },
            )
        }
        composable("BackupCenter") {
            BackupCenterScreen(
                onBack               = { navController.popBackStack() },
                onOpenDataPortability = { navController.navigate("DataPortability") },
                onOpenPrivacy        = { navController.navigate("Privacy") },
            )
        }
        composable("RestoreData") {
            RestoreDataScreen(
                onBack               = { navController.popBackStack() },
                onOpenBackupCenter   = { navController.navigate("BackupCenter") },
                onOpenImportCenter   = { navController.navigate("ImportCenter") },
                onOpenDataPortability = { source -> navController.navigate("DataPortability/${Uri.encode(source)}") },
            )
        }
        composable("ImportCenter") {
            val app = LocalContext.current.applicationContext as Application
            val icScope = rememberCoroutineScope()
            val tabsEntry = remember(navController) { runCatching { navController.getBackStackEntry("Tabs") }.getOrNull() }
            val appVm: AppDataViewModel = tabsEntry?.let { viewModel(it) } ?: viewModel()
            val statsVm: StatsViewModel = tabsEntry?.let { viewModel(it) } ?: viewModel()
            val gamificationVm: GamificationViewModel = tabsEntry?.let {
                viewModel(viewModelStoreOwner = it, factory = GamificationViewModelFactory(app, ObjectBox.store))
            } ?: viewModel(factory = GamificationViewModelFactory(app, ObjectBox.store))
            ImportCenterScreen(
                onBack               = { navController.popBackStack() },
                onOpenDataPortability = { source -> navController.navigate("DataPortability/${Uri.encode(source)}") },
                onRestoreComplete = {
                    icScope.launch {
                        reconcileNotificationsAfterRestore(app)
                        appVm.refresh().join()
                        statsVm.refresh().join()
                        gamificationVm.refreshFromHistory(
                            statsVm.state.value.history,
                            appVm.state.value.settings.weeklyGoalDays,
                        )
                        WidgetUpdateWorker.enqueueOneTime(app)
                    }
                },
            )
        }
        composable("AIPlan")         { AIPlanScreen(onBack = { navController.popBackStack() }) }
        composable("DataPortability") {
            val app = LocalContext.current.applicationContext as Application
            val dpScope = rememberCoroutineScope()
            val tabsEntry = remember(navController) { runCatching { navController.getBackStackEntry("Tabs") }.getOrNull() }
            val appVm: AppDataViewModel = tabsEntry?.let { viewModel(it) } ?: viewModel()
            val statsVm: StatsViewModel = tabsEntry?.let { viewModel(it) } ?: viewModel()
            val gamificationVm: GamificationViewModel = tabsEntry?.let {
                viewModel(viewModelStoreOwner = it, factory = GamificationViewModelFactory(app, ObjectBox.store))
            } ?: viewModel(factory = GamificationViewModelFactory(app, ObjectBox.store))
            DataPortabilityScreen(
                onBack = { navController.popBackStack() },
                onRestoreComplete = {
                    dpScope.launch {
                        reconcileNotificationsAfterRestore(app)
                        appVm.refresh().join()
                        statsVm.refresh().join()
                        gamificationVm.refreshFromHistory(
                            statsVm.state.value.history,
                            appVm.state.value.settings.weeklyGoalDays,
                        )
                        WidgetUpdateWorker.enqueueOneTime(app)
                    }
                },
            )
        }
        composable("DataPortability/{source}") { backStack ->
            val app = LocalContext.current.applicationContext as Application
            val dpScope = rememberCoroutineScope()
            val tabsEntry = remember(navController) { runCatching { navController.getBackStackEntry("Tabs") }.getOrNull() }
            val appVm: AppDataViewModel = tabsEntry?.let { viewModel(it) } ?: viewModel()
            val statsVm: StatsViewModel = tabsEntry?.let { viewModel(it) } ?: viewModel()
            val gamificationVm: GamificationViewModel = tabsEntry?.let {
                viewModel(viewModelStoreOwner = it, factory = GamificationViewModelFactory(app, ObjectBox.store))
            } ?: viewModel(factory = GamificationViewModelFactory(app, ObjectBox.store))
            DataPortabilityScreen(
                sourceHint = Uri.decode(backStack.arguments?.getString("source").orEmpty()),
                onBack     = { navController.popBackStack() },
                onRestoreComplete = {
                    dpScope.launch {
                        reconcileNotificationsAfterRestore(app)
                        appVm.refresh().join()
                        statsVm.refresh().join()
                        gamificationVm.refreshFromHistory(
                            statsVm.state.value.history,
                            appVm.state.value.settings.weeklyGoalDays,
                        )
                        WidgetUpdateWorker.enqueueOneTime(app)
                    }
                },
            )
        }
        composable("statusWindow") {
            val app = LocalContext.current.applicationContext as Application
            val tabsEntry = remember(navController) {
                runCatching { navController.getBackStackEntry("Tabs") }.getOrNull()
            }
            val appVm: AppDataViewModel = tabsEntry?.let { viewModel(it) } ?: viewModel()
            val appState by appVm.state.collectAsStateWithLifecycle()
            val gamificationVm: GamificationViewModel = tabsEntry?.let {
                viewModel(viewModelStoreOwner = it, factory = GamificationViewModelFactory(app, ObjectBox.store))
            } ?: viewModel(factory = GamificationViewModelFactory(app, ObjectBox.store))
            val gamState by gamificationVm.uiState.collectAsStateWithLifecycle()
            var showMakeupSheet by remember { mutableStateOf(false) }
            val ledgerNow by com.ironlog.app.ui.state.rememberPresentationTime()
            LaunchedEffect(appState.history, appState.settings.weeklyGoalDays, ledgerNow) {
                gamificationVm.refreshFromHistory(appState.history, appState.settings.weeklyGoalDays)
            }

            StatusWindowScreen(
                state = gamState,
                onRetryRefresh = { gamificationVm.loadProfile() },
                onBack = { navController.popBackStack() },
                onRecoveryCircuitTap = { showMakeupSheet = true },
                onDailyProofAction = {
                    when (gamState.dailyProofPrimaryRoute) {
                        "ActiveWorkout" -> navController.navigate("ActiveWorkout")
                        "RecoveryMap" -> navController.navigate("RecoveryMap")
                        "ProgramPicker" -> navController.navigate("ProgramPicker")
                        else -> navController.popBackStack()
                    }
                },
            )

            if (showMakeupSheet) {
                RecoveryCircuitSheet(
                    onDismiss = { showMakeupSheet = false },
                    onComplete = { circuitId ->
                        val weekKey = java.time.LocalDate.now().let {
                            val week = it.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
                            val year = it.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
                            "$year-W${week.toString().padStart(2, '0')}"
                        }
                        val result = gamificationVm.completeRecoveryCircuit(
                            isoWeekKey = weekKey,
                            circuitId = circuitId,
                        )
                        if (result == com.ironlog.app.data.repository.RecoveryCircuitResult.RECORDED) {
                            try {
                                // The circuit repository has committed the proof at this point.
                                // Dismiss any recommendation derived from the pre-circuit snapshot.
                                com.ironlog.app.services.WorkoutNotificationBridge
                                    .cancelReminderAfterDataMutation(app)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Timber.w(error, "Could not dismiss stale reminder after recovery circuit")
                            }
                        }
                        result
                    },
                )
            }
        }
    }
}

internal suspend fun reconcileNotificationsAfterRestore(app: Application) {
    withContext(Dispatchers.IO + NonCancellable) {
        com.ironlog.app.services.WorkoutNotificationBridge.cancelReminderAfterDataMutation(app)
        com.ironlog.app.services.WorkoutForegroundService.clearOrphanedNotification(app)
        com.ironlog.app.services.NotificationCoordinator.reconcile(app, forceReschedule = true)
    }
}

private suspend fun settingsRepoSaveOnboardingDataFull(context: Context, draft: com.ironlog.app.ui.screens.onboarding.OnboardingDraft) {
    val repo = SettingsRepository()
    val raw  = repo.getString("ironlog_settings") ?: "{}"
    val json = mergeOnboardingSettingsJson(raw, draft)
    repo.setString("ironlog_settings", json.toString(), "json")
    onboardingBaselineSettingsFromDraft(draft).forEach { (key, value) ->
        // The immutable baseline weight is committed with calibration, Ledger event, and profile.
        if (key == "baseline_bodyweight_kg") return@forEach
        if (value == "true" || value == "false") {
            repo.setBoolean(key, value.toBoolean())
        } else {
            repo.setString(key, value)
        }
    }
    val calibrationBox = ObjectBox.store.boxFor(AthleteCalibrationEntity::class.java)
    val existing = calibrationBox.query(AthleteCalibrationEntity_.offlineUserId.equal("local"))
        .build().use { it.findFirst() }
    val calibrationUpdatedAt = System.currentTimeMillis()
    persistOnboardingBaselineAtomically(
        store = ObjectBox.store,
        calibration = buildCalibrationEntityFromOnboardingDraft(draft, existing, calibrationUpdatedAt),
        bodyweightKg = draft.bodyweightKg.takeIf { it > 0 }?.toDouble(),
        occurredAtMs = calibrationUpdatedAt,
    )
    if (draft.cloudAiApiKey.isNotBlank()) {
        val provider = draft.cloudAiProviderPreset.ifBlank { "custom" }
        CloudAiKeyStore.save(context, provider, draft.cloudAiApiKey)
    }
    // Commit this marker last. A crash or key/calibration failure must return the
    // athlete to onboarding instead of launching a partially initialized ledger.
    repo.setBoolean("onboarding_state_migrated_v1", true)
    repo.setBoolean("onboarding_complete", true)
    runCatching { com.ironlog.app.services.NotificationCoordinator.reconcile(context) }
}

internal fun mergeOnboardingSettingsJson(
    existingRaw: String,
    draft: com.ironlog.app.ui.screens.onboarding.OnboardingDraft,
): org.json.JSONObject {
    val json = runCatching { org.json.JSONObject(existingRaw) }.getOrDefault(org.json.JSONObject())
    json.put("weeklyGoalDays",       draft.weeklyGoalDays.coerceIn(1, 7))
    json.put("weightUnit",           if (draft.weightUnit == "lbs") "lbs" else "kg")
    json.put("progressionStyle",     canonicalProgressionStyle(draft.progressionStyle))
    json.put("goalMode",             canonicalGoalMode(draft.goalMode))
    json.put("intelligenceMode",     canonicalIntelligenceMode(draft.intelligenceMode))
    TrainingDayPreferences.writeToSettings(json, draft.selectedDayIndices)
    json.put("cloudAiModelName",     draft.cloudAiModelName)
    json.put("cloudAiProviderPreset",draft.cloudAiProviderPreset)
    // Resolve base URL, API format and display name from the provider enum so they
    // are available in Settings immediately after onboarding — without this the
    // cloud AI section shows a blank URL and cloudConfigured stays false.
    val onboardingProvider = com.ironlog.app.ui.screens.onboarding.OnboardingConfig.providerFor(draft.cloudAiProviderPreset)
    json.put("cloudAiBaseUrl",       onboardingProvider.baseUrl)
    json.put("cloudAiApiFormat",     onboardingProvider.apiFormat)
    json.put("cloudAiDisplayName",   onboardingProvider.displayName)
    if (draft.userName.isNotBlank()) json.put("userName", draft.userName)
    return json
}

internal fun buildCalibrationEntityFromOnboardingDraft(
    draft: com.ironlog.app.ui.screens.onboarding.OnboardingDraft,
    existing: AthleteCalibrationEntity? = null,
    updatedAtMs: Long = System.currentTimeMillis(),
): AthleteCalibrationEntity {
    val entity = existing ?: AthleteCalibrationEntity()
    entity.offlineUserId = "local"
    entity.trainingAgeMonths = draft.trainingAgeMonths.coerceAtLeast(0)
    entity.historicalTrainingDaysPerWeek = draft.historicalTrainingDaysPerWeek.coerceIn(1, 7)
    entity.weightUnit = if (draft.weightUnit == "lbs") "lbs" else "kg"
    entity.bodyweightKg = draft.bodyweightKg.takeIf { it > 0 }?.toDouble()
    entity.goalMode = canonicalGoalMode(draft.goalMode)
    entity.weeklyGoalDays = draft.weeklyGoalDays.coerceIn(1, 7)
    entity.hasPastTraining = draft.hasPastTraining
    entity.hasGymAccess = draft.hasGymAccess
    entity.baselinePushups = draft.baselinePushups.coerceAtLeast(0)
    entity.baselinePullups = draft.baselinePullups.coerceAtLeast(0)
    entity.baselineBenchKg = draft.baselineBenchKg.coerceAtLeast(0)
    entity.baselineLatPulldownKg = draft.baselineLatPulldownKg.coerceAtLeast(0)
    entity.baselineMileRunSeconds = draft.baselineMileRunSeconds.coerceAtLeast(0)
    entity.updatedAt = updatedAtMs
    return entity
}

internal fun onboardingBaselineSettingsFromDraft(
    draft: com.ironlog.app.ui.screens.onboarding.OnboardingDraft,
): Map<String, String> = linkedMapOf(
    "baseline_training_age_months" to draft.trainingAgeMonths.coerceAtLeast(0).toString(),
    "baseline_historical_training_days_per_week" to draft.historicalTrainingDaysPerWeek.coerceIn(1, 7).toString(),
    "baseline_bodyweight_kg" to draft.bodyweightKg.coerceAtLeast(0).toString(),
    "baseline_pushups" to draft.baselinePushups.coerceAtLeast(0).toString(),
    "baseline_pullups" to draft.baselinePullups.coerceAtLeast(0).toString(),
    "baseline_bench_kg" to draft.baselineBenchKg.coerceAtLeast(0).toString(),
    "baseline_lat_pulldown_kg" to draft.baselineLatPulldownKg.coerceAtLeast(0).toString(),
    "baseline_mile_run_seconds" to draft.baselineMileRunSeconds.coerceAtLeast(0).toString(),
    "baseline_has_past_training" to draft.hasPastTraining.toString(),
    "baseline_has_gym_access" to draft.hasGymAccess.toString(),
    "notifications_enabled" to draft.notificationsGranted.toString(),
)

internal fun canonicalGoalMode(value: String): String = when (value.trim().lowercase()) {
    "strength" -> "strength"
    "general_fitness", "general fitness", "performance", "endurance" -> "general_fitness"
    else -> "hypertrophy"
}

internal fun canonicalProgressionStyle(value: String): String = when (value.trim().lowercase()) {
    "conservative", "linear" -> "conservative"
    "aggressive", "undulating" -> "aggressive"
    else -> "balanced"
}

private suspend fun settingsRepoSetPendingNavRoute(route: String) {
    SettingsRepository().setString("pending_nav_route", route)
}

internal suspend fun persistPendingRouteAndRevealTabs(
    route: String,
    persist: suspend (String) -> Unit,
    revealTabs: () -> Unit,
) {
    persist(route)
    revealTabs()
}

/** Notification route `Tabs` is the existing Home tab, never a second NavHost entry. */
internal fun pendingTabIndex(route: String): Int? = when (route) {
    "Tabs", "Home" -> 0
    "Plans" -> 1
    "Log" -> 2
    "Stats" -> 3
    "Settings" -> 4
    else -> null
}

private val AllowedPendingStackRoutes = setOf(
    "ActiveWorkout", "BodyWeight", "BodyMeasurements", "ProgressPhotos",
    "WorkoutCalendar", "VolumeAnalytics", "RecoveryMap", "TrainingIntelligence",
    "ProgramInsights", "ProgramPicker", "ExerciseLibrary", "CreateExercise",
    "GymProfiles", "GymProfileEditor", "BackupCenter", "Privacy", "RestoreData",
    "ImportCenter", "DataPortability", "AIPlan", "statusWindow",
)

internal fun isAllowedPendingStackRoute(route: String): Boolean =
    route in AllowedPendingStackRoutes ||
        (route.startsWith("ActiveWorkout/") && route.length in 15..512 &&
            route.removePrefix("ActiveWorkout/").matches(Regex("[A-Za-z0-9._~%+\\-]+")))

internal fun isAllowedPendingRoute(route: String): Boolean =
    pendingTabIndex(route) != null || isAllowedPendingStackRoute(route)

// ─────────────────────────────────────────────────────────────────────────────
// Tab host
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tabs(
    navController: NavHostController,
    pendingTabRoute: String?,
    onPendingTabHandled: (String) -> Unit,
) {
    val colors      = useTheme()
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository() }
    val workoutRepo  = remember { com.ironlog.app.data.repository.WorkoutRepository() }
    val scope       = rememberCoroutineScope()
    val pagerState  = rememberPagerState(initialPage = 0, pageCount = { TabScreens.size })



    // selectedTabIndex updates immediately on tap for a snappy indicator;
    // pagerState.currentPage syncs it back when the swipe gesture settles.
    var selectedTabIndex     by remember { mutableIntStateOf(0) }
    var activeWorkoutId      by remember { mutableStateOf<String?>(null) }
    var activeWorkoutDayId   by remember { mutableStateOf<String?>(null) }
    var activeWorkoutDayName by remember { mutableStateOf<String?>(null) }
    var activeWorkoutStartMs by remember { mutableLongStateOf(0L) }
    var discardRequestedId by remember { mutableStateOf<String?>(null) }
    var discardError by remember { mutableStateOf<String?>(null) }
    var discarding by remember { mutableStateOf(false) }

    // Sync indicator when user swipes the pager
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    val tabSettingsFlow = remember(settingsRepo) {
        settingsRepo.observeStrings(setOf("active_workout_id", "active_workout_day_id",
            "active_workout_day_name", "active_workout_start_ms"))
    }
    val tabSettings by tabSettingsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    LaunchedEffect(tabSettings) {
            activeWorkoutId = tabSettings["active_workout_id"]
            activeWorkoutDayId = tabSettings["active_workout_day_id"]
            activeWorkoutDayName = tabSettings["active_workout_day_name"]
            activeWorkoutStartMs = tabSettings["active_workout_start_ms"]?.toLongOrNull() ?: 0L
    }
    LaunchedEffect(pendingTabRoute) {
        val route = pendingTabRoute ?: return@LaunchedEffect
        val tabIndex = pendingTabIndex(route) ?: return@LaunchedEffect
        selectedTabIndex = tabIndex
        pagerState.scrollToPage(tabIndex)
        onPendingTabHandled(route)
    }

    discardRequestedId?.let { requestedId ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!discarding) discardRequestedId = null },
            title = { Text("Discard this workout?") },
            text = { Text(discardError ?: "This unfinished session will not be added to History. Your completed workouts and plan are kept.") },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = !discarding, onClick = {
                    discarding = true
                    discardError = null
                    scope.launch {
                        try {
                            commitWorkoutTerminalMutation {
                                workoutRepo.abandonWorkout(requestedId)
                                // Cleanup is part of the non-cancellable terminal boundary. Every
                                // operation is session-scoped so an older dialog cannot touch a new workout.
                                runCatching { com.ironlog.app.services.WorkoutForegroundService.stop(context, requestedId) }
                                runCatching { com.ironlog.app.services.WorkoutNotificationActionInbox.clearSession(requestedId) }
                                runCatching { com.ironlog.app.services.WorkoutNotificationBridge.clearWorkout(context) }
                                runCatching {
                                    com.ironlog.app.services.WorkoutNotificationBridge
                                        .cancelReminderAfterDataMutation(context)
                                }
                            }
                            discardRequestedId = null
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                        } catch (error: Exception) {
                            discardError = "Could not discard. Your session is still saved; please retry."
                        } finally { discarding = false }
                    }
                }) { Text(if (discarding) "Discarding…" else "Discard workout") }
            },
            dismissButton = { androidx.compose.material3.TextButton(enabled = !discarding,
                onClick = { discardRequestedId = null }) { Text("Keep workout") } },
        )
    }

    val liquidGlassEnabled = com.ironlog.app.ui.theme.LocalLiquidGlassEnabled.current
    val hazeState = rememberHazeState()

    Box(Modifier.fillMaxSize().background(colors.bg)) {

        // Pager — hazeSource records pixels for the tab bar blur.
        // userScrollEnabled = true lets users swipe; selectedTabIndex syncs via
        // LaunchedEffect(pagerState.currentPage) above.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (liquidGlassEnabled) Modifier.hazeSource(hazeState) else Modifier),
        ) {
            HorizontalPager(
                state                 = pagerState,
                userScrollEnabled     = true,
                beyondViewportPageCount = 1,
            ) { page ->
                when (TabScreens[page].name) {
                    "Home" -> HomeScreen(
                        onStartWorkout = { _, dayId ->
                            navController.navigate(if (dayId.isNotBlank()) "ActiveWorkout/${Uri.encode(dayId)}" else "ActiveWorkout")
                        },
                        onStartEmptyWorkout = {
                            navController.navigate("ActiveWorkout?startEmpty=true")
                        },
                        onOpenBodyWeight           = { navController.navigate("BodyWeight") },
                        onOpenRecovery             = { navController.navigate("RecoveryMap") },
                        onOpenTrainingIntelligence = { navController.navigate("TrainingIntelligence") },
                        onOpenProgramPicker        = { navController.navigate("ProgramPicker") },
                        onOpenProgramInsights      = { navController.navigate("ProgramInsights") },
                        onOpenVolumeAnalytics      = { navController.navigate("VolumeAnalytics") },
                        onResumeWorkout = {
                            navController.navigate(
                                if (!activeWorkoutDayId.isNullOrBlank()) "ActiveWorkout/${Uri.encode(activeWorkoutDayId)}" else "ActiveWorkout"
                            )
                        },
                        onOpenStatusWindow = { navController.navigate("statusWindow") },
                        onDiscardWorkout = {
                            discardError = null
                            discardRequestedId = activeWorkoutId
                        },
                    )
                    "Plans" -> PlansScreen(
                        onOpenPlan      = { planId -> navController.navigate("PlanEditor/${Uri.encode(planId)}") },
                        onStartWorkout  = { _, dayId ->
                            navController.navigate(if (dayId.isNotBlank()) "ActiveWorkout/${Uri.encode(dayId)}" else "ActiveWorkout")
                        },
                        onOpenProgramPicker = { navController.navigate("ProgramPicker") },
                        onOpenAIPlan        = { navController.navigate("AIPlan") },
                    )
                    "Log" -> HistoryScreen(
                        onOpenProgressPhotos = { navController.navigate("ProgressPhotos") },
                        onGoHome = {
                            selectedTabIndex = 0
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                    )
                    "Stats" -> StatsScreen(
                        onOpenExerciseProgress   = { exerciseName -> navController.navigate("ExerciseProgress/${Uri.encode(exerciseName)}") },
                        onOpenCalendar           = { navController.navigate("WorkoutCalendar") },
                        onOpenBodyTracker        = { navController.navigate("BodyMeasurements") },
                        onOpenVolumeAnalytics    = { navController.navigate("VolumeAnalytics") },
                        onOpenRecoveryMap        = { navController.navigate("RecoveryMap") },
                        onOpenTrainingIntelligence = { navController.navigate("TrainingIntelligence") },
                        onOpenProgressPhotos     = { navController.navigate("ProgressPhotos") },
                        onOpenStatusWindow       = { navController.navigate("statusWindow") },
                    )
                    "Settings" -> SettingsScreen(
                        onOpenProgramPicker        = { navController.navigate("ProgramPicker") },
                        onOpenAIPlan               = { navController.navigate("AIPlan") },
                        onOpenDataPortability      = { navController.navigate("DataPortability") },
                        onOpenTrainingIntelligence = { navController.navigate("TrainingIntelligence") },
                        onOpenExerciseLibrary      = { navController.navigate("ExerciseLibrary") },
                        onOpenGymProfiles          = { navController.navigate("GymProfiles") },
                        onOpenBodyWeight           = { navController.navigate("BodyWeight") },
                        onOpenImportCenter         = { navController.navigate("ImportCenter") },
                        onOpenPrivacy              = { navController.navigate("Privacy") },
                    )
                    // Safety fallback — all tabs are explicitly covered above.
                    else -> Unit
                }
            }
        }

        // ── Floating workout pill — sits just above the tab bar
        val activeWorkoutActive = !activeWorkoutId.isNullOrBlank()
        AnimatedVisibility(
            visible  = activeWorkoutActive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 92.dp),
            enter = fadeIn(tween(180)) + scaleIn(spring(stiffness = 480f, dampingRatio = 0.45f), initialScale = 0.7f),
            exit  = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.7f),
        ) {
            FloatingActiveWorkoutPill(
                activeWorkoutStartMs = activeWorkoutStartMs,
                activeWorkoutDayName = activeWorkoutDayName,
                activeWorkoutDayId = activeWorkoutDayId,
                navController = navController,
                colors = colors,
            )
        }

        // ── Custom full-width glass tab bar
        IronLogTabBar(
            selectedIndex = selectedTabIndex,
            onTabSelected = { index ->
                selectedTabIndex = index
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            hazeState = hazeState,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun FloatingActiveWorkoutPill(
    activeWorkoutStartMs: Long,
    activeWorkoutDayName: String?,
    activeWorkoutDayId: String?,
    navController: NavHostController,
    colors: IronLogThemeTokens
) {
    var elapsedSeconds by remember(activeWorkoutStartMs) {
        mutableLongStateOf(
            if (activeWorkoutStartMs > 0L) (System.currentTimeMillis() - activeWorkoutStartMs) / 1000L else 0L
        )
    }
    LaunchedEffect(activeWorkoutStartMs) {
        if (activeWorkoutStartMs > 0L) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - activeWorkoutStartMs) / 1000L
                delay(1000)
            }
        }
    }
    val timerText = remember(elapsedSeconds) {
        val s = elapsedSeconds; val m = s / 60; val h = m / 60
        if (h > 0) "$h:${(m % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
        else "${(m % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(max = 280.dp)
            .shadow(8.dp, RoundedCornerShape(999.dp))
            .clickable {
                navController.navigate(
                    if (!activeWorkoutDayId.isNullOrBlank()) "ActiveWorkout/${Uri.encode(activeWorkoutDayId)}" else "ActiveWorkout"
                )
            },
        color = colors.accent,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier             = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.FitnessCenter, null, tint = colors.textOnAccent.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
            Text(
                text     = buildString {
                    append(activeWorkoutDayName?.uppercase()?.take(12) ?: "WORKOUT")
                    append(" · ")
                    append(timerText)
                },
                color        = colors.textOnAccent,
                fontWeight   = FontWeight.ExtraBold,
                fontSize     = IronLogType.meta.fontSize.sp,
                letterSpacing = 0.4.sp,
                maxLines     = 1,
                overflow     = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ChevronRight, null, tint = colors.textOnAccent.copy(alpha = 0.65f), modifier = Modifier.size(13.dp))
        }
    }
}


