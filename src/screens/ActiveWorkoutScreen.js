
import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import {
  View, Text, ScrollView, TextInput, FlatList,
  StyleSheet, Modal, AppState, Animated, Easing,
  TouchableOpacity, Linking, Keyboard, InteractionManager,
} from 'react-native';
import { KeyboardToolbar } from 'react-native-keyboard-controller';
import { BottomSheetModal, BottomSheetView, BottomSheetBackdrop, BottomSheetFlatList } from '@gorhom/bottom-sheet';

import { TouchableOpacity as RNGHTouchableOpacity, ScrollView as RNGHScrollView } from 'react-native-gesture-handler';
import DraggableFlatList, { ScaleDecorator } from 'react-native-draggable-flatlist';
import Svg, { Circle } from 'react-native-svg';
import ReAnimated, {
  useSharedValue,
  withTiming,
  withRepeat,
  withSequence,
  withSpring,
  useAnimatedStyle,
  runOnJS,
  Easing as ReaEasing,
} from 'react-native-reanimated';
// Reanimated v4: Easing.cubic/sine are not available � use bezier equivalents
const EASE_OUT_CUBIC = ReaEasing.bezier(0.33, 1, 0.68, 1);
const EASE_INOUT_SINE = ReaEasing.bezier(0.45, 0, 0.55, 1);

import Ionicons from 'react-native-vector-icons/Ionicons';
import { useKeepAwake } from '../platform/keepAwake';
import * as Sharing from '../platform/sharing';
import { captureRef } from 'react-native-view-shot';
import { useFocusEffect } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import useWatermelonAppData from '../hooks/useWatermelonAppData';
import { useTheme } from '../context/ThemeContext';
import { WorkoutProvider, useWorkout } from '../context/WorkoutContext';
import { useActiveBanner } from '../context/ActiveWorkoutBannerContext';
import useDeferredScreenReady from '../hooks/useDeferredScreenReady';
import SetRow from '../components/SetRow';
import CustomAlert from '../components/CustomAlert';
import WorkoutShareCard from '../components/WorkoutShareCard';
import GoldConfettiBurst from '../components/GoldConfettiBurst';
import PRHud from '../components/PRHud';
import InlineToast from '../components/ui/InlineToast';
import useKeyboardInset from '../hooks/useKeyboardInset';
import { calcPlates } from '../utils/plateCalc';
import { epley } from '../utils/oneRM';
import { getExerciseIndex, saveCustomExercise } from '../services/ExerciseLibraryService';
import { EXERCISES } from '../data/exerciseLibrary';
import { EXERCISE_ID_MAP } from '../data/exerciseMapping';
import { buildProgressionSuggestion } from '../domain/intelligence/progressionEngine';
import { buildWorkoutCompletionSummary } from '../domain/intelligence/trainingAnalyticsEngine';
import { rankSubstitutionCandidates } from '../domain/intelligence/substitutionEngine';
import { buildVolumeInterpretation } from '../domain/intelligence/volumeInterpretationEngine';
import {
  computeWorkoutPerformanceScore,
  detectPREvents,
} from '../domain/intelligence/performanceEngine';
import {
  getSetting as getWatermelonSetting,
  setSetting as setWatermelonSetting,
} from '../db/repositories/settingsRepository';
import { fireHaptic } from '../services/hapticsEngine';
import { generateWarmupSets } from '../services/TrainingIntelligenceService';
import { resolveExerciseYoutubeMeta } from '../utils/exerciseVideoLinks';
import {
  buildFilterChipOptions,
  getExerciseFilterSummary,
  matchesExerciseFilter,
} from '../utils/exerciseFilters';
import { buildExerciseSearchIndex, queryExerciseSearch } from '../services/exerciseSearchAdapter';
import { resolveExerciseProfile } from '../domain/intelligence/exerciseProfileEngine';
import { formatVolumeFromKg, formatWeightFromKg, convertKgToUnit } from '../utils/weightUnits';
import { showOngoingWorkoutNotification, clearOngoingWorkoutNotification } from '../services/notificationScheduler';
// toastService not needed here � PRHud handles PR notifications, InlineToast handles set feedback
import {
  getFavoriteExerciseIds,
  setFavoriteExerciseIds,
} from '../services/favoriteExercisesService';
import { resolveCelebrationConfig } from '../utils/celebration';
import { withAlpha } from '../utils/colorUtils';
import { RADIUS } from '../utils/themes';
import { getBottomOverlaySpacing } from '../utils/bottomOverlaySpacing';
import {
  getActiveSession,
  setActiveSession,
  clearActiveSession,
  getLastPerformanceIndex,
  getNextTargetsIndex,
  setNextTargetsIndex,
} from '../services/workoutSessionStore';

function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 6); }

const ACTIVE_WORKOUT_SESSION_PREFIX = '@ironlog/activeWorkoutSession/';
const COMPARISON_USAGE_KEY = 'comparison_usage_v1';
const CARD_RADIUS = RADIUS.lg;
const CONTROL_RADIUS = RADIUS.md;
const SHEET_RADIUS = RADIUS.xl;

async function getRecentComparisonUsage() {
  const usage = await getWatermelonSetting(COMPARISON_USAGE_KEY);
  return Array.isArray(usage) ? usage : [];
}

async function recordComparisonUsage(item) {
  const current = await getRecentComparisonUsage();
  const next = [item, ...current].slice(0, 30);
  await setWatermelonSetting(COMPARISON_USAGE_KEY, next, 'json');
}

function toTitleCase(value) {
  return String(value || '')
    .replace(/[_-]+/g, ' ')
    .trim()
    .split(' ')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
}

function getExerciseMuscles(exercise) {
  return getExerciseFilterSummary(exercise, 6);
}

function getPlateText(targetKg, barWeight, profilePlates, weightUnit = 'kg') {
  const perSide = (targetKg - barWeight) / 2;
  if (perSide <= 0) return 'Bar only';
  const plates = profilePlates?.length
    ? profilePlates.map(p => p.weight).sort((a, b) => b - a)
    : [20, 15, 10, 5, 2.5, 1.25];
  const result = [];
  let rem = Math.round(perSide * 100) / 100;
  for (const p of plates) {
    while (rem >= p - 0.001) { result.push(p); rem = Math.round((rem - p) * 100) / 100; }
  }
  if (result.length === 0) return 'Bar only';
  return result.map(p => formatWeightFromKg(p, weightUnit)).join(' + ') + ' each side';
}

function parseRepTarget(value, fallback = 8) {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  const match = String(value || '').match(/\d+/);
  return match ? Number(match[0]) : fallback;
}

