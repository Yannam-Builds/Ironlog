import type { Plan, Workout } from "./types";
import { fineMuscleContribution } from "./engine";
import { validWorkingSet } from "./tracking";
import { isoWeekKey } from "./dates";
export type AnalyticsRange = "WEEK" | "7D" | "30D" | "PROGRAM";
export const radarAxes = ["Core", "Arms", "Chest", "Legs", "Back", "Shoulders"];
const fineToRadar: Record<string, string> = Object.fromEntries([
  ["Chest", ["upperChest", "midChest", "lowerChest"]], ["Back", ["lats", "upperBack", "traps", "spinalErectors"]],
  ["Shoulders", ["frontDelts", "sideDelts", "rearDelts"]], ["Arms", ["bicepsLong", "bicepsShort", "brachialis", "tricepsLong", "tricepsLateral", "tricepsMedial", "forearms"]],
  ["Core", ["upperAbs", "lowerAbs", "obliques"]], ["Legs", ["quads", "hamstrings", "glutes", "calves", "adductors", "abductors"]],
].flatMap(([group, names]) => (names as string[]).map(name => [name, group as string])));
export function analyticsWindows(workouts: Workout[], range: AnalyticsRange, activePlan?: Plan, now = Date.now()) {
  const start = new Date(now); start.setHours(0, 0, 0, 0);
  const days = range === "7D" ? 7 : range === "30D" ? 30 : Math.max(28, (activePlan?.days.length ?? 0) >= 3 ? activePlan!.days.length * 6 : 42);
  if (range === "WEEK") start.setDate(start.getDate() - (start.getDay() || 7) + 1);
  else start.setDate(start.getDate() - days);
  const cutoff = start.getTime();
  const prior = new Date(cutoff); prior.setDate(prior.getDate() - (range === "WEEK" ? 7 : days));
  const priorClock = new Date(now); priorClock.setDate(priorClock.getDate() - 7);
  const priorEnd = range === "WEEK" ? priorClock.getTime() : cutoff - 1;
  const completed = workouts.filter(w => w.status === "completed" && w.startedAt <= now);
  return { cutoff, current: completed.filter(w => w.startedAt >= cutoff), previous: completed.filter(w => w.startedAt >= prior.getTime() && w.startedAt <= priorEnd), previousCutoff: prior.getTime(), previousEnd: priorEnd };
}
export function granularVolume(workouts: Workout[]) {
  const fine: Record<string, number> = {}, radar: Record<string, number> = Object.fromEntries(radarAxes.map(axis => [axis, 0]));
  for (const workout of workouts) for (const exercise of workout.exercises) {
    const sets = exercise.loggedSets.filter(set => validWorkingSet(exercise, set)).length;
    for (const [muscle, fraction] of Object.entries(fineMuscleContribution(exercise))) fine[muscle] = (fine[muscle] ?? 0) + sets * fraction;
  }
  for (const [muscle, count] of Object.entries(fine)) { const group = fineToRadar[muscle]; if (group) radar[group] += count; }
  return { fine, radar };
}
export function weeklyConsistency(workouts: Workout[], cutoff: number, now = Date.now()) {
  const weeks = new Map<string, number>(); const monday = new Date(cutoff); monday.setHours(0, 0, 0, 0); monday.setDate(monday.getDate() - (monday.getDay() || 7) + 1);
  for (let date = monday; date.getTime() <= now; date.setDate(date.getDate() + 7)) weeks.set(isoWeekKey(date.getTime()), 0);
  workouts.forEach(w => { const week = isoWeekKey(w.startedAt); if (weeks.has(week)) weeks.set(week, weeks.get(week)! + 1); });
  return [...weeks].map(([week, sessions]) => ({ week, sessions }));
}
