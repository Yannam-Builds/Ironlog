const fs = require('fs');
const path = require('path');
function w(p, c) {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, c);
  console.log('W:', p);
}

// ─── MIGRATION SYSTEM ────────────────────────────────────────────────────────
w('src/services/migrations.js', `
import AsyncStorage from '@react-native-async-storage/async-storage';

const SCHEMA_VERSION_KEY = '@ironlog/schemaVersion';
const MIGRATION_ERRORS_KEY = '@ironlog/migrationErrors';
const CURRENT_VERSION = 8;

// old key helpers
const OLD_HISTORY_KEY = 'ironlog_history';
const OLD_BW_KEY = 'ironlog_bw';

async function logError(migration, err) {
  try {
    const existing = await AsyncStorage.getItem(MIGRATION_ERRORS_KEY);
    const errors = existing ? JSON.parse(existing) : [];
    errors.push({ migration, error: err.message, date: new Date().toISOString() });
    await AsyncStorage.setItem(MIGRATION_ERRORS_KEY, JSON.stringify(errors));
  } catch (_) {}
}

// v1→v2: convert warmup boolean on sets to set.type
async function migrate1to2() {
  const raw = await AsyncStorage.getItem(OLD_HISTORY_KEY);
  if (!raw) return;
  const history = JSON.parse(raw);
  const updated = history.map(session => ({
    ...session,
    exercises: (session.exercises || []).map(ex => ({
      ...ex,
      sets: (ex.sets || []).map(s => ({
        ...s,
        type: s.isWarmup ? 'warmup' : (s.type || 'normal'),
        isWarmup: undefined,
      })),
    })),
  }));
  await AsyncStorage.setItem(OLD_HISTORY_KEY, JSON.stringify(updated));
}

// v2→v3: add supersetGroup: null to all workout exercises
async function migrate2to3() {
  const raw = await AsyncStorage.getItem(OLD_HISTORY_KEY);
  if (!raw) return;
  const history = JSON.parse(raw);
  const updated = history.map(session => ({
    ...session,
    exercises: (session.exercises || []).map(ex => ({
      supersetGroup: null,
      ...ex,
    })),
  }));
  await AsyncStorage.setItem(OLD_HISTORY_KEY, JSON.stringify(updated));
}

// v3→v4: migrate body weight entries to BodyMeasurements format
async function migrate3to4() {
  const raw = await AsyncStorage.getItem(OLD_BW_KEY);
  if (!raw) return;
  const bwEntries = JSON.parse(raw);
  const measurements = bwEntries.map((e, i) => ({
    id: \`bw_migrated_\${i}\`,
    date: e.date || new Date().toISOString(),
    weight: e.weight || null,
    bodyFat: null, chest: null, waist: null, hips: null,
    leftArm: null, rightArm: null, leftThigh: null, rightThigh: null,
    leftCalf: null, rightCalf: null, shoulders: null, neck: null,
    notes: '',
  }));
  await AsyncStorage.setItem('@ironlog/bodyMeasurements', JSON.stringify(measurements));
  // keep old key for backward compat with existing BW chart
}

// v4→v5: add previousSets, targetWeight, targetReps to all sets
async function migrate4to5() {
  const raw = await AsyncStorage.getItem(OLD_HISTORY_KEY);
  if (!raw) return;
  const history = JSON.parse(raw);
  const updated = history.map(session => ({
    ...session,
    exercises: (session.exercises || []).map(ex => ({
      ...ex,
      previousSets: ex.previousSets !== undefined ? ex.previousSets : null,
      sets: (ex.sets || []).map(s => ({
        id: s.id || \`s_\${Math.random().toString(36).slice(2)}\`,
        rpe: s.rpe !== undefined ? s.rpe : null,
        rir: s.rir !== undefined ? s.rir : null,
        completed: s.completed !== undefined ? s.completed : true,
        targetWeight: s.targetWeight !== undefined ? s.targetWeight : null,
        targetReps: s.targetReps !== undefined ? s.targetReps : null,
        ...s,
      })),
    })),
  }));
  await AsyncStorage.setItem(OLD_HISTORY_KEY, JSON.stringify(updated));
}

// v5→v6: add isDeload to all sessions
async function migrate5to6() {
  const raw = await AsyncStorage.getItem(OLD_HISTORY_KEY);
  if (!raw) return;
  const history = JSON.parse(raw);
  const updated = history.map(s => ({ isDeload: false, rating: null, ...s }));
  await AsyncStorage.setItem(OLD_HISTORY_KEY, JSON.stringify(updated));
}

// v6→v7: create empty index structures
async function migrate6to7() {
  const pairs = [
    ['@ironlog/pr_index', '{}'],
    ['@ironlog/volume_index', '{}'],
    ['@ironlog/lastPerformance', '{}'],
  ];
  // only set if not already present
  const existing = await AsyncStorage.multiGet(pairs.map(p => p[0]));
  const toSet = pairs.filter((_, i) => existing[i][1] === null);
  if (toSet.length) await AsyncStorage.multiSet(toSet);
}

// v7→v8: rebuild all indexes from full history
// runs AFTER all schema migrations so set.type and isDeload are already correct
async function migrate7to8() {
  const raw = await AsyncStorage.getItem(OLD_HISTORY_KEY);
  if (!raw) return;
  const history = JSON.parse(raw);
  const prIndex = {};
  const volumeIndex = {};
  const lastPerf = {};

  for (const session of history) {
    if (!session.exercises || !session.date) continue;
    const dateStr = session.date.split('T')[0];
    // ISO week key
    const d = new Date(session.date);
    const jan4 = new Date(d.getFullYear(), 0, 4);
    const week = Math.ceil(((d - jan4) / 86400000 + jan4.getDay() + 1) / 7);
    const weekKey = \`\${d.getFullYear()}-W\${String(week).padStart(2, '0')}\`;

    for (const ex of session.exercises) {
      if (!ex.name || !ex.sets) continue;
      const exId = ex.exerciseId || ex.name;
      const workingSets = ex.sets.filter(s => s.type !== 'warmup');

      // lastPerformance — overwrite with most recent (history is newest-first)
      if (!lastPerf[exId]) {
        lastPerf[exId] = {
          date: dateStr,
          workoutId: session.id,
          sets: workingSets.map(s => ({ weight: s.weight, reps: s.reps, type: s.type || 'normal', rpe: s.rpe || null })),
        };
      }

      // pr_index — only normal/failure/amrap sets
      const prSets = ex.sets.filter(s => ['normal', 'failure', 'amrap'].includes(s.type || 'normal'));
      if (prSets.length && !session.isDeload) {
        if (!prIndex[exId]) prIndex[exId] = [];
        for (const s of prSets) {
          if (!s.weight || !s.reps) continue;
          const e1rm = Math.round(s.weight * (1 + s.reps / 30) * 10) / 10;
          prIndex[exId].push({ date: dateStr, weight: s.weight, reps: s.reps, e1rm });
        }
      }

      // volume_index — working sets to primary muscle, skip deload
      if (!session.isDeload && workingSets.length) {
        // primaryMuscle comes from exerciseId lookup — for migrated data use muscle field
        const muscle = (ex.primaryMuscles && ex.primaryMuscles[0]) || ex.primary || ex.muscle || 'other';
        const normalized = muscle.toLowerCase().replace(/\\s+/g, '_');
        if (!volumeIndex[weekKey]) volumeIndex[weekKey] = {};
        volumeIndex[weekKey][normalized] = (volumeIndex[weekKey][normalized] || 0) + workingSets.length;
      }
    }
  }

  // Sort pr_index entries by date ascending
  for (const id of Object.keys(prIndex)) {
    prIndex[id].sort((a, b) => a.date.localeCompare(b.date));
  }

  await AsyncStorage.multiSet([
    ['@ironlog/pr_index', JSON.stringify(prIndex)],
    ['@ironlog/volume_index', JSON.stringify(volumeIndex)],
    ['@ironlog/lastPerformance', JSON.stringify(lastPerf)],
  ]);
}

const MIGRATIONS = [
  null,          // placeholder so index = version number
  migrate1to2,
  migrate2to3,
  migrate3to4,
  migrate4to5,
  migrate5to6,
  migrate6to7,
  migrate7to8,
];

export async function runMigrations(onProgress) {
  const raw = await AsyncStorage.getItem(SCHEMA_VERSION_KEY);
  let currentVersion = raw ? parseInt(raw, 10) : 0;
  if (currentVersion >= CURRENT_VERSION) return false; // nothing to do

  for (let v = currentVersion + 1; v <= CURRENT_VERSION; v++) {
    onProgress && onProgress(v, CURRENT_VERSION);
    try {
      await MIGRATIONS[v]();
      await AsyncStorage.setItem(SCHEMA_VERSION_KEY, String(v));
    } catch (e) {
      await logError(\`v\${v - 1}->v\${v}\`, e);
      // skip and continue — never crash
    }
  }
  return true; // migrations ran
}

export async function getSchemaVersion() {
  const raw = await AsyncStorage.getItem(SCHEMA_VERSION_KEY);
  return raw ? parseInt(raw, 10) : 0;
}
`);

