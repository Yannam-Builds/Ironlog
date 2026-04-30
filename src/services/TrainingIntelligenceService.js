import { getPrGroupId } from './prLinkingEngine';

/**
 * TrainingIntelligenceService.js
 *
 * Pure computation functions for training analytics.
 * No external imports required — uses plain JS Date for all date math.
 */

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Compute Epley one-rep-max estimate.
 * @param {number} weight
 * @param {number} reps
 * @returns {number}
 */
function e1rm(weight, reps) {
  if (!weight || !reps) return 0;
  return weight * (1 + reps / 30);
}

/**
 * Return midnight (00:00:00.000) of the Monday that starts the ISO week
 * containing the given date.
 * @param {Date} date
 * @returns {Date}
 */
function getWeekStart(date) {
  const d = new Date(date);
  const day = d.getDay(); // 0=Sun, 1=Mon, …
  const diff = (day === 0 ? -6 : 1 - day); // shift to Monday
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

/**
 * Return a Date that is `days` full days before the start of today (00:00).
 * @param {number} days
 * @returns {Date}
 */
function daysAgo(days) {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return d;
}

/**
 * Parse a session date string into a Date object safely.
 * @param {string} dateStr
 * @returns {Date|null}
 */
function parseDate(dateStr) {
  if (!dateStr) return null;
  const d = new Date(dateStr);
  return isNaN(d.getTime()) ? null : d;
}

/**
 * Return work sets (not warmup) for an exercise object.
 * Handles both WatermelonDB shape (isWarmup: boolean) and legacy shape (type: 'warmup').
 * @param {{ sets?: Array<{isWarmup?: boolean, type?: string}> }} exercise
 * @returns {Array}
 */
function workSets(exercise) {
  if (!Array.isArray(exercise?.sets)) return [];
  return exercise.sets.filter((s) => !s?.isWarmup && s?.type !== 'warmup');
}

// ---------------------------------------------------------------------------
// Keyword lookup tables
// ---------------------------------------------------------------------------

const MUSCLE_KEYWORDS = {
  chest: ['bench', 'fly', 'push', 'pec', 'dip', 'cable cross'],
  back: ['row', 'pulldown', 'pull-up', 'pull up', 'chin', 'deadlift', 'lat '],
  legs: ['squat', 'lunge', 'leg press', 'leg ext', 'leg curl', 'step', 'hack', 'bulgarian'],
  shoulders: ['overhead', 'shoulder press', 'ohp', 'lateral', 'front raise', 'arnold', 'military'],
  arms: ['curl', 'tricep', 'extension', 'pullover'],
  core: ['plank', 'crunch', 'sit-up', 'ab ', 'oblique', 'leg raise'],
};

const MOVEMENT_KEYWORDS = {
  push: ['bench', 'press', 'push', 'fly', 'pec', 'dip', 'lateral raise', 'front raise'],
  pull: ['row', 'pull', 'chin', 'curl', 'face pull', 'rear delt', 'shrug', 'pullover'],
  hinge: ['deadlift', 'rdl', 'good morning', 'hip thrust', 'glute bridge', 'swing'],
  squat: ['squat', 'lunge', 'leg press', 'step-up', 'hack', 'bulgarian'],
  core: ['plank', 'crunch', 'sit.up', 'ab', 'oblique', 'leg raise', 'cable crunch'],
};

const HEAVY_COMPOUNDS = [
  'squat', 'deadlift', 'bench press', 'overhead press', 'ohp',
  'barbell row', 'power clean', 'front squat', 'sumo', 'rdl', 'romanian',
];

/**
 * Maps any fine-grained muscle name (from exercise library, MUSCLE_FILTER_OPTIONS,
 * or the muscle contribution engine) to one of the 6 canonical intelligence groups.
 * All keys are lowercase. Falls through to the original value if not found (which
 * will then be ignored by the volume bucket check).
 */
const MUSCLE_TO_GROUP = {
  // chest
  chest: 'chest', 'upper chest': 'chest', 'mid chest': 'chest', 'lower chest': 'chest',
  pectorals: 'chest', pecs: 'chest', 'pectoralis major': 'chest', 'pectoralis minor': 'chest',
  // back
  back: 'back', lats: 'back', lat: 'back', 'middle back': 'back', 'upper back': 'back',
  'lower back': 'back', traps: 'back', trapezius: 'back', 'spinal erectors': 'back',
  rhomboids: 'back', 'rear delts': 'back', 'rear delt': 'back',
  // legs
  legs: 'legs', quads: 'legs', quadriceps: 'legs', hamstrings: 'legs', glutes: 'legs',
  calves: 'legs', adductors: 'legs', abductors: 'legs', 'hip flexors': 'legs',
  // shoulders
  shoulders: 'shoulders', 'front delts': 'shoulders', 'front delt': 'shoulders',
  'side delts': 'shoulders', 'side delt': 'shoulders', 'rotator cuff': 'shoulders',
  deltoids: 'shoulders', 'anterior deltoid': 'shoulders', 'lateral deltoid': 'shoulders',
  'middle deltoid': 'shoulders', 'posterior deltoid': 'shoulders',
  // arms
  arms: 'arms', biceps: 'arms', triceps: 'arms', forearms: 'arms', brachialis: 'arms',
  'biceps long head': 'arms', 'biceps short head': 'arms',
  'triceps long head': 'arms', 'triceps lateral head': 'arms', 'triceps medial head': 'arms',
  // core
  core: 'core', abdominals: 'core', abs: 'core', obliques: 'core', serratus: 'core',
  'upper abs': 'core', 'lower abs': 'core', 'transverse abdominis': 'core',
};

const VOLUME_LANDMARKS = {
  chest:     { min: 10, max: 20, optimal: 14 },
  back:      { min: 10, max: 22, optimal: 16 },
  legs:      { min: 12, max: 22, optimal: 16 },
  shoulders: { min:  8, max: 16, optimal: 12 },
  arms:      { min:  8, max: 16, optimal: 12 },
  core:      { min:  6, max: 16, optimal: 10 },
};

/**
 * Classify an exercise name into a muscle group via keyword matching.
 * Returns the first matching muscle group key or null.
 * @param {string} name
 * @returns {string|null}
 */
function keywordMuscle(name) {
  if (!name) return null;
  const lower = name.toLowerCase();
  for (const [muscle, keywords] of Object.entries(MUSCLE_KEYWORDS)) {
    if (keywords.some((kw) => lower.includes(kw))) return muscle;
  }
  return null;
}

/**
 * Classify an exercise name into a movement pattern via keyword matching.
 * Returns the first matching pattern key or null.
 * @param {string} name
 * @returns {string|null}
 */
function keywordMovement(name) {
  if (!name) return null;
  const lower = name.toLowerCase();
  for (const [pattern, keywords] of Object.entries(MOVEMENT_KEYWORDS)) {
    if (keywords.some((kw) => lower.includes(kw))) return pattern;
  }
  return null;
}

function resolveMovementPattern(exercise, exerciseIndex) {
  const movementPattern = String(exercise?.movementPattern || '').trim().toLowerCase();
  if (movementPattern) {
    if (movementPattern === 'hinge') return 'hinge';
    if (movementPattern === 'squat') return 'squat';
    if (movementPattern === 'rotation' || movementPattern === 'isometric' || movementPattern === 'carry') return 'core';
    if (movementPattern === 'locomotion') return 'core';
  }
  const name = exercise?.name;
  const indexed = name && exerciseIndex ? exerciseIndex[name] : null;
  const indexedMovement = String(indexed?.movementPattern || '').trim().toLowerCase();
  if (indexedMovement) {
    if (indexedMovement === 'hinge') return 'hinge';
    if (indexedMovement === 'squat') return 'squat';
    if (indexedMovement === 'rotation' || indexedMovement === 'isometric' || indexedMovement === 'carry') return 'core';
    if (indexedMovement === 'locomotion') return 'core';
  }
  const primary = String(exercise?.primaryMuscle || exercise?.primaryMuscles?.[0] || indexed?.primaryMuscle || '').toLowerCase();
  if (primary.includes('chest') || primary.includes('shoulder') || primary.includes('triceps')) return 'push';
  if (primary.includes('back') || primary.includes('lat') || primary.includes('bicep')) return 'pull';
  if (primary.includes('quad') || primary.includes('hamstring') || primary.includes('glute') || primary.includes('calf')) return 'squat';
  if (primary.includes('core') || primary.includes('ab')) return 'core';
  return keywordMovement(name);
}

/**
 * Resolve the primary muscle group for one exercise.
 * Priority: exercise.primaryMuscles → exerciseIndex → keyword match.
 * @param {{ name?: string, primaryMuscles?: string[] }} exercise
 * @param {Object} exerciseIndex
 * @returns {string|null}
 */
function resolveMuscle(exercise, exerciseIndex) {
  // 1. primaryMuscles on the exercise object
  if (Array.isArray(exercise?.primaryMuscles) && exercise.primaryMuscles.length > 0) {
    return exercise.primaryMuscles[0].toLowerCase();
  }
  // 2. exerciseIndex fallback
  const name = exercise?.name;
  if (name && exerciseIndex) {
    const indexed = exerciseIndex[name];
    if (Array.isArray(indexed?.primaryMuscles) && indexed.primaryMuscles.length > 0) {
      return indexed.primaryMuscles[0].toLowerCase();
    }
  }
  // 3. keyword match
  return keywordMuscle(name);
}

// ---------------------------------------------------------------------------
// 1. computeWeeklyVolumeByMuscle
// ---------------------------------------------------------------------------

/**
 * Compute total work sets per muscle group for the current ISO week (Mon–Sun).
 *
 * @param {Array} history - Array of session objects.
 * @param {Object} [exerciseIndex={}] - Exercise library keyed by exercise name.
 * @returns {{ chest: number, back: number, legs: number, shoulders: number, arms: number, core: number }}
 */
export function computeWeeklyVolumeByMuscle(history, exerciseIndex = {}) {
  const totals = { chest: 0, back: 0, legs: 0, shoulders: 0, arms: 0, core: 0 };

  if (!Array.isArray(history) || history.length === 0) return totals;

  const weekStart = getWeekStart(new Date());

  for (const session of history) {
    const sessionDate = parseDate(session?.date);
    if (!sessionDate || sessionDate < weekStart) continue;

    for (const exercise of session?.exercises ?? []) {
      const rawMuscle = resolveMuscle(exercise, exerciseIndex);
      // Normalise fine-grained names (e.g. 'lats', 'quadriceps', 'upper chest')
      // to one of the 6 canonical group keys used by the volume bucket.
      const normalised = rawMuscle ? (MUSCLE_TO_GROUP[rawMuscle] ?? rawMuscle) : null;
      if (!normalised || !(normalised in totals)) continue;

      const ws = workSets(exercise);
      totals[normalised] += ws.length;
    }
  }

  return totals;
}

// ---------------------------------------------------------------------------
// 2. computeVolumeLandmarks
// ---------------------------------------------------------------------------

/**
 * Evaluate weekly set counts against evidence-based volume landmarks.
 *
 * @param {{ chest?: number, back?: number, legs?: number, shoulders?: number, arms?: number, core?: number }} weeklyVolume
 * @returns {{ [muscle: string]: { sets: number, status: 'low'|'optimal'|'high', target: { min: number, max: number, optimal: number } } }}
 */
export function computeVolumeLandmarks(weeklyVolume = {}) {
  const result = {};

  for (const [muscle, target] of Object.entries(VOLUME_LANDMARKS)) {
    const sets = Number(weeklyVolume[muscle]) || 0;
    let status;
    if (sets < target.min) {
      status = 'low';
    } else if (sets > target.max) {
      status = 'high';
    } else {
      status = 'optimal';
    }
    result[muscle] = { sets, status, target };
  }

  return result;
}

// ---------------------------------------------------------------------------
// 3. computePRVelocity
// ---------------------------------------------------------------------------

/**
 * Measure the rate at which new personal records are being set.
 *
 * @param {Array} history - Chronologically ordered sessions (oldest first preferred).
 * @param {Object} [pb={}] - Existing personal bests keyed by exercise name.
 * @returns {{ last30: number, prev30: number, trend: 'accelerating'|'steady'|'slowing', totalPRs: number }}
 */
export function computePRVelocity(history, pb = {}) {
  const empty = { last30: 0, prev30: 0, trend: 'steady', totalPRs: 0 };
  if (!Array.isArray(history) || history.length === 0) return empty;

  const now = new Date();
  const cutLast30 = daysAgo(30);
  const cutPrev30 = daysAgo(60);

  // Track running best e1rm per exercise across sessions (chronological).
  const runningBest = {}; // { [exerciseName]: number }
  let last30 = 0;
  let prev30 = 0;
  let totalPRs = 0;

  // Sort sessions chronologically ascending.
  const sorted = [...history].sort((a, b) => {
    const da = parseDate(a?.date);
    const db = parseDate(b?.date);
    if (!da && !db) return 0;
    if (!da) return 1;
    if (!db) return -1;
    return da - db;
  });

  for (const session of sorted) {
    const sessionDate = parseDate(session?.date);
    if (!sessionDate) continue;

    let sessionHadPR = false;

    for (const exercise of session?.exercises ?? []) {
      const name = exercise?.name;
      if (!name) continue;
      const prGroupId = getPrGroupId(name, {
        primaryMuscle: exercise?.primaryMuscle || exercise?.primaryMuscles?.[0],
        equipment: exercise?.equipment,
        category: exercise?.category,
      }, 'safe') || name;

      const ws = workSets(exercise);
      for (const set of ws) {
        const val = e1rm(set?.weight || 0, set?.reps || 0);
        if (val <= 0) continue;

        const prev = runningBest[prGroupId] ?? 0;
        if (val > prev) {
          runningBest[prGroupId] = val;
          // Count as a PR whether it's the first time logging this exercise
          // or a genuine improvement over a previously recorded lift.
          sessionHadPR = true;
        }
      }
    }

    if (sessionHadPR) {
      totalPRs++;
      if (sessionDate >= cutLast30) {
        last30++;
      } else if (sessionDate >= cutPrev30) {
        prev30++;
      }
    }
  }

  const trend =
    last30 > prev30 ? 'accelerating' :
    last30 < prev30 ? 'slowing' :
    'steady';

  return { last30, prev30, trend, totalPRs };
}

// ---------------------------------------------------------------------------
// 4. computeMovementBalance
// ---------------------------------------------------------------------------

/**
 * Analyse push/pull/hinge/squat/core set distribution over the last 28 days.
 *
 * @param {Array} history
 * @param {Object} [exerciseIndex={}]
 * @returns {{ push: number, pull: number, hinge: number, squat: number, core: number, pushPullRatio: number, dominantPattern: string }}
 */
export function computeMovementBalance(history, exerciseIndex = {}) {
  const counts = { push: 0, pull: 0, hinge: 0, squat: 0, core: 0 };

  if (!Array.isArray(history) || history.length === 0) {
    return { ...counts, pushPullRatio: 1, dominantPattern: 'push' };
  }

  const cutoff = daysAgo(28);

  for (const session of history) {
    const sessionDate = parseDate(session?.date);
    if (!sessionDate || sessionDate < cutoff) continue;

    for (const exercise of session?.exercises ?? []) {
      const pattern = resolveMovementPattern(exercise, exerciseIndex);
      if (!pattern || !(pattern in counts)) continue;

      const ws = workSets(exercise);
      counts[pattern] += ws.length;
    }
  }

  const pushPullRatio = counts.pull > 0
    ? parseFloat((counts.push / counts.pull).toFixed(2))
    : counts.push > 0 ? Infinity : 1;

  const dominantPattern = Object.entries(counts).reduce(
    (best, [k, v]) => (v > (counts[best] ?? 0) ? k : best),
    'push',
  );

  return { ...counts, pushPullRatio, dominantPattern };
}

// ---------------------------------------------------------------------------
// 5. computeNeuralFatigue
// ---------------------------------------------------------------------------

/**
 * Detect accumulated heavy-compound fatigue over the last 14 days.
 *
 * @param {Array} history
 * @returns {{ consecutiveDays: number, isFlagged: boolean, lastHeavyExercises: string[] }}
 */
export function computeNeuralFatigue(history) {
  const empty = { consecutiveDays: 0, isFlagged: false, lastHeavyExercises: [] };
  if (!Array.isArray(history) || history.length === 0) return empty;

  const now = new Date();
  now.setHours(0, 0, 0, 0);
  const cutoff = daysAgo(14);

  /**
   * Build a set of date-strings (YYYY-MM-DD) where a heavy compound appeared,
   * and track the exercise names from the most recent heavy session.
   */
  const heavyDays = new Set(); // 'YYYY-MM-DD'
  const heavyByDay = {}; // { 'YYYY-MM-DD': string[] }

  for (const session of history) {
    const sessionDate = parseDate(session?.date);
    if (!sessionDate || sessionDate < cutoff) continue;

    const dateKey = sessionDate.toISOString().slice(0, 10);
    const heavyNames = [];

    for (const exercise of session?.exercises ?? []) {
      const name = exercise?.name ?? '';
      const lower = name.toLowerCase();
      const isHeavy = HEAVY_COMPOUNDS.some((c) => lower.includes(c));
      if (!isHeavy) continue;

      const ws = workSets(exercise);
      if (ws.length >= 1) heavyNames.push(name);
    }

    if (heavyNames.length > 0) {
      heavyDays.add(dateKey);
      heavyByDay[dateKey] = [...new Set([...(heavyByDay[dateKey] ?? []), ...heavyNames])];
    }
  }

  // Walk backwards from today, counting consecutive days with heavy compounds.
  let consecutiveDays = 0;
  const todayStr = now.toISOString().slice(0, 10);
  const yesterdayDate = new Date(now);
  yesterdayDate.setDate(now.getDate() - 1);
  const yesterdayStr = yesterdayDate.toISOString().slice(0, 10);

  // Start from today or yesterday (allow for sessions logged same-day).
  const startKey = heavyDays.has(todayStr) ? todayStr : yesterdayStr;
  if (!heavyDays.has(startKey)) {
    return { consecutiveDays: 0, isFlagged: false, lastHeavyExercises: [] };
  }

  const cursor = new Date(startKey);
  while (true) {
    const key = cursor.toISOString().slice(0, 10);
    if (!heavyDays.has(key)) break;
    consecutiveDays++;
    cursor.setDate(cursor.getDate() - 1);
  }

  // Exercises from the most recent heavy session.
  const lastHeavyExercises = heavyByDay[startKey] ?? [];

  return {
    consecutiveDays,
    isFlagged: consecutiveDays >= 3,
    lastHeavyExercises,
  };
}

// ---------------------------------------------------------------------------
// 6. computeBestPerformanceWindow
// ---------------------------------------------------------------------------

/**
 * Identify the time-of-day window in which the athlete most frequently sets PRs.
 *
 * @param {Array} history
 * @returns {{ bestWindow: string, windowLabel: string, totalPRSessions: number, windowCounts: { morning: number, afternoon: number, evening: number, night: number } } | null}
 */
export function computeBestPerformanceWindow(history) {
  if (!Array.isArray(history) || history.length === 0) return null;

  const windowCounts = { morning: 0, afternoon: 0, evening: 0, night: 0 };
  const windowLabels = {
    morning:   '5–11am',
    afternoon: '12–4pm',
    evening:   '5–8pm',
    night:     '9pm–4am',
  };

  /**
   * Classify a 0-23 hour into a named window.
   * night spans 21-23 and 0-4.
   */
  function hourToWindow(h) {
    if (h >= 5  && h <= 11) return 'morning';
    if (h >= 12 && h <= 16) return 'afternoon';
    if (h >= 17 && h <= 20) return 'evening';
    return 'night'; // 21-23 and 0-4
  }

  // Sort chronologically to track running bests.
  const sorted = [...history].sort((a, b) => {
    const da = parseDate(a?.date);
    const db = parseDate(b?.date);
    if (!da && !db) return 0;
    if (!da) return 1;
    if (!db) return -1;
    return da - db;
  });

  const runningBest = {}; // { [exerciseName]: number }
  let totalPRSessions = 0;

  for (const session of sorted) {
    const sessionDate = parseDate(session?.date);
    if (!sessionDate) continue;

    let sessionHadPR = false;

    for (const exercise of session?.exercises ?? []) {
      const name = exercise?.name;
      if (!name) continue;
      const prGroupId = getPrGroupId(name, {
        primaryMuscle: exercise?.primaryMuscle || exercise?.primaryMuscles?.[0],
        equipment: exercise?.equipment,
        category: exercise?.category,
      }, 'safe') || name;

      const ws = workSets(exercise);
      for (const set of ws) {
        const val = e1rm(set?.weight || 0, set?.reps || 0);
        if (val <= 0) continue;

        const prev = runningBest[prGroupId] ?? 0;
        if (val > prev) {
          runningBest[prGroupId] = val;
          if (prev > 0) sessionHadPR = true;
        }
      }
    }

    if (sessionHadPR) {
      totalPRSessions++;
      const hour = sessionDate.getHours();
      const window = hourToWindow(hour);
      windowCounts[window]++;
    }
  }

  if (totalPRSessions < 3) return null;

  const bestWindow = Object.entries(windowCounts).reduce(
    (best, [w, c]) => (c > windowCounts[best] ? w : best),
    'morning',
  );

  return {
    bestWindow,
    windowLabel: windowLabels[bestWindow],
    totalPRSessions,
    windowCounts,
  };
}

// ---------------------------------------------------------------------------
// 7. computeTrainingAge
// ---------------------------------------------------------------------------

/**
 * Estimate the athlete's training age and progression stage.
 *
 * @param {Array} history
 * @returns {{ classification: 'beginner'|'intermediate'|'advanced', historyMonths: number, monthlyProgressionKg: number, label: string, tip: string }}
 */
export function computeTrainingAge(history) {
  const defaultResult = {
    classification: 'beginner',
    historyMonths: 0,
    monthlyProgressionKg: 0,
    label: 'Beginner',
    tip: 'Focus on form and adding weight each session',
  };

  if (!Array.isArray(history) || history.length === 0) return defaultResult;

  const sorted = [...history].sort((a, b) => {
    const da = parseDate(a?.date);
    const db = parseDate(b?.date);
    if (!da && !db) return 0;
    if (!da) return 1;
    if (!db) return -1;
    return da - db;
  });

  const firstDate = parseDate(sorted[0]?.date);
  if (!firstDate) return defaultResult;

  const now = new Date();
  const historyMonths = Math.max(
    1,
    (now.getFullYear() - firstDate.getFullYear()) * 12 +
      (now.getMonth() - firstDate.getMonth()),
  );

  // Compound lift keywords for identifying relevant exercises.
  const compoundKeywords = ['bench', 'squat', 'deadlift', 'overhead press', 'ohp'];

  function isCompound(name = '') {
    const lower = name.toLowerCase();
    return compoundKeywords.some((kw) => lower.includes(kw));
  }

  function bestE1rmInSession(session) {
    let best = 0;
    for (const exercise of session?.exercises ?? []) {
      if (!isCompound(exercise?.name)) continue;
      for (const set of workSets(exercise)) {
        const val = e1rm(set?.weight || 0, set?.reps || 0);
        if (val > best) best = val;
      }
    }
    return best;
  }

  const compoundSessions = sorted.filter((s) =>
    (s?.exercises ?? []).some((ex) => isCompound(ex?.name)),
  );

  if (compoundSessions.length < 2) {
    return { ...defaultResult, historyMonths };
  }

  const firstFour = compoundSessions.slice(0, 4);
  const lastFour = compoundSessions.slice(-4);

  const avgE1rmFirst =
    firstFour.reduce((sum, s) => sum + bestE1rmInSession(s), 0) / firstFour.length;
  const avgE1rmLast =
    lastFour.reduce((sum, s) => sum + bestE1rmInSession(s), 0) / lastFour.length;

  const monthlyProgressionKg = parseFloat(
    ((avgE1rmLast - avgE1rmFirst) / historyMonths).toFixed(2),
  );

  let classification;
  if (monthlyProgressionKg > 3 || historyMonths < 6) {
    classification = 'beginner';
  } else if (monthlyProgressionKg < 0.5 && historyMonths > 18) {
    classification = 'advanced';
  } else {
    classification = 'intermediate';
  }

  const labels = {
    beginner:     'Beginner',
    intermediate: 'Intermediate',
    advanced:     'Advanced',
  };
  const tips = {
    beginner:     'Focus on form and adding weight each session',
    intermediate: 'Periodization matters — vary intensity weekly',
    advanced:     'Marginal gains: sleep, nutrition, and technique refinement',
  };

  return {
    classification,
    historyMonths,
    monthlyProgressionKg,
    label: labels[classification],
    tip: tips[classification],
  };
}

// ---------------------------------------------------------------------------
// 8. generateWarmupSets
// ---------------------------------------------------------------------------

/**
 * Generate a standard warm-up ladder for a barbell lift.
 *
 * @param {number} workingWeightKg - Target working weight in kg.
 * @param {number} [barWeightKg=20] - Bar weight (default 20 kg Olympic bar).
 * @returns {Array<{ weight: number, reps: number, label: string }>}
 */
export function generateWarmupSets(workingWeightKg, barWeightKg = 20) {
  const working = Number(workingWeightKg) || 0;
  const bar = Number(barWeightKg) || 20;

  /** Round to nearest 2.5 kg plate increment. */
  function round25(kg) {
    return Math.round(kg / 2.5) * 2.5;
  }

  if (working <= 40) {
    return [{ weight: working, reps: 10, label: 'Warm-up' }];
  }

  const ladder = [
    { pct: null,  weight: bar,                   reps: 10, label: 'Bar speed' },
    { pct: 0.40,  weight: round25(working * 0.40), reps: 8,  label: '40%' },
    { pct: 0.60,  weight: round25(working * 0.60), reps: 5,  label: '60%' },
    { pct: 0.75,  weight: round25(working * 0.75), reps: 3,  label: '75%' },
    { pct: 0.85,  weight: round25(working * 0.85), reps: 2,  label: '85%' },
  ];

  return ladder
    .filter((step) => step.weight < working)
    .map(({ weight, reps, label }) => ({ weight, reps, label }));
}
