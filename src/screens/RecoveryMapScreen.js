import React, { useMemo, useRef, useState } from 'react';
import {
  PanResponder,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../context/ThemeContext';
import BodyMapSVG from '../components/BodyMapSVG';
import { getMuscleAtTouch } from '../utils/muscleMapHitTest';

const REGION_TO_GROUP = {
  chest: 'chest',
  shoulders: 'shoulders',
  rearDelts: 'rearDelts',
  arms: 'arms',
  core: 'core',
  quads: 'legs',
  hamstrings: 'legs',
  calves: 'legs',
  back: 'back',
};

const RECOVERY_COLORS = {
  recovering: '#FF6B6B',
  partial: '#FFD93D',
  ready: '#6BCB77',
  untrained: null,
};

function getRegionColors(groupReadiness, colors) {
  const result = {};
  Object.keys(REGION_TO_GROUP).forEach((region) => {
    const group = REGION_TO_GROUP[region];
    const readiness = typeof groupReadiness?.[group] === 'number'
      ? groupReadiness[group]
      : region === 'rearDelts' && typeof groupReadiness?.shoulders === 'number'
        ? groupReadiness.shoulders
        : 1;
    let status = 'ready';
    if (readiness < 0.6) status = 'recovering';
    else if (readiness < 0.82) status = 'partial';
    result[region] = RECOVERY_COLORS[status] || colors.faint;
  });
  return result;
}

export default function RecoveryMapScreen({ navigation, route }) {
  const colors = useTheme();
  const [view, setView] = useState('front');
  const [mapSize, setMapSize] = useState({ width: 1, height: 1 });
  const [tooltip, setTooltip] = useState(null);
  const hideTimerRef = useRef(null);
  const groupReadiness = route?.params?.groupReadiness || {};

  const regionColors = useMemo(
    () => getRegionColors(groupReadiness, colors),
    [groupReadiness, colors]
  );

  const revealRegionFromTouch = (evt) => {
    if (!mapSize.width || !mapSize.height) return;
    const { locationX, locationY } = evt.nativeEvent;
    const muscle = getMuscleAtTouch({
      view,
      mapWidth: mapSize.width,
      mapHeight: mapSize.height,
      locationX,
      locationY,
    });

    if (!muscle) {
      setTooltip(null);
      return;
    }

    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    const estimatedWidth = Math.min(170, Math.max(84, muscle.label.length * 8 + 28));
    setTooltip({
      label: muscle.label,
      x: Math.min(mapSize.width - estimatedWidth - 8, Math.max(8, locationX - estimatedWidth / 2)),
      y: Math.max(8, locationY - 40),
    });
  };

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: () => true,
        onPanResponderGrant: revealRegionFromTouch,
        onPanResponderMove: revealRegionFromTouch,
        onPanResponderRelease: () => {
          if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
          hideTimerRef.current = setTimeout(() => setTooltip(null), 700);
        },
      }),
    [mapSize, view]
  );

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <View style={s.switchRow}>
        {['front', 'back'].map((side) => {
          const active = side === view;
          return (
            <TouchableOpacity
              key={side}
              style={[
                s.switchBtn,
                {
                  borderColor: active ? colors.accent : colors.faint,
                  backgroundColor: active ? colors.accentSoft : colors.card,
                },
              ]}
              onPress={() => {
                setView(side);
                setTooltip(null);
              }}
            >
              <Text style={[s.switchText, { color: active ? colors.accent : colors.subtext }]}>
                {side.toUpperCase()}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <Text style={[s.helpText, { color: colors.muted }]}>
        Slide your finger over the body map to inspect muscle groups
      </Text>

      <View
        style={[s.mapCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
        onLayout={(e) => {
          const { width, height } = e.nativeEvent.layout;
          setMapSize({ width, height });
        }}
        {...panResponder.panHandlers}
      >
        <BodyMapSVG
          view={view}
          regionColors={regionColors}
          defaultColor={colors.subtext}
          width="100%"
          height="100%"
        />
        {tooltip ? (
          <View
            style={[
              s.tooltip,
              {
                left: tooltip.x,
                top: tooltip.y,
                backgroundColor: colors.surface,
                borderColor: colors.accent,
              },
            ]}
          >
            <Ionicons name="locate" size={11} color={colors.accent} />
            <Text style={[s.tooltipText, { color: colors.text }]}>{tooltip.label}</Text>
          </View>
        ) : null}
      </View>

      <TouchableOpacity
        style={[s.analyticsBtn, { backgroundColor: colors.accent }]}
        onPress={() => navigation.navigate('VolumeAnalytics')}
      >
        <Ionicons name="analytics-outline" size={15} color="#fff" />
        <Text style={s.analyticsBtnText}>OPEN VOLUME ANALYTICS</Text>
      </TouchableOpacity>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  switchRow: { flexDirection: 'row', gap: 10, marginBottom: 10 },
  switchBtn: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 12,
    paddingVertical: 11,
    alignItems: 'center',
  },
  switchText: { fontSize: 11, fontWeight: '800', letterSpacing: 1.5 },
  helpText: { fontSize: 12, marginBottom: 10 },
  mapCard: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 14,
    overflow: 'hidden',
    minHeight: 420,
  },
  tooltip: {
    position: 'absolute',
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  tooltipText: { fontSize: 11, fontWeight: '700', letterSpacing: 0.3 },
  analyticsBtn: {
    marginTop: 12,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
  },
  analyticsBtnText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 1.2,
  },
});
