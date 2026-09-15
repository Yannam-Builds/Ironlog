import { strFromU8, strToU8, unzipSync, zipSync } from "fflate";
import {
  defaultProfile,
  type AppSnapshot,
  type Exercise,
  type ImportResult,
  type Plan,
  type PlannedExercise,
  type Workout,
  type Tracking,
} from "./types";
import { localDateKey, parseHistoryDate } from "./dates";
import { snapshotSchema } from "../data/schema";
import { z } from "zod";
import { isTimed, trackingMode } from "./tracking";
import { resolveExerciseCandidate, type ExerciseResolution } from "./exercise-resolution";
type Row = Record<string, unknown>;
const obj = (v: unknown): Row =>
  v !== null && typeof v === "object" && !Array.isArray(v) ? (v as Row) : {};
const rows = (v: unknown): Row[] =>
  Array.isArray(v)
    ? (v.filter(
        (x) => x && typeof x === "object" && !Array.isArray(x),
      ) as Row[])
    : [];
const str = (v: unknown, def = "") =>
  v === null || v === undefined ? def : String(v);
const num = (v: unknown, def = 0) =>
  Number.isFinite(Number(v)) && v !== null && v !== "" ? Number(v) : def;
const bool = (v: unknown) =>
  v === true || v === "true" || (typeof v === "number" && v !== 0);
const uid = () => crypto.randomUUID();
const key = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, "");
const epoch = (v: unknown, fallback = Date.now()) =>
  typeof v === "number"
    ? v
    : typeof v === "string"
      ? /^\d+$/.test(v)
        ? Number(v)
        : (parseHistoryDate(v) ?? fallback)
      : fallback;
const parse = (raw: string) => {
  if (raw.length > 100 * 1024 * 1024)
    throw Error("File exceeds 100 MB JSON limit");
  return JSON.parse(raw) as unknown;
};
export function resolveExercise(id: string, name: string, library: Exercise[]) {
  const byId = library.find((e) => id && e.id === id);
  if (byId) return byId;
  const resolution = resolveExerciseCandidate(name, library);
  return resolution.status === "matched" ? resolution.matched : undefined;
}

