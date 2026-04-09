
import React, { useContext, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import CustomAlert from '../components/CustomAlert';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';

export default function HistoryScreen({ navigation }) {
  const { history, clearHistory } = useContext(AppContext);
  const colors = useTheme();

  const [alertConfig, setAlertConfig] = useState(null);
  const confirmClear = () => setAlertConfig({
    title: 'Clear History?',
    message: 'This cannot be undone.',
    buttons: [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Clear', style: 'destructive', onPress: clearHistory },
    ],
  });

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ paddingBottom: 40 }}>
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
        <View style={{ padding: 40, alignItems: 'center' }}>
          <Text style={{ color: colors.muted, fontSize: 14 }}>No workouts logged yet.</Text>
        </View>
      )}
      {history.map((h, i) => {
        const date = new Date(h.date);
        const dateStr = date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: '2-digit' });
        const timeStr = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
        const dayColors = { push: '#FF4500', pull: '#0080FF', legs: '#00C170', upper: '#A020F0' };
        const color = dayColors[h.dayId] || colors.accent;
        return (
          <View key={h.id || i} style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder, borderLeftColor: color }]}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <View>
                <Text style={[s.dayName, { color }]}>{h.dayName || h.dayId?.toUpperCase()}</Text>
                <Text style={[s.meta, { color: colors.muted }]}>{h.sets} sets · {Math.round(h.duration / 60)}min</Text>
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
          </View>
        );
      })}
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  photosBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', margin: 12, marginBottom: 0, padding: 16, borderWidth: 1 },
  photosBtnText: { fontSize: 13, fontWeight: '800', letterSpacing: 2 },
  clearBtn: { margin: 16, padding: 12, borderWidth: 1, alignItems: 'center' },
  clearText: { fontSize: 10, letterSpacing: 3 },
  card: { marginHorizontal: 12, marginTop: 10, padding: 16, borderWidth: 1, borderLeftWidth: 3 },
  dayName: { fontSize: 18, fontWeight: '900', letterSpacing: -0.5 },
  meta: { fontSize: 12, marginTop: 2 },
  date: { fontSize: 13, fontWeight: '700' },
  time: { fontSize: 11, marginTop: 2 },
  exLine: { fontSize: 11, marginTop: 2 },
});
