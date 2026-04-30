import React, { useEffect, useMemo, useState } from 'react';
import {
  Modal,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import useWatermelonAppData from '../hooks/useWatermelonAppData';
import { useTheme } from '../context/ThemeContext';
import CustomAlert from '../components/CustomAlert';
import { fireHaptic } from '../services/hapticsEngine';
import { showErrorToast, showInfoToast, showSuccessToast } from '../services/toastService';
import {
  BACKUP_RESTOREABLE_DOMAINS,
  buildRestorePreview,
  exportPreviewAndShareLatest,
  fetchRemoteSnapshot,
  getCurrentBackupPreview,
  importBackupWithPreview,
  readSnapshotContainer,
  refreshBackupHistory,
  restoreBackupContainer,
  validateLatestBackup,
} from '../services/backupService';
import { buildRestoreSummary } from '../services/restoreResultModel';
import {
  schedulePeriodicBackupAt,
  cancelPeriodicBackup,
  checkBatteryOptimizationIgnored,
  requestBatteryOptimizationExemption,
} from '../services/BackupScheduler';
import { getBottomOverlaySpacing } from '../utils/bottomOverlaySpacing';
import { withAlpha } from '../utils/colorUtils';

function formatWhen(value) {
  if (!value) return '--';
  try {
    return new Date(value).toLocaleString('en-GB', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch (_) {
    return '--';
  }
}

function pad2(n) {
  return String(n).padStart(2, '0');
}

function StatusCard({ label, value, hint, colors }) {
  return (
    <View style={[s.statusCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
      <Text style={[s.statusLabel, { color: colors.muted }]}>{label}</Text>
      <Text style={[s.statusValue, { color: colors.text }]}>{value}</Text>
      {!!hint && <Text style={[s.statusHint, { color: colors.subtext }]}>{hint}</Text>}
    </View>
  );
}

function ActionButton({ label, hint, icon, colors, onPress, tone = 'normal', disabled = false }) {
  const accent = tone === 'danger' ? '#CC3333' : colors.accent;
  return (
    <TouchableOpacity
      activeOpacity={0.85}
      style={[s.actionBtn, { backgroundColor: colors.card, borderColor: colors.cardBorder, opacity: disabled ? 0.55 : 1 }]}
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
    >
      <View style={[s.iconWrap, { backgroundColor: withAlpha(accent, 0.13), borderColor: withAlpha(accent, 0.27) }]}>
        <Ionicons name={icon} size={18} color={accent} />
      </View>
      <View style={{ flex: 1 }}>
        <Text style={[s.actionTitle, { color: colors.text }]}>{label}</Text>
        {!!hint && <Text style={[s.actionHint, { color: colors.subtext }]}>{hint}</Text>}
      </View>
      <Ionicons name="chevron-forward" size={16} color={colors.muted} />
    </TouchableOpacity>
  );
}

function Section({ title, colors, children }) {
  return (
    <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
      <Text style={[s.sectionTitle, { color: colors.muted }]}>{title}</Text>
      {children}
    </View>
  );
}

function RecordCountsList({ counts, colors }) {
  return (
    <View style={s.countGrid}>
      {Object.entries(counts || {})
        .filter(([key]) => key !== 'total')
        .map(([key, value]) => (
          <View key={key} style={[s.countCard, { borderColor: colors.faint }]}>
            <Text style={[s.countValue, { color: colors.text }]}>{value}</Text>
            <Text style={[s.countLabel, { color: colors.subtext }]}>{key}</Text>
          </View>
        ))}
    </View>
  );
}

export default function BackupCenterScreen({ navigation }) {
  const {
    backupConfig,
    backupStatus,
    refreshBackupState,
    reloadFromStorage,
    runManualBackup,
    setupBackupPassphrase,
    updateBackupPreferences,
    settings,
  } = useWatermelonAppData();
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const haptic = settings?.hapticFeedback !== false;
  const bottomSpacing = getBottomOverlaySpacing({
    safeAreaBottom: insets.bottom,
    includeWorkoutPill: true,
    extra: 10,
  });

  const [history, setHistory] = useState([]);
  const [preview, setPreview] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);
  const [passphraseModal, setPassphraseModal] = useState({ visible: false, mode: null, target: null });
  const [passphrase, setPassphrase] = useState('');
  const [confirmPassphrase, setConfirmPassphrase] = useState('');
  const [restoreCandidate, setRestoreCandidate] = useState(null);

  // Daily schedule state — loaded from backupConfig preferences
  const [dailyEnabled, setDailyEnabled] = useState(Boolean(backupConfig.scheduledBackupEnabled));
  const [dailyHour, setDailyHour] = useState(Number(backupConfig.scheduledBackupHour ?? 2));
  const [dailyMinute, setDailyMinute] = useState(Number(backupConfig.scheduledBackupMinute ?? 0));
  const [retentionCount, setRetentionCount] = useState(Number(backupConfig.localRetentionCount ?? 4));
  const [batteryOptIgnored, setBatteryOptIgnored] = useState(true);

  useEffect(() => {
    setDailyEnabled(Boolean(backupConfig.scheduledBackupEnabled));
    setDailyHour(Number(backupConfig.scheduledBackupHour ?? 2));
    setDailyMinute(Number(backupConfig.scheduledBackupMinute ?? 0));
    setRetentionCount(Number(backupConfig.localRetentionCount ?? 4));
  }, [
    backupConfig.scheduledBackupEnabled,
    backupConfig.scheduledBackupHour,
    backupConfig.scheduledBackupMinute,
    backupConfig.localRetentionCount,
  ]);

  useEffect(() => {
    checkBatteryOptimizationIgnored().then(setBatteryOptIgnored).catch(() => setBatteryOptIgnored(true));
  }, []);

  const refreshScreen = async () => {
    setLoadingPreview(true);
    try {
      const [nextPreview, nextHistory] = await Promise.all([
        getCurrentBackupPreview(),
        refreshBackupHistory(),
      ]);
      setPreview(nextPreview);
      setHistory(nextHistory);
      await refreshBackupState();
    } catch (error) {
      setAlertConfig({
        title: 'Backup center error',
        message: String(error?.message || error),
        buttons: [{ text: 'OK', style: 'default' }],
      });
    } finally {
      setLoadingPreview(false);
    }
  };

  useEffect(() => {
    refreshScreen();
  }, []);

  const latestRegularBackup = useMemo(
    () => history.find((record) => !record.isRollback),
    [history]
  );
  const backupHealth = useMemo(() => {
    if (backupStatus.lastFailure) return { label: 'Needs Attention', hint: backupStatus.lastFailure };
    if (!backupConfig.passphraseConfigured) return { label: 'Not Protected', hint: 'Set backup passphrase to enable encrypted snapshots.' };
    if (!latestRegularBackup) return { label: 'No Snapshot Yet', hint: 'Run your first encrypted backup.' };
    return { label: 'Protected', hint: 'Encrypted local snapshots are active.' };
  }, [backupConfig.passphraseConfigured, backupStatus.lastFailure, latestRegularBackup]);

  const openPassphraseModal = (mode, target = null) => {
    setPassphrase('');
    setConfirmPassphrase('');
    setPassphraseModal({ visible: true, mode, target });
  };

  const closePassphraseModal = () => {
    setPassphrase('');
    setConfirmPassphrase('');
    setPassphraseModal({ visible: false, mode: null, target: null });
  };

  const ensurePassphraseConfigured = (nextMode) => {
    if (backupConfig.passphraseConfigured) return true;
    openPassphraseModal(nextMode || 'set');
    return false;
  };

  const handleConfigurePassphrase = async () => {
    if (!passphrase.trim() || passphrase.trim().length < 8) {
      setAlertConfig({ title: 'Passphrase too short', message: 'Use at least 8 characters for your backup passphrase.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    if (passphrase !== confirmPassphrase) {
      setAlertConfig({ title: 'Passphrases do not match', message: 'Please confirm the same passphrase twice.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    setBusy(true);
    try {
      await setupBackupPassphrase(passphrase);
      fireHaptic('success', { enabled: haptic });
      closePassphraseModal();
      await refreshScreen();
      setAlertConfig({ title: 'Passphrase saved', message: 'Encrypted backups are now enabled on this device.', buttons: [{ text: 'OK', style: 'default' }] });
    } catch (error) {
      setAlertConfig({ title: 'Could not save passphrase', message: String(error?.message || error), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  const handleManualBackup = async () => {
    if (!ensurePassphraseConfigured('set')) return;
    setBusy(true);
    try {
      await runManualBackup();
      fireHaptic('backupSucceeded', { enabled: haptic });
      await refreshScreen();
      showSuccessToast('Backup complete', 'Encrypted local snapshot saved.');
    } catch (error) {
      showErrorToast('Backup failed', String(error?.message || error));
      setAlertConfig({ title: 'Backup failed', message: String(error?.message || error), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  const handleShareExport = async () => {
    if (!ensurePassphraseConfigured('set')) return;
    setBusy(true);
    try {
      await exportPreviewAndShareLatest();
      fireHaptic('backupSucceeded', { enabled: haptic });
      showInfoToast('Export ready', 'Pick Google Drive, Files, or any app to save your backup.');
      await refreshScreen();
    } catch (error) {
      showErrorToast('Export failed', String(error?.message || error));
      setAlertConfig({ title: 'Export failed', message: String(error?.message || error), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  const handleValidateBackup = async () => {
    if (!ensurePassphraseConfigured('set')) return;
    openPassphraseModal('validate');
  };

  const handleImportBackup = async () => {
    openPassphraseModal('import');
  };

  const handleHistoryRestore = async (record) => {
    openPassphraseModal('history_restore', record);
  };

  const continueRestore = async (container, selectedPassphrase) => {
    setBusy(true);
    try {
      await restoreBackupContainer(container, {
        passphrase: selectedPassphrase,
        selectedDomains: BACKUP_RESTOREABLE_DOMAINS,
        createRollback: true,
      });
      fireHaptic('restoreSucceeded', { enabled: haptic, force: true });
      setRestoreCandidate(null);
      closePassphraseModal();
      await reloadFromStorage();
      await refreshScreen();
      const summary = buildRestoreSummary({
        sourceLabel: 'Encrypted backup',
        counts: {},
        partialNotes: ['Rollback snapshot created before restore.'],
      });
      showSuccessToast(summary.title, summary.message);
      setAlertConfig({ title: summary.title, message: summary.message, buttons: [{ text: 'OK', style: 'default' }] });
    } catch (error) {
      showErrorToast('Restore failed', String(error?.message || error));
      setAlertConfig({ title: 'Restore failed', message: String(error?.message || error), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  const handlePassphraseSubmit = async () => {
    if (passphraseModal.mode === 'set') {
      await handleConfigurePassphrase();
      return;
    }

    setBusy(true);
    try {
      if (passphraseModal.mode === 'validate') {
        const validation = await validateLatestBackup(passphrase);
        closePassphraseModal();
        showInfoToast(
          validation.valid ? 'Backup validation passed' : 'Backup validation warning',
          validation.valid ? 'Latest snapshot checksum looks healthy.' : 'Checksum validation warning detected.',
        );
        setAlertConfig({
          title: validation.valid ? 'Backup looks healthy' : 'Backup validation warning',
          message: validation.preview?.checksumValid === false
            ? 'Checksum validation failed for the latest snapshot.'
            : `Latest snapshot: ${validation.preview?.snapshotId || latestRegularBackup?.snapshotId || '--'}`,
          buttons: [{ text: 'OK', style: 'default' }],
        });
      } else if (passphraseModal.mode === 'import') {
        const imported = await importBackupWithPreview(passphrase);
        if (!imported) {
          closePassphraseModal();
        } else {
          setRestoreCandidate({ ...imported, passphrase });
          closePassphraseModal();
        }
      } else if (passphraseModal.mode === 'history_restore') {
        const target = passphraseModal.target;
        const container = target?.remote && !target?.localUri
          ? await fetchRemoteSnapshot(target)
          : await readSnapshotContainer(target);
        const previewData = await buildRestorePreview(container, passphrase);
        setRestoreCandidate({ container, preview: previewData, record: target, passphrase });
        closePassphraseModal();
      }
    } catch (error) {
      showErrorToast('Passphrase failed', String(error?.message || error));
      setAlertConfig({ title: 'Passphrase failed', message: String(error?.message || error), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setBusy(false);
    }
  };

  // ── Daily schedule helpers ────────────────────────────────────────────────

  const promptBatteryOptIfNeeded = async () => {
    const ignored = await checkBatteryOptimizationIgnored().catch(() => true);
    setBatteryOptIgnored(ignored);
    if (!ignored) {
      setAlertConfig({
        title: 'Background backup may be blocked',
        message: 'Android battery optimizations can prevent background backups from running. Tap "Allow" to exempt IronlogDB.',
        buttons: [
          { text: 'Later', style: 'cancel' },
          { text: 'Allow', style: 'default', onPress: () => requestBatteryOptimizationExemption().catch(() => {}) },
        ],
      });
    }
  };

  const applyDailySchedule = async (enabled, hour, minute) => {
    try {
      await updateBackupPreferences({
        scheduledBackupEnabled: enabled,
        scheduledBackupHour: hour,
        scheduledBackupMinute: minute,
      });
      if (enabled) {
        await schedulePeriodicBackupAt(hour, minute);
        showSuccessToast('Daily backup scheduled', `Will run every day at ${pad2(hour)}:${pad2(minute)}.`);
        promptBatteryOptIfNeeded();
      } else {
        await cancelPeriodicBackup();
        showInfoToast('Daily backup off', 'No scheduled backup is running.');
      }
    } catch (error) {
      showErrorToast('Schedule failed', String(error?.message || error));
    }
  };

  const handleToggleDaily = async (value) => {
    setDailyEnabled(value);
    await applyDailySchedule(value, dailyHour, dailyMinute);
  };

  const handleHourChange = (delta) => {
    const next = (dailyHour + delta + 24) % 24;
    setDailyHour(next);
    if (dailyEnabled) applyDailySchedule(true, next, dailyMinute);
    else updateBackupPreferences({ scheduledBackupHour: next });
  };

  const handleMinuteChange = (delta) => {
    const next = (dailyMinute + delta + 60) % 60;
    setDailyMinute(next);
    if (dailyEnabled) applyDailySchedule(true, dailyHour, next);
    else updateBackupPreferences({ scheduledBackupMinute: next });
  };

  const handleRetentionChange = (delta) => {
    const next = Math.max(2, Math.min(20, retentionCount + delta));
    setRetentionCount(next);
    updateBackupPreferences({ localRetentionCount: next });
  };

  // ─────────────────────────────────────────────────────────────────────────

  return (
    <ScrollView
      style={[s.container, { backgroundColor: colors.bg }]}
      contentContainerStyle={{ padding: 16, paddingBottom: bottomSpacing, gap: 14 }}
      scrollIndicatorInsets={{ bottom: bottomSpacing }}>

      <Section title="TRUST STATUS" colors={colors}>
        <View style={s.statusGrid}>
          <StatusCard label="Last Backed Up" value={formatWhen(backupStatus.lastBackupAt)} hint={backupStatus.lastBackupResult || 'No local snapshot yet'} colors={colors} />
          <StatusCard label="Last Restored" value={formatWhen(backupStatus.lastRestoreAt)} hint={backupStatus.lastRestoreResult || 'No restore yet'} colors={colors} />
          <StatusCard label="Versions Kept" value={String(backupStatus.rollingVersionCount || 0)} hint="Latest snapshot + rolling history" colors={colors} />
          <StatusCard label="Backup Health" value={backupHealth.label} hint={backupHealth.hint} colors={colors} />
        </View>
        {!!backupStatus.lastFailure && (
          <View style={[s.warningBox, { borderColor: '#883333', backgroundColor: '#221010' }]}>
            <Text style={[s.warningTitle, { color: '#FF9A9A' }]}>Last issue</Text>
            <Text style={[s.warningText, { color: '#F0B9B9' }]}>{backupStatus.lastFailure}</Text>
          </View>
        )}
      </Section>

      <Section title="AUTO BACKUP" colors={colors}>
        <View style={[s.toggleRow, { borderBottomColor: colors.faint }]}>
          <View style={{ flex: 1 }}>
            <Text style={[s.toggleTitle, { color: colors.text }]}>Encrypted backups enabled</Text>
            <Text style={[s.toggleHint, { color: colors.subtext }]}>Uses your saved passphrase and local-first snapshots.</Text>
          </View>
          <Switch
            value={backupConfig.enabled}
            onValueChange={(value) => updateBackupPreferences({ enabled: value })}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={backupConfig.enabled ? colors.accent : colors.muted}
          />
        </View>
        <View style={[s.toggleRow, { borderBottomColor: colors.faint }]}>
          <View style={{ flex: 1 }}>
            <Text style={[s.toggleTitle, { color: colors.text }]}>On workout completion</Text>
            <Text style={[s.toggleHint, { color: colors.subtext }]}>Queues one encrypted backup after you finish a session.</Text>
          </View>
          <Switch
            value={backupConfig.autoBackupOnWorkoutCompletion}
            onValueChange={(value) => updateBackupPreferences({ autoBackupOnWorkoutCompletion: value })}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={backupConfig.autoBackupOnWorkoutCompletion ? colors.accent : colors.muted}
          />
        </View>
        <View style={[s.toggleRow, { borderBottomColor: colors.faint }]}>
          <View style={{ flex: 1 }}>
            <Text style={[s.toggleTitle, { color: colors.text }]}>On app background</Text>
            <Text style={[s.toggleHint, { color: colors.subtext }]}>Runs a backup shortly after you leave the app.</Text>
          </View>
          <Switch
            value={backupConfig.autoBackupOnBackground}
            onValueChange={(value) => updateBackupPreferences({ autoBackupOnBackground: value })}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={backupConfig.autoBackupOnBackground ? colors.accent : colors.muted}
          />
        </View>
        <View style={s.toggleRow}>
          <View style={{ flex: 1 }}>
            <Text style={[s.toggleTitle, { color: colors.text }]}>Daily scheduled backup</Text>
            <Text style={[s.toggleHint, { color: colors.subtext }]}>
              {dailyEnabled
                ? `Runs every day at ${pad2(dailyHour)}:${pad2(dailyMinute)} in the background.`
                : 'Pick a time and enable to back up automatically each day.'}
            </Text>
          </View>
          <Switch
            value={dailyEnabled}
            onValueChange={handleToggleDaily}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={dailyEnabled ? colors.accent : colors.muted}
          />
        </View>

        {/* Time picker — always visible so users can pre-set time before enabling */}
        <View style={[s.timePicker, { borderColor: colors.faint }]}>
          <Text style={[s.timeLabel, { color: colors.muted }]}>DAILY BACKUP TIME</Text>
          <View style={s.timeRow}>
            <View style={s.timeUnit}>
              <TouchableOpacity onPress={() => handleHourChange(1)} style={[s.timeBtn, { borderColor: colors.faint }]}>
                <Ionicons name="chevron-up" size={18} color={colors.accent} />
              </TouchableOpacity>
              <Text style={[s.timeDigit, { color: colors.text }]}>{pad2(dailyHour)}</Text>
              <TouchableOpacity onPress={() => handleHourChange(-1)} style={[s.timeBtn, { borderColor: colors.faint }]}>
                <Ionicons name="chevron-down" size={18} color={colors.accent} />
              </TouchableOpacity>
            </View>
            <Text style={[s.timeSep, { color: colors.muted }]}>:</Text>
            <View style={s.timeUnit}>
              <TouchableOpacity onPress={() => handleMinuteChange(5)} style={[s.timeBtn, { borderColor: colors.faint }]}>
                <Ionicons name="chevron-up" size={18} color={colors.accent} />
              </TouchableOpacity>
              <Text style={[s.timeDigit, { color: colors.text }]}>{pad2(dailyMinute)}</Text>
              <TouchableOpacity onPress={() => handleMinuteChange(-5)} style={[s.timeBtn, { borderColor: colors.faint }]}>
                <Ionicons name="chevron-down" size={18} color={colors.accent} />
              </TouchableOpacity>
            </View>
            <Text style={[s.timeHint, { color: colors.subtext }]}>24h</Text>
          </View>
        </View>

        {/* Rolling retention control */}
        <View style={[s.timePicker, { borderColor: colors.faint }]}>
          <Text style={[s.timeLabel, { color: colors.muted }]}>LOCAL SNAPSHOTS TO KEEP</Text>
          <View style={[s.timeRow, { gap: 10 }]}>
            <TouchableOpacity onPress={() => handleRetentionChange(-1)} style={[s.timeBtn, { borderColor: colors.faint }]}>
              <Ionicons name="remove" size={18} color={colors.accent} />
            </TouchableOpacity>
            <Text style={[s.timeDigit, { color: colors.text }]}>{retentionCount}</Text>
            <TouchableOpacity onPress={() => handleRetentionChange(1)} style={[s.timeBtn, { borderColor: colors.faint }]}>
              <Ionicons name="add" size={18} color={colors.accent} />
            </TouchableOpacity>
            <Text style={[s.timeHint, { color: colors.subtext }]}>2 – 20 files</Text>
          </View>
        </View>

        {/* Battery optimization warning */}
        {!batteryOptIgnored && (
          <TouchableOpacity
            style={[s.warningBox, { borderColor: '#886020', backgroundColor: '#21190D' }]}
            onPress={() => requestBatteryOptimizationExemption().catch(() => {})}
          >
            <Text style={[s.warningTitle, { color: '#F8DDA0' }]}>Battery optimization active</Text>
            <Text style={[s.warningText, { color: '#F8DDA0' }]}>
              Android may delay or skip scheduled backups. Tap to exempt IronlogDB from battery optimization.
            </Text>
          </TouchableOpacity>
        )}
      </Section>

      <Section title="ENCRYPTION" colors={colors}>
        <ActionButton
          label={backupConfig.passphraseConfigured ? 'Change backup passphrase' : 'Set backup passphrase'}
          hint={backupConfig.passphraseConfigured ? 'Encrypted auto-backups are ready on this device.' : 'Required before encrypted backup can run.'}
          icon="lock-closed-outline"
          colors={colors}
          onPress={() => openPassphraseModal('set')}
        />
      </Section>

      <Section title="BACKUP ACTIONS" colors={colors}>
        <ActionButton
          label="Back Up Now"
          hint="Create a fresh encrypted snapshot immediately."
          icon="save-outline"
          colors={colors}
          onPress={handleManualBackup}
        />
        <ActionButton
          label="Export & Share"
          hint="Share encrypted backup to Google Drive, Files, or email."
          icon="share-social-outline"
          colors={colors}
          onPress={handleShareExport}
        />
        <ActionButton
          label="Validate Latest Backup"
          hint="Checksum-validate the newest snapshot before you need it."
          icon="shield-checkmark-outline"
          colors={colors}
          onPress={handleValidateBackup}
        />
        <ActionButton
          label="Privacy & Local-First"
          hint="See exactly what stays local and what backup uploads."
          icon="document-text-outline"
          colors={colors}
          onPress={() => navigation.navigate('Privacy')}
        />
      </Section>

      <Section title="RESTORE & DANGEROUS ACTIONS" colors={colors}>
        <ActionButton
          label="Import Backup File"
          hint="Pick encrypted snapshot, preview data, then confirm restore."
          icon="download-outline"
          colors={colors}
          onPress={handleImportBackup}
          tone="danger"
        />
        <View style={[s.warningBox, { borderColor: '#886020', backgroundColor: '#21190D' }]}>
          <Text style={[s.warningTitle, { color: '#F8DDA0' }]}>Restore safety</Text>
          <Text style={[s.warningText, { color: '#F8DDA0' }]}>
            Restore replaces selected local domains. A rollback snapshot is created automatically before restore.
          </Text>
        </View>
      </Section>

      <Section title="EXPORT PREVIEW" colors={colors}>
        {loadingPreview || !preview ? (
          <Text style={[s.previewEmpty, { color: colors.muted }]}>Preparing backup preview...</Text>
        ) : (
          <>
            <Text style={[s.previewText, { color: colors.subtext }]}>
              Structured data only. Progress photos stay local in this release.
            </Text>
            <RecordCountsList counts={preview.recordCounts} colors={colors} />
          </>
        )}
      </Section>

      <Section title="BACKUP HISTORY" colors={colors}>
        {!history.length ? (
          <Text style={[s.previewEmpty, { color: colors.muted }]}>No encrypted snapshots yet.</Text>
        ) : history.slice(0, 8).map((record) => (
          <View key={`${record.source}-${record.snapshotId}`} style={[s.historyRow, { borderBottomColor: colors.faint }]}>
            <View style={{ flex: 1 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <Text style={[s.historyTitle, { color: colors.text }]}>{record.isRollback ? 'ROLLBACK SNAPSHOT' : 'ENCRYPTED SNAPSHOT'}</Text>
                <View style={[s.badge, { borderColor: colors.faint }]}>
                  <Text style={[s.badgeText, { color: colors.muted }]}>{(record.source || 'local').toUpperCase()}</Text>
                </View>
              </View>
              <Text style={[s.historyMeta, { color: colors.subtext }]}>{formatWhen(record.createdAt)}</Text>
              <Text style={[s.historyMeta, { color: colors.muted }]} numberOfLines={1}>{record.snapshotId}</Text>
            </View>
            <TouchableOpacity style={[s.restoreBtn, { borderColor: colors.accent }]} onPress={() => handleHistoryRestore(record)}>
              <Text style={[s.restoreBtnText, { color: colors.accent }]}>RESTORE</Text>
            </TouchableOpacity>
          </View>
        ))}
      </Section>

      {/* ── Passphrase modal ────────────────────────────────────────────── */}
      <Modal visible={passphraseModal.visible} transparent animationType="fade" onRequestClose={closePassphraseModal}>
        <View style={s.overlay}>
          <View style={[s.modalCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[s.modalTitle, { color: colors.text }]}>
              {passphraseModal.mode === 'set' ? 'Set Backup Passphrase' : 'Enter Backup Passphrase'}
            </Text>
            <Text style={[s.modalHint, { color: colors.subtext }]}>
              {passphraseModal.mode === 'set'
                ? 'This passphrase protects your encrypted snapshots and is required on a new phone.'
                : 'Used to validate checksum and decrypt the selected snapshot.'}
            </Text>
            <TextInput
              style={[s.input, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
              value={passphrase}
              onChangeText={setPassphrase}
              secureTextEntry
              placeholder="Passphrase"
              placeholderTextColor={colors.muted}
            />
            {passphraseModal.mode === 'set' && (
              <TextInput
                style={[s.input, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
                value={confirmPassphrase}
                onChangeText={setConfirmPassphrase}
                secureTextEntry
                placeholder="Confirm passphrase"
                placeholderTextColor={colors.muted}
              />
            )}
            <View style={s.modalActions}>
              <TouchableOpacity style={[s.modalBtn, { borderColor: colors.faint }]} onPress={closePassphraseModal}>
                <Text style={[s.modalBtnText, { color: colors.muted }]}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[s.modalBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
                onPress={handlePassphraseSubmit}
                disabled={busy}
              >
                <Text style={[s.modalBtnText, { color: colors.accent }]}>{busy ? 'Working...' : 'Continue'}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* ── Restore preview modal ───────────────────────────────────────── */}
      <Modal visible={!!restoreCandidate} transparent animationType="fade" onRequestClose={() => setRestoreCandidate(null)}>
        <View style={s.overlay}>
          <View style={[s.modalCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[s.modalTitle, { color: colors.text }]}>Restore Preview</Text>
            <Text style={[s.modalHint, { color: colors.subtext }]}>
              {restoreCandidate?.preview?.checksumValid === false
                ? 'Checksum validation failed. Do not restore this backup.'
                : 'Review this snapshot before overwriting your current local data.'}
            </Text>
            <Text style={[s.previewLine, { color: colors.text }]}>Snapshot: {restoreCandidate?.preview?.snapshotId || restoreCandidate?.record?.snapshotId || '--'}</Text>
            <Text style={[s.previewLine, { color: colors.subtext }]}>Created: {formatWhen(restoreCandidate?.preview?.createdAt || restoreCandidate?.record?.createdAt)}</Text>
            <Text style={[s.previewLine, { color: colors.subtext }]}>Conflict: {restoreCandidate?.preview?.conflictSummary || '--'}</Text>
            <RecordCountsList counts={restoreCandidate?.preview?.recordCounts || {}} colors={colors} />
            {!!restoreCandidate?.preview?.warnings?.length && (
              <View style={[s.warningBox, { borderColor: '#886020', backgroundColor: '#21190D' }]}>
                {restoreCandidate.preview.warnings.map((warning) => (
                  <Text key={warning} style={[s.warningText, { color: '#F8DDA0' }]}>{warning}</Text>
                ))}
              </View>
            )}
            <View style={s.modalActions}>
              <TouchableOpacity style={[s.modalBtn, { borderColor: colors.faint }]} onPress={() => setRestoreCandidate(null)}>
                <Text style={[s.modalBtnText, { color: colors.muted }]}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[s.modalBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
                disabled={restoreCandidate?.preview?.checksumValid === false || busy}
                onPress={() => continueRestore(
                  restoreCandidate.container || restoreCandidate.file?.uri || restoreCandidate.record,
                  restoreCandidate.passphrase || passphrase,
                )}
              >
                <Text style={[s.modalBtnText, { color: colors.accent }]}>{busy ? 'Restoring...' : 'Restore Now'}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      <CustomAlert
        visible={!!alertConfig}
        title={alertConfig?.title}
        message={alertConfig?.message}
        buttons={alertConfig?.buttons || [{ text: 'OK', style: 'default' }]}
        onDismiss={() => setAlertConfig(null)}
      />
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  section: { borderWidth: 1, borderRadius: 14, padding: 16, gap: 12 },
  sectionTitle: { fontSize: 10, fontWeight: '800', letterSpacing: 2.5 },
  statusGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  statusCard: { width: '48%', borderWidth: 1, borderRadius: 14, padding: 12, minHeight: 90 },
  statusLabel: { fontSize: 9, letterSpacing: 2, marginBottom: 6 },
  statusValue: { fontSize: 16, fontWeight: '900', lineHeight: 20 },
  statusHint: { fontSize: 11, lineHeight: 16, marginTop: 4 },
  warningBox: { borderWidth: 1, borderRadius: 10, padding: 12, gap: 6 },
  warningTitle: { fontSize: 11, fontWeight: '800', letterSpacing: 1.2 },
  warningText: { fontSize: 12, lineHeight: 18 },
  toggleRow: { flexDirection: 'row', gap: 12, alignItems: 'center', paddingBottom: 12, borderBottomWidth: 1 },
  toggleTitle: { fontSize: 13, fontWeight: '700' },
  toggleHint: { fontSize: 11, lineHeight: 16, marginTop: 2 },
  timePicker: { borderWidth: 1, borderRadius: 12, padding: 14, gap: 10, marginTop: 4 },
  timeLabel: { fontSize: 9, letterSpacing: 2, fontWeight: '800' },
  timeRow: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  timeUnit: { alignItems: 'center', gap: 6 },
  timeBtn: { borderWidth: 1, borderRadius: 8, padding: 6 },
  timeDigit: { fontSize: 32, fontWeight: '900', lineHeight: 38, fontVariant: ['tabular-nums'] },
  timeSep: { fontSize: 28, fontWeight: '900', marginBottom: 4 },
  timeHint: { fontSize: 11, marginLeft: 4 },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 12, borderWidth: 1, borderRadius: 10, padding: 14 },
  iconWrap: { width: 36, height: 36, borderWidth: 1, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  actionTitle: { fontSize: 14, fontWeight: '800' },
  actionHint: { fontSize: 11, lineHeight: 16, marginTop: 2 },
  previewText: { fontSize: 12, lineHeight: 18 },
  previewEmpty: { fontSize: 12, lineHeight: 18 },
  countGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  countCard: { width: '31%', borderWidth: 1, borderRadius: 10, padding: 10, minHeight: 76 },
  countValue: { fontSize: 18, fontWeight: '900' },
  countLabel: { fontSize: 10, marginTop: 4, textTransform: 'capitalize' },
  historyRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12, borderBottomWidth: 1 },
  historyTitle: { fontSize: 12, fontWeight: '800', letterSpacing: 0.6 },
  historyMeta: { fontSize: 11, lineHeight: 16, marginTop: 3 },
  badge: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 6, paddingVertical: 2 },
  badgeText: { fontSize: 8, fontWeight: '700', letterSpacing: 1 },
  restoreBtn: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 10, paddingVertical: 8 },
  restoreBtnText: { fontSize: 10, fontWeight: '800', letterSpacing: 1.2 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.84)', justifyContent: 'center', padding: 24 },
  modalCard: { borderWidth: 1, borderRadius: 14, padding: 20, gap: 12 },
  modalTitle: { fontSize: 18, fontWeight: '900' },
  modalHint: { fontSize: 12, lineHeight: 18 },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 12, fontSize: 14 },
  modalActions: { flexDirection: 'row', gap: 10, marginTop: 4 },
  modalBtn: { flex: 1, borderWidth: 1, borderRadius: 10, paddingVertical: 12, alignItems: 'center' },
  modalBtnText: { fontSize: 12, fontWeight: '800', letterSpacing: 1 },
  previewLine: { fontSize: 12, lineHeight: 18 },
});
