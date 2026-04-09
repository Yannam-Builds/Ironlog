const fs = require('fs');
const path = require('path');
function w(p, c) {
  const d = path.dirname(p);
  if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
  fs.writeFileSync(p, c);
  console.log('W: ' + p);
}

w('src/screens/ActiveWorkoutScreen.js', `
import React, { useContext, useState, useEffect, useRef, useCallback } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  StyleSheet, Alert, Modal, Vibration,
} from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import { Text as SvgText } from 'react-native-svg';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { calcPlates } from '../utils/plateCalc';
import { epley } from '../utils/oneRM';

// SVG REST TIMER
function RestTimerCircle({ seconds, total, onSkip, onAdd30 }) {
  const R = 54;
  const CIRC = 2 * Math.PI * R;
  const frac = Math.max(0, seconds / total);
  const dash = frac * CIRC;
  const mm = String(Math.floor(seconds / 60)).padStart(2, '0');
  const ss = String(seconds % 60).padStart(2, '0');
  return (
    <View style={{ alignItems: 'center', paddingVertical: 16 }}>
      <Svg width={130} height={130}>
        <Circle cx={65} cy={65} r={R} stroke="#1a1a1a" strokeWidth={6} fill="none" />
        <Circle cx={65} cy={65} r={R} stroke="#FF4500" strokeWidth={6} fill="none"
          strokeDasharray={dash + ' ' + CIRC} strokeLinecap="round"
          rotation={-90} originX={65} originY={65} />
        <SvgText x={65} y={70} textAnchor="middle" fill="#f0f0f0" fontSize={26} fontWeight="900">
          {mm}:{ss}
        </SvgText>
      </Svg>
      <View style={{ flexDirection: 'row', gap: 12, marginTop: 6 }}>
        <TouchableOpacity style={rt.btn} onPress={onAdd30}>
          <Text style={rt.btnText}>+30s</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[rt.btn, { backgroundColor: '#FF4500' }]} onPress={onSkip}>
          <Text style={[rt.btnText, { color: '#fff' }]}>SKIP</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}
const rt = StyleSheet.create({
  btn: { paddingHorizontal: 20, paddingVertical: 10, borderWidth: 1, borderColor: '#333' },
  btnText: { fontSize: 12, fontWeight: '700', color: '#888', letterSpacing: 1 },
});

// PLATE MODAL
function PlateModal({ visible, targetKg, barWeight, onClose }) {
  const result = calcPlates(targetKg, barWeight);
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={pm.overlay}>
        <View style={pm.sheet}>
          <Text style={pm.title}>PLATE CALCULATOR</Text>
          <Text style={pm.target}>{targetKg} kg total</Text>
          {result.valid ? (
            <>
              <Text style={pm.label}>Each side:</Text>
              {result.each.length === 0
                ? <Text style={pm.noPlates}>Bar only ({barWeight}kg)</Text>
                : result.each.map((p, i) => (
                    <View key={i} style={pm.plateRow}>
                      <View style={[pm.plateVis, { width: 20 + p.kg * 2 }]} />
                      <Text style={pm.plateText}>{p.kg}kg x {p.count}</Text>
                    </View>
                  ))}
              <Text style={pm.sub}>Bar: {barWeight}kg · Total: {result.total}kg</Text>
            </>
          ) : (
            <Text style={{ color: '#FF4500', textAlign: 'center' }}>Cannot load {targetKg}kg with available plates.</Text>
          )}
          <TouchableOpacity style={pm.close} onPress={onClose}>
            <Text style={{ color: '#f0f0f0', fontWeight: '700', letterSpacing: 2 }}>CLOSE</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}
const pm = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: '#000000cc', justifyContent: 'flex-end' },
  sheet: { backgroundColor: '#0d0d0d', padding: 24, borderTopWidth: 1, borderTopColor: '#222' },
  title: { fontSize: 11, letterSpacing: 4, color: '#444', marginBottom: 4 },
  target: { fontSize: 28, fontWeight: '900', color: '#f0f0f0', marginBottom: 16 },
  label: { fontSize: 10, letterSpacing: 3, color: '#555', marginBottom: 8 },
  noPlates: { color: '#888', marginBottom: 8 },
  plateRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8, gap: 12 },
  plateVis: { height: 24, backgroundColor: '#FF4500', borderRadius: 2 },
  plateText: { fontSize: 16, fontWeight: '700', color: '#f0f0f0' },
  sub: { fontSize: 11, color: '#444', marginTop: 12 },
  close: { marginTop: 20, padding: 16, borderWidth: 1, borderColor: '#333', alignItems: 'center' },
});

// REST OVERRIDE MODAL
function RestOverrideModal({ visible, current, onSave, onClose }) {
  const [val, setVal] = useState(String(current));
  useEffect(() => { setVal(String(current)); }, [current]);
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: '#000000cc', justifyContent: 'center', alignItems: 'center' }}>
        <View style={{ backgroundColor: '#0d0d0d', padding: 28, width: 280, borderWidth: 1, borderColor: '#222' }}>
          <Text style={{ fontSize: 11, letterSpacing: 4, color: '#444', marginBottom: 12 }}>REST TIME (seconds)</Text>
          <TextInput
            style={{ fontSize: 32, fontWeight: '900', color: '#f0f0f0', borderBottomWidth: 2, borderBottomColor: '#FF4500', paddingVertical: 8, textAlign: 'center' }}
            value={val} onChangeText={setVal} keyboardType="numeric" autoFocus />
          <TouchableOpacity
            style={{ marginTop: 20, padding: 14, backgroundColor: '#FF4500', alignItems: 'center' }}
            onPress={() => { const n = parseInt(val); if (n > 0) onSave(n); onClose(); }}>
            <Text style={{ color: '#fff', fontWeight: '800', letterSpacing: 2 }}>SET</Text>
          </TouchableOpacity>
          <TouchableOpacity style={{ marginTop: 10, padding: 10, alignItems: 'center' }} onPress={onClose}>
            <Text style={{ color: '#555' }}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

// MAIN SCREEN
export default function ActiveWorkoutScreen({ route, navigation }) {
  const { planIndex = 0, dayIndex = 0 } = route.params || {};
  const { plans, history, pb, exerciseNotes, settings, addHistory, updatePb, saveExerciseNotes, isHeavy } = useContext(AppContext);

  const plan = plans[planIndex];
  const day = plan?.days[dayIndex];

  const [setLog, setSetLog] = useState({});
  const [inputs, setInputs] = useState({});
  const [notes, setNotes] = useState({});
  const [restActive, setRestActive] = useState(false);
  const [restSecs, setRestSecs] = useState(0);
  const [restTotal, setRestTotal] = useState(0);
  const [restOverride, setRestOverride] = useState({});
  const [showRestOverride, setShowRestOverride] = useState(false);
  const [editingRestEx, setEditingRestEx] = useState(null);
  const [showPlates, setShowPlates] = useState(false);
  const [platesTarget, setPlatesTarget] = useState(0);
  const [pbNotif, setPbNotif] = useState(null);
  const startTime = useRef(Date.now());
  const restInterval = useRef(null);

  useEffect(() => {
    if (!day) return;
    const n = {};
    day.exercises.forEach((ex, i) => { n[i] = exerciseNotes[ex.name] || ''; });
    setNotes(n);
    const inp = {};
    day.exercises.forEach((ex, i) => {
      const lastH = history.find(h => h.dayId === day.id);
      const lastEx = lastH?.exercises?.find(e => e.name === ex.name);
      const lastSet = lastEx?.sets?.[0];
      inp[i] = { weight: lastSet ? String(lastSet.weight) : '', reps: lastSet ? String(lastSet.reps) : '' };
    });
    setInputs(inp);
  }, []);

  const startRest = useCallback((exIndex) => {
    const ex = day.exercises[exIndex];
    const heavy = isHeavy(ex.name);
    const base = heavy ? (settings.defaultRestHeavy || 180) : (settings.defaultRestNormal || 120);
    const secs = restOverride[exIndex] !== undefined ? restOverride[exIndex] : base;
    clearInterval(restInterval.current);
    setRestSecs(secs);
    setRestTotal(secs);
    setRestActive(true);
    Vibration.vibrate(100);
    restInterval.current = setInterval(() => {
      setRestSecs(s => {
        if (s <= 1) {
          clearInterval(restInterval.current);
          setRestActive(false);
          Vibration.vibrate([0, 200, 100, 200]);
          return 0;
        }
        return s - 1;
      });
    }, 1000);
  }, [day, settings, restOverride, isHeavy]);

  useEffect(() => () => clearInterval(restInterval.current), []);

  const logSet = (exIndex) => {
    const inp = inputs[exIndex] || {};
    const weight = parseFloat(inp.weight) || 0;
    const reps = parseInt(inp.reps) || 0;
    if (!reps) return;
    const ex = day.exercises[exIndex];
    const key = day.id + '-' + ex.name;
    const orm = epley(weight, reps);
    if (weight > 0 && (!pb[key] || weight > pb[key])) {
      updatePb(key, weight);
      setPbNotif('NEW PB: ' + ex.name + ' — ' + weight + 'kg');
      setTimeout(() => setPbNotif(null), 3000);
    }
    setSetLog(prev => {
      const existing = prev[exIndex] || [];
      return { ...prev, [exIndex]: [...existing, { weight, reps, orm }] };
    });
    startRest(exIndex);
  };

  const finishWorkout = () => {
    const totalSets = Object.values(setLog).reduce((a, s) => a + s.length, 0);
    if (totalSets === 0) { Alert.alert('No sets logged', 'Log at least one set before finishing.'); return; }
    day.exercises.forEach((ex, i) => {
      if (notes[i] && notes[i] !== exerciseNotes[ex.name]) saveExerciseNotes(ex.name, notes[i]);
    });
    const exerciseData = day.exercises.map((ex, i) => ({
      name: ex.name,
      sets: (setLog[i] || []).map(s => ({ weight: s.weight, reps: s.reps })),
    })).filter(e => e.sets.length > 0);
    const duration = Math.round((Date.now() - startTime.current) / 1000);
    Alert.alert('FINISH WORKOUT', totalSets + ' sets logged · ' + Math.round(duration / 60) + 'min', [
      { text: 'Keep Going', style: 'cancel' },
      { text: 'FINISH', onPress: async () => {
        await addHistory({ id: Date.now().toString(), date: new Date().toISOString(), planId: plan.id, dayId: day.id, dayName: day.name, duration, sets: totalSets, exercises: exerciseData });
        navigation.goBack();
      }},
    ]);
  };

  if (!day) return <View style={s.container}><Text style={{ color: '#f0f0f0', padding: 20 }}>Workout not found.</Text></View>;

  return (
    <View style={s.container}>
      <View style={s.header}>
        <TouchableOpacity onPress={() => Alert.alert('Quit?', 'Progress will be lost.', [
          { text: 'Stay', style: 'cancel' },
          { text: 'Quit', style: 'destructive', onPress: () => navigation.goBack() }])}>
          <Ionicons name="close" size={24} color="#555" />
        </TouchableOpacity>
        <View style={{ alignItems: 'center' }}>
          <Text style={[s.dayLabel, { color: day.color }]}>{day.label}</Text>
          <Text style={s.dayName}>{day.name}</Text>
        </View>
        <TouchableOpacity style={s.finishBtn} onPress={finishWorkout}>
          <Text style={s.finishText}>DONE</Text>
        </TouchableOpacity>
      </View>

      {pbNotif ? (
        <View style={s.pbBanner}><Text style={s.pbBannerText}>🏆 {pbNotif}</Text></View>
      ) : null}

      {restActive ? (
        <View style={s.restOverlay}>
          <Text style={s.restLabel}>REST</Text>
          <RestTimerCircle seconds={restSecs} total={restTotal}
            onSkip={() => { clearInterval(restInterval.current); setRestActive(false); }}
            onAdd30={() => setRestSecs(s => s + 30)} />
        </View>
      ) : null}

      <ScrollView contentContainerStyle={{ paddingBottom: 60 }}>
        {day.exercises.filter(e => e.isWarmup).length > 0 && (
          <View style={s.warmupSection}>
            <Text style={s.sectionLabel}>WARMUP / MOBILITY</Text>
            {day.exercises.filter(e => e.isWarmup).map((ex, i) => (
              <View key={i} style={s.warmupRow}>
                <Text style={s.warmupName}>{ex.name}</Text>
                <Text style={s.warmupMeta}>{ex.sets}x {ex.reps}</Text>
              </View>
            ))}
          </View>
        )}

        {day.exercises.filter(e => !e.isWarmup).map((ex) => {
          const exIndex = day.exercises.indexOf(ex);
          const logged = setLog[exIndex] || [];
          const inp = inputs[exIndex] || { weight: '', reps: '' };
          const heavy = isHeavy(ex.name);
          const base = heavy ? (settings.defaultRestHeavy || 180) : (settings.defaultRestNormal || 120);
          const restSec = restOverride[exIndex] !== undefined ? restOverride[exIndex] : base;

          const recentH = history.filter(h => h.dayId === day.id).slice(0, 3);
          const allSameOrLess = recentH.length === 3 && recentH.every(h => {
            const e = h.exercises?.find(e => e.name === ex.name);
            return e && e.sets[0] && parseFloat(inp.weight || 0) <= e.sets[0].weight;
          });

          return (
            <View key={exIndex} style={[s.exCard, { borderLeftColor: day.color }]}>
              <View style={s.exHeader}>
                <View style={{ flex: 1 }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <Text style={s.exName}>{ex.name}</Text>
                    {heavy ? <View style={s.heavyBadge}><Text style={s.heavyText}>HEAVY</Text></View> : null}
                    {allSameOrLess ? <View style={s.overloadBadge}><Text style={s.overloadText}>UP OVERLOAD</Text></View> : null}
                  </View>
                  <Text style={s.exTarget}>{ex.primary} · {ex.sets}x{ex.reps}</Text>
                </View>
              </View>

              <TextInput
                style={s.noteInput}
                value={notes[exIndex] || ''}
                onChangeText={t => setNotes(prev => ({ ...prev, [exIndex]: t }))}
                placeholder={ex.note || 'Add note...'}
                placeholderTextColor="#2a2a2a"
                multiline />

              {logged.length > 0 && (
                <View style={s.loggedSets}>
                  {logged.map((ls, si) => (
                    <View key={si} style={s.loggedRow}>
                      <Text style={s.loggedNum}>SET {si + 1}</Text>
                      <Text style={s.loggedVal}>{ls.weight > 0 ? ls.weight + 'kg' : 'BW'} x {ls.reps}</Text>
                      {ls.orm > 0 ? <Text style={s.orm1RM}>1RM ~{ls.orm}kg</Text> : null}
                    </View>
                  ))}
                </View>
              )}

              <View style={s.inputRow}>
                <View style={s.inputGroup}>
                  <Text style={s.inputLabel}>KG</Text>
                  <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                    <TextInput
                      style={s.input}
                      value={inp.weight}
                      onChangeText={t => setInputs(prev => ({ ...prev, [exIndex]: { ...prev[exIndex], weight: t } }))}
                      keyboardType="decimal-pad" placeholder="0" placeholderTextColor="#222" />
                    <TouchableOpacity style={s.plateBtn}
                      onPress={() => { setPlatesTarget(parseFloat(inp.weight) || 0); setShowPlates(true); }}>
                      <Ionicons name="barbell-outline" size={14} color="#555" />
                    </TouchableOpacity>
                  </View>
                </View>
                <View style={s.inputGroup}>
                  <Text style={s.inputLabel}>REPS</Text>
                  <TextInput
                    style={s.input}
                    value={inp.reps}
                    onChangeText={t => setInputs(prev => ({ ...prev, [exIndex]: { ...prev[exIndex], reps: t } }))}
                    keyboardType="number-pad" placeholder="0" placeholderTextColor="#222" />
                </View>
                <TouchableOpacity style={s.logBtn} onPress={() => logSet(exIndex)}>
                  <Text style={s.logBtnText}>LOG</Text>
                </TouchableOpacity>
              </View>

              <TouchableOpacity style={s.restRow}
                onPress={() => { setEditingRestEx(exIndex); setShowRestOverride(true); }}>
                <Ionicons name="timer-outline" size={12} color="#444" />
                <Text style={s.restRowText}>{restSec}s rest · tap to change</Text>
              </TouchableOpacity>
            </View>
          );
        })}
      </ScrollView>

      <PlateModal visible={showPlates} targetKg={platesTarget} barWeight={settings.barWeight || 20} onClose={() => setShowPlates(false)} />
      <RestOverrideModal
        visible={showRestOverride}
        current={editingRestEx !== null
          ? (restOverride[editingRestEx] !== undefined ? restOverride[editingRestEx]
            : (isHeavy(day.exercises[editingRestEx]?.name) ? (settings.defaultRestHeavy || 180) : (settings.defaultRestNormal || 120)))
          : 120}
        onSave={(secs) => { if (editingRestEx !== null) setRestOverride(prev => ({ ...prev, [editingRestEx]: secs })); }}
        onClose={() => setShowRestOverride(false)} />
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingTop: 50, paddingBottom: 16, borderBottomWidth: 1, borderBottomColor: '#111' },
  dayLabel: { fontSize: 9, letterSpacing: 3 },
  dayName: { fontSize: 20, fontWeight: '900', letterSpacing: -1, color: '#f0f0f0' },
  finishBtn: { backgroundColor: '#FF4500', paddingHorizontal: 16, paddingVertical: 8 },
  finishText: { fontSize: 11, fontWeight: '800', color: '#fff', letterSpacing: 2 },
  pbBanner: { backgroundColor: '#FFD70022', borderBottomWidth: 1, borderBottomColor: '#FFD70044', padding: 12, alignItems: 'center' },
  pbBannerText: { fontSize: 13, fontWeight: '700', color: '#FFD700' },
  restOverlay: { backgroundColor: '#0a0a0a', borderBottomWidth: 1, borderBottomColor: '#1a1a1a', alignItems: 'center', paddingVertical: 4 },
  restLabel: { fontSize: 10, letterSpacing: 5, color: '#444', marginTop: 8 },
  warmupSection: { padding: 16, borderBottomWidth: 1, borderBottomColor: '#0d0d0d' },
  sectionLabel: { fontSize: 9, letterSpacing: 4, color: '#333', marginBottom: 8 },
  warmupRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 },
  warmupName: { fontSize: 14, color: '#555' },
  warmupMeta: { fontSize: 12, color: '#333' },
  exCard: { margin: 12, marginBottom: 0, borderWidth: 1, borderColor: '#111', borderLeftWidth: 3, backgroundColor: '#0a0a0a' },
  exHeader: { flexDirection: 'row', alignItems: 'flex-start', padding: 16, paddingBottom: 8 },
  exName: { fontSize: 20, fontWeight: '900', color: '#f0f0f0', letterSpacing: -0.5 },
  exTarget: { fontSize: 11, color: '#444', marginTop: 2 },
  heavyBadge: { backgroundColor: '#FF450022', borderWidth: 1, borderColor: '#FF450044', paddingHorizontal: 6, paddingVertical: 2 },
  heavyText: { fontSize: 8, color: '#FF4500', fontWeight: '700', letterSpacing: 1 },
  overloadBadge: { backgroundColor: '#FFD70022', borderWidth: 1, borderColor: '#FFD70044', paddingHorizontal: 6, paddingVertical: 2 },
  overloadText: { fontSize: 8, color: '#FFD700', fontWeight: '700', letterSpacing: 1 },
  noteInput: { marginHorizontal: 16, marginBottom: 8, fontSize: 12, color: '#666', borderWidth: 1, borderColor: '#111', padding: 8, minHeight: 36 },
  loggedSets: { marginHorizontal: 16, marginBottom: 8 },
  loggedRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 4, gap: 12 },
  loggedNum: { fontSize: 9, letterSpacing: 2, color: '#333', width: 36 },
  loggedVal: { fontSize: 16, fontWeight: '700', color: '#f0f0f0', flex: 1 },
  orm1RM: { fontSize: 11, color: '#555' },
  inputRow: { flexDirection: 'row', alignItems: 'flex-end', paddingHorizontal: 16, paddingBottom: 12, gap: 8 },
  inputGroup: { flex: 1 },
  inputLabel: { fontSize: 8, letterSpacing: 3, color: '#333', marginBottom: 4 },
  input: { flex: 1, borderBottomWidth: 2, borderBottomColor: '#1a1a1a', fontSize: 24, fontWeight: '900', color: '#f0f0f0', paddingVertical: 6, textAlign: 'center' },
  plateBtn: { padding: 6 },
  logBtn: { backgroundColor: '#FF4500', paddingHorizontal: 20, paddingVertical: 14 },
  logBtnText: { fontSize: 13, fontWeight: '800', color: '#fff', letterSpacing: 2 },
  restRow: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 16, paddingBottom: 12 },
  restRowText: { fontSize: 10, color: '#333', letterSpacing: 1 },
});
`);

