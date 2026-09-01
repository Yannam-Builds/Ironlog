package com.ironlog.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.sp
import com.ironlog.app.R

private val Lexend = FontFamily(Font(R.font.lexend_variable))

val ObsidianTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = IronLogType.display.fontSize.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = IronLogType.metric.fontSize.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = IronLogType.title.fontSize.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = IronLogType.section.fontSize.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Normal,
        fontSize = IronLogType.body.fontSize.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Normal,
        fontSize = IronLogType.meta.fontSize.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.SemiBold,
        fontSize = IronLogType.button.fontSize.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

fun buildIronLogTypography(selection: TypographySelection): Typography {
    fun mapped(style: TextStyle, presetWeight: Int): TextStyle {
        val weight = if (selection.preset == TypographyPreset.ORIGINAL) style.fontWeight?.weight ?: 400 else presetWeight
        return style.copy(
            fontFamily = selection.fontFamily(),
            fontWeight = FontWeight(weight.coerceIn(selection.font.minWeight, selection.font.maxWeight)),
            fontSynthesis = FontSynthesis.None,
        )
    }
    val base = ObsidianTypography
    val preset = selection.preset
    return Typography(
        displayLarge = mapped(base.displayLarge, preset.headings),
        displayMedium = mapped(base.displayMedium, preset.headings),
        displaySmall = mapped(base.displaySmall, preset.headings),
        headlineLarge = mapped(base.headlineLarge, preset.headings),
        headlineMedium = mapped(base.headlineMedium, preset.headings),
        headlineSmall = mapped(base.headlineSmall, preset.headings),
        titleLarge = mapped(base.titleLarge, preset.headings),
        titleMedium = mapped(base.titleMedium, preset.headings),
        titleSmall = mapped(base.titleSmall, preset.headings),
        bodyLarge = mapped(base.bodyLarge, preset.body),
        bodyMedium = mapped(base.bodyMedium, preset.body),
        bodySmall = mapped(base.bodySmall, preset.body),
        labelLarge = mapped(base.labelLarge, preset.labels),
        labelMedium = mapped(base.labelMedium, preset.labels),
        labelSmall = mapped(base.labelSmall, preset.labels),
    )
}
