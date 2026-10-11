import type { Workout } from "./types";
export function workoutTimerStartedAt(workout: Workout): number | undefined {
  if (workout.timerStartedAt !== undefined) return workout.timerStartedAt;
  const clocks = workout.exercises.flatMap(e => e.loggedSets).map(s => s.loggedAt).filter(at => Number.isFinite(at) && at > 0);
  return clocks.length ? Math.min(...clocks) : undefined;
}
export function workoutElapsedSeconds(workout: Workout, now = Date.now()) {
  const started = workoutTimerStartedAt(workout);
  return started === undefined ? 0 : Math.max(0, Math.floor((now - started) / 1000));
}
