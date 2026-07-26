package com.ironlog.app.domain.ai

import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.util.normalizeExerciseNameKey
import kotlin.math.max

data class SmartPlanStructuringResult(
    val plan: FullPlanObject,
    val notes: List<String>,
    val insertedWarmupRows: Int,
    val suggestedSupersetRows: Int,
    val adjustedRestRows: Int,
)

private data class ExerciseProfile(
    val trackingType: String,
    val movementPattern: String?,
    val category: String,
    val equipment: String,
    val primaryMuscle: String?,
)

object SmartPlanStructuringEngine {
    private val COMPOUND_PATTERNS = setOf("push", "pull", "squat", "hinge", "lunge", "carry")
    private val SUPERSET_FRIENDLY_PATTERNS = setOf("isolation", "push", "pull")
    private val SUPERSET_FRIENDLY_EQUIPMENT = setOf("cable", "dumbbell", "machine", "band")

    fun structure(plan: FullPlanObject, library: List<LegacyExerciseShape>): SmartPlanStructuringResult {
        val byId = library.associateBy { it.id }
        val byName = library.associateBy { normalizeExerciseNameKey(it.name) }

        var warmupAdds = 0
        var supersetAdds = 0
        var restAdjustments = 0
        val notes = mutableListOf<String>()

        val newDays = plan.days.map { day ->
            val baseRows = day.exercises.toMutableList()
            val withWarmups = mutableListOf<PlanExerciseInput>()
            val warmedCompounds = mutableSetOf<String>()

            baseRows.forEach { row ->
                val profile = resolveProfile(row, byId, byName)
                val key = row.exerciseId ?: normalizeExerciseNameKey(row.name.orEmpty())
                val isCompound = isCompound(profile)
                val isWarmup = row.isWarmup == true
                val isTime = isTimeBased(profile, row)

                if (isCompound && !isWarmup && !isTime && !warmedCompounds.contains(key)) {
                    val hasWarmupAlready = baseRows.any { candidate ->
                        (candidate.exerciseId == row.exerciseId || normalizeExerciseNameKey(candidate.name.orEmpty()) == normalizeExerciseNameKey(row.name.orEmpty())) &&
                            candidate.isWarmup == true
                    }
                    if (!hasWarmupAlready) {
                        withWarmups += row.copy(
                            sets = 2,
                            reps = if (isTime) "45" else "8-10",
                            restSeconds = 60,
                            supersetGroup = null,
                            isWarmup = true,
                            notes = mergeNote(row.notes, "Auto warmup"),
                        )
                        warmupAdds++
                    }
                    warmedCompounds += key
                }

                val normalizedRest = normalizeRest(row, profile)
                if (normalizedRest != (row.restSeconds ?: 0)) {
                    restAdjustments++
                }
                withWarmups += row.copy(restSeconds = normalizedRest)
            }

            val (withSupersets, daySupersetAdds) = applySupersetSuggestions(withWarmups, byId, byName)
            supersetAdds += daySupersetAdds

            FullPlanDay(
                name = day.name,
                color = day.color,
                exercises = withSupersets,
            )
        }

        if (warmupAdds > 0) notes += "Auto-added $warmupAdds warmup row(s) for first heavy compounds."
        if (supersetAdds > 0) notes += "Suggested supersets for $supersetAdds exercise row(s)."
        if (restAdjustments > 0) notes += "Normalized rest on $restAdjustments row(s) for better pacing."

        return SmartPlanStructuringResult(
            plan = plan.copy(days = newDays),
            notes = notes,
            insertedWarmupRows = warmupAdds,
            suggestedSupersetRows = supersetAdds,
            adjustedRestRows = restAdjustments,
        )
    }

    private fun applySupersetSuggestions(
        rows: List<PlanExerciseInput>,
        byId: Map<String, LegacyExerciseShape>,
        byName: Map<String, LegacyExerciseShape>,
    ): Pair<List<PlanExerciseInput>, Int> {
        val out = rows.toMutableList()
        var counter = 0
        var groupIndex = 0
        var i = 0
        while (i < out.lastIndex) {
            val a = out[i]
            val b = out[i + 1]
            if (canSuggestSuperset(a, b, byId, byName)) {
                val group = ('A'.code + (groupIndex % 26)).toChar().toString()
                out[i] = a.copy(supersetGroup = group)
                out[i + 1] = b.copy(supersetGroup = group)
                counter += 2
                groupIndex++
                i += 2
                continue
            }
            i++
        }
        return out to counter
    }

