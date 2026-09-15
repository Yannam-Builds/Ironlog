import type { SessionExercise, Workout } from "./types";
import { estimatedOneRm, externalLoadVolume, isTimed, trackingMode, validWorkingSet } from "./tracking";

export type ExerciseTrendRow = {
  workoutId: string;
  date: number;
  name: string;
  mode: string;
  estimatedOneRmKg?: number;
  loadKg: number;
  meanReps: number;
  volumeKg: number;
  durationSeconds?: number;
  distanceKm: number;
  workingSets: number;
  loadAvailable: boolean;
  volumeAvailable: boolean;
  isPr: boolean;
};

const normalized = (value: string) => value.trim().toLocaleLowerCase();
const exerciseMatches = (exercise: SessionExercise, identity: string) => exercise.exerciseId === identity || normalized(exercise.name) === normalized(identity);

export function buildExerciseTrend(workouts: Workout[], identity: string): ExerciseTrendRow[] {
  const rows = workouts.filter((workout) => workout.status === "completed").flatMap((workout) => workout.exercises.filter((exercise) => exerciseMatches(exercise, identity)).flatMap((exercise) => {
    const sets = exercise.loggedSets.filter((set) => validWorkingSet(exercise, set));
    if (!sets.length) return [];
    const mode = trackingMode(exercise), timed = isTimed(exercise);
    const estimates = sets.map((set) => estimatedOneRm(exercise, set)).filter((value): value is number => value !== undefined);
    const loadAvailable = ["weight_reps", "bodyweight_plus_weight_reps", "assisted_bodyweight", "duration_weight", ""].includes(mode);
    const volumeAvailable = ["weight_reps", "bodyweight_plus_weight_reps", ""].includes(mode);
    return [{
      workoutId: workout.id, date: workout.startedAt, name: exercise.name, mode,
      estimatedOneRmKg: estimates.length ? Math.max(...estimates) : undefined,
      loadKg: loadAvailable ? Math.max(...sets.map((set) => set.weightKg)) : 0,
      meanReps: timed ? 0 : sets.reduce((sum, set) => sum + set.reps, 0) / sets.length,
      volumeKg: volumeAvailable ? sets.reduce((sum, set) => sum + externalLoadVolume(exercise, set), 0) : 0,
      durationSeconds: timed ? sets.reduce((sum, set) => sum + set.durationSeconds, 0) : undefined,
      distanceKm: sets.reduce((sum, set) => sum + set.distanceKm, 0),
      workingSets: sets.length, loadAvailable, volumeAvailable, isPr: false,
    } satisfies ExerciseTrendRow];
  })).sort((a, b) => a.date - b.date);
  let best = -Infinity;
  return rows.map((row) => {
    const value = row.estimatedOneRmKg;
    const isPr = value !== undefined && value > best;
    if (value !== undefined) best = Math.max(best, value);
    return { ...row, isPr };
  });
}

export function exerciseProgressTabs(rows: ExerciseTrendRow[]) {
  const tabs: string[] = [];
  if (rows.some((row) => row.estimatedOneRmKg !== undefined)) tabs.push("E1RM");
  if (rows.some((row) => row.loadAvailable)) tabs.push("LOAD");
  if (rows.some((row) => row.durationSeconds === undefined)) tabs.push("REPS");
  if (rows.some((row) => row.durationSeconds !== undefined)) tabs.push("DURATION");
  if (rows.some((row) => row.volumeAvailable)) tabs.push("VOLUME");
  tabs.push("CONSISTENCY", "HISTORY");
  return tabs;
}

export function filterExerciseTrend(rows: ExerciseTrendRow[], days?: number, now = Date.now()) {
  const cutoff = days === undefined ? -Infinity : now - days * 86400000;
  return rows.filter((row) => row.date <= now && row.date >= cutoff);
}

