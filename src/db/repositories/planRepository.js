import { Q } from '@nozbe/watermelondb';
import { combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { database } from '../database';
import { requireNonEmpty, requireNumberMin } from '../../utils/validation';

const PLANS = database.get('plans');
const PLAN_DAYS = database.get('plan_days');
const PLAN_EXERCISES = database.get('plan_exercises');
const EXERCISES_TABLE = database.get('exercises');

export async function createPlan(input) {
  requireNonEmpty(input?.name, 'name');
  requireNonEmpty(input?.goal, 'goal');
  const now = Date.now();
  let created = null;
  await database.write(async () => {
    created = await PLANS.create((row) => {
      row.name = String(input.name).trim();
      row.goal = String(input.goal).trim();
      row.description = input.description ? String(input.description) : '';
      row.isActive = !!input.isActive;
      row._raw.created_at = now;
      row.updatedAt = now;
    });
  });
  return created;
}

export async function updatePlan(planId, input) {
  requireNonEmpty(planId, 'planId');
  const plan = await PLANS.find(planId);
  await database.write(async () => {
    await plan.update((row) => {
      if (input.name) row.name = String(input.name).trim();
      if (input.goal) row.goal = String(input.goal).trim();
      if (input.description !== undefined) row.description = input.description || '';
      if (input.isActive !== undefined) row.isActive = !!input.isActive;
      row.updatedAt = Date.now();
    });
  });
  return plan;
}

export async function deletePlan(planId) {
  requireNonEmpty(planId, 'planId');
  const plan = await PLANS.find(planId);
  const days = await PLAN_DAYS.query(Q.where('plan_id', planId)).fetch();
  await database.write(async () => {
    for (const day of days) {
      const exercises = await PLAN_EXERCISES.query(Q.where('plan_day_id', day.id)).fetch();
      for (const exercise of exercises) {
        await exercise.markAsDeleted();
        await exercise.destroyPermanently();
      }
      await day.markAsDeleted();
      await day.destroyPermanently();
    }
    await plan.markAsDeleted();
    await plan.destroyPermanently();
  });
}

export function getPlansObservable() {
  return PLANS.query(Q.sortBy('updated_at', Q.desc)).observe();
}

export function getPlanObservable(planId) {
  requireNonEmpty(planId, 'planId');
  return PLANS.findAndObserve(planId);
}

export function getPlanDaysObservable(planId) {
  requireNonEmpty(planId, 'planId');
  return PLAN_DAYS.query(Q.where('plan_id', planId), Q.sortBy('order_index', Q.asc)).observe();
}

export function getPlanExercisesObservable(planDayId) {
  requireNonEmpty(planDayId, 'planDayId');
  return PLAN_EXERCISES.query(Q.where('plan_day_id', planDayId), Q.sortBy('order_index', Q.asc)).observe();
}

export function getPlanBundleObservable(planId, activeDayId = null) {
  requireNonEmpty(planId, 'planId');
  return combineLatest([
    getPlanObservable(planId),
    getPlanDaysObservable(planId),
  ]).pipe(
    map(([plan, days]) => ({
      plan,
      days,
      activeDayId: activeDayId || days[0]?.id || null,
    }))
  );
}

export async function getPlanById(planId) {
  requireNonEmpty(planId, 'planId');
  const plan = await PLANS.find(planId);
  const days = await PLAN_DAYS.query(Q.where('plan_id', planId), Q.sortBy('order_index', Q.asc)).fetch();
  const dayIds = days.map((d) => d.id);
  const exercises =
    dayIds.length === 0
      ? []
      : await PLAN_EXERCISES.query(Q.where('plan_day_id', Q.oneOf(dayIds)), Q.sortBy('order_index', Q.asc)).fetch();
  return { plan, days, exercises };
}

export async function createPlanDay(planId, input) {
  requireNonEmpty(planId, 'planId');
  requireNonEmpty(input?.name, 'name');
  const now = Date.now();
  const count = await PLAN_DAYS.query(Q.where('plan_id', planId)).fetchCount();
  let created = null;
  await database.write(async () => {
    created = await PLAN_DAYS.create((row) => {
      row.plan.id = planId;
      row.name = String(input.name).trim();
      row.color = input.color || '#FF4500';
      row.orderIndex = input.orderIndex ?? count;
      row._raw.created_at = now;
      row.updatedAt = now;
    });
  });
  return created;
}

export async function updatePlanDay(dayId, input) {
  requireNonEmpty(dayId, 'dayId');
  const day = await PLAN_DAYS.find(dayId);
  await database.write(async () => {
    await day.update((row) => {
      if (input.name) row.name = String(input.name).trim();
      if (input.color) row.color = String(input.color);
      if (input.orderIndex !== undefined) row.orderIndex = Number(input.orderIndex);
      row.updatedAt = Date.now();
    });
  });
  return day;
}

export async function deletePlanDay(dayId) {
  requireNonEmpty(dayId, 'dayId');
  const day = await PLAN_DAYS.find(dayId);
  const exercises = await PLAN_EXERCISES.query(Q.where('plan_day_id', dayId)).fetch();
  await database.write(async () => {
    for (const exercise of exercises) {
      await exercise.markAsDeleted();
      await exercise.destroyPermanently();
    }
    await day.markAsDeleted();
    await day.destroyPermanently();
  });
}

export async function addExerciseToPlanDay(dayId, input) {
  requireNonEmpty(dayId, 'dayId');
  requireNonEmpty(input?.exerciseId, 'exerciseId');
  requireNumberMin(input?.sets ?? 1, 1, 'sets');
  requireNonEmpty(input?.reps, 'reps');
  requireNumberMin(input?.restSeconds ?? 0, 0, 'restSeconds');

  const now = Date.now();
  const count = await PLAN_EXERCISES.query(Q.where('plan_day_id', dayId)).fetchCount();
  let created = null;
  await database.write(async () => {
    created = await PLAN_EXERCISES.create((row) => {
      row.planDay.id = dayId;
      row.exercise.id = input.exerciseId;
      row.orderIndex = input.orderIndex ?? count;
      row.sets = Number(input.sets ?? 1);
      row.reps = String(input.reps).trim();
      row.restSeconds = Number(input.restSeconds ?? 0);
      row.supersetGroup = input.supersetGroup ? String(input.supersetGroup) : '';
      row.isWarmup = !!input.isWarmup;
      row.notes = input.notes ? String(input.notes) : '';
      row._raw.created_at = now;
      row.updatedAt = now;
    });
  });
  return created;
}

export async function updatePlanExercise(planExerciseId, input) {
  requireNonEmpty(planExerciseId, 'planExerciseId');
  const row = await PLAN_EXERCISES.find(planExerciseId);
  await database.write(async () => {
    await row.update((item) => {
      if (input.exerciseId) item.exercise.id = input.exerciseId;
      if (input.orderIndex !== undefined) item.orderIndex = Number(input.orderIndex);
      if (input.sets !== undefined) item.sets = Number(input.sets);
      if (input.reps !== undefined) item.reps = String(input.reps);
      if (input.restSeconds !== undefined) item.restSeconds = Number(input.restSeconds);
      if (input.supersetGroup !== undefined) item.supersetGroup = input.supersetGroup || '';
      if (input.isWarmup !== undefined) item.isWarmup = !!input.isWarmup;
      if (input.notes !== undefined) item.notes = input.notes || '';
      item.updatedAt = Date.now();
    });
  });
  return row;
}

export async function removePlanExercise(planExerciseId) {
  requireNonEmpty(planExerciseId, 'planExerciseId');
  const row = await PLAN_EXERCISES.find(planExerciseId);
  await database.write(async () => {
    await row.markAsDeleted();
    await row.destroyPermanently();
  });
}

/**
 * Replace a plan day's exercises with the actual exercises performed in a completed workout.
 * Called from the "Save to Plan" prompt on the workout completion screen.
 *
 * @param {string} dayId  - plan_day id
 * @param {Array}  exerciseData - exerciseData array built by finishWorkout in ActiveWorkoutScreen
 *   Each entry: { exerciseId, name, sets: [{ type, reps, ... }], prescribedReps? }
 */
export async function syncPlanDayFromWorkout(dayId, exerciseData) {
  requireNonEmpty(dayId, 'dayId');
  const now = Date.now();
  const existing = await PLAN_EXERCISES.query(Q.where('plan_day_id', dayId)).fetch();

  await database.write(async () => {
    const ops = [];

    // Delete all current plan exercises for this day
    for (const pe of existing) {
      ops.push(pe.prepareDestroyPermanently());
    }

    // Create new plan exercises from what was actually done
    let order = 0;
    for (const ex of exerciseData) {
      if (!ex.exerciseId) continue;
      const workSets = (ex.sets || []).filter((s) => !s?.isWarmup && s?.type !== 'warmup');
      const setCount = workSets.length || 1;
      const avgReps = workSets.length > 0
        ? Math.round(workSets.reduce((sum, s) => sum + (Number(s.reps) || 0), 0) / workSets.length)
        : (ex.prescribedReps || 8);
      const restValues = workSets
        .map((s) => Number(s.restSeconds ?? s.rest ?? 0))
        .filter((v) => Number.isFinite(v) && v > 0);
      const avgRestSeconds = restValues.length > 0
        ? Math.max(0, Math.round(restValues.reduce((sum, v) => sum + v, 0) / restValues.length))
        : 90;

      ops.push(PLAN_EXERCISES.prepareCreate(row => {
        row.planDay.id = dayId;
        row.exercise.id = ex.exerciseId;
        row.orderIndex = order;
        row.sets = setCount;
        row.reps = String(avgReps || 8);
        row.restSeconds = avgRestSeconds;
        row.supersetGroup = '';
        row.isWarmup = false;
        row.notes = '';
        row._raw.created_at = now;
        row.updatedAt = now;
      }));
      order++;
    }

    if (ops.length > 0) await database.batch(...ops);
  });
}

export async function reorderPlanExercises(dayId, orderedIds) {
  requireNonEmpty(dayId, 'dayId');
  if (!Array.isArray(orderedIds)) throw new Error('orderedIds must be an array.');
  const rows = await PLAN_EXERCISES.query(Q.where('plan_day_id', dayId)).fetch();
  const rowMap = new Map(rows.map((r) => [r.id, r]));
  await database.write(async () => {
    for (let index = 0; index < orderedIds.length; index += 1) {
      const row = rowMap.get(orderedIds[index]);
      if (!row) continue;
      await row.update((item) => {
        item.orderIndex = index;
        item.updatedAt = Date.now();
      });
    }
  });
}

/**
 * Import a complete plan object (name + days[] + exercises[]) in one write.
 * Used by ProgramPickerScreen (template picker) and AIPlanScreen (AI import).
 *
 * Each exercise in a day may have `exerciseId` (WM or legacy id) or just `name`.
 * We resolve via id first, then name fallback. Exercises that can't be resolved
 * are silently skipped so an unrecognised exercise never aborts the whole import.
 *
 * @param {Object} planObject  { name, days: [{ name, color, exercises: [{ exerciseId?, name, sets, reps, restSeconds?, isWarmup?, notes? }] }] }
 * @returns {Object} the created WatermelonDB plan row
 */
export async function importFullPlan(planObject) {
  requireNonEmpty(planObject?.name, 'plan name');

  const now = Date.now();

  // Pre-resolve exercise IDs (reads must happen outside the write transaction)
  const resolvedDays = [];
  for (let di = 0; di < (planObject.days || []).length; di++) {
    const day = planObject.days[di];
    const resolvedExercises = [];
    for (const ex of (day.exercises || [])) {
      let exId = null;
      // 1. Try direct WM id lookup
      if (ex.exerciseId) {
        try {
          const found = await EXERCISES_TABLE.find(ex.exerciseId);
          if (found) exId = found.id;
        } catch (_) { /* id not found in WM — fall through */ }
      }
      // 2. Fallback: match by exercise name
      if (!exId && ex.name) {
        try {
          const byName = await EXERCISES_TABLE.query(Q.where('name', ex.name)).fetch();
          if (byName.length > 0) exId = byName[0].id;
        } catch (_) { /* ignore */ }
      }
      resolvedExercises.push({ ...ex, _wmExId: exId });
    }
    resolvedDays.push({ ...day, _index: di, exercises: resolvedExercises });
  }

  // Single write transaction — plan + days + exercises
  let wmPlan;
  await database.write(async () => {
    wmPlan = await PLANS.create((row) => {
      row.name = String(planObject.name).trim();
      row.goal = String(planObject.goal || 'General Fitness').trim();
      row.description = String(planObject.description || '').trim();
      row.isActive = false;
      row._raw.created_at = now;
      row.updatedAt = now;
    });

    for (const day of resolvedDays) {
      const wmDay = await PLAN_DAYS.create((row) => {
        row.plan.id = wmPlan.id;
        row.name = String(day.name || `Day ${day._index + 1}`).trim();
        row.color = day.color || '#FF4500';
        row.orderIndex = day._index;
        row._raw.created_at = now;
        row.updatedAt = now;
      });

      let exOrder = 0;
      for (const ex of day.exercises) {
        if (!ex._wmExId) continue; // skip unresolved exercises
        await PLAN_EXERCISES.create((row) => {
          row.planDay.id = wmDay.id;
          row.exercise.id = ex._wmExId;
          row.orderIndex = exOrder;
          row.sets = Number(ex.sets ?? 3);
          row.reps = String(ex.reps ?? '8-12').trim();
          row.restSeconds = Number(ex.restSeconds ?? 90);
          row.supersetGroup = ex.supersetGroup ?? '';
          row.isWarmup = !!ex.isWarmup;
          row.notes = ex.notes ?? '';
          row._raw.created_at = now;
          row.updatedAt = now;
        });
        exOrder++;
      }
    }
  });

  return wmPlan;
}

/**
 * Compatibility helper for legacy screens that still build an in-memory plans array
 * and call savePlans(nextPlans). This replaces the full plans graph in one transaction.
 */
export async function replaceAllPlans(nextPlans = []) {
  const planRows = await PLANS.query().fetch();
  const dayRows = await PLAN_DAYS.query().fetch();
  const exerciseRows = await PLAN_EXERCISES.query().fetch();

  await database.write(async () => {
    if (exerciseRows.length || dayRows.length || planRows.length) {
      await database.batch(
        ...exerciseRows.map((row) => row.prepareDestroyPermanently()),
        ...dayRows.map((row) => row.prepareDestroyPermanently()),
        ...planRows.map((row) => row.prepareDestroyPermanently()),
      );
    }
  });

  for (const plan of nextPlans) {
    // Sequentially import each plan so screen order is preserved by created_at ordering.
    // importFullPlan already runs safely in a write transaction.
    // eslint-disable-next-line no-await-in-loop
    await importFullPlan({
      name: plan?.name || 'Plan',
      goal: plan?.goal || 'General Fitness',
      description: plan?.description || '',
      days: Array.isArray(plan?.days) ? plan.days : [],
    });
  }
}
