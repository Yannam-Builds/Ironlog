import React, { useContext, useState, useEffect, useMemo } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Modal, Image } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import RecoveryHeatmap from '../components/RecoveryHeatmap';
import { computeStimulusFatigue, analyzeVolume, buildHomeProgramIntelligence } from '../utils/intelligenceEngine';
import { decayFatigueHourly, computeReadiness, getGroupReadiness } from '../utils/recoveryModel';

const ONBOARDING_KEY = '@ironlog/onboardingComplete';

function getStreak(history) {
  if (!history.length) return 0;
  const days = [...new Set(history.map(h => h.date.split('T')[0]))].sort().reverse();
  const today = new Date().toISOString().split('T')[0];
  const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];
  if (days[0] !== today && days[0] !== yesterday) return 0;
  let streak = 0; let prev = days[0];
  for (const d of days) {
    const diff = (new Date(prev) - new Date(d)) / 86400000;
    if (diff <= 1) { streak++; prev = d; } else break;
  }
  return streak;
}

const HOME_GROUP_KEYS = ['core', 'arms', 'chest', 'legs', 'back', 'shoulders'];
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

function normalizeGroupKey(value) {
  const key = String(value || '')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '');
  return GROUP_ALIASES[key] || null;
}

function getSessionExerciseGroups(exercise) {
  const values = [];
  if (Array.isArray(exercise?.primaryMuscles)) values.push(...exercise.primaryMuscles);
  else if (exercise?.primaryMuscles) values.push(exercise.primaryMuscles);
  if (Array.isArray(exercise?.secondaryMuscles)) values.push(...exercise.secondaryMuscles);
  if (exercise?.primaryMuscle) values.push(exercise.primaryMuscle);
  if (exercise?.primary) values.push(exercise.primary);
  if (exercise?.muscle) values.push(exercise.muscle);
  if (exercise?.target) values.push(exercise.target);
  if (exercise?.targetMuscle) values.push(exercise.targetMuscle);
  if (exercise?.bodyPart) values.push(exercise.bodyPart);

  const groups = [];
  const seen = new Set();
  values.forEach((value) => {
    const group = normalizeGroupKey(value);
    if (!group || seen.has(group)) return;
    seen.add(group);
    groups.push(group);
  });
  return groups;
}

function getWorkingSetCount(exercise) {
  if (!exercise) return 0;
  if (Array.isArray(exercise.sets)) {
    return exercise.sets.filter((setItem) => (setItem?.type || 'normal') !== 'warmup').length;
  }
  if (typeof exercise.sets === 'number') return Math.max(0, exercise.sets);
  return 0;
}

function computeSetBasedGroupReadiness(history) {
  const stress = {};
  HOME_GROUP_KEYS.forEach((group) => { stress[group] = 0; });

  (history || []).forEach((session) => {
    const diffHours = (Date.now() - new Date(session?.date || 0).getTime()) / 3600000;
    if (!Number.isFinite(diffHours) || diffHours < 0 || diffHours > 240) return;
    const decay = Math.exp(-0.03 * diffHours);

    (session.exercises || []).forEach((exercise) => {
      const setCount = getWorkingSetCount(exercise);
      if (!setCount) return;
      const groups = getSessionExerciseGroups(exercise);
      if (!groups.length) return;
      const perGroup = (setCount / groups.length) * decay;
      groups.forEach((group) => {
        stress[group] = (stress[group] || 0) + perGroup;
      });
    });
  });

  const readiness = {};
  HOME_GROUP_KEYS.forEach((group) => {
    // ~12 effective decayed sets in recent history should push that region close to red.
    const normalized = Math.min((stress[group] || 0) / 12, 1);
    readiness[group] = Math.max(0.1, 1 - normalized);
  });

  return readiness;
}