// ─── EXERCISE LIBRARY SERVICE ─────────────────────────────────────────────────
w('src/services/ExerciseLibraryService.js', `
import AsyncStorage from '@react-native-async-storage/async-storage';
import { EXERCISES as BUNDLED_EXERCISES } from '../data/exerciseLibrary';
import { EXERCISE_ID_MAP } from '../data/exerciseMapping';

const LIBRARY_KEY = '@ironlog/exerciseLibrary';
const INDEX_KEY = '@ironlog/exerciseIndex';
const FETCH_URL = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json';

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
  return BUNDLED_EXERCISES.map((ex, i) => ({
    id: ex.name.replace(/[^a-zA-Z0-9]/g, '_'),
    name: ex.name,
    force: null,
    level: 'intermediate',
    mechanic: null,
    equipment: ex.equipment || null,
    primaryMuscles: [ex.muscle || 'other'],
    secondaryMuscles: [],
    instructions: ex.cue ? [ex.cue] : [],
    category: ex.category ? ex.category.toLowerCase() : 'strength',
    images: [],
    isCustom: false,
    coachingCues: ex.cue ? [ex.cue] : null,
  }));
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
      });
    }
  }
  return merged;
}

function buildIndex(exercises) {
  return exercises.map(ex => ({
    id: ex.id,
    name: ex.name,
    primaryMuscles: ex.primaryMuscles,
    equipment: ex.equipment,
    category: ex.category,
    level: ex.level,
    force: ex.force,
    mechanic: ex.mechanic,
    isCustom: ex.isCustom || false,
  }));
}

export async function initExerciseLibrary(onStatus) {
  // Check if already bootstrapped
  const existing = await AsyncStorage.getItem(INDEX_KEY);
  if (existing) return JSON.parse(existing);

  onStatus && onStatus('setting_up');
  const bundled = buildFromBundled();

  try {
    const response = await fetch(FETCH_URL);
    if (!response.ok) throw new Error('fetch failed');
    const dbExercises = await response.json();
    const merged = mergeWithDB(bundled, dbExercises);

    // Get custom exercises and append
    const customRaw = await AsyncStorage.getItem('@ironlog/customExercises');
    const custom = customRaw ? JSON.parse(customRaw) : [];
    const full = [...merged, ...custom];

    const index = buildIndex(full);
    await AsyncStorage.multiSet([
      [LIBRARY_KEY, JSON.stringify(full)],
      [INDEX_KEY, JSON.stringify(index)],
    ]);
    onStatus && onStatus('done');
    return index;
  } catch (e) {
    // Offline or fetch failed — use bundled only
    const index = buildIndex(bundled);
    await AsyncStorage.multiSet([
      [LIBRARY_KEY, JSON.stringify(bundled)],
      [INDEX_KEY, JSON.stringify(index)],
    ]);
    onStatus && onStatus('offline');
    return index;
  }
}

export async function getExerciseIndex() {
  const raw = await AsyncStorage.getItem(INDEX_KEY);
  return raw ? JSON.parse(raw) : null;
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
`);

