package com.ironlog.app.data.repository

import org.json.JSONArray
import org.json.JSONObject

internal fun validateLegacySnapshot(snapshot: JSONObject) {
    fun rows(parent: JSONObject, key: String): List<JSONObject> {
        if (!parent.has(key)) return emptyList()
        val array = parent.opt(key) as? JSONArray ?: error("$key must be an array.")
        return (0 until array.length()).map { index -> array.opt(index) as? JSONObject ?: error("$key row ${index + 1} must be an object.") }
    }
    fun children(parent: JSONObject, first: String, second: String): List<JSONObject> {
        val a = rows(parent, first)
        val b = rows(parent, second)
        return if (parent.has(first)) a else b
    }
    rows(snapshot, "plans").forEach { plan ->
        children(plan, "days", "workoutDays").forEach { day -> children(day, "exercises", "items") }
    }
    rows(snapshot, "history").forEach { workout ->
        require(workout.keys().hasNext()) { "Empty workout row." }
        children(workout, "exercises", "items").forEach { exercise ->
            children(exercise, "sets", "logs").forEach { set ->
                listOf("weight", "reps", "rpe", "rir").forEach { key ->
                    if (set.has(key) && !set.isNull(key)) {
                        val number = set.opt(key)?.toString()?.toDoubleOrNull()
                        require(number != null && number.isFinite() && number >= 0) { "Invalid $key in legacy set." }
                    }
                }
            }
        }
    }
    listOf("customExercises", "bodyWeight", "bodyMeasurements").forEach { rows(snapshot, it) }
}
