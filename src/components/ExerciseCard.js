import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useTheme } from '../context/ThemeContext';
import { withAlpha } from '../utils/colorUtils';
import { RADIUS } from '../utils/themes';

const MUSCLE_HUE = {
  Chest:     '#FF4500',
  Back:      '#0080FF',
  Shoulders: '#A855F7',
  Biceps:    '#3B8FFF',
  Triceps:   '#FF6B35',
  Legs:      '#30D158',
  Core:      '#FFD700',
  Cardio:    '#FF6B35',
  Mobility:  '#5AC8FA',
};

export default function ExerciseCard({
  exercise, onPress, onDelete, onMoveUp, onMoveDown,
  showControls = false, isFirst = false, isLast = false, style,
}) {
  const colors = useTheme();
  const muscleColor = MUSCLE_HUE[exercise.muscleGroup] || colors.muted;
  const s = makeStyles(colors);

  return (
    <TouchableOpacity style={[s.card, style]} onPress={onPress} activeOpacity={0.85}>
      <View style={[s.leftAccent, { backgroundColor: muscleColor }]} />
      <View style={s.content}>
        <View style={s.header}>
          <Text style={s.name} numberOfLines={1}>{exercise.name}</Text>
          {exercise.isHeavy && (
            <View style={[s.badge, { backgroundColor: withAlpha(colors.accent, 0.13), borderColor: colors.accent }]}>
              <Text style={[s.badgeText, { color: colors.accent }]}>HEAVY</Text>
            </View>
          )}
          {exercise.isWarmup && (
            <View style={[s.badge, { backgroundColor: withAlpha(colors.muted, 0.13), borderColor: colors.muted }]}>
              <Text style={[s.badgeText, { color: colors.muted }]}>WARM-UP</Text>
            </View>
          )}
        </View>

        <View style={s.metaRow}>
          <View style={[s.muscleBadge, { borderColor: muscleColor }]}>
            <Text style={[s.muscleText, { color: muscleColor }]}>{exercise.muscleGroup}</Text>
          </View>
          <Text style={s.setsReps}>{exercise.sets} x {exercise.reps}</Text>
          {exercise.defaultRestSeconds > 0 && (
            <Text style={s.restText}>{Math.round(exercise.defaultRestSeconds / 60)}m rest</Text>
          )}
        </View>

        {exercise.notes ? (
          <Text style={s.notes} numberOfLines={2}>{exercise.notes}</Text>
        ) : null}
      </View>

      {showControls && (
        <View style={s.controls}>
          <TouchableOpacity style={[s.controlBtn, isFirst && s.controlBtnDisabled]}
            onPress={onMoveUp} disabled={isFirst} hitSlop={8}>
            <Ionicons name="chevron-up" size={16} color={colors.muted} />
          </TouchableOpacity>
          <TouchableOpacity style={[s.controlBtn, isLast && s.controlBtnDisabled]}
            onPress={onMoveDown} disabled={isLast} hitSlop={8}>
            <Ionicons name="chevron-down" size={16} color={colors.muted} />
          </TouchableOpacity>
          <TouchableOpacity style={s.deleteBtn} onPress={onDelete} hitSlop={8} activeOpacity={0.7}>
            <Ionicons name="close" size={16} color="#FF453A" />
          </TouchableOpacity>
        </View>
      )}
    </TouchableOpacity>
  );
}

const makeStyles = (c) => StyleSheet.create({
  card: {
    flexDirection: 'row',
    backgroundColor: c.card,
    borderRadius: RADIUS.sm,
    marginVertical: 5,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: c.cardBorder,
  },
  leftAccent: { width: 4 },
  content: { flex: 1, padding: 12 },
  header: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6, marginBottom: 6 },
  name: { color: c.text, fontSize: 15, fontWeight: '700', flex: 1, letterSpacing: 0.2 },
  badge: { borderRadius: RADIUS.xs, paddingHorizontal: 6, paddingVertical: 2, borderWidth: 1 },
  badgeText: { fontSize: 10, fontWeight: '800', letterSpacing: 1 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  muscleBadge: { borderRadius: RADIUS.xs, paddingHorizontal: 6, paddingVertical: 2, borderWidth: 1 },
  muscleText: { fontSize: 11, fontWeight: '700', letterSpacing: 0.5 },
  setsReps: { color: c.text, fontSize: 13, fontWeight: '700', letterSpacing: 1 },
  restText: { color: c.muted, fontSize: 12 },
  notes: { color: c.muted, fontSize: 12, marginTop: 6, lineHeight: 16, fontStyle: 'italic' },
  controls: { flexDirection: 'column', justifyContent: 'center', alignItems: 'center', paddingHorizontal: 8, gap: 4 },
  controlBtn: { padding: 6 },
  controlBtnDisabled: { opacity: 0.25 },
  deleteBtn: { padding: 6, marginTop: 4 },
});
