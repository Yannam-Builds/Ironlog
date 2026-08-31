import { beforeEach, describe, expect, it } from "vitest";
import Dexie from "dexie";
import {
  bootstrap,
  db,
  IronLogDatabase,
  readExerciseLibrary,
  readSnapshot,
  resetData,
  restoreSnapshot,
  saveExercise,
  savePlan,
  startWorkout,
} from "../src/data/store";
import type { Exercise, Plan } from "../src/domain/types";

const base: Exercise = {
  id: "bench",
  name: "Bundled Bench",
  muscle: "chest",
  equipment: "barbell",
  tracking: "weight_reps",
  aliases: ["BB Bench"],
};
beforeEach(async () => {
  await resetData();
});

describe("single-record bundled exercise catalog", () => {
  it("stores 1731 bundled exercises in one catalog row without per-exercise index writes", async () => {
    const exercises = Array.from({ length: 1731 }, (_, i) => ({
      ...base,
      id: `bundled-${i}`,
      name: `Exercise ${i}`,
    }));
    await bootstrap(exercises);
    expect(await db.exercises.count()).toBe(0);
    expect(await db.catalog.count()).toBe(1);
    expect((await db.catalog.get("bundled"))!.exercises).toHaveLength(1731);
    expect((await readSnapshot()).exercises).toHaveLength(1731);
    await bootstrap(exercises);
    expect(await db.catalog.count()).toBe(1);
    expect(await db.exercises.count()).toBe(0);
  });
  it("gives user overrides precedence and uses the same merged library when starting workouts", async () => {
    await bootstrap([base]);
    await saveExercise({
      ...base,
      name: "My Bench",
      tracking: "bodyweight_reps",
      custom: true,
    });
    await saveExercise({
      ...base,
      id: "custom",
      name: "Custom Move",
      custom: true,
    });
    await bootstrap([{ ...base, name: "Updated Bundled Bench" }]);
    const snapshot = await readSnapshot();
    expect(snapshot.exercises).toHaveLength(2);
    expect(snapshot.exercises.find((e) => e.id === "bench")!.name).toBe(
      "My Bench",
    );
    const plan: Plan = {
      id: "p",
      name: "Plan",
      description: "",
      goal: "Strength",
      order: 0,
      days: [
        {
          id: "d",
          name: "Day",
          color: "#fff",
          exercises: [
            {
              id: "slot",
              exerciseId: "bench",
              name: "Bench",
              sets: 3,
              reps: "8",
              restSeconds: 90,
              notes: "",
              supersetGroup: "",
              isWarmup: false,
            },
          ],
        },
      ],
    };
    await savePlan(plan);
    expect((await startWorkout("p", "d")).exercises[0].tracking).toBe(
      "bodyweight_reps",
    );
  });
  it("upgrades a real v1 database without deleting old bundled or user exercise rows", async () => {
    const name = `ironlog-migration-${crypto.randomUUID()}`;
    const old = new Dexie(name);
    old.version(1).stores({
      profiles: "id",
      plans: "id,order",
      workouts: "id,status,startedAt",
      exercises: "id,name",
      measurements: "id,date,type",
      photos: "id,date",
      checkins: "id,at",
      gyms: "id",
    });
    await old
      .table("exercises")
      .bulkAdd([
        base,
        { ...base, id: "mine", name: "Legacy custom", custom: true },
      ]);
    old.close();
    const migrated = new IronLogDatabase(name);
    try {
      await bootstrap([{ ...base, name: "New bundle" }], migrated);
      expect(migrated.verno).toBe(2);
      expect(await migrated.exercises.count()).toBe(2);
      expect(await migrated.catalog.count()).toBe(1);
      const merged = await readExerciseLibrary(migrated);
      expect(merged.find((e) => e.id === "bench")!.name).toBe("Bundled Bench");
      expect(merged.find((e) => e.id === "mine")!.name).toBe("Legacy custom");
    } finally {
      await migrated.delete();
    }
  });
  it("preserves restored exercise metadata when the bundled catalog refreshes", async () => {
    await bootstrap([base]);
    const snapshot = await readSnapshot();
    snapshot.exercises[0] = {
      ...base,
      name: "Restored Bench",
      aliases: ["Restored Alias"],
      secondaryMuscles: ["triceps"],
      custom: true,
    };
    await restoreSnapshot(snapshot);
    expect(await db.exercises.count()).toBe(0);
    expect((await db.catalog.get("restored"))?.exercises).toHaveLength(1);
    await bootstrap([base]);
    const restored = (await readSnapshot()).exercises;
    expect(restored).toHaveLength(1);
    expect(restored[0]).toMatchObject({
      name: "Restored Bench",
      aliases: ["Restored Alias"],
      secondaryMuscles: ["triceps"],
      custom: true,
    });
  });
});
