// Ironlog Intelligence Engine - Exercise Normalization, Fatigue, & SFR Modeling
export const MUSCLES = [
  "upperChest", "midChest", "lowerChest",
  "lats", "upperBack", "lowerBack", "traps",
  "biceps_long", "biceps_short", "triceps_long", "triceps_lateral", "triceps_medial", "forearms",
  "frontDelts", "lateralDelts", "rearDelts",
  "quads", "hamstrings", "glutes", "calves",
  "upperAbs", "lowerAbs", "obliques"
];

export const GROUPS = {
  chest: ["upperChest", "midChest", "lowerChest"],
  back: ["lats", "upperBack", "lowerBack", "traps"],
  arms: ["biceps_long", "biceps_short", "triceps_long", "triceps_lateral", "triceps_medial", "forearms"],
  shoulders: ["frontDelts", "lateralDelts", "rearDelts"],
  legs: ["quads", "hamstrings", "glutes", "calves"],
  core: ["upperAbs", "lowerAbs", "obliques"]
};

// --- BIOMECHANICAL PATTERNS ---
const PATTERN_flatPress = { midChest: 3, frontDelts: 1, triceps_lateral: 1 };
const PATTERN_inclinePress = { upperChest: 3, frontDelts: 2, triceps_lateral: 1 };
const PATTERN_declinePress = { lowerChest: 3, triceps_lateral: 1 };
const PATTERN_fly = { midChest: 3, upperChest: 1 };
const PATTERN_shoulderPress = { frontDelts: 3, lateralDelts: 2, triceps_lateral: 1 };

const PATTERN_triceps_overhead = { triceps_long: 3, triceps_lateral: 1 };
const PATTERN_triceps_pushdown = { triceps_lateral: 3, triceps_medial: 2 };

const PATTERN_bicepCurl = { biceps_short: 2, biceps_long: 2, forearms: 1 };
const PATTERN_bicep_incline = { biceps_long: 3, forearms: 1 };
const PATTERN_bicep_preacher = { biceps_short: 3, forearms: 1 };
const PATTERN_hammer = { forearms: 3, biceps_long: 1, biceps_short: 1 };

const PATTERN_row = { lats: 2, upperBack: 3, rearDelts: 1, biceps_short: 1 };
const PATTERN_pullup = { lats: 3, upperBack: 1, biceps_short: 2 };

const PATTERN_squat = { quads: 3, glutes: 2, lowerBack: 1 };
const PATTERN_hinge = { hamstrings: 3, glutes: 3, lowerBack: 2 };
const PATTERN_legExtension = { quads: 3 };
const PATTERN_legCurl = { hamstrings: 3 };
const PATTERN_calf = { calves: 3 };

const PATTERN_crunch = { upperAbs: 3, lowerAbs: 1 };
const PATTERN_legRaise = { lowerAbs: 3, upperAbs: 1 };

const PATTERN_lateralRaise = { lateralDelts: 3, frontDelts: 1 };
const PATTERN_shrug = { traps: 3, upperBack: 1 };
const PATTERN_facePull = { rearDelts: 3, upperBack: 2, lateralDelts: 1 };
const PATTERN_frontRaise = { frontDelts: 3, midChest: 1 };
const PATTERN_pushup = { midChest: 2, frontDelts: 1, triceps_lateral: 1 };
const PATTERN_coreGeneric = { upperAbs: 2, lowerAbs: 2, obliques: 1 };

export const PATTERNS = {
  flatPress: PATTERN_flatPress,
  inclinePress: PATTERN_inclinePress,
  declinePress: PATTERN_declinePress,
  fly: PATTERN_fly,
  shoulderPress: PATTERN_shoulderPress,
  triceps_overhead: PATTERN_triceps_overhead,
  triceps_pushdown: PATTERN_triceps_pushdown,
  bicepCurl: PATTERN_bicepCurl,
  bicep_incline: PATTERN_bicep_incline,
  bicep_preacher: PATTERN_bicep_preacher,
  hammer: PATTERN_hammer,
  row: PATTERN_row,
  pullup: PATTERN_pullup,
  squat: PATTERN_squat,
  hinge: PATTERN_hinge,
  legExtension: PATTERN_legExtension,
  legCurl: PATTERN_legCurl,
  calf: PATTERN_calf,
  crunch: PATTERN_crunch,
  legRaise: PATTERN_legRaise,
  lateralRaise: PATTERN_lateralRaise,
  shrug: PATTERN_shrug,
  facePull: PATTERN_facePull,
  frontRaise: PATTERN_frontRaise,
  pushup: PATTERN_pushup,
  coreGeneric: PATTERN_coreGeneric
};

