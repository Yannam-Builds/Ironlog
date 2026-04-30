
import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { AppState, InteractionManager } from 'react-native';
import { buildExerciseMap } from '../domain/intelligence/muscleContributionEngine';
import { EXERCISES } from '../data/exerciseLibrary';
import { setHapticsEnabled } from '../services/hapticsEngine';
import {
  buildWeeklySummary,
  computeStreaks,
  evaluateMilestones,
} from '../domain/intelligence/engagementEngine';
import { computeConsistencyMetrics } from '../domain/intelligence/performanceEngine';
import { buildReadinessSuggestions } from '../domain/intelligence/recoveryReadinessEngine';
import {
  buildDefaultNotificationCandidates,
  scheduleSmartNotification,
} from '../services/notificationScheduler';
import {
  configureBackupPassphrase,
  createRollbackSnapshot,
  loadBackupConfig,
  loadBackupStatus,
  loadNotificationSettings,
  queueBackup,
  runBackupNow,
  saveBackupConfig,
  saveNotificationSettings,
  setBackupDirtyFlag,
} from '../services/backupService';
import { DEFAULT_BACKUP_CONFIG, DEFAULT_BACKUP_STATUS, DEFAULT_NOTIFICATION_SETTINGS } from '../services/backupConstants';
import { cancelBackupJob, scheduleBackupJob } from '../services/BackupScheduler';
import { getExerciseIndex } from '../services/ExerciseLibraryService';
import { database } from '../db/database';
import {
  createCompletedWorkout,
  updateWorkoutMetadata,
  deleteWorkoutWithData,
  clearCompletedWorkouts,
} from '../db/repositories/workoutRepository';
import { addBodyMeasurement, deleteBodyMeasurement as deleteBodyMeasurementRow } from '../db/repositories/bodyMeasurementRepository';
import {
  getSetting as getWatermelonSetting,
  setSetting as setWatermelonSetting,
  removeSetting as removeWatermelonSetting,
} from '../db/repositories/settingsRepository';
import { importDatabase as importWatermelonDatabase } from '../db/repositories/importExportRepository';
import { mapHistoryFromWatermelonRows } from '../db/adapters/sessionAdapter';

function isoWeekKey(dateStr) {
  const d = new Date(dateStr);
  const jan4 = new Date(d.getFullYear(), 0, 4);
  const week = Math.ceil(((d - jan4) / 86400000 + jan4.getDay() + 1) / 7);
  return `${d.getFullYear()}-W${String(week).padStart(2, '0')}`;
}

function normalizeMuscleKey(value) {
  return String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[_\s]+/g, '_');
}

function resolveSessionPrimaryMuscle(exercise) {
  const values = [];
  if (Array.isArray(exercise?.primaryMuscles)) values.push(...exercise.primaryMuscles);
  else if (exercise?.primaryMuscles) values.push(exercise.primaryMuscles);
  if (exercise?.primaryMuscle) values.push(exercise.primaryMuscle);
  if (exercise?.primary) values.push(exercise.primary);
  if (exercise?.muscle) values.push(exercise.muscle);
  if (exercise?.target) values.push(exercise.target);
  if (exercise?.targetMuscle) values.push(exercise.targetMuscle);
  return values.find((value) => String(value || '').trim()) || 'other';
}

function hasEntryToday(list = [], field = 'date') {
  const today = new Date().toISOString().slice(0, 10);
  return (Array.isArray(list) ? list : []).some((item) => String(item?.[field] || '').slice(0, 10) === today);
}

function normalizeWeeklyGoalDays(value, fallback = 4) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(1, Math.min(7, Math.round(parsed)));
}

async function buildIndexUpdatesForSession(session) {
  if (!session?.exercises || !session?.date) return null;
  const prIndex = (await getWatermelonSetting('pr_index_v1')) || {};
  const volumeIndex = (await getWatermelonSetting('volume_index_v1')) || {};
  const lastPerf = (await getWatermelonSetting('last_performance_v1')) || {};

  const dateStr = session.date.split('T')[0];
  const weekKey = isoWeekKey(session.date);

  for (const ex of session.exercises) {
    if (!ex.name || !ex.sets) continue;
    const exId = ex.exerciseId || ex.name;
    const workingSets = ex.sets.filter(s => s.type !== 'warmup');

    lastPerf[exId] = {
      date: dateStr,
      workoutId: session.id,
      sets: workingSets.map(s => ({
        weight: s.weight, reps: s.reps,
        type: s.type || 'normal', rpe: s.rpe || null,
      })),
    };

    if (!session.isDeload) {
      const prSets = ex.sets.filter(s =>
        ['normal', 'failure', 'amrap'].includes(s.type || 'normal')
      );
      if (!prIndex[exId]) prIndex[exId] = [];
      for (const s of prSets) {
        if (!s.weight || !s.reps) continue;
        const e1rm = Math.round(s.weight * (1 + s.reps / 30) * 10) / 10;
        prIndex[exId].push({ date: dateStr, weight: s.weight, reps: s.reps, e1rm });
      }
      prIndex[exId].sort((a, b) => a.date.localeCompare(b.date));

      if (workingSets.length) {
        const muscle = resolveSessionPrimaryMuscle(ex);
        const normalized = normalizeMuscleKey(muscle);
        if (!volumeIndex[weekKey]) volumeIndex[weekKey] = {};
        volumeIndex[weekKey][normalized] = (volumeIndex[weekKey][normalized] || 0) + workingSets.length;
      }
    }
  }

  return { prIndex, volumeIndex, lastPerf };
}

