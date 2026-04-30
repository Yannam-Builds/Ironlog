import React, { useState, useCallback, useEffect, useMemo } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet,
  TextInput, Modal, Dimensions,
} from 'react-native';
import { MagicScroll } from '@appandflow/react-native-magic-scroll';
import CustomAlert from '../components/CustomAlert';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import useWatermelonAppData from '../hooks/useWatermelonAppData';
import ExerciseCard from '../components/ExerciseCard';
import { generateId } from '../utils/calculations';
import { EXERCISES } from '../data/exerciseLibrary';
import { getExerciseIndex, saveCustomExercise } from '../services/ExerciseLibraryService';
import { buildFilterChipOptions, matchesExerciseFilter } from '../utils/exerciseFilters';
import useKeyboardInset from '../hooks/useKeyboardInset';
import { buildExerciseSearchIndex, queryExerciseSearch } from '../services/exerciseSearchAdapter';
import { getFavoriteExerciseIds, setFavoriteExerciseIds } from '../services/favoriteExercisesService';

const SCREEN_H = Dimensions.get('window').height;

const C = {
  BG: '#080808',
  SURFACE: '#0f0f0f',
  CARD: '#141414',
  BORDER: '#1e1e1e',
  TEXT: '#f0f0f0',
  SECONDARY: '#666',
  MUTED: '#333',
  PUSH: '#FF4500',
  DANGER: '#CC2222',
};

function normalizeNameKey(value) {
  return String(value || '').trim().toLowerCase();
}

function inferTrackingType(exercise = {}) {
  const tracking = String(exercise?.trackingType || '').trim().toLowerCase();
  if (tracking) return tracking;
  const category = String(exercise?.category || '').toLowerCase();
  const name = String(exercise?.name || '').toLowerCase();
  if (/(cardio|conditioning|bike|treadmill|erg|run|row)/.test(`${category} ${name}`)) {
    return 'duration_distance';
  }
  if (/(plank|hold|isometric|mobility|stretch)/.test(`${category} ${name}`)) {
    return 'duration';
  }
  return 'weight_reps';
}

