
import AsyncStorage from '@react-native-async-storage/async-storage';
import { EXERCISES as BUNDLED_EXERCISES } from '../data/exerciseLibrary';
import { EXERCISE_ID_MAP } from '../data/exerciseMapping';
import { resolveExerciseYoutubeMeta } from '../utils/exerciseVideoLinks';

const LIBRARY_KEY = '@ironlog/exerciseLibrary';
const INDEX_KEY = '@ironlog/exerciseIndex';
const FETCH_URL = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json';

function toArray(value) {
  if (Array.isArray(value)) return value.filter(Boolean);
  if (value === null || value === undefined || value === '') return [];
  return [value];
}

function normalizeCategory(value) {
  const raw = String(value || '').trim().toLowerCase();
  if (!raw) return 'strength';
  if (raw.includes('weight') || raw.includes('rep')) return 'strength';
  if (raw.includes('distance') || raw.includes('duration') || raw.includes('time')) return 'cardio';
  if (raw.includes('cardio') || raw.includes('conditioning')) return 'cardio';
  if (raw.includes('mobility') || raw.includes('stretch')) return 'mobility';
  if (raw.includes('bodyweight') || raw.includes('calisthenics')) return 'bodyweight';
  if (raw.includes('olympic')) return 'olympic';
  return raw;
}

function normalizePrimaryMuscles(ex) {
  const raw = [
    ...toArray(ex.primaryMuscles),
    ...toArray(ex.primaryMuscle),
    ...toArray(ex.muscle),
    ...toArray(ex.target),
    ...toArray(ex.targetMuscle),
    ...toArray(ex.bodyPart),
    ...toArray(ex.muscleGroup),
  ];
  const seen = new Set();
  const cleaned = [];
  raw.forEach((muscle) => {
    const normalized = String(muscle || '').trim();
    if (!normalized) return;
    const key = normalized.toLowerCase();
    if (seen.has(key)) return;
    seen.add(key);
    cleaned.push(normalized);
  });
  return cleaned;
}

// Map free-exercise-db equipment strings to our normalized values
function normalizeEquipment(eq) {
  const map = {
    'barbell': 'Barbell', 'dumbbell': 'Dumbbell', 'cable': 'Cable',
    'machine': 'Machine', 'body only': 'Bodyweight', 'bodyweight': 'Bodyweight',
    'kettlebells': 'Kettlebell', 'bands': 'Band', 'other': 'Other',
    'medicine ball': 'Other', 'exercise ball': 'Other', 'e-z curl bar': 'Barbell',
    'foam roll': 'Other',
  };
  return map[(eq || '').toLowerCase()] || 'Other';
}

function buildFromBundled() {
  // Bundled exercises are already in full DB format (auto-generated from free-exercise-db)
  return BUNDLED_EXERCISES.map(ex => {
    const primaryMuscles = normalizePrimaryMuscles(ex);
    const youtube = resolveExerciseYoutubeMeta(ex);
    return {
      id: ex.id || ex.name.replace(/[^a-zA-Z0-9]/g, '_'),
      name: ex.name,
      force: ex.force || null,
      level: ex.level || 'intermediate',
      mechanic: ex.mechanic || null,
      equipment: ex.equipment || ex.mechanic || null,
      primaryMuscles,
      primaryMuscle: primaryMuscles[0] || null,
      secondaryMuscles: toArray(ex.secondaryMuscles),
      instructions: ex.instructions || (ex.cue ? [ex.cue] : []),
      category: normalizeCategory(ex.category || ex.trackingType),
      trackingType: ex.trackingType || null,
      images: ex.images || [],
      isCustom: false,
      coachingCues: ex.cue ? [ex.cue] : null,
      youtubeLink: youtube.youtubeLink,
      youtubeShortsLink: youtube.youtubeShortsLink,
      youtubeSearchQuery: youtube.youtubeSearchQuery,
      hasBundledYoutubeLink: youtube.hasBundledYoutubeLink,
    };
  });
}

