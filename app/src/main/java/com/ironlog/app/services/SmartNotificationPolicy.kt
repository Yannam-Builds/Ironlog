package com.ironlog.app.services

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity
import com.ironlog.app.data.objectbox.GamificationProfileEntity_
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import com.ironlog.app.data.repository.HistoryRepository
import com.ironlog.app.domain.gamification.CreditedProof
import com.ironlog.app.domain.gamification.StreakEngine
import com.ironlog.app.domain.gamification.parseHistoryLocalDate
import com.ironlog.app.ui.model.HistoryEntry
import io.objectbox.Box
import io.objectbox.BoxStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

internal object NotificationKeys {
    const val ENABLED = "notifications_enabled"
    const val TRAINING_ENABLED = "training_reminders_enabled"
    const val PROFILE = "notification_profile"
    const val LEGACY_PROFILE = "notificationProfile"
    const val REMINDER_MINUTES = "dailyReminderTimeMinutes"
    const val QUIET_START = "quietHoursStartMinutes"
    const val QUIET_END = "quietHoursEndMinutes"
    const val LAST_SUCCESSFUL_BACKUP = "last_successful_backup_ms"
    const val LAST_AUTO_BACKUP = "last_auto_backup_ms"
    const val AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    const val LAST_APP_FOREGROUND_EPOCH_MS = "notification_last_app_foreground_epoch_ms"
    const val DEFAULT_REMINDER_MINUTES = 8 * 60
    const val DEFAULT_QUIET_START_MINUTES = 22 * 60
    const val DEFAULT_QUIET_END_MINUTES = 8 * 60
}

data class NotificationCandidate(
    val key: String,
    val topic: String,
    val title: String,
    val body: String,
    val score: Int,
    val route: String,
)

internal data class PolicyDecision(val allowed: Boolean, val reason: String? = null)

internal object NotificationPolicyEngine {
    private const val DECISION_LOG_KEY = "decision_log_json"
    private const val SNOOZE_UNTIL_KEY = "snooze_until"
    private const val DAILY_OVERRIDE_KEY = "maxNotificationsPerDayOverride"
    private const val WEEKLY_OVERRIDE_KEY = "maxNotificationsPerWeekOverride"
    private const val COOLDOWN_HOURS_KEY = "cooldownHours"
    private const val LOG_LIMIT = 64

    private data class Profile(
        val dailyCap: Int,
        val weeklyCap: Int,
        val baseCooldownHours: Int,
        val topicCooldown: Map<String, Int>,
    )

    private val conservative = Profile(1, 2, 24, mapOf("training" to 18, "streak" to 20, "bodyweight" to 24, "backup" to 48))
    private val balanced = Profile(1, 3, 12, mapOf("training" to 12, "streak" to 16, "bodyweight" to 18, "backup" to 36))
    private val aggressive = Profile(1, 5, 8, mapOf("training" to 8, "streak" to 12, "bodyweight" to 12, "backup" to 24))