async function buildIndexUpdatesForHistory(historyList = []) {
  const prIndex = {};
  const volumeIndex = {};
  const lastPerf = {};
  const ordered = (Array.isArray(historyList) ? historyList : [])
    .slice()
    .sort((a, b) => new Date(a?.date || 0) - new Date(b?.date || 0));

  for (const session of ordered) {
    if (!session?.date || !Array.isArray(session?.exercises)) continue;
    const dateStr = session.date.split('T')[0];
    const weekKey = isoWeekKey(session.date);

    for (const ex of session.exercises) {
      if (!ex?.name || !Array.isArray(ex?.sets)) continue;
      const exId = ex.exerciseId || ex.name;
      const workingSets = ex.sets.filter((setItem) => (setItem?.type || 'normal') !== 'warmup');

      lastPerf[exId] = {
        date: dateStr,
        workoutId: session.id,
        sets: workingSets.map((setItem) => ({
          weight: setItem.weight,
          reps: setItem.reps,
          type: setItem.type || 'normal',
          rpe: setItem.rpe || null,
        })),
      };

      if (session.isDeload) continue;

      const prSets = ex.sets.filter((setItem) => ['normal', 'failure', 'amrap'].includes(setItem?.type || 'normal'));
      if (!prIndex[exId]) prIndex[exId] = [];
      for (const setItem of prSets) {
        const weight = Number(setItem?.weight || 0);
        const reps = Number(setItem?.reps || 0);
        if (!weight || !reps) continue;
        const e1rm = Math.round(weight * (1 + reps / 30) * 10) / 10;
        prIndex[exId].push({ date: dateStr, weight, reps, e1rm });
      }
      prIndex[exId].sort((a, b) => a.date.localeCompare(b.date));

      if (workingSets.length) {
        const muscle = resolveSessionPrimaryMuscle(ex);
        const normalized = normalizeMuscleKey(muscle);
        if (!volumeIndex[weekKey]) volumeIndex[weekKey] = {};
        volumeIndex[weekKey][normalized] = (volumeIndex[weekKey][normalized] || 0) + workingSets.length;
      }
    }
  }

  return { prIndex, volumeIndex, lastPerf };
}

const HEAVY = new Set([
  'Weighted Pull-Up', 'Weighted Pull-Up or Lat Pulldown',
  'Romanian Deadlift', 'Bulgarian Split Squat', 'DB Shrugs', 'Incline Smith Press',
  'Barbell Shrugs', 'Deadlift', 'Back Squat', 'Front Squat',
]);

export const AppContext = createContext();
export const useApp = () => useContext(AppContext);

const DEFAULT_GYM_PROFILE = {
  id: 'default',
  name: 'Standard',
  barWeight: 20,
  plates: [20, 15, 10, 5, 2.5, 1.25].map(w => ({ weight: w, quantity: 2 })),
  isDefault: true,
};
const HAPTICS_RECOVERY_MIGRATION_KEY = '@ironlog/hapticsRecoveryMigrationV1';
const EXERCISE_MAP_VERSION = 1;
const WM_GYM_PROFILES_KEY = 'gym_profiles_v1';
const WM_ACTIVE_GYM_PROFILE_ID_KEY = 'active_gym_profile_id_v1';
const WM_EXERCISE_NOTES_KEY = 'exercise_notes_v1';
const WM_MANUAL_RECOVERY_KEY = 'manual_recovery_v1';
const WM_MILESTONE_UNLOCKS_KEY = 'milestone_unlocks_v1';

const WM_EXERCISES = database.get('exercises');
const WM_PLANS = database.get('plans');
const WM_PLAN_DAYS = database.get('plan_days');
const WM_PLAN_EXERCISES = database.get('plan_exercises');
const WM_WORKOUTS = database.get('workouts');
const WM_WORKOUT_EXERCISES = database.get('workout_exercises');
const WM_WORKOUT_SETS = database.get('workout_sets');
const WM_BODY = database.get('body_measurements');

