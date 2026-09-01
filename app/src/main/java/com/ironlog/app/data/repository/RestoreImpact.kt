package com.ironlog.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class RestoreImpact(val preview: ImportPreview, val databaseFingerprint: String, val removed: Map<String, Int>, val mode: String)

fun canConfirmRestore(impact: RestoreImpact?, mode: String, confirmation: String): Boolean =
    impact != null && impact.mode == mode && impact.preview.valid &&
        (mode == "merge" || mode == "replace" && impact.preview.replacementSafe && confirmation == "REPLACE")

internal fun replacementRemovals(existing: JSONObject, incoming: JSONObject, replace: Boolean): Map<String, Int> {
    if (!replace) return emptyMap()
    return FULL_BACKUP_DATA_SECTIONS.associateWith { table ->
        val key = when (table) {
            "app_settings" -> "key"
            "athlete_calibrations", "gamification_profiles" -> "offline_user_id"
            "iron_ledger_events" -> "event_id"
            else -> "id"
        }
        fun ids(data: JSONObject): Set<String> = (data.optJSONArray(table) ?: JSONArray()).let { rows ->
            (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.optString(key) }.toSet()
        }
        if (table in setOf("exercises", "exercise_muscles") && (incoming.optJSONArray("exercises")?.length() ?: 0) == 0) 0
        else (ids(existing) - ids(incoming)).size
    }.filterValues { it > 0 }
}

/** Stable ordering and no export wall-clock: detects edits between preview and confirmation. */
internal fun backupFingerprint(payload: JSONObject): String {
    fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.opt(it)) }
        is JSONArray -> (0 until value.length()).map { canonical(value.opt(it)) }.sorted().joinToString(",", "[", "]")
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> "null"
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical(payload.getJSONObject("data")).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
