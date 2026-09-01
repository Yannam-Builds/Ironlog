package com.ironlog.app.data.plan

import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.PlanExerciseDefinition
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.ui.model.UiPlan
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Canonical plan sharing codec with backward-compatible aliases for legacy exports. */
object PlanJsonCodec {
    const val TYPE = "ironlog_plan"
    const val VERSION = 1

    data class DecodeResult(
        val plans: List<FullPlanObject>,
        val skipped: Int,
    )

    fun decode(raw: String): List<FullPlanObject> = decodeWithReport(raw).plans

    fun decodeWithReport(raw: String): DecodeResult {
        val text = raw.trim()
        require(text.isNotBlank()) { "Plan file is empty" }
        var skipped = 0
        val roots = when {
            text.startsWith("[") -> JSONArray(text).objects { skipped++ }
            else -> {
                val root = JSONObject(text)
                when {
                    root.optJSONObject("plan") != null -> listOf(root.getJSONObject("plan"))
                    root.has("plan") -> emptyList<JSONObject>().also { skipped++ }
                    root.optJSONArray("plans") != null -> root.getJSONArray("plans").objects { skipped++ }
                    root.has("plans") -> emptyList<JSONObject>().also { skipped++ }
                    else -> listOf(root)
                }
            }
        }
        val plans = roots.mapNotNull { plan -> decodePlan(plan) { skipped++ } }
        return DecodeResult(plans = plans, skipped = skipped)
    }

    fun encode(plan: UiPlan, exportedAt: String = Instant.now().toString()): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("type", TYPE)
        .put("exportedAt", exportedAt)
        .put("plan", JSONObject()
            .put("name", plan.name)
            .put("goal", plan.goal)
            .put("description", plan.description)
            .put("days", JSONArray().apply {
                plan.days.forEach { day ->
                    put(JSONObject()
                        .put("name", day.name)
                        .put("color", day.color)
                        .put("exercises", JSONArray().apply {
                            day.exercises.forEach { exercise ->
                                val encodedExercise = JSONObject()
                                    .put("exerciseId", exercise.exerciseId)
                                    .put("exerciseName", exercise.name)
                                    .put("sets", exercise.sets)
                                    .put("reps", exercise.reps)
                                    .put("restSeconds", exercise.restSeconds)
                                    .put("supersetGroup", exercise.supersetGroup)
                                    .put("isWarmup", exercise.isWarmup)
                                    .put("notes", exercise.notes)
                                if (exercise.isCustom) {
                                    encodedExercise.put("exerciseDefinition", JSONObject()
                                        .put("isCustom", true)
                                        .put("primaryMuscle", exercise.primaryMuscle)
                                        .put("equipment", exercise.equipment)
                                        .put("category", exercise.category)
                                        .put("trackingType", exercise.trackingType)
                                        .put("isBodyweight", exercise.isBodyweight)
                                        .put("movementPattern", exercise.movementPattern)
                                        .put("difficulty", exercise.difficulty)
                                        .put("notes", exercise.exerciseNotes)
                                        .put("secondaryMuscles", JSONArray(exercise.secondaryMuscles)))
                                }
                                put(encodedExercise)
                            }
                        }))
                }
            }))

