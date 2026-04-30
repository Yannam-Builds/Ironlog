
import React, { useMemo, useState } from 'react';
import {
  View, Text, TouchableOpacity, ScrollView,
  StyleSheet,
} from 'react-native';
import { MagicScroll } from '@appandflow/react-native-magic-scroll';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import CustomAlert from '../components/CustomAlert';
import { useTheme } from '../context/ThemeContext';
import { saveCustomExercise } from '../services/ExerciseLibraryService';
import { MUSCLE_FILTER_OPTIONS } from '../utils/exerciseFilters';

const MUSCLES = MUSCLE_FILTER_OPTIONS.map((muscle) => muscle.toLowerCase());
const EQUIPMENT_OPTS = ['Barbell','Dumbbell','Cable','Machine','Bodyweight','Kettlebell','Band','Other'];
const CATEGORY_OPTS = ['strength','stretching','plyometrics','cardio','olympic weightlifting','powerlifting','other'];
const FORCE_OPTS = ['push','pull','static'];
const TRACKING_OPTS = ['weight_reps', 'duration', 'duration_distance'];

function inferTrackingType(category) {
  const key = String(category || '').toLowerCase();
  if (key.includes('cardio')) return 'duration_distance';
  if (key.includes('stretch')) return 'duration';
  return 'weight_reps';
}

function genId(name) {
  return name.replace(/[^a-zA-Z0-9]/g, '_') + '_custom_' + Date.now().toString(36);
}

