import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  PanResponder,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import useWatermelonHome from '../hooks/useWatermelonHome';
import useWatermelonManualRecovery from '../hooks/useWatermelonManualRecovery';
import { useTheme } from '../context/ThemeContext';
import useDeferredScreenReady from '../hooks/useDeferredScreenReady';
import BodyMapSVG from '../components/BodyMapSVG';
import { computeMuscleAnalytics } from '../domain/intelligence/trainingAnalyticsEngine';
import { buildReadinessSuggestions, computeRecoveryScore } from '../domain/intelligence/recoveryReadinessEngine';
import { getExerciseIndex } from '../services/ExerciseLibraryService';
import { getMuscleAtTouch } from '../utils/muscleMapHitTest';
import SegmentedTabs from '../components/ui/SegmentedTabs';
import { BottomSheetModal, BottomSheetView } from '@gorhom/bottom-sheet';

const REGION_TO_GROUP = {
  chest: 'chest',
  shoulders: 'shoulders',
  rearDelts: 'rearDelts',
  arms: 'arms',
  core: 'core',
  quads: 'legs',
  hamstrings: 'legs',
  calves: 'legs',
  back: 'back',
};

const RECOVERY_COLORS = {
  recovering: '#E88787',
  partial: '#E5C46A',
  ready: '#79C98D',
  untrained: null,
};
const CARD_RADIUS = 14;
const RECOVERY_ACTIONS = {
  fresh: 'Train',
  recovering: 'Maintain',
  fatigued: 'Back Off',
};
const RECOVERY_RED_THRESHOLD = 0.72;
const RECOVERY_PARTIAL_THRESHOLD = 0.9;

function getRegionColors(groupReadiness, colors) {
  const result = {};
  Object.keys(REGION_TO_GROUP).forEach((region) => {
    const group = REGION_TO_GROUP[region];
    const readiness = typeof groupReadiness?.[group] === 'number'
      ? groupReadiness[group]
      : region === 'rearDelts' && typeof groupReadiness?.shoulders === 'number'
        ? groupReadiness.shoulders
        : 1;
    let status = 'ready';
    if (readiness < RECOVERY_RED_THRESHOLD) status = 'recovering';
    else if (readiness < RECOVERY_PARTIAL_THRESHOLD) status = 'partial';
    result[region] = RECOVERY_COLORS[status] || colors.faint;
  });
  return result;
}