// ─── IMAGE CACHE SERVICE ──────────────────────────────────────────────────────
w('src/services/ExerciseImageCache.js', `
import * as FileSystem from 'expo-file-system';

const CACHE_DIR = FileSystem.documentDirectory + 'exercise-images/';
const BASE_URL = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/';

// Flatten path: "Air_Bike/0.jpg" → "Air_Bike___0.jpg"
const flatten = path => path.replace(/\\//g, '___');
const localPath = imagePath => CACHE_DIR + flatten(imagePath);

// Deduplicate concurrent downloads
const inFlight = new Map();

async function ensureCacheDir() {
  const info = await FileSystem.getInfoAsync(CACHE_DIR);
  if (!info.exists) await FileSystem.makeDirectoryAsync(CACHE_DIR, { intermediates: true });
}

export async function getImageUri(imagePath) {
  if (!imagePath) return null;
  const local = localPath(imagePath);
  const info = await FileSystem.getInfoAsync(local);
  if (info.exists) return local;

  // Deduplicate concurrent downloads for same path
  if (inFlight.has(imagePath)) return inFlight.get(imagePath);

  const promise = (async () => {
    try {
      await ensureCacheDir();
      const url = BASE_URL + imagePath;
      const result = await FileSystem.downloadAsync(url, local);
      if (result.status === 200) return local;
      return null;
    } catch (_) {
      return null;
    } finally {
      inFlight.delete(imagePath);
    }
  })();

  inFlight.set(imagePath, promise);
  return promise;
}

export async function downloadAllImages(allImagePaths, onProgress, concurrency = 5) {
  await ensureCacheDir();
  let done = 0;
  const total = allImagePaths.length;

  // Filter already-cached
  const toDownload = [];
  await Promise.all(allImagePaths.map(async p => {
    const info = await FileSystem.getInfoAsync(localPath(p));
    if (!info.exists) toDownload.push(p);
    else { done++; onProgress && onProgress(done, total); }
  }));

  // Batch with concurrency limit
  for (let i = 0; i < toDownload.length; i += concurrency) {
    const batch = toDownload.slice(i, i + concurrency);
    await Promise.allSettled(batch.map(async p => {
      await getImageUri(p);
      done++;
      onProgress && onProgress(done, total);
    }));
  }
}

export async function getCacheSize() {
  try {
    const info = await FileSystem.getInfoAsync(CACHE_DIR);
    if (!info.exists) return 0;
    const files = await FileSystem.readDirectoryAsync(CACHE_DIR);
    let total = 0;
    await Promise.all(files.map(async f => {
      const fi = await FileSystem.getInfoAsync(CACHE_DIR + f);
      if (fi.exists) total += fi.size || 0;
    }));
    return total;
  } catch (_) { return 0; }
}

export async function clearCache() {
  try {
    await FileSystem.deleteAsync(CACHE_DIR, { idempotent: true });
    await FileSystem.makeDirectoryAsync(CACHE_DIR, { intermediates: true });
  } catch (_) {}
}
`);

