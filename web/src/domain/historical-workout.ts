import type { SessionExercise, Workout } from "./types";

export type FoldChoice = "earlier" | "later";
const parts = (date: string, time: string) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  const clock = /^(\d{2}):(\d{2})$/.exec(time);
  if (!match || !clock) throw Error("Choose a valid local date and time.");
  const values = { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]), hour: Number(clock[1]), minute: Number(clock[2]) };
  if (values.month < 1 || values.month > 12 || values.day < 1 || values.day > 31 || values.hour > 23 || values.minute > 59) throw Error("Choose a valid local date and time.");
  return values;
};
const sameLocal = (value: Date, target: ReturnType<typeof parts>) => value.getFullYear() === target.year && value.getMonth() === target.month - 1 && value.getDate() === target.day && value.getHours() === target.hour && value.getMinutes() === target.minute;

/** Resolves browser-local wall time and makes DST fold selection explicit. */
export function resolveLocalDateTime(date: string, time: string, fold: FoldChoice): number {
  const target = parts(date, time);
  const earlier = new Date(target.year, target.month - 1, target.day, target.hour, target.minute, 0, 0);
  if (!sameLocal(earlier, target)) throw Error("That local time does not exist because the clock changes then.");
  const alternate = new Date(earlier.getTime() + 3_600_000);
  const repeated = sameLocal(alternate, target);
  return fold === "later" && repeated ? alternate.getTime() : earlier.getTime();
}

export function localTimeIsRepeated(date: string, time: string) {
  const first = resolveLocalDateTime(date, time, "earlier");
  return resolveLocalDateTime(date, time, "later") !== first;
}

export type HistoricalWorkoutInput = { name: string; date: string; time: string; fold: FoldChoice; durationMinutes: number; rating?: number; notes: string; exercises: SessionExercise[] };
export function buildHistoricalWorkout(input: HistoricalWorkoutInput, now = Date.now()): Workout {
  const name = input.name.trim();
  if (!name) throw Error("Workout name is required.");
  if (!Number.isInteger(input.durationMinutes) || input.durationMinutes < 1 || input.durationMinutes > 1440) throw Error("Duration must be between 1 and 1440 minutes.");
  if (input.rating !== undefined && (!Number.isInteger(input.rating) || input.rating < 1 || input.rating > 5)) throw Error("Rating must be from 1 to 5.");
  const startedAt = resolveLocalDateTime(input.date, input.time, input.fold);
  if (startedAt > now) throw Error("A historical workout cannot start in the future.");
  const durationSeconds = input.durationMinutes * 60;
  if (startedAt + durationSeconds * 1000 > now) throw Error("A historical workout cannot finish in the future.");
  return { id: crypto.randomUUID(), name, startedAt, completedAt: startedAt + durationSeconds * 1000, durationSeconds, status: "completed", exercises: structuredClone(input.exercises), notes: input.notes.trim(), rating: input.rating, restUsed: false, revision: 0, imported: false };
}
