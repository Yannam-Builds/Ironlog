package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.model.HistoryExercise

// ── Fine-grained muscle list (26 muscles) ────────────────────────────────────

val FINE_MUSCLES = listOf(
    "upperChest", "midChest", "lowerChest",
    "lats", "upperBack", "traps", "spinalErectors",
    "frontDelts", "sideDelts", "rearDelts",
    "bicepsLong", "bicepsShort", "brachialis",
    "tricepsLong", "tricepsLateral", "tricepsMedial", "forearms",
    "upperAbs", "lowerAbs", "obliques",
    "quads", "hamstrings", "glutes", "calves", "adductors", "abductors",
)

/** Human-readable display name for each fine muscle key. */
val FINE_MUSCLE_DISPLAY = mapOf(
    "upperChest"    to "Upper Chest",
    "midChest"      to "Mid Chest",
    "lowerChest"    to "Lower Chest",
    "lats"          to "Lats",
    "upperBack"     to "Upper Back",
    "traps"         to "Traps",
    "spinalErectors" to "Spinal Erectors",
    "frontDelts"    to "Front Delts",
    "sideDelts"     to "Side Delts",
    "rearDelts"     to "Rear Delts",
    "bicepsLong"    to "Biceps Long",
    "bicepsShort"   to "Biceps Short",
    "brachialis"    to "Brachialis",
    "tricepsLong"   to "Triceps Long",
    "tricepsLateral" to "Triceps Lateral",
    "tricepsMedial" to "Triceps Medial",
    "forearms"      to "Forearms",
    "upperAbs"      to "Upper Abs",
    "lowerAbs"      to "Lower Abs",
    "obliques"      to "Obliques",
    "quads"         to "Quads",
    "hamstrings"    to "Hamstrings",
    "glutes"        to "Glutes",
    "calves"        to "Calves",
    "adductors"     to "Adductors",
    "abductors"     to "Abductors",
)

/** Short abbreviation for bar chart labels (≤7 chars). */
val FINE_MUSCLE_ABBREV = mapOf(
    "upperChest"    to "U.Chest",
    "midChest"      to "M.Chest",
    "lowerChest"    to "L.Chest",
    "lats"          to "Lats",
    "upperBack"     to "U.Back",
    "traps"         to "Traps",
    "spinalErectors" to "Spine",
    "frontDelts"    to "F.Delts",
    "sideDelts"     to "S.Delts",
    "rearDelts"     to "R.Delt",
    "bicepsLong"    to "Bi.Long",
    "bicepsShort"   to "Bi.Shrt",
    "brachialis"    to "Brach.",
    "tricepsLong"   to "Tri.Lng",
    "tricepsLateral" to "Tri.Lat",
    "tricepsMedial" to "Tri.Med",
    "forearms"      to "Forearm",
    "upperAbs"      to "U.Abs",
    "lowerAbs"      to "L.Abs",
    "obliques"      to "Obliq.",
    "quads"         to "Quads",
    "hamstrings"    to "Hams",
    "glutes"        to "Glutes",
    "calves"        to "Calves",
    "adductors"     to "Adduct.",
    "abductors"     to "Abduct.",
)

// ── Movement-family templates (fractional contribution per fine muscle) ────────

