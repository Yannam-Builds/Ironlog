package com.ironlog.app.ui.screens.onboarding

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.ironlog.app.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun virtualWheelStart(valueCount: Int, selectedIndex: Int): Int {
    require(valueCount > 0)
    val midpoint = Int.MAX_VALUE / 2
    return midpoint - (midpoint % valueCount) + selectedIndex.coerceIn(0, valueCount - 1)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InfiniteNumberWheelSheet(
    title: String,
    values: List<Int>,
    selected: Int,
    labelFor: (Int) -> String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (values.isEmpty()) return

    val selectedIndex = values.indexOf(selected).takeIf { it >= 0 } ?: 0
    val initialCenter = remember(title, values, selectedIndex) {
        virtualWheelStart(values.size, selectedIndex)
    }
    val rowSpec = wheelRowLayoutSpec(LocalDensity.current.fontScale)
    val rowHeight = rowSpec.heightDp.dp
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialCenter - (rowSpec.visibleRowCount / 2),
    )
    val flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val scope = rememberCoroutineScope()
    var pendingIndex by remember(title, selected) { mutableIntStateOf(selectedIndex) }
    val headerSpec = remember { wheelSheetHeaderLayoutSpec() }

    LaunchedEffect(state, values) {
        snapshotFlow { state.layoutInfo }
            .mapNotNull { layout ->
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                layout.visibleItemsInfo.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - center)
                }?.index
            }
            .distinctUntilChanged()
            .collect { virtualIndex -> pendingIndex = virtualIndex.mod(values.size) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OnboardingConfig.surfaceDark,
        contentColor = OnboardingConfig.textPrimary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(headerSpec.actionSlotWeight)
                    .heightIn(min = headerSpec.minTouchTargetDp.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    "Cancel",
                    color = OnboardingConfig.textMuted,
                    maxLines = headerSpec.actionMaxLines,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                title,
                color = OnboardingConfig.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = headerSpec.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(headerSpec.titleSlotWeight),
            )
            TextButton(
                onClick = {
                    onConfirm(values[pendingIndex])
                    onDismiss()
                },
                modifier = Modifier
                    .weight(headerSpec.actionSlotWeight)
                    .heightIn(min = headerSpec.minTouchTargetDp.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    "Done",
                    color = OnboardingConfig.accentBlue,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = headerSpec.actionMaxLines,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * rowSpec.visibleRowCount)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(OnboardingConfig.surfaceRaised, RoundedCornerShape(16.dp)),
            )

            LazyColumn(
                state = state,
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = title
                        stateDescription = labelFor(values[pendingIndex])
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = pendingIndex.toFloat(),
                            range = 0f..values.lastIndex.toFloat(),
                            steps = (values.size - 2).coerceAtLeast(0),
                        )
                        setProgress { target ->
                            val targetIndex = target.roundToInt().coerceIn(values.indices)
                            scope.launch {
                                state.animateScrollToItem(
                                    index = (
                                        virtualWheelStart(values.size, targetIndex) -
                                            rowSpec.visibleRowCount / 2
                                        ).coerceAtLeast(0),
                                )
                            }
                            true
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(Int.MAX_VALUE) { virtualIndex ->
                    val index = virtualIndex.mod(values.size)
                    val active = index == pendingIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .alpha(if (active) 1f else 0.34f)
                            .clickable {
                                scope.launch {
                                    state.animateScrollToItem(
                                        index = (
                                            virtualIndex - rowSpec.visibleRowCount / 2
                                            ).coerceAtLeast(0),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            labelFor(values[index]),
                            color = if (active) OnboardingConfig.textPrimary else OnboardingConfig.textMuted,
                            fontSize = if (active) 27.sp else 19.sp,
                            fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = rowSpec.valueMaxLines,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to OnboardingConfig.surfaceDark,
                            0.24f to Color.Transparent,
                            0.76f to Color.Transparent,
                            1f to OnboardingConfig.surfaceDark,
                        ),
                    ),
            )
        }

        Text(
            "Swipe to adjust · values snap to center",
            modifier = Modifier.fillMaxWidth().appPadding(top = 10.dp),
            color = OnboardingConfig.textFaint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(appGapDp(24.dp)))
    }
}
