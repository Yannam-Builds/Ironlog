import { afterEach, beforeEach, expect, it } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { App } from "../src/App";
import {
  bootstrap,
  db,
  mutateWorkout,
  readExerciseNextNote,
  saveProfile,
  startWorkout,
} from "../src/data/store";
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
  await saveProfile({ onboarded: true });
  const workout = await startWorkout();
  await mutateWorkout(workout.id, workout.revision, (w) => {
    w.exercises.push({
      id: "slot",
      exerciseId: "bench",
      name: "Bench",
      sets: 3,
      reps: "8",
      restSeconds: 90,
      notes: "Session tempo",
      supersetGroup: "",
      isWarmup: false,
      tracking: "weight_reps",
      muscle: "chest",
      equipment: "barbell",
      loggedSets: [],
      pendingWarmups: [],
    });
  });
  window.location.hash = "#/workout";
});
afterEach(cleanup);
it("saves a separate setup reminder and displays it after remount", async () => {
  const view = render(<App />);
  fireEvent.click(await screen.findByText("Add setup reminder"));
  fireEvent.change(screen.getByLabelText("Setup reminder"), {
    target: { value: "Seat 4" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save reminder" }));
  await waitFor(async () =>
    expect(await readExerciseNextNote("bench")).toBe("Seat 4"),
  );
  view.unmount();
  render(<App />);
  expect(await screen.findByText("Seat 4")).toBeVisible();
  expect(screen.getByText("Session tempo")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Edit setup reminder" }));
  fireEvent.click(screen.getByRole("button", { name: "Remove reminder" }));
  await waitFor(async () =>
    expect(await readExerciseNextNote("bench")).toBe(""),
  );
});
