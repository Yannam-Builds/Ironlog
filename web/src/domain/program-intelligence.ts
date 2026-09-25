import type { Plan, ProgramRules, Workout } from "./types";
import { estimatedOneRm, validWorkingSet } from "./tracking";

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

export type VolumeLandmark = { sets: number; status: "low" | "optimal" | "high"; min: number; max: number; optimal: number };
export type TrainingIntelligence = {
  setsByMuscle: Record<string, number>;
  volumeLandmarks: Record<string, VolumeLandmark>;
  movementBalance: { Push: number; Pull: number; Legs: number };
  prLast30: number; prPrev30: number; prTrend: "accelerating" | "steady" | "slowing"; prVelocity30d: number;
  bestWindow: string; trainingAgeYears: number; trainingAgeLabel: string; trainingAgeTip: string;
  neuralFatigue: { isFlagged: boolean; consecutiveDays: number; lastHeavyExercises: string[] };
};
const groups = ["Chest", "Back", "Legs", "Shoulders", "Arms", "Core"] as const;
const baseBands: Record<string, [number, number, number]> = { Chest: [10, 20, 14], Back: [10, 22, 16], Legs: [12, 22, 16], Shoulders: [8, 16, 12], Arms: [8, 16, 12], Core: [6, 16, 10] };
function groupFor(text: string) {
  const value = text.toLowerCase();
  if (/chest|pec/.test(value)) return "Chest";
  if (/back|lat|row/.test(value)) return "Back";
  if (/shoulder|delt/.test(value)) return "Shoulders";
  if (/bicep|tricep|curl|forearm/.test(value)) return "Arms";
  if (/quad|hamstring|glute|calf|leg|squat|deadlift/.test(value)) return "Legs";
  if (/core|abs|plank|crunch|oblique/.test(value)) return "Core";
}
const dayKey = (timestamp: number) => { const d = new Date(timestamp); return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`; };
const ageDays = (timestamp: number, now: number) => Math.floor((new Date(now).setHours(0, 0, 0, 0) - new Date(timestamp).setHours(0, 0, 0, 0)) / 86400000);

export function buildTrainingIntelligence(workouts: Workout[], profile: { goalMode: string; weeklyGoalDays: number }, now = Date.now()): TrainingIntelligence {
  const history = workouts.filter((workout) => workout.status === "completed" && workout.startedAt <= now && workout.exercises.some((exercise) => exercise.loggedSets.some((set) => validWorkingSet(exercise, set))));
  const weekStart = monday(now), raw = Object.fromEntries(groups.map((group) => [group, 0])) as Record<string, number>;
  history.filter((workout) => workout.startedAt >= weekStart).forEach((workout) => workout.exercises.forEach((exercise) => {
    const count = exercise.loggedSets.filter((set) => validWorkingSet(exercise, set)).length;
    const group = groupFor(`${exercise.muscle} ${exercise.primaryMuscles?.join(" ") ?? ""} ${exercise.name} ${exercise.category ?? ""}`);
    if (group) raw[group] += count;
  }));
  const goalScale = profile.goalMode.trim().toLowerCase() === "strength" ? .78 : ["general_fitness", "general fitness", "performance", "endurance"].includes(profile.goalMode.trim().toLowerCase()) ? .68 : 1;
  const scale = goalScale * (Math.max(1, Math.min(7, profile.weeklyGoalDays)) <= 2 ? .82 : 1);
  const volumeLandmarks = Object.fromEntries(groups.map((group) => {
    const [baseMin, baseMax, baseOptimal] = baseBands[group], min = Math.max(4, Math.round(baseMin * scale)), max = Math.max(min + 4, Math.round(baseMax * scale)), optimal = Math.max(min, Math.min(max, Math.round(baseOptimal * scale))), sets = raw[group];
    return [group, { sets, status: sets < min ? "low" : sets > max ? "high" : "optimal", min, max, optimal }];
  })) as Record<string, VolumeLandmark>;
  const push = raw.Chest + raw.Shoulders, pull = raw.Back, legs = raw.Legs, total = Math.max(1, push + pull + legs);
  const movementBalance = { Push: Math.trunc(push * 100 / total), Pull: Math.trunc(pull * 100 / total), Legs: Math.trunc(legs * 100 / total) };
  let prLast30 = 0, prPrev30 = 0; const best = new Map<string, number>();
  history.slice().sort((a, b) => a.startedAt - b.startedAt).forEach((workout) => {
    let sessionPr = false;
    workout.exercises.forEach((exercise) => {
      const values = exercise.loggedSets.map((set) => estimatedOneRm(exercise, set)).filter((value): value is number => value !== undefined);
      if (!values.length) return; const current = Math.max(...values), key = exercise.exerciseId || exercise.name, previous = best.get(key);
      if (previous !== undefined && current > previous) sessionPr = true; best.set(key, Math.max(previous ?? 0, current));
    });
    if (sessionPr) { const age = ageDays(workout.startedAt, now); if (age >= 0 && age <= 30) prLast30++; else if (age <= 60) prPrev30++; }
  });
  const recent = history.filter((workout) => ageDays(workout.startedAt, now) <= 30), prTrend = prLast30 > prPrev30 ? "accelerating" : prLast30 < prPrev30 ? "slowing" : "steady";
  const windows: Record<string, number[]> = { Morning: [], Afternoon: [], Evening: [], Night: [] };
  recent.forEach((workout) => { const hour = new Date(workout.startedAt).getHours(), bucket = hour >= 5 && hour <= 11 ? "Morning" : hour <= 16 ? "Afternoon" : hour <= 20 ? "Evening" : "Night"; const volume = workout.exercises.flatMap((exercise) => exercise.loggedSets.map((set) => validWorkingSet(exercise, set) ? set.weightKg * set.reps : 0)).reduce((sum, value) => sum + value, 0); if (volume > 0) windows[bucket].push(volume); });
  const eligibleWindows = Object.entries(windows).filter(([, values]) => values.length >= 3), leading = eligibleWindows.sort((a, b) => b[1].reduce((s, v) => s + v, 0) / b[1].length - a[1].reduce((s, v) => s + v, 0) / a[1].length)[0];
  const bestWindow = leading ? `${leading[0]} leads your logged session volume (${leading[1].length} sessions).` : "Log at least 3 sessions in one time window to compare performance.";
  const oldest = history.length ? Math.min(...history.map((workout) => workout.startedAt)) : now, months = history.length ? Math.max(0, (new Date(now).getFullYear() - new Date(oldest).getFullYear()) * 12 + new Date(now).getMonth() - new Date(oldest).getMonth()) : 0;
  const trainingAgeLabel = history.length < 12 || months < 3 ? "Building baseline" : history.length < 50 || months < 12 ? "Developing" : history.length < 150 || months < 36 ? "Established" : "Highly experienced";
  const trainingAgeTip = trainingAgeLabel === "Building baseline" ? "Repeat key movements and progress only when technique and target reps are stable." : trainingAgeLabel === "Developing" ? "Use small, repeatable increases while keeping recovery sustainable." : "Review your response history before changing the program.";
  const heavy = new Map<string, { at: number; names: string[] }>();
  history.filter((workout) => ageDays(workout.startedAt, now) <= 14).forEach((workout) => { const names = workout.exercises.filter((exercise) => /squat|deadlift|bench press|overhead press|ohp|barbell row|power clean|front squat|sumo|romanian/.test(exercise.name.toLowerCase()) && exercise.loggedSets.some((set) => estimatedOneRm(exercise, set) !== undefined && ((set.rpe ?? 0) >= 8 || (set.rir ?? 99) <= 2 || (set.reps >= 1 && set.reps <= 6)))).map((exercise) => exercise.name); if (names.length) heavy.set(dayKey(workout.startedAt), { at: workout.startedAt, names }); });
  const heavyDays = [...heavy.values()].sort((a, b) => b.at - a.at); let consecutiveDays = 0, previous: number | undefined; const lastHeavyExercises: string[] = [];
  if (heavyDays[0] && ageDays(heavyDays[0].at, now) <= 2) for (const day of heavyDays) { if (previous !== undefined && ageDays(day.at, previous) !== 1) break; consecutiveDays++; day.names.forEach((name) => { if (lastHeavyExercises.length < 3 && !lastHeavyExercises.includes(name)) lastHeavyExercises.push(name); }); previous = day.at; }
  return { setsByMuscle: raw, volumeLandmarks, movementBalance, prLast30, prPrev30, prTrend, prVelocity30d: recent.length ? Math.min(1, prLast30 / recent.length) : 0, bestWindow, trainingAgeYears: months / 12, trainingAgeLabel, trainingAgeTip, neuralFatigue: { isFlagged: consecutiveDays >= 3, consecutiveDays, lastHeavyExercises } };
}
