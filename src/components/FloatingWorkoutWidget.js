
import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import ReAnimated, {
  useSharedValue,
  withSpring,
  withTiming,
  useAnimatedStyle,
} from 'react-native-reanimated';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useActiveBanner } from '../context/ActiveWorkoutBannerContext';
import { useTheme } from '../context/ThemeContext';
import { haptic } from '../services/hapticsEngine';

function formatElapsed(ms) {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const h = Math.floor(m / 60);
  if (h > 0) return `${h}:${String(m % 60).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

function FloatingWorkoutWidget({ navigation }) {
  const { banner } = useActiveBanner();
  const colors = useTheme();
  const [elapsed, setElapsed] = useState(0);

  const scaleAnim   = useSharedValue(banner ? 1 : 0.6);
  const opacityAnim = useSharedValue(banner ? 1 : 0);

  useEffect(() => {
    if (banner) {
      // Pop in with a snappy spring matching the screen shrink timing (~250ms)
      scaleAnim.value   = withSpring(1, { stiffness: 480, damping: 22, mass: 0.6 });
      opacityAnim.value = withTiming(1, { duration: 200 });
    } else {
      scaleAnim.value   = withTiming(0.6, { duration: 140 });
      opacityAnim.value = withTiming(0,   { duration: 140 });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [!!banner]);

  const wrapStyle = useAnimatedStyle(() => ({
    opacity: opacityAnim.value,
    transform: [{ scale: scaleAnim.value }],
  }));

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
    <ReAnimated.View style={wrapStyle}>
      <TouchableOpacity
        style={[s.pill, {
          backgroundColor: colors.accent,
          elevation: 8,
          shadowColor: colors.accent,
          shadowOffset: { width: 0, height: 3 },
          shadowOpacity: 0.35,
          shadowRadius: 10,
        }]}
        onPress={() => {
        haptic('selection');
          navigation.navigate('ActiveWorkout', { planIndex: banner.planIndex, dayIndex: banner.dayIndex });
        }}
        activeOpacity={0.85}
      >
        <View style={s.left}>
          <Ionicons name="barbell-outline" size={13} color="rgba(255,255,255,0.9)" />
          <View>
            <Text style={s.name} numberOfLines={1}>{banner.dayName}</Text>
            <Text style={s.sub}>IN PROGRESS</Text>
          </View>
        </View>
        <View style={s.right}>
          <Text style={s.timer}>{timerDisplay}</Text>
          <Ionicons name="chevron-forward" size={12} color="rgba(255,255,255,0.65)" />
        </View>
      </TouchableOpacity>
    </ReAnimated.View>
  );
}

export default React.memo(FloatingWorkoutWidget);

const s = StyleSheet.create({
  pill: {
    marginBottom: 2,
    alignSelf: 'center',
    width: '82%',
    maxWidth: 304,
    minWidth: 220,
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  left: { flexDirection: 'row', alignItems: 'center', gap: 7, flex: 1, minWidth: 0 },
  name: { color: '#fff', fontWeight: '800', fontSize: 11, letterSpacing: 0.5 },
  sub: { color: 'rgba(255,255,255,0.68)', fontSize: 8, letterSpacing: 1.5, marginTop: 1, fontWeight: '700' },
  right: { flexDirection: 'row', alignItems: 'center', gap: 4, marginLeft: 10 },
  timer: { color: '#fff', fontWeight: '900', fontSize: 12, letterSpacing: 0.5, minWidth: 44, textAlign: 'right' },
});
