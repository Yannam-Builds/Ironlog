package com.ironlog.app.domain.intelligence

import com.ironlog.app.data.seed.ProgramTemplate
import kotlin.math.abs
import kotlin.math.roundToInt

data class ProgramRecommendationProfile(
    val goalMode: String = "hypertrophy",
    val weeklyDays: Int = 3,
    val trainingAgeMonths: Int = 0,
    val hasPastTraining: Boolean = false,
    val hasGymAccess: Boolean = true,
)

data class ProgramRecommendation(
    val template: ProgramTemplate,
    val score: Int,
    val reason: String,
    val experienceLabel: String,
    val sessionLengthLabel: String,
    val equipmentLabel: String,
)

/**
 * Transparent, offline ranking for built-in plans. Scores are intentionally
 * internal: the UI explains the recommendation instead of presenting a fake
 * precision percentage.
 */
object ProgramRecommendationEngine {

    fun rank(
        templates: List<ProgramTemplate>,
        profile: ProgramRecommendationProfile,
    ): List<ProgramRecommendation> = templates
        .mapIndexed { index, template ->
            val score = score(template, profile)
            ProgramRecommendation(
                template = template,
                score = score,
                reason = reason(template, profile),
                experienceLabel = experienceLabel(template),
                sessionLengthLabel = sessionLengthLabel(template),
                equipmentLabel = equipmentLabel(template),
            ) to index
        }
        .sortedWith(compareByDescending<Pair<ProgramRecommendation, Int>> { it.first.score }.thenBy { it.second })
        .map { it.first }

    private fun score(template: ProgramTemplate, profile: ProgramRecommendationProfile): Int {
        val category = template.category.uppercase()
        val nameAndId = "${template.id} ${template.name}".lowercase()
        val frequencyDifference = abs(template.days.size - profile.weeklyDays.coerceIn(1, 7))
        var score = 100 - (frequencyDifference * 24)

        if (frequencyDifference == 0) score += 30
        if (!profile.hasGymAccess) score += if (isHomeFriendly(template)) 90 else -160
        if (profile.hasGymAccess && !isHomeFriendly(template)) score += 8

        score += when (profile.goalMode.trim().lowercase()) {
            "strength" -> when {
                category == "STRENGTH" -> 48
                category == "BEGINNER" && profile.trainingAgeMonths < 12 -> 30
                nameAndId.contains("strength") -> 24
                else -> 0
            }
            "general_fitness", "general fitness", "performance", "endurance" -> when {
                category == "FAT_LOSS_CONDITIONING" -> 42
                category == "BODYWEIGHT_CALISTHENICS" -> 30
                category == "BEGINNER" || nameAndId.contains("full body") -> 26
                else -> 0
            }
            else -> when {
                category == "HYPERTROPHY" || category == "AESTHETIC" -> 48
                category == "LOWER_BODY_GLUTES" || category == "SPECIALIZATION" -> 28
                category == "BEGINNER" && profile.trainingAgeMonths < 12 -> 24
                else -> 0
            }
        }

        val isFoundation = category == "BEGINNER" || nameAndId.contains("novice") || nameAndId.contains("beginner")
        val isHighFrequency = template.days.size >= 5
        val isNewOrReturning = !profile.hasPastTraining || profile.trainingAgeMonths < 6
        score += when {
            isNewOrReturning && isFoundation -> 55
            isNewOrReturning && isHighFrequency -> -55
            profile.trainingAgeMonths >= 18 && isFoundation -> -12
            else -> 0
        }

        return score
    }

    private fun reason(template: ProgramTemplate, profile: ProgramRecommendationProfile): String {
        val requestedDays = profile.weeklyDays.coerceIn(1, 7)
        val frequency = if (template.days.size == requestedDays) {
            "Fits your $requestedDays-day schedule"
        } else {
            "${template.days.size}-day plan; your preference is $requestedDays-day training"
        }
        val focus = when (template.category.uppercase()) {
            "STRENGTH" -> "strength focus"
            "HYPERTROPHY", "AESTHETIC", "LOWER_BODY_GLUTES", "SPECIALIZATION" -> "muscle-building focus"
            "FAT_LOSS_CONDITIONING" -> "strength and conditioning focus"
            "BODYWEIGHT_CALISTHENICS" -> "bodyweight training focus"
            "BEGINNER" -> "foundation training focus"
            else -> "${template.name} routine"
        }
        val access = if (!profile.hasGymAccess) {
            if (isHomeFriendly(template)) "; home option if you have: ${equipmentLabel(template)}"
            else "; check access: ${equipmentLabel(template)}"
        } else ""
        return "$frequency · $focus$access."
    }