w('src/screens/StatsScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { AppContext } from '../context/AppContext';

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

export default function StatsScreen() {
  const { history, pb, clearPbs } = useContext(AppContext);
  const streak = getStreak(history);
  const totalSets = history.reduce((a, h) => a + h.sets, 0);
  const avgDur = history.length ? Math.round(history.reduce((a, h) => a + h.duration, 0) / history.length / 60) : 0;

  const dayMap = {};
  history.forEach(h => {
    const d = h.date.split('T')[0];
    dayMap[d] = (dayMap[d] || 0) + 1;
  });
  const days = Object.keys(dayMap).sort().slice(-14);

  const pbEntries = Object.entries(pb).sort(([, a], [, b]) => b - a);

  return (
    <ScrollView style={s.container} contentContainerStyle={{ paddingBottom: 40 }}>
      <View style={s.statsBar}>
        {[
          { label: 'SESSIONS', val: history.length },
          { label: 'TOTAL SETS', val: totalSets },
          { label: 'AVG TIME', val: avgDur + 'm' },
          { label: 'STREAK', val: streak + 'd' },
        ].map((stat, i) => (
          <View key={stat.label} style={[s.statBox, i < 3 && { borderRightWidth: 1, borderRightColor: '#111' }]}>
            <Text style={s.statVal}>{stat.val}</Text>
            <Text style={s.statLabel}>{stat.label}</Text>
          </View>
        ))}
      </View>

      {days.length > 0 && (
        <View style={s.section}>
          <Text style={s.sectionLabel}>SESSIONS PER DAY (LAST 14 DAYS)</Text>
          <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 4, height: 60 }}>
            {days.map(d => (
              <View key={d} style={{ flex: 1, alignItems: 'center' }}>
                <View style={{ width: '100%', backgroundColor: '#FF4500', height: Math.min(dayMap[d] * 40, 56), minHeight: 4 }} />
              </View>
            ))}
          </View>
        </View>
      )}

      {pbEntries.length > 0 && (
        <View style={s.section}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <Text style={s.sectionLabel}>PERSONAL BESTS</Text>
            <TouchableOpacity onPress={() => clearPbs()}>
              <Text style={{ fontSize: 10, color: '#FF4500', letterSpacing: 1 }}>RESET</Text>
            </TouchableOpacity>
          </View>
          {pbEntries.map(([k, v]) => {
            const name = k.split('-').slice(1).join('-');
            const orm = Math.round(v * (1 + 5 / 30));
            return (
              <View key={k} style={s.pbRow}>
                <View style={{ flex: 1 }}>
                  <Text style={{ fontSize: 14, fontWeight: '600', color: '#f0f0f0' }}>{name}</Text>
                  <Text style={{ fontSize: 11, color: '#444' }}>Est. 1RM: ~{orm}kg</Text>
                </View>
                <Text style={{ fontSize: 16, fontWeight: '800', color: '#FFD700' }}>🏆 {v}kg</Text>
              </View>
            );
          })}
        </View>
      )}

      {history.length === 0 && (
        <View style={{ padding: 40, alignItems: 'center' }}>
          <Text style={{ color: '#333', fontSize: 14 }}>No workouts yet. Start one!</Text>
        </View>
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  statsBar: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#111' },
  statBox: { flex: 1, padding: 16, alignItems: 'center' },
  statVal: { fontSize: 20, fontWeight: '900', color: '#f0f0f0' },
  statLabel: { fontSize: 8, letterSpacing: 2, color: '#444', marginTop: 4 },
  section: { padding: 20 },
  sectionLabel: { fontSize: 10, letterSpacing: 4, color: '#444', marginBottom: 10 },
  pbRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 12, backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', marginBottom: 6 },
});
`);

w('src/screens/HistoryScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { AppContext } from '../context/AppContext';

const DAY_COLORS = { push: '#FF4500', pull: '#0080FF', legs: '#00C170', upper: '#A020F0' };

export default function HistoryScreen() {
  const { history, clearHistory } = useContext(AppContext);

  const confirmClear = () => Alert.alert('Clear History?', 'This cannot be undone.', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Clear', style: 'destructive', onPress: clearHistory },
  ]);

  return (
    <ScrollView style={s.container} contentContainerStyle={{ paddingBottom: 40 }}>
      {history.length > 0 && (
        <TouchableOpacity style={s.clearBtn} onPress={confirmClear}>
          <Text style={s.clearText}>CLEAR ALL</Text>
        </TouchableOpacity>
      )}
      {history.length === 0 && (
        <View style={{ padding: 40, alignItems: 'center' }}>
          <Text style={{ color: '#333', fontSize: 14 }}>No workouts logged yet.</Text>
        </View>
      )}
      {history.map((h, i) => {
        const color = DAY_COLORS[h.dayId] || '#555';
        const date = new Date(h.date);
        const dateStr = date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: '2-digit' });
        const timeStr = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
        return (
          <View key={h.id || i} style={[s.card, { borderLeftColor: color }]}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <View>
                <Text style={[s.dayName, { color }]}>{h.dayName || h.dayId?.toUpperCase()}</Text>
                <Text style={s.meta}>{h.sets} sets · {Math.round(h.duration / 60)}min</Text>
              </View>
              <View style={{ alignItems: 'flex-end' }}>
                <Text style={s.date}>{dateStr}</Text>
                <Text style={s.time}>{timeStr}</Text>
              </View>
            </View>
            {h.exercises && h.exercises.length > 0 && (
              <View style={{ marginTop: 8 }}>
                {h.exercises.map((ex, ei) => (
                  <Text key={ei} style={s.exLine}>
                    {ex.name}: {ex.sets.map(set => (set.weight > 0 ? set.weight + 'kg' : 'BW') + 'x' + set.reps).join(', ')}
                  </Text>
                ))}
              </View>
            )}
          </View>
        );
      })}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  clearBtn: { margin: 16, padding: 12, borderWidth: 1, borderColor: '#1a1a1a', alignItems: 'center' },
  clearText: { fontSize: 10, color: '#FF4500', letterSpacing: 3 },
  card: { marginHorizontal: 12, marginTop: 10, padding: 16, backgroundColor: '#0a0a0a', borderWidth: 1, borderColor: '#111', borderLeftWidth: 3 },
  dayName: { fontSize: 18, fontWeight: '900', letterSpacing: -0.5 },
  meta: { fontSize: 12, color: '#444', marginTop: 2 },
  date: { fontSize: 13, fontWeight: '700', color: '#888' },
  time: { fontSize: 11, color: '#444', marginTop: 2 },
  exLine: { fontSize: 11, color: '#444', marginTop: 2 },
});
`);

console.log('ActiveWorkout + Stats + History done');
