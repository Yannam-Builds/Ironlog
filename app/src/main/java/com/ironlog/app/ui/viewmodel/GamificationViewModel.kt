// app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt
package com.ironlog.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ironlog.app.assets.ForgeFoxExpression
import com.ironlog.app.data.objectbox.IronLedgerEventEntity
import com.ironlog.app.data.objectbox.IronLedgerEventEntity_
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity
import com.ironlog.app.data.objectbox.AthleteCalibrationEntity_
import com.ironlog.app.data.objectbox.GamificationProfileEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity_
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.WorkoutImportProvenanceMigration
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.onboardingBaselineBodyweightKg
import com.ironlog.app.data.repository.onboardingBaselineOccurredAtMs
import com.ironlog.app.data.repository.upsertOnboardingBaselineEvent
import com.ironlog.app.domain.badges.AppStats
import com.ironlog.app.domain.badges.BadgeDefinitions
import com.ironlog.app.domain.gamification.AthleteCalibration
import com.ironlog.app.domain.gamification.BaselineCalibrationEngine
import com.ironlog.app.domain.gamification.DailyProofStatus
import com.ironlog.app.domain.gamification.IronGrade
import com.ironlog.app.domain.gamification.IronGradeGate
import com.ironlog.app.domain.gamification.IronLedgerEngine
import com.ironlog.app.domain.gamification.IronLedgerSnapshot
import com.ironlog.app.domain.gamification.IronLedgerStats
import com.ironlog.app.domain.gamification.RpgStats
import com.ironlog.app.domain.gamification.StatEngine
import com.ironlog.app.domain.gamification.StreakEngine
import com.ironlog.app.domain.gamification.XpAction
import com.ironlog.app.domain.gamification.XpEngine
import com.ironlog.app.domain.gamification.buildDailyProofSummary
import com.ironlog.app.domain.gamification.CreditedProof
import com.ironlog.app.domain.gamification.dailyWorkoutStreakDays
import com.ironlog.app.domain.gamification.effectiveOnboardingBaselineAward
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import com.ironlog.app.domain.intelligence.canonicalIntelligenceMode
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.widget.WidgetUpdateWorker
import io.objectbox.BoxStore
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class XpLogEntry(
    val kind: String,
    val title: String,
    val detail: String,
    val xp: Int,
)

data class GamificationUiState(
    val level: Int = 1,
    val xpInLevel: Long = 0L,
    val xpForNextLevel: Long = 100L,
    val totalXp: Long = 0L,
    val rank: String = "E",
    val dailyStreakDays: Int = 0,
    val streakWeeks: Int = 0,
    val stats: RpgStats = RpgStats(),
    val activeTitle: String = "Ledger Initiate",
    val unlockedBadges: List<String> = emptyList(),
    val latestBadgeTitle: String? = null,
    val integrityScore: Double = 1.0,
    val xpLogs: List<XpLogEntry> = emptyList(),
    val nextGradeLabel: String? = null,
    val nextGradeGates: List<IronGradeGate> = emptyList(),
    val ledgerStats: IronLedgerStats = IronLedgerStats(),
    val dailyProofStatus: DailyProofStatus = DailyProofStatus.SETUP,
    val dailyProofHeadline: String = "Set your proof loop",
    val dailyProofDetail: String = "",
    val dailyProofPrimaryActionLabel: String = "Choose a program",
    val dailyProofPrimaryRoute: String = "ProgramPicker",
    val foxExpressionId: String = ForgeFoxExpression.Clipboard.id,
    val recoveryCircuitCompletedThisWeek: Boolean = false,
    val refreshError: String? = null,
)

internal fun totalXpFromLedger(ledgerTotalXp: Long, bonusXp: Long): Long =
    ledgerTotalXp.coerceAtLeast(0L) + bonusXp.coerceAtLeast(0L)

internal fun proofOnlyLedgerXp(snapshotTotalXp: Long, effectiveBaselineXp: Long): Long =
    (snapshotTotalXp - effectiveBaselineXp.coerceAtLeast(0L)).coerceAtLeast(0L)

