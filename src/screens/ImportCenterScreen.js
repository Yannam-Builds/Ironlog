import React, { useContext, useState } from 'react';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import ImportPreviewModal from '../components/ImportPreviewModal';
import CustomAlert from '../components/CustomAlert';
import { pickAndParseCSV, importParsedCSV } from '../services/CSVImport';
import { pickAndParseOpenWeight, importParsedOpenWeight } from '../services/openweightInterop';
import { triggerHaptic } from '../services/hapticsEngine';

export default function ImportCenterScreen() {
  const colors = useTheme();
  const { settings, reloadFromStorage } = useContext(AppContext);
  const haptic = settings?.hapticFeedback !== false;
  const [parsedPayload, setParsedPayload] = useState(null);
  const [importing, setImporting] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);

  const runCSVPick = async () => {
    try {
      triggerHaptic('selection', { enabled: haptic }).catch(() => {});
      const parsed = await pickAndParseCSV();
      if (!parsed) return;
      setParsedPayload({ ...parsed, sourceLabel: parsed.format === 'hevy' ? 'Hevy CSV' : parsed.format === 'strong' ? 'Strong CSV' : 'IRONLOG CSV', parser: 'csv' });
    } catch (e) {
      setAlertConfig({ title: 'Import failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const runOpenWeightPick = async () => {
    try {
      triggerHaptic('selection', { enabled: haptic }).catch(() => {});
      const parsed = await pickAndParseOpenWeight();
      if (!parsed) return;
      setParsedPayload({ ...parsed, sourceLabel: 'OpenWeight JSON', parser: 'openweight' });
    } catch (e) {
      setAlertConfig({ title: 'Import failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const confirmImport = async () => {
    if (!parsedPayload) return;
    setImporting(true);
    try {
      const importedCount = parsedPayload.parser === 'openweight'
        ? await importParsedOpenWeight(parsedPayload)
        : await importParsedCSV(parsedPayload);
      await reloadFromStorage();
      setParsedPayload(null);
      triggerHaptic('restoreSucceeded', { enabled: haptic }).catch(() => {});
      setAlertConfig({
        title: 'Import complete',
        message: `Imported ${importedCount} workouts from ${parsedPayload.sourceLabel}.`,
        buttons: [{ text: 'OK', style: 'default' }],
      });
    } catch (e) {
      triggerHaptic('error', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'Import error', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setImporting(false);
    }
  };

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={s.content}>
      <Text style={[s.header, { color: colors.text }]}>IMPORT CENTER</Text>
      <Text style={[s.sub, { color: colors.subtext }]}>
        Import training history with a dry-run preview before writing data.
      </Text>

      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.text }]}>CSV Sources</Text>
        <Text style={[s.cardBody, { color: colors.subtext }]}>
          Supports Strong and Hevy exports with automatic format detection.
        </Text>
        <TouchableOpacity style={[s.primaryBtn, { backgroundColor: colors.accent }]} onPress={runCSVPick}>
          <Text style={s.primaryText}>IMPORT STRONG / HEVY CSV</Text>
        </TouchableOpacity>
      </View>

      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.text }]}>OpenWeight</Text>
        <Text style={[s.cardBody, { color: colors.subtext }]}>
          Import OpenWeight workout logs with deterministic matching and custom-exercise fallback.
        </Text>
        <TouchableOpacity style={[s.secondaryBtn, { borderColor: colors.accent }]} onPress={runOpenWeightPick}>
          <Text style={[s.secondaryText, { color: colors.accent }]}>IMPORT OPENWEIGHT JSON</Text>
        </TouchableOpacity>
      </View>

      <View style={[s.noteCard, { borderColor: colors.faint, backgroundColor: colors.card }]}>
        <Ionicons name="information-circle-outline" size={16} color={colors.muted} />
        <Text style={[s.noteText, { color: colors.muted }]}>
          Existing sessions with the same date + workout name are skipped to avoid duplicates.
        </Text>
      </View>

      <ImportPreviewModal
        visible={!!parsedPayload}
        preview={parsedPayload?.preview}
        loading={importing}
        onConfirm={confirmImport}
        onCancel={() => setParsedPayload(null)}
      />
      <CustomAlert
        visible={!!alertConfig}
        title={alertConfig?.title}
        message={alertConfig?.message}
        buttons={alertConfig?.buttons || []}
        onDismiss={() => setAlertConfig(null)}
      />
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20, gap: 14, paddingBottom: 40 },
  header: { fontSize: 24, fontWeight: '900', letterSpacing: 1.3, marginTop: 12 },
  sub: { fontSize: 13, lineHeight: 19 },
  card: { borderWidth: 1, padding: 16, gap: 8 },
  cardTitle: { fontSize: 15, fontWeight: '800', letterSpacing: 0.6 },
  cardBody: { fontSize: 12, lineHeight: 18 },
  primaryBtn: { marginTop: 6, paddingVertical: 14, alignItems: 'center' },
  primaryText: { color: '#fff', fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  secondaryBtn: { marginTop: 6, paddingVertical: 14, alignItems: 'center', borderWidth: 1 },
  secondaryText: { fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  noteCard: { borderWidth: 1, padding: 12, flexDirection: 'row', gap: 10, alignItems: 'flex-start' },
  noteText: { flex: 1, fontSize: 11, lineHeight: 16 },
});
