import { useEffect, useState } from 'react';
import { combineLatest } from 'rxjs';
import { Q } from '@nozbe/watermelondb';
import { database } from '../db/database';
import { calculateEstimated1RM } from '../db/repositories/statsRepository';
import { getPrGroupId } from '../services/prLinkingEngine';

const WORKOUTS = database.get('workouts');
const WORKOUT_EXERCISES = database.get('workout_exercises');
const WORKOUT_SETS = database.get('workout_sets');
const EXERCISES = database.get('exercises');
const APP_SETTINGS = database.get('app_settings');

function buildStreak(datestamps) {
  if (!datestamps.length) return 0;
  const days = [...new Set(datestamps)].sort().reverse();
  const today = new Date().toISOString().split('T')[0];
  const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];
  if (days[0] !== today && days[0] !== yesterday) return 0;
  let streak = 0;
  let prev = days[0];
  for (const d of days) {
    const diff = (new Date(prev) - new Date(d)) / 86400000;
    if (diff <= 1) { streak++; prev = d; } else break;
  }
  return streak;
}

export default function useWatermelonStats() {
  const [stats, setStats] = useState({
    history: [],
    pb: {},
    pbGroups: {},
    streak: 0,
    totalSets: 0,
    avgDurationMin: 0,
    chartData: [],
    weightUnit: 'kg',
  });

  useEffect(() => {
    const sub = combineLatest([
      WORKOUTS.query(Q.where('status', 'completed'), Q.sortBy('started_at', Q.desc)).observe(),
      WORKOUT_EXERCISES.query().observe(),
      WORKOUT_SETS.query().observe(),
      EXERCISES.query().observe(),
      APP_SETTINGS.query(Q.where('key', 'ironlog_settings')).observe(),
    ]).subscribe(([workouts, wes, sets, exercises, settingsRows]) => {
      // Weight unit from settings
      let weightUnit = 'kg';
      if (settingsRows.length > 0) {
        try {
          const s = JSON.parse(settingsRows[0].value);
          weightUnit = s?.weightUnit || 'kg';
        } catch (_) {}
      }

      // Lightweight history shape for streak + chart
      const history = workouts.map(w => ({
        date: new Date(w.startedAt).toISOString(),
        duration: w.durationSeconds,
      }));

      const datestamps = history.map(h => h.date.split('T')[0]);
      const streak = buildStreak(datestamps);

      // Set counts
      const weByWorkout = new Map();
      for (const we of wes) {
        const wid = we._raw.workout_id;
        if (!weByWorkout.has(wid)) weByWorkout.set(wid, []);
        weByWorkout.get(wid).push(we.id);
      }
      const completedWorkoutIds = new Set(workouts.map((workout) => workout.id));
      const completedWorkoutExerciseIds = new Set();
      wes.forEach((we) => {
        if (completedWorkoutIds.has(we._raw.workout_id)) {
          completedWorkoutExerciseIds.add(we.id);
        }
      });
      const setsByWE = new Map();
      for (const s of sets) {
        const weid = s._raw.workout_exercise_id;
        if (!setsByWE.has(weid)) setsByWE.set(weid, []);
        setsByWE.get(weid).push(s);
      }

      let totalSets = 0;
      workouts.forEach(w => {
        const weIds = weByWorkout.get(w.id) || [];
        weIds.forEach(weid => { totalSets += (setsByWE.get(weid) || []).length; });
      });

      const avgDurationMin = workouts.length
        ? Math.round(workouts.reduce((a, w) => a + (w.durationSeconds || 0), 0) / workouts.length / 60)
        : 0;

      // 14-day chart
      const dayMap = {};
      datestamps.forEach(d => { dayMap[d] = (dayMap[d] || 0) + 1; });
      const LABEL_INDICES = new Set([0, 4, 9, 13]);
      const chartData = Array.from({ length: 14 }, (_, i) => {
        const d = new Date(Date.now() - (13 - i) * 86400000).toISOString().split('T')[0];
        const [, mm, dd] = d.split('-');
        return { value: dayMap[d] || 0, label: LABEL_INDICES.has(i) ? `${dd}/${mm}` : '' };
      });

      // Personal bests: best estimated 1RM per resolved PR group
      const exById = new Map(exercises.map(e => [e.id, e]));
      const weToEx = new Map(wes.map(we => [we.id, we._raw.exercise_id]));
      const pbByGroup = {};
      sets.forEach(set => {
        if (!completedWorkoutExerciseIds.has(set._raw.workout_exercise_id)) return;
        if (set.isWarmup) return;
        const exId = weToEx.get(set._raw.workout_exercise_id);
        if (!exId) return;
        const ex = exById.get(exId);
        const name = ex?.name;
        if (!name || !ex) return;
        const prGroupId = getPrGroupId(name, {
          primaryMuscle: ex.primaryMuscle,
          equipment: ex.equipment,
          category: ex.category,
        }, 'safe') || `exact_${name}`;
        const orm = calculateEstimated1RM(set.weight, set.reps);
        if (!pbByGroup[prGroupId] || orm > pbByGroup[prGroupId]) pbByGroup[prGroupId] = orm;
      });

      // Backward-compatible map used by existing UI: exercise name -> group PB
      const pb = {};
      exercises.forEach((ex) => {
        const groupId = getPrGroupId(ex.name, {
          primaryMuscle: ex.primaryMuscle,
          equipment: ex.equipment,
          category: ex.category,
        }, 'safe') || `exact_${ex.name}`;
        if (pbByGroup[groupId]) {
          pb[ex.name] = pbByGroup[groupId];
        }
      });

      setStats({ history, pb, pbGroups: pbByGroup, streak, totalSets, avgDurationMin, chartData, weightUnit });
    });
    return () => sub.unsubscribe();
  }, []);

  return stats;
}
