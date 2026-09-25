import { beforeEach, describe, expect, it } from "vitest";
import { defaultProfile, type Workout } from "../src/domain/types";
import { deriveSnapshot } from "../src/domain/engine";
import { filterSettingsDestinations } from "../src/domain/settings-console";
import {
  bootstrap,
  clearCompletedHistory,
  db,
  readSnapshot,
  resetPersonalRecords,
  scheduleTutorialRestart,
} from "../src/data/store";

const workout = (id: string, status: Workout["status"], at: number, weightKg = 100): Workout => ({
  id,
  name: id,
  startedAt: at,
  completedAt: status === "completed" ? at + 60_000 : undefined,
  durationSeconds: 60,
  status,
  notes: "",
  restUsed: false,
  revision: 0,
  exercises: [{
    id: `${id}-exercise`, exerciseId: "bench", name: "Bench Press", sets: 1,
    reps: "5", restSeconds: 90, notes: "", supersetGroup: "", isWarmup: false,
    tracking: "weight_reps", muscle: "Chest", equipment: "Barbell",
    pendingWarmups: [], loggedSets: [{
      id: `${id}-set`, weightKg, reps: 5, durationSeconds: 0, distanceKm: 0,
      kind: "normal", notes: "", loggedAt: at,
    }],
  }],
});

describe("native settings console parity", () => {
  it("matches every whitespace-delimited term against titles, descriptions, and keywords", () => {
    expect(filterSettingsDestinations("gym rest").map((item) => item.title)).toEqual(["Training"]);
    expect(filterSettingsDestinations("clear history").map((item) => item.title)).toEqual(["Data & Privacy"]);
    expect(filterSettingsDestinations("font motion").map((item) => item.title)).toEqual(["Appearance"]);
    expect(filterSettingsDestinations("missing destination")).toEqual([]);
  });
});

describe("scoped settings maintenance", () => {
  beforeEach(async () => {
    await db.delete();
    await db.open();
    await bootstrap([]);
  });

  it("clears completed history while preserving active and discarded workouts", async () => {
    await db.workouts.bulkPut([
      workout("completed", "completed", 1_000),
      workout("active", "active", 2_000),
      workout("discarded", "discarded", 3_000),
    ]);
    await clearCompletedHistory();
    expect((await db.workouts.toArray()).map((row) => row.id).sort()).toEqual(["active", "discarded"]);
  });

  it("resets PR baselines without deleting workout history", async () => {
    const old = workout("old", "completed", 1_000, 120);
    const fresh = workout("fresh", "completed", 3_000, 80);
    await db.workouts.bulkPut([old, fresh]);
    await resetPersonalRecords(2_000);
    const snapshot = await readSnapshot();
    expect(snapshot.workouts).toHaveLength(2);
    expect(snapshot.profile.prResetAt).toBe(2_000);
    expect(deriveSnapshot(snapshot, 10_000).prs).toEqual([
      expect.objectContaining({ exerciseId: "bench", weightKg: 80 }),
    ]);
  });

  it("rejects invalid PR reset timestamps", async () => {
    await expect(resetPersonalRecords(0)).rejects.toThrow(/positive/i);
    expect((await readSnapshot()).profile).toMatchObject(defaultProfile);
  });

  it("schedules tutorial restart without immediately changing onboarding or training data", async () => {
    await db.workouts.put(workout("completed", "completed", 1_000));
    await db.profiles.update("local", { onboarded: true, onboardingStep: 10 });
    await scheduleTutorialRestart();
    const snapshot = await readSnapshot();
    expect(snapshot.profile.onboarded).toBe(true);
    expect(snapshot.profile.tutorialRestartPending).toBe(true);
    expect(snapshot.workouts).toHaveLength(1);
  });
});
