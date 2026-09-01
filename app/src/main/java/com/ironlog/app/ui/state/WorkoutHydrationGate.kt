package com.ironlog.app.ui.state

/** A null startup identity or failed read cannot consume the first successful DB hydration. */
internal class WorkoutHydrationGate {
    private var checkpoint: Pair<String, List<String>>? = null
    suspend fun reconcile(workoutId: String?, exerciseIds: List<String>, hydrate: suspend (List<String>) -> Boolean) {
        if (workoutId.isNullOrBlank() || exerciseIds.isEmpty()) return
        val key = workoutId to exerciseIds.toList()
        if (checkpoint == key) return
        // Removal has its own committed reducer operation; do not race an old exercise list.
        val old = checkpoint
        val removedOnly = old?.first == workoutId && exerciseIds.size < old.second.size &&
            exerciseIds.groupingBy { it }.eachCount().all { (id, count) -> count <= old.second.count { it == id } }
        if (removedOnly || hydrate(exerciseIds)) checkpoint = key
    }
}
