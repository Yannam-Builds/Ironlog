const fs = require('fs');
const path = require('path');
function w(p, c) {
  const d = path.dirname(p);
  if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
  fs.writeFileSync(p, c);
  console.log('W: ' + p);
}

// ── PLANS SCREEN ──
w('src/screens/PlansScreen.js', `
import React, { useContext, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert, TextInput, Modal } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';

function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }

export default function PlansScreen({ navigation }) {
  const { plans, savePlans } = useContext(AppContext);
  const [showNew, setShowNew] = useState(false);
  const [newName, setNewName] = useState('');

  const createPlan = () => {
    if (!newName.trim()) return;
    savePlans([...plans, { id: genId(), name: newName.trim(), days: [] }]);
    setNewName(''); setShowNew(false);
  };

  const deletePlan = (id) => {
    Alert.alert('Delete plan?', '', [
      { text: 'Cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => savePlans(plans.filter(p => p.id !== id)) },
    ]);
  };

  return (
    <View style={{ flex: 1, backgroundColor: '#080808' }}>
      <ScrollView contentContainerStyle={{ padding: 20, paddingBottom: 40 }}>
        {plans.map(plan => (
          <View key={plan.id} style={s.planCard}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <Text style={s.planName}>{plan.name}</Text>
              <View style={{ flexDirection: 'row', gap: 12 }}>
                <TouchableOpacity onPress={() => navigation.navigate('PlanEditor', { planId: plan.id })}>
                  <Ionicons name="pencil" size={18} color="#666" />
                </TouchableOpacity>
                <TouchableOpacity onPress={() => deletePlan(plan.id)}>
                  <Ionicons name="trash-outline" size={18} color="#CC2222" />
                </TouchableOpacity>
              </View>
            </View>
            {plan.days.map((d, i) => (
              <View key={d.id} style={[s.dayRow, { borderLeftColor: d.color || '#444' }]}>
                <View>
                  <Text style={s.dayName}>{d.name}</Text>
                  <Text style={s.dayMeta}>{d.exercises?.length || 0} exercises</Text>
                </View>
                <Text style={{ color: '#333', fontSize: 11 }}>{d.tag}</Text>
              </View>
            ))}
            {plan.days.length === 0 && (
              <TouchableOpacity style={s.addDayBtn} onPress={() => navigation.navigate('PlanEditor', { planId: plan.id })}>
                <Text style={s.addDayBtnText}>+ ADD DAYS</Text>
              </TouchableOpacity>
            )}
          </View>
        ))}

        <TouchableOpacity style={s.newPlanBtn} onPress={() => setShowNew(true)}>
          <Ionicons name="add" size={20} color="#FF4500" />
          <Text style={s.newPlanBtnText}>NEW PLAN</Text>
        </TouchableOpacity>
      </ScrollView>

      <Modal visible={showNew} transparent animationType="fade">
        <View style={s.modalOverlay}>
          <View style={s.modalCard}>
            <Text style={s.modalTitle}>NEW PLAN</Text>
            <TextInput style={s.input} placeholder="Plan name" placeholderTextColor="#444"
              value={newName} onChangeText={setNewName} autoFocus />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={s.cancelBtn} onPress={() => { setShowNew(false); setNewName(''); }}>
                <Text style={{ color: '#666' }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={s.confirmBtn} onPress={createPlan}>
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
  planCard: { backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', padding: 16, marginBottom: 16 },
  planName: { fontSize: 20, fontWeight: '900', color: '#f0f0f0', letterSpacing: -0.5 },
  dayRow: { borderLeftWidth: 3, paddingLeft: 12, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#111', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  dayName: { fontSize: 15, fontWeight: '700', color: '#f0f0f0' },
  dayMeta: { fontSize: 11, color: '#555', marginTop: 2 },
  addDayBtn: { borderWidth: 1, borderColor: '#FF4500', borderStyle: 'dashed', padding: 12, alignItems: 'center', marginTop: 8 },
  addDayBtnText: { color: '#FF4500', fontSize: 12, letterSpacing: 2, fontWeight: '700' },
  newPlanBtn: { flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: '#FF4500', borderStyle: 'dashed', padding: 16, justifyContent: 'center' },
  newPlanBtnText: { color: '#FF4500', fontSize: 14, fontWeight: '800', letterSpacing: 2 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', justifyContent: 'center', alignItems: 'center', padding: 24 },
  modalCard: { backgroundColor: '#141414', borderRadius: 12, padding: 24, width: '100%', borderWidth: 1, borderColor: '#1e1e1e' },
  modalTitle: { color: '#f0f0f0', fontSize: 18, fontWeight: '900', letterSpacing: 2, marginBottom: 16 },
  input: { backgroundColor: '#0f0f0f', borderWidth: 1, borderColor: '#2a2a2a', color: '#f0f0f0', padding: 12, fontSize: 16 },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1, borderColor: '#1e1e1e' },
  confirmBtn: { flex: 1, backgroundColor: '#FF4500', padding: 14, alignItems: 'center' },
});
`);

