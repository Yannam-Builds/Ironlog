import React, { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import ReAnimated, { useSharedValue, withTiming, useAnimatedStyle } from 'react-native-reanimated';
import { useTheme } from '../../context/ThemeContext';
import { withAlpha } from '../../utils/colorUtils';
import { RADIUS } from '../../utils/themes';

export default function InlineToast({ visible, text, intent = 'default' }) {
  const colors = useTheme();
  const opacity = useSharedValue(0);
  const translateY = useSharedValue(6);

  useEffect(() => {
    opacity.value = withTiming(visible ? 1 : 0, { duration: visible ? 140 : 120 });
    translateY.value = withTiming(visible ? 0 : 6, { duration: visible ? 140 : 120 });
  }, [visible]); // eslint-disable-line

  const animStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }],
  }));

  // Always resolve to a hex string so we can safely do string-alpha ops.
  // colors.accent may be a PlatformColor object on Monet - fall back to a safe orange.
  const accentRaw = intent === 'success' ? '#30D158'
                  : intent === 'warning' ? '#FF9500'
                  : intent === 'error'   ? '#FF453A'
                  : colors.accent;
  const accent = typeof accentRaw === 'string' ? accentRaw : '#FF4500';

  return (
    <ReAnimated.View
      pointerEvents="none"
      style={[
        s.wrap,
        { borderColor: withAlpha(accent, 0.4), backgroundColor: withAlpha(accent, 0.1) },
        animStyle,
      ]}
    >
      <View style={[s.dot, { backgroundColor: accent }]} />
      <Text style={[s.text, { color: colors.text }]} numberOfLines={1}>{text}</Text>
    </ReAnimated.View>
  );
}

const s = StyleSheet.create({
  wrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1,
    borderRadius: RADIUS.sm,
    paddingVertical: 8,
    paddingHorizontal: 10,
    marginHorizontal: 16,
    marginBottom: 6,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  text: {
    flex: 1,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
});

