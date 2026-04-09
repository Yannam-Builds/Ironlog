
import React, { useState, useEffect, useContext } from 'react';
import { View, Text, TextInput, TouchableOpacity, ScrollView, Modal, StyleSheet } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import { triggerHaptic } from '../services/hapticsEngine';

const PERCENTAGES = [50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100];

function epley(w, r) {
  if (!w || r <= 0) return 0;
  if (r === 1) return w;
  return Math.round(w * (1 + r / 30) * 10) / 10;
}

function roundToPlate(kg, plates) {
  const minPlate = plates && plates.length > 0
    ? Math.min(...plates.map(p => p.weight))
    : 1.25;
  // Round to nearest 2x minPlate (both sides)
  const step = minPlate * 2;
  return Math.round(kg / step) * step;
}

export default function TrainingMaxCalculator({ visible, onClose, exerciseName }) {
  const { settings, gymProfiles, activeGymProfileId } = useContext(AppContext);
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;

  const activeProfile = gymProfiles?.find(p => p.id === activeGymProfileId) || gymProfiles?.[0];

  const [mode, setMode] = useState('1rm');
  const [input1RM, setInput1RM] = useState('');
  const [inputWeight, setInputWeight] = useState('');
  const [inputReps, setInputReps] = useState('');
  const [tmPct, setTmPct] = useState(90);
  const [savedData, setSavedData] = useState(null);

  const exKey = (exerciseName || '').replace(/[^a-zA-Z0-9]/g, '_');

  useEffect(() => {
    if (!visible || !exKey) return;
    AsyncStorage.getItem('@ironlog/trainingMaxes').then(raw => {
      if (!raw) return;
      const all = JSON.parse(raw);
      if (all[exKey]) {
        setSavedData(all[exKey]);
        setInput1RM(String(all[exKey].estimated1RM || ''));
        setTmPct(all[exKey].trainingMaxPercent || 90);
      }
    }).catch(() => {});
  }, [visible, exKey]);

  const computed1RM = mode === '1rm'
    ? parseFloat(input1RM) || 0
    : epley(parseFloat(inputWeight) || 0, parseInt(inputReps) || 0);

  const trainingMax = computed1RM > 0 ? Math.round(computed1RM * (tmPct / 100) * 10) / 10 : 0;

  const doSave = async () => {
    if (!trainingMax) return;
    triggerHaptic('mediumConfirm', { enabled: haptic }).catch(() => {});
    try {
      const raw = await AsyncStorage.getItem('@ironlog/trainingMaxes');
      const all = raw ? JSON.parse(raw) : {};
      all[exKey] = {
        estimated1RM: computed1RM,
        trainingMaxPercent: tmPct,
        trainingMax,
        updatedAt: new Date().toISOString().split('T')[0],
      };
      await AsyncStorage.setItem('@ironlog/trainingMaxes', JSON.stringify(all));
      setSavedData(all[exKey]);
    } catch (_) {}
  };

  const copyWeight = w => {
    triggerHaptic('selection', { enabled: haptic }).catch(() => {});
    // Visual feedback via haptic; user reads value from screen
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.7)' }}>
        <View style={[s.sheet, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <Text style={[s.title, { color: colors.text }]}>TRAINING MAX</Text>
            <TouchableOpacity onPress={onClose} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Text style={{ color: colors.muted, fontSize: 12, letterSpacing: 1 }}>CLOSE</Text>
            </TouchableOpacity>
          </View>

          {exerciseName ? (
            <Text style={[s.exName, { color: colors.accent }]} numberOfLines={1}>{exerciseName}</Text>
          ) : null}

          {/* Mode tabs */}
          <View style={{ flexDirection: 'row', gap: 8, marginBottom: 14 }}>
            {[['1rm', 'ENTER 1RM'], ['calc', 'CALCULATE']].map(([m, label]) => (
              <TouchableOpacity key={m}
                style={[s.modeTab, { borderColor: colors.faint }, mode === m && { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
                onPress={() => setMode(m)}>
                <Text style={[s.modeTabText, { color: mode === m ? colors.accent : colors.muted }]}>{label}</Text>
              </TouchableOpacity>
            ))}
          </View>

          {mode === '1rm' ? (
            <View style={{ marginBottom: 12 }}>
              <Text style={[s.fieldLabel, { color: colors.muted }]}>KNOWN 1RM (kg)</Text>
              <TextInput style={[s.bigField, { color: colors.text, borderBottomColor: colors.accent }]}
                value={input1RM} onChangeText={setInput1RM} keyboardType="decimal-pad"
                placeholder="120" placeholderTextColor={colors.muted} />
            </View>
          ) : (
            <View style={{ flexDirection: 'row', gap: 12, marginBottom: 12 }}>
              <View style={{ flex: 1 }}>
                <Text style={[s.fieldLabel, { color: colors.muted }]}>WEIGHT (kg)</Text>
                <TextInput style={[s.bigField, { color: colors.text, borderBottomColor: colors.accent }]}
                  value={inputWeight} onChangeText={setInputWeight} keyboardType="decimal-pad"
                  placeholder="100" placeholderTextColor={colors.muted} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[s.fieldLabel, { color: colors.muted }]}>REPS</Text>
                <TextInput style={[s.bigField, { color: colors.text, borderBottomColor: colors.accent }]}
                  value={inputReps} onChangeText={setInputReps} keyboardType="number-pad"
                  placeholder="5" placeholderTextColor={colors.muted} />
              </View>
            </View>
          )}

          {/* TM% selector */}
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
            <Text style={[s.fieldLabel, { color: colors.muted }]}>TM%</Text>
            {[80, 85, 90, 95].map(pct => (
              <TouchableOpacity key={pct}
                style={[s.pctChip, { borderColor: colors.faint }, tmPct === pct && { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
                onPress={() => setTmPct(pct)}>
                <Text style={[s.pctText, { color: tmPct === pct ? colors.accent : colors.muted }]}>{pct}%</Text>
              </TouchableOpacity>
            ))}
          </View>

          {computed1RM > 0 && (
            <>
              <View style={[s.resultRow, { borderColor: colors.faint }]}>
                <View>
                  <Text style={[s.resultLabel, { color: colors.muted }]}>EST. 1RM</Text>
                  <Text style={[s.resultVal, { color: colors.text }]}>{computed1RM}kg</Text>
                </View>
                <View style={{ alignItems: 'flex-end' }}>
                  <Text style={[s.resultLabel, { color: colors.muted }]}>TRAINING MAX ({tmPct}%)</Text>
                  <Text style={[s.resultVal, { color: colors.accent }]}>{trainingMax}kg</Text>
                </View>
              </View>

              <ScrollView style={{ maxHeight: 180 }} showsVerticalScrollIndicator={false}>
                <Text style={[s.tableHeader, { color: colors.muted }]}>% TABLE · TAP ROW TO COPY</Text>
                {PERCENTAGES.map(pct => {
                  const raw = trainingMax * (pct / 100);
                  const rounded = roundToPlate(raw, activeProfile?.plates);
                  return (
                    <TouchableOpacity key={pct}
                      style={[s.tableRow, { borderBottomColor: colors.faint }]}
                      onPress={() => copyWeight(rounded)}>
                      <Text style={[s.tablePct, { color: colors.muted }]}>{pct}%</Text>
                      <Text style={[s.tableRaw, { color: colors.muted }]}>{raw.toFixed(1)}kg</Text>
                      <Text style={[s.tableRounded, { color: colors.text }]}>{rounded}kg</Text>
                    </TouchableOpacity>
                  );
                })}
              </ScrollView>

              <TouchableOpacity style={[s.saveBtn, { backgroundColor: colors.accent }]} onPress={doSave}>
                <Text style={s.saveBtnText}>SAVE TRAINING MAX</Text>
              </TouchableOpacity>
            </>
          )}

          {savedData ? (
            <Text style={[s.savedNote, { color: colors.muted }]}>
              Saved: {savedData.trainingMax}kg @ {savedData.trainingMaxPercent}% · {savedData.updatedAt}
            </Text>
          ) : null}
        </View>
      </View>
    </Modal>
  );
}

const s = StyleSheet.create({
  sheet: { padding: 20, borderTopWidth: 1, borderTopLeftRadius: 16, borderTopRightRadius: 16, maxHeight: '92%' },
  title: { fontSize: 14, fontWeight: '900', letterSpacing: 3 },
  exName: { fontSize: 13, fontWeight: '700', marginBottom: 12 },
  modeTab: { flex: 1, paddingVertical: 8, alignItems: 'center', borderWidth: 1 },
  modeTabText: { fontSize: 10, fontWeight: '700', letterSpacing: 1 },
  fieldLabel: { fontSize: 9, letterSpacing: 2, marginBottom: 4 },
  bigField: { fontSize: 26, fontWeight: '900', borderBottomWidth: 2, paddingVertical: 6 },
  pctChip: { paddingHorizontal: 10, paddingVertical: 5, borderWidth: 1 },
  pctText: { fontSize: 11, fontWeight: '700' },
  resultRow: { flexDirection: 'row', justifyContent: 'space-between', borderTopWidth: 1, borderBottomWidth: 1, paddingVertical: 12, marginBottom: 8 },
  resultLabel: { fontSize: 8, letterSpacing: 2, marginBottom: 4 },
  resultVal: { fontSize: 22, fontWeight: '900' },
  tableHeader: { fontSize: 8, letterSpacing: 2, marginBottom: 6, marginTop: 4 },
  tableRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 9, borderBottomWidth: 1, gap: 8 },
  tablePct: { width: 36, fontSize: 12, fontWeight: '700' },
  tableRaw: { flex: 1, fontSize: 12 },
  tableRounded: { fontSize: 15, fontWeight: '900', minWidth: 64, textAlign: 'right' },
  saveBtn: { padding: 14, alignItems: 'center', marginTop: 10 },
  saveBtnText: { color: '#fff', fontWeight: '800', fontSize: 12, letterSpacing: 2 },
  savedNote: { fontSize: 10, textAlign: 'center', marginTop: 8 },
});
