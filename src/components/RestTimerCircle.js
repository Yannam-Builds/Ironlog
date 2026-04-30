import React from 'react';
import { View, Text } from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import { useTheme } from '../context/ThemeContext';
import { withAlpha } from '../utils/colorUtils';

export default function RestTimerCircle({ time, total, color, size = 130 }) {
  const colors = useTheme();
  const faintStroke = withAlpha(colors.faint, 1, '#333333').slice(0, 7);
  const progressStroke = withAlpha(color || colors.accent, 1, '#FF4500').slice(0, 7);
  const sw = 7;
  const r = (size - sw * 2) / 2;
  const circ = 2 * Math.PI * r;
  const frac = total > 0 ? time / total : 0;
  const strokeDashoffset = (1 - Math.max(0, Math.min(1, frac))) * circ;

  const mins = Math.floor(time / 60);
  const secs = time % 60;
  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Svg width={size} height={size} style={{ position: 'absolute' }}>
        <Circle cx={size / 2} cy={size / 2} r={r} fill="none"
          stroke={faintStroke} strokeWidth={sw} />
        <Circle
          cx={size / 2} cy={size / 2} r={r} fill="none"
          stroke={progressStroke} strokeWidth={sw}
          strokeDasharray={String(circ)}
          strokeDashoffset={strokeDashoffset}
          strokeLinecap="round"
          rotation={-90}
          originX={size / 2}
          originY={size / 2}
        />
      </Svg>
      <Text style={{ color: colors.text, fontSize: 30, fontWeight: '900', letterSpacing: 0.8 }}>
        {mins}:{secs.toString().padStart(2, '0')}
      </Text>
    </View>
  );
}