const monday = (timestamp: number) => { const date = new Date(timestamp); date.setHours(0, 0, 0, 0); date.setDate(date.getDate() - (date.getDay() || 7) + 1); return date.getTime(); };
const muscleGroup = (exercise: SessionExercise) => {
  const value = `${exercise.muscle} ${exercise.primaryMuscles?.join(" ") ?? ""} ${exercise.name}`.toLowerCase();
  if (/chest|pec/.test(value)) return "Chest";
  if (/back|lat|row/.test(value)) return "Back";
  if (/shoulder|delt/.test(value)) return "Shoulders";
  if (/bicep|tricep|curl|forearm/.test(value)) return "Arms";
  if (/quad|hamstring|glute|calf|leg|squat|deadlift/.test(value)) return "Legs";
  if (/core|ab|plank|oblique/.test(value)) return "Core";
  return exercise.muscle || "Other";
};
const movement = (group: string) => group === "Chest" || group === "Shoulders" ? "Push" : group === "Back" || group === "Arms" ? "Pull" : group === "Legs" ? "Legs" : undefined;

export type VolumeAnalytics = {
  sessions: number; workingSets: number; totalVolumeKg: number; previousVolumeKg: number;
  deltaPct?: number; trend: "Progressing" | "Regressing" | "Stable" | "No prior data";
  weeklyVolume: { week: number; volumeKg: number }[]; muscleSets: Record<string, number>;
  movementBalance: { Push: number; Pull: number; Legs: number };
};

export function buildVolumeAnalytics(workouts: Workout[], rangeDays = 90, now = Date.now()): VolumeAnalytics {
  const cutoff = now - rangeDays * 86400000, previousCutoff = now - rangeDays * 2 * 86400000;
  const completed = workouts.filter((workout) => workout.status === "completed" && workout.startedAt <= now);
  const current = completed.filter((workout) => workout.startedAt >= cutoff), previous = completed.filter((workout) => workout.startedAt >= previousCutoff && workout.startedAt < cutoff);
  const volume = (sessions: Workout[]) => sessions.reduce((total, workout) => total + workout.exercises.reduce((exerciseTotal, exercise) => exerciseTotal + exercise.loggedSets.reduce((setTotal, set) => setTotal + externalLoadVolume(exercise, set), 0), 0), 0);
  const totalVolumeKg = volume(current), previousVolumeKg = volume(previous);
  const muscleSets: Record<string, number> = {}, movementSets = { Push: 0, Pull: 0, Legs: 0 }, byWeek = new Map<number, number>();
  let workingSets = 0;
  current.forEach((workout) => workout.exercises.forEach((exercise) => {
    const count = exercise.loggedSets.filter((set) => validWorkingSet(exercise, set)).length;
    if (!count) return;
    workingSets += count;
    const group = muscleGroup(exercise); muscleSets[group] = (muscleSets[group] ?? 0) + count;
    const bucket = movement(group); if (bucket) movementSets[bucket] += count;
    const week = monday(workout.startedAt); byWeek.set(week, (byWeek.get(week) ?? 0) + exercise.loggedSets.reduce((sum, set) => sum + externalLoadVolume(exercise, set), 0));
  }));
  const movementTotal = movementSets.Push + movementSets.Pull + movementSets.Legs;
  const movementBalance = movementTotal ? { Push: Math.round(movementSets.Push * 100 / movementTotal), Pull: Math.round(movementSets.Pull * 100 / movementTotal), Legs: Math.round(movementSets.Legs * 100 / movementTotal) } : { Push: 0, Pull: 0, Legs: 0 };
  const deltaPct = previousVolumeKg > 0 ? (totalVolumeKg - previousVolumeKg) / previousVolumeKg * 100 : undefined;
  const trend = deltaPct === undefined ? "No prior data" : deltaPct > 5 ? "Progressing" : deltaPct < -5 ? "Regressing" : "Stable";
  return { sessions: current.length, workingSets, totalVolumeKg, previousVolumeKg, deltaPct, trend, weeklyVolume: [...byWeek].map(([week, volumeKg]) => ({ week, volumeKg })).sort((a, b) => a.week - b.week), muscleSets, movementBalance };
}