private val FAMILY_TEMPLATES = mapOf(
    "incline_press"      to mapOf("upperChest" to 0.36, "midChest" to 0.18, "frontDelts" to 0.20, "tricepsLong" to 0.08, "tricepsLateral" to 0.10, "tricepsMedial" to 0.08),
    "decline_press"      to mapOf("lowerChest" to 0.38, "midChest" to 0.20, "tricepsLong" to 0.08, "tricepsLateral" to 0.16, "tricepsMedial" to 0.10, "frontDelts" to 0.08),
    "horizontal_press"   to mapOf("midChest" to 0.38, "upperChest" to 0.16, "frontDelts" to 0.16, "tricepsLong" to 0.08, "tricepsLateral" to 0.14, "tricepsMedial" to 0.08),
    "chest_fly"          to mapOf("midChest" to 0.46, "upperChest" to 0.22, "lowerChest" to 0.14, "frontDelts" to 0.10, "bicepsLong" to 0.08),
    "vertical_press"     to mapOf("frontDelts" to 0.34, "sideDelts" to 0.22, "tricepsLong" to 0.16, "tricepsLateral" to 0.14, "tricepsMedial" to 0.08, "upperChest" to 0.06),
    "vertical_pull"      to mapOf("lats" to 0.42, "upperBack" to 0.16, "bicepsLong" to 0.12, "bicepsShort" to 0.12, "brachialis" to 0.08, "forearms" to 0.10),
    "horizontal_pull"    to mapOf("upperBack" to 0.30, "lats" to 0.26, "rearDelts" to 0.12, "traps" to 0.10, "bicepsLong" to 0.08, "bicepsShort" to 0.08, "brachialis" to 0.06),
    "shrug"              to mapOf("traps" to 0.56, "upperBack" to 0.22, "forearms" to 0.08, "spinalErectors" to 0.14),
    "rear_delt"          to mapOf("rearDelts" to 0.46, "upperBack" to 0.22, "traps" to 0.12, "sideDelts" to 0.12, "bicepsShort" to 0.08),
    "lateral_raise"      to mapOf("sideDelts" to 0.58, "frontDelts" to 0.14, "rearDelts" to 0.12, "traps" to 0.16),
    "front_raise"        to mapOf("frontDelts" to 0.62, "upperChest" to 0.16, "sideDelts" to 0.12, "traps" to 0.10),
    "biceps_curl"        to mapOf("bicepsLong" to 0.24, "bicepsShort" to 0.28, "brachialis" to 0.22, "forearms" to 0.26),
    "hammer_curl"        to mapOf("brachialis" to 0.32, "bicepsLong" to 0.20, "bicepsShort" to 0.18, "forearms" to 0.30),
    "triceps_pushdown"   to mapOf("tricepsLateral" to 0.38, "tricepsMedial" to 0.32, "tricepsLong" to 0.18, "forearms" to 0.12),
    "triceps_overhead"   to mapOf("tricepsLong" to 0.42, "tricepsLateral" to 0.26, "tricepsMedial" to 0.20, "frontDelts" to 0.12),
    "squat"              to mapOf("quads" to 0.36, "glutes" to 0.20, "adductors" to 0.12, "spinalErectors" to 0.10, "hamstrings" to 0.12, "calves" to 0.10),
    "lunge"              to mapOf("quads" to 0.28, "glutes" to 0.24, "hamstrings" to 0.14, "adductors" to 0.14, "calves" to 0.08, "abductors" to 0.12),
    "hinge"              to mapOf("hamstrings" to 0.28, "glutes" to 0.26, "spinalErectors" to 0.20, "adductors" to 0.10, "upperBack" to 0.08, "forearms" to 0.08),
    "leg_extension"      to mapOf("quads" to 0.78, "adductors" to 0.12, "calves" to 0.10),
    "leg_curl"           to mapOf("hamstrings" to 0.66, "glutes" to 0.12, "calves" to 0.08, "adductors" to 0.14),
    "calf_raise"         to mapOf("calves" to 0.84, "hamstrings" to 0.08, "quads" to 0.08),
    "core_crunch"        to mapOf("upperAbs" to 0.44, "lowerAbs" to 0.24, "obliques" to 0.32),
    "leg_raise"          to mapOf("lowerAbs" to 0.40, "upperAbs" to 0.24, "obliques" to 0.36),
    "plank"              to mapOf("upperAbs" to 0.26, "lowerAbs" to 0.22, "obliques" to 0.18, "spinalErectors" to 0.16, "glutes" to 0.10, "frontDelts" to 0.08),
)

// ── Library group → fine muscle expansion ────────────────────────────────────

