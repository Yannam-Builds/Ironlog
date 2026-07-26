package com.ironlog.app.ui.context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.ironlog.app.ui.state.WorkoutState

/** Translation of ActiveWorkoutBannerContext.js. */
data class ActiveWorkoutBanner(
    val planIndex: Int? = null,
    val dayIndex: Int? = null,
    val dayName: String? = null,
    val startTime: Long? = null,
    val isDeload: Boolean = false,
)

data class ActiveWorkoutBannerState(
    val banner: ActiveWorkoutBanner? = null,
    val setBanner: (ActiveWorkoutBanner?) -> Unit = {},
    val minimized: Boolean = false,
    val setMinimized: (Boolean) -> Unit = {},
    val saveWorkoutState: (WorkoutState?) -> Unit = {},
    val getSavedWorkoutState: () -> WorkoutState? = { null },
    val clearSavedWorkoutState: () -> Unit = {},
)

val LocalActiveWorkoutBanner = staticCompositionLocalOf { ActiveWorkoutBannerState() }

@Composable
fun ActiveWorkoutBannerProvider(content: @Composable () -> Unit) {
    val banner = remember { mutableStateOf<ActiveWorkoutBanner?>(null) }
    val minimized = remember { mutableStateOf(false) }
    val savedWorkoutState = remember { mutableStateOf<WorkoutState?>(null) }
    val holder = ActiveWorkoutBannerState(
        banner = banner.value,
        setBanner = { banner.value = it },
        minimized = minimized.value,
        setMinimized = { minimized.value = it },
        saveWorkoutState = { savedWorkoutState.value = it },
        getSavedWorkoutState = { savedWorkoutState.value },
        clearSavedWorkoutState = { savedWorkoutState.value = null },
    )
    CompositionLocalProvider(LocalActiveWorkoutBanner provides holder, content = content)
}

@Composable fun useActiveBanner(): ActiveWorkoutBannerState = LocalActiveWorkoutBanner.current
