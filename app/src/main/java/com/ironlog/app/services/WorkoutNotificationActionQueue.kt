package com.ironlog.app.services

import android.net.Uri
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject

internal data class WorkoutNotificationActionQueueResult(
    val actionId: String?,
    val remainingJson: String?,
)

internal data class WorkoutRestControlResult(
    val applied: Boolean,
    val wallEndMs: Long = 0L,
    val elapsedEndMs: Long = 0L,
    val bootCount: Int = -1,
    val pausedRemainingMs: Long = 0L,
)

/** Pure, bounded queue codec so rapid notification actions cannot overwrite each other. */
internal object WorkoutNotificationActionQueue {
    private const val VERSION = 1
    private const val MAX_ITEMS = 16
    private const val MAX_AGE_MS = 5L * 60L * 1_000L
    private const val MAX_FUTURE_SKEW_MS = 60_000L

    private data class Entry(val sessionId: String, val actionId: String, val createdAt: Long)

    fun enqueue(raw: String?, sessionId: String, actionId: String, nowMs: Long): String {
        require(sessionId.isNotBlank())
        require(actionId in allowedActions)
        val entries = decode(raw, nowMs).toMutableList()
        entries += Entry(sessionId, actionId, nowMs)
        return encode(entries.takeLast(MAX_ITEMS))
    }

    fun isAllowed(actionId: String): Boolean = actionId in allowedActions

    fun consume(raw: String?, sessionId: String, nowMs: Long): WorkoutNotificationActionQueueResult {
        val entries = decode(raw, nowMs).toMutableList()
        val index = entries.indexOfFirst { it.sessionId == sessionId }
        val action = if (index >= 0) entries.removeAt(index).actionId else null
        return WorkoutNotificationActionQueueResult(action, encodeOrNull(entries))
    }

    fun clearSession(raw: String?, sessionId: String, nowMs: Long): String? =
        encodeOrNull(decode(raw, nowMs).filterNot { it.sessionId == sessionId })

    private fun decode(raw: String?, nowMs: Long): List<Entry> {
        val root = raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyList()
        if (root.optInt("version", 0) != VERSION) return emptyList()
        val array = root.optJSONArray("actions") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val sessionId = item.optString("sessionId")
                val actionId = item.optString("actionId")
                val createdAt = item.optLong("createdAt", 0L)
                if (sessionId.isBlank() || actionId !in allowedActions) continue
                if (createdAt < nowMs - MAX_AGE_MS || createdAt > nowMs + MAX_FUTURE_SKEW_MS) continue
                add(Entry(sessionId, actionId, createdAt))
            }
        }.takeLast(MAX_ITEMS)
    }

    private fun encodeOrNull(entries: List<Entry>): String? =
        entries.takeIf { it.isNotEmpty() }?.let(::encode)

    private fun encode(entries: List<Entry>): String {
        val actions = JSONArray()
        entries.forEach { entry ->
            actions.put(JSONObject()
                .put("sessionId", entry.sessionId)
                .put("actionId", entry.actionId)
                .put("createdAt", entry.createdAt))
        }
        return JSONObject().put("version", VERSION).put("actions", actions).toString()
    }

    private val allowedActions = setOf(
        NotificationActionRouter.Actions.SKIP_REST,
        NotificationActionRouter.Actions.ADD_30S,
        NotificationActionRouter.Actions.FINISH_WORKOUT,
        NotificationActionRouter.Actions.REST_STATE_CHANGED,
    )
}

/** Transactional ObjectBox inbox shared by the private notification entry and workout reducer. */
object WorkoutNotificationActionInbox {
    private const val QUEUE_KEY = "pending_workout_action_queue_v1"
    private const val LEGACY_ACTION_KEY = "pending_workout_action"
    private const val LEGACY_SESSION_KEY = "pending_workout_action_session_id"
    private val sessionSignals = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    internal fun signalsForSession(sessionId: String): Flow<Unit> = sessionSignals
        .filter { it == sessionId }
        .map { Unit }
        // Always drain once when a screen becomes active; this recovers events from process death
        // or while no lifecycle-aware collector existed.
        .onStart { emit(Unit) }

