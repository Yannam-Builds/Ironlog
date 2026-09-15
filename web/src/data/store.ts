import Dexie, { liveQuery, type Table } from "dexie";
import {
  defaultProfile,
  type AppSnapshot,
  type Exercise,
  type Gym,
  type LoggedSet,
  type Measurement,
  type Photo,
  type Plan,
  type Profile,
  type RecoveryCheckin,
  type WarmupTarget,
  type Workout,
} from "../domain/types";
import {
  exerciseSchema,
  planSchema,
  profileSchema,
  snapshotSchema,
  workoutSchema,
} from "./schema";
import { resolveExercise } from "../domain/codecs";
import { creditedProof, deriveSnapshot } from "../domain/engine";
import { isoWeekKey } from "../domain/dates";
import { recomputeOnboardingBaseline } from "../domain/onboarding-baseline";

export const newId = () => crypto.randomUUID();
interface CatalogRow {
  id: string;
  exercises: Exercise[];
}
type PhotoBytes = Omit<Photo, "blob"> & { bytes: Uint8Array; mimeType: string };
type StoredPhoto = Photo | PhotoBytes;
async function photoToStorage(photo: Photo): Promise<PhotoBytes> {
  return {
    id: photo.id,
    date: photo.date,
    notes: photo.notes,
    mimeType: photo.blob.type,
    bytes: new Uint8Array(await photo.blob.arrayBuffer()),
  };
}
function photoFromStorage(photo: StoredPhoto): Photo {
  if ("blob" in photo) return photo; // Older databases stored native Blobs.
  return {
    id: photo.id,
    date: photo.date,
    notes: photo.notes,
    blob: new Blob([new Uint8Array(photo.bytes)], { type: photo.mimeType }),
  };
}
export class IronLogDatabase extends Dexie {
  profiles!: Table<Profile & { id: string }, string>;
  plans!: Table<Plan, string>;
  workouts!: Table<Workout, string>;
  exercises!: Table<Exercise, string>;
  catalog!: Table<CatalogRow, string>;
  measurements!: Table<Measurement, string>;
  photos!: Table<StoredPhoto, string>;
  checkins!: Table<RecoveryCheckin, string>;
  gyms!: Table<Gym, string>;
  constructor(name = "ironlog-web-v1") {
    super(name);
    this.version(1).stores({
      profiles: "id",
      plans: "id,order",
      workouts: "id,status,startedAt",
      exercises: "id,name",
      measurements: "id,date,type",
      photos: "id,date",
      checkins: "id,at",
      gyms: "id",
    });
    // Additive upgrade: old exercise records may contain user edits, so never delete them.
    this.version(2).stores({ catalog: "id" });
  }
}
export const db = new IronLogDatabase();
export class RevisionConflict extends Error {
  constructor() {
    super("This workout changed in another tab. Reload and try again.");
    this.name = "RevisionConflict";
  }
}
const queue = new Map<string, Promise<unknown>>();
const failedWrites = new Set<string>();
export class UnacknowledgedMutationError extends Error {
  constructor() {
    super(
      "A previous workout change was not saved. Review the missing change and retry it, or explicitly acknowledge continuing without it, before finishing.",
    );
    this.name = "UnacknowledgedMutationError";
  }
}
export function hasFailedMutation(id: string): boolean {
  return failedWrites.has(id);
}
/** UI must show the failed-write warning and obtain explicit confirmation before calling. */
export function acknowledgeFailedMutation(id: string): Promise<void> {
  return serialize(id, async () => {
    await requireActive(id);
    failedWrites.delete(id);
  });
}
function serialize<T>(key: string, action: () => Promise<T>): Promise<T> {
  const run = (queue.get(key) ?? Promise.resolve())
    .catch(() => {})
    .then(action)
    .catch((error) => {
      failedWrites.add(key);
      throw error;
    });
  queue.set(key, run);
  void run
    .finally(() => {
      if (queue.get(key) === run) queue.delete(key);
    })
    .catch(() => {});
  return run;
}
export async function bootstrap(exercises: Exercise[], database = db) {
  const catalog = exercises.map((exercise) => exerciseSchema.parse(exercise));
  assertUnique(
    catalog.map((exercise) => exercise.id),
    "bundled catalog",
  );
  await database.transaction(
    "rw",
    database.profiles,
    database.catalog,
    async () => {
      const existing = await database.profiles.get("local");
      if (!existing)
        await database.profiles.add({ ...defaultProfile, id: "local" });
      else {
        const profile = profileSchema.parse({
          ...defaultProfile,
          ...existing,
        });
        await database.profiles.put({
          ...recomputeOnboardingBaseline(profile),
          id: "local",
        });
      }
      // One structured-clone write replaces thousands of individual indexed writes on WebKit.
      await database.catalog.put({ id: "bundled", exercises: catalog });
    },
  );
}
export async function readExerciseLibrary(database = db): Promise<Exercise[]> {
  return database.transaction(
    "r",
    database.catalog,
    database.exercises,
    async () => {
      const [catalog, restored, overrides] = await Promise.all([
        database.catalog.get("bundled"),
        database.catalog.get("restored"),
        database.exercises.toArray(),
      ]);
      const merged = new Map(
        (catalog?.exercises ?? []).map((exercise) => [exercise.id, exercise]),
      );
      for (const exercise of restored?.exercises ?? [])
        merged.set(exercise.id, exercise);
      for (const exercise of overrides) merged.set(exercise.id, exercise);
      return [...merged.values()];
    },
  );
}
export async function readSnapshot(): Promise<AppSnapshot> {
  return db.transaction("r", db.tables, async () => {
    const row = await db.profiles.get("local");
    const profile = { ...defaultProfile, ...row };
    const [plans, workouts, exercises, measurements, photos, checkins, gyms] =
      await Promise.all([
        db.plans.orderBy("order").toArray(),
        db.workouts.orderBy("startedAt").reverse().toArray(),
        readExerciseLibrary(),
        db.measurements.toArray(),
        db.photos.toArray(),
        db.checkins.toArray(),
        db.gyms.toArray(),
      ]);
    return {
      profile: profileSchema.parse(profile),
      plans,
      workouts,
      exercises,
      measurements,
      photos: photos.map(photoFromStorage),
      checkins,
      gyms,
    };
  });
}
export function subscribeSnapshot(
  listener: (snapshot: AppSnapshot) => void,
  onError?: (error: unknown) => void,
) {
  const sub = liveQuery(readSnapshot).subscribe({
    next: listener,
    error: onError,
  });
  return () => sub.unsubscribe();
}
export async function saveProfile(partial: Partial<Profile>) {
  await db.transaction("rw", db.profiles, async () => {
    const row = await db.profiles.get("local");
    const profile = profileSchema.parse({
      ...defaultProfile,
      ...row,
      ...partial,
    });
    await db.profiles.put({
      ...recomputeOnboardingBaseline(profile),
      id: "local",
    });
  });
}

