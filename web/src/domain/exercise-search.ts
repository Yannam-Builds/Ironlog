import type { Exercise } from "./types";
const normalize = (value: string) => value.replace(/([a-z])([A-Z])/g, "$1 $2")
  .toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
/** Native ExerciseUiFilters.queryExerciseSearch relevance tiers. */
export function queryExerciseSearch(exercises: Exercise[], query: string): Exercise[] {
  const q = normalize(query);
  if (!q) return exercises;
  const words = q.split(" ").filter(word => word.length >= 2);
  return exercises.map(exercise => {
    const name = normalize(exercise.name);
    const aliases = normalize((exercise.aliases ?? []).join(" "));
    const haystack = normalize([exercise.name, exercise.muscle,
      ...(exercise.primaryMuscles ?? []), ...(exercise.secondaryMuscles ?? []),
      exercise.equipment, exercise.category ?? "", exercise.movementPattern ?? "", exercise.difficulty ?? ""].join(" "));
    const score = name === q ? 0 : name.startsWith(q) ? 1 : name.includes(q) || aliases.includes(q) ? 2
      : words.length && words.every(word => name.includes(word)) ? 3
      : words.some(word => word.length >= 3 && name.split(" ").some(part => part.startsWith(word))) ? 4
      : words.length && words.every(word => haystack.includes(word)) ? 5
      : words.some(word => word.length >= 3 && haystack.includes(word)) ? 6 : 1000;
    return { exercise, score };
  }).filter(row => row.score < 1000)
    .sort((a, b) => a.score - b.score || a.exercise.name.localeCompare(b.exercise.name))
    .map(row => row.exercise);
}