    fun shouldSend(
        settingsBox: Box<AppSettingEntity>,
        key: String,
        topic: String,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PolicyDecision {
        val snoozeUntil = readString(settingsBox, SNOOZE_UNTIL_KEY)?.toLongOrNull()
        if (snoozeUntil != null && snoozeUntil > nowMs) return PolicyDecision(false, "snoozed")
        val profile = profile(settingsBox)
        val log = readDecisionLog(settingsBox)
        val dailyCap = (readString(settingsBox, DAILY_OVERRIDE_KEY)?.toDoubleOrNull()?.toInt() ?: profile.dailyCap).coerceIn(1, 3)
        val weeklyCap = (readString(settingsBox, WEEKLY_OVERRIDE_KEY)?.toDoubleOrNull()?.toInt() ?: profile.weeklyCap).coerceIn(1, 7)
        if (countToday(log, nowMs, zoneId) >= dailyCap) return PolicyDecision(false, "daily_cap")
        if (countThisWeek(log, nowMs, zoneId) >= weeklyCap) return PolicyDecision(false, "weekly_cap")
        if (isKeyCooldown(log, key, nowMs, settingsBox, profile)) return PolicyDecision(false, "cooldown_or_topic_gate")
        if (isTopicCooldown(log, topic, nowMs, settingsBox, profile)) return PolicyDecision(false, "cooldown_or_topic_gate")
        return PolicyDecision(true, "selected")
    }

    fun chooseCandidate(
        settingsBox: Box<AppSettingEntity>,
        candidates: List<NotificationCandidate>,
        nowMs: Long = System.currentTimeMillis(),
    ): NotificationCandidate? {
        val profile = profile(settingsBox)
        val log = readDecisionLog(settingsBox)
        return candidates.sortedByDescending { it.score }.firstOrNull { candidate ->
            !isKeyCooldown(log, candidate.key, nowMs, settingsBox, profile) &&
                !isTopicCooldown(log, candidate.topic, nowMs, settingsBox, profile)
        }
    }

    fun recordSent(settingsBox: Box<AppSettingEntity>, key: String, topic: String) {
        appendDecision(settingsBox, JSONObject()
            .put("outcome", "sent").put("key", key).put("topic", topic)
            .put("reason", "selected").put("at", Instant.now().toString()))
    }

    fun recordSuppressed(settingsBox: Box<AppSettingEntity>, key: String, topic: String, reason: String) {
        val latest = readDecisionLog(settingsBox).optJSONObject(0)
        val duplicate = latest != null && latest.optString("outcome") == "suppressed" &&
            latest.optString("key") == key && latest.optString("reason") == reason &&
            System.currentTimeMillis() - parseTime(latest.optString("at")) <= 6L * 60L * 60L * 1000L
        if (!duplicate) {
            appendDecision(settingsBox, JSONObject()
                .put("outcome", "suppressed").put("key", key).put("topic", topic)
                .put("reason", reason).put("at", Instant.now().toString()))
        }
    }

    private fun profile(settingsBox: Box<AppSettingEntity>): Profile {
        val selected = readString(settingsBox, NotificationKeys.PROFILE)
            ?: readString(settingsBox, NotificationKeys.LEGACY_PROFILE)
        return when (selected.orEmpty().lowercase()) {
            "conservative" -> conservative
            "aggressive" -> aggressive
            else -> balanced
        }
    }

    private fun appendDecision(settingsBox: Box<AppSettingEntity>, entry: JSONObject) {
        val current = readDecisionLog(settingsBox)
        val merged = JSONArray().put(entry)
        for (index in 0 until current.length()) {
            if (merged.length() >= LOG_LIMIT) break
            merged.put(current.opt(index))
        }
        writeSetting(settingsBox, DECISION_LOG_KEY, merged.toString(), "json")
    }

    private fun countToday(log: JSONArray, nowMs: Long, zoneId: ZoneId): Int {
        val start = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
        return countSentBetween(log, start, nowMs)
    }

    private fun countThisWeek(log: JSONArray, nowMs: Long, zoneId: ZoneId): Int {
        val monday = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val start = monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        return countSentBetween(log, start, nowMs)
    }

    private fun countSentBetween(log: JSONArray, startMs: Long, nowMs: Long): Int {
        var count = 0
        for (index in 0 until log.length()) {
            val entry = log.optJSONObject(index) ?: continue
            val sentAt = parseTime(entry.optString("at"))
            if (entry.optString("outcome") == "sent" && sentAt in startMs..nowMs) count++
        }
        return count
    }

    private fun isKeyCooldown(log: JSONArray, key: String, nowMs: Long, settingsBox: Box<AppSettingEntity>, profile: Profile): Boolean {
        val hours = (readString(settingsBox, COOLDOWN_HOURS_KEY)?.toDoubleOrNull()?.toInt()
            ?: profile.baseCooldownHours).coerceAtLeast(1)
        val latest = findLatestSent(log, "key", key) ?: return false
        val elapsed = nowMs - latest
        return elapsed >= 0L && elapsed < hours * 3_600_000L
    }

    private fun isTopicCooldown(log: JSONArray, topic: String, nowMs: Long, settingsBox: Box<AppSettingEntity>, profile: Profile): Boolean {
        val custom = readString(settingsBox, "perTopicCooldownHours")
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val hours = custom?.optDouble(topic, Double.NaN)?.takeIf { it.isFinite() }?.toInt()
            ?: profile.topicCooldown[topic] ?: profile.baseCooldownHours
        val latest = findLatestSent(log, "topic", topic) ?: return false
        val elapsed = nowMs - latest
        return elapsed >= 0L && elapsed < hours * 3_600_000L
    }

    private fun findLatestSent(log: JSONArray, field: String, value: String): Long? {
        for (index in 0 until log.length()) {
            val entry = log.optJSONObject(index) ?: continue
            if (entry.optString("outcome") == "sent" && entry.optString(field) == value) {
                return parseTime(entry.optString("at"))
            }
        }
        return null
    }

    private fun readDecisionLog(settingsBox: Box<AppSettingEntity>): JSONArray =
        readString(settingsBox, DECISION_LOG_KEY)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()

    private fun parseTime(iso: String?): Long = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
}

object SmartCandidateGenerator {
    fun buildCandidates(
        store: BoxStore,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        historyOverride: List<HistoryEntry>? = null,
    ): List<NotificationCandidate> {
        val workoutBox = store.boxFor(WorkoutEntity::class.java)
        val bodyBox = store.boxFor(BodyMeasurementEntity::class.java)
        val planBox = store.boxFor(PlanEntity::class.java)
        val settingsBox = store.boxFor(AppSettingEntity::class.java)
        val activeWorkoutId = readString(settingsBox, "active_workout_id")
        val hasValidActiveWorkout = activeWorkoutId?.takeIf { it.isNotBlank() }?.let { uid ->
            workoutBox.query(WorkoutEntity_.uid.equal(uid)).build().use { query ->
                query.findFirst()?.status == "active"
            }
        } == true
        // Re-engagement copy is always wrong while the user is already training. A stale
        // setting alone is not enough to suppress delivery; the referenced row must be active.
        if (hasValidActiveWorkout) return emptyList()
        val candidates = mutableListOf<NotificationCandidate>()
        val now = Instant.ofEpochMilli(nowMs)
        val today = now.atZone(zoneId).toLocalDate()
        val rawWorkouts = workoutBox.query(WorkoutEntity_.status.equal("completed"))
            .orderDesc(WorkoutEntity_.createdAt).build().use { it.find() }
            .filter { it.startedAt in 1..nowMs }
        val history = (historyOverride ?: HistoryRepository(store).completedSnapshotBlocking())
            .filter { entry ->
                com.ironlog.app.domain.gamification.parseHistoryInstant(entry.date, zoneId)
                    ?.let { !it.isAfter(now) } == true
            }
        val creditedHistory = history.filter { CreditedProof.qualifies(it, now, zoneId) }
        val hasWorkoutToday = creditedHistory.any {
            parseHistoryLocalDate(it.date, zoneId) == today
        }
        val targetDays = readWeeklyGoalDays(settingsBox)
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val doneThisWeek = creditedHistory.count {
            parseHistoryLocalDate(it.date, zoneId)?.isBefore(startOfWeek) == false
        }
        val daysLeftIncludingToday = 8 - today.dayOfWeek.value
        val trainingEnabled = readBoolean(settingsBox, NotificationKeys.TRAINING_ENABLED, true)

        if (trainingEnabled && !hasWorkoutToday && doneThisWeek < targetDays &&
            targetDays - doneThisWeek >= daysLeftIncludingToday
        ) {
            val remaining = targetDays - doneThisWeek
            candidates += NotificationCandidate(
                "train_reminder_plan_aware", "training", "Training reminder",
                "$remaining session${if (remaining == 1) "" else "s"} left this week. Schedule the next one when it fits.",
                100, "Home",
            )
        }

        val bodyRows = bodyBox.all
        val hasBodyweightToday = bodyRows.any {
            it.bodyweight != null && it.measuredAt in 1..nowMs &&
                Instant.ofEpochMilli(it.measuredAt).atZone(zoneId).toLocalDate() == today
        }
        val hasMeaningfulWorkout = history.any { entry ->
            entry.exercises.any { exercise -> exercise.sets.any { !it.isWarmup } }
        }
        if (!hasBodyweightToday && hasMeaningfulWorkout) {
            candidates += NotificationCandidate(
                "bw_reminder", "bodyweight", "Log body weight",
                "A quick bodyweight entry keeps your trend accurate.", 40, "BodyWeight",
            )
        }

        val recoveryCircuits = readRecoveryCircuitCompletions(store)
        val currentWeekKey = startOfWeek.let { monday ->
            val iso = WeekFields.ISO
            "${monday.get(iso.weekBasedYear())}-W${monday.get(iso.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
        }
        val currentWeekProtected = doneThisWeek >= targetDays ||
            (doneThisWeek == targetDays - 1 && (recoveryCircuits[currentWeekKey] ?: 0) > 0)
        val currentStreak = StreakEngine(zoneId).computeStreakWeeks(
            history = history,
            weeklyGoal = targetDays,
            recoveryCircuitCompletions = recoveryCircuits,
            now = now,
        )
        if (trainingEnabled && currentStreak >= 1 &&
            !hasWorkoutToday && !currentWeekProtected
        ) {
            val remaining = (targetDays - doneThisWeek).coerceAtLeast(1)
            candidates += NotificationCandidate(
                "streak_preserve", "streak", "Streak active",
                if (remaining == 1) {
                    "One more session this week keeps your streak alive."
                } else {
                    "$remaining sessions remain to protect this week's streak."
                },
                55, "Home",
            )
        }

        val autoBackupEnabled = readBoolean(settingsBox, NotificationKeys.AUTO_BACKUP_ENABLED, false)
        val lastBackup = readString(settingsBox, NotificationKeys.LAST_SUCCESSFUL_BACKUP)?.toLongOrNull()
            ?: readString(settingsBox, NotificationKeys.LAST_AUTO_BACKUP)?.toLongOrNull() ?: 0L
        val latestDataChange = maxOf(
            rawWorkouts.maxOfOrNull { maxOf(it.updatedAt, it.createdAt) } ?: 0L,
            bodyRows.maxOfOrNull { maxOf(it.updatedAt, it.createdAt, it.measuredAt) } ?: 0L,
            planBox.all.maxOfOrNull { maxOf(it.updatedAt, it.createdAt) } ?: 0L,
        )
        val overdueWindow = 3L * 24L * 60L * 60L * 1000L
        val backupIsOverdue = if (lastBackup <= 0L) {
            latestDataChange > 0L && nowMs - latestDataChange >= overdueWindow
        } else {
            latestDataChange > lastBackup && nowMs - lastBackup >= overdueWindow
        }
        if (autoBackupEnabled && rawWorkouts.isNotEmpty() && backupIsOverdue) {
            candidates += NotificationCandidate(
                "backup_integrity", "backup", "Backup needs attention",
                "Your latest training changes have not been protected by a successful backup for 3 days.",
                58, "BackupCenter",
            )
        }
        return candidates
    }