function normalizeExerciseLookupKey(value) {
  return String(value || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

function isTimeBasedExercise(exercise = {}) {
  return String(exercise?.trackingType || '').startsWith('duration');
}

function isBodyweightExercise(exercise = {}) {
  // v2 library: use authoritative field when present
  if (typeof exercise?.isBodyweight === 'boolean') return exercise.isBodyweight;
  // Fallback for custom exercises and legacy records without the field
  const equip = String(exercise?.equipment || '').toLowerCase();
  if (equip.includes('body weight') || equip.includes('bodyweight') || equip === 'body only') return true;
  const name = String(exercise?.name || '').toLowerCase();
  return /pull.?up|chin.?up|push.?up|\bdip\b|plank|crunch|sit.?up|leg raise|mountain climber|muscle.?up|handstand|pistol|nordic/.test(name);
}

function formatDurationShort(secondsValue) {
  const totalSeconds = Math.max(0, Math.round(Number(secondsValue) || 0));
  const mm = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
  const ss = String(totalSeconds % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

function normalizeSessionExercise(exercise = {}) {
  const trackingType = exercise.trackingType || 'weight_reps';
  return {
    ...exercise,
    name: exercise.name || 'Custom Exercise',
    exerciseId: exercise.exerciseId || exercise.id || exercise.name || genId(),
    sets: Number(exercise.sets || 3),
    reps: isTimeBasedExercise({ trackingType })
      ? parseRepTarget(exercise.reps, 60)
      : parseRepTarget(exercise.reps, 8),
    trackingType,
    isWarmup: !!exercise.isWarmup,
  };
}

function supportsPlateBreakdown(exercise) {
  const profile = resolveExerciseProfile(exercise || {});
  return profile?.equipmentClass === 'barbell';
}

const RATE_THRESHOLDS = [10, 25, 50];

const FUN_COMPARISONS = [
  { threshold: 0, text: 'a house cat', icon: '??' },
  { threshold: 500, text: 'a baby goat', icon: '??' },
  { threshold: 1000, text: 'a large pumpkin', icon: '??' },
  { threshold: 2000, text: 'a baby elephant', icon: '??' },
  { threshold: 3500, text: 'a baby hippo', icon: '??' },
  { threshold: 5000, text: 'a grand piano', icon: '??' },
  { threshold: 7500, text: 'a polar bear', icon: '??' },
  { threshold: 10000, text: 'a small car', icon: '??' },
  { threshold: 15000, text: 'a T-Rex', icon: '??' },
  { threshold: 20000, text: 'a rhino', icon: '??' },
  { threshold: 25000, text: 'an orca whale', icon: '??' },
  { threshold: 35000, text: 'an elephant', icon: '??' },
  { threshold: 40000, text: 'a school bus', icon: '??' },
  { threshold: 60000, text: 'a space shuttle', icon: '??' },
  { threshold: 100000, text: 'a blue whale', icon: '??' },
];

function getFunComparison(totalKg) {
  let match = FUN_COMPARISONS[0];
  for (const c of FUN_COMPARISONS) {
    if (totalKg >= c.threshold) match = c;
    else break;
  }
  return match;
}

//  REST TIMER CIRCLE 

function RestTimerCircle({ seconds, total, paused, onSkip, onAdd30, onPause, onResume, accent }) {
  const colors = useTheme();
  const R = 54;
  const CIRC = 2 * Math.PI * R;
  const frac = Math.max(0, seconds / Math.max(total, 1));
  const dashOffset = (1 - frac) * CIRC;
  const mm = String(Math.floor(seconds / 60)).padStart(2, '0');
  const ss = String(seconds % 60).padStart(2, '0');
  const col = withAlpha(accent || colors.accent || '#FF4500', 1, '#FF4500').slice(0, 7);
  const trackColor = withAlpha(colors.faint || '#2a2a2a', 1, '#2a2a2a').slice(0, 7);
  const timerTextColor = paused
    ? withAlpha(colors.muted || '#888', 1, '#888888').slice(0, 7)
    : withAlpha(colors.text || '#f0f0f0', 1, '#F0F0F0').slice(0, 7);
  const pausedTextColor = withAlpha(colors.muted || '#777', 1, '#777777').slice(0, 7);

  // Subtle pulse on the time text when timer is running
  const pulseScale = useSharedValue(1);
  useEffect(() => {
    if (paused) {
      pulseScale.value = withTiming(1, { duration: 200 });
    } else {
      pulseScale.value = withRepeat(
        withSequence(
          withTiming(1.07, { duration: 1100, easing: EASE_INOUT_SINE }),
          withTiming(1.0,  { duration: 1100, easing: EASE_INOUT_SINE }),
        ),
        -1,
        true,
      );
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paused]);

  const pulseStyle = useAnimatedStyle(() => ({
    transform: [{ scale: pulseScale.value }],
  }));

  return (
    <View style={{ alignItems: 'center', paddingVertical: 16 }}>
      <View style={{ width: 130, height: 130 }}>
        <Svg width={130} height={130} style={{ position: 'absolute' }}>
          {/* Track ring */}
          <Circle cx={65} cy={65} r={R} stroke={trackColor} strokeWidth={6} fill="none" />
          {/* Progress arc */}
          <Circle
            cx={65} cy={65} r={R}
            stroke={paused ? pausedTextColor : col}
            strokeWidth={6}
            fill="none"
            strokeDasharray={CIRC}
            strokeDashoffset={dashOffset}
            strokeLinecap="round"
            rotation={-90}
            originX={65}
            originY={65}
          />
        </Svg>
        <View style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' }}>
          <ReAnimated.View style={pulseStyle}>
            <Text style={{ fontSize: 28, fontWeight: '900', color: timerTextColor, letterSpacing: 1 }}>
              {mm}:{ss}
            </Text>
          </ReAnimated.View>
          {paused ? <Text style={{ fontSize: 9, letterSpacing: 3, color: pausedTextColor, marginTop: 2 }}>PAUSED</Text> : null}
        </View>
      </View>
      <View style={{ flexDirection: 'row', gap: 10, marginTop: 8 }}>
        <TouchableOpacity style={[rt.btn, { borderColor: trackColor, backgroundColor: colors.card }]} onPress={onAdd30}>
          <Text style={[rt.btnText, { color: colors.subtext || '#888' }]}>+30s</Text>
        </TouchableOpacity>
        {paused ? (
          <TouchableOpacity style={[rt.btn, { backgroundColor: col, borderColor: col }]} onPress={onResume}>
            <Text style={[rt.btnText, { color: colors.textOnAccent || '#fff' }]}>RESUME</Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity style={[rt.btn, { borderColor: trackColor, backgroundColor: colors.card }]} onPress={onPause}>
            <Text style={[rt.btnText, { color: colors.subtext || '#888' }]}>PAUSE</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity style={[rt.btn, { backgroundColor: col, borderColor: col }]} onPress={onSkip}>
          <Text style={[rt.btnText, { color: colors.textOnAccent || '#fff' }]}>SKIP</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}
const rt = StyleSheet.create({
  btn: { minWidth: 82, paddingHorizontal: 14, paddingVertical: 10, borderWidth: 1, borderColor: '#333', borderRadius: CONTROL_RADIUS, alignItems: 'center' },
  btnText: { fontSize: 11, fontWeight: '700', color: '#888', letterSpacing: 0.8 },
});

// Isolated rest-timer panel � owns its own displaySecs state so the 500ms
// tick only re-renders this component, not the entire WorkoutContent tree.
const RestTimerPanel = React.memo(function RestTimerPanel({
  restTimer, accent, cardBg, faint, mutedColor,
  restPanelStyle, restOverlayAnimStyle, restOverlayStyle, restLabelStyle,
  onSkip, onPause, onResume, onAdd30,
  onTick, onTimerDone,
  syncKey,
}) {
  const initSecs = () => {
    if (!restTimer.active) return 0;
    if (restTimer.paused) return Math.max(0, Math.ceil(((restTimer.endTime || 0) - (restTimer.pausedAt || 0)) / 1000));
    return Math.max(0, Math.ceil((restTimer.endTime - Date.now()) / 1000));
  };
  const [displaySecs, setDisplaySecs] = useState(initSecs);
  const intervalRef = useRef(null);

  // Re-sync whenever restTimer shape or foreground-restore syncKey changes
  useEffect(() => {
    clearInterval(intervalRef.current);
    if (!restTimer.active) { setDisplaySecs(0); return; }
    if (restTimer.paused) {
      setDisplaySecs(Math.max(0, Math.ceil(((restTimer.endTime || 0) - (restTimer.pausedAt || 0)) / 1000)));
      return;
    }
    if (!restTimer.endTime) { setDisplaySecs(0); return; }
    const tick = () => {
      const r = Math.ceil((restTimer.endTime - Date.now()) / 1000);
      if (r <= 0) {
        clearInterval(intervalRef.current);
        setDisplaySecs(0);
        onTimerDone?.();
      } else {
        onTick?.(r);
        setDisplaySecs(r);
      }
    };
    tick(); // immediate first tick
    intervalRef.current = setInterval(tick, 500);
    return () => clearInterval(intervalRef.current);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restTimer.active, restTimer.paused, restTimer.endTime, restTimer.pausedAt, syncKey]);

  const handleResumeInternal = useCallback(() => {
    onResume(displaySecs);
  }, [onResume, displaySecs]);

  const handleAdd30Internal = useCallback(() => {
    onAdd30();
    if (!restTimer.paused) setDisplaySecs(s => s + 30);
  }, [onAdd30, restTimer.paused]);

  const seconds = restTimer.paused
    ? Math.max(0, Math.ceil(((restTimer.endTime || 0) - (restTimer.pausedAt || 0)) / 1000))
    : displaySecs;

  return (
    <ReAnimated.View style={[restOverlayAnimStyle, restPanelStyle]}>
      <View style={[restOverlayStyle, { backgroundColor: cardBg, borderBottomColor: faint }]}>
        <Text style={[restLabelStyle, { color: mutedColor }]}>REST</Text>
        <RestTimerCircle
          seconds={seconds}
          total={restTimer.total}
          paused={restTimer.paused}
          accent={accent}
          onSkip={onSkip}
          onAdd30={handleAdd30Internal}
          onPause={onPause}
          onResume={handleResumeInternal}
        />
      </View>
    </ReAnimated.View>
  );
});

//  PLATE MODAL 

function PlateModal({ visible, targetKg, barWeight, onClose, weightUnit = 'kg' }) {
  const colors = useTheme();
  const sheetRef = useRef(null);
  const result = calcPlates(targetKg, barWeight);

  useEffect(() => {
    if (visible) requestAnimationFrame(() => sheetRef.current?.present());
    else sheetRef.current?.dismiss();
  }, [visible]);

  return (
    <BottomSheetModal
      ref={sheetRef}
      snapPoints={['42%', '66%']}
      index={0}
      enableDynamicSizing={false}
      enablePanDownToClose
      onDismiss={onClose}
      backgroundStyle={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.cardBorder }}
      handleIndicatorStyle={{ backgroundColor: colors.muted, width: 36, height: 4, borderRadius: 2, opacity: 0.5 }}
      backdropComponent={(props) => (
        <BottomSheetBackdrop {...props} appearsOnIndex={0} disappearsOnIndex={-1} opacity={0.6} />
      )}
    >
      <BottomSheetView style={{ paddingHorizontal: 24, paddingTop: 8, paddingBottom: 32 }}>
        <Text style={{ fontSize: 11, letterSpacing: 4, color: colors.muted, marginBottom: 4, fontWeight: '700' }}>
          PLATE CALCULATOR
        </Text>
        <Text style={{ fontSize: 28, fontWeight: '900', color: colors.text, marginBottom: 16 }}>
          {formatWeightFromKg(targetKg, weightUnit)} total
        </Text>
        {result.valid ? (
          <>
            <Text style={{ fontSize: 10, letterSpacing: 3, color: colors.muted, marginBottom: 8, fontWeight: '700' }}>
              EACH SIDE
            </Text>
            {result.each.length === 0
              ? <Text style={{ color: colors.muted, marginBottom: 8 }}>Bar only ({formatWeightFromKg(barWeight, weightUnit)})</Text>
              : result.each.map((p, i) => (
                  <View key={i} style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8, gap: 12 }}>
                    <View style={{ height: 24, width: 20 + p.kg * 2, backgroundColor: colors.accent, borderRadius: 2 }} />
                    <Text style={{ fontSize: 16, fontWeight: '700', color: colors.text }}>
                      {formatWeightFromKg(p.kg, weightUnit)} x {p.count}
                    </Text>
                  </View>
                ))}
            <Text style={{ fontSize: 11, color: colors.muted, marginTop: 12 }}>
              Bar: {formatWeightFromKg(barWeight, weightUnit)} - Total: {formatWeightFromKg(result.total, weightUnit)}
            </Text>
          </>
        ) : (
          <Text style={{ color: colors.accent }}>
            Cannot load {formatWeightFromKg(targetKg, weightUnit)} with available plates.
          </Text>
        )}
        <TouchableOpacity
          style={{ marginTop: 24, padding: 16, borderWidth: 1, borderColor: colors.faint,
            alignItems: 'center', borderRadius: CONTROL_RADIUS }}
          onPress={() => { fireHaptic('selection'); sheetRef.current?.dismiss(); }}
        >
          <Text style={{ color: colors.text, fontWeight: '700', letterSpacing: 2 }}>CLOSE</Text>
        </TouchableOpacity>
      </BottomSheetView>
    </BottomSheetModal>
  );
}

//  REST OVERRIDE MODAL � animated drum / barrel picker

const DRUM_ITEM_H = 54;
const DRUM_VISIBLE = 3; // items shown (centre = selected)

const DrumItem = React.memo(function DrumItem({ item, selectedValue, accent, muted }) {
  const active = item === selectedValue;
  const sc = useSharedValue(active ? 1 : 0.73);
  const op = useSharedValue(active ? 1 : 0.35);

  useEffect(() => {
    sc.value = withSpring(active ? 1 : 0.73, { stiffness: 300, damping: 22 });
    op.value = withTiming(active ? 1 : 0.35, { duration: 180 });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active]);

  const animStyle = useAnimatedStyle(() => ({
    opacity: op.value,
    transform: [{ scale: sc.value }],
  }));

  return (
    <View style={{ height: DRUM_ITEM_H, alignItems: 'center', justifyContent: 'center' }}>
      <ReAnimated.View style={animStyle}>
        <Text style={{
          fontSize: 30,
          fontWeight: '900',
          color: active ? accent : muted,
          letterSpacing: 0.5,
        }}>
          {String(item).padStart(2, '0')}
        </Text>
      </ReAnimated.View>
    </View>
  );
});

function DrumColumn({ data, selectedValue, listRef, onSelect, label, accent, muted, accentSoft, accentBorder }) {
  // Scroll to selected on open
  useEffect(() => {
    const idx = data.indexOf(selectedValue);
    if (idx >= 0) {
      const task = InteractionManager.runAfterInteractions(() => {
        listRef.current?.scrollToOffset({ offset: idx * DRUM_ITEM_H, animated: false });
      });
      return () => task.cancel();
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <View style={{ flex: 1, alignItems: 'center' }}>
      <Text style={{ fontSize: 9, letterSpacing: 2.5, color: muted, marginBottom: 6, fontWeight: '800' }}>{label}</Text>
      <View style={{ height: DRUM_ITEM_H * DRUM_VISIBLE, overflow: 'hidden' }}>
        {/* Selection highlight band */}
        <View pointerEvents="none" style={{
          position: 'absolute', top: DRUM_ITEM_H, left: 8, right: 8, height: DRUM_ITEM_H,
          backgroundColor: accentSoft, borderColor: accentBorder,
          borderTopWidth: 1, borderBottomWidth: 1, borderRadius: 10, zIndex: 1,
        }} />
        <FlatList
          ref={listRef}
          data={data}
          keyExtractor={(item) => String(item)}
          snapToInterval={DRUM_ITEM_H}
          decelerationRate="fast"
          showsVerticalScrollIndicator={false}
          scrollEventThrottle={16}
          contentContainerStyle={{ paddingVertical: DRUM_ITEM_H }}
          onMomentumScrollEnd={(e) => {
            const idx = Math.round(e.nativeEvent.contentOffset.y / DRUM_ITEM_H);
            const clamped = Math.max(0, Math.min(idx, data.length - 1));
            onSelect(data[clamped]);
            fireHaptic('selection');
          }}
          renderItem={({ item }) => (
            <DrumItem
              item={item}
              selectedValue={selectedValue}
              accent={accent}
              muted={muted}
            />
          )}
        />
      </View>
    </View>
  );
}

function RestOverrideModal({ visible, current, onSave, onClose, colors }) {
  const sheetRef = useRef(null);
  const snapPoints = useMemo(() => ['40%', '62%'], []);

  const initMins = Math.floor(current / 60);
  const initSecs = current % 60;
  const [mins, setMins] = useState(initMins);
  const [secs, setSecs] = useState(initSecs);
  const minsRef = useRef(null);
  const secsRef = useRef(null);

  // Reset values whenever the sheet opens with a new `current`
  useEffect(() => {
    if (visible) {
      const m = Math.floor(current / 60);
      const s = current % 60;
      setMins(m);
      setSecs(s);
      requestAnimationFrame(() => sheetRef.current?.present());
    } else {
      sheetRef.current?.dismiss();
    }
  }, [visible, current]);

  const minutesData = useMemo(() => Array.from({ length: 21 }, (_, i) => i), []); // 0�20 min
  const secondsData = useMemo(() => [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55], []);

  // Scroll to the closest second bucket when secs changes (preset buttons)
  const scrollToPreset = useCallback((totalSecs) => {
    const m = Math.floor(totalSecs / 60);
    const s = totalSecs % 60;
    const closestSec = secondsData.reduce((prev, cur) => Math.abs(cur - s) < Math.abs(prev - s) ? cur : prev, 0);
    setMins(m);
    setSecs(closestSec);
    minsRef.current?.scrollToOffset({ offset: m * DRUM_ITEM_H, animated: true });
    secsRef.current?.scrollToOffset({ offset: secondsData.indexOf(closestSec) * DRUM_ITEM_H, animated: true });
    fireHaptic('selection');
  }, [secondsData]);

  const accent     = colors?.accent     || '#FF4500';
  const muted      = colors?.muted      || '#888';
  const border     = colors?.faint      || '#333';
  const surface    = colors?.card       || '#0d0d0d';
  const cardBorder = colors?.cardBorder || '#222';
  const accentSoft   = colors?.accentSoft   || withAlpha(accent, 0.13);
  const accentBorder = colors?.accentBorder || withAlpha(accent, 0.27);

  const totalSecs = mins * 60 + secs;

  return (
    <BottomSheetModal
      ref={sheetRef}
      snapPoints={snapPoints}
      index={0}
      enableDynamicSizing={false}
      enablePanDownToClose
      onDismiss={onClose}
      backgroundStyle={{ backgroundColor: surface, borderWidth: 1, borderColor: cardBorder }}
      handleIndicatorStyle={{ backgroundColor: colors?.muted, width: 36, height: 4, borderRadius: 2, opacity: 0.5 }}
      backdropComponent={(props) => (
        <BottomSheetBackdrop {...props} appearsOnIndex={0} disappearsOnIndex={-1} opacity={0.6} />
      )}
    >
      <BottomSheetView style={{ paddingHorizontal: 20, paddingTop: 8, paddingBottom: 24 }}>
        <Text style={{ fontSize: 11, letterSpacing: 4, color: muted, marginBottom: 16, fontWeight: '700', textAlign: 'center' }}>
          REST TIME
        </Text>

        {/* Drum columns */}
        <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 18 }}>
          <DrumColumn
            data={minutesData}
            selectedValue={mins}
            listRef={minsRef}
            onSelect={setMins}
            label="MIN"
            accent={accent} muted={muted}
            accentSoft={accentSoft} accentBorder={accentBorder}
          />
          <Text style={{ fontSize: 32, fontWeight: '900', color: muted, paddingTop: 18, paddingHorizontal: 4, opacity: 0.5 }}>:</Text>
          <DrumColumn
            data={secondsData}
            selectedValue={secs}
            listRef={secsRef}
            onSelect={setSecs}
            label="SEC"
            accent={accent} muted={muted}
            accentSoft={accentSoft} accentBorder={accentBorder}
          />
        </View>

        {/* Quick presets */}
        <View style={{ flexDirection: 'row', gap: 8, marginBottom: 16 }}>
          {[30, 60, 90, 120, 180].map((preset) => (
            <TouchableOpacity
              key={preset}
              onPress={() => scrollToPreset(preset)}
              style={{ flex: 1, paddingVertical: 8, borderWidth: 1, borderColor: border, borderRadius: 999, alignItems: 'center' }}
            >
              <Text style={{ fontSize: 11, color: muted, fontWeight: '700' }}>
                {preset < 60 ? `${preset}s` : `${preset / 60}m`}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Confirm */}
        <TouchableOpacity
          style={{ padding: 15, backgroundColor: accent, alignItems: 'center', borderRadius: CONTROL_RADIUS }}
          onPress={() => {
            fireHaptic('lightConfirm');
            if (totalSecs > 0) onSave(totalSecs);
            sheetRef.current?.dismiss();
          }}
        >
                    <Text style={{ color: colors.textOnAccent || '#fff', fontWeight: '800', fontSize: 14, letterSpacing: 2 }}>
            SET {String(mins).padStart(2, '0')}:{String(secs).padStart(2, '0')}
          </Text>
        </TouchableOpacity>
      </BottomSheetView>
    </BottomSheetModal>
  );
}

//  COPY PREVIOUS MODAL 

function CopyPreviousModal({ visible, onCopy, onFresh }) {
  const colors = useTheme();
  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', justifyContent: 'center',
        alignItems: 'center', padding: 32 }}>
        <View style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.cardBorder,
          padding: 28, width: '100%', borderRadius: CARD_RADIUS }}>
          <Text style={{ fontSize: 11, letterSpacing: 4, color: colors.muted, marginBottom: 8, fontWeight: '700' }}>
            LAST SESSION FOUND
          </Text>
          <Text style={{ fontSize: 18, fontWeight: '900', color: colors.text, marginBottom: 20 }}>
            Copy previous weights?
          </Text>
          <Text style={{ fontSize: 13, color: colors.muted, marginBottom: 24, lineHeight: 20 }}>
            Pre-fill your inputs with last session's weights and reps.
          </Text>
          <TouchableOpacity
            style={{ padding: 16, backgroundColor: colors.accent, alignItems: 'center',
              marginBottom: 10, borderRadius: CONTROL_RADIUS }}
            onPress={() => { fireHaptic('lightConfirm'); onCopy(); }}>
            <Text style={{ color: colors.textOnAccent, fontWeight: '800', letterSpacing: 2 }}>COPY PREVIOUS</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={{ padding: 14, borderWidth: 1, borderColor: colors.faint,
              alignItems: 'center', borderRadius: CONTROL_RADIUS }}
            onPress={() => { fireHaptic('selection'); onFresh(); }}>
            <Text style={{ color: colors.muted, fontWeight: '700', letterSpacing: 1 }}>START FRESH</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

//  SUPERSET ASSIGN MODAL 

function SupersetModal({ visible, currentGroup, onAssign, onClose }) {
  const colors = useTheme();
  const GROUP_COLORS = {
    A: colors.accent,
    B: withAlpha(colors.accent, 0.65),
    C: withAlpha(colors.accent, 0.40),
  };
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', justifyContent: 'center',
        alignItems: 'center', padding: 32 }}>
        <View style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.cardBorder,
          padding: 24, width: '100%', borderRadius: CARD_RADIUS }}>
          <Text style={{ fontSize: 11, letterSpacing: 4, color: colors.muted, marginBottom: 20, fontWeight: '700' }}>
            ASSIGN TO SUPERSET
          </Text>
          <View style={{ flexDirection: 'row', gap: 12, marginBottom: 16 }}>
            {['A', 'B', 'C'].map(g => (
              <TouchableOpacity
                key={g}
                style={{ flex: 1, padding: 16, borderWidth: 2, borderColor: GROUP_COLORS[g],
                  alignItems: 'center', borderRadius: CONTROL_RADIUS,
                  backgroundColor: currentGroup === g ? withAlpha(GROUP_COLORS[g], 0.2) : 'transparent' }}
                onPress={() => { fireHaptic('selection'); onAssign(g); onClose(); }}>
                <Text style={{ fontSize: 22, fontWeight: '900', color: GROUP_COLORS[g] }}>{g}</Text>
              </TouchableOpacity>
            ))}
          </View>
          {currentGroup ? (
            <TouchableOpacity
              style={{ padding: 12, borderWidth: 1, borderColor: colors.faint,
                alignItems: 'center', marginBottom: 10, borderRadius: CONTROL_RADIUS }}
              onPress={() => { fireHaptic('selection'); onAssign(null); onClose(); }}>
              <Text style={{ color: colors.muted, letterSpacing: 1 }}>Remove from Superset</Text>
            </TouchableOpacity>
          ) : null}
          <TouchableOpacity style={{ padding: 12, alignItems: 'center' }}
            onPress={() => { fireHaptic('selection'); onClose(); }}>
            <Text style={{ color: colors.muted }}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

//  SWAP EXERCISE MODAL 

// Broad groups shown when quick-adding without a muscle filter selected.
// These map exactly to the 6 intelligence volume buckets.
const QUICK_ADD_MUSCLE_GROUPS = ['Chest', 'Back', 'Legs', 'Shoulders', 'Arms', 'Core'];

function SwapModal({ visible, exercise, onSwap, onClose, colors, mode = 'swap', onCreateExercise, onQuickAdd }) {
  const isLibraryMode = mode === 'library';
  const sheetRef = useRef(null);
  const swapSearchInputRef = useRef(null);
  const snapPoints = useMemo(() => ['88%'], []);
  const [allExercises, setAllExercises] = useState([]);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [muscle, setMuscle] = useState(null);
  const [muscles, setMuscles] = useState([]);
  const [scope, setScope] = useState('all');
  const [favoriteIds, setFavoriteIdsState] = useState([]);
  // Quick-add muscle picker: holds the pending exercise name when we need a muscle before saving.
  const [quickAddPending, setQuickAddPending] = useState(null);

  useEffect(() => {
    if (!visible) return;
    setSearch('');
    setDebouncedSearch('');
    setQuickAddPending(null);
    setTimeout(() => swapSearchInputRef.current?.clear(), 0);
    setMuscle(isLibraryMode ? null : (getExerciseFilterSummary(exercise, 1)[0] || null));
    setScope('all');
    getExerciseIndex().then(idx => {
      if (!idx) return;
      setAllExercises(idx);
      setMuscles(buildFilterChipOptions(idx, {
        includeCategory: true,
        includeEquipment: isLibraryMode,
      }));
    });
    getFavoriteExerciseIds().then((ids) => setFavoriteIdsState(Array.isArray(ids) ? ids : []));
  }, [isLibraryMode, visible, exercise]);

  useEffect(() => {
    if (visible) {
      requestAnimationFrame(() => sheetRef.current?.present());
    } else {
      sheetRef.current?.dismiss();
    }
  }, [visible]);

  useEffect(() => {
    if (!visible) return;
    const timer = setTimeout(() => setDebouncedSearch(search), 170);
    return () => clearTimeout(timer);
  }, [search, visible]);

  // Shared search index (same config as library and plan editor)
  const searchIndex = useMemo(() => {
    return buildExerciseSearchIndex(allExercises);
  }, [allExercises]);

  const searchResultIds = useMemo(() => {
    return queryExerciseSearch({
      query: debouncedSearch,
      index: searchIndex,
      exercises: allExercises,
    });
  }, [debouncedSearch, searchIndex, allExercises]);
  const searchRankMap = searchResultIds?.rankMap || null;

  if (!visible) return null;

  const favoriteSet = new Set(favoriteIds);
  const currentEquipment = exercise?.equipment || '';
  const filteredBase = allExercises.filter((e) => {
    const ms = !muscle || matchesExerciseFilter(e, muscle, {
      includeCategory: true,
      includeEquipment: isLibraryMode,
    });
    const favoritesMatch = scope === 'favorites' ? favoriteSet.has(e.id) : true;
    const searchMatch = !searchResultIds || searchResultIds.has(e.id);
    return ms && favoritesMatch && searchMatch;
  });
  const filtered = isLibraryMode
    ? filteredBase
        .slice()
        .sort((a, b) => {
          if (searchRankMap) {
            const rankA = searchRankMap.has(a.id) ? searchRankMap.get(a.id) : Number.MAX_SAFE_INTEGER;
            const rankB = searchRankMap.has(b.id) ? searchRankMap.get(b.id) : Number.MAX_SAFE_INTEGER;
            if (rankA !== rankB) return rankA - rankB;
          }
          const favDiff = Number(favoriteSet.has(b.id)) - Number(favoriteSet.has(a.id));
          if (favDiff !== 0) return favDiff;
          return a.name.localeCompare(b.name);
        })
        .map((candidate) => ({
          ...candidate,
          isFavorite: favoriteSet.has(candidate.id),
          sameEquipment: candidate.equipment === currentEquipment,
        }))
    : rankSubstitutionCandidates({
        exercise,
        candidates: filteredBase,
        limit: filteredBase.length || 40,
      })
        .map((candidate, rankIndex) => ({
          ...candidate,
          rankIndex,
          isFavorite: favoriteSet.has(candidate.id),
          sameEquipment: candidate.equipment === currentEquipment,
        }))
        .sort((a, b) => {
          const favDiff = Number(b.isFavorite) - Number(a.isFavorite);
          if (favDiff !== 0) return favDiff;
          return a.rankIndex - b.rankIndex;
        });

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

  const pillChipStyle = (active) => ({
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: active ? colors.accent : colors.faint,
    backgroundColor: active ? colors.accentSoft : 'transparent',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  });

  const listHeader = (
    <View style={{ paddingHorizontal: 20, paddingTop: 20 }}>
      {/* Title row */}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
        <Text style={{ fontSize: 14, fontWeight: '900', letterSpacing: 3, color: colors.text }}>
          {isLibraryMode ? 'ADD FROM LIBRARY' : 'SWAP EXERCISE'}
        </Text>
        <TouchableOpacity onPress={() => { fireHaptic('selection'); onClose(); }}>
          <Ionicons name="close" size={22} color={colors.muted} />
        </TouchableOpacity>
      </View>

      {/* Search pill */}
      <View style={{
        flexDirection: 'row', alignItems: 'center',
        backgroundColor: colors.bg || colors.surface,
        borderRadius: 14,
        borderWidth: 1.5, borderColor: colors.cardBorder || colors.faint,
        paddingHorizontal: 14, paddingVertical: 10,
        marginBottom: 14,
        gap: 8,
      }}>
        <Ionicons name="search-outline" size={17} color={colors.accent} />
        <TextInput
          ref={swapSearchInputRef}
          style={{ flex: 1, color: colors.text, fontSize: 14, padding: 0, fontWeight: '500' }}
          placeholder="Search exercises..."
          placeholderTextColor={colors.muted}
          defaultValue=""
          onChangeText={setSearch}
          autoCorrect={false}
          autoCapitalize="none"
          returnKeyType="search"
        />
      </View>

      {/* Scope chips */}
      <View style={{ flexDirection: 'row', gap: 8, marginBottom: 8 }}>
        <TouchableOpacity
          onPress={() => { fireHaptic('selection'); setScope('all'); }}
          style={pillChipStyle(scope === 'all')}>
          <Text style={{ fontSize: 11, fontWeight: '700', color: scope === 'all' ? colors.accent : colors.muted }}>All</Text>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={() => { fireHaptic('selection'); setScope(s => s === 'favorites' ? 'all' : 'favorites'); }}
          style={pillChipStyle(scope === 'favorites')}>
          <Ionicons name={scope === 'favorites' ? 'star' : 'star-outline'} size={12} color={scope === 'favorites' ? colors.accent : colors.muted} />
          <Text style={{ fontSize: 11, fontWeight: '700', color: scope === 'favorites' ? colors.accent : colors.muted }}>Favorites</Text>
        </TouchableOpacity>
      </View>

      {/* Muscle chips � use RNGH ScrollView so horizontal swipe isn't captured by BottomSheetFlatList */}
      {muscles.length > 0 && (
        <View style={{ height: 40, marginBottom: 10 }}>
          <RNGHScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={{ gap: 7, paddingHorizontal: 2, alignItems: 'center' }}
          >
            {muscles.map(m => (
              <TouchableOpacity key={m}
                onPress={() => { fireHaptic('selection'); setMuscle(prev => prev === m ? null : m); }}
                style={pillChipStyle(muscle === m)}>
                <Text style={{ fontSize: 11, fontWeight: '600', color: muscle === m ? colors.accent : colors.muted }}>{m}</Text>
              </TouchableOpacity>
            ))}
          </RNGHScrollView>
        </View>
      )}

      <Text style={{ fontSize: 10, letterSpacing: 2, color: colors.muted, marginBottom: 4 }}>
        {filtered.length} exercises
      </Text>
    </View>
  );

  return (
    <BottomSheetModal
      ref={sheetRef}
      snapPoints={snapPoints}
      index={0}
      enablePanDownToClose
      keyboardBehavior="extend"
      android_keyboardInputMode="adjustResize"
      keyboardBlurBehavior="restore"
      onDismiss={onClose}
      backgroundStyle={{ backgroundColor: colors.card, borderTopLeftRadius: 24, borderTopRightRadius: 24, borderWidth: 1, borderColor: colors.cardBorder }}
      handleIndicatorStyle={{ backgroundColor: colors.muted, width: 36, height: 4, borderRadius: 2, opacity: 0.5 }}
      backdropComponent={(props) => (
        <BottomSheetBackdrop {...props} appearsOnIndex={0} disappearsOnIndex={-1} opacity={0.6} />
      )}
    >
      <BottomSheetFlatList
        data={filtered}
        keyExtractor={item => item.id}
        ListHeaderComponent={listHeader}
        contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 40 }}
        keyboardShouldPersistTaps="handled"
        renderItem={({ item: ex }) => (
          <TouchableOpacity
            style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: colors.faint }}
            onPress={() => { fireHaptic('lightConfirm'); onSwap(ex); onClose(); }}>
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: 14, fontWeight: '600', color: colors.text }} numberOfLines={1}>{ex.name}</Text>
              <Text style={{ fontSize: 11, color: colors.muted, marginTop: 2 }} numberOfLines={1}>
                {getExerciseFilterSummary(ex).join(', ')}{ex.equipment ? ` � ${toTitleCase(ex.equipment)}` : ''}
              </Text>
              {!isLibraryMode && ex.substitutionReason ? (
                <Text style={{ fontSize: 10, color: colors.accent, marginTop: 2 }} numberOfLines={1}>
                  {ex.substitutionReason}
                </Text>
              ) : null}
            </View>
            <View style={{ width: 64, flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: 12 }}>
              <TouchableOpacity
                onPress={(event) => { event?.stopPropagation?.(); fireHaptic('selection'); toggleFavorite(ex.id); }}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
                <Ionicons name={ex.isFavorite ? 'star' : 'star-outline'} size={17} color={ex.isFavorite ? colors.accent : colors.muted} />
              </TouchableOpacity>
              <Ionicons name={isLibraryMode ? 'add-circle-outline' : 'swap-horizontal'} size={18} color={colors.accent} />
            </View>
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          <View style={{ paddingVertical: 32, alignItems: 'center', gap: 12 }}>
            <Text style={{ fontSize: 11, color: colors.muted }}>No exercises match this search/filter.</Text>
            {search.trim() && onCreateExercise ? (
              <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap', justifyContent: 'center' }}>
                <TouchableOpacity
                  onPress={() => { fireHaptic('lightConfirm'); onCreateExercise(search.trim()); }}
                  style={{ paddingHorizontal: 20, paddingVertical: 10, borderRadius: 999, borderWidth: 1, borderColor: colors.accent, backgroundColor: colors.accentSoft }}>
                  <Text style={{ fontSize: 12, fontWeight: '800', color: colors.accent, letterSpacing: 1 }}>
                    + ADD EXERCISE
                  </Text>
                </TouchableOpacity>
                {onQuickAdd ? (
                  <TouchableOpacity
                    onPress={() => {
                      fireHaptic('lightConfirm');
                      if (muscle) { onQuickAdd(search.trim(), muscle); }
                      else { setQuickAddPending(search.trim()); }
                    }}
                    style={{ paddingHorizontal: 20, paddingVertical: 10, borderRadius: 999, borderWidth: 1, borderColor: colors.faint, backgroundColor: 'transparent' }}>
                    <Text style={{ fontSize: 12, fontWeight: '800', color: colors.muted, letterSpacing: 1 }}>
                      + QUICK ADD
                    </Text>
                  </TouchableOpacity>
                ) : null}
              </View>
            ) : null}

            {/* -- Quick-add muscle picker ----------------------------------- */}
            {quickAddPending != null ? (
              <View style={{ marginHorizontal: 16, marginTop: 12, padding: 14, borderRadius: 14, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.cardBorder }}>
                <Text style={{ fontSize: 11, fontWeight: '800', color: colors.muted, letterSpacing: 1, marginBottom: 10 }}>
                  WHICH MUSCLE GROUP DOES "{quickAddPending.toUpperCase()}" TARGET?
                </Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  {QUICK_ADD_MUSCLE_GROUPS.map((g) => (
                    <TouchableOpacity
                      key={g}
                      onPress={() => { fireHaptic('selection'); onQuickAdd(quickAddPending, g); setQuickAddPending(null); }}
                      style={{ paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999, borderWidth: 1, borderColor: colors.accent, backgroundColor: colors.accentSoft }}>
                      <Text style={{ fontSize: 12, fontWeight: '800', color: colors.accent }}>{g}</Text>
                    </TouchableOpacity>
                  ))}
                  <TouchableOpacity
                    onPress={() => { fireHaptic('selection'); onQuickAdd(quickAddPending, null); setQuickAddPending(null); }}
                    style={{ paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999, borderWidth: 1, borderColor: colors.faint }}>
                    <Text style={{ fontSize: 12, fontWeight: '700', color: colors.muted }}>Skip</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ) : null}

          </View>
        }
        ListFooterComponent={
          search.trim() ? (
            <View style={{ paddingTop: 14, paddingBottom: 20, alignItems: 'center' }}>
              <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap', justifyContent: 'center' }}>
                {onCreateExercise ? (
                  <TouchableOpacity
                    onPress={() => { fireHaptic('selection'); onCreateExercise(search.trim()); }}
                    style={{ paddingHorizontal: 16, paddingVertical: 9, borderRadius: 999, borderWidth: 1, borderColor: colors.accent, backgroundColor: colors.accentSoft }}>
                    <Text style={{ fontSize: 11, fontWeight: '800', color: colors.accent, letterSpacing: 1 }}>+ ADD EXERCISE</Text>
                  </TouchableOpacity>
                ) : null}
                {onQuickAdd ? (
                  <TouchableOpacity
                    onPress={() => {
                      fireHaptic('selection');
                      if (muscle) { onQuickAdd(search.trim(), muscle); }
                      else { setQuickAddPending(search.trim()); }
                    }}
                    style={{ paddingHorizontal: 16, paddingVertical: 9, borderRadius: 999, borderWidth: 1, borderColor: colors.faint, backgroundColor: 'transparent' }}>
                    <Text style={{ fontSize: 11, fontWeight: '800', color: colors.muted, letterSpacing: 1 }}>+ QUICK ADD</Text>
                  </TouchableOpacity>
                ) : null}
              </View>
            </View>
          ) : null
        }
      />

    </BottomSheetModal>
  );
}

