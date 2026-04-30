import React, { useEffect } from 'react';
import { View } from 'react-native';
import ReAnimated, { useSharedValue, withRepeat, withTiming, useAnimatedStyle, Easing, interpolate } from 'react-native-reanimated';
import { useTheme } from '../../context/ThemeContext';
import { withAlpha } from '../../utils/colorUtils';
import { RADIUS } from '../../utils/themes';

export default function Skeleton({ width = '100%', height = 14, borderRadius = RADIUS.xs, style }) {
  const colors = useTheme();
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = withRepeat(
      withTiming(1, { duration: 1200, easing: Easing.inOut(Easing.ease) }),
      -1, true
    );
  }, []); // eslint-disable-line

  const animStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 1], [0.4, 0.8]),
  }));

  return (
    <ReAnimated.View style={[
      { width, height, borderRadius, backgroundColor: withAlpha(colors.muted, 0.15) },
      animStyle, style,
    ]} />
  );
}

export function SkeletonCard({ style }) {
  const colors = useTheme();
  return (
    <View style={[{
      backgroundColor: colors.card, borderColor: colors.cardBorder, borderWidth: 1,
      borderRadius: RADIUS.lg, padding: 16, gap: 10,
    }, style]}>
      <Skeleton width="40%" height={10} />
      <Skeleton width="85%" height={18} />
      <Skeleton width="70%" height={14} />
    </View>
  );
}
