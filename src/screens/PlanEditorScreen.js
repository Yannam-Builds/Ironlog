import React, { useState, useEffect, useMemo } from 'react';
import { View, Text, ScrollView, StyleSheet, TextInput, Modal, TouchableOpacity } from 'react-native';
import { TouchableOpacity as RNGHTouchableOpacity } from 'react-native-gesture-handler';
import { BottomSheetModal, BottomSheetBackdrop, BottomSheetView, BottomSheetFlatList } from '@gorhom/bottom-sheet';
import { MagicScroll } from '@appandflow/react-native-magic-scroll';
import CustomAlert from '../components/CustomAlert';
import DraggableFlatList, { ScaleDecorator } from 'react-native-draggable-flatlist';
import Ionicons from 'react-native-vector-icons/Ionicons';
import useWatermelonPlans from '../hooks/useWatermelonPlans';
import useWatermelonSettings from '../hooks/useWatermelonSettings';
import { useTheme } from '../context/ThemeContext';
import { getExerciseIndex } from '../services/ExerciseLibraryService';
import { fireHaptic } from '../services/hapticsEngine';
import {
  getFavoriteExerciseIds,
  setFavoriteExerciseIds,
} from '../services/favoriteExercisesService';
import {
  buildFilterChipOptions,
  getExerciseFilterSummary,
  getExercisePrimaryFocus,
  matchesExerciseFilter,
} from '../utils/exerciseFilters';
import { buildExerciseSearchIndex, queryExerciseSearch } from '../services/exerciseSearchAdapter';
import { withAlpha } from '../utils/colorUtils';

function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }
function toTitleCase(value) {
  return String(value || '')
    .replace(/[_-]+/g, ' ')
    .trim()
    .split(' ')
    .filter(Boolean)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
}
const DAY_COLORS = ['#FF4500', '#0080FF', '#00C170', '#A020F0', '#FFD700', '#FF6B35', '#00BCD4'];
const GROUP_COLORS = { A: '#FF4500', B: '#0080FF', C: '#00C170' };
const LIB_ROW_HEIGHT = 68;

function inferTrackingType(exercise = {}) {
  const tracking = String(exercise?.trackingType || '').trim();
  if (tracking) return tracking;
  const signal = `${String(exercise?.category || '').toLowerCase()} ${String(exercise?.name || '').toLowerCase()} ${String(exercise?.movementPattern || '').toLowerCase()}`;
  if (/(stretch|hold|plank|mobility|isometric)/.test(signal)) return 'duration';
  if (/(treadmill|bike|erg|rower|run|walk|ski|carry|conditioning|cardio|distance|locomotion)/.test(signal)) return 'duration_distance';
  return 'weight_reps';
}

function buildPlanExerciseFromLibrary(exercise = {}, previous = {}) {
  const trackingType = inferTrackingType(exercise);
  const primaryMuscles = Array.isArray(exercise.primaryMuscles)
    ? exercise.primaryMuscles
    : (exercise.primaryMuscle ? [exercise.primaryMuscle] : []);

  return {
    ...previous,
    name: exercise.name,
    sets: previous.sets ?? 3,
    reps: previous.reps || (trackingType === 'weight_reps' ? '10' : '60'),
    primary: getExercisePrimaryFocus(exercise),
    note: previous.note || '',
    exerciseId: exercise.id || exercise.exerciseId || exercise.name,
    supersetGroup: previous.supersetGroup ?? null,
    trackingType,
    primaryMuscles,
    secondaryMuscles: Array.isArray(exercise.secondaryMuscles) ? exercise.secondaryMuscles : [],
    equipment: exercise.equipment || previous.equipment || null,
    category: exercise.category || previous.category || null,
    isBodyweight: exercise.isBodyweight ?? previous.isBodyweight ?? null,
    requiresExternalLoad: exercise.requiresExternalLoad ?? previous.requiresExternalLoad ?? null,
    movementPattern: exercise.movementPattern || previous.movementPattern || null,
    difficulty: exercise.difficulty || previous.difficulty || null,
    apparatus: exercise.apparatus || previous.apparatus || null,
    equipmentDetail: exercise.equipmentDetail || previous.equipmentDetail || null,
    sourceTags: Array.isArray(exercise.sourceTags) ? exercise.sourceTags : (previous.sourceTags || []),
  };
}

