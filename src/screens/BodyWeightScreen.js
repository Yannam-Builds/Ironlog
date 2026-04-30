import React, { useMemo, useState } from 'react';
import {
  Alert,
  Dimensions,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import Svg, {
  Circle,
  Defs,
  Line,
  LinearGradient,
  Path,
  Stop,
  Text as SvgText,
} from 'react-native-svg';
import Ionicons from 'react-native-vector-icons/Ionicons';
import * as Sharing from '../platform/sharing';
import { captureRef } from 'react-native-view-shot';
import useWatermelonBodyMeasurements from '../hooks/useWatermelonBodyMeasurements';
import useWatermelonSettings from '../hooks/useWatermelonSettings';
import { useTheme } from '../context/ThemeContext';
import { fireHaptic } from '../services/hapticsEngine';
import { withAlpha } from '../utils/colorUtils';
import {
  BODY_WEIGHT_RANGES,
  calculateBodyWeightSummary,
  downsampleSeries,
  filterEntriesByRange,
  normalizeBodyWeightEntries,
} from '../utils/bodyWeightAnalytics';

// 16 scrollview padding + 16 card padding + extra safety margins for axis labels
const CHART_WIDTH = Dimensions.get('window').width - 76;
const CHART_HEIGHT = 240;
const PLOT = { top: 20, right: 30, bottom: 36, left: 42 };

function formatWeight(value, unit) {
  if (value == null || !Number.isFinite(value)) return '--';
  return `${value.toFixed(1)} ${unit}`;
}

function formatSigned(value, unit) {
  if (value == null || !Number.isFinite(value)) return '--';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(1)} ${unit}`;
}

function formatDateLabel(timestamp) {
  return new Date(timestamp).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
  });
}

function formatHistoryDate(timestamp) {
  return new Date(timestamp).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

function buildSmoothPath(points) {
  if (points.length === 0) return '';
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`;

  let path = `M ${points[0].x} ${points[0].y}`;

  for (let i = 0; i < points.length - 1; i += 1) {
    const p0 = points[i - 1] || points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[i + 2] || p2;
    const cp1x = p1.x + (p2.x - p0.x) / 6;
    const cp1y = p1.y + (p2.y - p0.y) / 6;
    const cp2x = p2.x - (p3.x - p1.x) / 6;
    const cp2y = p2.y - (p3.y - p1.y) / 6;
    path += ` C ${cp1x} ${cp1y} ${cp2x} ${cp2y} ${p2.x} ${p2.y}`;
  }

  return path;
}

function computeMovingAverage(entries, windowDays = 7) {
  return entries.map((entry, i) => {
    const cutoff = entry.timestamp - windowDays * 24 * 60 * 60 * 1000;
    const window = entries.slice(0, i + 1).filter(e => e.timestamp >= cutoff);
    return window.reduce((sum, e) => sum + e.weight, 0) / window.length;
  });
}

function calculateChartGeometry(entries) {
  if (!entries.length) return null;

  const maValues = computeMovingAverage(entries);
  const weights = entries.map((entry) => entry.weight);
  const allValues = [...weights, ...maValues];
  const minWeight = Math.min(...allValues);
  const maxWeight = Math.max(...allValues);
  const baseRange = maxWeight - minWeight;
  const pad = baseRange < 0.6 ? 0.4 : baseRange * 0.15;
  const yMin = minWeight - pad;
  const yMax = maxWeight + pad;
  const yRange = yMax - yMin || 1;
  const plotWidth = CHART_WIDTH - PLOT.left - PLOT.right;
  const plotHeight = CHART_HEIGHT - PLOT.top - PLOT.bottom;
  const denominator = Math.max(entries.length - 1, 1);

  const points = entries.map((entry, index) => {
    const x = entries.length === 1
      ? PLOT.left + (plotWidth / 2)
      : PLOT.left + (index / denominator) * plotWidth;
    const y = PLOT.top + ((yMax - entry.weight) / yRange) * plotHeight;
    return { ...entry, x, y };
  });

  const yTicks = [0, 0.25, 0.5, 0.75, 1].map((ratio) => {
    const value = yMin + (1 - ratio) * yRange;
    const y = PLOT.top + ratio * plotHeight;
    return { value, y };
  });

  const xTickIndices = [...new Set([
    0,
    Math.round((points.length - 1) * 0.33),
    Math.round((points.length - 1) * 0.66),
    points.length - 1,
  ])];
  const xTicks = xTickIndices.map((index) => points[index]).filter(Boolean);

  const path = buildSmoothPath(points);
  const areaPath =
    points.length > 1
      ? `${path} L ${points[points.length - 1].x} ${CHART_HEIGHT - PLOT.bottom} L ${points[0].x} ${CHART_HEIGHT - PLOT.bottom} Z`
      : '';

  const maPoints = points.map((pt, i) => ({ ...pt, y: PLOT.top + ((yMax - maValues[i]) / yRange) * plotHeight }));
  const maPath = buildSmoothPath(maPoints);

  return { points, yTicks, xTicks, path, areaPath, maPath };
}

