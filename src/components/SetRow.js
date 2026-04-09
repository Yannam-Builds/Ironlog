
import React, { useState, useCallback } from 'react';
import { View, Text, TouchableOpacity, TextInput, StyleSheet } from 'react-native';
import { triggerHaptic } from '../services/hapticsEngine';

const SET_TYPES = ['normal', 'warmup', 'drop', 'failure', 'amrap'];

const TYPE_LABEL = {
  normal: 'W',
  warmup: 'WU',
  drop: 'DS',
  failure: 'F',
  amrap: 'AMRAP',
};

const TYPE_COLOR = {
  normal: '#FF4500',
  warmup: '#0080FF',
  drop: '#A020F0',
  failure: '#CC2222',
  amrap: '#FFD700',
};

function SetRow({ set, setIndex, exIndex, dispatch, effortTracking, hapticFeedback }) {
  const [noteOpen, setNoteOpen] = useState(false);

  const cycleType = useCallback(() => {
    const next = SET_TYPES[(SET_TYPES.indexOf(set.type || 'normal') + 1) % SET_TYPES.length];
    triggerHaptic('selection', { enabled: hapticFeedback }).catch(() => {});
    dispatch({ type: 'SET_TYPE', exIndex, setIndex, type: next });
  }, [set.type, exIndex, setIndex, dispatch, hapticFeedback]);

  const onNoteChange = useCallback((text) => {
    dispatch({ type: 'SET_NOTE', exIndex, setIndex, note: text });
  }, [exIndex, setIndex, dispatch]);

  const onRpeFocus = useCallback(() => {
    triggerHaptic('selection', { enabled: hapticFeedback }).catch(() => {});
  }, [hapticFeedback]);

  const onRpeChange = useCallback((text) => {
    const val = parseFloat(text);
    dispatch({ type: 'SET_RPE', exIndex, setIndex, rpe: isNaN(val) ? null : val });
  }, [exIndex, setIndex, dispatch]);

  const onRirChange = useCallback((text) => {
    const val = parseInt(text);
    dispatch({ type: 'SET_RIR', exIndex, setIndex, rir: isNaN(val) ? null : val });
  }, [exIndex, setIndex, dispatch]);

  const typeKey = set.type || 'normal';
  const color = TYPE_COLOR[typeKey] || TYPE_COLOR.normal;

  return (
    <View style={s.row}>
      <View style={s.left}>
        <Text style={s.setNum}>SET {setIndex + 1}</Text>
        <TouchableOpacity
          onPress={cycleType}
          style={[s.typeBadge, { borderColor: color + '66', backgroundColor: color + '18' }]}
          hitSlop={{ top: 8, bottom: 8, left: 4, right: 4 }}
        >
          <Text style={[s.typeText, { color }]}>{TYPE_LABEL[typeKey] || typeKey.toUpperCase()}</Text>
        </TouchableOpacity>
      </View>

      <View style={s.middle}>
        <Text style={s.valText}>
          {set.weight > 0 ? `${set.weight}kg` : 'BW'} × {set.reps}
        </Text>
        {set.orm > 0 ? <Text style={s.ormText}>~{set.orm}kg 1RM</Text> : null}
      </View>

      <View style={s.right}>
        {effortTracking === 'rpe' || effortTracking === 'both' ? (
          <View style={s.effortGroup}>
            <Text style={s.effortLabel}>RPE</Text>
            <TextInput
              style={s.effortInput}
              value={set.rpe != null ? String(set.rpe) : ''}
              onChangeText={onRpeChange}
              onFocus={onRpeFocus}
              keyboardType="decimal-pad"
              placeholder="—"
              placeholderTextColor="#2a2a2a"
              maxLength={4}
            />
          </View>
        ) : null}

        {effortTracking === 'rir' || effortTracking === 'both' ? (
          <View style={s.effortGroup}>
            <Text style={s.effortLabel}>RIR</Text>
            <TextInput
              style={s.effortInput}
              value={set.rir != null ? String(set.rir) : ''}
              onChangeText={onRirChange}
              onFocus={onRpeFocus}
              keyboardType="number-pad"
              placeholder="—"
              placeholderTextColor="#2a2a2a"
              maxLength={2}
            />
          </View>
        ) : null}

        <TouchableOpacity
          onPress={() => {
            triggerHaptic('selection', { enabled: hapticFeedback }).catch(() => {});
            setNoteOpen(o => !o);
          }}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <Text style={[s.noteIcon, set.note ? s.noteIconActive : null]}>✎</Text>
        </TouchableOpacity>
      </View>

      {noteOpen ? (
        <TextInput
          style={s.noteInput}
          value={set.note || ''}
          onChangeText={onNoteChange}
          placeholder="Set note..."
          placeholderTextColor="#2a2a2a"
          multiline
          autoFocus
        />
      ) : set.note ? (
        <TouchableOpacity onPress={() => {
          triggerHaptic('selection', { enabled: hapticFeedback }).catch(() => {});
          setNoteOpen(true);
        }}>
          <Text style={s.notePeek} numberOfLines={1}>{set.note}</Text>
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

export default React.memo(SetRow);

const s = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    paddingVertical: 5,
    gap: 8,
  },
  left: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    width: 78,
  },
  setNum: {
    fontSize: 9,
    letterSpacing: 1.5,
    color: '#333',
    width: 36,
  },
  typeBadge: {
    borderWidth: 1,
    paddingHorizontal: 5,
    paddingVertical: 2,
  },
  typeText: {
    fontSize: 8,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  middle: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  valText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#f0f0f0',
  },
  ormText: {
    fontSize: 11,
    color: '#444',
  },
  right: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  effortGroup: {
    alignItems: 'center',
  },
  effortLabel: {
    fontSize: 7,
    letterSpacing: 1,
    color: '#333',
    marginBottom: 1,
  },
  effortInput: {
    fontSize: 13,
    fontWeight: '700',
    color: '#f0f0f0',
    borderBottomWidth: 1,
    borderBottomColor: '#222',
    textAlign: 'center',
    width: 36,
    paddingVertical: 2,
  },
  noteIcon: {
    fontSize: 14,
    color: '#2a2a2a',
  },
  noteIconActive: {
    color: '#FF4500',
  },
  noteInput: {
    width: '100%',
    marginTop: 4,
    fontSize: 12,
    color: '#888',
    borderWidth: 1,
    borderColor: '#1a1a1a',
    padding: 6,
    minHeight: 32,
  },
  notePeek: {
    width: '100%',
    fontSize: 11,
    color: '#555',
    fontStyle: 'italic',
    marginTop: 2,
    paddingHorizontal: 2,
  },
});
