import { useEffect, useState } from 'react';
import { combineLatest } from 'rxjs';
import { Q } from '@nozbe/watermelondb';
import { database } from '../db/database';
import { mapHistoryFromWatermelonRows } from '../db/adapters/sessionAdapter';

const WORKOUTS = database.get('workouts');
const WORKOUT_EXERCISES = database.get('workout_exercises');
const WORKOUT_SETS = database.get('workout_sets');
const EXERCISES = database.get('exercises');
const EXERCISE_MUSCLES = database.get('exercise_muscles');
const PLANS = database.get('plans');
const PLAN_DAYS = database.get('plan_days');
const PLAN_EXERCISES = database.get('plan_exercises');
const BODY = database.get('body_measurements');
const APP_SETTINGS = database.get('app_settings');

function assembleSettings(settingsRows) {
  const defaults = { weightUnit: 'kg', weeklyGoalDays: 4, goalMode: 'hypertrophy', theme: 'amoled' };
  if (!settingsRows.length) return defaults;
  try {
    return { ...defaults, ...JSON.parse(settingsRows[0].value) };
  } catch (_) { return defaults; }
}

export default function useWatermelonHome() {
  const [data, setData] = useState({
    history: [],
    plans: [],
    settings: { weightUnit: 'kg', weeklyGoalDays: 4, goalMode: 'hypertrophy' },
    bodyWeight: [],
    loading: true,
  });

  useEffect(() => {
    const sub = combineLatest([
      WORKOUTS.query(Q.or(Q.where('status', 'completed'), Q.where('status', 'active')), Q.sortBy('started_at', Q.desc)).observe(),
      WORKOUT_EXERCISES.query().observe(),
      WORKOUT_SETS.query().observe(),
      EXERCISES.query().observe(),
      EXERCISE_MUSCLES.query().observe(),
      PLANS.query().observe(),
      PLAN_DAYS.query().observe(),
      PLAN_EXERCISES.query().observe(),
      BODY.query(Q.sortBy('measured_at', Q.desc)).observe(),
      APP_SETTINGS.query(Q.where('key', 'ironlog_settings')).observe(),
    ]).subscribe(([workouts, wes, sets, exercises, muscles, plans, planDays, planExercises, body, settingsRows]) => {
      const history = mapHistoryFromWatermelonRows(workouts, wes, sets, exercises);
      const settings = assembleSettings(settingsRows);

      // Assemble plans (same shape as useWatermelonPlans)
      const exById = new Map(exercises.map(e => [e.id, e]));
      const exByDay = new Map();
      for (const pe of planExercises) {
        const dayId = pe._raw.plan_day_id;
        if (!exByDay.has(dayId)) exByDay.set(dayId, []);
        exByDay.get(dayId).push(pe);
      }
      const daysByPlan = new Map();
      for (const d of planDays) {
        const pid = d._raw.plan_id;
        if (!daysByPlan.has(pid)) daysByPlan.set(pid, []);
        daysByPlan.get(pid).push(d);
      }
      const assembledPlans = plans.map(p => ({
        id: p.id,
        name: p.name,
        goal: p.goal,
        isActive: p.isActive,
        days: (daysByPlan.get(p.id) || [])
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .map(d => ({
            id: d.id,
            name: d.name,
            color: d.color,
            exercises: (exByDay.get(d.id) || [])
              .sort((a, b) => a.orderIndex - b.orderIndex)
              .map(pe => ({
                name: exById.get(pe._raw.exercise_id)?.name || 'Unknown',
                sets: pe.sets,
                reps: pe.reps,
              })),
          })),
      }));

      const bodyWeight = body
        .filter(b => b.bodyweight != null)
        .map(b => ({ weight: b.bodyweight, date: new Date(b.measuredAt).toISOString() }));

      setData({ history, plans: assembledPlans, settings, bodyWeight, loading: false });
    });
    return () => sub.unsubscribe();
  }, []);

  return data;
}
