package com.ironlog.app.domain.intelligence

import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.domain.training.TrainingSetPolicy
import java.time.Instant
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
data class ManualRecoveryInput(
    val soreness: Int = 0,
    val sleepQuality: Int = 0,
    val energy: Int = 0,
    val notes: String = "",
    val recordedAt: Long = 0L
)

data class RecoveryScore(
    val score: Int,
    val state: String,
    val explanation: String,
    val limitingRegion: String? = null,
    val hasEvidence: Boolean = true,
) {
    val scoreOrNull: Int? get() = score.takeIf { hasEvidence }
}

data class RegionWorkloadEvidence(val workoutId: String, val date: String, val exerciseName: String, val workingSets: Int, val contribution: Double)
data class RecoverySnapshot(
    val readiness: Map<String, Double>,
    val workloadEvidence: Map<String, List<RegionWorkloadEvidence>>,
    val painFlags: Set<String>,
    val score: RecoveryScore,
)

object RecoveryReadinessEngine {
    /**
     * GAP-20: Pain-flagged regions are forced to 0.0 readiness regardless of training load.
     * All other regions are computed normally.
     */
    fun readinessByRegion(
        history: List<HistoryEntry>,
        painFlags: Set<String> = emptySet(),
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Map<String, Double> = snapshot(history, painFlags, nowEpochMs = nowEpochMs, zoneId = zoneId).readiness

    fun snapshot(
        history: List<HistoryEntry>, painFlags: Set<String> = emptySet(),
        manualInput: ManualRecoveryInput? = null,
        nowEpochMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault(),
    ): RecoverySnapshot {
        val fatigue = linkedMapOf<String, Double>()
        val evidence = linkedMapOf<String, MutableList<RegionWorkloadEvidence>>()
        history.forEach { w ->
            val t = parseHistoryInstant(w.date, zoneId)?.toEpochMilli() ?: return@forEach
            // Future proof is not workload, including timestamps within a clock-skew margin.
            if (t > nowEpochMs) return@forEach
            val hours = ((nowEpochMs - t).coerceAtLeast(0) / 3_600_000.0)
            val sessionDose = mutableMapOf<String, Double>()
            var hasFailureWork = false
            var hasLowerBodyCompound = false
            w.exercises.forEach { ex ->
                val workingSets = ex.sets.filter { TrainingSetPolicy.isValidWorkingSet(ex, it) }
                if (workingSets.isEmpty()) return@forEach
                val regionFold = resolveRegionContribution(ex)
                regionFold.filterValues { it.isFinite() && it > 0.0 }.forEach { (region, fraction) ->
                    evidence.getOrPut(region) { mutableListOf() }.add(RegionWorkloadEvidence(w.id, w.date, ex.name, workingSets.size, fraction))
                }
                val exerciseFactor = exerciseRecoveryFactor(ex)
                if (exerciseFactor > 1.08 && regionFold.containsKey("Legs")) hasLowerBodyCompound = true
                workingSets.forEach { set ->
                    val setFactor = setRecoveryFactor(set)
                    if (set.type.equals("failure", true) || (set.rir != null && set.rir <= 0.0) || (set.rpe != null && set.rpe >= 10.0)) {
                        hasFailureWork = true
                    }
                    regionFold.forEach { (region, fraction) ->
                        sessionDose[region] = (sessionDose[region] ?: 0.0) + fraction * setFactor * exerciseFactor
                    }
                }
            }
            if (sessionDose.isEmpty()) return@forEach

            val totalDose = sessionDose.values.sum()
            val halfLifeHours = (18.0 *
                (1.0 + if (hasFailureWork) 0.28 else 0.0) *
                (1.0 + if (hasLowerBodyCompound) 0.12 else 0.0) *
                (1.0 + (totalDose / 24.0).coerceIn(0.0, 0.30)))
                .coerceIn(16.0, 36.0)
            // Two-phase approximation: acute metabolic/neuromuscular fatigue clears faster,
            // while the slower component represents damage/remodelling. This is still an
            // estimate, but better matches observed 24–72 h recovery than one decay constant.
            val timeRemaining = 0.35 * exp(-ln(2.0) * hours / 8.0) +
                0.65 * exp(-ln(2.0) * hours / halfLifeHours)

            sessionDose.forEach { (region, dose) ->
                // Saturating dose response prevents a single huge/imported session from
                // pinning a muscle at zero, while retaining extra fatigue from high volume.
                val initialDeficit = 1.0 - exp(-dose / 3.5)
                val remainingDeficit = initialDeficit * timeRemaining
                // Independent sessions combine probabilistically instead of adding without bound.
                val prior = fatigue[region] ?: 0.0
                fatigue[region] = 1.0 - (1.0 - prior) * (1.0 - remainingDeficit)
            }
        }
        val base = fatigue.mapValues { (_, deficit) -> (1.0 - deficit).coerceIn(0.05, 1.0) }
        val validPain = painFlags.intersect(RECOVERY_REGIONS.toSet())
        val readiness = base + validPain.associateWith { 0.0 }
        val scored = score(readiness, manualInput, nowEpochMs)
        val finalScore = if (validPain.isEmpty()) scored else scored.copy(
            state = "pain flagged", explanation = "Pain flagged in ${validPain.sorted().joinToString()}. Avoid painful movements; this estimate is not medical clearance.",
        )
        return RecoverySnapshot(readiness, evidence.mapValues { it.value.toList() }, validPain, finalScore)
    }

    private fun resolveRegionContribution(exercise: com.ironlog.app.ui.model.HistoryExercise): Map<String, Double> {
        val fine = resolveContribution(exercise)
        if (fine.isNotEmpty()) return foldContributions(fine, FINE_MUSCLE_TO_REGION)
        val key = when ((exercise.primaryMuscle ?: exercise.primaryMuscles.firstOrNull().orEmpty()).lowercase()) {
            "chest" -> "Push"
            "back", "lats" -> "Pull"
            "quads", "hamstrings", "glutes", "calves", "legs", "leg" -> "Legs"
            "biceps", "triceps", "arms", "forearms" -> "Arms"
            "shoulders", "delts" -> "Shoulders"
            "core", "abs", "abdominals", "obliques" -> "Core"
            else -> return emptyMap()
        }
        return mapOf(key to 1.0)
    }

    private fun setRecoveryFactor(set: com.ironlog.app.ui.model.HistoryExerciseSet): Double {
        val inferredRir = set.rir ?: set.rpe?.let { 10.0 - it }
        val effort = when {
            inferredRir == null -> 0.90
            inferredRir <= 0.0 -> 1.28
            inferredRir <= 1.0 -> 1.16
            inferredRir <= 2.0 -> 1.05
            inferredRir <= 3.0 -> 0.95
            else -> 0.80
        }
        val type = when (set.type.lowercase()) {
            "failure" -> 1.18
            "drop", "dropset" -> 1.14
            "amrap" -> 1.10
            else -> 1.0
        }
        val longSet = if (set.reps >= 12.0) 1.05 else 1.0
        return (effort * type * longSet).coerceIn(0.65, 1.55)
    }

    private fun exerciseRecoveryFactor(exercise: com.ironlog.app.ui.model.HistoryExercise): Double {
        val text = "${exercise.name} ${exercise.category.orEmpty()} ${exercise.equipment.orEmpty()}".lowercase()
        val lowerCompound = listOf("squat", "deadlift", "leg press", "lunge", "split squat", "hinge").any(text::contains)
        val lengthenedOrEccentric = listOf("romanian", "stiff leg", "good morning", "nordic", "fly", "pullover").any(text::contains)
        val isolation = listOf("curl", "extension", "raise", "pushdown", "calf").any(text::contains)
        return when {
            lowerCompound && lengthenedOrEccentric -> 1.22
            lowerCompound -> 1.14
            lengthenedOrEccentric -> 1.10
            isolation -> 0.90
            else -> 1.0
        }
    }

    private fun scoreFromManualInput(input: ManualRecoveryInput?, nowEpochMs: Long): Int? {
        if (input == null) return null
        if (input.soreness == 0 && input.sleepQuality == 0 && input.energy == 0) return null
        // Ensure manual input isn't too stale (e.g., > 48 hours)
        if (input.recordedAt <= 0L || input.recordedAt > nowEpochMs + 5 * 60_000L ||
            nowEpochMs - input.recordedAt > 48 * 3600_000L) return null
        
        val signals = buildList {
            if (input.soreness in 1..5) add(1.0 - (input.soreness - 1) / 4.0)
            if (input.sleepQuality in 1..5) add((input.sleepQuality - 1) / 4.0)
            if (input.energy in 1..5) add((input.energy - 1) / 4.0)
        }
        if (signals.isEmpty()) return null
        val centeredWellness = signals.average() - 0.5
        return (centeredWellness * 30.0).roundToInt().coerceIn(-15, 15)
    }

    fun score(
        readiness: Map<String, Double>,
        manualInput: ManualRecoveryInput? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): RecoveryScore {
        val rows = readiness.filterValues { it.isFinite() }.mapValues { it.value.coerceIn(0.0, 1.0) }.entries.sortedBy { it.value }
        if (rows.isEmpty()) return RecoveryScore(0, "unknown", "Log a workout with known muscle targets to begin estimating recovery. Your check-in does not replace recorded workload.", hasEvidence = false)
        val limitingRegion = rows.firstOrNull()?.key
        val limiting = rows.firstOrNull()?.value
        val lowestThreeAverage = rows.take(3).map { it.value }.average().takeIf { it.isFinite() }
        // Readiness is a go/no-go aid for the muscles the next workout may need. A simple
        // whole-body mean hid a fatigued Push region behind five untouched regions.
        val baseScore = if (limiting == null || lowestThreeAverage == null) 70 else {
            ((0.75 * limiting + 0.25 * lowestThreeAverage) * 100.0).coerceIn(1.0, 99.0).roundToInt()
        }
        
        val manualOffset = scoreFromManualInput(manualInput, nowEpochMs)
        val s = (baseScore + (manualOffset ?: 0)).coerceIn(1, 99)
        
        val state = if (s >= 85) "ready" else if (s >= 60) "recovering" else "fatigued"
        val explanation = if (manualOffset == null) {
            limitingRegion?.let { "Estimate led by $it workload, effort and time since training." }
                ?: "Not enough recent training data for a high-confidence estimate."
        } else {
            "Estimate blends the limiting muscle with your soreness, sleep and energy check-in."
        }
        return RecoveryScore(s, state, explanation, limitingRegion)
    }

    fun suggestions(readiness: Map<String, Double>): List<String> {
        val rows = readiness.filterValues { it.isFinite() }.mapValues { it.value.coerceIn(0.0, 1.0) }.entries.sortedByDescending { it.value }
        if (rows.isEmpty()) return emptyList()
        val top = rows.first()
        val low = rows.last()
        if (rows.size == 1) return listOf(
            "${top.key} estimated readiness: ${(top.value * 100).roundToInt()}%. Other regions have no mapped workload evidence." +
                if (top.value < 0.85) " Consider lighter work and reduce volume for this region." else ""
        )
        val out = mutableListOf("Among mapped regions, ${top.key} is most recovered (${(top.value * 100).roundToInt()}%).")
        if (top.key != low.key && low.value < 0.85) {
            out += "${low.key} is least recovered (${(low.value * 100).roundToInt()}%), reduce volume."
        }
        return out
    }

}