function resolveImportedExercise(id: string, name: string, library: Exercise[]): ExerciseResolution {
  const byId = library.find((exercise) => id && exercise.id === id);
  if (byId) return { status: "matched", matched: byId, confidence: 100, reason: "Exact exercise ID", topCandidates: [] };
  return resolveExerciseCandidate(name, library);
}
export function decodePlans(
  raw: string,
  library: Exercise[],
): { plans: Plan[]; result: ImportResult } {
  const data = parse(raw),
    root = obj(data);
  const roots = Array.isArray(data)
    ? rows(data)
    : root.plan
      ? [obj(root.plan)]
      : root.plans
        ? rows(root.plans)
        : [root];
  const result: ImportResult = {
    imported: 0,
    unresolved: 0,
    skipped: 0,
    warnings: [],
  };
  const plans: Plan[] = [];
  for (const p of roots) {
    const name = str(p.name).trim();
    if (!name) {
      result.skipped++;
      continue;
    }
    const plan: Plan = {
      id: uid(),
      name,
      goal: str(p.goal) || "General Fitness",
      description: str(p.description ?? p.notes),
      order: plans.length,
      days: [],
    };
    for (const [dIndex, d] of rows(
      p.days ?? p.planDays ?? p.plan_days,
    ).entries()) {
      const exercises: PlannedExercise[] = [];
      for (const e of rows(
        d.exercises ?? d.planExercises ?? d.plan_exercises,
      )) {
        const name = str(e.exerciseName ?? e.exercise_name ?? e.name),
          id = str(e.exerciseId ?? e.exercise_id);
        if (!name && !id) {
          result.skipped++;
          continue;
        }
        const resolution = resolveImportedExercise(id, name, library);
        const resolved = resolution.status === "matched" ? resolution.matched : undefined;
        if (!resolved) {
          result.unresolved++;
          const candidates = resolution.topCandidates
            .filter((candidate) => candidate.score >= 52)
            .slice(0, 3)
            .map((candidate) => candidate.exercise.name);
          result.warnings.push(
            `Unresolved exercise: ${name || id}. Review before training.${candidates.length ? ` Review candidates: ${candidates.join(", ")}.` : ""}`,
          );
        }
        exercises.push({
          id: uid(),
          exerciseId: resolved?.id ?? id,
          name: resolved?.name || name || id,
          sets: Math.max(1, Math.trunc(num(e.sets, 3))),
          reps: str(e.reps) || "8-12",
          restSeconds: Math.max(
            0,
            num(e.restSeconds ?? e.rest_seconds ?? e.rest, 90),
          ),
          supersetGroup: str(e.supersetGroup ?? e.superset_group),
          isWarmup: bool(e.isWarmup ?? e.is_warmup),
          notes: str(e.notes ?? e.note),
        });
      }
      plan.days.push({
        id: uid(),
        name: str(d.name) || `Day ${dIndex + 1}`,
        color: str(d.color) || "#FF4500",
        exercises,
      });
    }
    plans.push(plan);
    result.imported++;
  }
  return { plans, result };
}
export function encodePlan(plan: Plan): string {
  return JSON.stringify(
    {
      version: 1,
      type: "ironlog_plan",
      exportedAt: new Date().toISOString(),
      plan: {
        name: plan.name,
        goal: plan.goal,
        description: plan.description,
        days: plan.days.map((d) => ({
          name: d.name,
          color: d.color,
          exercises: d.exercises.map((e) => ({
            exerciseId: e.exerciseId,
            exerciseName: e.name,
            sets: e.sets,
            reps: e.reps,
            restSeconds: e.restSeconds,
            supersetGroup: e.supersetGroup,
            isWarmup: e.isWarmup,
            notes: e.notes,
          })),
        })),
      },
    },
    null,
    2,
  );
}
const sections = [
  "exercises",
  "exercise_muscles",
  "plans",
  "plan_days",
  "plan_exercises",
  "workouts",
  "workout_exercises",
  "workout_sets",
  "body_measurements",
  "progress_photos",
  "app_settings",
  "athlete_calibrations",
  "gamification_profiles",
  "iron_ledger_events",
];
export function encodeAndroidBackup(snapshot: AppSnapshot): string {
  // Native plans store exercise foreign keys, not names. Materialize named unresolved
  // entries so a backup never turns a visible movement into an empty reference.
  const library = new Map(snapshot.exercises.map((e) => [e.id, e]));
  const reference = <T extends PlannedExercise>(e: T): T => {
    if (e.exerciseId && library.has(e.exerciseId)) return e;
    const id = e.exerciseId || `web-unresolved:${key(e.name) || e.id}`;
    const session = e as unknown as Partial<Workout["exercises"][number]>;
    if (!library.has(id))
      library.set(id, {
        id,
        name: e.name,
        muscle: session.muscle ?? "",
        equipment: session.equipment ?? "",
        tracking: session.tracking ?? "weight_reps",
        custom: true,
      });
    return { ...e, exerciseId: id };
  };
  snapshot = {
    ...snapshot,
    plans: snapshot.plans.map((p) => ({
      ...p,
      days: p.days.map((d) => ({
        ...d,
        exercises: d.exercises.map(reference),
      })),
    })),
    workouts: snapshot.workouts.map((w) => ({
      ...w,
      exercises: w.exercises.map(reference),
    })),
  };
  snapshot.exercises = [...library.values()];
  const data: Record<string, Row[]> = Object.fromEntries(
    sections.map((s) => [s, []]),
  );
  const now = Date.now();
  const stamp = { created_at: now, updated_at: now };
  data.exercises = snapshot.exercises.map((e) => ({
    id: e.id,
    name: e.name,
    normalized_name: key(e.name),
    primary_muscle: e.muscle,
    equipment: e.equipment,
    is_custom: !!e.custom,
    tracking_type: e.tracking || null,
    category: e.category ?? "",
    is_bodyweight: e.isBodyweight ?? ["bodyweight_reps", "bodyweight_plus_weight_reps", "assisted_bodyweight"].includes(e.tracking),
    requires_external_load: e.requiresExternalLoad ?? e.tracking === "weight_reps",
    secondary_muscles_json: JSON.stringify(e.secondaryMuscles ?? []),
    ...stamp,
  }));
  for (const e of snapshot.exercises) {
    const contributions = e.muscleContributions ?? {};
    for (const muscle of new Set([...(e.primaryMuscles ?? []), ...(e.secondaryMuscles ?? []), ...Object.keys(contributions)]))
      data.exercise_muscles.push({ id: `${e.id}:${muscle}`, exercise_id: e.id, muscle, role: e.primaryMuscles?.includes(muscle) ? "primary" : "secondary", contribution_fraction: contributions[muscle] ?? 0, ...stamp });
  }
  for (const p of snapshot.plans) {
    data.plans.push({
      id: p.id,
      name: p.name,
      description: p.description,
      goal: p.goal,
      is_active: snapshot.profile.activePlanId === p.id,
      ...stamp,
    });
    for (const [di, d] of p.days.entries()) {
      data.plan_days.push({
        id: d.id,
        plan_id: p.id,
        name: d.name,
        color: d.color,
        order_index: di,
        ...stamp,
      });
      for (const [ei, e] of d.exercises.entries())
        data.plan_exercises.push({
          id: e.id,
          plan_day_id: d.id,
          exercise_id: e.exerciseId,
          order_index: ei,
          sets: e.sets,
          reps: e.reps,
          rest_seconds: e.restSeconds,
          superset_group: e.supersetGroup,
          is_warmup: e.isWarmup,
          notes: e.notes,
          ...stamp,
        });
    }
  }
  for (const w of snapshot.workouts) {
    if (w.status === "discarded") continue;
    data.workouts.push({
      id: w.id,
      plan_id: w.planId ?? "",
      plan_day_id: w.dayId ?? "",
      name: w.name,
      started_at: w.startedAt,
      completed_at: w.completedAt ?? null,
      duration_seconds:
        w.durationSeconds ??
        (w.completedAt
          ? Math.max(0, Math.round((w.completedAt - w.startedAt) / 1000))
          : 0),
      status: w.status,
      rating: w.rating ?? null,
      notes: w.notes,
      imported: w.imported ?? false,
      ...stamp,
    });
    for (const [ei, e] of w.exercises.entries()) {
      const blockId = `${w.id}:${e.id}`;
      data.workout_exercises.push({
        id: blockId,
        workout_id: w.id,
        exercise_id: e.exerciseId,
        exercise_snapshot_json: JSON.stringify({version:1,name:e.name,primaryMuscle:e.muscle||null,primaryMuscles:e.primaryMuscles??[],secondaryMuscles:e.secondaryMuscles??[],muscleContributions:e.muscleContributions??{},equipment:e.equipment||null,category:e.category??null,trackingType:e.tracking||null,isBodyweight:e.isBodyweight??false,requiresExternalLoad:e.requiresExternalLoad??false}),
        order_index: ei,
        superset_group: e.supersetGroup,
        notes: e.notes,
        ...stamp,
      });
      for (const [si, s] of e.loggedSets.entries())
        data.workout_sets.push({
          id: s.id,
          workout_exercise_id: blockId,
          set_index: si + 1,
          weight:
            trackingMode(e) === "duration_distance" ? s.distanceKm : s.weightKg,
          reps: isTimed(e) ? s.durationSeconds : s.reps,
          rpe: s.rpe ?? null,
          rir: s.rir ?? null,
          rest_seconds: e.restSeconds,
          is_warmup: s.kind === "warmup",
          is_dropset: s.kind === "drop",
          is_amrap: s.kind === "amrap",
          to_failure: s.toFailure || s.kind === "failure",
          notes: s.notes,
          completed_at: s.loggedAt,
          ...stamp,
        });
    }
  }
  for (const m of snapshot.measurements)
    if (["bodyweight", "waist", "chest", "arm", "thigh"].includes(m.type))
      data.body_measurements.push({
        id: m.id,
        measured_at: epoch(m.date),
        [m.type]: m.value,
        notes: "",
        ...stamp,
      });
  data.athlete_calibrations = [
    {
      offline_user_id: "local",
      bodyweight_kg: snapshot.profile.weightKg,
      weight_unit: snapshot.profile.unit,
      weekly_goal_days: snapshot.profile.weeklyGoal,
      goal_mode: snapshot.profile.goal,
      ...stamp,
    },
  ];
  data.app_settings = Object.entries(snapshot.profile.exerciseNextNotes ?? {}).map(([key, value]) => ({
    id: key, key, value, value_type: "string", ...stamp,
  }));
  const finiteGyms = snapshot.gyms.filter(g => g.plateInventory !== undefined);
  let activeGym = finiteGyms.find(g => g.barKg === snapshot.profile.barKg &&
    JSON.stringify(g.plateInventory) === JSON.stringify(snapshot.profile.plateInventory));
  if (!activeGym && snapshot.profile.plateInventory !== undefined) {
    let id = 'web-current-setup';
    while (snapshot.gyms.some(g => g.id === id)) id += '-current';
    activeGym = { id, name: 'Current setup', barKg: snapshot.profile.barKg,
      platesKg: snapshot.profile.platesKg, plateInventory: snapshot.profile.plateInventory };
    finiteGyms.push(activeGym);
  }
  const nativeGyms = finiteGyms.map(g => ({
    id: g.id, name: g.name, barWeightKg: g.barKg,
    // Native quantities are per side; web quantities count physical plates.
    plates: g.plateInventory!.map(p => ({ weightKg: p.weightKg, quantity: Math.floor(p.quantity / 2) })),
  }));
  data.app_settings.push({ id: "gym_profiles_json", key: "gym_profiles_json", value: JSON.stringify(nativeGyms), value_type: "json", ...stamp });
  if (activeGym) data.app_settings.push({ id: "active_gym_profile_id", key: "active_gym_profile_id", value: activeGym.id, value_type: "string", ...stamp });
  data.gamification_profiles = [
    {
      offline_user_id: "local",
      badge_unlocks_json: JSON.stringify(snapshot.profile.badgeUnlocks),
      unlocked_badges: JSON.stringify(
        Object.keys(snapshot.profile.badgeUnlocks),
      ),
      recovery_circuit_completions_json: JSON.stringify(
        Object.fromEntries(snapshot.profile.recoveryWeeks.map((k) => [k, 1])),
      ),
      ...stamp,
    },
  ];
  // The extension preserves web-only session state; Android ignores it. Photo bytes require web ZIP.
  return JSON.stringify(
    {
      type: "ironlog_watermelon_export",
      version: 1,
      exportedAt: new Date().toISOString(),
      data,
      webExtension: {
        profile: snapshot.profile,
        checkins: snapshot.checkins,
        gyms: snapshot.gyms,
        workouts: snapshot.workouts,
        measurements: snapshot.measurements,
      },
      warnings: [
        "Progress photo bytes are not included. Use a web ZIP backup to retain photos.",
        ...(snapshot.gyms.some(g => g.plateInventory === undefined) ? ["Gym profiles with unlimited pairs are preserved only in the web extension; Android requires finite quantities."] : []),
        ...(finiteGyms.some(g => g.plateInventory!.some(p => p.quantity % 2)) ? ["Unpaired spare plates remain in the web extension; Android receives complete pairs only."] : []),
      ],
    },
    null,
    2,
  );
}
export function decodeAndroidBackup(raw: string): {
  snapshot: AppSnapshot;
  result: ImportResult;
  counts: Record<string, number>;
} {
  const root = obj(parse(raw));
  if (root.type !== "ironlog_watermelon_export" || root.version !== 1)
    throw Error("Unsupported Android backup type or version");
  const data = obj(root.data);
  for (const section of [
    "exercises",
    "plans",
    "plan_days",
    "plan_exercises",
    "workouts",
    "workout_exercises",
    "workout_sets",
  ])
    if (!Array.isArray(data[section]))
      throw Error(`Backup data section missing: ${section}`);
  for (const section of sections)
    if (data[section] !== undefined && !Array.isArray(data[section]))
      throw Error(`Invalid backup section: ${section}`);
  for (const section of sections) {
    const value = data[section];
    if (
      Array.isArray(value) &&
      value.some(
        (row) => row === null || typeof row !== "object" || Array.isArray(row),
      )
    )
      throw Error(`Invalid row in backup section: ${section}`);
  }
  const table = (name: string) => rows(data[name]);
  const counts = Object.fromEntries(sections.map((s) => [s, table(s).length]));
  const result: ImportResult = {
    imported: 0,
    unresolved: 0,
    skipped: 0,
    warnings: [],
  };
  for (const section of sections)
    if (data[section] === undefined)
      result.warnings.push(
        `Partial backup: missing ${section}; this section cannot be restored. Keep the original backup.`,
      );
  for (const w of table("workouts")) {
    if (
      !["active", "completed", "abandoned", "discarded"].includes(str(w.status))
    )
      throw Error("Invalid native workout status");
    if (w.started_at == null || !Number.isFinite(epoch(w.started_at, NaN)))
      throw Error("Invalid native workout start timestamp");
    if (w.completed_at != null && !Number.isFinite(epoch(w.completed_at, NaN)))
      throw Error("Invalid native workout completion timestamp");
    if (
      w.duration_seconds != null &&
      (!Number.isFinite(num(w.duration_seconds, NaN)) ||
        num(w.duration_seconds, NaN) < 0)
    )
      throw Error("Invalid native workout duration");
  }
  const stringArray = (v: unknown): string[] => {
    try { const x=typeof v === "string" ? JSON.parse(v) : v; return Array.isArray(x) ? x.filter((s): s is string => typeof s === "string") : []; } catch { return []; }
  };
  const exercises: Exercise[] = table("exercises").map((e) => {
    const relations=table("exercise_muscles").filter(m=>m.exercise_id===e.id);
    return {
      id: str(e.id) || uid(), name: str(e.name) || "Unnamed exercise",
      muscle: str(e.primary_muscle), equipment: str(e.equipment),
      tracking: str(e.tracking_type), category: str(e.category),
      isBodyweight: bool(e.is_bodyweight), requiresExternalLoad:bool(e.requires_external_load),
      primaryMuscles:relations.filter(m=>m.role==='primary').map(m=>str(m.muscle)),
      secondaryMuscles: [...new Set([...stringArray(e.secondary_muscles_json), ...relations.filter(m=>m.role==='secondary').map(m=>str(m.muscle))])],
      muscleContributions:Object.fromEntries(relations.filter(m=>num(m.contribution_fraction)>0).map(m=>[str(m.muscle),num(m.contribution_fraction)])),
      custom: bool(e.is_custom),
    };
  });
  const resolve = (id: string) => {
    const e = exercises.find((e) => e.id === id);
    if (e) return e;
    result.unresolved++;
    return {
      id,
      name: `Unresolved exercise (${id})`,
      muscle: "",
      equipment: "",
      tracking: "",
    };
  };
  const byOrder = (a: Row, b: Row) => num(a.order_index) - num(b.order_index);
  const plans: Plan[] = table("plans").map((p, order) => ({
    id: str(p.id) || uid(),
    name: str(p.name) || "Imported plan",
    description: str(p.description),
    goal: str(p.goal) || "General Fitness",
    order,
    days: table("plan_days")
      .filter((d) => d.plan_id === p.id)
      .sort(byOrder)
      .map((d) => ({
        id: str(d.id) || uid(),
        name: str(d.name),
        color: str(d.color) || "#FF4500",
        exercises: table("plan_exercises")
          .filter((e) => e.plan_day_id === d.id)
          .sort(byOrder)
          .map((e) => ({
            id: str(e.id) || uid(),
            exerciseId: str(e.exercise_id),
            name: resolve(str(e.exercise_id)).name,
            sets: Math.max(1, num(e.sets, 3)),
            reps: str(e.reps) || "8-12",
            restSeconds: Math.max(0, num(e.rest_seconds, 90)),
            notes: str(e.notes),
            supersetGroup: str(e.superset_group),
            isWarmup: bool(e.is_warmup),
          })),
      })),
  }));
  const workouts: Workout[] = table("workouts").map((w) => {
    const startedAt = epoch(w.started_at),
      completedAt =
        w.completed_at == null ? undefined : epoch(w.completed_at, startedAt);
    return {
      id: str(w.id) || uid(),
      planId: str(w.plan_id) || undefined,
      dayId: str(w.plan_day_id) || undefined,
      name: str(w.name) || "Imported workout",
      startedAt,
      completedAt,
      durationSeconds:
        w.duration_seconds == null
          ? undefined
          : Math.max(0, num(w.duration_seconds)),
      status:
        w.status === "completed"
          ? "completed"
          : w.status === "active"
            ? "active"
            : "discarded",
      notes: str(w.notes),
      rating: w.rating == null ? undefined : num(w.rating),
      revision: 0,
      restUsed: false,
      imported: true,
      exercises: table("workout_exercises")
        .filter((e) => e.workout_id === w.id)
        .sort(byOrder)
        .map((e) => {
          let exercise = resolve(str(e.exercise_id));
          if (e.exercise_snapshot_json) {
            try {
              const h=obj(JSON.parse(str(e.exercise_snapshot_json)));
              if(h.version===1 && str(h.name).trim()) exercise={...exercise,name:str(h.name),muscle:str(h.primaryMuscle),primaryMuscles:stringArray(h.primaryMuscles),secondaryMuscles:stringArray(h.secondaryMuscles),muscleContributions:Object.fromEntries(Object.entries(obj(h.muscleContributions)).filter(([,v])=>num(v)>0).map(([k,v])=>[k,num(v)])),equipment:str(h.equipment),category:str(h.category),tracking:str(h.trackingType),isBodyweight:bool(h.isBodyweight),requiresExternalLoad:bool(h.requiresExternalLoad)};
              else result.warnings.push("Unsupported exercise snapshot; library metadata used. Keep original backup.");
            } catch { result.warnings.push("Invalid exercise snapshot; library metadata used. Keep original backup."); }
          }
          return {
            id: str(e.id) || uid(),
            exerciseId: exercise.id,
            name: exercise.name,
            muscle: exercise.muscle,
            equipment: exercise.equipment,
            tracking: exercise.tracking,
            primaryMuscles:exercise.primaryMuscles,secondaryMuscles:exercise.secondaryMuscles,muscleContributions:exercise.muscleContributions,category:exercise.category,isBodyweight:exercise.isBodyweight,requiresExternalLoad:exercise.requiresExternalLoad,
            sets: 3,
            reps: "8-12",
            restSeconds: 90,
            notes: str(e.notes),
            supersetGroup: str(e.superset_group),
            isWarmup: false,
            pendingWarmups: [],
            loggedSets: table("workout_sets")
              .filter((s) => s.workout_exercise_id === e.id)
              .sort((a, b) => num(a.set_index) - num(b.set_index))
              .map((s) => ({
                id: str(s.id) || uid(),
                weightKg:
                  trackingMode(exercise) === "duration_distance"
                    ? 0
                    : Math.max(0, num(s.weight)),
                reps: isTimed(exercise)
                  ? 0
                  : Math.max(0, num(s.reps)),
                durationSeconds: isTimed(exercise)
                  ? Math.max(0, num(s.reps))
                  : 0,
                distanceKm:
                  trackingMode(exercise) === "duration_distance"
                    ? Math.max(0, num(s.weight))
                    : 0,
                kind: bool(s.is_warmup)
                  ? "warmup"
                  : bool(s.is_dropset)
                    ? "drop"
                    : bool(s.is_amrap)
                      ? "amrap"
                      : bool(s.to_failure)
                        ? "failure"
                        : "normal",
                toFailure: bool(s.to_failure),
                notes: str(s.notes),
                loggedAt: epoch(s.completed_at, completedAt ?? startedAt),
                rpe: s.rpe == null ? undefined : num(s.rpe),
                rir: s.rir == null ? undefined : num(s.rir),
              })),
          };
        }),
    };
  });
  const measurements = table("body_measurements").flatMap((m) =>
    ["bodyweight", "waist", "chest", "arm", "thigh"]
      .filter((k) => m[k] != null)
      .map((type) => ({
        id: `${str(m.id) || uid()}:${type}`,
        date: localDateKey(epoch(m.measured_at)),
        type,
        value: Math.max(0, num(m[type])),
        unit: type === "bodyweight" ? "kg" : "cm",
      })),
  );
  const calibration =
    table("athlete_calibrations").find((c) => c.offline_user_id === "local") ??
    table("athlete_calibrations")[0] ??
    {};
  const gamification =
    table("gamification_profiles").find((c) => c.offline_user_id === "local") ??
    table("gamification_profiles")[0] ??
    {};
  let badges: Record<string, number> = {};
  try {
    badges = obj(
      JSON.parse(str(gamification.badge_unlocks_json, "{}")),
    ) as Record<string, number>;
  } catch {
    result.warnings.push("Badge metadata could not be read.");
  }
  const exerciseNextNotes: Record<string, string> = {};
  const gymSetting = table("app_settings").find(s => s.key === "gym_profiles_json");
  const nativeGyms = gymSetting ? parse(str(gymSetting.value)) : [];
  if (!Array.isArray(nativeGyms)) throw Error("Invalid native gym profiles");
  const gyms = snapshotSchema.shape.gyms.parse(nativeGyms.map(value => {
    const gym = obj(value);
    const plates = gym.plates ?? [];
    if (!Array.isArray(plates)) throw Error("Invalid native plate inventory");
    const plateInventory = plates.map(value => {
      const p = obj(value);
      if (typeof p.quantity !== "number" || !Number.isSafeInteger(p.quantity) || p.quantity < 0)
        throw Error("Invalid native plate quantity");
      return { weightKg: p.weightKg, quantity: p.quantity * 2 };
    });
    return { id: gym.id, name: gym.name, barKg: gym.barWeightKg,
      platesKg: plateInventory.map(p => p.weightKg), plateInventory };
  }));
  const activeGymId = str(table("app_settings").find(s => s.key === "active_gym_profile_id")?.value);
  const activeGym = gyms.find(g => g.id === activeGymId);
  for (const setting of table("app_settings")) {
    const key = str(setting.key);
    if (!key.startsWith("exercise_next_note:") || !key.slice("exercise_next_note:".length).trim()) continue;
    if (typeof setting.value !== "string" || setting.value.length > 4000)
      throw Error("Invalid exercise setup reminder");
    exerciseNextNotes[key] = setting.value.trim();
  }
  const profile = {
    ...defaultProfile,
    ...(activeGym ? { barKg: activeGym.barKg, platesKg: activeGym.platesKg, plateInventory: activeGym.plateInventory } : {}),
    exerciseNextNotes,
    weightKg: num(calibration.bodyweight_kg, defaultProfile.weightKg),
    unit: calibration.weight_unit === "lb" ? ("lb" as const) : ("kg" as const),
    weeklyGoal: num(calibration.weekly_goal_days, 3),
    goal: str(calibration.goal_mode) || defaultProfile.goal,
    badgeUnlocks: badges,
    activePlanId:
      str(table("plans").find((p) => bool(p.is_active))?.id) || undefined,
  };
  const ext = obj(root.webExtension);
  if (ext.workouts != null) {
    const extended = snapshotSchema.shape.workouts.parse(ext.workouts);
    for (const native of workouts) {
      const corresponding = extended.find((w) => w.id === native.id);
      if (!corresponding)
        throw Error("Web extension is missing canonical workout history");
      for (const canonicalExercise of native.exercises) {
        const extendedExercise = corresponding.exercises.find(
          (e) => canonicalExercise.id === e.id || canonicalExercise.id === `${native.id}:${e.id}`,
        );
        if (!extendedExercise)
          throw Error("Web extension is missing a canonical workout exercise");
        const setIds = new Set(extendedExercise.loggedSets.map((s) => s.id));
        if (canonicalExercise.loggedSets.some((s) => !setIds.has(s.id)))
          throw Error("Web extension is missing canonical workout sets");
      }
    }
  }
  if (ext.measurements != null) {
    const extended = snapshotSchema.shape.measurements.parse(ext.measurements);
    for (const row of table("body_measurements"))
      for (const type of ["bodyweight", "waist", "chest", "arm", "thigh"])
        if (
          row[type] != null &&
          !extended.some(
            (m) =>
              (m.id === str(row.id) || m.id === `${str(row.id)}:${type}`) &&
              m.type === type &&
              m.value === num(row[type]),
          )
        )
          throw Error("Web extension is missing canonical body measurements");
  }
  // Canonical native snapshots own performed interpretation; web-only draft state remains.
  const extendedWorkouts = ext.workouts == null ? undefined : snapshotSchema.shape.workouts.parse(ext.workouts).map(w => {
    const canonicalWorkout = workouts.find(n => n.id === w.id);
    if (!canonicalWorkout) return w; // e.g. a web-only discarded draft
    const exercises = canonicalWorkout.exercises.map(canonical => {
      const extended = w.exercises.find(e => canonical.id === e.id || canonical.id === `${w.id}:${e.id}`)!;
      // Extension-only targets/drafts survive, but native tables own performed values,
      // associations and immutable metadata.
      return {...extended, exerciseId:canonical.exerciseId, name:canonical.name,
        muscle:canonical.muscle, equipment:canonical.equipment, notes:canonical.notes,
        supersetGroup:canonical.supersetGroup,
        tracking:canonical.tracking, primaryMuscles:canonical.primaryMuscles,
        secondaryMuscles:canonical.secondaryMuscles, muscleContributions:canonical.muscleContributions,
        category:canonical.category, isBodyweight:canonical.isBodyweight,
        requiresExternalLoad:canonical.requiresExternalLoad, loggedSets:canonical.loggedSets};
    });
    return {...w, ...canonicalWorkout, revision:w.revision, restUsed:w.restUsed,
      restEndsAt:w.restEndsAt, exercises};
  });
  const snapshot = snapshotSchema.parse({
    profile: { ...(ext.profile != null ? obj(ext.profile) : profile), exerciseNextNotes: { ...obj(obj(ext.profile).exerciseNextNotes), ...exerciseNextNotes } },
    plans,
    workouts: extendedWorkouts ?? workouts,
    exercises,
    measurements: ext.measurements ?? measurements,
    photos: [],
    checkins: ext.checkins ?? [],
    gyms: ext.gyms ?? gyms,
  });
  result.imported = workouts.length + plans.length + measurements.length;
  result.skipped = counts.progress_photos;
  for (const [child, parent, foreign] of [
    ["plan_days", "plans", "plan_id"],
    ["plan_exercises", "plan_days", "plan_day_id"],
    ["workout_exercises", "workouts", "workout_id"],
    ["workout_sets", "workout_exercises", "workout_exercise_id"],
  ]) {
    const ids = new Set(table(parent).map((p) => p.id));
    const orphan = table(child).filter((r) => !ids.has(r[foreign])).length;
    if (orphan) {
      result.skipped += orphan;
      result.warnings.push(
        `${orphan} orphan ${child} rows skipped; retain the original backup.`,
      );
    }
  }
  if (counts.progress_photos)
    result.warnings.push(
      `${counts.progress_photos} Android photo references cannot transfer image bytes into a browser.`,
    );
  if (
    counts.app_settings ||
    counts.athlete_calibrations ||
    counts.iron_ledger_events
  )
    result.warnings.push(
      "Android-only settings, calibration baselines and cached Ledger events are not restored. Web Ledger is rebuilt from history. Keep the original Android backup.",
    );
  if (result.unresolved)
    result.warnings.push(
      `${result.unresolved} exercise references require review.`,
    );
  return { snapshot, result, counts };
}
export async function encodeWebBackup(
  snapshot: AppSnapshot,
): Promise<Uint8Array> {
  const valid = snapshotSchema.parse(snapshot);
  const files: Record<string, Uint8Array> = {};
  const photos = [];
  for (const p of valid.photos) {
    const path = `photos/${encodeURIComponent(p.id)}.bin`;
    files[path] = new Uint8Array(await p.blob.arrayBuffer());
    photos.push({
      id: p.id,
      date: p.date,
      notes: p.notes,
      path,
      mime: p.blob.type,
    });
  }
  files["manifest.json"] = strToU8(
    JSON.stringify({
      type: "ironlog_web_backup",
      version: 1,
      exportedAt: new Date().toISOString(),
      snapshot: { ...valid, photos },
    }),
  );
  return zipSync(files, { level: 6 });
}
export async function decodeWebBackup(
  input: Uint8Array | ArrayBuffer | Blob,
): Promise<AppSnapshot> {
  const bytes =
    input instanceof Blob
      ? new Uint8Array(await input.arrayBuffer())
      : input instanceof Uint8Array
        ? input
        : new Uint8Array(input);
  if (bytes.byteLength > 200 * 1024 * 1024)
    throw Error("Archive exceeds 200 MB limit");
  let total = 0;
  const files = unzipSync(bytes, {
    filter: (file) => {
      total += file.originalSize;
      if (total > 500 * 1024 * 1024 || file.originalSize > 100 * 1024 * 1024)
        throw Error("Expanded archive exceeds safety limit");
      return (
        file.name === "manifest.json" || /^photos\/[^/]+\.bin$/.test(file.name)
      );
    },
  });
  if (!files["manifest.json"]) throw Error("Backup manifest missing");
  const root = obj(parse(strFromU8(files["manifest.json"])));
  if (root.type !== "ironlog_web_backup" || root.version !== 1)
    throw Error("Unsupported web backup version");
  const snapshot = obj(root.snapshot);
  // Validate the original manifest array before mapping: missing/null rows must never
  // disappear into an empty array and turn a corrupt restore into photo deletion.
  const manifestPhotos = z
    .array(
      z.object({
        id: z.string().min(1),
        date: z.string(),
        notes: z.string(),
        path: z.string().regex(/^photos\/[^/]+\.bin$/),
        mime: z.string(),
      }),
    )
    .parse(snapshot.photos);
  const paths = new Set(manifestPhotos.map((p) => p.path)),
    ids = new Set(manifestPhotos.map((p) => p.id));
  if (
    paths.size !== manifestPhotos.length ||
    ids.size !== manifestPhotos.length
  )
    throw Error("Duplicate photo IDs or paths in manifest");
  if (
    Object.keys(files).some(
      (path) => path.startsWith("photos/") && !paths.has(path),
    )
  )
    throw Error("Unreferenced photo bytes in backup");
  const photos = manifestPhotos.map((p) => {
    if (!files[p.path]) throw Error("Photo bytes missing from backup");
    return {
      id: p.id,
      date: p.date,
      notes: p.notes,
      blob: new Blob([files[p.path].slice().buffer as ArrayBuffer], {
        type: p.mime,
      }),
    };
  });
  return snapshotSchema.parse({ ...snapshot, photos });
}
