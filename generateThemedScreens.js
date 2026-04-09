const fs = require('fs');
function w(p, c) { fs.writeFileSync(p, c); console.log('W: ' + p); }

w('src/screens/HomeScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';

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

const MUSCLE_TAGS = { push: 'PUSH', pull: 'PULL', legs: 'LEGS', upper: 'UPPER' };

export default function HomeScreen({ navigation }) {
  const { plans, history, bodyWeight, pb } = useContext(AppContext);
  const colors = useTheme();
  const plan = plans[0];
  const streak = getStreak(history);

  const thisWeek = new Date(); thisWeek.setDate(thisWeek.getDate() - 7);
  const weekSessions = history.filter(h => new Date(h.date) > thisWeek);
  const hitDays = new Set(weekSessions.map(h => h.dayId));
  const avgDur = history.length ? Math.round(history.reduce((a, h) => a + h.duration, 0) / history.length / 60) : 0;
  const latestBW = bodyWeight[0];

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ paddingBottom: 40 }}>
      {/* Header */}
      <View style={[s.header, { borderBottomColor: colors.faint }]}>
        <Text style={[s.appSub, { color: colors.muted }]}>PRANAV / {plan?.name?.toUpperCase()}</Text>
        <View style={{ flexDirection: 'row', alignItems: 'flex-end' }}>
          <Text style={[s.appName, { color: colors.text }]}>IRON</Text>
          <Text style={[s.appName, { color: colors.accent }]}>LOG</Text>
        </View>
        <Text style={[s.appTagline, { color: colors.muted }]}>cutting phase · 4-day split</Text>
      </View>

      {/* Stats row */}
      <View style={[s.statsRow, { borderBottomColor: colors.faint }]}>
        {[
          { val: weekSessions.length + '/4', label: 'THIS WEEK' },
          { val: streak + ' days', label: 'STREAK' },
          { val: avgDur + 'm', label: 'AVG TIME' },
        ].map((st, i) => (
          <View key={st.label} style={[s.statBox, i < 2 && { borderRightWidth: 1, borderRightColor: colors.faint }]}>
            <Text style={[s.statVal, { color: colors.text }]}>{st.val}</Text>
            <Text style={[s.statLabel, { color: colors.muted }]}>{st.label}</Text>
          </View>
        ))}
      </View>

      {/* Volume chips */}
      <View style={[s.chipsRow, { borderBottomColor: colors.faint }]}>
        <Text style={[s.chipsLabel, { color: colors.muted }]}>THIS WEEK</Text>
        <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
          {Object.entries(MUSCLE_TAGS).map(([id, label]) => {
            const hit = hitDays.has(id);
            return (
              <View key={id} style={[s.chip, { borderColor: hit ? colors.accent : colors.faint, backgroundColor: hit ? colors.accentSoft : 'transparent' }]}>
                <Text style={[s.chipText, { color: hit ? colors.accent : colors.muted }]}>{label}</Text>
              </View>
            );
          })}
        </View>
      </View>

      {/* Body weight */}
      <TouchableOpacity style={[s.bwRow, { borderBottomColor: colors.faint }]} onPress={() => navigation.navigate('BodyWeight')}>
        <Text style={[s.bwLabel, { color: colors.muted }]}>BODY WEIGHT</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Text style={[s.bwVal, { color: colors.text }]}>{latestBW ? latestBW.weight + ' kg' : '— kg'}</Text>
          <Ionicons name="chevron-forward" size={18} color={colors.muted} />
        </View>
      </TouchableOpacity>

      {/* Day cards */}
      <View style={s.daysSection}>
        <Text style={[s.daysLabel, { color: colors.muted }]}>SELECT WORKOUT</Text>
        {plan?.days.map((day, i) => {
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
                    <Text style={[s.dayLabel, { color: day.color }]}>{day.label}</Text>
                    <Text style={[s.dayName, { color: colors.text }]}>{day.name}</Text>
                    {hasPb && <View style={[s.pbBadge, { borderColor: colors.gold + '44', backgroundColor: colors.gold + '11' }]}><Text style={[s.pbBadgeText, { color: colors.gold }]}>PB</Text></View>}
                  </View>
                  <Text style={[s.dayTag, { color: colors.subtext }]}>{day.tag}</Text>
                  <Text style={[s.dayMeta, { color: colors.muted }]}>{day.exercises.filter(e => !e.isWarmup).length} exercises · {lastDate}</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.muted} />
              </View>
            </TouchableOpacity>
          );
        })}
      </View>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  header: { padding: 24, paddingTop: 56, borderBottomWidth: 1 },
  appSub: { fontSize: 10, letterSpacing: 4, marginBottom: 4 },
  appName: { fontSize: 42, fontWeight: '900', letterSpacing: -2, lineHeight: 44 },
  appTagline: { fontSize: 13, marginTop: 4 },
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
  dayCard: { padding: 16, borderWidth: 1, borderLeftWidth: 3 },
  dayLabel: { fontSize: 10, letterSpacing: 2, fontWeight: '700' },
  dayName: { fontSize: 22, fontWeight: '900', letterSpacing: -1 },
  dayTag: { fontSize: 13, marginTop: 2 },
  dayMeta: { fontSize: 11, marginTop: 4 },
  pbBadge: { borderWidth: 1, paddingHorizontal: 6, paddingVertical: 2 },
  pbBadgeText: { fontSize: 8, fontWeight: '700', letterSpacing: 1 },
});
`);

w('src/screens/StatsScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';

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
  const colors = useTheme();
  const streak = getStreak(history);
  const totalSets = history.reduce((a, h) => a + h.sets, 0);
  const avgDur = history.length ? Math.round(history.reduce((a, h) => a + h.duration, 0) / history.length / 60) : 0;
  const days = [...new Set(history.map(h => h.date.split('T')[0]))].sort().slice(-14);
  const dayMap = {}; history.forEach(h => { const d = h.date.split('T')[0]; dayMap[d] = (dayMap[d] || 0) + 1; });
  const pbEntries = Object.entries(pb).sort(([,a],[,b]) => b - a);

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ paddingBottom: 40 }}>
      <View style={[s.statsBar, { borderBottomColor: colors.faint }]}>
        {[
          { label: 'SESSIONS', val: history.length },
          { label: 'TOTAL SETS', val: totalSets },
          { label: 'AVG TIME', val: avgDur + 'm' },
          { label: 'STREAK', val: streak + 'd' },
        ].map((stat, i) => (
          <View key={stat.label} style={[s.statBox, i < 3 && { borderRightWidth: 1, borderRightColor: colors.faint }]}>
            <Text style={[s.statVal, { color: colors.text }]}>{stat.val}</Text>
            <Text style={[s.statLabel, { color: colors.muted }]}>{stat.label}</Text>
          </View>
        ))}
      </View>

      {days.length > 0 && (
        <View style={[s.section, { borderBottomColor: colors.faint }]}>
          <Text style={[s.sectionLabel, { color: colors.muted }]}>SESSIONS PER DAY (LAST 14 DAYS)</Text>
          <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 4, height: 60 }}>
            {days.map(d => (
              <View key={d} style={{ flex: 1, alignItems: 'center' }}>
                <View style={{ width: '100%', backgroundColor: colors.accent, height: Math.min(dayMap[d] * 40, 56), minHeight: 4 }} />
              </View>
            ))}
          </View>
        </View>
      )}

      {pbEntries.length > 0 && (
        <View style={s.section}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <Text style={[s.sectionLabel, { color: colors.muted }]}>PERSONAL BESTS</Text>
            <TouchableOpacity onPress={clearPbs}>
              <Text style={{ fontSize: 10, color: colors.accent, letterSpacing: 1 }}>RESET</Text>
            </TouchableOpacity>
          </View>
          {pbEntries.map(([k, v]) => {
            const name = k.split('-').slice(1).join('-');
            const orm = Math.round(v * (1 + 5 / 30));
            return (
              <View key={k} style={[s.pbRow, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
                <View style={{ flex: 1 }}>
                  <Text style={{ fontSize: 14, fontWeight: '600', color: colors.text }}>{name}</Text>
                  <Text style={{ fontSize: 11, color: colors.muted }}>Est. 1RM: ~{orm}kg</Text>
                </View>
                <Text style={{ fontSize: 16, fontWeight: '800', color: colors.gold }}>🏆 {v}kg</Text>
              </View>
            );
          })}
        </View>
      )}

      {history.length === 0 && (
        <View style={{ padding: 40, alignItems: 'center' }}>
          <Text style={{ color: colors.muted, fontSize: 14 }}>No workouts yet. Start one!</Text>
        </View>
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  statsBar: { flexDirection: 'row', borderBottomWidth: 1 },
  statBox: { flex: 1, padding: 16, alignItems: 'center' },
  statVal: { fontSize: 20, fontWeight: '900' },
  statLabel: { fontSize: 8, letterSpacing: 2, marginTop: 4 },
  section: { padding: 20 },
  sectionLabel: { fontSize: 10, letterSpacing: 4, marginBottom: 10 },
  pbRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 12, borderWidth: 1, marginBottom: 6 },
});
`);