private val LIBRARY_GROUP_MAP = mapOf(
    "chest"              to mapOf("midChest" to 0.50, "upperChest" to 0.30, "lowerChest" to 0.20),
    "shoulders"          to mapOf("frontDelts" to 0.34, "sideDelts" to 0.34, "rearDelts" to 0.32),
    "back"               to mapOf("upperBack" to 0.42, "lats" to 0.34, "traps" to 0.14, "spinalErectors" to 0.10),
    "middle back"        to mapOf("upperBack" to 0.60, "traps" to 0.25, "spinalErectors" to 0.15),
    "lower back"         to mapOf("spinalErectors" to 0.72, "upperBack" to 0.12, "glutes" to 0.16),
    "lats"               to mapOf("lats" to 0.76, "upperBack" to 0.16, "bicepsShort" to 0.08),
    "lat"                to mapOf("lats" to 0.76, "upperBack" to 0.16, "bicepsShort" to 0.08),
    "traps"              to mapOf("traps" to 0.74, "upperBack" to 0.18, "rearDelts" to 0.08),
    "trap"               to mapOf("traps" to 0.74, "upperBack" to 0.18, "rearDelts" to 0.08),
    "quadriceps"         to mapOf("quads" to 0.78, "adductors" to 0.12, "calves" to 0.10),
    "quads"              to mapOf("quads" to 0.78, "adductors" to 0.12, "calves" to 0.10),
    "hamstrings"         to mapOf("hamstrings" to 0.68, "glutes" to 0.18, "adductors" to 0.14),
    "glutes"             to mapOf("glutes" to 0.62, "hamstrings" to 0.18, "abductors" to 0.20),
    "calves"             to mapOf("calves" to 0.90, "hamstrings" to 0.10),
    "adductors"          to mapOf("adductors" to 0.78, "quads" to 0.12, "hamstrings" to 0.10),
    "abductors"          to mapOf("abductors" to 0.72, "glutes" to 0.28),
    "front delts"        to mapOf("frontDelts" to 0.76, "sideDelts" to 0.14, "upperChest" to 0.10),
    "side delts"         to mapOf("sideDelts" to 0.78, "frontDelts" to 0.12, "rearDelts" to 0.10),
    "rear delts"         to mapOf("rearDelts" to 0.74, "upperBack" to 0.18, "traps" to 0.08),
    "rotator cuff"       to mapOf("rearDelts" to 0.28, "sideDelts" to 0.16, "frontDelts" to 0.16, "upperBack" to 0.12, "traps" to 0.10, "forearms" to 0.18),
    "biceps"             to mapOf("bicepsLong" to 0.28, "bicepsShort" to 0.32, "brachialis" to 0.20, "forearms" to 0.20),
    "biceps long head"   to mapOf("bicepsLong" to 0.60, "bicepsShort" to 0.16, "brachialis" to 0.10, "forearms" to 0.14),
    "biceps short head"  to mapOf("bicepsShort" to 0.60, "bicepsLong" to 0.16, "brachialis" to 0.10, "forearms" to 0.14),
    "brachialis"         to mapOf("brachialis" to 0.56, "bicepsLong" to 0.18, "bicepsShort" to 0.14, "forearms" to 0.12),
    "triceps"            to mapOf("tricepsLong" to 0.34, "tricepsLateral" to 0.34, "tricepsMedial" to 0.24, "forearms" to 0.08),
    "triceps long head"  to mapOf("tricepsLong" to 0.62, "tricepsLateral" to 0.20, "tricepsMedial" to 0.10, "frontDelts" to 0.08),
    "triceps lateral head" to mapOf("tricepsLateral" to 0.62, "tricepsLong" to 0.20, "tricepsMedial" to 0.10, "forearms" to 0.08),
    "triceps medial head" to mapOf("tricepsMedial" to 0.58, "tricepsLateral" to 0.20, "tricepsLong" to 0.14, "forearms" to 0.08),
    "forearms"           to mapOf("forearms" to 0.82, "brachialis" to 0.18),
    "abdominals"         to mapOf("upperAbs" to 0.42, "lowerAbs" to 0.32, "obliques" to 0.26),
    "upper abs"          to mapOf("upperAbs" to 0.64, "lowerAbs" to 0.18, "obliques" to 0.18),
    "lower abs"          to mapOf("lowerAbs" to 0.62, "upperAbs" to 0.18, "obliques" to 0.20),
    "obliques"           to mapOf("obliques" to 0.74, "upperAbs" to 0.14, "lowerAbs" to 0.12),
    "core"               to mapOf("upperAbs" to 0.34, "lowerAbs" to 0.28, "obliques" to 0.20, "spinalErectors" to 0.18),
)

