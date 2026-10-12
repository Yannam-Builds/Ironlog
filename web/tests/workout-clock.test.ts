import { beforeEach, expect, it, vi, afterEach } from "vitest";
import { bootstrap, db, deleteCompletedWorkouts, finishWorkout, mutateWorkout, readSnapshot, startWorkout } from "../src/data/store";
import { workoutElapsedSeconds } from "../src/domain/workout-clock";
beforeEach(async () => { await db.delete(); await db.open(); await bootstrap([]); });
afterEach(() => vi.restoreAllMocks());
it("starts on first log, survives deleting all sets/reload, and persists completion duration", async () => {
  vi.spyOn(Date, "now").mockReturnValue(1_000_000);
  const initial = await startWorkout(); expect(workoutElapsedSeconds(initial, 1_060_000)).toBe(0);
  vi.mocked(Date.now).mockReturnValue(1_120_000);
  const logged = await mutateWorkout(initial.id, initial.revision, w => w.exercises.push({ id: "e", exerciseId: "e", name: "E", muscle: "Chest", equipment: "Barbell", tracking: "weight_reps", sets: 3, reps: "8", restSeconds: 90, notes: "", supersetGroup: "", isWarmup: false, pendingWarmups: [], loggedSets: [{ id: "s", weightKg: 20, reps: 8, durationSeconds: 0, distanceKm: 0, kind: "normal", notes: "", loggedAt: Date.now() }] }));
  expect(logged.timerStartedAt).toBe(1_120_000);
  await mutateWorkout(logged.id, logged.revision, w => { w.exercises[0].loggedSets = []; });
  const persisted = (await readSnapshot()).workouts[0]; expect(workoutElapsedSeconds(persisted, 1_180_000)).toBe(60);
  vi.mocked(Date.now).mockReturnValue(1_240_000);
  expect((await finishWorkout(initial.id)).durationSeconds).toBe(120);
});
it("bulk deletion checks all revisions/statuses before removing anything and preserves active sessions", async () => {
  const first = await startWorkout(); const completed = await finishWorkout(first.id); const active = await startWorkout();
  await expect(deleteCompletedWorkouts([{ id: completed.id, revision: completed.revision }, { id: active.id, revision: active.revision }])).rejects.toThrow();
  expect((await readSnapshot()).workouts).toHaveLength(2);
  await expect(deleteCompletedWorkouts([{ id: completed.id, revision: completed.revision - 1 }])).rejects.toThrow();
  await deleteCompletedWorkouts([{ id: completed.id, revision: completed.revision }]);
  expect((await readSnapshot()).workouts.map(w => w.id)).toEqual([active.id]);
});