    private fun canSuggestSuperset(
        a: PlanExerciseInput,
        b: PlanExerciseInput,
        byId: Map<String, LegacyExerciseShape>,
        byName: Map<String, LegacyExerciseShape>,
    ): Boolean {
        if (a.isWarmup == true || b.isWarmup == true) return false
        if (!a.supersetGroup.isNullOrBlank() || !b.supersetGroup.isNullOrBlank()) return false
        val pa = resolveProfile(a, byId, byName)
        val pb = resolveProfile(b, byId, byName)
        if (isTimeBased(pa, a) || isTimeBased(pb, b)) return false
        if (isCompound(pa) || isCompound(pb)) return false
        val repA = extractRepFloor(a.reps)
        val repB = extractRepFloor(b.reps)
        if (repA < 8 || repB < 8) return false
        val pattA = pa?.movementPattern?.lowercase()
        val pattB = pb?.movementPattern?.lowercase()
        val eqA = pa?.equipment?.lowercase().orEmpty()
        val eqB = pb?.equipment?.lowercase().orEmpty()
        val patOk = (pattA in SUPERSET_FRIENDLY_PATTERNS || eqA in SUPERSET_FRIENDLY_EQUIPMENT) &&
            (pattB in SUPERSET_FRIENDLY_PATTERNS || eqB in SUPERSET_FRIENDLY_EQUIPMENT)
        if (!patOk) return false
        val mA = pa?.primaryMuscle?.lowercase().orEmpty()
        val mB = pb?.primaryMuscle?.lowercase().orEmpty()
        return mA.isNotBlank() && mB.isNotBlank() && mA != mB
    }

    private fun normalizeRest(row: PlanExerciseInput, profile: ExerciseProfile?): Int {
        val current = row.restSeconds ?: 0
        if (row.isWarmup == true) {
            return if (current > 0) current.coerceIn(45, 90) else 60
        }
        if (isTimeBased(profile, row)) {
            return if (current > 0) current.coerceIn(30, 75) else 45
        }
        return if (isCompound(profile)) {
            if (current > 0) current.coerceIn(120, 210) else 150
        } else {
            if (current > 0) current.coerceIn(60, 120) else 75
        }
    }

    private fun isCompound(profile: ExerciseProfile?): Boolean {
        val pattern = profile?.movementPattern?.lowercase()
        return pattern in COMPOUND_PATTERNS
    }

    private fun isTimeBased(profile: ExerciseProfile?, row: PlanExerciseInput): Boolean {
        val tracking = profile?.trackingType?.lowercase().orEmpty()
        if (tracking.startsWith("duration")) return true
        val reps = row.reps.orEmpty().lowercase()
        return "sec" in reps || "min" in reps || reps.endsWith("s")
    }

    private fun resolveProfile(
        row: PlanExerciseInput,
        byId: Map<String, LegacyExerciseShape>,
        byName: Map<String, LegacyExerciseShape>,
    ): ExerciseProfile? {
        val matched = row.exerciseId?.let(byId::get) ?: byName[normalizeExerciseNameKey(row.name.orEmpty())]
        return matched?.let {
            ExerciseProfile(
                trackingType = it.trackingType,
                movementPattern = it.movementPattern,
                category = it.category,
                equipment = it.equipment,
                primaryMuscle = it.primaryMuscle ?: it.primaryMuscles.firstOrNull(),
            )
        }
    }

    private fun extractRepFloor(raw: String?): Int {
        val src = raw.orEmpty()
        val ints = Regex("\\d+").findAll(src).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (ints.isEmpty()) return 0
        return max(0, ints.minOrNull() ?: 0)
    }

    private fun mergeNote(current: String?, extra: String): String {
        val base = current.orEmpty().trim()
        if (base.isBlank()) return extra
        if (base.contains(extra, ignoreCase = true)) return base
        return "$base | $extra"
    }
}