// ── Specific exercise overrides ───────────────────────────────────────────────

private val ANCHOR_OVERRIDES = mapOf(
    "barbellbenchpress"         to mapOf("upperChest" to 0.18, "midChest" to 0.42, "frontDelts" to 0.14, "tricepsLong" to 0.08, "tricepsLateral" to 0.10, "tricepsMedial" to 0.08),
    "inclinebarbellbenchpress"  to mapOf("upperChest" to 0.36, "midChest" to 0.16, "frontDelts" to 0.18, "tricepsLong" to 0.08, "tricepsLateral" to 0.12, "tricepsMedial" to 0.10),
    "dumbbellbenchpress"        to mapOf("upperChest" to 0.20, "midChest" to 0.38, "frontDelts" to 0.14, "tricepsLong" to 0.08, "tricepsLateral" to 0.10, "tricepsMedial" to 0.10),
    "barbelloverheadpress"      to mapOf("frontDelts" to 0.34, "sideDelts" to 0.22, "tricepsLong" to 0.16, "tricepsLateral" to 0.14, "tricepsMedial" to 0.08, "upperChest" to 0.06),
    "barbellbentoverrow"        to mapOf("upperBack" to 0.30, "lats" to 0.28, "rearDelts" to 0.10, "traps" to 0.12, "bicepsShort" to 0.08, "brachialis" to 0.06, "spinalErectors" to 0.06),
    "barbellfullsquat"          to mapOf("quads" to 0.38, "glutes" to 0.20, "adductors" to 0.12, "hamstrings" to 0.10, "spinalErectors" to 0.10, "calves" to 0.10),
    "barbelldeadlift"           to mapOf("hamstrings" to 0.24, "glutes" to 0.22, "spinalErectors" to 0.22, "upperBack" to 0.12, "traps" to 0.10, "forearms" to 0.10),
    "romaniandeadlift"          to mapOf("hamstrings" to 0.30, "glutes" to 0.26, "spinalErectors" to 0.20, "adductors" to 0.10, "forearms" to 0.06, "upperBack" to 0.08),
    "latpulldown"               to mapOf("lats" to 0.46, "upperBack" to 0.16, "bicepsLong" to 0.12, "bicepsShort" to 0.10, "brachialis" to 0.08, "forearms" to 0.08),
    "pullup"                    to mapOf("lats" to 0.42, "upperBack" to 0.18, "bicepsLong" to 0.12, "bicepsShort" to 0.12, "brachialis" to 0.08, "forearms" to 0.08),
    "legpress"                  to mapOf("quads" to 0.42, "glutes" to 0.20, "adductors" to 0.14, "hamstrings" to 0.12, "calves" to 0.12),
    "legextension"              to mapOf("quads" to 0.82, "adductors" to 0.10, "calves" to 0.08),
    "legcurlmachine"            to mapOf("hamstrings" to 0.72, "glutes" to 0.10, "calves" to 0.08, "adductors" to 0.10),
    "standingcalfraise"         to mapOf("calves" to 0.88, "hamstrings" to 0.06, "quads" to 0.06),
    "seatedcalfraise"           to mapOf("calves" to 0.90, "hamstrings" to 0.10),
)

// ── Movement-family detection rules ──────────────────────────────────────────

private data class FamilyRule(val id: String, val score: Int, val patterns: List<Regex>)