function mergeWithDB(bundled, dbExercises) {
  // Build lookup by db id
  const dbById = {};
  for (const ex of dbExercises) dbById[ex.id] = ex;

  // Build reverse map: dbId → bundled exercise
  const idMapReverse = {};
  for (const [existingName, dbId] of Object.entries(EXERCISE_ID_MAP)) {
    if (dbId) idMapReverse[dbId] = existingName;
  }

  const usedDbIds = new Set();
  const merged = bundled.map(ex => {
    // find db id for this bundled exercise by name lookup
    const dbId = EXERCISE_ID_MAP[ex.name];
    if (dbId && dbById[dbId]) {
      usedDbIds.add(dbId);
      const db = dbById[dbId];
      return {
        ...ex,
        force: db.force || null,
        level: db.level || 'intermediate',
        mechanic: db.mechanic || null,
        equipment: normalizeEquipment(db.equipment) || ex.equipment,
        primaryMuscles: db.primaryMuscles || ex.primaryMuscles,
        secondaryMuscles: db.secondaryMuscles || [],
        instructions: db.instructions || ex.instructions,
        category: db.category || ex.category,
        images: db.images || [],
      };
    }
    return ex;
  });

  // Add remaining db exercises not already merged
  for (const db of dbExercises) {
    if (!usedDbIds.has(db.id)) {
      merged.push({
        id: db.id,
        name: db.name,
        force: db.force || null,
        level: db.level || 'beginner',
        mechanic: db.mechanic || null,
        equipment: normalizeEquipment(db.equipment),
        primaryMuscles: db.primaryMuscles || [],
        secondaryMuscles: db.secondaryMuscles || [],
        instructions: db.instructions || [],
        category: db.category || 'strength',
        images: db.images || [],
        isCustom: false,
        coachingCues: null,
        ...resolveExerciseYoutubeMeta(db),
      });
    }
  }
  return merged;
}

function buildIndex(exercises) {
  return exercises.map(ex => {
    const primaryMuscles = normalizePrimaryMuscles(ex);
    const youtube = resolveExerciseYoutubeMeta(ex);
    return {
      id: ex.id,
      name: ex.name,
      primaryMuscles,
      primaryMuscle: primaryMuscles[0] || null,
      secondaryMuscles: toArray(ex.secondaryMuscles),
      equipment: ex.equipment,
      category: normalizeCategory(ex.category || ex.trackingType),
      trackingType: ex.trackingType || null,
      bodyPart: ex.bodyPart || null,
      target: ex.target || null,
      level: ex.level,
      force: ex.force,
      mechanic: ex.mechanic,
      isCustom: ex.isCustom || false,
      youtubeLink: ex.youtubeLink || youtube.youtubeLink,
      youtubeShortsLink: ex.youtubeShortsLink || youtube.youtubeShortsLink,
      youtubeSearchQuery: ex.youtubeSearchQuery || youtube.youtubeSearchQuery,
      hasBundledYoutubeLink: ex.hasBundledYoutubeLink === true || youtube.hasBundledYoutubeLink === true,
    };
  });
}

function shouldRebuildIndex(index) {
  if (!Array.isArray(index) || index.length === 0) return true;
  let withMuscles = 0;
  let withNonStrengthCategory = 0;
  let withYoutubeFields = 0;

  index.forEach((entry) => {
    const muscles = normalizePrimaryMuscles(entry);
    if (muscles.length > 0) withMuscles += 1;
    const category = normalizeCategory(entry.category || entry.trackingType);
    if (category !== 'strength') withNonStrengthCategory += 1;
    if (entry.youtubeLink || entry.youtubeShortsLink || entry.youtubeSearchQuery) withYoutubeFields += 1;
  });

  const muscleCoverage = withMuscles / index.length;
  const variedCategories = withNonStrengthCategory / index.length;
  const youtubeCoverage = withYoutubeFields / index.length;

  // Rebuild stale index generated from old schema where muscles were lost.
  return muscleCoverage < 0.5 || variedCategories < 0.02 || youtubeCoverage < 0.95;
}

async function rebuildLibraryAndIndexFromBundled() {
  const bundled = buildFromBundled();
  const customRaw = await AsyncStorage.getItem('@ironlog/customExercises');
  const custom = customRaw ? JSON.parse(customRaw) : [];
  const full = [...bundled, ...custom];
  const index = buildIndex(full);
  await AsyncStorage.multiSet([
    [LIBRARY_KEY, JSON.stringify(full)],
    [INDEX_KEY, JSON.stringify(index)],
  ]);
  return index;
}

