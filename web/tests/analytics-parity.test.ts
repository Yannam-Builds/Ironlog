import { expect, it } from "vitest";
import { buildExerciseTrend, exerciseProgressTabs, filterExerciseTrend, buildVolumeAnalytics } from "../src/domain/analytics";
import type { SessionExercise, Workout } from "../src/domain/types";

const set = (id: string, weightKg: number, reps: number, extra: Partial<SessionExercise["loggedSets"][number]> = {}) => ({ id, weightKg, reps, durationSeconds: 0, distanceKm: 0, kind: "normal" as const, notes: "", loggedAt: 0, ...extra });
const exercise = (id: string, name: string, tracking: string, sets: ReturnType<typeof set>[], muscle = "Chest"): SessionExercise => ({ id: `${id}-slot`, exerciseId: id, name, tracking, muscle, equipment: "Barbell", sets: 3, reps: "5", restSeconds: 120, notes: "", supersetGroup: "", isWarmup: false, pendingWarmups: [], loggedSets: sets });
const workout = (id: string, iso: string, exercises: SessionExercise[]): Workout => { const startedAt = new Date(iso).getTime(); return { id, name: "Session", startedAt, completedAt: startedAt + 3_600_000, durationSeconds: 3600, status: "completed", notes: "", restUsed: false, revision: 1, exercises }; };

it("builds native weighted progress rows and excludes warmups", () => {
  const rows = buildExerciseTrend([
    workout("a", "2026-08-01T10:00:00+02:00", [exercise("bench", "Bench Press", "weight_reps", [set("warm", 40, 10, { kind: "warmup" }), set("a", 80, 5), set("b", 75, 8)])]),
    workout("b", "2026-09-01T10:00:00+02:00", [exercise("bench", "Bench Press", "weight_reps", [set("c", 85, 5)])]),
  ], "bench");
  expect(rows).toHaveLength(2);
  expect(rows[0]).toMatchObject({ loadKg: 80, meanReps: 6.5, volumeKg: 1000, workingSets: 2 });
  expect(rows[0].estimatedOneRmKg).toBeCloseTo(95);
  expect(rows[1].isPr).toBe(true);
  expect(exerciseProgressTabs(rows)).toEqual(["E1RM", "LOAD", "REPS", "VOLUME", "CONSISTENCY", "HISTORY"]);
});

it("uses duration tabs for timed work and retains distance totals", () => {
  const rows = buildExerciseTrend([workout("run", "2026-09-01T10:00:00+02:00", [exercise("run", "Run", "duration_distance", [set("r", 0, 0, { durationSeconds: 1800, distanceKm: 5 })], "Cardio")])], "run");
  expect(rows[0]).toMatchObject({ durationSeconds: 1800, distanceKm: 5, loadAvailable: false, volumeAvailable: false });
  expect(exerciseProgressTabs(rows)).toEqual(["DURATION", "CONSISTENCY", "HISTORY"]);
});

it("filters future and out-of-range sessions using the workout start time", () => {
  const now = new Date("2026-09-15T12:00:00+02:00").getTime();
  const rows = buildExerciseTrend([
    workout("old", "2026-05-01T10:00:00+02:00", [exercise("bench", "Bench", "weight_reps", [set("a", 60, 5)])]),
    workout("recent", "2026-09-01T10:00:00+02:00", [exercise("bench", "Bench", "weight_reps", [set("b", 70, 5)])]),
    workout("future", "2026-09-16T10:00:00+02:00", [exercise("bench", "Bench", "weight_reps", [set("c", 80, 5)])]),
  ], "bench");
  expect(filterExerciseTrend(rows, 90, now).map((row) => row.workoutId)).toEqual(["recent"]);
});

it("compares current and previous volume windows with muscle and movement totals", () => {
  const now = new Date("2026-09-15T12:00:00+02:00").getTime();
  const result = buildVolumeAnalytics([
    workout("prior", "2026-08-01T10:00:00+02:00", [exercise("row", "Row", "weight_reps", [set("p", 50, 10)], "Back")]),
    workout("current", "2026-09-01T10:00:00+02:00", [exercise("bench", "Bench", "weight_reps", [set("c", 100, 10)], "Chest")]),
  ], 30, now);
  expect(result).toMatchObject({ sessions: 1, workingSets: 1, totalVolumeKg: 1000, previousVolumeKg: 500, trend: "Progressing" });
  expect(result.muscleSets).toMatchObject({ Chest: 1 });
  expect(result.movementBalance.Push).toBe(100);
});
