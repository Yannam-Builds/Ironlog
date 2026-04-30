import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useTheme } from '../../context/ThemeContext';
import { withAlpha } from '../../utils/colorUtils';
import { TYPE } from '../../utils/themes';
import { haptic } from '../../services/hapticsEngine';
import Button from './Button';

export default function EmptyState({ icon = 'barbell-outline', title, subtitle, ctaLabel, onCta }) {
  const colors = useTheme();
  return (
    <View style={s.wrap}>
      <View style={[s.iconCircle, { backgroundColor: withAlpha(colors.accent, 0.1), borderColor: withAlpha(colors.accent, 0.25) }]}>
        <Ionicons name={icon} size={32} color={colors.accent} />
      </View>
      <Text style={[TYPE.section, s.title, { color: colors.text }]}>{title}</Text>
      {subtitle ? <Text style={[TYPE.body, s.subtitle, { color: colors.muted }]}>{subtitle}</Text> : null}
      {ctaLabel && onCta ? (
        <Button
          label={ctaLabel}
          onPress={() => { haptic('selection'); onCta(); }}
          variant="primary"
          size="medium"
          style={{ marginTop: 12, minWidth: 140 }}
        />
      ) : null}
    </View>
  );
}

const s = StyleSheet.create({
  wrap: { alignItems: 'center', justifyContent: 'center', padding: 32, gap: 12 },
  iconCircle: { width: 72, height: 72, borderRadius: 36, borderWidth: 1, alignItems: 'center', justifyContent: 'center', marginBottom: 8 },
  title: { textAlign: 'center' },
  subtitle: { textAlign: 'center', maxWidth: 280 },
});
