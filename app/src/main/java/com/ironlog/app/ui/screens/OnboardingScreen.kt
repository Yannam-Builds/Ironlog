package com.ironlog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.app.data.repository.PlanRepository
import com.ironlog.app.data.seed.toPlanObject
import com.ironlog.app.ui.screens.onboarding.buildOnboardingLedgerSnapshot
import com.ironlog.app.ui.screens.onboarding.onboardingPreviewStats
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingDraft
import com.ironlog.app.ui.screens.onboarding.OnboardingViewModel
import com.ironlog.app.ui.screens.onboarding.steps.*
import kotlinx.coroutines.launch

/**
 * Root onboarding composable — 10-screen IronLog setup flow.
 * HorizontalPager with swipe navigation and page indicator.
 * [onComplete] receives the completed [OnboardingDraft] for atomic save.
 */
@Composable
fun OnboardingScreen(
    onComplete: suspend (OnboardingDraft) -> Unit,
    vm: OnboardingViewModel = viewModel(),
    planRepo: PlanRepository = PlanRepository(),
) {
    val draft by vm.draft.collectAsState()
    val seededSnapshot = remember(draft) { buildOnboardingLedgerSnapshot(draft) }
    val pagerState = rememberPagerState(pageCount = { 10 })
    val scope = rememberCoroutineScope()
    var completionError by remember { mutableStateOf<String?>(null) }

    fun advance() = scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
    fun goBack() = scope.launch {
        if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1)
    }

    val stageLabels = remember {
        listOf("Welcome", "Profile", "Baseline", "Training level", "Schedule", "Goal", "Coaching", "Permissions", "Calibration", "Starter plan")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .statusBarsPadding(),
    ) {
        HorizontalPager(
            state             = pagerState,
            userScrollEnabled = true,
            modifier          = Modifier
                .fillMaxSize()
                .padding(
                    top = if (pagerState.currentPage == 0) 0.dp else 62.dp,
                    bottom = 8.dp,
                ),
        ) { page ->
                when (page) {
                    0 -> Step1Awakening(
                        onAdvance = { advance() },
                        onSkip = {
                            scope.launch {
                                runCatching { onComplete(draft) }
                                    .onFailure { completionError = it.message ?: "Could not finish setup. Please try again." }
                            }
                        },
                    )
                    1 -> Step2Registration(
                        userName         = draft.userName,
                        onUserNameChange = vm::updateUserName,
                        onNext           = { advance() },
                    )
                    2 -> Step3Baseline(
                        yearOfBirth = draft.yearOfBirth,
                        bodyweightKg = draft.bodyweightKg,
                        trainingAgeMonths = draft.trainingAgeMonths,
                        historicalTrainingDaysPerWeek = draft.historicalTrainingDaysPerWeek,
                        hasPastTraining = draft.hasPastTraining,
                        hasGymAccess = draft.hasGymAccess,
                        pushups = draft.baselinePushups,
                        pullups = draft.baselinePullups,
                        benchKg = draft.baselineBenchKg,
                        latPulldownKg = draft.baselineLatPulldownKg,
                        mileRunSeconds = draft.baselineMileRunSeconds,
                        onYearOfBirthChange = vm::updateYearOfBirth,
                        onBodyweightChange = vm::updateBodyweightKg,
                        onTrainingAgeChange = vm::updateTrainingAgeMonths,
                        onHistoricalTrainingDaysPerWeekChange = vm::updateHistoricalTrainingDaysPerWeek,
                        onPastTrainingChange = vm::setHasPastTraining,
                        onGymAccessChange = vm::setHasGymAccess,
                        onPushupsChange = vm::updateBaselinePushups,
                        onPullupsChange = vm::updateBaselinePullups,
                        onBenchChange = vm::updateBaselineBenchKg,
                        onLatPulldownChange = vm::updateBaselineLatPulldownKg,
                        onMileRunChange = vm::updateBaselineMileRunSeconds,
                        seededGrade = seededSnapshot.grade.label,
                        seededStats = onboardingPreviewStats(seededSnapshot.stats),
                        onNext = { advance() },
                    )
                    3 -> Step3Classification(
                        selectedProgressionStyle = draft.progressionStyle,
                        onSelect                 = { style, goal -> vm.setClassification(style, goal) },
                        onNext                   = { advance() },
                    )
                    4 -> Step4Quota(
                        selectedDayIndices = draft.selectedDayIndices,
                        onDayToggle        = vm::toggleDayIndex,
                        weightUnit         = draft.weightUnit,
                        onWeightUnitChange = vm::updateWeightUnit,
                        onNext             = { advance() },
                    )
                    5 -> Step5GoalMode(
                        selectedGoalMode = draft.goalMode,
                        onSelect         = vm::setGoalMode,
                        onNext           = { advance() },
                    )
                    6 -> Step6AiAbilities(
                        cloudApiKey      = draft.cloudAiApiKey,
                        cloudModelName   = draft.cloudAiModelName,
                        cloudProvider    = draft.cloudAiProviderPreset,
                        onApiKeyChange   = vm::updateCloudApiKey,
                        onModelChange    = vm::updateCloudModelName,
                        onProviderChange = { provider, model -> vm.updateCloudProvider(provider, model) },
                        onNext           = { advance() },
                        onSkip           = { advance() },
                    )
                    7 -> Step7Permissions(
                        cameraGranted          = draft.cameraGranted,
                        healthConnectGranted   = draft.healthConnectGranted,
                        notificationsGranted   = draft.notificationsGranted,
                        onCameraGranted        = vm::setCameraGranted,
                        onHealthConnectGranted = vm::setHealthConnectGranted,
                        onNotificationsGranted = vm::setNotificationsGranted,
                        onNext                 = { advance() },
                    )
                    8 -> Step8Complete(
                        userName = draft.userName,
                        weeklyGoalDays = draft.weeklyGoalDays,
                        weightUnit = draft.weightUnit,
                        goalMode = draft.goalMode,
                        progressionStyle = draft.progressionStyle,
                        intelligenceMode = draft.intelligenceMode,
                        healthConnectGranted = draft.healthConnectGranted,
                        notificationsGranted = draft.notificationsGranted,
                        qualifiedBadge = seededSnapshot.grade.label,
                        onStartTraining = { advance() },
                    )
                    9 -> Step9ProgramSetup(
                        onSkip = {
                            scope.launch {
                                runCatching { onComplete(draft) }
                                    .onFailure { completionError = it.message ?: "Could not finish setup. Please try again." }
                            }
                        },
                        onApplyTemplate = { template ->
                            scope.launch {
                                runCatching {
                                    planRepo.importFullPlan(template.toPlanObject())
                                    onComplete(draft)
                                }.onFailure {
                                    completionError = it.message ?: "Could not save the starter plan. Please try again."
                                }
                            }
                        },
                    )
                }
            }
        if (pagerState.currentPage > 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::goBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Previous onboarding step",
                            tint = OnboardingConfig.textPrimary,
                        )
                    }
                    Text(
                        stageLabels[pagerState.currentPage],
                        color = OnboardingConfig.textMuted,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${pagerState.currentPage} / ${pagerState.pageCount - 1}",
                        color = OnboardingConfig.textFaint,
                        fontSize = 12.sp,
                    )
                }
                LinearProgressIndicator(
                    progress = { pagerState.currentPage.toFloat() / (pagerState.pageCount - 1).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = OnboardingConfig.accentBlue,
                    trackColor = OnboardingConfig.cardBorder,
                )
            }
        }
    }

    completionError?.let { message ->
        AlertDialog(
            onDismissRequest = { completionError = null },
            title = { Text("Setup not saved") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { completionError = null }) { Text("OK") }
            },
        )
    }
}
