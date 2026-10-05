import { beforeEach, expect, it, vi } from "vitest";
import { encodeWebBackup, decodeWebBackup } from "../src/domain/codecs";
import {
  db,
  bootstrap,
  saveGym,
  selectGym,
  deleteGym,
  readSnapshot,
  saveProfile,
  saveDefaultBarWeight,
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
it("edits the default bar independently and validates it without changing active gym stock", async () => {
  await saveGym(gym, true);
  await saveDefaultBarWeight(17.5);
  expect((await readSnapshot()).profile.barKg).toBe(20);
  await expect(saveDefaultBarWeight(NaN)).rejects.toThrow("0 and 100");
  await expect(saveDefaultBarWeight(101)).rejects.toThrow("0 and 100");
  await deleteGym(gym.id);
  expect((await readSnapshot()).profile.barKg).toBe(17.5);
  await saveDefaultBarWeight(0);
  expect((await readSnapshot()).profile.barKg).toBe(0);
});
it("uses native defaults for old active profiles whose former base was never stored", async () => {
  await saveGym(gym);
  await saveProfile({ activeGymId: gym.id, barKg: gym.barKg, platesKg: gym.platesKg, plateInventory: gym.plateInventory });
  await deleteGym(gym.id);
  expect((await readSnapshot()).profile).toMatchObject({ barKg: 20, platesKg: [20, 15, 10, 5, 2.5, 1.25] });
  expect((await readSnapshot()).profile.plateInventory).toEqual(
    [20, 15, 10, 5, 2.5, 1.25].map(weightKg => ({ weightKg, quantity: 4 })));
});
it("clears gym stock when a restored base setup has unlimited plates", async () => {
  await saveGym(gym, true);
  await saveProfile({ basePlateSetup: { barKg: 12, platesKg: [3] } });
  await deleteGym(gym.id);
  expect((await readSnapshot()).profile.barKg).toBe(12);
  expect((await readSnapshot()).profile.plateInventory).toBeUndefined();
});
it("restores the base bar and physical stock after switching and deleting every gym", async () => {
  const base = { barKg: 12, platesKg: [3], plateInventory: [{ weightKg: 3, quantity: 3, color: "#F9A825" }] };
  await saveProfile(base);
  await saveGym(gym, true);
  await saveGym({ ...gym, id: "other", barKg: 15 }, true);
  await saveGym({ ...gym, barKg: 25 });
  await deleteGym("other");
  expect((await readSnapshot()).profile.barKg).toBe(25);
  await deleteGym("home");
  const profile = (await readSnapshot()).profile;
  expect(profile.activeGymId).toBeUndefined();
  expect(profile).toMatchObject(base);
});
it("retains the base setup through a browser ZIP and rolls back failed final deletion", async () => {
  await saveProfile({ barKg: 12, platesKg: [3], plateInventory: [] });
  await saveGym(gym, true);
  const snapshot = await readSnapshot();
  expect((await decodeWebBackup(await encodeWebBackup(snapshot))).profile.basePlateSetup)
    .toEqual({ barKg: 12, platesKg: [3], plateInventory: [] });
  const write = vi.spyOn(db.profiles, "put").mockRejectedValueOnce(Error("disk full"));
  await expect(deleteGym(gym.id)).rejects.toThrow("disk full");
  write.mockRestore();
  expect(await db.gyms.count()).toBe(1);
  expect((await readSnapshot()).profile.activeGymId).toBe(gym.id);
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
