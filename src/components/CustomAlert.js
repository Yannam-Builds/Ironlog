
import React, { useEffect, useRef } from 'react';
import { Modal, View, Text, TouchableOpacity, Animated, StyleSheet } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { triggerHaptic } from '../services/hapticsEngine';

// Props: visible, title, message, buttons: [{ text, onPress, style: 'default'|'cancel'|'destructive' }]
export default function CustomAlert({ visible, title, message, buttons = [], onDismiss }) {
  const colors = useTheme();
  const scale = useRef(new Animated.Value(0.88)).current;
  const opacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.spring(scale, { toValue: 1, useNativeDriver: true, tension: 160, friction: 10 }),
        Animated.timing(opacity, { toValue: 1, duration: 160, useNativeDriver: true }),
      ]).start();
    } else {
      scale.setValue(0.88);
      opacity.setValue(0);
    }
  }, [visible]);

  const handleButton = (btn) => {
    const event = btn.hapticEvent
      || (btn.style === 'destructive' ? 'destructiveAction' : btn.style === 'cancel' ? 'selection' : 'lightConfirm');
    triggerHaptic(event).catch(() => {});
    if (onDismiss) onDismiss();
    if (btn.onPress) setTimeout(() => btn.onPress(), 0);
  };

  if (!visible) return null;

  return (
    <Modal transparent visible={visible} animationType="none" onRequestClose={onDismiss}>
      <View style={s.backdrop}>
        <Animated.View style={[s.card, { backgroundColor: colors.surface || colors.card, borderColor: colors.cardBorder, opacity, transform: [{ scale }] }]}>
          {title ? <Text style={[s.title, { color: colors.text }]}>{title}</Text> : null}
          {message ? <Text style={[s.message, { color: colors.subtext }]}>{message}</Text> : null}
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
                    { borderColor: colors.faint },
                    isDestructive && { backgroundColor: '#CC222222', borderColor: '#CC222244' },
                    !isDestructive && !isCancel && { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder },
                  ]}
                  onPress={() => handleButton(btn)}
                  activeOpacity={0.7}>
                  <Text style={[
                    s.btnText,
                    { color: isDestructive ? '#CC3333' : isCancel ? colors.muted : colors.accent },
                  ]}>
                    {btn.text}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
}

const s = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.65)', justifyContent: 'center', alignItems: 'center', padding: 28 },
  card: { width: '100%', borderRadius: 16, borderWidth: 1, padding: 24, gap: 12 },
  title: { fontSize: 18, fontWeight: '900', letterSpacing: 0.5 },
  message: { fontSize: 14, lineHeight: 20 },
  btnRow: { flexDirection: 'row', gap: 10, marginTop: 4 },
  btn: { flex: 1, borderWidth: 1, borderRadius: 10, paddingVertical: 13, paddingHorizontal: 12, alignItems: 'center' },
  btnFull: { flex: undefined, width: '100%' },
  btnText: { fontSize: 14, fontWeight: '700', letterSpacing: 0.5 },
});
