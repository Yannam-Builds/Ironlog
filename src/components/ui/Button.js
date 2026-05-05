/**
 * Button — centralized button component for Ironlog
 *
 * Variants:  primary | secondary | tertiary | destructive | icon
 * Sizes:     small (32) | medium (44) | large (52)
 *
 * Usage:
 *   <Button label="START WORKOUT" onPress={fn} />
 *   <Button label="CANCEL" variant="secondary" onPress={fn} />
 *   <Button label="DELETE" variant="destructive" onPress={fn} />
 *   <Button icon="add-outline" variant="icon" onPress={fn} />
 *   <Button label="SAVE" loading onPress={fn} />
 */

import React from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useTheme } from '../../context/ThemeContext';
import { textOnColor, withAlpha } from '../../utils/colorUtils';
import { RADIUS } from '../../utils/themes';

const SIZES = {
  small:  { minHeight: 32,  paddingH: 12, paddingV: 6,  fontSize: 11, iconSize: 16, letterSpacing: 0.6, borderRadius: RADIUS.sm },
  medium: { minHeight: 44,  paddingH: 16, paddingV: 11, fontSize: 12, iconSize: 18, letterSpacing: 0.8, borderRadius: RADIUS.md },
  large:  { minHeight: 52,  paddingH: 20, paddingV: 14, fontSize: 14, iconSize: 20, letterSpacing: 1.0, borderRadius: RADIUS.md },
};

export default function Button({
  label,
  onPress,
  variant = 'primary',
  size = 'medium',
  disabled = false,
  loading = false,
  icon = null,          // Ionicons name shown on left (or only content for 'icon' variant)
  iconRight = false,    // move icon to the right side of label
  iconSize,             // override icon size
  style,                // extra container style
  textStyle,            // extra label style
  hitSlop,
  testID,
  accessibilityLabel,
  ...rest
}) {
  const colors = useTheme();
  const sz = SIZES[size] || SIZES.medium;
  const isIconOnly = variant === 'icon';
  const isDisabled = disabled || loading;

  // ── variant colours ──────────────────────────────────────────────
  let containerStyle, labelColor, borderColor, bgColor;

  switch (variant) {
    case 'secondary':
      bgColor    = colors.card;
      borderColor = colors.accent;
      labelColor  = colors.accent;
      break;
    case 'tertiary':
      bgColor     = 'transparent';
      borderColor = 'transparent';
      labelColor  = colors.accent;
      break;
    case 'destructive':
      bgColor     = withAlpha('#FF453A', 0.10);
      borderColor = withAlpha('#FF453A', 0.28);
      labelColor  = '#FF453A';
      break;
    case 'icon':
      bgColor     = colors.card;
      borderColor = colors.faint;
      labelColor  = colors.subtext;
      break;
    default: // primary
      bgColor     = colors.accent;
      borderColor = colors.accent;
      labelColor  = textOnColor(colors.accent, colors.textOnAccent);
  }

  if (isDisabled) {
    bgColor     = withAlpha(bgColor === 'transparent' ? colors.faint : bgColor, 0.45);
    borderColor = withAlpha(borderColor, 0.3);
    labelColor  = withAlpha(labelColor, 0.45);
  }

  const resolvedIconSize = iconSize ?? sz.iconSize;

  // ── icon-only layout ─────────────────────────────────────────────
  if (isIconOnly) {
    const dim = sz.minHeight;
    return (
      <TouchableOpacity
        onPress={onPress}
        disabled={isDisabled}
        activeOpacity={0.75}
        hitSlop={hitSlop ?? { top: 8, bottom: 8, left: 8, right: 8 }}
        testID={testID}
        accessibilityLabel={accessibilityLabel}
        accessibilityRole="button"
        style={[
          s.iconBtn,
          {
            width: dim,
            height: dim,
            borderRadius: dim / 2,
            backgroundColor: bgColor,
            borderColor,
          },
          style,
        ]}
        {...rest}
      >
        {loading
          ? <ActivityIndicator size="small" color={labelColor} />
          : icon
            ? <Ionicons name={icon} size={resolvedIconSize} color={labelColor} />
            : null}
      </TouchableOpacity>
    );
  }

  // ── standard layout ──────────────────────────────────────────────
  const iconEl = icon
    ? <Ionicons name={icon} size={resolvedIconSize} color={loading ? 'transparent' : labelColor} />
    : null;

  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.82}
      hitSlop={hitSlop}
      testID={testID}
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      style={[
        s.base,
        {
          minHeight: sz.minHeight,
          paddingHorizontal: sz.paddingH,
          paddingVertical: sz.paddingV,
          borderRadius: sz.borderRadius,
          backgroundColor: bgColor,
          borderColor,
          borderWidth: variant === 'tertiary' ? 0 : 1,
        },
        style,
      ]}
      {...rest}
    >
      {/* Loading spinner overlaid — keeps layout stable */}
      {loading && (
        <ActivityIndicator
          size="small"
          color={labelColor}
          style={StyleSheet.absoluteFill}
        />
      )}

      <View style={[s.row, loading && { opacity: 0 }]}>
        {!iconRight && iconEl}
        {label ? (
          <Text
            style={[
              s.label,
              {
                fontSize: sz.fontSize,
                letterSpacing: sz.letterSpacing,
                color: labelColor,
              },
              textStyle,
            ]}
            numberOfLines={1}
          >
            {label}
          </Text>
        ) : null}
        {iconRight && iconEl}
      </View>
    </TouchableOpacity>
  );
}

const s = StyleSheet.create({
  base: {
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  label: {
    fontWeight: '800',
    textAlign: 'center',
  },
  iconBtn: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
});
