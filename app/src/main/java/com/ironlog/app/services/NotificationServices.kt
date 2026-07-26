package com.ironlog.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ironlog.app.MainActivity
import com.ironlog.app.R
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject
import com.ironlog.app.data.objectbox.BodyMeasurementEntity
import com.ironlog.app.data.objectbox.BodyMeasurementEntity_
import com.ironlog.app.data.objectbox.PlanEntity
import com.ironlog.app.data.objectbox.PlanEntity_
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.objectbox.WorkoutEntity_
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Serializable
data class PendingNotificationAction(
    val actionId: String? = null,
    val pressActionId: String? = null,
    val workoutId: String? = null,
    val setId: String? = null,
    val rawJson: String? = null,
    val storedAt: Long? = null,
)

/** Native equivalent of workoutNotificationBridge.consumePendingAction(). */
object PendingWorkoutNotificationBridge {
    private const val PREFS = "ironlog_pending_notification"
    private const val KEY = "pending_action"
    private const val MAX_PENDING_AGE_MS = 90_000L
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun consumePendingAction(context: Context): PendingNotificationAction? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return@withContext null
        prefs.edit().remove(KEY).apply()
        val parsed = runCatching { json.decodeFromString(PendingNotificationAction.serializer(), raw) }
            .getOrElse { PendingNotificationAction(rawJson = raw) }
        val storedAt = parsed.storedAt ?: 0L
        if (storedAt > 0L && (System.currentTimeMillis() - storedAt) > MAX_PENDING_AGE_MS) return@withContext null
        parsed
    }

    fun savePendingAction(context: Context, action: PendingNotificationAction) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.encodeToString(action))
            .apply()
    }
}

/** Native equivalent of notificationScheduler.handleNotificationAction(). */
object NotificationActionRouter {
    object Actions {
        const val SKIP_REST = "ironlog.skip_rest"
        const val ADD_30S = "ironlog.add_30s"
        const val FINISH_WORKOUT = "ironlog.finish_workout"
        const val START_WORKOUT = "ironlog.start_workout"
        const val SNOOZE_1HR = "ironlog.snooze_1hr"
        const val LOG_WEIGHT = "ironlog.log_weight"
        const val VIEW_PROGRESS = "ironlog.view_progress"
    }

    suspend fun handleNotificationAction(
        actionId: String,
        payload: PendingNotificationAction,
        isForeground: Boolean,
    ) {
        val settings = com.ironlog.app.data.repository.SettingsRepository()
        when (actionId) {
            Actions.SKIP_REST,
            Actions.ADD_30S,
            Actions.FINISH_WORKOUT -> {
                settings.setString("pending_workout_action", actionId)
            }
            Actions.START_WORKOUT -> {
                settings.setString("pending_nav_route", "Tabs")
            }
            Actions.SNOOZE_1HR -> {
                val until = System.currentTimeMillis() + 60L * 60L * 1000L
                settings.setString("snooze_until", until.toString())
                settings.setString("pending_nav_route", "Tabs")
            }
            Actions.LOG_WEIGHT -> {
                settings.setString("pending_nav_route", "BodyWeight")
            }
            Actions.VIEW_PROGRESS -> {
                settings.setString("pending_nav_route", "Stats")
            }
        }
    }
}