w('src/screens/HistoryScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';

export default function HistoryScreen() {
  const { history, clearHistory } = useContext(AppContext);
  const colors = useTheme();

  const confirmClear = () => Alert.alert('Clear History?', 'This cannot be undone.', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Clear', style: 'destructive', onPress: clearHistory },
  ]);

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ paddingBottom: 40 }}>
      {history.length > 0 && (
        <TouchableOpacity style={[s.clearBtn, { borderColor: colors.faint }]} onPress={confirmClear}>
          <Text style={[s.clearText, { color: colors.accent }]}>CLEAR ALL</Text>
        </TouchableOpacity>
      )}
      {history.length === 0 && (
        <View style={{ padding: 40, alignItems: 'center' }}>
          <Text style={{ color: colors.muted, fontSize: 14 }}>No workouts logged yet.</Text>
        </View>
      )}
      {history.map((h, i) => {
        const date = new Date(h.date);
        const dateStr = date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: '2-digit' });
        const timeStr = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
        const dayColors = { push: '#FF4500', pull: '#0080FF', legs: '#00C170', upper: '#A020F0' };
        const color = dayColors[h.dayId] || colors.accent;
        return (
          <View key={h.id || i} style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder, borderLeftColor: color }]}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <View>
                <Text style={[s.dayName, { color }]}>{h.dayName || h.dayId?.toUpperCase()}</Text>
                <Text style={[s.meta, { color: colors.muted }]}>{h.sets} sets · {Math.round(h.duration / 60)}min</Text>
              </View>
              <View style={{ alignItems: 'flex-end' }}>
                <Text style={[s.date, { color: colors.subtext }]}>{dateStr}</Text>
                <Text style={[s.time, { color: colors.muted }]}>{timeStr}</Text>
              </View>
            </View>
            {h.exercises && h.exercises.length > 0 && (
              <View style={{ marginTop: 8 }}>
                {h.exercises.map((ex, ei) => (
                  <Text key={ei} style={[s.exLine, { color: colors.muted }]}>
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
  container: { flex: 1 },
  clearBtn: { margin: 16, padding: 12, borderWidth: 1, alignItems: 'center' },
  clearText: { fontSize: 10, letterSpacing: 3 },
  card: { marginHorizontal: 12, marginTop: 10, padding: 16, borderWidth: 1, borderLeftWidth: 3 },
  dayName: { fontSize: 18, fontWeight: '900', letterSpacing: -0.5 },
  meta: { fontSize: 12, marginTop: 2 },
  date: { fontSize: 13, fontWeight: '700' },
  time: { fontSize: 11, marginTop: 2 },
  exLine: { fontSize: 11, marginTop: 2 },
});
`);

w('src/screens/PlansScreen.js', `
import React, { useContext, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert, TextInput, Modal } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';

function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }

export default function PlansScreen({ navigation }) {
  const { plans, savePlans } = useContext(AppContext);
  const colors = useTheme();
  const [showNew, setShowNew] = useState(false);
  const [newName, setNewName] = useState('');

  const createPlan = () => {
    if (!newName.trim()) return;
    savePlans([...plans, { id: genId(), name: newName.trim(), days: [] }]);
    setNewName(''); setShowNew(false);
  };

  const deletePlan = (id) => Alert.alert('Delete plan?', '', [
    { text: 'Cancel' },
    { text: 'Delete', style: 'destructive', onPress: () => savePlans(plans.filter(p => p.id !== id)) },
  ]);

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <ScrollView contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 40 }}>
        {plans.map(plan => (
          <TouchableOpacity key={plan.id} style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
            onPress={() => navigation.navigate('PlanEditor', { planId: plan.id })}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <View style={{ flex: 1 }}>
                <Text style={[s.planName, { color: colors.text }]}>{plan.name}</Text>
                <Text style={[s.planMeta, { color: colors.muted }]}>{plan.days.length} days · {plan.days.reduce((a, d) => a + d.exercises.filter(e => !e.isWarmup).length, 0)} exercises</Text>
              </View>
              <View style={{ flexDirection: 'row', gap: 16, alignItems: 'center' }}>
                <Ionicons name="create-outline" size={20} color={colors.muted} />
                {plan.id !== 'aesthetic-split' && (
                  <TouchableOpacity onPress={() => deletePlan(plan.id)}>
                    <Ionicons name="trash-outline" size={20} color="#CC3333" />
                  </TouchableOpacity>
                )}
              </View>
            </View>
            <View style={{ flexDirection: 'row', gap: 6, marginTop: 10, flexWrap: 'wrap' }}>
              {plan.days.map(d => (
                <View key={d.id} style={[s.dayChip, { backgroundColor: d.color + '22', borderColor: d.color + '44' }]}>
                  <Text style={{ fontSize: 10, color: d.color, fontWeight: '700', letterSpacing: 1 }}>{d.name}</Text>
                </View>
              ))}
            </View>
          </TouchableOpacity>
        ))}
        <TouchableOpacity style={[s.addBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]} onPress={() => setShowNew(true)}>
          <Ionicons name="add" size={20} color={colors.accent} />
          <Text style={[s.addText, { color: colors.accent }]}>NEW PLAN</Text>
        </TouchableOpacity>
      </ScrollView>

      <Modal visible={showNew} transparent animationType="fade" onRequestClose={() => setShowNew(false)}>
        <View style={s.overlay}>
          <View style={[s.modal, { backgroundColor: colors.surface, borderColor: colors.cardBorder }]}>
            <Text style={[s.modalTitle, { color: colors.text }]}>NEW PLAN</Text>
            <TextInput style={[s.input, { color: colors.text, borderBottomColor: colors.accent }]}
              value={newName} onChangeText={setNewName} placeholder="Plan name" placeholderTextColor={colors.muted} autoFocus />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 20 }}>
              <TouchableOpacity style={[s.cancelBtn, { borderColor: colors.faint }]} onPress={() => setShowNew(false)}>
                <Text style={{ color: colors.muted }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[s.confirmBtn, { backgroundColor: colors.accent }]} onPress={createPlan}>
                <Text style={{ color: '#fff', fontWeight: '800' }}>CREATE</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  card: { padding: 16, borderWidth: 1 },
  planName: { fontSize: 18, fontWeight: '900' },
  planMeta: { fontSize: 12, marginTop: 2 },
  dayChip: { paddingHorizontal: 10, paddingVertical: 4, borderWidth: 1 },
  addBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 16, borderWidth: 1, borderStyle: 'dashed' },
  addText: { fontSize: 12, fontWeight: '700', letterSpacing: 2 },
  overlay: { flex: 1, backgroundColor: '#000000cc', justifyContent: 'center', padding: 24 },
  modal: { padding: 24, borderWidth: 1 },
  modalTitle: { fontSize: 14, fontWeight: '900', letterSpacing: 3, marginBottom: 16 },
  input: { fontSize: 22, fontWeight: '700', borderBottomWidth: 2, paddingVertical: 8, marginBottom: 8 },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1 },
  confirmBtn: { flex: 1, padding: 14, alignItems: 'center' },
});
`);

w('src/screens/ExerciseLibraryScreen.js', `
import React, { useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, TextInput } from 'react-native';
import { EXERCISES, MUSCLES, CATEGORIES } from '../data/exerciseLibrary';
import { useTheme } from '../context/ThemeContext';

