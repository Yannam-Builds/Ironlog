import { readFileSync } from "node:fs";
import { beforeEach, expect, it } from "vitest";
import { bootstrap, db, resetData, savePlan, startWorkout, readSnapshot } from "../src/data/store";
import { instantiatePlan } from "../src/domain/plans";
import { trackingDimensions } from "../src/domain/tracking";
import templates from "../src/generated/templates.json";
import type { Exercise, Plan } from "../src/domain/types";

const catalog: Exercise[] = JSON.parse(readFileSync("public/data/exercises.json", "utf8"));
beforeEach(async () => { await resetData(); await bootstrap(catalog); });

it("makes every starter-template slot loggable and library linked", async () => {
  for (const template of templates) {
    const plan = instantiatePlan(template as Plan);
    await savePlan(plan);
    for (const day of plan.days) {
      const workout = await startWorkout(plan.id, day.id);
      for (const exercise of workout.exercises) {
        expect(trackingDimensions(exercise).known, exercise.name).toBe(true);
        expect(exercise.exerciseId, exercise.name).not.toBe("");
      }
      await db.workouts.delete(workout.id);
    }
  }
}, 30_000);

it("repairs missing tracking in an existing unlogged local session without touching recorded or imported interpretation", async () => {
  const plan = instantiatePlan(templates[0] as Plan);
  await savePlan(plan);
  const workout = await startWorkout(plan.id, plan.days[0].id);
  workout.exercises.forEach(exercise => { exercise.tracking = ""; });
  workout.exercises[1].loggedSets.push({ id: "recorded", weightKg: 20, reps: 10, durationSeconds: 0, distanceKm: 0, kind: "normal", notes: "", loggedAt: Date.now() });
  await db.workouts.put(workout);
  await bootstrap(catalog);
  const repaired = (await readSnapshot()).workouts[0];
  expect(trackingDimensions(repaired.exercises[0]).known).toBe(true);
  expect(repaired.exercises[1]).toEqual(workout.exercises[1]);
  expect(repaired.revision).toBe(workout.revision + 1);
  repaired.imported = true;
  repaired.exercises[0].tracking = "";
  await db.workouts.put(repaired);
  await bootstrap(catalog);
  expect((await readSnapshot()).workouts[0]).toEqual(repaired);
});
