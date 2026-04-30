import { Q } from '@nozbe/watermelondb';
import { combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { database } from '../database';
import { clearActiveWorkoutId, setActiveWorkoutId } from './settingsRepository';
import { requireNonEmpty, requireNumberMin } from '../../utils/validation';
import { calculateSetVolume } from '../../utils/volume';

const WORKOUTS = database.get('workouts');
const WORKOUT_EXERCISES = database.get('workout_exercises');
const WORKOUT_SETS = database.get('workout_sets');
const PLAN_EXERCISES = database.get('plan_exercises');
const PLAN_DAYS = database.get('plan_days');

export function getPlanDaysObservable() {
  return database.get('plan_days').query(Q.sortBy('order_index', Q.asc)).observe();
}

export async function startWorkoutFromPlanDay(planDayId) {
  requireNonEmpty(planDayId, 'planDayId');
  const now = Date.now();
  const planDay = await PLAN_DAYS.find(planDayId);
  const planRows = await PLAN_EXERCISES.query(Q.where('plan_day_id', planDayId), Q.sortBy('order_index', Q.asc)).fetch();
  let workout = null;
  await database.write(async () => {
    workout = await WORKOUTS.create((row) => {
      row.planDay.id = planDayId;
      row.plan.id = planDay?._raw?.plan_id || null;
      row.name = `${planDay?.name || 'Workout'} Session`;
      row.startedAt = now;
      row.durationSeconds = 0;
      row.status = 'active';
      row._raw.created_at = now;
      row.updatedAt = now;
    });

    const weCreates = planRows.map((planExercise, index) =>
      WORKOUT_EXERCISES.prepareCreate((item) => {
        item.workout.set(workout);
        item.exercise.id = planExercise._raw.exercise_id;
        item.orderIndex = index;
        item.supersetGroup = planExercise.supersetGroup || '';
        item.notes = planExercise.notes || '';
        item._raw.created_at = now;
        item.updatedAt = now;
      })
    );
    if (weCreates.length > 0) await database.batch(...weCreates);
  });
  await setActiveWorkoutId(workout.id);
  return workout;
}

export async function startEmptyWorkout(name) {
  requireNonEmpty(name, 'name');
  const now = Date.now();
  let workout = null;
  await database.write(async () => {
    workout = await WORKOUTS.create((row) => {
      row.name = String(name).trim();
      row.startedAt = now;
      row.durationSeconds = 0;
      row.status = 'active';
      row._raw.created_at = now;
      row.updatedAt = now;
    });
  });
  await setActiveWorkoutId(workout.id);
  return workout;
}

export function getActiveWorkoutObservable() {
  return WORKOUTS.query(Q.where('status', 'active'), Q.sortBy('started_at', Q.desc)).observe();
}

export function getWorkoutObservable(workoutId) {
  return WORKOUTS.findAndObserve(workoutId);
}

export async function addExerciseToWorkout(workoutId, exerciseId) {
  requireNonEmpty(workoutId, 'workoutId');
  requireNonEmpty(exerciseId, 'exerciseId');
  const now = Date.now();
  const count = await WORKOUT_EXERCISES.query(Q.where('workout_id', workoutId)).fetchCount();
  let created = null;
  await database.write(async () => {
    created = await WORKOUT_EXERCISES.create((row) => {
      row.workout.id = workoutId;
      row.exercise.id = exerciseId;
      row.orderIndex = count;
      row.supersetGroup = '';
      row.notes = '';
      row._raw.created_at = now;
      row.updatedAt = now;
    });
  });
  return created;
}

export async function removeExerciseFromWorkout(workoutExerciseId) {
  requireNonEmpty(workoutExerciseId, 'workoutExerciseId');
  const workoutExercise = await WORKOUT_EXERCISES.find(workoutExerciseId);
  const sets = await WORKOUT_SETS.query(Q.where('workout_exercise_id', workoutExerciseId)).fetch();
  await database.write(async () => {
    for (const set of sets) {
      await set.markAsDeleted();
      await set.destroyPermanently();
    }
    await workoutExercise.markAsDeleted();
    await workoutExercise.destroyPermanently();
  });
}

export async function addSet(workoutExerciseId, input) {
  requireNonEmpty(workoutExerciseId, 'workoutExerciseId');
  requireNumberMin(input?.weight ?? 0, 0, 'weight');
  requireNumberMin(input?.reps ?? 0, 0, 'reps');
  requireNumberMin(input?.restSeconds ?? 0, 0, 'restSeconds');

  const now = Date.now();
  const count = await WORKOUT_SETS.query(Q.where('workout_exercise_id', workoutExerciseId)).fetchCount();
  let created = null;
  await database.write(async () => {
    created = await WORKOUT_SETS.create((row) => {
      row.workoutExercise.id = workoutExerciseId;
      row.setIndex = count + 1;
      row.weight = Number(input.weight ?? 0);
      row.reps = Number(input.reps ?? 0);
      row.rpe = input.rpe !== undefined ? Number(input.rpe) : null;
      row.rir = input.rir !== undefined ? Number(input.rir) : null;
      row.restSeconds = Number(input.restSeconds ?? 0);
      row.isWarmup = !!input.isWarmup;
      row.isDropset = !!input.isDropset;
      row.isAmrap = !!input.isAmrap;
      row.toFailure = !!input.toFailure;
      row.completedAt = input.completedAt ? Number(input.completedAt) : now;
      row._raw.created_at = now;
      row.updatedAt = now;
    });
  });
  return created;
}

export async function updateSet(setId, input) {
  requireNonEmpty(setId, 'setId');
  const set = await WORKOUT_SETS.find(setId);
  await database.write(async () => {
    await set.update((row) => {
      if (input.setIndex !== undefined) row.setIndex = Number(input.setIndex);
      if (input.weight !== undefined) row.weight = Number(input.weight);
      if (input.reps !== undefined) row.reps = Number(input.reps);
      if (input.rpe !== undefined) row.rpe = input.rpe === null ? null : Number(input.rpe);
      if (input.rir !== undefined) row.rir = input.rir === null ? null : Number(input.rir);
      if (input.restSeconds !== undefined) row.restSeconds = Number(input.restSeconds);
      if (input.isWarmup !== undefined) row.isWarmup = !!input.isWarmup;
      if (input.isDropset !== undefined) row.isDropset = !!input.isDropset;
      if (input.isAmrap !== undefined) row.isAmrap = !!input.isAmrap;
      if (input.toFailure !== undefined) row.toFailure = !!input.toFailure;
      if (input.completedAt !== undefined) row.completedAt = input.completedAt ? Number(input.completedAt) : null;
      row.updatedAt = Date.now();
    });
  });
  return set;
}

export async function deleteSet(setId) {
  requireNonEmpty(setId, 'setId');
  const set = await WORKOUT_SETS.find(setId);
  await database.write(async () => {
    await set.markAsDeleted();
    await set.destroyPermanently();
  });
}

export async function completeWorkout(workoutId) {
  requireNonEmpty(workoutId, 'workoutId');
  const workout = await WORKOUTS.find(workoutId);
  const now = Date.now();
  await database.write(async () => {
    await workout.update((row) => {
      row.status = 'completed';
      row.completedAt = now;
      row.durationSeconds = Math.max(0, Math.round((now - row.startedAt) / 1000));
      row.updatedAt = now;
    });
  });
  await clearActiveWorkoutId();
  return workout;
}

export async function abandonWorkout(workoutId) {
  requireNonEmpty(workoutId, 'workoutId');
  const workout = await WORKOUTS.find(workoutId);
  await database.write(async () => {
    await workout.update((row) => {
      row.status = 'abandoned';
      row.completedAt = Date.now();
      row.updatedAt = Date.now();
    });
  });
  await clearActiveWorkoutId();
  return workout;
}

export function getCompletedWorkoutsObservable() {
  return WORKOUTS.query(Q.where('status', 'completed'), Q.sortBy('started_at', Q.desc)).observe();
}

export async function updateWorkoutMetadata(workoutId, { startedAt, durationSeconds, rating, notes }) {
  requireNonEmpty(workoutId, 'workoutId');
  const workout = await WORKOUTS.find(workoutId);
  await database.write(async () => {
    await workout.update((row) => {
      if (startedAt !== undefined) {
        row.startedAt = Number(startedAt);
        // keep completedAt in sync if it was the same as startedAt
        if (row.completedAt && row.completedAt < row.startedAt) {
          row.completedAt = row.startedAt;
        }
      }
      if (durationSeconds !== undefined) row.durationSeconds = Math.max(0, Number(durationSeconds));
      if (rating !== undefined) row.rating = rating === null ? null : Number(rating);
      if (notes !== undefined) row.notes = notes == null ? '' : String(notes);
      row.updatedAt = Date.now();
    });
  });
  return workout;
}

export async function deleteWorkoutWithData(workoutId) {
  requireNonEmpty(workoutId, 'workoutId');
  const wes = await WORKOUT_EXERCISES.query(Q.where('workout_id', workoutId)).fetch();
  const weIds = wes.map((we) => we.id);
  const sets = weIds.length > 0
    ? await WORKOUT_SETS.query(Q.where('workout_exercise_id', Q.oneOf(weIds))).fetch()
    : [];
  const workout = await WORKOUTS.find(workoutId);
  await database.write(async () => {
    await database.batch(
      ...sets.map((s) => s.prepareDestroyPermanently()),
      ...wes.map((we) => we.prepareDestroyPermanently()),
      workout.prepareDestroyPermanently(),
    );
  });
}

/**
 * Create a fully completed workout in one batch.
 * exerciseData shape: [{ exerciseId, name, sets: [{ weight, reps, rpe, isWarmup, isDropset }] }]
 */
export async function createCompletedWorkout({ name, startedAt, durationSeconds, rating, notes, exerciseData = [] }) {
  const now = Date.now();
  const completedAt = startedAt + durationSeconds * 1000;

  const EXERCISES_COL = database.get('exercises');
  const allExercises = await EXERCISES_COL.query().fetch();
  const exByNormName = new Map(allExercises.map(e => [e.normalizedName, e.id]));

  // Upsert any exercises not yet in the DB (custom ones from session)
  const missing = exerciseData.filter(ex => !exByNormName.has((ex.name || '').toLowerCase().trim()));
  if (missing.length) {
    const preps = missing.map(ex => {
      const normName = (ex.name || 'Exercise').toLowerCase().trim();
      return EXERCISES_COL.prepareCreate(row => {
        row.name = ex.name;
        row.normalizedName = normName;
        row.primaryMuscle = (Array.isArray(ex.primaryMuscles) ? ex.primaryMuscles[0] : ex.primaryMuscle) || 'Other';
        row.equipment = ex.equipment || 'Other';
        row.category = ex.category || 'strength';
        row.isCustom = true;
        row.source = 'user_custom';
        row.notes = '';
        row._raw.created_at = now;
        row.updatedAt = now;
      });
    });
    await database.write(async () => { await database.batch(...preps); });
    preps.forEach((p, i) => { exByNormName.set(missing[i].name.toLowerCase().trim(), p.id); });
  }

  const workoutPrep = WORKOUTS.prepareCreate(row => {
    row.name = name || 'Workout';
    row.startedAt = startedAt;
    row.completedAt = completedAt;
    row.durationSeconds = Math.max(0, Math.round(Number(durationSeconds)));
    row.rating = rating != null ? Number(rating) : null;
    row.notes = notes || '';
    row.status = 'completed';
    row._raw.created_at = startedAt;
    row.updatedAt = now;
  });

  const wePreps = [];
  const setPreps = [];

  exerciseData.forEach((ex, exIndex) => {
    const normName = (ex.name || '').toLowerCase().trim();
    const exerciseId = ex.exerciseId || exByNormName.get(normName);
    if (!exerciseId) return;
    const wePrep = WORKOUT_EXERCISES.prepareCreate(row => {
      row.workout.id = workoutPrep.id;
      row.exercise.id = exerciseId;
      row.orderIndex = exIndex;
      row.supersetGroup = ex.supersetGroup || '';
      row.notes = ex.note || ex.notes || '';
      row._raw.created_at = startedAt;
      row.updatedAt = now;
    });
    wePreps.push(wePrep);
    (ex.sets || []).forEach((s, si) => {
      setPreps.push(WORKOUT_SETS.prepareCreate(row => {
        row.workoutExercise.id = wePrep.id;
        row.setIndex = si + 1;
        row.weight = Math.max(0, Number(s.weight || 0));
        row.reps = Math.max(0, Number(s.reps || 0));
        row.rpe = s.rpe != null ? Number(s.rpe) : null;
        row.rir = s.rir != null ? Number(s.rir) : null;
        row.restSeconds = Math.max(0, Number(s.restSeconds ?? s.rest ?? 0));
        row.isWarmup = s.type === 'warmup' || !!s.isWarmup;
        row.isDropset = s.type === 'dropset' || !!s.isDropset;
        row.isAmrap = s.type === 'amrap' || !!s.isAmrap || !!s.isAMRAP;
        row.toFailure = s.type === 'failure' || !!s.toFailure;
        row.completedAt = completedAt;
        row._raw.created_at = startedAt;
        row.updatedAt = now;
      }));
    });
  });

  await database.write(async () => { await database.batch(workoutPrep, ...wePreps, ...setPreps); });
  return workoutPrep;
}

export async function clearCompletedWorkouts() {
  const workouts = await WORKOUTS.query(Q.where('status', 'completed')).fetch();
  if (workouts.length === 0) return;
  const workoutIds = workouts.map((w) => w.id);
  const wes = await WORKOUT_EXERCISES.query(Q.where('workout_id', Q.oneOf(workoutIds))).fetch();
  const weIds = wes.map((we) => we.id);
  const sets = weIds.length > 0
    ? await WORKOUT_SETS.query(Q.where('workout_exercise_id', Q.oneOf(weIds))).fetch()
    : [];
  await database.write(async () => {
    await database.batch(
      ...sets.map((s) => s.prepareDestroyPermanently()),
      ...wes.map((we) => we.prepareDestroyPermanently()),
      ...workouts.map((w) => w.prepareDestroyPermanently()),
    );
  });
}

export function getWorkoutDetailObservable(workoutId) {
  requireNonEmpty(workoutId, 'workoutId');
  return combineLatest([
    WORKOUTS.findAndObserve(workoutId),
    WORKOUT_EXERCISES.query(Q.where('workout_id', workoutId), Q.sortBy('order_index', Q.asc)).observe(),
    WORKOUT_SETS.query().observe(),
  ]).pipe(
    map(([workout, exercises, sets]) => {
      const exerciseIds = new Set(exercises.map((e) => e.id));
      const scopedSets = sets
        .filter((set) => exerciseIds.has(set._raw.workout_exercise_id))
        .sort((a, b) => a.setIndex - b.setIndex);
      const totalVolume = scopedSets.reduce(
        (sum, item) => sum + calculateSetVolume(item.weight, item.reps),
        0
      );
      return { workout, exercises, sets: scopedSets, totalVolume };
    })
  );
}
