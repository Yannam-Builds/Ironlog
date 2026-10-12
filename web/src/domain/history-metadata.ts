import { localDateKey } from "./dates";
import { resolveLocalDateTime, type FoldChoice } from "./historical-workout";
import type { Workout } from "./types";
export function historyMetadataDraft(workout: Workout) {
  const at = new Date(workout.startedAt);
  return { name: workout.name, notes: workout.notes, date: localDateKey(workout.startedAt), time: `${String(at.getHours()).padStart(2, "0")}:${String(at.getMinutes()).padStart(2, "0")}`, durationMinutes: (workout.durationSeconds ?? Math.max(0, ((workout.completedAt ?? workout.startedAt) - workout.startedAt) / 1000)) / 60, rating: workout.rating, fold: "earlier" as FoldChoice };
}
export function editHistoryMetadata(workout: Workout, draft: ReturnType<typeof historyMetadataDraft>, now = Date.now()) {
  if (workout.status !== "completed") throw Error("Only completed workouts can be edited here.");
  if (!draft.name.trim()) throw Error("Workout name is required.");
  if (!Number.isFinite(draft.durationMinutes) || draft.durationMinutes < 0 || draft.durationMinutes > 1440) throw Error("Duration must be between 0 and 1440 minutes.");
  if (draft.rating !== undefined && (!Number.isInteger(draft.rating) || draft.rating < 1 || draft.rating > 5)) throw Error("Rating must be from 1 to 5.");
  const original = historyMetadataDraft(workout);
  const clockChanged = draft.date !== original.date || draft.time !== original.time || draft.fold !== original.fold;
  const durationChanged = draft.durationMinutes !== original.durationMinutes;
  const startedAt = clockChanged ? resolveLocalDateTime(draft.date, draft.time, draft.fold) : workout.startedAt;
  const durationSeconds = Math.round(draft.durationMinutes * 60);
  const completedAt = clockChanged || durationChanged ? startedAt + durationSeconds * 1000 : workout.completedAt;
  if ((clockChanged || durationChanged) && (startedAt > now || (completedAt ?? startedAt) > now)) throw Error("A historical workout cannot finish in the future.");
  // Logged sets and their original timestamps remain performed history.
  return { name: draft.name.trim(), notes: draft.notes.trim(), startedAt, completedAt, durationSeconds: durationChanged ? durationSeconds : workout.durationSeconds, rating: draft.rating };
}
