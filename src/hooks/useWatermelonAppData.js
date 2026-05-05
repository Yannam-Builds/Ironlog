import { useCallback, useEffect, useMemo, useState } from 'react';
import { buildExerciseMap } from '../domain/intelligence/muscleContributionEngine';
import { buildWeeklySummary, computeStreaks, evaluateMilestones } from '../domain/intelligence/engagementEngine';
import { EXERCISES } from '../data/exerciseLibrary';
import { DEFAULT_BACKUP_CONFIG, DEFAULT_BACKUP_STATUS, DEFAULT_NOTIFICATION_SETTINGS } from '../services/backupConstants';
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
import { createCompletedWorkout } from '../db/repositories/workoutRepository';
import {
  getSetting,
  removeSetting,
  setSetting,
} from '../db/repositories/settingsRepository';
import { importAnyPayload } from '../db/repositories/importExportRepository';
import useWatermelonBodyMeasurements from './useWatermelonBodyMeasurements';
import useWatermelonGymProfiles from './useWatermelonGymProfiles';
import useWatermelonHistory from './useWatermelonHistory';
import useWatermelonHome from './useWatermelonHome';
import useWatermelonManualRecovery from './useWatermelonManualRecovery';
import useWatermelonPlans from './useWatermelonPlans';
import useWatermelonSettings from './useWatermelonSettings';
import useWatermelonStats from './useWatermelonStats';

const EMPTY_ARRAY = [];

const HEAVY = new Set([
  'Weighted Pull-Up',
  'Weighted Pull-Up or Lat Pulldown',
  'Romanian Deadlift',
  'Bulgarian Split Squat',
  'DB Shrugs',
  'Incline Smith Press',
  'Barbell Shrugs',
  'Deadlift',
  'Back Squat',
  'Front Squat',
]);

const DEFAULT_ENGAGEMENT = {
  streaks: {
    training: { current: 0, longest: 0 },
    logging: { current: 0, longest: 0 },
    bodyweight: { current: 0, longest: 0 },
  },
  weeklySummary: null,
};

const EXERCISE_NOTES_KEY = 'exercise_notes_v1';
const MILESTONE_UNLOCKS_KEY = 'milestone_unlocks_v1';
const ONBOARDING_KEY = 'onboarding_complete';

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

function normalizeWeeklyGoalDays(value, fallback = 4) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(1, Math.min(7, Math.round(parsed)));
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

async function buildIndexUpdatesForSession(session) {
  if (!session?.exercises || !session?.date) return null;
  const prIndex = (await getSetting('pr_index_v1')) || {};
  const volumeIndex = (await getSetting('volume_index_v1')) || {};
  const lastPerf = (await getSetting('last_performance_v1')) || {};

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

  return { prIndex, volumeIndex, lastPerf };
}

