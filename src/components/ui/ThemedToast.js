import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Toast from 'react-native-toast-message';
import { useTheme } from '../../context/ThemeContext';
import { withAlpha } from '../../utils/colorUtils';
import { RADIUS } from '../../utils/themes';

/**
 * Individual toast card — renders inside ThemeProvider so useTheme() works.
 */
function ToastCard({ text1, text2, accentColor }) {
  const colors = useTheme();
  return (
    <View
      style={[
        s.card,
        {
          backgroundColor: colors.card,
          borderColor: withAlpha(accentColor, 0.35),
          shadowColor: accentColor,
        },
      ]}
    >
      {/* Left accent stripe */}
      <View style={[s.stripe, { backgroundColor: accentColor }]} />
      <View style={s.body}>
        {text1 ? (
          <Text style={[s.title, { color: colors.text }]} numberOfLines={1}>
            {text1}
          </Text>
        ) : null}
        {text2 ? (
          <Text style={[s.sub, { color: colors.muted }]} numberOfLines={2}>
            {text2}
          </Text>
        ) : null}
      </View>
    </View>
  );
}

/**
 * Toast type → accent colour mapping.
 * These are fixed hex values (not theme tokens) so they work on every theme.
 */
export const toastConfig = {
  success: (props) => <ToastCard {...props} accentColor="#30D158" />,
  error:   (props) => <ToastCard {...props} accentColor="#FF453A" />,
  info:    (props) => <ToastCard {...props} accentColor="#0A84FF" />,
};

/**
 * Drop-in replacement for bare <Toast /> — pass this to App.js instead.
 * Must be rendered inside ThemeProvider so ToastCard can read the theme.
 */
export default function ThemedToast() {
  return <Toast config={toastConfig} />;
}

const s = StyleSheet.create({
  card: {
    width: '90%',
    maxWidth: 380,
    flexDirection: 'row',
    alignItems: 'stretch',
    borderWidth: 1,
    borderRadius: RADIUS.md,
    overflow: 'hidden',
    // iOS shadow
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.22,
    shadowRadius: 10,
    // Android elevation
    elevation: 8,
  },
  stripe: {
    width: 4,
  },
  body: {
    flex: 1,
    paddingVertical: 12,
    paddingHorizontal: 14,
    gap: 3,
  },
  title: {
    fontSize: 14,
    fontWeight: '800',
    letterSpacing: 0.2,
  },
  sub: {
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 0.1,
    lineHeight: 16,
  },
});