// ── PLAN EDITOR SCREEN ──
w('src/screens/PlanEditorScreen.js', `
import React, { useContext, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert, TextInput, Modal } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { EXERCISES, MUSCLES } from '../data/exerciseLibrary';

function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }
const DAY_COLORS = ['#FF4500','#0080FF','#00C170','#A020F0','#FFD700','#FF6B35','#00BCD4'];

export default function PlanEditorScreen({ route, navigation }) {
  const { planId } = route.params;
  const { plans, savePlans } = useContext(AppContext);
  const planIdx = plans.findIndex(p => p.id === planId);
  const plan = plans[planIdx];

  const [editDayIdx, setEditDayIdx] = useState(null);
  const [showAddDay, setShowAddDay] = useState(false);
  const [dayName, setDayName] = useState('');
  const [dayTag, setDayTag] = useState('');
  const [showExLib, setShowExLib] = useState(false);
  const [libSearch, setLibSearch] = useState('');
  const [libMuscle, setLibMuscle] = useState('All');

  if (!plan) return null;

  const updatePlan = (updated) => {
    const newPlans = [...plans];
    newPlans[planIdx] = updated;
    savePlans(newPlans);
  };

  const addDay = () => {
    if (!dayName.trim()) return;
    const colorIdx = plan.days.length % DAY_COLORS.length;
    const newDay = { id: genId(), name: dayName.trim().toUpperCase(), tag: dayTag.trim(), color: DAY_COLORS[colorIdx], exercises: [] };
    updatePlan({ ...plan, days: [...plan.days, newDay] });
    setDayName(''); setDayTag(''); setShowAddDay(false);
  };

  const deleteDay = (dayIdx) => {
    Alert.alert('Delete day?', '', [
      { text: 'Cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => {
        const days = plan.days.filter((_, i) => i !== dayIdx);
        updatePlan({ ...plan, days });
        if (editDayIdx === dayIdx) setEditDayIdx(null);
      }},
    ]);
  };

  const addExercise = (ex) => {
    if (editDayIdx === null) return;
    const days = [...plan.days];
    days[editDayIdx] = {
      ...days[editDayIdx],
      exercises: [...(days[editDayIdx].exercises || []), { name: ex.name, sets: 3, reps: '10', primary: ex.muscle, note: ex.cue }],
    };
    updatePlan({ ...plan, days });
    setShowExLib(false);
  };

  const removeExercise = (dayIdx, exIdx) => {
    const days = [...plan.days];
    days[dayIdx] = { ...days[dayIdx], exercises: days[dayIdx].exercises.filter((_, i) => i !== exIdx) };
    updatePlan({ ...plan, days });
  };

  const moveExercise = (dayIdx, exIdx, dir) => {
    const days = [...plan.days];
    const exs = [...days[dayIdx].exercises];
    const swap = exIdx + dir;
    if (swap < 0 || swap >= exs.length) return;
    [exs[exIdx], exs[swap]] = [exs[swap], exs[exIdx]];
    days[dayIdx] = { ...days[dayIdx], exercises: exs };
    updatePlan({ ...plan, days });
  };

  const filtered = EXERCISES.filter(e => {
    const ms = libMuscle === 'All' || e.muscle === libMuscle;
    const sr = !libSearch || e.name.toLowerCase().includes(libSearch.toLowerCase());
    return ms && sr;
  });

  const editDay = editDayIdx !== null ? plan.days[editDayIdx] : null;

  return (
    <View style={{ flex: 1, backgroundColor: '#080808' }}>
      <ScrollView contentContainerStyle={{ padding: 20, paddingBottom: 40 }}>
        <Text style={s.planName}>{plan.name}</Text>

        {plan.days.map((day, di) => (
          <View key={day.id} style={s.dayCard}>
            <TouchableOpacity style={[s.dayHeader, { borderLeftColor: day.color }]}
              onPress={() => setEditDayIdx(editDayIdx === di ? null : di)}>
              <View>
                <Text style={s.dayName}>{day.name}</Text>
                <Text style={s.dayTag}>{day.tag || 'No description'}</Text>
              </View>
              <View style={{ flexDirection: 'row', gap: 12, alignItems: 'center' }}>
                <TouchableOpacity onPress={() => deleteDay(di)}>
                  <Ionicons name="trash-outline" size={16} color="#CC2222" />
                </TouchableOpacity>
                <Ionicons name={editDayIdx === di ? 'chevron-up' : 'chevron-down'} size={16} color="#666" />
              </View>
            </TouchableOpacity>

            {editDayIdx === di && (
              <View style={{ paddingTop: 8 }}>
                {(day.exercises || []).map((ex, ei) => (
                  <View key={ei} style={s.exRow}>
                    <View style={{ flex: 1 }}>
                      <Text style={s.exName}>{ex.name}</Text>
                      <Text style={s.exMeta}>{ex.sets} sets \u00b7 {ex.reps} reps \u00b7 {ex.primary}</Text>
                    </View>
                    <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}>
                      <TouchableOpacity onPress={() => moveExercise(di, ei, -1)}>
                        <Ionicons name="chevron-up" size={16} color="#555" />
                      </TouchableOpacity>
                      <TouchableOpacity onPress={() => moveExercise(di, ei, 1)}>
                        <Ionicons name="chevron-down" size={16} color="#555" />
                      </TouchableOpacity>
                      <TouchableOpacity onPress={() => removeExercise(di, ei)}>
                        <Ionicons name="close" size={16} color="#CC2222" />
                      </TouchableOpacity>
                    </View>
                  </View>
                ))}
                <TouchableOpacity style={s.addExBtn} onPress={() => { setEditDayIdx(di); setShowExLib(true); }}>
                  <Text style={s.addExBtnText}>+ ADD EXERCISE</Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
        ))}

        <TouchableOpacity style={s.addDayBtn} onPress={() => setShowAddDay(true)}>
          <Ionicons name="add" size={18} color="#FF4500" />
          <Text style={s.addDayBtnText}>ADD DAY</Text>
        </TouchableOpacity>
      </ScrollView>

      {/* Add Day Modal */}
      <Modal visible={showAddDay} transparent animationType="fade">
        <View style={s.overlay}>
          <View style={s.modal}>
            <Text style={s.modalTitle}>ADD DAY</Text>
            <TextInput style={s.input} placeholder="Day name (e.g. PUSH)" placeholderTextColor="#444"
              value={dayName} onChangeText={setDayName} autoFocus />
            <TextInput style={[s.input, { marginTop: 10 }]} placeholder="Tag (e.g. Chest \u00b7 Shoulders)" placeholderTextColor="#444"
              value={dayTag} onChangeText={setDayTag} />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={s.cancelBtn} onPress={() => setShowAddDay(false)}><Text style={{ color: '#666' }}>Cancel</Text></TouchableOpacity>
              <TouchableOpacity style={s.confirmBtn} onPress={addDay}><Text style={{ color: '#fff', fontWeight: '800' }}>ADD</Text></TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* Exercise Library Modal */}
      <Modal visible={showExLib} transparent animationType="slide">
        <View style={s.libOverlay}>
          <View style={s.libCard}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <Text style={s.modalTitle}>ADD EXERCISE</Text>
              <TouchableOpacity onPress={() => setShowExLib(false)}><Text style={{ color: '#666', fontSize: 20 }}>\u2715</Text></TouchableOpacity>
            </View>
            <TextInput style={s.input} placeholder="Search..." placeholderTextColor="#444"
              value={libSearch} onChangeText={setLibSearch} />
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginVertical: 10, maxHeight: 36 }} contentContainerStyle={{ gap: 8 }}>
              {['All',...MUSCLES.slice(1)].map(m => (
                <TouchableOpacity key={m} onPress={() => setLibMuscle(m)}
                  style={[s.chip, libMuscle === m && s.chipActive]}>
                  <Text style={[s.chipText, libMuscle === m && { color: '#FF4500' }]}>{m}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
            <ScrollView>
              {filtered.map((ex, i) => (
                <TouchableOpacity key={i} style={s.libRow} onPress={() => addExercise(ex)}>
                  <View style={{ flex: 1 }}>
                    <Text style={s.exName}>{ex.name}</Text>
                    <Text style={s.exMeta}>{ex.muscle} \u00b7 {ex.equipment} \u00b7 {ex.cue}</Text>
                  </View>
                  <Text style={{ color: '#FF4500', fontSize: 20 }}>+</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  planName: { fontSize: 24, fontWeight: '900', color: '#f0f0f0', marginBottom: 20, letterSpacing: -0.5 },
  dayCard: { backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', marginBottom: 12 },
  dayHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 14, borderLeftWidth: 4 },
  dayName: { fontSize: 18, fontWeight: '900', color: '#f0f0f0' },
  dayTag: { fontSize: 11, color: '#555', marginTop: 2 },
  exRow: { flexDirection: 'row', alignItems: 'center', padding: 12, borderTopWidth: 1, borderTopColor: '#111' },
  exName: { fontSize: 14, fontWeight: '600', color: '#f0f0f0' },
  exMeta: { fontSize: 11, color: '#555', marginTop: 2 },
  addExBtn: { margin: 12, borderWidth: 1, borderColor: '#FF4500', borderStyle: 'dashed', padding: 10, alignItems: 'center' },
  addExBtnText: { color: '#FF4500', fontSize: 12, fontWeight: '700', letterSpacing: 2 },
  addDayBtn: { flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: '#FF4500', borderStyle: 'dashed', padding: 16, justifyContent: 'center' },
  addDayBtnText: { color: '#FF4500', fontSize: 14, fontWeight: '800', letterSpacing: 2 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', justifyContent: 'center', alignItems: 'center', padding: 24 },
  modal: { backgroundColor: '#141414', borderRadius: 12, padding: 24, width: '100%', borderWidth: 1, borderColor: '#1e1e1e' },
  modalTitle: { color: '#f0f0f0', fontSize: 18, fontWeight: '900', letterSpacing: 2, marginBottom: 16 },
  input: { backgroundColor: '#0f0f0f', borderWidth: 1, borderColor: '#2a2a2a', color: '#f0f0f0', padding: 12, fontSize: 15 },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1, borderColor: '#1e1e1e' },
  confirmBtn: { flex: 1, backgroundColor: '#FF4500', padding: 14, alignItems: 'center' },
  libOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.9)', justifyContent: 'flex-end' },
  libCard: { backgroundColor: '#141414', borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 20, height: '85%', borderTopWidth: 1, borderColor: '#1e1e1e' },
  chip: { paddingHorizontal: 12, paddingVertical: 6, borderWidth: 1, borderColor: '#1e1e1e' },
  chipActive: { borderColor: '#FF4500' },
  chipText: { color: '#555', fontSize: 12 },
  libRow: { flexDirection: 'row', alignItems: 'center', padding: 12, borderBottomWidth: 1, borderBottomColor: '#111' },
});
`);

console.log('Plans + PlanEditor done');
