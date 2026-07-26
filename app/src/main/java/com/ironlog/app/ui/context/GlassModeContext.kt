package com.ironlog.app.ui.context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.ironlog.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val GLASS_MODE_STORAGE_KEY = "tab_bar_glass_mode_v1"

data class GlassModeState(
    val glassMode: String = "frosted",
    val setGlassMode: (String) -> Unit = {},
)

val LocalGlassMode = staticCompositionLocalOf { GlassModeState() }

@Composable
fun GlassModeProvider(repository: SettingsRepository?, scope: CoroutineScope, content: @Composable () -> Unit) {
    val state = remember { mutableStateOf("frosted") }
    // Load the persisted glass mode on first composition. Without this the value is always
    // "frosted" on app launch regardless of what the user last selected.
    LaunchedEffect(repository) {
        if (repository != null) {
            val stored = withContext(Dispatchers.IO) { repository.getString(GLASS_MODE_STORAGE_KEY) }
            if (stored != null) state.value = stored
        }
    }
    val holder = GlassModeState(
        glassMode = state.value,
        setGlassMode = { mode ->
            state.value = mode
            scope.launch { repository?.setSetting(GLASS_MODE_STORAGE_KEY, mode, "string") }
        }
    )
    CompositionLocalProvider(LocalGlassMode provides holder, content = content)
}

@Composable fun useGlassMode(): GlassModeState = LocalGlassMode.current
