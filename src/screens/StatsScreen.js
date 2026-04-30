import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, useWindowDimensions } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { LineChart } from 'react-native-gifted-charts';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import useWatermelonStats from '../hooks/useWatermelonStats';
import { useTheme } from '../context/ThemeContext';
import { formatWeightFromKg } from '../utils/weightUnits';
import { getBottomOverlaySpacing } from '../utils/bottomOverlaySpacing';
import { TYPE, RADIUS } from '../utils/themes';
import { withAlpha } from '../utils/colorUtils';

// ── helpers ──────────────────────────────────────────────────────────────────

function getStreak(history) {
  if (!history.length) return 0;
  const days = [...new Set(history.map(h => h.date.split('T')[0]))].sort().reverse();
  const today     = new Date().toISOString().split('T')[0];
  const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];
  if (days[0] !== today && days[0] !== yesterday) return 0;
  let streak = 0;
  let prev = days[0];
  for (const d of days) {
    const diff = (new Date(prev) - new Date(d)) / 86400000;
    if (diff <= 1) { streak++; prev = d; } else break;
  }
  return streak;
}

/** Parse a YYYY-MM-DD string at local noon to avoid UTC-midnight timezone rollback. */
function parseLocalDate(dateStr) {
  return new Date(dateStr + 'T12:00:00');
}

/** Epley 1RM estimate: weight × (1 + reps/30). Falls back to 5 reps if unknown. */
function estimateOneRM(weight, reps = 5) {
  return Math.round(weight * (1 + Math.max(1, reps) / 30));
}

// ── component ─────────────────────────────────────────────────────────────────

