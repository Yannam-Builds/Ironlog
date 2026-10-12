import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it } from "vitest";
import { bootstrap, db, readSnapshot, reconcileBadges, saveProfile } from "../src/data/store";
import { cloudPresets, setCloudSessionKey } from "../src/domain/cloud-ai";
import { decodeAndroidBackup, encodeAndroidBackup } from "../src/domain/codecs";
import { CloudInsight } from "../src/features/intelligence/CloudInsight";
import { AppProvider } from "../src/ui/context";
import { deriveSnapshot } from "../src/domain/engine";
import type { Workout } from "../src/domain/types";
beforeEach(async () => { await db.delete(); await db.open(); await bootstrap([]); await saveProfile({ onboarded: true }); });
afterEach(() => { cleanup(); setCloudSessionKey(""); });
const now = new Date("2026-10-07T12:00:00Z").getTime();
function proof(id: string, at: number): Workout { return { id, name: "Proof", status: "completed", startedAt: at, completedAt: at + 1000, durationSeconds: 1200, notes: "", revision: 1, restUsed: false, exercises: [{ id: "slot"+id, exerciseId: "bench", name: "Bench", muscle: "Chest", equipment: "Barbell", tracking: "weight_reps", sets: 3, reps: "8", restSeconds: 90, notes: "", supersetGroup: "", isWarmup: false, pendingWarmups: [], loggedSets: Array.from({ length: 3 }, (_, i) => ({ id: id+i, weightKg: 50, reps: 8, durationSeconds: 0, distanceKm: 0, kind: "normal", notes: "", loggedAt: at })) }] }; }
it("counts goal modes only after a new credited workout and retains the durable reward", async () => {
  await saveProfile({ goalMode: "STRENGTH" }); await db.workouts.put(proof("one", now-30000)); await reconcileBadges(now);
  await saveProfile({ goalMode: "HYPERTROPHY" }); await reconcileBadges(now);
  expect((await readSnapshot()).profile.goalModesUsed).toEqual(["strength"]);
  await db.workouts.put(proof("two", now-20000)); await reconcileBadges(now);
  await saveProfile({ goalMode: "GENERAL_FITNESS" }); await db.workouts.put(proof("three", now-10000)); await reconcileBadges(now);
  const earned = (await readSnapshot()).profile;
  expect(earned.badgeUnlocks.all_goal_modes).toBe(now);
  await db.workouts.clear(); await reconcileBadges(now+1000);
  expect((await readSnapshot()).profile.badgeUnlocks.all_goal_modes).toBe(now);
});
it("requires explicit cloud mode and an endpoint-scoped session credential to unlock activation", async () => {
  const config = { ...cloudPresets.OpenAI, model: "test-model" };
  await saveProfile({ cloudConfig: config, intelligenceMode: "cloud_ai" }); await reconcileBadges(now);
  expect((await readSnapshot()).profile.badgeUnlocks.ai_activated).toBeUndefined();
  setCloudSessionKey("synthetic", config); await saveProfile({ intelligenceMode: "built_in" }); await reconcileBadges(now);
  expect((await readSnapshot()).profile.badgeUnlocks.ai_activated).toBeUndefined();
  await saveProfile({ intelligenceMode: "cloud_ai" }); await reconcileBadges(now);
  expect((await readSnapshot()).profile.badgeUnlocks.ai_activated).toBe(now);
  setCloudSessionKey(""); await reconcileBadges(now+1000);
  expect((await readSnapshot()).profile.badgeUnlocks.ai_activated).toBe(now);
});
it("gates cloud panels in built-in mode without erasing portable provider settings", async () => {
  await saveProfile({ cloudConfig: { ...cloudPresets.OpenAI, model: "test-model" }, intelligenceMode: "built_in" });
  const data = await readSnapshot(); render(<AppProvider value={{ data, derived: deriveSnapshot(data), busy: false, run: async () => true }}><CloudInsight title="Recovery advice" prompt="Prompt" /></AppProvider>);
  expect(screen.queryByRole("button", { name: "Ask Cloud AI" })).not.toBeInTheDocument();
  expect(data.profile.cloudConfig?.model).toBe("test-model");
});
it("roundtrips standard native provider keys, coaching mode and goal proof markers", async () => {
  for (const config of Object.values(cloudPresets)) {
    await saveProfile({ cloudConfig: { ...config, model: "test-model" }, intelligenceMode: "built_in", goalModesUsed: ["strength"], lastCreditedGoalWorkoutId: "one" });
    const raw = JSON.parse(encodeAndroidBackup(await readSnapshot()));
    const settings = JSON.parse(raw.data.app_settings.find((row: { key: string }) => row.key === "ironlog_settings").value);
    expect(settings.cloudAiProviderPreset).toBe(config.provider.toLowerCase());
    delete raw.webExtension;
    expect(decodeAndroidBackup(JSON.stringify(raw)).snapshot.profile).toMatchObject({ cloudConfig: { provider: config.provider }, intelligenceMode: "built_in", goalModesUsed: ["strength"], lastCreditedGoalWorkoutId: "one" });
  }
});
