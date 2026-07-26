package com.ironlog.app.domain.ai

import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.util.normalizeExerciseNameKey

enum class ResolutionStatus {
    MATCHED,
    NEEDS_REVIEW,
    UNRESOLVED,
}

data class ExerciseResolution(
    val status: ResolutionStatus,
    val matched: LegacyExerciseShape? = null,
    val confidence: Int = 0,
    val reason: String = "",
    val topCandidates: List<Pair<LegacyExerciseShape, Int>> = emptyList(),
)

object ExerciseResolutionEngine {
    private const val STRONG_MATCH_THRESHOLD = 88
    private const val REVIEW_MATCH_THRESHOLD = 52
    private val MUSCLE_HINTS = mapOf(
        "chest" to setOf("chest", "pec", "pecs", "pectoral"),
        "back" to setOf("back", "lat", "lats", "row"),
        "shoulders" to setOf("shoulder", "shoulders", "delt", "delts"),
        "biceps" to setOf("bicep", "biceps", "curl"),
        "triceps" to setOf("tricep", "triceps", "pushdown"),
        "quads" to setOf("quad", "quads", "thigh"),
        "hamstrings" to setOf("hamstring", "hamstrings"),
        "glutes" to setOf("glute", "glutes"),
        "calves" to setOf("calf", "calves"),
        "core" to setOf("core", "abs", "ab", "abdominal"),
        "forearms" to setOf("forearm", "forearms", "grip"),
        "traps" to setOf("trap", "traps", "shrug"),
        "cardio" to setOf("cardio", "run", "bike", "treadmill", "rower"),
    )
    private val EQUIPMENT_HINTS = mapOf(
        "barbell" to setOf("barbell", "bb"),
        "dumbbell" to setOf("dumbbell", "db"),
        "cable" to setOf("cable"),
        "machine" to setOf("machine", "smith"),
        "bodyweight" to setOf("bodyweight", "bw", "pullup", "pushup", "dip"),
        "band" to setOf("band"),
        "kettlebell" to setOf("kettlebell", "kb"),
    )
    private val MOVEMENT_HINTS = mapOf(
        "push" to setOf("push", "press"),
        "pull" to setOf("pull", "row"),
        "hinge" to setOf("hinge", "deadlift", "rdl"),
        "squat" to setOf("squat", "legpress"),
        "lunge" to setOf("lunge", "split squat"),
        "isolation" to setOf("curl", "extension", "raise", "fly"),
        "conditioning" to setOf("conditioning", "metcon", "interval"),
    )

    fun resolve(name: String, all: List<LegacyExerciseShape>): ExerciseResolution {
        val normalized = normalizeExerciseNameKey(name)
        if (normalized.isBlank()) {
            return ExerciseResolution(
                status = ResolutionStatus.UNRESOLVED,
                confidence = 0,
                reason = "Blank exercise name",
            )
        }

        val exact = all.firstOrNull { normalizeExerciseNameKey(it.name) == normalized }
        if (exact != null) {
            return ExerciseResolution(
                status = ResolutionStatus.MATCHED,
                matched = exact,
                confidence = 100,
                reason = "Exact normalized name",
            )
        }

        val aliasExact = all.firstOrNull { ex -> ex.aliases.any { normalizeExerciseNameKey(it) == normalized } }
        if (aliasExact != null) {
            return ExerciseResolution(
                status = ResolutionStatus.MATCHED,
                matched = aliasExact,
                confidence = 98,
                reason = "Alias exact match",
            )
        }

        val ranked = all.map { candidate ->
            val score = scoreSimilarity(normalized, candidate)
            candidate to score
        // Stable deterministic sort: primary by score desc, secondary by name asc so ties are repeatable.
        }.sortedWith(compareByDescending<Pair<LegacyExerciseShape, Int>> { it.second }.thenBy { it.first.name })

        val best = ranked.firstOrNull() ?: return ExerciseResolution(
            status = ResolutionStatus.UNRESOLVED,
            confidence = 0,
            reason = "No library candidates",
        )
        val top = ranked.take(6)

        val bestCandidate = best.first
        val bestScore = best.second
        return when {
            bestScore >= STRONG_MATCH_THRESHOLD -> ExerciseResolution(
                status = ResolutionStatus.MATCHED,
                matched = bestCandidate,
                confidence = bestScore,
                reason = "High-confidence fuzzy match",
                topCandidates = top,
            )

            bestScore >= REVIEW_MATCH_THRESHOLD -> ExerciseResolution(
                status = ResolutionStatus.NEEDS_REVIEW,
                matched = bestCandidate,
                confidence = bestScore,
                reason = "Ambiguous match candidate",
                topCandidates = top,
            )

            else -> ExerciseResolution(
                status = ResolutionStatus.UNRESOLVED,
                confidence = bestScore,
                reason = "Low similarity",
                topCandidates = top,
            )
        }
    }