    private fun decodePlan(plan: JSONObject, onSkipped: () -> Unit): FullPlanObject? {
        val name = plan.string("name")?.takeIf { it.isNotBlank() }
        if (name == null) {
            onSkipped()
            return null
        }
        val days = plan.arrayOrReport(onSkipped, "days", "planDays", "plan_days") ?: JSONArray()
        return FullPlanObject(
            name = name,
            goal = plan.string("goal")?.ifBlank { "General Fitness" } ?: "General Fitness",
            description = plan.string("description", "notes").orEmpty(),
            days = days.objects(onSkipped).mapIndexed { dayIndex, day ->
                val exercises = day.arrayOrReport(onSkipped, "exercises", "planExercises", "plan_exercises") ?: JSONArray()
                FullPlanDay(
                    name = day.string("name")?.ifBlank { "Day ${dayIndex + 1}" } ?: "Day ${dayIndex + 1}",
                    color = day.string("color")?.ifBlank { "#FF4500" } ?: "#FF4500",
                    exercises = exercises.objects(onSkipped).mapIndexedNotNull { exerciseIndex, exercise ->
                        val exerciseName = exercise.string("exerciseName", "exercise_name", "name")
                        val exerciseId = exercise.string("exerciseId", "exercise_id")
                        if (exerciseName.isNullOrBlank() && exerciseId.isNullOrBlank()) {
                            onSkipped()
                            return@mapIndexedNotNull null
                        }
                        PlanExerciseInput(
                            exerciseId = exerciseId?.takeIf { it.isNotBlank() },
                            name = exerciseName?.takeIf { it.isNotBlank() },
                            orderIndex = exercise.int("orderIndex", "order_index") ?: exerciseIndex,
                            sets = (exercise.int("sets") ?: 3).coerceAtLeast(1),
                            reps = exercise.string("reps")?.ifBlank { "8-12" } ?: "8-12",
                            restSeconds = (exercise.int("restSeconds", "rest_seconds", "rest") ?: 90).coerceAtLeast(0),
                            supersetGroup = exercise.string("supersetGroup", "superset_group").orEmpty(),
                            isWarmup = exercise.boolean("isWarmup", "is_warmup") ?: false,
                            notes = exercise.string("notes", "note").orEmpty(),
                            definition = exercise.exerciseDefinition(),
                        )
                    },
                )
            },
        )
    }

    private fun JSONObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        if (!has(key) || isNull(key)) null else opt(key)?.toString()
    }

    private fun JSONObject.int(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
        if (!has(key) || isNull(key)) null else opt(key)?.toString()?.toIntOrNull()
    }

    private fun JSONObject.boolean(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        if (!has(key) || isNull(key)) null else when (val value = opt(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
    }

    private fun JSONObject.array(vararg keys: String): JSONArray? = keys.firstNotNullOfOrNull(::optJSONArray)

    private fun JSONObject.arrayOrReport(onSkipped: () -> Unit, vararg keys: String): JSONArray? {
        val array = array(*keys)
        if (array == null && keys.any(::has)) onSkipped()
        return array
    }

    private fun JSONObject.exerciseDefinition(): PlanExerciseDefinition? {
        val nested = optJSONObject("exerciseDefinition")
            ?: optJSONObject("exercise_definition")
            ?: optJSONObject("customExercise")
            ?: optJSONObject("custom_exercise")
        val source = nested ?: this
        val hasDefinition = nested != null || listOf(
            "isCustom", "is_custom", "custom", "primaryMuscle", "primary_muscle",
            "equipment", "category", "trackingType", "tracking_type", "isBodyweight",
            "is_bodyweight", "movementPattern", "movement_pattern", "difficulty",
        ).any(source::has)
        if (!hasDefinition) return null
        return PlanExerciseDefinition(
            isCustom = source.boolean("isCustom", "is_custom", "custom"),
            primaryMuscle = source.string("primaryMuscle", "primary_muscle"),
            equipment = source.string("equipment"),
            category = source.string("category"),
            trackingType = source.string("trackingType", "tracking_type"),
            isBodyweight = source.boolean("isBodyweight", "is_bodyweight"),
            movementPattern = source.string("movementPattern", "movement_pattern"),
            difficulty = source.string("difficulty"),
            notes = if (nested != null) source.string("notes", "exerciseNotes", "exercise_notes") else source.string("exerciseNotes", "exercise_notes"),
            secondaryMuscles = source.array("secondaryMuscles", "secondary_muscles")?.strings().orEmpty(),
        )
    }

    private fun JSONArray.objects(onSkipped: () -> Unit): List<JSONObject> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index)
            if (item == null) onSkipped() else add(item)
        }
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }
}