export default function RecoveryMapScreen({ navigation, route }) {
  const { history, plans } = useWatermelonHome();
  const { manualRecoveryInput, saveManualRecovery } = useWatermelonManualRecovery();
  const colors = useTheme();
  const analyticsReady = useDeferredScreenReady({ minDelayMs: 24 });
  const [view, setView] = useState('front');
  const [windowKey, setWindowKey] = useState('7d');
  const [mapSize, setMapSize] = useState({ width: 1, height: 1 });
  const [tooltip, setTooltip] = useState(null);
  const [selectedRegion, setSelectedRegion] = useState(null);
  const hideTimerRef = useRef(null);
  const gestureRef = useRef({ startedAt: 0, moved: false, startX: 0, startY: 0, muscle: null });
  const regionSheetRef = useRef(null);
  const [libraryIndex, setLibraryIndex] = useState([]);
  const [showManualModal, setShowManualModal] = useState(false);
  const [manualDraft, setManualDraft] = useState({
    soreness: 0,
    sleepQuality: 0,
    energy: 0,
    notes: '',
  });
  const fallbackReadiness = route?.params?.groupReadiness || {};
  const activePlan = plans[0];
  const safePlanDays = useMemo(
    () => (Array.isArray(activePlan?.days) ? activePlan.days : []),
    [activePlan]
  );
  const programHasExercises = useMemo(() => {
    return safePlanDays.some((day) => Array.isArray(day?.exercises) && day.exercises.length > 0);
  }, [safePlanDays]);
  const effectiveWindow = windowKey === 'program' && !programHasExercises ? '7d' : windowKey;

  useEffect(() => {
    if (!analyticsReady) return undefined;
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
  }, [analyticsReady]);

  const analytics = useMemo(() => {
    if (!analyticsReady) return { readiness: {}, groups: {}, muscles: {} };
    return computeMuscleAnalytics({
      history,
      exerciseIndex: libraryIndex,
      activePlan,
      window: effectiveWindow,
    });
  }, [activePlan, analyticsReady, effectiveWindow, history, libraryIndex]);

  useEffect(() => {
    if (windowKey === 'program' && !programHasExercises) {
      setWindowKey('7d');
    }
  }, [programHasExercises, windowKey]);
  const groupReadiness = Object.keys(analytics.readiness || {}).length ? analytics.readiness : fallbackReadiness;
  const recoveryScore = useMemo(
    () => {
      if (!analyticsReady && !Object.keys(fallbackReadiness).length) {
        return {
          score: '...',
          state: 'recovering',
          explanation: 'Preparing recovery map...',
        };
      }
      return computeRecoveryScore({ readiness: groupReadiness, manualInput: manualRecoveryInput });
    },
    [analyticsReady, fallbackReadiness, groupReadiness, manualRecoveryInput]
  );
  const readinessSuggestions = useMemo(
    () => (analyticsReady || Object.keys(fallbackReadiness).length
      ? buildReadinessSuggestions({ readiness: groupReadiness })
      : []),
    [analyticsReady, fallbackReadiness, groupReadiness]
  );

  const regionColors = useMemo(
    () => getRegionColors(groupReadiness, colors),
    [groupReadiness, colors]
  );

  const readinessConfidence = useMemo(() => {
    const base = analytics.totalWorkingSets > 0 ? 'Volume + recency model' : 'Low-confidence (insufficient session data)';
    if (manualRecoveryInput?.recordedAt) return `${base} + manual check-in`;
    return base;
  }, [analytics.totalWorkingSets, manualRecoveryInput?.recordedAt]);

  const actionDirective = useMemo(() => RECOVERY_ACTIONS[recoveryScore.state] || 'Maintain', [recoveryScore.state]);

  const openRegionExplain = useCallback((muscle) => {
    if (!muscle) return;
    const group = REGION_TO_GROUP[muscle.slug] || REGION_TO_GROUP[muscle.id] || null;
    const readinessValue = typeof groupReadiness?.[group] === 'number'
      ? groupReadiness[group]
      : group === 'rearDelts' && typeof groupReadiness?.shoulders === 'number'
        ? groupReadiness.shoulders
        : null;
    let recommendation = 'Maintain current load and monitor response.';
    if (typeof readinessValue === 'number') {
      if (readinessValue < RECOVERY_RED_THRESHOLD) recommendation = 'Back off hard loading here for 24-48h.';
      else if (readinessValue < RECOVERY_PARTIAL_THRESHOLD) recommendation = 'Train with moderate effort and avoid forced reps.';
      else recommendation = 'Train this region normally or use it as a priority focus.';
    }
    setSelectedRegion({
      label: muscle.label || muscle.slug || 'Region',
      slug: muscle.slug || muscle.id || '',
      group: group || 'unknown',
      readinessValue,
      recommendation,
      source: readinessConfidence,
    });
    regionSheetRef.current?.present();
  }, [groupReadiness, readinessConfidence]);

  const revealRegionFromTouch = (evt) => {
    if (!mapSize.width || !mapSize.height) return;
    const { locationX, locationY } = evt.nativeEvent;
    const muscle = getMuscleAtTouch({
      view,
      mapWidth: mapSize.width,
      mapHeight: mapSize.height,
      locationX,
      locationY,
    });

    if (!muscle) {
      setTooltip(null);
      gestureRef.current.muscle = null;
      return;
    }

    gestureRef.current.muscle = muscle;
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    const estimatedWidth = Math.min(170, Math.max(84, muscle.label.length * 8 + 28));
    setTooltip({
      label: muscle.label,
      x: Math.min(mapSize.width - estimatedWidth - 8, Math.max(8, locationX - estimatedWidth / 2)),
      y: Math.max(8, locationY - 40),
    });
  };

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: () => true,
        onPanResponderGrant: (evt) => {
          const { locationX, locationY } = evt.nativeEvent;
          gestureRef.current = { startedAt: Date.now(), moved: false, startX: locationX, startY: locationY, muscle: null };
          revealRegionFromTouch(evt);
        },
        onPanResponderMove: (evt) => {
          const { locationX, locationY } = evt.nativeEvent;
          if (Math.abs(locationX - gestureRef.current.startX) > 8 || Math.abs(locationY - gestureRef.current.startY) > 8) {
            gestureRef.current.moved = true;
          }
          revealRegionFromTouch(evt);
        },
        onPanResponderRelease: () => {
          const didTap = !gestureRef.current.moved && (Date.now() - gestureRef.current.startedAt) < 260;
          if (didTap && gestureRef.current.muscle) {
            openRegionExplain(gestureRef.current.muscle);
          }
          if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
          hideTimerRef.current = setTimeout(() => setTooltip(null), 700);
        },
      }),
    [mapSize, openRegionExplain, view]
  );

  useEffect(() => {
    if (!showManualModal) return;
    setManualDraft({
      soreness: Number(manualRecoveryInput?.soreness || 0),
      sleepQuality: Number(manualRecoveryInput?.sleepQuality || 0),
      energy: Number(manualRecoveryInput?.energy || 0),
      notes: manualRecoveryInput?.notes || '',
    });
  }, [manualRecoveryInput, showManualModal]);

  return (
    <View style={[s.scrollRoot, { backgroundColor: colors.bg }]}>
    <ScrollView contentContainerStyle={s.container}>
      <View style={[s.scoreCard, { borderColor: colors.cardBorder, backgroundColor: colors.card }]}>
        <Text style={[s.scoreLabel, { color: colors.muted }]}>RECOVERY SCORE</Text>
        <Text style={[s.scoreValue, { color: recoveryScore.state === 'fatigued' ? '#E88787' : recoveryScore.state === 'recovering' ? '#E5C46A' : '#79C98D' }]}>
          {recoveryScore.score}
        </Text>
        <Text style={[s.scoreHint, { color: colors.subtext }]}>{recoveryScore.explanation}</Text>
        {readinessSuggestions[0] ? (
          <Text style={[s.scoreHint, { color: colors.muted }]}>{readinessSuggestions[0]}</Text>
        ) : null}
        <View style={[s.metaRow, { borderColor: colors.faint }]}>
          <Text style={[s.metaChip, { color: colors.text, borderColor: colors.faint }]}>Action: {actionDirective}</Text>
          <Text style={[s.metaChip, { color: colors.subtext, borderColor: colors.faint }]} numberOfLines={1}>
            Confidence: {readinessConfidence}
          </Text>
        </View>
        {!analyticsReady ? (
          <Text style={[s.scoreHint, { color: colors.muted }]}>Loading real muscle readiness after navigation settles.</Text>
        ) : null}
        <TouchableOpacity style={[s.manualBtn, { borderColor: colors.accent }]} onPress={() => setShowManualModal(true)}>
          <Text style={[s.manualBtnText, { color: colors.accent }]}>UPDATE MANUAL CHECK-IN</Text>
        </TouchableOpacity>
      </View>

      <SegmentedTabs
        items={[
          { id: 'current_workout', label: 'Workout' },
          { id: '7d', label: '7D' },
          { id: '30d', label: '30D' },
          { id: 'program', label: 'Program' },
        ]}
        value={windowKey}
        disabledIds={programHasExercises ? [] : ['program']}
        onChange={(key) => {
          setWindowKey(key);
          setTooltip(null);
        }}
      />

      <View style={{ marginTop: 10 }}>
        <SegmentedTabs
          items={[
            { id: 'front', label: 'Front' },
            { id: 'back', label: 'Back' },
          ]}
          value={view}
          onChange={(side) => {
            setView(side);
            setTooltip(null);
          }}
          compact
        />
      </View>

      <Text style={[s.helpText, { color: colors.muted }]}>
        Slide over the body map to inspect regions. Tap a region to explain why it is train / maintain / back-off.
      </Text>

      <View
        style={[s.mapCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
        onLayout={(e) => {
          const { width, height } = e.nativeEvent.layout;
          setMapSize({ width, height });
        }}
        {...panResponder.panHandlers}
      >
        <BodyMapSVG
          view={view}
          regionColors={regionColors}
          defaultColor={colors.subtext}
          width="100%"
          height="100%"
        />
        {tooltip ? (
          <View
            style={[
              s.tooltip,
              {
                left: tooltip.x,
                top: tooltip.y,
                backgroundColor: colors.surface,
                borderColor: colors.accent,
              },
            ]}
          >
            <Ionicons name="locate" size={11} color={colors.accent} />
            <Text style={[s.tooltipText, { color: colors.text }]}>{tooltip.label}</Text>
          </View>
        ) : null}
      </View>

      <TouchableOpacity
        style={[s.analyticsBtn, { backgroundColor: colors.accent }]}
        onPress={() => navigation.navigate('VolumeAnalytics')}
      >
        <Ionicons name="analytics-outline" size={15} color="#fff" />
        <Text style={s.analyticsBtnText}>OPEN VOLUME ANALYTICS</Text>
      </TouchableOpacity>

    </ScrollView>

      <BottomSheetModal
        ref={regionSheetRef}
        snapPoints={['42%']}
        backgroundStyle={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.cardBorder }}
        handleIndicatorStyle={{ backgroundColor: colors.faint }}
      >
        <BottomSheetView style={s.regionSheet}>
          <Text style={[s.sheetTitle, { color: colors.text }]}>{selectedRegion?.label || 'Region'}</Text>
          <Text style={[s.sheetSub, { color: colors.muted }]}>Source: {selectedRegion?.source || 'Readiness model'}</Text>
          <Text style={[s.sheetSub, { color: colors.subtext }]}>
            Readiness: {typeof selectedRegion?.readinessValue === 'number' ? `${Math.round(selectedRegion.readinessValue * 100)}%` : 'Not enough data'}
          </Text>
          <View style={[s.sheetRecommendation, { borderColor: colors.faint, backgroundColor: colors.bg }]}>
            <Text style={[s.sheetRecommendationTitle, { color: colors.accent }]}>
              {typeof selectedRegion?.readinessValue === 'number' && selectedRegion.readinessValue < RECOVERY_RED_THRESHOLD
                ? 'Back Off'
                : typeof selectedRegion?.readinessValue === 'number' && selectedRegion.readinessValue < RECOVERY_PARTIAL_THRESHOLD
                  ? 'Maintain'
                  : 'Train'}
            </Text>
            <Text style={[s.sheetRecommendationText, { color: colors.text }]}>{selectedRegion?.recommendation || 'Keep monitoring this region.'}</Text>
          </View>
        </BottomSheetView>
      </BottomSheetModal>

      <Modal visible={showManualModal} transparent animationType="fade" onRequestClose={() => setShowManualModal(false)}>
        <View style={s.overlay}>
          <View style={[s.modalCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[s.modalTitle, { color: colors.text }]}>Manual Recovery Input</Text>
            {[
              ['soreness', 'Soreness (1-5)'],
              ['sleepQuality', 'Sleep (1-5)'],
              ['energy', 'Energy (1-5)'],
            ].map(([key, label]) => (
              <View key={key} style={{ marginBottom: 10 }}>
                <Text style={[s.inputLabel, { color: colors.subtext }]}>{label}</Text>
                <View style={{ flexDirection: 'row', gap: 8 }}>
                  {[1, 2, 3, 4, 5].map((value) => {
                    const active = Number(manualDraft[key] || 0) === value;
                    return (
                      <TouchableOpacity
                        key={`${key}:${value}`}
                        style={[
                          s.rateBtn,
                          { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' },
                        ]}
                        onPress={() => setManualDraft((prev) => ({ ...prev, [key]: value }))}
                      >
                        <Text style={{ color: active ? colors.accent : colors.muted, fontSize: 12, fontWeight: '800' }}>{value}</Text>
                      </TouchableOpacity>
                    );
                  })}
                </View>
              </View>
            ))}
            <TextInput
              style={[s.notesInput, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
              value={manualDraft.notes}
              onChangeText={(value) => setManualDraft((prev) => ({ ...prev, notes: value }))}
              placeholder="Optional note..."
              placeholderTextColor={colors.muted}
              multiline
            />
            <View style={s.modalActions}>
              <TouchableOpacity style={[s.modalBtn, { borderColor: colors.faint }]} onPress={() => setShowManualModal(false)}>
                <Text style={{ color: colors.muted }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[s.modalBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
                onPress={async () => {
                  await saveManualRecovery(manualDraft);
                  setShowManualModal(false);
                }}
              >
                <Text style={{ color: colors.accent, fontWeight: '800' }}>Save</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  scrollRoot: { flex: 1 },
  container: { padding: 16, paddingBottom: 32 },
  scoreCard: { borderWidth: 1, borderRadius: 18, padding: 14, marginBottom: 12 },
  scoreLabel: { fontSize: 9, letterSpacing: 2.3, fontWeight: '800' },
  scoreValue: { fontSize: 30, fontWeight: '900', marginTop: 4 },
  scoreHint: { fontSize: 11, marginTop: 4, lineHeight: 16 },
  metaRow: { marginTop: 10, borderWidth: 1, borderRadius: 14, padding: 10, flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  metaChip: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 5, fontSize: 10, fontWeight: '700', maxWidth: '100%' },
  manualBtn: { marginTop: 12, borderWidth: 1, paddingVertical: 10, alignItems: 'center', borderRadius: 14 },
  manualBtnText: { fontSize: 10, fontWeight: '800', letterSpacing: 1.2 },
  switchRow: { flexDirection: 'row', gap: 10, marginBottom: 10 },
  helpText: { fontSize: 12, marginBottom: 10 },
  mapCard: {
    borderWidth: 1,
    borderRadius: 18,
    overflow: 'hidden',
    height: 480,
  },
  tooltip: {
    position: 'absolute',
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  tooltipText: { fontSize: 11, fontWeight: '700', letterSpacing: 0.3 },
  analyticsBtn: {
    marginTop: 12,
    borderRadius: 16,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
  },
  analyticsBtnText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 1.2,
  },
  regionSheet: { paddingHorizontal: 18, paddingTop: 8, paddingBottom: 24, gap: 10 },
  sheetTitle: { fontSize: 20, fontWeight: '900' },
  sheetSub: { fontSize: 12, lineHeight: 17 },
  sheetRecommendation: { borderWidth: 1, borderRadius: 12, padding: 12, marginTop: 4 },
  sheetRecommendationTitle: { fontSize: 11, fontWeight: '800', letterSpacing: 1.1, marginBottom: 4 },
  sheetRecommendationText: { fontSize: 13, lineHeight: 18 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.84)', justifyContent: 'center', padding: 24 },
  modalCard: { borderWidth: 1, padding: 16, borderRadius: CARD_RADIUS },
  modalTitle: { fontSize: 16, fontWeight: '900', marginBottom: 12 },
  inputLabel: { fontSize: 11, marginBottom: 8 },
  rateBtn: { width: 34, height: 34, borderWidth: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 10 },
  notesInput: { borderWidth: 1, minHeight: 70, padding: 10, textAlignVertical: 'top', marginTop: 6, borderRadius: 10 },
  modalActions: { flexDirection: 'row', gap: 8, marginTop: 12 },
  modalBtn: { flex: 1, borderWidth: 1, paddingVertical: 10, alignItems: 'center', borderRadius: 10 },
});


