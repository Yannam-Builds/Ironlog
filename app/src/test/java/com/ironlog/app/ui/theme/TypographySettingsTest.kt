package com.ironlog.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class TypographySettingsTest {
    @Test fun `defaults preserve Lexend and original weights`() {
        assertEquals(TypographySelection("lexend", TypographyPreset.ORIGINAL), TypographySelection.fromStored(null, null))
        assertEquals(900, TypographySelection().weightFor(900))
        assertEquals(400, TypographySelection().weightFor(400))
    }

    @Test fun `invalid and mistyped storage falls back independently`() {
        assertEquals(TypographySelection(), TypographySelection.fromStored(42, true))
        assertEquals(TypographySelection("inter", TypographyPreset.ORIGINAL), TypographySelection.fromStored("inter", "missing"))
        assertEquals(TypographySelection("lexend", TypographyPreset.LIGHT), TypographySelection.fromStored("missing", "light"))
    }

    @Test fun `catalog contains exactly twenty additions and stable unique IDs`() {
        assertEquals(21, TypographyFonts.all.size)
        assertEquals(21, TypographyFonts.all.map { it.id }.toSet().size)
        assertEquals(listOf("lexend", "inter", "manrope", "dmsans", "plusjakartasans", "outfit", "sora", "urbanist", "nunito", "nunitosans", "rubik", "worksans", "publicsans", "figtree", "assistant", "mulish", "quicksand", "raleway", "montserrat", "exo2", "sourcesans3"), TypographyFonts.all.map { it.id })
    }

    @Test fun `presets map body label and heading weights`() {
        val light = TypographySelection(preset = TypographyPreset.LIGHT)
        val regular = TypographySelection(preset = TypographyPreset.REGULAR)
        val bold = TypographySelection(preset = TypographyPreset.BOLD)
        assertEquals(listOf(350, 450, 450), listOf(400, 500, 900).map(light::weightFor))
        assertEquals(listOf(400, 500, 600), listOf(400, 500, 900).map(regular::weightFor))
        assertEquals(listOf(500, 650, 750), listOf(400, 500, 900).map(bold::weightFor))
    }

    @Test fun `weights are clamped to bundled family axes`() {
        assertEquals(300, TypographySelection("quicksand").weightFor(100))
        assertEquals(700, TypographySelection("quicksand").weightFor(900))
        assertEquals(800, TypographySelection("assistant").weightFor(900))
        assertEquals(200, TypographySelection("manrope").weightFor(100))
        for (font in TypographyFonts.all) for (preset in TypographyPreset.entries) {
            for (weight in listOf(-1, 100, 350, 400, 500, 600, 900, 2000)) {
                assertTrue(TypographySelection(font.id, preset).weightFor(weight) in font.minWeight..font.maxWeight)
            }
        }
    }
}