    private fun scoreSimilarity(input: String, candidate: LegacyExerciseShape): Int {
        val candidateName = normalizeExerciseNameKey(candidate.name)
        val inputTokens = tokenize(input).toSet()
        val candidateTokens = tokenize(candidateName).toSet()
        val nameTokenScore = tokenJaccardScore(input, candidateName)
        val editScore = editSimilarityScore(input, candidateName)
        // Substring / prefix bonuses.
        val containsBonus = when {
            input == candidateName -> 15
            (input.contains(candidateName) || candidateName.contains(input)) &&
                kotlin.math.abs(input.length - candidateName.length) <= 6 -> 10
            else -> 0
        }
        // Prefix token bonus: reward when the input tokens are a leading subset of the candidate tokens.
        val prefixTokenBonus = run {
            val cTokenList = tokenize(candidateName)
            val iTokenList = tokenize(input)
            if (iTokenList.isNotEmpty() && cTokenList.size >= iTokenList.size &&
                cTokenList.take(iTokenList.size) == iTokenList) 8 else 0
        }
        val extraInputPenalty = ((inputTokens - candidateTokens).size * 3).coerceAtMost(9)
        val extraCandidatePenalty = ((candidateTokens - inputTokens).size * 2).coerceAtMost(6)
        val typoTokenBonus = fuzzyTokenBonus(inputTokens, candidateTokens)
        val aliasTokenScore = candidate.aliases.maxOfOrNull {
            tokenJaccardScore(input, normalizeExerciseNameKey(it)).coerceAtLeast(editSimilarityScore(input, normalizeExerciseNameKey(it)))
        } ?: 0
        val hintBonus = hintBonusScore(input, candidate)
        val base = ((nameTokenScore * 0.52) + (editScore * 0.48)).toInt()
        val weighted = base + containsBonus + prefixTokenBonus + hintBonus + typoTokenBonus - extraInputPenalty - extraCandidatePenalty
        return weighted.coerceAtLeast(aliasTokenScore + hintBonus).coerceIn(0, 100)
    }

    private fun tokenJaccardScore(a: String, b: String): Int {
        val ta = tokenize(a).toSet()
        val tb = tokenize(b).toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0
        val overlap = ta.intersect(tb).size.toDouble()
        val union = ta.union(tb).size.toDouble()
        return ((overlap / union) * 100.0).toInt().coerceIn(0, 100)
    }

    private fun editSimilarityScore(a: String, b: String): Int {
        if (a.isBlank() || b.isBlank()) return 0
        val dist = damerauLevenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length).coerceAtLeast(1)
        return ((1.0 - (dist.toDouble() / maxLen.toDouble())) * 100.0).toInt().coerceIn(0, 100)
    }

    // Optimal-string-alignment variant; good typo tolerance for adjacent swaps ("teh" -> "the").
    private fun damerauLevenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = minOf(best, dp[i - 2][j - 2] + 1)
                }
                dp[i][j] = best
            }
        }
        return dp[m][n]
    }

    private fun hintBonusScore(input: String, candidate: LegacyExerciseShape): Int {
        val inputTokens = tokenize(input).toSet()
        var bonus = 0
        val muscleKey = candidate.primaryMuscle?.lowercase()?.trim()
        if (!muscleKey.isNullOrBlank()) {
            val hints = MUSCLE_HINTS[muscleKey]
            if (!hints.isNullOrEmpty() && hints.any { it in inputTokens }) bonus += 8
        }
        val equipKey = candidate.equipment.lowercase().trim()
        val equipHints = EQUIPMENT_HINTS[equipKey]
        if (!equipHints.isNullOrEmpty() && equipHints.any { it in inputTokens }) bonus += 7
        val movementKey = candidate.movementPattern?.lowercase()?.trim()
        val movementHints = movementKey?.let { MOVEMENT_HINTS[it] }
        if (!movementHints.isNullOrEmpty() && movementHints.any { it in inputTokens }) bonus += 6
        return bonus
    }

    private fun tokenize(value: String): List<String> = value
        .split(Regex("[\\s_\\-]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun fuzzyTokenBonus(inputTokens: Set<String>, candidateTokens: Set<String>): Int {
        if (inputTokens.isEmpty() || candidateTokens.isEmpty()) return 0
        var bonus = 0
        for (token in inputTokens) {
            val best = candidateTokens.maxOfOrNull { editSimilarityScore(token, it) } ?: 0
            if (best >= 82) bonus += 5
            else if (best >= 70) bonus += 3
        }
        return bonus.coerceAtMost(14)
    }
}
