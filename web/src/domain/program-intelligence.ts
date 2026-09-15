import type { Plan, ProgramRules, Workout } from "./types";

export type PolicySource = "exercise_override" | "plan_rules" | "global_setting" | "conservative_default";
export type ResolvedPolicy = { id: string; label: string; strategy: string; source: PolicySource; minimumEffortMargin: number; maximumLoadIncreaseRatio: number; percent1RM: number; rpeTarget: number; rirTarget: number };

const token = (value?: string) => (value ?? "").trim().toLowerCase().replace(/%/g, " ").replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
function modelPolicy(raw: string | undefined, source: PolicySource, rules?: ProgramRules): ResolvedPolicy | undefined {
  const percent1RM = Math.max(50, Math.min(95, rules?.percent1RM ?? 75));
  const rpeTarget = Math.max(1, Math.min(10, rules?.rpeTarget ?? 8));
  const rirTarget = Math.max(0, Math.min(10, rules?.rirTarget ?? 10 - rpeTarget));
  const common = { source, maximumLoadIncreaseRatio: .05, percent1RM, rpeTarget, rirTarget };
  switch (token(raw)) {
    case "double_progression": case "double": return { ...common, id: "double-progression-v1", label: "Double progression", strategy: "double_progression", minimumEffortMargin: 2 };
    case "linear": return { ...common, id: "linear-progression-v1", label: "Linear progression", strategy: "linear", minimumEffortMargin: 2 };
    case "percent_1rm": case "percent1rm": case "1rm_percentage": return { ...common, id: "percent-1rm-v1", label: `${percent1RM}% estimated 1RM`, strategy: "percent_1rm", minimumEffortMargin: 2 };
    case "rpe_rir": case "rpe": case "rir": return { ...common, id: "rpe-rir-progression-v1", label: "RPE/RIR progression", strategy: "rpe_rir", minimumEffortMargin: Math.max(rirTarget, 10 - rpeTarget) };
  }
}
export function resolveProgressionPolicy(exerciseOverride?: string, planRules?: ProgramRules, globalSetting?: string): ResolvedPolicy {
  const exercise = modelPolicy(exerciseOverride, "exercise_override", planRules); if (exercise) return exercise;
  const plan = modelPolicy(planRules?.progressionModel, "plan_rules", planRules); if (plan) return plan;
  const global = token(globalSetting);
  if (["conservative", "linear", "balanced", "aggressive", "undulating"].includes(global)) {
    const aggressive = ["aggressive", "undulating"].includes(global), balanced = global === "balanced";
    return { id: `${aggressive ? "aggressive" : balanced ? "balanced" : "conservative"}-double-progression-v1`, label: `${aggressive ? "Aggressive" : balanced ? "Balanced" : "Conservative"} double progression`, strategy: "double_progression", source: "global_setting", minimumEffortMargin: aggressive ? 1 : balanced ? 1.5 : 2, maximumLoadIncreaseRatio: aggressive ? .1 : balanced ? .075 : .05, percent1RM: 75, rpeTarget: 8, rirTarget: 2 };
  }
  return { id: "conservative-double-progression-v1", label: "Conservative double progression", strategy: "double_progression", source: "conservative_default", minimumEffortMargin: 2, maximumLoadIncreaseRatio: .05, percent1RM: 75, rpeTarget: 8, rirTarget: 2 };
}

const monday = (timestamp: number) => { const d = new Date(timestamp); d.setHours(0, 0, 0, 0); const day = d.getDay() || 7; d.setDate(d.getDate() - day + 1); return d.getTime(); };
export type ProgramInsights = { sessionsPerWeek: number; adherencePct: number; consistencyPct: number; weekCount: number; perDay: { id: string; name: string; count: number }[]; recommendation: string };
export function computeProgramInsights(plan: Plan | undefined, workouts: Workout[], weeklyGoalDays: number, now = Date.now()): ProgramInsights {
  const eligible = workouts.filter((workout) => workout.status === "completed" && workout.startedAt <= now && (!plan || workout.planId === plan.id));
  if (!eligible.length) return { sessionsPerWeek: 0, adherencePct: 0, consistencyPct: 0, weekCount: 0, perDay: plan?.days.map((day) => ({ id: day.id, name: day.name, count: 0 })) ?? [], recommendation: plan ? "Complete a planned session to establish adherence." : "Create or import a plan to receive progression guidance." };
  const current = monday(now), earliest = Math.min(...eligible.map((workout) => monday(workout.startedAt))), start = Math.max(earliest, current - 11 * 7 * 86400000);
  const weekCount = Math.floor((current - start) / (7 * 86400000)) + 1;
  const counts = new Map<number, number>();
  eligible.forEach((workout) => { const week = monday(workout.startedAt); if (week >= start && week <= current) counts.set(week, (counts.get(week) ?? 0) + 1); });
  const total = [...counts.values()].reduce((sum, value) => sum + value, 0), goal = Math.max(1, Math.min(7, weeklyGoalDays));
  const sessionsPerWeek = total / weekCount, adherencePct = Math.max(0, Math.min(100, Math.trunc(sessionsPerWeek / goal * 100)));
  let hit = 0; for (let week = start; week <= current; week += 7 * 86400000) if ((counts.get(week) ?? 0) >= goal) hit++;
  const consistencyPct = Math.max(0, Math.min(100, Math.round(hit / weekCount * 100)));
  const recommendation = adherencePct < 50 ? "Adherence is low. Reduce planned days or shorten sessions to build the habit first." : adherencePct < 85 ? "Stay steady. Focus on showing up consistently before adding volume." : consistencyPct >= 80 ? "Consistency is strong. Progress a key lift only when target reps and technique are repeatable; schedule easier work when fatigue is accumulating." : "Keep the current structure and review performance and recovery before changing volume.";
  return { sessionsPerWeek, adherencePct, consistencyPct, weekCount, perDay: plan?.days.map((day) => ({ id: day.id, name: day.name, count: eligible.filter((workout) => workout.dayId === day.id || workout.name.toLowerCase().includes(day.name.toLowerCase())).length })) ?? [], recommendation };
}
