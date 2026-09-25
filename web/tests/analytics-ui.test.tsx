import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, saveProfile } from "../src/data/store";
import type { SessionExercise, Workout } from "../src/domain/types";

const sessionExercise = (id: string, name: string, tracking: string, weightKg: number, reps: number, durationSeconds = 0): SessionExercise => ({ id: `${id}-slot`, exerciseId: id, name, tracking, muscle: tracking.includes("duration") ? "Cardio" : "Chest", equipment: tracking.includes("duration") ? "Bodyweight" : "Barbell", sets: 1, reps: String(reps), restSeconds: 0, notes: "", supersetGroup: "", isWarmup: false, pendingWarmups: [], loggedSets: [{ id: `${id}-set`, weightKg, reps, durationSeconds, distanceKm: durationSeconds ? 5 : 0, kind: "normal", notes: "", loggedAt: Date.now() - 86400000 }] });
const workout = (exercises: SessionExercise[]): Workout => ({ id: crypto.randomUUID(), name: "Test session", startedAt: Date.now() - 86400000, completedAt: Date.now() - 82800000, durationSeconds: 3600, status: "completed", notes: "", restUsed: false, revision: 1, exercises });

beforeEach(async () => { await db.delete(); await db.open(); await bootstrap([]); await saveProfile({ onboarded: true }); });
afterEach(cleanup);

it("shows tracking-aware exercise tabs and training max values", async () => {
  await db.workouts.put(workout([sessionExercise("bench", "Bench Press", "weight_reps", 100, 5)]));
  history.replaceState(null, "", "#/exercise/bench");
  render(<App />);
  expect(await screen.findByRole("tab", { name: "E1RM" })).toBeVisible();
  expect(screen.queryByRole("tab", { name: "DURATION" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Calc TM" }));
  expect(screen.getByText("90% TM")).toBeVisible();
});

it("renders native analytics ranges and opens timed exercise progress", async () => {
  await db.workouts.put(workout([sessionExercise("run", "Run", "duration_distance", 0, 0, 1800)]));
  history.replaceState(null, "", "#/analytics");
  render(<App />);
  expect(await screen.findByRole("heading", { name: "Volume analytics" })).toBeVisible();
  expect(screen.getByRole("button", { name: "30D" })).toHaveAttribute("aria-pressed", "true");
  fireEvent.click(screen.getByRole("button", { name: "Run Open" }));
  expect(await screen.findByRole("tab", { name: "DURATION" })).toBeVisible();
  expect(screen.queryByRole("tab", { name: "E1RM" })).not.toBeInTheDocument();
});
