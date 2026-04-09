
import React, { useState, useEffect, useCallback, useContext, useRef } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, Modal,
  FlatList, ScrollView, Dimensions, ActivityIndicator,
} from 'react-native';
import * as FileSystem from 'expo-file-system/legacy';
import * as ImagePicker from 'expo-image-picker';
import * as ImageManipulator from 'expo-image-manipulator';
import * as Sharing from 'expo-sharing';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'react-native';
import JSZip from 'jszip';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import CustomAlert from '../components/CustomAlert';
import { triggerHaptic } from '../services/hapticsEngine';

const PHOTO_DIR = FileSystem.documentDirectory + 'progress-photos/';
const PHOTO_INDEX_KEY = '@ironlog/progressPhotoIndex';
const DAYS_OF_WEEK = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];
const { width: SCREEN_W } = Dimensions.get('window');

// ─── Storage helpers ──────────────────────────────────────────────────────────

async function ensurePhotoDir() {
  const info = await FileSystem.getInfoAsync(PHOTO_DIR);
  if (!info.exists) await FileSystem.makeDirectoryAsync(PHOTO_DIR, { intermediates: true });
}

async function loadIndex() {
  try {
    const raw = await AsyncStorage.getItem(PHOTO_INDEX_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (_) { return []; }
}

async function saveIndex(index) {
  await AsyncStorage.setItem(PHOTO_INDEX_KEY, JSON.stringify(index));
}

function formatBytes(bytes) {
  if (!bytes) return '0 KB';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// ─── Calendar grid ────────────────────────────────────────────────────────────

function CalendarGrid({ year, month, photoSet, onDayPress, colors }) {
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const today = new Date().toISOString().split('T')[0];

  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) {
    const date = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    cells.push({ day: d, date, hasPhoto: photoSet.has(date), isToday: date === today });
  }

  const cellW = Math.floor((SCREEN_W - 32) / 7);

  return (
    <View>
      {/* Day headers */}
      <View style={{ flexDirection: 'row', marginBottom: 4 }}>
        {DAYS_OF_WEEK.map(d => (
          <View key={d} style={{ width: cellW, alignItems: 'center', paddingVertical: 6 }}>
            <Text style={{ fontSize: 9, letterSpacing: 1, color: colors.muted }}>{d}</Text>
          </View>
        ))}
      </View>
      {/* Day cells */}
      {Array.from({ length: Math.ceil(cells.length / 7) }, (_, ri) => (
        <View key={ri} style={{ flexDirection: 'row' }}>
          {cells.slice(ri * 7, ri * 7 + 7).map((cell, ci) =>
            cell ? (
              <TouchableOpacity
                key={ci}
                style={[cg.cell, { width: cellW, height: cellW + 8, borderColor: cell.isToday ? colors.accent : 'transparent' }]}
                onPress={() => onDayPress(cell)}
                activeOpacity={0.7}>
                <Text style={[cg.dayNum, { color: cell.hasPhoto ? colors.text : colors.muted, fontWeight: cell.isToday ? '900' : '400' }]}>
                  {cell.day}
                </Text>
                {cell.hasPhoto ? (
                  <View style={[cg.dot, { backgroundColor: colors.accent }]} />
                ) : null}
              </TouchableOpacity>
            ) : (
              <View key={ci} style={{ width: cellW, height: cellW + 8 }} />
            )
          )}
        </View>
      ))}
    </View>
  );
}
const cg = StyleSheet.create({
  cell: { alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderRadius: 4, margin: 1, paddingVertical: 6 },
  dayNum: { fontSize: 13 },
  dot: { width: 5, height: 5, borderRadius: 3, marginTop: 3 },
});

// ─── Full-screen photo viewer ─────────────────────────────────────────────────

function PhotoViewer({ visible, photoDays, initialDate, onClose }) {
  const initialIndex = photoDays.findIndex(p => p.date === initialDate);
  const listRef = useRef(null);

  useEffect(() => {
    if (visible && initialIndex >= 0 && listRef.current) {
      setTimeout(() => {
        listRef.current?.scrollToIndex({ index: initialIndex, animated: false });
      }, 100);
    }
  }, [visible, initialIndex]);

  return (
    <Modal visible={visible} animationType="fade" onRequestClose={onClose} statusBarTranslucent>
      <View style={{ flex: 1, backgroundColor: '#000' }}>
        <TouchableOpacity style={pv.closeBtn} onPress={onClose}>
          <Ionicons name="close" size={28} color="#fff" />
        </TouchableOpacity>
        <FlatList
          ref={listRef}
          data={photoDays}
          keyExtractor={item => item.date}
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          getItemLayout={(_, i) => ({ length: SCREEN_W, offset: SCREEN_W * i, index: i })}
          initialScrollIndex={initialIndex >= 0 ? initialIndex : 0}
          onScrollToIndexFailed={() => {}}
          renderItem={({ item }) => (
            <View style={{ width: SCREEN_W, flex: 1, justifyContent: 'center', alignItems: 'center' }}>
              <Image
                source={{ uri: PHOTO_DIR + item.filename }}
                style={{ width: SCREEN_W, height: SCREEN_W * 1.25, resizeMode: 'contain' }}
              />
              <View style={pv.dateOverlay}>
                <Text style={pv.dateText}>{item.date}</Text>
              </View>
            </View>
          )}
        />
      </View>
    </Modal>
  );
}
const pv = StyleSheet.create({
  closeBtn: { position: 'absolute', top: 48, right: 20, zIndex: 10, padding: 8 },
  dateOverlay: { position: 'absolute', bottom: 60, alignSelf: 'center', backgroundColor: 'rgba(0,0,0,0.6)', paddingHorizontal: 16, paddingVertical: 6 },
  dateText: { color: '#fff', fontSize: 13, letterSpacing: 2 },
});

// ─── Main Screen ──────────────────────────────────────────────────────────────

export default function ProgressPhotosScreen() {
  const { settings } = useContext(AppContext);
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;

  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth());
  const [photoIndex, setPhotoIndex] = useState([]);
  const [loading, setLoading] = useState(false);
  const [viewerDate, setViewerDate] = useState(null);
  const [showViewer, setShowViewer] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);

  const photoDays = photoIndex.filter(p => p.filename);
  const photoSet = new Set(photoIndex.map(p => p.date));
  const totalSize = photoIndex.reduce((a, p) => a + (p.sizeBytes || 0), 0);

  const reload = useCallback(async () => {
    const idx = await loadIndex();
    setPhotoIndex(idx);
  }, []);

  useEffect(() => { reload(); }, []);

  const prevMonth = () => {
    if (month === 0) { setYear(y => y - 1); setMonth(11); }
    else setMonth(m => m - 1);
  };
  const nextMonth = () => {
    if (month === 11) { setYear(y => y + 1); setMonth(0); }
    else setMonth(m => m + 1);
  };

  const pickPhoto = async (date, source) => {
    try {
      let result;
      if (source === 'camera') {
        const perm = await ImagePicker.requestCameraPermissionsAsync();
        if (!perm.granted) { setAlertConfig({ title: 'Permission required', message: 'Camera permission is needed to take photos.', buttons: [{ text: 'OK', style: 'default' }] }); return; }
        result = await ImagePicker.launchCameraAsync({ quality: 1 });
      } else {
        const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
        if (!perm.granted) { setAlertConfig({ title: 'Permission required', message: 'Gallery permission is needed to select photos.', buttons: [{ text: 'OK', style: 'default' }] }); return; }
        result = await ImagePicker.launchImageLibraryAsync({ quality: 1 });
      }
      if (result.canceled) return;

      setLoading(true);
      const asset = result.assets[0];
      const maxDim = Math.max(asset.width || 1080, asset.height || 1080);
      const scale = maxDim > 1080 ? 1080 / maxDim : 1;
      const manipulated = await ImageManipulator.manipulateAsync(
        asset.uri,
        scale < 1 ? [{ resize: { width: Math.round(asset.width * scale) } }] : [],
        { compress: 0.5, format: ImageManipulator.SaveFormat.JPEG }
      );

      await ensurePhotoDir();
      const filename = `${date}.jpg`;
      const destUri = PHOTO_DIR + filename;
      await FileSystem.copyAsync({ from: manipulated.uri, to: destUri });
      const info = await FileSystem.getInfoAsync(destUri);

      const idx = await loadIndex();
      const existing = idx.findIndex(p => p.date === date);
      const entry = { date, filename, sizeBytes: info.size || 0 };
      if (existing >= 0) idx[existing] = entry;
      else idx.push(entry);
      idx.sort((a, b) => a.date.localeCompare(b.date));
      await saveIndex(idx);
      setPhotoIndex(idx);
      triggerHaptic('selection', { enabled: haptic }).catch(() => {});
    } catch (e) {
      setAlertConfig({ title: 'Error saving photo', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setLoading(false);
    }
  };

  const onDayPress = (cell) => {
    triggerHaptic('selection', { enabled: haptic }).catch(() => {});
    if (cell.hasPhoto) {
      setViewerDate(cell.date);
      setShowViewer(true);
    } else {
      setAlertConfig({
        title: 'Add photo',
        message: cell.date,
        buttons: [
          { text: 'Camera', style: 'default', onPress: () => pickPhoto(cell.date, 'camera') },
          { text: 'Gallery', style: 'default', onPress: () => pickPhoto(cell.date, 'gallery') },
          { text: 'Cancel', style: 'cancel' },
        ],
      });
    }
  };

  const exportAll = async () => {
    if (photoDays.length === 0) { setAlertConfig({ title: 'No photos', message: 'No photos to export yet.', buttons: [{ text: 'OK', style: 'default' }] }); return; }
    setExporting(true);
    try {
      const zip = new JSZip();
      for (const { filename } of photoDays) {
        const uri = PHOTO_DIR + filename;
        const info = await FileSystem.getInfoAsync(uri);
        if (!info.exists) continue;
        const base64 = await FileSystem.readAsStringAsync(uri, { encoding: FileSystem.EncodingType.Base64 });
        zip.file(filename, base64, { base64: true });
      }
      const content = await zip.generateAsync({ type: 'base64' });
      const date = new Date().toISOString().split('T')[0];
      const zipUri = FileSystem.cacheDirectory + `IRONLOG_progress_photos_${date}.zip`;
      await FileSystem.writeAsStringAsync(zipUri, content, { encoding: FileSystem.EncodingType.Base64 });
      await Sharing.shareAsync(zipUri, { mimeType: 'application/zip', dialogTitle: 'Export Progress Photos' });
    } catch (e) {
      setAlertConfig({ title: 'Export failed', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setExporting(false);
    }
  };

  const clearAll = () => {
    triggerHaptic('destructiveAction', { enabled: haptic }).catch(() => {});
    setAlertConfig({
      title: 'Clear all photos?',
      message: `This will delete ${photoDays.length} photo(s) permanently.`,
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear All', style: 'destructive', onPress: async () => {
            try {
              await FileSystem.deleteAsync(PHOTO_DIR, { idempotent: true });
              await AsyncStorage.removeItem(PHOTO_INDEX_KEY);
              setPhotoIndex([]);
            } catch (e) { setAlertConfig({ title: 'Error', message: String(e), buttons: [{ text: 'OK', style: 'default' }] }); }
          },
        },
      ],
    });
  };

  return (
    <View style={[ps.container, { backgroundColor: colors.bg }]}>
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 48 }}>
        {/* Month nav */}
        <View style={[ps.monthNav, { borderBottomColor: colors.faint }]}>
          <TouchableOpacity onPress={prevMonth} hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}>
            <Ionicons name="chevron-back" size={22} color={colors.text} />
          </TouchableOpacity>
          <Text style={[ps.monthTitle, { color: colors.text }]}>{MONTH_NAMES[month]} {year}</Text>
          <TouchableOpacity onPress={nextMonth} hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}>
            <Ionicons name="chevron-forward" size={22} color={colors.text} />
          </TouchableOpacity>
        </View>

        {/* Calendar */}
        <CalendarGrid
          year={year}
          month={month}
          photoSet={photoSet}
          onDayPress={onDayPress}
          colors={colors}
        />

        {loading ? (
          <View style={{ alignItems: 'center', paddingVertical: 20 }}>
            <ActivityIndicator color={colors.accent} />
            <Text style={{ color: colors.muted, marginTop: 8, fontSize: 12 }}>Processing photo...</Text>
          </View>
        ) : null}

        {/* Stats + actions */}
        <View style={[ps.statsCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
          <Text style={[ps.statsLabel, { color: colors.muted }]}>STORAGE</Text>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text style={{ color: colors.text, fontSize: 14 }}>
              {photoDays.length} photo{photoDays.length !== 1 ? 's' : ''} · {formatBytes(totalSize)}
            </Text>
          </View>
        </View>

        <TouchableOpacity
          style={[ps.actionBtn, { backgroundColor: colors.accent }]}
          onPress={exportAll}
          disabled={exporting || photoDays.length === 0}>
          <Ionicons name="cloud-download-outline" size={18} color="#fff" />
          <Text style={ps.actionBtnText}>
            {exporting ? 'Exporting...' : 'Export All as ZIP'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[ps.actionBtn, { backgroundColor: 'transparent', borderWidth: 1, borderColor: '#CC2222', marginTop: 8 }]}
          onPress={clearAll}
          disabled={photoDays.length === 0}>
          <Ionicons name="trash-outline" size={18} color="#CC2222" />
          <Text style={[ps.actionBtnText, { color: '#CC2222' }]}>Clear All Photos</Text>
        </TouchableOpacity>

        <Text style={{ color: colors.faint, fontSize: 11, textAlign: 'center', marginTop: 16, letterSpacing: 1 }}>
          Tap a day to add or view a photo
        </Text>
      </ScrollView>

      <PhotoViewer
        visible={showViewer}
        photoDays={photoDays}
        initialDate={viewerDate}
        onClose={() => setShowViewer(false)}
      />
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </View>
  );
}

const ps = StyleSheet.create({
  container: { flex: 1 },
  monthNav: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingBottom: 16, marginBottom: 12, borderBottomWidth: 1 },
  monthTitle: { fontSize: 18, fontWeight: '900', letterSpacing: -0.5 },
  statsCard: { padding: 16, borderWidth: 1, marginVertical: 16 },
  statsLabel: { fontSize: 9, letterSpacing: 3, marginBottom: 8 },
  actionBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, padding: 16 },
  actionBtnText: { color: '#fff', fontWeight: '700', fontSize: 14, letterSpacing: 1 },
});
