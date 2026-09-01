package com.ironlog.app.ui.theme

data class TypographyFont(val id: String, val name: String, val minWeight: Int, val maxWeight: Int)

object TypographyFonts {
    // Bounds are the wght axes of the bundled upright variable fonts, not synthetic weights.
    val all = listOf(
        TypographyFont("lexend", "Lexend", 100, 900),
        TypographyFont("inter", "Inter", 100, 900),
        TypographyFont("manrope", "Manrope", 200, 800),
        TypographyFont("dmsans", "DM Sans", 100, 1000),
        TypographyFont("plusjakartasans", "Plus Jakarta Sans", 200, 800),
        TypographyFont("outfit", "Outfit", 100, 900),
        TypographyFont("sora", "Sora", 100, 800),
        TypographyFont("urbanist", "Urbanist", 100, 900),
        TypographyFont("nunito", "Nunito", 200, 1000),
        TypographyFont("nunitosans", "Nunito Sans", 200, 1000),
        TypographyFont("rubik", "Rubik", 300, 900),
        TypographyFont("worksans", "Work Sans", 100, 900),
        TypographyFont("publicsans", "Public Sans", 100, 900),
        TypographyFont("figtree", "Figtree", 300, 900),
        TypographyFont("assistant", "Assistant", 200, 800),
        TypographyFont("mulish", "Mulish", 200, 1000),
        TypographyFont("quicksand", "Quicksand", 300, 700),
        TypographyFont("raleway", "Raleway", 100, 900),
        TypographyFont("montserrat", "Montserrat", 100, 900),
        TypographyFont("exo2", "Exo 2", 100, 900),
        TypographyFont("sourcesans3", "Source Sans 3", 200, 900),
    )

    fun resolve(id: Any?): TypographyFont = all.firstOrNull { it.id == id } ?: all.first()
}

enum class TypographyPreset(val id: String, val label: String, val body: Int, val labels: Int, val headings: Int) {
    ORIGINAL("original", "Original", 400, 500, 700),
    LIGHT("light", "Light", 350, 450, 450),
    REGULAR("regular", "Regular", 400, 500, 600),
    BOLD("bold", "Bold", 500, 650, 750),
}

data class TypographySelection(val fontId: String = "lexend", val preset: TypographyPreset = TypographyPreset.ORIGINAL) {
    val font: TypographyFont get() = TypographyFonts.resolve(fontId)

    fun weightFor(originalWeight: Int): Int {
        val requested = when {
            preset == TypographyPreset.ORIGINAL -> originalWeight
            originalWeight <= 400 -> preset.body
            originalWeight < 600 -> preset.labels
            else -> preset.headings
        }
        return requested.coerceIn(font.minWeight, font.maxWeight)
    }

    companion object {
        fun fromStored(fontId: Any?, presetId: Any?): TypographySelection = TypographySelection(
            TypographyFonts.resolve(fontId).id,
            TypographyPreset.entries.firstOrNull { it.id == presetId } ?: TypographyPreset.ORIGINAL,
        )
    }
}
