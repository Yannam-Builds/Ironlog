import { useEffect, useState } from 'react';
import { combineLatest } from 'rxjs';
import { Q } from '@nozbe/watermelondb';
import { database } from '../db/database';
import {
  createPlan,
  updatePlan,
  deletePlan,
  createPlanDay,
  updatePlanDay,
  deletePlanDay,
  addExerciseToPlanDay,
  updatePlanExercise,
  removePlanExercise,
  reorderPlanExercises,
  importFullPlan,
  replaceAllPlans,
} from '../db/repositories/planRepository';

const PLANS = database.get('plans');
const PLAN_DAYS = database.get('plan_days');
const PLAN_EXERCISES = database.get('plan_exercises');
const EXERCISES = database.get('exercises');

/** Map WatermelonDB rows into the flat shape PlansScreen already expects. */
function assemblePlans(plans, days, planExercises, exercises) {
  const exerciseById = new Map(exercises.map((e) => [e.id, e]));

  const exercisesByDay = new Map();
  for (const pe of planExercises) {
    const dayId = pe._raw.plan_day_id;
    if (!exercisesByDay.has(dayId)) exercisesByDay.set(dayId, []);
    exercisesByDay.get(dayId).push(pe);
  }

  const daysByPlan = new Map();
  for (const day of days) {
    const planId = day._raw.plan_id;
    if (!daysByPlan.has(planId)) daysByPlan.set(planId, []);
    daysByPlan.get(planId).push(day);
  }

  return plans.map((plan) => {
    const planDays = (daysByPlan.get(plan.id) || [])
      .slice()
      .sort((a, b) => a.orderIndex - b.orderIndex);

    return {
      id: plan.id,
      name: plan.name,
      goal: plan.goal,
      description: plan.description,
      isActive: plan.isActive,
      days: planDays.map((day) => {
        const exRows = (exercisesByDay.get(day.id) || [])
          .slice()
          .sort((a, b) => a.orderIndex - b.orderIndex);
        return {
          id: day.id,
          name: day.name,
          color: day.color,
          exercises: exRows.map((pe) => {
            const ex = exerciseById.get(pe._raw.exercise_id);
            return {
              id: pe.id,
              exerciseId: pe._raw.exercise_id,
              name: ex?.name || 'Unknown',
              sets: pe.sets,
              reps: pe.reps,
              restSeconds: pe.restSeconds,
              supersetGroup: pe.supersetGroup,
              isWarmup: pe.isWarmup,
              notes: pe.notes,
            };
          }),
        };
      }),
    };
  });
}

export default function useWatermelonPlans() {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const sub = combineLatest([
      PLANS.query(Q.sortBy('updated_at', Q.desc)).observe(),
      PLAN_DAYS.query(Q.sortBy('order_index', Q.asc)).observe(),
      PLAN_EXERCISES.query(Q.sortBy('order_index', Q.asc)).observe(),
      EXERCISES.query().observe(),
    ]).subscribe(([p, d, pe, e]) => {
      setPlans(assemblePlans(p, d, pe, e));
      setLoading(false);
    });
    return () => sub.unsubscribe();
  }, []);

  const addPlan = (name) =>
    createPlan({ name: name.trim(), goal: 'General Fitness', description: '', isActive: false });

  const renamePlan = (planId, name) =>
    updatePlan(planId, { name: name.trim() });

  const removePlan = (planId) => deletePlan(planId);

  const addDay = (planId, name, color) =>
    createPlanDay(planId, { name, color: color || '#FF4500' });

  const renameDay = (dayId, name) => updatePlanDay(dayId, { name });

  const removeDay = (dayId) => deletePlanDay(dayId);

  const addExerciseToDay = (dayId, exerciseId, meta) =>
    addExerciseToPlanDay(dayId, {
      exerciseId,
      sets: meta?.sets ?? 3,
      reps: meta?.reps ?? '8-12',
      restSeconds: meta?.restSeconds ?? 90,
      supersetGroup: meta?.supersetGroup ?? '',
      isWarmup: meta?.isWarmup ?? false,
      notes: meta?.notes ?? '',
    });

  const updateExercise = (planExerciseId, updates) =>
    updatePlanExercise(planExerciseId, updates);

  const removeExercise = (planExerciseId) => removePlanExercise(planExerciseId);

  const reorderExercises = (dayId, orderedIds) =>
    reorderPlanExercises(dayId, orderedIds);

  const savePlans = async (nextPlans) => {
    await replaceAllPlans(Array.isArray(nextPlans) ? nextPlans : []);
  };

  return {
    plans,
    loading,
    savePlans,
    addPlan,
    renamePlan,
    removePlan,
    addDay,
    renameDay,
    removeDay,
    addExerciseToDay,
    updateExercise,
    removeExercise,
    reorderExercises,
    importPlan: importFullPlan,
  };
}
