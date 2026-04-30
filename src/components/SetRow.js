import React, { useState, useCallback, useEffect } from 'react';
import { View, Text, TouchableOpacity, TextInput, StyleSheet } from 'react-native';
import ReAnimated, {
  useSharedValue,
  withSpring,
  withSequence,
  withTiming,
  useAnimatedStyle,
} from 'react-native-reanimated';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useTheme } from '../context/ThemeContext';
import { fireHaptic } from '../services/hapticsEngine';
import { formatWeightFromKg } from '../utils/weightUnits';
import { withAlpha } from '../utils/colorUtils';

const SET_TYPES = ['normal', 'warmup', 'drop', 'failure', 'amrap'];

const TYPE_LABEL = {
  normal: 'W',
  warmup: 'WU',
  drop: 'DS',
  failure: 'F',
  amrap: 'AMRAP',
};

const makeStyles = (c) => StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    paddingVertical: 10,
    minHeight: 60,
    gap: 8,
    borderBottomWidth: 1,
    borderBottomColor: c.faint,
    overflow: 'hidden',
  },
  left: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    width: 92,
  },
  setNum: {
    fontSize: 10,
    letterSpacing: 1.2,
    color: c.muted,
    width: 44,
    fontWeight: '700',
  },
  typeBadge: {
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
    minWidth: 36,
    alignItems: 'center',
  },
  typeText: {
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 0.7,
  },
  middle: {
    flex: 1,
    flexBasis: 0,
    minWidth: 0,
    flexDirection: 'column',
    alignItems: 'flex-start',
    justifyContent: 'center',
    gap: 1,
    paddingRight: 8,
  },
  valText: {
    fontSize: 17,
    fontWeight: '800',
    color: c.text,
    lineHeight: 20,
  },
  ormText: {
    fontSize: 10,
    color: c.muted,
    maxWidth: 96,
    lineHeight: 12,
  },
  right: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  effortGroup: {
    alignItems: 'center',
    minWidth: 40,
  },
  effortLabel: {
    fontSize: 8,
    letterSpacing: 1.2,
    color: c.muted,
    marginBottom: 2,
    fontWeight: '700',
  },
  effortInput: {
    width: 36,
    height: 32,
    borderWidth: 1,
    borderRadius: 6,
    borderColor: c.faint,
    color: c.text,
    fontSize: 12,
    fontWeight: '700',
    textAlign: 'center',
    paddingVertical: 2,
    backgroundColor: c.surface,
  },
  iconBtn: {
    width: 32,
    height: 32,
    borderWidth: 1,
    borderColor: c.faint,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: c.surface,
  },
  noteInput: {
    width: '100%',
    borderWidth: 1,
    borderColor: c.faint,
    borderRadius: 8,
    backgroundColor: c.surface,
    color: c.text,
    padding: 10,
    fontSize: 13,
    marginTop: 8,
    minHeight: 40,
  },
  notePeek: {
    marginTop: 6,
    width: '100%',
    fontSize: 11,
    color: c.muted,
    paddingBottom: 2,
  },
  editRow: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
    marginTop: 8,
    paddingTop: 4,
  },
  editGroup: {
    flex: 1,
  },
  editLabel: {
    fontSize: 9,
    letterSpacing: 1.2,
    color: c.muted,
    marginBottom: 3,
  },
  editInput: {
    borderWidth: 1,
    borderColor: c.faint,
    borderRadius: 8,
    backgroundColor: c.surface,
    paddingVertical: 8,
    paddingHorizontal: 8,
    color: c.text,
    fontSize: 16,
    fontWeight: '800',
    textAlign: 'center',
  },
  editSaveBtn: {
    backgroundColor: c.accent,
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  editSaveText: {
    color: c.textOnAccent,
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1.3,
  },
});