function mapPlansFromWatermelon(plansRows, dayRows, dayExerciseRows, exerciseRows) {
  const exById = new Map(exerciseRows.map((e) => [e.id, e]));
  const dayByPlan = new Map();
  dayRows.forEach((d) => {
    const pid = d._raw.plan_id;
    if (!dayByPlan.has(pid)) dayByPlan.set(pid, []);
    dayByPlan.get(pid).push(d);
  });
  const exByDay = new Map();
  dayExerciseRows.forEach((pe) => {
    const did = pe._raw.plan_day_id;
    if (!exByDay.has(did)) exByDay.set(did, []);
    exByDay.get(did).push(pe);
  });
  return plansRows
    .slice()
    .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
    .map((p) => ({
      id: p.id,
      name: p.name,
      goal: p.goal,
      description: p.description || '',
      isActive: !!p.isActive,
      days: (dayByPlan.get(p.id) || [])
        .slice()
        .sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0))
        .map((d) => ({
          id: d.id,
          name: d.name,
          color: d.color,
          exercises: (exByDay.get(d.id) || [])
            .slice()
            .sort((a, b) => (a.orderIndex || 0) - (b.orderIndex || 0))
            .map((pe) => {
              const ex = exById.get(pe._raw.exercise_id);
              return {
                id: pe.id,
                exerciseId: pe._raw.exercise_id,
                name: ex?.name || 'Unknown',
                sets: pe.sets,
                reps: pe.reps,
                restSeconds: pe.restSeconds,
                supersetGroup: pe.supersetGroup || null,
                isWarmup: !!pe.isWarmup,
                notes: pe.notes || '',
              };
            }),
        })),
    }));
}

function mapHistoryFromWatermelon(workoutRows, workoutExerciseRows, setRows, exerciseRows) {
  return mapHistoryFromWatermelonRows(workoutRows, workoutExerciseRows, setRows, exerciseRows);
}