export default function useWatermelonAppData() {
  const home = useWatermelonHome();
  const plansHook = useWatermelonPlans();
  const historyHook = useWatermelonHistory();
  const stats = useWatermelonStats();
  const settingsHook = useWatermelonSettings();
  const gym = useWatermelonGymProfiles();
  const manualRecovery = useWatermelonManualRecovery();
  const bodyMeasurements = useWatermelonBodyMeasurements();

  const plans = plansHook.plans?.length ? plansHook.plans : EMPTY_ARRAY;
  const history = historyHook.history?.length ? historyHook.history : EMPTY_ARRAY;
  const bodyWeight = bodyMeasurements.bodyWeight?.length ? bodyMeasurements.bodyWeight : home.bodyWeight?.length ? home.bodyWeight : EMPTY_ARRAY;
  const settings = settingsHook.settings || home.settings || {};

  const [manualPb, setManualPb] = useState({});
  const [exerciseNotes, setExerciseNotes] = useState({});
  const [onboardingComplete, setOnboardingComplete] = useState(false);
  const [settingsLoaded, setSettingsLoaded] = useState(false);
  const [milestoneUnlocks, setMilestoneUnlocks] = useState({});
  const [backupConfig, setBackupConfig] = useState(DEFAULT_BACKUP_CONFIG);
  const [backupStatus, setBackupStatus] = useState(DEFAULT_BACKUP_STATUS);
  const [notificationSettings, setNotificationSettings] = useState(DEFAULT_NOTIFICATION_SETTINGS);

  const exerciseMap = useMemo(() => buildExerciseMap(EXERCISES), []);
  const pb = useMemo(() => ({ ...(stats.pb || {}), ...(manualPb || {}) }), [manualPb, stats.pb]);

  const engagementSnapshot = useMemo(() => {
    try {
      return {
        streaks: computeStreaks({ history, bodyWeight }),
        weeklySummary: buildWeeklySummary({ history, bodyWeight }),
      };
    } catch (_) {
      return DEFAULT_ENGAGEMENT;
    }
  }, [bodyWeight, history]);

  const refreshBackupState = useCallback(async () => {
    const [config, status, notifications] = await Promise.all([
      loadBackupConfig().catch(() => DEFAULT_BACKUP_CONFIG),
      loadBackupStatus().catch(() => DEFAULT_BACKUP_STATUS),
      loadNotificationSettings().catch(() => DEFAULT_NOTIFICATION_SETTINGS),
    ]);
    const nextStatus = {
      ...DEFAULT_BACKUP_STATUS,
      ...status,
      enabled: config.enabled,
      driveLinked: false,
    };
    setBackupConfig({ ...DEFAULT_BACKUP_CONFIG, ...config, driveEnabled: false });
    setBackupStatus(nextStatus);
    setNotificationSettings({ ...DEFAULT_NOTIFICATION_SETTINGS, ...notifications });
    return { config, status: nextStatus, notifications };
  }, []);

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const [notes, onboarding, milestones, savedPb] = await Promise.all([
          getSetting(EXERCISE_NOTES_KEY),
          getSetting(ONBOARDING_KEY),
          getSetting(MILESTONE_UNLOCKS_KEY),
          getSetting('ironlog_pb'),
        ]);
        if (!mounted) return;
        if (notes && typeof notes === 'object') setExerciseNotes(notes);
        setOnboardingComplete(!!onboarding);
        if (milestones && typeof milestones === 'object') setMilestoneUnlocks(milestones);
        if (savedPb && typeof savedPb === 'object') setManualPb(savedPb);
      } catch (_) {}
      if (mounted) setSettingsLoaded(true);
      refreshBackupState().catch(() => {});
    })();
    return () => { mounted = false; };
  }, [refreshBackupState]);

  // One-time migration: if onboarding_complete was never written to WM (users upgrading from the
  // old AsyncStorage build), but they have existing plans, treat them as onboarded and persist it.
  useEffect(() => {
    if (!settingsLoaded || onboardingComplete) return;
    if (plans && plans.length > 0) {
      setSetting(ONBOARDING_KEY, true, 'boolean').catch(() => {});
      setOnboardingComplete(true);
    }
  }, [settingsLoaded, onboardingComplete, plans]);

  const flagDirty = useCallback(async (reason = 'data_changed') => {
    try {
      await setBackupDirtyFlag(reason);
      await refreshBackupState();
    } catch (_) {}
  }, [refreshBackupState]);

  const updateSettings = useCallback(async (updates) => {
    const next = {
      ...(settings || {}),
      ...(updates || {}),
      weeklyGoalDays: normalizeWeeklyGoalDays(
        updates?.weeklyGoalDays ?? settings?.weeklyGoalDays,
        settings?.weeklyGoalDays || 4,
      ),
    };
    await settingsHook.updateSettings(next);
    await flagDirty('settings_changed');
    return next;
  }, [flagDirty, settings, settingsHook]);

  const addHistory = useCallback(async (entry) => {
    const nextHistory = [entry, ...history].slice(0, 200);
    const nextMilestones = evaluateMilestones({ history: nextHistory, milestoneState: milestoneUnlocks });
    setMilestoneUnlocks(nextMilestones.state);
    await setSetting(MILESTONE_UNLOCKS_KEY, nextMilestones.state, 'json');

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
        setSetting('pr_index_v1', indexes.prIndex, 'json'),
        setSetting('volume_index_v1', indexes.volumeIndex, 'json'),
        setSetting('last_performance_v1', indexes.lastPerf, 'json'),
      ]);
    }
    await flagDirty('workout_completion');
    if (backupConfig.enabled && backupConfig.autoBackupOnWorkoutCompletion && backupConfig.passphraseConfigured) {
      queueBackup('workout_completion').catch(() => {});
    }
    return { unlockedMilestones: nextMilestones.unlocked };
  }, [backupConfig, flagDirty, history, milestoneUnlocks]);

  const updatePb = useCallback(async (key, val) => {
    const next = { ...(manualPb || {}), [key]: val };
    setManualPb(next);
    await setSetting('ironlog_pb', next, 'json');
    await flagDirty('pb_changed');
  }, [flagDirty, manualPb]);

  const clearPbs = useCallback(async () => {
    setManualPb({});
    await removeSetting('ironlog_pb');
    await flagDirty('pb_cleared');
  }, [flagDirty]);

  const saveExerciseNotes = useCallback(async (exName, note) => {
    const key = String(exName || '').trim();
    if (!key) return;
    const next = { ...(exerciseNotes || {}), [key]: note };
    setExerciseNotes(next);
    await setSetting(EXERCISE_NOTES_KEY, next, 'json');
    await flagDirty('notes_changed');
  }, [exerciseNotes, flagDirty]);

  const logBodyWeight = useCallback(async (entry) => {
    await bodyMeasurements.add({
      measuredAt: entry?.date ? new Date(entry.date).getTime() : Date.now(),
      bodyweight: Number(entry?.weight ?? entry?.bodyweight ?? 0),
      notes: entry?.note || entry?.notes || '',
    });
    await flagDirty('body_weight_changed');
  }, [bodyMeasurements, flagDirty]);

  const deleteBodyWeightEntry = useCallback(async ({ id, sourceIndex }) => {
    const targetId = id || bodyWeight[sourceIndex]?.id;
    if (targetId) await bodyMeasurements.remove(targetId);
    await flagDirty('body_weight_changed');
  }, [bodyMeasurements, bodyWeight, flagDirty]);

  const completeOnboarding = useCallback(async (options = {}) => {
    setOnboardingComplete(true);
    if (options?.weeklyGoalDays != null) {
      await updateSettings({
        ...settings,
        weeklyGoalDays: normalizeWeeklyGoalDays(options.weeklyGoalDays, settings?.weeklyGoalDays || 4),
      });
    }
    await setSetting(ONBOARDING_KEY, true, 'boolean');
    await flagDirty('onboarding_changed');
  }, [flagDirty, settings, updateSettings]);

  const resetOnboarding = useCallback(async () => {
    setOnboardingComplete(false);
    await setSetting(ONBOARDING_KEY, false, 'boolean');
    await flagDirty('onboarding_changed');
  }, [flagDirty]);

  const reloadFromStorage = useCallback(async () => {
    await refreshBackupState();
  }, [refreshBackupState]);

  const getAllData = useCallback(() => ({
    plans,
    history,
    pb,
    bodyWeight,
    exerciseNotes,
    settings,
    gymProfiles: gym.gymProfiles,
    activeGymProfileId: gym.activeGymProfileId,
    onboardingComplete,
    backupConfig,
    backupStatus,
    notificationSettings,
    manualRecoveryInput: manualRecovery.manualRecoveryInput,
    milestoneUnlocks,
    engagementSnapshot,
  }), [
    backupConfig,
    backupStatus,
    bodyWeight,
    engagementSnapshot,
    exerciseNotes,
    gym.activeGymProfileId,
    gym.gymProfiles,
    history,
    manualRecovery.manualRecoveryInput,
    milestoneUnlocks,
    notificationSettings,
    onboardingComplete,
    pb,
    plans,
    settings,
  ]);

  const restoreData = useCallback(async (data) => {
    const payload = data?.payload && typeof data.payload === 'object' ? data.payload : data;
    const result = await importAnyPayload(payload);
    await refreshBackupState();
    return result;
  }, [refreshBackupState]);

  const updateBackupPreferences = useCallback(async (patch) => {
    const next = await saveBackupConfig({ ...backupConfig, ...(patch || {}) });
    setBackupConfig(next);
    await refreshBackupState();
    return next;
  }, [backupConfig, refreshBackupState]);

  const updateNotificationPreferences = useCallback(async (patch) => {
    const next = await saveNotificationSettings({ ...notificationSettings, ...(patch || {}) });
    setNotificationSettings(next);
    await flagDirty('notification_settings_changed');
    return next;
  }, [flagDirty, notificationSettings]);

  const setupBackupPassphrase = useCallback(async (passphrase) => {
    const result = await configureBackupPassphrase(passphrase, { ...backupConfig, enabled: true });
    await refreshBackupState();
    return result;
  }, [backupConfig, refreshBackupState]);

  const runManualBackup = useCallback(async (options = {}) => {
    const result = await runBackupNow({ reason: 'manual', syncToDrive: false, ...options });
    await refreshBackupState();
    return result;
  }, [refreshBackupState]);

  const updatePlanDay = useCallback(async (planId, dayId, patch = {}) => {
    const nextPlans = (plans || []).map((plan) => {
      if (plan.id !== planId) return plan;
      return {
        ...plan,
        days: (plan.days || []).map((day) => (
          day.id === dayId ? { ...day, ...(patch || {}) } : day
        )),
      };
    });
    await plansHook.savePlans(nextPlans);
  }, [plans, plansHook]);

  const isHeavy = useCallback((name) => {
    if (HEAVY.has(name)) return true;
    return /(deadlift|squat|shrug|weighted pull|romanian|front squat|back squat)/i.test(String(name || ''));
  }, []);

  return {
    plans,
    history,
    pb,
    bodyWeight,
    exerciseNotes,
    settings,
    initialized: !home.loading && !plansHook.loading && !historyHook.loading && settingsLoaded,
    manualRecoveryInput: manualRecovery.manualRecoveryInput,
    milestoneUnlocks,
    engagementSnapshot,
    onboardingComplete,
    completeOnboarding,
    resetOnboarding,
    gymProfiles: gym.gymProfiles,
    saveGymProfiles: gym.saveGymProfiles,
    activeGymProfileId: gym.activeGymProfileId,
    setActiveGymProfileId: gym.setActiveGymProfileId,
    activeGymProfile: gym.activeGymProfile,
    exerciseMap,
    backupConfig,
    backupStatus,
    notificationSettings,
    savePlans: plansHook.savePlans,
    addHistory,
    updateHistoryEntry: historyHook.updateHistoryEntry,
    deleteHistoryEntry: historyHook.deleteHistoryEntry,
    clearHistory: historyHook.clearHistory,
    updatePlanDay,
    updatePb,
    clearPbs,
    logBodyWeight,
    deleteBodyWeightEntry,
    saveExerciseNotes,
    updateSettings,
    saveManualRecovery: manualRecovery.saveManualRecovery,
    getAllData,
    restoreData,
    reloadFromStorage,
    refreshBackupState,
    updateBackupPreferences,
    updateNotificationPreferences,
    setupBackupPassphrase,
    runManualBackup,
    isHeavy,
    createRollbackSnapshot,
  };
}
