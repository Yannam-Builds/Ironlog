import type { Plan } from "./types";

/** Copy prescriptions, never database identities or references to mutable template arrays. */
export function instantiatePlan(
  source: Plan,
  options: { name?: string; order?: number } = {},
): Plan {
  const copy = structuredClone(source);
  return {
    ...copy,
    ...options,
    id: crypto.randomUUID(),
    days: copy.days.map((day) => ({
      ...day,
      id: crypto.randomUUID(),
      exercises: day.exercises.map((exercise) => ({
        ...exercise,
        id: crypto.randomUUID(),
      })),
    })),
  };
}
