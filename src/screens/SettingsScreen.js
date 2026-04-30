
import React, { useState, useEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, TextInput, Modal, Switch, Vibration } from 'react-native';
import CustomAlert from '../components/CustomAlert';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as FileSystem from '../platform/filesystem';
import useWatermelonAppData from '../hooks/useWatermelonAppData';
import { useTheme } from '../context/ThemeContext';
import GoldConfettiBurst from '../components/GoldConfettiBurst';
import { THEMES } from '../utils/themes';
import { exportBackup, importBackup } from '../utils/backup';
import { getCacheSize, clearCache, downloadAllImages } from '../services/ExerciseImageCache';
import { getExerciseIndex } from '../services/ExerciseLibraryService';
import { exportCSV } from '../services/CSVExport';
import { pickAndParseCSV, importParsedCSV } from '../services/CSVImport';
import {
  exportSQLiteBundleAndShare,
  importSQLiteBundle,
  pickSQLiteBundleFile,
  validateSQLiteBundle,
} from '../services/sqliteExportImport';
import { exportOpenWeightBundleAndShare } from '../services/openweightInterop';
import ImportPreviewModal from '../components/ImportPreviewModal';
import { setHapticsEnabled, fireHaptic } from '../services/hapticsEngine';
import { ensureNotificationPermissions, getNotificationPermissionStatus, sendTestNotification } from '../services/notificationScheduler';
import { convertUnitToKg, formatWeightFromKg } from '../utils/weightUnits';
import { resolveCelebrationConfig } from '../utils/celebration';
import { getBottomOverlaySpacing } from '../utils/bottomOverlaySpacing';
import { showErrorToast, showInfoToast, showSuccessToast } from '../services/toastService';
import { clearProgressPhotos, getProgressPhotos } from '../db/repositories/progressPhotoRepository';

const PHOTO_DIR = FileSystem.documentDirectory + 'progress-photos/';
const EFFORT_CYCLE = ['off', 'rpe', 'rir', 'both'];
const EFFORT_LABEL = { off: 'Off', rpe: 'RPE', rir: 'RIR', both: 'Both' };
const GOAL_MODES = [
  { id: 'hypertrophy', label: 'Hypertrophy' },
  { id: 'strength', label: 'Strength' },
  { id: 'general_fitness', label: 'General Fitness' },
];
const PROGRESSION_STYLES = [
  { id: 'conservative', label: 'Conservative' },
  { id: 'balanced', label: 'Balanced' },
  { id: 'aggressive', label: 'Aggressive' },
];
const NOTIFICATION_PROFILES = [
  { id: 'conservative', label: 'Conservative' },
  { id: 'balanced', label: 'Balanced' },
  { id: 'aggressive', label: 'Aggressive' },
];

function formatBytes(bytes) {
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function formatQuietTime(totalMinutes) {
  const minutes = Math.max(0, Math.min(1439, Math.round(Number(totalMinutes || 0))));
  const hoursPart = String(Math.floor(minutes / 60)).padStart(2, '0');
  const minutesPart = String(minutes % 60).padStart(2, '0');
  return `${hoursPart}:${minutesPart}`;
}

function parseQuietTimeInput(value) {
  const raw = String(value || '').trim();
  const match = raw.match(/^(\d{1,2})(?::?(\d{1,2}))?$/);
  if (!match) return null;
  const hours = Number(match[1]);
  const minutes = Number(match[2] || 0);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null;
  if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) return null;
  return hours * 60 + minutes;
}

function getQuietMinutes(settings = {}, key = 'quietHoursStartMinutes', fallbackHour = 22) {
  const direct = Number(settings?.[key]);
  if (Number.isFinite(direct)) return Math.max(0, Math.min(1439, Math.round(direct)));
  const fallbackKey = key === 'quietHoursStartMinutes' ? 'quietHoursStart' : 'quietHoursEnd';
  const hour = Number(settings?.[fallbackKey]);
  return Math.max(0, Math.min(1439, Math.round((Number.isFinite(hour) ? hour : fallbackHour) * 60)));
}

function formatDecisionLabel(entry = {}) {
  const topic = String(entry?.topic || entry?.key || 'system')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
  const suppressedReasonMap = {
    disabled_or_no_candidates: 'Nothing worth sending right now',
    snoozed: 'Notifications are snoozed',
    daily_cap: 'Daily cap reached',
    weekly_cap: 'Weekly cap reached',
    cooldown_or_topic_gate: 'Cooldown is active',
    already_actioned: 'Action already completed',
    permission_denied: 'Notification permission is off',
  };
  if (entry?.outcome === 'suppressed') {
    return `SUPPRESSED - ${suppressedReasonMap[entry?.reason] || topic}`;
  }
  if (entry?.outcome === 'sent') {
    return `SCHEDULED - ${topic}`;
  }
  return `${String(entry?.outcome || 'event').toUpperCase()} - ${topic}`;
}

function formatDecisionTime(entry = {}) {
  const stamp = entry?.outcome === 'sent' && entry?.scheduledFor ? entry.scheduledFor : entry?.at;
  return String(stamp || '').replace('T', ' ').slice(0, 16);
}

