import React, { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import ReAnimated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useTheme } from '../context/ThemeContext';
import { withAlpha } from '../utils/colorUtils';
import { RADIUS, TYPE } from '../utils/themes';
import { haptic } from '../services/hapticsEngine';

function formatWeight(value, unit) {
  const n = Number(value || 0);
  if (!Number.isFinite(n)) return `0 ${unit}`;
  const label = Math.abs(n % 1) > 0.01 ? n.toFixed(1) : String(Math.round(n));
  return `${label} ${unit}`;
}

export default function PRHud({ pr, onDismiss }) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const translateY = useSharedValue(-90);
  const opacity = useSharedValue(0);
  const trophyScale = useSharedValue(0.5);
  const trophyRotate = useSharedValue(-14);
  const progress = useSharedValue(1);
  const [displayWeight, setDisplayWeight] = useState(pr?.prev || 0);
  const intervalRef = useRef(null);
  const dismissRef = useRef(null);

  useEffect(() => {
    clearInterval(intervalRef.current);
    clearTimeout(dismissRef.current);

    if (!pr) {
      opacity.value = withTiming(0, { duration: 120 });
      translateY.value = withTiming(-90, { duration: 160 });
      progress.value = 1;
      return undefined;
    }

    haptic('prUnlocked');
    setDisplayWeight(pr.prev || 0);
    opacity.value = withTiming(1, { duration: 140 });
    translateY.value = withSpring(0, { stiffness: 260, damping: 22, mass: 0.75 });
    trophyScale.value = 0.5;
    trophyRotate.value = -14;
    trophyScale.value = withDelay(220, withSpring(1, { stiffness: 320, damping: 18, mass: 0.6 }));
    trophyRotate.value = withDelay(220, withSpring(0, { stiffness: 280, damping: 18, mass: 0.6 }));
    progress.value = 1;
    progress.value = withTiming(0, { duration: 3800, easing: Easing.linear });

    const start = Number(pr.prev || 0);
    const end = Number(pr.weight || 0);
    let step = 0;
    intervalRef.current = setInterval(() => {
      step += 1;
      const ratio = Math.min(1, step / 8);
      setDisplayWeight(start + (end - start) * ratio);
      if (ratio >= 1) clearInterval(intervalRef.current);
    }, 60);

    dismissRef.current = setTimeout(() => onDismiss?.(), 3900);
    return () => {
      clearInterval(intervalRef.current);
      clearTimeout(dismissRef.current);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pr]);

  const wrapStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }],
  }));

  const trophyStyle = useAnimatedStyle(() => ({
    transform: [
      { scale: trophyScale.value },
      { rotate: `${trophyRotate.value}deg` },
    ],
  }));

  const progressStyle = useAnimatedStyle(() => ({
    width: `${Math.max(0, Math.min(1, progress.value)) * 100}%`,
  }));

  if (!pr) return null;

  const accent = colors.accent || '#FF4500';
  const unit = pr.unit || 'kg';

  return (
    <ReAnimated.View
      pointerEvents="box-none"
      style={[s.safeLayer, { top: insets.top + 8 }, wrapStyle]}
    >
      <Pressable
        onPress={onDismiss}
        style={[
          s.card,
          {
            backgroundColor: colors.surface || colors.card,
            borderColor: withAlpha(accent, 0.4),
            shadowColor: typeof accent === 'string' ? accent : '#000',
          },
        ]}
      >
        <View style={s.row}>
          <ReAnimated.Text style={[s.trophy, trophyStyle]}>{'\uD83C\uDFC6'}</ReAnimated.Text>
          <View style={s.copy}>
            <Text style={[s.kicker, { color: accent }]}>NEW PERSONAL BEST</Text>
            <Text style={[s.exercise, { color: colors.text }]} numberOfLines={1}>{pr.exercise}</Text>
            {pr.isBodyweight && pr.addedWeight > 0 ? (
              <Text style={{ fontSize: 10, color: colors.muted, marginBottom: 2 }}>
                {`BW + ${pr.addedWeight % 1 > 0.01 ? Number(pr.addedWeight).toFixed(1) : Math.round(pr.addedWeight)} ${unit}`}
              </Text>
            ) : null}
            <View style={s.metricRow}>
              <Text style={[s.weight, { color: colors.text }]}>{formatWeight(displayWeight, unit)}</Text>
              {pr.delta ? <Text style={[s.delta, { color: accent }]}>{pr.delta}</Text> : null}
            </View>
          </View>
        </View>
        <View style={[s.progressTrack, { backgroundColor: withAlpha(accent, 0.16) }]}>
          <ReAnimated.View style={[s.progressFill, { backgroundColor: accent }, progressStyle]} />
        </View>
      </Pressable>
    </ReAnimated.View>
  );
}

const s = StyleSheet.create({
  safeLayer: {
    position: 'absolute',
    left: 14,
    right: 14,
    zIndex: 9999,
    elevation: 9999,
  },
  card: {
    borderWidth: 1,
    borderRadius: RADIUS.lg,
    padding: 14,
    overflow: 'hidden',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.22,
    shadowRadius: 18,
    elevation: 14,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  trophy: {
    fontSize: 32,
    width: 42,
    textAlign: 'center',
  },
  copy: {
    flex: 1,
    minWidth: 0,
  },
  kicker: {
    ...TYPE.eyebrow,
    letterSpacing: 1.9,
  },
  exercise: {
    ...TYPE.section,
    marginTop: 2,
  },
  metricRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 10,
    marginTop: 2,
  },
  weight: {
    ...TYPE.title,
  },
  delta: {
    ...TYPE.button,
  },
  progressTrack: {
    height: 3,
    borderRadius: RADIUS.full,
    marginTop: 12,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: RADIUS.full,
  },
});
