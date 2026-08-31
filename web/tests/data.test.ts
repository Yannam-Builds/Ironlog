import { beforeEach, describe, expect, it } from "vitest";
import {
  bootstrap,
  db,
  readSnapshot,
  savePlan,
  startWorkout,
  mutateWorkout,
  finishWorkout,
  addWarmups,
  logSet,
  swapExercise,
  restoreSnapshot,
  resetData,
  RevisionConflict,
  editHistory,
  completeRecoveryCircuit,
  reconcileBadges,
  deleteWorkout,
  importPlans,
  acknowledgeFailedMutation,
  hasFailedMutation,
  savePlanIfUnchanged,
  reorderPlans,
  savePhoto,
} from "../src/data/store";
import type { Exercise, Plan, LoggedSet } from "../src/domain/types";
const bench: Exercise = {
  id: "bench",
  name: "Bench Press",
  muscle: "chest",
  equipment: "barbell",
  tracking: "weight_reps",
};
const plan: Plan = {
  id: "plan",
  name: "A",
  goal: "Strength",
  description: "",
  order: 0,
  days: [
    {
      id: "day",
      name: "Push",
      color: "#ff4500",
      exercises: [
        {
          id: "slot",
          exerciseId: "bench",
          name: "Bench Press",
          sets: 3,
          reps: "8",
          restSeconds: 90,
          notes: "tempo",
          supersetGroup: "A",
          isWarmup: false,
        },
      ],
    },
  ],
};
const set: LoggedSet = {
  id: "set",
  weightKg: 60,
  reps: 8,
  durationSeconds: 0,
  distanceKm: 0,
  kind: "normal",
  notes: "",
  loggedAt: Date.now(),
};
it("refuses restore when another tab starts a workout after backup preview", async () => {
  await bootstrap([bench]);
  const preview = await readSnapshot();
  const active = await startWorkout();
  await expect(restoreSnapshot(preview)).rejects.toThrow(/active workout/i);
  expect(
    (await readSnapshot()).workouts.find((w) => w.id === active.id)?.status,
  ).toBe("active");
});
it("stores photo bytes without IndexedDB Blob support and reconstructs legacy photos", async () => {
  await resetData();
  await bootstrap([bench]);
  const bytes = new Uint8Array([1, 2, 3]);
  const photo = {
    id: "photo",
    date: "2026-08-31",
    notes: "synthetic",
    blob: new Blob([bytes], { type: "image/png" }),
  };
  await savePhoto(photo);
  expect(await db.photos.get("photo")).not.toHaveProperty("blob");
  expect(await db.photos.get("photo")).toHaveProperty("bytes");
  const snapshot = await readSnapshot();
  expect(new Uint8Array(await snapshot.photos[0].blob.arrayBuffer())).toEqual(
    bytes,
  );
  await restoreSnapshot(snapshot);
  expect(await db.photos.get("photo")).not.toHaveProperty("blob");
  await db.photos.put({ ...photo, id: "legacy" });
  expect(
    (await readSnapshot()).photos.find((p) => p.id === "legacy")?.blob.type,
  ).toBe("image/png");
});
beforeEach(async () => {
  await resetData();
  await bootstrap([bench]);
  await savePlan(plan);
});
describe("transactional workout repository", () => {
  it("rejects stale concurrent plan editor writes without losing the winning change", async () => {
    const baseline = (await db.plans.get("plan"))!;
    const results = await Promise.allSettled([
      savePlanIfUnchanged({ ...baseline, name: "Editor A" }, baseline),
      savePlanIfUnchanged({ ...baseline, name: "Editor B" }, baseline),
    ]);
    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect(results.find((r) => r.status === "rejected")).toMatchObject({
      reason: { message: expect.stringContaining("changed") },
    });
    expect(["Editor A", "Editor B"]).toContain(
      (await db.plans.get("plan"))!.name,
    );
  });
  it("rejects saving a deleted plan from a stale editor", async () => {
    const baseline = (await db.plans.get("plan"))!;
    await db.plans.delete("plan");
    await expect(
      savePlanIfUnchanged({ ...baseline, name: "Resurrected" }, baseline),
    ).rejects.toThrow("changed");
    expect(await db.plans.get("plan")).toBeUndefined();
  });
  it("reorders all current plans atomically and rejects incomplete or duplicate lists", async () => {
    await savePlan({ ...plan, id: "p2", order: 1 });
    await savePlan({ ...plan, id: "p3", order: 2 });
    await reorderPlans(["p3", "plan", "p2"]);
    expect(
      (await db.plans.orderBy("order").toArray()).map((p) => p.id),
    ).toEqual(["p3", "plan", "p2"]);
    await expect(reorderPlans(["p2", "plan"])).rejects.toThrow();
    await expect(reorderPlans(["p2", "plan", "p2"])).rejects.toThrow();
    expect(
      (await db.plans.orderBy("order").toArray()).map((p) => p.id),
    ).toEqual(["p3", "plan", "p2"]);
  });
  it("creates one durable active session, preserving prescription notes", async () => {
    const [a, b] = await Promise.all([
      startWorkout("plan", "day"),
      startWorkout("plan", "day"),
    ]);
    expect(a.id).toBe(b.id);
    expect(a.exercises[0].notes).toBe("tempo");
    expect((await readSnapshot()).workouts).toHaveLength(1);
  });
  it("rejects stale cross-tab revisions without lost updates", async () => {
    const w = await startWorkout("plan", "day");
    const results = await Promise.allSettled([
      mutateWorkout(w.id, 0, (x) => {
        x.notes = "first";
      }),
      mutateWorkout(w.id, 0, (x) => {
        x.notes = "second";
      }),
    ]);
    expect(results.filter((x) => x.status === "fulfilled")).toHaveLength(1);
    expect(
      (results.find((x) => x.status === "rejected") as PromiseRejectedResult)
        .reason,
    ).toBeInstanceOf(RevisionConflict);
    expect((await db.workouts.get(w.id))?.revision).toBe(1);
  });
  it("finish waits earlier queued writes and is idempotent", async () => {
    const w = await startWorkout("plan", "day");
    const write = mutateWorkout(w.id, 0, (x) => {
      x.exercises[0].loggedSets.push(set);
    });
    const finish = finishWorkout(w.id);
    await write;
    const saved = await finish;
    const twice = await finishWorkout(w.id);
    expect(saved.exercises[0].loggedSets).toHaveLength(1);
    expect(twice).toEqual(saved);
    await expect(
      mutateWorkout(w.id, saved.revision, (x) => {
        x.notes = "late";
      }),
    ).rejects.toThrow("not active");
  });
  it("generated warmups remain pending and explicit logs precede work sets", async () => {
    let w = await startWorkout("plan", "day");
    w = await logSet(w.id, w.revision, "slot", set);
    w = await addWarmups(w.id, w.revision, "slot", [
      { id: "warm", weightKg: 20, reps: 10 },
    ]);
    expect(w.exercises[0].loggedSets).toHaveLength(1);
    w = await logSet(w.id, w.revision, "slot", {
      ...set,
      id: "warm",
      weightKg: 20,
      kind: "warmup",
    });
    expect(w.exercises[0].pendingWarmups).toHaveLength(0);
    expect(w.exercises[0].loggedSets.map((s) => s.kind)).toEqual([
      "warmup",
      "normal",
    ]);
  });
  it("session-only swap leaves plan intact; persistent swap changes both atomically", async () => {
    const row: Exercise = { ...bench, id: "row", name: "Row", muscle: "back" };
    let w = await startWorkout("plan", "day");
    w = await swapExercise(w.id, w.revision, "slot", row, false);
    expect((await db.plans.get("plan"))?.days[0].exercises[0].exerciseId).toBe(
      "bench",
    );
    expect(w.exercises[0].exerciseId).toBe("row");
    w = await swapExercise(w.id, w.revision, "slot", row, true);
    expect((await db.plans.get("plan"))?.days[0].exercises[0].exerciseId).toBe(
      "row",
    );
  });
  it("rejects malformed restore without removing any current data", async () => {
    const before = await readSnapshot();
    await expect(
      restoreSnapshot({ ...before, plans: [{ ...plan, days: null } as never] }),
    ).rejects.toThrow();
    expect((await readSnapshot()).plans).toEqual(before.plans);
  });
  it("edits history with revision checks and persists badges additively after deletion", async () => {
    let w = await startWorkout("plan", "day");
    w = await mutateWorkout(w.id, 0, (x) => {
      x.exercises[0].loggedSets = Array.from({ length: 8 }, (_, i) => ({
        ...set,
        id: String(i),
      }));
    });
    w = await finishWorkout(w.id);
    await reconcileBadges();
    expect(
      (await readSnapshot()).profile.badgeUnlocks.first_workout,
    ).toBeGreaterThan(0);
    w = await editHistory(w.id, w.revision, (x) => {
      x.notes = "edited";
    });
    expect(w.notes).toBe("edited");
    await expect(
      editHistory(w.id, 0, (x) => {
        x.notes = "stale";
      }),
    ).rejects.toBeInstanceOf(RevisionConflict);
    await deleteWorkout(w.id);
    await reconcileBadges();
    expect(
      (await readSnapshot()).profile.badgeUnlocks.first_workout,
    ).toBeGreaterThan(0);
  });
  it("requires exactly goal minus one credited sessions for a weekly recovery circuit", async () => {
    await expect(completeRecoveryCircuit()).rejects.toThrow(
      "one session short",
    );
    for (let i = 0; i < 2; i++) {
      let w = await startWorkout("plan", "day");
      w = await mutateWorkout(w.id, 0, (x) => {
        x.exercises[0].loggedSets = Array.from({ length: 8 }, (_, j) => ({
          ...set,
          id: `${i}-${j}`,
        }));
      });
      await finishWorkout(w.id);
    }
    await completeRecoveryCircuit();
    await expect(completeRecoveryCircuit()).rejects.toThrow("already");
    const s = await readSnapshot();
    expect(s.profile.recoveryWeeks).toHaveLength(1);
    expect(s.workouts).toHaveLength(2);
  });
  it("resolves a normalized name when a plan lacks a library ID", async () => {
    await savePlan({
      ...plan,
      days: plan.days.map((d) => ({
        ...d,
        exercises: d.exercises.map((e) => ({
          ...e,
          exerciseId: "",
          name: "bench-press",
        })),
      })),
    });
    const w = await startWorkout("plan", "day");
    expect(w.exercises[0].exerciseId).toBe("bench");
    expect(w.exercises[0].muscle).toBe("chest");
  });
  it("does not allow unrelated successful writes to acknowledge a failed set", async () => {
    const w = await startWorkout("plan", "day");
    const failed = mutateWorkout(w.id, 0, (x) => {
      x.exercises[0].loggedSets.push({ ...set, weightKg: NaN });
    });
    const finish = finishWorkout(w.id);
    await expect(failed).rejects.toThrow();
    await expect(finish).rejects.toThrow("previous workout change");
    await mutateWorkout(w.id, 0, (x) => {
      x.notes = "unrelated notes";
      x.restEndsAt = Date.now() + 90000;
    });
    await expect(finishWorkout(w.id)).rejects.toThrow(
      "previous workout change",
    );
    expect(hasFailedMutation(w.id)).toBe(true);
    await acknowledgeFailedMutation(w.id);
    expect(hasFailedMutation(w.id)).toBe(false);
    expect((await finishWorkout(w.id)).status).toBe("completed");
  });
  it("imports multiple plans atomically and rejects duplicate nested stable IDs on restore", async () => {
    await expect(
      importPlans([
        { ...plan, id: "new" },
        { ...plan, id: "broken", days: null } as never,
      ]),
    ).rejects.toThrow();
    expect(await db.plans.get("new")).toBeUndefined();
    const snapshot = await readSnapshot();
    snapshot.plans[0].days[0].exercises.push({
      ...snapshot.plans[0].days[0].exercises[0],
    });
    await expect(restoreSnapshot(snapshot)).rejects.toThrow("Duplicate");
    expect((await db.plans.get("plan"))?.days[0].exercises).toHaveLength(1);
  });
});
