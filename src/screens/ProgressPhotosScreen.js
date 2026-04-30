
import React, { useState, useEffect, useContext, useRef } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, Modal,
  FlatList, ScrollView, Dimensions, ActivityIndicator,
  Linking, PanResponder,
} from 'react-native';
import * as FileSystem from '../platform/filesystem';
import * as ImagePicker from '../platform/imagePicker';
import * as ImageManipulator from '../platform/imageManipulator';
import * as Sharing from '../platform/sharing';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { Image } from 'react-native';
import JSZip from 'jszip';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import useWatermelonSettings from '../hooks/useWatermelonSettings';
import { useTheme } from '../context/ThemeContext';
import CustomAlert from '../components/CustomAlert';
import EmptyState from '../components/ui/EmptyState';
import { fireHaptic } from '../services/hapticsEngine';
import { getBottomOverlaySpacing } from '../utils/bottomOverlaySpacing';
import { RADIUS } from '../utils/themes';
import {
  clearProgressPhotos,
  getProgressPhotosObservable,
  upsertProgressPhotoByDate,
} from '../db/repositories/progressPhotoRepository';

const PHOTO_DIR = FileSystem.documentDirectory + 'progress-photos/';
const DAYS_OF_WEEK = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];
const { width: SCREEN_W } = Dimensions.get('window');

// --- Storage helpers ----------------------------------------------------------

async function ensurePhotoDir() {
  const info = await FileSystem.getInfoAsync(PHOTO_DIR);
  if (!info.exists) await FileSystem.makeDirectoryAsync(PHOTO_DIR, { intermediates: true });
}

function formatBytes(bytes) {
  if (!bytes) return '0 KB';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// --- Calendar grid ------------------------------------------------------------

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
                activeOpacity={0.85}>
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
  cell: { alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderRadius: RADIUS.xs, margin: 1, paddingVertical: 6 },
  dayNum: { fontSize: 13 },
  dot: { width: 5, height: 5, borderRadius: 3, marginTop: 3 },
});

// --- Full-screen photo viewer -------------------------------------------------

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

// --- Compare slider -----------------------------------------------------------
const SLIDER_W = SCREEN_W - 56; // overlay pad 14 + card pad 14 each side
const SLIDER_H = Math.round(SLIDER_W * 1.3);

