import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import { bootstrap, db, readSnapshot, savePlan, saveProfile, startWorkout } from "../src/data/store";
import { navigateTo } from "../src/ui/navigation";

beforeEach(async () => {
  await db.delete();
  await db.open();
  await bootstrap([]);
  await saveProfile({ onboarded: true });
  history.replaceState(null, "", "#/home");
});
afterEach(cleanup);

it.each(["plan/missing", "workout", "history/missing", "history/new", "calendar", "library", "recovery", "ledger", "body", "measurements", "photos", "analytics", "exercise/Bench", "intelligence", "research"])("gives root detail %s its own shell without tab controls or workout pill", async (route) => {
  await startWorkout(undefined, undefined, "Navigation session");
  history.replaceState(null, "", `#/${route}`);
  render(<App />);
  await screen.findByRole("main");
  await waitFor(() => expect(document.querySelector(".app-content")).toBeInTheDocument());
  expect(screen.queryByRole("navigation", { name: "Main" })).not.toBeInTheDocument();
  expect(document.querySelector(".resume")).not.toBeInTheDocument();
  expect(document.querySelector(".app-shell")).toHaveClass("detail-shell");
  expect((await readSnapshot()).workouts.find((workout) => workout.status === "active")?.name).toBe("Navigation session");
});

it.each(["home", "plans", "log", "stats", "settings"])("keeps tab controls and resumability on %s", async (route) => {
  await startWorkout(undefined, undefined, "Navigation session");
  history.replaceState(null, "", `#/${route}`);
  render(<App />);
  expect(await screen.findByRole("navigation", { name: "Main" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Resume Navigation session" })).toBeInTheDocument();
});

it("keeps a dirty plan mounted when the shell Back is requested", async () => {
  await savePlan({ id: "guard", name: "Saved plan", description: "", goal: "Strength", order: 0, days: [] });
  navigateTo("plans");
  navigateTo("plan/guard");
  render(<App />);
  fireEvent.change(await screen.findByLabelText("Plan name"), { target: { value: "Unsaved name" } });
  fireEvent.click(screen.getByRole("button", { name: "Back" }));
  expect(await screen.findByRole("dialog", { name: "Discard plan edits?" })).toBeInTheDocument();
  expect(screen.getByLabelText("Plan name")).toHaveValue("Unsaved name");
  expect(location.hash).toBe("#/plan/guard");
});
