import React, { useState } from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../context/ThemeContext';
import useWatermelonAppData from '../hooks/useWatermelonAppData';
import CustomAlert from '../components/CustomAlert';
import { fireHaptic } from '../services/hapticsEngine';
import { showErrorToast, showInfoToast, showSuccessToast } from '../services/toastService';
import { exportDatabase, importAnyPayload } from '../db/repositories/importExportRepository';
import * as FileSystem from '../platform/filesystem';
import * as Sharing from '../platform/sharing';
import * as DocumentPicker from '../platform/documentPicker';
import { withAlpha } from '../utils/colorUtils';
import { getBottomOverlaySpacing } from '../utils/bottomOverlaySpacing';

function ActionCard({ icon, title, hint, onPress, tone = 'normal', colors, disabled }) {
  const accent = tone === 'danger' ? '#CC3333' : colors.accent;
  return (
    <TouchableOpacity
      activeOpacity={0.85}
      style={[styles.card, { backgroundColor: colors.card, borderColor: colors.cardBorder, opacity: disabled ? 0.5 : 1 }]}
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
    >
      <View style={[styles.iconWrap, { backgroundColor: withAlpha(accent, 0.13), borderColor: withAlpha(accent, 0.27) }]}>
        <Ionicons name={icon} size={22} color={accent} />
      </View>
      <View style={{ flex: 1 }}>
        <Text style={[styles.cardTitle, { color: colors.text }]}>{title}</Text>
        <Text style={[styles.cardHint, { color: colors.subtext }]}>{hint}</Text>
      </View>
      <Ionicons name="chevron-forward" size={16} color={colors.muted} />
    </TouchableOpacity>
  );
}

export default function DataPortabilityScreen() {
  const { settings } = useWatermelonAppData();
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const haptic = settings?.hapticFeedback !== false;
  const bottomSpacing = getBottomOverlaySpacing({ safeAreaBottom: insets.bottom, includeWorkoutPill: true, extra: 10 });

  const [busy, setBusy] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);

  const handleExport = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const payload = await exportDatabase();
      const json = JSON.stringify(payload, null, 2);
      const date = new Date().toISOString().split('T')[0];
      const uri = FileSystem.documentDirectory + `ironlog_backup_${date}.json`;
      await FileSystem.writeAsStringAsync(uri, json, { encoding: 'utf8' });
      await Sharing.shareAsync(uri, { mimeType: 'application/json', dialogTitle: 'Save Ironlog backup' });
      fireHaptic('backupSucceeded', { enabled: haptic });
      showInfoToast('Export ready', 'Pick Google Drive, Files, or any app to save your backup.');
    } catch (error) {
      showErrorToast('Export failed', String(error?.message || error));
      setAlertConfig({ title: 'Export failed', message: String(error?.message || error), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  const handleImport = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const picked = await DocumentPicker.getDocumentAsync({ type: 'application/json', copyToCacheDirectory: true });
      if (picked.canceled) {
        setBusy(false);
        return;
      }
      const content = await FileSystem.readAsStringAsync(picked.assets[0].uri, { encoding: 'utf8' });
      const payload = JSON.parse(content);
      await importAnyPayload(payload);
      fireHaptic('restoreSucceeded', { enabled: haptic, force: true });
      showSuccessToast('Import complete', 'Your data has been restored from the backup file.');
      setAlertConfig({ title: 'Import complete', message: 'Your data has been restored. Restart the app if needed for all changes to appear.', buttons: [{ text: 'OK', style: 'default' }] });
    } catch (error) {
      showErrorToast('Import failed', String(error?.message || error));
      setAlertConfig({ title: 'Import failed', message: String(error?.message || error), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.bg }]}
      contentContainerStyle={{ padding: 16, paddingBottom: bottomSpacing, gap: 14 }}
      scrollIndicatorInsets={{ bottom: bottomSpacing }}
    >
      <View style={[styles.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[styles.sectionTitle, { color: colors.muted }]}>EXPORT YOUR DATA</Text>
        <Text style={[styles.body, { color: colors.subtext }]}>
          Exports all your workouts, plans, body measurements, settings, and exercise notes as a single JSON file. Share it to Google Drive, Files, or email for safekeeping.
        </Text>
        <ActionCard
          icon="share-social-outline"
          title={busy ? 'Exporting…' : 'Export Backup'}
          hint="Plain JSON — no encryption needed."
          onPress={handleExport}
          disabled={busy}
          colors={colors}
        />
      </View>

      <View style={[styles.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[styles.sectionTitle, { color: colors.muted }]}>IMPORT A BACKUP</Text>
        <Text style={[styles.body, { color: colors.subtext }]}>
          Pick a .json backup previously exported from Ironlog. Current Watermelon and supported legacy backup formats are accepted. Your existing data will be replaced.
        </Text>
        <ActionCard
          icon="download-outline"
          title={busy ? 'Importing…' : 'Import Backup File'}
          hint="Replaces your current data from a backup file."
          onPress={handleImport}
          tone="danger"
          disabled={busy}
          colors={colors}
        />
        <View style={[styles.warningBox, { borderColor: withAlpha('#FF9500', 0.36), backgroundColor: withAlpha('#FF9500', 0.11) }]}>
          <Text style={[styles.warningTitle, { color: '#FF9500' }]}>This replaces your current data</Text>
          <Text style={[styles.warningText, { color: colors.subtext }]}>
            Import overwrites your local database. Export a fresh backup first if you want to be safe.
          </Text>
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  section: { borderWidth: 1, borderRadius: 14, padding: 16, gap: 12 },
  sectionTitle: { fontSize: 10, fontWeight: '800', letterSpacing: 2.5 },
  body: { fontSize: 13, lineHeight: 20 },
  card: { flexDirection: 'row', alignItems: 'center', gap: 12, borderWidth: 1, borderRadius: 12, padding: 16 },
  iconWrap: { width: 40, height: 40, borderWidth: 1, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  cardTitle: { fontSize: 14, fontWeight: '800' },
  cardHint: { fontSize: 12, lineHeight: 17, marginTop: 2 },
  warningBox: { borderWidth: 1, borderRadius: 10, padding: 12, gap: 6 },
  warningTitle: { fontSize: 11, fontWeight: '800', letterSpacing: 1.2 },
  warningText: { fontSize: 12, lineHeight: 18 },
});