    private fun readRecoveryCircuitCompletions(store: BoxStore): Map<String, Int> {
        val raw = store.boxFor(GamificationProfileEntity::class.java)
            .query(GamificationProfileEntity_.offlineUserId.equal("local"))
            .build().use { it.findFirst() }?.makeupCompletionsJson.orEmpty()
        val parsed = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrDefault(JSONObject())
        return buildMap {
            val keys = parsed.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                parsed.optInt(key).takeIf { it > 0 }?.let { put(key, it) }
            }
        }
    }
}

internal fun readString(settingsBox: Box<AppSettingEntity>, key: String): String? =
    settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }?.value

internal fun readBoolean(settingsBox: Box<AppSettingEntity>, key: String, fallback: Boolean): Boolean =
    readString(settingsBox, key)?.equals("true", ignoreCase = true) ?: fallback

internal fun writeSetting(settingsBox: Box<AppSettingEntity>, key: String, value: String, valueType: String) {
    val now = System.currentTimeMillis()
    val row = settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }
    settingsBox.put((row ?: AppSettingEntity()).apply {
        this.key = key
        this.value = value
        this.valueType = valueType
        updatedAt = now
    })
}

internal fun readWeeklyGoalDays(settingsBox: Box<AppSettingEntity>): Int {
    val nested = readString(settingsBox, "ironlog_settings")
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.optInt("weeklyGoalDays", 0)?.takeIf { it in 1..7 }
    return nested ?: readString(settingsBox, "weekly_goal_days")?.toIntOrNull()?.coerceIn(1, 7) ?: 3
}

internal fun isInQuietHours(
    settingsBox: Box<AppSettingEntity>,
    nowMinutes: Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) },
): Boolean {
    val start = readString(settingsBox, NotificationKeys.QUIET_START)?.toDoubleOrNull()?.toInt()
        ?.coerceIn(0, 1439) ?: NotificationKeys.DEFAULT_QUIET_START_MINUTES
    val end = readString(settingsBox, NotificationKeys.QUIET_END)?.toDoubleOrNull()?.toInt()
        ?.coerceIn(0, 1439) ?: NotificationKeys.DEFAULT_QUIET_END_MINUTES
    return when {
        start == end -> false
        start < end -> nowMinutes in start until end
        else -> nowMinutes >= start || nowMinutes < end
    }
}
