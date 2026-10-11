import { expect, it } from "vitest";
import { editHistoryMetadata, historyMetadataDraft } from "../src/domain/history-metadata";
import type { Workout } from "../src/domain/types";
const workout: Workout = { id: "w", name: "Workout", notes: "", startedAt: new Date(2025, 4, 3, 18, 15, 23).getTime(), completedAt: new Date(2025, 4, 3, 19, 15, 23).getTime(), durationSeconds: 3600, status: "completed", revision: 0, restUsed: false, exercises: [] };
it("preserves exact clocks when only the name/rating changes", () => {
  const result = editHistoryMetadata(workout, { ...historyMetadataDraft(workout), name: "New", rating: 4 });
  expect(result.startedAt).toBe(workout.startedAt); expect(result.completedAt).toBe(workout.completedAt); expect(result.rating).toBe(4);
});
it("edits local clocks and duration together without changing logged history", () => {
  const result = editHistoryMetadata(workout, { ...historyMetadataDraft(workout), time: "17:00", durationMinutes: 30 });
  expect(new Date(result.startedAt).getHours()).toBe(17); expect(result.completedAt! - result.startedAt).toBe(1_800_000);
  expect(result).not.toHaveProperty("exercises");
});
it("rejects invalid rating, durations, nonexistent dates and future completion", () => {
  const draft = historyMetadataDraft(workout);
  for (const change of [{ rating: 6 }, { durationMinutes: -1 }, { durationMinutes: NaN }, { date: "2025-02-30" }, { date: "2099-01-01" }])
    expect(() => editHistoryMetadata(workout, { ...draft, ...change })).toThrow();
});