internal fun combinedBaselineAndEvidenceBadgeIds(
    baselineSupported: Set<String>,
    evaluated: Set<String>,
    historical: Set<String>,
): Set<String> = baselineSupported + evaluated + historical

internal fun isCloudAiBadgeActive(
    intelligenceMode: String,
    baseUrl: String,
    modelName: String,
    apiKey: String,
): Boolean = canonicalIntelligenceMode(intelligenceMode) == "cloud_ai" &&
    baseUrl.isNotBlank() && modelName.isNotBlank() && apiKey.isNotBlank()

internal fun mergedGoalModes(existing: Set<String>, current: String): Set<String> =
    (existing + current).mapNotNull(::canonicalGamificationGoalMode).toSet()

internal data class GoalModeEvidence(
    val goalModes: Set<String>,
    val lastCreditedWorkoutId: String?,
)

/** Settings changes are preferences, not training proof. A mode counts only with a new credited workout. */
internal fun goalModeEvidenceAfterProof(
    existing: Set<String>,
    current: String,
    lastCreditedWorkoutId: String?,
    latestCreditedWorkoutId: String?,
): GoalModeEvidence {
    val canonicalExisting = existing.mapNotNull(::canonicalGamificationGoalMode).toSet()
    if (latestCreditedWorkoutId.isNullOrBlank() || latestCreditedWorkoutId == lastCreditedWorkoutId) {
        return GoalModeEvidence(canonicalExisting, lastCreditedWorkoutId)
    }
    return GoalModeEvidence(
        goalModes = mergedGoalModes(canonicalExisting, current),
        lastCreditedWorkoutId = latestCreditedWorkoutId,
    )
}

internal fun canonicalGamificationGoalMode(value: String): String? = when (value.trim().lowercase()) {
    "strength" -> "strength"
    "hypertrophy" -> "hypertrophy"
    "general_fitness", "general fitness", "performance", "endurance" -> "general_fitness"
    else -> null
}

internal fun canRecordRecoveryCircuit(
    completions: Map<String, Int>,
    isoWeekKey: String,
    hasDurableEvent: Boolean,
): Boolean = (completions[isoWeekKey] ?: 0) <= 0 && !hasDurableEvent

internal fun unlockedBadgesAfterGrade(
    existingCsv: String,
    currentGrade: IronGrade,
): List<String> {
    val existing = existingCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val canonicalGradeBadges = IronGrade.entries
        .filter { it != IronGrade.UNCALIBRATED && it.ordinal <= currentGrade.ordinal }
        .map { it.label }
    return (existing + canonicalGradeBadges).distinct()
}

