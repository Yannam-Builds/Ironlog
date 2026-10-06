import { beforeEach, expect, it, vi } from "vitest";
import * as store from "../src/data/store";
import {
  decodeAndroidBackup,
  encodeAndroidBackup,
  decodeWebBackup,
  encodeWebBackup,
} from "../src/domain/codecs";
import type { Exercise } from "../src/domain/types";

const bench: Exercise = {
  id: "bench",
  name: "Bench Press",
  muscle: "Chest",
  equipment: "Barbell",
  tracking: "weight_reps",
};
const custom: Exercise = {
  ...bench,
  id: "custom",
  name: "My Press",
  custom: true,
};
beforeEach(async () => {
  await store.db.delete();
  await store.db.open();
  await store.bootstrap([bench]);
});
it("rejects normalized duplicate custom names even for concurrent creates", async () => {
  await expect(
    store.saveExercise({ ...custom, name: " bench--press " }),
  ).rejects.toThrow(/already exists/i);
  const results = await Promise.allSettled([
    store.saveExercise(custom),
    store.saveExercise({ ...custom, id: "other", name: "MY PRESS" }),
  ]);
  expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
  expect(
    (await store.readSnapshot()).exercises.filter((e) => e.custom),
  ).toHaveLength(1);
  await expect(
    store.saveExercise({ ...custom, id: "blank", name: "   " }),
  ).rejects.toThrow(/name/i);
});
it("atomically changes one favorite without overwriting another tab's changes and round-trips native settings", async () => {
  await store.saveExercise(custom);
  await Promise.all([
    store.setExerciseFavorite(bench.id, true),
    store.setExerciseFavorite(custom.id, true),
  ]);
  await store.setExerciseFavorite(bench.id, false);
  const snapshot = await store.readSnapshot();
  expect(snapshot.profile.favoriteExerciseIds).toEqual([custom.id]);
  expect(
    (await decodeWebBackup(await encodeWebBackup(snapshot))).profile
      .favoriteExerciseIds,
  ).toEqual([custom.id]);
  const native = JSON.parse(encodeAndroidBackup(snapshot));
  expect(
    JSON.parse(
      native.data.app_settings.find((s: any) => s.key === "favorite_exercises")
        .value,
    ),
  ).toEqual([custom.id]);
  delete native.webExtension;
  expect(
    decodeAndroidBackup(JSON.stringify(native)).snapshot.profile
      .favoriteExerciseIds,
  ).toEqual([custom.id]);
});
it("protects bundled, planned and logged references including name-only imported references", async () => {
  await store.saveExercise(custom);
  await expect(store.deleteCustomExercise(bench.id)).rejects.toThrow(
    /built-in/i,
  );
  const plan = {
    id: "p",
    name: "Plan",
    description: "",
    goal: "Strength",
    order: 0,
    days: [
      {
        id: "d",
        name: "Day",
        color: "#f00",
        exercises: [
          {
            id: "slot",
            exerciseId: custom.id,
            name: custom.name,
            sets: 3,
            reps: "10",
            restSeconds: 90,
            notes: "",
            supersetGroup: "",
            isWarmup: false,
          },
        ],
      },
    ],
  };
  await store.savePlan(plan);
  await expect(store.deleteCustomExercise(custom.id)).rejects.toThrow(/plan/i);
  const workout = await store.startWorkout(plan.id, "d");
  await store.db.plans.delete(plan.id);
  await store.db.workouts.update(workout.id, { status: "completed" });
  await expect(store.deleteCustomExercise(custom.id)).rejects.toThrow(
    /workout/i,
  );
  const row = (await store.db.workouts.get(workout.id))!;
  row.exercises[0].exerciseId = "";
  await store.db.workouts.put(row);
  await expect(store.deleteCustomExercise(custom.id)).rejects.toThrow(
    /workout/i,
  );
  expect((await store.readSnapshot()).exercises).toContainEqual(custom);
});
it("deletes restored custom entries without resurrection and rolls back favorite cleanup on failure", async () => {
  await store.db.catalog.put({ id: "restored", exercises: [custom] });
  await store.setExerciseFavorite(custom.id, true);
  const spy = vi
    .spyOn(store.db.profiles, "put")
    .mockRejectedValueOnce(Error("disk failed"));
  await expect(store.deleteCustomExercise(custom.id)).rejects.toThrow(
    "disk failed",
  );
  spy.mockRestore();
  expect((await store.readSnapshot()).exercises).toContainEqual(custom);
  await store.deleteCustomExercise(custom.id);
  await store.bootstrap([bench]);
  const snapshot = await store.readSnapshot();
  expect(snapshot.exercises.some((e) => e.id === custom.id)).toBe(false);
  expect(snapshot.profile.favoriteExerciseIds).toEqual([]);
});
