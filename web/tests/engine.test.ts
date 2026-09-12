import { describe, it, expect } from "vitest";
import {
  defaultProfile,
  type AppSnapshot,
  type Workout,
} from "../src/domain/types";
import {
  creditedProof,
  deriveSnapshot,
  plateCalculation,
  warmupTargets,
  readinessByRegion,
  progressionSuggestion,
  suggestPlanDay,
} from "../src/domain/engine";
import { isoWeekKey, parseHistoryDate } from "../src/domain/dates";
it("rejects impossible calendar dates instead of rolling them forward", () => {
  expect(parseHistoryDate("2026-02-30")).toBeUndefined();
  expect(parseHistoryDate("2025-02-29T10:00:00Z")).toBeUndefined();
  expect(parseHistoryDate("2024-02-29")).toBe(new Date(2024, 1, 29).getTime());
  expect(parseHistoryDate("2026-13-01")).toBeUndefined();
});
const now = new Date(2026, 7, 31, 12).getTime();
const fixture = (
  count = 8,
  weight = 50,
  at = now,
  duration = 1800,
): Workout => ({
  id: String(at),
  name: "Bench",
  startedAt: at - duration * 1000,
  completedAt: at,
  status: "completed",
  notes: "",
  restUsed: false,
  revision: 0,
  exercises: [
    {
      id: "slot",
      exerciseId: "bench",
      name: "Barbell Bench Press",
      sets: count,
      reps: "8",
      restSeconds: 90,
      notes: "",
      supersetGroup: "",
      isWarmup: false,
      tracking: "weight_reps",
      muscle: "chest",
      equipment: "barbell",
      pendingWarmups: [],
      loggedSets: Array.from({ length: count }, (_, i) => ({
        id: String(i),
        weightKg: weight,
        reps: 8,
        durationSeconds: 0,
        distanceKm: 0,
        kind: "normal",
        notes: "",
        loggedAt: at,
      })),
    },
  ],
});
const snap = (workouts: Workout[]): AppSnapshot => ({
  profile: defaultProfile,
  workouts,
  plans: [],
  exercises: [],
  photos: [],
  measurements: [],
  checkins: [],
  gyms: [],
});
describe("native-reference deterministic rules", () => {
  it("credits eight hard sets or three with twenty minutes, not warmup/future", () => {
    expect(creditedProof(fixture(8, 50, now, 60), now)).toBe(true);
    expect(creditedProof(fixture(3), now)).toBe(true);
    expect(creditedProof(fixture(2), now)).toBe(false);
    const future = fixture();
    future.startedAt = now + 1;
    expect(creditedProof(future, now)).toBe(false);
    const w = fixture();
    w.exercises[0].loggedSets.forEach((s) => (s.kind = "warmup"));
    expect(creditedProof(w, now)).toBe(false);
  });
  it("ports Ledger base XP, first-lift baseline, PR bonus and cumulative level curve", () => {
    const a = fixture(8, 50, now - 86400000);
    const b = fixture(8, 60);
    const s = deriveSnapshot(snap([a, b]), now);
    expect(s.xp).toBe(56 + 56 + 35);
    expect(s.level).toBe(2);
    expect(s.nextLevelXp).toBe(500);
    expect(s.levelProgress).toBeCloseTo(22 / 500);
    expect(s.prs[0].oneRmKg).toBe(76);
  });
  it("suppresses daily spam and does not keep stale daily streaks alive", () => {
    const s = deriveSnapshot(snap([fixture(8, 50, now - 86400000 * 4)]), now);
    expect(s.streak).toBe(0);
    const w = [0, 1, 2].map((n) => fixture(8, 50, now - n * 1000));
    expect(deriveSnapshot(snap(w), now).xp).toBe(0);
  });
  it("ports exact barbell bench fractions, warmup exclusion and two-phase recovery", () => {
    const w = fixture();
    w.startedAt = now;
    const r = readinessByRegion([w], [], now);
    expect(r.Push).toBeCloseTo(Math.exp(-(8 * 0.9 * 0.6) / 3.5) * 100, 8);
    expect(r.Core).toBeUndefined();
    expect(readinessByRegion([w], ["Push"], now).Push).toBe(0);
    expect(readinessByRegion([w], [], now + 86400000).Push).toBeGreaterThan(
      r.Push,
    );
  });
  it("uses native workout started-at as the history clock across a week boundary", () => {
    const w = fixture(8, 50, new Date(2026, 7, 31, 0, 10).getTime(), 1800);
    expect(deriveSnapshot(snap([w]), now).weeklyCount).toBe(0);
  });
  it("ports warmup rounding, deduplication and bar minimum", () => {
    expect(warmupTargets(100).map((x) => [x.weightKg, x.reps])).toEqual([
      [40, 8],
      [55, 5],
      [70, 3],
      [85, 1],
    ]);
    expect(warmupTargets(20)).toEqual([]);
    expect(warmupTargets(25).map((x) => x.weightKg)).toEqual([20, 22.5]);
  });
  it("calculates per-side inventory and below-bar remainder", () => {
    expect(
      plateCalculation(100, 20, [20, 15, 10, 5, 2.5, 1.25]).platesPerSide,
    ).toEqual([{ weightKg: 20, quantity: 2 }]);
    expect(plateCalculation(21, 20, [1.25]).remainderKg).toBe(1);
    expect(plateCalculation(10, 20, [20]).isValid).toBe(false);
  });
  it("uses local calendar dates and ISO week year at year boundary", () => {
    expect(isoWeekKey(new Date(2021, 0, 1).getTime())).toBe("2020-W53");
    expect(parseHistoryDate("2026-08-31")).toBe(
      new Date(2026, 7, 31).getTime(),
    );
    expect(parseHistoryDate("nonsense")).toBeUndefined();
  });
  it("uses one weekly recovery make-up to bridge goal minus one, and breaks old streaks", () => {
    const s = snap([
      fixture(8, 50, now - 7 * 86400000),
      fixture(8, 50, now - 6 * 86400000),
    ]);
    s.profile = {
      ...defaultProfile,
      recoveryWeeks: [isoWeekKey(now - 7 * 86400000)],
    };
    expect(deriveSnapshot(s, now).weeklyStreak).toBe(1);
    expect(deriveSnapshot(s, now + 14 * 86400000).weeklyStreak).toBe(0);
  });
  it("ports ghost progression +2.5kg or +5lb and bodyweight +2reps", () => {
    const w = fixture();
    expect(progressionSuggestion(w.exercises[0], "kg")).toMatchObject({
      weightKg: 52.5,
      reps: 8,
    });
    expect(progressionSuggestion(w.exercises[0], "lb")!.weightKg).toBeCloseTo(
      50 + 5 / 2.20462262185,
    );
    w.exercises[0].tracking = "bodyweight_reps";
    w.exercises[0].loggedSets.forEach((s) => (s.weightKg = 0));
    expect(progressionSuggestion(w.exercises[0])).toMatchObject({
      weightKg: 0,
      reps: 10,
    });
  });
  it("suggests a day using token-aware native matching and neutral unknown coverage", () => {
    expect(
      suggestPlanDay({ Shoulders: 20, Pull: 100, Legs: 30 }, [
        ["Lateral Raise"],
        ["Lat Pulldown"],
        ["Hip Abduction"],
      ]),
    ).toBe(1);
    expect(suggestPlanDay({}, [[], []])).toBe(0);
  });
});
