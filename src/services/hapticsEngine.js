import { AppState, Platform, Vibration } from 'react-native';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';

const HAPTIC_EVENTS = {
  selection: { method: 'selection', minGapMs: 28, priority: 1, androidPattern: [0, 18, 16, 12] },
  lightConfirm: { method: 'impactLight', minGapMs: 45, priority: 2, androidPattern: [0, 22, 18, 16] },
  countdownLightest: { method: 'impactLight', minGapMs: 110, priority: 2, androidPattern: [0, 10] },
  countdownLight: { method: 'impactLight', minGapMs: 120, priority: 2, androidPattern: [0, 14] },
  countdownMedium: { method: 'impactMedium', minGapMs: 135, priority: 3, androidPattern: [0, 20] },
  countdownHigh: { method: 'impactHeavy', minGapMs: 150, priority: 4, androidPattern: [0, 24] },
  countdownBuzz: { method: 'notificationWarning', minGapMs: 220, priority: 5, androidPattern: [0, 18, 16, 30] },
  mediumConfirm: { method: 'impactMedium', minGapMs: 70, priority: 3, androidPattern: [0, 26, 22, 20] },
  success: { method: 'notificationSuccess', minGapMs: 130, priority: 4, androidPattern: [0, 22, 28, 18, 34, 26] },
  milestoneSuccess: { method: 'notificationSuccess', minGapMs: 500, priority: 7, androidPattern: [0, 24, 34, 22, 42, 34] },
  warning: { method: 'notificationWarning', minGapMs: 180, priority: 4, androidPattern: [0, 22, 34, 18, 44, 20] },
  error: { method: 'notificationError', minGapMs: 240, priority: 5, androidPattern: [0, 26, 38, 24, 52, 28] },
  timerDone: { method: 'notificationWarning', minGapMs: 800, priority: 7, androidPattern: [0, 28, 40, 24, 56, 42] },
  setLogged: { method: 'impactLight', minGapMs: 90, priority: 3, androidPattern: [0, 24, 24, 18] },
  setCompleted: { method: 'impactMedium', minGapMs: 130, priority: 4, androidPattern: [0, 28, 28, 22] },
  workoutCompleted: { method: 'notificationSuccess', minGapMs: 900, priority: 8, androidPattern: [0, 28, 38, 24, 52, 40] },
  prUnlocked: { method: 'notificationSuccess', minGapMs: 650, priority: 7, androidPattern: [0, 24, 30, 20, 46, 34] },
  restoreSucceeded: { method: 'notificationSuccess', minGapMs: 400, priority: 6, androidPattern: [0, 22, 28, 18, 38, 28] },
  backupSucceeded: { method: 'notificationSuccess', minGapMs: 300, priority: 6, androidPattern: [0, 20, 26, 18, 38, 24] },
  destructiveAction: { method: 'impactHeavy', minGapMs: 220, priority: 5, androidPattern: [0, 34, 24, 20] },
  invalidAction: { method: 'notificationWarning', minGapMs: 180, priority: 4, androidPattern: [0, 18, 38, 18, 46, 18] },
};

const EVENT_LAST_FIRED = new Map();
const GLOBAL_MIN_GAP_MS = 14;
let HAPTICS_ENABLED = true;
let LAST_GLOBAL_TS = 0;
let LAST_GLOBAL_PRIORITY = 0;

AppState.addEventListener('change', () => {});

function shouldDropForSpam(eventName, config, force) {
  if (force) return false;
  const now = Date.now();
  const minGapMs = config.minGapMs ?? 60;
  const eventLast = EVENT_LAST_FIRED.get(eventName) ?? 0;
  if (now - eventLast < minGapMs) return true;
  if (now - LAST_GLOBAL_TS < GLOBAL_MIN_GAP_MS && (config.priority ?? 1) <= LAST_GLOBAL_PRIORITY) return true;

  EVENT_LAST_FIRED.set(eventName, now);
  LAST_GLOBAL_TS = now;
  LAST_GLOBAL_PRIORITY = config.priority ?? 1;
  return false;
}

function runAndroidAssist(config) {
  if (Platform.OS !== 'android') return;
  try {
    Vibration.vibrate(config?.androidPattern || [0, 20], false);
  } catch (_) {
    // Ignore unsupported vibration behavior.
  }
}

export function setHapticsEnabled(enabled) {
  HAPTICS_ENABLED = enabled !== false;
}

export function resetHapticsEngine() {
  EVENT_LAST_FIRED.clear();
  LAST_GLOBAL_TS = 0;
  LAST_GLOBAL_PRIORITY = 0;
}

export async function triggerHaptic(eventName, options = {}) {
  const config = HAPTIC_EVENTS[eventName] || HAPTIC_EVENTS.selection;
  const enabled = options.enabled ?? HAPTICS_ENABLED;
  if (!enabled) return false;
  if (shouldDropForSpam(eventName, config, options.force)) return false;

  if (Platform.OS === 'android' && options.androidAssist !== false) {
    runAndroidAssist(config);
  }

  try {
    ReactNativeHapticFeedback.trigger(config.method || 'selection', {
      enableVibrateFallback: true,
      ignoreAndroidSystemSettings: false,
    });
    return true;
  } catch (_) {
    runAndroidAssist(config);
    return false;
  }
}

export { HAPTIC_EVENTS };

/**
 * Fire-and-forget haptic trigger - swallows rejections silently.
 * Use this in UI callbacks instead of triggerHaptic(...).
 */
export function haptic(event = 'selection', options = {}) {
  try { triggerHaptic(event, options).catch(() => {}); } catch {}
}

export const fireHaptic = haptic;

