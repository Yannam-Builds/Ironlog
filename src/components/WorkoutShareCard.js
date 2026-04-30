
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

const WorkoutShareCard = forwardRef(function WorkoutShareCard({ workout, history, summaryText, accentColor = '#FF4500' }, ref) {
  if (!workout) return null;
  const { dayName, duration, totalVolume, exercises, prs } = workout;
  const streak = getStreak(history);
  const mins = Math.round((duration || 0) / 60);
  const today = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

  return (
    <View ref={ref} style={s.card} collapsable={false}>
      <View style={{ height: 3, backgroundColor: accentColor, width: '100%' }} />
      <View style={{ padding: 20 }}>
        {/* Header */}
        <View style={s.headerRow}>
          <Text style={{ fontWeight: '900', letterSpacing: 4, fontSize: 13 }}>
            <Text style={{ color: '#ffffff' }}>IRON</Text>
            <Text style={{ color: '#FF4500' }}>LOG</Text>
          </Text>
          <Text style={s.date}>{today}</Text>
        </View>

        <Text style={s.workoutName}>{dayName || 'WORKOUT'}</Text>
        <Text style={{ fontSize: 11, color: '#444', marginBottom: 16 }}>
          {(exercises || []).slice(0, 3).map(e => e.name).join(' · ')}
          {exercises?.length > 3 ? ` +${exercises.length - 3}` : ''}
        </Text>

        {/* Stats */}
        <View style={s.statsRow}>
          {[
            { val: `${mins}m`, label: 'DURATION', accent: false },
            { val: (totalVolume || 0).toLocaleString(), label: 'KG LIFTED', accent: true },
            { val: `${streak}d`, label: 'STREAK', accent: false },
          ].map(({ val, label, accent }) => (
            <View key={label} style={s.statBox}>
              <Text style={accent ? s.statValAccent : s.statVal}>{val}</Text>
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
        {(exercises || []).slice(0, 5).map((ex, i) => {
          const bestSet = (ex.sets || [])
            .filter(s => s.type !== 'warmup' && s.reps > 0)
            .reduce((best, s) => (!best || s.weight * s.reps > best.weight * best.reps ? s : best), null);
          const isPR = prs && prs[ex.name];
          return (
            <View key={i} style={s.exRow}>
              <Text style={s.exName} numberOfLines={1}>
                {isPR ? (
                  <Text style={{ backgroundColor: 'rgba(255,69,0,0.15)', color: '#FF6B35', borderRadius: 4, paddingHorizontal: 5, paddingVertical: 1, fontSize: 9, fontWeight: '800', marginRight: 6 }}>PR</Text>
                ) : null}{ex.name}
              </Text>
              {bestSet && (
                <Text style={s.exBest}>
                  {bestSet.weight > 0 ? `${bestSet.weight}kg` : 'BW'}×{bestSet.reps}
                </Text>
              )}
            </View>
          );
        })}
        {(exercises || []).length > 5 ? (
          <Text style={{ fontSize: 10, color: '#282828', paddingVertical: 4 }}>
            + {exercises.length - 5} more exercises
          </Text>
        ) : null}

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', borderTopWidth: 1, borderTopColor: '#161616', marginTop: 12, paddingTop: 12 }}>
          <Text style={{ fontWeight: '900', letterSpacing: 3, fontSize: 10 }}>
            <Text style={{ color: '#fff' }}>IRON</Text>
            <Text style={{ color: '#FF4500' }}>LOG</Text>
          </Text>
          <Text style={{ color: '#2a2a2a', fontSize: 9, letterSpacing: 1 }}>Track Every Rep</Text>
        </View>
      </View>
    </View>
  );
});

export default WorkoutShareCard;

const s = StyleSheet.create({
  card: { width: CARD_W, backgroundColor: '#080808', borderWidth: 1, borderColor: '#1e1e1e', padding: 0, borderRadius: 18, overflow: 'hidden' },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  date: { fontSize: 11, color: '#444', letterSpacing: 1 },
  workoutName: { fontSize: 26, fontWeight: '900', color: '#f0f0f0', letterSpacing: -0.5, marginBottom: 4 },
  statsRow: { flexDirection: 'row', marginBottom: 14, gap: 8 },
  statBox: { flex: 1, alignItems: 'center', backgroundColor: '#151515', borderRadius: 10, padding: 10, borderWidth: 1, borderColor: '#1e1e1e' },
  statVal: { fontSize: 18, fontWeight: '900', color: '#ffffff' },
  statValAccent: { fontSize: 18, fontWeight: '900', color: '#FF4500' },
  statLabel: { fontSize: 8, letterSpacing: 2, color: '#444', marginTop: 2 },
  funRow: { backgroundColor: '#FF450011', borderWidth: 1, borderColor: '#FF450033', padding: 10, marginBottom: 14 },
  funText: { fontSize: 12, color: '#FF4500', fontWeight: '600', flex: 1 },
  divider: { height: 1, backgroundColor: '#1a1a1a', marginBottom: 10 },
  exRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 7, borderBottomWidth: 1, borderBottomColor: '#111' },
  exName: { fontSize: 13, fontWeight: '600', color: '#d0d0d0', flex: 1 },
  exBest: { fontSize: 13, fontWeight: '900', color: '#FF4500', marginLeft: 8 },
});
