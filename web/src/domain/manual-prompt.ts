import type { Exercise, Profile } from "./types";

export type ManualPlanPromptOptions = {
  goal: string;
  daysPerWeek: number;
  sessionMinutes: number;
  equipment: string[];
  limitations: string;
  cardioEverySession: boolean;
};

export function exerciseCatalogMarkdown(exercises: Exercise[]) {
  const rows = exercises
    .slice()
    .sort((a, b) => a.name.localeCompare(b.name))
    .map((exercise) => [
      exercise.name,
      exercise.muscle,
      exercise.equipment,
      exercise.category ?? "strength",
      exercise.tracking,
      exercise.movementPattern ?? "",
    ].join(" | "));
  return [
    "# IronLog Exercise Catalog (Compact)",
    "Use these names when possible.",
    "Exercise Name | Primary Muscle | Equipment | Category | Tracking Type | Movement Pattern",
    ...rows,
  ].join("\n");
}

export function buildManualPlanPrompt(options: ManualPlanPromptOptions, exercises: Exercise[]) {
  const equipment = options.equipment.length ? options.equipment.join(", ") : "Bodyweight";
  const limitation = options.limitations.trim() || "None reported";
  const cardio = options.cardioEverySession
    ? "Include 10–15 minutes of low-intensity cardio when it fits. Do not mark it as a warmup."
    : "No dedicated cardio is required.";
  const example = JSON.stringify({ version: 1, type: "ironlog_plan", plan: { name: "Plan name", goal: options.goal, description: "Short rationale", days: [{ name: "Day 1", color: "#FF4500", exercises: [{ exerciseName: "Exact catalog name", sets: 3, reps: "8-12", restSeconds: 90, notes: "Technique note", supersetGroup: "", isWarmup: false }] }] } });
  return `You are a professional strength and conditioning coach. Create a ${options.daysPerWeek}-day-per-week training plan optimized for ${options.goal}. Each session should take about ${options.sessionMinutes} minutes.

AVAILABLE EQUIPMENT: ${equipment}
LIMITATIONS OR INJURIES: ${limitation}
CARDIO: ${cardio}

Return only one JSON object with no markdown fences or prose. Use this shape:
${example}

Keep sets from 1–100, rest from 0–3600 seconds, and include every requested day. Preserve pain limitations. Prefer exact names from the attached IronLog catalog; when no catalog exercise fits, use a clear conventional name so IronLog can flag it for review.

CATALOG PREVIEW:
${exerciseCatalogMarkdown(exercises).split("\n").slice(0, 42).join("\n")}`;
}

/** Compatibility entry point for the Training Intelligence manual workflow. */
export function manualPlanPrompt(profile: Profile, equipment: string, limitations: string) {
  return `Create this plan for a person aged ${profile.age}.\n` + buildManualPlanPrompt({
    goal: profile.goal,
    daysPerWeek: profile.weeklyGoal,
    sessionMinutes: profile.sessionMinutes,
    equipment: equipment.split(",").map((value) => value.trim()).filter(Boolean),
    limitations,
    cardioEverySession: false,
  }, []);
}
