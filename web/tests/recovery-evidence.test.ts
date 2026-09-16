import { expect, it } from "vitest";
import { defaultProfile, type AppSnapshot, type SessionExercise, type Workout } from "../src/domain/types";
import { readinessTrend, recoverySourceLabel, recoverySuggestions, regionRecoveryEvidence } from "../src/domain/recovery-evidence";

const now = Date.UTC(2026, 8, 16, 12);
const exercise = (name: string, muscle: string, sets = 3): SessionExercise => ({ id: name, exerciseId: name, name, tracking: "weight_reps", muscle, equipment: "Barbell", sets, reps: "8", restSeconds: 90, notes: "", supersetGroup: "", isWarmup: false, pendingWarmups: [], loggedSets: Array.from({ length: sets }, (_, index) => ({ id: `${name}-${index}`, weightKg: 50, reps: 8, durationSeconds: 0, distanceKm: 0, kind: "normal", notes: "", loggedAt: now })) });
const workout = (id: string, ageDays: number, exercises: SessionExercise[]): Workout => ({ id, name: id, startedAt: now - ageDays * 86_400_000, completedAt: now - ageDays * 86_400_000 + 3_600_000, durationSeconds: 3600, status: "completed", notes: "", restUsed: false, revision: 1, exercises });
const snapshot = (workouts: Workout[] = []): AppSnapshot => ({ profile: defaultProfile, workouts, exercises: [], plans: [], measurements: [], photos: [], checkins: [], gyms: [] });

it("groups region evidence by exercise and respects the explanatory range", () => {
  const rows = regionRecoveryEvidence([workout("recent", 1, [exercise("Bench Press", "Chest")]), workout("old", 40, [exercise("Old Bench", "Chest")])], "Push", 30, now);
  expect(rows).toMatchObject([{ exerciseName: "Bench Press", sessions: 1, workingSets: 3 }]);
});

it("keeps an expired check-in out of the source label while pain recommendations remain first", () => {
  expect(recoverySourceLabel([], [{ id: "old", at: now - 49 * 3_600_000, sleep: 1, energy: 1, soreness: 5, painRegions: ["Legs"], notes: "knee" }], 30, now)).toBe("No mapped workout evidence");
  expect(recoverySuggestions({ Legs: 95 }, ["Legs"])).toEqual(["Review pain flags before training; avoid painful movements."]);
});

it("represents no-history trend days as gaps instead of fabricated zeroes", () => {
  expect(readinessTrend(snapshot(), now).every((point) => point.score === undefined)).toBe(true);
  expect(readinessTrend(snapshot([workout("recent", 1, [exercise("Squat", "Quads")])]), now).at(-1)?.score).toBeTypeOf("number");
});
