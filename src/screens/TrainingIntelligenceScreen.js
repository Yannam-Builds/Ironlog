import React, { useMemo } from 'react';
import {
  View, Text, ScrollView, StyleSheet,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';
import useWatermelonHome from '../hooks/useWatermelonHome';
import { useTheme } from '../context/ThemeContext';
import EmptyState from '../components/ui/EmptyState';
import Button from '../components/ui/Button';
import { withAlpha } from '../utils/colorUtils';
import { TYPE, RADIUS } from '../utils/themes';
import { getBottomOverlaySpacing } from '../utils/bottomOverlaySpacing';
import { getExerciseIndex } from '../services/ExerciseLibraryService';
import {
  computeWeeklyVolumeByMuscle,
  computeVolumeLandmarks,
  computePRVelocity,
  computeMovementBalance,
  computeNeuralFatigue,
  computeBestPerformanceWindow,
  computeTrainingAge,
} from '../services/TrainingIntelligenceService';

// ── Tiny helpers ──────────────────────────────────────────────────────────────

const MUSCLE_LABELS = {
  chest: 'Chest',
  back: 'Back',
  legs: 'Legs',
  shoulders: 'Shoulders',
  arms: 'Arms',
  core: 'Core',
};

const STATUS_COLORS = {
  low:     '#FF9500',
  optimal: '#34C759',
  high:    '#FF453A',
};

const STATUS_LABELS = {
  low:     'Low',
  optimal: 'Optimal',
  high:    'High',
};

const WINDOW_ICONS = {
  morning:   'sunny-outline',
  afternoon: 'partly-sunny-outline',
  evening:   'moon-outline',
  night:     'moon',
};

function VolumeFill({ sets, target, color }) {
  const max = Math.max(target.max + 4, sets + 2, 1);
  const fillPct = Math.min(sets / max, 1);
  const optPct = target.optimal / max;
  const minPct = target.min / max;
  const maxPct = target.max / max;

  return (
    <View style={s.barTrack}>
      {/* optimal zone backdrop */}
      <View
        style={[
          s.barZone,
          {
            left: `${minPct * 100}%`,
            width: `${(maxPct - minPct) * 100}%`,
            backgroundColor: withAlpha('#34C759', 0.12),
          },
        ]}
      />
      {/* filled sets bar */}
      <View
        style={[
          s.barFill,
          {
            width: `${fillPct * 100}%`,
            backgroundColor: color,
            borderRadius: RADIUS.sm,
          },
        ]}
      />
    </View>
  );
}

function SectionCard({ title, icon, children, colors }) {
  return (
    <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
      <View style={s.cardHeader}>
        <Ionicons name={icon} size={16} color={colors.accent} />
        <Text style={[TYPE.eyebrow, { color: colors.muted, marginLeft: 6 }]}>{title}</Text>
      </View>
      {children}
    </View>
  );
}

function StatPill({ label, value, color, colors, compact = true }) {
  // When compact=true (compactAnalyticsNumbers setting), cap fontSize at 20.
  // When compact=false, use length-based scaling (32/24/18) for larger display.
  const valueStr = String(value);
  const fontSize = compact
    ? 20
    : valueStr.length > 5 ? 18 : valueStr.length > 3 ? 24 : 32;
  return (
    <View style={[s.statPill, { backgroundColor: withAlpha(color, 0.12), borderColor: withAlpha(color, 0.25) }]}>
      <Text style={{ fontSize, fontWeight: '900', color, lineHeight: fontSize + 4 }} numberOfLines={1} adjustsFontSizeToFit>{value}</Text>
      <Text style={[TYPE.micro, { color: colors.muted, marginTop: 2 }]}>{label}</Text>
    </View>
  );
}

// ── Main screen ───────────────────────────────────────────────────────────────

export default function TrainingIntelligenceScreen({ navigation }) {
  const { history, settings } = useWatermelonHome();
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const bottomSpacing = getBottomOverlaySpacing({ safeAreaBottom: insets.bottom, extra: 8 });
  const weightUnit = settings?.weightUnit || 'kg';
  const [exerciseIndex, setExerciseIndex] = React.useState({});

  React.useEffect(() => {
    let mounted = true;
    getExerciseIndex()
      .then((index) => {
        if (!mounted) return;
        if (Array.isArray(index)) {
          const keyed = {};
          index.forEach((exercise) => {
            if (exercise?.name) keyed[exercise.name] = exercise;
          });
          setExerciseIndex(keyed);
        } else if (index && typeof index === 'object') {
          setExerciseIndex(index);
        }
      })
      .catch(() => {});
    return () => { mounted = false; };
  }, []);

  // ── Computed metrics ───────────────────────────────────────────────────────
  const weeklyVolume = useMemo(
    () => computeWeeklyVolumeByMuscle(history, exerciseIndex),
    [history, exerciseIndex],
  );

  const landmarks = useMemo(
    () => computeVolumeLandmarks(weeklyVolume),
    [weeklyVolume],
  );

  const prVelocity = useMemo(
    () => computePRVelocity(history),
    [history],
  );

  const movementBalance = useMemo(
    () => computeMovementBalance(history, exerciseIndex),
    [history, exerciseIndex],
  );

  const neuralFatigue = useMemo(
    () => computeNeuralFatigue(history),
    [history],
  );

  const bestWindow = useMemo(
    () => computeBestPerformanceWindow(history),
    [history],
  );

  const trainingAge = useMemo(
    () => computeTrainingAge(history),
    [history],
  );

  // ── Empty state ────────────────────────────────────────────────────────────
  if (!Array.isArray(history) || history.length === 0) {
    return (
      <View style={[s.container, { backgroundColor: colors.bg }]}>
        <EmptyState
          icon="analytics-outline"
          title="No training data yet"
          subtitle="Log a few workouts and your athlete profile will build here automatically."
          ctaLabel="START LOGGING"
          onCta={() => navigation.navigate('ActiveWorkout')}
        />
      </View>
    );
  }

  // ── Push-pull ratio badge ──────────────────────────────────────────────────
  const ppRatio = movementBalance.pushPullRatio;
  const ppColor = ppRatio > 1.5 ? '#FF453A' : ppRatio < 0.7 ? '#FF9500' : '#34C759';
  const ppLabel = ppRatio > 1.5 ? 'Push-heavy' : ppRatio < 0.7 ? 'Pull-heavy' : 'Balanced';

  // ── Neural fatigue badge ───────────────────────────────────────────────────
  const fatigueColor = neuralFatigue.isFlagged ? '#FF453A' : '#34C759';
  const fatigueLabel = neuralFatigue.isFlagged
    ? `${neuralFatigue.consecutiveDays} heavy days in a row — consider deload`
    : neuralFatigue.consecutiveDays > 0
    ? `${neuralFatigue.consecutiveDays} consecutive heavy day${neuralFatigue.consecutiveDays > 1 ? 's' : ''}`
    : 'No consecutive heavy loading detected';

  // ── PR trend badge ─────────────────────────────────────────────────────────
  const prTrendIcon =
    prVelocity.trend === 'accelerating' ? 'trending-up' :
    prVelocity.trend === 'slowing'      ? 'trending-down' :
    'remove';
  const prTrendColor =
    prVelocity.trend === 'accelerating' ? '#34C759' :
    prVelocity.trend === 'slowing'      ? '#FF9500' :
    colors.muted;

  // ── Movement balance bar ───────────────────────────────────────────────────
  const movementKeys = ['push', 'pull', 'hinge', 'squat', 'core'];
  const movementColors = {
    push:  '#FF4500',
    pull:  '#0080FF',
    hinge: '#A020F0',
    squat: '#00C170',
    core:  '#FF8C00',
  };

  return (
    <ScrollView
      style={[s.container, { backgroundColor: colors.bg }]}
      contentContainerStyle={{ padding: 16, paddingBottom: bottomSpacing }}
      showsVerticalScrollIndicator={false}
    >

      {/* ── Athlete profile hero ───────────────────────────────────────── */}
      <View style={[s.hero, { backgroundColor: colors.accentSoft, borderColor: withAlpha(colors.accent, 0.3) }]}>
        <View style={[s.heroIcon, { backgroundColor: withAlpha(colors.accent, 0.15) }]}>
          <Ionicons name="person-outline" size={22} color={colors.accent} />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={[TYPE.eyebrow, { color: colors.accent }]}>ATHLETE PROFILE</Text>
          <Text style={[TYPE.title, { color: colors.text, marginTop: 2 }]}>{trainingAge.label}</Text>
          <Text style={[TYPE.body, { color: colors.subtext, marginTop: 2 }]}>{trainingAge.tip}</Text>
          {settings?.goalMode ? (
            <Text style={[TYPE.micro, { color: colors.muted, marginTop: 4, textTransform: 'uppercase', letterSpacing: 0.5 }]}>
              {settings.goalMode.replace('_', ' ')}
            </Text>
          ) : null}
        </View>
        <View style={[s.heroBadge, { backgroundColor: withAlpha(colors.accent, 0.1), borderColor: withAlpha(colors.accent, 0.25) }]}>
          <Text style={[TYPE.section, { color: colors.accent }]}>{trainingAge.historyMonths}</Text>
          <Text style={[TYPE.micro, { color: colors.muted }]}>months</Text>
        </View>
      </View>

      {/* ── Key stats row ──────────────────────────────────────────────── */}
      <View style={s.statsRow}>
        <StatPill
          label={`PRs last 30d`}
          value={String(prVelocity.last30)}
          color={prTrendColor}
          colors={colors}
          compact={settings?.compactAnalyticsNumbers !== false}
        />
        <StatPill
          label="PRs all-time"
          value={String(prVelocity.totalPRs)}
          color={colors.accent}
          colors={colors}
          compact={settings?.compactAnalyticsNumbers !== false}
        />
        <StatPill
          label="progress/mo"
          value={(() => {
            // monthlyProgressionKg is already in the user's stored weight unit (e1RM
            // delta computed from raw set weights). No kg→unit conversion needed.
            const p = trainingAge.monthlyProgressionKg;
            const abs = Math.abs(p);
            const rounded = abs < 1 ? p.toFixed(1) : String(Math.round(p));
            return `${p > 0 ? '+' : ''}${rounded}${weightUnit}`;
          })()}
          color={trainingAge.monthlyProgressionKg >= 0 ? '#34C759' : '#FF9500'}
          colors={colors}
          compact={settings?.compactAnalyticsNumbers !== false}
        />
      </View>

      {/* ── Weekly volume vs landmarks ─────────────────────────────────── */}
      <SectionCard title="WEEKLY VOLUME" icon="barbell-outline" colors={colors}>
        {Object.entries(landmarks).map(([muscle, { sets, status, target }]) => {
          const barColor = STATUS_COLORS[status];
          return (
            <View key={muscle} style={s.volumeRow}>
              <View style={s.volumeMeta}>
                <Text style={[TYPE.body, { color: colors.text, width: 90 }]}>{MUSCLE_LABELS[muscle]}</Text>
                <Text style={[TYPE.meta, { color: barColor, marginLeft: 'auto', marginRight: 8, width: 52, textAlign: 'right' }]}>
                  {sets} / {target.optimal} sets
                </Text>
                <View style={[s.statusDot, { backgroundColor: barColor }]} />
              </View>
              <VolumeFill sets={sets} target={target} color={barColor} />
            </View>
          );
        })}
        <View style={s.legendRow}>
          {(['low', 'optimal', 'high']).map(st => (
            <View key={st} style={s.legendItem}>
              <View style={[s.statusDot, { backgroundColor: STATUS_COLORS[st] }]} />
              <Text style={[TYPE.micro, { color: colors.muted, marginLeft: 4 }]}>{STATUS_LABELS[st]}</Text>
            </View>
          ))}
        </View>
      </SectionCard>

      {/* ── PR velocity ───────────────────────────────────────────────── */}
      <SectionCard title="PR VELOCITY" icon="trophy-outline" colors={colors}>
        <View style={s.prRow}>
          <View style={s.prBlock}>
            <Text style={[TYPE.display, { color: prTrendColor, lineHeight: 34 }]}>{prVelocity.last30}</Text>
            <Text style={[TYPE.meta, { color: colors.muted }]}>Last 30 days</Text>
          </View>
          <View style={[s.prDivider, { backgroundColor: colors.faint }]} />
          <View style={s.prBlock}>
            <Text style={[TYPE.display, { color: colors.subtext, lineHeight: 34 }]}>{prVelocity.prev30}</Text>
            <Text style={[TYPE.meta, { color: colors.muted }]}>Prev 30 days</Text>
          </View>
          <View style={[s.prDivider, { backgroundColor: colors.faint }]} />
          <View style={s.prBlock}>
            <Ionicons name={prTrendIcon} size={28} color={prTrendColor} />
            <Text style={[TYPE.meta, { color: prTrendColor, marginTop: 4, textTransform: 'capitalize' }]}>
              {prVelocity.trend}
            </Text>
          </View>
        </View>
      </SectionCard>

      {/* ── Movement balance ──────────────────────────────────────────── */}
      <SectionCard title="MOVEMENT BALANCE" icon="git-branch-outline" colors={colors}>
        <View style={s.movementBar}>
          {movementKeys.map(key => {
            const count = movementBalance[key] ?? 0;
            const total = Math.max(
              movementKeys.reduce((sum, k) => sum + (movementBalance[k] ?? 0), 0),
              1,
            );
            const flex = count / total;
            if (flex < 0.01) return null;
            return (
              <View
                key={key}
                style={[s.movementSegment, { flex, backgroundColor: movementColors[key] }]}
              />
            );
          })}
        </View>
        <View style={s.movementLegend}>
          {movementKeys.map(key => {
            const count = movementBalance[key] ?? 0;
            return (
              <View key={key} style={s.movementLegendItem}>
                <View style={[s.movementDot, { backgroundColor: movementColors[key] }]} />
                <Text style={[TYPE.micro, { color: colors.subtext }]}>{key}: {count}</Text>
              </View>
            );
          })}
        </View>
        <View style={[s.ppRatioBadge, { backgroundColor: withAlpha(ppColor, 0.1), borderColor: withAlpha(ppColor, 0.25) }]}>
          <Text style={[TYPE.meta, { color: ppColor }]}>
            Push : Pull  {isFinite(ppRatio) ? ppRatio.toFixed(2) : '∞'} — {ppLabel}
          </Text>
        </View>
      </SectionCard>

      {/* ── Neural fatigue ────────────────────────────────────────────── */}
      <SectionCard title="NEURAL FATIGUE" icon="flash-outline" colors={colors}>
        <View style={[s.fatigueRow, { backgroundColor: withAlpha(fatigueColor, 0.1), borderColor: withAlpha(fatigueColor, 0.22) }]}>
          <Ionicons
            name={neuralFatigue.isFlagged ? 'warning-outline' : 'checkmark-circle-outline'}
            size={20}
            color={fatigueColor}
            style={{ marginRight: 10 }}
          />
          <View style={{ flex: 1 }}>
            <Text style={[TYPE.body, { color: fatigueColor }]}>{fatigueLabel}</Text>
            {neuralFatigue.lastHeavyExercises.length > 0 && (
              <Text style={[TYPE.meta, { color: colors.muted, marginTop: 3 }]}>
                {neuralFatigue.lastHeavyExercises.slice(0, 3).join(' · ')}
              </Text>
            )}
          </View>
        </View>
        {neuralFatigue.isFlagged && (
          <>
            <Text style={[TYPE.meta, { color: colors.subtext, marginTop: 10 }]}>
              3+ consecutive days of heavy compound loading. Consider a lighter session or full rest day.
            </Text>
            <Button
              label="VIEW RECOVERY MAP"
              variant="secondary"
              size="small"
              onPress={() => navigation.navigate('RecoveryMap')}
              style={{ marginTop: 10, alignSelf: 'flex-start' }}
            />
          </>
        )}
      </SectionCard>

      {/* ── Best performance window ───────────────────────────────────── */}
      {bestWindow ? (
        <SectionCard title="PERFORMANCE WINDOW" icon="time-outline" colors={colors}>
          <View style={s.windowRow}>
            <View style={[s.windowIcon, { backgroundColor: withAlpha(colors.accent, 0.12) }]}>
              <Ionicons name={WINDOW_ICONS[bestWindow.bestWindow]} size={26} color={colors.accent} />
            </View>
            <View style={{ flex: 1, marginLeft: 14 }}>
              <Text style={[TYPE.section, { color: colors.text }]}>{bestWindow.windowLabel}</Text>
              <Text style={[TYPE.body, { color: colors.subtext, marginTop: 2 }]}>
                You hit the most PRs training at this time.
              </Text>
              <Text style={[TYPE.meta, { color: colors.muted, marginTop: 4 }]}>
                Based on {bestWindow.totalPRSessions} PR sessions
              </Text>
            </View>
            <View style={s.windowCounts}>
              {Object.entries(bestWindow.windowCounts).map(([w, c]) => (
                <View key={w} style={s.windowCountItem}>
                  <Ionicons name={WINDOW_ICONS[w]} size={12} color={w === bestWindow.bestWindow ? colors.accent : colors.muted} />
                  <Text style={[TYPE.micro, { color: w === bestWindow.bestWindow ? colors.accent : colors.muted, marginLeft: 2 }]}>{c}</Text>
                </View>
              ))}
            </View>
          </View>
        </SectionCard>
      ) : null}

      {/* ── CTA: AI Plan ──────────────────────────────────────────────── */}
      <View style={s.ctaRow}>
        <Button
          label="CREATE AI PLAN"
          icon="sparkles-outline"
          variant="primary"
          size="large"
          onPress={() => navigation.navigate('AIPlan')}
          style={{ flex: 1 }}
        />
        <Button
          label="PROGRAM INSIGHTS"
          icon="analytics-outline"
          variant="secondary"
          size="large"
          onPress={() => navigation.navigate('ProgramInsights')}
          style={{ flex: 1 }}
        />
      </View>

    </ScrollView>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────
const s = StyleSheet.create({
  container: { flex: 1 },

  // hero
  hero: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    marginBottom: 16,
    gap: 12,
  },
  heroIcon: {
    width: 44, height: 44, borderRadius: 22,
    alignItems: 'center', justifyContent: 'center',
  },
  heroBadge: {
    borderRadius: RADIUS.md,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 6,
    alignItems: 'center',
  },

  // stats row
  statsRow: { flexDirection: 'row', gap: 8, marginBottom: 16 },
  statPill: {
    flex: 1,
    borderRadius: RADIUS.md,
    borderWidth: 1,
    paddingVertical: 10,
    paddingHorizontal: 8,
    alignItems: 'center',
  },

  // section card
  card: {
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    padding: 16,
    marginBottom: 14,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 14,
  },

  // volume
  volumeRow: { marginBottom: 10 },
  volumeMeta: { flexDirection: 'row', alignItems: 'center', marginBottom: 5 },
  barTrack: {
    height: 8,
    borderRadius: 4,
    backgroundColor: '#ffffff10',
    overflow: 'hidden',
    position: 'relative',
  },
  barZone: { position: 'absolute', top: 0, bottom: 0 },
  barFill: { position: 'absolute', top: 0, bottom: 0, left: 0 },
  statusDot: { width: 7, height: 7, borderRadius: 3.5 },
  legendRow: { flexDirection: 'row', gap: 14, marginTop: 12 },
  legendItem: { flexDirection: 'row', alignItems: 'center' },

  // PR
  prRow: { flexDirection: 'row', alignItems: 'center' },
  prBlock: { flex: 1, alignItems: 'center', paddingVertical: 4 },
  prDivider: { width: 1, height: 48 },

  // movement
  movementBar: {
    height: 12,
    borderRadius: RADIUS.sm,
    overflow: 'hidden',
    flexDirection: 'row',
    marginBottom: 12,
  },
  movementSegment: { height: '100%' },
  movementLegend: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 10 },
  movementLegendItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  movementDot: { width: 8, height: 8, borderRadius: 4 },
  ppRatioBadge: {
    borderRadius: RADIUS.sm,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 6,
    alignSelf: 'flex-start',
  },

  // fatigue
  fatigueRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    padding: 12,
    borderRadius: RADIUS.md,
    borderWidth: 1,
  },

  // window
  windowRow: { flexDirection: 'row', alignItems: 'center' },
  windowIcon: {
    width: 52, height: 52, borderRadius: 26,
    alignItems: 'center', justifyContent: 'center',
  },
  windowCounts: { alignItems: 'flex-end', gap: 4 },
  windowCountItem: { flexDirection: 'row', alignItems: 'center' },

  // CTA
  ctaRow: { flexDirection: 'row', gap: 10, marginTop: 4 },
});

