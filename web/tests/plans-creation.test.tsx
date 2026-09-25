import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ExercisePicker, Plans } from "../src/features/Plans";
import { Onboarding } from "../src/features/Onboarding";
import { AppProvider, type AppContextValue } from "../src/ui/context";
import {
  bootstrap,
  readSnapshot,
  resetData,
  savePlan,
  saveProfile,
  restoreSnapshot,
} from "../src/data/store";
import { deriveSnapshot } from "../src/domain/engine";
import templates from "../src/generated/templates.json";
import type { Plan } from "../src/domain/types";

beforeEach(async () => {
  await resetData();
  await bootstrap([]);
  window.location.hash = "#/plans";
});
afterEach(cleanup);

it.each(["blank", "template"])(
  "waits for canonical snapshot refresh before navigating after %s plan creation",
  async (kind) => {
    const data = await readSnapshot();
    let finishRefresh!: () => void;
    const refresh = new Promise<void>((resolve) => {
      finishRefresh = resolve;
    });
    let writesDone = false;
    const value: AppContextValue = {
      data,
      derived: deriveSnapshot(data),
      busy: false,
      run: async (work) => {
        await work();
        writesDone = true;
        await refresh;
        return true;
      },
    };
    render(
      <AppProvider value={value}>
        <Plans />
      </AppProvider>,
    );
    if (kind === "blank")
      fireEvent.click(screen.getByRole("button", { name: "New plan" }));
    else {
      fireEvent.click(screen.getByRole("button", { name: "Browse programs" }));
      fireEvent.click(
        screen.getByRole("button", {
          name: new RegExp(`^${templates[0].name}`),
        }),
      );
      fireEvent.click(screen.getByRole("button", { name: "Add to my plans" }));
    }
    await waitFor(() => expect(writesDone).toBe(true));
    expect(window.location.hash).toBe("#/plans");
    await act(async () => {
      finishRefresh();
    });
    await waitFor(() => expect(window.location.hash).toMatch(/^#\/plan\//));
  },
);

it("does not navigate when App.run reports a failed refresh", async () => {
  const data = await readSnapshot();
  const value: AppContextValue = {
    data,
    derived: deriveSnapshot(data),
    busy: false,
    run: async (work) => {
      await work();
      return false;
    },
  };
  render(
    <AppProvider value={value}>
      <Plans />
    </AppProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: "New plan" }));
  await waitFor(async () =>
    expect((await readSnapshot()).plans).toHaveLength(1),
  );
  expect(window.location.hash).toBe("#/plans");
});

it("onboarding assigns fresh nested IDs even if the same starter was previously saved", async () => {
  await savePlan(templates[0] as Plan);
  await saveProfile({ onboardingStep: 9 });
  const data = await readSnapshot();
  const value: AppContextValue = {
    data,
    derived: deriveSnapshot(data),
    busy: false,
    run: async (work) => {
      await work();
      return true;
    },
  };
  render(
    <AppProvider value={value}>
      <Onboarding />
    </AppProvider>,
  );
  fireEvent.change(screen.getByLabelText("Starter program"), {
    target: { value: templates[0].id },
  });
  fireEvent.click(screen.getByRole("button", { name: "Start training" }));
  await waitFor(async () =>
    expect((await readSnapshot()).profile.onboarded).toBe(true),
  );
  const snapshot = await readSnapshot();
  expect(snapshot.plans).toHaveLength(2);
  await expect(restoreSnapshot(snapshot)).resolves.toBeUndefined();
});

it("creates a custom exercise with native metadata and every supported tracking mode", async () => {
  const data = await readSnapshot();
  const onPick = vi.fn();
  const value: AppContextValue = {
    data,
    derived: deriveSnapshot(data),
    busy: false,
    run: async (work) => {
      await work();
      return true;
    },
  };
  render(
    <AppProvider value={value}>
      <ExercisePicker onPick={onPick} onClose={() => undefined} />
    </AppProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: "Create custom exercise" }));
  fireEvent.change(screen.getByLabelText("Exercise name"), { target: { value: "Offset carry" } });
  fireEvent.change(screen.getByLabelText("Tracking"), { target: { value: "duration_weight" } });
  fireEvent.change(screen.getByLabelText("Movement pattern"), { target: { value: "Carry" } });
  fireEvent.change(screen.getByLabelText("Difficulty"), { target: { value: "advanced" } });
  fireEvent.click(screen.getByRole("checkbox", { name: "Shoulders" }));
  fireEvent.click(screen.getByRole("button", { name: "Create and select" }));
  await waitFor(() => expect(onPick).toHaveBeenCalledTimes(1));
  expect(onPick.mock.calls[0][0]).toMatchObject({
    name: "Offset carry",
    tracking: "duration_weight",
    movementPattern: "Carry",
    difficulty: "advanced",
    secondaryMuscles: ["Shoulders"],
    category: "strength",
  });
});