export default function ExerciseLibraryScreen() {
  const colors = useTheme();
  const [search, setSearch] = useState('');
  const [muscle, setMuscle] = useState('All');
  const [cat, setCat] = useState('All');

  const filtered = EXERCISES.filter(e =>
    (muscle === 'All' || e.muscle === muscle) &&
    (cat === 'All' || e.category === cat) &&
    (!search || e.name.toLowerCase().includes(search.toLowerCase()))
  );

  const FilterChip = ({ label, active, onPress }) => (
    <TouchableOpacity style={[s.chip, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]} onPress={onPress}>
      <Text style={[s.chipText, { color: active ? colors.accent : colors.muted }]}>{label}</Text>
    </TouchableOpacity>
  );

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <View style={[s.searchBar, { backgroundColor: colors.card, borderBottomColor: colors.faint }]}>
        <TextInput style={[s.input, { color: colors.text }]} value={search} onChangeText={setSearch}
          placeholder="Search exercises..." placeholderTextColor={colors.muted} />
        {search ? <TouchableOpacity onPress={() => setSearch('')}><Text style={{ color: colors.muted, padding: 8 }}>x</Text></TouchableOpacity> : null}
      </View>
      <View style={[s.filterRow, { borderBottomColor: colors.faint }]}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingHorizontal: 16 }}>
          {['All', ...CATEGORIES].map(c => <FilterChip key={c} label={c} active={cat === c} onPress={() => setCat(c)} />)}
        </ScrollView>
      </View>
      <View style={[s.filterRow, { borderBottomColor: colors.faint }]}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingHorizontal: 16 }}>
          {['All', ...MUSCLES].map(m => <FilterChip key={m} label={m} active={muscle === m} onPress={() => setMuscle(m)} />)}
        </ScrollView>
      </View>
      <Text style={[s.count, { color: colors.muted }]}>{filtered.length} exercises</Text>
      <ScrollView contentContainerStyle={{ paddingBottom: 40 }}>
        {filtered.map((ex, i) => (
          <View key={i} style={[s.exRow, { borderBottomColor: colors.faint }]}>
            <View style={{ flex: 1 }}>
              <Text style={[s.exName, { color: colors.text }]}>{ex.name}</Text>
              <Text style={[s.exCue, { color: colors.muted }]}>{ex.cue}</Text>
            </View>
            <View style={{ alignItems: 'flex-end', gap: 4 }}>
              <View style={[s.tag, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
                <Text style={[s.tagText, { color: colors.accent }]}>{ex.muscle}</Text>
              </View>
              <View style={[s.tag, { backgroundColor: colors.faint }]}>
                <Text style={[s.tagText, { color: colors.muted }]}>{ex.equipment}</Text>
              </View>
            </View>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  searchBar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, borderBottomWidth: 1 },
  input: { flex: 1, fontSize: 16, paddingVertical: 14 },
  filterRow: { paddingVertical: 10, borderBottomWidth: 1 },
  chip: { paddingHorizontal: 12, paddingVertical: 6, borderWidth: 1 },
  chipText: { fontSize: 11, fontWeight: '600', letterSpacing: 0.5 },
  count: { fontSize: 10, letterSpacing: 3, padding: 12, paddingHorizontal: 16 },
  exRow: { flexDirection: 'row', padding: 16, borderBottomWidth: 1, alignItems: 'center' },
  exName: { fontSize: 15, fontWeight: '700' },
  exCue: { fontSize: 12, marginTop: 2 },
  tag: { paddingHorizontal: 8, paddingVertical: 3, borderWidth: 1 },
  tagText: { fontSize: 10, fontWeight: '600' },
});
`);

w('src/screens/BodyWeightScreen.js', `
import React, { useContext, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, TextInput, Alert } from 'react-native';
import Svg, { Polyline, Circle, Line, Text as SvgText } from 'react-native-svg';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';

export default function BodyWeightScreen() {
  const { bodyWeight, logBodyWeight } = useContext(AppContext);
  const colors = useTheme();
  const [input, setInput] = useState('');

  const log = () => {
    const w = parseFloat(input);
    if (!w || w < 20 || w > 300) { Alert.alert('Enter a valid weight (20-300 kg)'); return; }
    logBodyWeight({ date: new Date().toISOString(), weight: w });
    setInput('');
  };

  const recent = bodyWeight.slice(0, 30).reverse();
  const lastWeek = bodyWeight[7];
  const delta = lastWeek && bodyWeight[0] ? (bodyWeight[0].weight - lastWeek.weight).toFixed(1) : null;

  // SVG chart
  const W = 320; const H = 120; const PAD = 20;
  let chart = null;
  if (recent.length >= 2) {
    const weights = recent.map(e => e.weight);
    const minW = Math.min(...weights) - 1;
    const maxW = Math.max(...weights) + 1;
    const points = recent.map((e, i) => {
      const x = PAD + (i / (recent.length - 1)) * (W - PAD * 2);
      const y = H - PAD - ((e.weight - minW) / (maxW - minW)) * (H - PAD * 2);
      return x + ',' + y;
    }).join(' ');
    const lastPt = recent[recent.length - 1];
    const lx = W - PAD;
    const ly = H - PAD - ((lastPt.weight - minW) / (maxW - minW)) * (H - PAD * 2);
    chart = (
      <Svg width={W} height={H} style={{ marginVertical: 8 }}>
        <Line x1={PAD} y1={H - PAD} x2={W - PAD} y2={H - PAD} stroke={colors.faint} strokeWidth={1} />
        <Polyline points={points} fill="none" stroke={colors.accent} strokeWidth={2} />
        <Circle cx={lx} cy={ly} r={4} fill={colors.accent} />
        <SvgText x={lx} y={ly - 8} textAnchor="middle" fill={colors.text} fontSize={11}>{lastPt.weight}kg</SvgText>
      </Svg>
    );
  }

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ padding: 20, paddingBottom: 40 }}>
      <View style={[s.inputCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.label, { color: colors.muted }]}>TODAY'S WEIGHT (KG)</Text>
        <View style={{ flexDirection: 'row', gap: 12, alignItems: 'center' }}>
          <TextInput style={[s.input, { color: colors.text, borderBottomColor: colors.accent }]}
            value={input} onChangeText={setInput} keyboardType="decimal-pad" placeholder="0.0" placeholderTextColor={colors.muted} />
          <TouchableOpacity style={[s.logBtn, { backgroundColor: colors.accent }]} onPress={log}>
            <Text style={s.logBtnText}>LOG</Text>
          </TouchableOpacity>
        </View>
        {delta !== null && (
          <Text style={{ color: parseFloat(delta) <= 0 ? '#00C170' : '#FF4500', fontSize: 13, marginTop: 8 }}>
            {parseFloat(delta) > 0 ? '+' : ''}{delta} kg vs last week
          </Text>
        )}
      </View>

      {chart && (
        <View style={[s.chartCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
          <Text style={[s.label, { color: colors.muted }]}>LAST 30 DAYS</Text>
          {chart}
        </View>
      )}

      {bodyWeight.slice(0, 14).map((e, i) => (
        <View key={i} style={[s.histRow, { borderBottomColor: colors.faint }]}>
          <Text style={{ fontSize: 14, color: colors.subtext }}>{new Date(e.date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })}</Text>
          <Text style={{ fontSize: 18, fontWeight: '700', color: colors.text }}>{e.weight} kg</Text>
        </View>
      ))}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  inputCard: { padding: 20, borderWidth: 1, marginBottom: 16 },
  label: { fontSize: 10, letterSpacing: 3, marginBottom: 12 },
  input: { flex: 1, fontSize: 36, fontWeight: '900', borderBottomWidth: 2, paddingVertical: 6 },
  logBtn: { paddingHorizontal: 24, paddingVertical: 16 },
  logBtnText: { color: '#fff', fontWeight: '800', fontSize: 13, letterSpacing: 2 },
  chartCard: { padding: 16, borderWidth: 1, marginBottom: 16, alignItems: 'center' },
  histRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1 },
});
`);

console.log('All themed screens done');