function ExpandRow({ visible, children }) {
  const anim = useSharedValue(visible ? 1 : 0);
  useEffect(() => {
    anim.value = withSpring(visible ? 1 : 0, { stiffness: 360, damping: 32, mass: 0.6 });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);
  const style = useAnimatedStyle(() => ({
    opacity: anim.value,
    // maxHeight collapses both visual AND layout height (scaleY only does visual)
    maxHeight: Math.max(0, anim.value) * 400,
    overflow: 'hidden',
    // Hide from accessibility/layout when fully collapsed
    pointerEvents: anim.value === 0 ? 'none' : 'auto',
  }));
  // Keep mounted so the spring collapse animation can play out
  return <ReAnimated.View style={style}>{children}</ReAnimated.View>;
}

function formatDuration(secondsValue) {
  const totalSeconds = Math.max(0, Math.round(Number(secondsValue) || 0));
  const mm = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
  const ss = String(totalSeconds % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

function SetRow({ set, setIndex, exIndex, dispatch, effortTracking, hapticFeedback, weightUnit = 'kg', trackingType = 'weight_reps' }) {
  const colors = useTheme();
  const s = makeStyles(colors);
  const [noteOpen, setNoteOpen] = useState(false);

  const bloomOpacity = useSharedValue(0);
  useEffect(() => {
    // Bloom on mount — signals a new set was just added
    bloomOpacity.value = withSequence(
      withTiming(1, { duration: 120 }),
      withTiming(0, { duration: 450 }),
    );
  }, []); // eslint-disable-line

  const bloomStyle = useAnimatedStyle(() => ({
    opacity: bloomOpacity.value,
  }));
  const [editOpen, setEditOpen] = useState(false);
  const [editWeight, setEditWeight] = useState(String(set.weight ?? ''));
  const [editReps, setEditReps] = useState(String(set.reps ?? ''));

  const cycleType = useCallback(() => {
    const next = SET_TYPES[(SET_TYPES.indexOf(set.type || 'normal') + 1) % SET_TYPES.length];
    fireHaptic('selection', { enabled: hapticFeedback });
    dispatch({ type: 'SET_TYPE', exIndex, setIndex, type: next });
  }, [set.type, exIndex, setIndex, dispatch, hapticFeedback]);

  const onNoteChange = useCallback((text) => {
    dispatch({ type: 'SET_NOTE', exIndex, setIndex, note: text });
  }, [exIndex, setIndex, dispatch]);

  const onRpeFocus = useCallback(() => {
    fireHaptic('selection', { enabled: hapticFeedback });
  }, [hapticFeedback]);

  const onRpeChange = useCallback((text) => {
    const val = parseFloat(text);
    dispatch({ type: 'SET_RPE', exIndex, setIndex, rpe: isNaN(val) ? null : val });
  }, [exIndex, setIndex, dispatch]);

  const onRirChange = useCallback((text) => {
    const val = parseInt(text, 10);
    dispatch({ type: 'SET_RIR', exIndex, setIndex, rir: isNaN(val) ? null : val });
  }, [exIndex, setIndex, dispatch]);

  const toggleEdit = useCallback(() => {
    setEditWeight(String(set.weight ?? ''));
    setEditReps(String(set.reps ?? ''));
    setEditOpen((prev) => !prev);
    fireHaptic('selection', { enabled: hapticFeedback });
  }, [set.weight, set.reps, hapticFeedback]);

  const saveEdit = useCallback(() => {
    const weight = parseFloat(editWeight);
    const reps = parseInt(editReps, 10);
    dispatch({
      type: 'UPDATE_SET',
      exIndex,
      setIndex,
      weight: Number.isFinite(weight) ? weight : 0,
      reps: Number.isFinite(reps) ? reps : 0,
    });
    setEditOpen(false);
    fireHaptic('lightConfirm', { enabled: hapticFeedback });
  }, [dispatch, exIndex, setIndex, editWeight, editReps, hapticFeedback]);

  const deleteSet = useCallback(() => {
    dispatch({ type: 'DELETE_SET', exIndex, setIndex });
    fireHaptic('selection', { enabled: hapticFeedback });
  }, [dispatch, exIndex, setIndex, hapticFeedback]);

  const typeKey = set.type || 'normal';
  const typeColorMap = {
    normal:  colors.accent,
    warmup:  '#3B8FFF',
    drop:    '#A855F7',
    failure: '#FF453A',
    amrap:   colors.gold || '#FFD700',
  };
  const color = typeColorMap[typeKey] || colors.accent;

  const isTimeBased = String(trackingType || '').startsWith('duration');
  const durationSec = Number(set.durationSec || set.reps || 0);
  const displayValue = isTimeBased
    ? `${formatDuration(durationSec)}${set.weight > 0 ? ` · ${set.weight}` : ''}`
    : `${set.weight > 0 ? formatWeightFromKg(set.weight, weightUnit) : 'BW'} x ${set.reps}`;

  return (
    <View style={s.row}>
      <ReAnimated.View
        pointerEvents="none"
        style={[StyleSheet.absoluteFillObject, bloomStyle, { backgroundColor: withAlpha(colors.accent, 0.18) }]}
      />
      <View style={s.left}>
        <Text style={s.setNum}>SET {setIndex + 1}</Text>
        <TouchableOpacity
          onPress={cycleType}
          style={[s.typeBadge, { borderColor: withAlpha(color, 0.4), backgroundColor: withAlpha(color, 0.09) }]}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <Text style={[s.typeText, { color }]}>{TYPE_LABEL[typeKey] || typeKey.toUpperCase()}</Text>
        </TouchableOpacity>
      </View>

      <View style={s.middle}>
        <Text style={s.valText}>{displayValue}</Text>
        {set.orm > 0 ? (
          <Text style={s.ormText} numberOfLines={1} ellipsizeMode="tail">
            ~{formatWeightFromKg(set.orm, weightUnit)} 1RM
          </Text>
        ) : null}
      </View>

      <View style={s.right}>
        {(effortTracking === 'rpe' || effortTracking === 'both') ? (
          <View style={s.effortGroup}>
            <Text style={s.effortLabel}>RPE</Text>
            <TextInput
              style={s.effortInput}
              value={set.rpe != null ? String(set.rpe) : ''}
              onChangeText={onRpeChange}
              onFocus={onRpeFocus}
              keyboardType="decimal-pad"
              placeholder="-"
              placeholderTextColor={colors.muted}
              maxLength={4}
            />
          </View>
        ) : null}

        {(effortTracking === 'rir' || effortTracking === 'both') ? (
          <View style={s.effortGroup}>
            <Text style={s.effortLabel}>RIR</Text>
            <TextInput
              style={s.effortInput}
              value={set.rir != null ? String(set.rir) : ''}
              onChangeText={onRirChange}
              onFocus={onRpeFocus}
              keyboardType="number-pad"
              placeholder="-"
              placeholderTextColor={colors.muted}
              maxLength={2}
            />
          </View>
        ) : null}

        <TouchableOpacity
          onPress={() => {
            fireHaptic('selection', { enabled: hapticFeedback });
            setNoteOpen((open) => !open);
          }}
          style={[s.iconBtn, { borderColor: noteOpen || set.note ? withAlpha(colors.accent, 0.4) : colors.faint }]}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <Ionicons name="chatbox-ellipses-outline" size={16} color={set.note ? colors.accent : colors.muted} />
        </TouchableOpacity>

        <TouchableOpacity style={[s.iconBtn, { borderColor: editOpen ? withAlpha(colors.accent, 0.4) : colors.faint }]} onPress={toggleEdit} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <Ionicons name="create-outline" size={16} color={editOpen ? colors.accent : colors.muted} />
        </TouchableOpacity>

        <TouchableOpacity style={s.iconBtn} onPress={deleteSet} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <Ionicons name="trash-outline" size={16} color="#FF453A" />
        </TouchableOpacity>
      </View>

      <ExpandRow visible={editOpen}>
        <View style={s.editRow}>
          <View style={s.editGroup}>
            <Text style={s.editLabel}>{String(weightUnit || 'kg').toUpperCase()}</Text>
            <TextInput
              style={s.editInput}
              value={editWeight}
              onChangeText={setEditWeight}
              keyboardType="decimal-pad"
              placeholder="0"
              placeholderTextColor={colors.muted}
            />
          </View>
          <View style={s.editGroup}>
            <Text style={s.editLabel}>REPS</Text>
            <TextInput
              style={s.editInput}
              value={editReps}
              onChangeText={setEditReps}
              keyboardType="number-pad"
              placeholder="0"
              placeholderTextColor={colors.muted}
            />
          </View>
          <TouchableOpacity style={s.editSaveBtn} onPress={saveEdit}>
            <Text style={s.editSaveText}>SAVE</Text>
          </TouchableOpacity>
        </View>
      </ExpandRow>

      <ExpandRow visible={noteOpen}>
        <TextInput
          style={s.noteInput}
          value={set.note || ''}
          onChangeText={onNoteChange}
          placeholder="Set note..."
          placeholderTextColor={colors.muted}
          multiline
          autoFocus={noteOpen}
        />
      </ExpandRow>
      {!noteOpen && set.note ? (
        <TouchableOpacity
          onPress={() => {
            fireHaptic('selection', { enabled: hapticFeedback });
            setNoteOpen(true);
          }}
        >
          <Text style={[s.notePeek, { color: colors.muted }]} numberOfLines={1}>{set.note}</Text>
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

export default React.memo(SetRow);