// --- KEYWORD WEIGHTING LOGIC ---
const KEYWORDS = [
  { words: ["overhead press", "shoulder press", "military press", "arnold press"], pattern: "shoulderPress", weight: 6 },
  { words: ["incline"], pattern: "inclinePress", weight: 3 },
  { words: ["decline", "dip"], pattern: "declinePress", weight: 3 },
  { words: ["fly", "crossover", "pec deck", "butterfly"], pattern: "fly", weight: 3 },
  { words: ["press", "push-up", "pushup"], pattern: "flatPress", weight: 2 },
  { words: ["overhead", "extension", "skullcrusher"], pattern: "triceps_overhead", weight: 3 },
  { words: ["pushdown", "kickback"], pattern: "triceps_pushdown", weight: 3 },
  { words: ["preacher"], pattern: "bicep_preacher", weight: 3 },
  { words: ["incline curl"], pattern: "bicep_incline", weight: 3 },
  { words: ["hammer"], pattern: "hammer", weight: 3 },
  { words: ["curl"], pattern: "bicepCurl", weight: 2 },
  { words: ["row", "pullover"], pattern: "row", weight: 3 },
  { words: ["pull-up", "pullup", "chin", "pulldown"], pattern: "pullup", weight: 3 },
  { words: ["squat", "lunge", "step", "jumps", "jump squat"], pattern: "squat", weight: 3 },
  { words: ["deadlift", "rdl", "good morning", "thrust", "swing", "clean", "snatch", "jerk"], pattern: "hinge", weight: 3 },
  { words: ["leg extension"], pattern: "legExtension", weight: 4 },
  { words: ["leg curl"], pattern: "legCurl", weight: 4 },
  { words: ["calf"], pattern: "calf", weight: 4 },
  { words: ["crunch", "sit-up", "situp"], pattern: "crunch", weight: 3 },
  { words: ["leg raise", "knee raise", "leg-raise"], pattern: "legRaise", weight: 3 },
  { words: ["plank", "ab roller", "v-sit", "burpee", "climb", "jack", "hold", "bridge"], pattern: "coreGeneric", weight: 3 },
  { words: ["lateral", "side raise"], pattern: "lateralRaise", weight: 4 },
  { words: ["shrug"], pattern: "shrug", weight: 4 },
  { words: ["face pull"], pattern: "facePull", weight: 4 },
  { words: ["front raise"], pattern: "frontRaise", weight: 4 }
];

// Map library muscle names to internal pattern IDs/muscles
const LIBRARY_MUSCLE_MAP = {
  "abdominals": ["upperAbs", "lowerAbs", "obliques"],
  "hamstrings": ["hamstrings"],
  "adductors": ["hamstrings"],
  "quadriceps": ["quads"],
  "biceps": ["biceps_short", "biceps_long"],
  "shoulders": ["frontDelts", "lateralDelts", "rearDelts"],
  "chest": ["midChest", "upperChest"],
  "middle back": ["upperBack", "traps"],
  "calves": ["calves"],
  "glutes": ["glutes"],
  "lower back": ["lowerBack"],
  "lats": ["lats"],
  "triceps": ["triceps_lateral", "triceps_long"],
  "traps": ["traps"],
  "forearms": ["forearms"],
  "neck": ["traps"],
  "abductors": ["glutes"]
};

function buildFallbackContribution(exercise) {
  const fallbackContrib = {};
  const rawMuscles = [];

  // Legacy schema
  if (Array.isArray(exercise.primaryMuscles)) {
    rawMuscles.push(...exercise.primaryMuscles);
  }
  if (typeof exercise.primary === "string") rawMuscles.push(exercise.primary);
  if (typeof exercise.muscle === "string") rawMuscles.push(exercise.muscle);

  // v2 schema
  if (typeof exercise.primaryMuscle === "string") rawMuscles.push(exercise.primaryMuscle);
  if (Array.isArray(exercise.secondaryMuscles)) {
    rawMuscles.push(...exercise.secondaryMuscles);
  }

  rawMuscles.forEach(m => {
    const key = String(m || "").trim().toLowerCase();
    const internalMuscles = LIBRARY_MUSCLE_MAP[key] || [];
    internalMuscles.forEach(im => {
      fallbackContrib[im] = (fallbackContrib[im] || 0) + 1;
    });
  });

  return fallbackContrib;
}

export function getPatternScores(name) {
  const scores = {};
  KEYWORDS.forEach(({ words, pattern, weight }) => {
    words.forEach(word => {
      if (name.includes(word)) {
        scores[pattern] = (scores[pattern] || 0) + weight;
      }
    });
  });
  return scores;
}

export function selectPattern(scores) {
  let best = null;
  let max = 0;
  Object.entries(scores).forEach(([pattern, score]) => {
    if (score > max) {
      max = score;
      best = pattern;
    }
  });
  return best ? { key: best, data: PATTERNS[best] } : null;
}

export function normalize(obj) {
  const sum = Object.values(obj).reduce((a, b) => a + b, 0);
  if (sum === 0) return {};
  const normalized = {};
  Object.entries(obj).forEach(([k, v]) => {
    normalized[k] = v / sum; // Fractional contribution
  });
  return normalized;
}

