import { beforeEach, expect, it, vi } from "vitest";
import {
  db,
  bootstrap,
  saveGym,
  selectGym,
  deleteGym,
  readSnapshot,
} from "../src/data/store";
const gym = {
  id: "home",
  name: "Home",
  barKg: 20,
  platesKg: [10],
  plateInventory: [{ weightKg: 10, quantity: 4, color: "#1565C0" }],
  unavailableEquipment: ["Cable"],
};
beforeEach(async () => {
  await db.delete();
  await db.open();
  await bootstrap([]);
});
it("updates stock atomically when an active gym is edited", async () => {
  await saveGym(gym, true);
  await saveGym({ ...gym, barKg: 15, plateInventory: [] });
  const snapshot = await readSnapshot();
  expect(snapshot.profile).toMatchObject({
    activeGymId: "home",
    barKg: 15,
    plateInventory: [],
  });
  expect(snapshot.gyms[0].unavailableEquipment).toEqual(["Cable"]);
});
it("rolls back gym creation if the profile write fails", async () => {
  const write = vi
    .spyOn(db.profiles, "put")
    .mockRejectedValueOnce(Error("disk full"));
  await expect(saveGym(gym, true)).rejects.toThrow("disk full");
  write.mockRestore();
  expect(await db.gyms.count()).toBe(0);
  expect((await readSnapshot()).profile.activeGymId).toBeUndefined();
});
it("selects legacy unlimited stock, falls back after deletion and rejects missing selections", async () => {
  await saveGym(gym, true);
  await saveGym({ id: "old", name: "Old", barKg: 15, platesKg: [5] });
  await deleteGym("home");
  expect((await readSnapshot()).profile).toMatchObject({
    activeGymId: "old",
    barKg: 15,
  });
  expect((await readSnapshot()).profile.plateInventory).toBeUndefined();
  await deleteGym("old");
  expect((await readSnapshot()).profile.activeGymId).toBeUndefined();
  await expect(selectGym("missing")).rejects.toThrow("no longer exists");
});