function EditExerciseModal({ visible, exercise, onSave, onClose }) {
  const [name, setName] = useState(exercise?.name || '');
  const [sets, setSets] = useState(String(exercise?.sets || 3));
  const [reps, setReps] = useState(exercise?.reps || '10');
  const [rest, setRest] = useState(String(exercise?.defaultRestSeconds || 120));
  const [notes, setNotes] = useState(exercise?.notes || '');
  const [isHeavy, setIsHeavy] = useState(exercise?.isHeavy || false);
  const [isWarmup, setIsWarmup] = useState(exercise?.isWarmup || false);

  useEffect(() => {
    if (!exercise) return;
    setName(exercise.name || '');
    setSets(String(exercise.sets || 3));
    setReps(exercise.reps || '10');
    setRest(String(exercise.defaultRestSeconds || 120));
    setNotes(exercise.notes || '');
    setIsHeavy(exercise.isHeavy || false);
    setIsWarmup(exercise.isWarmup || false);
  }, [exercise]);

  function handleSave() {
    if (!name.trim()) return;
    onSave({
      ...exercise,
      name: name.trim(),
      sets: parseInt(sets, 10) || 3,
      reps: reps.trim() || '10',
      defaultRestSeconds: parseInt(rest, 10) || 120,
      notes: notes.trim(),
      isHeavy,
      isWarmup,
    });
  }

  return (
    <Modal visible={visible} transparent animationType="slide">
      <View style={modalStyles.overlay}>
        <View style={modalStyles.card}>
          <Text style={modalStyles.title}>EDIT EXERCISE</Text>
          <ScrollView showsVerticalScrollIndicator={false}>
            <Text style={modalStyles.label}>Name</Text>
            <TextInput style={modalStyles.input} value={name} onChangeText={setName} placeholderTextColor={C.SECONDARY} />
            <View style={modalStyles.row}>
              <View style={modalStyles.half}>
                <Text style={modalStyles.label}>Sets</Text>
                <TextInput style={modalStyles.input} value={sets} onChangeText={setSets} keyboardType="numeric" placeholderTextColor={C.SECONDARY} />
              </View>
              <View style={modalStyles.half}>
                <Text style={modalStyles.label}>Reps / Time</Text>
                <TextInput style={modalStyles.input} value={reps} onChangeText={setReps} placeholderTextColor={C.SECONDARY} placeholder="e.g. 8-12 or 60" />
              </View>
            </View>
            <Text style={modalStyles.label}>Rest (seconds)</Text>
            <TextInput style={modalStyles.input} value={rest} onChangeText={setRest} keyboardType="numeric" placeholderTextColor={C.SECONDARY} />
            <Text style={modalStyles.label}>Notes</Text>
            <TextInput
              style={[modalStyles.input, { height: 80 }]}
              value={notes}
              onChangeText={setNotes}
              placeholderTextColor={C.SECONDARY}
              multiline
              placeholder="Cues, technique notes..."
            />
            <View style={modalStyles.togglesRow}>
              <TouchableOpacity style={[modalStyles.toggle, isHeavy && modalStyles.toggleActive]} onPress={() => setIsHeavy(!isHeavy)}>
                <Text style={[modalStyles.toggleText, isHeavy && { color: C.PUSH }]}>HEAVY</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[modalStyles.toggle, isWarmup && modalStyles.toggleWarmupActive]} onPress={() => setIsWarmup(!isWarmup)}>
                <Text style={[modalStyles.toggleText, isWarmup && { color: '#888' }]}>WARM-UP</Text>
              </TouchableOpacity>
            </View>
          </ScrollView>
          <View style={modalStyles.buttons}>
            <TouchableOpacity style={modalStyles.cancel} onPress={onClose}>
              <Text style={modalStyles.cancelText}>Cancel</Text>
            </TouchableOpacity>
            <TouchableOpacity style={modalStyles.save} onPress={handleSave}>
              <Text style={modalStyles.saveText}>SAVE</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

function AddFromLibraryModal({ visible, onAdd, onClose, onCreateExercise, onQuickAdd }) {
  const insets = useSafeAreaInsets();
  const keyboardInset = useKeyboardInset(insets.bottom);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [filterMuscle, setFilterMuscle] = useState(null);
  const [scope, setScope] = useState('all');
  const [library, setLibrary] = useState([]);
  const [chips, setChips] = useState([]);
  const [favoriteIds, setFavoriteIdsState] = useState([]);

  useEffect(() => {
    if (!visible) return;
    setFilterMuscle(null);
    setSearch('');
    setDebouncedSearch('');
    setScope('all');
    getExerciseIndex()
      .then((index) => {
        const source = Array.isArray(index) && index.length ? index : EXERCISES;
        setLibrary(source);
        setChips(buildFilterChipOptions(source, { includeCategory: true, includeEquipment: false }));
      })
      .catch(() => {
        setLibrary(EXERCISES);
        setChips(buildFilterChipOptions(EXERCISES, { includeCategory: true, includeEquipment: false }));
      });
    getFavoriteExerciseIds().then((ids) => setFavoriteIdsState(Array.isArray(ids) ? ids : []));
  }, [visible]);

  useEffect(() => {
    if (!visible) return;
    const timer = setTimeout(() => setDebouncedSearch(search), 170);
    return () => clearTimeout(timer);
  }, [search, visible]);

  const source = library.length ? library : EXERCISES;
  const favoriteSet = useMemo(() => new Set(favoriteIds), [favoriteIds]);
  const searchIndex = useMemo(() => buildExerciseSearchIndex(source), [source]);
  const searchResultIds = useMemo(() => queryExerciseSearch({
    query: debouncedSearch,
    index: searchIndex,
    exercises: source,
  }), [debouncedSearch, searchIndex, source]);
  const rankMap = searchResultIds?.rankMap || null;

  const filtered = useMemo(() => source
    .filter((exercise) => {
      const muscleMatch = !filterMuscle || matchesExerciseFilter(exercise, filterMuscle, {
        includeCategory: true,
        includeEquipment: false,
      });
      const scopeMatch = scope === 'favorites' ? favoriteSet.has(exercise.id) : true;
      const searchMatch = !searchResultIds || searchResultIds.has(exercise.id);
      return muscleMatch && scopeMatch && searchMatch;
    })
    .sort((a, b) => {
      if (rankMap) {
        const rankA = rankMap.has(a.id) ? rankMap.get(a.id) : Number.MAX_SAFE_INTEGER;
        const rankB = rankMap.has(b.id) ? rankMap.get(b.id) : Number.MAX_SAFE_INTEGER;
        if (rankA !== rankB) return rankA - rankB;
      }
      const favDiff = Number(favoriteSet.has(b.id)) - Number(favoriteSet.has(a.id));
      if (favDiff !== 0) return favDiff;
      return String(a.name || '').localeCompare(String(b.name || ''));
    }),
  [source, filterMuscle, scope, favoriteSet, searchResultIds, rankMap]);

  const searchSeed = String(search || '').trim();
  const hasExact = useMemo(
    () => source.some((exercise) => normalizeNameKey(exercise?.name) === normalizeNameKey(searchSeed)),
    [source, searchSeed]
  );

  const toggleFavorite = async (exerciseId) => {
    const id = String(exerciseId || '').trim();
    if (!id) return;
    const has = favoriteSet.has(id);
    const next = has
      ? favoriteIds.filter((value) => value !== id)
      : [...favoriteIds, id];
    setFavoriteIdsState(next);
    await setFavoriteExerciseIds(next);
  };

  return (
    <Modal visible={visible} transparent animationType="slide">
      <View style={libStyles.overlay}>
        <View style={libStyles.card}>
          <View style={libStyles.header}>
            <Text style={libStyles.title}>EXERCISE LIBRARY</Text>
            <TouchableOpacity onPress={onClose}>
              <Text style={libStyles.closeBtn}>X</Text>
            </TouchableOpacity>
          </View>

          <MagicScroll.TextInput
            name="plan_day_editor_library_search"
            textInputProps={{
              style: [libStyles.search, keyboardInset > 0 ? { marginBottom: 8 } : null],
              value: search,
              onChangeText: setSearch,
              placeholder: 'Search exercises...',
              placeholderTextColor: C.SECONDARY,
              autoCorrect: false,
              returnKeyType: 'search',
            }}
          />

          <View style={{ flexDirection: 'row', gap: 8, marginBottom: 8 }}>
            <TouchableOpacity
              style={[libStyles.filterChip, scope === 'all' && libStyles.filterChipActive]}
              onPress={() => setScope('all')}
            >
              <Text style={[libStyles.filterText, scope === 'all' && { color: C.PUSH }]}>All</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[libStyles.filterChip, scope === 'favorites' && libStyles.filterChipActive]}
              onPress={() => setScope((prev) => (prev === 'favorites' ? 'all' : 'favorites'))}
            >
              <Text style={[libStyles.filterText, scope === 'favorites' && { color: C.PUSH }]}>? Favorites</Text>
            </TouchableOpacity>
          </View>

          <View style={{ height: 44, marginBottom: 8 }}>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              keyboardShouldPersistTaps="handled"
              contentContainerStyle={{ alignItems: 'center', paddingHorizontal: 8 }}
            >
              {(chips.length ? chips : ['All']).map((muscle) => {
                const active = filterMuscle === muscle;
                return (
                  <TouchableOpacity
                    key={muscle}
                    style={[libStyles.filterChip, active && libStyles.filterChipActive]}
                    onPress={() => setFilterMuscle((prev) => (prev === muscle ? null : muscle))}
                  >
                    <Text style={[libStyles.filterText, active && { color: C.PUSH }]}>{muscle}</Text>
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          </View>

          <Text style={libStyles.countLabel}>{filtered.length} exercises</Text>

          <ScrollView
            style={libStyles.list}
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={{ paddingBottom: Math.max(20, keyboardInset + 14) }}
          >
            {filtered.map((exercise) => (
              <TouchableOpacity key={exercise.id} style={libStyles.exRow} onPress={() => onAdd(exercise)}>
                <View style={libStyles.exInfo}>
                  <Text style={libStyles.exName}>{exercise.name}</Text>
                  <Text style={libStyles.exMeta}>
                    {exercise.muscleGroup || exercise.primaryMuscle || (exercise.primaryMuscles || [])[0] || 'Other'} · {exercise.equipment || 'Other'}
                  </Text>
                </View>
                <View style={libStyles.rowActions}>
                  <TouchableOpacity
                    onPress={(event) => {
                      event?.stopPropagation?.();
                      toggleFavorite(exercise.id);
                    }}
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                  >
                    <Text style={[libStyles.starIcon, favoriteSet.has(exercise.id) && { color: C.PUSH }]}>?</Text>
                  </TouchableOpacity>
                  <Text style={libStyles.addIcon}>+</Text>
                </View>
              </TouchableOpacity>
            ))}

            {!filtered.length ? (
              <View style={libStyles.emptyWrap}>
                <Text style={libStyles.emptyText}>No exercises found</Text>
                {!!searchSeed && (
                  <View style={libStyles.emptyActions}>
                    <TouchableOpacity style={libStyles.createBtn} onPress={() => onCreateExercise?.(searchSeed, filterMuscle)}>
                      <Text style={libStyles.createBtnText}>+ ADD EXERCISE</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={libStyles.quickBtn} onPress={() => onQuickAdd?.(searchSeed, filterMuscle)}>
                      <Text style={libStyles.quickBtnText}>+ QUICK ADD</Text>
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            ) : null}

            {!!searchSeed && !hasExact ? (
              <View style={libStyles.inlineCreateFooter}>
                <Text style={libStyles.inlineHint}>Can't find it?</Text>
                <View style={libStyles.inlineCreateActions}>
                  <TouchableOpacity style={libStyles.inlineCreateBtn} onPress={() => onCreateExercise?.(searchSeed, filterMuscle)}>
                    <Text style={libStyles.inlineCreateBtnText}>+ Add Exercise</Text>
                  </TouchableOpacity>
                  <TouchableOpacity style={libStyles.inlineQuickBtn} onPress={() => onQuickAdd?.(searchSeed, filterMuscle)}>
                    <Text style={libStyles.inlineQuickText}>+ Quick Add</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ) : null}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

export default function PlanDayEditorScreen({ route, navigation }) {
  const { plan, day } = route.params;
  const { updatePlanDay } = useWatermelonAppData() || {};
  const [exercises, setExercises] = useState(day.exercises || []);
  const [editingExercise, setEditingExercise] = useState(null);
  const [showLibrary, setShowLibrary] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);

  function saveExercises(newExercises) {
    setExercises(newExercises);
    if (updatePlanDay) updatePlanDay(plan.id, day.id, { exercises: newExercises });
  }

  function handleMoveUp(idx) {
    if (idx <= 0) return;
    const updated = [...exercises];
    [updated[idx - 1], updated[idx]] = [updated[idx], updated[idx - 1]];
    saveExercises(updated);
  }

  function handleMoveDown(idx) {
    if (idx >= exercises.length - 1) return;
    const updated = [...exercises];
    [updated[idx], updated[idx + 1]] = [updated[idx + 1], updated[idx]];
    saveExercises(updated);
  }

  function handleDelete(idx) {
    setAlertConfig({
      title: 'Remove Exercise',
      message: 'Remove this exercise from the day?',
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Remove', style: 'destructive', onPress: () => saveExercises(exercises.filter((_, i) => i !== idx)) },
      ],
    });
  }

  function handleEditSave(updated) {
    const newExercises = exercises.map((exercise) => (
      exercise.id === updated.id ? updated : exercise
    ));
    saveExercises(newExercises);
    setEditingExercise(null);
  }

  function handleAddFromLibrary(libEx) {
    const trackingType = inferTrackingType(libEx);
    const repsDefault = trackingType === 'weight_reps' ? '10-12' : '60';
    const newEx = {
      id: generateId(),
      name: libEx.name,
      sets: 3,
      reps: repsDefault,
      isHeavy: false,
      isWarmup: false,
      muscleGroup: libEx.muscleGroup,
      primaryMuscles: libEx.primaryMuscles || (libEx.primaryMuscle ? [libEx.primaryMuscle] : []),
      exerciseId: libEx.id || libEx.exerciseId || libEx.name,
      notes: libEx.cue || '',
      defaultRestSeconds: 120,
      trackingType,
    };
    saveExercises([...exercises, newEx]);
    setShowLibrary(false);
  }

  const handleQuickAddFromLibrary = useCallback(async (nameSeed, preferredMuscle = null) => {
    const cleaned = String(nameSeed || '').trim();
    if (!cleaned) return;
    try {
      const custom = {
        id: `quick_${cleaned.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase()}_${Date.now().toString(36)}`,
        name: cleaned,
        primaryMuscles: [String(preferredMuscle || 'other').toLowerCase()],
        secondaryMuscles: [],
        equipment: 'Other',
        category: 'strength',
        force: 'push',
        instructions: [],
        isCustom: true,
      };
      await saveCustomExercise(custom);
      handleAddFromLibrary(custom);
    } catch (error) {
      if (error?.code === 'DUPLICATE_EXERCISE_NAME') {
        const index = await getExerciseIndex();
        const existing = (index || []).find((exercise) => normalizeNameKey(exercise?.name) === normalizeNameKey(cleaned));
        if (existing) {
          handleAddFromLibrary(existing);
          return;
        }
      }
      setAlertConfig({
        title: 'Quick add unavailable',
        message: String(error?.message || error || 'Could not add exercise right now.'),
        buttons: [{ text: 'OK', style: 'default' }],
      });
    }
  }, [exercises]);

  function handleAddCustom() {
    navigation.navigate('CreateExercise');
  }

  function handleCreateFromPicker(seedName, preferredMuscle = null) {
    setShowLibrary(false);
    navigation.navigate('CreateExercise', {
      initialName: seedName,
      preferredPrimaryMuscle: preferredMuscle || null,
    });
  }

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
        <View style={styles.dayHeader}>
          <Text style={styles.planName}>{plan.name}</Text>
          <Text style={styles.dayName}>{day.name}</Text>
          <Text style={styles.exerciseCount}>{exercises.length} exercises</Text>
        </View>

        {exercises.map((exercise, idx) => (
          <ExerciseCard
            key={exercise.id}
            exercise={exercise}
            showControls
            isFirst={idx === 0}
            isLast={idx === exercises.length - 1}
            onPress={() => setEditingExercise(exercise)}
            onMoveUp={() => handleMoveUp(idx)}
            onMoveDown={() => handleMoveDown(idx)}
            onDelete={() => handleDelete(idx)}
          />
        ))}

        {exercises.length === 0 && (
          <View style={styles.empty}>
            <Text style={styles.emptyText}>No exercises yet</Text>
          </View>
        )}

        <View style={styles.addButtonsRow}>
          <TouchableOpacity style={styles.addLibBtn} onPress={() => setShowLibrary(true)}>
            <Text style={styles.addLibBtnText}>+ FROM LIBRARY</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.addCustomBtn} onPress={handleAddCustom}>
            <Text style={styles.addCustomBtnText}>+ CUSTOM</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>

      <EditExerciseModal
        visible={!!editingExercise}
        exercise={editingExercise}
        onSave={handleEditSave}
        onClose={() => setEditingExercise(null)}
      />

      <AddFromLibraryModal
        visible={showLibrary}
        onAdd={handleAddFromLibrary}
        onClose={() => setShowLibrary(false)}
        onCreateExercise={handleCreateFromPicker}
        onQuickAdd={handleQuickAddFromLibrary}
      />
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: C.BG },
  scroll: { flex: 1 },
  content: { padding: 16, paddingBottom: 40 },
  dayHeader: { marginBottom: 20 },
  planName: { color: C.SECONDARY, fontSize: 13, letterSpacing: 1, marginBottom: 4 },
  dayName: {
    color: C.TEXT,
    fontSize: 24,
    fontWeight: '900',
    letterSpacing: 1,
  },
  exerciseCount: { color: C.SECONDARY, fontSize: 13, marginTop: 4 },
  empty: { alignItems: 'center', paddingTop: 40 },
  emptyText: { color: C.SECONDARY, fontSize: 16 },
  addButtonsRow: { flexDirection: 'row', gap: 12, marginTop: 20 },
  addLibBtn: {
    flex: 1,
    backgroundColor: C.CARD,
    borderWidth: 1,
    borderColor: C.PUSH,
    borderRadius: 10,
    padding: 14,
    alignItems: 'center',
  },
  addLibBtnText: { color: C.PUSH, fontSize: 13, fontWeight: '700', letterSpacing: 1 },
  addCustomBtn: {
    flex: 1,
    backgroundColor: C.CARD,
    borderWidth: 1,
    borderColor: C.BORDER,
    borderRadius: 10,
    padding: 14,
    alignItems: 'center',
  },
  addCustomBtnText: { color: C.SECONDARY, fontSize: 13, fontWeight: '700', letterSpacing: 1 },
});

const modalStyles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.85)',
    justifyContent: 'flex-end',
  },
  card: {
    backgroundColor: C.CARD,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 24,
    maxHeight: '90%',
    borderTopWidth: 1,
    borderColor: C.BORDER,
  },
  title: {
    color: C.TEXT,
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: 3,
    marginBottom: 16,
  },
  label: { color: C.SECONDARY, fontSize: 11, letterSpacing: 2, marginBottom: 6, marginTop: 10 },
  input: {
    backgroundColor: C.SURFACE,
    borderWidth: 1,
    borderColor: C.BORDER,
    borderRadius: 8,
    padding: 12,
    color: C.TEXT,
    fontSize: 15,
  },
  row: { flexDirection: 'row', gap: 12 },
  half: { flex: 1 },
  togglesRow: { flexDirection: 'row', gap: 12, marginTop: 16, marginBottom: 8 },
  toggle: {
    flex: 1,
    borderWidth: 1,
    borderColor: C.BORDER,
    borderRadius: 8,
    padding: 10,
    alignItems: 'center',
  },
  toggleActive: { borderColor: C.PUSH, backgroundColor: '#FF450015' },
  toggleWarmupActive: { borderColor: '#666', backgroundColor: '#66666615' },
  toggleText: { color: C.SECONDARY, fontSize: 12, fontWeight: '700', letterSpacing: 1 },
  buttons: { flexDirection: 'row', gap: 12, marginTop: 20 },
  cancel: { flex: 1, padding: 14, alignItems: 'center' },
  cancelText: { color: C.SECONDARY, fontSize: 16 },
  save: {
    flex: 1,
    backgroundColor: C.PUSH,
    borderRadius: 10,
    padding: 14,
    alignItems: 'center',
  },
  saveText: { color: '#fff', fontSize: 16, fontWeight: '700', letterSpacing: 1 },
});

