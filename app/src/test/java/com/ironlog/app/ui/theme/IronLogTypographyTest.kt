package com.ironlog.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import org.junit.Assert.*
import org.junit.Test

class IronLogTypographyTest {
    @Test fun `all fifteen styles use the selected family and real weight synthesis`() {
        val selection = TypographySelection("inter", TypographyPreset.LIGHT)
        val typography = buildIronLogTypography(selection)
        val styles = listOf(typography.displayLarge, typography.displayMedium, typography.displaySmall,
            typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
            typography.titleLarge, typography.titleMedium, typography.titleSmall,
            typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
            typography.labelLarge, typography.labelMedium, typography.labelSmall)
        assertEquals(15, styles.size)
        styles.forEach { assertEquals(selection.fontFamily(), it.fontFamily); assertEquals(FontSynthesis.None, it.fontSynthesis) }
        assertEquals(350, typography.bodyMedium.fontWeight?.weight)
        assertEquals(450, typography.labelLarge.fontWeight?.weight)
        assertEquals(450, typography.headlineLarge.fontWeight?.weight)
    }

    @Test fun `regular and bold label roles do not inherit heading weights`() {
        val regular = buildIronLogTypography(TypographySelection(preset = TypographyPreset.REGULAR))
        val bold = buildIronLogTypography(TypographySelection(preset = TypographyPreset.BOLD))
        assertEquals(500, regular.labelLarge.fontWeight?.weight)
        assertEquals(600, regular.titleLarge.fontWeight?.weight)
        assertEquals(650, bold.labelMedium.fontWeight?.weight)
        assertEquals(750, bold.headlineMedium.fontWeight?.weight)
    }

    @Test fun `original preserves existing sizes spacing and weights`() {
        val mapped = buildIronLogTypography(TypographySelection())
        fun sameMetrics(a: TextStyle, b: TextStyle) {
            assertEquals(a.fontWeight, b.fontWeight)
            assertEquals(a.fontSize, b.fontSize)
            assertEquals(a.lineHeight, b.lineHeight)
            assertEquals(a.letterSpacing, b.letterSpacing)
        }
        sameMetrics(ObsidianTypography.displayLarge, mapped.displayLarge)
        sameMetrics(ObsidianTypography.bodyMedium, mapped.bodyMedium)
        sameMetrics(ObsidianTypography.labelMedium, mapped.labelMedium)
    }

    @Test fun `shared metadata tokens remain readable across screens`() {
        assertTrue(IronLogType.eyebrow.fontSize >= 12)
        assertTrue(IronLogType.micro.fontSize >= 12)
        assertTrue(IronLogType.eyebrow.lineHeight >= IronLogType.eyebrow.fontSize)
        assertTrue(IronLogType.micro.lineHeight >= IronLogType.micro.fontSize)
    }
}
