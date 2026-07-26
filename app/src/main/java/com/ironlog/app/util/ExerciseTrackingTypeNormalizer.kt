package com.ironlog.app.util

import java.util.Locale

object ExerciseTrackingTypeNormalizer {
    private val validTrackingTypes = setOf("weight_reps", "duration", "duration_distance", "duration_weight")

    private val repBasedNameSignals = listOf(
        Regex("\\bab crunch\\b"),
        Regex("\\bcrunch(es)?\\b"),
        Regex("\\bsit[- ]?up(s)?\\b"),
        Regex("\\bleg pull[- ]?in\\b"),
        Regex("\\bmountain climber(s)?\\b"),
        Regex("\\bcurl(s)?\\b"),
        Regex("\\braise(s)?\\b"),
        Regex("\\bextension(s)?\\b"),
        Regex("\\bpress(es)?\\b"),
        Regex("\\brow\\b"),
        Regex("\\bpulldown(s)?\\b"),
        Regex("\\bpushdown(s)?\\b"),
        Regex("\\bfly(es)?\\b"),
        Regex("\\bsquat(s)?\\b"),
        Regex("\\bdeadlift(s)?\\b"),
        Regex("\\bhip thrust(s)?\\b"),
        Regex("\\babduction(s)?\\b"),
        Regex("\\badduction(s)?\\b"),
        Regex("\\bdip(s)?\\b"),
        Regex("\\bpull[- ]?up(s)?\\b"),
        Regex("\\bpush[- ]?up(s)?\\b"),
        Regex("\\blunge(s)?\\b"),
        Regex("\\bshrug(s)?\\b"),
        Regex("\\bcalf raise(s)?\\b"),
        Regex("\\bbox jump(s)?\\b"),
    )

    private val durationOnlyNameSignals = listOf(
        Regex("\\bplank(s)?\\b"),
        Regex("\\bhold(s)?\\b"),
        Regex("\\bstretch(es)?\\b"),
        Regex("\\bmobility\\b"),
        Regex("\\bisometric\\b"),
        Regex("\\bdead hang\\b"),
        Regex("\\bwall sit\\b"),
    )

    private val durationDistanceNameSignals = listOf(
        Regex("\\btreadmill\\b"),
        Regex("\\bwalk(ing)?\\b"),
        Regex("\\brun(ning)?\\b"),
        Regex("\\bsprint(s)?\\b"),
        Regex("\\bbike\\b"),
        Regex("\\bcycling\\b"),
        Regex("\\bcycle\\b"),
        Regex("\\brower\\b"),
        Regex("\\browing\\b"),
        Regex("\\berg\\b"),
        Regex("\\bski\\b"),
        Regex("\\bswim(ming)?\\b"),
        Regex("\\bstair\\b"),
        Regex("\\belliptical\\b"),
        Regex("\\bprowler\\b"),
        Regex("\\bsled\\b"),
        Regex("\\bcarry\\b"),
        Regex("\\bfarmer\\b"),
        Regex("\\bjump rope\\b"),
        Regex("\\bbattle rope\\b"),
    )

    fun normalize(
        name: String?,
        category: String?,
        equipment: String?,
        explicitTrackingType: String?,
    ): String {
        val normalizedName = name.norm()
        val normalizedCategory = category.norm()
        val normalizedEquipment = equipment.norm()
        val explicit = explicitTrackingType.norm().takeIf { it in validTrackingTypes }

        if (durationOnlyNameSignals.any { it.containsMatchIn(normalizedName) }) return "duration"
        if (repBasedNameSignals.any { it.containsMatchIn(normalizedName) }) return "weight_reps"
        if (durationDistanceNameSignals.any { it.containsMatchIn(normalizedName) }) return "duration_distance"

        return when {
            normalizedCategory.contains("cardio") || normalizedCategory.contains("conditioning") -> "duration_distance"
            normalizedCategory.contains("mobility") || normalizedCategory.contains("stretch") -> "duration"
            normalizedEquipment.contains("cardio") || normalizedEquipment.contains("conditioning") -> "duration_distance"
            explicit != null -> explicit
            else -> "weight_reps"
        }
    }

    private fun String?.norm(): String = orEmpty().trim().lowercase(Locale.ROOT)
}
