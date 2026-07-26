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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
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
 * Root onboarding composable — 8-screen Ironlog setup flow.
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

    fun advance() = scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }

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
                .padding(bottom = 40.dp),
        ) { page ->
                when (page) {
                    0 -> Step1Awakening(onAdvance = { advance() })
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
                        onSkip = { scope.launch { onComplete(draft) } },
                        onApplyTemplate = { template ->
                            scope.launch {
                                runCatching { planRepo.importFullPlan(template.toPlanObject()) }
                                onComplete(draft)
                            }
                        },
                    )
                }
            }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                OnboardingConfig.surfaceDark.copy(alpha = 0.82f),
                                OnboardingConfig.bgDark.copy(alpha = 0.70f),
                            )
                        )
                    )
                    .border(1.dp, OnboardingConfig.cardBorder.copy(alpha = 0.90f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pagerState.pageCount) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (selected) 18.dp else 7.dp, height = 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) OnboardingConfig.accentBlue
                                else OnboardingConfig.cardBorder.copy(alpha = 0.95f),
                            ),
                    )
                }
            }
        }
    }
}
