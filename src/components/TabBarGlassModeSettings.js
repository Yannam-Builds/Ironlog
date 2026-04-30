import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useTheme } from '../context/ThemeContext';

/**
 * Tab Bar Glass Mode toggle — drop into SettingsScreen.
 *
 * Props:
 *   glassMode    — current mode string ('frosted' | 'apple')
 *   setGlassMode — setter from useGlassMode() context
 *
 * Two modes (Liquid Glass excluded — requires @shopify/react-native-skia):
 *   'frosted' — glow ring + frosted surface, ~1ms/frame, all devices
 *   'apple'   — blur backdrop + shimmer, ~8-15ms/frame, 2021+ devices
 */

const MODES = [
  {
    key: 'frosted',
    label: 'FROSTED',
    icon: 'snow-outline',
    desc: 'Glow ring + frosted surface. Lightweight, works on all devices.',
  },
  {
    key: 'apple',
    label: 'APPLE GLASS',
    icon: 'glasses-outline',
    desc: 'Blur backdrop + refraction shimmer. Premium look, moderate CPU.',
  },
];

export function TabBarGlassModeToggle({ glassMode, setGlassMode }) {
  const colors = useTheme();

  const currentMode = MODES.find(m => m.key === glassMode) || MODES[0];
  const nextMode = MODES[(MODES.indexOf(currentMode) + 1) % MODES.length];

  return (
    <View style={[s.container, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
      {/* Header */}
      <View style={s.labelGroup}>
        <Text style={[s.label, { color: colors.muted }]}>TAB BAR STYLE</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 }}>
          <Ionicons name={currentMode.icon} size={15} color={colors.accent} />
          <Text style={[s.currentLabel, { color: colors.text }]}>{currentMode.label}</Text>
        </View>
      </View>

      {/* Toggle button */}
      <Pressable
        onPress={() => setGlassMode(nextMode.key)}
        style={[s.toggle, { backgroundColor: colors.accent }]}
        android_ripple={{ color: 'rgba(255,255,255,0.15)', borderless: false }}
      >
        <Ionicons name={nextMode.icon} size={16} color="#fff" />
        <Text style={s.toggleLabel}>SWITCH TO {nextMode.label}</Text>
        <Ionicons name="chevron-forward" size={14} color="#fff" style={{ marginLeft: 2 }} />
      </Pressable>

      {/* Description */}
      <Text style={[s.desc, { color: colors.muted, borderTopColor: colors.faint }]}>
        {currentMode.desc}
      </Text>

      {/* Mode indicator dots */}
      <View style={[s.dots, { borderTopColor: colors.faint }]}>
        {MODES.map(mode => (
          <View
            key={mode.key}
            style={[
              s.dot,
              {
                backgroundColor: glassMode === mode.key ? colors.accent : colors.faint,
                flex: 1,
              },
            ]}
          />
        ))}
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  container: {
    marginHorizontal: 16,
    marginVertical: 8,
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
  },
  labelGroup: {
    marginBottom: 12,
  },
  label: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.5,
  },
  currentLabel: {
    fontSize: 14,
    fontWeight: '700',
  },
  toggle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 11,
    paddingHorizontal: 16,
    borderRadius: 10,
    gap: 6,
    marginBottom: 12,
  },
  toggleLabel: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.8,
    color: '#fff',
  },
  desc: {
    fontSize: 11,
    lineHeight: 16,
    paddingTop: 12,
    borderTopWidth: 1,
    marginBottom: 12,
  },
  dots: {
    flexDirection: 'row',
    gap: 6,
    paddingTop: 12,
    borderTopWidth: 1,
  },
  dot: {
    height: 3,
    borderRadius: 999,
  },
});