private val FAMILY_RULES = listOf(
    FamilyRule("incline_press",    10, listOf("incline.*press", "incline.*bench", "incline.*push").map(::Regex)),
    FamilyRule("decline_press",    10, listOf("decline.*press", "decline.*bench", "dip").map(::Regex)),
    FamilyRule("chest_fly",         9, listOf("fly", "crossover", "pec deck").map(::Regex)),
    FamilyRule("horizontal_press",  8, listOf("\\bbench\\b", "\\bpress\\b", "push.?up", "pushup").map(::Regex)),
    FamilyRule("vertical_press",   10, listOf("shoulder press", "overhead press", "\\bohp\\b", "military press", "arnold press").map(::Regex)),
    FamilyRule("vertical_pull",     9, listOf("pull.?up", "chin.?up", "pulldown", "lat pull").map(::Regex)),
    FamilyRule("horizontal_pull",   9, listOf("\\brow\\b", "meadows", "seal row", "pullover").map(::Regex)),
    FamilyRule("shrug",             8, listOf("shrug").map(::Regex)),
    FamilyRule("rear_delt",         8, listOf("rear delt", "reverse pec", "reverse fly", "face pull").map(::Regex)),
    FamilyRule("lateral_raise",     8, listOf("lateral raise", "side raise").map(::Regex)),
    FamilyRule("front_raise",       7, listOf("front raise").map(::Regex)),
    FamilyRule("hammer_curl",       9, listOf("hammer curl", "rope hammer").map(::Regex)),
    FamilyRule("biceps_curl",       8, listOf("curl", "preacher", "concentration", "zottman").map(::Regex)),
    FamilyRule("triceps_overhead",  9, listOf("overhead extension", "skull crusher", "skullcrusher", "lying extension").map(::Regex)),
    FamilyRule("triceps_pushdown",  8, listOf("pushdown", "kickback").map(::Regex)),
    FamilyRule("hinge",            10, listOf("deadlift", "\\brdl\\b", "romanian", "good morning", "hip thrust", "glute bridge").map(::Regex)),
    FamilyRule("lunge",             9, listOf("split squat", "lunge", "step.?up", "walking lunge").map(::Regex)),
    FamilyRule("squat",            10, listOf("squat", "hack squat", "leg press", "belt squat").map(::Regex)),
    FamilyRule("leg_extension",    10, listOf("leg extension").map(::Regex)),
    FamilyRule("leg_curl",         10, listOf("leg curl", "nordic", "glute ham").map(::Regex)),
    FamilyRule("calf_raise",       10, listOf("calf").map(::Regex)),
    FamilyRule("core_crunch",      10, listOf("crunch", "sit.?up", "cable crunch").map(::Regex)),
    FamilyRule("leg_raise",        10, listOf("leg raise", "toes to bar", "v.?up", "hanging knee").map(::Regex)),
    FamilyRule("plank",             9, listOf("plank", "ab wheel", "hollow", "carry").map(::Regex)),
)

// ── Cross-system grouping maps ────────────────────────────────────────────────

/**
 * Maps each fine muscle to a Push / Pull / Legs / Core training-split bucket.
 * Used by TrainingIntelligenceEngine for volume landmarks and movement balance.
 */
val FINE_MUSCLE_TO_PPL: Map<String, String> = mapOf(
    // Push
    "upperChest"     to "Push",  "midChest"       to "Push",  "lowerChest"     to "Push",
    "frontDelts"     to "Push",  "sideDelts"      to "Push",
    "tricepsLong"    to "Push",  "tricepsLateral" to "Push",  "tricepsMedial"  to "Push",
    // Pull
    "lats"           to "Pull",  "upperBack"      to "Pull",  "traps"          to "Pull",
    "spinalErectors" to "Pull",  "rearDelts"      to "Pull",
    "bicepsLong"     to "Pull",  "bicepsShort"    to "Pull",  "brachialis"     to "Pull",
    "forearms"       to "Pull",
    // Legs
    "quads"          to "Legs",  "hamstrings"     to "Legs",  "glutes"         to "Legs",
    "calves"         to "Legs",  "adductors"      to "Legs",  "abductors"      to "Legs",
    // Core
    "upperAbs"       to "Core",  "lowerAbs"       to "Core",  "obliques"       to "Core",
)

/**
 * Maps each fine muscle to its recovery region (Push / Pull / Legs / Arms / Shoulders / Core).
 * Used by RecoveryReadinessEngine for the body-map heatmap.
 */
