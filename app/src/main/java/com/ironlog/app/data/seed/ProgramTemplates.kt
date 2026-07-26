package com.ironlog.app.data.seed

import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.PlanExerciseInput

data class ProgramTemplate(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val days: List<FullPlanDay>,
    val difficulty: String = "Intermediate",
    val durationWeeks: Int = 4,
)

val PROGRAM_TEMPLATES: List<ProgramTemplate> = listOf(
    ProgramTemplate(
        id = "aesthetic-split-default",
        name = "Aesthetic Split",
        category = "AESTHETIC",
        description = "4 days, cutting phase, focused on lats, side delts, abs, and legs",
        days = listOf(
            FullPlanDay("PUSH", "#FF4500", listOf(
                    PlanExerciseInput(name = "Incline Smith Press", sets = 4, reps = "8–10", restSeconds = 90, isWarmup = false, notes = "Full stretch at bottom, squeeze at top."),
                    PlanExerciseInput(name = "Cable Fly Low to High", sets = 3, reps = "12–15", restSeconds = 90, isWarmup = false, notes = "Cables at lowest point, pull upward in arc."),
                    PlanExerciseInput(name = "Cable Lateral Raise Single Arm", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = "3-second negative. Lead with elbow."),
                    PlanExerciseInput(name = "DB Lateral Raise", sets = 3, reps = "15–20", restSeconds = 90, isWarmup = false, notes = "Slight forward lean. Elbow leads."),
                    PlanExerciseInput(name = "Rope Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "Flare rope at bottom. Elbows pinned."),
                    PlanExerciseInput(name = "Single Arm Overhead Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "Long head stretch. Slow eccentric."),
                    PlanExerciseInput(name = "Weighted Cable Crunch", sets = 4, reps = "15–20", restSeconds = 90, isWarmup = false, notes = "Crunch into hips not knees.")
            )),
            FullPlanDay("PULL", "#0080FF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6–8", restSeconds = 90, isWarmup = false, notes = "Best lat builder. Add weight progressively."),
                    PlanExerciseInput(name = "Single Arm DB Row", sets = 4, reps = "10–12", restSeconds = 90, isWarmup = false, notes = "Incline bench 30 degrees. Pull elbow back."),
                    PlanExerciseInput(name = "Single Arm Cable Pulldown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "Lie chest-down on incline bench."),
                    PlanExerciseInput(name = "DB Shrugs", sets = 4, reps = "15–20", restSeconds = 90, isWarmup = false, notes = "1-second hold at top."),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "Brachialis = thicker arm silhouette."),
                    PlanExerciseInput(name = "Incline DB Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "Full long head stretch. Builds the peak."),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 4, reps = "12–15", restSeconds = 90, isWarmup = false, notes = "Add ankle weights when easy.")
            )),
            FullPlanDay("LEGS", "#00C170", listOf(
                    PlanExerciseInput(name = "Hip 90/90 Stretch", sets = 2, reps = "60s each side", restSeconds = 90, isWarmup = true, notes = "Non-negotiable warmup."),
                    PlanExerciseInput(name = "Worlds Greatest Stretch", sets = 2, reps = "5 reps each side", restSeconds = 90, isWarmup = true, notes = "T-spine plus hip flexor plus hamstring."),
                    PlanExerciseInput(name = "Bulgarian Split Squat", sets = 4, reps = "8–10 each leg", restSeconds = 90, isWarmup = false, notes = "Front shin vertical. Go deep."),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = "Hip hinge equals running power."),
                    PlanExerciseInput(name = "Leg Press High Feet", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "High foot placement shifts load."),
                    PlanExerciseInput(name = "Lateral Band Walk", sets = 3, reps = "15 steps each way", restSeconds = 90, isWarmup = false, notes = "Lateral stability for cutting."),
                    PlanExerciseInput(name = "Single Leg Calf Raise", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = "On a step for full ROM."),
                    PlanExerciseInput(name = "Ankle Mobility Drill", sets = 2, reps = "60s each side", restSeconds = 90, isWarmup = true, notes = "Cooldown. Do not skip.")
            )),
            FullPlanDay("UPPER", "#A020F0", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up or Lat Pulldown", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = "Second lat session. Full stretch."),
                    PlanExerciseInput(name = "Cable Lateral Raise Single Arm", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = "Second hit this week. 3s negative."),
                    PlanExerciseInput(name = "DB Shrugs", sets = 3, reps = "20", restSeconds = 90, isWarmup = false, notes = "Push weight up from Pull day."),
                    PlanExerciseInput(name = "Preacher Curl", sets = 3, reps = "10–12", restSeconds = 90, isWarmup = false, notes = "No cheating possible."),
                    PlanExerciseInput(name = "Rope Overhead Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "Long head. Makes arms look big."),
                    PlanExerciseInput(name = "Weighted Cable Crunch", sets = 4, reps = "15–20", restSeconds = 90, isWarmup = false, notes = "Heavier than Push day."),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = "Add ankle weights."),
                    PlanExerciseInput(name = "Ab Wheel Rollout", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "Hardest ab exercise.")
            ))
        ),
    ),
    ProgramTemplate(
        id = "full_body_beginner_3x",
        name = "Full Body Beginner (3x/week)",
        category = "BEGINNER",
        description = "Simple full-body progression.",
        days = listOf(
            FullPlanDay("FULL BODY A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("FULL BODY B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Deadlift", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 2, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("FULL BODY C", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Front Squat", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 2, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "minimalist_full_body_23",
        name = "Minimalist Full Body (2-3 Day)",
        category = "BEGINNER",
        description = "Time-efficient full-body essentials.",
        days = listOf(
            FullPlanDay("MINIMAL A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("MINIMAL B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("MINIMAL A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "upper_lower_beginner_4x",
        name = "Upper / Lower Beginner (4x/week)",
        category = "BEGINNER",
        description = "Balanced upper/lower split.",
        days = listOf(
            FullPlanDay("UPPER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "bodyweight_beginner",
        name = "Bodyweight Beginner",
        category = "BEGINNER",
        description = "No-gym bodyweight basics.",
        days = listOf(
            FullPlanDay("CALI PUSH", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dips", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Sit-Up", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CALI PULL", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Inverted Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Single-Leg Romanian Deadlift", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CALI MIXED", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Bodyweight Squat", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Split Squat", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Glute Bridge", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "upper_lower_strength_4x",
        name = "Upper / Lower Strength Focus (4x/week)",
        category = "STRENGTH",
        description = "Power + volume blend.",
        days = listOf(
            FullPlanDay("UPPER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "4", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Deadlift", sets = 2, reps = "3", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "novice_barbell_strength",
        name = "Novice Barbell Strength",
        category = "STRENGTH",
        description = "Linear barbell progression.",
        days = listOf(
            FullPlanDay("WORKOUT A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("WORKOUT B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Deadlift", sets = 1, reps = "5", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("WORKOUT C", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Front Squat", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Barbell Press", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "powerbuilding_4_day",
        name = "Powerbuilding 4-Day",
        category = "STRENGTH",
        description = "Strength first, size second.",
        days = listOf(
            FullPlanDay("UPPER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "4", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Deadlift", sets = 2, reps = "3", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PUSH B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Barbell Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Front Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "powerbuilding_5_day",
        name = "Powerbuilding 5-Day",
        category = "STRENGTH",
        description = "Higher-frequency powerbuilding.",
        days = listOf(
            FullPlanDay("UPPER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "4", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Deadlift", sets = 2, reps = "3", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PUSH A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PULL B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Back Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Front Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "pure_hypertrophy_5_day",
        name = "Pure Hypertrophy 5-Day",
        category = "HYPERTROPHY",
        description = "High-volume growth split.",
        days = listOf(
            FullPlanDay("PUSH A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PULL A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Deadlift", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "ppl_moderate_5x",
        name = "Push / Pull / Legs Moderate (5x/week)",
        category = "HYPERTROPHY",
        description = "Moderate-frequency PPL.",
        days = listOf(
            FullPlanDay("PUSH A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PULL A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Deadlift", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PUSH B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Barbell Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PULL B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Back Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "ppl_classic_6x",
        name = "Push / Pull / Legs Classic (6x/week)",
        category = "HYPERTROPHY",
        description = "Classic high-frequency PPL.",
        days = listOf(
            FullPlanDay("PUSH A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PULL A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Deadlift", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PUSH B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Barbell Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PULL B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Back Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Front Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "bro_split_5x",
        name = "Bro Split (5x/week)",
        category = "HYPERTROPHY",
        description = "Single-muscle focus days.",
        days = listOf(
            FullPlanDay("CHEST", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Skull Crusher", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("BACK", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("SHOULDERS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Overhead Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 5, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("ARMS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Curl", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "EZ-Bar Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "arnold_split_6x",
        name = "Arnold Split (6x/week)",
        category = "HYPERTROPHY",
        description = "Classic high-volume pairing split.",
        days = listOf(
            FullPlanDay("CHEST + BACK (HEAVY)", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("SHOULDERS + ARMS (HEAVY)", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Overhead Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Skull Crusher", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS (HEAVY)", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CHEST + BACK (VOLUME)", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("SHOULDERS + ARMS (VOLUME)", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS (VOLUME)", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "mens_aesthetic_v_taper",
        name = "Men's Aesthetic V-Taper Program",
        category = "AESTHETIC",
        description = "Shoulders and lats emphasis.",
        days = listOf(
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("SHOULDERS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Overhead Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 5, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("BACK", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("ARMS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Curl", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "EZ-Bar Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "lean_physique_recomp",
        name = "Lean Physique Recomp Program",
        category = "AESTHETIC",
        description = "Lifting + conditioning recomposition.",
        days = listOf(
            FullPlanDay("UPPER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "upper_body_bias_program",
        name = "Upper Body Bias Program",
        category = "AESTHETIC",
        description = "Upper-focused with lower maintenance.",
        days = listOf(
            FullPlanDay("UPPER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("SHOULDERS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Overhead Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 5, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "glute_legs_focus_45",
        name = "Glute & Legs Focus (4-5x/week)",
        category = "LOWER_BODY_GLUTES",
        description = "Lower-body dominant volume.",
        days = listOf(
            FullPlanDay("GLUTE + QUAD", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 2, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("GLUTE + HAM", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("GLUTE PUMP", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Hack Squat", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 2, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "womens_lower_body_focus",
        name = "Women's Lower Body Focus",
        category = "LOWER_BODY_GLUTES",
        description = "Lower-body emphasis with upper support.",
        days = listOf(
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Front Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "athletic_legs_program",
        name = "Athletic Legs Program",
        category = "LOWER_BODY_GLUTES",
        description = "Power + conditioning lower split.",
        days = listOf(
            FullPlanDay("LOWER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "4", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Deadlift", sets = 2, reps = "3", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LEGS A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "arm_specialization_block",
        name = "Arm Specialization Block",
        category = "SPECIALIZATION",
        description = "Extra direct arm volume.",
        days = listOf(
            FullPlanDay("ARMS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Curl", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "EZ-Bar Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PULL B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Back Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("ARMS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Curl", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "EZ-Bar Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "chest_specialization_block",
        name = "Chest Specialization Block",
        category = "SPECIALIZATION",
        description = "Chest-priority frequency block.",
        days = listOf(
            FullPlanDay("CHEST", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Skull Crusher", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CHEST", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Skull Crusher", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "back_width_thickness_block",
        name = "Back Width + Thickness Block",
        category = "SPECIALIZATION",
        description = "Dual-focus back growth block.",
        days = listOf(
            FullPlanDay("BACK", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PULL A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Deadlift", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("BACK", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("ARMS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Curl", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "EZ-Bar Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "shoulder_specialization_block",
        name = "Shoulder Specialization Block",
        category = "SPECIALIZATION",
        description = "High-frequency delt block.",
        days = listOf(
            FullPlanDay("SHOULDERS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Overhead Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 5, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Calf Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("SHOULDERS", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Overhead Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 5, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("PUSH B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Barbell Press", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Tricep Extension", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Cable Fly", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "home_dumbbell_only_program",
        name = "Home Dumbbell Only Program",
        category = "HOME_MINIMAL",
        description = "No-machine home split.",
        days = listOf(
            FullPlanDay("HOME UPPER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Dumbbell Bench Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "One-Arm Dumbbell Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("HOME LOWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Goblet Squat", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "60", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("HOME UPPER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Dumbbell Bench Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "One-Arm Dumbbell Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("HOME LOWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Goblet Squat", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "60", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "home_dumbbell_bench_program",
        name = "Home Dumbbell + Bench Program",
        category = "HOME_MINIMAL",
        description = "Home split with bench options.",
        days = listOf(
            FullPlanDay("HOME PUSH", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Dumbbell Bench Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dumbbell Shoulder Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("HOME LOWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Goblet Squat", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "60", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("HOME PULL", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "One-Arm Dumbbell Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "One-Arm Dumbbell Row", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Back Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("HOME LOWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Goblet Squat", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Romanian Deadlift", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "60", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "resistance_band_only_program",
        name = "Resistance Band Only Program",
        category = "HOME_MINIMAL",
        description = "Portable resistance-band routine.",
        days = listOf(
            FullPlanDay("BAND UPPER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Band Row", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Band Curl", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("BAND LOWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Band Squat", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Band Squat", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Glute Bridge", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Band Calf Raise", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("BAND UPPER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Band Row", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Band Curl", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("BAND LOWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Band Squat", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Band Squat", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Glute Bridge", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Band Calf Raise", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "calisthenics_strength_base",
        name = "Calisthenics Strength Base",
        category = "BODYWEIGHT_CALISTHENICS",
        description = "Bodyweight strength progression.",
        days = listOf(
            FullPlanDay("CALI PUSH", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Dips", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Sit-Up", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CALI PULL", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Inverted Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Single-Leg Romanian Deadlift", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CALI MIXED", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Bodyweight Squat", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Split Squat", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Glute Bridge", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 4, reps = "8", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CALI PULL", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Inverted Row", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Single-Leg Romanian Deadlift", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Push-Up", sets = 4, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "fat_loss_conditioning_lifting_hybrid",
        name = "Fat Loss Conditioning + Lifting Hybrid",
        category = "FAT_LOSS_CONDITIONING",
        description = "Alternating lift and conditioning days.",
        days = listOf(
            FullPlanDay("FULL BODY A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("FULL BODY B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Deadlift", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 2, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "interference_aware_recomp",
        name = "Interference-Aware Fat Loss / Recomp",
        category = "FAT_LOSS_CONDITIONING",
        description = "Recovery-aware lifting and conditioning split.",
        days = listOf(
            FullPlanDay("UPPER A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Pull-Up", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Hack Squat", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Extension", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 4, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "lift_3day_cardio_2day",
        name = "3-Day Lift + 2-Day Cardio Plan",
        category = "FAT_LOSS_CONDITIONING",
        description = "Simple split with cardio built in.",
        days = listOf(
            FullPlanDay("FULL BODY A", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 3, reps = "45", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("FULL BODY B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Deadlift", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "8", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 2, reps = "15", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("FULL BODY C", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Front Squat", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Seated Cable Row", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 2, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    ),
    ProgramTemplate(
        id = "athletic_conditioning_program",
        name = "Athletic Conditioning Program",
        category = "FAT_LOSS_CONDITIONING",
        description = "Athletic engine + strength emphasis.",
        days = listOf(
            FullPlanDay("UPPER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Bench Press", sets = 4, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Weighted Pull-Up", sets = 4, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Overhead Press", sets = 3, reps = "5", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Barbell Row", sets = 3, reps = "6", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("LOWER POWER", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Barbell Squat", sets = 4, reps = "4", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Deadlift", sets = 2, reps = "3", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Leg Curl", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Standing Calf Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Walking Lunge", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hip Thrust", sets = 3, reps = "10", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("UPPER B", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Incline Dumbbell Press", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lat Pulldown", sets = 4, reps = "10", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Lateral Raise", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Face Pull", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hammer Curl", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Tricep Pushdown", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            )),
            FullPlanDay("CONDITIONING", "#4C6FFF", listOf(
                    PlanExerciseInput(name = "Air Bike", sets = 6, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Reverse Crunch", sets = 3, reps = "15", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Plank", sets = 4, reps = "45", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Running", sets = 4, reps = "400", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Spider Crawl", sets = 4, reps = "20", restSeconds = 90, isWarmup = false, notes = ""),
                    PlanExerciseInput(name = "Hanging Leg Raise", sets = 3, reps = "12", restSeconds = 90, isWarmup = false, notes = "")
            ))
        ),
    )
)

fun ProgramTemplate.toPlanObject(): FullPlanObject = FullPlanObject(
    name = name,
    goal = category,
    description = description,
    days = days,
)
