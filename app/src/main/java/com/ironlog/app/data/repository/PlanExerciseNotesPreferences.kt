package com.ironlog.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

const val PLAN_EXERCISE_NOTES_VISIBLE_KEY = "plan_exercise_notes_visible_v1"

/**
 * Plan instructions stay visible for existing users unless they explicitly turn them off.
 * Unknown legacy values fail open so an upgrade cannot silently hide saved instructions.
 */
fun parsePlanExerciseNotesVisible(raw: String?): Boolean =
    raw?.trim()?.equals("false", ignoreCase = true) != true

fun SettingsRepository.observePlanExerciseNotesVisible(): Flow<Boolean> =
    observeStrings(setOf(PLAN_EXERCISE_NOTES_VISIBLE_KEY))
        .map { values -> parsePlanExerciseNotesVisible(values[PLAN_EXERCISE_NOTES_VISIBLE_KEY]) }

suspend fun SettingsRepository.setPlanExerciseNotesVisible(visible: Boolean) =
    setBoolean(PLAN_EXERCISE_NOTES_VISIBLE_KEY, visible)
