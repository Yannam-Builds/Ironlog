
import React, { useEffect } from 'react';
import { Modal, View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import ReAnimated, { useSharedValue, withSpring, withTiming, useAnimatedStyle } from 'react-native-reanimated';
import { useTheme } from '../context/ThemeContext';
import { haptic } from '../services/hapticsEngine';
import { pickContrastText, withAlpha } from '../utils/colorUtils';
import { RADIUS } from '../utils/themes';

// Props: visible, title, message, buttons: [{ text, onPress, style: 'default'|'cancel'|'destructive' }]
export default function CustomAlert({ visible, title, message, buttons = [], onDismiss, overlay = null, backdropOverlay = null }) {
  const colors = useTheme();
  const scale = useSharedValue(0.88);
  const opacity = useSharedValue(0);

  useEffect(() => {
    if (visible) {
      scale.value = withSpring(1, { stiffness: 340, damping: 24, mass: 0.7 });
      opacity.value = withTiming(1, { duration: 160 });
    } else {
      scale.value = 0.88;
      opacity.value = 0;
    }
  }, [visible]); // eslint-disable-line

  // CRITICAL: Resolve PlatformColor (used by Monet) to plain hex strings.
  // Reanimated's UI-thread validator rejects PlatformColor objects at mount time.
  const cardBg = withAlpha(colors.surface || colors.card, 1, '#1C1C1E').slice(0, 7);
  const cardBdr = withAlpha(colors.cardBorder, 1, '#333333').slice(0, 7);
  const textHex = withAlpha(colors.text, 1, '#FFFFFF').slice(0, 7);
  const subtextHex = withAlpha(colors.subtext, 1, '#CCCCCC').slice(0, 7);
  const mutedHex = withAlpha(colors.muted, 1, '#999999').slice(0, 7);
  const accentHex = withAlpha(colors.accent, 1, '#FF4500').slice(0, 7);
  const textOnAccentHex = withAlpha(colors.textOnAccent || '#FFFFFF', 1, '#FFFFFF').slice(0, 7);
  const faintHex = withAlpha(colors.faint, 1, '#333333').slice(0, 7);
  const safeTextOnAccent = textOnAccentHex.toLowerCase() === accentHex.toLowerCase()
    ? pickContrastText(accentHex)
    : textOnAccentHex;

  const cardStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ scale: scale.value }],
  }));

  const handleButton = (btn) => {
    const event = btn.hapticEvent
      || (btn.style === 'destructive' ? 'destructiveAction' : btn.style === 'cancel' ? 'selection' : 'lightConfirm');
    haptic(event);
    if (onDismiss) onDismiss();
    if (btn.onPress) setTimeout(() => btn.onPress(), 0);
  };

  if (!visible) return null;

  return (
    <Modal transparent visible={visible} animationType="none" onRequestClose={onDismiss}>
      <View style={s.backdrop}>
        {backdropOverlay ? <View pointerEvents="none" style={s.backdropOverlay}>{backdropOverlay}</View> : null}
        <ReAnimated.View style={[s.card, { backgroundColor: cardBg, borderColor: cardBdr }, cardStyle]}>
          {overlay ? <View pointerEvents="none" style={s.overlay}>{overlay}</View> : null}
          {title ? <Text style={[s.title, { color: textHex }]}>{title}</Text> : null}
          {message ? <Text style={[s.message, { color: subtextHex }]}>{message}</Text> : null}
          <View style={[s.btnRow, buttons.length > 2 && { flexDirection: 'column' }]}>
            {buttons.map((btn, i) => {
              const isDestructive = btn.style === 'destructive';
              const isCancel = btn.style === 'cancel';
              return (
                <TouchableOpacity
                  key={i}
                  style={[
                    s.btn,
                    buttons.length > 2 && s.btnFull,
                    { borderColor: faintHex },
                    isDestructive && { backgroundColor: withAlpha('#FF453A', 0.13), borderColor: withAlpha('#FF453A', 0.3) },
                    !isDestructive && !isCancel && { backgroundColor: accentHex, borderColor: withAlpha(accentHex, 0.75) },
                  ]}
                  onPress={() => handleButton(btn)}
                  activeOpacity={isDestructive ? 0.7 : 0.85}>
                  <Text style={[
                    s.btnText,
                    { color: isDestructive ? '#FF453A' : isCancel ? mutedHex : safeTextOnAccent },
                  ]}>
                    {btn.text}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </ReAnimated.View>
      </View>
    </Modal>
  );
}

const s = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', justifyContent: 'center', alignItems: 'center', padding: 28 },
  backdropOverlay: { ...StyleSheet.absoluteFillObject, zIndex: 1 },
  card: { width: '100%', borderRadius: RADIUS.lg, borderWidth: 1, padding: 24, gap: 12, overflow: 'hidden' },
  overlay: { position: 'absolute', left: 0, right: 0, top: 0, height: 140 },
  title: { fontSize: 18, fontWeight: '900', letterSpacing: 0.5, textAlign: 'center' },
  message: { fontSize: 14, lineHeight: 20, textAlign: 'center' },
  btnRow: { flexDirection: 'row', gap: 10, marginTop: 4 },
  btn: { flex: 1, borderWidth: 1, borderRadius: RADIUS.sm, paddingVertical: 13, paddingHorizontal: 12, alignItems: 'center' },
  btnFull: { flex: undefined, width: '100%' },
  btnText: { fontSize: 14, fontWeight: '700', letterSpacing: 0.5 },
});
