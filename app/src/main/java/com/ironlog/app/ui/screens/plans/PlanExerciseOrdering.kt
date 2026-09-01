package com.ironlog.app.ui.screens.plans

/** Lazy-list positions include headers; only exercise IDs may identify a move. */
internal fun movePlanExerciseIds(ids: List<String>, fromKey: Any?, toKey: Any?): List<String> {
    val from = ids.indexOf(fromKey as? String ?: return ids)
    val to = ids.indexOf(toKey as? String ?: return ids)
    if (from < 0 || to < 0 || from == to) return ids
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

/** A successful result means persistence finished, not merely that a write was launched. */
internal suspend fun persistPlanExerciseMove(
    ids: List<String>,
    fromKey: Any?,
    toKey: Any?,
    persist: suspend (List<String>) -> Unit,
): Boolean {
    val reordered = movePlanExerciseIds(ids, fromKey, toKey)
    if (reordered == ids) return false
    persist(reordered)
    return true
}