export async function initExerciseLibrary(onStatus) {
  // Already bootstrapped — instant return
  const existing = await AsyncStorage.getItem(INDEX_KEY);
  if (existing) return JSON.parse(existing);

  onStatus && onStatus('setting_up');
  const bundled = buildFromBundled();
  const index = buildIndex(bundled);

  // Save bundled exercises immediately — never block on network
  await AsyncStorage.multiSet([
    [LIBRARY_KEY, JSON.stringify(bundled)],
    [INDEX_KEY, JSON.stringify(index)],
  ]);
  onStatus && onStatus('done');

  // Bundled library already contains the full free-exercise-db (873 exercises)
  // No network fetch needed

  return index;
}

async function _fetchAndMerge(bundled) {
  try {
    const controller = new AbortController();
    const tid = setTimeout(() => controller.abort(), 30000);
    const response = await fetch(FETCH_URL, { signal: controller.signal });
    clearTimeout(tid);
    if (!response.ok) return;
    const dbExercises = await response.json();
    const merged = mergeWithDB(bundled, dbExercises);
    const customRaw = await AsyncStorage.getItem('@ironlog/customExercises');
    const custom = customRaw ? JSON.parse(customRaw) : [];
    const full = [...merged, ...custom];
    const index = buildIndex(full);
    await AsyncStorage.multiSet([
      [LIBRARY_KEY, JSON.stringify(full)],
      [INDEX_KEY, JSON.stringify(index)],
    ]);
  } catch (_) {}
}

export async function getExerciseIndex() {
  const raw = await AsyncStorage.getItem(INDEX_KEY);
  if (!raw) return null;
  const parsed = JSON.parse(raw);
  if (shouldRebuildIndex(parsed)) {
    return rebuildLibraryAndIndexFromBundled();
  }
  return parsed;
}

export async function getExerciseById(id) {
  const raw = await AsyncStorage.getItem(LIBRARY_KEY);
  if (!raw) return null;
  const lib = JSON.parse(raw);
  return lib.find(ex => ex.id === id) || null;
}

export async function getExerciseByName(name) {
  const raw = await AsyncStorage.getItem(LIBRARY_KEY);
  if (!raw) return null;
  const lib = JSON.parse(raw);
  return lib.find(ex => ex.name === name) || null;
}

export async function saveCustomExercise(exercise) {
  const raw = await AsyncStorage.getItem('@ironlog/customExercises');
  const custom = raw ? JSON.parse(raw) : [];
  const idx = custom.findIndex(e => e.id === exercise.id);
  if (idx >= 0) custom[idx] = exercise;
  else custom.push(exercise);

  // Also update main library and index
  const libRaw = await AsyncStorage.getItem(LIBRARY_KEY);
  const lib = libRaw ? JSON.parse(libRaw) : [];
  const libIdx = lib.findIndex(e => e.id === exercise.id);
  if (libIdx >= 0) lib[libIdx] = exercise;
  else lib.push(exercise);

  const index = buildIndex(lib);
  await AsyncStorage.multiSet([
    ['@ironlog/customExercises', JSON.stringify(custom)],
    [LIBRARY_KEY, JSON.stringify(lib)],
    [INDEX_KEY, JSON.stringify(index)],
  ]);
  return index;
}

export async function deleteCustomExercise(id) {
  const raw = await AsyncStorage.getItem('@ironlog/customExercises');
  const custom = raw ? JSON.parse(raw) : [];
  const updated = custom.filter(e => e.id !== id);

  const libRaw = await AsyncStorage.getItem(LIBRARY_KEY);
  const lib = libRaw ? JSON.parse(libRaw) : [];
  const updatedLib = lib.filter(e => e.id !== id);
  const index = buildIndex(updatedLib);

  await AsyncStorage.multiSet([
    ['@ironlog/customExercises', JSON.stringify(updated)],
    [LIBRARY_KEY, JSON.stringify(updatedLib)],
    [INDEX_KEY, JSON.stringify(index)],
  ]);
  return index;
}

export async function retryLibraryFetch() {
  // Clear index to force re-init on next call
  await AsyncStorage.removeItem(INDEX_KEY);
  await AsyncStorage.removeItem(LIBRARY_KEY);
}