export function AppContextProvider({ children }) {
  const [plans, setPlans] = useState([]);
  const [history, setHistory] = useState([]);
  const [pb, setPb] = useState({});
  const [bodyWeight, setBodyWeight] = useState([]);
  const [exerciseNotes, setExerciseNotes] = useState({});
  const [settings, setSettings] = useState({
    weightUnit: 'kg', defaultRestNormal: 120, defaultRestHeavy: 180, barWeight: 20,
    effortTracking: 'off', keepAwake: true, warmupScheme: 'standard', hapticFeedback: true,
    goalMode: 'hypertrophy',
    weeklyGoalDays: 4,
    completedWorkoutCount: 0, lastRatePromptCount: 0, neverAskToRate: false,
  });
  const [gymProfiles, setGymProfiles] = useState([DEFAULT_GYM_PROFILE]);
  const [activeGymProfileId, setActiveGymProfileIdState] = useState('default');
  const [initialized, setInitialized] = useState(false);
  const [onboardingComplete, setOnboardingCompleteState] = useState(false);
  const [exerciseMap, setExerciseMap] = useState({});
  const [manualRecoveryInput, setManualRecoveryInput] = useState(null);
  const [milestoneUnlocks, setMilestoneUnlocks] = useState({});
  const [latestMilestones, setLatestMilestones] = useState([]);
  const [engagementSnapshot, setEngagementSnapshot] = useState({
    streaks: { training: { current: 0, longest: 0 }, logging: { current: 0, longest: 0 }, bodyweight: { current: 0, longest: 0 } },
    weeklySummary: null,
  });
   const [backupConfig, setBackupConfigState] = useState(DEFAULT_BACKUP_CONFIG);
   const [backupStatus, setBackupStatusState] = useState(DEFAULT_BACKUP_STATUS);
   const [notificationSettings, setNotificationSettingsState] = useState(DEFAULT_NOTIFICATION_SETTINGS);
   const appStateRef = useRef(AppState.currentState);

  useEffect(() => { init(); }, []);
  useEffect(() => {
    setHapticsEnabled(settings?.hapticFeedback !== false);
  }, [settings?.hapticFeedback]);

  const refreshBackupState = async () => {
    const [config, status, notifications] = await Promise.all([
      loadBackupConfig(),
      loadBackupStatus(),
      loadNotificationSettings(),
    ]);
    const nextStatus = {
      ...status,
      enabled: config.enabled,
      driveLinked: false,
      driveConfigured: false,
      driveEmail: null,
      driveRedirectUri: null,
      driveConfigMessage: 'Google Drive backup is disabled in this build.',
      driveFolderId: null,
      driveFolderName: null,
      driveMode: 'appdata',
    };
    setBackupConfigState(config);
    setBackupStatusState(nextStatus);
    setNotificationSettingsState(notifications);
    return { config, status: nextStatus, notifications };
  };

  const flagDirty = async (reason) => {
    setBackupStatusState((prev) => ({ ...prev, dirty: true, queuedReason: reason }));
    try {
      const nextStatus = await setBackupDirtyFlag(reason);
      setBackupStatusState(nextStatus);
    } catch (e) {
      console.warn('Backup dirty flag error:', e);
    }
  };

  const queueAutoBackup = async (reason, delayMs) => {
    try {
      const config = await loadBackupConfig();
      if (!config.enabled || !config.passphraseConfigured) return;
      await queueBackup(reason);
      const scheduled = await scheduleBackupJob(reason, delayMs);
      if (!scheduled) {
        await runBackupNow({ reason, syncToDrive: false });
      }
      await refreshBackupState();
    } catch (e) {
      console.warn('Auto backup scheduling failed:', e);
    }
  };

  const init = async () => {
    try {
      const [
        wmPlanRows, wmDayRows, wmPlanExerciseRows, wmExerciseRows,
        wmWorkoutRows, wmWorkoutExerciseRows, wmWorkoutSetRows,
        wmBodyRows,
      ] = await Promise.all([
        WM_PLANS.query().fetch(),
        WM_PLAN_DAYS.query().fetch(),
        WM_PLAN_EXERCISES.query().fetch(),
        WM_EXERCISES.query().fetch(),
        WM_WORKOUTS.query().fetch(),
        WM_WORKOUT_EXERCISES.query().fetch(),
        WM_WORKOUT_SETS.query().fetch(),
        WM_BODY.query().fetch(),
      ]);

      const wmPlans = mapPlansFromWatermelon(wmPlanRows, wmDayRows, wmPlanExerciseRows, wmExerciseRows);
      const wmHistory = mapHistoryFromWatermelon(
        wmWorkoutRows.filter((w) => String(w.status || '') === 'completed'),
        wmWorkoutExerciseRows,
        wmWorkoutSetRows,
        wmExerciseRows
      );
      const wmBodyWeight = wmBodyRows
        .filter((row) => row.bodyweight != null)
        .sort((a, b) => (b.measuredAt || 0) - (a.measuredAt || 0))
        .map((row) => ({
          id: row.id,
          date: new Date(Number(row.measuredAt) || Date.now()).toISOString(),
          weight: Number(row.bodyweight) || 0,
          note: row.notes || '',
        }));

      const wmSettings = await getWatermelonSetting('ironlog_settings');
      const wmNotes = await getWatermelonSetting(WM_EXERCISE_NOTES_KEY);
      const wmManualRecovery = await getWatermelonSetting(WM_MANUAL_RECOVERY_KEY);
      const wmMilestones = await getWatermelonSetting(WM_MILESTONE_UNLOCKS_KEY);
      const wmProfiles = await getWatermelonSetting(WM_GYM_PROFILES_KEY);
      const wmActiveProfile = await getWatermelonSetting(WM_ACTIVE_GYM_PROFILE_ID_KEY);
      const wmOnboarding = await getWatermelonSetting('onboarding_complete');
      const fallbackPb = await getWatermelonSetting('ironlog_pb');

      setPlans(wmPlans);
      setHistory(wmHistory);
      setBodyWeight(wmBodyWeight);
      if (wmSettings && typeof wmSettings === 'object') {
        setSettings((prev) => ({
          ...prev,
          ...wmSettings,
          weeklyGoalDays: normalizeWeeklyGoalDays(wmSettings.weeklyGoalDays, prev.weeklyGoalDays || 4),
        }));
      }
      if (wmNotes && typeof wmNotes === 'object') setExerciseNotes(wmNotes);
      if (wmManualRecovery && typeof wmManualRecovery === 'object') setManualRecoveryInput(wmManualRecovery);
      if (wmMilestones && typeof wmMilestones === 'object') setMilestoneUnlocks(wmMilestones);
      if (Array.isArray(wmProfiles) && wmProfiles.length) setGymProfiles(wmProfiles);
      if (wmActiveProfile) setActiveGymProfileIdState(wmActiveProfile);
      if (typeof wmOnboarding === 'boolean') setOnboardingCompleteState(wmOnboarding);
      if (fallbackPb && typeof fallbackPb === 'object') setPb(fallbackPb);
    } catch (e) { console.warn('Init error:', e); }
    setInitialized(true);
    // Non-critical boot work deferred until after first paint and interactions settle.
    InteractionManager.runAfterInteractions(async () => {
      try {
        let emap = await getWatermelonSetting('exercise_map_v1');
        const storedMapVersion = Number(await getWatermelonSetting('exercise_map_version_v1') || 0);
        if (!emap || Object.keys(emap).length === 0 || storedMapVersion < EXERCISE_MAP_VERSION) {
          const libraryIndex = await getExerciseIndex().catch(() => null);
          emap = buildExerciseMap(libraryIndex || EXERCISES);
          await Promise.all([
            setWatermelonSetting('exercise_map_v1', emap, 'json'),
            setWatermelonSetting('exercise_map_version_v1', EXERCISE_MAP_VERSION, 'number'),
          ]);
        }
        setExerciseMap(emap || {});
      } catch (e) {
        console.warn('Exercise map boot hydration failed:', e);
      }
      await refreshBackupState().catch((e) => console.warn('Backup state refresh failed:', e));
    });
  };

  useEffect(() => {
    const streaks = computeStreaks({ history, bodyWeight });
    const weeklySummary = buildWeeklySummary({ history, bodyWeight });
    setEngagementSnapshot({ streaks, weeklySummary });
  }, [history, bodyWeight]);

  useEffect(() => {
    if (!initialized || notificationSettings?.enabled !== true) return;
    const run = async () => {
      const consistency = computeConsistencyMetrics({ history, activePlan: plans?.[0], bodyWeight });
      const recoverySuggestions = manualRecoveryInput?.soreness >= 4
        ? ['Soreness is elevated today. Keep loading conservative and prioritize recovery.']
        : buildReadinessSuggestions({ readiness: {} });
      const candidates = buildDefaultNotificationCandidates({
        settings: notificationSettings,
        workoutsLast7d: consistency.workoutsLast7d,
        bodyweightLoggingConsistency: consistency.bodyweightLoggingConsistency,
        recoverySuggestions,
        streaks: engagementSnapshot.streaks,
        newMilestones: latestMilestones,
        history,
        bodyWeightEntries: bodyWeight,
        backupStatus,
        activePlan: plans?.[0] || null,
      });
      const chosen = await scheduleSmartNotification({
        settings: notificationSettings,
        activePlan: plans?.[0] || null,
        alreadyActionedTopics: [
          ...(hasEntryToday(history, 'date') ? ['training', 'streak'] : []),
          ...(hasEntryToday(bodyWeight, 'date') ? ['bodyweight'] : []),
        ],
        updateSettings: async (nextSettings) => {
          const next = await saveNotificationSettings(nextSettings);
          setNotificationSettingsState(next);
          return next;
        },
        candidates,
      });
      if (chosen && latestMilestones.length) setLatestMilestones([]);
    };
    run().catch(() => {});
  }, [initialized, history.length, bodyWeight.length, manualRecoveryInput, notificationSettings, plans, engagementSnapshot.streaks, latestMilestones, backupStatus]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (nextState) => {
      const wasBackgrounded = /inactive|background/.test(appStateRef.current || '');
      const enteringBackground = /inactive|background/.test(nextState);
      if (enteringBackground) {
        if (backupStatus.dirty && backupConfig.enabled && backupConfig.autoBackupOnBackground && backupConfig.passphraseConfigured) {
          queueAutoBackup('app_background', 60000).catch((error) => console.warn('Background backup queue failed:', error));
        }
      }
      if (wasBackgrounded && nextState === 'active') {
        cancelBackupJob().catch(() => {});
        refreshBackupState().catch(() => {});
      }
      appStateRef.current = nextState;
    });
    return () => sub.remove();
  }, [backupConfig.autoBackupOnBackground, backupConfig.enabled, backupConfig.passphraseConfigured, backupStatus.dirty]);

  const savePlans = async (v) => {
    setPlans(v);
    try {
      await database.write(async () => {
        const [existingPlans, existingDays, existingPlanExercises] = await Promise.all([
          WM_PLANS.query().fetch(),
          WM_PLAN_DAYS.query().fetch(),
          WM_PLAN_EXERCISES.query().fetch(),
        ]);
        const deleteOps = [
          ...existingPlanExercises.map((row) => row.prepareDestroyPermanently()),
          ...existingDays.map((row) => row.prepareDestroyPermanently()),
          ...existingPlans.map((row) => row.prepareDestroyPermanently()),
        ];
        if (deleteOps.length) await database.batch(...deleteOps);

        const exRows = await WM_EXERCISES.query().fetch();
        const exByName = new Map(exRows.map((ex) => [String(ex.name || '').trim().toLowerCase(), ex.id]));
        const now = Date.now();
        const createOps = [];
        for (let pi = 0; pi < (v || []).length; pi += 1) {
          const plan = v[pi];
          const p = WM_PLANS.prepareCreate((row) => {
            row.name = String(plan?.name || `Plan ${pi + 1}`).trim();
            row.goal = String(plan?.goal || 'General Fitness').trim();
            row.description = String(plan?.description || '');
            row.isActive = !!plan?.isActive;
            row._raw.created_at = now;
            row.updatedAt = now;
          });
          createOps.push(p);
          const days = Array.isArray(plan?.days) ? plan.days : [];
          for (let di = 0; di < days.length; di += 1) {
            const day = days[di];
            const d = WM_PLAN_DAYS.prepareCreate((row) => {
              row.plan.id = p.id;
              row.name = String(day?.name || `Day ${di + 1}`).trim();
              row.color = String(day?.color || '#FF4500');
              row.orderIndex = di;
              row._raw.created_at = now;
              row.updatedAt = now;
            });
            createOps.push(d);
            const exs = Array.isArray(day?.exercises) ? day.exercises : [];
            for (let ei = 0; ei < exs.length; ei += 1) {
              const ex = exs[ei];
              const exId = ex?.exerciseId || exByName.get(String(ex?.name || '').trim().toLowerCase());
              if (!exId) continue;
              createOps.push(WM_PLAN_EXERCISES.prepareCreate((row) => {
                row.planDay.id = d.id;
                row.exercise.id = exId;
                row.orderIndex = ei;
                row.sets = Math.max(1, Number(ex?.sets || 3));
                row.reps = String(ex?.reps || '10');
                row.restSeconds = Math.max(0, Number(ex?.restSeconds ?? ex?.rest ?? 90));
                row.supersetGroup = String(ex?.supersetGroup || '');
                row.isWarmup = !!ex?.isWarmup;
                row.notes = String(ex?.notes || '');
                row._raw.created_at = now;
                row.updatedAt = now;
              }));
            }
          }
        }
        if (createOps.length) await database.batch(...createOps);
      });
    } catch (wmError) {
      console.warn('savePlans Watermelon error:', wmError);
    }
    await flagDirty('plans_changed');
  };
  const addHistory = async (entry) => {
    const h = [entry, ...history].slice(0, 200);
    setHistory(h);
    const nextMilestones = evaluateMilestones({ history: h, milestoneState: milestoneUnlocks });
    setMilestoneUnlocks(nextMilestones.state);
    setLatestMilestones(nextMilestones.unlocked);
    try {
      await setWatermelonSetting(WM_MILESTONE_UNLOCKS_KEY, nextMilestones.state, 'json');
      await createCompletedWorkout({
        name: entry?.dayName || entry?.name || 'Workout',
        startedAt: entry?.date ? new Date(entry.date).getTime() : Date.now(),
        durationSeconds: Number(entry?.duration || 0),
        rating: entry?.rating ?? null,
        notes: entry?.summaryText || entry?.notes || '',
        exerciseData: Array.isArray(entry?.exercises) ? entry.exercises : [],
      });
      const indexes = await buildIndexUpdatesForSession(entry);
      if (indexes) {
        await Promise.all([
          setWatermelonSetting('pr_index_v1', indexes.prIndex, 'json'),
          setWatermelonSetting('volume_index_v1', indexes.volumeIndex, 'json'),
          setWatermelonSetting('last_performance_v1', indexes.lastPerf, 'json'),
        ]);
      }
      await flagDirty('workout_completion');
      if (backupConfig.enabled && backupConfig.autoBackupOnWorkoutCompletion && backupConfig.passphraseConfigured) {
        queueAutoBackup('workout_completion', 5000).catch((error) => console.warn('Workout backup queue failed:', error));
      }
    } catch (e) {
      console.warn('addHistory index error:', e);
      await flagDirty('workout_completion');
    }
    return { unlockedMilestones: nextMilestones.unlocked };
  };
  const updateHistoryEntry = async (entry) => {
    if (!entry?.id) return false;
    const h = history
      .map((item) => (item?.id === entry.id ? { ...item, ...entry } : item))
      .sort((a, b) => new Date(b?.date || 0) - new Date(a?.date || 0))
      .slice(0, 200);
    setHistory(h);
    const nextMilestones = evaluateMilestones({ history: h, milestoneState: milestoneUnlocks });
    setMilestoneUnlocks(nextMilestones.state);
    try {
      await setWatermelonSetting(WM_MILESTONE_UNLOCKS_KEY, nextMilestones.state, 'json');
      if (entry?.id) {
        await updateWorkoutMetadata(entry.id, {
          startedAt: entry?.date ? new Date(entry.date).getTime() : undefined,
          durationSeconds: entry?.duration,
          rating: entry?.rating,
          notes: entry?.summaryText,
        });
      }
      const indexes = await buildIndexUpdatesForHistory(h);
      if (indexes) {
        await Promise.all([
          setWatermelonSetting('pr_index_v1', indexes.prIndex, 'json'),
          setWatermelonSetting('volume_index_v1', indexes.volumeIndex, 'json'),
          setWatermelonSetting('last_performance_v1', indexes.lastPerf, 'json'),
        ]);
      }
      await flagDirty('history_edited');
    } catch (e) {
      console.warn('updateHistoryEntry persistence error:', e);
      await flagDirty('history_edited');
    }
    return true;
  };
  const deleteHistoryEntry = async (entryId) => {
    if (!entryId) return false;
    const h = history
      .filter((item) => item?.id !== entryId)
      .sort((a, b) => new Date(b?.date || 0) - new Date(a?.date || 0));
    setHistory(h);
    const nextMilestones = evaluateMilestones({ history: h, milestoneState: milestoneUnlocks });
    setMilestoneUnlocks(nextMilestones.state);
    try {
      await setWatermelonSetting(WM_MILESTONE_UNLOCKS_KEY, nextMilestones.state, 'json');
      if (entryId) await deleteWorkoutWithData(entryId);
      const indexes = await buildIndexUpdatesForHistory(h);
      if (indexes) {
        await Promise.all([
          setWatermelonSetting('pr_index_v1', indexes.prIndex, 'json'),
          setWatermelonSetting('volume_index_v1', indexes.volumeIndex, 'json'),
          setWatermelonSetting('last_performance_v1', indexes.lastPerf, 'json'),
        ]);
      }
      await flagDirty('history_deleted');
    } catch (e) {
      console.warn('deleteHistoryEntry persistence error:', e);
      await flagDirty('history_deleted');
    }
    return true;
  };
  const clearHistory = async () => {
    try {
      if (backupConfig.passphraseConfigured) {
        await createRollbackSnapshot();
      }
    } catch (e) {
      console.warn('Rollback snapshot before clearHistory failed:', e);
    }
    setHistory([]);
    try {
      await clearCompletedWorkouts();
    } catch (e) {
      console.warn('clearHistory SQLite error:', e);
    }
    await Promise.all([
      removeWatermelonSetting('pr_index_v1'),
      removeWatermelonSetting('volume_index_v1'),
      removeWatermelonSetting('last_performance_v1'),
    ]);
    await flagDirty('history_cleared');
  };
  const updatePb = async (key, val) => {
    const n = { ...pb, [key]: val };
    setPb(n);
    await setWatermelonSetting('ironlog_pb', n, 'json');
    await flagDirty('pb_changed');
  };
  const clearPbs = async () => {
    setPb({});
    await removeWatermelonSetting('ironlog_pb');
    await flagDirty('pb_cleared');
  };
  const logBodyWeight = async (entry) => {
    const bw = [entry, ...bodyWeight].slice(0, 365);
    setBodyWeight(bw);
    try {
      await addBodyMeasurement({
        measuredAt: entry?.date ? new Date(entry.date).getTime() : Date.now(),
        bodyweight: Number(entry?.weight ?? entry?.bodyweight ?? 0),
        notes: entry?.note || entry?.notes || '',
      });
      await flagDirty('body_weight_changed');
    } catch (e) {
      console.warn('saveBodyWeight SQLite error:', e);
    }
  };
  const deleteBodyWeightEntry = async ({ id, sourceIndex }) => {
    const current = Array.isArray(bodyWeight) ? bodyWeight : [];
    const next = current.filter((_, index) => index !== sourceIndex);
    setBodyWeight(next);
    if (id) {
      try {
        await deleteBodyMeasurementRow(id);
      } catch (e) {
        console.warn('deleteBodyWeightEntry Watermelon error:', e);
      }
    }
    await flagDirty('body_weight_changed');
  };
  const saveExerciseNotes = async (exName, note) => {
    const n = { ...exerciseNotes, [exName]: note };
    setExerciseNotes(n);
    try {
      await setWatermelonSetting(WM_EXERCISE_NOTES_KEY, n, 'json');
      await flagDirty('notes_changed');
    } catch (wmError) {
      console.warn('saveExerciseNotes Watermelon write failed:', wmError);
    }
  };
  const updateSettings = async (s) => {
    const next = { ...(s || {}), weeklyGoalDays: normalizeWeeklyGoalDays(s?.weeklyGoalDays, settings?.weeklyGoalDays || 4) };
    setSettings(next);
    try {
      await setWatermelonSetting('ironlog_settings', next, 'json');
      await flagDirty('settings_changed');
    } catch (wmError) {
      console.warn('updateSettings Watermelon write failed:', wmError);
    }
  };
  const saveManualRecovery = async (entry) => {
    const payload = {
      soreness: Number(entry?.soreness || 0),
      sleepQuality: Number(entry?.sleepQuality || 0),
      energy: Number(entry?.energy || 0),
      notes: entry?.notes || '',
      recordedAt: entry?.recordedAt || new Date().toISOString(),
    };
    setManualRecoveryInput(payload);
    try {
      await setWatermelonSetting(WM_MANUAL_RECOVERY_KEY, payload, 'json');
      await flagDirty('manual_recovery_changed');
    } catch (wmError) {
      console.warn('manual recovery Watermelon write failed:', wmError);
    }
    return payload;
  };

  const completeOnboarding = async (options = {}) => {
    setOnboardingCompleteState(true);
    try {
      if (options?.weeklyGoalDays != null) {
        const nextSettings = { ...settings, weeklyGoalDays: normalizeWeeklyGoalDays(options.weeklyGoalDays, settings?.weeklyGoalDays || 4) };
        setSettings(nextSettings);
        await setWatermelonSetting('ironlog_settings', nextSettings, 'json');
      }
      await setWatermelonSetting('onboarding_complete', true, 'boolean');
      await flagDirty('onboarding_changed');
    } catch (e) { console.warn(e); }
  };
  const resetOnboarding = async () => {
    setOnboardingCompleteState(false);
    try {
      await setWatermelonSetting('onboarding_complete', false, 'boolean');
      await flagDirty('onboarding_changed');
    } catch (e) { console.warn(e); }
  };

  const saveGymProfiles = async (profiles) => {
    setGymProfiles(profiles);
    try {
      await setWatermelonSetting(WM_GYM_PROFILES_KEY, profiles, 'json');
      await flagDirty('gym_profiles_changed');
    } catch (e) { console.warn(e); }
  };
  const setActiveGymProfileId = async (id) => {
    setActiveGymProfileIdState(id);
    try {
      await setWatermelonSetting(WM_ACTIVE_GYM_PROFILE_ID_KEY, String(id || 'default'), 'string');
      await flagDirty('gym_profiles_changed');
    } catch (e) { console.warn(e); }
  };

  const reloadFromStorage = async () => {
    await init();
  };

  const getAllData = () => ({
    plans,
    history,
    pb,
    bodyWeight,
    exerciseNotes,
    settings,
    gymProfiles,
    activeGymProfileId,
    onboardingComplete,
    backupConfig,
    backupStatus,
    notificationSettings,
    manualRecoveryInput,
    milestoneUnlocks,
    engagementSnapshot,
  });

  const updateBackupPreferences = async (patch) => {
    const next = await saveBackupConfig({ ...backupConfig, ...(patch || {}) });
    setBackupConfigState(next);
    await refreshBackupState();
    return next;
  };

  const updateNotificationPreferences = async (patch) => {
    const next = await saveNotificationSettings({ ...notificationSettings, ...(patch || {}) });
    setNotificationSettingsState(next);
    await flagDirty('notification_settings_changed');
    return next;
  };

  const setupBackupPassphrase = async (passphrase) => {
    const result = await configureBackupPassphrase(passphrase, { ...backupConfig, enabled: true });
    await refreshBackupState();
    return result;
  };

  const unlinkDriveBackup = async () => {
    await refreshBackupState();
    return { disabled: true };
  };

  const linkDriveBackup = async (options = {}) => {
    await refreshBackupState();
    return { connected: false, disabled: true, reason: 'Google Drive backup is disabled in this build.', options };
  };

  const updateDriveBackupFolder = async (folderName) => {
    await refreshBackupState();
    return { updated: false, disabled: true, folderName };
  };

  const updateDriveSyncMode = async (mode) => {
    await refreshBackupState();
    return { updated: false, disabled: true, mode };
  };

  const runManualBackup = async (options = {}) => {
    const result = await runBackupNow({ reason: 'manual', syncToDrive: false, ...options });
    await refreshBackupState();
    return result;
  };

  const saveDriveOAuthClient = async (clientId) => {
    await refreshBackupState();
    return { saved: false, disabled: true, clientId };
  };

  const clearDriveOAuthClient = async () => {
    await refreshBackupState();
    return { cleared: false, disabled: true };
  };

  const restoreData = async (data) => {
    const payload = data?.payload && typeof data.payload === 'object' ? data.payload : data;
    try {
      await importWatermelonDatabase(payload);
      await init();
      await refreshBackupState();
      return {
        plans: Array.isArray(payload?.plans) ? payload.plans.length : 0,
        history: Array.isArray(payload?.history) ? payload.history.length : 0,
        bodyWeight: Array.isArray(payload?.bodyWeight) ? payload.bodyWeight.length : 0,
        bodyMeasurements: Array.isArray(payload?.bodyMeasurements) ? payload.bodyMeasurements.length : 0,
        customExercises: Array.isArray(payload?.customExercises) ? payload.customExercises.length : 0,
      };
    } catch (wmImportError) {
      console.warn('Watermelon restore failed:', wmImportError);
      throw wmImportError;
    }
  };

  const activeGymProfile = gymProfiles.find(p => p.id === activeGymProfileId) || gymProfiles[0] || DEFAULT_GYM_PROFILE;

  return (
    <AppContext.Provider value={{
      plans, history, pb, bodyWeight, exerciseNotes, settings, initialized,
      manualRecoveryInput, milestoneUnlocks, engagementSnapshot,
      onboardingComplete, completeOnboarding, resetOnboarding,
      gymProfiles, activeGymProfileId, activeGymProfile,
      exerciseMap, backupConfig, backupStatus, notificationSettings,
      savePlans, addHistory, updateHistoryEntry, deleteHistoryEntry, clearHistory, updatePb, clearPbs,
      logBodyWeight, deleteBodyWeightEntry, saveExerciseNotes, updateSettings,
      saveManualRecovery,
      saveGymProfiles, setActiveGymProfileId,
      getAllData, restoreData, reloadFromStorage,
      refreshBackupState, updateBackupPreferences, updateNotificationPreferences,
      setupBackupPassphrase, runManualBackup, linkDriveBackup, unlinkDriveBackup, updateDriveBackupFolder, updateDriveSyncMode,
      saveDriveOAuthClient, clearDriveOAuthClient,
      isHeavy: (name) => HEAVY.has(name),
    }}>
      {children}
    </AppContext.Provider>
  );
}