const libStyles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.85)',
    justifyContent: 'flex-end',
  },
  card: {
    backgroundColor: C.CARD,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 16,
    height: SCREEN_H * 0.86,
    borderTopWidth: 1,
    borderColor: C.BORDER,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  title: {
    color: C.TEXT,
    fontSize: 18,
    fontWeight: '900',
    letterSpacing: 3,
  },
  closeBtn: { color: C.SECONDARY, fontSize: 20, padding: 4 },
  search: {
    backgroundColor: C.SURFACE,
    borderWidth: 1,
    borderColor: C.BORDER,
    borderRadius: 8,
    padding: 10,
    color: C.TEXT,
    fontSize: 15,
    marginBottom: 10,
  },
  filterChip: {
    height: 32,
    paddingHorizontal: 14,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: C.BORDER,
    marginRight: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterChipActive: { borderColor: C.PUSH, backgroundColor: 'rgba(255, 69, 0, 0.1)' },
  filterText: { color: C.SECONDARY, fontSize: 13, fontWeight: '600' },
  countLabel: { color: C.SECONDARY, fontSize: 11, letterSpacing: 2, marginBottom: 4, marginTop: 2 },
  list: { flex: 1 },
  exRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: C.BORDER,
  },
  exInfo: { flex: 1, paddingRight: 10 },
  exName: { color: C.TEXT, fontSize: 15, fontWeight: '600' },
  exMeta: { color: C.SECONDARY, fontSize: 12, marginTop: 2 },
  rowActions: {
    width: 72,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 14,
  },
  starIcon: { color: C.SECONDARY, fontSize: 22 },
  addIcon: { color: C.PUSH, fontSize: 24, fontWeight: '300' },
  emptyWrap: { alignItems: 'center', paddingVertical: 26, gap: 10 },
  emptyText: { color: C.SECONDARY, fontSize: 13 },
  emptyActions: { flexDirection: 'row', gap: 10, flexWrap: 'wrap', justifyContent: 'center' },
  createBtn: {
    borderWidth: 1,
    borderColor: C.PUSH,
    borderRadius: 999,
    paddingHorizontal: 16,
    paddingVertical: 9,
    backgroundColor: 'rgba(255,69,0,0.1)',
  },
  createBtnText: { color: C.PUSH, fontSize: 11, fontWeight: '800', letterSpacing: 1 },
  quickBtn: {
    borderWidth: 1,
    borderColor: C.BORDER,
    borderRadius: 999,
    paddingHorizontal: 16,
    paddingVertical: 9,
    backgroundColor: 'transparent',
  },
  quickBtnText: { color: C.SECONDARY, fontSize: 11, fontWeight: '800', letterSpacing: 1 },
  inlineCreateFooter: { marginTop: 12, paddingTop: 10, borderTopWidth: 1, borderTopColor: C.BORDER },
  inlineHint: { color: C.SECONDARY, fontSize: 12, marginBottom: 8 },
  inlineCreateActions: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  inlineCreateBtn: {
    borderWidth: 1,
    borderColor: C.PUSH,
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 8,
    backgroundColor: 'rgba(255,69,0,0.1)',
  },
  inlineCreateBtnText: { color: C.PUSH, fontSize: 11, fontWeight: '800' },
  inlineQuickBtn: {
    borderWidth: 1,
    borderColor: C.BORDER,
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  inlineQuickText: { color: C.SECONDARY, fontSize: 11, fontWeight: '700' },
});