export function generateExerciseMap(exerciseList) {
  const map = {};
  exerciseList.forEach(exercise => {
    const lower = exercise.name.toLowerCase();
    const scores = getPatternScores(lower);
    const patternEntry = selectPattern(scores);

    if (patternEntry && patternEntry.data) {
      map[exercise.name] = {
        patternKey: patternEntry.key,
        contribution: normalize(patternEntry.data)
      };
    } else {
      // Fallback to library muscles (supports both legacy and v2 schemas)
      const fallbackContrib = buildFallbackContribution(exercise);

      if (Object.keys(fallbackContrib).length > 0) {
        map[exercise.name] = {
          patternKey: 'unknown',
          contribution: normalize(fallbackContrib)
        };
      } else {
        map[exercise.name] = { patternKey: 'unknown', contribution: {} };
      }
    }
  });
  return map;
}

// --- STIMULUS VS FATIGUE (SFR MODEL) ---
export const PATTERN_META = {
  flatPress: { stimulus: 0.8, fatigue: 1.0 },
  inclinePress: { stimulus: 0.85, fatigue: 1.0 },
  declinePress: { stimulus: 0.8, fatigue: 1.0 },
  fly: { stimulus: 0.9, fatigue: 0.6 },
  shoulderPress: { stimulus: 0.9, fatigue: 0.95 },
  row: { stimulus: 0.85, fatigue: 0.9 },
  pullup: { stimulus: 0.9, fatigue: 0.95 },
  squat: { stimulus: 0.9, fatigue: 1.2 },
  hinge: { stimulus: 0.9, fatigue: 1.3 },
  legExtension: { stimulus: 0.85, fatigue: 0.6 },
  legCurl: { stimulus: 0.85, fatigue: 0.6 },
  bicepCurl: { stimulus: 0.9, fatigue: 0.5 },
  triceps_pushdown: { stimulus: 0.9, fatigue: 0.5 },
  triceps_overhead: { stimulus: 0.95, fatigue: 0.6 },
  hammer: { stimulus: 0.8, fatigue: 0.4 },
  bicep_preacher: { stimulus: 0.9, fatigue: 0.5 },
  bicep_incline: { stimulus: 0.9, fatigue: 0.6 },
  calf: { stimulus: 0.8, fatigue: 0.4 },
  crunch: { stimulus: 0.7, fatigue: 0.4 },
  legRaise: { stimulus: 0.75, fatigue: 0.45 },
  lateralRaise: { stimulus: 0.9, fatigue: 0.4 },
  shrug: { stimulus: 0.85, fatigue: 0.5 },
  facePull: { stimulus: 0.9, fatigue: 0.5 },
  frontRaise: { stimulus: 0.85, fatigue: 0.4 },
  pushup: { stimulus: 0.8, fatigue: 0.8 },
  coreGeneric: { stimulus: 0.7, fatigue: 0.4 },
  unknown: { stimulus: 0.5, fatigue: 0.5 }
};

export const VOLUME_TARGETS = {
  chest: [10, 20],
  back: [12, 22],
  shoulders: [10, 18],
  arms: [8, 16],
  legs: [12, 20],
  core: [6, 12]
};

export function volumeToSets(volume) {
  return volume / 1000;
}

export function computeStimulusFatigue(workouts, exerciseMap) {
  const stimulus = {};
  const fatigue = {};
  MUSCLES.forEach(m => {
    stimulus[m] = 0;
    fatigue[m] = 0;
  });

  workouts.forEach(w => {
    // Expected structure: { name, sets, reps, weight, oneRM }
    const volume = w.sets * w.reps * (w.weight || 0);
    const intensity = w.oneRM ? (w.weight / w.oneRM) : 0.7; // default to 0.7 if no 1RM available

    let mappingState = exerciseMap[w.name];
    
    // If exact name not found in map, attempt to resolve via EXERCISE_ID_MAP or calculate dynamic pattern
    if (!mappingState || !mappingState.contribution || Object.keys(mappingState.contribution).length === 0) {
      const EXERCISE_ID_MAP = require('../data/exerciseMapping').EXERCISE_ID_MAP;
      const mappedId = EXERCISE_ID_MAP[w.name];
      let searchName = w.name;
      
      if (mappedId) {
        const EXERCISES = require('../data/exerciseLibrary').EXERCISES;
        const libMatch = EXERCISES.find(e => e.id === mappedId);
        if (libMatch) {
          searchName = libMatch.name;
          mappingState = exerciseMap[searchName];
        }
      }
      
      // Fallback: Compute pattern directly if still not found
      if (!mappingState || !mappingState.contribution || Object.keys(mappingState.contribution).length === 0) {
        const scores = getPatternScores(searchName.toLowerCase());
        const patternEntry = selectPattern(scores);
        if (patternEntry && patternEntry.data) {
          mappingState = {
            patternKey: patternEntry.key,
            contribution: normalize(patternEntry.data)
          };
        } else {
          // Absolute last resort: try to find the exercise in library and use fallback logic
          const EXERCISES = require('../data/exerciseLibrary').EXERCISES;
          const libMatch = EXERCISES.find(e => e.name === searchName);
          if (libMatch) {
            const fallbackContrib = buildFallbackContribution(libMatch);
            mappingState = { patternKey: 'unknown', contribution: normalize(fallbackContrib) };
          }
        }
      }
    }

    if (mappingState && mappingState.contribution) {
      const meta = PATTERN_META[mappingState.patternKey] || PATTERN_META.unknown;
      Object.entries(mappingState.contribution).forEach(([muscle, contrib]) => {
        stimulus[muscle] += volume * contrib * meta.stimulus;
        fatigue[muscle] += volume * contrib * meta.fatigue * intensity;
      });
    }
  });

  return { stimulus, fatigue };
}

