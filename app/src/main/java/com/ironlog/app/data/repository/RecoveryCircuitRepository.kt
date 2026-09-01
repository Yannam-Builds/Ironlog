package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import com.ironlog.app.domain.gamification.CreditedProof
import com.ironlog.app.domain.gamification.parseHistoryLocalDate
import com.ironlog.app.domain.gamification.XpAction
import com.ironlog.app.domain.gamification.XpEngine
import io.objectbox.BoxStore
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.WeekFields
import org.json.JSONObject
import org.json.JSONTokener

enum class RecoveryCircuitResult { RECORDED, ALREADY_RECORDED, INELIGIBLE }

/** Eligibility, history, settings, and the once-a-week write share ONE database transaction. */
class RecoveryCircuitRepository(private val store: BoxStore, private val clock: Clock = Clock.systemDefaultZone()) {
    fun complete(requestedWeek: String, circuitId: String): RecoveryCircuitResult = store.callInTx {
        val now = clock.instant()
        val week = isoWeek(now.atZone(clock.zone).toLocalDate())
        if (requestedWeek != week || circuitId !in setOf("push_basic", "pull_basic", "core_basic", "fullbody_basic")) return@callInTx RecoveryCircuitResult.INELIGIBLE
        val settingsBox = store.boxFor(AppSettingEntity::class.java)
        val raw = settingsBox.query(AppSettingEntity_.key.equal("ironlog_settings")).build().use { it.findFirst()?.value }.orEmpty()
        val settings = runCatching {
            val parsed = JSONTokener(raw).nextValue()
            if (parsed is String) JSONObject(parsed) else parsed as JSONObject
        }.getOrDefault(JSONObject())
        val goal = settings.optInt("weeklyGoalDays", 4).coerceIn(1, 7)
        val events = store.boxFor(IronLedgerEventEntity::class.java)
        val eventId = "recovery:$week"
        val previousEvent = events.query(IronLedgerEventEntity_.eventId.equal(eventId)).build().use { it.findFirst() }
        val profiles = store.boxFor(GamificationProfileEntity::class.java)
        val previousProfile = profiles.query(GamificationProfileEntity_.offlineUserId.equal("local")).build().use { it.findFirst() }
        val completions = runCatching { JSONObject(previousProfile?.makeupCompletionsJson ?: "{}") }.getOrDefault(JSONObject())
        if (previousEvent != null || completions.optInt(week) > 0) return@callInTx RecoveryCircuitResult.ALREADY_RECORDED
        val sessions = HistoryRepository(store).completedSnapshotBlocking().count {
            CreditedProof.qualifies(it, now, clock.zone) && parseHistoryLocalDate(it.date, clock.zone)?.let(::isoWeek) == week
        }
        if (sessions != goal - 1) return@callInTx RecoveryCircuitResult.INELIGIBLE
        val profile = previousProfile ?: GamificationProfileEntity(offlineUserId = "local")
        val gained = XpEngine().xpForAction(XpAction.RECOVERY_CIRCUIT)
        completions.put(week, 1)
        profile.makeupCompletionsJson = completions.toString()
        profile.updatedAt = now.toEpochMilli()
        profiles.put(profile)
        events.put(IronLedgerEventEntity(eventId = eventId, sourceType = "bonus", sourceId = circuitId,
            eventKind = "recovery_circuit", occurredAt = now.toEpochMilli(), xpDelta = gained,
            metadataJson = JSONObject().put("title", "Recovery proof logged").put("detail", "Protected $week with a recovery circuit").put("circuitId", circuitId).toString()))
        RecoveryCircuitResult.RECORDED
    }

    private fun isoWeek(date: LocalDate): String {
        val fields = WeekFields.ISO
        return "${date.get(fields.weekBasedYear())}-W${date.get(fields.weekOfWeekBasedYear()).toString().padStart(2, '0')}"
    }
}