    /** Home-feasible is conditional on owning the listed portable equipment, not equipment-free. */
    fun isHomeFriendly(template: ProgramTemplate): Boolean = equipmentRequirements(template).let {
        Requirement.GYM !in it && Requirement.UNKNOWN !in it
    }

    private enum class Requirement(val label: String) {
        BODYWEIGHT("Bodyweight / floor space"), DUMBBELLS("Dumbbells"), BENCH("bench"),
        BANDS("resistance bands"), PULL_UP("pull-up bar"), SUPPORT("stable dip / row support"),
        GYM("Gym equipment"), UNKNOWN("Equipment not verified"),
    }

    private fun equipmentLabel(template: ProgramTemplate): String =
        equipmentRequirements(template).joinToString(", ") { it.label }

    // PlanExerciseInput has no equipment field. Infer only recognizable exercise requirements;
    // unknown names stay unverified. Titles/categories never establish equipment eligibility.
    private fun equipmentRequirements(template: ProgramTemplate): Set<Requirement> {
        val exercises = template.days.flatMap { it.exercises }
        if (exercises.isEmpty()) return setOf(Requirement.UNKNOWN)
        return exercises.flatMap { exercise ->
            val name = exercise.name.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
            val words = " $name "
            val requirements = linkedSetOf<Requirement>()
            when {
                listOf("barbell", "cable", "machine", "smith", "leg press", "hack squat", "air bike")
                    .any { words.contains(" $it ") } -> requirements += Requirement.GYM
                words.contains(" band ") || words.contains(" bands ") -> requirements += Requirement.BANDS
                words.contains(" dumbbell ") || words.contains(" dumbbells ") || words.contains(" db ") -> {
                    requirements += Requirement.DUMBBELLS
                    if (listOf("bench", "incline").any { words.contains(" $it ") }) requirements += Requirement.BENCH
                }
                name in setOf("hammer curl", "lateral raise", "goblet squat", "romanian deadlift") ->
                    requirements += Requirement.DUMBBELLS
                listOf("pull up", "pullup", "chin up", "chinup", "hanging leg raise", "hanging knee raise")
                    .any { words.contains(" $it ") } -> requirements += Requirement.PULL_UP
                name in setOf("dip", "dips", "inverted row") -> requirements += Requirement.SUPPORT
                name in setOf("push up", "pushup", "plank", "sit up", "situp", "reverse crunch", "crunch",
                    "bodyweight squat", "split squat", "glute bridge", "standing calf raise", "calf raise",
                    "walking lunge", "lunge", "single leg romanian deadlift") -> requirements += Requirement.BODYWEIGHT
                else -> requirements += Requirement.UNKNOWN
            }
            requirements
        }.toCollection(linkedSetOf())
    }

    fun experienceLabel(template: ProgramTemplate): String {
        val haystack = "${template.id} ${template.name} ${template.category}".lowercase()
        return when {
            haystack.contains("beginner") || haystack.contains("novice") -> "Foundation"
            template.days.size >= 5 || haystack.contains("specialization") -> "Experienced"
            else -> "Established"
        }
    }

    fun sessionLengthLabel(template: ProgramTemplate): String {
        if (template.days.isEmpty()) return "Flexible"
        val averageMinutes = template.days.map { day ->
            val workSeconds = day.exercises.sumOf { exercise ->
                val sets = (exercise.sets ?: 1).coerceAtLeast(1)
                sets * ((exercise.restSeconds ?: 90).coerceAtLeast(30) + 45)
            }
            8.0 + (workSeconds / 60.0)
        }.average()
        val rounded = ((averageMinutes / 5.0).roundToInt() * 5).coerceIn(30, 120)
        return "~$rounded min"
    }
}
