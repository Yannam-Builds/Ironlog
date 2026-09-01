package com.ironlog.app.ui.theme

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalSpacingScale = staticCompositionLocalOf { SpacingSettings.DEFAULT }
val LocalCardSpacingScale = staticCompositionLocalOf<Float?> { null }
val LocalPaddingSpacingScale = staticCompositionLocalOf<Float?> { null }
val LocalContentSpacingScale = staticCompositionLocalOf<Float?> { null }

object SpacingRuntime {
    @Volatile private var instance: SpacingStore? = null
    fun store(context: Context): SpacingStore = instance ?: synchronized(this) {
        instance ?: SpacingStore(object : SpacingStorage {
            private val preferences = context.applicationContext.getSharedPreferences(AppearancePreferenceKeys.FILE, Context.MODE_PRIVATE)
            override fun read(): Any? = preferences.all[AppearancePreferenceKeys.GLOBAL_SPACING]
            override fun write(scale: Float) {
                val before = SpacingSettings.normalize(read())
                if (!preferences.edit().putFloat(AppearancePreferenceKeys.GLOBAL_SPACING, scale).commit()) {
                    preferences.edit().putFloat(AppearancePreferenceKeys.GLOBAL_SPACING, before).apply()
                    error("Spacing could not be saved. Please try again.")
                }
            }
        }).also { instance = it }
    }

    private val roleStores = mutableMapOf<SpacingRole, SpacingStore>()

    @Synchronized
    fun store(context: Context, role: SpacingRole): SpacingStore = roleStores.getOrPut(role) {
        SpacingStore(object : SpacingStorage {
            private val preferences = context.applicationContext.getSharedPreferences(AppearancePreferenceKeys.FILE, Context.MODE_PRIVATE)
            override fun read(): Any = role.initialValue(preferences.all)
            override fun write(scale: Float) {
                val before = read() as Float
                if (!preferences.edit().putFloat(role.preferenceKey, scale).commit()) {
                    preferences.edit().putFloat(role.preferenceKey, before).apply()
                    error("Spacing could not be saved. Please try again.")
                }
            }
        })
    }
}

/** Layout rhythm only. Never use this for font sizes, icons, touch-target sizes, or drawing geometry. */
@Composable
fun appGapDp(gap: Dp, role: SpacingRole = SpacingRole.CONTENT): Dp {
    val scale = when (role) {
        SpacingRole.CARDS -> LocalCardSpacingScale.current
        SpacingRole.PADDING -> LocalPaddingSpacingScale.current
        SpacingRole.CONTENT -> LocalContentSpacingScale.current
    } ?: LocalSpacingScale.current
    return SpacingSettings.scaleGapDp(gap.value, scale).dp
}

@Composable
fun appCardSpacedBy(space: Dp): Arrangement.HorizontalOrVertical =
    Arrangement.spacedBy(appGapDp(space, SpacingRole.CARDS))

@Composable
fun appSpacedBy(space: Dp): Arrangement.HorizontalOrVertical = Arrangement.spacedBy(appGapDp(space))

@Composable
fun appSpacedBy(space: Dp, alignment: Alignment.Horizontal): Arrangement.Horizontal = Arrangement.spacedBy(appGapDp(space), alignment)

@Composable
fun appSpacedBy(space: Dp, alignment: Alignment.Vertical): Arrangement.Vertical = Arrangement.spacedBy(appGapDp(space), alignment)

// composed keeps these regular Modifier extensions usable in existing modifier-building helpers.
// Only audited non-interactive, non-fixed-geometry padding is routed through them.
fun Modifier.appPadding(all: Dp): Modifier = composed { padding(appGapDp(all, SpacingRole.PADDING)) }

fun Modifier.appPadding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): Modifier = composed {
    padding(horizontal = appGapDp(horizontal, SpacingRole.PADDING), vertical = appGapDp(vertical, SpacingRole.PADDING))
}

fun Modifier.appPadding(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): Modifier = composed {
    padding(start = appGapDp(start, SpacingRole.PADDING), top = appGapDp(top, SpacingRole.PADDING), end = appGapDp(end, SpacingRole.PADDING), bottom = appGapDp(bottom, SpacingRole.PADDING))
}

/** Scaffold/system insets are absolute safe areas and must not shrink with compact spacing. */
fun Modifier.appPadding(paddingValues: PaddingValues): Modifier = padding(paddingValues)
