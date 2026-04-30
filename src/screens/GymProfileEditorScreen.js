
import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import CustomAlert from '../components/CustomAlert';
import Ionicons from 'react-native-vector-icons/Ionicons';
import useWatermelonGymProfiles from '../hooks/useWatermelonGymProfiles';
import useWatermelonSettings from '../hooks/useWatermelonSettings';
import { useTheme } from '../context/ThemeContext';
import { fireHaptic } from '../services/hapticsEngine';
import { convertKgToUnit, convertUnitToKg } from '../utils/weightUnits';

function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }

const DEFAULT_PLATES = [20, 15, 10, 5, 2.5, 1.25].map(w => ({ weight: w, quantity: 2 }));

export default function GymProfileEditorScreen({ route, navigation }) {
  const { profile } = route.params || {};
  const { gymProfiles, saveGymProfiles } = useWatermelonGymProfiles();
  const { settings } = useWatermelonSettings();
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;
  const unit = settings?.weightUnit === 'lbs' ? 'lbs' : 'kg';
  const unitLabel = unit === 'lbs' ? 'lb' : 'kg';

  const [name, setName] = useState(profile?.name || '');
  const [barWeight, setBarWeight] = useState(
    String(convertKgToUnit(Number(profile?.barWeight ?? 20), unit, 2))
  );
  const [plates, setPlates] = useState(profile?.plates || DEFAULT_PLATES);
  const [newPlate, setNewPlate] = useState('');

  const [alertConfig, setAlertConfig] = useState(null);

  const addPlate = () => {
    const typedWeight = parseFloat(newPlate);
    if (!typedWeight || typedWeight <= 0) {
      setAlertConfig({ title: 'Invalid weight', message: `Enter a positive plate weight (${unitLabel}).`, buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    const weightKg = convertUnitToKg(typedWeight, unit);
    if (!Number.isFinite(weightKg) || weightKg <= 0) {
      setAlertConfig({ title: 'Invalid weight', message: `Could not convert ${unitLabel} value to kg.`, buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    const exists = plates.some((p) => Math.abs(Number(p.weight || 0) - weightKg) < 0.001);
    if (exists) { setAlertConfig({ title: 'Already exists', message: 'That plate weight is already in the list.', buttons: [{ text: 'OK', style: 'default' }] }); return; }
    setPlates(prev => [...prev, { weight: weightKg, quantity: 2 }].sort((a, b) => b.weight - a.weight));
    setNewPlate('');
  };

  const removePlate = w => setPlates(prev => prev.filter(p => p.weight !== w));

  const adjustQty = (w, delta) =>
    setPlates(prev => prev.map(p => p.weight === w ? { ...p, quantity: Math.max(1, p.quantity + delta) } : p));

  const save = () => {
    if (!name.trim()) { setAlertConfig({ title: 'Name required', message: 'Enter a profile name.', buttons: [{ text: 'OK', style: 'default' }] }); return; }
    const parsedBar = parseFloat(barWeight);
    if (!parsedBar || parsedBar <= 0) {
      setAlertConfig({ title: 'Invalid bar weight', message: `Enter a valid bar weight in ${unitLabel}.`, buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    const bw = convertUnitToKg(parsedBar, unit);
    if (!Number.isFinite(bw) || bw <= 0) {
      setAlertConfig({ title: 'Invalid bar weight', message: 'Could not convert the entered bar weight to kg.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    fireHaptic('mediumConfirm', { enabled: haptic });
    const updated = { id: profile?.id || genId(), name: name.trim(), barWeight: bw, plates, isDefault: profile?.isDefault || false };
    if (profile) {
      saveGymProfiles(gymProfiles.map(p => p.id === profile.id ? updated : p));
    } else {
      saveGymProfiles([...gymProfiles, updated]);
    }
    navigation.goBack();
  };

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ padding: 20, gap: 16, paddingBottom: 40 }}>
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>PROFILE NAME</Text>
        <TextInput
          style={[s.bigInput, { color: colors.text, borderBottomColor: colors.accent }]}
          value={name} onChangeText={setName}
          placeholder="e.g. Home Gym, Planet Fitness"
          placeholderTextColor={colors.muted}
          autoFocus={!profile}
        />
      </View>

      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>BAR WEIGHT ({unitLabel})</Text>
        <TextInput
          style={[s.bigInput, { color: colors.text, borderBottomColor: colors.accent }]}
          value={barWeight} onChangeText={setBarWeight}
          keyboardType="decimal-pad" placeholder={unit === 'lbs' ? '45' : '20'}
          placeholderTextColor={colors.muted}
        />
      </View>

      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>AVAILABLE PLATES</Text>
        <Text style={[s.hint, { color: colors.muted }]}>Quantity = pairs available per side</Text>
        {plates.map(plate => (
          <View key={plate.weight} style={[s.plateRow, { borderBottomColor: colors.faint }]}>
            <Text style={[s.plateWeight, { color: colors.text }]}>{convertKgToUnit(plate.weight, unit, 2)}{unitLabel}</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
              <TouchableOpacity onPress={() => adjustQty(plate.weight, -1)} style={[s.qBtn, { borderColor: colors.faint }]}>
                <Ionicons name="remove" size={14} color={colors.muted} />
              </TouchableOpacity>
              <Text style={[s.qVal, { color: colors.text }]}>{plate.quantity}</Text>
              <TouchableOpacity onPress={() => adjustQty(plate.weight, 1)} style={[s.qBtn, { borderColor: colors.faint }]}>
                <Ionicons name="add" size={14} color={colors.muted} />
              </TouchableOpacity>
              <TouchableOpacity onPress={() => removePlate(plate.weight)} style={{ marginLeft: 6 }}>
                <Ionicons name="close-circle" size={18} color="#CC2222" />
              </TouchableOpacity>
            </View>
          </View>
        ))}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 12 }}>
          <TextInput
            style={[s.plateInput, { color: colors.text, borderBottomColor: colors.faint, flex: 1 }]}
            value={newPlate} onChangeText={setNewPlate}
            keyboardType="decimal-pad" placeholder={`Add plate weight (${unitLabel})`}
            placeholderTextColor={colors.muted}
          />
          <TouchableOpacity
            style={[s.addPlateBtn, { backgroundColor: colors.accentSoft, borderColor: colors.accent }]}
            onPress={addPlate}>
            <Ionicons name="add" size={18} color={colors.accent} />
          </TouchableOpacity>
        </View>
      </View>

      <TouchableOpacity style={[s.saveBtn, { backgroundColor: colors.accent }]} onPress={save}>
        <Text style={s.saveBtnText}>SAVE PROFILE</Text>
      </TouchableOpacity>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  section: { padding: 16, borderWidth: 1, borderRadius: 14 },
  sectionTitle: { fontSize: 10, letterSpacing: 3, marginBottom: 12 },
  bigInput: { fontSize: 22, fontWeight: '700', borderBottomWidth: 2, paddingVertical: 8 },
  hint: { fontSize: 11, marginBottom: 12 },
  plateRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1 },
  plateWeight: { fontSize: 16, fontWeight: '700' },
  qBtn: { padding: 6, borderWidth: 1, borderRadius: 10 },
  qVal: { fontSize: 16, fontWeight: '700', minWidth: 22, textAlign: 'center' },
  plateInput: { fontSize: 16, borderBottomWidth: 1, paddingVertical: 8 },
  addPlateBtn: { padding: 10, borderWidth: 1, borderRadius: 10 },
  saveBtn: { padding: 18, alignItems: 'center', borderRadius: 10 },
  saveBtnText: { color: '#fff', fontWeight: '900', fontSize: 14, letterSpacing: 3 },
});


