
import React, { useState } from 'react';
import { Modal, TextInput, View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FlashList } from '@shopify/flash-list';
import CustomAlert from '../components/CustomAlert';
import Ionicons from 'react-native-vector-icons/Ionicons';
import useWatermelonHistory from '../hooks/useWatermelonHistory';
import { useTheme } from '../context/ThemeContext';
import EmptyState from '../components/ui/EmptyState';
import { getBottomOverlaySpacing } from '../utils/bottomOverlaySpacing';

const CARD_RADIUS = 16;
const CONTROL_RADIUS = 12;

export default function HistoryScreen({ navigation }) {
  const { history, clearHistory, updateHistoryEntry, deleteHistoryEntry } = useWatermelonHistory();
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const bottomSpacing = getBottomOverlaySpacing({
    safeAreaBottom: insets.bottom,
    includeWorkoutPill: true,
    extra: 28,
  });

  const [alertConfig, setAlertConfig] = useState(null);
  const [editingLog, setEditingLog] = useState(null);
  const [editDraft, setEditDraft] = useState({
    date: '',
    time: '',
    durationMinutes: '',
    rating: '',
    summaryText: '',
  });

  const openEditLog = (entry) => {
    const date = new Date(entry?.date || Date.now());
    setEditingLog(entry);
    setEditDraft({
      date: date.toISOString().slice(0, 10),
      time: date.toTimeString().slice(0, 5),
      durationMinutes: String(Math.max(0, Math.round(Number(entry?.duration || 0) / 60))),
      rating: entry?.rating == null ? '' : String(entry.rating),
      summaryText: entry?.summaryText || '',
    });
  };

  const saveEditedLog = async () => {
    if (!editingLog?.id) return;
    const dateRaw = String(editDraft.date || '').trim();
    const timeRaw = String(editDraft.time || '').trim() || '00:00';
    const nextDate = new Date(`${dateRaw}T${timeRaw}:00`);
    if (!dateRaw || Number.isNaN(nextDate.getTime())) {
      setAlertConfig({
        title: 'Invalid date or time',
        message: 'Use date format YYYY-MM-DD and 24-hour time like 18:30.',
        buttons: [{ text: 'OK', style: 'default' }],
      });
      return;
    }
    const durationMinutes = Math.max(0, Math.round(Number(editDraft.durationMinutes || 0)));
    const ratingValue = String(editDraft.rating || '').trim() === ''
      ? null
      : Math.max(1, Math.min(5, Math.round(Number(editDraft.rating || 0))));
    await updateHistoryEntry({
      ...editingLog,
      date: nextDate.toISOString(),
      duration: durationMinutes * 60,
      rating: Number.isFinite(ratingValue) ? ratingValue : null,
      summaryText: String(editDraft.summaryText || '').trim() || null,
    });
    setEditingLog(null);
  };

  const confirmClear = () => setAlertConfig({
    title: 'Clear History?',
    message: 'This cannot be undone.',
    buttons: [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Clear', style: 'destructive', onPress: clearHistory },
    ],
  });

  const renderHistoryCard = ({ item: h, index: i }) => {
    const date = new Date(h.date);
    const dateStr = date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: '2-digit' });
    const timeStr = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
    const dayColors = { push: '#FF4500', pull: '#0080FF', legs: '#00C170', upper: '#A020F0' };
    const color = dayColors[h.dayId] || colors.accent;
    const prev = i < history.length - 1 ? history[i + 1] : null;
    const volume = Math.round(Number(h.totalVolume || 0));
    const prevVolume = Math.round(Number(prev?.totalVolume || 0));
    const volumeDelta = prev ? volume - prevVolume : null;
    return (
      <TouchableOpacity
        activeOpacity={0.85}
        onPress={() => openEditLog(h)}
        style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder, borderLeftColor: color }]}
      >
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <View>
            <Text style={[s.dayName, { color }]}>{h.dayName || h.dayId?.toUpperCase()}</Text>
            <Text style={[s.meta, { color: colors.muted }]}>{h.sets} sets - {Math.round(h.duration / 60)}min</Text>
            <Text style={[s.meta, { color: colors.subtext }]}>
              {volume.toLocaleString()} kg
              {volumeDelta != null ? ` - ${volumeDelta >= 0 ? '+' : ''}${volumeDelta.toLocaleString()} vs previous` : ''}
            </Text>
          </View>
          <View style={{ alignItems: 'flex-end' }}>
            <Text style={[s.date, { color: colors.subtext }]}>{dateStr}</Text>
            <Text style={[s.time, { color: colors.muted }]}>{timeStr}</Text>
          </View>
        </View>
        {h.exercises && h.exercises.length > 0 && (
          <View style={{ marginTop: 8 }}>
            {h.exercises.map((ex, ei) => (
              <Text key={ei} style={[s.exLine, { color: colors.muted }]}>
                {ex.name}: {ex.sets.map(set => (set.weight > 0 ? set.weight + 'kg' : 'BW') + 'x' + set.reps).join(', ')}
              </Text>
            ))}
          </View>
        )}
        <View style={s.editHintRow}>
          <Ionicons name="create-outline" size={13} color={colors.muted} />
          <Text style={[s.editHint, { color: colors.muted }]}>Tap to edit time, duration, rating</Text>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <FlashList
        data={history}
        estimatedItemSize={170}
        keyExtractor={(item, index) => String(item?.id || index)}
        renderItem={renderHistoryCard}
        ListHeaderComponent={
          <>
      {/* Progress Photos shortcut */}
      <TouchableOpacity
        style={[s.photosBtn, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
        onPress={() => navigation.navigate('ProgressPhotos')}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
          <Ionicons name="camera-outline" size={20} color={colors.accent} />
          <Text style={[s.photosBtnText, { color: colors.text }]}>PROGRESS PHOTOS</Text>
        </View>
        <Ionicons name="chevron-forward" size={16} color={colors.muted} />
      </TouchableOpacity>

      {history.length > 0 && (
        <TouchableOpacity style={[s.clearBtn, { borderColor: colors.faint }]} onPress={confirmClear}>
          <Text style={[s.clearText, { color: colors.accent }]}>CLEAR ALL</Text>
        </TouchableOpacity>
      )}
      {history.length === 0 && (
        <EmptyState
          icon="time-outline"
          title="No workouts yet"
          subtitle="Start your first session to see your history here."
          ctaLabel="GO TO TODAY"
          onCta={() => navigation.navigate('Home')}
        />
      )}
          </>
        }
        contentContainerStyle={{ paddingBottom: bottomSpacing }}
        scrollIndicatorInsets={{ bottom: bottomSpacing }}
      />
      <Modal visible={!!editingLog} transparent animationType="fade" onRequestClose={() => setEditingLog(null)}>
        <View style={s.overlay}>
          <View style={[s.editModal, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[s.editTitle, { color: colors.text }]}>EDIT WORKOUT LOG</Text>
            <Text style={[s.editSub, { color: colors.muted }]}>
              Adjust session metadata without changing logged sets.
            </Text>
            <View style={s.editGrid}>
              <View style={s.editField}>
                <Text style={[s.inputLabel, { color: colors.subtext }]}>DATE</Text>
                <TextInput
                  style={[s.input, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
                  value={editDraft.date}
                  onChangeText={(value) => setEditDraft((prev) => ({ ...prev, date: value }))}
                  placeholder="YYYY-MM-DD"
                  placeholderTextColor={colors.muted}
                />
              </View>
              <View style={s.editField}>
                <Text style={[s.inputLabel, { color: colors.subtext }]}>TIME</Text>
                <TextInput
                  style={[s.input, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
                  value={editDraft.time}
                  onChangeText={(value) => setEditDraft((prev) => ({ ...prev, time: value }))}
                  placeholder="18:30"
                  placeholderTextColor={colors.muted}
                  keyboardType="numbers-and-punctuation"
                />
              </View>
            </View>
            <View style={s.editGrid}>
              <View style={s.editField}>
                <Text style={[s.inputLabel, { color: colors.subtext }]}>DURATION MIN</Text>
                <TextInput
                  style={[s.input, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
                  value={editDraft.durationMinutes}
                  onChangeText={(value) => setEditDraft((prev) => ({ ...prev, durationMinutes: value }))}
                  keyboardType="numeric"
                  placeholder="45"
                  placeholderTextColor={colors.muted}
                />
              </View>
              <View style={s.editField}>
                <Text style={[s.inputLabel, { color: colors.subtext }]}>RATING 1-5</Text>
                <TextInput
                  style={[s.input, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
                  value={editDraft.rating}
                  onChangeText={(value) => setEditDraft((prev) => ({ ...prev, rating: value }))}
                  keyboardType="numeric"
                  placeholder="-"
                  placeholderTextColor={colors.muted}
                />
              </View>
            </View>
            <Text style={[s.inputLabel, { color: colors.subtext }]}>SUMMARY NOTE</Text>
            <TextInput
              style={[s.summaryInput, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.bg }]}
              value={editDraft.summaryText}
              onChangeText={(value) => setEditDraft((prev) => ({ ...prev, summaryText: value }))}
              placeholder="Optional session note..."
              placeholderTextColor={colors.muted}
              multiline
            />
            <TouchableOpacity
              style={[s.deleteBtn, { borderColor: 'rgba(255,69,58,0.35)', backgroundColor: 'rgba(255,69,58,0.1)' }]}
              onPress={() => setAlertConfig({
                title: 'Delete this log?',
                message: 'This cannot be undone.',
                buttons: [
                  { text: 'Cancel', style: 'cancel' },
                  {
                    text: 'Delete', style: 'destructive', onPress: async () => {
                      await deleteHistoryEntry(editingLog?.id);
                      setEditingLog(null);
                    },
                  },
                ],
              })}
            >
              <Ionicons name="trash-outline" size={14} color="#FF453A" />
              <Text style={s.deleteBtnText}>DELETE LOG</Text>
            </TouchableOpacity>
            <View style={s.modalActions}>
              <TouchableOpacity style={[s.modalBtn, { borderColor: colors.faint }]} onPress={() => setEditingLog(null)}>
                <Text style={{ color: colors.muted, fontWeight: '800' }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[s.modalBtn, { backgroundColor: colors.accent, borderColor: colors.accent }]} onPress={saveEditedLog}>
                <Text style={{ color: colors.textOnAccent || '#fff', fontWeight: '900' }}>SAVE</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  photosBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', margin: 12, marginBottom: 0, padding: 16, borderWidth: 1, borderRadius: CARD_RADIUS },
  photosBtnText: { fontSize: 13, fontWeight: '800', letterSpacing: 2 },
  clearBtn: { margin: 16, padding: 12, borderWidth: 1, alignItems: 'center', borderRadius: CONTROL_RADIUS },
  clearText: { fontSize: 10, letterSpacing: 3 },
  card: { marginHorizontal: 12, marginTop: 10, padding: 16, borderWidth: 1, borderLeftWidth: 3, borderRadius: CARD_RADIUS },
  dayName: { fontSize: 18, fontWeight: '900', letterSpacing: -0.5 },
  meta: { fontSize: 12, marginTop: 2 },
  date: { fontSize: 13, fontWeight: '700' },
  time: { fontSize: 11, marginTop: 2 },
  exLine: { fontSize: 11, marginTop: 2 },
  editHintRow: { marginTop: 10, flexDirection: 'row', alignItems: 'center', gap: 6 },
  editHint: { fontSize: 10, fontWeight: '700', letterSpacing: 0.5 },
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.84)', justifyContent: 'center', padding: 18 },
  editModal: { borderWidth: 1, borderRadius: 18, padding: 16 },
  editTitle: { fontSize: 16, fontWeight: '900', letterSpacing: 1.5 },
  editSub: { fontSize: 12, lineHeight: 17, marginTop: 4, marginBottom: 12 },
  editGrid: { flexDirection: 'row', gap: 10, marginBottom: 12 },
  editField: { flex: 1 },
  inputLabel: { fontSize: 9, fontWeight: '800', letterSpacing: 1.8, marginBottom: 6 },
  input: { borderWidth: 1, borderRadius: CONTROL_RADIUS, paddingHorizontal: 12, paddingVertical: 10, fontSize: 14, fontWeight: '700' },
  summaryInput: { borderWidth: 1, borderRadius: CONTROL_RADIUS, minHeight: 74, padding: 12, textAlignVertical: 'top', fontSize: 13 },
  deleteBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, borderWidth: 1, borderRadius: CONTROL_RADIUS, paddingVertical: 11, marginTop: 14 },
  deleteBtnText: { fontSize: 12, fontWeight: '800', letterSpacing: 1, color: '#FF453A' },
  modalActions: { flexDirection: 'row', gap: 10, marginTop: 10 },
  modalBtn: { flex: 1, borderWidth: 1, borderRadius: CONTROL_RADIUS, paddingVertical: 13, alignItems: 'center' },
  emptyWrap: { margin: 20, borderWidth: 1, borderStyle: 'dashed', padding: 24, alignItems: 'center', borderRadius: CARD_RADIUS },
  emptyBtn: { borderWidth: 1, paddingHorizontal: 12, paddingVertical: 8, borderRadius: CONTROL_RADIUS },
  emptyBtnText: { fontSize: 10, fontWeight: '800', letterSpacing: 1.1 },
});

