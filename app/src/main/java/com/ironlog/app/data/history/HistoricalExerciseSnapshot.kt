package com.ironlog.app.data.history

import com.ironlog.app.data.objectbox.ExerciseEntity
import com.ironlog.app.data.objectbox.ExerciseMuscleEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Immutable display and interpretation metadata captured for one performed exercise.
 * The exercise UID remains the stable library identity used for progress grouping; this payload
 * prevents later library edits from rewriting the meaning or label of completed work.
 */
@Serializable
data class HistoricalExerciseSnapshot(
    val version: Int = CURRENT_HISTORICAL_EXERCISE_SNAPSHOT_VERSION,
    val name: String,
    val primaryMuscles: List<String> = emptyList(),
    val primaryMuscle: String? = null,
    val secondaryMuscles: List<String> = emptyList(),
    val muscleContributions: Map<String, Double> = emptyMap(),
    val equipment: String? = null,
    val category: String? = null,
    val trackingType: String? = null,
    val isBodyweight: Boolean = false,
    val requiresExternalLoad: Boolean = false,
)

const val CURRENT_HISTORICAL_EXERCISE_SNAPSHOT_VERSION = 1

/** Nullable members distinguish "not supplied" from an intentional false or empty value. */
data class HistoricalExerciseSnapshotOverride(
    val name: String? = null,
    val primaryMuscles: List<String>? = null,
    val primaryMuscle: String? = null,
    val secondaryMuscles: List<String>? = null,
    val muscleContributions: Map<String, Double>? = null,
    val equipment: String? = null,
    val category: String? = null,
    val trackingType: String? = null,
    val isBodyweight: Boolean? = null,
    val requiresExternalLoad: Boolean? = null,
)

object HistoricalExerciseSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: HistoricalExerciseSnapshot): String = json.encodeToString(snapshot)

    fun decode(raw: String?): HistoricalExerciseSnapshot? {
        if (raw.isNullOrBlank()) return null
        val decoded = runCatching { json.decodeFromString<HistoricalExerciseSnapshot>(raw) }.getOrNull()
            ?: return null
        if (decoded.version != CURRENT_HISTORICAL_EXERCISE_SNAPSHOT_VERSION || decoded.name.isBlank()) return null
        return decoded.copy(
            name = decoded.name.trim(),
            primaryMuscles = decoded.primaryMuscles.cleanNames(),
            primaryMuscle = decoded.primaryMuscle.cleanNullable(),
            secondaryMuscles = decoded.secondaryMuscles.cleanNames(),
            muscleContributions = decoded.muscleContributions.cleanContributions(),
            equipment = decoded.equipment.cleanNullable(),
            category = decoded.category.cleanNullable(),
            trackingType = decoded.trackingType.cleanNullable(),
        )
    }
}

fun captureHistoricalExerciseSnapshot(
    exercise: ExerciseEntity,
    muscles: List<ExerciseMuscleEntity> = emptyList(),
    override: HistoricalExerciseSnapshotOverride = HistoricalExerciseSnapshotOverride(),
): HistoricalExerciseSnapshot {
    val primaryFromRelations = muscles.asSequence()
        .filter { it.role.equals("primary", ignoreCase = true) }
        .map { it.muscle }
        .toList()
        .cleanNames()
    val secondaryFromEntity = exercise.secondaryMusclesJson?.let { raw ->
        runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }.orEmpty()
    val secondaryFromRelations = muscles.asSequence()
        .filter { it.role.equals("secondary", ignoreCase = true) }
        .map { it.muscle }
        .toList()
    val primaryMuscle = override.primaryMuscle.cleanNullable()
        ?: override.primaryMuscles?.cleanNames()?.firstOrNull()
        ?: exercise.primaryMuscle.cleanNullable()
    val primaryMuscles = override.primaryMuscles?.cleanNames()
        ?: primaryFromRelations.ifEmpty { listOfNotNull(primaryMuscle) }

    return HistoricalExerciseSnapshot(
        name = override.name.cleanNullable() ?: exercise.name.trim().ifBlank { "Unknown" },
        primaryMuscles = primaryMuscles,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = override.secondaryMuscles?.cleanNames()
            ?: (secondaryFromEntity + secondaryFromRelations).cleanNames(),
        muscleContributions = override.muscleContributions?.cleanContributions()
            ?: muscles.asSequence()
                .filter { it.muscle.isNotBlank() && it.contributionFraction.isFinite() && it.contributionFraction > 0.0 }
                .groupBy { it.muscle.trim() }
                .mapValues { (_, rows) -> rows.sumOf { it.contributionFraction } }
                .cleanContributions(),
        equipment = override.equipment.cleanNullable() ?: exercise.equipment.cleanNullable(),
        category = override.category.cleanNullable() ?: exercise.category.cleanNullable(),
        trackingType = override.trackingType.cleanNullable() ?: exercise.trackingType.cleanNullable(),
        isBodyweight = override.isBodyweight ?: exercise.isBodyweight,
        requiresExternalLoad = override.requiresExternalLoad ?: exercise.requiresExternalLoad,
    )
}

private fun String?.cleanNullable(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun List<String>.cleanNames(): List<String> = asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .toList()

private fun Map<String, Double>.cleanContributions(): Map<String, Double> = entries.asSequence()
    .filter { it.key.isNotBlank() && it.value.isFinite() && it.value > 0.0 }
    .groupBy { it.key.trim() }
    .mapValues { (_, entries) -> entries.sumOf { it.value } }
