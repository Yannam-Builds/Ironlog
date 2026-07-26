package com.ironlog.app.widget

import androidx.compose.ui.graphics.Color

internal data class ForgeWidgetPresentation(
    val title: String,
    val message: String,
    val background: Color,
    val accent: Color,
)

internal fun resolveForgeWidgetLayoutClass(
    width: Float,
    height: Float,
    preferredLayout: ForgeWidgetLayoutClass,
): ForgeWidgetLayoutClass {
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
        return preferredLayout
    }

    return when {
        width >= 240f && width > height * 1.28f -> ForgeWidgetLayoutClass.WIDE
        height >= 240f && height > width * 1.12f -> ForgeWidgetLayoutClass.TALL
        width <= 150f || height <= 150f -> ForgeWidgetLayoutClass.SMALL
        else -> ForgeWidgetLayoutClass.MEDIUM
    }
}

internal fun forgePresentationFor(
    visualState: WidgetVisualState,
    minutesUntilWorkout: Int?,
): ForgeWidgetPresentation = when (visualState) {
    WidgetVisualState.ACTIVE_STREAK -> ForgeWidgetPresentation(
        title = "DAY STREAK",
        message = "Keep it up!",
        background = Color(0xFFFF3D00),
        accent = Color(0xFFFFD166),
    )
    WidgetVisualState.AT_RISK -> ForgeWidgetPresentation(
        title = "AT RISK",
        message = "Don't let it break!",
        background = Color(0xFFC91616),
        accent = Color(0xFFFF9A42),
    )
    WidgetVisualState.WORKOUT_SOON -> ForgeWidgetPresentation(
        title = "WORKOUT SOON",
        message = minutesUntilWorkout?.let { "Starts in ${it.coerceAtLeast(0)} min" } ?: "I'm waiting...",
        background = Color(0xFF5E1FB4),
        accent = Color(0xFFFFB24A),
    )
    WidgetVisualState.WORKOUT_DONE -> ForgeWidgetPresentation(
        title = "WORKOUT DONE!",
        message = "Streak saved",
        background = Color(0xFF118A3B),
        accent = Color(0xFF95F27C),
    )
    WidgetVisualState.RECOVERY_DAY -> ForgeWidgetPresentation(
        title = "RECOVERY DAY",
        message = "Rest and recover",
        background = Color(0xFF0B4C91),
        accent = Color(0xFF8ED6FF),
    )
    WidgetVisualState.EARLY_WORKOUT -> ForgeWidgetPresentation(
        title = "EARLY WORKOUT?",
        message = "Start your workout!",
        background = Color(0xFF7A1FC8),
        accent = Color(0xFFFFB24A),
    )
    WidgetVisualState.NO_EXCUSES -> ForgeWidgetPresentation(
        title = "NO EXCUSES.",
        message = "Start your workout",
        background = Color(0xFF111111),
        accent = Color(0xFFFF6A00),
    )
    WidgetVisualState.NEW_PB -> ForgeWidgetPresentation(
        title = "NEW PB!",
        message = "Strong work",
        background = Color(0xFF0E7F42),
        accent = Color(0xFFFFD34D),
    )
    WidgetVisualState.COMEBACK -> ForgeWidgetPresentation(
        title = "BACK ON TRACK!",
        message = "Keep moving",
        background = Color(0xFFFF9F0A),
        accent = Color(0xFF4A2300),
    )
    WidgetVisualState.MISSION_COMPLETE -> ForgeWidgetPresentation(
        title = "MISSION COMPLETE",
        message = "Workout done!",
        background = Color(0xFFD99A13),
        accent = Color(0xFFFFFFFF),
    )
}