const EMPTY_ARRAY = [];
const EMPTY_INPUT = { weight: '', reps: '' };

//  EXERCISE CARD (memoized to avoid re-renders on unrelated state changes)

const ExerciseCard = React.memo(function ExerciseCard({
  exIndex,
  ex,
  originalEx,
  logged,
  inp,
  exerciseNote,
  onNoteBlur,
  heavy,
  suggestion,
  ghost,
  group,
  groupColor,
  isFirstInGroup,
  timeBased,
  showPlateUi,
  borderColor,
  restSec,
  dayHistory,
  colors,
  settings,
  activeGymProfile,
  drag,
  isActive,
  onMenu,
  onDispatch,
  onRegisterRef,
  onFocusInput,
  onStepInput,
  onLogSet,
  onGenerateWarmups,
  onShowPlates,
  onEditRest,
  setActiveInputMetaRef,
  setFocusedField,
  haptic,
}) {
  // Local note state � prevents Android cursor-jump on parent re-renders.
  // We hold text locally while focused; sync from prop only when not editing.
  const [localNote, setLocalNote] = useState(exerciseNote || '');
  const [localWeight, setLocalWeight] = useState(inp.weight || '');
  const [localReps, setLocalReps] = useState(inp.reps || '');
  const noteFocusedRef = useRef(false);
  const weightFocusedRef = useRef(false);
  const repsFocusedRef = useRef(false);
  useEffect(() => {
    if (!noteFocusedRef.current) setLocalNote(exerciseNote || '');
  }, [exerciseNote]);
  useEffect(() => {
    setLocalWeight(inp.weight || '');
  }, [inp.weight]);
  useEffect(() => {
    setLocalReps(inp.reps || '');
  }, [inp.reps]);

  const groupColors = useMemo(() => {
    if (!groupColor) return null;
    return {
      chipBorder:  withAlpha(groupColor, 0.33),
      chipBg:      withAlpha(groupColor, 0.09),
      badgeBorder: withAlpha(groupColor, 0.40),
      badgeBg:     withAlpha(groupColor, 0.13),
    };
  }, [groupColor]);

  const parsedWeight = Number.parseFloat(localWeight || '0') || 0;
  const recentH = dayHistory;
  const allSameOrLess = useMemo(
    () => recentH.length === 3 && recentH.every((historyItem) => {
      const match = historyItem.exercises?.find((entry) => entry.name === ex.name);
      return match && match.sets[0] && parsedWeight <= Number(match.sets[0].weight || 0);
    }),
    [recentH, ex.name, parsedWeight]
  );

  return (
    <ScaleDecorator activeScale={0.97}>
      <View style={{ marginBottom: 16 }}>
        {isFirstInGroup ? (
          <View style={[{
            marginHorizontal: 12, marginTop: 12, paddingHorizontal: 10, paddingVertical: 4,
            borderWidth: 1, alignSelf: 'flex-start', borderRadius: 999,
            borderColor: groupColors?.chipBorder,
            backgroundColor: groupColors?.chipBg,
          }]}>
            <Text style={{ fontSize: 9, fontWeight: '800', letterSpacing: 2, color: groupColor }}>SUPERSET {group}</Text>
          </View>
        ) : null}
        <View style={{
          margin: 12, marginBottom: 0, borderWidth: 1, borderLeftWidth: 3,
          borderRadius: CARD_RADIUS, overflow: 'hidden',
          borderLeftColor: borderColor,
          backgroundColor: colors.card,
          borderColor: colors.cardBorder,
          opacity: isActive ? 0.85 : 1,
        }}>
          <View style={{ flexDirection: 'row', alignItems: 'flex-start', paddingHorizontal: 16, paddingTop: 14, paddingBottom: 8 }}>
            <RNGHTouchableOpacity onLongPress={drag} delayLongPress={150} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Ionicons name="reorder-three-outline" size={22} color={colors.muted} />
            </RNGHTouchableOpacity>
            <View style={{ flex: 1, marginLeft: 8 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <Text style={{ fontSize: 19, fontWeight: '900', color: colors.text, letterSpacing: -0.4, flexShrink: 1, lineHeight: 23 }}>{ex.name}</Text>
                {group ? <View style={{ borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999, borderColor: groupColors?.badgeBorder, backgroundColor: groupColors?.badgeBg }}><Text style={{ fontSize: 10, fontWeight: '900', letterSpacing: 1, color: groupColor }}>{group}</Text></View> : null}
                {heavy ? (
                  <View style={{ backgroundColor: colors.accentSoft, borderWidth: 1, borderColor: colors.accentBorder, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 }}>
                    <Text style={{ fontSize: 8, color: colors.accent, fontWeight: '700', letterSpacing: 1 }}>HEAVY</Text>
                  </View>
                ) : null}
                {allSameOrLess ? (
                  <View style={{ backgroundColor: colors.faint, borderWidth: 1, borderColor: colors.gold || colors.accent, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 }}>
                    <Text style={{ fontSize: 8, color: colors.gold || colors.accent, fontWeight: '700', letterSpacing: 1 }}>? OVERLOAD</Text>
                  </View>
                ) : null}
                {suggestion ? (
                  <View style={{
                    borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999,
                    borderColor: suggestion.action === 'increase' ? colors.accent : suggestion.action === 'reduce' ? '#FF6B6B55' : colors.faint,
                    backgroundColor: suggestion.action === 'increase' ? colors.accentSoft : suggestion.action === 'reduce' ? '#FF6B6B11' : 'transparent',
                  }}>
                    <Text style={{ fontSize: 8, fontWeight: '800', letterSpacing: 1.2, color: suggestion.action === 'reduce' ? '#FF8E8E' : suggestion.action === 'increase' ? colors.accent : colors.muted }}>
                      {suggestion.action.toUpperCase()}
                    </Text>
                  </View>
                ) : null}
              </View>
              <Text style={{ fontSize: 11, color: colors.muted, marginTop: 3, lineHeight: 15 }}>
                {ex.primary || (ex.primaryMuscles || [])[0] || '-'} � {originalEx.sets}x{timeBased ? `${originalEx.reps}s` : originalEx.reps}
              </Text>
              {suggestion ? (
                <Text style={{ fontSize: 10, marginTop: 4, lineHeight: 14, color: suggestion.action === 'reduce' ? '#FF8E8E' : colors.subtext }}>
                  Next target {Number(suggestion.targetWeight || 0) > 0 ? formatWeightFromKg(suggestion.targetWeight, settings?.weightUnit || 'kg') : 'BW'} x {Math.max(1, Number(suggestion.targetReps || 0))}
                  {suggestion.plateauSignal ? ` � Plateau: ${suggestion.plateauSignal.recommendation}` : ''}
                  {suggestion.deloadSignal ? ` � ${suggestion.deloadSignal.recommendation}` : ''}
                </Text>
              ) : null}
            </View>
            <TouchableOpacity onPress={() => { fireHaptic('selection', { enabled: haptic }); onMenu(exIndex); }} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
              <Ionicons name="ellipsis-vertical" size={18} color={colors.muted} />
            </TouchableOpacity>
          </View>
          <TextInput
            style={{ marginHorizontal: 16, marginBottom: 8, fontSize: 12, color: colors.subtext, borderWidth: 1, borderColor: colors.faint, padding: 10, minHeight: 42, borderRadius: 16 }}
            value={localNote}
            onChangeText={setLocalNote}
            onFocus={() => { noteFocusedRef.current = true; }}
            onBlur={() => {
              noteFocusedRef.current = false;
              onDispatch({ type: 'SET_EXERCISE_NOTE', exIndex, note: localNote });
              onNoteBlur?.(localNote);
            }}
            placeholder={originalEx.note || 'Add note...'}
            placeholderTextColor={colors.muted}
            multiline
          />
          {ghost && ghost.sets && ghost.sets.length > 0 ? (
            <TouchableOpacity
              style={{ marginHorizontal: 16, marginBottom: 8, padding: 10, borderWidth: 1, borderColor: colors.faint, borderStyle: 'dashed', borderRadius: 16 }}
              onPress={() => {
                fireHaptic('selection', { enabled: haptic });
                const fs = ghost.sets[0];
                if (fs) {
                  const nextWeight = String(fs.weight ?? '');
                  const nextReps = String(fs.reps ?? '');
                  setLocalWeight(nextWeight);
                  setLocalReps(nextReps);
                  onDispatch({ type: 'SET_INPUT', exIndex, weight: nextWeight, reps: nextReps });
                }
              }}
            >
              <Text style={{ fontSize: 8, letterSpacing: 2, color: colors.muted, marginBottom: 4 }}>LAST � {ghost.date} � tap to fill</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                {ghost.sets.map((gs, gi) => <Text key={gi} style={{ fontSize: 12, color: colors.subtext, fontWeight: '600' }}>{gs.weight > 0 ? formatWeightFromKg(gs.weight, settings?.weightUnit || 'kg') : 'BW'}x{gs.reps}</Text>)}
              </View>
            </TouchableOpacity>
          ) : null}
          {logged.length > 0 && (
            <View style={{ marginHorizontal: 16, marginBottom: 4, marginTop: 2 }}>
              {logged.map((ls, si) => (
                <View key={ls.id}>
                  <SetRow
                    set={ls}
                    setIndex={si}
                    exIndex={exIndex}
                    dispatch={onDispatch}
                    effortTracking={settings.effortTracking || 'off'}
                    hapticFeedback={haptic}
                    weightUnit={settings?.weightUnit || 'kg'}
                    trackingType={ex?.trackingType || 'weight_reps'}
                  />
                  {showPlateUi && ls.type === 'warmup' && ls.weight > 0 && (
                    <Text style={{ fontSize: 10, color: colors.muted, paddingLeft: 48, paddingBottom: 4, letterSpacing: 0.3 }}>
                      {getPlateText(ls.weight, activeGymProfile?.barWeight || settings.barWeight || 20, activeGymProfile?.plates, settings?.weightUnit || 'kg')}
                    </Text>
                  )}
                </View>
              ))}
            </View>
          )}
          {showPlateUi ? (
            <TouchableOpacity
              style={{ marginHorizontal: 16, marginBottom: 4, paddingVertical: 8, borderWidth: 1, borderColor: colors.faint, borderStyle: 'dashed', alignItems: 'center', borderRadius: 16 }}
              onPress={() => { fireHaptic('selection', { enabled: haptic }); onGenerateWarmups(exIndex); }}
            >
              <Text style={{ fontSize: 10, color: colors.muted, letterSpacing: 1.4, fontWeight: '700' }}>GENERATE WARM-UP SETS</Text>
            </TouchableOpacity>
          ) : null}
          <View style={{ flexDirection: 'row', alignItems: 'flex-end', paddingHorizontal: 16, paddingTop: 10, paddingBottom: 8, gap: 10 }}>
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: 9, letterSpacing: 2.4, color: colors.subtext, marginBottom: 4, fontWeight: '700' }}>{timeBased ? 'DIST' : String(settings?.weightUnit || 'kg').toUpperCase()}</Text>
              <View style={{ position: 'relative', justifyContent: 'center' }}>
                <TextInput
                  ref={(ref) => onRegisterRef(exIndex, 'weight', ref)}
                  style={{ flex: 1, borderBottomWidth: 2, borderBottomColor: colors.faint, fontSize: 22, fontWeight: '900', color: colors.text, paddingVertical: 9, textAlign: 'center', minHeight: 48 }}
                  value={localWeight}
                  onChangeText={setLocalWeight}
                  onFocus={() => {
                    weightFocusedRef.current = true;
                    setActiveInputMetaRef({ exIndex, field: 'weight' });
                    setFocusedField('weight');
                  }}
                  onBlur={() => {
                    weightFocusedRef.current = false;
                    onDispatch({ type: 'SET_INPUT', exIndex, weight: localWeight });
                  }}
                  onSubmitEditing={() => {
                    onDispatch({ type: 'SET_INPUT', exIndex, weight: localWeight });
                    onFocusInput(exIndex, 'reps');
                  }}
                  returnKeyType="next"
                  blurOnSubmit={false}
                  keyboardType="decimal-pad"
                  placeholder={timeBased ? '0.0' : '0'}
                  placeholderTextColor={colors.muted}
                />
                {showPlateUi && !timeBased ? (
                  <TouchableOpacity
                    style={{ position: 'absolute', right: 0, top: 8, width: 28, height: 28, alignItems: 'center', justifyContent: 'center' }}
                    onPress={() => { fireHaptic('selection', { enabled: haptic }); onShowPlates(Number.parseFloat(localWeight || '0') || 0); }}
                  >
                    <Ionicons name="barbell-outline" size={14} color={colors.muted} />
                  </TouchableOpacity>
                ) : null}
              </View>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: 9, letterSpacing: 2.4, color: colors.subtext, marginBottom: 4, fontWeight: '700' }}>{timeBased ? 'SEC' : 'REPS'}</Text>
              <TextInput
                ref={(ref) => onRegisterRef(exIndex, 'reps', ref)}
                style={{ flex: 1, borderBottomWidth: 2, borderBottomColor: colors.faint, fontSize: 22, fontWeight: '900', color: colors.text, paddingVertical: 9, textAlign: 'center', minHeight: 48 }}
                value={localReps}
                onChangeText={setLocalReps}
                onFocus={() => {
                  repsFocusedRef.current = true;
                  setActiveInputMetaRef({ exIndex, field: 'reps' });
                  setFocusedField('reps');
                }}
                onBlur={() => {
                  repsFocusedRef.current = false;
                  onDispatch({ type: 'SET_INPUT', exIndex, reps: localReps });
                }}
                onSubmitEditing={() => onLogSet(exIndex, { weight: localWeight, reps: localReps })}
                returnKeyType="done"
                keyboardType="number-pad"
                placeholder={timeBased ? '60' : '0'}
                placeholderTextColor={colors.muted}
              />
            </View>
            <TouchableOpacity
              style={{ backgroundColor: colors.accent, paddingHorizontal: 20, paddingVertical: 13, borderRadius: 16, minHeight: 48, justifyContent: 'center' }}
              onPress={() => onLogSet(exIndex, { weight: localWeight, reps: localReps })}
            >
                    <Text style={{ fontSize: 12, fontWeight: '800', color: colors.textOnAccent || '#fff', letterSpacing: 1.5 }}>LOG</Text>
            </TouchableOpacity>
          </View>
          {logged.length > 0 ? (
            <TouchableOpacity
              style={{ marginHorizontal: 16, marginBottom: 4, paddingVertical: 8, borderWidth: 1, borderColor: colors.faint, borderStyle: 'dashed', alignItems: 'center', borderRadius: 16 }}
              onPress={() => { fireHaptic('selection', { enabled: haptic }); onDispatch({ type: 'QUICK_ADD_SET', exIndex }); }}
            >
              <Text style={{ fontSize: 10, color: colors.muted, letterSpacing: 1.4, fontWeight: '700' }}>+ QUICK ADD</Text>
            </TouchableOpacity>
          ) : null}
          <TouchableOpacity
            style={{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 16, paddingBottom: 12 }}
            onPress={() => { fireHaptic('selection', { enabled: haptic }); onEditRest(exIndex); }}
          >
            <Ionicons name="timer-outline" size={12} color={colors.muted} />
            <Text style={{ fontSize: 10, color: colors.muted, letterSpacing: 0.8 }}>{restSec}s rest � tap to change</Text>
          </TouchableOpacity>
        </View>
      </View>
    </ScaleDecorator>
  );
}, (prev, next) =>
  prev.inp === next.inp &&
  prev.logged === next.logged &&
  prev.isActive === next.isActive &&
  prev.suggestion === next.suggestion &&
  prev.ghost === next.ghost &&
  prev.group === next.group &&
  prev.colors === next.colors
);

