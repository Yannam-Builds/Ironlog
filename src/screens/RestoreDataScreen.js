import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import * as DocumentPicker from '../platform/documentPicker';
import * as FileSystem from '../platform/filesystem';
import useWatermelonAppData from '../hooks/useWatermelonAppData';
import { useTheme } from '../context/ThemeContext';
import { importBackup } from '../utils/backup';
import {
  importSQLiteBundle,
  pickSQLiteBundleFile,
  validateSQLiteBundle,
} from '../services/sqliteExportImport';
import { normalizeBackupImport } from '../services/backupImportNormalizer';
import { buildRestoreSummary } from '../services/restoreResultModel';
import CustomAlert from '../components/CustomAlert';

export default function RestoreDataScreen({ navigation }) {
  const colors = useTheme();
  const { restoreData, reloadFromStorage, completeOnboarding } = useWatermelonAppData();
  const [busy, setBusy] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);

  const finishRestore = async (messageOrSummary) => {
    const message = typeof messageOrSummary === 'string'
      ? messageOrSummary
      : messageOrSummary?.message || 'Restore finished.';
    await reloadFromStorage();
    await completeOnboarding();
    setAlertConfig({
      title: messageOrSummary?.title || 'Restore complete',
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
      if (data?.manifest && data?.encryptedPayload) {
        setAlertConfig({
          title: 'Encrypted container detected',
          message: 'This is an encrypted snapshot container. Restore it from Backup Center with your passphrase.',
          buttons: [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Open Backup Center', style: 'default', onPress: () => navigation.navigate('BackupCenter') },
          ],
        });
        return;
      }
      const normalized = normalizeBackupImport(data);
      const result = await restoreData(normalized.payload);
      const summary = buildRestoreSummary({
        sourceLabel: 'Backup file',
        counts: result,
        unsupportedFields: normalized.unsupportedFields,
      });
      await finishRestore(summary);
    } catch (e) {
      setAlertConfig({ title: 'Restore failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  const restoreFromUniversalJson = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const picked = await DocumentPicker.getDocumentAsync({
        type: ['application/json', 'text/plain', '*/*'],
        copyToCacheDirectory: true,
      });
      if (picked.canceled || !picked.assets?.[0]) return;
      const raw = await FileSystem.readAsStringAsync(picked.assets[0].uri, { encoding: 'utf8' });
      const parsed = JSON.parse(raw);
      const normalized = normalizeBackupImport(parsed);
      const result = await restoreData(normalized.payload);
      const summary = buildRestoreSummary({
        sourceLabel: `JSON (${normalized.format})`,
        counts: result,
        unsupportedFields: normalized.unsupportedFields,
      });
      await finishRestore(summary);
    } catch (e) {
      setAlertConfig({ title: 'Import failed', message: String(e?.message || e), buttons: [{ text: 'OK', style: 'default' }] });
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
      const imported = await importSQLiteBundle(bundle);
      const summary = buildRestoreSummary({
        sourceLabel: 'SQLite export',
        counts: imported?.counts || validation?.counts || {},
      });
      await finishRestore(summary);
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

      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.text }]}>Universal JSON import</Text>
        <Text style={[s.cardBody, { color: colors.subtext }]}>Import any IRONLOG backup JSON: plain backup, SQLite export, or old-format files.</Text>
        <TouchableOpacity
          disabled={busy}
          style={[s.primaryBtn, { backgroundColor: colors.accent, opacity: busy ? 0.6 : 1 }]}
          onPress={restoreFromUniversalJson}
        >
          <Text style={s.primaryText}>{busy ? 'WORKING...' : 'IMPORT BACKUP JSON'}</Text>
        </TouchableOpacity>
      </View>

      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.text }]}>Switching from another app?</Text>
        <Text style={[s.cardBody, { color: colors.subtext }]}>Open Import Center for Strong CSV, Hevy CSV, or OpenWeight JSON migration.</Text>
        <TouchableOpacity
          disabled={busy}
          style={[s.secondaryBtn, { borderColor: colors.accent, opacity: busy ? 0.6 : 1 }]}
          onPress={() => navigation.navigate('ImportCenter')}
        >
          <Text style={[s.secondaryText, { color: colors.accent }]}>OPEN IMPORT CENTER</Text>
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
  card: { borderWidth: 1, borderRadius: 14, padding: 16, gap: 8 },
  cardTitle: { fontSize: 15, fontWeight: '800', letterSpacing: 0.6 },
  cardBody: { fontSize: 12, lineHeight: 18 },
  primaryBtn: { marginTop: 6, paddingVertical: 14, alignItems: 'center', borderRadius: 10 },
  primaryText: { color: '#fff', fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  secondaryBtn: { marginTop: 6, paddingVertical: 14, alignItems: 'center', borderWidth: 1, borderRadius: 10 },
  secondaryText: { fontSize: 12, fontWeight: '900', letterSpacing: 1.5 },
  skipBtn: { marginTop: 4, borderWidth: 1, borderRadius: 10, paddingVertical: 12, alignItems: 'center' },
  skipText: { fontSize: 11, fontWeight: '700', letterSpacing: 1.2 },
});
