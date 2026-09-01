package com.ironlog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.components.SetRow
import com.ironlog.app.ui.context.ThemeProvider
import com.ironlog.app.ui.screens.workout.ActiveWorkoutViewport
import com.ironlog.app.ui.screens.workout.preserveWorkoutInputAnchor
import com.ironlog.app.ui.state.LoggedSet
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic insets isolate layout; real keyboard replay remains a device check. */
@OptIn(ExperimentalComposeUiApi::class)
class ActiveWorkoutImeLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun consumedAncestorInsetsAreNotCountedAgainInWorkoutViewport() {
        compose.setContent {
            ThemeProvider("Dark") {
                val density = LocalDensity.current
                val keyboard = WindowInsets(bottom = with(density) { 240.dp.roundToPx() })
                val consumed = WindowInsets(bottom = with(density) { 100.dp.roundToPx() })
                Box(Modifier.requiredSize(360.dp, 600.dp).testTag("frame")) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(consumed)) {
                        ActiveWorkoutViewport(imeInsets = keyboard) {
                            Box(Modifier.fillMaxSize().testTag("viewport-content"))
                        }
                    }
                }
            }
        }
        val frame = compose.onNodeWithTag("frame").getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag("viewport-content").getUnclippedBoundsInRoot()
        val expectedBottom = frame.bottom - 240.dp
        assertTrue(
            "Only the unconsumed keyboard inset should shrink the workout viewport: $content",
            kotlin.math.abs((content.bottom - expectedBottom).value) <= 1f,
        )
    }

    @Test fun firstLoggedSetKeepsFocusedInputAndRestOverlayAboveKeyboard() = checkLoggedSetAnchor(initiallyScrolled = true)

    @Test fun firstSetAndHeaderGrowthKeepTheFocusedInputVisualAnchor() = checkLoggedSetAnchor(initiallyScrolled = false)

    @Test fun tallerRestControlsDoNotCoverTheLowerFocusedInput() = checkLoggedSetAnchor(initiallyScrolled = false, restHeightDp = 144)

    @Test fun separateInsertionsKeyboardReopenAndLastSetDeletionPreserveUsableFocus() =
        checkLoggedSetAnchor(initiallyScrolled = true, checkLifecycle = true)

    @Test fun delayedSourceWriteAfterSupersetNavigationDoesNotPullBackFromDestination() =
        checkDelayedSupersetWrite(transferFocus = false)

    @Test fun transferredFocusStaysOnDestinationWhenTheSourceWriteArrivesLater() =
        checkDelayedSupersetWrite(transferFocus = true)

    private fun checkLoggedSetAnchor(initiallyScrolled: Boolean, restHeightDp: Int = 88, checkLifecycle: Boolean = false) {
        val insetDp = mutableIntStateOf(0)
        val loggedCount = mutableIntStateOf(0)
        val bannerVisible = mutableStateOf(false)
        lateinit var listState: LazyListState
        compose.setContent {
            ThemeProvider("Dark") {
                MaterialTheme {
                    // Preserve real Compose focus and relocation, but do not ask
                    // the platform IME to compete with the deterministic inset.
                    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                        val insetPx = with(LocalDensity.current) { insetDp.intValue.dp.roundToPx() }
                        Box(Modifier.requiredSize(360.dp, 600.dp).testTag("frame")) {
                            ActiveWorkoutViewport(
                                imeInsets = WindowInsets(bottom = insetPx),
                                bottomBar = {
                                    if (loggedCount.intValue > 0) {
                                        Box(Modifier.fillMaxWidth().height(restHeightDp.dp).testTag("rest-overlay"))
                                    }
                                },
                            ) { inputViewport ->
                                listState = rememberLazyListState()
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().padding(16.dp).testTag("workout-list")
                                        .onGloballyPositioned(inputViewport::capture),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                ) {
                                    item("header") {
                                        Spacer(Modifier.height(
                                            (if (loggedCount.intValue > 0) 168.dp else 140.dp) +
                                                (if (bannerVisible.value) 40.dp else 0.dp),
                                        ))
                                    }
                                    item("pr-banner") { Spacer(Modifier.height(0.dp)) }
                                    item("exercise") {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text("Synthetic bench press")
                                            repeat(loggedCount.intValue) { index ->
                                                SetRow(
                                                    set = LoggedSet(id = "set-$index", weight = 65.0, reps = 8.0),
                                                    setIndex = index,
                                                    exIndex = 0,
                                                    dispatch = {},
                                                    effortTracking = "both",
                                                    hapticFeedback = false,
                                                )
                                            }
                                            OutlinedTextField(
                                                value = "8",
                                                onValueChange = {},
                                                label = { Text("REPS") },
                                                modifier = Modifier.fillMaxWidth().testTag("reps")
                                                    .preserveWorkoutInputAnchor(listState, List(loggedCount.intValue) { "set-$it" } to bannerVisible.value, inputViewport),
                                            )
                                            Text("Exercise note and rest controls")
                                        }
                                    }
                                    item("next-exercise") { Spacer(Modifier.height(420.dp)) }
                                    item("finish") { Spacer(Modifier.height(52.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Match an athlete already scrolled to the exercise, typing the first
        // set. Logging expands the same keyed item, not a replaced list.
        if (initiallyScrolled) compose.onNodeWithTag("workout-list").performScrollToIndex(2)
        compose.onNodeWithTag("reps").performClick().assertIsFocused()
        compose.runOnIdle { insetDp.intValue = 240 }
        compose.waitForIdle()
        val inputBefore = compose.onNodeWithTag("reps").getUnclippedBoundsInRoot()
        compose.runOnIdle { loggedCount.intValue = 1 }
        compose.waitForIdle()

        val frame = compose.onNodeWithTag("frame").getUnclippedBoundsInRoot()
        val keyboardTop = frame.bottom - 240.dp
        val list = compose.onNodeWithTag("workout-list").getUnclippedBoundsInRoot()
        val rest = compose.onNodeWithTag("rest-overlay").getUnclippedBoundsInRoot()
        val input = compose.onNodeWithTag("reps").assertIsFocused().getUnclippedBoundsInRoot()
        assertTrue("List viewport must end above IME, not behind it: $list", list.bottom <= keyboardTop + 1.dp)
        assertTrue("Rest controls must end above IME: $rest", rest.bottom <= keyboardTop + 1.dp)
        assertTrue("First set must leave focused input above keyboard: $input", input.bottom <= keyboardTop + 1.dp)
        assertTrue("Rest controls must not cover the focused input: input=$input rest=$rest", input.bottom <= rest.top + 1.dp)
        assertTrue("First set must not move focused input above viewport: $input", input.top >= list.top - 1.dp)
        val usableBottom = minOf(list.bottom, rest.top)
        val expectedTop = minOf(inputBefore.top, usableBottom - (input.bottom - input.top))
        assertTrue(
            "Preserve the input anchor unless rest controls require an upward clamp: before=$inputBefore after=$input expectedTop=$expectedTop",
            kotlin.math.abs((input.top - expectedTop).value) <= 1f,
        )
        compose.runOnIdle {
            if (initiallyScrolled) assertTrue("Logging must not reset the exercise scroll anchor to the header", listState.firstVisibleItemIndex > 0)
        }
        fun assertAnchorAfter(change: () -> Unit) {
            val before = compose.onNodeWithTag("reps").getUnclippedBoundsInRoot()
            compose.runOnIdle(change)
            compose.waitForIdle()
            val after = compose.onNodeWithTag("reps").assertIsFocused().getUnclippedBoundsInRoot()
            val restBounds = compose.onNodeWithTag("rest-overlay").getUnclippedBoundsInRoot()
            assertTrue("Structural updates must not put the input under rest controls", after.bottom <= restBounds.top + 1.dp)
            assertTrue("Structural updates must retain the input anchor: before=$before after=$after",
                kotlin.math.abs((after.top - before.top).value) <= 1f)
        }
        assertAnchorAfter { bannerVisible.value = true }
        assertAnchorAfter { bannerVisible.value = false }
        assertAnchorAfter { loggedCount.intValue = 2; loggedCount.intValue = 3 }
        assertAnchorAfter { loggedCount.intValue = 2 }
        if (checkLifecycle) {
            // Distinct commits/frames, not two coalesced writes to one snapshot.
            assertAnchorAfter { loggedCount.intValue = 3 }
            assertAnchorAfter { loggedCount.intValue = 4 }
            compose.runOnIdle { loggedCount.intValue = 0 }
            compose.waitForIdle()
            compose.onNodeWithTag("rest-overlay").assertDoesNotExist()

            fun assertFocusedInsideList() {
                val visible = compose.onNodeWithTag("workout-list").getUnclippedBoundsInRoot()
                val focused = compose.onNodeWithTag("reps").assertIsFocused().getUnclippedBoundsInRoot()
                assertTrue("Input must stay in the usable list viewport: input=$focused list=$visible",
                    focused.top >= visible.top - 1.dp && focused.bottom <= visible.bottom + 1.dp)
            }
            assertFocusedInsideList()
            compose.runOnIdle { insetDp.intValue = 0 }
            compose.waitForIdle()
            assertFocusedInsideList()
            compose.runOnIdle { insetDp.intValue = 240 }
            compose.waitForIdle()
            assertFocusedInsideList()
        }
    }

    private fun checkDelayedSupersetWrite(transferFocus: Boolean) {
        val sourceSetCount = mutableIntStateOf(0)
        val anchorEnabled = mutableStateOf(true)
        lateinit var listState: LazyListState
        var sourceFocused = false
        compose.setContent {
            ThemeProvider("Dark") {
                MaterialTheme {
                    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                        val keyboardPx = with(LocalDensity.current) { 240.dp.roundToPx() }
                        Box(Modifier.requiredSize(360.dp, 600.dp)) {
                            ActiveWorkoutViewport(imeInsets = WindowInsets(bottom = keyboardPx)) { inputViewport ->
                                listState = rememberLazyListState()
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().padding(16.dp).testTag("superset-list")
                                        .onGloballyPositioned(inputViewport::capture),
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                ) {
                                    item("source-exercise") {
                                        Column(Modifier.fillMaxWidth().heightIn(min = 360.dp)) {
                                            Text("Source superset exercise")
                                            repeat(sourceSetCount.intValue) { index ->
                                                SetRow(
                                                    LoggedSet(id = "source-set-$index", weight = 65.0, reps = 8.0),
                                                    index, 0, dispatch = {}, effortTracking = "both", hapticFeedback = false,
                                                )
                                            }
                                            OutlinedTextField(
                                                value = "8", onValueChange = {}, label = { Text("Source REPS") },
                                                modifier = Modifier.fillMaxWidth().testTag("source-input")
                                                    .preserveWorkoutInputAnchor(
                                                        listState, List(sourceSetCount.intValue) { "source-set-$it" },
                                                        inputViewport, enabled = anchorEnabled.value,
                                                    )
                                                    .onFocusChanged { sourceFocused = it.isFocused },
                                            )
                                            Spacer(Modifier.height(220.dp))
                                        }
                                    }
                                    item("destination-exercise") {
                                        Column(Modifier.fillMaxWidth().height(420.dp)) {
                                            Text("Next superset exercise", modifier = Modifier.testTag("destination-title"))
                                            OutlinedTextField(
                                                value = "10", onValueChange = {}, label = { Text("Next REPS") },
                                                modifier = Modifier.fillMaxWidth().testTag("destination-input"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("source-input").performClick().assertIsFocused()
        compose.runOnIdle { anchorEnabled.value = false }
        compose.onNodeWithTag("superset-list").performScrollToIndex(1)
        compose.onNodeWithTag("destination-title").assertIsDisplayed()
        if (transferFocus) {
            compose.onNodeWithTag("destination-input").performClick().assertIsFocused()
        } else {
            compose.runOnIdle {
                assertTrue("Exercise navigation must retain source focus to exercise the delayed-write risk", sourceFocused)
            }
        }
        compose.runOnIdle { anchorEnabled.value = true }
        compose.waitForIdle()
        var destinationIndex = -1
        var destinationOffset = -1
        compose.runOnIdle {
            destinationIndex = listState.firstVisibleItemIndex
            destinationOffset = listState.firstVisibleItemScrollOffset
        }

        // Model the source set reaching Compose only after asynchronous save and
        // navigation finish. The modifier sees a separate, later composition.
        compose.runOnIdle { sourceSetCount.intValue = 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("destination-title").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("A delayed source write must not undo completed superset navigation",
                listState.firstVisibleItemIndex == destinationIndex &&
                    kotlin.math.abs(listState.firstVisibleItemScrollOffset - destinationOffset) <= 1)
        }
        if (transferFocus) compose.onNodeWithTag("destination-input").assertIsFocused().assertIsDisplayed()
    }
}