/** Completes first-run setup and optional starter-plan creation as one commit. */
export async function completeOnboarding(
  partial: Partial<Profile>,
  starterPlan?: Plan,
) {
  await db.transaction("rw", db.profiles, db.plans, async () => {
    const row = await db.profiles.get("local");
    const validPlan = starterPlan ? planSchema.parse(starterPlan) : undefined;
    const profile = profileSchema.parse({
      ...defaultProfile,
      ...row,
      ...partial,
      onboarded: true,
      onboardingStep: 10,
      activePlanId: validPlan?.id ?? row?.activePlanId,
    });
    if (validPlan) await db.plans.put(validPlan);
    await db.profiles.put({
      ...recomputeOnboardingBaseline(profile),
      id: "local",
    });
  });
}
export async function readExerciseNextNote(exerciseId: string): Promise<string> {
  if (!exerciseId.trim()) throw Error("Exercise identity is required.");
  return (await db.profiles.get("local"))?.exerciseNextNotes?.[`exercise_next_note:${exerciseId}`] ?? "";
}
export async function saveExerciseNextNote(exerciseId: string, note: string): Promise<void> {
  if (note.length > 4000) throw Error("Keep the reminder under 4,000 characters.");
  await db.transaction("rw", db.profiles, db.catalog, db.exercises, async () => {
    const library = await readExerciseLibrary();
    if (!exerciseId.trim() || !library.some((e) => e.id === exerciseId))
      throw Error("Save this exercise to the library before attaching a reminder.");
    const row = await db.profiles.get("local");
    const profile = profileSchema.parse({ ...defaultProfile, ...row,
      exerciseNextNotes: { ...row?.exerciseNextNotes, [`exercise_next_note:${exerciseId}`]: note.trim() },
    });
    await db.profiles.put({ ...profile, id: "local" });
  });
}
export async function savePlan(plan: Plan) {
  await db.plans.put(planSchema.parse(plan));
}
/** Compare the editor's immutable opening snapshot inside the same write transaction. */
export async function savePlanIfUnchanged(
  plan: Plan,
  baseline: Plan,
): Promise<void> {
  const valid = planSchema.parse(plan);
  const expected = planSchema.parse(baseline);
  if (valid.id !== expected.id) throw Error("Plan identity cannot be changed");
  await db.transaction("rw", db.plans, async () => {
    const current = await db.plans.get(expected.id);
    if (
      !current ||
      JSON.stringify(planSchema.parse(current)) !== JSON.stringify(expected)
    ) {
      throw Error(
        "This plan changed or was deleted in another editor. Reopen it before saving.",
      );
    }
    await db.plans.put(valid);
  });
}
/** Requires every current plan ID exactly once; no partial/rebased order is guessed. */
export async function reorderPlans(ids: string[]): Promise<void> {
  assertUnique(ids, "plan order");
  await db.transaction("rw", db.plans, async () => {
    const current = await db.plans.toArray();
    const byId = new Map(current.map((plan) => [plan.id, plan]));
    if (ids.length !== current.length || ids.some((id) => !byId.has(id))) {
      throw Error("The plan list changed. Reload it before reordering.");
    }
    await db.plans.bulkPut(
      ids.map((id, order) => ({ ...byId.get(id)!, order })),
    );
  });
}
export async function importPlans(plans: Plan[]) {
  const valid = plans.map((p) => planSchema.parse(p));
  assertUnique(
    valid.map((p) => p.id),
    "plans",
  );
  validatePlanIds(valid);
  await db.transaction("rw", db.plans, async () => {
    for (const p of valid)
      if (await db.plans.get(p.id))
        throw Error("Imported plan ID already exists");
    await db.plans.bulkAdd(valid);
  });
}
export async function deletePlan(id: string) {
  await db.transaction("rw", db.plans, db.profiles, async () => {
    await db.plans.delete(id);
    const p = await db.profiles.get("local");
    if (p?.activePlanId === id)
      await db.profiles.put({ ...p, activePlanId: undefined });
  });
}
export async function duplicatePlan(id: string) {
  const original = await db.plans.get(id);
  if (!original) throw Error("Plan not found");
  const plan = {
    ...original,
    id: newId(),
    name: `${original.name} (copy)`,
    days: original.days.map((d) => ({
      ...d,
      id: newId(),
      exercises: d.exercises.map((e) => ({ ...e, id: newId() })),
    })),
  };
  await savePlan(plan);
  return plan;
}
export async function startWorkout(
  planId?: string,
  dayId?: string,
  name?: string,
): Promise<Workout> {
  return db.transaction(
    "rw",
    [db.workouts, db.plans, db.exercises, db.catalog],
    async () => {
      const active = await db.workouts.where("status").equals("active").first();
      if (active) return active;
      const plan = planId ? await db.plans.get(planId) : undefined;
      if (planId && !plan) throw Error("Plan not found");
      const day = dayId
        ? plan?.days.find((d) => d.id === dayId)
        : plan?.days[0];
      if (dayId && !day) throw Error("Plan day not found");
      const exercises = await readExerciseLibrary();
      const w: Workout = {
        id: newId(),
        planId: plan?.id,
        dayId: day?.id,
        name: name || day?.name || "Freestyle workout",
        startedAt: Date.now(),
        status: "active",
        notes: "",
        restUsed: false,
        revision: 0,
        exercises: (day?.exercises ?? []).map((e) => {
          const lib = resolveExercise(e.exerciseId, e.name, exercises);
          return {
            ...e,
            exerciseId: lib?.id ?? e.exerciseId,
            tracking: lib?.tracking ?? "",
            muscle: lib?.muscle ?? "",
            equipment: lib?.equipment ?? "",
            secondaryMuscles: lib?.secondaryMuscles,
            primaryMuscles: lib?.primaryMuscles, muscleContributions: lib?.muscleContributions,
            category: lib?.category, isBodyweight: lib?.isBodyweight, requiresExternalLoad: lib?.requiresExternalLoad,
            loggedSets: [],
            pendingWarmups: [],
          };
        }),
      };
      await db.workouts.add(w);
      return w;
    },
  );
}
async function requireActive(id: string, revision?: number) {
  const w = await db.workouts.get(id);
  if (!w) throw Error("Workout not found");
  if (w.status !== "active") throw Error("Workout is not active");
  if (revision !== undefined && w.revision !== revision)
    throw new RevisionConflict();
  return w;
}
export function mutateWorkout(
  id: string,
  expectedRevision: number,
  recipe: (workout: Workout) => void,
): Promise<Workout> {
  return serialize(id, () =>
    db.transaction("rw", db.workouts, async () => {
      const w = await requireActive(id, expectedRevision);
      recipe(w);
      if (
        w.id !== id ||
        w.status !== "active" ||
        w.revision !== expectedRevision
      )
        throw Error("Session identity and lifecycle cannot be edited");
      w.revision++;
      const valid = workoutSchema.parse(w);
      await db.workouts.put(valid);
      return valid;
    }),
  );
}
export function finishWorkout(id: string): Promise<Workout> {
  return serialize(id, () =>
    db.transaction("rw", db.workouts, async () => {
      const w = await db.workouts.get(id);
      if (!w) throw Error("Workout not found");
      if (w.status === "completed") return w;
      if (failedWrites.has(id)) throw new UnacknowledgedMutationError();
      if (w.status !== "active") throw Error("Workout is not active");
      w.status = "completed";
      w.completedAt = Date.now();
      w.durationSeconds = Math.max(
        0,
        Math.round((w.completedAt - w.startedAt) / 1000),
      );
      w.restEndsAt = undefined;
      w.revision++;
      await db.workouts.put(w);
      return w;
    }),
  );
}
export function discardWorkout(id: string) {
  return serialize(id, () =>
    db.transaction("rw", db.workouts, async () => {
      const w = await requireActive(id);
      await db.workouts.put({
        ...w,
        status: "discarded",
        restEndsAt: undefined,
        revision: w.revision + 1,
      });
    }),
  );
}
export function logSet(
  id: string,
  revision: number,
  exerciseId: string,
  set: LoggedSet,
) {
  return mutateWorkout(id, revision, (w) => {
    const e = w.exercises.find((e) => e.id === exerciseId);
    if (!e) throw Error("Exercise not found");
    if (e.loggedSets.some((s) => s.id === set.id)) return;
    if (set.kind === "warmup") {
      const at = e.loggedSets.findIndex((s) => s.kind !== "warmup");
      e.loggedSets.splice(at < 0 ? e.loggedSets.length : at, 0, set);
      e.pendingWarmups = e.pendingWarmups.filter((s) => s.id !== set.id);
    } else e.loggedSets.push(set);
  });
}
export function addWarmups(
  id: string,
  revision: number,
  exerciseId: string,
  targets: WarmupTarget[],
) {
  return mutateWorkout(id, revision, (w) => {
    const e = w.exercises.find((e) => e.id === exerciseId);
    if (!e) throw Error("Exercise not found");
    e.pendingWarmups = targets.filter(
      (t) => !e.loggedSets.some((s) => s.id === t.id),
    );
  });
}
export function swapExercise(
  id: string,
  revision: number,
  slotId: string,
  exercise: Exercise,
  saveToPlan: boolean,
): Promise<Workout> {
  return serialize(id, () =>
    db.transaction("rw", db.workouts, db.plans, db.exercises, async () => {
      const w = await requireActive(id, revision);
      const e = w.exercises.find((e) => e.id === slotId);
      if (!e) throw Error("Exercise not found");
      if (e.loggedSets.length)
        throw Error("Remove logged sets before swapping this exercise");
      Object.assign(e, {
        exerciseId: exercise.id,
        name: exercise.name,
        muscle: exercise.muscle,
        equipment: exercise.equipment,
        tracking: exercise.tracking,
        secondaryMuscles: exercise.secondaryMuscles,
        primaryMuscles: exercise.primaryMuscles, muscleContributions: exercise.muscleContributions,
        category: exercise.category, isBodyweight: exercise.isBodyweight, requiresExternalLoad: exercise.requiresExternalLoad,
        pendingWarmups: [],
      });
      if (saveToPlan) {
        const p = w.planId ? await db.plans.get(w.planId) : undefined;
        const slot = p?.days
          .find((d) => d.id === w.dayId)
          ?.exercises.find((x) => x.id === slotId);
        if (!p || !slot) throw Error("Linked plan exercise not found");
        slot.exerciseId = exercise.id;
        slot.name = exercise.name;
        await db.plans.put(p);
      }
      await db.exercises.put(exerciseSchema.parse(exercise));
      w.revision++;
      await db.workouts.put(w);
      return w;
    }),
  );
}
export async function saveExercise(e: Exercise) {
  await db.exercises.put(exerciseSchema.parse(e));
}
export async function saveMeasurement(m: Measurement) {
  await db.measurements.put(snapshotSchema.shape.measurements.element.parse(m));
}
export async function deleteMeasurement(id: string) {
  await db.measurements.delete(id);
}
export async function savePhoto(p: Photo) {
  const valid = snapshotSchema.shape.photos.element.parse(p);
  // File reads must finish outside the IndexedDB transaction (notably on Safari).
  await db.photos.put(await photoToStorage(valid));
}
export async function deletePhoto(id: string) {
  await db.photos.delete(id);
}
export async function saveCheckin(c: RecoveryCheckin) {
  await db.checkins.put(snapshotSchema.shape.checkins.element.parse(c));
}
export async function saveGym(g: Gym) {
  await db.gyms.put(snapshotSchema.shape.gyms.element.parse(g));
}
export async function deleteGym(id: string) {
  await db.gyms.delete(id);
}
export async function deleteWorkout(id: string) {
  return serialize(id, () => db.workouts.delete(id));
}
export async function restoreSnapshot(snapshot: AppSnapshot) {
  const valid = snapshotSchema.parse(snapshot);
  for (const key of [
    "plans",
    "workouts",
    "exercises",
    "measurements",
    "photos",
    "checkins",
    "gyms",
  ] as const) {
    const rows = valid[key];
    if (new Set(rows.map((r) => r.id)).size !== rows.length)
      throw Error(`Duplicate IDs in ${key}`);
  }
  if (valid.workouts.filter((w) => w.status === "active").length > 1)
    throw Error("Backup contains multiple active sessions");
  validatePlanIds(valid.plans);
  for (const w of valid.workouts) {
    assertUnique(
      w.exercises.map((e) => e.id),
      "workout slots",
    );
    for (const e of w.exercises) {
      assertUnique(
        e.pendingWarmups.map((s) => s.id),
        "pending warmups",
      );
      const logged = new Set(e.loggedSets.map((s) => s.id));
      if (e.pendingWarmups.some((s) => logged.has(s.id)))
        throw Error("Warmup is both pending and logged");
    }
    if (
      w.status === "completed" &&
      (w.completedAt === undefined || w.completedAt < w.startedAt)
    )
      throw Error("Invalid completed workout chronology");
  }
  assertUnique(
    valid.workouts.flatMap((w) =>
      w.exercises.flatMap((e) => e.loggedSets.map((s) => s.id)),
    ),
    "logged sets",
  );
  await Promise.allSettled([...queue.values()]);
  const storedPhotos = await Promise.all(valid.photos.map(photoToStorage));
  await db.transaction("rw", db.tables, async () => {
    if (await db.workouts.where("status").equals("active").count())
      throw Error(
        "Finish or discard the active workout before restoring a backup.",
      );
    await Promise.all(db.tables.map((t) => t.clear()));
    await db.profiles.put({
      ...recomputeOnboardingBaseline(valid.profile),
      id: "local",
    });
    await Promise.all([
      db.plans.bulkPut(valid.plans),
      db.workouts.bulkPut(valid.workouts),
      db.catalog.put({ id: "restored", exercises: valid.exercises }),
      db.measurements.bulkPut(valid.measurements),
      db.photos.bulkPut(storedPhotos),
      db.checkins.bulkPut(valid.checkins),
      db.gyms.bulkPut(valid.gyms),
    ]);
  });
}
export async function resetData() {
  await Promise.allSettled([...queue.values()]);
  await db.transaction("rw", db.tables, async () => {
    await Promise.all(db.tables.map((t) => t.clear()));
  });
  failedWrites.clear();
}
export function editHistory(
  id: string,
  expectedRevision: number,
  recipe: (workout: Workout) => void,
): Promise<Workout> {
  return serialize(id, () =>
    db.transaction("rw", db.workouts, async () => {
      const w = await db.workouts.get(id);
      if (!w || w.status !== "completed")
        throw Error("Completed workout not found");
      if (w.revision !== expectedRevision) throw new RevisionConflict();
      recipe(w);
      if (
        w.id !== id ||
        w.status !== "completed" ||
        w.revision !== expectedRevision
      )
        throw Error("History identity and lifecycle cannot be edited");
      w.revision++;
      const valid = workoutSchema.parse(w);
      await db.workouts.put(valid);
      return valid;
    }),
  );
}
export async function completeRecoveryCircuit(now = Date.now()) {
  await db.transaction("rw", db.profiles, db.workouts, async () => {
    const p = await db.profiles.get("local");
    if (!p) throw Error("Profile not found");
    const week = isoWeekKey(now);
    if (p.recoveryWeeks.includes(week))
      throw Error("Recovery circuit already credited this week");
    const history = await db.workouts.toArray();
    const count = history.filter(
      (w) => creditedProof(w, now) && isoWeekKey(w.startedAt) === week,
    ).length;
    if (count !== Math.max(1, p.weeklyGoal) - 1)
      throw Error(
        "Recovery makeup is available when you are exactly one session short of your weekly goal",
      );
    await db.profiles.put({ ...p, recoveryWeeks: [...p.recoveryWeeks, week] });
  });
}
/** Separate from pure deriveSnapshot so live queries never write in response to themselves. */
export async function reconcileBadges(now = Date.now()) {
  return db.transaction("rw", db.tables, async () => {
    const snapshot = await readSnapshot();
    const earned = deriveSnapshot(snapshot, now).durableUnlockedBadges;
    const old = snapshot.profile.badgeUnlocks;
    const added = earned.filter((id) => !(id in old));
    if (added.length)
      await db.profiles.put({
        ...snapshot.profile,
        id: "local",
        badgeUnlocks: {
          ...old,
          ...Object.fromEntries(added.map((id) => [id, now])),
        },
      });
    return added;
  });
}
function assertUnique(ids: string[], label: string) {
  if (new Set(ids).size !== ids.length)
    throw Error(`Duplicate IDs in ${label}`);
}
function validatePlanIds(plans: Plan[]) {
  assertUnique(
    plans.flatMap((p) => p.days.map((d) => d.id)),
    "plan days",
  );
  assertUnique(
    plans.flatMap((p) => p.days.flatMap((d) => d.exercises.map((e) => e.id))),
    "plan exercises",
  );
}
