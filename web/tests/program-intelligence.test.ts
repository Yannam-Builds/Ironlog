import { describe, expect, it } from "vitest";
import { computeProgramInsights, resolveProgressionPolicy } from "../src/domain/program-intelligence";
import type { Plan, Workout } from "../src/domain/types";

const rules = { progressionModel: "percent_1rm", blockLengthWeeks: 4, currentWeek: 1, deloadEveryWeeks: 4, percent1RM: 80, rpeTarget: 8, rirTarget: 2 };
it("resolves exercise, plan, global, then conservative progression policy", () => {
  expect(resolveProgressionPolicy("rpe", rules, "aggressive")).toMatchObject({ source: "exercise_override", strategy: "rpe_rir" });
  expect(resolveProgressionPolicy(undefined, rules, "aggressive")).toMatchObject({ source: "plan_rules", label: "80% estimated 1RM" });
  expect(resolveProgressionPolicy(undefined, undefined, "balanced")).toMatchObject({ source: "global_setting", maximumLoadIncreaseRatio: .075 });
  expect(resolveProgressionPolicy()).toMatchObject({ source: "conservative_default" });
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
