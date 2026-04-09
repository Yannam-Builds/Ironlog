const fs = require('fs');
const path = require('path');
function w(p, c) {
  const d = path.dirname(p);
  if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
  fs.writeFileSync(p, c);
  console.log('W: ' + p);
}

// ── EXERCISE LIBRARY SCREEN ──
w('src/screens/ExerciseLibraryScreen.js', `
import React, { useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, TextInput } from 'react-native';
import { EXERCISES, MUSCLES, CATEGORIES } from '../data/exerciseLibrary';

export default function ExerciseLibraryScreen() {
  const [search, setSearch] = useState('');
  const [muscle, setMuscle] = useState('All');
  const [category, setCategory] = useState('All');

  const filtered = EXERCISES.filter(e => {
    const ms = muscle === 'All' || e.muscle === muscle;
    const cat = category === 'All' || e.category === category;
    const sr = !search || e.name.toLowerCase().includes(search.toLowerCase());
    return ms && cat && sr;
  });

  return (
    <View style={{ flex: 1, backgroundColor: '#080808' }}>
      <View style={s.searchBar}>
        <TextInput style={s.searchInput} placeholder="Search exercises..." placeholderTextColor="#444"
          value={search} onChangeText={setSearch} />
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.filters} contentContainerStyle={{ paddingHorizontal: 16, gap: 8 }}>
        {CATEGORIES.map(c => (
          <TouchableOpacity key={c} style={[s.chip, category === c && s.chipActive]} onPress={() => setCategory(c)}>
            <Text style={[s.chipText, category === c && { color: '#FF4500' }]}>{c}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.filters} contentContainerStyle={{ paddingHorizontal: 16, gap: 8 }}>
        {MUSCLES.map(m => (
          <TouchableOpacity key={m} style={[s.chip, muscle === m && s.chipActive]} onPress={() => setMuscle(m)}>
            <Text style={[s.chipText, muscle === m && { color: '#0080FF' }]}>{m}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <Text style={s.count}>{filtered.length} EXERCISES</Text>

      <ScrollView contentContainerStyle={{ paddingBottom: 40 }}>
        {filtered.map((ex, i) => (
          <View key={i} style={s.exRow}>
            <View style={{ flex: 1 }}>
              <Text style={s.exName}>{ex.name}</Text>
              <View style={{ flexDirection: 'row', gap: 8, marginTop: 4 }}>
                <Text style={[s.tag, { color: '#FF4500' }]}>{ex.muscle}</Text>
                <Text style={[s.tag, { color: '#0080FF' }]}>{ex.category}</Text>
                <Text style={[s.tag, { color: '#555' }]}>{ex.equipment}</Text>
              </View>
              <Text style={s.cue}>{ex.cue}</Text>
            </View>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  searchBar: { padding: 16, borderBottomWidth: 1, borderBottomColor: '#111' },
  searchInput: { backgroundColor: '#0f0f0f', borderWidth: 1, borderColor: '#1e1e1e', color: '#f0f0f0', padding: 10, fontSize: 15 },
  filters: { flexGrow: 0, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#111', maxHeight: 46 },
  chip: { paddingHorizontal: 12, paddingVertical: 6, borderWidth: 1, borderColor: '#1e1e1e' },
  chipActive: { borderColor: '#333' },
  chipText: { color: '#555', fontSize: 12 },
  count: { fontSize: 9, letterSpacing: 3, color: '#333', padding: 12, paddingHorizontal: 16 },
  exRow: { padding: 14, borderBottomWidth: 1, borderBottomColor: '#111' },
  exName: { fontSize: 16, fontWeight: '700', color: '#f0f0f0' },
  tag: { fontSize: 10, fontWeight: '700', letterSpacing: 1 },
  cue: { fontSize: 12, color: '#444', marginTop: 4, fontStyle: 'italic' },
});
`);