export default function PlanEditorScreen({ route, navigation }) {
  const { planId } = route.params;
  const { plans, savePlans } = useWatermelonPlans();
  const { settings } = useWatermelonSettings();
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;
  const exLibrarySheetRef = React.useRef(null);
  const exLibrarySnapPoints = useMemo(() => ['85%'], []);

  const planIdx = plans.findIndex(p => p.id === planId);
  const plan = plans[planIdx];

  const [alertConfig, setAlertConfig] = useState(null);
  const [editDayIdx, setEditDayIdx] = useState(null);
  const [showAddDay, setShowAddDay] = useState(false);
  const [dayName, setDayName] = useState('');
  const [dayTag, setDayTag] = useState('');
  const [showEditPlan, setShowEditPlan] = useState(false);
  const [editPlanName, setEditPlanName] = useState('');
  const [showEditDay, setShowEditDay] = useState(false);
  const [editDayModalIdx, setEditDayModalIdx] = useState(null);
  const [editDayName, setEditDayName] = useState('');
  const [editDayTag, setEditDayTag] = useState('');
  const [showExLib, setShowExLib] = useState(false);
  const [libSearch, setLibSearch] = useState('');
  const [libMuscle, setLibMuscle] = useState(null); // null = show all
  const [libScope, setLibScope] = useState('all');
  const [allExercises, setAllExercises] = useState([]);
  const [libMuscles, setLibMuscles] = useState([]);
  const [favoriteIds, setFavoriteIds] = useState([]);
  const [replaceExIdx, setReplaceExIdx] = useState(null); // if set, library modal replaces instead of adds
  const [debouncedLibSearch, setDebouncedLibSearch] = useState('');

  useEffect(() => {
    getExerciseIndex().then(idx => {
      if (!idx) return;
      setAllExercises(idx);
      setLibMuscles(buildFilterChipOptions(idx, { includeCategory: true, includeEquipment: false }));
    });
    getFavoriteExerciseIds().then((ids) => setFavoriteIds(Array.isArray(ids) ? ids : []));
  }, []);

  useEffect(() => {
    if (!libMuscle) return;
    if (!libMuscles.includes(libMuscle)) setLibMuscle(null);
  }, [libMuscles, libMuscle]);

  useEffect(() => {
    if (!showExLib) return;
    setLibMuscle(null);
    setLibSearch('');
    setLibScope('all');
    getFavoriteExerciseIds().then((ids) => setFavoriteIds(Array.isArray(ids) ? ids : []));
  }, [showExLib]);

  useEffect(() => {
    if (showExLib) {
      requestAnimationFrame(() => exLibrarySheetRef.current?.present());
    } else {
      exLibrarySheetRef.current?.dismiss();
    }
  }, [showExLib]);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedLibSearch(libSearch), 180);
    return () => clearTimeout(timer);
  }, [libSearch]);

  // MiniSearch index for exercise library
  const libSearchIndex = useMemo(() => {
    return buildExerciseSearchIndex(allExercises);
  }, [allExercises]);

  const libSearchResultIds = useMemo(() => {
    return queryExerciseSearch({
      query: debouncedLibSearch,
      index: libSearchIndex,
      exercises: allExercises,
    });
  }, [debouncedLibSearch, libSearchIndex, allExercises]);
  const libSearchRankMap = libSearchResultIds?.rankMap || null;

  if (!plan) return null;

  const updatePlan = (updated) => {
    const newPlans = [...plans];
    newPlans[planIdx] = updated;
    savePlans(newPlans);
  };

  const addDay = () => {
    if (!dayName.trim()) return;
    const colorIdx = plan.days.length % DAY_COLORS.length;
    const newDay = {
      id: genId(),
      label: `D${plan.days.length + 1}`,
      name: dayName.trim().toUpperCase(),
      tag: dayTag.trim(),
      color: DAY_COLORS[colorIdx],
      exercises: [],
    };
    updatePlan({ ...plan, days: [...plan.days, newDay] });
    setDayName(''); setDayTag(''); setShowAddDay(false);
  };

  const savePlanName = () => {
    if (!editPlanName.trim()) return;
    updatePlan({ ...plan, name: editPlanName.trim() });
    setShowEditPlan(false);
  };

  const saveDayEdit = () => {
    if (editDayModalIdx === null || !editDayName.trim()) return;
    const days = [...plan.days];
    days[editDayModalIdx] = { ...days[editDayModalIdx], name: editDayName.trim().toUpperCase(), tag: editDayTag.trim() };
    updatePlan({ ...plan, days });
    setShowEditDay(false);
    setEditDayModalIdx(null);
  };

  const deleteDay = (dayIdx) => {
    fireHaptic('destructiveAction', { enabled: haptic });
    setAlertConfig({
      title: 'Delete day?',
      message: plan.days[dayIdx]?.name || '',
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete', style: 'destructive', onPress: () => {
            const days = plan.days.filter((_, i) => i !== dayIdx);
            updatePlan({ ...plan, days });
            if (editDayIdx === dayIdx) setEditDayIdx(null);
          },
        },
      ],
    });
  };

  const addExercise = (ex) => {
    if (editDayIdx === null) return;
    const days = [...plan.days];
    if (replaceExIdx !== null) {
      // Replace mode
      const exs = [...(days[editDayIdx].exercises || [])];
      exs[replaceExIdx] = buildPlanExerciseFromLibrary(ex, exs[replaceExIdx]);
      days[editDayIdx] = { ...days[editDayIdx], exercises: exs };
    } else {
      days[editDayIdx] = {
        ...days[editDayIdx],
        exercises: [...(days[editDayIdx].exercises || []), buildPlanExerciseFromLibrary(ex)],
      };
    }
    updatePlan({ ...plan, days });
    setShowExLib(false);
    setReplaceExIdx(null);
  };

  const removeExercise = (dayIdx, exIdx) => {
    fireHaptic('destructiveAction', { enabled: haptic });
    setAlertConfig({
      title: 'Remove exercise?',
      message: plan.days[dayIdx]?.exercises[exIdx]?.name || '',
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove', style: 'destructive', onPress: () => {
            const days = [...plan.days];
            days[dayIdx] = { ...days[dayIdx], exercises: days[dayIdx].exercises.filter((_, i) => i !== exIdx) };
            updatePlan({ ...plan, days });
          },
        },
      ],
    });
  };

  const onExLongPress = (dayIdx, exIdx) => {
    fireHaptic('selection', { enabled: haptic });
    const ex = plan.days[dayIdx].exercises[exIdx];
    const currentGroup = ex.supersetGroup || null;
    setAlertConfig({
      title: ex.name,
      message: '',
      buttons: [
        {
          text: 'Replace exercise', style: 'default', onPress: () => {
            setEditDayIdx(dayIdx);
            setReplaceExIdx(exIdx);
            setShowExLib(true);
          },
        },
        {
          text: currentGroup ? `Change Superset (${currentGroup})` : 'Assign to Superset',
          style: 'default',
          onPress: () => showSupersetPicker(dayIdx, exIdx),
        },
        { text: 'Remove exercise', style: 'destructive', onPress: () => removeExercise(dayIdx, exIdx) },
        { text: 'Cancel', style: 'cancel' },
      ],
    });
  };

  const showSupersetPicker = (dayIdx, exIdx) => {
    const ex = plan.days[dayIdx].exercises[exIdx];
    const current = ex.supersetGroup;
    const buttons = ['A', 'B', 'C'].map(g => ({
      text: g + (current === g ? ' ?' : ''),
      style: 'default',
      onPress: () => assignSuperset(dayIdx, exIdx, g),
    }));
    if (current) buttons.push({ text: 'Remove from Superset', style: 'destructive', onPress: () => assignSuperset(dayIdx, exIdx, null) });
    buttons.push({ text: 'Cancel', style: 'cancel' });
    setAlertConfig({ title: 'Assign to Superset', message: '', buttons });
  };

  const assignSuperset = (dayIdx, exIdx, group) => {
    fireHaptic('selection', { enabled: haptic });
    const days = [...plan.days];
    const exs = [...days[dayIdx].exercises];
    exs[exIdx] = { ...exs[exIdx], supersetGroup: group };
    days[dayIdx] = { ...days[dayIdx], exercises: exs };
    updatePlan({ ...plan, days });
  };

  const onExerciseDragEnd = (dayIdx, { data }) => {
    fireHaptic('selection', { enabled: haptic });
    const days = [...plan.days];
    days[dayIdx] = { ...days[dayIdx], exercises: data };
    updatePlan({ ...plan, days });
  };

  const favoriteSet = new Set(favoriteIds);

  const filtered = allExercises.filter(e => {
    const ms = !libMuscle || matchesExerciseFilter(e, libMuscle, { includeCategory: true, includeEquipment: false });
    const favoritesMatch = libScope === 'favorites' ? favoriteSet.has(e.id) : true;
    const searchMatch = !libSearchResultIds || libSearchResultIds.has(e.id);
    return ms && favoritesMatch && searchMatch;
  }).sort((a, b) => {
    if (libSearchRankMap) {
      const rankA = libSearchRankMap.has(a.id) ? libSearchRankMap.get(a.id) : Number.MAX_SAFE_INTEGER;
      const rankB = libSearchRankMap.has(b.id) ? libSearchRankMap.get(b.id) : Number.MAX_SAFE_INTEGER;
      if (rankA !== rankB) return rankA - rankB;
    }
    const favDiff = Number(favoriteSet.has(b.id)) - Number(favoriteSet.has(a.id));
    if (favDiff !== 0) return favDiff;
    return a.name.localeCompare(b.name);
  });
  const createSeed = String(libSearch || '').trim();
  const hasExactLibraryMatch = createSeed.length > 0
    && allExercises.some((exercise) => String(exercise?.name || '').trim().toLowerCase() === createSeed.toLowerCase());

  const toggleFavorite = async (exerciseId) => {
    const id = String(exerciseId || '').trim();
    if (!id) return;
    const has = favoriteSet.has(id);
    const next = has
      ? favoriteIds.filter((value) => value !== id)
      : [...favoriteIds, id];
    setFavoriteIds(next);
    await setFavoriteExerciseIds(next);
  };

  const renderLibRow = ({ item: ex }) => (
  <TouchableOpacity
    style={[ls.libRow, { borderBottomColor: colors.faint }]}
    onPress={() => addExercise(ex)}>
    <View style={{ flex: 1 }}>
      <Text style={[ls.exName, { color: colors.text }]} numberOfLines={1}>{ex.name}</Text>
      <Text style={[ls.exMeta, { color: colors.muted }]} numberOfLines={1}>
        {getExerciseFilterSummary(ex).join(', ')}{ex.equipment ? ` · ${toTitleCase(ex.equipment)}` : ''}
      </Text>
    </View>
    <View style={ls.rowActions}>
      <TouchableOpacity
        onPress={(event) => {
          event?.stopPropagation?.();
          fireHaptic('selection', { enabled: haptic });
          toggleFavorite(ex.id);
        }}
        hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
        <Ionicons name={favoriteSet.has(ex.id) ? 'star' : 'star-outline'} size={17} color={favoriteSet.has(ex.id) ? colors.accent : colors.muted} />
      </TouchableOpacity>
      <Ionicons name={replaceExIdx !== null ? 'swap-horizontal' : 'add'} size={20} color={colors.accent} />
    </View>
  </TouchableOpacity>
);

