import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { App } from "../src/App";
import {
  bootstrap,
  db,
  readSnapshot,
  saveExercise,
  saveProfile,
  saveGym,
  savePlan,
} from "../src/data/store";
import type { Exercise } from "../src/domain/types";
const bench: Exercise = {
  id: "bench",
  name: "Bench Press",
  muscle: "Chest",
  equipment: "Barbell",
  tracking: "weight_reps",
  secondaryMuscles: ["Triceps"],
  category: "strength",
  difficulty: "beginner",
  movementPattern: "push",
};
const row: Exercise = {
  ...bench,
  id: "row",
  name: "Cable Row",
  muscle: "Back",
  equipment: "Cable",
  secondaryMuscles: [],
  difficulty: "advanced",
  movementPattern: "pull",
};
afterEach(cleanup);
beforeEach(async () => {
  await db.delete();
  await db.open();
  await bootstrap([bench, row]);
  await saveProfile({ onboarded: true });
  window.history.replaceState(null, "", "#/settings");
});
async function openLibrary() {
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: /^Training/ }));
  fireEvent.click(
    screen.getByRole("button", { name: /Exercise library & custom exercises/ }),
  );
  return screen.findByRole("heading", { name: "Exercise library" });
}
it("opens a separate library destination and persists favorites without dismissing the screen", async () => {
  await openLibrary();
  expect(window.location.hash).toBe("#/library");
  fireEvent.click(screen.getByRole("button", { name: "Favorite Bench Press" }));
  await screen.findByRole("button", { name: "Unfavorite Bench Press" });
  expect((await readSnapshot()).profile.favoriteExerciseIds).toEqual(["bench"]);
  fireEvent.click(screen.getByRole("button", { name: "Filters" }));
  fireEvent.click(
    within(screen.getByRole("dialog", { name: "Filters" })).getByRole(
      "button",
      { name: "Favorites" },
    ),
  );
  fireEvent.click(screen.getByRole("button", { name: "Show exercises" }));
  expect(
    screen.getByRole("button", { name: "View progress for Bench Press" }),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "View progress for Cable Row" }),
  ).not.toBeInTheDocument();
});
it("combines secondary muscle and difficulty filters, clears them and respects active gym exclusion", async () => {
  await saveGym(
    {
      id: "gym",
      name: "Gym",
      barKg: 20,
      platesKg: [10],
      unavailableEquipment: ["Cable"],
    },
    true,
  );
  await openLibrary();
  expect(
    screen.queryByRole("button", { name: "View progress for Cable Row" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Filters" }));
  const dialog = screen.getByRole("dialog", { name: "Filters" });
  fireEvent.click(within(dialog).getByRole("button", { name: "Triceps" }));
  fireEvent.click(within(dialog).getByRole("button", { name: "Beginner" }));
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Show exercises" }),
  );
  expect(screen.getByText("1 exercise")).toBeVisible();
  expect(
    screen.getByRole("button", { name: "Remove Triceps filter" }),
  ).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Filters" }));
  fireEvent.click(screen.getByRole("button", { name: "Clear filters" }));
  fireEvent.click(screen.getByRole("button", { name: "Show exercises" }));
  expect(
    screen.queryByRole("button", { name: "Remove Triceps filter" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText("Gym filtered")).toBeVisible();
});
it("reaches results beyond the old 50-row limit and follows native progress navigation", async () => {
  await bootstrap(
    Array.from({ length: 151 }, (_, i) => ({
      ...bench,
      id: `ex-${i}`,
      name: `Exercise ${String(i).padStart(3, "0")}`,
    })),
  );
  await openLibrary();
  expect(screen.getByText("151 exercises")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: /Show more exercises/ }));
  fireEvent.click(
    screen.getByRole("button", { name: "View progress for Exercise 150" }),
  );
  await waitFor(() => expect(window.location.hash).toBe("#/exercise/ex-150"));
  expect(
    await screen.findByRole("heading", {
      name: "No data for this exercise yet",
    }),
  ).toBeVisible();
});
it("confirms custom deletion and keeps the library open, while built-ins have no delete control", async () => {
  await saveExercise({
    ...bench,
    id: "custom",
    name: "My Press",
    custom: true,
  });
  await openLibrary();
  expect(
    screen.queryByRole("button", { name: "Delete Bench Press" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Delete My Press" }));
  expect((await readSnapshot()).exercises.some((e) => e.id === "custom")).toBe(
    true,
  );
  fireEvent.click(screen.getByRole("button", { name: "Delete exercise" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(
    screen.queryByRole("button", { name: "Delete My Press" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "Exercise library" }),
  ).toBeVisible();
});
it("shows referenced deletion failure and retains the confirmation and exercise", async () => {
  await saveExercise({
    ...bench,
    id: "custom",
    name: "My Press",
    custom: true,
  });
  await savePlan({
    id: "p",
    name: "Plan",
    description: "",
    goal: "Strength",
    order: 0,
    days: [
      {
        id: "d",
        name: "Day",
        color: "#f00",
        exercises: [
          {
            id: "slot",
            exerciseId: "custom",
            name: "My Press",
            sets: 3,
            reps: "10",
            restSeconds: 90,
            notes: "",
            supersetGroup: "",
            isWarmup: false,
          },
        ],
      },
    ],
  });
  await openLibrary();
  fireEvent.click(screen.getByRole("button", { name: "Delete My Press" }));
  fireEvent.click(screen.getByRole("button", { name: "Delete exercise" }));
  expect(
    await within(screen.getByRole("dialog")).findByRole("alert"),
  ).toHaveTextContent("used in a plan");
  expect(screen.getByRole("dialog")).toBeVisible();
  expect((await readSnapshot()).exercises.some((e) => e.id === "custom")).toBe(
    true,
  );
});
it("creates an unmatched search as a custom exercise without selecting or closing the library", async () => {
  await openLibrary();
  fireEvent.change(screen.getByLabelText("Search exercises"), {
    target: { value: "Athlete custom press" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Add exercise" }));
  expect(screen.getByLabelText("Exercise name")).toHaveValue(
    "Athlete custom press",
  );
  fireEvent.click(screen.getByRole("button", { name: "Create exercise" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(
    await screen.findByRole("button", { name: "Delete Athlete custom press" }),
  ).toBeVisible();
  expect(
    (await readSnapshot()).exercises.find(
      (e) => e.name === "Athlete custom press",
    )?.custom,
  ).toBe(true);
  expect(window.location.hash).toBe("#/library");
});
it("All scope clears the BW-only restriction as in Kotlin", async () => {
  await openLibrary();
  fireEvent.click(screen.getByRole("button", { name: "Filters" }));
  const dialog = screen.getByRole("dialog", { name: "Filters" });
  fireEvent.click(within(dialog).getByRole("button", { name: "BW Only" }));
  expect(within(dialog).getByRole("button", { name: "All" })).toHaveAttribute("aria-pressed", "false");
  fireEvent.click(
    within(dialog).getByRole("button", { name: "All" }),
  );
  expect(
    within(dialog).getByRole("button", { name: "BW Only" }),
  ).toHaveAttribute("aria-pressed", "false");
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Show exercises" }),
  );
  expect(screen.getByText("2 exercises")).toBeVisible();
});
it("uses the latest completed workout names as recent search chips", async () => {
  await db.workouts.put({
    id: "w",
    name: "Session",
    startedAt: 1,
    completedAt: 2,
    status: "completed",
    notes: "",
    restUsed: false,
    revision: 1,
    exercises: [
      {
        id: "slot",
        exerciseId: bench.id,
        name: bench.name,
        sets: 3,
        reps: "10",
        restSeconds: 90,
        notes: "",
        supersetGroup: "",
        isWarmup: false,
        tracking: "weight_reps",
        muscle: "Chest",
        equipment: "Barbell",
        pendingWarmups: [],
        loggedSets: [],
      },
    ],
  });
  await openLibrary();
  fireEvent.click(
    screen.getByRole("button", { name: "Bench Press" }),
  );
  expect(screen.getByLabelText("Search exercises")).toHaveValue("Bench Press");
  await waitFor(() =>
    expect(
      screen.queryByRole("button", { name: "View progress for Cable Row" }),
    ).not.toBeInTheDocument(),
  );
});

