import { afterAll, beforeAll, expect, it } from "vitest";
import { buildHistoricalWorkout, resolveLocalDateTime } from "../src/domain/historical-workout";

const originalTimezone = process.env.TZ;
beforeAll(() => { process.env.TZ = "Europe/Warsaw"; });
afterAll(() => {
  if (originalTimezone === undefined) delete process.env.TZ;
  else process.env.TZ = originalTimezone;
});

it("rejects a Warsaw DST gap instead of silently moving the workout", () => {
  expect(() => resolveLocalDateTime("2026-03-29", "02:30", "earlier")).toThrow(/does not exist/);
});

it("offers both instants for a repeated local hour", () => {
  const earlier = resolveLocalDateTime("2026-10-25", "02:30", "earlier");
  const later = resolveLocalDateTime("2026-10-25", "02:30", "later");
  expect(later - earlier).toBe(3_600_000);
});

it("builds completed history without live workout state", () => {
  const workout = buildHistoricalWorkout({ name: "Past session", date: "2026-09-10", time: "18:30", fold: "earlier", durationMinutes: 75, rating: 4, notes: "Good", exercises: [] }, new Date("2026-09-15T12:00:00+02:00").getTime());
  expect(workout).toMatchObject({ status: "completed", durationSeconds: 4500, rating: 4, restUsed: false, revision: 0 });
  expect(workout.completedAt! - workout.startedAt).toBe(4_500_000);
});

it("rejects a session whose duration would finish in the future", () => {
  const now = new Date(2026, 8, 15, 12, 0).getTime();
  expect(() => buildHistoricalWorkout({ name: "Too recent", date: "2026-09-15", time: "11:30", fold: "earlier", durationMinutes: 60, rating: 3, notes: "", exercises: [] }, now)).toThrow(/finish in the future/);
});