class GamificationViewModel(
    application: Application,
    private val boxStore: BoxStore,
    private val clock: java.time.Clock = java.time.Clock.systemDefaultZone(),
) : AndroidViewModel(application) {

    private val xpEngine = XpEngine()
    private val statEngine = StatEngine()
    private val streakEngine = StreakEngine()
    private val ledgerEngine = IronLedgerEngine()
    private val mutationMutex = Mutex()
    private val settingsRepo = SettingsRepository(boxStore.boxFor(com.ironlog.app.data.objectbox.AppSettingEntity::class.java))

    private val profileBox get() = boxStore.boxFor(GamificationProfileEntity::class.java)
    private val calibrationBox get() = boxStore.boxFor(AthleteCalibrationEntity::class.java)
    private val ledgerEventBox get() = boxStore.boxFor(IronLedgerEventEntity::class.java)

    private val _uiState = MutableStateFlow(GamificationUiState())
    val uiState: StateFlow<GamificationUiState> = _uiState

    init {
        loadProfile()
        viewModelScope.launch(Dispatchers.IO) {
            com.ironlog.app.data.repository.observeEntityChanges({ boxStore },
                com.ironlog.app.data.objectbox.WorkoutEntity::class.java,
                com.ironlog.app.data.objectbox.WorkoutExerciseEntity::class.java,
                com.ironlog.app.data.objectbox.WorkoutSetEntity::class.java,
                com.ironlog.app.data.objectbox.ExerciseEntity::class.java,
                com.ironlog.app.data.objectbox.ExerciseMuscleEntity::class.java,
                AthleteCalibrationEntity::class.java, PlanEntity::class.java,
            ).collect { refreshFromHistory(emptyList(), 4).join() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.observeStrings(setOf("ironlog_settings", "manual_recovery_input", "active_workout_day_name",
                "gamification_rest_timer_used", "gamification_user_created_plan") +
                com.ironlog.app.domain.intelligence.RECOVERY_REGIONS.map { "pain_flag_$it" })
                .collect { refreshFromHistory(emptyList(), 4).join() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            CloudAiKeyStore.revision.collect { refreshFromHistory(emptyList(), 4).join() }
        }
    }

    private fun getOrCreateProfile(): GamificationProfileEntity {
        return profileBox.query(GamificationProfileEntity_.offlineUserId.equal("local"))
            .build().use { it.findFirst() }
            ?: GamificationProfileEntity(offlineUserId = "local")
                .also { profileBox.put(it) }
    }

    /** Bootstrap follows the same canonical refresh path; cached defaults cannot overwrite derived state. */
    fun loadProfile() { refreshFromHistory(emptyList(), 4) }

    /**
     * Award XP for an action. Persists to ObjectBox and refreshes UI state.
     */
    @Deprecated("Use a durable, idempotent bonus ledger event", level = DeprecationLevel.ERROR)
    private fun awardXp(action: XpAction) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = getOrCreateProfile()
            val gained = xpEngine.xpForAction(action)
            profile.totalXp += gained
            profile.weeklyXp += gained
            profile.level = xpEngine.levelFromTotalXp(profile.totalXp)
            profile.xpInLevel = xpEngine.xpInCurrentLevel(profile.totalXp)
            profile.rank = xpEngine.rankForLevel(profile.level)
            profileBox.put(profile)
            // Update only the XP/level fields — preserve xpLogs, ledgerStats, etc. from refreshFromHistory
            _uiState.update { current ->
                current.copy(
                    level = profile.level,
                    xpInLevel = profile.xpInLevel,
                    xpForNextLevel = xpEngine.xpForLevel(profile.level),
                    totalXp = profile.totalXp,
                    rank = profile.rank,
                )
            }
        }
    }

    /**
     * Recompute streak, stats, and refresh profile from history.
     * Call this after every workout completion.
     */
    @Suppress("UNUSED_PARAMETER")
    fun refreshFromHistory(history: List<HistoryEntry>, weeklyGoal: Int): kotlinx.coroutines.Job =
        viewModelScope.launch(Dispatchers.IO) {
            mutationMutex.withLock {
                try {
                    refreshFromHistoryNow()
                    WidgetUpdateWorker.enqueueOneTime(getApplication())
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    _uiState.update { it.copy(refreshError = "Progress could not refresh. Your saved workouts are safe; retry from the Ledger.") }
                }
            }
        }

    private fun refreshFromHistoryNow() {
        val committed = com.ironlog.app.data.repository.recomputeGamificationAtomically(boxStore) { history ->
            val capturedClock = java.time.Clock.fixed(clock.instant(), clock.zone)
            val now = capturedClock.instant()
            val nowEpochMs = capturedClock.millis()
            val zone = capturedClock.zone
            val engine = IronLedgerEngine(zone, capturedClock)
            val settingsJson = currentSettingsJson()
            val weeklyGoal = settingsJson.optInt("weeklyGoalDays", 4).coerceIn(1, 7)
            val profile = getOrCreateProfile()
            val calibration = readCalibration(weeklyGoal)
            val baseline = BaselineCalibrationEngine().calculate(calibration)
            val baselineOccurredAtMs = onboardingBaselineOccurredAtMs(boxStore, nowEpochMs)
            val effectiveBaseline = effectiveOnboardingBaselineAward(
                baseline = baseline,
                history = history,
                baselineOccurredAt = java.time.Instant.ofEpochMilli(baselineOccurredAtMs),
                zoneId = zone,
            )
            upsertOnboardingBaselineEvent(
                store = boxStore,
                calibration = calibration,
                result = baseline,
                effectiveAward = effectiveBaseline,
                occurredAtMs = baselineOccurredAtMs,
            )
            val snapshot = engine.rebuild(
                history,
                weeklyGoal,
                calibration,
                effectiveBaselineXp = effectiveBaseline.xp,
            )
            com.ironlog.app.data.repository.migrateLegacyBonusXpBlocking(
                boxStore,
                profile.totalXp,
                proofOnlyLedgerXp(snapshot.totalXp, effectiveBaseline.xp),
                nowEpochMs,
            )
            val bonusEvents = bonusLedgerEvents()
            val historicalUnlocks = com.ironlog.app.domain.gamification.historicalBadgeUnlocks(history, now, zone) +
                com.ironlog.app.domain.gamification.historicalPrBadgeUnlocks(history, snapshot.events, now, zone)
            val badgeEvidence = combinedBaselineAndEvidenceBadgeIds(
                baselineSupported = baseline.supportedBadgeIds,
                evaluated = evaluateAppBadges(history, snapshot, settingsJson, capturedClock),
                historical = historicalUnlocks.keys,
            )
            val badges = mergedUnlockedBadges(profile.unlockedBadges, snapshot.grade, badgeEvidence)
            val durable = runCatching { Json.decodeFromString<Map<String, Long>>(profile.badgeUnlocksJson.orEmpty()) }
                .getOrDefault(emptyMap())
            profile.unlockedBadges = badges.joinToString(",")
            profile.badgeUnlocksJson = Json.encodeToString(com.ironlog.app.domain.gamification.mergeBadgeUnlockTimes(
                badges, durable, historicalUnlocks, profile.updatedAt.takeIf { it > 0L } ?: nowEpochMs) as Map<String, Long>)
            profile.rank = snapshot.grade.label
            profile.statsJson = Json.encodeToString(toRpgStats(snapshot.stats))
            // An event failure escapes and rolls back profile, bonus migration and event invalidations together.
            persistLedgerEvents(snapshot.events)
            com.ironlog.app.data.repository.commitGamificationProfile(boxStore, profile, snapshot.totalXp, history, weeklyGoal, capturedClock)
            val painFlags = com.ironlog.app.domain.intelligence.RECOVERY_REGIONS.filter {
                settingsRepo.getStringBlocking("pain_flag_$it") == "true"
            }.toSet()
            val manual = com.ironlog.app.domain.intelligence.RecoveryCheckInCodec.decode(
                settingsRepo.getStringBlocking("manual_recovery_input"), nowEpochMs)
            val daily = buildDailyProofSummary(history, hasAnyPlan(),
                settingsRepo.getStringBlocking("active_workout_day_name"),
                computeReadinessScore(history, painFlags, manual, nowEpochMs, zone),
                nowEpochMs, painFlags, zone)
            GamificationUiState(
                level = profile.level, xpInLevel = profile.xpInLevel,
                xpForNextLevel = if (profile.level >= 100) 0L else engine.xpForLevel(profile.level),
                totalXp = profile.totalXp, rank = profile.rank,
                dailyStreakDays = dailyWorkoutStreakDays(history, nowEpochMs, zone),
                streakWeeks = profile.streakWeeks, stats = toRpgStats(snapshot.stats),
                activeTitle = profile.activeTitle,
                unlockedBadges = profile.unlockedBadges.split(',').filter(String::isNotBlank),
                latestBadgeTitle = com.ironlog.app.domain.gamification.latestEarnedBadgeId(profile.unlockedBadges, profile.badgeUnlocksJson)
                    ?.let(::displayTitleForBadgeId),
                integrityScore = snapshot.integrityScore,
                xpLogs = bonusEvents.sortedByDescending { it.occurredAt }.map(::bonusEventLogEntry) +
                    snapshot.events.map { XpLogEntry(it.kind, it.title, it.detail, it.xp) },
                nextGradeLabel = snapshot.nextGrade?.label, nextGradeGates = snapshot.nextGradeGates,
                ledgerStats = snapshot.stats, dailyProofStatus = daily.status,
                dailyProofHeadline = daily.headline, dailyProofDetail = daily.detail,
                dailyProofPrimaryActionLabel = daily.primaryActionLabel, dailyProofPrimaryRoute = daily.primaryRoute,
                foxExpressionId = daily.foxExpressionId,
                recoveryCircuitCompletedThisWeek = JSONObject(profile.makeupCompletionsJson)
                    .optInt(currentIsoWeekKey(now.atZone(zone).toLocalDate())) > 0,
            )
        }
        _uiState.value = committed
    }

    /**
     * Record a completed recovery circuit for [isoWeekKey] and award XP.
     */
    suspend fun completeRecoveryCircuit(
        isoWeekKey: String,
        circuitId: String,
    ): com.ironlog.app.data.repository.RecoveryCircuitResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val result = com.ironlog.app.data.repository.RecoveryCircuitRepository(boxStore, clock).complete(isoWeekKey, circuitId)
            refreshFromHistoryNow()
            WidgetUpdateWorker.enqueueOneTime(getApplication())
            result
        }
    }

    private fun bonusLedgerEvents(): List<IronLedgerEventEntity> = ledgerEventBox.all
        .filter { !it.invalidated && it.sourceType in setOf("bonus", "onboarding") }

    private fun bonusEventLogEntry(event: IronLedgerEventEntity): XpLogEntry {
        val metadata = runCatching { JSONObject(event.metadataJson) }.getOrDefault(JSONObject())
        return XpLogEntry(
            kind = event.eventKind,
            title = metadata.optString("title").ifBlank { "Bonus XP" },
            detail = metadata.optString("detail").ifBlank { "XP earned outside workout proof" },
            xp = event.xpDelta,
        )
    }

    private fun persistLedgerEvents(events: List<com.ironlog.app.domain.gamification.IronLedgerEvent>) {
        com.ironlog.app.data.repository.persistLedgerSnapshotEvents(boxStore, events, clock.zone)
    }

    private fun readCalibration(weeklyGoal: Int): AthleteCalibration {
        val entity = calibrationBox.query(AthleteCalibrationEntity_.offlineUserId.equal("local"))
            .build().use { it.findFirst() }
        fun settingInt(key: String): Int =
            runCatching { settingsRepo.getStringBlocking(key)?.toIntOrNull() ?: 0 }.getOrDefault(0)
        val historicalTrainingDays = entity?.historicalTrainingDaysPerWeek?.takeIf { it in 1..7 }
            ?: settingInt("baseline_historical_training_days_per_week").takeIf { it in 1..7 }
            ?: 3
        return AthleteCalibration(
            trainingAgeMonths = entity?.trainingAgeMonths ?: settingInt("baseline_training_age_months"),
            historicalTrainingDaysPerWeek = historicalTrainingDays,
            importedHistory = entity?.importedHistory ?: (settingsRepo.getStringBlocking("ledger_imported_history")?.toBooleanStrictOrNull() ?: false),
            weeklyGoalDays = entity?.weeklyGoalDays ?: weeklyGoal,
            bodyweightKg = onboardingBaselineBodyweightKg(boxStore, entity),
            hasPastTraining = entity?.hasPastTraining ?: (settingsRepo.getStringBlocking("baseline_has_past_training")?.toBooleanStrictOrNull() ?: false),
            hasGymAccess = entity?.hasGymAccess ?: (settingsRepo.getStringBlocking("baseline_has_gym_access")?.toBooleanStrictOrNull() ?: true),
            baselinePushups = entity?.baselinePushups ?: settingInt("baseline_pushups"),
            baselinePullups = entity?.baselinePullups ?: settingInt("baseline_pullups"),
            baselineBenchKg = entity?.baselineBenchKg ?: settingInt("baseline_bench_kg"),
            baselineLatPulldownKg = entity?.baselineLatPulldownKg ?: settingInt("baseline_lat_pulldown_kg"),
            baselineMileRunSeconds = entity?.baselineMileRunSeconds ?: settingInt("baseline_mile_run_seconds"),
        )
    }

    private fun toRpgStats(stats: IronLedgerStats): RpgStats = RpgStats(
        str = stats.strength,
        vit = stats.recovery,
        end = stats.endurance,
        agi = stats.agility,
        wis = stats.discipline,
        luk = stats.power,
    )

    private fun titleForGrade(grade: IronGrade): String = com.ironlog.app.domain.gamification.gradeTitle(grade)

    private fun currentSettingsJson(): JSONObject =
        runCatching { JSONObject(settingsRepo.getStringBlocking("ironlog_settings") ?: "{}") }
            .getOrDefault(JSONObject())

    private fun hasAnyPlan(): Boolean =
        boxStore.boxFor(PlanEntity::class.java).query().build().use { it.count() > 0 }

    private fun computeReadinessScore(
        history: List<HistoryEntry>, painFlags: Set<String>,
        manualInput: com.ironlog.app.domain.intelligence.ManualRecoveryInput?, nowEpochMs: Long, zone: java.time.ZoneId,
    ): Int? {
        val readiness = com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
            .readinessByRegion(
                history, painFlags, nowEpochMs, zoneId = zone
            )
        return com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
            .score(readiness, manualInput, nowEpochMs)
            .scoreOrNull
    }

    private fun evaluateAppBadges(
        history: List<HistoryEntry>,
        snapshot: IronLedgerSnapshot,
        settingsJson: JSONObject,
        capturedClock: java.time.Clock,
    ): Set<String> {
        val verifiedHistory = history.filter { CreditedProof.qualifies(it, capturedClock.instant(), capturedClock.zone) }
        val totalVolumeKg = verifiedHistory.sumOf { it.volume }
        val dayStreak = dailyWorkoutStreakDays(verifiedHistory, capturedClock.millis(), capturedClock.zone)
        val currentRank = legacyRankForBadge(snapshot.grade)
        val daysSinceFirstWorkout = verifiedHistory.mapNotNull { com.ironlog.app.domain.gamification.parseHistoryInstant(it.date, capturedClock.zone) }
            .minOrNull()
            ?.let { java.time.temporal.ChronoUnit.DAYS.between(it.atZone(capturedClock.zone).toLocalDate(), capturedClock.instant().atZone(capturedClock.zone).toLocalDate()).toInt() }
            ?: 0
        val createdPlan = settingsRepo.getStringBlocking("gamification_user_created_plan")
            ?.toBooleanStrictOrNull() ?: false
        val usedRestTimer = (settingsRepo.getStringBlocking("gamification_rest_timer_used")?.toBooleanStrictOrNull() ?: false)
        val hasLoggedPr = snapshot.events.any { it.kind == "pr" }
        val providerPreset = settingsJson.optString("cloudAiProviderPreset").ifBlank { "custom" }
        val cloudAiActivated = isCloudAiBadgeActive(
            intelligenceMode = canonicalIntelligenceMode(settingsJson.optString("intelligenceMode")),
            baseUrl = settingsJson.optString("cloudAiBaseUrl"),
            modelName = settingsJson.optString("cloudAiModelName"),
            apiKey = CloudAiKeyStore.load(getApplication(), providerPreset),
        )
        val goalMode = settingsJson.optString("goalMode").ifBlank { "hypertrophy" }
        val goalModesUsed = recordGoalMode(goalMode, verifiedHistory, capturedClock)
        val prWorkoutIds = snapshot.events
            .filter { it.kind == "pr" }
            .mapNotNull { event -> event.workoutId ?: verifiedHistory.map { it.id }.sortedByDescending(String::length).firstOrNull { event.sourceId.startsWith("$it:") } }
            .toSet()
        var consecutivePrStreak = 0
        for (workout in verifiedHistory.sortedByDescending { com.ironlog.app.domain.gamification.parseHistoryInstant(it.date, capturedClock.zone) }) {
            if (workout.id in prWorkoutIds) consecutivePrStreak++ else break
        }

        return BadgeDefinitions.evaluate(
            AppStats(
                totalWorkouts = verifiedHistory.size,
                currentStreak = dayStreak,
                daysSinceFirstWorkout = daysSinceFirstWorkout,
                totalVolumeKg = totalVolumeKg,
                hasLoggedPR = hasLoggedPr,
                usedRestTimer = usedRestTimer,
                createdPlan = createdPlan,
                cloudAiActivated = cloudAiActivated,
                goalModesUsed = goalModesUsed,
                currentRank = currentRank,
                weeksConsistent = snapshot.qualifyingWeeks,
                consecutiveProgressionWorkouts = consecutivePrStreak,
            )
        )
    }

    private fun recordGoalMode(
        current: String,
        verifiedHistory: List<HistoryEntry>,
        capturedClock: java.time.Clock,
    ): Set<String> {
        val key = "gamification_goal_modes_used"
        val markerKey = "gamification_goal_mode_last_workout_id"
        val existing = runCatching {
            val array = JSONArray(settingsRepo.getStringBlocking(key) ?: "[]")
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().lowercase().takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
        val latestWorkoutId = verifiedHistory.maxWithOrNull(
            compareBy<HistoryEntry>(
                { com.ironlog.app.domain.gamification.parseHistoryInstant(it.date, capturedClock.zone) ?: java.time.Instant.MIN },
                { it.id },
            )
        )?.id
        val evidence = goalModeEvidenceAfterProof(
            existing = existing,
            current = current,
            lastCreditedWorkoutId = settingsRepo.getStringBlocking(markerKey),
            latestCreditedWorkoutId = latestWorkoutId,
        )
        if (evidence.goalModes != existing) {
            settingsRepo.setStringBlocking(key, JSONArray(evidence.goalModes.sorted()).toString(), "json")
        }
        if (!evidence.lastCreditedWorkoutId.isNullOrBlank() &&
            evidence.lastCreditedWorkoutId != settingsRepo.getStringBlocking(markerKey)
        ) {
            settingsRepo.setStringBlocking(markerKey, evidence.lastCreditedWorkoutId)
        }
        return evidence.goalModes
    }

    private fun activeTitleFor(unlockedBadges: List<String>, grade: IronGrade): String {
        val latest = unlockedBadges.lastOrNull() ?: return titleForGrade(grade)
        return displayTitleForBadgeId(latest)
    }

    private fun badgeTitleForId(id: String): String =
        BadgeDefinitions.all.firstOrNull { it.id == id }?.title ?: id

    private fun displayTitleForBadgeId(id: String): String = com.ironlog.app.domain.gamification.earnedBadgeTitle(id)

    private fun legacyRankForBadge(grade: IronGrade): String = when (grade) {
        IronGrade.APEX, IronGrade.AETHER, IronGrade.IRIDIUM, IronGrade.OBSIDIAN -> "S"
        IronGrade.TITANIUM -> "A"
        IronGrade.STEEL -> "B"
        IronGrade.IRON -> "C"
        IronGrade.GRAPHITE -> "D"
        IronGrade.UNCALIBRATED -> "E"
    }
}

