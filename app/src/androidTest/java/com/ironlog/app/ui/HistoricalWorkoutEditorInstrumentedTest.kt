package com.ironlog.app.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.data.model.HistoricalWorkoutConflict
import com.ironlog.app.data.model.HistoricalWorkoutSaveResult
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.screens.history.HistoricalExerciseChoice
import com.ironlog.app.ui.screens.history.HistoricalWorkoutEditor
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoricalWorkoutEditorInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailedEntrySelectsExerciseEditsSetAndSavesWithoutLiveSession() {
        var saved: CreateCompletedWorkoutInput? = null
        content(onSave = { input, _ -> saved = input; HistoricalWorkoutSaveResult.Saved(input.uid!!) })
        fillMetadata()
        compose.onNodeWithText("Synthetic bench").performScrollTo().performClick()
        compose.onNodeWithText("Load (kg)").performScrollTo().performTextReplacement("40")
        compose.onNodeWithText("Reps").performTextReplacement("8")
        compose.onNodeWithText("Add set").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Note").performScrollTo().performClick()
        compose.onNodeWithText("Set note...").performTextReplacement("Synthetic note")
        compose.onNodeWithText("SAVE TO HISTORY").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Synthetic note", saved!!.exerciseData.single().sets.single().notes)
            assertEquals(40.0, saved!!.exerciseData.single().sets.single().weight!!, 0.0)
            assertEquals(Instant.parse("2026-08-31T04:00:00Z").toEpochMilli(), saved!!.startedAt)
        }
        compose.onAllNodesWithText("FINISH WORKOUT").assertCountEquals(0)
    }

    @Test fun failedSaveRetainsDraftAndRetryKeepsTheSameUid() {
        val ids = mutableListOf<String>()
        content(detailed = false, onSave = { input, _ ->
            ids += input.uid!!
            if (ids.size == 1) error("Synthetic failure")
            HistoricalWorkoutSaveResult.Saved(input.uid!!)
        })
        fillMetadata()
        compose.onNodeWithText("SAVE TO HISTORY").performScrollTo().performClick()
        compose.onNodeWithText("Could not save this historical workout. Your draft is intact; try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SAVE TO HISTORY").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, ids.size); assertEquals(ids.first(), ids.last()) }
    }

    @Test fun quickSummaryRetainsOptionalWorkoutRating() {
        var saved: CreateCompletedWorkoutInput? = null
        content(detailed = false, onSave = { input, _ -> saved = input; HistoricalWorkoutSaveResult.Saved(input.uid!!) })
        fillMetadata()
        compose.onNodeWithContentDescription("Workout rating 5 out of 5").performScrollTo().performClick()
        compose.onNodeWithText("SAVE TO HISTORY").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(5.0, saved!!.rating!!, 0.0) }
    }

    @Test fun bodyweightEntryUsesRepsWithoutARequiredLoadField() {
        var saved: CreateCompletedWorkoutInput? = null
        content(choices = listOf(HistoricalExerciseChoice("bodyweight", "Synthetic bodyweight exercise", "bodyweight_reps")),
            onSave = { input, _ -> saved = input; HistoricalWorkoutSaveResult.Saved(input.uid!!) })
        fillMetadata()
        compose.onNodeWithText("Synthetic bodyweight exercise").performScrollTo().performClick()
        compose.onAllNodesWithText("Load (kg)").assertCountEquals(0)
        compose.onNodeWithText("Reps").performScrollTo().performTextReplacement("12")
        compose.onNodeWithText("Add set").performScrollTo().performClick()
        compose.onNodeWithText("SAVE TO HISTORY").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0.0, saved!!.exerciseData.single().sets.single().weight!!, 0.0) }
    }

    @Test fun duplicateDateRequiresAddAnotherAndDoesNotSilentlyRetry() {
        val choices = mutableListOf<Boolean>()
        content(detailed = false, onSave = { input, allow ->
            choices += allow
            if (!allow) HistoricalWorkoutSaveResult.DateConflict(listOf(HistoricalWorkoutConflict("existing", "Earlier session", input.startedAt)))
            else HistoricalWorkoutSaveResult.Saved(input.uid!!)
        })
        fillMetadata()
        compose.onNodeWithText("SAVE TO HISTORY").performScrollTo().performClick()
        compose.onNodeWithText("A workout is already recorded on this date").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(false), choices) }
        compose.onNodeWithText("Add another").performClick()
        compose.runOnIdle { assertEquals(listOf(false, true), choices) }
    }

    @Test fun largeTextShortViewportKeepsSaveReachableAndBackProtectsDraft() {
        content(detailed = false, fontScale = 2f, modifier = Modifier.width(320.dp).height(300.dp))
        fillMetadata()
        compose.onNodeWithText("Workout notes").performScrollTo().performTextReplacement("Keep this draft")
        compose.onNodeWithText("SAVE TO HISTORY").performScrollTo().assertIsDisplayed()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        pressBack()
        compose.onNodeWithText("Discard historical workout draft?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Keep this draft").performScrollTo().assertIsDisplayed()
    }

    private fun fillMetadata() {
        compose.onNodeWithText("Start time (HH:MM)").performScrollTo().performTextReplacement("09:30")
        compose.onNodeWithText("Duration (minutes)").performScrollTo().performTextReplacement("20")
    }

    private fun content(
        detailed: Boolean = true,
        fontScale: Float = 1f,
        modifier: Modifier = Modifier,
        choices: List<HistoricalExerciseChoice> = listOf(HistoricalExerciseChoice("bench", "Synthetic bench")),
        onSave: suspend (CreateCompletedWorkoutInput, Boolean) -> HistoricalWorkoutSaveResult = { input, _ -> HistoricalWorkoutSaveResult.Saved(input.uid!!) },
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ThemeProvider("dark") { MaterialTheme {
                    HistoricalWorkoutEditor(initialDate = "2026-08-31", initialDetailed = detailed,
                        choices = choices, weightUnit = "kg",
                        onDismiss = {}, onSave = onSave, modifier = modifier,
                        now = Instant.parse("2026-09-01T12:00:00Z"), zoneId = ZoneId.of("Asia/Kolkata"))
                } }
            }
        }
    }
}
