
import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useActiveBanner } from '../context/ActiveWorkoutBannerContext';
import { useTheme } from '../context/ThemeContext';
import { triggerHaptic } from '../services/hapticsEngine';

function formatElapsed(ms) {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const h = Math.floor(m / 60);
  if (h > 0) return `${h}:${String(m % 60).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

export default function FloatingWorkoutWidget({ navigation }) {
  const { banner } = useActiveBanner();
  const colors = useTheme();
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!banner || !banner.startTime) {
      setElapsed(0);
      return;
    }
    const interval = setInterval(() => {
      setElapsed(Date.now() - banner.startTime);
    }, 1000);
    return () => clearInterval(interval);
  }, [banner]);

  if (!banner) return null;

  const timerDisplay = banner.startTime ? formatElapsed(elapsed) : '--:--';

  return (
    <TouchableOpacity
      style={[s.pill, { backgroundColor: colors.accent }]}
      onPress={() => {
        triggerHaptic('selection').catch(() => {});
        navigation.navigate('ActiveWorkout', { planIndex: banner.planIndex, dayIndex: banner.dayIndex });
      }}
      activeOpacity={0.85}>
      <View style={s.left}>
        <Ionicons name="barbell-outline" size={16} color={colors.textOnAccent || '#fff'} />
        <View>
          <Text style={[s.name, { color: colors.textOnAccent || '#fff' }]} numberOfLines={1}>{banner.dayName}</Text>
          <Text style={[s.sub, { color: colors.textOnAccent || 'rgba(255,255,255,0.8)' }]}>WORKOUT IN PROGRESS</Text>
        </View>
      </View>
      <View style={s.right}>
        <Text style={[s.timer, { color: colors.textOnAccent || '#fff' }]}>{timerDisplay}</Text>
        <Ionicons name="chevron-forward" size={16} color={colors.textOnAccent || '#fff'} style={{ opacity: 0.7 }} />
      </View>
    </TouchableOpacity>
  );
}

const s = StyleSheet.create({
  pill: {
    marginHorizontal: 12,
    marginBottom: 8,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  left: { flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1 },
  name: { color: '#fff', fontWeight: '800', fontSize: 14, letterSpacing: 0.5 },
  sub: { color: 'rgba(255,255,255,0.75)', fontSize: 9, letterSpacing: 2, marginTop: 1 },
  right: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  timer: { color: '#fff', fontWeight: '900', fontSize: 16, letterSpacing: 1 },
});