export default function SettingsScreen({ navigation }) {
  const {
    settings,
    updateSettings,
    getAllData,
    restoreData,
    reloadFromStorage,
    clearHistory,
    clearPbs,
    history,
    resetOnboarding,
    notificationSettings,
    updateNotificationPreferences,
  } = useWatermelonAppData();
  const colors = useTheme();
  const [editTimer, setEditTimer] = useState(null);
  const [timerVal, setTimerVal] = useState('');
  const [cacheSize, setCacheSize] = useState(null);
  const [downloading, setDownloading] = useState(false);
  const [downloadProgress, setDownloadProgress] = useState(null);
  const [photoSize, setPhotoSize] = useState(0);
  const [csvParsed, setCsvParsed] = useState(null);
  const [csvImporting, setCsvImporting] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);
  const [quietStartInput, setQuietStartInput] = useState('22:00');
  const [quietEndInput, setQuietEndInput] = useState('08:00');
  const [dailyReminderInput, setDailyReminderInput] = useState('');
  const [notificationPermissionGranted, setNotificationPermissionGranted] = useState(null);
  const [celebrationPreviewKey, setCelebrationPreviewKey] = useState(0);

  const haptic = settings?.hapticFeedback !== false;
  const weightUnit = settings?.weightUnit || 'kg';
  const celebration = resolveCelebrationConfig(settings);
  const insets = useSafeAreaInsets();
  const bottomSpacing = getBottomOverlaySpacing({
    safeAreaBottom: insets.bottom,
    includeWorkoutPill: true,
    extra: 8,
  });

  useEffect(() => {
    getCacheSize().then(bytes => setCacheSize(bytes));
    getProgressPhotos()
      .then((rows) => Promise.all((rows || []).map((row) => FileSystem.getInfoAsync(row.fileUri))))
      .then((infos) => {
        const total = (infos || []).reduce((sum, info) => sum + Number(info?.size || 0), 0);
        setPhotoSize(total);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    setQuietStartInput(formatQuietTime(getQuietMinutes(notificationSettings, 'quietHoursStartMinutes', 22)));
    setQuietEndInput(formatQuietTime(getQuietMinutes(notificationSettings, 'quietHoursEndMinutes', 8)));
    const reminderMinutes = Number(notificationSettings?.dailyReminderTimeMinutes);
    setDailyReminderInput(Number.isFinite(reminderMinutes) ? formatQuietTime(reminderMinutes) : '');
  }, [
    notificationSettings?.quietHoursStartMinutes,
    notificationSettings?.quietHoursEndMinutes,
    notificationSettings?.quietHoursStart,
    notificationSettings?.quietHoursEnd,
    notificationSettings?.dailyReminderTimeMinutes,
  ]);

  useEffect(() => {
    getNotificationPermissionStatus()
      .then((status) => setNotificationPermissionGranted(status.granted))
      .catch(() => {});
  }, []);

  const doDownloadAll = async () => {
    setDownloading(true);
    try {
      const index = await getExerciseIndex();
      const allPaths = (index || []).flatMap(ex => ex.images || []);
      const unique = [...new Set(allPaths)];
      await downloadAllImages(unique, (done, total) => {
        setDownloadProgress({ done, total });
      });
      const bytes = await getCacheSize();
      setCacheSize(bytes);
      setAlertConfig({ title: 'Done', message: `Downloaded ${unique.length} images (${formatBytes(bytes)})`, buttons: [{ text: 'OK', style: 'default' }] });
    } catch (e) {
      setAlertConfig({ title: 'Download failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setDownloading(false);
      setDownloadProgress(null);
    }
  };

  const doClearCache = async () => {
    setAlertConfig({
      title: 'Clear image cache?',
      message: 'Images will re-download on demand.',
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Clear', style: 'destructive', onPress: async () => { await clearCache(); setCacheSize(0); } },
      ],
    });
  };

  const cycleEffort = () => {
    const cur = settings.effortTracking || 'off';
    const next = EFFORT_CYCLE[(EFFORT_CYCLE.indexOf(cur) + 1) % EFFORT_CYCLE.length];
    fireHaptic('selection', { enabled: haptic });
    updateSettings({ ...settings, effortTracking: next });
  };

  const clearAllPhotos = () => {
    setAlertConfig({
      title: 'Clear all photos?',
      message: 'This will permanently delete all progress photos.',
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear All', style: 'destructive', onPress: async () => {
            try {
              await FileSystem.deleteAsync(PHOTO_DIR, { idempotent: true });
              await clearProgressPhotos();
              setPhotoSize(0);
            } catch (e) { setAlertConfig({ title: 'Error', message: String(e), buttons: [{ text: 'OK', style: 'default' }] }); }
          },
        },
      ],
    });
  };

  const saveTimer = () => {
    const val = parseFloat(timerVal);
    if (editTimer === 'barWeight') {
      const barWeightKg = convertUnitToKg(val, weightUnit, 1);
      if (!val || barWeightKg < 1 || barWeightKg > 100) {
        fireHaptic('invalidAction', { enabled: haptic });
        setAlertConfig({
          title: 'Invalid value',
          message: `Enter a bar weight between ${formatWeightFromKg(1, weightUnit)} and ${formatWeightFromKg(100, weightUnit)}.`,
          buttons: [{ text: 'OK', style: 'default' }],
        });
        return;
      }
    } else if (!val || val < 10 || val > 600) {
      fireHaptic('invalidAction', { enabled: haptic });
      setAlertConfig({ title: 'Invalid value', message: 'Enter a value between 10-600 seconds.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    fireHaptic('lightConfirm', { enabled: haptic });
    updateSettings({
      ...settings,
      [editTimer]: editTimer === 'barWeight' ? convertUnitToKg(val, weightUnit, 1) : Math.round(val),
    });
    setEditTimer(null);
  };

  const doExport = async () => {
    try {
      await exportBackup(getAllData());
      fireHaptic('backupSucceeded', { enabled: haptic });
      setAlertConfig({ title: 'Backup saved!', message: 'Your data has been exported.', buttons: [{ text: 'OK', style: 'default' }] });
    } catch (e) {
      fireHaptic('error', { enabled: haptic });
      setAlertConfig({ title: 'Export failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const doImport = async () => {
    try {
      const data = await importBackup();
      if (!data) return;
      setAlertConfig({
        title: 'Restore backup?',
        message: 'This will replace all current data.',
        buttons: [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Restore', style: 'destructive', onPress: async () => { await restoreData(data); fireHaptic('restoreSucceeded', { enabled: haptic, force: true }); setAlertConfig({ title: 'Restored!', message: 'Your backup has been restored.', buttons: [{ text: 'OK', style: 'default' }] }); } },
        ],
      });
    } catch (e) {
      fireHaptic('error', { enabled: haptic });
      setAlertConfig({ title: 'Import failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const doExportCSV = async () => {
    try {
      fireHaptic('lightConfirm', { enabled: haptic });
      await exportCSV(history);
    } catch (e) {
      fireHaptic('error', { enabled: haptic });
      setAlertConfig({ title: 'Export failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const doImportCSV = async () => {
    try {
      fireHaptic('lightConfirm', { enabled: haptic });
      const parsed = await pickAndParseCSV();
      if (!parsed) return;
      setCsvParsed(parsed);
    } catch (e) {
      fireHaptic('error', { enabled: haptic });
      setAlertConfig({ title: 'Import failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const doExportSQLite = async () => {
    try {
      fireHaptic('lightConfirm', { enabled: haptic });
      const bundle = await exportSQLiteBundleAndShare();
      const c = bundle.counts || {};
      const parts = [];
      if (c.plans > 0) parts.push(`${c.plans} plan${c.plans !== 1 ? 's' : ''}`);
      if (c.history > 0) parts.push(`${c.history} workout${c.history !== 1 ? 's' : ''}`);
      if (c.bodyWeight > 0) parts.push(`${c.bodyWeight} weight entries`);
      if (c.customExercises > 0) parts.push(`${c.customExercises} custom exercises`);
      setAlertConfig({
        title: 'SQLite export complete',
        message: parts.length ? `Exported: ${parts.join(', ')}.` : 'Export complete (no training data found).',
        buttons: [{ text: 'OK', style: 'default' }],
      });
    } catch (e) {
      setAlertConfig({ title: 'SQLite export failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const doExportOpenWeight = async () => {
    try {
      fireHaptic('lightConfirm', { enabled: haptic });
      const bundle = await exportOpenWeightBundleAndShare(history);
      setAlertConfig({
        title: 'OpenWeight export complete',
        message: `Exported ${bundle?.workoutLogs?.length || 0} workout logs.`,
        buttons: [{ text: 'OK', style: 'default' }],
      });
    } catch (e) {
      setAlertConfig({ title: 'OpenWeight export failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const doImportSQLite = async () => {
    try {
      const bundle = await pickSQLiteBundleFile();
      if (!bundle) return;
      const validation = validateSQLiteBundle(bundle);
      if (!validation.valid) {
        setAlertConfig({ title: 'Invalid SQLite export', message: validation.reason || 'Unsupported file.', buttons: [{ text: 'OK', style: 'default' }] });
        return;
      }
      setAlertConfig({
        title: 'Restore SQLite export?',
        message: `This will replace current training data.\n\nWorkouts: ${validation.counts?.history || 0}\nPlans: ${validation.counts?.plans || 0}`,
        buttons: [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Restore',
            style: 'destructive',
            onPress: async () => {
              await importSQLiteBundle(bundle);
              await reloadFromStorage();
              fireHaptic('restoreSucceeded', { enabled: haptic, force: true });
              setAlertConfig({ title: 'SQLite restore complete', message: 'Data imported and migrated safely.', buttons: [{ text: 'OK', style: 'default' }] });
            },
          },
        ],
      });
    } catch (e) {
      setAlertConfig({ title: 'SQLite import failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const confirmCSVImport = async () => {
    if (!csvParsed) return;
    setCsvImporting(true);
    try {
      const count = await importParsedCSV(csvParsed);
      await reloadFromStorage();
      setCsvParsed(null);
      fireHaptic('restoreSucceeded', { enabled: haptic });
      setAlertConfig({ title: 'Import complete', message: `Imported ${count} workouts.`, buttons: [{ text: 'OK', style: 'default' }] });
    } catch (e) {
      fireHaptic('error', { enabled: haptic });
      setAlertConfig({ title: 'Import error', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setCsvImporting(false);
    }
  };

  const setNotificationSnooze = async (hours) => {
    if (!hours || hours <= 0) {
      await updateNotificationPreferences({ snoozeUntil: null });
      setAlertConfig({ title: 'Notifications active', message: 'Snooze cleared.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    const until = new Date(Date.now() + hours * 3600000).toISOString();
    await updateNotificationPreferences({ snoozeUntil: until });
    setAlertConfig({ title: 'Notifications snoozed', message: `Paused for ${hours} hours.`, buttons: [{ text: 'OK', style: 'default' }] });
  };

  const requestNotificationAccess = async () => {
    const granted = await ensureNotificationPermissions();
    setNotificationPermissionGranted(granted);
    if (!granted) {
      showErrorToast('Notifications blocked', 'Enable notification permission in Android settings.');
      setAlertConfig({
        title: 'Notifications blocked',
        message: 'IRONLOG could not get notification permission. You can enable it later from Android app settings.',
        buttons: [{ text: 'OK', style: 'default' }],
      });
      return false;
    }
    await updateNotificationPreferences({ enabled: true });
    showSuccessToast('Notifications enabled', 'Smart reminders can now run with your policy settings.');
    setAlertConfig({
      title: 'Notifications enabled',
      message: 'Smart reminders can now use your quiet hours and cooldown rules.',
      buttons: [{ text: 'OK', style: 'default' }],
    });
    return true;
  };

  const sendTestNotificationNow = async () => {
    const granted = notificationPermissionGranted === true ? true : await requestNotificationAccess();
    if (!granted) return;
    try {
      await sendTestNotification({
        title: 'IRONLOG test notification',
        body: 'Your notification pipeline is alive and ready.',
      });
      showSuccessToast('Test notification sent', 'Check your notification shade in a moment.');
      setAlertConfig({
        title: 'Test notification sent',
        message: 'Check your notification shade in a moment.',
        buttons: [{ text: 'OK', style: 'default' }],
      });
    } catch (e) {
      showErrorToast('Test notification failed', String(e?.message || e));
      setAlertConfig({
        title: 'Test notification failed',
        message: String(e),
        buttons: [{ text: 'OK', style: 'default' }],
      });
    }
  };

  const handleNotificationToggle = async (value) => {
    if (!value) {
      await updateNotificationPreferences({ enabled: false });
      return;
    }
    const granted = await requestNotificationAccess();
    if (!granted) {
      await updateNotificationPreferences({ enabled: false });
    }
  };

  const applyQuietHours = async () => {
    const startMinutes = parseQuietTimeInput(quietStartInput);
    const endMinutes = parseQuietTimeInput(quietEndInput);
    if (startMinutes == null || endMinutes == null) {
      setAlertConfig({
        title: 'Invalid quiet hours',
        message: 'Use 24-hour time like 22:30 or 07:15.',
        buttons: [{ text: 'OK', style: 'default' }],
      });
      return;
    }
    await updateNotificationPreferences({
      quietHoursStartMinutes: startMinutes,
      quietHoursEndMinutes: endMinutes,
      quietHoursStart: Math.floor(startMinutes / 60),
      quietHoursEnd: Math.floor(endMinutes / 60),
    });
    fireHaptic('lightConfirm', { enabled: haptic });
    showInfoToast('Quiet hours updated', `${formatQuietTime(startMinutes)} - ${formatQuietTime(endMinutes)}`);
  };

  const applyDailyReminderTime = async () => {
    const raw = String(dailyReminderInput || '').trim();
    if (!raw) {
      await updateNotificationPreferences({ dailyReminderTimeMinutes: null });
      showInfoToast('Reminder time cleared', 'IRONLOG will infer reminder timing from your workout history.');
      return;
    }
    const minutes = parseQuietTimeInput(raw);
    if (minutes == null) {
      setAlertConfig({
        title: 'Invalid reminder time',
        message: 'Use 24-hour format like 18:30.',
        buttons: [{ text: 'OK', style: 'default' }],
      });
      return;
    }
    await updateNotificationPreferences({ dailyReminderTimeMinutes: minutes });
    fireHaptic('lightConfirm', { enabled: haptic });
    showSuccessToast('Reminder time saved', `Daily reminder target set to ${formatQuietTime(minutes)}.`);
  };

  const Row = ({ label, value, onPress, danger }) => (
    <TouchableOpacity
      style={[s.row, { borderBottomColor: colors.faint }]}
      onPress={() => {
        fireHaptic(danger ? 'destructiveAction' : 'selection', { enabled: haptic });
        if (onPress) onPress();
      }}>
      <Text style={[s.rowLabel, { color: colors.text }, danger && { color: '#CC3333' }]}>{label}</Text>
      {value ? <Text style={[s.rowValue, { color: colors.subtext }]}>{value}</Text> : <Ionicons name="chevron-forward" size={16} color={colors.muted} />}
    </TouchableOpacity>
  );

  const OptionRow = ({ label, hint, children, noBorder = false }) => (
    <View style={[s.stackRow, { borderBottomColor: noBorder ? 'transparent' : colors.faint }]}>
      <Text style={[s.rowLabel, { color: colors.text }]}>{label}</Text>
      {hint ? <Text style={[s.optionHint, { color: colors.muted }]}>{hint}</Text> : null}
      <View style={s.optionControlWrap}>{children}</View>
    </View>
  );

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <ScrollView
        style={s.container}
        contentContainerStyle={{ padding: 20, paddingBottom: bottomSpacing, gap: 16 }}
        scrollIndicatorInsets={{ bottom: bottomSpacing }}>
      {/* Theme */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>THEME</Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {Object.entries(THEMES).map(([key, theme]) => {
            const active = (settings.theme || 'amoled') === key;
            return (
              <TouchableOpacity key={key}
                style={[s.themeBtn, { borderColor: active ? theme.accent : colors.faint, backgroundColor: theme.bg }]}
                onPress={() => {
                  if ((settings.theme || 'amoled') !== key) fireHaptic('selection', { enabled: haptic });
                  updateSettings({ ...settings, theme: key });
                }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                  <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: theme.accent }} />
                  <Text style={{ color: theme.text, fontSize: 11, fontWeight: '700', letterSpacing: 1 }}>{theme.name}</Text>
                </View>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* Weight unit */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>WEIGHT UNIT</Text>
        <View style={{ flexDirection: 'row', gap: 10 }}>
          {['kg', 'lbs'].map(u => (
            <TouchableOpacity key={u} style={[s.unitBtn, { borderColor: colors.faint }, settings.weightUnit === u && { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
              onPress={() => {
                if (settings.weightUnit !== u) fireHaptic('selection', { enabled: haptic });
                updateSettings({ ...settings, weightUnit: u });
              }}>
              <Text style={[s.unitBtnText, { color: colors.muted }, settings.weightUnit === u && { color: colors.accent }]}>{u.toUpperCase()}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* Rest timers */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>REST TIMERS</Text>
        <Row label="Normal exercises" value={settings.defaultRestNormal + 's'} onPress={() => { setTimerVal(String(settings.defaultRestNormal)); setEditTimer('defaultRestNormal'); }} />
        <Row label="Heavy exercises" value={settings.defaultRestHeavy + 's'} onPress={() => { setTimerVal(String(settings.defaultRestHeavy)); setEditTimer('defaultRestHeavy'); }} />
      </View>

      {/* Workout */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>WORKOUT</Text>
        <View style={[s.row, { borderBottomColor: colors.faint }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Keep Screen Awake</Text>
          <Switch
            value={settings.keepAwake !== false}
            onValueChange={v => {
              fireHaptic('selection', { enabled: haptic });
              updateSettings({ ...settings, keepAwake: v });
            }}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={settings.keepAwake !== false ? colors.accent : colors.muted}
          />
        </View>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={cycleEffort}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Effort Tracking</Text>
          <Text style={[s.rowValue, { color: colors.accent }]}>{EFFORT_LABEL[settings.effortTracking || 'off']}</Text>
        </TouchableOpacity>
        <View style={[s.row, { borderBottomColor: colors.faint }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Haptic Feedback</Text>
          <Switch
            value={settings.hapticFeedback !== false}
            onValueChange={v => {
              updateSettings({ ...settings, hapticFeedback: v });
              setHapticsEnabled(v);
              if (v) fireHaptic('selection', { enabled: true, force: true });
            }}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={settings.hapticFeedback !== false ? colors.accent : colors.muted}
          />
        </View>

        <OptionRow label="Celebration animation" hint="Choose how the celebration moves after workout completion.">
          <View style={s.optionChips}>
            {[
              { id: 'off', label: 'Off' },
              { id: 'fireworks', label: 'Fireworks' },
              { id: 'wave', label: 'Wave' },
            ].map((opt) => {
              const active = opt.id === 'off' ? !celebration.enabled : celebration.enabled && celebration.style === opt.id;
              return (
                <TouchableOpacity
                  key={`celebration:${opt.id}`}
                  style={[s.goalBtn, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
                  onPress={() => {
                    if (opt.id === 'off') {
                      updateSettings({
                        ...settings,
                        celebrationEnabled: false,
                        celebrationAnimation: 'off',
                      });
                      return;
                    }
                    updateSettings({
                      ...settings,
                      celebrationEnabled: true,
                      celebrationStyle: opt.id,
                    });
                  }}
                >
                  <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{opt.label}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </OptionRow>
        <OptionRow label="Celebration colors" hint="Default is gold. Theme mode uses your active app palette.">
          <View style={s.optionChips}>
            {[
              { id: 'gold', label: 'Gold' },
              { id: 'theme', label: 'Theme' },
              { id: 'multicolor', label: 'Multicolor' },
            ].map((opt) => {
              const active = (celebration.colorMode || 'gold') === opt.id;
              return (
                <TouchableOpacity
                  key={`celebration-color:${opt.id}`}
                  style={[s.goalBtn, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
                  onPress={() => {
                    updateSettings({
                      ...settings,
                      celebrationEnabled: true,
                      celebrationColorMode: opt.id,
                      // Backward-compatible marker used by older builds.
                      celebrationAnimation: opt.id === 'gold' ? 'gold' : 'confetti',
                    });
                  }}
                >
                  <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{opt.label}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </OptionRow>

        <TouchableOpacity
          style={[s.row, { borderBottomColor: colors.faint }]}
          onPress={() => {
            fireHaptic('workoutCompleted', { enabled: true, force: true, androidAssist: true });
            Vibration.vibrate([0, 24, 40, 30]);
          }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Test Haptics</Text>
          <Ionicons name="pulse-outline" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity
          style={[s.row, { borderBottomColor: colors.faint }]}
          onPress={() => {
            fireHaptic('success', { enabled: true, force: true, androidAssist: true });
            setCelebrationPreviewKey(Date.now());
          }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Test Celebration</Text>
          <Ionicons name="sparkles-outline" size={16} color={colors.muted} />
        </TouchableOpacity>
      </View>

      {/* Goal mode */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>GOAL MODE</Text>
        <View style={[s.row, { borderBottomColor: colors.faint, alignItems: 'center' }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Workout days / week</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 0 }}>
            <TouchableOpacity
              onPress={() => {
                const next = Math.max(1, (settings.weeklyGoalDays || 4) - 1);
                fireHaptic('selection', { enabled: haptic });
                updateSettings({ ...settings, weeklyGoalDays: next });
              }}
              style={[s.stepperBtn, { borderColor: colors.faint }]}>
              <Text style={[s.stepperBtnText, { color: colors.text }]}>−</Text>
            </TouchableOpacity>
            <Text style={[s.stepperVal, { color: colors.text }]}>{settings.weeklyGoalDays || 4}</Text>
            <TouchableOpacity
              onPress={() => {
                const next = Math.min(7, (settings.weeklyGoalDays || 4) + 1);
                fireHaptic('selection', { enabled: haptic });
                updateSettings({ ...settings, weeklyGoalDays: next });
              }}
              style={[s.stepperBtn, { borderColor: colors.faint }]}>
              <Text style={[s.stepperBtnText, { color: colors.text }]}>+</Text>
            </TouchableOpacity>
          </View>
        </View>
        <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
          {GOAL_MODES.map((mode) => {
            const active = (settings.goalMode || 'hypertrophy') === mode.id;
            return (
              <TouchableOpacity
                key={mode.id}
                style={[
                  s.goalBtn,
                  { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' },
                ]}
                onPress={() => {
                  if (!active) fireHaptic('selection', { enabled: haptic });
                  updateSettings({ ...settings, goalMode: mode.id });
                }}>
                <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{mode.label}</Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* Intelligence preferences */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>INTELLIGENCE</Text>
        <Text style={[s.rowValue, { color: colors.subtext, marginBottom: 8 }]}>Progression behavior</Text>
        <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
          {PROGRESSION_STYLES.map((style) => {
            const active = (settings.progressionStyle || 'balanced') === style.id;
            return (
              <TouchableOpacity
                key={style.id}
                style={[s.goalBtn, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
                onPress={() => updateSettings({ ...settings, progressionStyle: style.id })}
              >
                <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{style.label}</Text>
              </TouchableOpacity>
            );
          })}
        </View>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { fireHaptic('selection', { enabled: haptic }); navigation.navigate('TrainingIntelligence'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Athlete Profile</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <View style={[s.row, { borderBottomColor: 'transparent', marginTop: 8 }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Compact analytics numbers</Text>
          <Switch
            value={settings.compactAnalyticsNumbers !== false}
            onValueChange={(value) => updateSettings({ ...settings, compactAnalyticsNumbers: value })}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={settings.compactAnalyticsNumbers !== false ? colors.accent : colors.muted}
          />
        </View>
      </View>

      {/* Plate calculator */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>PLATE CALCULATOR</Text>
        <Row
          label="Bar weight"
          value={formatWeightFromKg(settings.barWeight, weightUnit)}
          onPress={() => { setTimerVal(String(formatWeightFromKg(settings.barWeight, weightUnit, { showUnit: false }))); setEditTimer('barWeight'); }}
        />
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { fireHaptic('selection', { enabled: haptic }); navigation.navigate('GymProfiles'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Gym Profiles</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { fireHaptic('selection', { enabled: haptic }); navigation.navigate('ExerciseLibrary'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Exercise Library</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { fireHaptic('selection', { enabled: haptic }); navigation.navigate('BodyWeight'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Body Weight Tracker</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
      </View>

      {/* Exercise Images */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>EXERCISE IMAGES</Text>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]}
          onPress={doDownloadAll} disabled={downloading}>
          <Text style={[s.rowLabel, { color: colors.text }]}>
            {downloading
              ? downloadProgress
                ? `Downloading... ${downloadProgress.done}/${downloadProgress.total}`
                : 'Preparing...'
              : 'Download all images'}
          </Text>
          <Ionicons name="cloud-download-outline" size={16} color={colors.muted} />
        </TouchableOpacity>
        <Row
          label="Cache size"
          value={cacheSize !== null ? formatBytes(cacheSize) : '...'}
          onPress={() => getCacheSize().then(b => setCacheSize(b))}
        />
        <Row label="Clear image cache" onPress={doClearCache} />
      </View>

      {/* Progress Photos */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>PROGRESS PHOTOS</Text>
        <Row label="Storage used" value={formatBytes(photoSize)} onPress={() => {
          getProgressPhotos()
            .then((rows) => Promise.all((rows || []).map((row) => FileSystem.getInfoAsync(row.fileUri))))
            .then((infos) => {
              const total = (infos || []).reduce((sum, info) => sum + Number(info?.size || 0), 0);
              setPhotoSize(total);
            })
            .catch(() => {});
        }} />
        <TouchableOpacity style={[s.row, { borderBottomColor: 'transparent' }]} onPress={() => { fireHaptic('destructiveAction', { enabled: haptic }); clearAllPhotos(); }}>
          <Text style={[s.rowLabel, { color: '#CC2222' }]}>Clear All Photos</Text>
          <Ionicons name="trash-outline" size={16} color="#CC2222" />
        </TouchableOpacity>
      </View>

      {/* Backup */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>BACKUP & RESTORE</Text>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { fireHaptic('selection', { enabled: haptic }); navigation.navigate('DataPortability'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Backup & Export</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { fireHaptic('selection', { enabled: haptic }); navigation.navigate('BackupCenter'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Advanced Backup Centre</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { fireHaptic('selection', { enabled: haptic }); navigation.navigate('Privacy'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Privacy & Local-First</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => navigation.navigate('ImportCenter')}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Import Center (Strong / Hevy / OpenWeight)</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <Row label="Export workout history as CSV" onPress={doExportCSV} />
        <Row label="Import workout history from CSV" onPress={doImportCSV} />
        <Row label="Export workout logs as OpenWeight JSON" onPress={doExportOpenWeight} />
        <Row label="Export full data (SQLite v2)" onPress={doExportSQLite} />
        <Row label="Import full data (SQLite v2)" onPress={doImportSQLite} />
      </View>

      {/* Notifications */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>NOTIFICATIONS</Text>
        <View style={[s.row, { borderBottomColor: colors.faint }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Enable smart notifications</Text>
          <Switch
            value={notificationSettings?.enabled === true}
            onValueChange={handleNotificationToggle}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={notificationSettings?.enabled ? colors.accent : colors.muted}
          />
        </View>
        <OptionRow label="Notification permission">
          <TouchableOpacity
            style={[s.goalBtn, { borderColor: notificationPermissionGranted ? colors.accent : colors.faint, backgroundColor: notificationPermissionGranted ? colors.accentSoft : 'transparent' }]}
            onPress={requestNotificationAccess}
          >
            <Text style={[s.goalBtnText, { color: notificationPermissionGranted ? colors.accent : colors.subtext }]}>
              {notificationPermissionGranted ? 'Allowed' : 'Allow now'}
            </Text>
          </TouchableOpacity>
        </OptionRow>
        <TouchableOpacity
          style={[s.row, { borderBottomColor: colors.faint }]}
          onPress={sendTestNotificationNow}
        >
          <Text style={[s.rowLabel, { color: colors.text }]}>Send test notification</Text>
          <Ionicons name="notifications-outline" size={16} color={colors.muted} />
        </TouchableOpacity>
        <View style={[s.row, { borderBottomColor: colors.faint }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Daily workout reminder time</Text>
          <Text style={[s.rowValue, { color: colors.subtext }]}>
            {notificationSettings?.dailyReminderTimeMinutes == null ? 'Auto' : formatQuietTime(notificationSettings.dailyReminderTimeMinutes)}
          </Text>
        </View>
        <View style={s.timeInputRow}>
          <View style={s.timeInputGroup}>
            <Text style={[s.inputLabel, { color: colors.subtext }]}>TIME</Text>
            <TextInput
              style={[s.timeInput, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
              value={dailyReminderInput}
              onChangeText={setDailyReminderInput}
              placeholder="18:30 (blank = auto)"
              placeholderTextColor={colors.muted}
              keyboardType="numbers-and-punctuation"
              autoCapitalize="none"
              autoCorrect={false}
            />
          </View>
        </View>
        <View style={{ flexDirection: 'row', gap: 8, marginTop: 8, marginBottom: 8 }}>
          <TouchableOpacity
            style={[s.goalBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
            onPress={applyDailyReminderTime}
          >
            <Text style={[s.goalBtnText, { color: colors.accent }]}>Apply reminder time</Text>
          </TouchableOpacity>
        </View>
        <OptionRow label="Policy profile">
          <View style={s.optionChips}>
            {NOTIFICATION_PROFILES.map((profile) => {
              const active = (notificationSettings?.notificationProfile || 'balanced') === profile.id;
              return (
                <TouchableOpacity
                  key={`profile:${profile.id}`}
                  style={[s.goalBtn, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
                  onPress={() => updateNotificationPreferences({ notificationProfile: profile.id })}
                >
                  <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{profile.label}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </OptionRow>
        <View style={[s.row, { borderBottomColor: colors.faint }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Training reminders</Text>
          <Switch
            value={notificationSettings?.trainingReminders !== false}
            onValueChange={(value) => updateNotificationPreferences({ trainingReminders: value })}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={notificationSettings?.trainingReminders !== false ? colors.accent : colors.muted}
          />
        </View>
        <View style={[s.row, { borderBottomColor: colors.faint }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Milestone alerts</Text>
          <Switch
            value={notificationSettings?.milestoneNotifications !== false}
            onValueChange={(value) => updateNotificationPreferences({ milestoneNotifications: value })}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={notificationSettings?.milestoneNotifications !== false ? colors.accent : colors.muted}
          />
        </View>
        <View style={[s.row, { borderBottomColor: colors.faint }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Quiet hours</Text>
          <Text style={[s.rowValue, { color: colors.subtext }]}>
            {formatQuietTime(getQuietMinutes(notificationSettings, 'quietHoursStartMinutes', 22))}-{formatQuietTime(getQuietMinutes(notificationSettings, 'quietHoursEndMinutes', 8))}
          </Text>
        </View>
        <Text style={[s.rowValue, { color: colors.muted, fontSize: 12, marginTop: 6, marginBottom: 8 }]}>
          Use 24-hour time. Example: `22:30` to `07:15`.
        </Text>
        <View style={s.timeInputRow}>
          <View style={s.timeInputGroup}>
            <Text style={[s.inputLabel, { color: colors.subtext }]}>START</Text>
            <TextInput
              style={[s.timeInput, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
              value={quietStartInput}
              onChangeText={setQuietStartInput}
              placeholder="22:00"
              placeholderTextColor={colors.muted}
              keyboardType="numbers-and-punctuation"
              autoCapitalize="none"
              autoCorrect={false}
            />
          </View>
          <View style={s.timeInputGroup}>
            <Text style={[s.inputLabel, { color: colors.subtext }]}>END</Text>
            <TextInput
              style={[s.timeInput, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
              value={quietEndInput}
              onChangeText={setQuietEndInput}
              placeholder="08:00"
              placeholderTextColor={colors.muted}
              keyboardType="numbers-and-punctuation"
              autoCapitalize="none"
              autoCorrect={false}
            />
          </View>
        </View>
        <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
          <TouchableOpacity
            style={[s.goalBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
            onPress={applyQuietHours}
          >
            <Text style={[s.goalBtnText, { color: colors.accent }]}>Apply quiet hours</Text>
          </TouchableOpacity>
        </View>
        <OptionRow label="Cooldown window">
          <View style={s.optionChips}>
            {[6, 12, 24].map((hours) => {
              const active = Number(notificationSettings?.cooldownHours ?? 12) === hours;
              return (
                <TouchableOpacity
                  key={`cooldown:${hours}`}
                  style={[s.goalBtn, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
                  onPress={() => updateNotificationPreferences({ cooldownHours: hours })}
                >
                  <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{hours}h</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </OptionRow>
        <OptionRow label="Weekly cap mode">
          <View style={s.optionChips}>
            {[
              { id: 'plan_based', label: 'Plan' },
              { id: 'fixed_7', label: '7/wk' },
            ].map((mode) => {
              const active = (notificationSettings?.weeklyCapMode || 'plan_based') === mode.id;
              return (
                <TouchableOpacity
                  key={`cap:${mode.id}`}
                  style={[s.goalBtn, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
                  onPress={() => updateNotificationPreferences({ weeklyCapMode: mode.id })}
                >
                  <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{mode.label}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </OptionRow>
        <OptionRow label="Reminder lead time">
          <View style={s.optionChips}>
            {[60, 90, 120].map((mins) => {
              const active = Number(notificationSettings?.reminderLeadMinutes ?? 90) === mins;
              return (
                <TouchableOpacity
                  key={`lead:${mins}`}
                  style={[s.goalBtn, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
                  onPress={() => updateNotificationPreferences({ reminderLeadMinutes: mins })}
                >
                  <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{mins}m</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </OptionRow>
        <OptionRow label="Temporary snooze">
          <View style={s.optionChips}>
            {[
              { label: 'Off', hours: 0 },
              { label: '24h', hours: 24 },
              { label: '72h', hours: 72 },
            ].map((option) => {
              const until = new Date(notificationSettings?.snoozeUntil || 0).getTime();
              const active = option.hours === 0
                ? !Number.isFinite(until) || until <= Date.now()
                : Number.isFinite(until) && until > Date.now() && Math.abs(until - (Date.now() + option.hours * 3600000)) < 3 * 3600000;
              return (
                <TouchableOpacity
                  key={`snooze:${option.label}`}
                  style={[s.goalBtn, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}
                  onPress={() => setNotificationSnooze(option.hours)}
                >
                  <Text style={[s.goalBtnText, { color: active ? colors.accent : colors.subtext }]}>{option.label}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </OptionRow>
        <View style={{ marginTop: 8, gap: 6 }}>
          <Text style={[s.sectionTitle, { color: colors.muted, marginBottom: 0 }]}>NOTIFICATION LOG</Text>
          {(notificationSettings?.decisionLog || []).slice(0, 5).map((entry, idx) => (
            <View key={`${entry?.at || ''}:${idx}`} style={[s.row, s.logRow, { borderBottomColor: colors.faint, paddingVertical: 8 }]}>
              <Text style={[s.rowLabel, s.logLabel, { color: colors.subtext, fontSize: 12 }]} numberOfLines={1}>
                {formatDecisionLabel(entry)}
              </Text>
              <Text style={[s.rowValue, s.logTime, { color: colors.muted, fontSize: 11 }]} numberOfLines={1}>
                {formatDecisionTime(entry)}
              </Text>
            </View>
          ))}
          {(!notificationSettings?.decisionLog || notificationSettings.decisionLog.length === 0) ? (
            <Text style={[s.rowValue, { color: colors.muted, fontSize: 12 }]}>No notification decisions yet.</Text>
          ) : null}
        </View>
      </View>

      {/* App */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>APP</Text>
        <Row label="Show App Tutorial" onPress={() => setAlertConfig({
          title: 'Show Tutorial?',
          message: 'The onboarding walkthrough will appear on next launch.',
          buttons: [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Reset', style: 'default', onPress: resetOnboarding },
          ],
        })} />
      </View>

      {/* Danger zone */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 28 }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>DANGER ZONE</Text>
        <Row label="Clear all history" danger onPress={() => setAlertConfig({ title: 'Clear history?', message: 'This cannot be undone.', buttons: [{ text: 'Cancel', style: 'cancel' }, { text: 'Clear', style: 'destructive', onPress: clearHistory }] })} />
        <Row label="Reset all PBs" danger onPress={() => setAlertConfig({ title: 'Reset PBs?', message: 'All personal bests will be cleared.', buttons: [{ text: 'Cancel', style: 'cancel' }, { text: 'Reset', style: 'destructive', onPress: clearPbs }] })} />
      </View>

      <Text style={{ color: colors.faint, fontSize: 11, textAlign: 'center', letterSpacing: 2 }}>IRONLOG v2.0</Text>

      <ImportPreviewModal
        visible={!!csvParsed}
        preview={csvParsed?.preview}
        loading={csvImporting}
        onConfirm={confirmCSVImport}
        onCancel={() => setCsvParsed(null)}
      />

      {/* Timer edit modal */}
      <Modal visible={!!editTimer} transparent animationType="fade">
        <View style={s.overlay}>
          <View style={[s.modal, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[s.modalTitle, { color: colors.text }]}>{editTimer === 'defaultRestNormal' ? 'NORMAL REST' : editTimer === 'defaultRestHeavy' ? 'HEAVY REST' : 'BAR WEIGHT'}</Text>
            <TextInput style={[s.input, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]} keyboardType="numeric" value={timerVal} onChangeText={setTimerVal} autoFocus
              placeholder={editTimer === 'barWeight' ? weightUnit : 'seconds'} placeholderTextColor={colors.muted} />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={[s.cancelBtn, { borderColor: colors.faint }]} onPress={() => { fireHaptic('selection', { enabled: haptic }); setEditTimer(null); }}><Text style={{ color: colors.muted }}>Cancel</Text></TouchableOpacity>
              <TouchableOpacity style={[s.confirmBtn, { backgroundColor: colors.accent }]} onPress={saveTimer}><Text style={{ color: colors.textOnAccent || '#fff', fontWeight: '800' }}>SAVE</Text></TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
      </ScrollView>
      {celebrationPreviewKey ? (
        <GoldConfettiBurst
          key={celebrationPreviewKey}
          enabled={true}
          count={celebration.style === 'wave' ? 55 : 85}
          variant={celebration.style || 'fireworks'}
          colorMode={celebration.colorMode || 'gold'}
          themeColors={colors}
          fullScreen
          onDone={() => setCelebrationPreviewKey(0)}
        />
      ) : null}
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  section: { borderWidth: 1, padding: 16, borderRadius: 14, marginBottom: 10 },
  sectionTitle: { fontSize: 10, letterSpacing: 3, marginBottom: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 13, borderBottomWidth: 1, gap: 10 },
  rowLabel: { fontSize: 14, flex: 1, lineHeight: 19 },
  rowValue: { fontSize: 14 },
  unitBtn: { flex: 1, borderWidth: 1, padding: 12, alignItems: 'center', borderRadius: 10 },
  unitBtnActive: {},
  unitBtnText: { fontWeight: '800', fontSize: 16, letterSpacing: 2 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', justifyContent: 'center', alignItems: 'center', padding: 24 },
  modal: { borderRadius: 14, padding: 24, width: '100%', borderWidth: 1 },
  modalTitle: { fontSize: 16, fontWeight: '900', letterSpacing: 2, marginBottom: 16 },
  inputLabel: { fontSize: 9, fontWeight: '700', letterSpacing: 2, marginBottom: 6 },
  input: { borderWidth: 1, padding: 14, fontSize: 24, fontWeight: '900', textAlign: 'center', borderRadius: 10 },
  themeBtn: { paddingHorizontal: 14, paddingVertical: 10, borderWidth: 2, borderRadius: 10 },
  goalBtn: { borderWidth: 1, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 999 },
  goalBtnText: { fontSize: 11, fontWeight: '700', letterSpacing: 0.6 },
  stepperBtn: { borderWidth: 1, width: 32, height: 32, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  stepperBtnText: { fontSize: 18, fontWeight: '700', lineHeight: 22 },
  stepperVal: { fontSize: 18, fontWeight: '700', minWidth: 32, textAlign: 'center' },
  stackRow: { paddingVertical: 13, borderBottomWidth: 1 },
  optionHint: { fontSize: 11, marginTop: 6, lineHeight: 16 },
  optionControlWrap: { marginTop: 10, alignItems: 'flex-start' },
  optionChips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, alignItems: 'center' },
  timeInputRow: { flexDirection: 'row', gap: 10 },
  timeInputGroup: { flex: 1 },
  timeInput: { borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10, fontSize: 15, fontWeight: '700', letterSpacing: 0.8, borderRadius: 10 },
  logRow: { alignItems: 'flex-start' },
  logLabel: { flex: 1, paddingRight: 8 },
  logTime: { minWidth: 106, textAlign: 'right', marginTop: 1 },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1, borderRadius: 10 },
  confirmBtn: { flex: 1, padding: 14, alignItems: 'center', borderRadius: 10 },
});


