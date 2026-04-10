import React, { useContext, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import { importBackup } from '../utils/backup';
import {
  importSQLiteBundle,
  pickSQLiteBundleFile,
  validateSQLiteBundle,
} from '../services/sqliteExportImport';
import CustomAlert from '../components/CustomAlert';

export default function RestoreDataScreen({ navigation }) {
  const colors = useTheme();
  const { restoreData, reloadFromStorage, completeOnboarding } = useContext(AppContext);
  const [busy, setBusy] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);

  const finishRestore = async (message) => {
    await reloadFromStorage();
    await completeOnboarding();
    setAlertConfig({
      title: 'Restore complete',
      message,
      buttons: [{ text: 'Continue', style: 'default', onPress: () => navigation.reset({ index: 0, routes: [{ name: 'Tabs' }] }) }],
    });
  };

  const restoreFromEncryptedBackup = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const data = await importBackup();
      if (!data) return;
      await restoreData(data);
      await finishRestore('Encrypted backup imported successfully.');
    } catch (e) {
      setAlertConfig({ title: 'Restore failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  const restoreFromSQLiteExport = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const bundle = await pickSQLiteBundleFile();
      if (!bundle) return;
      const validation = validateSQLiteBundle(bundle);
      if (!validation.valid) {
        setAlertConfig({ title: 'Invalid export', message: validation.reason || 'Unsupported file format.', buttons: [{ text: 'OK', style: 'default' }] });
        return;
      }
      await importSQLiteBundle(bundle);
      await finishRestore(`Imported ${validation.counts?.history || 0} workouts from SQLite export.`);
    } catch (e) {
      setAlertConfig({ title: 'Restore failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={s.content}>
      <Text style={[s.header, { color: colors.text }]}>RESTORE YOUR DATA</Text>
      <Text style={[s.sub, { color: colors.subtext }]}>
        If you reinstalled the app, import your backup to recover workouts, plans, bodyweight, and intelligence history.
      </Text>

      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.text }]}>Encrypted backup (recommended)</Text>
        <Text style={[s.cardBody, { color: colors.subtext }]}>Import your encrypted IRONLOG backup file.</Text>
        <TouchableOpacity
          disabled={busy}
          style={[s.primaryBtn, { backgroundColor: colors.accent, opacity: busy ? 0.6 : 1 }]}
          onPress={restoreFromEncryptedBackup}
        >
          <Text style={s.primaryText}>{busy ? 'WORKING...' : 'IMPORT ENCRYPTED BACKUP'}</Text>
        </TouchableOpacity>
      </View>

      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.text }]}>SQLite export</Text>
        <Text style={[s.cardBody, { color: colors.subtext }]}>Import a full SQLite export bundle (`IRONLOG_SQLITE_EXPORT_V1`).</Text>
        <TouchableOpacity
          disabled={busy}
          style={[s.secondaryBtn, { borderColor: colors.accent, opacity: busy ? 0.6 : 1 }]}
          onPress={restoreFromSQLiteExport}
        >
          <Text style={[s.secondaryText, { color: colors.accent }]}>{busy ? 'WORKING...' : 'IMPORT SQLITE EXPORT'}</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={[s.skipBtn, { borderColor: colors.faint }]} onPress={() => navigation.goBack()}>
        <Text style={[s.skipText, { color: colors.muted }]}>SKIP FOR NOW</Text>
      </TouchableOpacity>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
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
  skipBtn: { marginTop: 4, borderWidth: 1, paddingVertical: 12, alignItems: 'center' },
  skipText: { fontSize: 11, fontWeight: '700', letterSpacing: 1.2 },
});
