package com.ironlog.app.ui.screens.workout

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Retain the typing position when logged rows or a header banner change size. */
@Composable
internal fun Modifier.preserveWorkoutInputAnchor(
    listState: LazyListState,
    structuralKey: Any,
    viewport: WorkoutInputViewport,
    enabled: Boolean = true,
): Modifier {
    val anchor = remember { WorkoutInputAnchorState() }
    val placement = remember { WorkoutInputPlacement() }
    val scope = rememberCoroutineScope()
    val latestEnabled = rememberUpdatedState(enabled)
    val dragged = listState.interactionSource.collectIsDraggedAsState()
    val userScrollSession = remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            anchor.cancelPending()
            placement.scrollJob?.cancel()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { dragged.value to listState.isScrollInProgress }.collectLatest { (dragging, scrolling) ->
            if (dragging) {
                userScrollSession.value = true
                anchor.cancelPending()
                placement.scrollJob?.cancel()
            } else if (!scrolling) {
                // Drag release may hand over to a fling on the next frame.
                // Keep treating that entire session as user-owned scrolling.
                withFrameNanos { }
                if (!dragged.value && !listState.isScrollInProgress) userScrollSession.value = false
            }
        }
    }
    return this
        .onFocusChanged {
            anchor.setFocused(it.isFocused)
            if (!it.isFocused) placement.scrollJob?.cancel()
        }
        .onGloballyPositioned { coordinates ->
            placement.coordinates = coordinates
            val placementBounds = viewport.bounds
            val request = anchor.recordPlacement(
                structuralKey, coordinates.positionInRoot().y,
                enabled = latestEnabled.value, scrolling = dragged.value || userScrollSession.value,
                viewportTop = placementBounds?.top,
                viewportBottom = placementBounds?.bottom,
                inputHeight = coordinates.size.height.toFloat(),
            ) ?: return@onGloballyPositioned
            placement.scrollJob?.cancel()
            placement.scrollJob = scope.launch {
                // Re-read after layout/Compose relocation; never apply the raw
                // inserted-row height on top of a framework scroll.
                withFrameNanos { }
                // A footer resize can start Compose's own bring-into-view
                // animation. Let it settle, then correct only its residual;
                // actual drags/flings cancel this job via the session above.
                val settled = withTimeoutOrNull(2_000) {
                    snapshotFlow { listState.isScrollInProgress }.first { !it }
                    true
                } == true
                if (!settled) {
                    anchor.cancelPending(request)
                    return@launch
                }
                val currentCoordinates = placement.coordinates?.takeIf { it.isAttached }
                val currentTop = currentCoordinates?.positionInRoot()?.y
                val visibleBounds = viewport.bounds
                val delta = anchor.compensation(
                    request, currentTop,
                    enabled = latestEnabled.value, scrolling = dragged.value || userScrollSession.value,
                    minTop = visibleBounds?.top,
                    maxBottom = visibleBounds?.bottom,
                    inputHeight = currentCoordinates?.size?.height?.toFloat() ?: 0f,
                ) ?: return@launch
                if (delta != 0f) listState.scrollBy(delta)
            }
        }
}

/** Captured on the actual LazyColumn, after its outer padding has been applied. */
internal class WorkoutInputViewport {
    private var coordinates: LayoutCoordinates? = null
    fun capture(value: LayoutCoordinates) { coordinates = value }
    val bounds: Rect?
        get() = coordinates?.takeIf { it.isAttached }?.boundsInRoot()
}

private class WorkoutInputPlacement {
    var coordinates: LayoutCoordinates? = null
    var scrollJob: Job? = null
}

internal class WorkoutInputAnchorState {
    internal data class Request(val sequence: Long, val anchorTop: Float)

    private var focused = false
    private var previousKey: Any? = null
    private var previousTop: Float? = null
    private var previousWasVisible = false
    private var sequence = 0L
    private var pending: Request? = null

    fun setFocused(value: Boolean) {
        focused = value
        if (!value) cancelPending()
    }

    fun cancelPending(request: Request? = null) {
        if (request == null || request == pending) pending = null
    }

    fun recordPlacement(
        key: Any,
        top: Float,
        enabled: Boolean = true,
        scrolling: Boolean = false,
        viewportTop: Float? = null,
        viewportBottom: Float? = null,
        inputHeight: Float = 0f,
    ): Request? {
        val oldTop = previousTop
        val wasVisible = previousWasVisible
        val changed = previousKey != key
        previousKey = key
        previousTop = top
        previousWasVisible = viewportTop == null || viewportBottom == null ||
            (top >= viewportTop && top + inputHeight <= viewportBottom)
        if (!focused || !enabled || scrolling) {
            pending = null
            return null
        }
        if (!changed || oldTop == null) return null
        // Focus can remain on a pinned, offscreen item after navigation. A late
        // save must not resurrect that old typing position. Use PRE-change
        // visibility so an appearing footer can still clamp a legitimate anchor.
        // Rapid insertions retain their already-pending, originally visible one.
        if (!wasVisible && pending == null) return null
        return Request(++sequence, pending?.anchorTop ?: oldTop).also { pending = it }
    }

    fun compensation(
        request: Request,
        currentTop: Float?,
        enabled: Boolean = true,
        scrolling: Boolean = false,
        minTop: Float? = null,
        maxBottom: Float? = null,
        inputHeight: Float = 0f,
    ): Float? {
        if (pending != request) return null
        pending = null
        if (!focused || !enabled || scrolling || currentTop == null) return null
        val targetTop = if (minTop != null && maxBottom != null) {
            request.anchorTop.coerceIn(minTop, maxOf(minTop, maxBottom - inputHeight))
        } else request.anchorTop
        return currentTop - targetTop
    }
}