export default function CreateExerciseScreen({ navigation, route }) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const existing = route?.params?.exercise;
  const quickAdd = !!route?.params?.quickAdd;
  const preferredPrimary = String(route?.params?.preferredPrimaryMuscle || '').trim().toLowerCase();
  const initialPrimary = useMemo(() => {
    if (Array.isArray(existing?.primaryMuscles) && existing.primaryMuscles.length) return existing.primaryMuscles;
    if (preferredPrimary && MUSCLES.includes(preferredPrimary)) return [preferredPrimary];
    return [];
  }, [existing?.primaryMuscles, preferredPrimary]);
  const [name, setName] = useState(existing?.name || route?.params?.initialName || '');
  const [primaryMuscles, setPrimary] = useState(initialPrimary);
  const [secondaryMuscles, setSecondary] = useState(existing?.secondaryMuscles || []);
  const [equipment, setEquipment] = useState(existing?.equipment || 'Barbell');
  const [category, setCategory] = useState(existing?.category || 'strength');
  const [trackingType, setTrackingType] = useState(existing?.trackingType || inferTrackingType(existing?.category || 'strength'));
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
      trackingType,
      images: [],
      isCustom: true,
      coachingCues: null,
      // Infer isBodyweight so the BW-only filter and active workout weight logic work correctly
      isBodyweight: equipment === 'Bodyweight',
    };
    try {
      await saveCustomExercise(ex);
      navigation.goBack();
    } catch (error) {
      if (error?.code === 'DUPLICATE_EXERCISE_NAME') {
        setAlertConfig({
          title: 'Exercise already exists',
          message: 'Use the existing exercise from the library instead of creating a duplicate.',
          buttons: [{ text: 'OK', style: 'default' }],
        });
        return;
      }
      setAlertConfig({
        title: 'Could not save exercise',
        message: String(error?.message || error || 'Unknown error'),
        buttons: [{ text: 'OK', style: 'default' }],
      });
    }
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
    <MagicScroll.ScrollView
      wrapperStyle={{ flex: 1, backgroundColor: colors.bg }}
      scrollViewProps={{
        keyboardShouldPersistTaps: 'handled',
        contentContainerStyle: { padding: 20, paddingBottom: Math.max(60, insets.bottom + 32) },
      }}
    >
      <View style={[s.noticeCard, { borderColor: colors.faint, backgroundColor: colors.card }]}>
        <Text style={[s.noticeTitle, { color: colors.text }]}>
          {existing ? 'EDIT CUSTOM EXERCISE' : 'ADD MISSING EXERCISE'}
        </Text>
        <Text style={[s.noticeText, { color: colors.muted }]}>
          Custom exercises are added to your library index automatically and included in backup/export restore data.
        </Text>
      </View>

      <View style={s.field}>
        <Text style={s.fieldLabel}>EXERCISE NAME *</Text>
        <MagicScroll.TextInput
          name="custom_exercise_name"
          chainTo="custom_exercise_notes"
          textInputProps={{
            style: [s.textInput, { color: colors.text, borderBottomColor: colors.accent }],
            value: name,
            onChangeText: setName,
            placeholder: 'e.g. Cable Pull-Through',
            placeholderTextColor: colors.muted,
            autoFocus: true,
            returnKeyType: 'next',
          }}
        />
      </View>

      <MuscleGrid label="PRIMARY MUSCLES *" selected={primaryMuscles} onToggle={m => toggleMuscle(primaryMuscles, setPrimary, m)} />
      <MuscleGrid label="SECONDARY MUSCLES" selected={secondaryMuscles} onToggle={m => toggleMuscle(secondaryMuscles, setSecondary, m)} />
      <OptionRow label="EQUIPMENT" opts={EQUIPMENT_OPTS} value={equipment} onSelect={setEquipment} />
      <OptionRow label="CATEGORY" opts={CATEGORY_OPTS} value={category} onSelect={(next) => {
        setCategory(next);
        if (!existing?.trackingType) {
          setTrackingType(inferTrackingType(next));
        }
      }} />
      <OptionRow label="TRACKING TYPE" opts={TRACKING_OPTS} value={trackingType} onSelect={setTrackingType} />
      <OptionRow label="FORCE" opts={FORCE_OPTS} value={force} onSelect={setForce} />

      <View style={s.field}>
        <Text style={s.fieldLabel}>INSTRUCTIONS (one per line)</Text>
        <MagicScroll.TextInput
          name="custom_exercise_notes"
          textInputProps={{
            style: [s.textArea, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.card }],
            value: instructions,
            onChangeText: setInstructions,
            placeholder: 'Step-by-step coaching cues...',
            placeholderTextColor: colors.muted,
            multiline: true,
            numberOfLines: 5,
            textAlignVertical: 'top',
          }}
        />
      </View>

      <TouchableOpacity style={[s.saveBtn, { backgroundColor: colors.accent }]} onPress={save}>
        <Text style={{ color: '#fff', fontWeight: '800', fontSize: 14, letterSpacing: 2 }}>
          {existing ? 'UPDATE EXERCISE' : quickAdd ? 'QUICK ADD EXERCISE' : 'CREATE EXERCISE'}
        </Text>
      </TouchableOpacity>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </MagicScroll.ScrollView>
  );
}

const makeStyles = colors => StyleSheet.create({
  noticeCard: { borderWidth: 1, borderRadius: 14, padding: 12, marginBottom: 16 },
  noticeTitle: { fontSize: 11, fontWeight: '800', letterSpacing: 1.1, marginBottom: 4 },
  noticeText: { fontSize: 11, lineHeight: 16 },
  field: { marginBottom: 20 },
  fieldLabel: { fontSize: 10, letterSpacing: 3, color: colors.muted, marginBottom: 8 },
  textInput: { fontSize: 22, fontWeight: '700', borderBottomWidth: 2, paddingVertical: 8 },
  textArea: { borderWidth: 1, padding: 12, fontSize: 13, minHeight: 100, borderRadius: 10 },
  pill: { paddingHorizontal: 12, paddingVertical: 7, borderWidth: 1, borderRadius: 999, borderColor: colors.faint },
  pillText: { fontSize: 12, fontWeight: '600' },
  saveBtn: { padding: 18, alignItems: 'center', marginTop: 8, borderRadius: 10 },
});
