import type { Profile } from "./types";
export function manualPlanPrompt(
  profile: Profile,
  equipment: string,
  limitations: string,
) {
  const schema = {
    version: 1,
    type: "ironlog_plan",
    plan: {
      name: "Program name",
      description: "Rationale and progression guidance",
      goal: profile.goal,
      days: [
        {
          name: "Day A",
          color: "#FF4500",
          exercises: [
            {
              exerciseName: "Barbell Bench Press",
              sets: 3,
              reps: "8–12",
              restSeconds: 120,
              notes: "Technique and progression cues",
              isWarmup: false,
              supersetGroup: "",
            },
          ],
        },
      ],
    },
  };
  return `Create a practical, age-appropriate training plan for a person aged ${profile.age} with ${profile.experience} training experience.
Goal: ${profile.goal}. ${profile.weeklyGoal} sessions/week, about ${profile.sessionMinutes} minutes/session. Coaching preference: ${profile.coaching}.
Available equipment (user input): ${equipment}.
Preferences and limitations (user input): ${limitations || "None specified; ask rather than assume injuries or medical status."}
If essential safety or equipment information is missing, ask clarifying questions first instead of generating a plan. Do not infer medical clearance. For minors, favor age-appropriate supervised practice and avoid maximal-testing prescriptions.
Prioritize sustainable progression, balanced movement patterns, realistic session duration including rest, and suitable alternatives. Do not diagnose or guarantee results. Separate warmups from working sets. Use kilograms if specifying loads. Never invent readiness measurements or personal records.
When enough information is available, return only valid JSON using this exact IronLog schema:
${JSON.stringify(schema)}
Use recognized exercise names when possible. Put technique, effort, substitution and progression cues in exercise notes; put program rationale in the plan description. No day-level notes. Superset partners use the same supersetGroup string; use an empty string otherwise. Review session duration, redundant movements, recovery demands and equipment compatibility before output.`;
}
