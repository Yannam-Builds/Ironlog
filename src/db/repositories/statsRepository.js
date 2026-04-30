import { Q } from '@nozbe/watermelondb';
import { combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { database } from '../database';
import { startOfWeek } from '../../utils/dates';
import { calculateSetVolume } from '../../utils/volume';
import { calculateEstimated1RM } from '../../utils/oneRepMax';
import { getPrGroupId } from '../../services/prLinkingEngine';

const WORKOUTS = database.get('workouts');
const WORKOUT_EXERCISES = database.get('workout_exercises');
const WORKOUT_SETS = database.get('workout_sets');
const EXERCISES = database.get('exercises');
const EXERCISE_MUSCLES = database.get('exercise_muscles');

export function getSessionsPerWeekObservable() {
  const weekStart = startOfWeek();
  return WORKOUTS.query(
    Q.where('status', 'completed'),
    Q.where('started_at', Q.gte(weekStart))
  ).observeCount();
}

export function getWeeklyVolumeObservable() {
  const weekStart = startOfWeek();
  return combineLatest([
    WORKOUTS.query(Q.where('status', 'completed'), Q.where('started_at', Q.gte(weekStart))).observe(),
    WORKOUT_EXERCISES.query().observe(),
    WORKOUT_SETS.query().observe(),
  ]).pipe(
    map(([workouts, workoutExercises, sets]) => {
      const workoutIds = new Set(workouts.map((w) => w.id));
      const workoutExerciseIds = new Set(
        workoutExercises.filter((row) => workoutIds.has(row._raw.workout_id)).map((row) => row.id)
      );
      return sets
        .filter((set) => workoutExerciseIds.has(set._raw.workout_exercise_id) && !set.isWarmup)
        .reduce((sum, set) => sum + calculateSetVolume(set.weight, set.reps), 0);
    })
  );
}

export function getExerciseEstimatedOneRepMaxObservable(exerciseId) {
  return combineLatest([WORKOUT_EXERCISES.query(Q.where('exercise_id', exerciseId)).observe(), WORKOUT_SETS.query().observe()]).pipe(
    map(([workoutExercises, sets]) => {
      const exerciseRows = new Set(workoutExercises.map((item) => item.id));
      let max = 0;
      sets.forEach((set) => {
        if (!exerciseRows.has(set._raw.workout_exercise_id) || set.isWarmup) return;
        max = Math.max(max, calculateEstimated1RM(set.weight, set.reps));
      });
      return max;
    })
  );
}

export function getPRsObservable() {
  return combineLatest([WORKOUT_SETS.query().observe(), WORKOUT_EXERCISES.query().observe(), EXERCISES.query().observe()]).pipe(
    map(([sets, workoutExercises, exercises]) => {
      const exByWorkoutExerciseId = new Map(workoutExercises.map((row) => [row.id, row._raw.exercise_id]));
      const exerciseById = new Map(exercises.map((ex) => [ex.id, ex]));
      const best = new Map();
      sets.forEach((set) => {
        if (set.isWarmup) return;
        const exerciseId = exByWorkoutExerciseId.get(set._raw.workout_exercise_id);
        if (!exerciseId) return;
        const ex = exerciseById.get(exerciseId);
        const groupId = getPrGroupId(ex?.name || '', {
          primaryMuscle: ex?.primaryMuscle,
          equipment: ex?.equipment,
          category: ex?.category,
        }, 'safe') || `exact_${ex?.name || exerciseId}`;
        const current = best.get(groupId);
        const candidate = calculateEstimated1RM(set.weight, set.reps);
        if (!current || candidate > current.estimated1RM) {
          best.set(groupId, {
            exerciseId,
            exerciseName: ex?.name || 'Unknown',
            exercisePrGroupId: groupId,
            weight: set.weight,
            reps: set.reps,
            estimated1RM: candidate,
          });
        }
      });
      return Array.from(best.values()).sort((a, b) => b.estimated1RM - a.estimated1RM);
    })
  );
}

export function getMuscleVolumeObservable(timeRange = 14) {
  const from = Date.now() - timeRange * 24 * 60 * 60 * 1000;
  return combineLatest([
    WORKOUTS.query(Q.where('status', 'completed'), Q.where('started_at', Q.gte(from))).observe(),
    WORKOUT_EXERCISES.query().observe(),
    WORKOUT_SETS.query().observe(),
    EXERCISE_MUSCLES.query().observe(),
  ]).pipe(
    map(([workouts, workoutExercises, sets, exerciseMuscles]) => {
      const workoutIds = new Set(workouts.map((w) => w.id));
      const workoutExerciseRows = workoutExercises.filter((row) => workoutIds.has(row._raw.workout_id));
      const workoutExerciseById = new Map(workoutExerciseRows.map((row) => [row.id, row]));
      const musclesByExercise = new Map();
      exerciseMuscles.forEach((row) => {
        const list = musclesByExercise.get(row._raw.exercise_id) || [];
        list.push(row);
        musclesByExercise.set(row._raw.exercise_id, list);
      });

      const totals = {};
      sets.forEach((set) => {
        if (set.isWarmup) return;
        const workoutExercise = workoutExerciseById.get(set._raw.workout_exercise_id);
        if (!workoutExercise) return;
        const volume = calculateSetVolume(set.weight, set.reps);
        const muscles = musclesByExercise.get(workoutExercise._raw.exercise_id) || [];
        muscles.forEach((muscle) => {
          const contrib = volume * Number(muscle.contributionFraction || 0);
          totals[muscle.muscle] = (totals[muscle.muscle] || 0) + contrib;
        });
      });
      return totals;
    })
  );
}

export { calculateEstimated1RM };
export { calculateSetVolume };
