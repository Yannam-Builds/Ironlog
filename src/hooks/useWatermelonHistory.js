/**
 * useWatermelonHistory
 *
 * Replaces AppContext { history, clearHistory, updateHistoryEntry, deleteHistoryEntry }
 * for HistoryScreen with reactive WatermelonDB data.
 *
 * Observes all four relevant collections at once (combineLatest) and joins them
 * in-memory so there's a single subscription for the entire screen.
 */
import { useEffect, useState } from 'react';
import { combineLatest } from 'rxjs';
import { Q } from '@nozbe/watermelondb';
import { database } from '../db/database';
import { mapHistoryFromWatermelonRows } from '../db/adapters/sessionAdapter';
import {
  updateWorkoutMetadata,
  deleteWorkoutWithData,
  clearCompletedWorkouts,
} from '../db/repositories/workoutRepository';

const WORKOUTS = database.get('workouts');
const WORKOUT_EXERCISES = database.get('workout_exercises');
const WORKOUT_SETS = database.get('workout_sets');
const EXERCISES = database.get('exercises');

/** Best-effort dayId from the workout name (for accent colour). */
function inferDayId(name) {
  const lower = (name || '').toLowerCase();
  if (lower.includes('push')) return 'push';
  if (lower.includes('pull')) return 'pull';
  if (lower.includes('leg')) return 'legs';
  if (lower.includes('upper')) return 'upper';
  return null;
}

export default function useWatermelonHistory() {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const sub = combineLatest([
      WORKOUTS.query(Q.where('status', 'completed'), Q.sortBy('started_at', Q.desc)).observe(),
      WORKOUT_EXERCISES.query(Q.on('workouts', Q.where('status', 'completed'))).observe(),
      WORKOUT_SETS.query().observe(),
      EXERCISES.query().observe(),
    ]).subscribe(([workouts, workoutExercises, workoutSets, exercises]) => {
      const mapped = mapHistoryFromWatermelonRows(workouts, workoutExercises, workoutSets, exercises).map((entry) => ({
        ...entry,
        dayId: inferDayId(entry.dayName),
      }));

      setHistory(mapped);
      setLoading(false);
    });

    return () => sub.unsubscribe();
  }, []);

  /**
   * Update workout metadata (date/time, duration, rating, notes) from the
   * HistoryScreen edit modal.
   *
   * Entry shape expected from HistoryScreen:
   *   { id, date (ISO string), duration (seconds), rating, summaryText }
   */
  const updateHistoryEntry = async (entry) => {
    if (!entry?.id) return;
    const startedAt = entry.date ? new Date(entry.date).getTime() : undefined;
    await updateWorkoutMetadata(entry.id, {
      startedAt,
      durationSeconds: entry.duration !== undefined ? Number(entry.duration) : undefined,
      rating: entry.rating !== undefined ? entry.rating : undefined,
      notes: entry.summaryText !== undefined ? entry.summaryText : undefined,
    });
  };

  const deleteHistoryEntry = async (entryOrId) => {
    const id = typeof entryOrId === 'string' ? entryOrId : entryOrId?.id;
    if (!id) return;
    await deleteWorkoutWithData(id);
  };

  const clearHistory = async () => {
    await clearCompletedWorkouts();
  };

  return { history, loading, clearHistory, updateHistoryEntry, deleteHistoryEntry };
}
