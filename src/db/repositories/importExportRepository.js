import { database } from '../database';
import { seedExercisesIfNeeded } from './exerciseRepository';
import { normalizeExerciseName, normalizeExerciseNameKey } from '../../utils/validation';

const EXPORT_TYPE = 'ironlog_watermelon_export';
const SQLITE_SCHEMA_PREFIX = 'IRONLOG_SQLITE_EXPORT_V';

const EXPORT_TABLES = [
  'exercises',
  'exercise_muscles',
  'plans',
  'plan_days',
  'plan_exercises',
  'workouts',
  'workout_exercises',
  'workout_sets',
  'body_measurements',
  'progress_photos',
  'app_settings',
];

const LEGACY_KEY_MAP = {
  plans: 'ironlog_plans',
  history: 'ironlog_history',
  bodyWeight: 'ironlog_bw',
  bodyMeasurements: '@ironlog/bodyMeasurements',
  customExercises: '@ironlog/customExercises',
  settings: 'ironlog_settings',
};

function nowMs() {
  return Date.now();
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function cleanRow(raw) {
  const copy = { ...raw };
  delete copy._status;
  delete copy._changed;
  return copy;
}

function asString(value, fallback = '') {
  if (value == null) return fallback;
  return String(value);
}

function asNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function asBoolean(value) {
  return !!value;
}

function parseMaybeJson(value) {
  if (typeof value !== 'string') return value;
  try {
    return JSON.parse(value);
  } catch (_) {
    return value;
  }
}

function stableId(prefix, rawId, index) {
  if (rawId != null && String(rawId).trim()) {
    return `legacy_${prefix}_${String(rawId).replace(/[^a-zA-Z0-9_-]/g, '_')}`;
  }
  return `legacy_${prefix}_${index}_${Math.random().toString(36).slice(2, 8)}`;
}

function isWatermelonExport(payload) {
  return payload?.type === EXPORT_TYPE && payload?.version === 1;
}

function isLegacySQLiteBundle(payload) {
  const schema = asString(payload?.schema, '');
  return (
    schema.startsWith(SQLITE_SCHEMA_PREFIX) &&
    payload?.payload &&
    typeof payload.payload === 'object'
  );
}

function getLegacyDomainItem(payload, key) {
  const domains = payload?.domains;
  if (!domains || typeof domains !== 'object') return null;
  for (const domain of Object.values(domains)) {
    if (!domain?.items || typeof domain.items !== 'object') continue;
    if (domain.items[key] != null) return parseMaybeJson(domain.items[key]);
  }
  return null;
}

function extractLegacySnapshot(payload) {
  if (isLegacySQLiteBundle(payload)) {
    return {
      format: 'sqlite_v1',
      snapshot: payload.payload || {},
      appState: payload.appState || {},
    };
  }

  const root = payload?.payload && typeof payload.payload === 'object' ? payload.payload : payload;
  const snapshot = {
    plans: parseMaybeJson(root?.plans) ?? getLegacyDomainItem(payload, LEGACY_KEY_MAP.plans) ?? [],
    history: parseMaybeJson(root?.history) ?? getLegacyDomainItem(payload, LEGACY_KEY_MAP.history) ?? [],
    bodyWeight: parseMaybeJson(root?.bodyWeight) ?? getLegacyDomainItem(payload, LEGACY_KEY_MAP.bodyWeight) ?? [],
    bodyMeasurements:
      parseMaybeJson(root?.bodyMeasurements) ?? getLegacyDomainItem(payload, LEGACY_KEY_MAP.bodyMeasurements) ?? [],
    customExercises:
      parseMaybeJson(root?.customExercises) ?? getLegacyDomainItem(payload, LEGACY_KEY_MAP.customExercises) ?? [],
  };

  const appState = payload?.appState && typeof payload.appState === 'object'
    ? payload.appState
    : parseMaybeJson(root?.settings) || getLegacyDomainItem(payload, LEGACY_KEY_MAP.settings) || {};

  return {
    format: 'legacy_async',
    snapshot,
    appState,
  };
}

function collectPlanDays(plan) {
  if (Array.isArray(plan?.days)) return plan.days;
  if (Array.isArray(plan?.workoutDays)) return plan.workoutDays;
  return [];
}

function collectDayExercises(day) {
  if (Array.isArray(day?.exercises)) return day.exercises;
  if (Array.isArray(day?.items)) return day.items;
  return [];
}

function collectWorkoutExercises(entry) {
  if (Array.isArray(entry?.exercises)) return entry.exercises;
  if (Array.isArray(entry?.items)) return entry.items;
  return [];
}

function collectExerciseSets(exercise) {
  if (Array.isArray(exercise?.sets)) return exercise.sets;
  if (Array.isArray(exercise?.logs)) return exercise.logs;
  return [];
}

function convertLegacyToWatermelonExport(payload) {
  const { format, snapshot, appState } = extractLegacySnapshot(payload);
  const plans = safeArray(snapshot.plans);
  const history = safeArray(snapshot.history);
  const bodyWeight = safeArray(snapshot.bodyWeight);
  const bodyMeasurements = safeArray(snapshot.bodyMeasurements);
  const customExercises = safeArray(snapshot.customExercises);

  const createdAt = nowMs();
  const exerciseRows = [];
  const exerciseMuscleRows = [];
  const planRows = [];
  const planDayRows = [];
  const planExerciseRows = [];
  const workoutRows = [];
  const workoutExerciseRows = [];
  const workoutSetRows = [];
  const bodyMeasurementRows = [];
  const progressPhotoRows = [];
  const appSettingsRows = [];

  const exerciseIdByNormName = new Map();
  let exerciseCounter = 0;
  let planCounter = 0;
  let workoutCounter = 0;
  let bodyCounter = 0;

  function ensureExercise(input = {}) {
    const name = normalizeExerciseName(input.name || input.exerciseName || input.title || 'Exercise');
    const normalized = normalizeExerciseNameKey(name);
    if (exerciseIdByNormName.has(normalized)) return exerciseIdByNormName.get(normalized);

    exerciseCounter += 1;
    const id = stableId('exercise', input.id, exerciseCounter);
    const primaryMuscle = asString(input.primaryMuscle || input.muscleGroup || input.muscle || 'Other');
    const equipment = asString(input.equipment || 'Other');
    const category = asString(input.category || input.type || 'strength');
    const isCustom = input.isCustom != null ? asBoolean(input.isCustom) : true;
    const source = isCustom ? 'user_custom' : 'import';
    const notes = asString(input.notes || '');

    exerciseRows.push({
      id,
      name,
      normalized_name: normalized,
      primary_muscle: primaryMuscle,
      equipment,
      category,
      is_custom: isCustom,
      source,
      notes,
      created_at: createdAt,
      updated_at: createdAt,
    });
    exerciseMuscleRows.push({
      id: stableId('exercise_muscle', `${id}_primary`, exerciseCounter),
      exercise_id: id,
      muscle: primaryMuscle,
      role: 'primary',
      contribution_fraction: 1,
      created_at: createdAt,
      updated_at: createdAt,
    });
    exerciseIdByNormName.set(normalized, id);
    return id;
  }

  customExercises.forEach((exercise, index) => {
    ensureExercise({
      ...exercise,
      id: exercise?.id ?? `custom_${index}`,
      isCustom: true,
      source: 'user_custom',
    });
  });

  plans.forEach((plan, planIndex) => {
    planCounter += 1;
    const planId = stableId('plan', plan?.id, planCounter);
    planRows.push({
      id: planId,
      name: asString(plan?.name || `Imported Plan ${planCounter}`),
      goal: asString(plan?.goal || 'General Fitness'),
      description: asString(plan?.description || ''),
      is_active: asBoolean(plan?.isActive),
      created_at: asNumber(plan?.createdAt, createdAt),
      updated_at: asNumber(plan?.updatedAt, createdAt),
    });

    collectPlanDays(plan).forEach((day, dayIndex) => {
      const planDayId = stableId('plan_day', day?.id || `${planId}_${dayIndex + 1}`, dayIndex + 1);
      planDayRows.push({
        id: planDayId,
        plan_id: planId,
        name: asString(day?.name || day?.title || `Day ${dayIndex + 1}`),
        color: asString(day?.color || '#FF4500'),
        order_index: dayIndex,
        created_at: createdAt,
        updated_at: createdAt,
      });

      collectDayExercises(day).forEach((ex, exIndex) => {
        const exerciseId = ensureExercise(ex || {});
        planExerciseRows.push({
          id: stableId('plan_exercise', ex?.id || `${planDayId}_${exIndex + 1}`, exIndex + 1),
          plan_day_id: planDayId,
          exercise_id: exerciseId,
          order_index: exIndex,
          sets: Math.max(1, asNumber(ex?.sets, 3)),
          reps: asString(ex?.reps || ex?.repRange || '8-12'),
          rest_seconds: Math.max(0, asNumber(ex?.restSeconds ?? ex?.rest, 90)),
          superset_group: asString(ex?.supersetGroup || ''),
          is_warmup: asBoolean(ex?.isWarmup),
          notes: asString(ex?.notes || ''),
          created_at: createdAt,
          updated_at: createdAt,
        });
      });
    });
  });

  history.forEach((workout, workoutIndex) => {
    workoutCounter += 1;
    const dateFallback = workout?.date ? new Date(workout.date).getTime() : createdAt;
    const startedAt = asNumber(workout?.startedAt, asNumber(workout?.timestamp, dateFallback));
    const completedAt = asNumber(workout?.completedAt, startedAt);
    const workoutId = stableId('workout', workout?.id, workoutCounter);
    workoutRows.push({
      id: workoutId,
      plan_id: workout?.planId ? stableId('plan', workout.planId, workoutIndex + 1) : '',
      plan_day_id: workout?.dayId ? stableId('plan_day', workout.dayId, workoutIndex + 1) : '',
      name: asString(workout?.name || workout?.day || `Imported Workout ${workoutCounter}`),
      started_at: startedAt,
      completed_at: completedAt || null,
      duration_seconds: Math.max(0, asNumber(workout?.durationSeconds ?? workout?.duration, 0)),
      rating: workout?.rating != null ? asNumber(workout.rating, null) : null,
      notes: asString(workout?.notes || ''),
      status: asString(workout?.status || 'completed'),
      created_at: startedAt || createdAt,
      updated_at: completedAt || createdAt,
    });

    collectWorkoutExercises(workout).forEach((ex, exIndex) => {
      const workoutExerciseId = stableId('workout_exercise', ex?.id || `${workoutId}_${exIndex + 1}`, exIndex + 1);
      const exerciseId = ensureExercise(ex || {});
      workoutExerciseRows.push({
        id: workoutExerciseId,
        workout_id: workoutId,
        exercise_id: exerciseId,
        order_index: exIndex,
        superset_group: asString(ex?.supersetGroup || ''),
        notes: asString(ex?.notes || ''),
        created_at: startedAt || createdAt,
        updated_at: completedAt || createdAt,
      });

      collectExerciseSets(ex).forEach((set, setIndex) => {
        workoutSetRows.push({
          id: stableId('workout_set', set?.id || `${workoutExerciseId}_${setIndex + 1}`, setIndex + 1),
          workout_exercise_id: workoutExerciseId,
          set_index: setIndex + 1,
          weight: Math.max(0, asNumber(set?.weight, 0)),
          reps: Math.max(0, asNumber(set?.reps, 0)),
          rpe: set?.rpe != null ? asNumber(set.rpe, null) : null,
          rir: set?.rir != null ? asNumber(set.rir, null) : null,
          rest_seconds: Math.max(0, asNumber(set?.restSeconds ?? set?.rest, 0)),
          is_warmup: asBoolean(set?.isWarmup),
          is_dropset: asBoolean(set?.isDropset),
          is_amrap: asBoolean(set?.isAmrap || set?.isAMRAP),
          to_failure: asBoolean(set?.toFailure),
          completed_at: completedAt || startedAt || createdAt,
          created_at: startedAt || createdAt,
          updated_at: completedAt || createdAt,
        });
      });
    });
  });

  bodyWeight.forEach((row, index) => {
    bodyCounter += 1;
    const measuredAt = asNumber(row?.measuredAt, asNumber(row?.date, createdAt + index));
    bodyMeasurementRows.push({
      id: stableId('body', row?.id, bodyCounter),
      measured_at: measuredAt,
      bodyweight: asNumber(row?.weight ?? row?.bodyweight, null),
      waist: null,
      chest: null,
      arm: null,
      thigh: null,
      notes: asString(row?.notes || ''),
      created_at: measuredAt,
      updated_at: measuredAt,
    });
  });

  bodyMeasurements.forEach((row, index) => {
    bodyCounter += 1;
    const measuredAt = asNumber(row?.measuredAt, asNumber(row?.date, createdAt + index));
    bodyMeasurementRows.push({
      id: stableId('body_measure', row?.id, bodyCounter),
      measured_at: measuredAt,
      bodyweight: row?.bodyweight != null ? asNumber(row.bodyweight, null) : null,
      waist: row?.waist != null ? asNumber(row.waist, null) : null,
      chest: row?.chest != null ? asNumber(row.chest, null) : null,
      arm: row?.arm != null ? asNumber(row.arm, null) : null,
      thigh: row?.thigh != null ? asNumber(row.thigh, null) : null,
      notes: asString(row?.notes || ''),
      created_at: measuredAt,
      updated_at: measuredAt,
    });
  });

  if (appState && typeof appState === 'object') {
    Object.entries(appState).forEach(([key, value], index) => {
      const normalizedKey = asString(key, '').trim();
      if (!normalizedKey) return;
      const type =
        typeof value === 'boolean'
          ? 'boolean'
          : typeof value === 'number'
            ? 'number'
            : typeof value === 'string'
              ? 'string'
              : 'json';
      appSettingsRows.push({
        id: stableId('setting', normalizedKey, index + 1),
        key: normalizedKey,
        value: type === 'json' ? JSON.stringify(value) : asString(value),
        value_type: type,
        updated_at: createdAt,
      });
    });
  }

  return {
    convertedFrom: format,
    payload: {
      version: 1,
      type: EXPORT_TYPE,
      exportedAt: new Date().toISOString(),
      data: {
        exercises: exerciseRows,
        exercise_muscles: exerciseMuscleRows,
        plans: planRows,
        plan_days: planDayRows,
        plan_exercises: planExerciseRows,
        workouts: workoutRows,
        workout_exercises: workoutExerciseRows,
        workout_sets: workoutSetRows,
        body_measurements: bodyMeasurementRows,
        progress_photos: progressPhotoRows,
        app_settings: appSettingsRows,
      },
    },
  };
}

export async function exportDatabase() {
  const data = {};
  for (const table of EXPORT_TABLES) {
    const rows = await database.get(table).query().fetch();
    data[table] = rows.map((row) => cleanRow(row._raw));
  }

  return {
    version: 1,
    type: EXPORT_TYPE,
    exportedAt: new Date().toISOString(),
    data,
  };
}

export function validateImportPayload(payload) {
  if (!payload || typeof payload !== 'object') {
    return { valid: false, reason: 'Payload must be an object.' };
  }
  if (payload.type !== EXPORT_TYPE) {
    return { valid: false, reason: 'Invalid export type.' };
  }
  if (payload.version !== 1) {
    return { valid: false, reason: 'Unsupported export version.' };
  }
  if (!payload.data || typeof payload.data !== 'object') {
    return { valid: false, reason: 'Missing data payload.' };
  }
  for (const table of EXPORT_TABLES) {
    if (!Array.isArray(payload.data[table])) {
      return { valid: false, reason: `Missing table array: ${table}` };
    }
  }
  return { valid: true };
}

export function detectImportFormat(payload) {
  if (isWatermelonExport(payload)) return 'watermelon_v1';
  if (isLegacySQLiteBundle(payload)) return 'sqlite_v1';
  if (
    payload?.domains ||
    payload?.plans ||
    payload?.history ||
    payload?.bodyWeight ||
    payload?.customExercises ||
    payload?.payload?.plans
  ) {
    return 'legacy_async';
  }
  return 'unknown';
}

// Tables that hold user-generated data — cleared on every import.
const USER_DATA_TABLES = [
  'workout_sets',
  'workout_exercises',
  'workouts',
  'plan_exercises',
  'plan_days',
  'plans',
  'body_measurements',
  'progress_photos',
  'app_settings',
];

// Tables that hold the seeded exercise library — only cleared when the
// import payload itself contains custom exercises (so we don't force a
// 1,731-row delete + re-seed on every restore of a standard backup).
const EXERCISE_TABLES = ['exercise_muscles', 'exercises'];

async function batchDeleteTable(table) {
  const rows = await database.get(table).query().fetch();
  if (rows.length === 0) return;
  // One write() per table so each transaction stays small.
  await database.write(async () => {
    await database.batch(...rows.map((row) => row.prepareDestroyPermanently()));
  });
}

export async function clearDatabaseForImport({ clearExercises = false } = {}) {
  // Clear user data tables first (reverse FK order).
  for (const table of USER_DATA_TABLES) {
    await batchDeleteTable(table);
  }
  // Only nuke the exercise library when the import payload brings its own.
  if (clearExercises) {
    for (const table of EXERCISE_TABLES) {
      await batchDeleteTable(table);
    }
  }
}

async function batchImport(table, rows) {
  const collection = database.get(table);
  const prepared = rows.map((item) =>
    collection.prepareCreateFromDirtyRaw({
      ...item,
      _status: 'synced',
      _changed: '',
    })
  );
  if (prepared.length > 0) {
    await database.write(async () => {
      await database.batch(...prepared);
    });
  }
}

export async function importExercises(payload) {
  await batchImport('exercises', payload.data.exercises);
  await batchImport('exercise_muscles', payload.data.exercise_muscles);
}

export async function importPlans(payload) {
  await batchImport('plans', payload.data.plans);
  await batchImport('plan_days', payload.data.plan_days);
  await batchImport('plan_exercises', payload.data.plan_exercises);
}

export async function importWorkouts(payload) {
  await batchImport('workouts', payload.data.workouts);
  await batchImport('workout_exercises', payload.data.workout_exercises);
  await batchImport('workout_sets', payload.data.workout_sets);
  await batchImport('body_measurements', payload.data.body_measurements);
  await batchImport('progress_photos', payload.data.progress_photos || []);
}

export async function importDatabase(payload) {
  const validation = validateImportPayload(payload);
  if (!validation.valid) {
    throw new Error(validation.reason);
  }

  const hasImportedExercises = (payload?.data?.exercises || []).length > 0;

  // Only clear the exercise library when the import payload brings its own.
  // For standard user backups (no custom exercises) this avoids the expensive
  // 1,731-row delete + full re-seed cycle that would otherwise time out.
  await clearDatabaseForImport({ clearExercises: hasImportedExercises });

  if (hasImportedExercises) {
    await importExercises(payload);
  }
  await importPlans(payload);
  await importWorkouts(payload);
  await batchImport('app_settings', payload.data.app_settings);

  // Re-seed the built-in library only if we cleared it (or it was never seeded).
  if (hasImportedExercises) {
    await seedExercisesIfNeeded();
  }
}

export function normalizeLegacyPayload(payload) {
  const { convertedFrom, payload: converted } = convertLegacyToWatermelonExport(payload);
  const validation = validateImportPayload(converted);
  if (!validation.valid) throw new Error(validation.reason);
  return { convertedFrom, payload: converted };
}

export async function importAnyPayload(payload) {
  const format = detectImportFormat(payload);
  if (format === 'unknown') {
    throw new Error('Unsupported payload format.');
  }

  if (format === 'watermelon_v1') {
    await importDatabase(payload);
    return { importedFormat: format };
  }

  const normalized = normalizeLegacyPayload(payload);
  await importDatabase(normalized.payload);
  return {
    importedFormat: format,
    normalizedTo: 'watermelon_v1',
    convertedFrom: normalized.convertedFrom,
  };
}
