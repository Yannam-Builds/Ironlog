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