/** Native equivalent of Notifee background ACTION_PRESS handler from index.js. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actionId = intent.getStringExtra("actionId") ?: intent.action ?: return
        PendingWorkoutNotificationBridge.savePendingAction(
            context,
            PendingNotificationAction(
                actionId = actionId,
                pressActionId = intent.getStringExtra("pressActionId"),
                workoutId = intent.getStringExtra("workoutId"),
                setId = intent.getStringExtra("setId"),
                storedAt = System.currentTimeMillis(),
            ),
        )
    }
}

object WorkoutNotificationBridge {
    private const val CHANNEL_ID = "ironlog_workout"
    private const val NOTIF_ACTIVE = 7101
    private const val NOTIF_REST = 7102
    private const val NOTIF_REMINDER = 7103
    private val settingsBox: Box<AppSettingEntity> by lazy { ObjectBox.store.boxFor(AppSettingEntity::class.java) }
    private const val PR_STATUSBAR_KEY = "pr_statusbar_notifications_enabled"

    /** RN parity policy: PR feedback stays in-app (HUD/haptics), never noisy status-bar spam by default. */
    fun shouldPostPrStatusBarNotification(): Boolean {
        val row = settingsBox.query(AppSettingEntity_.key.equal(PR_STATUSBAR_KEY)).build().use { it.findFirst() }
        return row?.value?.equals("true", ignoreCase = true) ?: false
    }

    fun showActiveWorkout(context: Context, title: String = "Workout in progress") {
        // Active workout status should always be visible while training; don't suppress
        // due to reminder quiet-hours profile gates.
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            11,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ironlog)
            .setContentTitle("Ironlog")
            .setContentText(title)
            // Do NOT set setOngoing(true) — ongoing notifications persist in the notification
            // shade even after the process is killed (swipe from recents). Regular notifications
            // are cancelled automatically by Android when their creating process dies.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(
                0,
                "Finish",
                actionPendingIntent(context, NotificationActionRouter.Actions.FINISH_WORKOUT, 511),
            )
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_ACTIVE, n)
    }

    fun showRestTimer(context: Context, secondsRemaining: Int) {
        // Rest timer is part of an active workout UX; keep it visible regardless of quiet-hours.
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ironlog)
            .setContentTitle("Rest Timer")
            .setContentText("Rest: ${secondsRemaining.coerceAtLeast(0)}s remaining")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                0,
                "Skip",
                actionPendingIntent(context, NotificationActionRouter.Actions.SKIP_REST, 521),
            )
            .addAction(
                0,
                "+30s",
                actionPendingIntent(context, NotificationActionRouter.Actions.ADD_30S, 522),
            )
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_REST, n)
    }

    fun clearRestTimer(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_REST)
    }

    fun clearWorkout(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_REST)
        nm.cancel(NOTIF_ACTIVE)
    }

    fun showDailyReminder(context: Context, text: String = "Time to train. Keep the streak alive.") {
        if (!canPostNow()) return
        val workoutBox = ObjectBox.store.boxFor(WorkoutEntity::class.java)
        val bodyBox = ObjectBox.store.boxFor(BodyMeasurementEntity::class.java)
        val planBox = ObjectBox.store.boxFor(PlanEntity::class.java)
        
        val candidates = SmartCandidateGenerator.buildCandidates(workoutBox, bodyBox, planBox, settingsBox)
        val candidate = NotificationPolicyEngine.chooseCandidate(settingsBox, candidates)
        if (candidate == null) return
        
        val decision = NotificationPolicyEngine.shouldSend(settingsBox, key = candidate.key, topic = candidate.topic)
        if (!decision.allowed) {
            NotificationPolicyEngine.recordSuppressed(settingsBox, candidate.key, candidate.topic, decision.reason ?: "suppressed")
            return
        }
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            31,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("ironlog_route", "Tabs")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ironlog)
            .setContentTitle(candidate.title)
            .setContentText(candidate.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_REMINDER, n)
        NotificationPolicyEngine.recordSent(settingsBox, candidate.key, candidate.topic)
    }

    fun showTestNotification(context: Context, text: String = "Ironlog notifications are working.") {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            41,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("ironlog_route", "Settings")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ironlog)
            .setContentTitle("Ironlog Test")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(7104, n)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ironlog Workout", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Active workout and rest timer notifications"
            }
        )
    }

    private fun canPostNow(): Boolean {
        if (!notificationsEnabled()) return false
        return !isInQuietHours()
    }

    private fun notificationsEnabled(): Boolean {
        val row = settingsBox.query(AppSettingEntity_.key.equal("notifications_enabled")).build().use { it.findFirst() }
        return row?.value?.equals("true", ignoreCase = true) ?: true
    }

    private fun isInQuietHours(): Boolean {
        val start = readQuietMinutes("quietHoursStartMinutes", 22 * 60)
        val end = readQuietMinutes("quietHoursEndMinutes", 7 * 60)
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (start == end) {
            false
        } else if (start < end) {
            now in start until end
        } else {
            now >= start || now < end
        }
    }

    private fun readQuietMinutes(key: String, fallback: Int): Int {
        val row = settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() } ?: return fallback
        return row.value.toDoubleOrNull()?.toInt()?.coerceIn(0, 1439) ?: fallback
    }

    private fun actionPendingIntent(context: Context, actionId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra("actionId", actionId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private data class PolicyDecision(val allowed: Boolean, val reason: String? = null)

private object NotificationPolicyEngine {
    private const val DECISION_LOG_KEY = "decision_log_json"
    private const val SNOOZE_UNTIL_KEY = "snooze_until"
    private const val PROFILE_KEY = "notificationProfile"
    private const val DAILY_OVERRIDE_KEY = "maxNotificationsPerDayOverride"
    private const val WEEKLY_OVERRIDE_KEY = "maxNotificationsPerWeekOverride"
    private const val COOLDOWN_HOURS_KEY = "cooldownHours"
    private const val LOG_LIMIT = 64

    private data class Profile(val dailyCap: Int, val weeklyCap: Int, val baseCooldownHours: Int, val topicCooldown: Map<String, Int>)

    private val conservative = Profile(1, 2, 24, mapOf("training" to 18, "recovery" to 24, "streak" to 20, "bodyweight" to 24, "milestone" to 12, "backup" to 48))
    private val balanced = Profile(1, 3, 12, mapOf("training" to 12, "recovery" to 16, "streak" to 16, "bodyweight" to 18, "milestone" to 8, "backup" to 36))
    private val aggressive = Profile(1, 5, 8, mapOf("training" to 8, "recovery" to 10, "streak" to 12, "bodyweight" to 12, "milestone" to 6, "backup" to 24))

    fun shouldSend(settingsBox: Box<AppSettingEntity>, key: String, topic: String): PolicyDecision {
        val now = System.currentTimeMillis()
        val snoozeUntil = readLong(settingsBox, SNOOZE_UNTIL_KEY)
        if (snoozeUntil != null && snoozeUntil > now) return PolicyDecision(false, "snoozed")
        val profile = profile(settingsBox)
        val log = readDecisionLog(settingsBox)
        val dailyCap = (readDouble(settingsBox, DAILY_OVERRIDE_KEY)?.toInt() ?: profile.dailyCap).coerceIn(1, 3)
        val weeklyCap = (readDouble(settingsBox, WEEKLY_OVERRIDE_KEY)?.toInt() ?: profile.weeklyCap).coerceIn(1, 7)
        if (countToday(log, now) >= dailyCap) return PolicyDecision(false, "daily_cap")
        if (countThisWeek(log, now) >= weeklyCap) return PolicyDecision(false, "weekly_cap")
        if (isKeyCooldown(log, key, now, settingsBox, profile)) return PolicyDecision(false, "cooldown_or_topic_gate")
        if (isTopicCooldown(log, topic, now, settingsBox, profile)) return PolicyDecision(false, "cooldown_or_topic_gate")
        return PolicyDecision(true, "selected")
    }

    fun recordSent(settingsBox: Box<AppSettingEntity>, key: String, topic: String) {
        appendDecision(settingsBox, JSONObject().put("outcome", "sent").put("key", key).put("topic", topic).put("reason", "selected").put("at", isoNow()))
    }

    fun recordSuppressed(settingsBox: Box<AppSettingEntity>, key: String, topic: String, reason: String) {
        val latest = readDecisionLog(settingsBox).optJSONObject(0)
        val sixHoursMs = 6L * 3600L * 1000L
        val sameAsLatest = latest != null &&
            latest.optString("outcome") == "suppressed" &&
            latest.optString("key") == key &&
            latest.optString("reason") == reason &&
            (System.currentTimeMillis() - parseTime(latest.optString("at"))) <= sixHoursMs
        if (sameAsLatest) return
        appendDecision(settingsBox, JSONObject().put("outcome", "suppressed").put("key", key).put("topic", topic).put("reason", reason).put("at", isoNow()))
    }

    private fun appendDecision(settingsBox: Box<AppSettingEntity>, entry: JSONObject) {
        val current = readDecisionLog(settingsBox)
        val merged = JSONArray()
        merged.put(entry)
        for (i in 0 until current.length()) {
            if (merged.length() >= LOG_LIMIT) break
            merged.put(current.opt(i))
        }
        writeSetting(settingsBox, DECISION_LOG_KEY, merged.toString(), "json")
    }

    private fun countToday(log: JSONArray, nowMs: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = nowMs; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val start = c.timeInMillis
        var count = 0
        for (i in 0 until log.length()) {
            val e = log.optJSONObject(i) ?: continue
            if (e.optString("outcome") != "sent") continue
            if (parseTime(e.optString("at")) >= start) count++
        }
        return count
    }

    private fun countThisWeek(log: JSONArray, nowMs: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val day = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else cal.get(Calendar.DAY_OF_WEEK) - 1
        cal.add(Calendar.DAY_OF_MONTH, -day + 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        var count = 0
        for (i in 0 until log.length()) {
            val e = log.optJSONObject(i) ?: continue
            if (e.optString("outcome") != "sent") continue
            if (parseTime(e.optString("at")) >= start) count++
        }
        return count
    }

    private fun isKeyCooldown(log: JSONArray, key: String, nowMs: Long, settingsBox: Box<AppSettingEntity>, profile: Profile): Boolean {
        val defaultHours = (readDouble(settingsBox, COOLDOWN_HOURS_KEY)?.toInt() ?: profile.baseCooldownHours).coerceAtLeast(1)
        val latest = findLatestSentForKey(log, key) ?: return false
        return nowMs - latest < defaultHours * 3600_000L
    }

    private fun isTopicCooldown(log: JSONArray, topic: String, nowMs: Long, settingsBox: Box<AppSettingEntity>, profile: Profile): Boolean {
        val row = settingsBox.query(AppSettingEntity_.key.equal("perTopicCooldownHours")).build().use { it.findFirst() }
        val perTopic = row?.value?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        val topicHours = perTopic?.optDouble(topic, Double.NaN)?.takeIf { it.isFinite() }?.toInt()
            ?: profile.topicCooldown[topic]
            ?: profile.baseCooldownHours
        val latest = findLatestSentForTopic(log, topic) ?: return false
        return nowMs - latest < topicHours * 3600_000L
    }

    private fun findLatestSentForKey(log: JSONArray, key: String): Long? {
        for (i in 0 until log.length()) {
            val e = log.optJSONObject(i) ?: continue
            if (e.optString("outcome") == "sent" && e.optString("key") == key) return parseTime(e.optString("at"))
        }
        return null
    }

    private fun findLatestSentForTopic(log: JSONArray, topic: String): Long? {
        for (i in 0 until log.length()) {
            val e = log.optJSONObject(i) ?: continue
            if (e.optString("outcome") == "sent" && e.optString("topic") == topic) return parseTime(e.optString("at"))
        }
        return null
    }

    private fun readDecisionLog(settingsBox: Box<AppSettingEntity>): JSONArray {
        val row = settingsBox.query(AppSettingEntity_.key.equal(DECISION_LOG_KEY)).build().use { it.findFirst() } ?: return JSONArray()
        return runCatching { JSONArray(row.value) }.getOrElse { JSONArray() }
    }

    private fun profile(settingsBox: Box<AppSettingEntity>): Profile {
        val p = settingsBox.query(AppSettingEntity_.key.equal(PROFILE_KEY)).build().use { it.findFirst() }?.value?.lowercase().orEmpty()
        return when (p) {
            "conservative" -> conservative
            "aggressive" -> aggressive
            else -> balanced
        }
    }

    private fun readLong(settingsBox: Box<AppSettingEntity>, key: String): Long? =
        settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }?.value?.toLongOrNull()

    private fun readDouble(settingsBox: Box<AppSettingEntity>, key: String): Double? =
        settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }?.value?.toDoubleOrNull()

    private fun writeSetting(settingsBox: Box<AppSettingEntity>, key: String, value: String, type: String) {
        val now = System.currentTimeMillis()
        val row = settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }
        if (row != null) {
            row.value = value
            row.valueType = type
            row.updatedAt = now
            settingsBox.put(row)
        } else {
            settingsBox.put(AppSettingEntity().apply {
                this.key = key
                this.value = value
                this.valueType = type
                this.updatedAt = now
            })
        }
    }

    private fun isoNow(): String = java.time.Instant.now().toString()
    private fun parseTime(iso: String?): Long = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrElse { 0L }

    fun chooseCandidate(settingsBox: Box<AppSettingEntity>, candidates: List<NotificationCandidate>): NotificationCandidate? {
        val profile = profile(settingsBox)
        val log = readDecisionLog(settingsBox)
        val nowMs = System.currentTimeMillis()
        
        val sorted = candidates.sortedByDescending { it.score }
        for (c in sorted) {
            if (isKeyCooldown(log, c.key, nowMs, settingsBox, profile)) continue
            if (isTopicCooldown(log, c.topic, nowMs, settingsBox, profile)) continue
            return c
        }
        return null
    }
}

