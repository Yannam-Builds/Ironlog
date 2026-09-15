import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { PlanEditor } from "../src/features/Plans";
import { AppProvider, type AppContextValue } from "../src/ui/context";
import { bootstrap, readSnapshot, resetData, savePlan } from "../src/data/store";
import { deriveSnapshot } from "../src/domain/engine";
import type { Plan } from "../src/domain/types";

const plan: Plan = {
  id: "plan-editor-fixture",
  name: "Fixture",
  description: "Plan note",
  goal: "Strength",
  order: 0,
  days: [
    { id: "day-a", name: "Day A", color: "#ff4500", exercises: [
      { id: "pe-a", exerciseId: "bench", name: "Bench Press", sets: 3, reps: "5", restSeconds: 120, notes: "Pause", supersetGroup: "", isWarmup: false },
      { id: "pe-b", exerciseId: "row", name: "Row", sets: 3, reps: "8", restSeconds: 90, notes: "Strict", supersetGroup: "", isWarmup: false },
    ] },
    { id: "day-b", name: "Day B", color: "#0080ff", exercises: [] },
  ],
};

beforeEach(async () => {
  await resetData();
  await bootstrap([]);
  await savePlan(plan);
  window.location.hash = "#/plan/plan-editor-fixture";
});
afterEach(cleanup);

async function renderEditor() {
  const data = await readSnapshot();
  const value: AppContextValue = {
    data,
    derived: deriveSnapshot(data),
    busy: false,
    run: async (work) => { await work(); return true; },
  };
  render(<AppProvider value={value}><PlanEditor id={plan.id} /></AppProvider>);
}

it("moves exercises across days while preserving their stable identity and fields", async () => {
  await renderEditor();
  fireEvent.change(screen.getByLabelText("Move Bench Press to day"), { target: { value: "day-b" } });
  fireEvent.click(screen.getByRole("button", { name: "Save plan" }));
  await waitFor(() => expect(window.location.hash).toBe("#/plans"));
  const saved = (await readSnapshot()).plans[0];
  expect(saved.days[0].exercises.map((exercise) => exercise.id)).toEqual(["pe-b"]);
  expect(saved.days[1].exercises[0]).toMatchObject({ id: "pe-a", notes: "Pause", sets: 3 });
});

it("separates hiding notes from confirmed deletion and guards unsaved close", async () => {
  await renderEditor();
  fireEvent.click(screen.getByRole("button", { name: "Plan note settings" }));
  fireEvent.click(screen.getByRole("checkbox", { name: "Show exercise notes" }));
  expect(screen.queryByLabelText("Exercise notes")).not.toBeInTheDocument();
  expect((await readSnapshot()).profile.planExerciseNotesVisible).toBe(false);

  fireEvent.click(screen.getByRole("button", { name: "Delete all notes in this plan" }));
  fireEvent.click(screen.getByRole("button", { name: "Confirm delete all notes" }));
  expect(screen.getByLabelText("Description")).toHaveValue("");

  fireEvent.change(screen.getByLabelText("Plan name"), { target: { value: "Changed" } });
  fireEvent.click(screen.getByRole("button", { name: "Back to plans" }));
  expect(screen.getByRole("heading", { name: "Discard plan edits?" })).toBeInTheDocument();
  expect(window.location.hash).toBe("#/plan/plan-editor-fixture");
  fireEvent.click(screen.getByRole("button", { name: "Discard and leave" }));
  expect(window.location.hash).toBe("#/plans");
});
