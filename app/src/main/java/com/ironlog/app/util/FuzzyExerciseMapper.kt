package com.ironlog.app.util

import com.ironlog.app.data.objectbox.ExerciseEntity

class FuzzyExerciseMapper(exercises: List<ExerciseEntity>) {
    private val normMap = mutableMapOf<String, String>()
    private val lib = exercises.map { it to it.name.lowercase().replace(Regex("[^a-z0-9]"), "") }
    
    fun match(rawName: String): String? {
        val n = rawName.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (normMap.containsKey(n)) return normMap[n]
        
        val exact = lib.find { it.second == n }
        if (exact != null) {
            normMap[n] = exact.first.uid
            return exact.first.uid
        }
        
        var best: ExerciseEntity? = null
        var bestSim = 0.0
        for ((ex, norm) in lib) {
            val dist = levenshteinDistance(n, norm)
            if (dist > 3) continue
            val sim = 1.0 - (dist.toDouble() / maxOf(n.length, norm.length, 1))
            if (sim >= 0.8 && sim > bestSim) {
                bestSim = sim
                best = ex
            }
        }
        if (best != null) {
            normMap[n] = best.uid
            return best.uid
        }
        
        return null
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