export default function StatsScreen({ navigation }) {
  const { history, pb, weightUnit } = useWatermelonStats();
  const clearPbs = () => {};
  const colors    = useTheme();
  const chartAccent = withAlpha(colors.accent, 1, '#FF4500').slice(0, 7);
  const chartFaint = withAlpha(colors.faint, 1, '#333333').slice(0, 7);
  const chartMuted = withAlpha(colors.muted, 1, '#888888').slice(0, 7);
  const { width } = useWindowDimensions();
  const insets        = useSafeAreaInsets();
  const bottomSpacing = getBottomOverlaySpacing({
    safeAreaBottom: insets.bottom,
    includeWorkoutPill: true,
    extra: 4,
  });

  // ── aggregate stats ──────────────────────────────────────────────────────
  const streak    = getStreak(history);
  const totalSets = history.reduce((a, h) => a + (h.sets || 0), 0);
  const avgDur    = history.length
    ? Math.round(history.reduce((a, h) => a + (h.duration || 0), 0) / history.length / 60)
    : 0;

  // ── 14-day chart ─────────────────────────────────────────────────────────
  const dayMap = {};
  history.forEach((h) => {
    const d = (h.date || '').split('T')[0];
    if (d) dayMap[d] = (dayMap[d] || 0) + 1;
  });

  const last14 = Array.from({ length: 14 }, (_, i) => {
    const d = new Date(Date.now() - (13 - i) * 86400000);
    return d.toISOString().split('T')[0];
  });

  // Show a label at indices 0, 4, 9, 13 — gives well-spaced anchors across 14 days.
  const LABEL_INDICES = new Set([0, 4, 9, 13]);
  const chartData = last14.map((day, idx) => {
    const [, mm, dd] = day.split('-');
    return {
      value: dayMap[day] || 0,
      label: LABEL_INDICES.has(idx) ? `${dd}/${mm}` : '',
    };
  });

  // Reserve enough horizontal space. Section padding is 16 each side → 32 total.
  const sectionPad = 16;
  const chartWidth = Math.max(280, width - sectionPad * 2 - 4);
  const pointSpacing = Math.floor((chartWidth - 40) / (chartData.length - 1));
  const chartMax = Math.max(4, ...chartData.map(p => p.value));

  // ── personal bests ───────────────────────────────────────────────────────
  const pbEntries = Object.entries(pb).sort(([, a], [, b]) => b - a);

  // ── stat pill data ───────────────────────────────────────────────────────
  const STATS = [
    { label: 'SESSIONS',  val: String(history.length) },
    { label: 'TOTAL SETS', val: String(totalSets) },
    { label: 'AVG TIME',  val: `${avgDur}m` },
    { label: 'STREAK',    val: `${streak}d` },
  ];

  return (
    <ScrollView
      style={[s.container, { backgroundColor: colors.bg }]}
      contentContainerStyle={{ paddingBottom: bottomSpacing }}
      scrollIndicatorInsets={{ bottom: bottomSpacing }}
    >
      {/* ── Quick-nav buttons ─────────────────────────────────────── */}
      <View style={[s.quickNav, { borderBottomColor: colors.faint }]}>
        {[
          { label: 'CALENDAR', icon: 'calendar-outline',  route: 'WorkoutCalendar' },
          { label: 'VOLUME',   icon: 'bar-chart-outline', route: 'VolumeAnalytics' },
          { label: 'BODY',     icon: 'body-outline',      route: 'BodyMeasurements' },
        ].map(({ label, icon, route }) => (
          <TouchableOpacity
            key={label}
            style={[s.navBtn, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
            onPress={() => navigation.navigate(route)}
          >
            <Ionicons name={icon} size={20} color={colors.accent} />
            <Text style={[s.navBtnText, { color: colors.text }]}>{label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* ── Key stats row ─────────────────────────────────────────── */}
      <View style={s.statsRow}>
        {STATS.map(({ label, val }) => (
          <View
            key={label}
            style={[s.statCard, {
              backgroundColor: colors.card,
              borderColor: colors.cardBorder,
            }]}
          >
            <Text
              style={[s.statVal, { color: colors.text }]}
              numberOfLines={1}
              adjustsFontSizeToFit
            >
              {val}
            </Text>
            <Text
              style={[s.statLabel, { color: colors.muted }]}
              numberOfLines={2}
              adjustsFontSizeToFit
            >
              {label}
            </Text>
          </View>
        ))}
      </View>

      {/* ── Performance stats ─────────────────────────────────────── */}
      <View style={[s.section, { borderBottomColor: colors.faint }]}>
        <Text style={[s.sectionLabel, { color: colors.muted }]}>PERFORMANCE STATS</Text>
        <TouchableOpacity
          style={[s.bodyCompLink, { borderColor: colors.faint, backgroundColor: colors.card }]}
          onPress={() => navigation.navigate('BodyWeight')}
        >
          <Ionicons name="body-outline" size={16} color={colors.accent} />
          <Text style={[s.bodyCompLinkText, { color: colors.text }]}>Open body composition stats</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.muted} />
        </TouchableOpacity>
      </View>

      {/* ── 14-day activity chart ─────────────────────────────────── */}
      <View style={[s.section, { borderBottomColor: colors.faint }]}>
        <Text style={[s.sectionLabel, { color: colors.muted }]}>LAST 14 DAYS</Text>
        <LineChart
          data={chartData}
          curved
          color={chartAccent}
          thickness={2}
          hideDataPoints
          yAxisColor={chartFaint}
          xAxisColor={chartFaint}
          xAxisLabelTextStyle={{ color: chartMuted, fontSize: 7, textAlign: 'center' }}
          yAxisTextStyle={{ color: chartMuted, fontSize: 9 }}
          spacing={pointSpacing}
          initialSpacing={4}
          endSpacing={4}
          noOfSections={4}
          maxValue={chartMax}
          rulesType="solid"
          rulesColor={chartFaint}
          rulesThickness={1}
          isAnimated
          animationDuration={200}
          disableScroll
          width={chartWidth}
          height={150}
          labelWidth={52}
          yAxisOffset={0}
          yAxisExtraHeight={4}
        />
      </View>

      {/* ── Personal bests ────────────────────────────────────────── */}
      {pbEntries.length > 0 && (
        <View style={s.section}>
          <View style={s.pbHeader}>
            <Text style={[s.sectionLabel, { color: colors.muted }]}>PERSONAL BESTS</Text>
            <TouchableOpacity onPress={clearPbs} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Text style={[s.resetText, { color: colors.accent }]}>RESET</Text>
            </TouchableOpacity>
          </View>

          {pbEntries.map(([k, v]) => {
            // Key format: "<planId/dayId>-<exerciseName>"
            const name = k.split('-').slice(1).join('-');
            const orm  = estimateOneRM(v);
            return (
              <View
                key={k}
                style={[s.pbRow, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
              >
                <View style={s.pbInfo}>
                  <Text style={[s.pbName, { color: colors.text }]} numberOfLines={1}>{name}</Text>
                  <Text style={[s.pbOrm, { color: colors.muted }]}>Est. 1RM: ~{formatWeightFromKg(orm, weightUnit)}</Text>
                </View>
                <Text style={[s.pbValue, { color: colors.accent }]}>
                  PR {formatWeightFromKg(v, weightUnit)}
                </Text>
              </View>
            );
          })}
        </View>
      )}

      {/* ── Empty state ───────────────────────────────────────────── */}
      {history.length === 0 && (
        <View style={s.emptyState}>
          <Ionicons name="barbell-outline" size={40} color={colors.muted} />
          <Text style={[s.emptyText, { color: colors.muted }]}>
            No workouts yet. Start one to see stats.
          </Text>
        </View>
      )}
    </ScrollView>
  );
}

// ── styles ────────────────────────────────────────────────────────────────────

const s = StyleSheet.create({
  container: { flex: 1 },

  // nav
  quickNav: {
    flexDirection: 'row',
    gap: 10,
    padding: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  navBtn: {
    flex: 1,
    borderWidth: 1,
    paddingVertical: 12,
    paddingHorizontal: 8,
    alignItems: 'center',
    gap: 6,
    borderRadius: RADIUS.lg,
  },
  navBtnText: {
    ...TYPE.micro,
    textAlign: 'center',
  },

  // stat cards
  statsRow: {
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  statCard: {
    flex: 1,
    borderRadius: RADIUS.lg,
    paddingVertical: 12,
    paddingHorizontal: 6,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 68,
  },
  statVal: {
    ...TYPE.title,
    textAlign: 'center',
    includeFontPadding: false,
  },
  statLabel: {
    fontSize: 8,
    fontWeight: '800',
    letterSpacing: 1.4,
    textAlign: 'center',
    marginTop: 4,
    lineHeight: 11,
  },

  // sections
  section: {
    paddingHorizontal: 16,
    paddingVertical: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  sectionLabel: {
    ...TYPE.eyebrow,
    marginBottom: 12,
  },

  // body comp link
  bodyCompLink: {
    borderWidth: 1,
    borderRadius: RADIUS.lg,
    paddingHorizontal: 12,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  bodyCompLinkText: {
    ...TYPE.body,
    flex: 1,
  },

  // PB section
  pbHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  resetText: {
    ...TYPE.button,
  },
  pbRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 14,
    borderWidth: 1,
    marginBottom: 8,
    borderRadius: RADIUS.lg,
    gap: 8,
  },
  pbInfo: {
    flex: 1,
    gap: 2,
  },
  pbName: {
    ...TYPE.body,
    fontWeight: '700',
  },
  pbOrm: {
    ...TYPE.meta,
  },
  pbValue: {
    ...TYPE.section,
    fontWeight: '800',
    flexShrink: 0,
  },

  // empty
  emptyState: {
    padding: 48,
    alignItems: 'center',
    gap: 12,
  },
  emptyText: {
    ...TYPE.body,
    textAlign: 'center',
  },
});

