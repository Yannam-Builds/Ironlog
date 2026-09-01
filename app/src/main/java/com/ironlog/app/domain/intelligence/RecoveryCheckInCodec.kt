package com.ironlog.app.domain.intelligence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** One storage boundary for native, legacy double-encoded, and widget check-ins. */
object RecoveryCheckInCodec {
    private val json = Json { ignoreUnknownKeys = true }
    const val MAX_AGE_MS = 48 * 3_600_000L

    fun encode(input: ManualRecoveryInput, nowEpochMs: Long): String {
        require(validSignals(input) && nowEpochMs > 0) { "Invalid recovery check-in." }
        return json.encodeToString(ManualRecoveryInput.serializer(), input.copy(recordedAt = nowEpochMs))
    }

    fun decode(raw: String?, nowEpochMs: Long): ManualRecoveryInput? = runCatching {
        if (raw.isNullOrBlank()) return null
        val parsed = json.parseToJsonElement(raw)
        val unwrapped = if (parsed is JsonPrimitive && parsed.isString) parsed.content else raw
        json.decodeFromString(ManualRecoveryInput.serializer(), unwrapped).takeIf { input ->
            validSignals(input) && input.recordedAt > 0 && input.recordedAt <= nowEpochMs + 300_000L &&
                nowEpochMs - input.recordedAt <= MAX_AGE_MS
        }
    }.getOrNull()

    private fun validSignals(input: ManualRecoveryInput) =
        input.soreness in 0..5 && input.sleepQuality in 0..5 && input.energy in 0..5
}

val RECOVERY_REGIONS = listOf("Push", "Pull", "Legs", "Core", "Arms", "Shoulders")