// ─── CACHED IMAGE COMPONENT ───────────────────────────────────────────────────
w('src/components/CachedExerciseImage.js', `
import React, { useEffect, useState } from 'react';
import { View, Image, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { getImageUri } from '../services/ExerciseImageCache';
import { useTheme } from '../context/ThemeContext';

export default function CachedExerciseImage({ imagePath, style, iconSize = 32 }) {
  const colors = useTheme();
  const [uri, setUri] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!imagePath) return;
    let cancelled = false;
    getImageUri(imagePath).then(u => {
      if (!cancelled) { if (u) setUri(u); else setFailed(true); }
    });
    return () => { cancelled = true; };
  }, [imagePath]);

  if (!imagePath || failed) return (
    <View style={[s.placeholder, { backgroundColor: colors.card }, style]}>
      <Ionicons name="barbell-outline" size={iconSize} color={colors.muted} />
    </View>
  );

  if (!uri) return (
    <View style={[s.placeholder, { backgroundColor: colors.card }, style]} />
  );

  return <Image source={{ uri }} style={[s.img, style]} resizeMode="cover" />;
}

const s = StyleSheet.create({
  placeholder: { alignItems: 'center', justifyContent: 'center' },
  img: {},
});
`);

// ─── CUSTOM EXERCISE CREATION SCREEN ─────────────────────────────────────────
w('src/screens/CreateExerciseScreen.js', `
import React, { useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  StyleSheet, Alert,
} from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { saveCustomExercise } from '../services/ExerciseLibraryService';

const MUSCLES = [
  'chest','back','shoulders','biceps','triceps','quadriceps','hamstrings',
  'glutes','calves','abdominals','obliques','lats','traps','forearms','other',
];
const EQUIPMENT_OPTS = ['Barbell','Dumbbell','Cable','Machine','Bodyweight','Kettlebell','Band','Other'];
const CATEGORY_OPTS = ['strength','stretching','plyometrics','cardio','olympic weightlifting','powerlifting','other'];
const FORCE_OPTS = ['push','pull','static'];

function genId(name) {
  return name.replace(/[^a-zA-Z0-9]/g, '_') + '_custom_' + Date.now().toString(36);
}

export default function CreateExerciseScreen({ navigation, route }) {
  const colors = useTheme();
  const existing = route.params?.exercise;
  const [name, setName] = useState(existing?.name || '');
  const [primaryMuscles, setPrimary] = useState(existing?.primaryMuscles || []);
  const [secondaryMuscles, setSecondary] = useState(existing?.secondaryMuscles || []);
  const [equipment, setEquipment] = useState(existing?.equipment || 'Barbell');
  const [category, setCategory] = useState(existing?.category || 'strength');
  const [force, setForce] = useState(existing?.force || 'push');
  const [instructions, setInstructions] = useState(
    existing?.instructions ? existing.instructions.join('\\n') : ''
  );

  const toggleMuscle = (list, setList, muscle) => {
    setList(list.includes(muscle) ? list.filter(m => m !== muscle) : [...list, muscle]);
  };

  const save = async () => {
    if (!name.trim()) { Alert.alert('Name required'); return; }
    if (!primaryMuscles.length) { Alert.alert('Select at least one primary muscle'); return; }
    const ex = {
      id: existing?.id || genId(name.trim()),
      name: name.trim(),
      force,
      level: 'intermediate',
      mechanic: null,
      equipment,
      primaryMuscles,
      secondaryMuscles,
      instructions: instructions.trim() ? instructions.trim().split('\\n').filter(Boolean) : [],
      category,
      images: [],
      isCustom: true,
      coachingCues: null,
    };
    await saveCustomExercise(ex);
    navigation.goBack();
  };

  const s = makeStyles(colors);

  const OptionRow = ({ label, opts, value, onSelect }) => (
    <View style={s.field}>
      <Text style={s.fieldLabel}>{label}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingVertical: 4 }}>
        {opts.map(opt => {
          const active = value === opt;
          return (
            <TouchableOpacity key={opt} style={[s.pill, active && { backgroundColor: colors.accentSoft, borderColor: colors.accent }]} onPress={() => onSelect(opt)}>
              <Text style={[s.pillText, { color: active ? colors.accent : colors.muted }]}>{opt}</Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );

  const MuscleGrid = ({ label, selected, onToggle }) => (
    <View style={s.field}>
      <Text style={s.fieldLabel}>{label}</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {MUSCLES.map(m => {
          const active = selected.includes(m);
          return (
            <TouchableOpacity key={m} style={[s.pill, active && { backgroundColor: colors.accentSoft, borderColor: colors.accent }]} onPress={() => onToggle(m)}>
              <Text style={[s.pillText, { color: active ? colors.accent : colors.muted }]}>{m}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={{ padding: 20, paddingBottom: 60 }}>
      <View style={s.field}>
        <Text style={s.fieldLabel}>EXERCISE NAME *</Text>
        <TextInput style={[s.textInput, { color: colors.text, borderBottomColor: colors.accent }]}
          value={name} onChangeText={setName} placeholder="e.g. Cable Pull-Through"
          placeholderTextColor={colors.muted} autoFocus />
      </View>

      <MuscleGrid label="PRIMARY MUSCLES *" selected={primaryMuscles} onToggle={m => toggleMuscle(primaryMuscles, setPrimary, m)} />
      <MuscleGrid label="SECONDARY MUSCLES" selected={secondaryMuscles} onToggle={m => toggleMuscle(secondaryMuscles, setSecondary, m)} />
      <OptionRow label="EQUIPMENT" opts={EQUIPMENT_OPTS} value={equipment} onSelect={setEquipment} />
      <OptionRow label="CATEGORY" opts={CATEGORY_OPTS} value={category} onSelect={setCategory} />
      <OptionRow label="FORCE" opts={FORCE_OPTS} value={force} onSelect={setForce} />

      <View style={s.field}>
        <Text style={s.fieldLabel}>INSTRUCTIONS (one per line)</Text>
        <TextInput
          style={[s.textArea, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.card }]}
          value={instructions} onChangeText={setInstructions}
          placeholder="Step-by-step coaching cues..."
          placeholderTextColor={colors.muted}
          multiline numberOfLines={5} textAlignVertical="top" />
      </View>

      <TouchableOpacity style={[s.saveBtn, { backgroundColor: colors.accent }]} onPress={save}>
        <Text style={{ color: '#fff', fontWeight: '800', fontSize: 14, letterSpacing: 2 }}>
          {existing ? 'UPDATE EXERCISE' : 'CREATE EXERCISE'}
        </Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const makeStyles = colors => StyleSheet.create({
  field: { marginBottom: 20 },
  fieldLabel: { fontSize: 10, letterSpacing: 3, color: colors.muted, marginBottom: 8 },
  textInput: { fontSize: 22, fontWeight: '700', borderBottomWidth: 2, paddingVertical: 8 },
  textArea: { borderWidth: 1, padding: 12, fontSize: 13, minHeight: 100, borderRadius: 2 },
  pill: { paddingHorizontal: 12, paddingVertical: 7, borderWidth: 1, borderColor: colors.faint },
  pillText: { fontSize: 12, fontWeight: '600' },
  saveBtn: { padding: 18, alignItems: 'center', marginTop: 8 },
});
`);

