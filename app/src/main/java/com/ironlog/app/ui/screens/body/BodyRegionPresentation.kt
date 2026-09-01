package com.ironlog.app.ui.screens.body

internal data class BodyRegionPresentation(val key: String, val label: String, val group: String)
internal val BODY_REGIONS = listOf(
    BodyRegionPresentation("chest", "Chest", "Push"),
    BodyRegionPresentation("back", "Back", "Pull"),
    BodyRegionPresentation("shoulders", "Shoulders", "Shoulders"),
    BodyRegionPresentation("rearDelts", "Rear delts", "Shoulders"),
    BodyRegionPresentation("arms", "Arms", "Arms"),
    BodyRegionPresentation("core", "Core", "Core"),
    BodyRegionPresentation("quads", "Quads", "Legs"),
    BodyRegionPresentation("hamstrings", "Hamstrings", "Legs"),
    BodyRegionPresentation("calves", "Calves", "Legs"),
)
internal fun bodyPainRegions(flags: Set<String>): Set<String> = BODY_REGIONS
    .filter { it.group in flags || it.key in flags }.map { it.key }.toSet()
