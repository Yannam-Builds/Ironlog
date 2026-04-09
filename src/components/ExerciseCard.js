import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

const MUSCLE_COLORS = {
  Chest: '#FF4500',
  Back: '#0080FF',
  Shoulders: '#A020F0',
  Biceps: '#0080FF',
  Triceps: '#FF4500',
  Legs: '#00C170',
  Core: '#FFD700',
  Cardio: '#FF6B35',
  Mobility: '#00BCD4',
};

export default function ExerciseCard({
  exercise,
  onPress,
  onDelete,
  onMoveUp,
  onMoveDown,
  showControls = false,
  isFirst = false,
  isLast = false,
  style,
}) {
  const muscleColor = MUSCLE_COLORS[exercise.muscleGroup] || '#666';

  return (
    <TouchableOpacity
      style={[styles.card, style]}
      onPress={onPress}
      activeOpacity={0.8}
    >
      <View style={styles.leftAccent} backgroundColor={muscleColor} />

      <View style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.name} numberOfLines={1}>
            {exercise.name}
          </Text>
          {exercise.isHeavy && (
            <View style={styles.heavyBadge}>
              <Text style={styles.heavyText}>HEAVY</Text>
            </View>
          )}
          {exercise.isWarmup && (
            <View style={styles.warmupBadge}>
              <Text style={styles.warmupText}>WARM-UP</Text>
            </View>
          )}
        </View>

        <View style={styles.metaRow}>
          <View style={[styles.muscleBadge, { borderColor: muscleColor }]}>
            <Text style={[styles.muscleText, { color: muscleColor }]}>
              {exercise.muscleGroup}
            </Text>
          </View>
          <Text style={styles.setsReps}>
            {exercise.sets} × {exercise.reps}
          </Text>
          {exercise.defaultRestSeconds > 0 && (
            <Text style={styles.restText}>
              {Math.round(exercise.defaultRestSeconds / 60)}m rest
            </Text>
          )}
        </View>

        {exercise.notes ? (
          <Text style={styles.notes} numberOfLines={2}>
            {exercise.notes}
          </Text>
        ) : null}
      </View>

      {showControls && (
        <View style={styles.controls}>
          <TouchableOpacity
            style={[styles.controlBtn, isFirst && styles.controlBtnDisabled]}
            onPress={onMoveUp}
            disabled={isFirst}
          >
            <Text style={styles.controlIcon}>▲</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.controlBtn, isLast && styles.controlBtnDisabled]}
            onPress={onMoveDown}
            disabled={isLast}
          >
            <Text style={styles.controlIcon}>▼</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.deleteBtn} onPress={onDelete}>
            <Text style={styles.deleteIcon}>✕</Text>
          </TouchableOpacity>
        </View>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    backgroundColor: '#141414',
    borderRadius: 10,
    marginVertical: 5,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#1e1e1e',
  },
  leftAccent: {
    width: 4,
  },
  content: {
    flex: 1,
    padding: 12,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 6,
    marginBottom: 6,
  },
  name: {
    color: '#f0f0f0',
    fontSize: 15,
    fontWeight: '600',
    flex: 1,
    fontFamily: 'BarlowCondensed_600SemiBold',
    letterSpacing: 0.5,
  },
  heavyBadge: {
    backgroundColor: '#FF450022',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderWidth: 1,
    borderColor: '#FF4500',
  },
  heavyText: {
    color: '#FF4500',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
  },
  warmupBadge: {
    backgroundColor: '#66666622',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderWidth: 1,
    borderColor: '#666',
  },
  warmupText: {
    color: '#666',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  muscleBadge: {
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderWidth: 1,
  },
  muscleText: {
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.5,
  },
  setsReps: {
    color: '#f0f0f0',
    fontSize: 13,
    fontWeight: '700',
    fontFamily: 'BarlowCondensed_700Bold',
    letterSpacing: 1,
  },
  restText: {
    color: '#666',
    fontSize: 12,
  },
  notes: {
    color: '#666',
    fontSize: 12,
    marginTop: 6,
    lineHeight: 16,
    fontStyle: 'italic',
  },
  controls: {
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 8,
    gap: 4,
  },
  controlBtn: {
    padding: 6,
  },
  controlBtnDisabled: {
    opacity: 0.2,
  },
  controlIcon: {
    color: '#666',
    fontSize: 14,
  },
  deleteBtn: {
    padding: 6,
    marginTop: 4,
  },
  deleteIcon: {
    color: '#CC2222',
    fontSize: 14,
  },
});
