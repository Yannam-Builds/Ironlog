import React, { useContext, useMemo, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, FlatList, StyleSheet, Dimensions } from 'react-native';
import Svg, { Polyline, Line, Rect, Text as SvgText, Circle } from 'react-native-svg';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import TrainingMaxCalculator from '../components/TrainingMaxCalculator';

const SCREEN_WIDTH = Dimensions.get('window').width;
const CHART_WIDTH = SCREEN_WIDTH - 32;
const CHART_HEIGHT = 160;
const PAD = 24;

const TABS = ['1RM', 'BEST SET', 'VOLUME', 'HISTORY'];

const RANGES = [
  { label: '90D', days: 90 },
  { label: '6M', days: 180 },
  { label: '1Y', days: 365 },
  { label: 'ALL', days: null },
];

function epley(weight, reps) {
  if (!weight || !reps) return 0;
  return weight * (1 + reps / 30);
}

function filterByRange(sessions, days) {
  if (!days) return sessions;
  const cutoff = Date.now() - days * 86400000;
  return sessions.filter(s => new Date(s.date).getTime() >= cutoff);
}

function formatDate(dateStr) {
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

function formatDateFull(dateStr) {
  const d = new Date(dateStr);
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

// Build chart points from data array of numbers, within the SVG canvas
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
    const val = Math.round(maxV - (i / steps) * (maxV - minV));
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
  // Show up to 5 evenly spaced labels
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
  const polylinePoints = points.map(p => `${p.x},${p.y}`).join(' ');
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
      {RANGES.map(r => {
        const active = selected === r.label;
        return (
          <TouchableOpacity
            key={r.label}
            onPress={() => onSelect(r.label)}
            style={[
              styles.rangeChip,
              { borderColor: active ? colors.accent : colors.cardBorder },
              active && { backgroundColor: colors.accent },
            ]}
          >
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
  const { history, pb } = useContext(AppContext);
  const colors = useTheme();

  const [activeTab, setActiveTab] = useState(0);
  const [range, setRange] = useState('ALL');
  const [showTMCalc, setShowTMCalc] = useState(false);

  // Gather all sessions that include this exercise, sorted oldest→newest
  const exerciseSessions = useMemo(() => {
    const sessions = [];
    for (const entry of history) {
      const ex = entry.exercises?.find(e => e.name === exerciseName);
      if (!ex) continue;
      sessions.push({ date: entry.date, exercise: ex });
    }
    sessions.sort((a, b) => new Date(a.date) - new Date(b.date));
    return sessions;
  }, [history, exerciseName]);

  const rangeDays = RANGES.find(r => r.label === range)?.days ?? null;
  const filteredSessions = useMemo(() => filterByRange(exerciseSessions, rangeDays), [exerciseSessions, rangeDays]);

  // --- Tab 1: 1RM data ---
  const ormData = useMemo(() => {
    return filteredSessions.map(s => {
      const allSets = s.exercise.sets || [];
      let best = 0;
      for (const set of allSets) {
        if (!set.weight || !set.reps) continue;
        // Use stored orm if available, else compute Epley
        const val = set.orm != null ? set.orm : epley(set.weight, set.reps);
        if (val > best) best = val;
      }
      return { date: s.date, value: Math.round(best * 10) / 10 };
    }).filter(d => d.value > 0);
  }, [filteredSessions]);

  const currentBest1RM = useMemo(() => {
    if (!ormData.length) return null;
    return Math.max(...ormData.map(d => d.value));
  }, [ormData]);

  // --- Tab 2: Best Set (heaviest weight) data ---
  const bestSetData = useMemo(() => {
    return filteredSessions.map(s => {
      const allSets = s.exercise.sets || [];
      const best = allSets.reduce((max, set) => {
        const w = set.weight || 0;
        return w > max ? w : max;
      }, 0);
      return { date: s.date, value: best };
    }).filter(d => d.value > 0);
  }, [filteredSessions]);

  // --- Tab 3: Volume (working sets only) data ---
  const volumeData = useMemo(() => {
    return filteredSessions.map(s => {
      const workingSets = (s.exercise.sets || []).filter(set => set.type !== 'warmup');
      const vol = workingSets.reduce((sum, set) => sum + (set.weight || 0) * (set.reps || 0), 0);
      return { date: s.date, value: Math.round(vol) };
    }).filter(d => d.value > 0);
  }, [filteredSessions]);

  // --- Tab 4: History (all sessions, newest first) ---
  const historyRows = useMemo(() => {
    return [...exerciseSessions].reverse().map(s => {
      const allSets = s.exercise.sets || [];
      const workingSets = allSets.filter(set => set.type !== 'warmup');
      // Find best set by weight
      const bestSet = workingSets.reduce((best, set) => {
        if (!best || (set.weight || 0) > (best.weight || 0)) return set;
        return best;
      }, null);
      // Summarize: group by weight×reps
      const setSummaries = workingSets.map(set => `${set.reps || 0}×${set.weight || 0}kg`);
      // Compact: find most common pattern or just list sets count
      const distinctPatterns = {};
      workingSets.forEach(set => {
        const key = `${set.reps}@${set.weight}`;
        distinctPatterns[key] = (distinctPatterns[key] || { reps: set.reps, weight: set.weight, count: 0 });
        distinctPatterns[key].count++;
      });
      const summaryParts = Object.values(distinctPatterns).map(p =>
        p.count > 1 ? `${p.count}×${p.reps} @ ${p.weight}kg` : `${p.reps} @ ${p.weight}kg`
      );
      const summary = summaryParts.join(', ') || '—';
      return { date: s.date, summary, bestSet, totalSets: workingSets.length };
    });
  }, [exerciseSessions]);

  const renderHistoryRow = ({ item }) => {
    const bestSetStr = item.bestSet
      ? `${item.bestSet.reps} × ${item.bestSet.weight}kg`
      : null;
    return (
      <View style={[styles.historyRow, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <View style={styles.historyRowLeft}>
          <Text style={[styles.historyDate, { color: colors.text }]}>{formatDateFull(item.date)}</Text>
          <Text style={[styles.historySummary, { color: colors.subtext }]}>{item.summary}</Text>
        </View>
        {bestSetStr && (
          <View style={[styles.bestSetBadge, { borderColor: colors.accent }]}>
            <Text style={[styles.bestSetLabel, { color: colors.muted }]}>BEST</Text>
            <Text style={[styles.bestSetValue, { color: colors.accent }]}>{bestSetStr}</Text>
          </View>
        )}
      </View>
    );
  };

  const renderChart = () => {
    if (activeTab === 0) {
      // 1RM tab
      return (
        <View>
          {currentBest1RM != null && (
            <View style={[styles.heroCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
              <Text style={[styles.heroLabel, { color: colors.muted }]}>CURRENT BEST EST. 1RM</Text>
              <Text style={[styles.heroValue, { color: colors.accent }]}>{currentBest1RM} kg</Text>
            </View>
          )}
          <View style={[styles.chartContainer, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            {ormData.length === 0 ? (
              <EmptyState colors={colors} />
            ) : (
              <LineChart
                values={ormData.map(d => d.value)}
                dates={ormData.map(d => d.date)}
                colors={colors}
              />
            )}
          </View>
          <RangeSelector selected={range} onSelect={setRange} colors={colors} />
        </View>
      );
    }

    if (activeTab === 1) {
      // Best Set tab
      return (
        <View>
          <View style={[styles.chartContainer, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            {bestSetData.length === 0 ? (
              <EmptyState colors={colors} />
            ) : (
              <LineChart
                values={bestSetData.map(d => d.value)}
                dates={bestSetData.map(d => d.date)}
                colors={colors}
              />
            )}
          </View>
          <RangeSelector selected={range} onSelect={setRange} colors={colors} />
        </View>
      );
    }

    if (activeTab === 2) {
      // Volume tab
      return (
        <View>
          <View style={[styles.chartContainer, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            {volumeData.length === 0 ? (
              <EmptyState colors={colors} />
            ) : (
              <BarChart
                values={volumeData.map(d => d.value)}
                dates={volumeData.map(d => d.date)}
                colors={colors}
              />
            )}
          </View>
          <RangeSelector selected={range} onSelect={setRange} colors={colors} />
        </View>
      );
    }

    // History tab (index 3) — rendered separately below as FlatList
    return null;
  };

  return (
    <View style={[styles.screen, { backgroundColor: colors.bg }]}>
      {/* Header */}
      <View style={[styles.header, { borderBottomColor: colors.faint }]}>
        <Text style={[styles.exerciseName, { color: colors.text, flex: 1 }]} numberOfLines={1}>
          {exerciseName}
        </Text>
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

      {/* Tab Bar */}
      <View style={[styles.tabBar, { borderBottomColor: colors.faint }]}>
        {TABS.map((tab, i) => {
          const active = activeTab === i;
          return (
            <TouchableOpacity
              key={tab}
              style={[styles.tabItem, active && { borderBottomColor: colors.accent, borderBottomWidth: 2 }]}
              onPress={() => setActiveTab(i)}
            >
              <Text style={[styles.tabLabel, { color: active ? colors.accent : colors.muted }]}>
                {tab}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* Content */}
      {activeTab === 3 ? (
        historyRows.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={[styles.emptyText, { color: colors.muted }]}>No data for this exercise yet.</Text>
          </View>
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
          {renderChart()}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    gap: 10,
  },
  exerciseName: {
    fontSize: 17,
    fontWeight: '800',
    letterSpacing: 0.3,
    flex: 1,
  },
  tmBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderWidth: 1,
  },
  tmBtnText: {
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 1,
  },
  tabBar: {
    flexDirection: 'row',
    borderBottomWidth: 1,
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  tabLabel: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1.5,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  heroCard: {
    borderRadius: 8,
    borderWidth: 1,
    padding: 16,
    marginBottom: 12,
    alignItems: 'center',
  },
  heroLabel: {
    fontSize: 9,
    letterSpacing: 3,
    marginBottom: 4,
  },
  heroValue: {
    fontSize: 36,
    fontWeight: '900',
  },
  chartContainer: {
    borderRadius: 8,
    borderWidth: 1,
    overflow: 'hidden',
    marginBottom: 12,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
  },
  emptyText: {
    fontSize: 14,
    letterSpacing: 0.3,
  },
  rangeRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
    marginTop: 4,
  },
  rangeChip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: 1,
  },
  rangeChipText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
  },
  historyList: {
    padding: 16,
    paddingBottom: 40,
  },
  historyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
  },
  historyRowLeft: {
    flex: 1,
    marginRight: 12,
  },
  historyDate: {
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 3,
  },
  historySummary: {
    fontSize: 12,
  },
  bestSetBadge: {
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    minWidth: 72,
  },
  bestSetLabel: {
    fontSize: 8,
    letterSpacing: 2,
    marginBottom: 2,
  },
  bestSetValue: {
    fontSize: 12,
    fontWeight: '800',
  },
});
