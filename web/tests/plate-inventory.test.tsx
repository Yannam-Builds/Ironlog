import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import { plateCalculation } from "../src/domain/engine";
import { profileSchema, snapshotSchema } from "../src/data/schema";
import { defaultProfile } from "../src/domain/types";
import { PlateView } from "../src/features/Workout";
import { App } from "../src/App";
import { db, bootstrap, saveProfile, readSnapshot } from "../src/data/store";
afterEach(cleanup);
const inventory = [{ weightKg: 20, quantity: 2 }, { weightKg: 2.5, quantity: 2 }];
it("loads 65 kg using one 20 and one 2.5 kg plate on each side", () => {
  expect(plateCalculation(65, 20, [20, 2.5], inventory)).toMatchObject({
    isValid: true, achievedWeightKg: 65,
    platesPerSide: [{ weightKg: 20, quantity: 1 }, { weightKg: 2.5, quantity: 1 }],
  });
});
it("never spends more physical plates than available, including odd quantities", () => {
  expect(plateCalculation(100, 20, [20], [{ weightKg: 20, quantity: 3 }])).toMatchObject({
    isValid: false, achievedWeightKg: 60, remainderKg: 40,
    platesPerSide: [{ weightKg: 20, quantity: 1 }],
  });
  expect(plateCalculation(60, 20, [20], []).achievedWeightKg).toBe(20);
});
it("finds a bounded exact combination where greedy fails", () => {
  expect(plateCalculation(44, 20, [10, 6], [{ weightKg: 10, quantity: 2 }, { weightKg: 6, quantity: 4 }])).toMatchObject({
    isValid: true, platesPerSide: [{ weightKg: 6, quantity: 2 }],
  });
});
it("preserves inventory in profile and gym schemas while accepting legacy omissions", () => {
  expect(profileSchema.parse({ ...defaultProfile, plateInventory: inventory }).plateInventory).toEqual(inventory);
  expect(profileSchema.parse(defaultProfile).plateInventory).toBeUndefined();
  expect(snapshotSchema.shape.gyms.element.parse({ id: "gym", name: "Home", barKg: 20, platesKg: [20], plateInventory: inventory }).plateInventory).toEqual(inventory);
  expect(profileSchema.safeParse({ ...defaultProfile, plateInventory: [{ weightKg: 20, quantity: 1.5 }] }).success).toBe(false);
});
it("labels unlimited legacy assumptions and uses finite inventory in the plate sheet", () => {
  const view = render(<PlateView loadKg={100} barKg={20} platesKg={[20]} unit="kg" />);
  expect(screen.getByText(/unlimited/i)).toBeInTheDocument();
  view.rerender(<PlateView loadKg={100} barKg={20} platesKg={[20]} plateInventory={inventory} unit="kg" />);
  expect(screen.getByText(/Achievable: 65 kg/)).toBeInTheDocument();
  expect(screen.getByText(/physical plates/i)).toBeInTheDocument();
});
it("saves physical quantities in a gym and restores unlimited semantics when selecting an old gym", async () => {
  await db.delete(); await db.open(); await bootstrap([]);
  await saveProfile({ onboarded: true });
  await db.gyms.put({ id: "old", name: "Old gym", barKg: 20, platesKg: [20] });
  window.location.hash = "#/settings";
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: /^Training/ }));
  fireEvent.click(await screen.findByRole("button", { name: /Gym & plate setup/ }));
  fireEvent.change(screen.getByLabelText("Profile name"), { target: { value: "Home" } });
  fireEvent.click(screen.getByLabelText("Limit to my physical plates"));
  fireEvent.change(screen.getByLabelText("Plate sizes (kg, comma separated)"), { target: { value: "20, 2.5" } });
  fireEvent.change(screen.getByLabelText("Total quantities (comma separated)"), { target: { value: "2, 2" } });
  fireEvent.click(screen.getByRole("button", { name: "Save & use setup" }));
  await waitFor(async () => expect((await readSnapshot()).profile.plateInventory).toEqual(inventory));
  const home = (await readSnapshot()).gyms.find(g => g.name === "Home")!;
  expect(home.plateInventory).toEqual(inventory);
  expect((await readSnapshot()).profile.activeGymId).toBe(home.id);
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  fireEvent.click(screen.getByRole("button", { name: /Gym & plate setup/ }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Save & use setup" })).toBeEnabled());
  fireEvent.click(screen.getByRole("button", { name: /^Old gym/ }));
  await waitFor(async () => expect((await readSnapshot()).profile.activeGymId).toBe("old"));
  expect((await readSnapshot()).profile.plateInventory).toBeUndefined();
  fireEvent.click(screen.getByRole("button", { name: "Edit Old gym" }));
  fireEvent.change(screen.getByLabelText("Profile name"), { target: { value: "Renamed gym" } });
  fireEvent.change(screen.getByLabelText("Bar weight (kg)"), { target: { value: "15" } });
  await waitFor(() => expect(screen.getByRole("button", { name: "Save & use setup" })).toBeEnabled());
  fireEvent.click(screen.getByRole("button", { name: "Save & use setup" }));
  await waitFor(async () => {
    const snapshot = await readSnapshot();
    expect(snapshot.gyms.find(g => g.id === "old")).toMatchObject({ name: "Renamed gym", barKg: 15 });
    expect(snapshot.gyms.filter(g => g.id === "old")).toHaveLength(1);
    expect(snapshot.profile.activeGymId).toBe("old");
  });
});


