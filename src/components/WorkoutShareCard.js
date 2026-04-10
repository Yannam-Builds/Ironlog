
import React, { forwardRef } from 'react';
import { View, Text, StyleSheet, Dimensions } from 'react-native';

const CARD_W = Dimensions.get('window').width - 32;

function getStreak(history) {
  if (!history || !history.length) return 0;
  const days = [...new Set(history.map(h => h.date?.split('T')[0]).filter(Boolean))].sort().reverse();
  if (!days.length) return 0;
  const today = new Date().toISOString().split('T')[0];
  const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];
  if (days[0] !== today && days[0] !== yesterday) return 0;
  let streak = 0, prev = days[0];
  for (const d of days) {
    const diff = (new Date(prev) - new Date(d)) / 86400000;
    if (diff <= 1) { streak++; prev = d; } else break;
  }
  return streak;
}

const WorkoutShareCard = forwardRef(function WorkoutShareCard({ workout, history, summaryText }, ref) {
  if (!workout) return null;
  const { dayName, duration, totalVolume, exercises, prs } = workout;
  const streak = getStreak(history);
  const mins = Math.round((duration || 0) / 60);
  const today = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

  return (
    <View ref={ref} style={s.card} collapsable={false}>
      {/* Header */}
      <View style={s.headerRow}>
        <Text style={s.logo}>IRONLOG</Text>
        <Text style={s.date}>{today}</Text>
      </View>

      <Text style={s.workoutName}>{dayName || 'WORKOUT'}</Text>

      {/* Stats */}
      <View style={s.statsRow}>
        {[
          { val: `${mins}m`, label: 'DURATION' },
          { val: (totalVolume || 0).toLocaleString(), label: 'KG LIFTED' },
          { val: `${streak}d`, label: 'STREAK' },
        ].map(({ val, label }) => (
          <View key={label} style={s.statBox}>
            <Text style={s.statVal}>{val}</Text>
            <Text style={s.statLabel}>{label}</Text>
          </View>
        ))}
      </View>

      {summaryText ? (
        <View style={s.funRow}>
          <Text style={s.funText}>{summaryText}</Text>
        </View>
      ) : null}

      {/* Exercises */}
      <View style={s.divider} />
      {(exercises || []).slice(0, 7).map((ex, i) => {
        const bestSet = (ex.sets || [])
          .filter(s => s.type !== 'warmup' && s.reps > 0)
          .reduce((best, s) => (!best || s.weight * s.reps > best.weight * best.reps ? s : best), null);
        const isPR = prs && prs[ex.name];
        return (
          <View key={i} style={s.exRow}>
            <Text style={s.exName} numberOfLines={1}>
              {isPR ? '🏆 ' : ''}{ex.name}
            </Text>
            {bestSet && (
              <Text style={s.exBest}>
                {bestSet.weight > 0 ? `${bestSet.weight}kg` : 'BW'}×{bestSet.reps}
              </Text>
            )}
          </View>
        );
      })}

      <Text style={s.watermark}>IRONLOG</Text>
    </View>
  );
});

export default WorkoutShareCard;

const s = StyleSheet.create({
  card: {
    width: CARD_W,
    backgroundColor: '#080808',
    borderWidth: 1,
    borderColor: '#1e1e1e',
    padding: 24,
  },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  logo: { fontSize: 13, fontWeight: '900', letterSpacing: 4, color: '#FF4500' },
  date: { fontSize: 11, color: '#444', letterSpacing: 1 },
  workoutName: { fontSize: 26, fontWeight: '900', color: '#f0f0f0', letterSpacing: -0.5, marginBottom: 16 },
  statsRow: { flexDirection: 'row', marginBottom: 14 },
  statBox: { flex: 1, alignItems: 'center' },
  statVal: { fontSize: 20, fontWeight: '900', color: '#FF4500' },
  statLabel: { fontSize: 8, letterSpacing: 2, color: '#444', marginTop: 2 },
  funRow: { backgroundColor: '#FF450011', borderWidth: 1, borderColor: '#FF450033', padding: 10, marginBottom: 14 },
  funText: { fontSize: 12, color: '#FF4500', fontWeight: '600', flex: 1 },
  divider: { height: 1, backgroundColor: '#1a1a1a', marginBottom: 10 },
  exRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 7, borderBottomWidth: 1, borderBottomColor: '#111' },
  exName: { fontSize: 13, fontWeight: '600', color: '#d0d0d0', flex: 1 },
  exBest: { fontSize: 13, fontWeight: '900', color: '#FF4500', marginLeft: 8 },
  watermark: { fontSize: 9, letterSpacing: 4, color: '#1e1e1e', textAlign: 'center', marginTop: 16 },
});
