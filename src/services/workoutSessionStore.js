import {
  getSetting,
  setSetting,
  removeSetting,
} from '../db/repositories/settingsRepository';

const LAST_PERFORMANCE_KEY = 'last_performance_v1';
const NEXT_TARGETS_KEY = 'next_targets_v1';

export async function getActiveSession(sessionKey) {
  if (!sessionKey) return null;
  return getSetting(sessionKey);
}

export async function setActiveSession(sessionKey, payload) {
  if (!sessionKey) return;
  await setSetting(sessionKey, payload || null, 'json');
}

export async function clearActiveSession(sessionKey) {
  if (!sessionKey) return;
  await removeSetting(sessionKey);
}

export async function getLastPerformanceIndex() {
  return (await getSetting(LAST_PERFORMANCE_KEY)) || {};
}

export async function setLastPerformanceIndex(value) {
  await setSetting(LAST_PERFORMANCE_KEY, value || {}, 'json');
}

export async function getNextTargetsIndex() {
  return (await getSetting(NEXT_TARGETS_KEY)) || {};
}

export async function setNextTargetsIndex(value) {
  await setSetting(NEXT_TARGETS_KEY, value || {}, 'json');
}
