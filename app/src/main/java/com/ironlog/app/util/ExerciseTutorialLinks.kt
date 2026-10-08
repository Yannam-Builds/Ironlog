package com.ironlog.app.util

/** Preserve exact normalized names, then support the compact keys in the bundled tutorial asset. */
fun exerciseTutorialLink(links: Map<String, String>, name: String): String? {
    val key = normalizeExerciseNameKey(name)
    return links[key] ?: links[key.replace("_", "")]
}