function getValueColor(value, colors) {
  if (value == null) return colors.subtext;
  if (Math.abs(value) < 0.05) return colors.subtext;
  return value < 0 ? '#00C170' : '#FF6B35';
}

function summaryCards(summary, unit) {
  return [
    { id: 'current', label: 'Current Weight', value: formatWeight(summary.currentWeight, unit), raw: summary.currentWeight },
    { id: 'prev', label: 'Change vs Previous', value: formatSigned(summary.changeFromPrevious, unit), raw: summary.changeFromPrevious },
    { id: 'trend', label: 'Weekly Trend', value: formatSigned(summary.weeklyTrend, unit), raw: summary.weeklyTrend },
    { id: 'week', label: 'This Week Change', value: formatSigned(summary.weekChange, unit), raw: summary.weekChange },
    { id: 'month', label: 'This Month Change', value: formatSigned(summary.monthChange, unit), raw: summary.monthChange },
    { id: 'total', label: 'Total Change', value: formatSigned(summary.totalChange, unit), raw: summary.totalChange },
  ];
}

function WeightChart({ entries, colors, unit }) {
  const chart = useMemo(() => calculateChartGeometry(entries), [entries]);

  if (!chart || !chart.points.length) {
    return (
      <View style={s.emptyChart}>
        <Text style={[s.emptyText, { color: colors.muted }]}>No body weight entries for this range yet.</Text>
      </View>
    );
  }

  const labelStride = chart.points.length <= 10 ? 1 : Math.ceil(chart.points.length / 6);

  // SVG props (stopColor, stroke, fill) do NOT accept PlatformColor (used by Monet theme).
  // Resolve to a plain hex string via withAlpha, which uses processColor as fallback.
  const accentHex = withAlpha(colors.accent, 1, '#FF4500').slice(0, 7);
  const subtextHex = withAlpha(colors.subtext || colors.muted, 1, '#888888').slice(0, 7);
  const faintHex = withAlpha(colors.faint, 1, '#333333').slice(0, 7);
  const cardHex = withAlpha(colors.card, 1, '#1C1C1E').slice(0, 7);
  const textHex = withAlpha(colors.text, 1, '#FFFFFF').slice(0, 7);
  const MA_COLOR = '#A0C4FF';

  return (
    <Svg width={CHART_WIDTH} height={CHART_HEIGHT}>
      <Defs>
        <LinearGradient id="lineFill" x1="0%" y1="0%" x2="0%" y2="100%">
          <Stop offset="0%" stopColor={accentHex} stopOpacity="0.35" />
          <Stop offset="100%" stopColor={accentHex} stopOpacity="0.04" />
        </LinearGradient>
      </Defs>

      {chart.yTicks.map((tick, index) => (
        <React.Fragment key={`y-${index}`}>
          <Line
            x1={PLOT.left}
            y1={tick.y}
            x2={CHART_WIDTH - PLOT.right}
            y2={tick.y}
            stroke={faintHex}
            strokeWidth={1}
            strokeDasharray="4,4"
          />
          <SvgText
            x={PLOT.left - 8}
            y={tick.y + 4}
            fontSize={10}
            textAnchor="end"
            fill={subtextHex}
          >
            {tick.value.toFixed(1)}
          </SvgText>
        </React.Fragment>
      ))}

      {chart.areaPath ? <Path d={chart.areaPath} fill="url(#lineFill)" /> : null}
      {chart.path ? <Path d={chart.path} fill="none" stroke={accentHex} strokeWidth={3} /> : null}
      {chart.maPath && chart.points.length >= 3 ? (
        <Path d={chart.maPath} fill="none" stroke={MA_COLOR} strokeWidth={1.5} strokeDasharray="5,3" opacity={0.75} />
      ) : null}

      {chart.points.map((point, index) => {
        const showValueLabel = index % labelStride === 0 || index === chart.points.length - 1;
        const isFirst = index === 0;
        const isLast = index === chart.points.length - 1;
        const labelAnchor = isFirst ? 'start' : (isLast ? 'end' : 'middle');
        const labelX = isFirst ? point.x + 4 : (isLast ? point.x - 4 : point.x);
        return (
          <React.Fragment key={`p-${point.id}`}>
            <Circle cx={point.x} cy={point.y} r={4.5} fill={cardHex} stroke={accentHex} strokeWidth={2} />
            {showValueLabel ? (
              <SvgText
                x={labelX}
                y={point.y - 9}
                fontSize={10}
                textAnchor={labelAnchor}
                fill={textHex}
                fontWeight="700"
              >
                {point.weight.toFixed(1)}
              </SvgText>
            ) : null}
          </React.Fragment>
        );
      })}

      {chart.xTicks.map((tick) => (
        <SvgText
          key={`x-${tick.id}`}
          x={tick.x}
          y={CHART_HEIGHT - 10}
          fontSize={10}
          textAnchor="middle"
          fill={subtextHex}
        >
          {formatDateLabel(tick.timestamp)}
        </SvgText>
      ))}

      <SvgText x={CHART_WIDTH - 4} y={14} fontSize={10} textAnchor="end" fill={subtextHex}>
        {unit}
      </SvgText>
      {chart.points.length < 2 ? (
        <SvgText x={CHART_WIDTH / 2} y={CHART_HEIGHT - 18} fontSize={10} textAnchor="middle" fill={subtextHex}>
          Add more entries for a smoother trend
        </SvgText>
      ) : null}
    </Svg>
  );
}

