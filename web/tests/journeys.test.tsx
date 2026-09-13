import {
  render,
  act,
  screen,
  fireEvent,
  cleanup,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, saveProfile } from "../src/data/store";
import { startWorkout, readSnapshot, mutateWorkout } from "../src/data/store";
beforeEach(async () => {
  await db.delete();
  await db.open();
  await bootstrap([]);
  window.location.hash = "#/home";
});
afterEach(cleanup);
const sessionExercise = (id: string, name: string) => ({
  id,
  exerciseId: id,
  name,
  sets: 3,
  reps: "8",
  restSeconds: 90,
  notes: "",
  supersetGroup: "",
  isWarmup: false,
  tracking: "weight_reps" as const,
  muscle: "Chest",
  equipment: "Barbell",
  pendingWarmups: [],
  loggedSets: [],
});
it("converts an unlogged weight draft when another tab changes units", async () => {
  await saveProfile({ name: "Test Athlete", onboarded: true });
  const w = await startWorkout();
  await mutateWorkout(w.id, w.revision, (x) =>
    x.exercises.push({
      id: "slot",
      exerciseId: "bench",
      name: "Bench Press",
      sets: 3,
      reps: "8",
      restSeconds: 120,
      notes: "",
      supersetGroup: "",
      isWarmup: false,
      tracking: "weight_reps",
      muscle: "Chest",
      equipment: "Barbell",
      pendingWarmups: [],
      loggedSets: [],
    }),
  );
  window.location.hash = "#/workout";
  render(<App />);
  fireEvent.change(await screen.findByLabelText("KG"), {
    target: { value: "65" },
  });
  await act(async () => {
    await saveProfile({ unit: "lb" });
  });
  await waitFor(() => expect(screen.getByLabelText("LB")).toHaveValue(143.3));
});
it("matches native workout reorder controls and set-type cycling", async () => {
  await saveProfile({ name: "Motion Athlete", onboarded: true });
  const started = await startWorkout();
  await mutateWorkout(started.id, started.revision, (workout) => {
    workout.exercises.push(
      sessionExercise("bench", "Bench Press"),
      sessionExercise("row", "Barbell Row"),
    );
    workout.exercises[0].loggedSets.push({
      id: "working-set",
      weightKg: 80,
      reps: 8,
      durationSeconds: 0,
      distanceKm: 0,
      kind: "normal",
      notes: "",
      loggedAt: Date.now(),
    });
  });
  window.location.hash = "#/workout";
  render(<App />);

  const rowHandle = await screen.findByRole("button", { name: "Drag to reorder Barbell Row" });
  await act(async () => fireEvent.keyDown(rowHandle, { key: "ArrowUp" }));
  await waitFor(async () => {
    expect((await readSnapshot()).workouts[0].exercises.map((exercise) => exercise.id)).toEqual(["row", "bench"]);
  });

  const type = screen.getByRole("button", { name: "Set type for set 1" });
  expect(type).toHaveTextContent("W");
  await waitFor(() => expect(type).toBeEnabled());
  await act(async () => fireEvent.click(type));
  await waitFor(async () => {
    expect((await readSnapshot()).workouts[0].exercises[1].loggedSets[0].kind).toBe("warmup");
  });
  expect(screen.getByLabelText("Workout volume")).toHaveTextContent("Volume:");
});
it.each(["empty", "warmup", "future"] as const)(
  "does not show readiness from %s-only history",
  async (kind) => {
    await saveProfile({ name: "Test Athlete", onboarded: true });
    const w = await startWorkout();
    await db.workouts.update(w.id, {
      status: "completed",
      startedAt: Date.now() + (kind === "future" ? 86400000 : -3600000),
      completedAt: Date.now() + (kind === "future" ? 86400000 : 0),
      exercises:
        kind === "empty"
          ? []
          : [
              {
                id: "slot",
                exerciseId: "bench",
                name: "Bench Press",
                sets: 3,
                reps: "8",
                restSeconds: 120,
                notes: "",
                supersetGroup: "",
                isWarmup: false,
                tracking: "weight_reps",
                muscle: "Chest",
                equipment: "Barbell",
                pendingWarmups: [],
                loggedSets: [
                  {
                    id: "set",
                    weightKg: 65,
                    reps: 8,
                    durationSeconds: 0,
                    distanceKm: 0,
                    kind: kind === "warmup" ? "warmup" : "normal",
                    notes: "",
                    loggedAt: Date.now(),
                  },
                ],
              },
            ],
    });
    render(<App />);
    expect(
      await screen.findByText("No training history yet"),
    ).toBeInTheDocument();
    await act(async () => {
      window.location.hash = "#/recovery";
      window.dispatchEvent(new HashChangeEvent("hashchange"));
    });
    expect(
      await screen.findByRole("heading", { name: "No history yet" }),
    ).toBeInTheDocument();
  },
);
it("restores duration and distance inputs from a timed workout", async () => {
  await saveProfile({ name: "Test Athlete", onboarded: true });
  const w = await startWorkout();
  await mutateWorkout(w.id, w.revision, (x) =>
    x.exercises.push({
      id: "slot",
      exerciseId: "run",
      name: "Treadmill",
      sets: 1,
      reps: "120s",
      restSeconds: 0,
      notes: "",
      supersetGroup: "",
      isWarmup: false,
      tracking: "duration_distance",
      muscle: "Cardio",
      equipment: "Treadmill",
      pendingWarmups: [],
      loggedSets: [
        {
          id: "set",
          weightKg: 0,
          reps: 0,
          durationSeconds: 120,
          distanceKm: 1.5,
          kind: "normal",
          notes: "",
          loggedAt: Date.now(),
        },
      ],
    }),
  );
  window.location.hash = "#/workout";
  render(<App />);
  expect(await screen.findByLabelText("Seconds")).toHaveValue(120);
  expect(screen.getByLabelText("Distance (km)")).toHaveValue(1.5);
});
it("creates and selects a custom exercise during an active workout", async () => {
  await saveProfile({ name: "Test Athlete", onboarded: true });
  await startWorkout();
  window.location.hash = "#/workout";
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "Add exercise" }));
  fireEvent.click(
    screen.getByRole("button", { name: "Create custom exercise" }),
  );
  fireEvent.change(screen.getByLabelText("Exercise name"), {
    target: { value: "QA Custom Cable Press" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Create and select" }));
  await waitFor(async () =>
    expect((await readSnapshot()).workouts[0].exercises[0]?.name).toBe(
      "QA Custom Cable Press",
    ),
  );
});
it("starts with onboarding and no invented rewards", async () => {
  render(<App />);
  expect(await screen.findByText("Make it your own.")).toBeInTheDocument();
  expect(screen.queryByText(/earned/i)).not.toBeInTheDocument();
});
it("shows five native tabs and navigates to settings", async () => {
  await saveProfile({ name: "Test Athlete", onboarded: true });
  render(<App />);
  expect(await screen.findByText("Set your proof loop")).toBeInTheDocument();
  expect(screen.getByRole("navigation", { name: "Main" })).toHaveTextContent(
    "HomePlansLogStatsSettings",
  );
  fireEvent.click(screen.getByRole("link", { name: "Settings" }));
  await waitFor(() =>
    expect(
      screen.getByRole("heading", { name: "Training Console" }),
    ).toBeInTheDocument(),
  );
});
