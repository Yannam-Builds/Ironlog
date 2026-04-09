
import React, { useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, TextInput,
  StyleSheet,
} from 'react-native';
import CustomAlert from '../components/CustomAlert';
import { useTheme } from '../context/ThemeContext';
import { saveCustomExercise } from '../services/ExerciseLibraryService';

const MUSCLES = [
  'chest','back','shoulders','biceps','triceps','quadriceps','hamstrings',
  'glutes','calves','abdominals','obliques','lats','traps','forearms','other',
];
const EQUIPMENT_OPTS = ['Barbell','Dumbbell','Cable','Machine','Bodyweight','Kettlebell','Band','Other'];
const CATEGORY_OPTS = ['strength','stretching','plyometrics','cardio','olympic weightlifting','powerlifting','other'];
const FORCE_OPTS = ['push','pull','static'];

function genId(name) {
  return name.replace(/[^a-zA-Z0-9]/g, '_') + '_custom_' + Date.now().toString(36);
}

export default function CreateExerciseScreen({ navigation, route }) {
  const colors = useTheme();
  const existing = route.params?.exercise;
  const [name, setName] = useState(existing?.name || '');
  const [primaryMuscles, setPrimary] = useState(existing?.primaryMuscles || []);
  const [secondaryMuscles, setSecondary] = useState(existing?.secondaryMuscles || []);
  const [equipment, setEquipment] = useState(existing?.equipment || 'Barbell');
  const [category, setCategory] = useState(existing?.category || 'strength');
  const [force, setForce] = useState(existing?.force || 'push');
  const [instructions, setInstructions] = useState(
    existing?.instructions ? existing.instructions.join('\n') : ''
  );

  const toggleMuscle = (list, setList, muscle) => {
    setList(list.includes(muscle) ? list.filter(m => m !== muscle) : [...list, muscle]);
  };

  const [alertConfig, setAlertConfig] = useState(null);

  const save = async () => {
    if (!name.trim()) { setAlertConfig({ title: 'Name required', message: 'Please enter an exercise name.', buttons: [{ text: 'OK', style: 'default' }] }); return; }
    if (!primaryMuscles.length) { setAlertConfig({ title: 'Muscle required', message: 'Select at least one primary muscle.', buttons: [{ text: 'OK', style: 'default' }] }); return; }
    const ex = {
      id: existing?.id || genId(name.trim()),
      name: name.trim(),
      force,
      level: 'intermediate',
      mechanic: null,
      equipment,
      primaryMuscles,
      secondaryMuscles,
      instructions: instructions.trim() ? instructions.trim().split('\n').filter(Boolean) : [],
      category,
      images: [],
      isCustom: true,
      coachingCues: null,
    };
    await saveCustomExercise(ex);
    navigation.goBack();
  };

  const s = makeStyles(colors);

  const OptionRow = ({ label, opts, value, onSelect }) => (
    <View style={s.field}>
      <Text style={s.fieldLabel}>{label}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingVertical: 4 }}>
        {opts.map(opt => {
          const active = value === opt;
          return (
            <TouchableOpacity key={opt} style={[s.pill, active && { backgroundColor: colors.accentSoft, borderColor: colors.accent }]} onPress={() => onSelect(opt)}>
              <Text style={[s.pillText, { color: active ? colors.accent : colors.muted }]}>{opt}</Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );

  const MuscleGrid = ({ label, selected, onToggle }) => (
    <View style={s.field}>
      <Text style={s.fieldLabel}>{label}</Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {MUSCLES.map(m => {
          const active = selected.includes(m);
          return (
            <TouchableOpacity key={m} style={[s.pill, active && { backgroundColor: colors.accentSoft, borderColor: colors.accent }]} onPress={() => onToggle(m)}>
              <Text style={[s.pillText, { color: active ? colors.accent : colors.muted }]}>{m}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={{ padding: 20, paddingBottom: 60 }}>
      <View style={s.field}>
        <Text style={s.fieldLabel}>EXERCISE NAME *</Text>
        <TextInput style={[s.textInput, { color: colors.text, borderBottomColor: colors.accent }]}
          value={name} onChangeText={setName} placeholder="e.g. Cable Pull-Through"
          placeholderTextColor={colors.muted} autoFocus />
      </View>

      <MuscleGrid label="PRIMARY MUSCLES *" selected={primaryMuscles} onToggle={m => toggleMuscle(primaryMuscles, setPrimary, m)} />
      <MuscleGrid label="SECONDARY MUSCLES" selected={secondaryMuscles} onToggle={m => toggleMuscle(secondaryMuscles, setSecondary, m)} />
      <OptionRow label="EQUIPMENT" opts={EQUIPMENT_OPTS} value={equipment} onSelect={setEquipment} />
      <OptionRow label="CATEGORY" opts={CATEGORY_OPTS} value={category} onSelect={setCategory} />
      <OptionRow label="FORCE" opts={FORCE_OPTS} value={force} onSelect={setForce} />

      <View style={s.field}>
        <Text style={s.fieldLabel}>INSTRUCTIONS (one per line)</Text>
        <TextInput
          style={[s.textArea, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.card }]}
          value={instructions} onChangeText={setInstructions}
          placeholder="Step-by-step coaching cues..."
          placeholderTextColor={colors.muted}
          multiline numberOfLines={5} textAlignVertical="top" />
      </View>

      <TouchableOpacity style={[s.saveBtn, { backgroundColor: colors.accent }]} onPress={save}>
        <Text style={{ color: '#fff', fontWeight: '800', fontSize: 14, letterSpacing: 2 }}>
          {existing ? 'UPDATE EXERCISE' : 'CREATE EXERCISE'}
        </Text>
      </TouchableOpacity>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </ScrollView>
  );
}

const makeStyles = colors => StyleSheet.create({
  field: { marginBottom: 20 },
  fieldLabel: { fontSize: 10, letterSpacing: 3, color: colors.muted, marginBottom: 8 },
  textInput: { fontSize: 22, fontWeight: '700', borderBottomWidth: 2, paddingVertical: 8 },
  textArea: { borderWidth: 1, padding: 12, fontSize: 13, minHeight: 100, borderRadius: 2 },
  pill: { paddingHorizontal: 12, paddingVertical: 7, borderWidth: 1, borderColor: colors.faint },
  pillText: { fontSize: 12, fontWeight: '600' },
  saveBtn: { padding: 18, alignItems: 'center', marginTop: 8 },
});
