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
import com.ironlog.app.domain.badges.AppStats
import com.ironlog.app.domain.badges.BadgeDefinitions
import com.ironlog.app.domain.gamification.AthleteCalibration
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
import com.ironlog.app.domain.gamification.dailyWorkoutStreakDays
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
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
)

internal fun totalXpFromLedger(ledgerTotalXp: Long, bonusXp: Long): Long =
    ledgerTotalXp.coerceAtLeast(0L) + bonusXp.coerceAtLeast(0L)

internal fun isCloudAiBadgeActive(
    intelligenceMode: String,
    baseUrl: String,
    modelName: String,
    apiKey: String,
): Boolean = intelligenceMode == "cloud_ai" &&
    baseUrl.isNotBlank() && modelName.isNotBlank() && apiKey.isNotBlank()

internal fun mergedGoalModes(existing: Set<String>, current: String): Set<String> =
    (existing + current).mapNotNull(::canonicalGamificationGoalMode).toSet()

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
) : AndroidViewModel(application) {

    private val xpEngine = XpEngine()
    private val statEngine = StatEngine()
    private val streakEngine = StreakEngine()
    private val ledgerEngine = IronLedgerEngine()
    private val settingsRepo = SettingsRepository()

    private val profileBox get() = boxStore.boxFor(GamificationProfileEntity::class.java)
    private val calibrationBox get() = boxStore.boxFor(AthleteCalibrationEntity::class.java)
    private val ledgerEventBox get() = boxStore.boxFor(IronLedgerEventEntity::class.java)

    private val _uiState = MutableStateFlow(GamificationUiState())
    val uiState: StateFlow<GamificationUiState> = _uiState

    init {
        loadProfile()
    }

    private fun getOrCreateProfile(): GamificationProfileEntity {
        WorkoutImportProvenanceMigration.run()
        return profileBox.query(GamificationProfileEntity_.offlineUserId.equal("local"))
            .build().use { it.findFirst() }
            ?: GamificationProfileEntity(offlineUserId = "local")
                .also { profileBox.put(it) }
    }

    fun loadProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = getOrCreateProfile()
            val badges = profile.unlockedBadges.split(",").filter { it.isNotBlank() }
            val stats = runCatching { Json.decodeFromString<RpgStats>(profile.statsJson) }
                .getOrDefault(RpgStats())
            _uiState.value = GamificationUiState(
                level = profile.level,
                xpInLevel = profile.xpInLevel,
                xpForNextLevel = xpEngine.xpForLevel(profile.level),
                totalXp = profile.totalXp,
                rank = profile.rank,
                streakWeeks = profile.streakWeeks,
                stats = stats,
                activeTitle = profile.activeTitle,
                unlockedBadges = badges,
                latestBadgeTitle = badges.lastOrNull()?.let(::displayTitleForBadgeId),
            )
        }
    }

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
    fun refreshFromHistory(history: List<HistoryEntry>, weeklyGoal: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            refreshFromHistoryNow(history, weeklyGoal)
        }
    }

    private suspend fun refreshFromHistoryNow(history: List<HistoryEntry>, weeklyGoal: Int) {
        val profile = getOrCreateProfile()

            val recoveryCircuitCompletions: Map<String, Int> = runCatching {
                Json.decodeFromString<Map<String, Int>>(profile.makeupCompletionsJson)
            }.getOrDefault(emptyMap())

            profile.streakWeeks = streakEngine.computeStreakWeeks(history, weeklyGoal, recoveryCircuitCompletions)
            val dailyStreakDays = dailyWorkoutStreakDays(history)

            // Stats
            val stats = statEngine.compute(history, profile.streakWeeks, history.size)
            profile.statsJson = Json.encodeToString(stats)

            val snapshot = ledgerEngine.rebuild(
                history = history,
                weeklyGoal = weeklyGoal,
                calibration = readCalibration(weeklyGoal),
            )
            migrateLegacyBonusXp(profile, snapshot.totalXp)
            val bonusEvents = bonusLedgerEvents()
            val currentWeekKey = currentIsoWeekKey()
            val recoveryCircuitCompletedThisWeek =
                (recoveryCircuitCompletions[currentWeekKey] ?: 0) > 0 ||
                    bonusEvents.any { it.eventId == "recovery:$currentWeekKey" }
            val bonusXp = bonusEvents.sumOf { it.xpDelta.toLong() }.coerceAtLeast(0L)
            val reconciledTotalXp = totalXpFromLedger(snapshot.totalXp, bonusXp)
            val reconciledLevel = ledgerEngine.levelFromTotalXp(reconciledTotalXp)
            val reconciledXpInLevel = ledgerEngine.xpInCurrentLevel(reconciledTotalXp)
            val reconciledXpForNextLevel = ledgerEngine.xpForLevel(reconciledLevel)
            profile.totalXp = reconciledTotalXp
            profile.level = reconciledLevel
            profile.xpInLevel = reconciledXpInLevel
            profile.rank = snapshot.grade.label
            val settingsJson = currentSettingsJson()
            val unlockedBadges = mergedUnlockedBadges(
                existingCsv = profile.unlockedBadges,
                currentGrade = snapshot.grade,
                appBadges = evaluateAppBadges(
                    history = history,
                    snapshot = snapshot,
                    settingsJson = settingsJson,
                ),
            )
            profile.unlockedBadges = unlockedBadges.joinToString(",")
            profile.activeTitle = activeTitleFor(unlockedBadges, snapshot.grade)

            profileBox.put(profile)
            persistLedgerEvents(snapshot.events)
            val badges = unlockedBadges
            val dailyProof = buildDailyProofSummary(
                history = history,
                hasActivePlan = hasAnyPlan(),
                activeWorkoutDayName = settingsRepo.getString("active_workout_day_name"),
                readinessScore = computeReadinessScore(history),
            )
            val xpLogs = buildList {
                addAll(bonusEvents.sortedByDescending { it.occurredAt }.map(::bonusEventLogEntry))
                addAll(
                    snapshot.events.map {
                        XpLogEntry(
                            kind = it.kind,
                            title = it.title,
                            detail = it.detail,
                            xp = it.xp,
                        )
                    }
                )
            }
            _uiState.value = GamificationUiState(
                level = reconciledLevel,
                xpInLevel = reconciledXpInLevel,
                xpForNextLevel = reconciledXpForNextLevel,
                totalXp = reconciledTotalXp,
                rank = snapshot.grade.label,
                dailyStreakDays = dailyStreakDays,
                streakWeeks = profile.streakWeeks,
                stats = toRpgStats(snapshot.stats),
                activeTitle = profile.activeTitle,
                unlockedBadges = badges,
                latestBadgeTitle = badges.lastOrNull()?.let(::displayTitleForBadgeId),
                integrityScore = snapshot.integrityScore,
                xpLogs = xpLogs,
                nextGradeLabel = snapshot.nextGrade?.label,
                nextGradeGates = snapshot.nextGradeGates,
                ledgerStats = snapshot.stats,
                dailyProofStatus = dailyProof.status,
                dailyProofHeadline = dailyProof.headline,
                dailyProofDetail = dailyProof.detail,
                dailyProofPrimaryActionLabel = dailyProof.primaryActionLabel,
                dailyProofPrimaryRoute = dailyProof.primaryRoute,
                foxExpressionId = dailyProof.foxExpressionId,
                recoveryCircuitCompletedThisWeek = recoveryCircuitCompletedThisWeek,
            )
    }

    /**
     * Record a completed recovery circuit for [isoWeekKey] and award XP.
     */
    fun completeRecoveryCircuit(
        isoWeekKey: String,
        circuitId: String,
        history: List<HistoryEntry>,
        weeklyGoal: Int,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = getOrCreateProfile()
            val recoveryCircuitCompletions = runCatching {
                Json.decodeFromString<Map<String, Int>>(profile.makeupCompletionsJson).toMutableMap()
            }.getOrDefault(mutableMapOf())
            val eventId = "recovery:$isoWeekKey"
            val existingEvent = ledgerEventBox.query(IronLedgerEventEntity_.eventId.equal(eventId))
                .build().use { it.findFirst() }
            val canRecord = canRecordRecoveryCircuit(
                completions = recoveryCircuitCompletions,
                isoWeekKey = isoWeekKey,
                hasDurableEvent = existingEvent?.invalidated == false,
            )

            if (canRecord) {
                val gained = xpEngine.xpForAction(XpAction.RECOVERY_CIRCUIT)
                boxStore.runInTx {
                    recoveryCircuitCompletions[isoWeekKey] = 1
                    profile.makeupCompletionsJson = Json.encodeToString(recoveryCircuitCompletions as Map<String, Int>)
                    profile.weeklyXp += gained
                    profileBox.put(profile)
                    ledgerEventBox.put(
                        IronLedgerEventEntity(
                            eventId = eventId,
                            sourceType = "bonus",
                            sourceId = circuitId,
                            eventKind = "recovery_circuit",
                            occurredAt = System.currentTimeMillis(),
                            xpDelta = gained,
                            metadataJson = JSONObject()
                                .put("title", "Recovery proof logged")
                                .put("detail", "Protected $isoWeekKey with a recovery circuit")
                                .put("circuitId", circuitId)
                                .toString(),
                        )
                    )
                }
            } else if ((recoveryCircuitCompletions[isoWeekKey] ?: 0) == 0) {
                recoveryCircuitCompletions[isoWeekKey] = 1
                profile.makeupCompletionsJson = Json.encodeToString(recoveryCircuitCompletions as Map<String, Int>)
                profileBox.put(profile)
            }

            refreshFromHistoryNow(history, weeklyGoal)
            WidgetUpdateWorker.enqueueOneTime(getApplication())
        }
    }

    private suspend fun migrateLegacyBonusXp(profile: GamificationProfileEntity, ledgerTotalXp: Long) {
        val migrationKey = "gamification_bonus_events_v2_migrated"
        if (settingsRepo.getBoolean(migrationKey, false)) return
        val legacyBonus = (profile.totalXp - ledgerTotalXp).coerceAtLeast(0L)
        if (legacyBonus > 0L) {
            ledgerEventBox.put(
                IronLedgerEventEntity(
                    eventId = "legacy:bonus-v1",
                    sourceType = "bonus",
                    sourceId = "legacy-profile",
                    eventKind = "legacy_bonus",
                    occurredAt = System.currentTimeMillis(),
                    xpDelta = legacyBonus.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    metadataJson = JSONObject()
                        .put("title", "Legacy bonus XP")
                        .put("detail", "Preserved XP earned outside workout proof")
                        .toString(),
                )
            )
        }
        settingsRepo.setBoolean(migrationKey, true)
    }

    private fun bonusLedgerEvents(): List<IronLedgerEventEntity> = ledgerEventBox.all
        .filter { !it.invalidated && it.sourceType == "bonus" }

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
        runCatching {
            val box = ledgerEventBox
            val currentIds = events.map { "${it.sourceId}:${it.kind}" }.toSet()
            box.all
                .filter { it.sourceType == "workout" || it.sourceType == "exercise" }
                .filter { it.eventId !in currentIds && !it.invalidated }
                .forEach { stale ->
                    stale.invalidated = true
                    box.put(stale)
                }
            events.forEach { event ->
                val eventId = "${event.sourceId}:${event.kind}"
                val existing = box.query(IronLedgerEventEntity_.eventId.equal(eventId))
                    .build().use { it.findFirst() }
                val entity = existing ?: IronLedgerEventEntity()
                entity.eventId = eventId
                entity.sourceType = if (event.sourceId.contains(':')) "exercise" else "workout"
                entity.sourceId = event.sourceId
                entity.eventKind = event.kind
                entity.invalidated = false
                entity.occurredAt = com.ironlog.app.domain.gamification.parseHistoryInstant(event.occurredAt)
                    ?.toEpochMilli() ?: 0L
                entity.xpDelta = event.xp
                entity.trustScore = event.trust
                entity.metadataJson = org.json.JSONObject()
                    .put("title", event.title)
                    .put("detail", event.detail)
                    .toString()
                box.put(entity)
            }
        }
    }

    private suspend fun readCalibration(weeklyGoal: Int): AthleteCalibration {
        val entity = calibrationBox.query(AthleteCalibrationEntity_.offlineUserId.equal("local"))
            .build().use { it.findFirst() }
        suspend fun settingInt(key: String): Int =
            runCatching { settingsRepo.getSettingString(key)?.toIntOrNull() ?: 0 }.getOrDefault(0)
        val historicalTrainingDays = entity?.historicalTrainingDaysPerWeek?.takeIf { it in 1..7 }
            ?: settingInt("baseline_historical_training_days_per_week").takeIf { it in 1..7 }
            ?: 3
        return AthleteCalibration(
            trainingAgeMonths = entity?.trainingAgeMonths ?: settingInt("baseline_training_age_months"),
            historicalTrainingDaysPerWeek = historicalTrainingDays,
            importedHistory = entity?.importedHistory ?: settingsRepo.getBoolean("ledger_imported_history", false),
            weeklyGoalDays = entity?.weeklyGoalDays ?: weeklyGoal,
            bodyweightKg = entity?.bodyweightKg ?: settingInt("baseline_bodyweight_kg").takeIf { it > 0 }?.toDouble(),
            hasPastTraining = entity?.hasPastTraining ?: settingsRepo.getBoolean("baseline_has_past_training", false),
            hasGymAccess = entity?.hasGymAccess ?: settingsRepo.getBoolean("baseline_has_gym_access", true),
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

    private fun titleForGrade(grade: IronGrade): String = when (grade) {
        IronGrade.UNCALIBRATED -> "Ledger Initiate"
        IronGrade.GRAPHITE -> "Graphite Trainee"
        IronGrade.IRON -> "Iron Regular"
        IronGrade.STEEL -> "Steel Builder"
        IronGrade.TITANIUM -> "Titanium Athlete"
        IronGrade.OBSIDIAN -> "Obsidian Veteran"
        IronGrade.IRIDIUM -> "Iridium Specialist"
        IronGrade.AETHER -> "Aether Standard"
        IronGrade.APEX -> "Apex Ledger"
    }

    private suspend fun currentSettingsJson(): JSONObject =
        runCatching { JSONObject(settingsRepo.getString("ironlog_settings") ?: "{}") }
            .getOrDefault(JSONObject())

    private fun hasAnyPlan(): Boolean =
        boxStore.boxFor(PlanEntity::class.java).query().build().use { it.count() > 0 }

    private fun computeReadinessScore(history: List<HistoryEntry>): Int? {
        if (history.isEmpty()) return null
        val readiness = com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
            .readinessByRegion(
                history.sortedByDescending { com.ironlog.app.domain.gamification.parseHistoryInstant(it.date) }.take(60)
            )
        return com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
            .score(readiness)
            .score
    }

    private suspend fun evaluateAppBadges(
        history: List<HistoryEntry>,
        snapshot: IronLedgerSnapshot,
        settingsJson: JSONObject,
    ): Set<String> {
        val verifiedHistory = history.filterNot { it.imported }
        val totalVolumeKg = verifiedHistory.sumOf { it.volume }
        val dayStreak = dailyWorkoutStreakDays(verifiedHistory)
        val currentRank = legacyRankForBadge(snapshot.grade)
        val daysSinceFirstWorkout = verifiedHistory.mapNotNull { com.ironlog.app.domain.gamification.parseHistoryInstant(it.date) }
            .minOrNull()
            ?.let { java.time.temporal.ChronoUnit.DAYS.between(it.atZone(java.time.ZoneId.systemDefault()).toLocalDate(), java.time.LocalDate.now()).toInt() }
            ?: 0
        val createdPlan = hasAnyPlan()
        val usedRestTimer = verifiedHistory.any { entry ->
            entry.exercises.any { exercise ->
                exercise.sets.any { set -> set.restSeconds > 0 }
            }
        }
        val hasLoggedPr = snapshot.events.any { it.kind == "pr" }
        val providerPreset = settingsJson.optString("cloudAiProviderPreset").ifBlank { "custom" }
        val cloudAiActivated = isCloudAiBadgeActive(
            intelligenceMode = settingsJson.optString("intelligenceMode"),
            baseUrl = settingsJson.optString("cloudAiBaseUrl"),
            modelName = settingsJson.optString("cloudAiModelName"),
            apiKey = CloudAiKeyStore.load(getApplication(), providerPreset),
        )
        val goalMode = settingsJson.optString("goalMode").ifBlank { "hypertrophy" }
        val goalModesUsed = recordGoalMode(goalMode)
        val prWorkoutIds = snapshot.events
            .filter { it.kind == "pr" }
            .map { it.sourceId.substringBefore(':') }
            .toSet()
        var consecutivePrStreak = 0
        for (workout in verifiedHistory.sortedByDescending { it.date }) {
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

    private suspend fun recordGoalMode(current: String): Set<String> {
        val key = "gamification_goal_modes_used"
        val existing = runCatching {
            val array = JSONArray(settingsRepo.getString(key) ?: "[]")
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().lowercase().takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
        val merged = mergedGoalModes(existing, current)
        if (merged != existing) {
            settingsRepo.setString(key, JSONArray(merged.sorted()).toString(), "json")
        }
        return merged
    }

    private fun activeTitleFor(unlockedBadges: List<String>, grade: IronGrade): String {
        val latest = unlockedBadges.lastOrNull() ?: return titleForGrade(grade)
        return displayTitleForBadgeId(latest)
    }

    private fun badgeTitleForId(id: String): String =
        BadgeDefinitions.all.firstOrNull { it.id == id }?.title ?: id

    private fun displayTitleForBadgeId(id: String): String {
        BadgeDefinitions.all.firstOrNull { it.id == id }?.let { return it.title }
        IronGrade.entries.firstOrNull { it.label == id }?.let { return titleForGrade(it) }
        return id.replace('_', ' ').replaceFirstChar(Char::titlecase)
    }

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
    val existing = existingCsv.split(",").map(String::trim).filter(String::isNotBlank).distinct()
    val gradeBadges = IronGrade.entries
        .filter { it != IronGrade.UNCALIBRATED && it.ordinal <= currentGrade.ordinal }
        .map { it.label }
    val orderedAppBadges = BadgeDefinitions.all.map { it.id }.filter { it in appBadges }
    val knownGradeBadges = IronGrade.entries.map { it.label }.toSet()
    val knownAppBadges = BadgeDefinitions.all.map { it.id }.toSet()
    val validKnownBadges = gradeBadges.toSet() + orderedAppBadges.toSet()
    val reconciledExisting = existing.filter { badge ->
        badge in validKnownBadges || (badge !in knownGradeBadges && badge !in knownAppBadges)
    }
    return (reconciledExisting + gradeBadges + orderedAppBadges).distinct()
}

internal fun currentIsoWeekKey(date: java.time.LocalDate = java.time.LocalDate.now()): String {
    val fields = java.time.temporal.WeekFields.ISO
    return "%04d-W%02d".format(
        date.get(fields.weekBasedYear()),
        date.get(fields.weekOfWeekBasedYear()),
    )
}
