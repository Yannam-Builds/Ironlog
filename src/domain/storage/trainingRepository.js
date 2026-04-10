import AsyncStorage from '@react-native-async-storage/async-storage';
import { buildExerciseContributionCatalog } from '../intelligence/muscleContributionEngine';
import { buildExerciseProfileCatalog } from '../intelligence/exerciseProfileEngine';
import {
  SQLITE_MIGRATION_MARKER_KEY,
  ensureTrainingDatabase,
  runInTransaction,
  setAppMeta,
} from './trainingDatabase';

const LEGACY_KEYS = {
  plans: 'ironlog_plans',
  history: 'ironlog_history',
  bodyWeight: 'ironlog_bw',
  bodyMeasurements: '@ironlog/bodyMeasurements',
  customExercises: '@ironlog/customExercises',
};

function parseJson(raw, fallback) {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return fallback;
  }
}

function boolToInt(value) {
  return value ? 1 : 0;
}

function safeId(prefix, value) {
  return value || `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

function computeSetVolume(setItem) {
  const weight = Number(setItem?.weight || 0);
  const reps = Number(setItem?.reps || 0);
  if ((setItem?.type || 'normal') === 'warmup') return 0;
  if (!Number.isFinite(weight) || !Number.isFinite(reps) || weight <= 0 || reps <= 0) return 0;
  return weight * reps;
}

function computeSessionExerciseMetrics(exercise) {
  const sets = Array.isArray(exercise?.sets) ? exercise.sets : [];
  let totalVolume = 0;
  let workingSets = 0;
  let bestWeight = 0;
  let bestE1rm = 0;

  sets.forEach((setItem) => {
    const weight = Number(setItem?.weight || 0);
    const reps = Number(setItem?.reps || 0);
    const isWarmup = (setItem?.type || 'normal') === 'warmup';
    if (!isWarmup) workingSets += 1;
    totalVolume += computeSetVolume(setItem);
    if (weight > bestWeight) bestWeight = weight;
    if (weight > 0 && reps > 0) {
      const e1rm = Number(setItem?.orm || (weight * (1 + reps / 30)));
      if (e1rm > bestE1rm) bestE1rm = e1rm;
    }
  });

  return { totalVolume, workingSets, bestWeight, bestE1rm };
}

function hydratePlanDays(plan) {
  return Array.isArray(plan?.days) ? plan.days : [];
}

async function replacePlansInternal(db, plans = []) {
  await db.execAsync('DELETE FROM plan_day_exercises; DELETE FROM plan_days; DELETE FROM plans;');

  for (let planIndex = 0; planIndex < plans.length; planIndex += 1) {
    const plan = plans[planIndex];
    await db.runAsync(
      'INSERT INTO plans (id, name, active_order, metadata_json) VALUES (?, ?, ?, ?)',
      plan.id,
      plan.name || `Plan ${planIndex + 1}`,
      planIndex,
      JSON.stringify(plan || {})
    );

    const days = hydratePlanDays(plan);
    for (let dayIndex = 0; dayIndex < days.length; dayIndex += 1) {
      const day = days[dayIndex];
      await db.runAsync(
        `INSERT INTO plan_days (id, plan_id, name, label, color, tag, day_order, metadata_json)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        day.id,
        plan.id,
        day.name || `Day ${dayIndex + 1}`,
        day.label || null,
        day.color || null,
        day.tag || null,
        dayIndex,
        JSON.stringify(day || {})
      );

      const exercises = Array.isArray(day?.exercises) ? day.exercises : [];
      for (let exerciseIndex = 0; exerciseIndex < exercises.length; exerciseIndex += 1) {
        const exercise = exercises[exerciseIndex];
        await db.runAsync(
          `INSERT INTO plan_day_exercises (
             id, day_id, exercise_id, exercise_name, prescribed_sets, prescribed_reps,
             exercise_order, equipment, is_warmup, note, metadata_json
           ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          safeId('plan_ex', exercise.id || `${day.id}_${exerciseIndex}`),
          day.id,
          exercise.exerciseId || exercise.id || null,
          exercise.name || `Exercise ${exerciseIndex + 1}`,
          Number(exercise.sets || 0),
          Number(exercise.reps || 0),
          exerciseIndex,
          exercise.equipment || null,
          boolToInt(exercise.isWarmup),
          exercise.note || null,
          JSON.stringify(exercise || {})
        );
      }
    }
  }
}

async function replaceHistoryInternal(db, history = []) {
  await db.execAsync('DELETE FROM session_sets; DELETE FROM session_exercises; DELETE FROM pr_events; DELETE FROM session_insights; DELETE FROM workout_sessions;');

  const oldestFirst = history.slice().sort((a, b) => new Date(a?.date || 0) - new Date(b?.date || 0));
  for (let sessionIndex = 0; sessionIndex < oldestFirst.length; sessionIndex += 1) {
    const session = oldestFirst[sessionIndex];
    await insertHistorySessionInternal(db, session);
  }
}

async function insertHistorySessionInternal(db, session) {
  const exercises = Array.isArray(session?.exercises) ? session.exercises : [];
  const totalVolume = exercises.reduce((sum, exercise) => {
    const metrics = computeSessionExerciseMetrics(exercise);
    return sum + metrics.totalVolume;
  }, 0);
  const totalSets = exercises.reduce((sum, exercise) => sum + ((exercise?.sets || []).length || 0), 0);

  await db.runAsync(
    `INSERT INTO workout_sessions (
       id, occurred_at, plan_id, day_id, day_name, duration_seconds, total_sets,
       total_volume, is_deload, rating, performance_score, summary_text, updated_at
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    session.id,
    session.date,
    session.planId || null,
    session.dayId || null,
    session.dayName || null,
    Number(session.duration || 0),
    Number(session.sets || totalSets),
    Number(session.totalVolume || totalVolume),
    boolToInt(session.isDeload),
    session.rating == null ? null : Number(session.rating),
    session.performanceScore == null ? null : Number(session.performanceScore),
    session.summaryText || null,
    new Date().toISOString()
  );

  for (let exerciseIndex = 0; exerciseIndex < exercises.length; exerciseIndex += 1) {
    const exercise = exercises[exerciseIndex];
    const metrics = computeSessionExerciseMetrics(exercise);
    const sessionExerciseId = safeId('session_ex', exercise.id || `${session.id}_${exerciseIndex}`);
    await db.runAsync(
      `INSERT INTO session_exercises (
         id, session_id, exercise_id, exercise_name, exercise_order, prescribed_sets,
         prescribed_reps, equipment, primary_muscle, muscle_payload_json, set_count,
         total_volume, best_e1rm, best_weight, note, exercise_profile_json,
         progression_json, plateau_json, deload_json, substitution_json, metadata_json
       ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      sessionExerciseId,
      session.id,
      exercise.exerciseId || null,
      exercise.name || `Exercise ${exerciseIndex + 1}`,
      exerciseIndex,
      exercise.prescribedSets == null ? null : Number(exercise.prescribedSets),
      exercise.prescribedReps == null ? null : Number(exercise.prescribedReps),
      exercise.equipment || null,
      exercise.primaryMuscle || null,
      JSON.stringify(exercise.primaryMuscles || []),
      metrics.workingSets,
      metrics.totalVolume,
      metrics.bestE1rm,
      metrics.bestWeight,
      exercise.note || null,
      exercise.exerciseProfile ? JSON.stringify(exercise.exerciseProfile) : null,
      exercise.progressionSuggestion ? JSON.stringify(exercise.progressionSuggestion) : null,
      exercise.plateauSignal ? JSON.stringify(exercise.plateauSignal) : null,
      exercise.deloadSignal ? JSON.stringify(exercise.deloadSignal) : null,
      exercise.substitutionSuggestion ? JSON.stringify(exercise.substitutionSuggestion) : null,
      JSON.stringify(exercise || {})
    );

    const sets = Array.isArray(exercise?.sets) ? exercise.sets : [];
    for (let setIndex = 0; setIndex < sets.length; setIndex += 1) {
      const setItem = sets[setIndex];
      await db.runAsync(
        `INSERT INTO session_sets (
           id, session_exercise_id, set_order, weight, reps, type, rpe, rir, note, orm,
           target_weight, target_reps, completed, is_warmup, volume
         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        setItem.id || safeId('set', `${sessionExerciseId}_${setIndex}`),
        sessionExerciseId,
        setIndex,
        Number(setItem.weight || 0),
        Number(setItem.reps || 0),
        setItem.type || 'normal',
        setItem.rpe == null ? null : Number(setItem.rpe),
        setItem.rir == null ? null : Number(setItem.rir),
        setItem.note || null,
        Number(setItem.orm || 0),
        setItem.targetWeight == null ? null : Number(setItem.targetWeight),
        setItem.targetReps == null ? null : Number(setItem.targetReps),
        boolToInt(setItem.completed !== false),
        boolToInt((setItem.type || 'normal') === 'warmup'),
        computeSetVolume(setItem)
      );
    }
  }

  const prEvents = Array.isArray(session?.prEvents) ? session.prEvents : [];
  for (let eventIndex = 0; eventIndex < prEvents.length; eventIndex += 1) {
    const event = prEvents[eventIndex];
    await db.runAsync(
      `INSERT INTO pr_events (
         id, exercise_id, exercise_name, session_id, occurred_at, pr_type, value, payload_json
       ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      event.id || safeId('pr', `${session.id}_${eventIndex}`),
      event.exerciseId || null,
      event.exerciseName || null,
      session.id,
      session.date || new Date().toISOString(),
      event.type || null,
      Number(event.value || 0),
      JSON.stringify(event || {})
    );
  }

  await db.runAsync(
    `INSERT OR REPLACE INTO session_insights (
       session_id, generated_at, summary_text, wins, losses, payload_json
     ) VALUES (?, ?, ?, ?, ?, ?)`,
    session.id,
    new Date().toISOString(),
    session.summaryText || null,
    Number(session?.wins || prEvents.length || 0),
    Number(session?.losses || 0),
    JSON.stringify({
      performanceScore: session?.performanceScore ?? null,
      prEventCount: prEvents.length,
      summaryText: session?.summaryText || null,
    })
  );
}

async function replaceBodyWeightInternal(db, entries = []) {
  await db.execAsync('DELETE FROM bodyweight_entries;');
  const ordered = entries.slice().sort((a, b) => new Date(a?.date || 0) - new Date(b?.date || 0));
  for (let index = 0; index < ordered.length; index += 1) {
    const entry = ordered[index];
    await db.runAsync(
      'INSERT INTO bodyweight_entries (id, recorded_at, weight, source) VALUES (?, ?, ?, ?)',
      entry.id || safeId('bw', `${entry.date || index}`),
      entry.date || new Date().toISOString(),
      Number(entry.weight || 0),
      entry.source || 'manual'
    );
  }
}

async function replaceBodyMeasurementsInternal(db, entries = []) {
  await db.execAsync('DELETE FROM body_measurements;');
  const ordered = entries.slice().sort((a, b) => new Date(a?.date || 0) - new Date(b?.date || 0));
  for (let index = 0; index < ordered.length; index += 1) {
    const entry = ordered[index];
    await db.runAsync(
      'INSERT INTO body_measurements (id, recorded_at, payload_json) VALUES (?, ?, ?)',
      entry.id || safeId('bm', `${entry.date || index}`),
      entry.date || new Date().toISOString(),
      JSON.stringify(entry || {})
    );
  }
}

async function replaceCustomExercisesInternal(db, exercises = []) {
  await db.execAsync('DELETE FROM custom_exercises;');
  for (let index = 0; index < exercises.length; index += 1) {
    const exercise = exercises[index];
    await db.runAsync(
      'INSERT INTO custom_exercises (id, name, data_json) VALUES (?, ?, ?)',
      exercise.id || safeId('custom_ex', index),
      exercise.name || `Custom Exercise ${index + 1}`,
      JSON.stringify(exercise || {})
    );
  }
}

export async function replaceTrainingSnapshot({
  plans = [],
  history = [],
  bodyWeight = [],
  bodyMeasurements = [],
  customExercises = [],
} = {}) {
  await runInTransaction(async (db) => {
    await replacePlansInternal(db, plans);
    await replaceHistoryInternal(db, history);
    await replaceBodyWeightInternal(db, bodyWeight);
    await replaceBodyMeasurementsInternal(db, bodyMeasurements);
    await replaceCustomExercisesInternal(db, customExercises);
  });
}

function inflatePlanDayExercise(row) {
  const metadata = parseJson(row.metadata_json, {});
  return {
    ...metadata,
    id: metadata.id || row.id,
    exerciseId: metadata.exerciseId || row.exercise_id,
    name: metadata.name || row.exercise_name,
    sets: metadata.sets ?? row.prescribed_sets,
    reps: metadata.reps ?? row.prescribed_reps,
    equipment: metadata.equipment || row.equipment,
    isWarmup: metadata.isWarmup != null ? metadata.isWarmup : !!row.is_warmup,
    note: metadata.note || row.note || null,
  };
}

export async function loadPlansFromDb() {
  const db = await ensureTrainingDatabase();
  const planRows = await db.getAllAsync('SELECT * FROM plans ORDER BY active_order ASC, rowid ASC');
  const dayRows = await db.getAllAsync('SELECT * FROM plan_days ORDER BY day_order ASC, rowid ASC');
  const exerciseRows = await db.getAllAsync('SELECT * FROM plan_day_exercises ORDER BY exercise_order ASC, rowid ASC');

  const daysByPlan = {};
  dayRows.forEach((row) => {
    const metadata = parseJson(row.metadata_json, {});
    const day = {
      ...metadata,
      id: metadata.id || row.id,
      name: metadata.name || row.name,
      label: metadata.label || row.label || null,
      color: metadata.color || row.color || null,
      tag: metadata.tag || row.tag || null,
      exercises: [],
    };
    if (!daysByPlan[row.plan_id]) daysByPlan[row.plan_id] = [];
    daysByPlan[row.plan_id].push(day);
  });

  const dayById = {};
  Object.values(daysByPlan).flat().forEach((day) => {
    dayById[day.id] = day;
  });

  exerciseRows.forEach((row) => {
    if (!dayById[row.day_id]) return;
    dayById[row.day_id].exercises.push(inflatePlanDayExercise(row));
  });

  return planRows.map((row) => {
    const metadata = parseJson(row.metadata_json, {});
    return {
      ...metadata,
      id: metadata.id || row.id,
      name: metadata.name || row.name,
      days: daysByPlan[row.id] || [],
    };
  });
}

export async function loadHistoryFromDb() {
  const db = await ensureTrainingDatabase();
  const sessionRows = await db.getAllAsync('SELECT * FROM workout_sessions ORDER BY occurred_at DESC');
  const exerciseRows = await db.getAllAsync('SELECT * FROM session_exercises ORDER BY exercise_order ASC');
  const setRows = await db.getAllAsync('SELECT * FROM session_sets ORDER BY set_order ASC');
  const prRows = await db.getAllAsync('SELECT * FROM pr_events ORDER BY occurred_at DESC');

  const setsByExercise = {};
  setRows.forEach((row) => {
    if (!setsByExercise[row.session_exercise_id]) setsByExercise[row.session_exercise_id] = [];
    setsByExercise[row.session_exercise_id].push({
      id: row.id,
      weight: row.weight,
      reps: row.reps,
      type: row.type || 'normal',
      rpe: row.rpe,
      rir: row.rir,
      note: row.note,
      orm: row.orm,
      targetWeight: row.target_weight,
      targetReps: row.target_reps,
      completed: !!row.completed,
    });
  });

  const exercisesBySession = {};
  exerciseRows.forEach((row) => {
    const metadata = parseJson(row.metadata_json, {});
    const exercise = {
      ...metadata,
      id: metadata.id || row.id,
      exerciseId: metadata.exerciseId || row.exercise_id || null,
      name: metadata.name || row.exercise_name,
      prescribedSets: metadata.prescribedSets ?? row.prescribed_sets,
      prescribedReps: metadata.prescribedReps ?? row.prescribed_reps,
      equipment: metadata.equipment || row.equipment || null,
      primaryMuscle: metadata.primaryMuscle || row.primary_muscle || null,
      primaryMuscles: metadata.primaryMuscles || parseJson(row.muscle_payload_json, []),
      note: metadata.note || row.note || null,
      sets: setsByExercise[row.id] || [],
      exerciseProfile: parseJson(row.exercise_profile_json, metadata.exerciseProfile || null),
      progressionSuggestion: parseJson(row.progression_json, metadata.progressionSuggestion || null),
      plateauSignal: parseJson(row.plateau_json, metadata.plateauSignal || null),
      deloadSignal: parseJson(row.deload_json, metadata.deloadSignal || null),
      substitutionSuggestion: parseJson(row.substitution_json, metadata.substitutionSuggestion || null),
    };
    if (!exercisesBySession[row.session_id]) exercisesBySession[row.session_id] = [];
    exercisesBySession[row.session_id].push(exercise);
  });

  const prBySession = {};
  prRows.forEach((row) => {
    if (!prBySession[row.session_id]) prBySession[row.session_id] = [];
    const payload = parseJson(row.payload_json, {});
    prBySession[row.session_id].push({
      ...payload,
      id: payload.id || row.id,
      type: payload.type || row.pr_type || null,
      value: payload.value ?? row.value,
      exerciseId: payload.exerciseId || row.exercise_id || null,
      exerciseName: payload.exerciseName || row.exercise_name || null,
    });
  });

  return sessionRows.map((row) => ({
    id: row.id,
    date: row.occurred_at,
    planId: row.plan_id,
    dayId: row.day_id,
    dayName: row.day_name,
    duration: row.duration_seconds,
    sets: row.total_sets,
    totalVolume: row.total_volume,
    isDeload: !!row.is_deload,
    rating: row.rating,
    performanceScore: row.performance_score,
    summaryText: row.summary_text,
    prEvents: prBySession[row.id] || [],
    exercises: exercisesBySession[row.id] || [],
  }));
}

export async function loadBodyWeightFromDb() {
  const db = await ensureTrainingDatabase();
  const rows = await db.getAllAsync('SELECT * FROM bodyweight_entries ORDER BY recorded_at DESC');
  return rows.map((row) => ({ id: row.id, date: row.recorded_at, weight: row.weight, source: row.source || 'manual' }));
}

export async function loadBodyMeasurementsFromDb() {
  const db = await ensureTrainingDatabase();
  const rows = await db.getAllAsync('SELECT * FROM body_measurements ORDER BY recorded_at DESC');
  return rows.map((row) => parseJson(row.payload_json, { id: row.id, date: row.recorded_at }));
}

export async function loadCustomExercisesFromDb() {
  const db = await ensureTrainingDatabase();
  const rows = await db.getAllAsync('SELECT * FROM custom_exercises ORDER BY name ASC');
  return rows.map((row) => parseJson(row.data_json, { id: row.id, name: row.name }));
}

export async function loadTrainingSnapshot() {
  const [plans, history, bodyWeight, bodyMeasurements, customExercises] = await Promise.all([
    loadPlansFromDb(),
    loadHistoryFromDb(),
    loadBodyWeightFromDb(),
    loadBodyMeasurementsFromDb(),
    loadCustomExercisesFromDb(),
  ]);
  return { plans, history, bodyWeight, bodyMeasurements, customExercises };
}

export async function savePlansToDb(plans = []) {
  await runInTransaction((db) => replacePlansInternal(db, plans));
}

export async function addHistorySessionToDb(session) {
  await runInTransaction((db) => insertHistorySessionInternal(db, session));
}

export async function clearHistoryInDb() {
  const db = await ensureTrainingDatabase();
  await db.execAsync('DELETE FROM session_sets; DELETE FROM session_exercises; DELETE FROM workout_sessions;');
}

export async function saveBodyWeightToDb(entries = []) {
  await runInTransaction((db) => replaceBodyWeightInternal(db, entries));
}

export async function syncCustomExercisesToDb(exercises = []) {
  await runInTransaction((db) => replaceCustomExercisesInternal(db, exercises));
}

export async function upsertCustomExerciseToDb(exercise) {
  const db = await ensureTrainingDatabase();
  await db.runAsync(
    `INSERT INTO custom_exercises (id, name, data_json)
     VALUES (?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET name = excluded.name, data_json = excluded.data_json`,
    exercise.id,
    exercise.name,
    JSON.stringify(exercise || {})
  );
}

export async function deleteCustomExerciseFromDb(id) {
  const db = await ensureTrainingDatabase();
  await db.runAsync('DELETE FROM custom_exercises WHERE id = ?', id);
}

export async function seedExerciseIntelligence(exercises = []) {
  if (!Array.isArray(exercises) || exercises.length === 0) return;
  const db = await ensureTrainingDatabase();
  const profileCatalog = buildExerciseProfileCatalog(exercises);
  const contributionCatalog = buildExerciseContributionCatalog(exercises, profileCatalog);

  await runInTransaction(async (txn) => {
    await txn.execAsync('DELETE FROM exercise_profiles; DELETE FROM exercise_muscle_contributions;');
    for (const exercise of exercises) {
      const id = exercise.id || exercise.exerciseId || exercise.name;
      const profile = profileCatalog.byId[id] || profileCatalog.byName?.[String(exercise.name || '').toLowerCase()];
      const contribution = contributionCatalog.byId[id] || contributionCatalog.byName?.[String(exercise.name || '').toLowerCase()];
      if (!profile || !contribution) continue;
      await txn.runAsync(
        `INSERT INTO exercise_profiles (
           exercise_id, exercise_name, family, equipment_class, compound, unilateral,
           load_model, microload_step, rep_ceiling, confidence, profile_json
         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        id,
        exercise.name,
        profile.family,
        profile.equipmentClass,
        boolToInt(profile.compound),
        boolToInt(profile.unilateral),
        profile.loadModel,
        profile.microloadStep,
        profile.repCeiling,
        profile.confidence,
        JSON.stringify(profile)
      );

      const primarySet = new Set(contribution.primaryMuscles || []);
      for (const [muscleKey, weight] of Object.entries(contribution.contributions || {})) {
        await txn.runAsync(
          `INSERT INTO exercise_muscle_contributions (
             exercise_id, muscle_key, weight, confidence, source, is_primary
           ) VALUES (?, ?, ?, ?, ?, ?)`,
          id,
          muscleKey,
          weight,
          contribution.confidence,
          contribution.source,
          boolToInt(primarySet.has(muscleKey))
        );
      }
    }
  });
}

export async function getRecentComparisonUsage(limit = 5) {
  const db = await ensureTrainingDatabase();
  const rows = await db.getAllAsync(
    'SELECT object_name, category, total_kg, context_label, used_at FROM comparison_usage_log ORDER BY used_at DESC LIMIT ?',
    limit
  );
  return rows.map((row) => ({
    objectName: row.object_name,
    category: row.category,
    totalKg: row.total_kg,
    contextLabel: row.context_label,
    usedAt: row.used_at,
    rotationCategory: row.category === 'industrial' ? 'industrial' : row.category === 'animals' ? 'animals' : row.category === 'vehicles' ? 'vehicles' : 'objects',
  }));
}

export async function recordComparisonUsage({
  objectName,
  category,
  totalKg,
  contextLabel = 'workout_completion',
  usedAt = new Date().toISOString(),
} = {}) {
  if (!objectName) return;
  const db = await ensureTrainingDatabase();
  await db.runAsync(
    'INSERT INTO comparison_usage_log (id, used_at, object_name, category, total_kg, context_label) VALUES (?, ?, ?, ?, ?, ?)',
    safeId('comparison', usedAt),
    usedAt,
    objectName,
    category,
    Number(totalKg || 0),
    contextLabel
  );
}

export async function migrateLegacyAsyncStorageToSQLite({ force = false } = {}) {
  const marker = await AsyncStorage.getItem(SQLITE_MIGRATION_MARKER_KEY);
  if (marker && !force) return false;

  const pairs = await AsyncStorage.multiGet(Object.values(LEGACY_KEYS));
  const map = Object.fromEntries(pairs);
  const plans = parseJson(map[LEGACY_KEYS.plans], []);
  const history = parseJson(map[LEGACY_KEYS.history], []);
  const bodyWeight = parseJson(map[LEGACY_KEYS.bodyWeight], []);
  const bodyMeasurements = parseJson(map[LEGACY_KEYS.bodyMeasurements], []);
  const customExercises = parseJson(map[LEGACY_KEYS.customExercises], []);

  await replaceTrainingSnapshot({ plans, history, bodyWeight, bodyMeasurements, customExercises });

  const db = await ensureTrainingDatabase();
  const [planCount, sessionCount, bodyWeightCount] = await Promise.all([
    db.getFirstAsync('SELECT COUNT(*) AS count FROM plans'),
    db.getFirstAsync('SELECT COUNT(*) AS count FROM workout_sessions'),
    db.getFirstAsync('SELECT COUNT(*) AS count FROM bodyweight_entries'),
  ]);

  const counts = {
    plans: Number(planCount?.count || 0),
    sessions: Number(sessionCount?.count || 0),
    bodyWeight: Number(bodyWeightCount?.count || 0),
  };

  if (counts.plans !== plans.length || counts.sessions !== history.length || counts.bodyWeight !== bodyWeight.length) {
    throw new Error('SQLite migration verification failed');
  }

  await AsyncStorage.setItem(SQLITE_MIGRATION_MARKER_KEY, 'true');
  await setAppMeta('legacy_import_completed_at', new Date().toISOString());
  await setAppMeta('legacy_import_counts', JSON.stringify(counts));
  return true;
}
