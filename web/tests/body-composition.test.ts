import { expect, it } from "vitest";
import { bodyWeightSummary, filterMeasurements, movingAverage, measurementDelta } from "../src/domain/body-composition";
import type { Measurement } from "../src/domain/types";

const weight = (date: string, value: number): Measurement => ({ id: date, date, value, type: "bodyweight", unit: "kg" });

it("sorts bodyweight chronologically and computes a trailing seven-day average", () => {
  const rows = [weight("2026-09-08", 80), weight("2026-09-01", 82), weight("2026-09-07", 81)];
  expect(movingAverage(rows).map((point) => point.average)).toEqual([82, 81.5, 81]);
});

it("computes previous, weekly, monthly, total, and goal changes from canonical kg", () => {
  const now = new Date(2026, 8, 15, 12, 0).getTime();
  const summary = bodyWeightSummary([weight("2026-08-01", 85), weight("2026-08-15", 84), weight("2026-09-08", 82), weight("2026-09-15", 81)], 78, now);
  expect(summary).toMatchObject({ currentKg: 81, previousChangeKg: -1, weeklyChangeKg: -1, monthlyChangeKg: -3, totalChangeKg: -4, toGoalKg: -3 });
});

it("filters measurement ranges and computes latest-to-first deltas", () => {
  const now = new Date(2026, 8, 15, 12, 0).getTime();
  const rows: Measurement[] = [{ id: "old", date: "2026-01-01", type: "waist", value: 90, unit: "cm" }, { id: "new", date: "2026-09-10", type: "waist", value: 84, unit: "cm" }];
  expect(filterMeasurements(rows, 30, now).map((row) => row.id)).toEqual(["new"]);
  expect(measurementDelta(rows)).toBe(-6);
});
