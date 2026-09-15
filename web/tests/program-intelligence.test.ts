import { describe, expect, it } from "vitest";
import { buildTrainingIntelligence, computeProgramInsights, resolveProgressionPolicy } from "../src/domain/program-intelligence";
import type { Plan, Workout } from "../src/domain/types";

const rules = { progressionModel: "percent_1rm", blockLengthWeeks: 4, currentWeek: 1, deloadEveryWeeks: 4, percent1RM: 80, rpeTarget: 8, rirTarget: 2 };
it("resolves exercise, plan, global, then conservative progression policy", () => {
  expect(resolveProgressionPolicy("rpe", rules, "aggressive")).toMatchObject({ source: "exercise_override", strategy: "rpe_rir" });
  expect(resolveProgressionPolicy(undefined, rules, "aggressive")).toMatchObject({ source: "plan_rules", label: "80% estimated 1RM" });
  expect(resolveProgressionPolicy(undefined, undefined, "balanced")).toMatchObject({ source: "global_setting", maximumLoadIncreaseRatio: .075 });
  expect(resolveProgressionPolicy()).toMatchObject({ source: "conservative_default" });
});

it("ports weekly volume, PR velocity, performance window and heavy-day fatigue", () => {
  const now = new Date("2026-09-16T12:00:00+02:00").getTime();
  const session = (id: string, iso: string, weight: number): Workout => ({
    id, name: "Bench Press", startedAt: new Date(iso).getTime(), completedAt: new Date(iso).getTime() + 3600000,
    status: "completed", notes: "", restUsed: false, revision: 1,
    exercises: [{ id: `${id}-e`, exerciseId: "bench", name: "Bench Press", sets: 3, reps: "5", restSeconds: 120, notes: "", supersetGroup: "", isWarmup: false, tracking: "weight_reps", muscle: "Chest", equipment: "Barbell", pendingWarmups: [], loggedSets: [0, 1, 2].map((n) => ({ id: `${id}-${n}`, weightKg: weight, reps: 5, durationSeconds: 0, distanceKm: 0, kind: "normal", rpe: 8, notes: "", loggedAt: new Date(iso).getTime() })) }],
  });
  const result = buildTrainingIntelligence([
    session("a", "2026-09-14T10:00:00+02:00", 80), session("b", "2026-09-15T10:00:00+02:00", 82.5), session("c", "2026-09-16T10:00:00+02:00", 85),
  ], { goalMode: "strength", weeklyGoalDays: 3 }, now);
  expect(result.setsByMuscle.Chest).toBe(9);
  expect(result.volumeLandmarks.Chest).toMatchObject({ status: "optimal", min: 8, max: 16 });
  expect(result.movementBalance).toEqual({ Push: 100, Pull: 0, Legs: 0 });
  expect(result).toMatchObject({ prLast30: 2, prPrev30: 0, prTrend: "accelerating", trainingAgeLabel: "Building baseline" });
  expect(result.bestWindow).toContain("Morning leads");
  expect(result.neuralFatigue).toMatchObject({ isFlagged: true, consecutiveDays: 3 });
});

describe("program insights", () => {
  const plan: Plan = { id: "p", name: "P", goal: "Strength", description: "", order: 0, days: [{ id: "a", name: "Push", color: "#f40", exercises: [] }, { id: "b", name: "Pull", color: "#08f", exercises: [] }] };
  const workout = (id: string, dayId: string, startedAt: number): Workout => ({ id, planId: "p", dayId, name: dayId === "a" ? "Push" : "Pull", startedAt, completedAt: startedAt + 3600000, status: "completed", exercises: [], notes: "", restUsed: false, revision: 1 });
  it("matches native rolling-week adherence and stable per-day identity", () => {
    const now = new Date("2026-09-16T12:00:00+02:00").getTime();
    const result = computeProgramInsights(plan, [workout("1", "a", new Date("2026-09-14T10:00:00+02:00").getTime()), workout("2", "b", new Date("2026-09-15T10:00:00+02:00").getTime())], 3, now);
    expect(result).toMatchObject({ weekCount: 1, sessionsPerWeek: 2, adherencePct: 66, consistencyPct: 0 });
    expect(result.perDay.map((day) => day.count)).toEqual([1, 1]);
    expect(result.recommendation).toContain("Stay steady");
  });
});