export function detectJunkVolume(stimulus, fatigue) {
  const junk = {};
  MUSCLES.forEach(m => {
    if (fatigue[m] > 0) {
      const sfr = stimulus[m] / fatigue[m];
      if (sfr < 0.8) junk[m] = true;
    }
  });
  return junk;
}

export function analyzeVolume(groupStimulus) {
  const status = {};
  Object.entries(GROUPS).forEach(([group, muscles]) => {
    const total = muscles.reduce((sum, m) => sum + (groupStimulus[m] || 0), 0);
    const sets = volumeToSets(total);
    const [min, max] = VOLUME_TARGETS[group];

    if (sets < min) status[group] = "undertrained";
    else if (sets > max) status[group] = "overtrained";
    else status[group] = "optimal";
  });
  return status;
}

const HOME_GROUPS = ['core', 'arms', 'chest', 'legs', 'back', 'shoulders'];
const GROUP_ALIASES = {
  abs: 'core',
  upperabs: 'core',
  lowerabs: 'core',
  obliques: 'core',
  core: 'core',
  biceps: 'arms',
  bicepslong: 'arms',
  bicepsshort: 'arms',
  triceps: 'arms',
  tricepslong: 'arms',
  tricepslateral: 'arms',
  tricepsmedial: 'arms',
  forearms: 'arms',
  chest: 'chest',
  upperchest: 'chest',
  midchest: 'chest',
  lowerchest: 'chest',
  quads: 'legs',
  quadriceps: 'legs',
  hamstrings: 'legs',
  glutes: 'legs',
  calves: 'legs',
  back: 'back',
  upperback: 'back',
  lowerback: 'back',
  middleback: 'back',
  lats: 'back',
  traps: 'back',
  shoulders: 'shoulders',
  shoulder: 'shoulders',
  deltoids: 'shoulders',
  deltoid: 'shoulders',
  frontdelts: 'shoulders',
  frontdeltoid: 'shoulders',
  lateraldelts: 'shoulders',
  lateraldeltoid: 'shoulders',
  reardelts: 'shoulders',
  reardeltoid: 'shoulders',
  rotatorcuff: 'shoulders',
};

const GROUP_SET_TARGETS_14D = {
  core: 6,
  arms: 8,
  chest: 10,
  legs: 12,
  back: 12,
  shoulders: 8,
};

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function toDayStart(dateLike) {
  const d = new Date(dateLike);
  d.setHours(0, 0, 0, 0);
  return d;
}

function getWeekKey(dateLike) {
  const d = toDayStart(dateLike);
  const mondayOffset = d.getDay() === 0 ? -6 : 1 - d.getDay();
  d.setDate(d.getDate() + mondayOffset);
  return d.toISOString().slice(0, 10);
}

function normalizeGroupKey(value) {
  const key = String(value || '')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '');
  return GROUP_ALIASES[key] || null;
}

function getExerciseGroups(exercise) {
  const values = [];
  if (Array.isArray(exercise?.primaryMuscles)) values.push(...exercise.primaryMuscles);
  else if (exercise?.primaryMuscles) values.push(exercise.primaryMuscles);
  if (exercise?.primaryMuscle) values.push(exercise.primaryMuscle);
  if (exercise?.primary) values.push(exercise.primary);
  if (exercise?.muscle) values.push(exercise.muscle);
  if (exercise?.target) values.push(exercise.target);

  const out = [];
  const seen = new Set();
  values.forEach((value) => {
    const group = normalizeGroupKey(value);
    if (!group || seen.has(group)) return;
    seen.add(group);
    out.push(group);
  });
  return out;
}

function getWorkingSetCount(exercise) {
  if (!exercise) return 0;
  if (Array.isArray(exercise.sets)) {
    return exercise.sets.filter((setItem) => (setItem?.type || 'normal') !== 'warmup').length;
  }
  if (typeof exercise.sets === 'number') return Math.max(0, exercise.sets);
  return 0;
}

