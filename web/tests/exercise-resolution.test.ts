import { describe, expect, it } from "vitest";
import { resolveExerciseCandidate } from "../src/domain/exercise-resolution";
import type { Exercise } from "../src/domain/types";

const exercise = (id: string, name: string, extra: Partial<Exercise> = {}): Exercise => ({
  id,
  name,
  muscle: "Chest",
  equipment: "Barbell",
  tracking: "weight_reps",
  ...extra,
});

const library = [
  exercise("bench", "Barbell Bench Press", { aliases: ["Flat Bench"], movementPattern: "push" }),
  exercise("incline", "Incline Dumbbell Press", { equipment: "Dumbbell" }),
  exercise("cable", "Cable Chest Press", { equipment: "Cable" }),
  exercise("row", "Barbell Row", { muscle: "Back" }),
];

describe("native exercise candidate resolution", () => {
  it("matches normalized names and aliases without fuzzy ranking", () => {
    expect(resolveExerciseCandidate("barbell-bench_press", library)).toMatchObject({
      status: "matched",
      matched: { id: "bench" },
      confidence: 100,
      reason: "Exact normalized name",
    });
    expect(resolveExerciseCandidate("flat bench", library)).toMatchObject({
      status: "matched",
      matched: { id: "bench" },
      confidence: 98,
      reason: "Alias exact match",
    });
  });

  it("matches a strong adjacent-swap typo and exposes deterministic candidates", () => {
    const result = resolveExerciseCandidate("barbell benhc press", library);
    expect(result).toMatchObject({
      status: "matched",
      matched: { id: "bench" },
      reason: "High-confidence fuzzy match",
    });
    expect(result.topCandidates[0].exercise.id).toBe("bench");
    expect(result.confidence).toBeGreaterThanOrEqual(88);
  });

  it("keeps a plausible but ambiguous movement for review", () => {
    const result = resolveExerciseCandidate("incline press", library);
    expect(result.status).toBe("needs_review");
    expect(result.matched?.id).toBe("incline");
    expect(result.confidence).toBeGreaterThanOrEqual(52);
    expect(result.confidence).toBeLessThan(88);
  });

  it("does not invent a match for unrelated input", () => {
    const result = resolveExerciseCandidate("underwater basket weaving", library);
    expect(result.status).toBe("unresolved");
    expect(result.confidence).toBeLessThan(52);
    expect(result.topCandidates).toHaveLength(4);
  });
});
