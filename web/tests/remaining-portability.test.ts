import { beforeEach, expect, it } from "vitest";
import { decodeLegacyBackup } from "../src/domain/legacy-import";
import { parseCsv, previewExternalImport } from "../src/domain/external-import";
import { decodeAndroidBackup, encodeAndroidBackup, decodeWebBackup, encodeWebBackup } from "../src/domain/codecs";
import { defaultProfile, type AppSnapshot } from "../src/domain/types";
import { calculateOnboardingBaseline } from "../src/domain/onboarding-baseline";
import { appendImportedWorkouts, bootstrap, db, readSnapshot, restoreSnapshot, startWorkout } from "../src/data/store";
const snapshot: AppSnapshot = { profile: { ...defaultProfile, trainingAgeMonths: 24, historicalTrainingDaysPerWeek: 4, hasPastTraining: true, hasGymAccess: false, baselinePushups: 20, baselinePullups: 5, baselineBenchKg: 80, baselineLatPulldownKg: 60, baselineMileRunSeconds: 450, calibrationProvenance: { firstVerifiedSessionAt: 1000, importedHistory: true, confidence: .7, updatedAt: 2000 }, badgeUnlocks: { first_workout: 3000 } }, plans: [], workouts: [], exercises: [], measurements: [], photos: [], checkins: [], gyms: [] };
beforeEach(async () => { await db.delete(); await db.open(); await bootstrap([]); });
it("preserves the fixed baseline clock and calibration provenance across repeated native-format roundtrips", () => {
  const data = { ...snapshot, profile: { ...snapshot.profile, onboarded: true, ledgerBaseline: calculateOnboardingBaseline(snapshot.profile, 1000) } };
  const first = decodeAndroidBackup(encodeAndroidBackup(data)).snapshot;
  const second = decodeAndroidBackup(encodeAndroidBackup(first)).snapshot;
  expect(second.profile.ledgerBaseline?.seededAt).toBe(1000);
  expect(second.profile.calibrationProvenance?.updatedAt).toBe(2000);
});
it("restores full native calibration without depending on a web extension", () => {
  const native = JSON.parse(encodeAndroidBackup(snapshot)); delete native.webExtension;
  const restored = decodeAndroidBackup(JSON.stringify(native)).snapshot.profile;
  expect(restored).toMatchObject({ trainingAgeMonths: 24, historicalTrainingDaysPerWeek: 4, hasPastTraining: true, hasGymAccess: false, baselinePushups: 20, baselinePullups: 5, baselineBenchKg: 80, baselineLatPulldownKg: 60, baselineMileRunSeconds: 450, calibrationProvenance: { firstVerifiedSessionAt: 1000, importedHistory: true, confidence: .7 } });
  expect(restored.ledgerBaseline?.provenance).toBe("onboarding_self_report"); expect(restored.badgeOrigins?.first_workout).toBe("native_import");
  expect(restored.onboarded).toBe(true);
});
it("keeps portable provider configuration but excludes credentials from both backup formats", async () => {
  const data = { ...snapshot, profile: { ...snapshot.profile, cloudConfig: { provider: "Custom", baseUrl: "https://example.com/v1", model: "model", format: "openai" as const }, nativeSettings: { cloudAiApiKey: "secret", nested: { authorization: "secret", accent: "orange" }, array: [{ token: "secret", theme: "dark" }], ironlog_settings: JSON.stringify({ cloudAiApiKey: "secret", theme: "dark" }) } } };
  const android = encodeAndroidBackup(data); expect(android).not.toContain("secret");
  expect(decodeAndroidBackup(android).snapshot.profile.cloudConfig?.model).toBe("model");
  const web = await decodeWebBackup(await encodeWebBackup(data)); expect(JSON.stringify(web.profile)).not.toContain("secret");
});
it("uses updated canonical calibration, badges and recovery credit over stale extensions", async () => {
  const native = JSON.parse(encodeAndroidBackup(snapshot));
  native.data.athlete_calibrations[0].baseline_pushups = 42;
  native.data.athlete_calibrations[0].training_age_months = 0;
  native.webExtension.profile.ledgerBaseline = { ...(decodeAndroidBackup(JSON.stringify(native)).snapshot.profile.ledgerBaseline), seededAt: 1000, supportedBadgeIds: ["first_workout"] };
  native.data.gamification_profiles[0].badge_unlocks_json = JSON.stringify({ comeback: 4000, first_workout: 1000 });
  native.data.gamification_profiles[0].recovery_circuit_completions_json = JSON.stringify({ "2026-W40": 1, "2026-W39": 0 });
  const profile = decodeAndroidBackup(JSON.stringify(native)).snapshot.profile;
  expect(profile.baselinePushups).toBe(42);
  expect(profile.badgeUnlocks).toEqual({ comeback: 4000, first_workout: 1000 });
  expect(profile.badgeOrigins?.comeback).toBe("native_import");
  expect(profile.ledgerBaseline?.estimatedLifetimeSessions).toBe(0);
  expect(profile.recoveryWeeks).toEqual(["2026-W40"]);
  await restoreSnapshot(decodeAndroidBackup(JSON.stringify(native)).snapshot);
  expect((await readSnapshot()).profile.badgeUnlocks.first_workout).toBe(1000);
});
it("normalizes native SQLite-export JSON and stringified AsyncStorage domains", () => {
  const history = [{ id: "old", name: "Imported", date: "2025-01-01", exercises: [{ name: "Custom Press", logs: [{ weight: 20, reps: 8, rpe: 7 }] }] }];
  const sqlite = decodeLegacyBackup(JSON.stringify({ schema: "IRONLOG_SQLITE_EXPORT_V1", payload: { plans: [], history, bodyWeight: [{ date: "2025-01-01", weight: 75 }] }, appState: { weightUnit: "lb" } }));
  const async = decodeLegacyBackup(JSON.stringify({ domains: { workouts: { items: { ironlog_history: JSON.stringify(history), ironlog_plans: "[]" } } } }));
  expect(sqlite.snapshot.workouts[0].imported).toBe(true); expect(sqlite.snapshot.measurements[0].value).toBe(75); expect(sqlite.snapshot.profile.unit).toBe("lb");
  expect(async.snapshot.workouts[0].id).toBe(sqlite.snapshot.workouts[0].id);
  expect(async.snapshot.workouts[0].exercises[0].tracking).toBe("");
  expect(() => decodeLegacyBackup('{"history":"not an array"}')).toThrow();
  expect(() => decodeLegacyBackup('{"schema":"IRONLOG_SQLITE_EXPORT_V1","payload":{"invalid":"wrong"}}')).toThrow();
  expect(() => decodeLegacyBackup('{"domains":{}}')).toThrow();
  expect(() => decodeLegacyBackup('{"history":null}')).toThrow();
  const calibrated = decodeLegacyBackup(JSON.stringify({ history: [], settings: { baseline_training_age_months: 24, baseline_pushups: 40 } })).snapshot.profile;
  expect(calibrated).toMatchObject({ trainingAgeMonths: 24, baselinePushups: 40 });
});
it("keeps session IDs stable when an expanded export adds an earlier row and rejects impossible ISO dates", () => {
  const header = "Date,Workout Name,Exercise Name,Weight,Reps\n";
  const row = "2025-01-02,Push,Press,40,8\n";
  const first = previewExternalImport(header + row,"strong",[]);
  const expanded = previewExternalImport(header + "2025-01-01,Pull,Row,30,8\n" + row,"strong",[]);
  expect(expanded.workouts[1].id).toBe(first.workouts[0].id);
  expect(() => previewExternalImport(header + "2025-02-30,Push,Press,40,8\n","strong",[])).toThrow("No valid workouts");
});
it("parses quoted multiline CSV, explicit pound columns, dates and warmups", () => {
  expect(parseCsv('a,b\n"x, y","line1\nline2"')).toEqual([["a", "b"], ["x, y", "line1\nline2"]]);
  const preview = previewExternalImport('title,start_time,exercise_title,weight_lbs,reps,set_type,exercise_notes\n"Push, A",2025-01-01T18:00:00,Bench Press,100,8,warmup,"note\nline"\n"Push, A",2025-01-01T18:00:00,Bench Press,100,8,normal,', "hevy", [], "kg");
  expect(preview.workouts).toHaveLength(1); expect(preview.workouts[0].exercises[0].loggedSets[0].weightKg).toBeCloseTo(45.359);
  expect(preview.workouts[0].exercises[0].loggedSets[0].kind).toBe("warmup"); expect(preview.unresolved).toBe(1);
});
it("appends reviewed history atomically, skips repeat import and preserves active workouts", async () => {
  const active = await startWorkout();
  const preview = previewExternalImport(JSON.stringify({ workouts: [{ name: "Past", startedAt: 1_700_000_000_000, durationSeconds: 1800, exercises: [{ name: "Press", sets: [{ weight: 40, reps: 8 }] }] }] }), "openweight", []);
  expect(await appendImportedWorkouts(preview.workouts)).toEqual({ imported: 1, skipped: 0 });
  expect(await appendImportedWorkouts(preview.workouts)).toEqual({ imported: 0, skipped: 1 });
  expect((await readSnapshot()).workouts.find(w => w.id === active.id)?.status).toBe("active");
});