    fun enqueue(sessionId: String, actionId: String, nowMs: Long = System.currentTimeMillis()) {
        ObjectBox.store.runInTx {
            val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
            val row = find(box, QUEUE_KEY)
            val next = WorkoutNotificationActionQueue.enqueue(row?.value, sessionId, actionId, nowMs)
            put(box, row, next, nowMs)
        }
        sessionSignals.tryEmit(sessionId)
    }

    /**
     * Commits the action and navigation route before the private entry opens MainActivity.
     * This tiny local transaction cannot be cancelled with the entry Activity lifecycle.
     */
    fun commitNotificationTap(
        sessionId: String,
        actionId: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val committed = ObjectBox.store.callInTx {
            if (sessionId.isBlank()) return@callInTx false
            val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
            if (find(box, "active_workout_id")?.value != sessionId) return@callInTx false
            if (!actionId.isNullOrBlank()) {
                if (!WorkoutNotificationActionQueue.isAllowed(actionId)) return@callInTx false
                val row = find(box, QUEUE_KEY)
                put(
                    box,
                    row,
                    WorkoutNotificationActionQueue.enqueue(row?.value, sessionId, actionId, nowMs),
                    nowMs,
                )
            }
            val routeRow = find(box, "pending_nav_route")
            val dayId = find(box, "active_workout_day_id")?.value.orEmpty()
            box.put((routeRow ?: AppSettingEntity()).apply {
                key = "pending_nav_route"
                value = if (dayId.isBlank()) "ActiveWorkout" else "ActiveWorkout/${Uri.encode(dayId)}"
                valueType = "string"
                updatedAt = nowMs
            })
            true
        }
        if (committed && !actionId.isNullOrBlank()) sessionSignals.tryEmit(sessionId)
        return committed
    }

    /**
     * Applies a shade rest control in the receiver's short ObjectBox transaction. The service
     * and UI then observe the same authoritative deadline; no Activity startup or polling delay
     * can make a last-second +30/Skip tap lose to an old in-memory value.
     */
    internal fun commitRestControl(
        sessionId: String,
        actionId: String,
        now: WorkoutClockSnapshot,
        enqueueUiSync: Boolean = true,
        expectedRestEndWallMs: Long? = null,
    ): WorkoutRestControlResult {
        val result = ObjectBox.store.callInTx {
            if (sessionId.isBlank() || actionId !in setOf(
                NotificationActionRouter.Actions.SKIP_REST,
                NotificationActionRouter.Actions.ADD_30S,
                NotificationActionRouter.Actions.PAUSE_REST,
            )
            ) return@callInTx WorkoutRestControlResult(false)

        val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
        if (find(box, "active_workout_id")?.value != sessionId) {
            return@callInTx WorkoutRestControlResult(false)
        }
        val persisted = readDeadline(box)
        val persistedPausedMs = find(box, WorkoutTimerSettingKeys.REST_PAUSED_REMAINING_MS)
            ?.value?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        if (expectedRestEndWallMs != null &&
            (expectedRestEndWallMs <= 0L || persisted.wallEndMs != expectedRestEndWallMs)
        ) {
            return@callInTx WorkoutRestControlResult(false)
        }
        val activeRemainingMs = persisted.takeIf { it.wallEndMs > 0L }
            ?.remainingMs(now)?.coerceAtLeast(0L) ?: 0L
        val mayApply = when (actionId) {
            NotificationActionRouter.Actions.PAUSE_REST -> activeRemainingMs > 0L
            NotificationActionRouter.Actions.ADD_30S,
            NotificationActionRouter.Actions.SKIP_REST ->
                activeRemainingMs > 0L || persistedPausedMs > 0L
            else -> false
        }
        // A captured PendingIntent must not resurrect a completed or long-expired rest.
        if (!mayApply) {
            return@callInTx WorkoutRestControlResult(false)
        }
        val (next, pausedRemainingMs) = when (actionId) {
            NotificationActionRouter.Actions.SKIP_REST ->
                WorkoutTimerDeadline(0L, 0L, -1) to 0L
            NotificationActionRouter.Actions.PAUSE_REST ->
                WorkoutTimerDeadline(0L, 0L, -1) to activeRemainingMs
            else -> if (persistedPausedMs > 0L) {
                WorkoutTimerDeadline(0L, 0L, -1) to
                    (persistedPausedMs.coerceAtMost(Long.MAX_VALUE - 30_000L) + 30_000L)
            } else {
                WorkoutTimerClock.deadlineAfter(
                    now,
                    activeRemainingMs.coerceAtMost(Long.MAX_VALUE - 30_000L) + 30_000L,
                ) to 0L
            }
        }
        putSetting(box, WorkoutTimerSettingKeys.REST_END_WALL_MS, next.wallEndMs, now.wallTimeMs)
        putSetting(box, WorkoutTimerSettingKeys.REST_END_ELAPSED_MS, next.elapsedEndMs, now.wallTimeMs)
        putSetting(box, WorkoutTimerSettingKeys.REST_BOOT_COUNT, next.bootCount.toLong(), now.wallTimeMs)
        putSetting(
            box,
            WorkoutTimerSettingKeys.REST_PAUSED_REMAINING_MS,
            pausedRemainingMs,
            now.wallTimeMs,
        )

        if (enqueueUiSync) {
            val queue = find(box, QUEUE_KEY)
            put(
                box,
                queue,
                WorkoutNotificationActionQueue.enqueue(
                    queue?.value,
                    sessionId,
                    NotificationActionRouter.Actions.REST_STATE_CHANGED,
                    now.wallTimeMs,
                ),
                now.wallTimeMs,
            )
        }
            WorkoutRestControlResult(
                applied = true,
                wallEndMs = next.wallEndMs,
                elapsedEndMs = next.elapsedEndMs,
                bootCount = next.bootCount,
                pausedRemainingMs = pausedRemainingMs,
            )
        }
        if (result.applied && enqueueUiSync) sessionSignals.tryEmit(sessionId)
        return result
    }