function parseWeightEntry(entry) {
  const value = Number(entry?.weight);
  if (!Number.isFinite(value)) return null;
  const d = toDayStart(entry?.date || Date.now());
  return { date: d, value };
}

function buildWorkoutRows(history, now, daysBack) {
  const threshold = new Date(now.getTime() - daysBack * 86400000);
  return (history || []).filter((item) => {
    const d = new Date(item?.date || 0);
    return Number.isFinite(d.getTime()) && d >= threshold;
  });
}

function computeAdherenceMetrics(history, activePlan) {
  const recent = buildWorkoutRows(history, new Date(), 28);
  const weekTotals = {};
  recent.forEach((row) => {
    const key = getWeekKey(row.date);
    weekTotals[key] = (weekTotals[key] || 0) + 1;
  });

  const weekCount = Math.max(1, Object.keys(weekTotals).length);
  const avgWeeklyCompleted = recent.length / weekCount;
  const plannedPerWeek = activePlan?.days?.length || 0;
  const adherenceScore = plannedPerWeek > 0
    ? clamp(Math.round((avgWeeklyCompleted / plannedPerWeek) * 100), 0, 100)
    : clamp(Math.round((avgWeeklyCompleted / 4) * 100), 0, 100);

  const realismPenalty = plannedPerWeek > 0
    ? Math.max(0, plannedPerWeek - avgWeeklyCompleted - 1) * 22
    : 0;
  const programRealismScore = clamp(Math.round(100 - realismPenalty), 20, 100);

  return { plannedPerWeek, avgWeeklyCompleted, adherenceScore, programRealismScore };
}

function computeGroupSetVolumes(history) {
  const recent = buildWorkoutRows(history, new Date(), 14);
  const totals = {};
  HOME_GROUPS.forEach((group) => { totals[group] = 0; });

  recent.forEach((session) => {
    (session.exercises || []).forEach((exercise) => {
      const setCount = getWorkingSetCount(exercise);
      if (!setCount) return;
      const groups = getExerciseGroups(exercise);
      if (!groups.length) return;
      const perGroup = setCount / groups.length;
      groups.forEach((group) => {
        totals[group] = (totals[group] || 0) + perGroup;
      });
    });
  });
  return totals;
}

function computeMuscleBalanceScore(groupSets) {
  const values = HOME_GROUPS.map((g) => groupSets[g] || 0);
  const nonZero = values.filter((v) => v > 0);
  if (nonZero.length === 0) return 40;

  const avg = values.reduce((sum, value) => sum + value, 0) / values.length;
  if (avg <= 0) return 40;
  const variance = values.reduce((sum, value) => sum + ((value - avg) ** 2), 0) / values.length;
  const cv = Math.sqrt(variance) / avg;
  return clamp(Math.round(100 - (cv * 45)), 35, 100);
}

function computeProgressionQualityScore(history) {
  const recent = buildWorkoutRows(history, new Date(), 42);
  const byExercise = {};

  recent.forEach((session) => {
    (session.exercises || []).forEach((exercise) => {
      const sets = Array.isArray(exercise?.sets) ? exercise.sets : [];
      let best = 0;
      sets.forEach((setItem) => {
        if ((setItem?.type || 'normal') === 'warmup') return;
        const weight = Number(setItem?.weight || 0);
        const reps = Number(setItem?.reps || 0);
        if (!Number.isFinite(weight) || !Number.isFinite(reps) || weight <= 0 || reps <= 0) return;
        const e1rm = weight * (1 + reps / 30);
        if (e1rm > best) best = e1rm;
      });
      if (!best) return;
      const key = exercise?.name || exercise?.exerciseId || 'unknown';
      if (!byExercise[key]) byExercise[key] = [];
      byExercise[key].push({ date: new Date(session.date), e1rm: best });
    });
  });

  const deltas = [];
  Object.values(byExercise).forEach((rows) => {
    if (rows.length < 2) return;
    rows.sort((a, b) => a.date - b.date);
    const first = rows[0].e1rm;
    const last = rows[rows.length - 1].e1rm;
    if (first <= 0) return;
    deltas.push((last - first) / first);
  });

  if (!deltas.length) return 55;
  const avgDelta = deltas.reduce((sum, value) => sum + value, 0) / deltas.length;
  return clamp(Math.round(60 + (avgDelta * 180)), 25, 100);
}

function computeWeightTrend(bodyWeight) {
  const parsed = (bodyWeight || [])
    .map(parseWeightEntry)
    .filter(Boolean)
    .sort((a, b) => a.date - b.date);
  if (parsed.length < 2) return { weeklyDelta: 0, monthlyDelta: 0 };

  const latest = parsed[parsed.length - 1];
  const weekCut = new Date(latest.date.getTime() - 7 * 86400000);
  const monthCut = new Date(latest.date.getTime() - 30 * 86400000);

  const weekBase = parsed.find((row) => row.date >= weekCut) || parsed[0];
  const monthBase = parsed.find((row) => row.date >= monthCut) || parsed[0];
  return {
    weeklyDelta: latest.value - weekBase.value,
    monthlyDelta: latest.value - monthBase.value,
  };
}

