package com.ironlog.app.util

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants

object HapticsEngine {
    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        repeat(12) {
            when (current) {
                is Activity -> return current
                is ContextWrapper -> current = current.baseContext
                else -> return null
            }
        }
        return null
    }

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @Suppress("DEPRECATION")
    private fun pulse(
        context: Context,
        predefinedEffect: Int,
        fallbackDurationMs: Long,
        fallbackAmplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE,
        viewFeedbackConstant: Int,
    ) {
        // First path: UI haptic feedback (works better on some OEM builds).
        runCatching {
            val activity = findActivity(context)
            val view = activity?.window?.decorView
            if (view != null) {
                view.performHapticFeedback(viewFeedbackConstant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            }
        }

        val vib = vibrator(context) ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Second path: platform predefined vibration.
            val ok = runCatching {
                vib.vibrate(VibrationEffect.createPredefined(predefinedEffect))
            }.isSuccess
            if (!ok) {
                runCatching {
                    vib.vibrate(VibrationEffect.createOneShot(fallbackDurationMs, fallbackAmplitude))
                }
            }
            return
        }

        runCatching { vib.vibrate(fallbackDurationMs) }
    }

    fun selection(context: Context) = pulse(
        context = context,
        predefinedEffect = VibrationEffect.EFFECT_TICK,
        fallbackDurationMs = 12L,
        viewFeedbackConstant = HapticFeedbackConstants.CLOCK_TICK,
    )

    fun lightConfirm(context: Context) = pulse(
        context = context,
        predefinedEffect = VibrationEffect.EFFECT_CLICK,
        fallbackDurationMs = 18L,
        viewFeedbackConstant = HapticFeedbackConstants.KEYBOARD_TAP,
    )

    fun success(context: Context) = pulse(
        context = context,
        predefinedEffect = VibrationEffect.EFFECT_HEAVY_CLICK,
        fallbackDurationMs = 28L,
        fallbackAmplitude = 180,
        viewFeedbackConstant = HapticFeedbackConstants.CONFIRM,
    )

    fun error(context: Context) = pulse(
        context = context,
        predefinedEffect = VibrationEffect.EFFECT_DOUBLE_CLICK,
        fallbackDurationMs = 36L,
        fallbackAmplitude = 220,
        viewFeedbackConstant = HapticFeedbackConstants.REJECT,
    )

    fun low(context: Context) = pulse(
        context = context,
        predefinedEffect = VibrationEffect.EFFECT_TICK,
        fallbackDurationMs = 10L,
        fallbackAmplitude = 90,
        viewFeedbackConstant = HapticFeedbackConstants.CLOCK_TICK,
    )

    fun medium(context: Context) = pulse(
        context = context,
        predefinedEffect = VibrationEffect.EFFECT_CLICK,
        fallbackDurationMs = 18L,
        fallbackAmplitude = 140,
        viewFeedbackConstant = HapticFeedbackConstants.KEYBOARD_TAP,
    )

    fun mediumStrong(context: Context) = pulse(
        context = context,
        predefinedEffect = VibrationEffect.EFFECT_HEAVY_CLICK,
        fallbackDurationMs = 26L,
        fallbackAmplitude = 180,
        viewFeedbackConstant = HapticFeedbackConstants.CONFIRM,
    )

    fun strong(context: Context) = pulse(
        context = context,
        predefinedEffect = VibrationEffect.EFFECT_DOUBLE_CLICK,
        fallbackDurationMs = 42L,
        fallbackAmplitude = 255,
        viewFeedbackConstant = HapticFeedbackConstants.REJECT,
    )

    fun toggle(context: Context, enabled: Boolean) = pulse(
        context = context,
        predefinedEffect = if (enabled) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK,
        fallbackDurationMs = if (enabled) 16L else 10L,
        fallbackAmplitude = if (enabled) 130 else 100,
        viewFeedbackConstant = if (enabled) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF,
    )
}
