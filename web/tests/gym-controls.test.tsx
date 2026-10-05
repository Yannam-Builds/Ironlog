import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import { AppProvider } from "../src/ui/context";
import { deriveSnapshot } from "../src/domain/engine";
import { ExercisePicker } from "../src/features/Plans";
import { GymProfiles } from "../src/features/GymProfiles";
import { PlateView } from "../src/features/Workout";
import {
  bootstrap,
  db,
  readSnapshot,
  saveGym,
  saveProfile,
} from "../src/data/store";
import type { AppSnapshot } from "../src/domain/types";
afterEach(cleanup);
function surface(data: AppSnapshot, children: React.ReactNode) {
  return (
    <AppProvider
      value={{
        data,
        derived: deriveSnapshot(data),
        busy: false,
        run: async (work) => {
          await work();
          return true;
        },
      }}
    >
      {children}
    </AppProvider>
  );
}
it("filters unavailable equipment only while its gym is active", async () => {
  await db.delete();
  await db.open();
  await bootstrap([
    {
      id: "cable",
      name: "Cable row",
      equipment: "cable",
      muscle: "Back",
      tracking: "weight_reps",
    },
    {
      id: "bar",
      name: "Barbell row",
      equipment: "Barbell",
      muscle: "Back",
      tracking: "weight_reps",
    },
  ]);
  await saveGym(
    {
      id: "home",
      name: "Home",
      barKg: 20,
      platesKg: [],
      plateInventory: [],
      unavailableEquipment: ["Cable"],
    },
    true,
  );
  const data = await readSnapshot();
  const view = render(
    surface(data, <ExercisePicker onClose={() => {}} onPick={() => {}} />),
  );
  expect(
    screen.queryByRole("button", { name: /^Cable row/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: /^Barbell row/ }),
  ).toBeInTheDocument();
  view.rerender(
    surface(
      { ...data, profile: { ...data.profile, activeGymId: undefined } },
      <ExercisePicker onClose={() => {}} onPick={() => {}} />,
    ),
  );
  expect(
    screen.getByRole("button", { name: /^Cable row/ }),
  ).toBeInTheDocument();
});
it("editing a pounds profile preserves canonical precision and an odd spare", async () => {
  await db.delete();
  await db.open();
  await bootstrap([]);
  await saveProfile({ unit: "lb" });
  await saveGym(
    {
      id: "home",
      name: "Home",
      barKg: 20.12345,
      platesKg: [10],
      plateInventory: [{ weightKg: 10, quantity: 3 }],
    },
    true,
  );
  render(surface(await readSnapshot(), <GymProfiles onClose={() => {}} />));
  fireEvent.click(screen.getByRole("button", { name: "Edit Home" }));
  expect(screen.getByLabelText("Bar weight (lb)")).toHaveValue(44.36);
  fireEvent.click(screen.getByRole("button", { name: "Add pair of 22.05 lb" }));
  fireEvent.click(screen.getByRole("button", { name: "Save & use setup" }));
  await waitFor(async () =>
    expect((await readSnapshot()).profile.plateInventory?.[0].quantity).toBe(5),
  );
  expect((await readSnapshot()).profile.barKg).toBe(20.12345);
});
it("uses saved custom colors in the visible plate diagram", () => {
  const view = render(
    <PlateView
      loadKg={40}
      barKg={20}
      platesKg={[10]}
      plateInventory={[{ weightKg: 10, quantity: 2, color: "#1565C0" }]}
      unit="kg"
    />,
  );
  expect(view.container.querySelector('rect[fill="#1565C0"]')).not.toBeNull();
});
