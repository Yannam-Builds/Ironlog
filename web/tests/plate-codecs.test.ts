import { expect, it } from "vitest";
import { encodeAndroidBackup, decodeAndroidBackup } from "../src/domain/codecs";
import { defaultProfile, type AppSnapshot } from "../src/domain/types";
const inventory = [{ weightKg: 20, quantity: 3 }, { weightKg: 2.5, quantity: 2 }];
const snapshot: AppSnapshot = { profile: { ...defaultProfile, plateInventory: inventory }, plans: [], workouts: [], exercises: [], measurements: [], photos: [], checkins: [], gyms: [
  { id: "home", name: "Home", barKg: 20, platesKg: [20, 2.5], plateInventory: inventory },
  { id: "legacy", name: "Legacy", barKg: 20, platesKg: [20] },
] };
it("exports finite named gyms as native per-side quantities without fabricating legacy stock", () => {
  const payload = JSON.parse(encodeAndroidBackup(snapshot));
  const gyms = JSON.parse(payload.data.app_settings.find((s: { key: string }) => s.key === "gym_profiles_json")?.value ?? "[]");
  expect(gyms).toEqual([{ id: "home", name: "Home", barWeightKg: 20, plates: [{ weightKg: 20, quantity: 1 }, { weightKg: 2.5, quantity: 1 }] }]);
  expect(payload.data.app_settings.find((s: { key: string }) => s.key === "active_gym_profile_id")?.value).toBe("home");
  expect(payload.warnings.join(" ")).toMatch(/unlimited/i);
  expect(decodeAndroidBackup(JSON.stringify(payload)).snapshot.gyms).toEqual(snapshot.gyms);
});
it("imports native gym quantities and active setup without a web extension", () => {
  const payload = JSON.parse(encodeAndroidBackup(snapshot));
  delete payload.webExtension;
  payload.data.app_settings = [
    { key: "gym_profiles_json", value: JSON.stringify([{ id: "native", name: "Garage", barWeightKg: 15, plates: [{ weightKg: 10, quantity: 3 }] }]) },
    { key: "active_gym_profile_id", value: "native" },
  ];
  const decoded = decodeAndroidBackup(JSON.stringify(payload)).snapshot;
  expect(decoded.gyms).toEqual([{ id: "native", name: "Garage", barKg: 15, platesKg: [10], plateInventory: [{ weightKg: 10, quantity: 6 }] }]);
  expect(decoded.profile).toMatchObject({ barKg: 15, platesKg: [10], plateInventory: [{ weightKg: 10, quantity: 6 }] });
});
it("rejects corrupt native finite quantities", () => {
  const payload = JSON.parse(encodeAndroidBackup(snapshot)); delete payload.webExtension;
  payload.data.app_settings = [{ key: "gym_profiles_json", value: JSON.stringify([{ id: "bad", name: "Bad", barWeightKg: 20, plates: [{ weightKg: 20, quantity: -1 }] }]) }];
  expect(() => decodeAndroidBackup(JSON.stringify(payload))).toThrow();
});
it("preserves plate colors, exclusions and explicit selection among equal setups", () => {
  const first = { ...snapshot.gyms[0], platesKg: [20], plateInventory: [{ weightKg: 20, quantity: 3, color: "#1565C0" }], unavailableEquipment: ["Cable"] };
  const second = { ...first, id: "second", name: "Second" };
  const payload = JSON.parse(encodeAndroidBackup({ ...snapshot, profile: { ...snapshot.profile, activeGymId: "second", plateInventory: second.plateInventory }, gyms: [first, second] }));
  expect(payload.data.app_settings.find((s: { key: string }) => s.key === "active_gym_profile_id").value).toBe("second");
  const decoded = decodeAndroidBackup(JSON.stringify(payload)).snapshot;
  expect(decoded.gyms).toEqual([first, second]);
  expect(decoded.profile.activeGymId).toBe("second");
  delete payload.webExtension;
  expect(decodeAndroidBackup(JSON.stringify(payload)).snapshot.gyms[0]).toMatchObject({ unavailableEquipment: ["Cable"], plateInventory: [{ weightKg: 20, quantity: 2, color: "#1565C0" }] });
});
it("honors native edits and deletions over stale web gym extensions", () => {
  const payload = JSON.parse(encodeAndroidBackup(snapshot));
  const setting = payload.data.app_settings.find((s: { key: string }) => s.key === "gym_profiles_json");
  setting.value = JSON.stringify([{ id: "home", name: "Updated", barWeightKg: 15, plates: [{ weightKg: 20, quantity: 2, color: "#D32F2F" }], unavailableEquipment: ["Machine"] }]);
  const decoded = decodeAndroidBackup(JSON.stringify(payload)).snapshot;
  expect(decoded.gyms[0]).toMatchObject({ name: "Updated", barKg: 15, plateInventory: [{ weightKg: 20, quantity: 4, color: "#D32F2F" }], unavailableEquipment: ["Machine"] });
  expect(decoded.profile).toMatchObject({ activeGymId: "home", barKg: 15, plateInventory: decoded.gyms[0].plateInventory });
  setting.value = "[]";
  expect(decodeAndroidBackup(JSON.stringify(payload)).snapshot.gyms.map(g => g.id)).toEqual(["legacy"]);
});