val FINE_MUSCLE_TO_REGION: Map<String, String> = mapOf(
    // Chest → Push recovery
    "upperChest"     to "Push",       "midChest"       to "Push",      "lowerChest"     to "Push",
    // Back → Pull recovery
    "lats"           to "Pull",       "upperBack"      to "Pull",      "traps"          to "Pull",
    "spinalErectors" to "Pull",
    // Delts → Shoulders recovery
    "frontDelts"     to "Shoulders",  "sideDelts"      to "Shoulders", "rearDelts"      to "Shoulders",
    // Elbow flexors / extensors → Arms recovery
    "bicepsLong"     to "Arms",       "bicepsShort"    to "Arms",      "brachialis"     to "Arms",
    "tricepsLong"    to "Arms",       "tricepsLateral" to "Arms",      "tricepsMedial"  to "Arms",
    "forearms"       to "Arms",
    // Midsection → Core recovery
    "upperAbs"       to "Core",       "lowerAbs"       to "Core",      "obliques"       to "Core",
    // Lower body → Legs recovery
    "quads"          to "Legs",       "hamstrings"     to "Legs",      "glutes"         to "Legs",
    "calves"         to "Legs",       "adductors"      to "Legs",      "abductors"      to "Legs",
)

/**
 * Maps each fine muscle to a radar-chart bucket (Chest / Back / Arms / Shoulders / Legs / Core).
 * Used by VolumeAnalyticsScreen to fold granular data into the 6-axis radar.
 */
val FINE_MUSCLE_TO_RADAR: Map<String, String> = mapOf(
    "upperChest"     to "Chest",      "midChest"       to "Chest",     "lowerChest"     to "Chest",
    "lats"           to "Back",       "upperBack"      to "Back",      "traps"          to "Back",
    "spinalErectors" to "Back",
    "frontDelts"     to "Shoulders",  "sideDelts"      to "Shoulders", "rearDelts"      to "Shoulders",
    "bicepsLong"     to "Arms",       "bicepsShort"    to "Arms",      "brachialis"     to "Arms",
    "tricepsLong"    to "Arms",       "tricepsLateral" to "Arms",      "tricepsMedial"  to "Arms",
    "forearms"       to "Arms",
    "upperAbs"       to "Core",       "lowerAbs"       to "Core",      "obliques"       to "Core",
    "quads"          to "Legs",       "hamstrings"     to "Legs",      "glutes"         to "Legs",
    "calves"         to "Legs",       "adductors"      to "Legs",      "abductors"      to "Legs",
)

/**
 * Folds a fine-muscle contribution map (muscle → fraction, summing ≈1.0) into coarse region
 * buckets using [regionMap], weighting fractions by the number of working sets.
 */
fun foldContributions(contributions: Map<String, Double>, regionMap: Map<String, String>): Map<String, Double> {
    val out = mutableMapOf<String, Double>()
    contributions.forEach { (muscle, frac) ->
        val bucket = regionMap[muscle] ?: return@forEach
        out[bucket] = (out[bucket] ?: 0.0) + frac
    }
    return out
}

/**
 * Folds the output of [computeGranularVolume] into coarse radar buckets (Chest/Back/…)
 * using [FINE_MUSCLE_TO_RADAR], returning Int set counts (rounded).
 * The [axes] list defines which buckets must always appear in the output.
 */
fun foldGranularToRadar(granular: Map<String, Float>, axes: List<String>): Map<String, Int> {
    val out = axes.associateWith { 0f }.toMutableMap()
    granular.forEach { (muscle, sets) ->
        val bucket = FINE_MUSCLE_TO_RADAR[muscle] ?: return@forEach
        if (bucket in out) out[bucket] = (out[bucket] ?: 0f) + sets
    }
    return out.mapValues { it.value.toInt() }
}

// ── Core engine functions ─────────────────────────────────────────────────────

private fun normalizeKey(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "")