//  WORKOUT CONTENT

function WorkoutContent({ plan, day, planIndex, dayIndex, navigation }) {
  useKeepAwake();

  const { history, pb, settings, addHistory, updatePb, saveExerciseNotes, isHeavy, exerciseNotes, activeGymProfile, updateSettings, bodyWeight, plans, savePlans } = useWatermelonAppData();
  const colors = useTheme();
  const transitionReady = useDeferredScreenReady({ minDelayMs: 16, resetOnBlur: false });
  const s = useMemo(() => makeStyles(colors), [colors]);
  const { state, dispatch } = useWorkout();
  const { setBanner } = useActiveBanner();
  const haptic = settings.hapticFeedback !== false;
  const insets = useSafeAreaInsets();
  const GROUP_COLORS = useMemo(() => ({
    A: colors.accent,
    B: withAlpha(colors.accent, 0.65),
    C: withAlpha(colors.accent, 0.40),
  }), [colors.accent]);

  const [extraExercises, setExtraExercises] = useState([]);
  const baseExercises = useMemo(
    () => (Array.isArray(day?.exercises) ? day.exercises.filter((exercise) => !exercise?.isWarmup).map(normalizeSessionExercise) : []),
    [day?.exercises]
  );
  const workingExercises = useMemo(
    () => [...baseExercises, ...extraExercises.map(normalizeSessionExercise)],
    [baseExercises, extraExercises]
  );

  // Local UI state
  const [restOverride, setRestOverride] = useState({});
  const [showRestOverride, setShowRestOverride] = useState(false);
  const [editingRestEx, setEditingRestEx] = useState(null);
  const [showPlates, setShowPlates] = useState(false);
  const [platesTarget, setPlatesTarget] = useState(0);
  const [showCopyModal, setShowCopyModal] = useState(false);
  const [timerSyncKey, setTimerSyncKey] = useState(0);
  const [supersetModalEx, setSupersetModalEx] = useState(null);
  const [swapModalEx, setSwapModalEx] = useState(null);
  const [showAddExerciseModal, setShowAddExerciseModal] = useState(false);
  const [isDeload, setIsDeload] = useState(false);
  const [orderedIndices, setOrderedIndices] = useState(() => workingExercises.map((_, i) => i));
  const [completionSummary, setCompletionSummary] = useState(null);
  const [completionPrCount, setCompletionPrCount] = useState(0);
  const [quitAlert, setQuitAlert] = useState(false);
  const [nextTargets, setNextTargets] = useState(null); // [{ name, weight, reps }] | null
  const [showShareModal, setShowShareModal] = useState(false);
  const [completedWorkout, setCompletedWorkout] = useState(null);
  const [ratePrompt, setRatePrompt] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);
  const [inlineToast, setInlineToast] = useState({ visible: false, text: '', intent: 'default' });
  const [prHud, setPrHud] = useState(null);
  const [libraryIndex, setLibraryIndex] = useState(EXERCISES);
  const activeInputMeta = useRef(null);
  const [focusedField, setFocusedField] = useState(null); // for UI rendering only
  const [liveExerciseHint, setLiveExerciseHint] = useState(null);
  const keyboardInset = useKeyboardInset(0);
  const baseListBottomSpacing = useMemo(
    () => getBottomOverlaySpacing({
      safeAreaBottom: insets.bottom,
      includeWorkoutPill: false,
      extra: 28,
    }),
    [insets.bottom]
  );
  const restPanelAnim = useSharedValue(state.restTimer.active ? 1 : 0);
  const [showRestPanel, setShowRestPanel] = useState(state.restTimer.active);
  const restPanelStyle = useAnimatedStyle(() => ({
    opacity: restPanelAnim.value,
    maxHeight: restPanelAnim.value * 360,
    overflow: 'hidden',
    transform: [{ translateY: (1 - restPanelAnim.value) * 12 }],
  }));
  const inputRefs = useRef({});
  const shareCardRef = useRef(null);
  const flatListRef = useRef(null);
  // Pre-filter history for this day once � avoids O(n) per renderItem per frame
  const dayHistory = useMemo(
    () => history.filter(h => h.dayId === day.id).slice(0, 3),
    [history, day.id]
  );

  const sessionKey = useMemo(
    () => `${ACTIVE_WORKOUT_SESSION_PREFIX}${plan?.id || planIndex}:${day?.id || dayIndex}`,
    [plan?.id, planIndex, day?.id, dayIndex]
  );

  const startTime = useRef(Date.now());
  const restTimerRef = useRef(state.restTimer);
  const countdownHapticRef = useRef(null);
  const appStateRef = useRef(AppState.currentState);
  const copyModalShown = useRef(false);
  const persistDebounceRef = useRef(null);
  const suppressKeyboardScrollUntilRef = useRef(0);
  const [sessionReady, setSessionReady] = useState(false);

  useEffect(() => {
    setExtraExercises([]);
    setLiveExerciseHint(null);
  }, [day?.id]);

  useEffect(() => {
    if (!transitionReady) return undefined;
    let mounted = true;
    getExerciseIndex()
      .then((index) => {
        if (!mounted) return;
        if (Array.isArray(index) && index.length > 0) setLibraryIndex(index);
      })
      .catch(() => {});
    return () => {
      mounted = false;
    };
  }, [transitionReady]);

  useEffect(() => {
    setOrderedIndices((prev) => {
      const merged = prev.filter((index, pos) => index < workingExercises.length && prev.indexOf(index) === pos);
      for (let index = 0; index < workingExercises.length; index += 1) {
        if (!merged.includes(index)) merged.push(index);
      }
      return merged;
    });
  }, [workingExercises.length]);

  useEffect(() => { restTimerRef.current = state.restTimer; }, [state.restTimer]);

  useEffect(() => {
    if (state.restTimer.active) {
      setShowRestPanel(true);
      restPanelAnim.value = withTiming(1, {
        duration: 260,
        easing: ReaEasing.bezier(0.25, 1, 0.5, 1),
      });
      return;
    }
    if (!showRestPanel) return;
    restPanelAnim.value = withTiming(0, {
      duration: 220,
      easing: ReaEasing.bezier(0.5, 0, 0.75, 0),
    }, (finished) => {
      if (finished) runOnJS(setShowRestPanel)(false);
    });
  }, [state.restTimer.active, showRestPanel]); // eslint-disable-line

  const hasMeaningfulSessionState = useMemo(() => {
    const setCount = Object.values(state.setLog).reduce((total, sets) => total + sets.length, 0);
    return (
      setCount > 0 ||
      extraExercises.length > 0 ||
      Object.keys(state.inputs).length > 0 ||
      Object.keys(state.exerciseNotes).length > 0 ||
      Object.keys(state.supersetGroups).length > 0 ||
      Object.keys(state.swappedExercises).length > 0 ||
      state.restTimer.active ||
      state.copiedPrevious
    );
  }, [state.setLog, extraExercises, state.inputs, state.exerciseNotes, state.supersetGroups, state.swappedExercises, state.restTimer.active, state.copiedPrevious]);

  useEffect(() => {
    let cancelled = false;
    setSessionReady(false);
    copyModalShown.current = false;
    (async () => {
      try {
        const session = await getActiveSession(sessionKey);
        if (cancelled) return;
        if (session) {
          if (session?.planId === plan?.id && session?.dayId === day?.id && session?.state) {
            startTime.current = session.startedAt || Date.now();
            setIsDeload(!!session.isDeload);
            dispatch({ type: 'HYDRATE_STATE', payload: session.state });
            if (Array.isArray(session.state.extraExercises) && session.state.extraExercises.length > 0) {
              setExtraExercises(session.state.extraExercises);
            }
            if (Array.isArray(session.state.orderedIndices) && session.state.orderedIndices.length > 0) {
              setOrderedIndices(session.state.orderedIndices);
            }
          } else {
            await clearActiveSession(sessionKey);
          }
        }
      } catch (_) {
        // Ignore malformed or missing active session payloads.
      } finally {
        if (!cancelled) setSessionReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [sessionKey, plan?.id, day?.id, dispatch]);

  // Ongoing system notification � updates title + body as sets are logged.
  useEffect(() => {
    if (!sessionReady) return;
    const weightUnit = settings?.weightUnit || 'kg';
    const dayLabel = day?.name ? String(day.name).toUpperCase() : 'WORKOUT';
    const hintedIdx = Number.isInteger(activeInputMeta.current?.exIndex)
      ? activeInputMeta.current.exIndex
      : (Number.isInteger(liveExerciseHint) ? liveExerciseHint : -1);
    // Find the first exercise that still has sets remaining
    const fallbackIdx = workingExercises.findIndex((ex, i) => {
      const logged = state.setLog[i] || [];
      return logged.length < (Number(ex.sets) || 1);
    });
    let exIdx = hintedIdx >= 0 && hintedIdx < workingExercises.length
      ? hintedIdx
      : (fallbackIdx !== -1 ? fallbackIdx : workingExercises.length - 1);
    if (exIdx < 0) exIdx = 0;
    const currentEx = workingExercises[exIdx];
    const loggedSets = state.setLog[exIdx] || [];
    const loggedCount = loggedSets.length;
    const plannedCount = Math.max(1, Number(currentEx?.sets || 1));
    const exName = currentEx?.name || '';
    const isTimeBased = isTimeBasedExercise(currentEx);

    // Build compact set history � up to 4 sets shown
    const setChips = loggedSets.slice(-4).map((s) => {
      if (isTimeBased) return formatDurationShort(s.durationSec || s.reps);
      const w = Number(s.weight || 0);
      const r = Number(s.reps || 0);
      return w > 0 ? `${w}�${r}` : `BW�${r}`;
    });

    // Current live input if typing
    const liveInp = state.inputs[exIdx] || {};
    const liveW = liveInp.weight ? String(liveInp.weight).trim() : '';
    const liveR = liveInp.reps ? String(liveInp.reps).trim() : '';
    const liveChip = loggedCount < plannedCount && (liveW || liveR)
      ? `? ${liveW || '?'}�${liveR || '?'}`
      : null;

    const setLine = [...setChips, ...(liveChip ? [liveChip] : [])].join('  ');
    const body = exName
      ? `${exName}  ${loggedCount}/${plannedCount}${setLine ? `\n${setLine}` : ''}`
      : '';

    showOngoingWorkoutNotification({
      title: dayLabel,
      body,
      startedAtMs: startTime.current || Date.now(),
    }).catch(() => {});
  // Intentionally excludes state.inputs � we don't want a notification IPC round-trip
  // on every keystroke. The live input chip is nice-to-have; set-log accuracy is not.
  }, [sessionReady, day?.name, plan?.name, state.setLog, workingExercises, liveExerciseHint, settings?.weightUnit]);

  useEffect(() => {
    if (!sessionReady) return;
    const payload = {
      version: 1,
      planId: plan?.id || null,
      dayId: day?.id || null,
      planIndex,
      dayIndex,
      startedAt: startTime.current,
      isDeload,
      state: {
        inputs: state.inputs,
        setLog: state.setLog,
        exerciseNotes: state.exerciseNotes,
        supersetGroups: state.supersetGroups,
        restTimer: state.restTimer,
        swappedExercises: state.swappedExercises,
        copiedPrevious: state.copiedPrevious,
        extraExercises,
        orderedIndices,
      },
    };
    // Debounce: coalesce rapid state changes (e.g. keystrokes) into a single write.
    // Structural changes (set logged, exercise swapped) still persist within ~350 ms.
    if (persistDebounceRef.current) clearTimeout(persistDebounceRef.current);
    persistDebounceRef.current = setTimeout(() => {
      const persist = hasMeaningfulSessionState
        ? setActiveSession(sessionKey, payload)
        : clearActiveSession(sessionKey);
      persist.catch(() => {});
    }, 350);
  }, [
    sessionReady,
    sessionKey,
    plan?.id,
    day?.id,
    planIndex,
    dayIndex,
    isDeload,
    hasMeaningfulSessionState,
    extraExercises,
    orderedIndices,
    state.inputs,
    state.setLog,
    state.exerciseNotes,
    state.supersetGroups,
    state.restTimer,
    state.swappedExercises,
    state.copiedPrevious,
  ]);

  // Determine when the first actual set was logged to start the timer
  const firstLogTime = useMemo(() => {
    const allSets = Object.values(state.setLog).flat();
    if (allSets.length === 0) return null;
    return startTime.current;
  }, [state.setLog]);

  // Set floating banner on mount, don't clear on unmount (cleared on finish/quit)
  useEffect(() => {
    setBanner({ planIndex, dayIndex, dayName: day.name, startTime: firstLogTime, isDeload });
  }, [isDeload, firstLogTime]);

  useEffect(() => {
    if (copyModalShown.current) return;
    if (!sessionReady || hasMeaningfulSessionState) return;
    if (Object.keys(state.ghostData).length > 0) {
      copyModalShown.current = true;
      setShowCopyModal(true);
    }
  }, [state.ghostData, sessionReady, hasMeaningfulSessionState]);

  useEffect(() => {
    workingExercises.forEach((ex, i) => {
      const note = exerciseNotes[ex.name] || '';
      if (note) dispatch({ type: 'SET_EXERCISE_NOTE', exIndex: i, note });
    });
  }, []);

  const progressionSuggestions = useMemo(() => {
    if (!transitionReady) return {};
    const suggestions = {};
    workingExercises.forEach((exercise, index) => {
      const actual = state.swappedExercises[index] || exercise;
      suggestions[index] = buildProgressionSuggestion({
        exercise: {
          ...actual,
          exerciseId: actual.exerciseId || actual.id || exercise.exerciseId || exercise.name,
          prescribedSets: Number(exercise.sets || 0),
          prescribedReps: parseRepTarget(exercise.reps, 8),
          sets: Number(exercise.sets || 0),
          reps: parseRepTarget(exercise.reps, 8),
        },
        history,
        progressionStyle: settings?.progressionStyle,
      });
    });
    return suggestions;
  }, [history, state.swappedExercises, transitionReady, workingExercises]);

  const triggerCountdownHaptic = useCallback((remaining) => {
    if (!Number.isFinite(remaining) || remaining < 1 || remaining > 5) {
      countdownHapticRef.current = null;
      return;
    }
    if (countdownHapticRef.current === remaining) return;
    countdownHapticRef.current = remaining;

    const eventName = {
      5: 'countdownLightest',
      4: 'countdownLight',
      3: 'countdownMedium',
      2: 'countdownHigh',
      1: 'countdownBuzz',
    }[remaining];

    if (!eventName) return;
    fireHaptic(eventName, { enabled: haptic, force: true });
  }, [haptic]);

  // syncRestTimer: only needed to handle timer-expired-while-backgrounded and
  // to bump timerSyncKey so RestTimerPanel restarts its interval after foreground restore.
  const syncRestTimer = useCallback(() => {
    const timer = restTimerRef.current;
    countdownHapticRef.current = null;
    if (timer.active && !timer.paused && timer.endTime) {
      const remaining = Math.ceil((timer.endTime - Date.now()) / 1000);
      if (remaining <= 0) {
        dispatch({ type: 'SKIP_REST' });
        fireHaptic('timerDone', { enabled: haptic, force: true });
        return;
      }
    }
    // Bump key so RestTimerPanel re-syncs its interval
    setTimerSyncKey(k => k + 1);
  }, [dispatch, haptic]);

  // No longer drives the interval (RestTimerPanel owns that) � only needed
  // for expired-while-backgrounded dispatch. Panel re-syncs via its own dep array.
  // Keep empty dep array here; foreground restore handled by AppState listener above.

  const startRestWithDuration = useCallback((secs, options = {}) => {
    const endTime = Date.now() + secs * 1000;
    countdownHapticRef.current = null;
    dispatch({ type: 'START_REST', endTime, total: secs, triggerExIndex: null });
    if (options.hapticEvent) {
      fireHaptic(options.hapticEvent, { enabled: haptic });
    }
  }, [dispatch, haptic]);

  const startRest = useCallback((exIndex) => {
    const ex = workingExercises[exIndex];
    const heavy = isHeavy(ex.name);
    const base = heavy ? (settings.defaultRestHeavy || 180) : (settings.defaultRestNormal || 120);
    const secs = restOverride[exIndex] !== undefined ? restOverride[exIndex] : base;
    startRestWithDuration(secs);
  }, [workingExercises, settings, restOverride, isHeavy, startRestWithDuration]);

  const handlePause = useCallback(() => {
    countdownHapticRef.current = null;
    dispatch({ type: 'PAUSE_REST', pausedAt: Date.now() });
    fireHaptic('selection', { enabled: haptic });
  }, [dispatch, haptic]);

  const handleResume = useCallback((displaySecs) => {
    const newEndTime = Date.now() + displaySecs * 1000;
    dispatch({ type: 'RESUME_REST', newEndTime });
    fireHaptic('selection', { enabled: haptic });
  }, [dispatch, haptic]);

  const handleSkip = useCallback(() => {
    countdownHapticRef.current = null;
    dispatch({ type: 'SKIP_REST' });
    fireHaptic('lightConfirm', { enabled: haptic });
  }, [dispatch, haptic]);

  const handleAdd30 = useCallback(() => {
    countdownHapticRef.current = null;
    dispatch({ type: 'ADD_30S' });
    fireHaptic('selection', { enabled: haptic });
  }, [dispatch, haptic]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', nextState => {
      const wasBackgrounded = appStateRef.current.match(/inactive|background/);
      appStateRef.current = nextState;
      if (wasBackgrounded && nextState === 'active') syncRestTimer();
    });
    return () => sub.remove();
  }, [syncRestTimer]);

  useFocusEffect(
    useCallback(() => {
      syncRestTimer();
    }, [syncRestTimer])
  );

  //  F9: Superset-aware logSet 
  const logSet = useCallback((exIndex, draftInput = null) => {
    const inp = draftInput || state.inputs[exIndex] || {};
    const ex = workingExercises[exIndex];
    const isTimeBased = isTimeBasedExercise(ex);
    const hasTypedWeight = String(inp.weight ?? '').trim().length > 0;
    const hasTypedReps = String(inp.reps ?? '').trim().length > 0;
    const profileBarWeight = Number(activeGymProfile?.barWeight ?? settings?.barWeight ?? 20);
    const defaultWeight = !isTimeBased && supportsPlateBreakdown(ex) ? Math.max(0, profileBarWeight) : 0;
    const weight = hasTypedWeight ? (parseFloat(inp.weight) || 0) : defaultWeight;
    const reps = hasTypedReps ? (parseInt(inp.reps, 10) || 0) : parseRepTarget(ex?.reps, isTimeBased ? 60 : 8);
    if (!reps) {
      fireHaptic('invalidAction', { enabled: haptic });
      return;
    }
    const key = day.id + '-' + ex.name;
    const nextSetCount = (state.setLog[exIndex] || []).length + 1;
    const plannedSetCount = parseInt(workingExercises[exIndex]?.sets, 10) || 0;
    const didCompleteExercise = plannedSetCount > 0 && nextSetCount >= plannedSetCount;
    let primarySetEvent = didCompleteExercise ? 'setCompleted' : 'setLogged';

    // For bodyweight exercises with added load, effective weight = BW + added
    const isBW = isBodyweightExercise(ex);
    const weightUnit = settings?.weightUnit || 'kg';
    const latestBWKg = Number(bodyWeight?.[0]?.weight || 0);
    const latestBWUnit = latestBWKg > 0 ? convertKgToUnit(latestBWKg, weightUnit, 2) : 0;
    const effectiveWeight = isBW && weight > 0 && latestBWUnit > 0 ? latestBWUnit + weight : weight;

    const previousPb = Number(pb[key] || 0);
    const prWeight = isBW && latestBWUnit > 0 ? effectiveWeight : weight;
    if (prWeight > 0 && (!previousPb || prWeight > previousPb)) {
      updatePb(key, prWeight);
      const prDisplayStr = isBW && weight > 0 && latestBWUnit > 0
        ? `BW+${formatWeightFromKg(weight, weightUnit)}`
        : formatWeightFromKg(weight, weightUnit);
      dispatch({ type: 'SET_PB_NOTIF', message: 'NEW PB: ' + ex.name + ' - ' + prDisplayStr });
      setTimeout(() => dispatch({ type: 'SET_PB_NOTIF', message: null }), 3000);
      // PRHud handles the PR notification � no separate toast needed
      const previousDisplay = previousPb > 0 ? previousPb : Math.max(0, prWeight - 2.5);
      const delta = prWeight - previousDisplay;
      setPrHud({
        exercise: ex.name,
        weight: prWeight,
        prev: previousDisplay,
        unit: weightUnit,
        delta: `+${Math.abs(delta % 1) > 0.01 ? delta.toFixed(1) : Math.round(delta)} ${weightUnit}`,
        isBodyweight: isBW && weight > 0 && latestBWUnit > 0,
        addedWeight: weight,
        bodyweightKg: latestBWKg,
      });
      primarySetEvent = 'prUnlocked';
    }
    fireHaptic(primarySetEvent, { enabled: haptic });

    const setPayload = isTimeBased
      ? { weight, reps, durationSec: reps, trackingType: ex?.trackingType || 'duration' }
      : { weight, reps, trackingType: ex?.trackingType || 'weight_reps' };
    dispatch({ type: 'SET_INPUT', exIndex, weight: String(weight), reps: String(reps) });
    dispatch({ type: 'LOG_SET', exIndex, set: setPayload });
    setLiveExerciseHint(exIndex);
    setInlineToast({
      visible: true,
      intent: 'success',
      text: isTimeBased
        ? `${workingExercises[exIndex]?.name || 'Exercise'} � ${formatDurationShort(reps)} logged`
        : `${workingExercises[exIndex]?.name || 'Exercise'} � set logged`,
    });
    setTimeout(() => {
      setInlineToast((previous) => ({ ...previous, visible: false }));
    }, 850);
    activeInputMeta.current = null;
    setFocusedField(null);
    suppressKeyboardScrollUntilRef.current = Date.now() + 950;
    Keyboard.dismiss();

    // Superset rest logic
    const group = state.supersetGroups[exIndex];
    if (group) {
      const groupIndices = workingExercises.map((_, i) => i).filter(i => state.supersetGroups[i] === group);
      const newCount = (state.setLog[exIndex] || []).length + 1;
      const roundComplete = groupIndices
        .filter(i => i !== exIndex)
        .every(i => (state.setLog[i] || []).length >= newCount);
      if (roundComplete) {
        const maxRest = Math.max(...groupIndices.map(i => {
          const gEx = workingExercises[i];
          const heavy = isHeavy(gEx.name);
          const base = heavy ? (settings.defaultRestHeavy || 180) : (settings.defaultRestNormal || 120);
          return restOverride[i] !== undefined ? restOverride[i] : base;
        }));
        startRestWithDuration(maxRest);
      }
      // no timer within superset round
    } else {
      startRest(exIndex);
    }
  }, [state, workingExercises, day, pb, updatePb, dispatch, haptic, isHeavy, settings, bodyWeight, restOverride, startRest, startRestWithDuration, activeGymProfile?.barWeight]);

  //  F10: Warmup generator 
  const generateWarmups = useCallback((exIndex) => {
    const inp = state.inputs[exIndex] || {};
    const target = parseFloat(inp.weight);
    if (!target || target <= 0) {
      setAlertConfig({ title: 'Enter target weight first', message: 'Set the KG field before generating warm-up sets.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    const barWeight = activeGymProfile?.barWeight || settings.barWeight || 20;
    const warmupSets = generateWarmupSets(target, barWeight).map((w) => ({
      id: genId(), weight: w.weight, reps: w.reps, type: 'warmup', rpe: null, rir: null, note: null, orm: 0,
    }));
    dispatch({ type: 'INSERT_WARMUPS', exIndex, warmupSets });
  }, [state.inputs, activeGymProfile?.barWeight, settings.barWeight, dispatch]);

  //  F16: Exercise swap
  const handleSwap = useCallback(async (exIndex, newExercise) => {
    const oldExercise = state.swappedExercises[exIndex] || workingExercises[exIndex];
    dispatch({ type: 'SWAP_EXERCISE', exIndex, exercise: newExercise });
    setLiveExerciseHint(exIndex);
    fireHaptic('selection', { enabled: haptic });
    try {
      const lastPerf = await getLastPerformanceIndex();
      const id = newExercise.exerciseId || newExercise.id || newExercise.name;
      const ghost = lastPerf[id] || null;
      dispatch({ type: 'UPDATE_GHOST', exIndex, ghost });
    } catch (_) {}
    // Only offer plan update for base exercises (not session-only extras)
    if (
      exIndex < baseExercises.length &&
      plan && typeof planIndex === 'number' && planIndex >= 0 &&
      typeof dayIndex === 'number' && dayIndex >= 0
    ) {
      setAlertConfig({
        title: 'Update plan too?',
        message: `Replace "${oldExercise?.name || ''}" with "${newExercise.name}" permanently in this plan day?`,
        buttons: [
          { text: 'Session only', style: 'cancel' },
          {
            text: 'Update plan', style: 'default', onPress: () => {
              const updatedPlans = (plans || []).map((p, pi) => {
                if (pi !== planIndex) return p;
                return {
                  ...p,
                  days: p.days.map((d, di) => {
                    if (di !== dayIndex) return d;
                    return {
                      ...d,
                      exercises: d.exercises.map((e, ei) => {
                        if (ei !== exIndex) return e;
                        return {
                          ...e,
                          name: newExercise.name,
                          exerciseId: newExercise.exerciseId || newExercise.id || newExercise.name,
                          primary: newExercise.primaryMuscle || (newExercise.primaryMuscles || [])[0] || e.primary,
                          primaryMuscles: newExercise.primaryMuscles || (newExercise.primaryMuscle ? [newExercise.primaryMuscle] : []),
                          secondaryMuscles: newExercise.secondaryMuscles || [],
                          equipment: newExercise.equipment || e.equipment,
                          category: newExercise.category || e.category,
                          trackingType: newExercise.trackingType || e.trackingType,
                          isBodyweight: newExercise.isBodyweight ?? e.isBodyweight,
                          movementPattern: newExercise.movementPattern || e.movementPattern,
                          difficulty: newExercise.difficulty || e.difficulty,
                          apparatus: newExercise.apparatus || e.apparatus,
                          equipmentDetail: newExercise.equipmentDetail || e.equipmentDetail,
                          sourceTags: newExercise.sourceTags || e.sourceTags,
                        };
                      }),
                    };
                  }),
                };
              });
              savePlans(updatedPlans);
              fireHaptic('success', { enabled: haptic });
            },
          },
        ],
      });
    }
  }, [dispatch, haptic, baseExercises.length, plan, planIndex, dayIndex, plans, savePlans, workingExercises, state.swappedExercises]);

  const addExerciseToSession = useCallback((exercise) => {
    const normalized = normalizeSessionExercise(exercise);
    setExtraExercises((prev) => [...prev, normalized]);
    setLiveExerciseHint(workingExercises.length);
    fireHaptic('selection', { enabled: haptic });
  }, [haptic, workingExercises.length]);

  const removeExercise = useCallback((exIndex) => {
    const ex = state.swappedExercises[exIndex] || workingExercises[exIndex];
    fireHaptic('selection', { enabled: haptic });
    setAlertConfig({
      title: 'Remove exercise?',
      message: ex?.name || '',
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove', style: 'destructive', onPress: () => {
            fireHaptic('destructiveAction', { enabled: haptic });
            // Removing from orderedIndices is enough � finishWorkout and rendering
            // both iterate orderedIndices, so the exercise is completely excluded.
            setOrderedIndices((prev) => prev.filter((i) => i !== exIndex));
          },
        },
      ],
    });
  }, [workingExercises, state.swappedExercises, haptic]);

  const quickAddExerciseFromSearch = useCallback(async (rawName, contextMode = 'swap', preferredMuscle = null) => {
    const seed = String(rawName || '').trim();
    if (!seed) return;
    const duplicate = libraryIndex.find((entry) => String(entry?.name || '').trim().toLowerCase() === seed.toLowerCase());
    if (duplicate) {
      if (contextMode === 'swap' && swapModalEx !== null) {
        handleSwap(swapModalEx, duplicate);
      } else {
        addExerciseToSession(duplicate);
      }
      setSwapModalEx(null);
      setShowAddExerciseModal(false);
      return;
    }
    const normalizedMuscle = String(preferredMuscle || '').trim().toLowerCase();
    const primaryMuscle = normalizedMuscle || 'other';
    try {
      const custom = {
        id: `quick_${seed.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase()}_${Date.now().toString(36)}`,
        name: seed,
        primaryMuscles: [toTitleCase(primaryMuscle)],
        secondaryMuscles: [],
        equipment: 'Other',
        category: 'strength',
        trackingType: 'weight_reps',
        isBodyweight: false,
        requiresExternalLoad: true,
        movementPattern: null,
        difficulty: 'beginner',
        apparatus: null,
        equipmentDetail: null,
        sourceTags: ['quick_add'],
        force: 'push',
        instructions: [],
        isCustom: true,
      };
      const refreshedIndex = await saveCustomExercise(custom);
      if (Array.isArray(refreshedIndex) && refreshedIndex.length) {
        setLibraryIndex(refreshedIndex);
      }
      const createdFromIndex = Array.isArray(refreshedIndex)
        ? refreshedIndex.find((entry) => entry.id === custom.id || String(entry?.name || '').trim().toLowerCase() === seed.toLowerCase())
        : null;
      const createdExercise = normalizeSessionExercise(createdFromIndex || custom);
      if (contextMode === 'swap' && swapModalEx !== null) {
        handleSwap(swapModalEx, createdExercise);
      } else {
        addExerciseToSession(createdExercise);
      }
      setSwapModalEx(null);
      setShowAddExerciseModal(false);
      fireHaptic('success', { enabled: haptic });
    } catch (error) {
      setAlertConfig({
        title: 'Quick add unavailable',
        message: String(error?.message || error || 'Unable to create exercise right now.'),
        buttons: [{ text: 'OK', style: 'default' }],
      });
    }
  }, [libraryIndex, swapModalEx, handleSwap, addExerciseToSession, haptic]);

  // Exercise menu
  const showExerciseMenu = useCallback((exIndex) => {
    const ex = state.swappedExercises[exIndex] || workingExercises[exIndex];
    const group = state.supersetGroups[exIndex];
    const canGenerateBarbellWarmup = supportsPlateBreakdown(ex);
    const youtubeMeta = resolveExerciseYoutubeMeta(ex);

    const options = [
      {
        text: group ? `Superset ${group} - Change Group` : 'Assign to Superset',
        onPress: () => setSupersetModalEx(exIndex),
      },
      { text: 'Swap Exercise', onPress: () => setSwapModalEx(exIndex) },
      {
        text: 'Watch Demo on YouTube',
        onPress: async () => {
          const url = youtubeMeta?.youtubeLink;
          if (!url) {
            setAlertConfig({ title: 'No tutorial link', message: 'Unable to build a tutorial link for this exercise.', buttons: [{ text: 'OK', style: 'default' }] });
            return;
          }
          try {
            fireHaptic('lightConfirm', { enabled: haptic });
            // Try YouTube app deep-link first (vnd.youtube://), fall back to HTTPS.
            // canOpenURL is unreliable on Android 11+ for HTTPS � skip it.
            const query = youtubeMeta?.youtubeSearchQuery;
            const appUrl = query
              ? `vnd.youtube://results?search_query=${encodeURIComponent(query)}`
              : url.replace('https://www.youtube.com', 'vnd.youtube://');
            try {
              await Linking.openURL(appUrl);
            } catch {
              await Linking.openURL(url);
            }
          } catch (error) {
            setAlertConfig({ title: 'Could not open YouTube', message: String(error?.message || error), buttons: [{ text: 'OK', style: 'default' }] });
          }
        },
      },
      ...(canGenerateBarbellWarmup ? [{ text: 'Generate Warm-Up', onPress: () => generateWarmups(exIndex) }] : []),
      { text: 'Remove Exercise', style: 'destructive', onPress: () => removeExercise(exIndex) },
      { text: 'Cancel', style: 'cancel' },
    ];
    setAlertConfig({ title: ex.name, message: '', buttons: options });
  }, [workingExercises, state.supersetGroups, state.swappedExercises, generateWarmups, haptic, removeExercise]);

  const finishWorkout = useCallback(() => {
    const totalSets = Object.values(state.setLog).reduce((a, s) => a + s.length, 0);
    if (totalSets === 0) { setAlertConfig({ title: 'No sets logged', message: 'Log at least one set before finishing.', buttons: [{ text: 'OK', style: 'default' }] }); return; }

    orderedIndices.forEach((exIndex) => {
      const ex = workingExercises[exIndex];
      const note = state.exerciseNotes[exIndex];
      if (ex?.name && note && note !== exerciseNotes[ex.name]) saveExerciseNotes(ex.name, note);
    });

    const exerciseData = orderedIndices.map((exIndex) => {
      const ex = workingExercises[exIndex];
      const actual = state.swappedExercises[exIndex] || ex;
      // Resolve against the live rebuilt index first so v2 metadata and custom exercises survive finish.
      const actualId = actual.exerciseId || actual.id || ex.exerciseId || ex.id;
      const mappedId = EXERCISE_ID_MAP[actual.name] || actualId;
      const actualNameKey = normalizeExerciseLookupKey(actual.name);
      const libMatch = (libraryIndex || []).find((candidate) => {
        const candidateId = candidate.exerciseId || candidate.id;
        if (mappedId && candidateId === mappedId) return true;
        if (actualId && candidateId === actualId) return true;
        if (normalizeExerciseLookupKey(candidate.name) === actualNameKey) return true;
        return Array.isArray(candidate.aliases)
          && candidate.aliases.some((alias) => normalizeExerciseLookupKey(alias) === actualNameKey);
      }) || (mappedId
        ? EXERCISES.find(e => e.id === mappedId)
        : EXERCISES.find(e => normalizeExerciseLookupKey(e.name) === actualNameKey));
      
      const resolvedMuscles = (() => {
        const merged = [
          ...getExerciseMuscles(actual),
          ...getExerciseMuscles(libMatch),
          ...getExerciseMuscles(ex),
        ];
        const seen = new Set();
        const unique = [];
        merged.forEach((muscle) => {
          const key = String(muscle || '').toLowerCase();
          if (!key || seen.has(key)) return;
          seen.add(key);
          unique.push(muscle);
        });
        return unique;
      })();

      return {
        name: actual.name,
        exerciseId: actual.exerciseId || actual.id || ex.exerciseId,
        trackingType: actual.trackingType || ex.trackingType || 'weight_reps',
        primaryMuscles: resolvedMuscles,
        primaryMuscle: resolvedMuscles[0] || null,
        secondaryMuscles: actual.secondaryMuscles || libMatch?.secondaryMuscles || ex.secondaryMuscles || [],
        equipment: actual.equipment || libMatch?.equipment || ex.equipment || null,
        category: actual.category || libMatch?.category || ex.category || null,
        isBodyweight: actual.isBodyweight ?? libMatch?.isBodyweight ?? ex.isBodyweight ?? null,
        requiresExternalLoad: actual.requiresExternalLoad ?? libMatch?.requiresExternalLoad ?? ex.requiresExternalLoad ?? null,
        movementPattern: actual.movementPattern || libMatch?.movementPattern || ex.movementPattern || null,
        difficulty: actual.difficulty || libMatch?.difficulty || ex.difficulty || null,
        apparatus: actual.apparatus || libMatch?.apparatus || ex.apparatus || null,
        equipmentDetail: actual.equipmentDetail || libMatch?.equipmentDetail || ex.equipmentDetail || null,
        sourceTags: actual.sourceTags || libMatch?.sourceTags || ex.sourceTags || [],
        prescribedSets: Number(ex.sets || 0),
        prescribedReps: parseRepTarget(ex.reps, 8),
        sets: (state.setLog[exIndex] || []).map(s => ({
          id: s.id, weight: s.weight, reps: s.reps,
          type: s.type || 'normal', rpe: s.rpe || null, rir: s.rir || null,
          note: s.note || null, orm: s.orm || 0,
          durationSec: s.durationSec || null,
          trackingType: s.trackingType || actual.trackingType || ex.trackingType || 'weight_reps',
        })),
      };
    }).filter(e => e.sets.length > 0);

    const duration = Math.round((Date.now() - startTime.current) / 1000);
    const totalVolume = exerciseData.reduce(
      (sum, exercise) => sum + exercise.sets.reduce((setTotal, setItem) => {
        const timeBased = String(setItem?.trackingType || exercise?.trackingType || '').startsWith('duration');
        return setTotal + (timeBased ? 0 : ((setItem.weight || 0) * (setItem.reps || 0)));
      }, 0),
      0
    );
    const totalDurationSec = exerciseData.reduce(
      (sum, exercise) => sum + exercise.sets.reduce((setTotal, setItem) => {
        const timeBased = String(setItem?.trackingType || exercise?.trackingType || '').startsWith('duration');
        if (!timeBased) return setTotal;
        return setTotal + Math.max(0, Number(setItem.durationSec || setItem.reps || 0));
      }, 0),
      0
    );
    setAlertConfig({
      title: 'FINISH WORKOUT',
      message: totalSets + ' sets logged � ' + Math.round(duration / 60) + 'min' + (totalDurationSec > 0 ? ` � ${Math.round(totalDurationSec / 60)}m timed` : ''),
      buttons: [
        { text: 'Keep Going', style: 'cancel' },
        {
          text: 'FINISH', style: 'default', onPress: async () => {
            fireHaptic('workoutCompleted', { enabled: haptic });
            const sessionId = Date.now().toString();
            const sessionDate = new Date().toISOString();
            const baseSession = {
              id: sessionId,
              date: sessionDate,
              planId: plan.id, dayId: day.id, dayName: day.name,
              duration, sets: totalSets, exercises: exerciseData,
              isDeload,
              totalVolume,
            };
            const analysisHistory = [baseSession, ...history];
            const enrichedExercises = exerciseData.map((exercise) => {
              const suggestion = buildProgressionSuggestion({
                exercise: {
                  ...exercise,
                  sets: exercise.prescribedSets || exercise.sets.length,
                  reps: exercise.prescribedReps || 8,
                },
                history: analysisHistory,
                progressionStyle: settings?.progressionStyle,
              });
              return {
                ...exercise,
                progressionSuggestion: suggestion,
                plateauSignal: suggestion?.plateauSignal || null,
                deloadSignal: suggestion?.deloadSignal || null,
              };
            });
            const recentUsage = await getRecentComparisonUsage().catch(() => []);
            const comparison = buildVolumeInterpretation(totalVolume, { recentUsage });
            const summaryText = buildWorkoutCompletionSummary({
              session: { ...baseSession, exercises: enrichedExercises },
              history,
              exerciseIndex: libraryIndex,
              recentUsage,
              progressionStyle: settings?.progressionStyle,
            });
            const prEvents = detectPREvents(
              { ...baseSession, exercises: enrichedExercises },
              history
            );
            const performanceScore = computeWorkoutPerformanceScore(
              { ...baseSession, exercises: enrichedExercises },
              history,
              prEvents
            );
            const completedSession = {
              ...baseSession,
              exercises: enrichedExercises,
              summaryText,
              prEvents,
              performanceScore,
            };
            const addResult = await addHistory(completedSession);
            if (comparison?.objectName) {
              await recordComparisonUsage({
                objectName: comparison.objectName,
                category: comparison.category,
                totalKg: totalVolume,
                contextLabel: 'workout_completion',
              }).catch(() => {});
            }
            await clearActiveSession(sessionKey).catch(() => {});
            setBanner(null);
            // Rate prompt check
            if (!settings.neverAskToRate) {
              const newCount = (settings.completedWorkoutCount || 0) + 1;
              const newSettings = { ...settings, completedWorkoutCount: newCount };
              const lastPrompt = settings.lastRatePromptCount || 0;
              if (RATE_THRESHOLDS.includes(newCount) && newCount !== lastPrompt) {
                newSettings.lastRatePromptCount = newCount;
                setRatePrompt(true);
              }
              updateSettings(newSettings);
            }
            // Store completed workout for share card
            setCompletedWorkout({
              dayName: day.name,
              duration,
              totalVolume,
              exercises: enrichedExercises,
              prs: Object.fromEntries(Object.entries(pb).map(([k, v]) => [k.split('-').slice(1).join('-'), v])),
              summaryText,
              prEvents,
              performanceScore,
            });
            // Compute + save next-session targets
            try {
              const existing = await getNextTargetsIndex();
              enrichedExercises.forEach(ex => {
                const suggestion = ex.progressionSuggestion;
                if (!suggestion) return;
                existing[ex.exerciseId || ex.name] = {
                  weight: suggestion.targetWeight,
                  reps: suggestion.targetReps,
                  action: suggestion.action,
                  rationale: suggestion.rationale,
                  updatedAt: new Date().toISOString(),
                };
              });
              await setNextTargetsIndex(existing);
              const targets = enrichedExercises.map(ex => {
                const key = ex.exerciseId || ex.name;
                return existing[key]
                  ? {
                      name: ex.name,
                      ...existing[key],
                      plateau: ex.plateauSignal?.recommendation || null,
                      deload: ex.deloadSignal?.recommendation || null,
                    }
                  : null;
              }).filter(Boolean);
              if (targets.length > 0) setNextTargets(targets);
            } catch (_) {}
            const weightUnit = settings?.weightUnit || 'kg';
            const prCount = prEvents.length || 0;
            const prLine = prCount
              ? `${prCount === 1 ? '1 PR landed' : `${prCount} PRs landed`}`
              : 'Session locked in';
            const volumeLine = comparison?.fallback
              ? `Volume: ${formatVolumeFromKg(totalVolume, weightUnit)}`
              : `Volume: ${formatVolumeFromKg(totalVolume, weightUnit)} � ${comparison.text}`;
            const milestoneCount = addResult?.unlockedMilestones?.length || 0;
            const milestoneLine = milestoneCount
              ? `Milestones: ${milestoneCount}`
              : '';
            const scoreLine = `Score: ${performanceScore}/100`;
            if (addResult?.unlockedMilestones?.length) {
              fireHaptic('milestoneSuccess', { enabled: haptic });
            }
            clearOngoingWorkoutNotification().catch(() => {});
            setCompletionPrCount(prEvents.length || 0);
            setCompletionSummary(
              [prLine, volumeLine, milestoneLine, scoreLine]
                .filter(Boolean)
                .join('\n')
            );
          },
        },
      ],
    });
  }, [state, workingExercises, orderedIndices, day, plan, exerciseNotes, saveExerciseNotes, addHistory, navigation, haptic, isDeload, setBanner, sessionKey, settings, updateSettings, pb, history, libraryIndex]);

  // Resolve displayed exercise (may be swapped)
  const resolveEx = (exIndex) => state.swappedExercises[exIndex] || workingExercises[exIndex];

  const registerInputRef = useCallback((exIndex, field, ref) => {
    if (!inputRefs.current[exIndex]) inputRefs.current[exIndex] = {};
    inputRefs.current[exIndex][field] = ref;
  }, []);

  const focusInput = useCallback((exIndex, field) => {
    const target = inputRefs.current?.[exIndex]?.[field];
    if (target && typeof target.focus === 'function') {
      target.focus();
      setLiveExerciseHint(exIndex);
      activeInputMeta.current = { exIndex, field };
      setFocusedField(field);
    }
  }, []);

  const stepInput = useCallback((direction = 1) => {
    if (!activeInputMeta.current) return;
    const idx = orderedIndices.indexOf(activeInputMeta.current.exIndex);
    if (idx < 0) return;
    const fields = ['weight', 'reps'];
    const fieldIndex = fields.indexOf(activeInputMeta.current.field);
    const linearIndex = idx * fields.length + Math.max(0, fieldIndex);
    const nextLinear = linearIndex + direction;
    if (nextLinear < 0) return;
    const nextExerciseListIndex = Math.floor(nextLinear / fields.length);
    const nextFieldIndex = nextLinear % fields.length;
    if (nextExerciseListIndex >= orderedIndices.length) {
      Keyboard.dismiss();
      activeInputMeta.current = null;
      setFocusedField(null);
      return;
    }
    const nextExIndex = orderedIndices[nextExerciseListIndex];
    const nextField = fields[nextFieldIndex];
    focusInput(nextExIndex, nextField);
  }, [activeInputMeta, orderedIndices, focusInput]);

  const adjustActiveWeight = useCallback((deltaKg) => {
    if (!activeInputMeta.current || activeInputMeta.current.field !== 'weight') return;
    const exIndex = activeInputMeta.current.exIndex;
    const currentInput = state.inputs[exIndex] || {};
    const currentWeight = Number.parseFloat(currentInput.weight || '0') || 0;
    const nextWeight = Math.max(0, Math.round((currentWeight + deltaKg) * 100) / 100);
    dispatch({ type: 'SET_INPUT', exIndex, weight: String(nextWeight) });
    fireHaptic('selection', { enabled: haptic });
  }, [activeInputMeta, state.inputs, dispatch, haptic]);

  // Stable callbacks passed down to ExerciseCard
  const onShowPlates = useCallback((weight) => {
    fireHaptic('selection', { enabled: haptic });
    setPlatesTarget(weight);
    setShowPlates(true);
  }, [haptic]);

  const onEditRest = useCallback((exIndex) => {
    fireHaptic('selection', { enabled: haptic });
    setEditingRestEx(exIndex);
    setShowRestOverride(true);
  }, [haptic]);

  const setActiveInputMetaRef = useCallback((meta) => {
    activeInputMeta.current = meta;
  }, []);

  const buildCopyPreviousInputs = useCallback(() => {
    const nextInputs = {};
    const unit = settings?.weightUnit || 'kg';
    Object.entries(state.ghostData || {}).forEach(([idxKey, ghost]) => {
      const idx = Number(idxKey);
      if (!Number.isInteger(idx)) return;
      const resolved = resolveEx(idx);
      const isDurationBased = isTimeBasedExercise(resolved);
      const firstSet = ghost?.sets?.[0];
      if (!firstSet) return;
      const sourceWeightKg = Number(firstSet.weight || 0);
      const sourceReps = Number(firstSet.reps || 0);
      const sourceDuration = Number(firstSet.durationSec || firstSet.reps || 0);
      nextInputs[idx] = {
        weight: sourceWeightKg > 0 ? String(convertKgToUnit(sourceWeightKg, unit, 2)) : '',
        reps: isDurationBased
          ? String(Math.max(0, Math.round(sourceDuration)))
          : (sourceReps > 0 ? String(sourceReps) : ''),
      };
    });
    return nextInputs;
  }, [state.ghostData, settings?.weightUnit, resolveEx]);

  // Build superset label map: group -> first exIndex in current display order
  const supersetFirstIndex = useMemo(() => {
    const map = {};
    orderedIndices.forEach(exIndex => {
      const g = state.supersetGroups[exIndex];
      if (g && map[g] === undefined) map[g] = exIndex;
    });
    return map;
  }, [orderedIndices, state.supersetGroups]);
  // When keyboard is open, add full keyboard height + buffer so last exercise stays visible
  const listKeyboardPad = keyboardInset > 0 ? keyboardInset + 16 : 0;

  const warmupExercises = useMemo(
    () => (day?.exercises || []).filter(e => e.isWarmup),
    [day?.exercises]
  );

  const listHeader = useMemo(() => (
    <View>
      {warmupExercises.length > 0 && (
        <View style={s.warmupSection}>
          <Text style={s.sectionLabel}>WARMUP / MOBILITY</Text>
          {warmupExercises.map((ex, i) => {
            const barW = activeGymProfile?.barWeight || settings.barWeight || 20;
            const targetKg = ex.targetWeight || null;
            const plateText = (targetKg && supportsPlateBreakdown(ex))
              ? getPlateText(targetKg, barW, activeGymProfile?.plates)
              : null;
            return (
              <View key={i} style={s.warmupRow}>
                <View style={{ flex: 1 }}>
                  <Text style={s.warmupName}>{ex.name}</Text>
                  {plateText && <Text style={s.warmupPlates}>{plateText}</Text>}
                </View>
                <Text style={s.warmupMeta}>{ex.sets}x {ex.reps}</Text>
              </View>
            );
          })}
        </View>
      )}
      <Text style={s.sectionLabel}>WORKING SETS</Text>
    </View>
  ), [warmupExercises, settings, colors, s, activeGymProfile]);

  // Auto-scroll to the focused exercise when the keyboard opens so it's always visible
  useEffect(() => {
    if (keyboardInset <= 0 || !activeInputMeta.current) return;
    if (Date.now() < suppressKeyboardScrollUntilRef.current) return;
    const idx = orderedIndices.indexOf(activeInputMeta.current.exIndex);
    if (idx < 0) return;
    const t = setTimeout(() => {
      try {
        flatListRef.current?.scrollToIndex({
          index: idx,
          animated: true,
          viewPosition: 0,
          viewOffset: -8,
        });
      } catch (_) {}
    }, 120); // wait for keyboard animation to settle
    return () => clearTimeout(t);
  }, [keyboardInset]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const hideSub = Keyboard.addListener('keyboardDidHide', () => {
      activeInputMeta.current = null;
      setFocusedField(null);
    });
    return () => hideSub.remove();
  }, []);

  const onExerciseDragEnd = ({ data }) => {
    setOrderedIndices(data);
    fireHaptic('selection', { enabled: haptic });
  };

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      {/* Header */}
      <View style={[s.header, { borderBottomColor: colors.faint }]}>
        <TouchableOpacity onPress={() => { fireHaptic('selection', { enabled: haptic }); setQuitAlert(true); }}>
          <Ionicons name="close" size={24} color={colors.muted} />
        </TouchableOpacity>
        <View style={{ alignItems: 'center', flex: 1, gap: 4 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Text style={[s.dayLabel, { color: day.color }]}>{day.label}</Text>
            <TouchableOpacity
              onPress={() => { fireHaptic('selection', { enabled: haptic }); setIsDeload(v => !v); }}
              style={[s.deloadPill, isDeload && { backgroundColor: '#7B7B7B44', borderColor: '#7B7B7B' }]}>
              <Text style={[s.deloadText, isDeload && { color: '#AAA' }]}>DELOAD</Text>
            </TouchableOpacity>
          </View>
          <Text style={s.dayName}>{day.name}</Text>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <TouchableOpacity onPress={() => { fireHaptic('selection', { enabled: haptic }); navigation.navigate('Tabs'); }}>
            <Ionicons name="chevron-down" size={22} color={colors.muted} />
          </TouchableOpacity>
          <TouchableOpacity style={[s.finishBtn, { backgroundColor: colors.accent }]} onPress={() => { fireHaptic('lightConfirm', { enabled: haptic }); finishWorkout(); }}>
            <Text style={s.finishText}>DONE</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Rest timer � isolated component so 500ms tick doesn't re-render WorkoutContent */}
      {showRestPanel ? (
        <RestTimerPanel
          restTimer={state.restTimer}
          accent={colors.accent}
          cardBg={colors.card}
          faint={colors.faint}
          mutedColor={colors.muted}
          restPanelStyle={restPanelStyle}
          restOverlayAnimStyle={s.restOverlayAnim}
          restOverlayStyle={s.restOverlay}
          restLabelStyle={s.restLabel}
          onSkip={handleSkip}
          onPause={handlePause}
          onResume={handleResume}
          onAdd30={handleAdd30}
          onTick={triggerCountdownHaptic}
          onTimerDone={() => {
            countdownHapticRef.current = null;
            dispatch({ type: 'SKIP_REST' });
            fireHaptic('timerDone', { enabled: haptic, force: true });
          }}
          syncKey={timerSyncKey}
        />
      ) : null}

      <View style={{ flex: 1 }}>
        <DraggableFlatList
          ref={flatListRef}
          data={orderedIndices}
          keyExtractor={(item) => String(item)}
          onDragEnd={onExerciseDragEnd}
          contentContainerStyle={{ padding: 16, paddingBottom: baseListBottomSpacing + listKeyboardPad }}
          scrollIndicatorInsets={{ bottom: Math.max(96, baseListBottomSpacing - 24) + listKeyboardPad }}
          onScrollToIndexFailed={({ index }) => {
            // Avoid jumping to list end; use a stable offset fallback instead.
            flatListRef.current?.scrollToOffset({
              offset: Math.max(0, index * 260),
              animated: true,
            });
          }}
          initialNumToRender={4}
          maxToRenderPerBatch={6}
          updateCellsBatchingPeriod={16}
          windowSize={6}
          activationDistance={14}
          keyboardDismissMode="on-drag"
          nestedScrollEnabled
          removeClippedSubviews={false}
          ListHeaderComponent={listHeader}
          ListFooterComponent={
            <TouchableOpacity
              style={[s.addExerciseBtn, { borderColor: colors.faint, backgroundColor: colors.card }]}
              onPress={() => {
                fireHaptic('selection', { enabled: haptic });
                setSwapModalEx(null);
                setShowAddExerciseModal(true);
              }}
            >
              <Ionicons name="add-circle-outline" size={16} color={colors.accent} />
              <Text style={[s.addExerciseBtnText, { color: colors.accent }]}>ADD EXERCISE</Text>
            </TouchableOpacity>
          }
          renderItem={useCallback(({ item: exIndex, drag, isActive }) => {
            const ex = resolveEx(exIndex);
            const heavy = isHeavy(ex.name);
            const base = heavy ? (settings.defaultRestHeavy || 180) : (settings.defaultRestNormal || 120);
            const restSec = restOverride[exIndex] !== undefined ? restOverride[exIndex] : base;
            const group = state.supersetGroups[exIndex];
            const groupColor = group ? GROUP_COLORS[group] : null;
            const borderColor = groupColor || day.color;
            return (
              <ExerciseCard
                exIndex={exIndex}
                ex={ex}
                originalEx={workingExercises[exIndex]}
                logged={state.setLog[exIndex] || EMPTY_ARRAY}
                inp={state.inputs[exIndex] || EMPTY_INPUT}
                exerciseNote={state.exerciseNotes[exIndex]}
                suggestion={progressionSuggestions[exIndex]}
                ghost={state.ghostData[exIndex]}
                group={group}
                groupColor={groupColor}
                isFirstInGroup={!!(group && supersetFirstIndex[group] === exIndex)}
                heavy={heavy}
                timeBased={isTimeBasedExercise(ex)}
                showPlateUi={supportsPlateBreakdown(ex)}
                borderColor={borderColor}
                restSec={restSec}
                dayHistory={dayHistory}
                colors={colors}
                settings={settings}
                activeGymProfile={activeGymProfile}
                drag={drag}
                isActive={isActive}
                onMenu={showExerciseMenu}
                onDispatch={dispatch}
                onRegisterRef={registerInputRef}
                onFocusInput={focusInput}
                onLogSet={logSet}
                onGenerateWarmups={generateWarmups}
                onShowPlates={onShowPlates}
                onEditRest={onEditRest}
                setActiveInputMetaRef={setActiveInputMetaRef}
                setFocusedField={setFocusedField}
                haptic={haptic}
              />
            );
          }, [state.setLog, state.inputs, state.exerciseNotes, state.supersetGroups, state.ghostData, state.swappedExercises, progressionSuggestions, colors, settings, haptic, workingExercises, restOverride, dayHistory, activeGymProfile, supersetFirstIndex, GROUP_COLORS, day.color])}
        />
      </View>
      <InlineToast visible={inlineToast.visible} text={inlineToast.text} intent={inlineToast.intent} />
      {focusedField === 'weight' ? (
        <View style={[s.keyboardChipRow, { backgroundColor: colors.surface, borderColor: colors.faint }]}>
          {[2.5, 5, 10].map((delta) => (
            <TouchableOpacity
              key={delta}
              style={[s.keyboardChipBtn, { borderColor: colors.faint, backgroundColor: colors.card }]}
              onPress={() => adjustActiveWeight(delta)}
            >
              <Text style={[s.keyboardChipText, { color: colors.text }]}>+{delta}</Text>
            </TouchableOpacity>
          ))}
        </View>
      ) : null}
      <KeyboardToolbar
        doneText="Done"
        onDoneCallback={() => {
          Keyboard.dismiss();
          activeInputMeta.current = null;
          setFocusedField(null);
        }}
        onPrevCallback={() => stepInput(-1)}
        onNextCallback={() => stepInput(1)}
      />

      {/* Modals */}
      <PlateModal
        visible={showPlates}
        targetKg={platesTarget}
        barWeight={activeGymProfile?.barWeight || settings.barWeight || 20}
        weightUnit={settings?.weightUnit || 'kg'}
        onClose={() => setShowPlates(false)}
      />

      <RestOverrideModal
        visible={showRestOverride}
        current={editingRestEx !== null
          ? (restOverride[editingRestEx] !== undefined ? restOverride[editingRestEx]
            : (isHeavy(resolveEx(editingRestEx)?.name)
              ? (settings.defaultRestHeavy || 180)
              : (settings.defaultRestNormal || 120)))
          : 120}
        onSave={(secs) => { if (editingRestEx !== null) setRestOverride(prev => ({ ...prev, [editingRestEx]: secs })); }}
        onClose={() => setShowRestOverride(false)}
        colors={colors}
      />

      <CopyPreviousModal
        visible={showCopyModal}
        onCopy={() => {
          const nextInputs = buildCopyPreviousInputs();
          dispatch({ type: 'COPY_PREVIOUS_CUSTOM', inputs: nextInputs });
          setShowCopyModal(false);
        }}
        onFresh={() => setShowCopyModal(false)} />

      <SupersetModal
        visible={supersetModalEx !== null}
        currentGroup={supersetModalEx !== null ? state.supersetGroups[supersetModalEx] : null}
        onAssign={(group) => {
          if (supersetModalEx !== null) {
            dispatch({ type: 'ASSIGN_SUPERSET', exIndex: supersetModalEx, group });
            fireHaptic('selection', { enabled: haptic });
          }
        }}
        onClose={() => setSupersetModalEx(null)} />

      <SwapModal
        visible={swapModalEx !== null}
        exercise={swapModalEx !== null ? resolveEx(swapModalEx) : null}
        onSwap={(newEx) => { if (swapModalEx !== null) handleSwap(swapModalEx, newEx); }}
        onClose={() => setSwapModalEx(null)}
        onCreateExercise={(name) => { setSwapModalEx(null); navigation.navigate('CreateExercise', { initialName: name }); }}
        onQuickAdd={(name, preferredMuscle) => quickAddExerciseFromSearch(name, 'swap', preferredMuscle)}
  colors={colors} />

      <SwapModal
        visible={showAddExerciseModal}
        exercise={null}
        mode="library"
        onSwap={(newEx) => addExerciseToSession(newEx)}
        onClose={() => setShowAddExerciseModal(false)}
        onCreateExercise={(name) => { setShowAddExerciseModal(false); navigation.navigate('CreateExercise', { initialName: name }); }}
        onQuickAdd={(name, preferredMuscle) => quickAddExerciseFromSearch(name, 'add', preferredMuscle)}
        colors={colors}
      />

      {/* Rate prompt */}
      <CustomAlert
        visible={ratePrompt}
        title="Enjoying IRONLOG?"
        message={`You've completed ${settings.completedWorkoutCount || 0} workouts! If you're loving the app, a rating helps a lot.`}
        onDismiss={() => setRatePrompt(false)}
        buttons={[
          { text: 'Never', style: 'cancel', onPress: () => { updateSettings({ ...settings, neverAskToRate: true }); setRatePrompt(false); } },
          { text: 'Later', style: 'default', onPress: () => setRatePrompt(false) },
          { text: 'Rate', style: 'default', onPress: () => { setRatePrompt(false); require('react-native').Linking.openURL('https://play.google.com/store/apps/details?id=com.ironlog.app'); } },
        ]} />

      {/* Quit confirmation */}
      <CustomAlert
        visible={quitAlert}
        title="Quit Workout?"
        message="All logged sets will be lost."
        onDismiss={() => setQuitAlert(false)}
        buttons={[
          { text: 'Minimize', style: 'default', onPress: () => navigation.goBack() },
          { text: 'Stay', style: 'cancel', onPress: () => setQuitAlert(false) },
          { text: 'Quit', style: 'destructive', onPress: async () => { clearOngoingWorkoutNotification().catch(() => {}); await clearActiveSession(sessionKey).catch(() => {}); setBanner(null); navigation.goBack(); } },
        ]} />

      {/* Workout completion summary */}
      <CustomAlert
        visible={!!completionSummary}
        title={completionPrCount > 0 ? 'SESSION LOCKED IN' : 'WORKOUT COMPLETE'}
        message={completionSummary || ''}
        onDismiss={() => { setCompletionSummary(null); setCompletionPrCount(0); if (!nextTargets) setShowShareModal(true); }}
        backdropOverlay={(() => {
          const celebration = resolveCelebrationConfig(settings);
          if (!celebration.enabled) return null;
          const baseCount = celebration.style === 'wave' ? 55 : 85;
          return (
            <GoldConfettiBurst
              enabled={!!completionSummary}
              fullScreen
              variant={celebration.style}
              colorMode={completionPrCount > 0 ? 'multicolor' : celebration.colorMode}
              count={completionPrCount > 0 ? Math.round(baseCount * 0.82) : Math.round(baseCount * 0.78)}
              themeColors={colors}
              durationScale={1.0}
            />
          );
        })()}
        buttons={[{ text: 'VIEW', style: 'default', onPress: () => { setCompletionSummary(null); setCompletionPrCount(0); if (!nextTargets) setShowShareModal(true); } }]} />

      {/* F28 Share card modal */}
      <Modal visible={showShareModal} transparent animationType="fade" onRequestClose={() => { setShowShareModal(false); navigation.goBack(); }}>
        <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.9)', justifyContent: 'center', alignItems: 'center', padding: 16 }}>
          <WorkoutShareCard
            ref={shareCardRef}
            workout={completedWorkout}
            history={history}
            summaryText={completedWorkout?.summaryText}
          />
          <View style={{ flexDirection: 'row', gap: 12, marginTop: 16, width: '100%', paddingHorizontal: 16 }}>
            <TouchableOpacity
              style={{ flex: 1, padding: 16, borderWidth: 1, borderColor: '#333', alignItems: 'center', borderRadius: CONTROL_RADIUS }}
              onPress={() => { setShowShareModal(false); navigation.goBack(); }}>
              <Text style={{ color: '#888', fontWeight: '700', letterSpacing: 1 }}>SKIP</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={{ flex: 2, padding: 16, backgroundColor: colors.accent, alignItems: 'center', flexDirection: 'row', justifyContent: 'center', gap: 8, borderRadius: CONTROL_RADIUS }}
              onPress={async () => {
                try {
                  fireHaptic('lightConfirm', { enabled: haptic });
                  const uri = await captureRef(shareCardRef, { format: 'png', quality: 1.0 });
                  if (await Sharing.isAvailableAsync()) await Sharing.shareAsync(uri, { mimeType: 'image/png' });
                } catch (e) { setAlertConfig({ title: 'Share failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] }); }
              }}>
              <Ionicons name="share-outline" size={16} color="#fff" />
              <Text style={{ color: colors.textOnAccent || '#fff', fontWeight: '800', fontSize: 13, letterSpacing: 2 }}>SHARE</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>

      {/* F24 Next-session targets */}
      <Modal visible={!!nextTargets && !completionSummary} transparent animationType="slide" onRequestClose={() => { setNextTargets(null); setShowShareModal(true); }}>
        <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.6)' }}>
          <View style={[s.targetSheet, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[s.targetTitle, { color: colors.text }]}>NEXT SESSION TARGETS</Text>
            <Text style={[s.targetSub, { color: colors.muted }]}>Suggested progression for your next workout</Text>
            <ScrollView style={{ maxHeight: 280 }} contentContainerStyle={{ gap: 8, paddingVertical: 8 }}>
              {(nextTargets || []).map((t, i) => (
                <View key={i} style={[s.targetRow, { borderColor: colors.faint }]}>
                  <View style={{ flex: 1, marginRight: 12 }}>
                    <Text style={[s.targetName, { color: colors.text }]} numberOfLines={1}>{t.name}</Text>
                    <Text style={[s.targetReason, { color: colors.muted }]} numberOfLines={2}>
                      {t.action ? `${String(t.action).toUpperCase()} � ` : ''}{t.rationale || (t.plateau ? `Plateau: ${t.plateau}` : t.deload ? t.deload : 'Keep the trend moving')}
                    </Text>
                  </View>
                  <Text style={[s.targetVal, { color: t.action === 'reduce' ? '#FF8E8E' : colors.accent }]}>{Number(t.weight || 0) > 0 ? formatWeightFromKg(t.weight, settings?.weightUnit || 'kg') : 'BW'} x {Math.max(1, Number(t.reps || 0))}</Text>
                </View>
              ))}
            </ScrollView>
            <View style={{ flexDirection: 'row', gap: 10 }}>
              <TouchableOpacity style={[s.targetBtn, { flex: 1, backgroundColor: colors.accentSoft, borderWidth: 1, borderColor: colors.accent }]} onPress={() => { setNextTargets(null); setShowShareModal(true); }}>
                <Ionicons name="share-outline" size={16} color={colors.accent} />
                <Text style={{ color: colors.accent, fontWeight: '800', fontSize: 12, letterSpacing: 1 }}>SHARE</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[s.targetBtn, { flex: 1, backgroundColor: colors.accent }]} onPress={() => { setNextTargets(null); navigation.goBack(); }}>
                <Text style={{ color: colors.textOnAccent || '#fff', fontWeight: '800', fontSize: 13, letterSpacing: 2 }}>DONE</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
      <PRHud pr={prHud} onDismiss={() => setPrHud(null)} />
    </View>
  );
}

//  ROOT EXPORT 

export default function ActiveWorkoutScreen({ route, navigation }) {
  const { planIndex = 0, dayIndex = 0 } = route.params || {};
  const { plans } = useWatermelonAppData();
  const colors = useTheme();

  const safePlans = Array.isArray(plans) ? plans : [];
  const resolvedPlanIndex = Number.isInteger(planIndex) ? planIndex : 0;
  const plan = safePlans[resolvedPlanIndex];
  const safeDays = Array.isArray(plan?.days) ? plan.days : [];
  const resolvedDayIndex = Number.isInteger(dayIndex) ? dayIndex : 0;
  const day = safeDays[resolvedDayIndex];
  const safeExercises = Array.isArray(day?.exercises) ? day.exercises : [];

  if (!day) {
    return (
      <View style={[s.container, { backgroundColor: colors.bg }]}>
        <Text style={{ color: colors.text, padding: 20 }}>Workout not found.</Text>
      </View>
    );
  }

  return (
    <WorkoutProvider exercises={safeExercises.filter(e => e && !e.isWarmup)}>
      <WorkoutContent plan={plan} day={day} planIndex={resolvedPlanIndex} dayIndex={resolvedDayIndex} navigation={navigation} />
    </WorkoutProvider>
  );
}

//  STYLES 

function makeStyles(colors) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.bg },
    header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingTop: 50, paddingBottom: 14, borderBottomWidth: 1 },
    dayLabel: { fontSize: 9, letterSpacing: 3 },
    dayName: { fontSize: 20, fontWeight: '900', letterSpacing: -1, color: colors.text },
    finishBtn: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 14 },
    finishText: { fontSize: 11, fontWeight: '800', color: colors.textOnAccent || '#fff', letterSpacing: 2 },
    deloadPill: { borderWidth: 1, borderColor: colors.cardBorder, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
    deloadText: { fontSize: 8, fontWeight: '800', letterSpacing: 2, color: colors.subtext },
    targetSheet: { borderTopWidth: 1, padding: 24, borderTopLeftRadius: SHEET_RADIUS, borderTopRightRadius: SHEET_RADIUS },
    targetTitle: { fontSize: 16, fontWeight: '900', letterSpacing: 2, marginBottom: 4 },
    targetSub: { fontSize: 12, marginBottom: 12 },
    targetRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1 },
    targetName: { fontSize: 14, fontWeight: '600', flex: 1, marginRight: 12 },
    targetReason: { fontSize: 10, marginTop: 4, lineHeight: 14 },
    targetVal: { fontSize: 15, fontWeight: '900' },
    targetBtn: { marginTop: 16, padding: 16, alignItems: 'center', flexDirection: 'row', justifyContent: 'center', gap: 6 },
    pbBanner: { backgroundColor: '#FFD70022', borderBottomWidth: 1, borderBottomColor: '#FFD70044', padding: 12, alignItems: 'center' },
    pbBannerText: { fontSize: 13, fontWeight: '700', color: '#FFD700' },
    restOverlayAnim: { zIndex: 10 }, // only geometry � NO PlatformColor (would crash Reanimated on Monet)
    restOverlay: { borderBottomWidth: 1, alignItems: 'center', paddingVertical: 4 },
    restLabel: { fontSize: 10, letterSpacing: 5, marginTop: 8 },
    warmupSection: { padding: 16, borderBottomWidth: 1, borderBottomColor: colors.faint },
    sectionLabel: { fontSize: 9, letterSpacing: 4, color: colors.muted, marginBottom: 8 },
    warmupRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 },
    warmupName: { fontSize: 14, color: colors.subtext },
    warmupPlates: { fontSize: 10, color: colors.muted, marginTop: 2 },
    warmupMeta: { fontSize: 12, color: colors.muted },
    plateHint: { fontSize: 10, color: colors.muted, paddingLeft: 48, paddingBottom: 4, letterSpacing: 0.3 },
    supersetChip: { marginHorizontal: 12, marginTop: 12, paddingHorizontal: 10, paddingVertical: 4, borderWidth: 1, alignSelf: 'flex-start', borderRadius: 999 },
    supersetChipText: { fontSize: 9, fontWeight: '800', letterSpacing: 2 },
    exCard: { margin: 12, marginBottom: 0, borderWidth: 1, borderLeftWidth: 3, borderRadius: CARD_RADIUS, overflow: 'hidden' },
    exHeader: { flexDirection: 'row', alignItems: 'flex-start', paddingHorizontal: 16, paddingTop: 14, paddingBottom: 8 },
    exName: { fontSize: 19, fontWeight: '900', color: colors.text, letterSpacing: -0.4, flexShrink: 1, lineHeight: 23 },
    exTarget: { fontSize: 11, color: colors.muted, marginTop: 3, lineHeight: 15 },
    exTargetHint: { fontSize: 10, marginTop: 4, lineHeight: 14 },
    groupBadge: { borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
    groupBadgeText: { fontSize: 10, fontWeight: '900', letterSpacing: 1 },
    heavyBadge: { backgroundColor: '#FF450022', borderWidth: 1, borderColor: '#FF450044', paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
    heavyText: { fontSize: 8, color: '#FF4500', fontWeight: '700', letterSpacing: 1 },
    overloadBadge: { backgroundColor: '#FFD70022', borderWidth: 1, borderColor: '#FFD70044', paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
    overloadText: { fontSize: 8, color: '#FFD700', fontWeight: '700', letterSpacing: 1 },
    targetChip: { borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3, borderRadius: 999 },
    targetChipText: { fontSize: 8, fontWeight: '800', letterSpacing: 1.2 },
    noteInput: { marginHorizontal: 16, marginBottom: 8, fontSize: 12, color: colors.subtext, borderWidth: 1, borderColor: colors.faint, padding: 10, minHeight: 42, borderRadius: 16 },
    ghostContainer: { marginHorizontal: 16, marginBottom: 8, padding: 10, borderWidth: 1, borderColor: colors.faint, borderStyle: 'dashed', borderRadius: 16 },
    ghostLabel: { fontSize: 8, letterSpacing: 2, color: colors.muted, marginBottom: 4 },
    ghostSet: { fontSize: 12, color: colors.subtext, fontWeight: '600' },
    loggedSets: { marginHorizontal: 16, marginBottom: 4, marginTop: 2 },
    inputRow: { flexDirection: 'row', alignItems: 'flex-end', paddingHorizontal: 16, paddingTop: 10, paddingBottom: 8, gap: 10 },
    inputGroup: { flex: 1 },
    inputFieldWrap: { position: 'relative', justifyContent: 'center' },
    inputLabel: { fontSize: 9, letterSpacing: 2.4, color: colors.subtext, marginBottom: 4, fontWeight: '700' },
    input: { flex: 1, borderBottomWidth: 2, borderBottomColor: colors.faint, fontSize: 22, fontWeight: '900', color: colors.text, paddingVertical: 9, textAlign: 'center', minHeight: 48 },
    inputWithPlate: { paddingRight: 28 },
    plateBtn: { position: 'absolute', right: 0, top: 8, width: 28, height: 28, alignItems: 'center', justifyContent: 'center' },
    logBtn: { backgroundColor: colors.accent, paddingHorizontal: 20, paddingVertical: 13, borderRadius: 16, minHeight: 48, justifyContent: 'center' },
    logBtnText: { fontSize: 12, fontWeight: '800', color: colors.textOnAccent || '#fff', letterSpacing: 1.5 },
    quickAddBtn: { marginHorizontal: 16, marginBottom: 4, paddingVertical: 8, borderWidth: 1, borderColor: colors.faint, borderStyle: 'dashed', alignItems: 'center', borderRadius: 16 },
    quickAddText: { fontSize: 10, color: colors.muted, letterSpacing: 1.4, fontWeight: '700' },
    addExerciseBtn: { marginHorizontal: 12, marginTop: 12, marginBottom: 10, paddingVertical: 12, borderWidth: 1, borderStyle: 'dashed', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: 18 },
    addExerciseBtnText: { fontSize: 11, fontWeight: '800', letterSpacing: 1.2 },
    restRow: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 16, paddingBottom: 12 },
    restRowText: { fontSize: 10, color: colors.muted, letterSpacing: 0.8 },
    keyboardChipRow: {
      flexDirection: 'row',
      gap: 10,
      paddingHorizontal: 12,
      paddingVertical: 8,
      borderTopWidth: 1,
      borderBottomWidth: 1,
      justifyContent: 'center',
    },
    keyboardChipBtn: {
      borderWidth: 1,
      borderRadius: 999,
      paddingHorizontal: 14,
      paddingVertical: 6,
      minWidth: 54,
      alignItems: 'center',
    },
    keyboardChipText: {
      fontSize: 12,
      fontWeight: '800',
      letterSpacing: 0.6,
    },
  });
}