// ── BODY WEIGHT SCREEN ──
w('src/screens/BodyWeightScreen.js', `
import React, { useContext, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, TextInput, Alert } from 'react-native';
import Svg, { Polyline, Circle, Line, Text as SvgText } from 'react-native-svg';
import { AppContext } from '../context/AppContext';

function LineChart({ data, color = '#FF4500', width = 340, height = 160 }) {
  if (data.length < 2) return (
    <View style={{ width, height, justifyContent: 'center', alignItems: 'center' }}>
      <Text style={{ color: '#333', fontSize: 12 }}>Log at least 2 entries to see chart</Text>
    </View>
  );
  const vals = data.map(d => d.weight);
  const min = Math.min(...vals) - 1;
  const max = Math.max(...vals) + 1;
  const pad = 30;
  const W = width - pad * 2;
  const H = height - pad * 2;
  const pts = data.map((d, i) => {
    const x = pad + (i / (data.length - 1)) * W;
    const y = pad + H - ((d.weight - min) / (max - min)) * H;
    return [x, y];
  });
  const polyPts = pts.map(p => p.join(',')).join(' ');
  return (
    <Svg width={width} height={height}>
      <Polyline points={polyPts} fill="none" stroke={color} strokeWidth="2" />
      {pts.map(([x, y], i) => (
        <Circle key={i} cx={x} cy={y} r={3} fill={color} />
      ))}
      {[0, Math.floor(data.length / 2), data.length - 1].map(i => {
        if (i >= data.length) return null;
        const [x, y] = pts[i];
        return (
          <SvgText key={i} x={x} y={y - 8} fill="#666" fontSize="10" textAnchor="middle">
            {data[i].weight}
          </SvgText>
        );
      })}
      <SvgText x={pad} y={height - 6} fill="#333" fontSize="9">{data[data.length - 1]?.date?.slice(5)}</SvgText>
      <SvgText x={width - pad} y={height - 6} fill="#333" fontSize="9" textAnchor="end">{data[0]?.date?.slice(5)}</SvgText>
    </Svg>
  );
}

export default function BodyWeightScreen() {
  const { bodyWeight, logBodyWeight } = useContext(AppContext);
  const [input, setInput] = useState('');

  const logWeight = () => {
    const val = parseFloat(input);
    if (!val || val < 20 || val > 300) { Alert.alert('Enter a valid weight'); return; }
    const today = new Date().toISOString().split('T')[0];
    logBodyWeight({ date: today, weight: val });
    setInput('');
  };

  const displayed = [...bodyWeight].reverse(); // oldest first for chart
  const latest = bodyWeight[0];
  const weekAgo = bodyWeight.find(b => {
    const diff = (Date.now() - new Date(b.date).getTime()) / 86400000;
    return diff >= 6 && diff <= 8;
  });

  return (
    <ScrollView style={s.container} contentContainerStyle={{ padding: 20, paddingBottom: 40 }}>
      {/* Quick stats */}
      <View style={s.statsRow}>
        <View style={s.statCard}>
          <Text style={s.statVal}>{latest ? latest.weight + ' kg' : '—'}</Text>
          <Text style={s.statLabel}>CURRENT</Text>
        </View>
        {weekAgo && (
          <View style={s.statCard}>
            <Text style={[s.statVal, { color: latest.weight < weekAgo.weight ? '#00C170' : latest.weight > weekAgo.weight ? '#FF4500' : '#f0f0f0' }]}>
              {latest.weight < weekAgo.weight ? '-' : '+'}{Math.abs(latest.weight - weekAgo.weight).toFixed(1)} kg
            </Text>
            <Text style={s.statLabel}>VS LAST WEEK</Text>
          </View>
        )}
        <View style={s.statCard}>
          <Text style={s.statVal}>{bodyWeight.length}</Text>
          <Text style={s.statLabel}>ENTRIES</Text>
        </View>
      </View>

      {/* Log input */}
      <View style={s.logRow}>
        <TextInput style={s.input} placeholder="Weight in kg" placeholderTextColor="#444"
          keyboardType="decimal-pad" value={input} onChangeText={setInput} />
        <TouchableOpacity style={s.logBtn} onPress={logWeight}>
          <Text style={s.logBtnText}>LOG</Text>
        </TouchableOpacity>
      </View>

      {/* Chart */}
      {bodyWeight.length >= 2 && (
        <View style={s.chartBox}>
          <Text style={s.chartTitle}>LAST {Math.min(30, bodyWeight.length)} ENTRIES</Text>
          <LineChart data={displayed.slice(-30)} />
        </View>
      )}

      {/* History list */}
      <Text style={s.sectionLabel}>HISTORY</Text>
      {bodyWeight.slice(0, 30).map((entry, i) => (
        <View key={i} style={s.entryRow}>
          <Text style={s.entryDate}>{entry.date}</Text>
          <Text style={s.entryWeight}>{entry.weight} kg</Text>
        </View>
      ))}
      {bodyWeight.length === 0 && <Text style={{ color: '#444', fontSize: 13 }}>No entries yet.</Text>}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  statsRow: { flexDirection: 'row', gap: 10, marginBottom: 20 },
  statCard: { flex: 1, backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', padding: 14, alignItems: 'center' },
  statVal: { fontSize: 22, fontWeight: '900', color: '#f0f0f0' },
  statLabel: { fontSize: 9, letterSpacing: 3, color: '#444', marginTop: 4 },
  logRow: { flexDirection: 'row', gap: 10, marginBottom: 20 },
  input: { flex: 1, backgroundColor: '#0f0f0f', borderWidth: 1, borderColor: '#1e1e1e', color: '#f0f0f0', padding: 12, fontSize: 18, fontWeight: '700', textAlign: 'center' },
  logBtn: { backgroundColor: '#FF4500', paddingHorizontal: 20, justifyContent: 'center' },
  logBtnText: { color: '#fff', fontWeight: '900', fontSize: 14, letterSpacing: 2 },
  chartBox: { backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', padding: 12, marginBottom: 20 },
  chartTitle: { fontSize: 9, letterSpacing: 3, color: '#444', marginBottom: 8 },
  sectionLabel: { fontSize: 10, letterSpacing: 4, color: '#444', marginBottom: 12 },
  entryRow: { flexDirection: 'row', justifyContent: 'space-between', padding: 12, borderBottomWidth: 1, borderBottomColor: '#111' },
  entryDate: { fontSize: 13, color: '#666' },
  entryWeight: { fontSize: 16, fontWeight: '800', color: '#f0f0f0' },
});
`);

