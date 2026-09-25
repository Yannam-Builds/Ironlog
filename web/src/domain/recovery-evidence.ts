import { deriveSnapshot, isWorkingSet, regionContribution } from "./engine";
import type { AppSnapshot, RecoveryCheckin, Workout } from "./types";

export const RECOVERY_WINDOWS = [["Workout", 3], ["7D", 7], ["30D", 30], ["Program", 90]] as const;
export type RecoveryEvidence = { exerciseName: string; sessions: number; workingSets: number; contribution: number; latestAt: number };

const inWindow = (workout: Workout, days: number, now: number) => workout.status === "completed" && workout.startedAt <= now && now - workout.startedAt <= days * 86_400_000;

export function regionRecoveryEvidence(workouts: Workout[], region: string, days: number, now = Date.now()): RecoveryEvidence[] {
  const grouped = new Map<string, { sessions: Set<string>; workingSets: number; contribution: number; latestAt: number }>();
  for (const workout of workouts.filter((row) => inWindow(row, days, now))) for (const exercise of workout.exercises) {
    const share = regionContribution(exercise)[region] ?? 0;
    const sets = exercise.loggedSets.filter((set) => isWorkingSet(exercise, set));
    if (!share || !sets.length) continue;
    const current = grouped.get(exercise.name) ?? { sessions: new Set(), workingSets: 0, contribution: 0, latestAt: 0 };
    current.sessions.add(workout.id); current.workingSets += sets.length; current.contribution += sets.length * share; current.latestAt = Math.max(current.latestAt, workout.startedAt); grouped.set(exercise.name, current);
  }
  return [...grouped.entries()].map(([exerciseName, row]) => ({ exerciseName, sessions: row.sessions.size, workingSets: row.workingSets, contribution: row.contribution, latestAt: row.latestAt })).sort((a, b) => b.contribution - a.contribution || b.latestAt - a.latestAt || a.exerciseName.localeCompare(b.exerciseName));
}

export function recoverySourceLabel(workouts: Workout[], checkins: RecoveryCheckin[], days: number, now = Date.now()) {
  const mapped = new Set(workouts.filter((row) => inWindow(row, days, now) && row.exercises.some((exercise) => Object.keys(regionContribution(exercise)).length && exercise.loggedSets.some((set) => isWorkingSet(exercise, set)))).map((row) => row.id)).size;
  const manual = checkins.some((row) => row.at <= now && now - row.at <= 48 * 3_600_000);
  const base = mapped === 0 ? "No mapped workout evidence" : mapped === 1 ? "1 recent mapped workout" : `${mapped} recent mapped workouts`;
  return manual ? `${base} + manual check-in` : base;
}

export function recoverySuggestions(recovery: Record<string, number>, painRegions: string[]) {
  if (painRegions.length) return ["Review pain flags before training; avoid painful movements."];
  const rows = Object.entries(recovery).sort((a, b) => a[1] - b[1]);
  if (!rows.length) return [];
  const [limiting, score] = rows[0];
  if (score < 72) return [`Back off ${limiting.toLowerCase()} loading and use warm-ups to reassess.`];
  if (score < 90) return [`Maintain ${limiting.toLowerCase()} work without adding fatigue.`];
  return ["Recent mapped regions are ready; progress only if warm-ups and symptoms agree."];
}

export function readinessTrend(snapshot: AppSnapshot, now = Date.now()) {
  return Array.from({ length: 14 }, (_, index) => {
    const at = now - (13 - index) * 86_400_000;
    const derived = deriveSnapshot(snapshot, at);
    return { at, score: Object.keys(derived.recovery).length ? derived.readiness : undefined };
  });
}
