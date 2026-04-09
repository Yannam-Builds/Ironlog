import React from 'react';
import { View, Text } from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import { useTheme } from '../context/ThemeContext';

export default function RestTimerCircle({ time, total, color, size = 130 }) {
  const colors = useTheme();
  const sw = 6;
  const r = (size - sw * 2) / 2;
  const circ = 2 * Math.PI * r;
  const pct = total > 0 ? time / total : 0;
  const offset = circ * (1 - pct);
  const mins = Math.floor(time / 60);
  const secs = time % 60;

  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Svg width={size} height={size} style={{ transform: [{ rotate: '-90deg' }], position: 'absolute' }}>
        <Circle cx={size/2} cy={size/2} r={r} fill="none" stroke={colors.faint || '#2a2a2a'} strokeWidth={sw} />
        <Circle cx={size/2} cy={size/2} r={r} fill="none" stroke={color} strokeWidth={sw}
          strokeDasharray={String(circ)} strokeDashoffset={offset} strokeLinecap="round" />
      </Svg>
      <Text style={{ color: colors.text || '#fff', fontSize: 28, fontWeight: '900' }}>
        {mins}:{secs.toString().padStart(2, '0')}
      </Text>
    </View>
  );
}
