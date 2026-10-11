import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useState } from "react";
import { App } from "../src/App";
import { NumberWheel } from "../src/ui/components";
import { bootstrap, db, readSnapshot, savePlan, saveProfile } from "../src/data/store";
import type { Plan } from "../src/domain/types";

const plan: Plan = { id: "home-plan", name: "Full Body Beginner (3x/week)", description: "", goal: "Strength", order: 0,
  days: ["A", "B", "C"].map((id) => ({ id, name: `Full Body ${id}`, color: "", exercises: [{ id: `slot-${id}`, exerciseId: "bench", name: "Bench Press", sets: 3, reps: "8", restSeconds: 90, notes: "", supersetGroup: "", isWarmup: false }] })) };
beforeEach(async () => { await db.delete(); await db.open(); await bootstrap([]); await savePlan(plan); await saveProfile({ onboarded: true, activePlanId: plan.id }); history.replaceState(null, "", "#/home"); });
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it("starts the selected carousel day and keeps the plan title on Home", async () => {
  render(<App />);
  expect(await screen.findByRole("heading", { name: plan.name })).toBeVisible();
  expect(screen.getByRole("button", { name: /Full Body A, 1 exercises, recommended/ })).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Full Body C, 1 exercises" }));
  await waitFor(async () => expect((await readSnapshot()).workouts.find((w) => w.status === "active")?.dayId).toBe("C"));
});

it("asks before discarding and leaves completed history intact", async () => {
  await db.workouts.bulkPut([
    { id: "active", name: "Resumable session", status: "active", startedAt: Date.now() - 600000, exercises: [], notes: "", revision: 1, restUsed: false },
    { id: "done", name: "Past session", status: "completed", startedAt: Date.now() - 86400000, completedAt: Date.now() - 86000000, exercises: [], notes: "", revision: 1, restUsed: false },
  ]);
  render(<App />);
  expect((await screen.findAllByRole("heading", { name: "Resumable session" }))[0]).toBeVisible();
  // Kotlin shows the Daily Proof CTA only for setup/recovery states. The
  // active session owns its single round resume control and main action.
  expect(screen.getAllByRole("button", { name: "Resume workout" })).toHaveLength(1);
  expect(screen.getByLabelText("00:00")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "DISCARD WORKOUT" }));
  expect(await db.workouts.get("active")).toHaveProperty("status", "active");
  fireEvent.click(screen.getByRole("button", { name: "Keep workout" }));
  expect(await db.workouts.get("active")).toHaveProperty("status", "active");
  fireEvent.click(screen.getByRole("button", { name: "DISCARD WORKOUT" }));
  fireEvent.click(screen.getByRole("button", { name: /^Discard workout$/ }));
  await waitFor(async () => expect(await db.workouts.get("active")).toHaveProperty("status", "discarded"));
  expect(await db.workouts.get("done")).toHaveProperty("status", "completed");
});

function Wheel() { const [value, set] = useState(2); return <NumberWheel label="Count" value={value} min={1} max={3} onChange={set} />; }
it("snaps cyclic scrolling while keyboard and numeric entry stay bounded", async () => {
  render(<Wheel />);
  const wheel = screen.getByRole("spinbutton", { name: "Count wheel" });
  expect(wheel.scrollTop).toBe(4 * 48);
  wheel.scrollTop = 6 * 48;
  fireEvent.scroll(wheel);
  await waitFor(() => expect(wheel).toHaveAttribute("aria-valuenow", "1"));
  expect(wheel.scrollTop).toBe(3 * 48);
  fireEvent.keyDown(wheel, { key: "End" });
  fireEvent.keyDown(wheel, { key: "ArrowUp" });
  expect(wheel).toHaveAttribute("aria-valuenow", "3");
  fireEvent.change(screen.getByRole("spinbutton", { name: /^Count$/ }), { target: { value: "999" } });
  fireEvent.blur(screen.getByRole("spinbutton", { name: /^Count$/ }));
  expect(wheel).toHaveAttribute("aria-valuenow", "3");
});
