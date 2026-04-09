import React, { useContext, useState } from 'react';
import { View, Text, StyleSheet, TextInput, Modal, Alert, TouchableOpacity } from 'react-native';
import { TouchableOpacity as RNGHTouchableOpacity } from 'react-native-gesture-handler';
import CustomAlert from '../components/CustomAlert';
import DraggableFlatList, { ScaleDecorator } from 'react-native-draggable-flatlist';
import { Ionicons } from '@expo/vector-icons';
import * as Sharing from 'expo-sharing';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import { EXERCISES } from '../data/exerciseLibrary';
import { triggerHaptic } from '../services/hapticsEngine';

function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }

// Fuzzy-match an exercise name to the library
function matchExercise(name) {
  if (!name) return null;
  const lower = name.toLowerCase();
  // exact match first
  const exact = EXERCISES.find(e => e.name.toLowerCase() === lower);
  if (exact) return exact.id;
  // partial match
  const partial = EXERCISES.find(e => e.name.toLowerCase().includes(lower) || lower.includes(e.name.toLowerCase()));
  if (partial) return partial.id;
  return null;
}

export default function PlansScreen({ navigation }) {
  const { plans, savePlans, settings } = useContext(AppContext);
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;

  const [showNew, setShowNew] = useState(false);
  const [newName, setNewName] = useState('');
  const [showRename, setShowRename] = useState(false);
  const [renamePlanId, setRenamePlanId] = useState(null);
  const [renameVal, setRenameVal] = useState('');
  const [alertConfig, setAlertConfig] = useState(null);

  const createPlan = () => {
    if (!newName.trim()) return;
    savePlans([...plans, { id: genId(), name: newName.trim(), days: [] }]);
    setNewName(''); setShowNew(false);
  };

  const openRename = (plan) => {
    setRenamePlanId(plan.id);
    setRenameVal(plan.name);
    setShowRename(true);
  };

  const doRename = () => {
    if (!renameVal.trim()) return;
    savePlans(plans.map(p => p.id === renamePlanId ? { ...p, name: renameVal.trim() } : p));
    setShowRename(false);
    setRenamePlanId(null);
  };

  const deletePlan = (id) => {
    triggerHaptic('destructiveAction', { enabled: haptic }).catch(() => {});
    setAlertConfig({
      title: 'Delete plan?',
      message: '',
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: () => savePlans(plans.filter(p => p.id !== id)) },
      ],
    });
  };

  // ── Share / Export ──────────────────────────────────────────────
  const sharePlan = async (plan) => {
    try {
      const payload = {
        version: 1,
        type: 'ironlog_plan',
        exportedAt: new Date().toISOString(),
        plan: {
          name: plan.name,
          days: (plan.days || []).map(day => ({
            name: day.name,
            color: day.color,
            exercises: (day.exercises || []).map(ex => ({
              exerciseId: ex.exerciseId || ex.id || null,
              exerciseName: ex.name || ex.exerciseName || '',
              sets: ex.sets ?? 3,
              reps: ex.reps ?? '10',
              restSeconds: ex.restSeconds ?? 90,
              supersetGroup: ex.supersetGroup ?? null,
              isWarmup: ex.isWarmup ?? false,
              notes: ex.notes ?? '',
            })),
          })),
        },
      };

      const fileName = `${plan.name.replace(/[^a-zA-Z0-9]/g, '_')}_ironlog.json`;
      const filePath = FileSystem.cacheDirectory + fileName;
      await FileSystem.writeAsStringAsync(filePath, JSON.stringify(payload, null, 2));

      const canShare = await Sharing.isAvailableAsync();
      if (canShare) {
        await Sharing.shareAsync(filePath, { mimeType: 'application/json', dialogTitle: `Share "${plan.name}"` });
      } else {
        setAlertConfig({ title: 'Sharing unavailable', message: 'This device does not support file sharing.', buttons: [{ text: 'OK', style: 'cancel' }] });
      }
    } catch (e) {
      console.warn('Share error:', e);
      setAlertConfig({ title: 'Export failed', message: e.message || 'Could not export plan.', buttons: [{ text: 'OK', style: 'cancel' }] });
    }
  };

  // ── Import ──────────────────────────────────────────────────────
  const importPlan = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({ type: 'application/json', copyToCacheDirectory: true });
      if (result.canceled || !result.assets?.length) return;

      const uri = result.assets[0].uri;
      const raw = await FileSystem.readAsStringAsync(uri);
      const data = JSON.parse(raw);

      // Validate
      if (data.type !== 'ironlog_plan' || !data.plan || !data.plan.name) {
        setAlertConfig({ title: 'Invalid file', message: 'This file is not a valid IronLog plan.', buttons: [{ text: 'OK', style: 'cancel' }] });
        return;
      }

      const imported = data.plan;
      // Rebuild days with fresh IDs and fuzzy-matched exercises
      const days = (imported.days || []).map(day => ({
        id: genId(),
        name: day.name || 'Day',
        color: day.color || '#E53935',
        exercises: (day.exercises || []).map(ex => {
          const matched = matchExercise(ex.exerciseName);
          return {
            id: genId(),
            exerciseId: matched || ex.exerciseId || null,
            name: ex.exerciseName || '',
            sets: ex.sets ?? 3,
            reps: ex.reps ?? '10',
            restSeconds: ex.restSeconds ?? 90,
            supersetGroup: ex.supersetGroup ?? null,
            isWarmup: ex.isWarmup ?? false,
            notes: ex.notes ?? '',
          };
        }),
      }));

      // Name collision: append " (Imported)"
      let importedName = imported.name;
      if (plans.some(p => p.name === importedName)) importedName += ' (Imported)';

      const newPlan = { id: genId(), name: importedName, days };
      savePlans([...plans, newPlan]);

      setAlertConfig({
        title: 'Plan imported!',
        message: `"${importedName}" has been added to your plans.`,
        buttons: [{ text: 'OK', style: 'default' }],
      });
    } catch (e) {
      console.warn('Import error:', e);
      setAlertConfig({ title: 'Import failed', message: 'Could not read the selected file.', buttons: [{ text: 'OK', style: 'cancel' }] });
    }
  };

  const onLongPress = (plan) => {
    triggerHaptic('selection', { enabled: haptic }).catch(() => {});
    setAlertConfig({
      title: plan.name,
      message: '',
      buttons: [
        { text: 'Rename', style: 'default', onPress: () => openRename(plan) },
        { text: 'Share Plan', style: 'default', onPress: () => sharePlan(plan) },
        { text: 'Delete', style: 'destructive', onPress: () => deletePlan(plan.id) },
        { text: 'Cancel', style: 'cancel' },
      ],
    });
  };

  const renderPlan = ({ item: plan, drag, isActive }) => (
    <ScaleDecorator activeScale={0.97}>
      <View
        style={[
          s.card,
          { backgroundColor: colors.card, borderColor: isActive ? colors.accent : colors.cardBorder },
        ]}
      >
        <TouchableOpacity onPress={() => navigation.navigate('PlanEditor', { planId: plan.id })} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flex: 1 }}>
            <Text style={[s.planName, { color: colors.text }]}>{plan.name}</Text>
            <Text style={[s.planMeta, { color: colors.muted }]}>
              {plan.days.length} days · {plan.days.reduce((a, d) => a + (d.exercises || []).filter(e => !e.isWarmup).length, 0)} exercises
            </Text>
          </View>
          <View style={{ flexDirection: 'row', gap: 16, alignItems: 'center' }}>
            <TouchableOpacity onPress={() => onLongPress(plan)} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
              <Ionicons name="ellipsis-vertical" size={18} color={colors.muted} />
            </TouchableOpacity>
            <RNGHTouchableOpacity onLongPress={drag} delayLongPress={150} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
              <Ionicons name="reorder-three-outline" size={22} color={colors.faint} />
            </RNGHTouchableOpacity>
          </View>
        </TouchableOpacity>
        <View style={{ flexDirection: 'row', gap: 6, marginTop: 10, flexWrap: 'wrap' }}>
          {plan.days.map(d => (
            <View key={d.id} style={[s.dayChip, { backgroundColor: d.color + '22', borderColor: d.color + '44' }]}>
              <Text style={{ fontSize: 10, color: d.color, fontWeight: '700', letterSpacing: 1 }}>{d.name}</Text>
            </View>
          ))}
        </View>
      </View>
    </ScaleDecorator>
  );

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <DraggableFlatList
        data={plans}
        keyExtractor={item => item.id}
        renderItem={renderPlan}
        onDragEnd={({ data }) => {
          triggerHaptic('selection', { enabled: haptic }).catch(() => {});
          savePlans(data);
        }}
        contentContainerStyle={{ padding: 16, gap: 12, paddingBottom: 80 }}
        ListHeaderComponent={
          <View style={{ gap: 10, marginBottom: 4 }}>
            <TouchableOpacity
              style={[s.browseBtn, { borderColor: colors.faint, backgroundColor: colors.card }]}
              onPress={() => navigation.navigate('ProgramPicker')}>
              <Ionicons name="library-outline" size={18} color={colors.accent} />
              <Text style={[s.browseText, { color: colors.accent }]}>BROWSE PROGRAMS</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[s.browseBtn, { borderColor: colors.faint, backgroundColor: colors.card }]}
              onPress={importPlan}>
              <Ionicons name="download-outline" size={18} color={colors.accent} />
              <Text style={[s.browseText, { color: colors.accent }]}>IMPORT PLAN</Text>
            </TouchableOpacity>
          </View>
        }
        ListFooterComponent={
          <TouchableOpacity
            style={[s.addBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
            onPress={() => setShowNew(true)}>
            <Ionicons name="add" size={20} color={colors.accent} />
            <Text style={[s.addText, { color: colors.accent }]}>NEW PLAN</Text>
          </TouchableOpacity>
        }
      />

      {/* New Plan Modal */}
      <Modal visible={showNew} transparent animationType="fade" onRequestClose={() => setShowNew(false)}>
        <View style={s.overlay}>
          <View style={[s.modal, { backgroundColor: colors.surface, borderColor: colors.cardBorder }]}>
            <Text style={[s.modalTitle, { color: colors.text }]}>NEW PLAN</Text>
            <TextInput
              style={[s.input, { color: colors.text, borderBottomColor: colors.accent }]}
              value={newName} onChangeText={setNewName}
              placeholder="Plan name" placeholderTextColor={colors.muted} autoFocus />
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

      {/* Rename Modal */}
      <Modal visible={showRename} transparent animationType="fade" onRequestClose={() => setShowRename(false)}>
        <View style={s.overlay}>
          <View style={[s.modal, { backgroundColor: colors.surface, borderColor: colors.cardBorder }]}>
            <Text style={[s.modalTitle, { color: colors.text }]}>RENAME PLAN</Text>
            <TextInput
              style={[s.input, { color: colors.text, borderBottomColor: colors.accent }]}
              value={renameVal} onChangeText={setRenameVal}
              placeholder="Plan name" placeholderTextColor={colors.muted} autoFocus
              selectTextOnFocus />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 20 }}>
              <TouchableOpacity style={[s.cancelBtn, { borderColor: colors.faint }]} onPress={() => setShowRename(false)}>
                <Text style={{ color: colors.muted }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[s.confirmBtn, { backgroundColor: colors.accent }]} onPress={doRename}>
                <Text style={{ color: '#fff', fontWeight: '800' }}>SAVE</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  card: { padding: 16, borderWidth: 1 },
  planName: { fontSize: 18, fontWeight: '900' },
  planMeta: { fontSize: 12, marginTop: 2 },
  dayChip: { paddingHorizontal: 10, paddingVertical: 4, borderWidth: 1 },
  browseBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 14, borderWidth: 1 },
  browseText: { fontSize: 11, fontWeight: '800', letterSpacing: 2 },
  addBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 16, borderWidth: 1, borderStyle: 'dashed', marginTop: 4 },
  addText: { fontSize: 12, fontWeight: '700', letterSpacing: 2 },
  overlay: { flex: 1, backgroundColor: '#000000cc', justifyContent: 'center', padding: 24 },
  modal: { padding: 24, borderWidth: 1 },
  modalTitle: { fontSize: 14, fontWeight: '900', letterSpacing: 3, marginBottom: 16 },
  input: { fontSize: 22, fontWeight: '700', borderBottomWidth: 2, paddingVertical: 8, marginBottom: 8 },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1 },
  confirmBtn: { flex: 1, padding: 14, alignItems: 'center' },
});