export default function HomeScreen({ navigation }) {
  const { plans, history, bodyWeight, pb, exerciseMap, onboardingComplete, completeOnboarding } = useContext(AppContext);
  const colors = useTheme();
  const [showOnboarding, setShowOnboarding] = useState(false);

  useEffect(() => {
    // Show onboarding if not complete AND user has no workout history (fresh start)
    if (!onboardingComplete && (!history || history.length === 0)) {
      setShowOnboarding(true);
    }
  }, [onboardingComplete, history]);

  const dismissOnboarding = async (goToPlans) => {
    await completeOnboarding();
    setShowOnboarding(false);
    if (goToPlans) navigation.navigate('Plans');
  };

  const streak = getStreak(history);
  const thisWeek = new Date(); thisWeek.setDate(thisWeek.getDate() - 7);
  const weekSessions = history.filter(h => new Date(h.date) > thisWeek);
  const hitDays = new Set(weekSessions.map(h => h.dayId));
  const avgDur = history.length ? Math.round(history.reduce((a, h) => a + (h.duration || 0), 0) / history.length / 60) : 0;
  const latestBW = bodyWeight[0];

  const activePlan = plans[0];

  const { recommendation, volumeStatus, groupReadiness, readiness } = useMemo(() => {
    if (!history || history.length === 0) return { recommendation: null, volumeStatus: {}, groupReadiness: {}, readiness: {} };
    
    const totalFatigue = {};
    const totalStimulus = {};
    
    history.forEach(h => {
      const diffHours = (new Date() - new Date(h.date)) / 3600000;
      if (diffHours > 240) return; // ignore > 10 days
  
      const sessionWorkouts = [];
      (h.exercises || []).forEach(ex => {
        const wsets = (ex.sets || []).filter(st => st.type !== 'warmup' && st.reps > 0);
        wsets.forEach(ws => {
           sessionWorkouts.push({
              name: ex.name,
              sets: 1,
              reps: ws.reps,
              weight: ws.weight || 0,
              oneRM: null
           });
        });
      });
  
      if (exerciseMap) {
        const { stimulus, fatigue } = computeStimulusFatigue(sessionWorkouts, exerciseMap);
        const decayed = decayFatigueHourly(fatigue, diffHours);
        Object.entries(decayed).forEach(([m, v]) => totalFatigue[m] = (totalFatigue[m] || 0) + v);

        if (diffHours <= 168) {
          Object.entries(stimulus).forEach(([m, v]) => totalStimulus[m] = (totalStimulus[m] || 0) + v);
        }
      }
    });

    const r = computeReadiness(totalFatigue);
    const gr = getGroupReadiness(r);
    const vs = analyzeVolume(totalStimulus);
    const setBasedGroupReadiness = computeSetBasedGroupReadiness(history);
    const mergedGroupReadiness = { ...gr };
    HOME_GROUP_KEYS.forEach((group) => {
      const modelValue = gr[group];
      const setValue = setBasedGroupReadiness[group];
      if (typeof modelValue === 'number' && typeof setValue === 'number') {
        mergedGroupReadiness[group] = Math.min(modelValue, setValue);
      } else if (typeof setValue === 'number') {
        mergedGroupReadiness[group] = setValue;
      }
    });
    if (typeof mergedGroupReadiness.shoulders === 'number') {
      mergedGroupReadiness.rearDelts = mergedGroupReadiness.shoulders;
    }

    const rec = buildHomeProgramIntelligence({
      history,
      bodyWeight,
      activePlan,
      groupReadiness: mergedGroupReadiness,
      volumeStatus: vs,
    });

    return { recommendation: rec, volumeStatus: vs, groupReadiness: mergedGroupReadiness, readiness: r };
  }, [activePlan, bodyWeight, history, exerciseMap]);

  const openRecommendedTemplate = () => {
    if (!recommendation?.recommendedTemplateId) return;
    navigation.navigate('ProgramPicker', {
      recommendedTemplateId: recommendation.recommendedTemplateId,
      autoOpenRecommended: true,
    });
  };

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ paddingBottom: 40 }}>
      {/* Header */}
      <View style={[s.header, { borderBottomColor: colors.faint }]}>
        <Text style={[s.appSub, { color: colors.muted }]}>IRONLOG</Text>
        <View style={{ flexDirection: 'row', alignItems: 'flex-end' }}>
          <Text style={[s.appName, { color: colors.text }]}>IRON</Text>
          <Text style={[s.appName, { color: colors.accent }]}>LOG</Text>
        </View>
        <Text style={[s.appTagline, { color: colors.muted }]}>
          {activePlan ? activePlan.name : 'No active plan'}
        </Text>
      </View>

      {/* PROGRAM INTELLIGENCE */}
      {recommendation && (
        <TouchableOpacity
          activeOpacity={recommendation?.recommendedTemplateId ? 0.82 : 1}
          disabled={!recommendation?.recommendedTemplateId}
          onPress={openRecommendedTemplate}
          style={[s.intelCard, { backgroundColor: colors.accent + '15', borderColor: colors.accent + '40' }]}>
          <Text style={[s.intelSup, { color: colors.accent }]}>PROGRAM INTELLIGENCE</Text>
          <Text style={[s.intelMain, { color: colors.text }]}>{recommendation.headline}</Text>
          <Text style={[s.intelSub, { color: colors.muted }]}>
            Suggested Intensity: <Text style={{ color: colors.accent, fontWeight: '700' }}>{recommendation.intensity}</Text>
          </Text>
          {!!recommendation.reason && (
            <Text style={[s.intelReason, { color: colors.subtext }]}>Reason: {recommendation.reason}</Text>
          )}
          {!!recommendation.findings?.[0] && (
            <Text style={[s.intelReason, { color: colors.muted, marginTop: 6 }]}>
              Insight: {recommendation.findings[0]}
            </Text>
          )}
          {!!recommendation.recommendedTemplateId && (
            <View style={{ marginTop: 8, flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <Ionicons name="sparkles-outline" size={13} color={colors.accent} />
              <Text style={{ fontSize: 11, color: colors.accent, fontWeight: '700', letterSpacing: 0.2 }}>
                Tap to open recommended plan
              </Text>
            </View>
          )}
        </TouchableOpacity>
      )}

      {/* Stats row */}
      <View style={[s.statsRow, { borderBottomColor: colors.faint }]}>
        {[
          { val: weekSessions.length + '/7', label: 'THIS WEEK' },
          { val: streak + ' days', label: 'STREAK' },
          { val: avgDur + 'm', label: 'AVG TIME' },
        ].map((st, i) => (
          <View key={st.label} style={[s.statBox, i < 2 && { borderRightWidth: 1, borderRightColor: colors.faint }]}>
            <Text style={[s.statVal, { color: colors.text }]}>{st.val}</Text>
            <Text style={[s.statLabel, { color: colors.muted }]}>{st.label}</Text>
          </View>
        ))}
      </View>

      {/* Volume chips — only show if active plan has days */}
      {activePlan && activePlan.days.length > 0 && (
        <View style={[s.chipsRow, { borderBottomColor: colors.faint }]}>
          <Text style={[s.chipsLabel, { color: colors.muted }]}>THIS WEEK</Text>
          <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
            {activePlan.days.map(day => {
              const hit = hitDays.has(day.id);
              return (
                <View key={day.id} style={[s.chip, { borderColor: hit ? colors.accent : colors.faint, backgroundColor: hit ? colors.accentSoft : 'transparent' }]}>
                  <Text style={[s.chipText, { color: hit ? colors.accent : colors.muted }]}>{day.name}</Text>
                </View>
              );
            })}
          </View>
        </View>
      )}

      {/* Body weight */}
      <TouchableOpacity style={[s.bwRow, { borderBottomColor: colors.faint }]} onPress={() => navigation.navigate('BodyWeight')}>
        <Text style={[s.bwLabel, { color: colors.muted }]}>BODY WEIGHT</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Text style={[s.bwVal, { color: colors.text }]}>{latestBW ? latestBW.weight + ' kg' : '— kg'}</Text>
          <Ionicons name="chevron-forward" size={18} color={colors.muted} />
        </View>
      </TouchableOpacity>

      {/* Recovery heatmap */}
      <RecoveryHeatmap navigation={navigation} groupReadiness={groupReadiness} />

      {/* Day cards */}
      <View style={s.daysSection}>
        <Text style={[s.daysLabel, { color: colors.muted }]}>SELECT WORKOUT</Text>
        {!activePlan || activePlan.days.length === 0 ? (
          <TouchableOpacity style={[s.emptyCard, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
            onPress={() => navigation.navigate('Plans')}>
            <Ionicons name="add-circle-outline" size={32} color={colors.accent} />
            <Text style={[s.emptyTitle, { color: colors.accent }]}>CREATE YOUR FIRST PLAN</Text>
            <Text style={[s.emptyHint, { color: colors.muted }]}>Tap to go to Plans and build your workout</Text>
          </TouchableOpacity>
        ) : (
          activePlan.days.map((day, i) => {
            const lastDone = history.find(h => h.dayId === day.id);
            const lastDate = lastDone ? new Date(lastDone.date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }) : 'never done';
            const pbKeys = Object.keys(pb).filter(k => k.startsWith(day.id + '-'));
            const hasPb = pbKeys.length > 0;
            return (
              <TouchableOpacity key={day.id} style={[s.dayCard, { backgroundColor: colors.card, borderColor: colors.cardBorder, borderLeftColor: day.color }]}
                onPress={() => navigation.navigate('ActiveWorkout', { planIndex: 0, dayIndex: i })}>
                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                  <View style={{ flex: 1 }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                      <Text style={[s.dayLabel, { color: day.color }]}>{day.label || `D${i + 1}`}</Text>
                      <Text style={[s.dayName, { color: colors.text }]}>{day.name}</Text>
                      {hasPb && <View style={[s.pbBadge, { borderColor: (colors.gold || '#FFD700') + '44', backgroundColor: (colors.gold || '#FFD700') + '11' }]}><Text style={[s.pbBadgeText, { color: colors.gold || '#FFD700' }]}>PB</Text></View>}
                    </View>
                    {day.tag ? <Text style={[s.dayTag, { color: colors.subtext }]}>{day.tag}</Text> : null}
                    <Text style={[s.dayMeta, { color: colors.muted }]}>{(day.exercises || []).filter(e => !e.isWarmup).length} exercises · {lastDate}</Text>
                  </View>
                  <Ionicons name="chevron-forward" size={18} color={colors.muted} />
                </View>
              </TouchableOpacity>
            );
          })
        )}
      </View>

      {/* Onboarding modal */}
      <Modal visible={showOnboarding} transparent animationType="fade">
        <View style={s.overlay}>
          <View style={[s.onboardCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[s.onboardTitle, { color: colors.text }]}>WELCOME TO{'\n'}IRONLOG</Text>
            <Text style={[s.onboardSub, { color: colors.muted }]}>
              Your offline-first gym tracker.{'\n'}Start by creating your first workout plan.
            </Text>
            <TouchableOpacity style={[s.onboardBtn, { backgroundColor: colors.accent }]}
              onPress={() => dismissOnboarding(true)}>
              <Text style={s.onboardBtnText}>CREATE A PLAN</Text>
            </TouchableOpacity>
            <TouchableOpacity style={s.onboardSkip} onPress={() => dismissOnboarding(false)}>
              <Text style={[s.onboardSkipText, { color: colors.muted }]}>Skip for now</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  header: { padding: 24, paddingTop: 56, borderBottomWidth: 1 },
  appSub: { fontSize: 10, letterSpacing: 4, marginBottom: 4 },
  appName: { fontSize: 42, fontWeight: '900', letterSpacing: -2, lineHeight: 44 },
  appTagline: { fontSize: 13, marginTop: 4 },
  intelCard: { margin: 16, marginBottom: 0, padding: 16, borderWidth: 1, borderLeftWidth: 4 },
  intelSup: { fontSize: 9, fontWeight: '800', letterSpacing: 3, marginBottom: 4 },
  intelMain: { fontSize: 20, fontWeight: '900', letterSpacing: -0.5 },
  intelSub: { fontSize: 12, marginTop: 2 },
  intelReason: { fontSize: 11, marginTop: 8, lineHeight: 15 },
  statsRow: { flexDirection: 'row', borderBottomWidth: 1 },
  statBox: { flex: 1, padding: 16, alignItems: 'center' },
  statVal: { fontSize: 20, fontWeight: '900' },
  statLabel: { fontSize: 8, letterSpacing: 2, marginTop: 4 },
  chipsRow: { padding: 16, borderBottomWidth: 1, gap: 8 },
  chipsLabel: { fontSize: 9, letterSpacing: 4, marginBottom: 8 },
  chip: { paddingHorizontal: 12, paddingVertical: 6, borderWidth: 1 },
  chipText: { fontSize: 10, fontWeight: '700', letterSpacing: 2 },
  bwRow: { padding: 16, borderBottomWidth: 1 },
  bwLabel: { fontSize: 9, letterSpacing: 4, marginBottom: 4 },
  bwVal: { fontSize: 28, fontWeight: '900' },
  daysSection: { padding: 16, gap: 10 },
  daysLabel: { fontSize: 9, letterSpacing: 4, marginBottom: 4 },
  emptyCard: { padding: 32, borderWidth: 1, borderStyle: 'dashed', alignItems: 'center', gap: 10 },
  emptyTitle: { fontSize: 13, fontWeight: '800', letterSpacing: 2 },
  emptyHint: { fontSize: 12, textAlign: 'center' },
  dayCard: { padding: 16, borderWidth: 1, borderLeftWidth: 3 },
  dayLabel: { fontSize: 10, letterSpacing: 2, fontWeight: '700' },
  dayName: { fontSize: 22, fontWeight: '900', letterSpacing: -1 },
  dayTag: { fontSize: 13, marginTop: 2 },
  dayMeta: { fontSize: 11, marginTop: 4 },
  pbBadge: { borderWidth: 1, paddingHorizontal: 6, paddingVertical: 2 },
  pbBadgeText: { fontSize: 8, fontWeight: '700', letterSpacing: 1 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', justifyContent: 'center', padding: 28 },
  onboardCard: { padding: 32, borderWidth: 1, alignItems: 'center', gap: 16 },
  onboardTitle: { fontSize: 32, fontWeight: '900', letterSpacing: -1, textAlign: 'center', lineHeight: 36 },
  onboardSub: { fontSize: 14, textAlign: 'center', lineHeight: 22 },
  onboardBtn: { width: '100%', padding: 18, alignItems: 'center', marginTop: 8 },
  onboardBtnText: { color: '#fff', fontWeight: '800', fontSize: 14, letterSpacing: 2 },
  onboardSkip: { paddingVertical: 8 },
  onboardSkipText: { fontSize: 13 },
});