const renderExercise = (dayIdx) => ({ item: ex, drag, isActive, getIndex }) => {
    const exIdx = getIndex();
    const group = ex.supersetGroup;
    const groupColor = group ? GROUP_COLORS[group] : null;
    return (
      <ScaleDecorator activeScale={0.98}>
        <View style={[ls.exRow, {
          borderTopColor: colors.faint,
          backgroundColor: isActive ? colors.cardBorder : 'transparent',
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingVertical: 12,
        }]}>
          <View style={{ flex: 1 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <Text style={[ls.exName, { color: colors.text }]} numberOfLines={1}>{ex.name}</Text>
              {group ? (
                <View style={[ls.groupBadge, { borderColor: withAlpha(groupColor, 0.4), backgroundColor: withAlpha(groupColor, 0.13) }]}>
                  <Text style={[ls.groupBadgeText, { color: groupColor }]}>{group}</Text>
                </View>
              ) : null}
            </View>
            <Text style={[ls.exMeta, { color: colors.muted }]}>{ex.sets} sets · {ex.reps} reps · {ex.primary}</Text>
          </View>
          {/* Explicit edit menu button · replace, superset, remove */}
          <TouchableOpacity
            onPress={() => onExLongPress(dayIdx, exIdx)}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
            style={{ paddingHorizontal: 10 }}>
            <Ionicons name="ellipsis-vertical" size={18} color={colors.muted} />
          </TouchableOpacity>
          <RNGHTouchableOpacity onLongPress={drag} delayLongPress={150} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }} style={{ paddingLeft: 4 }}>
            <Ionicons name="reorder-two-outline" size={24} color={colors.faint} />
          </RNGHTouchableOpacity>
        </View>
      </ScaleDecorator>
    );
  };

  return (
    <View style={[ls.container, { backgroundColor: colors.bg }]}>
      <ScrollView contentContainerStyle={{ padding: 20, paddingBottom: 40 }}>
        <TouchableOpacity
          style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 20 }}
          onPress={() => { setEditPlanName(plan.name); setShowEditPlan(true); }}>
          <Text style={[ls.planName, { color: colors.text, marginBottom: 0 }]}>{plan.name}</Text>
          <Ionicons name="pencil-outline" size={16} color={colors.muted} />
        </TouchableOpacity>

        {plan.days.map((day, di) => (
          <View key={day.id} style={[ls.dayCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <TouchableOpacity
              style={[ls.dayHeader, { borderLeftColor: day.color }]}
              onPress={() => setEditDayIdx(editDayIdx === di ? null : di)}>
              <View>
                <Text style={[ls.dayName, { color: colors.text }]}>{day.name}</Text>
                <Text style={[ls.dayTag, { color: colors.muted }]}>{day.tag || 'No description'}</Text>
              </View>
              <View style={{ flexDirection: 'row', gap: 12, alignItems: 'center' }}>
                <TouchableOpacity onPress={() => { setEditDayName(day.name); setEditDayTag(day.tag || ''); setEditDayModalIdx(di); setShowEditDay(true); }}>
                  <Ionicons name="pencil-outline" size={16} color={colors.muted} />
                </TouchableOpacity>
                <TouchableOpacity onPress={() => deleteDay(di)}>
                  <Ionicons name="trash-outline" size={16} color="#CC2222" />
                </TouchableOpacity>
                <Ionicons name={editDayIdx === di ? 'chevron-up' : 'chevron-down'} size={16} color={colors.muted} />
              </View>
            </TouchableOpacity>

            {editDayIdx === di && (
              <View style={{ paddingTop: 4 }}>
                <DraggableFlatList
                  data={day.exercises || []}
                  keyExtractor={(ex, i) => ex.exerciseId || ex.name + i}
                  renderItem={renderExercise(di)}
                  onDragEnd={(result) => onExerciseDragEnd(di, result)}
                  scrollEnabled={false}
                  activationDistance={10}
                />
                <TouchableOpacity
                  style={[ls.addExBtn, { borderColor: colors.accent }]}
                  onPress={() => { setEditDayIdx(di); setReplaceExIdx(null); setShowExLib(true); }}>
                  <Text style={[ls.addExBtnText, { color: colors.accent }]}>+ ADD EXERCISE</Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
        ))}

        <TouchableOpacity style={[ls.addDayBtn, { borderColor: colors.accent }]} onPress={() => setShowAddDay(true)}>
          <Ionicons name="add" size={18} color={colors.accent} />
          <Text style={[ls.addDayBtnText, { color: colors.accent }]}>ADD DAY</Text>
        </TouchableOpacity>
      </ScrollView>

      {/* Add Day Modal */}
      <Modal visible={showAddDay} transparent animationType="fade">
        <View style={ls.overlay}>
          <View style={[ls.modal, { backgroundColor: colors.surface, borderColor: colors.cardBorder }]}>
            <Text style={[ls.modalTitle, { color: colors.text }]}>ADD DAY</Text>
            <TextInput
              style={[ls.input, { color: colors.text, borderColor: colors.faint }]}
              placeholder="Day name (e.g. PUSH)" placeholderTextColor={colors.muted}
              value={dayName} onChangeText={setDayName} autoFocus />
            <TextInput
              style={[ls.input, { color: colors.text, borderColor: colors.faint, marginTop: 10 }]}
              placeholder="Tag (e.g. Chest · Shoulders)" placeholderTextColor={colors.muted}
              value={dayTag} onChangeText={setDayTag} />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={[ls.cancelBtn, { borderColor: colors.faint }]} onPress={() => setShowAddDay(false)}>
                <Text style={{ color: colors.muted }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[ls.confirmBtn, { backgroundColor: colors.accent }]} onPress={addDay}>
                <Text style={{ color: '#fff', fontWeight: '800' }}>ADD</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* Edit Plan Name Modal */}
      <Modal visible={showEditPlan} transparent animationType="fade">
        <View style={ls.overlay}>
          <View style={[ls.modal, { backgroundColor: colors.surface, borderColor: colors.cardBorder }]}>
            <Text style={[ls.modalTitle, { color: colors.text }]}>RENAME PLAN</Text>
            <TextInput
              style={[ls.input, { color: colors.text, borderColor: colors.faint }]}
              placeholder="Plan name" placeholderTextColor={colors.muted}
              value={editPlanName} onChangeText={setEditPlanName} autoFocus />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={[ls.cancelBtn, { borderColor: colors.faint }]} onPress={() => setShowEditPlan(false)}>
                <Text style={{ color: colors.muted }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[ls.confirmBtn, { backgroundColor: colors.accent }]} onPress={savePlanName}>
                <Text style={{ color: '#fff', fontWeight: '800' }}>SAVE</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* Edit Day Modal */}
      <Modal visible={showEditDay} transparent animationType="fade">
        <View style={ls.overlay}>
          <View style={[ls.modal, { backgroundColor: colors.surface, borderColor: colors.cardBorder }]}>
            <Text style={[ls.modalTitle, { color: colors.text }]}>EDIT DAY</Text>
            <TextInput
              style={[ls.input, { color: colors.text, borderColor: colors.faint }]}
              placeholder="Day name (e.g. PUSH)" placeholderTextColor={colors.muted}
              value={editDayName} onChangeText={setEditDayName} autoFocus />
            <TextInput
              style={[ls.input, { color: colors.text, borderColor: colors.faint, marginTop: 10 }]}
              placeholder="Tag (e.g. Chest · Shoulders)" placeholderTextColor={colors.muted}
              value={editDayTag} onChangeText={setEditDayTag} />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={[ls.cancelBtn, { borderColor: colors.faint }]} onPress={() => setShowEditDay(false)}>
                <Text style={{ color: colors.muted }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[ls.confirmBtn, { backgroundColor: colors.accent }]} onPress={saveDayEdit}>
                <Text style={{ color: '#fff', fontWeight: '800' }}>SAVE</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />

      {/* Exercise Library Sheet */}
      <BottomSheetModal
        ref={exLibrarySheetRef}
        snapPoints={exLibrarySnapPoints}
        index={0}
        enablePanDownToClose
        keyboardBehavior="extend"
        android_keyboardInputMode="adjustResize"
        keyboardBlurBehavior="restore"
        onDismiss={() => { setShowExLib(false); setReplaceExIdx(null); }}
        backgroundStyle={{ backgroundColor: colors.card, borderTopLeftRadius: 24, borderTopRightRadius: 24, borderWidth: 1, borderColor: colors.cardBorder }}
        handleIndicatorStyle={{ backgroundColor: colors.faint }}
        backdropComponent={(props) => (
          <BottomSheetBackdrop {...props} appearsOnIndex={0} disappearsOnIndex={-1} opacity={0.58} />
        )}
      >
        <BottomSheetFlatList
          data={filtered}
          keyExtractor={item => item.id}
          renderItem={renderLibRow}
          contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 40 }}
          keyboardShouldPersistTaps="handled"
          ListHeaderComponent={
            <View style={{ paddingTop: 16 }}>
              {/* Title row */}
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
                <Text style={[ls.modalTitle, { color: colors.text, marginBottom: 0 }]}>
                  {replaceExIdx !== null ? 'REPLACE EXERCISE' : 'ADD EXERCISE'}
                </Text>
                <TouchableOpacity onPress={() => { setShowExLib(false); setReplaceExIdx(null); }}>
                  <Ionicons name="close" size={22} color={colors.muted} />
                </TouchableOpacity>
              </View>

              {/* Search pill */}
              <View style={{
                flexDirection: 'row', alignItems: 'center',
                backgroundColor: colors.surface, borderRadius: 999,
                borderWidth: 1, borderColor: colors.faint,
                paddingHorizontal: 14, paddingVertical: 11,
                marginBottom: 14,
              }}>
                <Ionicons name="search-outline" size={16} color={colors.muted} style={{ marginRight: 8 }} />
                <TextInput
                  style={{ flex: 1, color: colors.text, fontSize: 14, padding: 0 }}
                  placeholder="Search exercises..."
                  placeholderTextColor={colors.muted}
                  value={libSearch}
                  onChangeText={setLibSearch}
                  autoCorrect={false}
                  returnKeyType="search"
                />
              </View>

              {/* Scope chips */}
              <View style={{ flexDirection: 'row', gap: 8, marginBottom: 8 }}>
                <TouchableOpacity
                  onPress={() => { fireHaptic('selection', { enabled: haptic }); setLibScope(s => s === 'favorites' ? 'all' : 'favorites'); }}
                  style={[ls.chip, {
                    borderColor: libScope === 'favorites' ? colors.accent : colors.faint,
                    backgroundColor: libScope === 'favorites' ? colors.accentSoft : 'transparent',
                    flexDirection: 'row', alignItems: 'center', gap: 5,
                  }]}>
                  <Ionicons name={libScope === 'favorites' ? 'star' : 'star-outline'} size={12} color={libScope === 'favorites' ? colors.accent : colors.muted} />
                  <Text style={[ls.chipText, { color: libScope === 'favorites' ? colors.accent : colors.muted }]}>Favorites</Text>
                </TouchableOpacity>
              </View>

              {/* Muscle chips */}
              <View style={ls.chipsWrap}>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled"
                  style={ls.chipsScroller} contentContainerStyle={ls.chipsContent}>
                  {libMuscles.map(m => (
                    <TouchableOpacity key={m}
                      onPress={() => { fireHaptic('selection', { enabled: haptic }); setLibMuscle(prev => prev === m ? null : m); }}
                      style={[ls.chip, {
                        borderColor: libMuscle === m ? colors.accent : colors.faint,
                        backgroundColor: libMuscle === m ? colors.accentSoft : 'transparent',
                      }]}>
                      <Text style={[ls.chipText, { color: libMuscle === m ? colors.accent : colors.muted }]}>{m}</Text>
                    </TouchableOpacity>
                  ))}
                </ScrollView>
              </View>

              <Text style={[ls.countText, { color: colors.muted }]}>{filtered.length} exercises</Text>
            </View>
          }
        ListEmptyComponent={
          <View style={ls.emptyState}>
            <Text style={[ls.exMeta, { color: colors.muted }]}>No exercises match this search/filter.</Text>
            {libSearch.trim() ? (
              <View style={ls.inlineCreateWrap}>
                <TouchableOpacity
                  onPress={() => { setShowExLib(false); navigation.navigate('CreateExercise', { initialName: libSearch.trim() }); }}
                  style={[ls.chip, { borderColor: colors.accent, backgroundColor: colors.accentSoft, marginTop: 12, alignSelf: 'center', paddingHorizontal: 16, paddingVertical: 8 }]}>
                  <Text style={[ls.chipText, { color: colors.accent }]}>+ ADD EXERCISE</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => { setShowExLib(false); navigation.navigate('CreateExercise', { initialName: libSearch.trim(), quickAdd: true }); }}
                  style={[ls.chip, { borderColor: colors.faint, backgroundColor: 'transparent', marginTop: 12, alignSelf: 'center', paddingHorizontal: 16, paddingVertical: 8 }]}>
                  <Text style={[ls.chipText, { color: colors.muted }]}>+ QUICK ADD</Text>
                </TouchableOpacity>
              </View>
            ) : null}
          </View>
        }
        ListFooterComponent={
          createSeed && !hasExactLibraryMatch ? (
            <View style={ls.inlineCreateWrap}>
              <TouchableOpacity
                onPress={() => { setShowExLib(false); navigation.navigate('CreateExercise', { initialName: createSeed }); }}
                style={[ls.chip, { borderColor: colors.accent, backgroundColor: colors.accentSoft, marginTop: 12, alignSelf: 'center', paddingHorizontal: 16, paddingVertical: 8 }]}>
                <Text style={[ls.chipText, { color: colors.accent }]}>+ ADD EXERCISE</Text>
              </TouchableOpacity>
              <TouchableOpacity
                onPress={() => { setShowExLib(false); navigation.navigate('CreateExercise', { initialName: createSeed, quickAdd: true }); }}
                style={[ls.chip, { borderColor: colors.faint, backgroundColor: 'transparent', marginTop: 12, alignSelf: 'center', paddingHorizontal: 16, paddingVertical: 8 }]}>
                <Text style={[ls.chipText, { color: colors.muted }]}>+ QUICK ADD</Text>
              </TouchableOpacity>
            </View>
          ) : null
        }
      />
      </BottomSheetModal>
    </View>
  );
}

