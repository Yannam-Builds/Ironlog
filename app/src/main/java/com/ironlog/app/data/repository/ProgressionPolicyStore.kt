package com.ironlog.app.data.repository

import com.ironlog.app.domain.intelligence.ProgramRules
import com.ironlog.app.domain.intelligence.ProgressionPolicyResolver
import com.ironlog.app.domain.intelligence.ResolvedProgressionPolicy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val IRONLOG_SETTINGS_KEY = "ironlog_settings"

fun programProgressionRulesKey(planId: String): String = "program_rules:$planId"
fun exerciseProgressionOverrideKey(planExerciseId: String): String = "ex_prog:$planExerciseId"

data class ProgressionPolicySnapshot(
    val fallback: ResolvedProgressionPolicy,
    val byPlanExerciseId: Map<String, ResolvedProgressionPolicy>,
) {
    fun forPlanExercise(planExerciseId: String?): ResolvedProgressionPolicy =
        planExerciseId?.let(byPlanExerciseId::get) ?: fallback
}

/** Reads the existing settings schemas and resolves them into one immutable workout snapshot. */
class ProgressionPolicyStore(
    private val readString: suspend (String) -> String?,
) {
    constructor(settingsRepository: SettingsRepository) : this({ key -> settingsRepository.getString(key) })

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(
        planId: String?,
        planExerciseIds: Set<String>,
    ): ProgressionPolicySnapshot {
        val globalSetting = decodeGlobalProgressionStyle(readString(IRONLOG_SETTINGS_KEY))
        val normalizedPlanId = planId?.trim().orEmpty()
        val planRules = normalizedPlanId.takeIf(String::isNotEmpty)
            ?.let { decodeProgramRules(readString(programProgressionRulesKey(it))) }
        val fallback = ProgressionPolicyResolver.resolve(
            exerciseOverride = null,
            planRules = planRules,
            globalSetting = globalSetting,
        )
        val byExercise = linkedMapOf<String, ResolvedProgressionPolicy>()
        planExerciseIds.map(String::trim).filter(String::isNotEmpty).distinct().sorted().forEach { planExerciseId ->
            byExercise[planExerciseId] = ProgressionPolicyResolver.resolve(
                exerciseOverride = readString(exerciseProgressionOverrideKey(planExerciseId)),
                planRules = planRules,
                globalSetting = globalSetting,
            )
        }
        return ProgressionPolicySnapshot(fallback = fallback, byPlanExerciseId = byExercise.toMap())
    }

    private fun decodeProgramRules(raw: String?): ProgramRules? = raw
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { json.decodeFromString<ProgramRules>(it) }.getOrNull() }

    private fun decodeGlobalProgressionStyle(raw: String?): String? = raw
        ?.takeIf(String::isNotBlank)
        ?.let { encoded ->
            runCatching {
                json.parseToJsonElement(encoded).jsonObject["progressionStyle"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
}