function CompareSlider({ uriA, dateA, uriB, dateB }) {
  const [divX, setDivX] = React.useState(SLIDER_W / 2);
  const panResponder = React.useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderMove: (_, gesture) => {
        setDivX(Math.max(6, Math.min(SLIDER_W - 6, gesture.moveX)));
      },
    })
  ).current;

  return (
    <View style={{ width: SLIDER_W, height: SLIDER_H, overflow: 'hidden', borderRadius: 10 }}>
      {/* Photo A — fills full width */}
      <Image source={{ uri: uriA }} style={{ position: 'absolute', width: SLIDER_W, height: SLIDER_H, resizeMode: 'cover' }} />
      {/* Photo B — clipped to right of divider */}
      <View style={{ position: 'absolute', left: divX, right: 0, top: 0, bottom: 0, overflow: 'hidden' }}>
        <Image source={{ uri: uriB }} style={{ position: 'absolute', right: 0, width: SLIDER_W, height: SLIDER_H, resizeMode: 'cover' }} />
      </View>
      {/* Divider line + handle */}
      <View
        style={[cs.dividerBar, { left: divX - 1 }]}
        {...panResponder.panHandlers}
      >
        <View style={cs.dividerHandle}>
          <View style={cs.dividerArrowLeft} />
          <View style={cs.dividerArrowRight} />
        </View>
      </View>
      {/* Date labels */}
      <View style={[cs.datePill, { left: 8 }]}>
        <Text style={cs.datePillText}>{dateA}</Text>
      </View>
      <View style={[cs.datePill, { right: 8 }]}>
        <Text style={cs.datePillText}>{dateB}</Text>
      </View>
    </View>
  );
}
const cs = StyleSheet.create({
  dividerBar: { position: 'absolute', top: 0, bottom: 0, width: 2, backgroundColor: 'rgba(255,255,255,0.85)', alignItems: 'center', justifyContent: 'center' },
  dividerHandle: { width: 34, height: 34, borderRadius: 17, backgroundColor: 'rgba(255,255,255,0.95)', alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 2, elevation: 4 },
  dividerArrowLeft: { width: 0, height: 0, borderTopWidth: 5, borderBottomWidth: 5, borderRightWidth: 7, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderRightColor: '#555' },
  dividerArrowRight: { width: 0, height: 0, borderTopWidth: 5, borderBottomWidth: 5, borderLeftWidth: 7, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderLeftColor: '#555' },
  datePill: { position: 'absolute', top: 8, backgroundColor: 'rgba(0,0,0,0.55)', paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  datePillText: { color: '#fff', fontSize: 10, fontWeight: '700', letterSpacing: 0.5 },
});

// --- Main Screen --------------------------------------------------------------

export default function ProgressPhotosScreen() {
  const { settings } = useWatermelonSettings();
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;
  const insets = useSafeAreaInsets();

  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth());
  const [photoIndex, setPhotoIndex] = useState([]);
  const [loading, setLoading] = useState(false);
  const [viewerDate, setViewerDate] = useState(null);
  const [showViewer, setShowViewer] = useState(false);
  const [showCompareModal, setShowCompareModal] = useState(false);
  const [compareA, setCompareA] = useState(null);
  const [compareB, setCompareB] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);

  const photoDays = photoIndex.filter(p => p.filename);
  const photoSet = new Set(photoIndex.map(p => p.date));
  const totalSize = photoIndex.reduce((a, p) => a + (p.sizeBytes || 0), 0);

  useEffect(() => {
    const sub = getProgressPhotosObservable().subscribe({
      next: (rows) => {
        const mapped = (rows || [])
          .map((row) => {
            const date = new Date(Number(row.takenAt) || Date.now()).toISOString().slice(0, 10);
            const uri = String(row.fileUri || '');
            const filename = uri.startsWith(PHOTO_DIR) ? uri.slice(PHOTO_DIR.length) : uri.split('/').pop();
            return { id: row.id, date, filename, sizeBytes: 0, uri };
          })
          .sort((a, b) => a.date.localeCompare(b.date));
        setPhotoIndex(mapped);
      },
      error: (error) => {
        setAlertConfig({ title: 'Load failed', message: String(error), buttons: [{ text: 'OK', style: 'default' }] });
      },
    });
    return () => sub.unsubscribe();
  }, []);

  useEffect(() => {
    if (!photoDays.length) {
      setCompareA(null);
      setCompareB(null);
      return;
    }
    if (!compareA) setCompareA(photoDays[0]?.date || null);
    if (!compareB) setCompareB(photoDays[Math.min(1, photoDays.length - 1)]?.date || photoDays[0]?.date || null);
  }, [compareA, compareB, photoDays]);

  const prevMonth = () => {
    if (month === 0) { setYear(y => y - 1); setMonth(11); }
    else setMonth(m => m - 1);
  };
  const nextMonth = () => {
    if (month === 11) { setYear(y => y + 1); setMonth(0); }
    else setMonth(m => m + 1);
  };

  const openPermissionSettings = () => {
    Linking.openSettings().catch(() => {
      setAlertConfig({
        title: 'Open settings failed',
        message: 'Open Android app settings manually and allow camera/photos permission for IRONLOG.',
        buttons: [{ text: 'OK', style: 'default' }],
      });
    });
  };

  const pickPhoto = async (date, source) => {
    try {
      let result;
      if (source === 'camera') {
        const perm = await ImagePicker.requestCameraPermissionsAsync();
        if (!perm.granted) {
          setAlertConfig({
            title: 'Camera permission needed',
            message: 'Allow camera access to take a progress photo, or choose one from your gallery instead.',
            buttons: [
              { text: 'Open Settings', style: 'default', onPress: openPermissionSettings },
              { text: 'Choose Gallery', style: 'default', onPress: () => pickPhoto(date, 'gallery') },
              { text: 'Cancel', style: 'cancel' },
            ],
          });
          return;
        }
        result = await ImagePicker.launchCameraAsync({ quality: 1 });
      } else {
        const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
        if (!perm.granted) {
          setAlertConfig({
            title: 'Gallery permission needed',
            message: 'Allow photo library access to select an existing progress photo.',
            buttons: [
              { text: 'Open Settings', style: 'default', onPress: openPermissionSettings },
              { text: 'Cancel', style: 'cancel' },
            ],
          });
          return;
        }
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

      await upsertProgressPhotoByDate({
        fileUri: destUri,
        takenAt: new Date(`${date}T12:00:00`).getTime(),
        notes: '',
      });
      fireHaptic('selection', { enabled: haptic });
    } catch (e) {
      setAlertConfig({ title: 'Error saving photo', message: String(e), buttons: [{ text: 'OK', style: 'default' }] });
    } finally {
      setLoading(false);
    }
  };

  const promptAddPhotoForToday = () => {
    const todayDate = new Date().toISOString().split('T')[0];
    setAlertConfig({
      title: 'Take first photo',
      message: todayDate,
      buttons: [
        { text: 'Camera', style: 'default', onPress: () => pickPhoto(todayDate, 'camera') },
        { text: 'Gallery', style: 'default', onPress: () => pickPhoto(todayDate, 'gallery') },
        { text: 'Cancel', style: 'cancel' },
      ],
    });
  };

  const onDayPress = (cell) => {
    fireHaptic('selection', { enabled: haptic });
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
    fireHaptic('destructiveAction', { enabled: haptic });
    setAlertConfig({
      title: 'Clear all photos?',
      message: `This will delete ${photoDays.length} photo(s) permanently.`,
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear All', style: 'destructive', onPress: async () => {
            try {
              await FileSystem.deleteAsync(PHOTO_DIR, { idempotent: true });
              await clearProgressPhotos();
              setPhotoIndex([]);
            } catch (e) { setAlertConfig({ title: 'Error', message: String(e), buttons: [{ text: 'OK', style: 'default' }] }); }
          },
        },
      ],
    });
  };

  const stepCompareDate = (value, direction, setter) => {
    if (!photoDays.length) return;
    const index = Math.max(0, photoDays.findIndex((entry) => entry.date === value));
    const next = Math.max(0, Math.min(photoDays.length - 1, index + direction));
    setter(photoDays[next]?.date || value);
  };

  const comparePhotoA = photoDays.find((entry) => entry.date === compareA) || null;
  const comparePhotoB = photoDays.find((entry) => entry.date === compareB) || null;
  const bottomSpacing = getBottomOverlaySpacing({
    safeAreaBottom: insets.bottom,
    includeWorkoutPill: true,
    extra: 8,
  });

  return (
    <View style={[ps.container, { backgroundColor: colors.bg }]}>
      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: bottomSpacing }}
        scrollIndicatorInsets={{ bottom: bottomSpacing }}>
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

        {photoDays.length === 0 && !loading ? (
          <EmptyState
            icon="camera-outline"
            title="No progress photos"
            subtitle="Take your first photo to start tracking changes over time."
            ctaLabel="TAKE PHOTO"
            onCta={promptAddPhotoForToday}
          />
        ) : null}

        <TouchableOpacity
          style={[ps.comparePrimaryBtn, { backgroundColor: colors.accent }]}
          disabled={photoDays.length < 2}
          onPress={() => setShowCompareModal(true)}>
          <Ionicons name="git-compare-outline" size={18} color="#fff" />
          <Text style={ps.actionBtnText}>
            {photoDays.length < 2 ? 'Add one more photo to compare' : 'Compare Latest Photos'}
          </Text>
        </TouchableOpacity>

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

        <TouchableOpacity style={{ paddingVertical: 10, alignItems: 'center' }} onPress={clearAll} disabled={photoDays.length === 0}>
          <Text style={{ color: '#ff4444', fontSize: 13, fontWeight: '600' }}>Clear All Photos</Text>
        </TouchableOpacity>


        <View style={[ps.statsCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
          <Text style={[ps.statsLabel, { color: colors.muted }]}>COMPARE WORKFLOW</Text>
          <Text style={[ps.compareHint, { color: colors.subtext }]}>
            Keep pose, distance, lighting, and camera angle consistent for meaningful comparisons.
          </Text>
          <TouchableOpacity
            style={[ps.compareBtn, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
            disabled={photoDays.length < 2}
            onPress={() => setShowCompareModal(true)}
          >
            <Ionicons name="git-compare-outline" size={16} color={colors.text} />
            <Text style={[ps.compareBtnText, { color: colors.text }]}>Open side-by-side compare</Text>
          </TouchableOpacity>
          {photoDays.length < 2 ? (
            <Text style={[ps.compareHint, { color: colors.muted }]}>Add at least two photos to use compare mode.</Text>
          ) : null}
        </View>

        <View style={[ps.statsCard, { backgroundColor: colors.card, borderColor: colors.cardBorder, marginTop: 0 }]}>
          <Text style={[ps.statsLabel, { color: colors.muted }]}>PRIVACY & CLEANUP</Text>
          <Text style={[ps.compareHint, { color: colors.subtext }]}>
            Photos stay on-device unless you export. Use `Clear All Photos` to remove local photo data permanently.
          </Text>
        </View>

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
      <Modal visible={showCompareModal} transparent animationType="fade" onRequestClose={() => setShowCompareModal(false)}>
        <View style={ps.compareOverlay}>
          <View style={[ps.compareCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[ps.compareTitle, { color: colors.text }]}>COMPARE PHOTOS</Text>
            <Text style={[ps.compareHint, { color: colors.muted }]}>Drag the divider left or right to reveal each photo.</Text>
            {comparePhotoA && comparePhotoB ? (
              <View style={{ marginTop: 10 }}>
                <CompareSlider
                  uriA={PHOTO_DIR + comparePhotoA.filename}
                  dateA={compareA}
                  uriB={PHOTO_DIR + comparePhotoB.filename}
                  dateB={compareB}
                />
              </View>
            ) : null}
            <View style={[ps.compareRow, { marginTop: 12 }]}>
              <View style={ps.compareColumn}>
                <Text style={[ps.compareLabel, { color: colors.subtext }]}>PHOTO A</Text>
                <View style={ps.compareControls}>
                  <TouchableOpacity onPress={() => stepCompareDate(compareA, -1, setCompareA)} style={[ps.compareArrow, { borderColor: colors.faint }]}>
                    <Ionicons name="chevron-back" size={16} color={colors.text} />
                  </TouchableOpacity>
                  <Text style={[ps.compareDate, { color: colors.text }]}>{compareA || '--'}</Text>
                  <TouchableOpacity onPress={() => stepCompareDate(compareA, 1, setCompareA)} style={[ps.compareArrow, { borderColor: colors.faint }]}>
                    <Ionicons name="chevron-forward" size={16} color={colors.text} />
                  </TouchableOpacity>
                </View>
              </View>
              <View style={ps.compareColumn}>
                <Text style={[ps.compareLabel, { color: colors.subtext }]}>PHOTO B</Text>
                <View style={ps.compareControls}>
                  <TouchableOpacity onPress={() => stepCompareDate(compareB, -1, setCompareB)} style={[ps.compareArrow, { borderColor: colors.faint }]}>
                    <Ionicons name="chevron-back" size={16} color={colors.text} />
                  </TouchableOpacity>
                  <Text style={[ps.compareDate, { color: colors.text }]}>{compareB || '--'}</Text>
                  <TouchableOpacity onPress={() => stepCompareDate(compareB, 1, setCompareB)} style={[ps.compareArrow, { borderColor: colors.faint }]}>
                    <Ionicons name="chevron-forward" size={16} color={colors.text} />
                  </TouchableOpacity>
                </View>
              </View>
            </View>
            <TouchableOpacity style={[ps.actionBtn, { backgroundColor: colors.accent, marginTop: 10 }]} onPress={() => setShowCompareModal(false)}>
              <Text style={ps.actionBtnText}>Done</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </View>
  );
}

const ps = StyleSheet.create({
  container: { flex: 1 },
  monthNav: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingBottom: 16, marginBottom: 12, borderBottomWidth: 1 },
  monthTitle: { fontSize: 18, fontWeight: '900', letterSpacing: -0.5 },
  statsCard: { padding: 16, borderWidth: 1, marginVertical: 16, borderRadius: RADIUS.md },
  statsLabel: { fontSize: 9, letterSpacing: 3, marginBottom: 8 },
  actionBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, padding: 16, borderRadius: RADIUS.md },
  comparePrimaryBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, padding: 14, borderRadius: RADIUS.md, marginTop: 8 },
  actionBtnText: { color: '#fff', fontWeight: '700', fontSize: 14, letterSpacing: 1 },
  compareBtn: { marginTop: 10, borderWidth: 1, borderRadius: RADIUS.sm, paddingVertical: 10, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8 },
  compareBtnText: { fontSize: 12, fontWeight: '700' },
  compareHint: { fontSize: 12, lineHeight: 17, marginTop: 4 },
  compareOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.86)', justifyContent: 'center', padding: 14 },
  compareCard: { borderWidth: 1, borderRadius: RADIUS.md, padding: 14 },
  compareTitle: { fontSize: 14, fontWeight: '900', letterSpacing: 1.1 },
  compareRow: { flexDirection: 'row', gap: 10, marginTop: 12 },
  compareColumn: { flex: 1 },
  compareLabel: { fontSize: 10, letterSpacing: 1.1, marginBottom: 6 },
  compareImage: { width: '100%', height: 210, borderRadius: RADIUS.sm, resizeMode: 'cover' },
  compareControls: { marginTop: 8, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  compareArrow: { borderWidth: 1, borderRadius: 999, width: 34, height: 34, alignItems: 'center', justifyContent: 'center' },
  compareDate: { flex: 1, textAlign: 'center', fontSize: 11, fontWeight: '700' },
});

