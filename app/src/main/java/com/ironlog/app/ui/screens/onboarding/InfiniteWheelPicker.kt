package com.ironlog.app.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val WHEEL_ROWS = 5
private val WheelRowHeight = 54.dp

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
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialCenter - (WHEEL_ROWS / 2))
    val flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val scope = rememberCoroutineScope()
    var pendingIndex by remember(title, selected) { mutableIntStateOf(selectedIndex) }

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
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnboardingConfig.textMuted)
            }
            Text(
                title,
                color = OnboardingConfig.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(
                onClick = {
                    onConfirm(values[pendingIndex])
                    onDismiss()
                },
            ) {
                Text("Done", color = OnboardingConfig.accentBlue, fontWeight = FontWeight.ExtraBold)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelRowHeight * WHEEL_ROWS)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .background(OnboardingConfig.surfaceRaised, RoundedCornerShape(16.dp)),
            )

            LazyColumn(
                state = state,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(Int.MAX_VALUE) { virtualIndex ->
                    val index = virtualIndex.mod(values.size)
                    val active = index == pendingIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WheelRowHeight)
                            .alpha(if (active) 1f else 0.34f)
                            .clickable {
                                scope.launch {
                                    state.animateScrollToItem(
                                        index = (virtualIndex - WHEEL_ROWS / 2).coerceAtLeast(0),
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
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            color = OnboardingConfig.textFaint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
