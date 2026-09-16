import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, readSnapshot, saveProfile } from "../src/data/store";
import type { Workout } from "../src/domain/types";

const workout: Workout = { id: "bench-day", name: "Push", startedAt: Date.now() - 86_400_000, completedAt: Date.now() - 82_800_000, durationSeconds: 3600, status: "completed", notes: "", restUsed: false, revision: 1, exercises: [{ id: "bench-slot", exerciseId: "bench", name: "Bench Press", tracking: "weight_reps", muscle: "Chest", equipment: "Barbell", sets: 3, reps: "8", restSeconds: 90, notes: "", supersetGroup: "", isWarmup: false, pendingWarmups: [], loggedSets: [1, 2, 3].map((index) => ({ id: `set-${index}`, weightKg: 80, reps: 8, durationSeconds: 0, distanceKm: 0, kind: "normal", notes: "", loggedAt: Date.now() - 86_400_000 })) }] };

beforeEach(async () => { await db.delete(); await db.open(); await bootstrap([]); await saveProfile({ onboarded: true }); await db.workouts.put(workout); });
afterEach(cleanup);

it("shows range-bound region evidence and an honest readiness trend", async () => {
  history.replaceState(null, "", "#/recovery"); render(<App />);
  expect(await screen.findByRole("button", { name: "30D" })).toHaveAttribute("aria-pressed", "true");
  fireEvent.click(screen.getByRole("button", { name: /Push.*\/ 100|Push.*Building|Push.*Recovering|Push.*Ready/ }));
  expect(screen.getByText(/Source: 1 recent mapped workout/)).toBeVisible();
  expect(screen.getByText(/Bench Press \(1 sessions · 3 working sets\)/)).toBeVisible();
  expect(screen.getByRole("img", { name: "Fourteen day readiness trend" })).toBeVisible();
});

it("persists native score controls, notes and pain that stays visible in recommendations", async () => {
  history.replaceState(null, "", "#/recovery"); render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "How do you feel today?" }));
  fireEvent.click(within(screen.getByRole("radiogroup", { name: "Sleep" })).getByRole("radio", { name: "Sleep: 5" }));
  fireEvent.click(screen.getByRole("checkbox", { name: "Push" }));
  fireEvent.change(screen.getByLabelText("Notes (optional)"), { target: { value: "Shoulder pinch" } });
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Save check-in" })));
  await waitFor(async () => expect((await readSnapshot()).checkins[0]).toMatchObject({ sleep: 5, painRegions: ["Push"], notes: "Shoulder pinch" }));
  expect(await screen.findByText(/Review pain flags before training; avoid painful movements/)).toBeVisible();
  expect(screen.getByRole("button", { name: /Push.*Pain flagged/ })).toBeVisible();
});

it("runs the native choose-start-complete circuit stages once for the eligible week", async () => {
  const goal = (await readSnapshot()).profile.weeklyGoal;
  for (let index = 1; index < goal - 1; index++) await db.workouts.put({ ...workout, id: `session-${index}`, startedAt: workout.startedAt - index * 3_600_000, completedAt: workout.completedAt! - index * 3_600_000 });
  history.replaceState(null, "", "#/ledger"); render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "Open recovery circuit" }));
  fireEvent.click(screen.getByRole("button", { name: /Core Circuit/ }));
  fireEvent.click(screen.getByRole("button", { name: "Start Circuit" }));
  expect(screen.getByRole("heading", { name: "Complete this circuit:" })).toBeVisible();
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Complete & check proof eligibility" })));
  await waitFor(async () => expect((await readSnapshot()).profile.recoveryWeeks).toHaveLength(1));
});
