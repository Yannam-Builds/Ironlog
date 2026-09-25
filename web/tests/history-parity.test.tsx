import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, readSnapshot, saveProfile, startWorkout } from "../src/data/store";

beforeEach(async () => {
  await db.delete();
  await db.open();
  await bootstrap([]);
  await saveProfile({ onboarded: true, name: "History Tester" });
  history.replaceState(null, "", "#/history/new");
});

afterEach(cleanup);

it("logs a past workout without replacing an active session", async () => {
  const active = await startWorkout(undefined, undefined, "Active session");
  render(<App />);

  expect(await screen.findByRole("heading", { name: "Log past workout" })).toBeVisible();
  fireEvent.click(screen.getByRole("radio", { name: "Quick summary" }));
  fireEvent.change(screen.getByLabelText("Workout name"), { target: { value: "Yesterday legs" } });
  fireEvent.change(screen.getByLabelText("Duration minutes"), { target: { value: "75" } });
  fireEvent.change(screen.getByLabelText("Rating (optional)"), { target: { value: "4" } });
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Save past workout" })));

  await waitFor(async () => {
    const workouts = (await readSnapshot()).workouts;
    expect(workouts.find((workout) => workout.id === active.id)?.status).toBe("active");
    expect(workouts.find((workout) => workout.name === "Yesterday legs")).toMatchObject({ status: "completed", durationSeconds: 4500, rating: 4 });
  });
});

it("opens the calendar route from Stats", async () => {
  history.replaceState(null, "", "#/stats");
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: /Calendar/ }));
  expect(await screen.findByRole("heading", { name: "Calendar" })).toBeVisible();
  expect(window.location.hash).toBe("#/calendar");
});
