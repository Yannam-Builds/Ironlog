import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { AIPlan } from "../src/features/plans/AIPlan";
import { AppProvider, type AppContextValue } from "../src/ui/context";
import { bootstrap, readSnapshot, resetData } from "../src/data/store";
import { deriveSnapshot } from "../src/domain/engine";

beforeEach(async () => { await resetData(); await bootstrap([{ id: "bench", name: "Bench Press", muscle: "Chest", equipment: "Barbell", tracking: "weight_reps" }]); });
afterEach(cleanup);

it("keeps the native manual AI flow reviewed and saves only after preview", async () => {
  const data = await readSnapshot();
  const value: AppContextValue = { data, derived: deriveSnapshot(data), busy: false, run: async (work) => { await work(); return true; } };
  render(<AppProvider value={value}><AIPlan onClose={() => undefined} /></AppProvider>);
  fireEvent.click(screen.getByRole("button", { name: "Get started" }));
  fireEvent.change(screen.getByLabelText("Goal"), { target: { value: "Strength" } });
  fireEvent.change(screen.getByLabelText("Limitations or injuries"), { target: { value: "Avoid overhead pressing" } });
  fireEvent.click(screen.getByRole("button", { name: "Build prompt" }));
  expect((screen.getByLabelText("Editable AI prompt") as HTMLTextAreaElement).value).toContain("Avoid overhead pressing");
  fireEvent.click(screen.getByRole("button", { name: "Next: exercise catalog" }));
  fireEvent.click(screen.getByRole("button", { name: "Next: paste response" }));
  fireEvent.change(screen.getByLabelText("AI response JSON"), { target: { value: JSON.stringify({
    type: "ironlog_plan", version: 1, plan: { name: "AI Strength", goal: "Strength", description: "Reviewed", days: [{ name: "Day 1", exercises: [{ exerciseName: "Unknown Press", sets: 3, reps: "5", restSeconds: 120 }] }] },
  }) } });
  fireEvent.click(screen.getByRole("button", { name: "Validate and preview" }));
  expect(screen.getAllByRole("heading", { name: "AI Strength" })).toHaveLength(2);
  expect(screen.getByText(/1 exercises need review/)).toBeInTheDocument();
  expect((await readSnapshot()).plans).toHaveLength(0);
  expect(screen.getByRole("button", { name: "Import reviewed plan" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Match or create exercise" }));
  fireEvent.change(screen.getByLabelText("Exercise name"), { target: { value: "Bench" } });
  fireEvent.click(await screen.findByRole("button", { name: /Bench Press/ }));
  expect(screen.getByRole("button", { name: "Import reviewed plan" })).toBeEnabled();
  fireEvent.click(screen.getByRole("button", { name: "Import reviewed plan" }));
  await waitFor(async () => expect((await readSnapshot()).plans).toHaveLength(1));
});