class GamificationViewModelFactory(
    private val application: Application,
    private val boxStore: BoxStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GamificationViewModel::class.java)) {
            return GamificationViewModel(application, boxStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

internal fun mergedUnlockedBadges(
    existingCsv: String,
    currentGrade: IronGrade,
    appBadges: Set<String>,
): List<String> {
    val existing = existingCsv.split(",").map(String::trim)
        .filter { it.isNotBlank() && it != "s_rank" }
        .distinct()
    val gradeBadges = IronGrade.entries
        .filter { it != IronGrade.UNCALIBRATED && it.ordinal <= currentGrade.ordinal }
        .map { it.label }
    val orderedAppBadges = BadgeDefinitions.all.map { it.id }.filter { it in appBadges }
    return (existing + gradeBadges + orderedAppBadges).distinct()
}

internal fun creditedSessionsInWeek(history: List<HistoryEntry>, isoWeekKey: String): Int = history.count { workout ->
    CreditedProof.qualifies(workout) &&
        com.ironlog.app.domain.gamification.parseHistoryLocalDate(workout.date)?.let(::currentIsoWeekKey) == isoWeekKey
}

internal fun currentIsoWeekKey(date: java.time.LocalDate = java.time.LocalDate.now()): String {
    val fields = java.time.temporal.WeekFields.ISO
    return "%04d-W%02d".format(
        date.get(fields.weekBasedYear()),
        date.get(fields.weekOfWeekBasedYear()),
    )
}
