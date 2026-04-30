
import React, { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet,
  ScrollView, Share, TextInput,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FlashList } from '@shopify/flash-list';
import CustomAlert from '../components/CustomAlert';
import { useFocusEffect } from '@react-navigation/native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useTheme } from '../context/ThemeContext';
import { getExerciseIndex, deleteCustomExercise } from '../services/ExerciseLibraryService';
import {
  getFavoriteExerciseIds,
  setFavoriteExerciseIds,
} from '../services/favoriteExercisesService';
import {
  buildFilterChipOptions,
  getExerciseFilterSummary,
  getZeroCountMuscleChipWarnings,
  matchesExerciseFilter,
} from '../utils/exerciseFilters';
import { buildExerciseSearchIndex, queryExerciseSearch } from '../services/exerciseSearchAdapter';
import useKeyboardInset from '../hooks/useKeyboardInset';

const ROW_HEIGHT = 75; // paddingV 16+16 + name ~22 + cue ~18 + mt2 + border1
const CONTROL_RADIUS = 10;
const EQUIP_TAGS = new Set(['Barbell', 'Dumbbell', 'Cable', 'Machine', 'Bodyweight', 'Band', 'Kettlebell', 'Other']);

export default function ExerciseLibraryScreen({ navigation }) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const [exercises, setExercises] = useState([]);
  const [search, setSearch] = useState('');
  const [muscle, setMuscle] = useState(null);    // null = no filter (show all)
  const [cat, setCat] = useState(null);           // null = no filter (show all)
  const [equip, setEquip] = useState(null);       // null = no filter (show all)
  const [movement, setMovement] = useState(null); // null = no filter (show all)
  const [difficulty, setDifficulty] = useState(null); // null = no filter (show all)
  const [bwOnly, setBwOnly] = useState(false);
  const [scope, setScope] = useState('all');
  const [favoriteIds, setFavoriteIds] = useState([]);
  const [alertConfig, setAlertConfig] = useState(null);
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const searchInputRef = useRef(null);
  const keyboardInset = useKeyboardInset(insets.bottom);
  const missingExerciseSeed = String(search || '').trim();
  const searchDockBottom = keyboardInset > 0
    ? keyboardInset + 8
    : Math.max(insets.bottom + 8, 16);
  const listEndPadding = 112 + searchDockBottom;
  const emptyLift = keyboardInset > 0 ? -Math.min(72, keyboardInset * 0.18) : 0;

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 180);
    return () => clearTimeout(timer);
  }, [search]);

  useFocusEffect(
    useCallback(() => {
      Promise.all([getExerciseIndex(), getFavoriteExerciseIds()]).then(([idx, ids]) => {
        if (idx) setExercises(idx);
        setFavoriteIds(Array.isArray(ids) ? ids : []);
      });
    }, [])
  );

  const toTitle = s => s ? s.trim().charAt(0).toUpperCase() + s.trim().slice(1).toLowerCase() : s;

  const muscles = useMemo(
    () => buildFilterChipOptions(exercises, { includeCategory: false, includeEquipment: false }),
    [exercises]
  );

  const equipmentOptions = useMemo(
    () => buildFilterChipOptions(exercises, { includeCategory: false, includeEquipment: true }).filter(t => EQUIP_TAGS.has(t)),
    [exercises]
  );

  useEffect(() => {
    if (!__DEV__) return;
    const zeroCount = getZeroCountMuscleChipWarnings(exercises);
    if (zeroCount.length) {
      console.warn('[exerciseFilters] zero-count muscle chips hidden:', zeroCount.join(', '));
    }
  }, [exercises]);

  const categories = useMemo(() => {
    const map = new Map();
    exercises.forEach(ex => { if (ex.category) { const k = ex.category.trim().toLowerCase(); if (!map.has(k)) map.set(k, toTitle(ex.category)); } });
    return [...map.values()].sort();
  }, [exercises]);

  const movementOptions = useMemo(() => {
    const set = new Set();
    exercises.forEach(ex => { if (ex.movementPattern) set.add(ex.movementPattern); });
    return [...set].sort();
  }, [exercises]);

  const difficultyOptions = useMemo(() => {
    const order = ['beginner', 'intermediate', 'advanced', 'expert'];
    const set = new Set();
    exercises.forEach(ex => { if (ex.difficulty) set.add(ex.difficulty.toLowerCase()); });
    return order.filter(d => set.has(d));
  }, [exercises]);

  const favoriteSet = useMemo(() => new Set(favoriteIds), [favoriteIds]);

  const searchIndex = useMemo(() => {
    return buildExerciseSearchIndex(exercises);
  }, [exercises]);

  const searchResultIds = useMemo(() => {
    return queryExerciseSearch({
      query: debouncedSearch,
      index: searchIndex,
      exercises,
    });
  }, [debouncedSearch, searchIndex, exercises]);

  const searchRankMap = searchResultIds?.rankMap || null;
  const normalizedSeed = String(search || '').trim();
  const normalizedSeedKey = normalizedSeed.toLowerCase();
  const hasExactNameMatch = useMemo(
    () => exercises.some((exercise) => String(exercise?.name || '').trim().toLowerCase() === normalizedSeedKey),
    [exercises, normalizedSeedKey]
  );

  const filtered = useMemo(() => {
    return exercises.filter(ex => {
      const muscleMatch = !muscle || matchesExerciseFilter(ex, muscle, { includeCategory: false, includeEquipment: false });
      const catMatch = !cat || (ex.category && ex.category.trim().toLowerCase() === cat.trim().toLowerCase());
      const equipMatch = !equip || matchesExerciseFilter(ex, equip, { includeCategory: false, includeEquipment: true });
      const movementMatch = !movement || (ex.movementPattern && ex.movementPattern === movement);
      const difficultyMatch = !difficulty || (ex.difficulty && ex.difficulty.toLowerCase() === difficulty);
      const bwMatch = !bwOnly || ex.isBodyweight === true;
      const scopeMatch = scope === 'favorites' ? favoriteSet.has(ex.id) : true;
      const searchMatch = !searchResultIds || searchResultIds.has(ex.id);
      return muscleMatch && catMatch && equipMatch && movementMatch && difficultyMatch && bwMatch && scopeMatch && searchMatch;
    }).sort((a, b) => {
      if (searchRankMap) {
        const rankA = searchRankMap.has(a.id) ? searchRankMap.get(a.id) : Number.MAX_SAFE_INTEGER;
        const rankB = searchRankMap.has(b.id) ? searchRankMap.get(b.id) : Number.MAX_SAFE_INTEGER;
        if (rankA !== rankB) return rankA - rankB;
      }
      const favDiff = Number(favoriteSet.has(b.id)) - Number(favoriteSet.has(a.id));
      if (favDiff !== 0) return favDiff;
      return a.name.localeCompare(b.name);
    });
  }, [exercises, muscle, cat, equip, movement, difficulty, bwOnly, scope, searchResultIds, favoriteSet, searchRankMap]);

  const toggleFavorite = useCallback(async (exerciseId) => {
    const id = String(exerciseId || '').trim();
    if (!id) return;
    const has = favoriteSet.has(id);
    const next = has
      ? favoriteIds.filter((value) => value !== id)
      : [...favoriteIds, id];
    setFavoriteIds(next);
    await setFavoriteExerciseIds(next);
  }, [favoriteIds, favoriteSet]);

  const onLongPressCustom = (ex) => {
    const focus = getExerciseFilterSummary(ex, 6);
    const shareMessage = [
      'IRONLOG custom exercise',
      `Name: ${ex.name}`,
      `Primary: ${(ex.primaryMuscles || []).join(', ') || ex.primaryMuscle || 'Unspecified'}`,
      `Secondary: ${(ex.secondaryMuscles || []).join(', ') || 'None'}`,
      `Equipment: ${ex.equipment || 'Other'}`,
      `Category: ${ex.category || 'strength'}`,
      focus.length ? `Tags: ${focus.join(', ')}` : null,
    ].filter(Boolean).join('\n');

    setAlertConfig({
      title: ex.name,
      message: '',
      buttons: [
        { text: 'Edit', style: 'default', onPress: () => navigation.navigate('CreateExercise', { exercise: ex }) },
        {
          text: 'Share',
          style: 'default',
          onPress: async () => {
            try {
              await Share.share({ message: shareMessage });
            } catch (_) {}
          },
        },
        {
          text: 'Delete', style: 'destructive', onPress: () => {
            setAlertConfig({
              title: 'Delete exercise?',
              message: ex.name,
              buttons: [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Delete', style: 'destructive', onPress: async () => {
                    const newIndex = await deleteCustomExercise(ex.id);
                    setExercises(newIndex || []);
                  },
                },
              ],
            });
          },
        },
        { text: 'Cancel', style: 'cancel' },
      ],
    });
  };

  const renderItem = useCallback(({ item: ex }) => (
    <TouchableOpacity
      style={[s.row, { borderBottomColor: colors.faint }]}
      activeOpacity={0.85}
      onPress={() => navigation.navigate('ExerciseProgress', { exerciseName: ex.name })}
      onLongPress={() => ex.isCustom && onLongPressCustom(ex)}
    >
      <View style={{ flex: 1 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <Text style={[s.name, { color: colors.text }]} numberOfLines={1}>{ex.name}</Text>
          {ex.isCustom && (
            <View style={[s.customBadge, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
              <Text style={[s.customBadgeText, { color: colors.accent }]}>CUSTOM</Text>
            </View>
          )}
        </View>
        <Text style={[s.sub, { color: colors.muted }]} numberOfLines={1}>
          {getExerciseFilterSummary(ex).join(', ') || '—'}
        </Text>
      </View>
      <View style={{ alignItems: 'flex-end', gap: 6 }}>
        <TouchableOpacity
          onPress={(event) => {
            event?.stopPropagation?.();
            toggleFavorite(ex.id);
          }}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <Ionicons name={favoriteSet.has(ex.id) ? 'star' : 'star-outline'} size={17} color={favoriteSet.has(ex.id) ? colors.accent : colors.muted} />
        </TouchableOpacity>
        {ex.isCustom ? (
          <TouchableOpacity
            style={[s.customMenuBtn, { borderColor: colors.faint, backgroundColor: colors.bg }]}
            onPress={(event) => {
              event?.stopPropagation?.();
              onLongPressCustom(ex);
            }}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Ionicons name="create-outline" size={14} color={colors.accent} />
            <Ionicons name="trash-outline" size={14} color="#FF453A" />
          </TouchableOpacity>
        ) : null}
        {ex.equipment ? (
          <View style={[s.tag, { backgroundColor: colors.faint }]}>
            <Text style={[s.tagText, { color: colors.muted }]}>{ex.equipment}</Text>
          </View>
        ) : null}
        {(ex.difficulty || ex.level) ? (
          <View style={[s.tag, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
            <Text style={[s.tagText, { color: colors.accent }]}>{ex.difficulty || ex.level}</Text>
          </View>
        ) : null}
      </View>
    </TouchableOpacity>
  ), [colors, favoriteSet, navigation, onLongPressCustom, toggleFavorite]);

  const FilterChip = ({ label, active, onPress }) => (
    <TouchableOpacity
      style={[s.chip, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
      onPress={onPress}
    >
      <Text style={[s.chipText, { color: active ? colors.accent : colors.muted }]}>{label}</Text>
    </TouchableOpacity>
  );

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      {/* Scope chips — above search pill */}
      <View style={[s.filterRow, { borderBottomColor: colors.faint }]}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingHorizontal: 16, alignItems: 'center' }}>
          <TouchableOpacity
            style={[s.chip, {
              borderColor: scope === 'favorites' ? colors.accent : colors.faint,
              backgroundColor: scope === 'favorites' ? colors.accentSoft : 'transparent',
              flexDirection: 'row', alignItems: 'center', gap: 6,
            }]}
            onPress={() => setScope(s => s === 'favorites' ? 'all' : 'favorites')}
          >
            <Ionicons name={scope === 'favorites' ? 'star' : 'star-outline'} size={12} color={scope === 'favorites' ? colors.accent : colors.muted} />
            <Text style={[s.chipText, { color: scope === 'favorites' ? colors.accent : colors.muted }]}>Favorites</Text>
          </TouchableOpacity>
          {/* Category chips */}
          {categories.map(c => (
            <FilterChip key={c} label={c} active={cat === c} onPress={() => setCat(prev => prev === c ? null : c)} />
          ))}
        </ScrollView>
      </View>

      {/* Muscle filter chips — above search pill */}
      <View style={[s.filterRow, { borderBottomColor: colors.faint }]}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingHorizontal: 16, alignItems: 'center' }}>
          {muscles.map(m => (
            <FilterChip key={m} label={m} active={muscle === m} onPress={() => setMuscle(prev => prev === m ? null : m)} />
          ))}
        </ScrollView>
      </View>

      {/* Equipment filter chips */}
      {equipmentOptions.length > 0 && (
        <View style={[s.filterRow, { borderBottomColor: colors.faint }]}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingHorizontal: 16, alignItems: 'center' }}>
            {equipmentOptions.map(e => (
              <FilterChip key={e} label={e} active={equip === e} onPress={() => setEquip(prev => prev === e ? null : e)} />
            ))}
          </ScrollView>
        </View>
      )}

      {/* Movement pattern / difficulty / bodyweight filter row */}
      {(movementOptions.length > 0 || difficultyOptions.length > 0) && (
        <View style={[s.filterRow, { borderBottomColor: colors.faint }]}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingHorizontal: 16, alignItems: 'center' }}>
            <TouchableOpacity
              style={[s.chip, {
                borderColor: bwOnly ? colors.accent : colors.faint,
                backgroundColor: bwOnly ? colors.accentSoft : 'transparent',
                flexDirection: 'row', alignItems: 'center', gap: 5,
              }]}
              onPress={() => setBwOnly(prev => !prev)}
            >
              <Text style={[s.chipText, { color: bwOnly ? colors.accent : colors.muted }]}>BW Only</Text>
            </TouchableOpacity>
            {movementOptions.map(m => (
              <FilterChip key={m} label={m} active={movement === m} onPress={() => setMovement(prev => prev === m ? null : m)} />
            ))}
            {difficultyOptions.map(d => (
              <FilterChip key={d} label={d} active={difficulty === d} onPress={() => setDifficulty(prev => prev === d ? null : d)} />
            ))}
          </ScrollView>
        </View>
      )}

      {/* Result count */}
      <Text style={[s.count, { color: colors.muted }]}>{filtered.length} exercises</Text>

      <View style={s.contentArea}>
        {filtered.length === 0 ? (
          <View
            style={[
              s.emptyWrap,
              emptyLift ? { transform: [{ translateY: emptyLift }] } : null,
            ]}>
            <Ionicons name="search-outline" size={24} color={colors.muted} />
            <Text style={[s.emptyTitle, { color: colors.text }]}>No exercises found</Text>
            <Text style={[s.emptyHint, { color: colors.muted }]}>Try a different filter or search term.</Text>
            {missingExerciseSeed ? (
              <View style={s.emptyActionRow}>
                <TouchableOpacity
                  style={[s.emptyActionBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
                  onPress={() => navigation.navigate('CreateExercise', { initialName: missingExerciseSeed })}
                >
                  <Text style={[s.emptyActionText, { color: colors.accent }]}>+ Add Exercise</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[s.emptyActionBtn, { borderColor: colors.faint, backgroundColor: 'transparent' }]}
                  onPress={() => navigation.navigate('CreateExercise', { initialName: missingExerciseSeed, quickAdd: true })}
                >
                  <Text style={[s.emptyActionText, { color: colors.muted }]}>+ Quick Add</Text>
                </TouchableOpacity>
              </View>
            ) : null}
          </View>
        ) : (
          <FlashList
            data={filtered}
            keyExtractor={(item) => item.id}
            renderItem={renderItem}
            estimatedItemSize={ROW_HEIGHT}
            contentContainerStyle={{ paddingBottom: listEndPadding }}
            scrollIndicatorInsets={{ bottom: listEndPadding }}
            ListFooterComponent={
              normalizedSeed && !hasExactNameMatch ? (
                <View style={s.inlineCreateFooter}>
                  <Text style={[s.inlineCreateHint, { color: colors.muted }]}>{"Can't find it?"}</Text>
                  <View style={s.inlineCreateActions}>
                    <TouchableOpacity
                      style={[s.inlineCreateBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
                      onPress={() => navigation.navigate('CreateExercise', { initialName: normalizedSeed })}
                    >
                      <Text style={[s.inlineCreateBtnText, { color: colors.accent }]}>+ Add Exercise</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[s.inlineCreateBtn, { borderColor: colors.faint, backgroundColor: 'transparent' }]}
                      onPress={() => navigation.navigate('CreateExercise', { initialName: normalizedSeed, quickAdd: true })}
                    >
                      <Text style={[s.inlineCreateBtnText, { color: colors.muted }]}>+ Quick Add</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              ) : null
            }
          />
        )}
      </View>

      <View style={[s.searchDock, { paddingBottom: searchDockBottom }]}>
        <View style={[s.searchShell, { borderColor: colors.cardBorder || colors.faint, shadowColor: colors.text, backgroundColor: colors.surface }]}>
        <TouchableOpacity activeOpacity={1} onPress={() => searchInputRef.current?.focus()} style={[s.searchPill, { backgroundColor: colors.surface, borderColor: colors.faint }]}>
          <Ionicons name="search-outline" size={18} color={colors.muted} style={{ marginRight: 10 }} />
          <TextInput
            ref={searchInputRef}
            style={[s.input, { color: colors.text }]}
            defaultValue=""
            onChangeText={setSearch}
            placeholder="Search exercises..."
            placeholderTextColor={colors.muted}
            returnKeyType="search"
            autoCorrect={false}
            autoCapitalize="none"
          />
        </TouchableOpacity>
        </View>
      </View>

      {/* FAB */}
      {keyboardInset === 0 ? (
        <TouchableOpacity
          style={[s.fab, { backgroundColor: colors.accent, bottom: searchDockBottom + 74 }]}
          onPress={() => navigation.navigate('CreateExercise')}
          activeOpacity={0.85}
        >
          <Ionicons name="add" size={28} color="#fff" />
        </TouchableOpacity>
      ) : null}

      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  contentArea: { flex: 1 },
  searchShell: {
    borderRadius: 999,
    borderWidth: 1,
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.12,
    shadowRadius: 14,
    elevation: 10,
  },
  searchPill: {
    flexDirection: 'row', alignItems: 'center',
    minHeight: 54,
    paddingHorizontal: 16, paddingVertical: 13,
    borderRadius: 999, borderWidth: 1,
  },
  input: { flex: 1, fontSize: 15, padding: 0 },
  filterRow: { paddingVertical: 8, borderBottomWidth: 1, minHeight: 48 },
  chip: { height: 34, paddingHorizontal: 15, justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderRadius: 999 },
  chipText: { fontSize: 11, fontWeight: '700', letterSpacing: 0.5 },
  count: { fontSize: 10, letterSpacing: 2.2, padding: 12, paddingHorizontal: 16, fontWeight: '700' },
  row: { flexDirection: 'row', paddingHorizontal: 16, paddingVertical: 16, borderBottomWidth: 1, alignItems: 'center', minHeight: ROW_HEIGHT },
  name: { fontSize: 15, fontWeight: '800', flexShrink: 1 },
  sub: { fontSize: 12, marginTop: 3 },
  tag: { paddingHorizontal: 9, paddingVertical: 4, borderWidth: 1, borderRadius: 999 },
  tagText: { fontSize: 10, fontWeight: '600' },
  customMenuBtn: { flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderRadius: 999, paddingHorizontal: 8, paddingVertical: 5 },
  customBadge: { paddingHorizontal: 8, paddingVertical: 3, borderWidth: 1, borderRadius: 999 },
  customBadgeText: { fontSize: 9, fontWeight: '800', letterSpacing: 1 },
  emptyWrap: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 28, gap: 8 },
  emptyTitle: { fontSize: 16, fontWeight: '800' },
  emptyHint: { fontSize: 12, textAlign: 'center', lineHeight: 18 },
  emptyActionRow: { marginTop: 8, flexDirection: 'row', gap: 8, flexWrap: 'wrap', justifyContent: 'center' },
  emptyActionBtn: { marginTop: 8, borderWidth: 1, paddingHorizontal: 14, paddingVertical: 9, borderRadius: 999 },
  emptyActionText: { fontSize: 11, fontWeight: '800', letterSpacing: 1.2 },
  inlineCreateFooter: { paddingTop: 14, paddingBottom: 8, alignItems: 'center', gap: 10 },
  inlineCreateHint: { fontSize: 11, letterSpacing: 0.4 },
  inlineCreateActions: { flexDirection: 'row', gap: 8, flexWrap: 'wrap', justifyContent: 'center' },
  inlineCreateBtn: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8 },
  inlineCreateBtnText: { fontSize: 10, fontWeight: '800', letterSpacing: 1 },
  missingRow: {
    marginHorizontal: 16,
    marginBottom: 10,
    marginTop: 2,
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
  },
  missingTitle: { fontSize: 12, fontWeight: '800', letterSpacing: 0.5, marginBottom: 4 },
  missingHint: { fontSize: 11, lineHeight: 16 },
  missingBtn: {
    borderWidth: 1,
    borderRadius: CONTROL_RADIUS,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  missingBtnText: { fontSize: 10, fontWeight: '800', letterSpacing: 1.1 },
  fab: { position: 'absolute', right: 20, width: 56, height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center', elevation: 6 },
  searchDock: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: 14,
    paddingTop: 8,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 6,
  },
});
