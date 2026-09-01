import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  bootstrap,
  readSnapshot,
  reconcileBadges,
  resetData,
  saveProfile,
} from "../src/data/store";
import { deriveSnapshot } from "../src/domain/engine";
import {
  defaultProfile,
  type AppSnapshot,
  type Profile,
  type Workout,
} from "../src/domain/types";
import { Onboarding } from "../src/features/Onboarding";
import { Ledger } from "../src/features/Recovery";
import { AppProvider } from "../src/ui/context";

type TrainingSignals = {
  strength: number;
  power: number;
  hypertrophy: number;
  endurance: number;
  agility: number;
  discipline: number;
  recovery: number;
};

type BaselineProfile = Profile & {
  trainingAgeMonths: number;
  historicalTrainingDaysPerWeek: number;
  ledgerBaseline?: {
    version: number;
    provenance: string;
    formula: string;
    trustScore: number;
    estimatedLifetimeSessions: number;
    xp: number;
    grade: string;
    stats: TrainingSignals;
    supportedBadgeIds: string[];
    seededAt: number;
  };
};

type DerivedBaseline = ReturnType<typeof deriveSnapshot> & {
  trainingSignals: TrainingSignals;
  xpBreakdown: { selfReported: number; verified: number };
};

const supportedBadges = [
  "first_workout",
  "workouts_10",
  "workouts_50",
  "workouts_100",
  "consistency_4w",
  "member_365",
];

const expectedSignals: TrainingSignals = {
  strength: 246,
  power: 160,
  hypertrophy: 152,
  endurance: 208,
  agility: 129,
  discipline: 325,
  recovery: 241,
};

const onboardingAnswers = {
  onboarded: true,
  trainingAgeMonths: 19,
  historicalTrainingDaysPerWeek: 4,
  weeklyGoal: 5,
  weightKg: 70,
} as Partial<Profile>;

const snapshotWith = (
  profile: Profile,
  workouts: Workout[] = [],
): AppSnapshot => ({
  profile,
  workouts,
  plans: [],
  exercises: [],
  measurements: [],
  photos: [],
  checkins: [],
  gyms: [],
});

const verifiedWorkout = (at: number, id = "verified-session"): Workout => ({
  id,
  name: "Verified session",
  startedAt: at,
  completedAt: at + 3_600_000,
  durationSeconds: 3_600,
  status: "completed",
  notes: "",
  restUsed: false,
  revision: 1,
  exercises: [
    {
      id: "bench-slot",
      exerciseId: "bench",
      name: "Bench Press",
      sets: 8,
      reps: "8",
      restSeconds: 90,
      notes: "",
      supersetGroup: "",
      isWarmup: false,
      tracking: "weight_reps",
      muscle: "chest",
      equipment: "barbell",
      loggedSets: Array.from({ length: 8 }, (_, index) => ({
        id: `set-${index}`,
        weightKg: 60,
        reps: 8,
        durationSeconds: 0,
        distanceKm: 0,
        kind: "normal" as const,
        notes: "",
        loggedAt: at,
      })),
      pendingWarmups: [],
    },
  ],
});

