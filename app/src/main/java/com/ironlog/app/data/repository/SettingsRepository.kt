package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.AppSettingEntity_
import com.ironlog.app.data.objectbox.ObjectBox
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

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
        val row = findSettingRow(key) ?: return@withContext null
        val raw = row.value.takeIf { it.isNotEmpty() } ?: return@withContext null
        if (row.valueType == "json") {
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
