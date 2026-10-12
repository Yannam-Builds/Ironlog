import { snapshotSchema } from "../data/schema";
import { parseHistoryDate, localDateKey } from "./dates";
import { resolveExerciseCandidate } from "./exercise-resolution";
import { portableNativeSettings } from "./portable-profile";
import { calculateOnboardingBaseline } from "./onboarding-baseline";
import { defaultProfile, type AppSnapshot, type Exercise, type LoggedSet, type SessionExercise } from "./types";
type Row = Record<string, unknown>;
const object = (value: unknown): Row => { if (!value || typeof value !== "object" || Array.isArray(value)) throw Error("Expected an object in the legacy backup."); return value as Row; };
const decode = (value: unknown) => typeof value === "string" ? JSON.parse(value) : value;
const text = (value: unknown, fallback = "") => value == null ? fallback : String(value);
const number = (value: unknown, fallback = 0) => { if (value == null || value === "") return fallback; const result = Number(value); if (!Number.isFinite(result) || result < 0) throw Error("Invalid legacy numeric value."); return result; };
const list = (value: unknown): Row[] => { if (value == null) return []; const rows = decode(value); if (!Array.isArray(rows)) throw Error("Legacy data section must be an array."); return rows.map(object); };
const timestamp = (value: unknown, fallback: number) => {
  if (value == null || value === "") return fallback;
  const numeric = typeof value === "number" || /^\d+$/.test(String(value)) ? Number(value) : undefined;
  const at = numeric === undefined ? parseHistoryDate(String(value)) : numeric < 10_000_000_000 ? numeric * 1000 : numeric;
  if (at === undefined || !Number.isFinite(at) || at < 0) throw Error("Invalid legacy date. No data has been restored."); return at;
};
export function legacyId(prefix: string, value: string) {
  let hash = 14695981039346656037n;
  for (const char of value) { hash ^= BigInt(char.codePointAt(0)!); hash = BigInt.asUintN(64, hash * 1099511628211n); }
  return `legacy_${prefix}_${hash.toString(16)}`;
}
export function decodeLegacyBackup(raw: string, catalog: Exercise[] = [], now = Date.now()): { snapshot: AppSnapshot; warnings: string[] } {
  if (raw.length > 50_000_000) throw Error("Legacy backup exceeds 50 MB.");
  const root = object(JSON.parse(raw));
  const sqlite = root.schema === "IRONLOG_SQLITE_EXPORT_V1";
  if (root.type || (root.schema && !sqlite)) throw Error("Unsupported legacy backup type or version.");
  const payload = root.payload == null ? root : object(root.payload);
  if (!sqlite && !root.domains && !["plans", "history", "bodyWeight", "bodyMeasurements", "customExercises"].some(key => key in payload)) throw Error("Unrecognized legacy backup.");
  const domainValue = (key: string): unknown => {
    for (const value of Object.values(root.domains == null ? {} : object(root.domains))) { const domain = object(value); const items = domain.items == null ? {} : object(domain.items); if (key in items) return decode(items[key]); }
  };
  const recognized = [["plans", "ironlog_plans"], ["history", "ironlog_history"], ["bodyWeight", "ironlog_bw"], ["bodyMeasurements", "@ironlog/bodyMeasurements"], ["customExercises", "@ironlog/customExercises"]].some(([name,key]) => name in payload || domainValue(key) !== undefined);
  if (!recognized) throw Error("Legacy backup contains no recognized data sections. No data has been restored.");
  const section = (name: string, domainKey: string) => { const value = name in payload ? payload[name] : domainValue(domainKey); if (value === null) throw Error(`Legacy ${name} must be an array.`); return list(value); };
  const settings = portableNativeSettings(object(decode(root.appState ?? payload.settings ?? domainValue("ironlog_settings") ?? {})));
  const profile = { ...defaultProfile, name: text(settings.name ?? settings.userName), unit: settings.weightUnit === "lb" ? "lb" as const : "kg" as const, weeklyGoal: number(settings.weeklyGoalDays, 3), goalMode: text(settings.goalMode, defaultProfile.goalMode), nativeSettings: settings, onboarded: true,
    trainingAgeMonths: number(settings.baseline_training_age_months), historicalTrainingDaysPerWeek: number(settings.baseline_historical_training_days_per_week, 3), weightKg: number(settings.baseline_bodyweight_kg, defaultProfile.weightKg),
    hasPastTraining: settings.baseline_has_past_training === true || settings.baseline_has_past_training === "true", hasGymAccess: settings.baseline_has_gym_access == null || settings.baseline_has_gym_access === true || settings.baseline_has_gym_access === "true",
    baselinePushups: number(settings.baseline_pushups), baselinePullups: number(settings.baseline_pullups), baselineBenchKg: number(settings.baseline_bench_kg), baselineLatPulldownKg: number(settings.baseline_lat_pulldown_kg), baselineMileRunSeconds: number(settings.baseline_mile_run_seconds),
    calibrationProvenance: { firstVerifiedSessionAt: timestamp(settings.first_verified_session_at, 0), importedHistory: settings.ledger_imported_history === true || settings.ledger_imported_history === "true", confidence: .5, updatedAt: now },
  };
  const custom = section("customExercises", "@ironlog/customExercises");
  const library = [...catalog];
  const ensure = (row: Row): Exercise => {
    const name = text(row.name ?? row.exerciseName ?? row.title, "Exercise").trim(); if (!name) throw Error("Legacy exercise is missing its name.");
    const resolved = resolveExerciseCandidate(name, library); if (resolved.status === "matched" && resolved.matched) return resolved.matched;
    const same = library.find(e => e.name.trim().toLowerCase() === name.toLowerCase()); if (same) return same;
    const exercise: Exercise = { id: legacyId("exercise", text(row.id, name)), name, muscle: text(row.primaryMuscle ?? row.muscleGroup ?? row.muscle, "Other"), equipment: text(row.equipment, "Other"), category: text(row.category ?? row.type, "strength"), tracking: text(row.trackingType ?? row.tracking), custom: true };
    library.push(exercise); return exercise;
  };
  custom.forEach(ensure);
  const plans = section("plans", "ironlog_plans").map((row,index) => {
    const id = legacyId("plan", text(row.id, String(index)));
    return { id, name: text(row.name, `Imported plan ${index + 1}`), goal: text(row.goal, "General Fitness"), description: text(row.description), order: index,
      days: list(row.days ?? row.workoutDays).map((day,dayIndex) => ({ id: legacyId("day", `${id}:${text(day.id, String(dayIndex))}`), name: text(day.name ?? day.title, `Day ${dayIndex + 1}`), color: text(day.color, "#FF4500"), exercises: list(day.exercises ?? day.items).map((exercise,exerciseIndex) => { const linked = ensure(exercise); return { id: legacyId("slot", `${id}:${dayIndex}:${exerciseIndex}`), exerciseId: linked.id, name: linked.name, sets: number(exercise.sets, 3), reps: text(exercise.reps ?? exercise.repRange, "8-12"), restSeconds: number(exercise.restSeconds ?? exercise.rest, 90), notes: text(exercise.notes), supersetGroup: text(exercise.supersetGroup), isWarmup: exercise.isWarmup === true }; }) })) };
  });
  const workouts = section("history", "ironlog_history").map((row,index) => {
    const id = legacyId("workout", text(row.id, String(index))); const startedAt = timestamp(row.startedAt ?? row.timestamp ?? row.date, now);
    const durationSeconds = number(row.durationSeconds ?? row.duration);
    const completedAt = timestamp(row.completedAt, startedAt + durationSeconds * 1000);
    const exercises: SessionExercise[] = list(row.exercises ?? row.items).map((exercise,exerciseIndex) => {
      const linked = ensure(exercise);
      const sets: LoggedSet[] = list(exercise.sets ?? exercise.logs).map((set,setIndex) => ({ id: legacyId("set", `${id}:${exerciseIndex}:${text(set.id, String(setIndex))}`), weightKg: number(set.weightKg ?? set.weight), reps: number(set.reps), durationSeconds: number(set.durationSeconds), distanceKm: number(set.distanceKm), kind: set.isWarmup ? "warmup" : set.isDropset ? "drop" : set.isAmrap || set.isAMRAP ? "amrap" : "normal", toFailure: set.toFailure === true, rpe: set.rpe == null ? undefined : number(set.rpe), rir: set.rir == null ? undefined : number(set.rir), notes: text(set.notes), loggedAt: timestamp(set.loggedAt, completedAt) }));
      return { ...linked, id: legacyId("sessionExercise", `${id}:${exerciseIndex}`), exerciseId: linked.id, sets: Math.max(1, sets.length), reps: "", restSeconds: number(exercise.restSeconds, 90), notes: text(exercise.notes), supersetGroup: text(exercise.supersetGroup), isWarmup: false, pendingWarmups: [], loggedSets: sets };
    });
    if (row.status != null && !["completed", "active", "discarded"].includes(String(row.status))) throw Error("Unsupported legacy workout lifecycle.");
    return { id, name: text(row.name ?? row.day, "Imported workout"), startedAt, completedAt: row.status === "active" ? undefined : completedAt, durationSeconds: row.status === "active" ? undefined : durationSeconds, status: (row.status ?? "completed") as "completed" | "active" | "discarded", exercises, notes: text(row.notes), rating: row.rating == null ? undefined : number(row.rating), imported: true, revision: 0, restUsed: false };
  });
  const measurements = [...section("bodyWeight", "ironlog_bw").flatMap((row,index) => [{ id: legacyId("body", text(row.id, String(index))), date: localDateKey(timestamp(row.measuredAt ?? row.date, now)), type: "bodyweight", value: number(row.weight ?? row.bodyweight), unit: "kg" }]), ...section("bodyMeasurements", "@ironlog/bodyMeasurements").flatMap((row,index) => ["bodyweight", "waist", "chest", "arm", "thigh"].filter(type => row[type] != null).map(type => ({ id: legacyId("measurement", `${text(row.id, String(index))}:${type}`), date: localDateKey(timestamp(row.measuredAt ?? row.date, now)), type, value: number(row[type]), unit: type === "bodyweight" ? "kg" : "cm" })))];
  const activeIndex = section("plans", "ironlog_plans").findIndex(row => row.isActive === true); if (activeIndex >= 0) Object.assign(profile, { activePlanId: plans[activeIndex].id });
  profile.calibrationProvenance.importedHistory ||= workouts.length > 0;
  const snapshot = snapshotSchema.parse({ profile: { ...profile, ledgerBaseline: calculateOnboardingBaseline(profile, now) }, plans, workouts, exercises: library, measurements, photos: [], checkins: [], gyms: [] });
  return { snapshot, warnings: [sqlite ? "Legacy SQLite-export JSON normalized; original file remains unchanged." : "Legacy AsyncStorage backup normalized; original file remains unchanged.", "Legacy photo references do not contain portable image bytes. Keep the original backup.", ...(library.some(e => !e.tracking) ? ["Exercises without explicit tracking retain unknown tracking. Review them before logging new sets."] : [])] };
}
