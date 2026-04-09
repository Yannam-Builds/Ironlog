
import React from 'react';
import { Modal, View, Text, TouchableOpacity, ScrollView, StyleSheet } from 'react-native';
import { useTheme } from '../context/ThemeContext';

export default function ImportPreviewModal({ visible, preview, onConfirm, onCancel, loading }) {
  const colors = useTheme();
  if (!preview) return null;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onCancel}>
      <View style={s.overlay}>
        <View style={[s.sheet, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
          <Text style={[s.title, { color: colors.text }]}>IMPORT PREVIEW</Text>

          <View style={s.statsRow}>
            {[
              { label: 'WORKOUTS', val: preview.workouts },
              { label: 'EXERCISES', val: preview.exercises },
              { label: 'SETS', val: preview.sets },
            ].map(({ label, val }) => (
              <View key={label} style={s.statBox}>
                <Text style={[s.statVal, { color: colors.accent }]}>{val}</Text>
                <Text style={[s.statLabel, { color: colors.muted }]}>{label}</Text>
              </View>
            ))}
          </View>

          {preview.fuzzyMatches.length > 0 && (
            <View style={[s.section, { borderTopColor: colors.faint }]}>
              <Text style={[s.sectionTitle, { color: colors.muted }]}>
                {preview.fuzzyMatches.length} AUTO-MAPPED (fuzzy)
              </Text>
              <ScrollView style={{ maxHeight: 80 }}>
                {preview.fuzzyMatches.map(name => (
                  <Text key={name} style={[s.item, { color: colors.text }]} numberOfLines={1}>• {name}</Text>
                ))}
              </ScrollView>
            </View>
          )}

          {preview.customExercises.length > 0 && (
            <View style={[s.section, { borderTopColor: colors.faint }]}>
              <Text style={[s.sectionTitle, { color: colors.muted }]}>
                {preview.customExercises.length} NEW CUSTOM EXERCISES
              </Text>
              <ScrollView style={{ maxHeight: 80 }}>
                {preview.customExercises.map(name => (
                  <Text key={name} style={[s.item, { color: colors.text }]} numberOfLines={1}>• {name}</Text>
                ))}
              </ScrollView>
            </View>
          )}

          <Text style={[s.note, { color: colors.muted }]}>
            Sessions matching an existing date + name will be skipped.
          </Text>

          <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
            <TouchableOpacity style={[s.cancelBtn, { borderColor: colors.faint }]} onPress={onCancel}>
              <Text style={{ color: colors.muted }}>Cancel</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[s.confirmBtn, { backgroundColor: loading ? colors.muted : colors.accent }]}
              onPress={onConfirm}
              disabled={loading}>
              <Text style={{ color: '#fff', fontWeight: '800' }}>
                {loading ? 'IMPORTING...' : `IMPORT ${preview.workouts} WORKOUTS`}
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const s = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: '#000000cc', justifyContent: 'flex-end' },
  sheet: { padding: 24, borderTopWidth: 1, borderTopLeftRadius: 16, borderTopRightRadius: 16 },
  title: { fontSize: 14, fontWeight: '900', letterSpacing: 3, marginBottom: 16 },
  statsRow: { flexDirection: 'row', marginBottom: 16 },
  statBox: { flex: 1, alignItems: 'center' },
  statVal: { fontSize: 26, fontWeight: '900' },
  statLabel: { fontSize: 8, letterSpacing: 2, marginTop: 2 },
  section: { borderTopWidth: 1, paddingTop: 10, marginBottom: 10 },
  sectionTitle: { fontSize: 9, letterSpacing: 2, marginBottom: 6 },
  item: { fontSize: 12, marginBottom: 3 },
  note: { fontSize: 11, fontStyle: 'italic' },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1 },
  confirmBtn: { flex: 2, padding: 14, alignItems: 'center' },
});