data class NotificationCandidate(
    val key: String,
    val topic: String,
    val title: String,
    val body: String,
    val score: Int
)

object SmartCandidateGenerator {
    fun buildCandidates(
        workoutBox: Box<WorkoutEntity>,
        bodyBox: Box<BodyMeasurementEntity>,
        planBox: Box<PlanEntity>,
        settingsBox: Box<AppSettingEntity>
    ): List<NotificationCandidate> {
        val candidates = mutableListOf<NotificationCandidate>()
        val now = System.currentTimeMillis()
        val todayStr = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        
        val history = workoutBox.query(WorkoutEntity_.status.equal("completed")).orderDesc(WorkoutEntity_.createdAt).build().use { it.find() }
        val hasWorkoutToday = history.any { 
            Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() == todayStr 
        }

        val activePlan = planBox.query(PlanEntity_.isActive.equal(true)).build().use { it.findFirst() }
        val targetDays = activePlan?.days?.size?.coerceIn(1, 7) ?: 3
        
        val startOfWeek = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val doneThisWeek = history.count { 
            val d = Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(startOfWeek)
        }
        val daysLeft = 8 - LocalDate.now().dayOfWeek.value
        
        // 1. Plan Aware Training Candidate
        if (!hasWorkoutToday && doneThisWeek < targetDays && (targetDays - doneThisWeek) >= daysLeft) {
            val remaining = targetDays - doneThisWeek
            candidates.add(NotificationCandidate(
                key = "train_reminder_plan_aware",
                topic = "training",
                title = "Training reminder",
                body = "$remaining session${if (remaining > 1) "s" else ""} left this week. Aim to train at your usual time.",
                score = 100
            ))
        }

        // 2. Bodyweight Reminder
        val bodyweightEntries = bodyBox.all
        val hasBodyweightToday = bodyweightEntries.any {
            Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate() == todayStr
        }
        if (!hasBodyweightToday && history.isNotEmpty()) {
            candidates.add(NotificationCandidate(
                key = "bw_reminder",
                topic = "bodyweight",
                title = "Log body weight",
                body = "A quick bodyweight entry keeps your trend accurate.",
                score = 40
            ))
        }

        // 3. Streak Preserve
        val currentStreak = calculateStreak(history)
        if (currentStreak >= 1 && !hasWorkoutToday && doneThisWeek < targetDays) {
            candidates.add(NotificationCandidate(
                key = "streak_preserve",
                topic = "streak",
                title = "Streak active",
                body = "One more session this week keeps your streak alive.",
                score = 55
            ))
        }

        // 4. Backup Integrity
        val lastBackup = settingsBox.query(AppSettingEntity_.key.equal("last_auto_backup_ms")).build().use { it.findFirst() }?.value?.toLongOrNull() ?: 0L
        if (history.isNotEmpty() && (now - lastBackup) > 3L * 24L * 3600L * 1000L) {
            candidates.add(NotificationCandidate(
                key = "backup_integrity",
                topic = "backup",
                title = "Backup reminder",
                body = "Your training data has unsynced changes. Run a backup today.",
                score = 58
            ))
        }

        return candidates
    }
    
    private fun calculateStreak(history: List<WorkoutEntity>): Int {
        if (history.isEmpty()) return 0
        var streak = 0
        var currentWeek = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val historyDates = history.map { 
            Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) 
        }.toSet()
        
        while (historyDates.contains(currentWeek)) {
            streak++
            currentWeek = currentWeek.minusWeeks(1)
        }
        return streak
    }
}
