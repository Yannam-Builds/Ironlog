import { beforeEach, expect, it } from "vitest";
import {
  bootstrap,
  db,
  readSnapshot,
  saveExerciseNextNote,
  readExerciseNextNote,
  startWorkout,
  discardWorkout,
  savePlan,
} from "../src/data/store";
import {
  decodeAndroidBackup,
  encodeAndroidBackup,
  decodeWebBackup,
  encodeWebBackup,
} from "../src/domain/codecs";

beforeEach(async () => {
  await db.delete();
  await db.open();
  await bootstrap([
    {
      id: "bench",
      name: "Bench",
      muscle: "chest",
      equipment: "barbell",
      tracking: "weight_reps",
    },
  ]);
});

it("persists trimmed reminders by library identity across reload, independent of session notes", async () => {
  await saveExerciseNextNote("bench", "  Seat 4 · grip marks  ");
  db.close();
  await db.open();
  expect(await readExerciseNextNote("bench")).toBe("Seat 4 · grip marks");
  expect((await readSnapshot()).profile.exerciseNextNotes).toEqual({
    "exercise_next_note:bench": "Seat 4 · grip marks",
  });
  await saveExerciseNextNote("bench", "  ");
  expect(await readExerciseNextNote("bench")).toBe("");
});

it("rejects unknown exercises and oversized reminders without changing saved data", async () => {
  await saveExerciseNextNote("bench", "Seat 4");
  await expect(saveExerciseNextNote("missing", "note")).rejects.toThrow(
    "library",
  );
  await expect(saveExerciseNextNote("bench", "x".repeat(4001))).rejects.toThrow(
    "4,000",
  );
  expect(await readExerciseNextNote("bench")).toBe("Seat 4");
});

it("round-trips ZIP and canonical native settings without a web extension", async () => {
  await saveExerciseNextNote("bench", "Seat 4");
  const snapshot = await readSnapshot();
  const zipped = await encodeWebBackup(snapshot);
  expect((await decodeWebBackup(zipped)).profile.exerciseNextNotes).toEqual(
    snapshot.profile.exerciseNextNotes,
  );
  const native = JSON.parse(encodeAndroidBackup(snapshot));
  expect(native.data.app_settings).toContainEqual(
    expect.objectContaining({
      key: "exercise_next_note:bench",
      value: "Seat 4",
      value_type: "string",
    }),
  );
  delete native.webExtension;
  expect(
    decodeAndroidBackup(JSON.stringify(native)).snapshot.profile
      .exerciseNextNotes,
  ).toEqual(snapshot.profile.exerciseNextNotes);
  native.webExtension = {
    profile: {
      ...snapshot.profile,
      exerciseNextNotes: { "exercise_next_note:bench": "stale web reminder" },
    },
  };
  expect(
    decodeAndroidBackup(JSON.stringify(native)).snapshot.profile
      .exerciseNextNotes,
  ).toEqual(snapshot.profile.exerciseNextNotes);
});

it("keeps one reminder across different routines without overwriting their exercise notes", async () => {
  for (const id of ["a", "b"])
    await savePlan({
      id,
      name: id,
      description: "",
      goal: "",
      order: 0,
      days: [
        {
          id: `day-${id}`,
          name: "Push",
          color: "red",
          exercises: [
            {
              id: `slot-${id}`,
              exerciseId: "bench",
              name: "Bench",
              sets: 3,
              reps: "8",
              restSeconds: 90,
              notes: `Tempo ${id}`,
              supersetGroup: "",
              isWarmup: false,
            },
          ],
        },
      ],
    });
  const first = await startWorkout("a", "day-a");
  await saveExerciseNextNote("bench", "Seat 4");
  await discardWorkout(first.id);
  const second = await startWorkout("b", "day-b");
  expect(second.exercises[0].notes).toBe("Tempo b");
  expect(await readExerciseNextNote(second.exercises[0].exerciseId)).toBe(
    "Seat 4",
  );
});
