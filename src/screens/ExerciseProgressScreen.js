import React, { useContext, useMemo, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, FlatList, StyleSheet, Dimensions } from 'react-native';
import Svg, { Polyline, Line, Rect, Text as SvgText, Circle } from 'react-native-svg';
import { Ionicons } from '@expo/vector-icons';
import { captureRef } from 'react-native-view-shot';
import * as Sharing from 'expo-sharing';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import TrainingMaxCalculator from '../components/TrainingMaxCalculator';
import { buildExerciseTrend } from '../domain/intelligence/performanceEngine';

const SCREEN_WIDTH = Dimensions.get('window').width;
const CHART_WIDTH = SCREEN_WIDTH - 32;
const CHART_HEIGHT = 160;
const PAD = 24;

const TABS = ['E1RM', 'LOAD', 'REPS', 'VOLUME', 'CONSISTENCY', 'HISTORY'];
const RANGES = [
  { label: '90D', days: 90 },
  { label: '6M', days: 180 },
  { label: '1Y', days: 365 },
  { label: 'ALL', days: null },
];

function filterByRange(rows, days) {
  if (!days) return rows;
  const cutoff = Date.now() - days * 86400000;
  return rows.filter((row) => new Date(row?.date || 0).getTime() >= cutoff);
}

function formatDate(dateStr) {
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

function formatDateFull(dateStr) {
  const d = new Date(dateStr);
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function roundMetric(value, digits = 1) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 0;
  const mult = 10 ** digits;
  return Math.round(n * mult) / mult;
}

function buildPoints(values) {
  if (!values || values.length === 0) return { points: [], minV: 0, maxV: 0 };
  const minV = Math.min(...values);
  const maxV = Math.max(...values);
  const range = maxV - minV || 1;
  const innerW = CHART_WIDTH - PAD * 2;
  const innerH = CHART_HEIGHT - PAD * 2;

  const points = values.map((v, i) => {
    const x = PAD + (values.length === 1 ? innerW / 2 : (i / (values.length - 1)) * innerW);
    const y = PAD + innerH - ((v - minV) / range) * innerH;
    return { x, y, v };
  });
  return { points, minV, maxV };
}

function GridLines({ minV, maxV, colors }) {
  const steps = 4;
  const innerH = CHART_HEIGHT - PAD * 2;
  const lines = [];
  for (let i = 0; i <= steps; i++) {
    const y = PAD + (i / steps) * innerH;
    const val = roundMetric(maxV - (i / steps) * (maxV - minV), 1);
    lines.push(
      <Line key={i} x1={PAD} y1={y} x2={CHART_WIDTH - PAD} y2={y} stroke={colors.faint} strokeWidth="1" />,
      <SvgText key={`t${i}`} x={PAD - 2} y={y + 4} fontSize="8" fill={colors.muted} textAnchor="end">
        {val}
      </SvgText>
    );
  }
  return <>{lines}</>;
}

function XAxisLabels({ labels, colors }) {
  const innerW = CHART_WIDTH - PAD * 2;
  const count = labels.length;
  if (count === 0) return null;
  const step = Math.max(1, Math.floor(count / 5));
  const shown = labels.map((l, i) => i === 0 || i === count - 1 || i % step === 0 ? { l, i } : null).filter(Boolean);
  return (
    <>
      {shown.map(({ l, i }) => {
        const x = PAD + (count === 1 ? innerW / 2 : (i / (count - 1)) * innerW);
        return (
          <SvgText key={i} x={x} y={CHART_HEIGHT - 4} fontSize="8" fill={colors.muted} textAnchor="middle">
            {l}
          </SvgText>
        );
      })}
    </>
  );
}

function LineChart({ values, dates, colors }) {
  if (!values || values.length === 0) return null;
  const { points, minV, maxV } = buildPoints(values);
  const polylinePoints = points.map((p) => `${p.x},${p.y}`).join(' ');
  const labels = dates.map(formatDate);

  return (
    <Svg width={CHART_WIDTH} height={CHART_HEIGHT}>
      <GridLines minV={minV} maxV={maxV} colors={colors} />
      <XAxisLabels labels={labels} colors={colors} />
      {points.length > 1 && (
        <Polyline points={polylinePoints} fill="none" stroke={colors.accent} strokeWidth="2" />
      )}
      {points.map((p, i) => (
        <Circle key={i} cx={p.x} cy={p.y} r="3.5" fill={colors.accent} />
      ))}
    </Svg>
  );
}

function BarChart({ values, dates, colors }) {
  if (!values || values.length === 0) return null;
  const minV = 0;
  const maxV = Math.max(...values) || 1;
  const innerW = CHART_WIDTH - PAD * 2;
  const innerH = CHART_HEIGHT - PAD * 2;
  const count = values.length;
  const totalGap = count > 1 ? (count - 1) * 4 : 0;
  const barW = Math.max(4, (innerW - totalGap) / count);
  const labels = dates.map(formatDate);

  return (
    <Svg width={CHART_WIDTH} height={CHART_HEIGHT}>
      <GridLines minV={minV} maxV={maxV} colors={colors} />
      <XAxisLabels labels={labels} colors={colors} />
      {values.map((v, i) => {
        const barH = ((v - minV) / (maxV - minV)) * innerH;
        const x = PAD + i * (barW + 4);
        const y = PAD + innerH - barH;
        return (
          <Rect
            key={i}
            x={x}
            y={y}
            width={barW}
            height={Math.max(2, barH)}
            fill={colors.accent}
            rx="2"
            ry="2"
          />
        );
      })}
    </Svg>
  );
}

function EmptyState({ colors }) {
  return (
    <View style={styles.emptyState}>
      <Text style={[styles.emptyText, { color: colors.muted }]}>No data for this exercise yet.</Text>
    </View>
  );
}

function RangeSelector({ selected, onSelect, colors }) {
  return (
    <View style={styles.rangeRow}>
      {RANGES.map((r) => {
        const active = selected === r.label;
        return (
          <TouchableOpacity
            key={r.label}
            onPress={() => onSelect(r.label)}
            style={[
              styles.rangeChip,
              { borderColor: active ? colors.accent : colors.cardBorder },
              active && { backgroundColor: colors.accent },
            ]}>
            <Text style={[styles.rangeChipText, { color: active ? '#fff' : colors.muted }]}>
              {r.label}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

export default function ExerciseProgressScreen({ route }) {
  const { exerciseName } = route.params;
  const { history } = useContext(AppContext);
  const colors = useTheme();

  const [activeTab, setActiveTab] = useState(0);
  const [range, setRange] = useState('ALL');
  const [showTMCalc, setShowTMCalc] = useState(false);
  const [shareStatus, setShareStatus] = useState('');
  const [shareNode, setShareNode] = useState(null);

  const trend = useMemo(() => buildExerciseTrend(history, { name: exerciseName }), [exerciseName, history]);
  const rangeDays = RANGES.find((r) => r.label === range)?.days ?? null;
  const pointsInRange = useMemo(() => filterByRange(trend.points, rangeDays), [rangeDays, trend.points]);

  const historyRows = useMemo(() => {
    return history
      .map((session) => {
        const exercise = (session?.exercises || []).find((candidate) => candidate?.name === exerciseName);
        if (!exercise) return null;
        const workingSets = (exercise?.sets || []).filter((setItem) => (setItem?.type || 'normal') !== 'warmup');
        const bestSet = workingSets.reduce((best, setItem) => {
          if (!best || Number(setItem?.weight || 0) > Number(best?.weight || 0)) return setItem;
          return best;
        }, null);
        const summary = workingSets.length
          ? workingSets.map((setItem) => `${setItem.reps || 0}x${setItem.weight || 0}kg`).join(', ')
          : '-';
        return {
          date: session.date,
          summary,
          bestSet,
          volume: workingSets.reduce((sum, setItem) => sum + (Number(setItem?.weight || 0) * Number(setItem?.reps || 0)), 0),
        };
      })
      .filter(Boolean)
      .sort((a, b) => new Date(b.date) - new Date(a.date));
  }, [exerciseName, history]);

  const stats = useMemo(() => {
    if (!pointsInRange.length) {
      return {
        bestE1rm: 0,
        bestLoad: 0,
        avgReps: 0,
        totalVolume: 0,
        consistency: 0,
      };
    }
    const totalVolume = pointsInRange.reduce((sum, row) => sum + (row.volume || 0), 0);
    const consistency = Math.min(100, Math.round((pointsInRange.length / 8) * 100));
    return {
      bestE1rm: roundMetric(Math.max(...pointsInRange.map((row) => row.e1rm || 0)), 1),
      bestLoad: roundMetric(Math.max(...pointsInRange.map((row) => row.load || 0)), 1),
      avgReps: roundMetric(pointsInRange.reduce((sum, row) => sum + (row.reps || 0), 0) / pointsInRange.length, 1),
      totalVolume: Math.round(totalVolume),
      consistency,
    };
  }, [pointsInRange]);

  const metricConfig = [
    { key: 'e1rm', label: 'BEST EST. 1RM', unit: 'kg', values: pointsInRange.map((row) => row.e1rm), chart: 'line', stat: stats.bestE1rm },
    { key: 'load', label: 'TOP LOAD', unit: 'kg', values: pointsInRange.map((row) => row.load), chart: 'line', stat: stats.bestLoad },
    { key: 'reps', label: 'AVG REPS/SET', unit: '', values: pointsInRange.map((row) => row.reps), chart: 'line', stat: stats.avgReps },
    { key: 'volume', label: 'SESSION VOLUME', unit: 'kg', values: pointsInRange.map((row) => row.volume), chart: 'bar', stat: stats.totalVolume },
    { key: 'consistency', label: 'CONSISTENCY SCORE', unit: '%', values: pointsInRange.map((_, i) => Math.min(100, Math.round(((i + 1) / Math.max(1, pointsInRange.length)) * stats.consistency))), chart: 'line', stat: stats.consistency },
  ];

  const activeMetric = metricConfig[Math.min(activeTab, metricConfig.length - 1)];

  const renderHistoryRow = ({ item }) => {
    const bestSetStr = item.bestSet ? `${item.bestSet.reps} x ${item.bestSet.weight}kg` : null;
    return (
      <View style={[styles.historyRow, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <View style={styles.historyRowLeft}>
          <Text style={[styles.historyDate, { color: colors.text }]}>{formatDateFull(item.date)}</Text>
          <Text style={[styles.historySummary, { color: colors.subtext }]}>{item.summary}</Text>
          <Text style={[styles.historySub, { color: colors.muted }]}>Volume: {Math.round(item.volume)} kg</Text>
        </View>
        {bestSetStr ? (
          <View style={[styles.bestSetBadge, { borderColor: colors.accent }]}>
            <Text style={[styles.bestSetLabel, { color: colors.muted }]}>BEST</Text>
            <Text style={[styles.bestSetValue, { color: colors.accent }]}>{bestSetStr}</Text>
          </View>
        ) : null}
      </View>
    );
  };

  const onShare = async () => {
    if (!shareNode) return;
    try {
      setShareStatus('');
      const uri = await captureRef(shareNode, { format: 'png', quality: 1 });
      const canShare = await Sharing.isAvailableAsync();
      if (!canShare) {
        setShareStatus('Sharing unavailable on this device.');
        return;
      }
      await Sharing.shareAsync(uri, { mimeType: 'image/png', dialogTitle: 'Share PR progress' });
    } catch (e) {
      setShareStatus(`Share failed: ${String(e?.message || e)}`);
    }
  };

  return (
    <View style={[styles.screen, { backgroundColor: colors.bg }]}>
      <View style={[styles.header, { borderBottomColor: colors.faint }]}>
        <Text style={[styles.exerciseName, { color: colors.text }]} numberOfLines={1}>{exerciseName}</Text>
        <TouchableOpacity
          style={[styles.shareBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
          onPress={onShare}>
          <Ionicons name="share-social-outline" size={13} color={colors.accent} />
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tmBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
          onPress={() => setShowTMCalc(true)}>
          <Ionicons name="calculator-outline" size={13} color={colors.accent} />
          <Text style={[styles.tmBtnText, { color: colors.accent }]}>CALC TM</Text>
        </TouchableOpacity>
      </View>

      <TrainingMaxCalculator
        visible={showTMCalc}
        onClose={() => setShowTMCalc(false)}
        exerciseName={exerciseName}
      />

      <View style={[styles.tabBar, { borderBottomColor: colors.faint }]}>
        {TABS.map((tab, i) => {
          const active = activeTab === i;
          return (
            <TouchableOpacity
              key={tab}
              style={[styles.tabItem, active && { borderBottomColor: colors.accent, borderBottomWidth: 2 }]}
              onPress={() => setActiveTab(i)}>
              <Text style={[styles.tabLabel, { color: active ? colors.accent : colors.muted }]}>{tab}</Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {activeTab === 5 ? (
        historyRows.length === 0 ? (
          <EmptyState colors={colors} />
        ) : (
          <FlatList
            data={historyRows}
            keyExtractor={(_, i) => String(i)}
            renderItem={renderHistoryRow}
            contentContainerStyle={styles.historyList}
          />
        )
      ) : (
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {shareStatus ? <Text style={[styles.shareStatus, { color: colors.muted }]}>{shareStatus}</Text> : null}
          <View ref={setShareNode} collapsable={false}>
          <View style={[styles.heroCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[styles.heroLabel, { color: colors.muted }]}>{activeMetric.label}</Text>
            <Text style={[styles.heroValue, { color: colors.accent }]}>
              {activeMetric.stat}
              {activeMetric.unit ? ` ${activeMetric.unit}` : ''}
            </Text>
            <Text style={[styles.heroSub, { color: colors.subtext }]}>
              {pointsInRange.length} sessions in selected range
            </Text>
          </View>

          <View style={[styles.chartContainer, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            {!activeMetric.values.length ? (
              <EmptyState colors={colors} />
            ) : activeMetric.chart === 'bar' ? (
              <BarChart
                values={activeMetric.values}
                dates={pointsInRange.map((row) => row.date)}
                colors={colors}
              />
            ) : (
              <LineChart
                values={activeMetric.values}
                dates={pointsInRange.map((row) => row.date)}
                colors={colors}
              />
            )}
          </View>
          <RangeSelector selected={range} onSelect={setRange} colors={colors} />
          </View>
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    gap: 10,
  },
  exerciseName: { fontSize: 17, fontWeight: '800', letterSpacing: 0.3, flex: 1 },
  tmBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderWidth: 1,
  },
  tmBtnText: { fontSize: 9, fontWeight: '800', letterSpacing: 1 },
  shareBtn: { borderWidth: 1, width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  tabBar: { flexDirection: 'row', borderBottomWidth: 1 },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  tabLabel: { fontSize: 9, fontWeight: '700', letterSpacing: 0.8 },
  scrollContent: { padding: 16, paddingBottom: 40 },
  heroCard: {
    borderRadius: 8,
    borderWidth: 1,
    padding: 16,
    marginBottom: 12,
    alignItems: 'center',
  },
  heroLabel: { fontSize: 9, letterSpacing: 2.6, marginBottom: 4 },
  heroValue: { fontSize: 32, fontWeight: '900' },
  heroSub: { fontSize: 11, marginTop: 4 },
  shareStatus: { fontSize: 11, marginBottom: 8, textAlign: 'center' },
  chartContainer: { borderRadius: 8, borderWidth: 1, overflow: 'hidden', marginBottom: 12 },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
  },
  emptyText: { fontSize: 14, letterSpacing: 0.3 },
  rangeRow: { flexDirection: 'row', justifyContent: 'center', gap: 8, marginTop: 4 },
  rangeChip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
  },
  rangeChipText: { fontSize: 11, fontWeight: '700', letterSpacing: 1 },
  historyList: { padding: 16, paddingBottom: 40 },
  historyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
  },
  historyRowLeft: { flex: 1, marginRight: 12 },
  historyDate: { fontSize: 13, fontWeight: '700', marginBottom: 3 },
  historySummary: { fontSize: 12 },
  historySub: { fontSize: 10, marginTop: 5 },
  bestSetBadge: {
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    minWidth: 72,
  },
  bestSetLabel: { fontSize: 8, letterSpacing: 2, marginBottom: 2 },
  bestSetValue: { fontSize: 12, fontWeight: '800' },
});
