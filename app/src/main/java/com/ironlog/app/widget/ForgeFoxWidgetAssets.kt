package com.ironlog.app.widget

import androidx.annotation.DrawableRes
import com.ironlog.app.R

object ForgeFoxWidgetAssets {
    @DrawableRes
    val streakIcon: Int = R.drawable.ic_forge_streak_dumbbell

    @DrawableRes
    fun mascotFor(state: WidgetVisualState): Int = when (state) {
        WidgetVisualState.ACTIVE_STREAK -> R.drawable.forgefox_widget_active
        WidgetVisualState.AT_RISK -> R.drawable.forgefox_widget_at_risk
        WidgetVisualState.WORKOUT_SOON -> R.drawable.forgefox_widget_workout_soon
        WidgetVisualState.WORKOUT_DONE -> R.drawable.forgefox_widget_done
        WidgetVisualState.RECOVERY_DAY -> R.drawable.forgefox_widget_recovery
        WidgetVisualState.EARLY_WORKOUT -> R.drawable.forgefox_widget_early_workout
        WidgetVisualState.NO_EXCUSES -> R.drawable.forgefox_widget_coach
        WidgetVisualState.NEW_PB -> R.drawable.forgefox_widget_pb
        WidgetVisualState.COMEBACK -> R.drawable.forgefox_widget_comeback
        WidgetVisualState.MISSION_COMPLETE -> R.drawable.forgefox_widget_mission_complete
    }
}