export default function BodyWeightScreen() {
  const { bodyWeight, add: logBodyWeight, remove: deleteBodyWeightEntry } = useWatermelonBodyMeasurements();
  const { settings } = useWatermelonSettings();
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;

  const [weightInput, setWeightInput] = useState('');
  const [range, setRange] = useState('30D');
  const [shareStatus, setShareStatus] = useState('');
  const [shareNode, setShareNode] = useState(null);

  const unit = settings?.weightUnit || 'kg';

  const allEntriesAsc = useMemo(() => normalizeBodyWeightEntries(bodyWeight), [bodyWeight]);
  const selectedEntriesAsc = useMemo(
    () => filterEntriesByRange(allEntriesAsc, range),
    [allEntriesAsc, range]
  );
  const chartEntries = useMemo(
    () => downsampleSeries(selectedEntriesAsc),
    [selectedEntriesAsc]
  );
  const summary = useMemo(
    () => calculateBodyWeightSummary(allEntriesAsc, selectedEntriesAsc),
    [allEntriesAsc, selectedEntriesAsc]
  );
  const cards = useMemo(() => summaryCards(summary, unit), [summary, unit]);
  const historyDesc = useMemo(
    () => [...allEntriesAsc].sort((a, b) => b.timestamp - a.timestamp),
    [allEntriesAsc]
  );

  const onLogWeight = async () => {
    const value = Number.parseFloat(weightInput);
    if (!Number.isFinite(value) || value < 20 || value > 400) {
      fireHaptic('invalidAction', { enabled: haptic });
      Alert.alert('Invalid weight', `Please enter a value between 20 and 400 ${unit}.`);
      return;
    }
    await logBodyWeight({ date: new Date().toISOString(), weight: value });
    setWeightInput('');
    fireHaptic('success', { enabled: haptic });
  };

  const onDeleteEntry = (entry) => {
    Alert.alert(
      'Delete entry?',
      `${entry.weight.toFixed(1)} ${unit} on ${formatHistoryDate(entry.timestamp)} will be removed.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            await deleteBodyWeightEntry(entry);
            fireHaptic('lightConfirm', { enabled: haptic });
          },
        },
      ]
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
      await Sharing.shareAsync(uri, { mimeType: 'image/png', dialogTitle: 'Share bodyweight progress' });
      fireHaptic('success', { enabled: haptic });
    } catch (e) {
      setShareStatus(`Share failed: ${String(e?.message || e)}`);
    }
  };

  return (
    <ScrollView
      style={[s.container, { backgroundColor: colors.bg }]}
      contentContainerStyle={s.content}
      keyboardShouldPersistTaps="handled"
    >
      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.muted }]}>LOG BODY WEIGHT</Text>
        <View style={s.inputRow}>
          <TextInput
            value={weightInput}
            onChangeText={setWeightInput}
            keyboardType="decimal-pad"
            placeholder={`0.0 ${unit}`}
            placeholderTextColor={colors.muted}
            style={[s.input, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.surface }]}
          />
          <TouchableOpacity style={[s.logButton, { backgroundColor: colors.accent }]} onPress={onLogWeight}>
            <Text style={s.logButtonText}>LOG</Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={s.rangeRow}>
        {BODY_WEIGHT_RANGES.map((option) => {
          const active = option === range;
          return (
            <TouchableOpacity
              key={option}
              style={[
                s.rangePill,
                {
                  borderColor: active ? colors.accent : colors.faint,
                  backgroundColor: active ? colors.accentSoft : colors.card,
                },
              ]}
              onPress={() => {
                setRange(option);
                fireHaptic('selection', { enabled: haptic });
              }}
            >
              <Text style={[s.rangeText, { color: active ? colors.accent : colors.subtext }]}>
                {option}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <TouchableOpacity style={[s.shareBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]} onPress={onShare}>
        <Ionicons name="share-social-outline" size={14} color={colors.accent} />
        <Text style={[s.shareBtnText, { color: colors.accent }]}>SHARE BODYWEIGHT CARD</Text>
      </TouchableOpacity>
      {shareStatus ? <Text style={[s.shareStatus, { color: colors.muted }]}>{shareStatus}</Text> : null}

      <View ref={setShareNode} collapsable={false}>
      <View style={s.summaryGrid}>
        {cards.map((card) => (
          <View
            key={card.id}
            style={[s.summaryCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
          >
            <Text style={[s.summaryLabel, { color: colors.muted }]}>{card.label}</Text>
            <Text style={[s.summaryValue, { color: getValueColor(card.raw, colors) }]}>{card.value}</Text>
          </View>
        ))}
      </View>

      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.muted }]}>WEIGHT TREND ({range})</Text>
        <WeightChart entries={chartEntries} colors={colors} unit={unit} />
        {chartEntries.length >= 3 ? (
          <View style={s.chartLegend}>
            <View style={s.legendItem}><View style={[s.legendDot, { backgroundColor: withAlpha(colors.accent, 1, '#FF4500').slice(0, 7) }]} /><Text style={[s.legendLabel, { color: colors.muted }]}>Daily</Text></View>
            <View style={s.legendItem}><View style={[s.legendDash, { borderColor: '#A0C4FF' }]} /><Text style={[s.legendLabel, { color: colors.muted }]}>7-day avg</Text></View>
          </View>
        ) : null}
      </View>
      </View>

      <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.cardTitle, { color: colors.muted }]}>HISTORY</Text>
        {historyDesc.length === 0 ? (
          <Text style={[s.emptyText, { color: colors.muted }]}>No body weight entries logged yet.</Text>
        ) : (
          historyDesc.map((entry) => (
            <View key={entry.id} style={[s.historyRow, { borderBottomColor: colors.faint }]}>
              <View>
                <Text style={[s.historyWeight, { color: colors.text }]}>
                  {entry.weight.toFixed(1)} {unit}
                </Text>
                <Text style={[s.historyDate, { color: colors.subtext }]}>
                  {formatHistoryDate(entry.timestamp)}
                </Text>
              </View>
              <TouchableOpacity
                onPress={() => onDeleteEntry(entry)}
                hitSlop={{ top: 10, right: 10, bottom: 10, left: 10 }}
              >
                <Ionicons name="trash-outline" size={18} color={colors.muted} />
              </TouchableOpacity>
            </View>
          ))
        )}
      </View>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, paddingBottom: 40 },
  card: {
    borderWidth: 1,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
  },
  cardTitle: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 2.2,
    marginBottom: 12,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 12,
    fontSize: 24,
    fontWeight: '800',
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  logButton: {
    paddingHorizontal: 18,
    paddingVertical: 14,
    borderRadius: 12,
  },
  logButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 1.6,
  },
  rangeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 12,
  },
  rangePill: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  rangeText: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
  },
  summaryGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 12,
  },
  summaryCard: {
    width: (Dimensions.get('window').width - 16 * 2 - 10 - 2) / 2,
    borderWidth: 1,
    borderRadius: 14,
    paddingVertical: 12,
    paddingHorizontal: 12,
  },
  summaryLabel: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
  },
  summaryValue: {
    marginTop: 8,
    fontSize: 18,
    fontWeight: '900',
  },
  emptyChart: {
    height: CHART_HEIGHT - 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    fontSize: 13,
    textAlign: 'center',
  },
  historyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 11,
    borderBottomWidth: 1,
  },
  historyWeight: {
    fontSize: 18,
    fontWeight: '800',
  },
  historyDate: {
    fontSize: 12,
    marginTop: 1,
  },
  chartLegend: { flexDirection: 'row', gap: 16, marginTop: 8, justifyContent: 'flex-end' },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  legendDot: { width: 8, height: 8, borderRadius: 4 },
  legendDash: { width: 16, height: 0, borderWidth: 1, borderStyle: 'dashed' },
  legendLabel: { fontSize: 10, fontWeight: '600' },
  shareBtn: { borderWidth: 1, borderRadius: 12, paddingVertical: 10, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8, marginBottom: 10 },
  shareBtnText: { fontSize: 11, fontWeight: '800', letterSpacing: 1.2 },
  shareStatus: { fontSize: 11, marginBottom: 8, textAlign: 'center' },
});