internal fun detectFamily(name: String): String {
    val text = name.lowercase()
    var best = "" to 0
    FAMILY_RULES.forEach { rule ->
        if (rule.score > best.second && rule.patterns.any { it.containsMatchIn(text) }) {
            best = rule.id to rule.score
        }
    }
    // Muscle-name fallback if no pattern matched
    if (best.second == 0) {
        return when {
            "chest" in text -> "horizontal_press"
            "lat" in text || "back" in text -> "horizontal_pull"
            "squat" in text || "quad" in text -> "squat"
            "deadlift" in text || "hinge" in text || "glute" in text -> "hinge"
            "shoulder" in text || "delt" in text -> "vertical_press"
            "curl" in text || "bicep" in text -> "biceps_curl"
            "tricep" in text || "extension" in text -> "triceps_pushdown"
            "calf" in text -> "calf_raise"
            "ab" in text || "core" in text -> "core_crunch"
            else -> ""
        }
    }
    return best.first
}

private fun normalize(raw: Map<String, Double>): Map<String, Double> {
    val cleaned = raw.filter { (k, _) -> k in FINE_MUSCLES }
    val total = cleaned.values.sum().takeIf { it > 0 } ?: return emptyMap()
    return cleaned.mapValues { it.value / total }
}

private fun mergeWeighted(a: Map<String, Double>, wA: Double, b: Map<String, Double>, wB: Double): Map<String, Double> {
    val merged = linkedMapOf<String, Double>()
    a.forEach { (k, v) -> merged[k] = (merged[k] ?: 0.0) + v * wA }
    b.forEach { (k, v) -> merged[k] = (merged[k] ?: 0.0) + v * wB }
    return normalize(merged)
}

private fun libraryContribution(exercise: HistoryExercise): Map<String, Double> {
    val hints = (exercise.primaryMuscles + listOfNotNull(exercise.primaryMuscle))
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
    val merged = linkedMapOf<String, Double>()
    hints.forEach { hint ->
        LIBRARY_GROUP_MAP[hint]?.forEach { (k, v) -> merged[k] = (merged[k] ?: 0.0) + v }
    }
    return normalize(merged)
}

/** Resolve fractional contribution map for a single exercise (sums to ≈1.0). */
fun resolveContribution(exercise: HistoryExercise): Map<String, Double> {
    // 1. Anchor override — exercise-specific ground truth
    val anchorKey = normalizeKey(exercise.name)
    ANCHOR_OVERRIDES[anchorKey]?.let { return normalize(it) }

    // 2. Family template from movement pattern detection
    val family = detectFamily(exercise.name)
    val template = FAMILY_TEMPLATES[family]?.let(::normalize) ?: emptyMap()

    // 3. Library expansion from primaryMuscles metadata
    val library = libraryContribution(exercise)

    return when {
        template.isNotEmpty() && library.isNotEmpty() -> mergeWeighted(template, 0.72, library, 0.28)
        template.isNotEmpty() -> template
        library.isNotEmpty() -> library
        else -> emptyMap()
    }
}

/**
 * Aggregate fractional effective sets per fine muscle across a workout history.
 * Each working set contributes [contribution_fraction] effective sets to each fine muscle.
 * Returns map sorted descending, keys are camelCase FINE_MUSCLES keys.
 */
fun computeGranularVolume(history: List<HistoryEntry>): Map<String, Float> {
    val totals = linkedMapOf<String, Double>()
    history.forEach { workout ->
        workout.exercises.forEach { ex ->
            val workingSets = ex.sets.count { it.type != "warmup" }
            if (workingSets > 0) {
                val contrib = resolveContribution(ex)
                if (contrib.isNotEmpty()) {
                    contrib.forEach { (muscle, fraction) ->
                        totals[muscle] = (totals[muscle] ?: 0.0) + workingSets * fraction
                    }
                } else {
                    // Fallback: credit the broad primaryMuscle group
                    val broad = (ex.primaryMuscle ?: ex.primaryMuscles.firstOrNull()).orEmpty().lowercase()
                    val fallback = LIBRARY_GROUP_MAP[broad]
                    if (fallback != null) {
                        val norm = normalize(fallback)
                        norm.forEach { (muscle, fraction) ->
                            totals[muscle] = (totals[muscle] ?: 0.0) + workingSets * fraction
                        }
                    }
                }
            }
        }
    }
    return totals
        .filter { it.value >= 0.05 }
        .entries
        .sortedByDescending { it.value }
        .associate { it.key to it.value.toFloat() }
}