    fun consumeForSession(sessionId: String, nowMs: Long = System.currentTimeMillis()): String? =
        ObjectBox.store.callInTx {
            val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
            val row = find(box, QUEUE_KEY) ?: return@callInTx null
            val result = WorkoutNotificationActionQueue.consume(row.value, sessionId, nowMs)
            persistRemaining(box, row, result.remainingJson, nowMs)
            result.actionId
        }

    fun clearSession(sessionId: String, nowMs: Long = System.currentTimeMillis()) {
        if (sessionId.isBlank()) return
        ObjectBox.store.runInTx {
            val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
            val row = find(box, QUEUE_KEY) ?: return@runInTx
            val remaining = WorkoutNotificationActionQueue.clearSession(row.value, sessionId, nowMs)
            persistRemaining(box, row, remaining, nowMs)
        }
    }

    fun clearLegacyKeys() {
        ObjectBox.store.runInTx {
            val box = ObjectBox.store.boxFor(AppSettingEntity::class.java)
            listOf(LEGACY_ACTION_KEY, LEGACY_SESSION_KEY).forEach { key ->
                find(box, key)?.let(box::remove)
            }
        }
    }

    private fun find(box: io.objectbox.Box<AppSettingEntity>, key: String): AppSettingEntity? =
        box.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }

    private fun readDeadline(box: io.objectbox.Box<AppSettingEntity>): WorkoutTimerDeadline =
        WorkoutTimerDeadline(
            wallEndMs = find(box, WorkoutTimerSettingKeys.REST_END_WALL_MS)?.value?.toLongOrNull() ?: 0L,
            elapsedEndMs = find(box, WorkoutTimerSettingKeys.REST_END_ELAPSED_MS)?.value?.toLongOrNull() ?: 0L,
            bootCount = find(box, WorkoutTimerSettingKeys.REST_BOOT_COUNT)?.value?.toIntOrNull() ?: -1,
        )

    private fun putSetting(
        box: io.objectbox.Box<AppSettingEntity>,
        key: String,
        value: Long,
        nowMs: Long,
    ) {
        box.put((find(box, key) ?: AppSettingEntity()).apply {
            this.key = key
            this.value = value.toString()
            valueType = "number"
            updatedAt = nowMs
        })
    }

    private fun put(
        box: io.objectbox.Box<AppSettingEntity>,
        existing: AppSettingEntity?,
        value: String,
        nowMs: Long,
    ) {
        box.put((existing ?: AppSettingEntity()).apply {
            key = QUEUE_KEY
            this.value = value
            valueType = "json"
            updatedAt = nowMs
        })
    }

    private fun persistRemaining(
        box: io.objectbox.Box<AppSettingEntity>,
        row: AppSettingEntity,
        remaining: String?,
        nowMs: Long,
    ) {
        if (remaining == null) box.remove(row) else put(box, row, remaining, nowMs)
    }

}