// ─── MIGRATION LOADING SCREEN ─────────────────────────────────────────────────
w('src/screens/MigrationScreen.js', `
import React from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';

export default function MigrationScreen({ step, total, message }) {
  return (
    <View style={s.container}>
      <ActivityIndicator size="large" color="#FF4500" style={{ marginBottom: 24 }} />
      <Text style={s.title}>IRONLOG</Text>
      <Text style={s.msg}>{message || 'Updating your data...'}</Text>
      {step > 0 && (
        <Text style={s.step}>Step {step} of {total}</Text>
      )}
      <View style={s.barBg}>
        <View style={[s.barFill, { width: \`\${(step / total) * 100}%\` }]} />
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808', alignItems: 'center', justifyContent: 'center', padding: 40 },
  title: { fontSize: 32, fontWeight: '900', color: '#f0f0f0', letterSpacing: -1, marginBottom: 8 },
  msg: { fontSize: 13, color: '#666', textAlign: 'center', marginBottom: 8, letterSpacing: 1 },
  step: { fontSize: 11, color: '#444', marginBottom: 20 },
  barBg: { width: '100%', height: 3, backgroundColor: '#111', marginTop: 8 },
  barFill: { height: 3, backgroundColor: '#FF4500' },
});
`);

// ─── LIBRARY SETUP SCREEN ─────────────────────────────────────────────────────
w('src/screens/LibrarySetupScreen.js', `
import React from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';

export default function LibrarySetupScreen({ status }) {
  const msg = status === 'offline'
    ? 'Offline — using bundled exercises.\\nFull library will download on next launch.'
    : 'Setting up exercise library...';
  return (
    <View style={s.container}>
      <ActivityIndicator size="large" color="#FF4500" style={{ marginBottom: 24 }} />
      <Text style={s.title}>IRONLOG</Text>
      <Text style={s.msg}>{msg}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808', alignItems: 'center', justifyContent: 'center', padding: 40 },
  title: { fontSize: 32, fontWeight: '900', color: '#f0f0f0', letterSpacing: -1, marginBottom: 8 },
  msg: { fontSize: 13, color: '#666', textAlign: 'center', letterSpacing: 0.5 },
});
`);

console.log('Phase 1 files done');
