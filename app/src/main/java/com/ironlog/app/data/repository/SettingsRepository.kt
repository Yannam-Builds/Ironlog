package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

const val PR_RESET_AT_KEY = "pr_reset_at_epoch_ms"

/** Translation of settingsRepository.js. */
class SettingsRepository(
    private val settingsBox: Box<AppSettingEntity> = ObjectBox.store.boxFor(AppSettingEntity::class.java),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private fun findSettingRow(key: String): AppSettingEntity? =
        settingsBox.query(AppSettingEntity_.key.equal(key)).build().use { it.findFirst() }

    suspend fun getSetting(key: String): Any? = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "key must not be empty" }
        val row = findSettingRow(key) ?: return@withContext null
        when (row.valueType) {
            "number" -> row.value.toDoubleOrNull()
            "boolean" -> row.value == "true"
            "json" -> runCatching { json.parseToJsonElement(row.value) }.getOrNull()
            else -> row.value
        }
    }

    suspend fun getSettingJson(key: String): JsonElement? = getSetting(key) as? JsonElement
    suspend fun getSettingString(key: String): String? = getSetting(key) as? String
    suspend fun getSettingBoolean(key: String): Boolean? = getSetting(key) as? Boolean
    suspend fun getSettingNumber(key: String): Double? = getSetting(key) as? Double

    suspend fun setSetting(key: String, value: Any?, valueType: String = "string") = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "key must not be empty" }
        val serialized = when (valueType) {
            "json" -> when (value) {
                null -> "null"
                is JsonElement -> value.toString()
                else -> json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), JsonPrimitive(value.toString()))
            }
            "boolean" -> ((value as? Boolean) ?: false).toString()
            else -> value?.toString() ?: ""
        }
        val now = System.currentTimeMillis()
        val existing = findSettingRow(key)
        if (existing != null) {
            existing.value = serialized
            existing.valueType = valueType
            existing.updatedAt = now
            settingsBox.put(existing)
        } else {
            settingsBox.put(AppSettingEntity().apply {
                this.key = key
                this.value = serialized
                this.valueType = valueType
                this.updatedAt = now
            })
        }
    }

    suspend fun removeSetting(key: String) = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "key must not be empty" }
        findSettingRow(key)?.let { settingsBox.remove(it) }
    }

    suspend fun getTheme(): String = getSettingString("theme") ?: com.ironlog.app.ui.theme.IronLogThemes.DEFAULT_THEME
    suspend fun setTheme(theme: String) = setSetting("theme", theme, "string")
    suspend fun getActiveWorkoutId(): String? = getSettingString("active_workout_id")
    suspend fun setActiveWorkoutId(workoutId: String?) {
        if (workoutId.isNullOrBlank()) removeSetting("active_workout_id") else setSetting("active_workout_id", workoutId, "string")
    }
    suspend fun clearActiveWorkoutId() = removeSetting("active_workout_id")

    /**
     * Reads back the raw stored string for the given key.
     *
     * For rows with valueType="json" the historic [setSetting] implementation accidentally
     * double-encoded plain strings via [JsonPrimitive], producing a JSON string-literal
     * `"\"...\""` rather than the raw JSON text.  This function transparently unwraps that
     * old encoding so the first-restart-after-upgrade path keeps working without a migration.
     */
    suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        decodeString(findSettingRow(key))
    }

    /** For an IO caller's existing database transaction; never switches transaction threads. */
    fun getStringBlocking(key: String): String? = decodeString(findSettingRow(key))

    /** Consume a navigation request only if it is still the one the caller observed. */
    suspend fun consumeString(key: String, expected: String): Boolean = withContext(Dispatchers.IO) {
        settingsBox.store.callInTx {
            val row = findSettingRow(key)
            if (row == null || decodeString(row) != expected) false
            else { settingsBox.remove(row); true }
        }
    }

    fun observeStrings(keys: Set<String>): Flow<Map<String, String?>> {
        require(keys.isNotEmpty() && keys.all(String::isNotBlank))
        return observeQuery { settingsBox.query(AppSettingEntity_.key.oneOf(keys.toTypedArray())).build() }
            .map { rows -> val byKey = rows.associateBy { it.key }; keys.associateWith { decodeString(byKey[it]) } }
            .distinctUntilChanged()
    }

    /** Check-in and restrictions are one fact: observers must never see half a save. */
    suspend fun saveRecoveryCheckIn(
        input: com.ironlog.app.domain.intelligence.ManualRecoveryInput,
        painFlags: Set<String>, nowEpochMs: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        val regions = com.ironlog.app.domain.intelligence.RECOVERY_REGIONS
        require(painFlags.all { it in regions }) { "Unknown pain region." }
        val encoded = com.ironlog.app.domain.intelligence.RecoveryCheckInCodec.encode(input, nowEpochMs)
        val values = mapOf("manual_recovery_input" to encoded) + regions.associate { "pain_flag_$it" to (it in painFlags).toString() }
        settingsBox.store.runInTx {
            settingsBox.put(values.map { (key, value) ->
                (findSettingRow(key) ?: AppSettingEntity().apply { this.key = key }).apply {
                    this.value = value
                    valueType = if (key == "manual_recovery_input") "json" else "string"
                    updatedAt = nowEpochMs
                }
            })
        }
    }

    private fun decodeString(row: AppSettingEntity?): String? {
        val raw = row?.value?.takeIf { it.isNotEmpty() } ?: return null
        return if (row.valueType == "json") {
            // Try to detect the old double-encoded format: a JSON string literal wrapping
            // a JSON object/array.  If parsing yields a JsonPrimitive whose content is itself
            // valid JSON, return the inner content; otherwise return the raw value as-is.
            runCatching {
                val element = json.parseToJsonElement(raw)
                if (element is JsonPrimitive && element.isString) element.content else raw
            }.getOrDefault(raw)
        } else raw
    }

    /**
     * Stores [value] verbatim — does NOT go through [setSetting]'s [JsonPrimitive] encoding
     * path, which would double-encode a JSON string into `"\"...escaped...\"".
     */
    suspend fun setString(key: String, value: String, valueType: String = "string") = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "key must not be empty" }
        val now = System.currentTimeMillis()
        val existing = findSettingRow(key)
        if (existing != null) {
            existing.value = value
            existing.valueType = valueType
            existing.updatedAt = now
            settingsBox.put(existing)
        } else {
            settingsBox.put(AppSettingEntity().apply {
                this.key = key
                this.value = value
                this.valueType = valueType
                this.updatedAt = now
            })
        }
    }

    /** Atomically binds related timer values to the expected active workout identity. */
    suspend fun setLongSettingsIfStringMatches(
        matchKey: String,
        expectedValue: String,
        values: Map<String, Long>,
    ): Boolean = withContext(Dispatchers.IO) {
        require(matchKey.isNotBlank() && expectedValue.isNotBlank())
        require(values.isNotEmpty() && values.keys.all(String::isNotBlank))
        settingsBox.store.callInTx {
            if (decodeString(findSettingRow(matchKey)) != expectedValue) return@callInTx false
            val now = System.currentTimeMillis()
            settingsBox.put(values.map { (key, value) ->
                (findSettingRow(key) ?: AppSettingEntity()).apply {
                    this.key = key
                    this.value = value.toString()
                    valueType = "number"
                    updatedAt = now
                }
            })
            true
        }
    }

    suspend fun setLongSettingsAtomically(values: Map<String, Long>) = withContext(Dispatchers.IO) {
        require(values.isNotEmpty() && values.keys.all(String::isNotBlank))
        settingsBox.store.runInTx {
            val now = System.currentTimeMillis()
            settingsBox.put(values.map { (key, value) ->
                (findSettingRow(key) ?: AppSettingEntity()).apply {
                    this.key = key
                    this.value = value.toString()
                    valueType = "number"
                    updatedAt = now
                }
            })
        }
    }

    /**
     * Starts a new PR era without deleting workout history. The cutoff and all legacy/manual
     * summaries change in one ObjectBox transaction, so observers never rebuild from half a reset.
     */
    suspend fun resetPersonalBests(nowEpochMs: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        require(nowEpochMs > 0L) { "PR reset timestamp must be positive" }
        settingsBox.store.runInTx {
            val values = mapOf(
                PR_RESET_AT_KEY to (nowEpochMs.toString() to "number"),
                "ironlog_pb" to ("{}" to "json"),
                "pr_index_v1" to ("{}" to "json"),
                "widget_last_new_pb_ms" to ("0" to "number"),
            )
            settingsBox.put(values.map { (key, valueAndType) ->
                (findSettingRow(key) ?: AppSettingEntity()).apply {
                    this.key = key
                    value = valueAndType.first
                    valueType = valueAndType.second
                    updatedAt = nowEpochMs
                }
            })
        }
    }

    suspend fun getPersonalBestResetAt(): Instant? = withContext(Dispatchers.IO) {
        parsePersonalBestResetAt(decodeString(findSettingRow(PR_RESET_AT_KEY)))
    }

    /** IO-only companion to [getPersonalBestResetAt] for an existing blocking projection. */
    fun getPersonalBestResetAtBlocking(): Instant? =
        parsePersonalBestResetAt(decodeString(findSettingRow(PR_RESET_AT_KEY)))

    /**
     * Synchronous escape hatch for lifecycle-critical writes where the caller may be disposed
     * before a launched coroutine gets CPU time (for example active workout draft snapshots
     * during ON_STOP). Keep normal UI writes on the suspend API.
     */
    fun setStringBlocking(key: String, value: String, valueType: String = "string") {
        require(key.isNotBlank()) { "key must not be empty" }
        val now = System.currentTimeMillis()
        val existing = findSettingRow(key)
        if (existing != null) {
            existing.value = value
            existing.valueType = valueType
            existing.updatedAt = now
            settingsBox.put(existing)
        } else {
            settingsBox.put(AppSettingEntity().apply {
                this.key = key
                this.value = value
                this.valueType = valueType
                this.updatedAt = now
            })
        }
    }
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean = getSettingBoolean(key) ?: default
    suspend fun setBoolean(key: String, value: Boolean) = setSetting(key, value, "boolean")
    suspend fun isExerciseSeedComplete(): Boolean = getBoolean("exercise_seed_complete", false)
    suspend fun markExerciseSeedComplete() = setBoolean("exercise_seed_complete", true)

    suspend fun loadFavorites(): Set<String> {
        val raw = getSettingJson("favorite_exercises") ?: return emptySet()
        return runCatching { raw.jsonArray.map { it.jsonPrimitive.content }.filter { it.isNotBlank() }.toSet() }.getOrDefault(emptySet())
    }

    suspend fun saveFavorites(favorites: Set<String>) {
        val payload = buildJsonArray { favorites.sorted().forEach { add(JsonPrimitive(it)) } }
        setSetting("favorite_exercises", payload, "json")
    }
}

fun parsePersonalBestResetAt(raw: String?): Instant? = raw
    ?.trim()
    ?.toLongOrNull()
    ?.takeIf { it > 0L }
    ?.let { runCatching { Instant.ofEpochMilli(it) }.getOrNull() }
