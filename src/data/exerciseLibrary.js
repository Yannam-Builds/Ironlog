// Re-exports the Codex-verified v2 exercise library (1,731 exercises).
// The raw JSON is the single source of truth - do not duplicate data here.
import v2Library from './ironlog_exercise_library_v2.json';

export const MUSCLE_GROUPS = v2Library.meta.muscleValues;
export const EXERCISES = v2Library.exercises;