const ls = StyleSheet.create({
  container: { flex: 1 },
  planName: { fontSize: 24, fontWeight: '900', marginBottom: 20, letterSpacing: -0.5 },
  dayCard: { borderWidth: 1, marginBottom: 12, borderRadius: 18, overflow: 'hidden' },
  dayHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 14, borderLeftWidth: 4 },
  dayName: { fontSize: 18, fontWeight: '900' },
  dayTag: { fontSize: 11, marginTop: 2 },
  exRow: { flexDirection: 'row', alignItems: 'center', padding: 12, borderTopWidth: 1 },
  exName: { fontSize: 14, fontWeight: '600', flexShrink: 1 },
  exMeta: { fontSize: 11, marginTop: 2 },
  groupBadge: { borderWidth: 1, paddingHorizontal: 7, paddingVertical: 2, borderRadius: 999 },
  groupBadgeText: { fontSize: 9, fontWeight: '900', letterSpacing: 1 },
  addExBtn: { margin: 12, borderWidth: 1, borderStyle: 'dashed', padding: 10, alignItems: 'center', borderRadius: 16 },
  addExBtnText: { fontSize: 12, fontWeight: '700', letterSpacing: 2 },
  addDayBtn: { flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderStyle: 'dashed', padding: 16, justifyContent: 'center', borderRadius: 18 },
  addDayBtnText: { fontSize: 14, fontWeight: '800', letterSpacing: 2 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', justifyContent: 'center', alignItems: 'center', padding: 24 },
  modal: { padding: 24, borderWidth: 1, width: '100%', borderRadius: 20 },
  modalTitle: { fontSize: 14, fontWeight: '900', letterSpacing: 3, marginBottom: 16 },
  input: { borderWidth: 1, padding: 12, fontSize: 15, borderRadius: 14 },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1, borderRadius: 14 },
  confirmBtn: { flex: 1, padding: 14, alignItems: 'center', borderRadius: 14 },
  scopeChip: { paddingHorizontal: 12, paddingVertical: 7, borderWidth: 1, borderRadius: 999 },
  scopeChipText: { fontSize: 11, fontWeight: '700' },
  chipsWrap: { height: 42, marginBottom: 8 },
  chipsScroller: { flex: 1 },
  chipsContent: { gap: 8, paddingHorizontal: 2, paddingVertical: 2, alignItems: 'center' },
  chip: { paddingHorizontal: 12, paddingVertical: 7, borderWidth: 1, borderRadius: 999 },
  chipText: { fontSize: 11, fontWeight: '600' },
  countText: { fontSize: 10, letterSpacing: 2, marginBottom: 8 },
  libRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 6, paddingVertical: 13, borderBottomWidth: 1, minHeight: LIB_ROW_HEIGHT },
  rowActions: { width: 64, flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'center', gap: 14 },
  inlineCreateWrap: { alignItems: 'center', justifyContent: 'center', gap: 8, paddingBottom: 12 },
  emptyState: { paddingVertical: 20, alignItems: 'center', justifyContent: 'center' },
});



