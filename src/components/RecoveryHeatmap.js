import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import BodyMapSVG from './BodyMapSVG';
const CARD_RADIUS = 14;

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
  recovering: '#E88787',
  partial: '#E5C46A',
  ready: '#79C98D',
  untrained: null,
};
const RECOVERY_RED_THRESHOLD = 0.72;
const RECOVERY_PARTIAL_THRESHOLD = 0.9;

const LEGEND = [
  { key: 'recovering', label: '< 72%' },
  { key: 'partial', label: '72-90%' },
  { key: 'ready', label: '> 90%' },
  { key: 'untrained', label: 'Unknown' },
];

export default function RecoveryHeatmap({ navigation, groupReadiness }) {
  const colors = useTheme();

  const regionColors = {};
  ['chest', 'shoulders', 'rearDelts', 'arms', 'core', 'quads', 'hamstrings', 'calves', 'back'].forEach((region) => {
    const group = REGION_TO_GROUP[region];
    let readiness = 1.0;
    if (groupReadiness && groupReadiness[group] !== undefined) {
      readiness = groupReadiness[group];
    } else if (region === 'rearDelts' && groupReadiness && groupReadiness.shoulders !== undefined) {
      readiness = groupReadiness.shoulders;
    }

    let status = 'ready';
    if (readiness < RECOVERY_RED_THRESHOLD) status = 'recovering';
    else if (readiness < RECOVERY_PARTIAL_THRESHOLD) status = 'partial';

    regionColors[region] = RECOVERY_COLORS[status] || colors.faint;
  });

  const mapW = 110;
  const mapH = 180;

  return (
    <View style={[s.container, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}> 
      <TouchableOpacity activeOpacity={0.85} onPress={() => navigation?.navigate('RecoveryMap', { groupReadiness })}>
        <View style={s.headerRow}>
          <Text style={[s.title, { color: colors.muted }]}>MUSCLE RECOVERY</Text>
          <Text style={[s.tap, { color: colors.muted }]}>TAP TO EXPAND -></Text>
        </View>

        <View style={s.mapRow}>
          <View style={s.mapSide}>
            <Text style={[s.sideLabel, { color: colors.muted }]}>FRONT</Text>
            <BodyMapSVG regionColors={regionColors} defaultColor={colors.subtext} width={mapW} height={mapH} view="front" />
          </View>
          <View style={s.mapSide}>
            <Text style={[s.sideLabel, { color: colors.muted }]}>BACK</Text>
            <BodyMapSVG regionColors={regionColors} defaultColor={colors.subtext} width={mapW} height={mapH} view="back" />
          </View>
          <View style={[s.legendCol, { borderLeftColor: colors.faint }]}>
            {LEGEND.map(({ key, label }) => (
              <View key={key} style={s.legendItem}>
                <View style={[s.dot, { backgroundColor: RECOVERY_COLORS[key] || colors.faint }]} />
                <Text style={[s.legendLabel, { color: colors.subtext }]}>{label}</Text>
              </View>
            ))}
          </View>
        </View>
      </TouchableOpacity>

      <TouchableOpacity
        style={[s.analyticsBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
        onPress={() => navigation?.navigate('VolumeAnalytics')}
      >
        <Text style={[s.analyticsBtnText, { color: colors.accent }]}>OPEN VOLUME ANALYTICS</Text>
      </TouchableOpacity>
    </View>
  );
}

const s = StyleSheet.create({
  container: { margin: 16, marginBottom: 0, padding: 12, borderWidth: 1, borderRadius: CARD_RADIUS },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  mapRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  mapSide: { alignItems: 'center' },
  title: { fontSize: 9, letterSpacing: 3, fontWeight: '700' },
  tap: { fontSize: 9, letterSpacing: 1 },
  sideLabel: { fontSize: 8, letterSpacing: 1.6, marginBottom: 2, fontWeight: '700' },
  legendCol: { paddingLeft: 10, borderLeftWidth: 1, gap: 8, justifyContent: 'center', flex: 1 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { width: 9, height: 9, borderRadius: 5 },
  legendLabel: { fontSize: 10, fontWeight: '700' },
  analyticsBtn: { marginTop: 12, borderWidth: 1, paddingVertical: 10, alignItems: 'center', borderRadius: 10 },
  analyticsBtnText: { fontSize: 10, fontWeight: '800', letterSpacing: 1.3 },
});
