package com.ironlog.app.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ironlog.app.ui.context.useTheme

/** The rest controls own measured space, outside the scroll/relocation viewport. */
@Composable
internal fun ActiveWorkoutViewport(
    modifier: Modifier = Modifier,
    imeInsets: WindowInsets = WindowInsets.ime,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable BoxScope.(WorkoutInputViewport) -> Unit,
) {
    val inputViewport = remember { WorkoutInputViewport() }
    Column(
        modifier.fillMaxSize().background(useTheme().bg).statusBarsPadding()
            .windowInsetsPadding(imeInsets),
    ) {
        Box(Modifier.weight(1f)) { content(inputViewport) }
        bottomBar()
    }
}
