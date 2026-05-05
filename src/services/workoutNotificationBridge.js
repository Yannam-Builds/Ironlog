/**
 * Workout Notification Bridge
 *
 * Two-channel action delivery:
 *  • Foreground  → DeviceEventEmitter  ('ironlog:notifAction')
 *  • Background  → AsyncStorage pending action  (consumed on next foreground resume)
 */
import { DeviceEventEmitter } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

// ── Action IDs (must match pressAction.id strings in notificationScheduler) ──
export const NOTIF_ACTIONS = {
  SKIP_REST:       'ironlog.skip_rest',
  ADD_30S:         'ironlog.add_30s',
  FINISH_WORKOUT:  'ironlog.finish_workout',
  START_WORKOUT:   'ironlog.start_workout',
  SNOOZE_1HR:      'ironlog.snooze_1hr',
  LOG_WEIGHT:      'ironlog.log_weight',
  VIEW_PROGRESS:   'ironlog.view_progress',
};

const PENDING_KEY = '@ironlog/pendingNotifAction';
// Background actions older than 90 s are stale (user probably dismissed the app)
const MAX_PENDING_AGE_MS = 90_000;

/** Emit to any active listener in the JS foreground. */
export function emitNotifAction(actionId, payload = {}) {
  DeviceEventEmitter.emit('ironlog:notifAction', { actionId, ...payload });
}

/** Persist an action for consumption after the next foreground resume. */
export async function storePendingAction(actionId, payload = {}) {
  try {
    const record = JSON.stringify({ actionId, ...payload, storedAt: Date.now() });
    await AsyncStorage.setItem(PENDING_KEY, record);
  } catch (_) {
    // Non-fatal
  }
}

/**
 * Read + clear the pending action.
 * Returns null if there is nothing, or if the stored action has expired.
 */
export async function consumePendingAction() {
  try {
    const raw = await AsyncStorage.getItem(PENDING_KEY);
    if (!raw) return null;
    await AsyncStorage.removeItem(PENDING_KEY);
    const parsed = JSON.parse(raw);
    if (Date.now() - (parsed.storedAt || 0) > MAX_PENDING_AGE_MS) return null;
    return parsed;
  } catch (_) {
    return null;
  }
}
