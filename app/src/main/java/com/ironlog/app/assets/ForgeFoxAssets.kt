package com.ironlog.app.assets

import androidx.annotation.DrawableRes
import com.ironlog.app.R

enum class ForgeFoxExpression(
    val id: String,
    val displayName: String,
    val category: String,
    @DrawableRes val drawableRes: Int
) {
    Neutral(
        id = "forgefox_01_neutral",
        displayName = "Neutral",
        category = "core",
        drawableRes = R.drawable.forgefox_01_neutral
    ),

    Smile(
        id = "forgefox_02_smile",
        displayName = "Smile",
        category = "core",
        drawableRes = R.drawable.forgefox_02_smile
    ),

    BigHappy(
        id = "forgefox_03_big_happy",
        displayName = "Big Happy",
        category = "emotion",
        drawableRes = R.drawable.forgefox_03_big_happy
    ),

    Excited(
        id = "forgefox_04_excited",
        displayName = "Excited",
        category = "emotion",
        drawableRes = R.drawable.forgefox_04_excited
    ),

    Laughing(
        id = "forgefox_05_laughing",
        displayName = "Laughing",
        category = "emotion",
        drawableRes = R.drawable.forgefox_05_laughing
    ),

    WinkTeasing(
        id = "forgefox_06_wink_teasing",
        displayName = "Wink / Teasing",
        category = "emotion",
        drawableRes = R.drawable.forgefox_06_wink_teasing
    ),

    Determined(
        id = "forgefox_07_determined",
        displayName = "Determined",
        category = "fitness",
        drawableRes = R.drawable.forgefox_07_determined
    ),

    Serious(
        id = "forgefox_08_serious",
        displayName = "Serious",
        category = "fitness",
        drawableRes = R.drawable.forgefox_08_serious
    ),

    AngryCoach(
        id = "forgefox_09_angry_coach",
        displayName = "Angry Coach",
        category = "warning",
        drawableRes = R.drawable.forgefox_09_angry_coach
    ),

    Disappointed(
        id = "forgefox_10_disappointed",
        displayName = "Disappointed",
        category = "warning",
        drawableRes = R.drawable.forgefox_10_disappointed
    ),

    Sad(
        id = "forgefox_11_sad",
        displayName = "Sad",
        category = "emotion",
        drawableRes = R.drawable.forgefox_11_sad
    ),

    Exhausted(
        id = "forgefox_12_exhausted",
        displayName = "Exhausted",
        category = "recovery",
        drawableRes = R.drawable.forgefox_12_exhausted
    ),

    Sleepy(
        id = "forgefox_13_sleepy",
        displayName = "Sleepy",
        category = "recovery",
        drawableRes = R.drawable.forgefox_13_sleepy
    ),

    Shocked(
        id = "forgefox_14_shocked",
        displayName = "Shocked",
        category = "warning",
        drawableRes = R.drawable.forgefox_14_shocked
    ),

    Confused(
        id = "forgefox_15_confused",
        displayName = "Confused",
        category = "warning",
        drawableRes = R.drawable.forgefox_15_confused
    ),

    Proud(
        id = "forgefox_16_proud",
        displayName = "Proud",
        category = "reward",
        drawableRes = R.drawable.forgefox_16_proud
    ),

    Flexing(
        id = "forgefox_17_flexing",
        displayName = "Flexing",
        category = "fitness",
        drawableRes = R.drawable.forgefox_17_flexing
    ),

    FistPump(
        id = "forgefox_18_fist_pump",
        displayName = "Fist Pump",
        category = "reward",
        drawableRes = R.drawable.forgefox_18_fist_pump
    ),

    PointingForward(
        id = "forgefox_19_pointing_forward",
        displayName = "Pointing Forward",
        category = "widget",
        drawableRes = R.drawable.forgefox_19_pointing_forward
    ),

    Clipboard(
        id = "forgefox_20_clipboard",
        displayName = "Clipboard",
        category = "widget",
        drawableRes = R.drawable.forgefox_20_clipboard
    ),

    WaterBottle(
        id = "forgefox_21_water_bottle",
        displayName = "Water Bottle",
        category = "widget",
        drawableRes = R.drawable.forgefox_21_water_bottle
    ),

    Dumbbell(
        id = "forgefox_22_dumbbell",
        displayName = "Dumbbell",
        category = "fitness",
        drawableRes = R.drawable.forgefox_22_dumbbell
    ),

    PullUp(
        id = "forgefox_23_pull_up",
        displayName = "Pull Up",
        category = "fitness",
        drawableRes = R.drawable.forgefox_23_pull_up
    ),

    TiredTowel(
        id = "forgefox_24_tired_towel",
        displayName = "Tired Towel",
        category = "recovery",
        drawableRes = R.drawable.forgefox_24_tired_towel
    ),

    TrophyMedal(
        id = "forgefox_25_trophy_medal",
        displayName = "Trophy Medal",
        category = "reward",
        drawableRes = R.drawable.forgefox_25_trophy_medal
    ),

    StreakFire(
        id = "forgefox_26_streak_fire",
        displayName = "Streak Fire",
        category = "reward",
        drawableRes = R.drawable.forgefox_26_streak_fire
    ),

    RepairStreak(
        id = "forgefox_27_repair_streak",
        displayName = "Repair Streak",
        category = "recovery",
        drawableRes = R.drawable.forgefox_27_repair_streak
    ),

    RestBlanket(
        id = "forgefox_28_rest_blanket",
        displayName = "Rest Blanket",
        category = "recovery",
        drawableRes = R.drawable.forgefox_28_rest_blanket
    ),

    CheckingWatch(
        id = "forgefox_29_checking_watch",
        displayName = "Checking Watch",
        category = "warning",
        drawableRes = R.drawable.forgefox_29_checking_watch
    ),

    CoachArmsCrossed(
        id = "forgefox_30_coach_arms_crossed",
        displayName = "Coach Arms Crossed",
        category = "widget",
        drawableRes = R.drawable.forgefox_30_coach_arms_crossed
    ),

    SupportiveFailure(
        id = "forgefox_31_supportive_failure",
        displayName = "Supportive Failure",
        category = "recovery",
        drawableRes = R.drawable.forgefox_31_supportive_failure
    ),

    LevelUp(
        id = "forgefox_32_level_up",
        displayName = "Level Up",
        category = "reward",
        drawableRes = R.drawable.forgefox_32_level_up
    );

    companion object {
        fun fromId(id: String): ForgeFoxExpression =
            entries.firstOrNull { it.id == id } ?: Neutral
    }
}
