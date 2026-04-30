import React, { useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import ReAnimated, {
  useSharedValue,
  withSpring,
  useAnimatedStyle,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../context/ThemeContext';

const ICON_ONLY = new Set(['Settings']);

function isHex(color) {
  return typeof color === 'string' && /^#([0-9a-f]{6}|[0-9a-f]{8})$/i.test(color);
}

function withAlpha(color, alpha, fallback) {
  if (!isHex(color)) return fallback ?? color;
  const hex = color.slice(1, 7);
  const channel = Math.round(Math.max(0, Math.min(1, alpha)) * 255)
    .toString(16)
    .padStart(2, '0');
  return `#${hex}${channel}`;
}

function pillGlass(colors) {
  return colors.surface || colors.card || 'rgba(32,32,34,0.93)';
}

function labelColor(colors, focused) {
  return focused ? (colors.accent || colors.text) : (colors.muted || colors.text);
}

export default function PremiumFrostedTabBar({ state, descriptors, navigation }) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const [barWidth, setBarWidth] = useState(0);
  const activeX = useSharedValue(0);

  const tabWidth = useMemo(() => {
    if (!barWidth || !state.routes.length) return 0;
    return barWidth / state.routes.length;
  }, [barWidth, state.routes.length]);

  useEffect(() => {
    if (!tabWidth) return;
    activeX.value = withSpring(state.index * tabWidth, {
      stiffness: 300,
      damping: 28,
      mass: 0.4,
      overshootClamping: false,
    });
  }, [state.index, tabWidth]); // eslint-disable-line

  const sliderAnimStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: activeX.value }],
  }));

  const accent = colors.accent || '#FF4500';
  const glass = pillGlass(colors);
  const accentSoft = colors.accentSoft || withAlpha(accent, 0.14, colors.faint || 'rgba(255,255,255,0.08)');
  const accentBorder = colors.accentBorder || withAlpha(accent, 0.28, colors.faint || 'rgba(255,255,255,0.16)');
  const accentRing = withAlpha(accent, 0.16, colors.accentBorder || colors.faint || 'rgba(255,255,255,0.12)');
  const highlight = withAlpha(colors.text, typeof colors.bg === 'string' && colors.bg === '#f2f2f7' ? 0.05 : 0.08, colors.cardBorder || 'rgba(255,255,255,0.08)');
  const shadowColor = isHex(accent) ? accent : '#000000';
  const bottomPad = Math.max(insets.bottom, Platform.OS === 'ios' ? 12 : 10);

  return (
    <View style={[styles.floatWrapper, { paddingBottom: bottomPad }]} pointerEvents="box-none">
      <View style={[styles.glowRing, { borderColor: accentRing }]}>
        <View style={[styles.shadowShell, { shadowColor }]}>
          <View
            onLayout={(e) => setBarWidth(e.nativeEvent.layout.width)}
            style={[
              styles.pill,
              {
                backgroundColor: glass,
                borderTopColor: highlight,
                borderColor: colors.cardBorder || highlight,
              },
            ]}
          >
            {tabWidth > 0 && (
              <ReAnimated.View
                pointerEvents="none"
                style={[styles.sliderTrack, { width: tabWidth - 10 }, sliderAnimStyle]}
              >
                <View style={[styles.sliderFill, { backgroundColor: accentSoft, borderColor: accentBorder }]} />
              </ReAnimated.View>
            )}

            {state.routes.map((route) => {
              const { options } = descriptors[route.key];
              const focused = state.index === state.routes.indexOf(route);
              const tint = labelColor(colors, focused);
              const label = (options.title || route.name).toUpperCase();
              const iconOnly = ICON_ONLY.has(route.name);
              const icon = options.tabBarIcon
                ? options.tabBarIcon({ focused, color: tint, size: 22 })
                : null;

              const onPress = () => {
                const event = navigation.emit({
                  type: 'tabPress',
                  target: route.key,
                  canPreventDefault: true,
                });
                if (!focused && !event.defaultPrevented) {
                  navigation.navigate(route.name);
                }
              };

              return (
                <Pressable
                  key={route.key}
                  onPress={onPress}
                  onLongPress={() => navigation.emit({ type: 'tabLongPress', target: route.key })}
                  style={[styles.tab, iconOnly && styles.tabIconOnly]}
                  accessibilityRole="button"
                  accessibilityState={focused ? { selected: true } : {}}
                  accessibilityLabel={options.tabBarAccessibilityLabel ?? label}
                  testID={options.tabBarButtonTestID}
                >
                  {icon}
                  {!iconOnly && (
                    <Text style={[styles.label, { color: tint, fontWeight: focused ? '700' : '500' }]}>
                      {label}
                    </Text>
                  )}
                </Pressable>
              );
            })}
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  floatWrapper: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 12,
    paddingTop: 10,
    backgroundColor: 'transparent',
  },
  glowRing: {
    borderRadius: 999,
    borderWidth: 1.5,
  },
  shadowShell: {
    borderRadius: 999,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 7,
    elevation: 5,
  },
  pill: {
    borderRadius: 999,
    overflow: 'hidden',
    flexDirection: 'row',
    minHeight: 62,
    position: 'relative',
    borderWidth: 1,
    borderTopWidth: 1.2,
  },
  sliderTrack: {
    position: 'absolute',
    top: 5,
    bottom: 5,
    left: 5,
    borderRadius: 999,
    overflow: 'hidden',
  },
  sliderFill: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 999,
    borderWidth: 1,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    zIndex: 2,
    gap: 3,
  },
  tabIconOnly: {
    paddingVertical: 0,
  },
  label: {
    fontSize: 9,
    letterSpacing: 0.7,
    fontFamily: Platform.OS === 'ios' ? 'System' : 'sans-serif-medium',
  },
});