// ── SETTINGS SCREEN (full with rest timer editing + backup) ──
w('src/screens/SettingsScreen.js', `
import React, { useContext, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert, TextInput, Modal } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { exportBackup, importBackup } from '../utils/backup';

export default function SettingsScreen({ navigation }) {
  const { settings, updateSettings, getAllData, restoreData, clearHistory, clearPbs } = useContext(AppContext);
  const [editTimer, setEditTimer] = useState(null);
  const [timerVal, setTimerVal] = useState('');

  const saveTimer = () => {
    const val = parseInt(timerVal);
    if (!val || val < 10 || val > 600) { Alert.alert('Enter 10\u2013600 seconds'); return; }
    updateSettings({ ...settings, [editTimer]: val });
    setEditTimer(null);
  };

  const doExport = async () => {
    try {
      await exportBackup(getAllData());
      Alert.alert('Backup saved!', 'Your data has been exported.');
    } catch (e) { Alert.alert('Export failed', String(e)); }
  };

  const doImport = async () => {
    try {
      const data = await importBackup();
      if (!data) return;
      Alert.alert('Restore backup?', 'This will replace all current data.', [
        { text: 'Cancel' },
        { text: 'Restore', style: 'destructive', onPress: async () => { await restoreData(data); Alert.alert('Restored!'); } },
      ]);
    } catch (e) { Alert.alert('Import failed', String(e)); }
  };

  const Row = ({ label, value, onPress, danger }) => (
    <TouchableOpacity style={s.row} onPress={onPress}>
      <Text style={[s.rowLabel, danger && { color: '#CC2222' }]}>{label}</Text>
      {value ? <Text style={s.rowValue}>{value}</Text> : <Ionicons name="chevron-forward" size={16} color="#444" />}
    </TouchableOpacity>
  );

  return (
    <ScrollView style={s.container} contentContainerStyle={{ padding: 20, paddingBottom: 40, gap: 16 }}>
      {/* Weight unit */}
      <View style={s.section}>
        <Text style={s.sectionTitle}>WEIGHT UNIT</Text>
        <View style={{ flexDirection: 'row', gap: 10 }}>
          {['kg', 'lbs'].map(u => (
            <TouchableOpacity key={u} style={[s.unitBtn, settings.weightUnit === u && s.unitBtnActive]}
              onPress={() => updateSettings({ ...settings, weightUnit: u })}>
              <Text style={[s.unitBtnText, settings.weightUnit === u && { color: '#FF4500' }]}>{u.toUpperCase()}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* Rest timers */}
      <View style={s.section}>
        <Text style={s.sectionTitle}>REST TIMERS</Text>
        <Row label="Normal exercises" value={settings.defaultRestNormal + 's'} onPress={() => { setTimerVal(String(settings.defaultRestNormal)); setEditTimer('defaultRestNormal'); }} />
        <Row label="Heavy exercises" value={settings.defaultRestHeavy + 's'} onPress={() => { setTimerVal(String(settings.defaultRestHeavy)); setEditTimer('defaultRestHeavy'); }} />
      </View>

      {/* Plate calculator */}
      <View style={s.section}>
        <Text style={s.sectionTitle}>PLATE CALCULATOR</Text>
        <Row label="Bar weight" value={settings.barWeight + ' kg'} onPress={() => { setTimerVal(String(settings.barWeight)); setEditTimer('barWeight'); }} />
        <TouchableOpacity style={s.row} onPress={() => navigation.navigate('ExerciseLibrary')}>
          <Text style={s.rowLabel}>Exercise Library</Text>
          <Ionicons name="chevron-forward" size={16} color="#444" />
        </TouchableOpacity>
        <TouchableOpacity style={s.row} onPress={() => navigation.navigate('BodyWeight')}>
          <Text style={s.rowLabel}>Body Weight Tracker</Text>
          <Ionicons name="chevron-forward" size={16} color="#444" />
        </TouchableOpacity>
      </View>

      {/* Backup */}
      <View style={s.section}>
        <Text style={s.sectionTitle}>BACKUP & RESTORE</Text>
        <Row label="Export backup (JSON)" onPress={doExport} />
        <Row label="Import backup" onPress={doImport} />
      </View>

      {/* Danger zone */}
      <View style={s.section}>
        <Text style={s.sectionTitle}>DANGER ZONE</Text>
        <Row label="Clear all history" danger onPress={() => Alert.alert('Clear history?', '', [{ text: 'Cancel' }, { text: 'Clear', style: 'destructive', onPress: clearHistory }])} />
        <Row label="Reset all PBs" danger onPress={() => Alert.alert('Reset PBs?', '', [{ text: 'Cancel' }, { text: 'Reset', style: 'destructive', onPress: clearPbs }])} />
      </View>

      <Text style={{ color: '#222', fontSize: 11, textAlign: 'center', letterSpacing: 2 }}>IRONLOG v1.0 \u00b7 BUILT FOR PRANAV</Text>

      {/* Timer edit modal */}
      <Modal visible={!!editTimer} transparent animationType="fade">
        <View style={s.overlay}>
          <View style={s.modal}>
            <Text style={s.modalTitle}>{editTimer === 'defaultRestNormal' ? 'NORMAL REST' : editTimer === 'defaultRestHeavy' ? 'HEAVY REST' : 'BAR WEIGHT'}</Text>
            <TextInput style={s.input} keyboardType="numeric" value={timerVal} onChangeText={setTimerVal} autoFocus
              placeholder={editTimer === 'barWeight' ? 'kg' : 'seconds'} placeholderTextColor="#444" />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={s.cancelBtn} onPress={() => setEditTimer(null)}><Text style={{ color: '#666' }}>Cancel</Text></TouchableOpacity>
              <TouchableOpacity style={s.confirmBtn} onPress={saveTimer}><Text style={{ color: '#fff', fontWeight: '800' }}>SAVE</Text></TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  section: { backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', padding: 16 },
  sectionTitle: { fontSize: 10, letterSpacing: 3, color: '#444', marginBottom: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#111' },
  rowLabel: { fontSize: 14, color: '#f0f0f0' },
  rowValue: { fontSize: 14, color: '#666' },
  unitBtn: { flex: 1, borderWidth: 1, borderColor: '#1e1e1e', padding: 12, alignItems: 'center' },
  unitBtnActive: { borderColor: '#FF4500', backgroundColor: '#FF450011' },
  unitBtnText: { fontWeight: '800', fontSize: 16, color: '#666', letterSpacing: 2 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', justifyContent: 'center', alignItems: 'center', padding: 24 },
  modal: { backgroundColor: '#141414', borderRadius: 12, padding: 24, width: '100%', borderWidth: 1, borderColor: '#1e1e1e' },
  modalTitle: { color: '#f0f0f0', fontSize: 16, fontWeight: '900', letterSpacing: 2, marginBottom: 16 },
  input: { backgroundColor: '#0f0f0f', borderWidth: 1, borderColor: '#2a2a2a', color: '#f0f0f0', padding: 14, fontSize: 24, fontWeight: '900', textAlign: 'center' },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1, borderColor: '#1e1e1e' },
  confirmBtn: { flex: 1, backgroundColor: '#FF4500', padding: 14, alignItems: 'center' },
});
`);

console.log('ExLib + BodyWeight + Settings done');