beforeEach(async () => {
  await resetData();
  await bootstrap([]);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it("persists the deterministic provisional baseline and only inferable badges", async () => {
  await saveProfile(onboardingAnswers);
  const saved = (await readSnapshot()).profile as BaselineProfile;

  expect(saved.ledgerBaseline).toMatchObject({
    version: 1,
    provenance: "onboarding_self_report",
    formula: "trainingAgeMonths*4.345*historicalTrainingDaysPerWeek",
    trustScore: 0.5,
    estimatedLifetimeSessions: 330,
    xp: 6_600,
    grade: "Titanium",
    stats: expectedSignals,
    supportedBadgeIds: supportedBadges,
  });
  expect(saved.badgeUnlocks).toEqual({});

  const derived = deriveSnapshot(snapshotWith(saved), 2_000) as DerivedBaseline;
  expect(derived).toMatchObject({
    xp: 6_600,
    level: 5,
    grade: "Titanium",
    trainingSignals: expectedSignals,
    xpBreakdown: { selfReported: 6_600, verified: 0 },
  });
});

it("recomputes idempotently while preserving independent badges and the original seed timestamp", async () => {
  vi.spyOn(Date, "now").mockReturnValue(1_000);
  await saveProfile(onboardingAnswers);
  const first = (await readSnapshot()).profile as BaselineProfile;

  vi.mocked(Date.now).mockReturnValue(2_000);
  await saveProfile({
    theme: "light",
    badgeUnlocks: { ...first.badgeUnlocks, first_pr: 111 },
  });
  const second = (await readSnapshot()).profile as BaselineProfile;

  expect(second.ledgerBaseline).toEqual(first.ledgerBaseline);
  expect(second.ledgerBaseline?.seededAt).toBe(1_000);
  expect(second.badgeUnlocks).toEqual({ first_pr: 111 });
});

it("subtracts only pre-baseline credited sessions from estimated XP and keeps later proof additive", async () => {
  vi.spyOn(Date, "now").mockReturnValue(1_000_000_000);
  await saveProfile(onboardingAnswers);
  const profile = (await readSnapshot()).profile;
  const before = verifiedWorkout(
    1_000_000_000 - 2 * 86_400_000,
    "before-baseline",
  );
  const after = verifiedWorkout(
    1_000_000_000 + 2 * 86_400_000,
    "after-baseline",
  );
  const now = 1_000_000_000 + 4 * 86_400_000;
  const snapshot = snapshotWith(profile, [before, after]);
  const once = deriveSnapshot(snapshot, now) as DerivedBaseline;
  const twice = deriveSnapshot(snapshot, now) as DerivedBaseline;

  expect(once).toMatchObject({
    xp: 6_692,
    creditedCount: 2,
    grade: "Titanium",
    xpBreakdown: { selfReported: 6_580, verified: 112 },
    trainingSignals: expectedSignals,
  });
  expect(once.onboardingBaseline).toMatchObject({
    seededAt: 1_000_000_000,
    estimatedLifetimeSessions: 330,
    xp: 6_580,
  });
  expect(twice).toEqual(once);

  const afterOnly = deriveSnapshot(snapshotWith(profile, [after]), now);
  expect(afterOnly).toMatchObject({
    xp: 6_656,
    xpBreakdown: { selfReported: 6_600, verified: 56 },
  });
});

it("drops superseded provisional unlocks on a lower retry while preserving independent proof", async () => {
  vi.spyOn(Date, "now").mockReturnValue(1_000);
  await saveProfile(onboardingAnswers);
  const first = (await readSnapshot()).profile as BaselineProfile;

  await saveProfile({
    trainingAgeMonths: 1,
    historicalTrainingDaysPerWeek: 3,
    badgeUnlocks: {
      ...Object.fromEntries(
        supportedBadges.map((id) => [id, first.ledgerBaseline!.seededAt]),
      ),
      first_workout: 222,
      first_pr: 111,
      s_rank: 333,
    },
  });
  const lowered = (await readSnapshot()).profile as BaselineProfile;
  const derived = deriveSnapshot(snapshotWith(lowered), 2_000);

  expect(lowered.ledgerBaseline).toMatchObject({
    seededAt: 1_000,
    estimatedLifetimeSessions: 13,
    supportedBadgeIds: ["first_workout", "workouts_10", "consistency_4w"],
  });
  expect(lowered.badgeUnlocks).toEqual({
    first_workout: 222,
    first_pr: 111,
  });
  expect(derived.unlockedBadges).toEqual(
    expect.arrayContaining(["first_workout", "workouts_10", "first_pr"]),
  );
  expect(derived.unlockedBadges).not.toEqual(
    expect.arrayContaining(["workouts_50", "workouts_100", "s_rank"]),
  );
});

it("keeps provisional badges derived instead of persisting them during reconciliation", async () => {
  vi.spyOn(Date, "now").mockReturnValue(1_000);
  await saveProfile(onboardingAnswers);

  expect((await reconcileBadges(2_000)).sort()).toEqual([]);
  const saved = await readSnapshot();
  expect(saved.profile.badgeUnlocks).toEqual({});
  expect(deriveSnapshot(saved, 2_000).unlockedBadges.sort()).toEqual(
    [...supportedBadges].sort(),
  );
});

it("does not fabricate XP or badges for the zero-history default path", async () => {
  vi.spyOn(Date, "now").mockReturnValue(5_000);
  await saveProfile({
    onboarded: true,
    name: "Guest",
    trainingAgeMonths: 0,
    badgeUnlocks: { s_rank: 123 },
  });
  const saved = await readSnapshot();
  const derived = deriveSnapshot(snapshotWith(saved.profile), 6_000);

  expect(saved.profile.ledgerBaseline).toMatchObject({
    seededAt: 5_000,
    estimatedLifetimeSessions: 0,
    xp: 0,
    supportedBadgeIds: [],
  });
  expect(saved.profile.badgeUnlocks).toEqual({});
  expect(derived).toMatchObject({
    xp: 0,
    creditedCount: 0,
    unlockedBadges: [],
    xpBreakdown: { selfReported: 0, verified: 0 },
  });
});

it("labels provisional provenance and all seven signals in the Iron Ledger", async () => {
  await saveProfile(onboardingAnswers);
  const profile = (await readSnapshot()).profile;
  const data = snapshotWith(profile);
  const derived = deriveSnapshot(data) as DerivedBaseline;
  render(
    <AppProvider value={{ data, derived, busy: false, run: async () => true }}>
      <Ledger />
    </AppProvider>,
  );

  expect(
    screen.getByText("Self-reported onboarding baseline"),
  ).toBeInTheDocument();
  expect(screen.getByText(/provisional profile rank/i)).toBeInTheDocument();
  expect(
    screen.getByText(/330 estimated lifetime sessions/i),
  ).toBeInTheDocument();
  expect(screen.getByText("Training signals")).toBeInTheDocument();
  for (const label of Object.keys(expectedSignals))
    expect(screen.getByText(new RegExp(`^${label}$`, "i"))).toBeInTheDocument();
});

it("previews the estimate honestly before onboarding is committed", () => {
  const profile = {
    ...defaultProfile,
    onboardingStep: 2,
    trainingAgeMonths: 19,
    historicalTrainingDaysPerWeek: 4,
    weeklyGoal: 5,
  } as BaselineProfile;
  const data = snapshotWith(profile);
  render(
    <AppProvider
      value={{
        data,
        derived: deriveSnapshot(data),
        busy: false,
        run: async () => true,
      }}
    >
      <Onboarding />
    </AppProvider>,
  );

  expect(screen.getByText(/self-reported estimate/i)).toBeInTheDocument();
  expect(screen.getByText(/titanium.*provisional/i)).toBeInTheDocument();
  expect(screen.getByText(/6,600 XP/i)).toBeInTheDocument();
  expect(
    screen.getByText(/does not ask for lift or run performance checks/i),
  ).toBeInTheDocument();
});