function formatGroupLabel(group) {
  return String(group || '')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function computeAverageSessionMinutes(history, daysBack = 28) {
  const recent = buildWorkoutRows(history, new Date(), daysBack).filter((session) => Number(session?.duration) > 0);
  if (!recent.length) return 0;
  return recent.reduce((sum, session) => sum + (Number(session.duration) || 0), 0) / recent.length / 60;
}

function computeSessionCompletionRate(history, activePlan) {
  if (!activePlan?.days?.length) return 1;
  const dayMap = new Map(activePlan.days.map((day) => [day.id, day]));
  const recent = buildWorkoutRows(history, new Date(), 28);
  const rates = recent.map((session) => {
    const plannedDay = dayMap.get(session?.dayId);
    const plannedCount = Array.isArray(plannedDay?.exercises)
      ? plannedDay.exercises.filter((exercise) => !exercise?.isWarmup).length
      : 0;
    const loggedCount = Array.isArray(session?.exercises)
      ? session.exercises.filter((exercise) => !exercise?.isWarmup).length
      : 0;
    if (!plannedCount || !loggedCount) return null;
    return clamp(loggedCount / plannedCount, 0, 1.2);
  }).filter((value) => value != null);
  if (!rates.length) return 1;
  return rates.reduce((sum, value) => sum + value, 0) / rates.length;
}

function computeDayCompletionMetrics(history, activePlan) {
  if (!activePlan?.days?.length) {
    return { repeatedMissedDay: null, dayStats: [] };
  }

  const recent = buildWorkoutRows(history, new Date(), 28);
  const completions = {};
  recent.forEach((session) => {
    const key = session?.dayId;
    if (!key) return;
    completions[key] = (completions[key] || 0) + 1;
  });

  const dayStats = activePlan.days.map((day, index) => ({
    id: day.id,
    name: day.name || `Day ${index + 1}`,
    completions: completions[day.id] || 0,
  }));

  const repeatedMissedDay = dayStats
    .slice()
    .sort((a, b) => a.completions - b.completions)[0] || null;

  if (!repeatedMissedDay || repeatedMissedDay.completions > 0) {
    return { repeatedMissedDay: null, dayStats };
  }

  return { repeatedMissedDay, dayStats };
}

function computeUndertrainedGroups(groupSets) {
  return HOME_GROUPS.filter((group) => (groupSets[group] || 0) < (GROUP_SET_TARGETS_14D[group] || 8));
}

function computeOverEmphasizedGroups(groupSets, volumeStatus) {
  return HOME_GROUPS.filter((group) => {
    const status = volumeStatus[group];
    if (status === 'overtrained') return true;
    return (groupSets[group] || 0) >= ((GROUP_SET_TARGETS_14D[group] || 8) * 2.1);
  });
}

function computeRecoveryComplianceScore(groupReadiness, overEmphasized) {
  const lowReadiness = HOME_GROUPS
    .map((group) => ({ group, value: Number(groupReadiness[group] || 0) }))
    .sort((a, b) => a.value - b.value);
  const leastReady = lowReadiness[0];
  const mismatchPenalty = leastReady && overEmphasized.includes(leastReady.group) && leastReady.value < 0.5
    ? 45
    : leastReady && leastReady.value < 0.55
      ? 20
      : 8;
  return {
    leastReady,
    recoveryComplianceScore: clamp(Math.round(100 - mismatchPenalty), 20, 100),
  };
}

function chooseTemplateSwitch({ plannedPerWeek, adherenceScore, averageCompletionRate, avgSessionMinutes }) {
  if (plannedPerWeek >= 6 && (adherenceScore < 72 || averageCompletionRate < 0.8)) {
    return 'upper_lower_beginner_4x';
  }
  if (plannedPerWeek >= 5 && (adherenceScore < 65 || avgSessionMinutes > 95)) {
    return 'upper_lower_beginner_4x';
  }
  if (plannedPerWeek >= 4 && (adherenceScore < 48 || averageCompletionRate < 0.72)) {
    return 'full_body_beginner_3x';
  }
  return null;
}

function buildBaseReport({
  mode,
  headline,
  intensity,
  reason,
  scores,
  metrics,
  findings = [],
  focusGroups = [],
  recommendedTemplateId = null,
  suggestedSplitOrder = null,
  suggestedVolumeAdjustments = [],
}) {
  return {
    generatedAt: new Date().toISOString(),
    windowDays: 28,
    recommendationMode: mode,
    mode,
    headline,
    intensity,
    reason,
    recommendedTemplateId,
    suggestedTemplateId: recommendedTemplateId,
    suggestedSplitOrder,
    suggestedVolumeAdjustments,
    scores,
    findings,
    metrics,
    focusGroups,
  };
}

export function buildHomeProgramIntelligence({
  history = [],
  bodyWeight = [],
  activePlan = null,
  groupReadiness = {},
  volumeStatus = {},
}) {
  if (!Array.isArray(history) || history.length < 3) {
    return buildBaseReport({
      mode: 'fallback_low_data',
      headline: 'Log 2-3 workouts to unlock smarter recommendations',
      intensity: 'Moderate',
      reason: 'Need more history to model adherence, recovery, and progression.',
      scores: {
        adherenceScore: 0,
        recoveryComplianceScore: 0,
        programRealismScore: 0,
        muscleBalanceScore: 0,
        progressionQualityScore: 0,
        recoveryMismatchScore: 100,
      },
      metrics: {},
      findings: ['Need at least a few completed sessions before IronLog can judge your split or volume.'],
      focusGroups: [],
    });
  }

  const { plannedPerWeek, avgWeeklyCompleted, adherenceScore, programRealismScore } = computeAdherenceMetrics(history, activePlan);
  const groupSets = computeGroupSetVolumes(history);
  const muscleBalanceScore = computeMuscleBalanceScore(groupSets);
  const progressionQualityScore = computeProgressionQualityScore(history);
  const weightTrend = computeWeightTrend(bodyWeight);
  const undertrained = computeUndertrainedGroups(groupSets);
  const overEmphasized = computeOverEmphasizedGroups(groupSets, volumeStatus);
  const readinessEntries = HOME_GROUPS
    .map((group) => ({ group, value: Number(groupReadiness[group] || 0) }))
    .sort((a, b) => a.value - b.value);
  const leastReady = readinessEntries[0];
  const bestReady = [...readinessEntries].sort((a, b) => b.value - a.value)[0];
  const avgSessionMinutes = computeAverageSessionMinutes(history);
  const averageCompletionRate = computeSessionCompletionRate(history, activePlan);
  const { repeatedMissedDay, dayStats } = computeDayCompletionMetrics(history, activePlan);
  const { recoveryComplianceScore } = computeRecoveryComplianceScore(groupReadiness, overEmphasized);
  const scores = {
    adherenceScore,
    recoveryComplianceScore,
    programRealismScore,
    muscleBalanceScore,
    progressionQualityScore,
    recoveryMismatchScore: 100 - recoveryComplianceScore,
  };
  const findings = [];

  if (plannedPerWeek > 0) {
    findings.push(`You planned ${plannedPerWeek} sessions per week and are averaging ${avgWeeklyCompleted.toFixed(1)} lately.`);
  }
  if (undertrained.length) {
    findings.push(`${formatGroupLabel(undertrained[0])} volume is below target over the last 14 days.`);
  }
  if (overEmphasized.length) {
    findings.push(`${formatGroupLabel(overEmphasized[0])} is overemphasized versus your current set balance.`);
  }
  if (progressionQualityScore < 52) {
    findings.push('Performance on repeating lifts is mostly flat across the recent training window.');
  }
  if (Math.abs(weightTrend.monthlyDelta) >= 1.2) {
    findings.push(`Body weight moved ${weightTrend.monthlyDelta > 0 ? 'up' : 'down'} ${Math.abs(weightTrend.monthlyDelta).toFixed(1)} over the last month.`);
  }

  const templateId = chooseTemplateSwitch({
    plannedPerWeek,
    adherenceScore,
    averageCompletionRate,
    avgSessionMinutes,
  });

  if (templateId) {
    const targetFreq = templateId === 'full_body_beginner_3x' ? '3x/week' : '4x/week';
    return buildBaseReport({
      mode: 'switch_template',
      headline: `Switch to a ${targetFreq} structure`,
      intensity: 'Moderate',
      reason: `You average ${avgWeeklyCompleted.toFixed(1)} sessions and finish ${(averageCompletionRate * 100).toFixed(0)}% of planned work.`,
      recommendedTemplateId: templateId,
      scores,
      metrics: {
        plannedPerWeek,
        avgWeeklyCompleted,
        avgSessionMinutes,
        averageCompletionRate,
        groupSets,
        weightTrend,
        dayStats,
      },
      findings,
      focusGroups: undertrained.slice(0, 2),
      suggestedVolumeAdjustments: undertrained.slice(0, 2).map((group) => ({
        group,
        direction: 'increase',
        reason: `${formatGroupLabel(group)} is below your current target band.`,
      })),
    });
  }

  if (repeatedMissedDay && plannedPerWeek >= 4) {
    const suggestedSplitOrder = dayStats
      .slice()
      .sort((a, b) => b.completions - a.completions)
      .map((day) => day.name);
    return buildBaseReport({
      mode: 'reorder_split',
      headline: `Reorder ${repeatedMissedDay.name}`,
      intensity: 'Moderate',
      reason: `${repeatedMissedDay.name} keeps getting skipped while earlier days are completed.`,
      scores,
      metrics: {
        plannedPerWeek,
        avgWeeklyCompleted,
        avgSessionMinutes,
        averageCompletionRate,
        groupSets,
        weightTrend,
        dayStats,
      },
      findings,
      focusGroups: undertrained.slice(0, 2),
      suggestedSplitOrder,
    });
  }

  if (leastReady && leastReady.value < 0.45 && overEmphasized.includes(leastReady.group)) {
    return buildBaseReport({
      mode: 'reduce_intensity',
      headline: `Pull back ${formatGroupLabel(leastReady.group)} volume today`,
      intensity: 'Light / Deload',
      reason: `${formatGroupLabel(leastReady.group)} readiness is ${Math.round(leastReady.value * 100)}% while recent set volume is high.`,
      scores,
      metrics: {
        plannedPerWeek,
        avgWeeklyCompleted,
        avgSessionMinutes,
        averageCompletionRate,
        groupSets,
        weightTrend,
        dayStats,
      },
      findings,
      focusGroups: [leastReady.group],
      suggestedVolumeAdjustments: [{
        group: leastReady.group,
        direction: 'decrease',
        reason: 'Recovery is lagging behind recent training stress.',
      }],
    });
  }

  if (avgSessionMinutes > 92 && averageCompletionRate < 0.82) {
    return buildBaseReport({
      mode: 'cut_junk_volume',
      headline: 'Trim late-session volume',
      intensity: 'Moderate',
      reason: `Sessions average ${Math.round(avgSessionMinutes)} minutes and completion drops to ${(averageCompletionRate * 100).toFixed(0)}%.`,
      scores,
      metrics: {
        plannedPerWeek,
        avgWeeklyCompleted,
        avgSessionMinutes,
        averageCompletionRate,
        groupSets,
        weightTrend,
        dayStats,
      },
      findings,
      focusGroups: overEmphasized.slice(0, 2),
      suggestedVolumeAdjustments: overEmphasized.slice(0, 2).map((group) => ({
        group,
        direction: 'decrease',
        reason: 'Reduce lower-priority accessories to improve completion.',
      })),
    });
  }

  if (undertrained.length) {
    const prioritized = undertrained
      .map((group) => ({ group, readiness: Number(groupReadiness[group] || 0) }))
      .sort((a, b) => b.readiness - a.readiness)[0];
    const intensity = prioritized.readiness > 0.82 ? 'Heavy' : 'Moderate';
    return buildBaseReport({
      mode: 'train_next',
      headline: `Train ${formatGroupLabel(prioritized.group)} next`,
      intensity,
      reason: `${formatGroupLabel(prioritized.group)} volume is low and readiness is ${Math.round(prioritized.readiness * 100)}%.`,
      scores,
      metrics: {
        plannedPerWeek,
        avgWeeklyCompleted,
        avgSessionMinutes,
        averageCompletionRate,
        groupSets,
        weightTrend,
        dayStats,
      },
      findings,
      focusGroups: [prioritized.group],
      suggestedVolumeAdjustments: [{
        group: prioritized.group,
        direction: 'increase',
        reason: 'Recent volume is below target and recovery looks good.',
      }],
    });
  }

  if (overEmphasized.length) {
    const shiftTo = bestReady?.group || 'legs';
    return buildBaseReport({
      mode: 'rebalance_volume',
      headline: `Rebalance toward ${formatGroupLabel(shiftTo)}`,
      intensity: 'Moderate',
      reason: `${formatGroupLabel(overEmphasized[0])} is ahead of your 14-day set balance.`,
      scores,
      metrics: {
        plannedPerWeek,
        avgWeeklyCompleted,
        avgSessionMinutes,
        averageCompletionRate,
        groupSets,
        weightTrend,
        dayStats,
      },
      findings,
      focusGroups: [shiftTo],
      suggestedVolumeAdjustments: [
        {
          group: overEmphasized[0],
          direction: 'decrease',
          reason: 'Current emphasis is already above the target band.',
        },
        {
          group: shiftTo,
          direction: 'increase',
          reason: 'Higher readiness and lower recent volume make this a better focus.',
        },
      ],
    });
  }

  const fallbackGroup = bestReady?.group || 'back';
  const fallbackIntensity = (bestReady?.value || 0) > 0.82 ? 'Heavy' : 'Moderate';
  return buildBaseReport({
    mode: 'train_next',
    headline: `Train ${formatGroupLabel(fallbackGroup)} next`,
    intensity: fallbackIntensity,
    reason: 'Best readiness and balanced recent volume support this focus.',
    scores,
    metrics: {
      plannedPerWeek,
      avgWeeklyCompleted,
      avgSessionMinutes,
      averageCompletionRate,
      groupSets,
      weightTrend,
      dayStats,
    },
    findings,
    focusGroups: [fallbackGroup],
  });
}
