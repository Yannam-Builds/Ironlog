
import React, { useContext, useState, useEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, TextInput, Modal, Switch, Vibration } from 'react-native';
import CustomAlert from '../components/CustomAlert';
import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as FileSystem from 'expo-file-system/legacy';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import { THEMES } from '../utils/themes';
import { exportBackup, importBackup } from '../utils/backup';
import { getCacheSize, clearCache, downloadAllImages } from '../services/ExerciseImageCache';
import { getExerciseIndex } from '../services/ExerciseLibraryService';
import { exportCSV } from '../services/CSVExport';
import { pickAndParseCSV, importParsedCSV } from '../services/CSVImport';
import ImportPreviewModal from '../components/ImportPreviewModal';
import { setHapticsEnabled, triggerHaptic } from '../services/hapticsEngine';

const PHOTO_INDEX_KEY = '@ironlog/progressPhotoIndex';
const PHOTO_DIR = FileSystem.documentDirectory + 'progress-photos/';
const EFFORT_CYCLE = ['off', 'rpe', 'rir', 'both'];
const EFFORT_LABEL = { off: 'Off', rpe: 'RPE', rir: 'RIR', both: 'Both' };

function formatBytes(bytes) {
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

export default function SettingsScreen({ navigation }) {
  const { settings, updateSettings, getAllData, restoreData, reloadFromStorage, clearHistory, clearPbs, history, resetOnboarding } = useContext(AppContext);
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

  const haptic = settings?.hapticFeedback !== false;

  useEffect(() => {
    getCacheSize().then(bytes => setCacheSize(bytes));
    AsyncStorage.getItem(PHOTO_INDEX_KEY).then(raw => {
      if (!raw) return;
      const idx = JSON.parse(raw);
      const total = idx.reduce((a, p) => a + (p.sizeBytes || 0), 0);
      setPhotoSize(total);
    }).catch(() => {});
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
    triggerHaptic('selection', { enabled: haptic }).catch(() => {});
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
              await AsyncStorage.removeItem(PHOTO_INDEX_KEY);
              setPhotoSize(0);
            } catch (e) { setAlertConfig({ title: 'Error', message: String(e), buttons: [{ text: 'OK', style: 'default' }] }); }
          },
        },
      ],
    });
  };

  const saveTimer = () => {
    const val = parseInt(timerVal);
    if (editTimer === 'barWeight') {
      if (!val || val < 1 || val > 100) {
        triggerHaptic('invalidAction', { enabled: haptic }).catch(() => {});
        setAlertConfig({ title: 'Invalid value', message: 'Enter a bar weight between 1–100 kg.', buttons: [{ text: 'OK', style: 'default' }] });
        return;
      }
    } else if (!val || val < 10 || val > 600) {
      triggerHaptic('invalidAction', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'Invalid value', message: 'Enter a value between 10–600 seconds.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    triggerHaptic('lightConfirm', { enabled: haptic }).catch(() => {});
    updateSettings({ ...settings, [editTimer]: val });
    setEditTimer(null);
  };

  const doExport = async () => {
    try {
      await exportBackup(getAllData());
      triggerHaptic('backupSucceeded', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'Backup saved!', message: 'Your data has been exported.', buttons: [{ text: 'OK', style: 'default' }] });
    } catch (e) {
      triggerHaptic('error', { enabled: haptic }).catch(() => {});
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
          { text: 'Restore', style: 'destructive', onPress: async () => { await restoreData(data); triggerHaptic('restoreSucceeded', { enabled: haptic, force: true }).catch(() => {}); setAlertConfig({ title: 'Restored!', message: 'Your backup has been restored.', buttons: [{ text: 'OK', style: 'default' }] }); } },
        ],
      });
    } catch (e) {
      triggerHaptic('error', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'Import failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const doExportCSV = async () => {
    try {
      triggerHaptic('lightConfirm', { enabled: haptic }).catch(() => {});
      await exportCSV(history);
    } catch (e) {
      triggerHaptic('error', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'Export failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const doImportCSV = async () => {
    try {
      triggerHaptic('lightConfirm', { enabled: haptic }).catch(() => {});
      const parsed = await pickAndParseCSV();
      if (!parsed) return;
      setCsvParsed(parsed);
    } catch (e) {
      triggerHaptic('error', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'Import failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    }
  };

  const confirmCSVImport = async () => {
    if (!csvParsed) return;
    setCsvImporting(true);
    try {
      const count = await importParsedCSV(csvParsed);
      await reloadFromStorage();
      setCsvParsed(null);
      triggerHaptic('restoreSucceeded', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'Import complete', message: `Imported ${count} workouts.`, buttons: [{ text: 'OK', style: 'default' }] });
    } catch (e) {
      triggerHaptic('error', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'Import error', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setCsvImporting(false);
    }
  };

  const Row = ({ label, value, onPress, danger }) => (
    <TouchableOpacity
      style={[s.row, { borderBottomColor: colors.faint }]}
      onPress={() => {
        triggerHaptic(danger ? 'destructiveAction' : 'selection', { enabled: haptic }).catch(() => {});
        if (onPress) onPress();
      }}>
      <Text style={[s.rowLabel, { color: colors.text }, danger && { color: '#CC3333' }]}>{label}</Text>
      {value ? <Text style={[s.rowValue, { color: colors.subtext }]}>{value}</Text> : <Ionicons name="chevron-forward" size={16} color={colors.muted} />}
    </TouchableOpacity>
  );

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ padding: 20, paddingBottom: 40, gap: 16 }}>
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
                  if ((settings.theme || 'amoled') !== key) triggerHaptic('selection', { enabled: haptic }).catch(() => {});
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
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>WEIGHT UNIT</Text>
        <View style={{ flexDirection: 'row', gap: 10 }}>
          {['kg', 'lbs'].map(u => (
            <TouchableOpacity key={u} style={[s.unitBtn, { borderColor: colors.faint }, settings.weightUnit === u && { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
              onPress={() => {
                if (settings.weightUnit !== u) triggerHaptic('selection', { enabled: haptic }).catch(() => {});
                updateSettings({ ...settings, weightUnit: u });
              }}>
              <Text style={[s.unitBtnText, { color: colors.muted }, settings.weightUnit === u && { color: colors.accent }]}>{u.toUpperCase()}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* Rest timers */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>REST TIMERS</Text>
        <Row label="Normal exercises" value={settings.defaultRestNormal + 's'} onPress={() => { setTimerVal(String(settings.defaultRestNormal)); setEditTimer('defaultRestNormal'); }} />
        <Row label="Heavy exercises" value={settings.defaultRestHeavy + 's'} onPress={() => { setTimerVal(String(settings.defaultRestHeavy)); setEditTimer('defaultRestHeavy'); }} />
      </View>

      {/* Workout */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>WORKOUT</Text>
        <View style={[s.row, { borderBottomColor: colors.faint }]}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Keep Screen Awake</Text>
          <Switch
            value={settings.keepAwake !== false}
            onValueChange={v => {
              triggerHaptic('selection', { enabled: haptic }).catch(() => {});
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
              if (v) triggerHaptic('selection', { enabled: true, force: true }).catch(() => {});
            }}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={settings.hapticFeedback !== false ? colors.accent : colors.muted}
          />
        </View>
        <TouchableOpacity
          style={[s.row, { borderBottomColor: colors.faint }]}
          onPress={() => {
            triggerHaptic('workoutCompleted', { enabled: true, force: true, androidAssist: true }).catch(() => {});
            Vibration.vibrate([0, 24, 40, 30]);
          }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Test Haptics</Text>
          <Ionicons name="pulse-outline" size={16} color={colors.muted} />
        </TouchableOpacity>
      </View>

      {/* Plate calculator */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>PLATE CALCULATOR</Text>
        <Row label="Bar weight" value={settings.barWeight + ' kg'} onPress={() => { setTimerVal(String(settings.barWeight)); setEditTimer('barWeight'); }} />
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { triggerHaptic('selection', { enabled: haptic }).catch(() => {}); navigation.navigate('GymProfiles'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Gym Profiles</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { triggerHaptic('selection', { enabled: haptic }).catch(() => {}); navigation.navigate('ExerciseLibrary'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Exercise Library</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { triggerHaptic('selection', { enabled: haptic }).catch(() => {}); navigation.navigate('BodyWeight'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Body Weight Tracker</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
      </View>

      {/* Exercise Images */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
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
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>PROGRESS PHOTOS</Text>
        <Row label="Storage used" value={formatBytes(photoSize)} onPress={() => {
          AsyncStorage.getItem(PHOTO_INDEX_KEY).then(raw => {
            if (!raw) { setPhotoSize(0); return; }
            const idx = JSON.parse(raw);
            setPhotoSize(idx.reduce((a, p) => a + (p.sizeBytes || 0), 0));
          }).catch(() => {});
        }} />
        <TouchableOpacity style={[s.row, { borderBottomColor: 'transparent' }]} onPress={() => { triggerHaptic('destructiveAction', { enabled: haptic }).catch(() => {}); clearAllPhotos(); }}>
          <Text style={[s.rowLabel, { color: '#CC2222' }]}>Clear All Photos</Text>
          <Ionicons name="trash-outline" size={16} color="#CC2222" />
        </TouchableOpacity>
      </View>

      {/* Backup */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>BACKUP & RESTORE</Text>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { triggerHaptic('selection', { enabled: haptic }).catch(() => {}); navigation.navigate('BackupCenter'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Backup Center</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <TouchableOpacity style={[s.row, { borderBottomColor: colors.faint }]} onPress={() => { triggerHaptic('selection', { enabled: haptic }).catch(() => {}); navigation.navigate('Privacy'); }}>
          <Text style={[s.rowLabel, { color: colors.text }]}>Privacy & Local-First</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
        <Row label="Manual export backup (JSON)" onPress={doExport} />
        <Row label="Manual import backup (JSON)" onPress={doImport} />
        <Row label="Export workout history as CSV" onPress={doExportCSV} />
        <Row label="Import workout history from CSV" onPress={doImportCSV} />
      </View>

      {/* App */}
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
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
      <View style={[s.section, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.sectionTitle, { color: colors.muted }]}>DANGER ZONE</Text>
        <Row label="Clear all history" danger onPress={() => setAlertConfig({ title: 'Clear history?', message: 'This cannot be undone.', buttons: [{ text: 'Cancel', style: 'cancel' }, { text: 'Clear', style: 'destructive', onPress: clearHistory }] })} />
        <Row label="Reset all PBs" danger onPress={() => setAlertConfig({ title: 'Reset PBs?', message: 'All personal bests will be cleared.', buttons: [{ text: 'Cancel', style: 'cancel' }, { text: 'Reset', style: 'destructive', onPress: clearPbs }] })} />
      </View>

      <Text style={{ color: colors.faint, fontSize: 11, textAlign: 'center', letterSpacing: 2 }}>IRONLOG v2.0 · BUILT BY PRANAV</Text>

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
          <View style={s.modal}>
            <Text style={s.modalTitle}>{editTimer === 'defaultRestNormal' ? 'NORMAL REST' : editTimer === 'defaultRestHeavy' ? 'HEAVY REST' : 'BAR WEIGHT'}</Text>
            <TextInput style={s.input} keyboardType="numeric" value={timerVal} onChangeText={setTimerVal} autoFocus
              placeholder={editTimer === 'barWeight' ? 'kg' : 'seconds'} placeholderTextColor="#444" />
            <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
              <TouchableOpacity style={s.cancelBtn} onPress={() => { triggerHaptic('selection', { enabled: haptic }).catch(() => {}); setEditTimer(null); }}><Text style={{ color: '#666' }}>Cancel</Text></TouchableOpacity>
              <TouchableOpacity style={s.confirmBtn} onPress={saveTimer}><Text style={{ color: '#fff', fontWeight: '800' }}>SAVE</Text></TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  section: { backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', padding: 16 },
  sectionTitle: { fontSize: 10, letterSpacing: 3, color: '#444', marginBottom: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#111' },
  rowLabel: { fontSize: 14, color: '#f0f0f0' },
  rowValue: { fontSize: 14, color: '#666' },
  unitBtn: { flex: 1, borderWidth: 1, borderColor: '#1e1e1e', padding: 12, alignItems: 'center' },
  unitBtnActive: { borderColor: '#FF4500', backgroundColor: '#FF450011' },
  unitBtnText: { fontWeight: '800', fontSize: 16, color: '#666', letterSpacing: 2 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.85)', justifyContent: 'center', alignItems: 'center', padding: 24 },
  modal: { backgroundColor: '#141414', borderRadius: 12, padding: 24, width: '100%', borderWidth: 1, borderColor: '#1e1e1e' },
  modalTitle: { color: '#f0f0f0', fontSize: 16, fontWeight: '900', letterSpacing: 2, marginBottom: 16 },
  input: { backgroundColor: '#0f0f0f', borderWidth: 1, borderColor: '#2a2a2a', color: '#f0f0f0', padding: 14, fontSize: 24, fontWeight: '900', textAlign: 'center' },
  themeBtn: { paddingHorizontal: 14, paddingVertical: 10, borderWidth: 2, borderRadius: 2 },
  cancelBtn: { flex: 1, padding: 14, alignItems: 'center', borderWidth: 1, borderColor: '#1e1e1e' },
  confirmBtn: { flex: 1, backgroundColor: '#FF4500', padding: 14, alignItems: 'center' },
});
