package com.ironlog.app.domain.intelligence

import com.ironlog.app.data.health.BiometricSnapshot
import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.ui.model.HistoryEntry
import java.time.Instant
import kotlin.math.exp
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

data class RecoveryScore(val score: Int, val state: String, val explanation: String)

object RecoveryReadinessEngine {
    /**
     * GAP-20: Pain-flagged regions are forced to 0.0 readiness regardless of training load.
     * All other regions are computed normally.
     */
    fun readinessByRegion(
        history: List<HistoryEntry>,
        painFlags: Set<String> = emptySet(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Map<String, Double> {
        val load = mutableMapOf("Push" to 0.0, "Pull" to 0.0, "Legs" to 0.0, "Core" to 0.0, "Arms" to 0.0, "Shoulders" to 0.0)
        history.forEach { w ->
            val t = parseHistoryInstant(w.date)?.toEpochMilli() ?: return@forEach
            val hours = ((nowEpochMs - t).coerceAtLeast(0) / 3_600_000.0)
            val decay = exp(-0.03 * hours)
            w.exercises.forEach { ex ->
                val workingSets = ex.sets.count { it.type != "warmup" }.toDouble()
                if (workingSets > 0) {
                    val contrib = resolveContribution(ex)
                    if (contrib.isNotEmpty()) {
                        // Distribute load across recovery regions proportionally
                        val regionFold = foldContributions(contrib, FINE_MUSCLE_TO_REGION)
                        regionFold.forEach { (region, frac) ->
                            load[region] = (load[region] ?: 0.0) + workingSets * frac * decay
                        }
                    } else {
                        // Fallback: coarse keyword classification
                        val key = when ((ex.primaryMuscle ?: ex.primaryMuscles.firstOrNull().orEmpty()).lowercase()) {
                            "chest" -> "Push"
                            "back", "lats" -> "Pull"
                            "quads", "hamstrings", "glutes", "calves", "legs", "leg" -> "Legs"
                            "biceps", "triceps", "arms", "forearms" -> "Arms"
                            "shoulders", "delts" -> "Shoulders"
                            else -> "Core"
                        }
                        load[key] = (load[key] ?: 0.0) + workingSets * decay
                    }
                }
            }
        }
        val base = load.mapValues { (_, v) -> (1.0 - (v / 8.0).coerceIn(0.0, 1.0)).coerceAtLeast(0.05) }
        return if (painFlags.isEmpty()) base else base.mapValues { (region, v) -> if (region in painFlags) 0.0 else v }
    }

    private fun scoreFromManualInput(input: ManualRecoveryInput?, nowEpochMs: Long): Int? {
        if (input == null) return null
        if (input.soreness == 0 && input.sleepQuality == 0 && input.energy == 0) return null
        // Ensure manual input isn't too stale (e.g., > 48 hours)
        if (input.recordedAt <= 0L || nowEpochMs - input.recordedAt > 48 * 3600_000L) return null
        
        val s = input.soreness.toDouble()
        val sl = input.sleepQuality.toDouble()
        val e = input.energy.toDouble()
        val normalized = (((sl + e) / 10.0) - (s / 10.0)).coerceIn(-1.0, 1.0)
        return (normalized * 15.0).roundToInt()
    }

    fun score(
        readiness: Map<String, Double>,
        manualInput: ManualRecoveryInput? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): RecoveryScore {
        val rows = readiness.values.filter { it.isFinite() }
        val baseScore = if (rows.isEmpty()) 70 else ((rows.average() * 100).coerceIn(1.0, 99.0)).roundToInt()
        
        val manualOffset = scoreFromManualInput(manualInput, nowEpochMs)
        val s = (baseScore + (manualOffset ?: 0)).coerceIn(1, 99)
        
        val state = if (s >= 78) "fresh" else if (s >= 55) "recovering" else "fatigued"
        val explanation = if (manualOffset == null) {
            "Based on recent muscle workload and time since training."
        } else {
            "Blended from workload history and your soreness/sleep/energy check-in."
        }
        return RecoveryScore(s, state, explanation)
    }

    fun suggestions(readiness: Map<String, Double>): List<String> {
        val rows = readiness.entries.sortedByDescending { it.value }
        if (rows.isEmpty()) return emptyList()
        val top = rows.first()
        val low = rows.last()
        val out = mutableListOf("${top.key} is most recovered (${(top.value * 100).roundToInt()}%).")
        if (top.key != low.key && low.value < 0.85) {
            out += "${low.key} is least recovered (${(low.value * 100).roundToInt()}%), reduce volume."
        }
        return out
    }

    /**
     * Blends a training-load [readinessScore] (0.0–1.0) with biometric data
     * from Health Connect.
     *
     * Weights:
     *   - Training load readiness: 60%
     *   - Sleep quality:           25%
     *   - HRV:                     15%
     *
     * If no biometric data is available (all nulls), returns [readinessScore] unchanged.
     *
     * @param readinessScore  Overall score from [score] (0.0 = very fatigued, 1.0 = fully ready).
     * @param biometrics      Latest snapshot from HealthConnectRepository.readBiometricSnapshot().
     */
    fun blendWithBiometric(readinessScore: Double, biometrics: BiometricSnapshot): Double {
        fun sleepScore(hours: Double) = ((hours - 4.0) / 4.0).coerceIn(0.0, 1.0)
        fun hrvScore(rmssd: Double)   = ((rmssd - 20.0) / 60.0).coerceIn(0.0, 1.0)

        val hasSleep = biometrics.sleepHours != null
        val hasHrv   = biometrics.hrvRmssd != null

        if (!hasSleep && !hasHrv) return readinessScore

        var weightedSum = readinessScore * 0.60
        var totalWeight = 0.60

        if (hasSleep) {
            weightedSum += sleepScore(biometrics.sleepHours!!) * 0.25
            totalWeight += 0.25
        }
        if (hasHrv) {
            weightedSum += hrvScore(biometrics.hrvRmssd!!) * 0.15
            totalWeight += 0.15
        }

        return (weightedSum / totalWeight).coerceIn(0.0, 1.0)
    }
}
