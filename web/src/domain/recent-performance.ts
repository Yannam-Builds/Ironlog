import type { LoggedSet, SessionExercise, Workout } from './types';
import { setDescription, trackingMode, trackingOptions, validWorkingSet } from './tracking';

export interface RecentPerformance {
  workoutId: string;
  workoutName: string;
  occurredAt: number;
  exercise: SessionExercise;
  comparable: boolean;
}

/** Read-only retained working sets; never copies old performance into a new session. */
export function recentPerformances(
  history: Workout[], current: SessionExercise, dayId?: string,
  now = Date.now(), limit = 6,
): RecentPerformance[] {
  if (!current.exerciseId.trim() || !Number.isFinite(limit) || limit <= 0) return [];
  const mode = trackingMode(current);
  return history
    .filter(w => w.status === 'completed' && Number.isFinite(w.startedAt) && w.startedAt <= now &&
      (dayId === undefined || w.dayId === dayId))
    .sort((a,b) => b.startedAt - a.startedAt || a.id.localeCompare(b.id))
    .flatMap(w => w.exercises.filter(e => e.exerciseId === current.exerciseId).flatMap(e => {
      const loggedSets = e.loggedSets.filter(s => validWorkingSet(e,s));
      if (!loggedSets.length) return [];
      return [{workoutId:w.id,workoutName:w.name,occurredAt:w.startedAt,
        exercise:{...e,loggedSets},comparable:trackingOptions.includes(mode) && mode === trackingMode(e) &&
          !!current.equipment.trim() && current.equipment.trim().toLowerCase() === e.equipment.trim().toLowerCase()}];
    }))
    .slice(0,Math.min(20,Math.floor(limit)));
}

export function recentSetLabel(
  exercise: SessionExercise, set: LoggedSet, unit: string,
  display: (kg:number,unit:string)=>number,
) {
  const rir = set.rir;
  const rpe = set.rpe;
  const effort = rir !== undefined && Number.isFinite(rir) && rir >= 0 && rir <= 10 ? ` · RIR ${rir}`
    : rpe !== undefined && Number.isFinite(rpe) && rpe >= 1 && rpe <= 10 ? ` · RPE ${rpe}` : '';
  return setDescription(exercise,set,unit,display) + effort;
}

/** Timestamp ordering still works when warmups are inserted before working sets. */
export function latestPerformedSet(workout: Workout, now = Date.now()) {
  return workout.exercises.flatMap(exercise => exercise.loggedSets.map(set => ({exercise,set})))
    .filter(({set}) => Number.isFinite(set.loggedAt) && set.loggedAt <= now)
    .sort((a,b) => b.set.loggedAt - a.set.loggedAt)
    .at(0);
}
