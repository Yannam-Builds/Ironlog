import React, { useState, useRef, useEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { formatDuration, calculateVolume } from '../utils/calculations';
import useWatermelonAppData from '../hooks/useWatermelonAppData';
import { formatVolumeFromKg, formatWeightFromKg } from '../utils/weightUnits';
import GoldConfettiBurst from '../components/GoldConfettiBurst';
import { useTheme } from '../context/ThemeContext';
import { withAlpha } from '../utils/colorUtils';
import { RADIUS } from '../utils/themes';
import { resolveCelebrationConfig } from '../utils/celebration';
import Ionicons from 'react-native-vector-icons/Ionicons';

export default function WorkoutSummaryScreen({ route, navigation }) {
  const { settings, updateHistoryEntry } = useWatermelonAppData();
  const colors = useTheme();
  const weightUnit = settings?.weightUnit || 'kg';
  const { session, prsHit = [], prevSession } = route.params;
  const [sessionNote, setSessionNote] = useState(session?.summaryText || '');
  const saveTimerRef = useRef(null);
  const celebration = resolveCelebrationConfig(settings);
  const baseCount = celebration.style === 'fireworks'
    ? 66
    : celebration.style === 'fountain'
      ? 52
      : celebration.style === 'wave'
        ? 58
        : 44;

  const saveNote = (text) => {
    if (!session?.id || !updateHistoryEntry) return;
    clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      updateHistoryEntry({ ...session, summaryText: text.trim() || null });
    }, 600);
  };

  useEffect(() => () => clearTimeout(saveTimerRef.current), []);

  const completedSets = session.exercises.reduce((sum, ex) => sum + ex.sets.filter(s => s.completed).length, 0);
  const totalSets = session.exercises.reduce((sum, ex) => sum + ex.sets.length, 0);
  const completedExercises = session.exercises.filter(ex => ex.sets.some(s => s.completed)).length;

  const prevVolume = prevSession
    ? prevSession.exercises.reduce((sum, ex) => sum + calculateVolume(ex.sets), 0)
    : null;
  const volumeDiff = prevVolume !== null ? session.totalVolume - prevVolume : null;
  const volumeDiffPct = prevVolume && prevVolume > 0
    ? Math.round((volumeDiff / prevVolume) * 100)
    : null;

  const styles = makeStyles(colors);

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      {celebration.enabled ? (
        <GoldConfettiBurst
          enabled
          fullScreen
          variant={celebration.style}
          colorMode={celebration.colorMode}
          count={prsHit.length > 0 ? baseCount : Math.round(baseCount * 0.58)}
          themeColors={colors}
        />
      ) : null}
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.celebHeader}>
          <View style={styles.celebPill}>
            <Text style={styles.celebIcon}>{prsHit.length > 0 ? 'PR' : 'DONE'}</Text>
          </View>
          <Text style={styles.celebTitle}>{prsHit.length > 0 ? 'PRS LANDED' : 'SESSION LOCKED IN'}</Text>
          <Text style={styles.sessionDate}>
            {new Date(session.date).toLocaleDateString('en-US', {
              weekday: 'long',
              month: 'long',
              day: 'numeric',
            })}
          </Text>
          <Text style={styles.sessionTag}>
            {prsHit.length > 0 ? 'Clean work. New peaks.' : 'Solid work. Keep the momentum.'}
          </Text>
        </View>

        <View style={styles.statsGrid}>
          <View style={styles.statCard}>
            <Text style={[styles.statValue, { color: colors.text }]}>{formatDuration(session.duration)}</Text>
            <Text style={styles.statLabel}>DURATION</Text>
          </View>
          <View style={[styles.statCard, { borderColor: withAlpha(colors.accent, 0.3) }]}>
            <Text style={[styles.statValue, { color: colors.accent }]}>
              {Math.round((weightUnit === 'lbs' ? session.totalVolume * 2.2046226218 : session.totalVolume)).toLocaleString()}
            </Text>
            <Text style={styles.statLabel}>VOLUME ({weightUnit})</Text>
          </View>
          <View style={styles.statCard}>
            <Text style={[styles.statValue, { color: colors.text }]}>{completedSets}/{totalSets}</Text>
            <Text style={styles.statLabel}>SETS</Text>
          </View>
          <View style={styles.statCard}>
            <Text style={[styles.statValue, { color: colors.text }]}>{completedExercises}</Text>
            <Text style={styles.statLabel}>EXERCISES</Text>
          </View>
        </View>

        {volumeDiff !== null && (
          <View style={styles.compareCard}>
            <Text style={styles.compareLabel}>VS LAST SESSION</Text>
            <Text style={[styles.compareValue, { color: volumeDiff >= 0 ? colors.accent : colors.accent }]}>
              {volumeDiff >= 0 ? '+' : ''}{formatVolumeFromKg(Math.abs(volumeDiff), weightUnit)}
              {volumeDiffPct !== null ? ` (${volumeDiff >= 0 ? '+' : ''}${volumeDiffPct}%)` : ''}
            </Text>
          </View>
        )}

        {prsHit.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>PERSONAL RECORDS</Text>
            {prsHit.map(name => (
              <View key={name} style={styles.prCard}>
                <Ionicons name="trophy" size={18} color={colors.accent} />
                <Text style={styles.prName}>{name}</Text>
              </View>
            ))}
          </View>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>EXERCISES</Text>
          {session.exercises.map((ex, idx) => {
            const exVol = calculateVolume(ex.sets);
            return (
              <View key={idx} style={styles.exCard}>
                <View style={styles.exCardHeader}>
                  <Text style={styles.exCardName}>{ex.name}</Text>
                  <Text style={styles.exCardVol}>{exVol > 0 ? formatVolumeFromKg(exVol, weightUnit) : '-'}</Text>
                </View>
                <View style={styles.setsList}>
                  {ex.sets.map((s, si) => (
                    <View key={si} style={[styles.setItem, !s.completed && styles.setItemSkipped]}>
                      <Text style={styles.setItemText}>
                        {s.isWarmup ? 'W' : si + 1}{' '}
                        <Text style={[styles.setWeight, s.completed && { color: colors.text }]}>
                          {Number(s.weight || 0) > 0 ? formatWeightFromKg(s.weight, weightUnit) : 'BW'} x {s.reps}
                        </Text>
                      </Text>
                      {s.completed && <Ionicons name="checkmark" size={13} color={colors.accent} />}
                    </View>
                  ))}
                </View>
              </View>
            );
          })}
        </View>

        <View style={styles.noteSection}>
          <Text style={styles.sectionTitle}>SESSION NOTE</Text>
          <TextInput
            style={styles.noteInput}
            value={sessionNote}
            onChangeText={(text) => { setSessionNote(text); saveNote(text); }}
            placeholder="How did this session feel? Any notes..."
            placeholderTextColor={colors.subtext}
            multiline
            returnKeyType="done"
            blurOnSubmit
          />
        </View>

        <View style={styles.actions}>
          <TouchableOpacity style={styles.homeBtn} onPress={() => navigation.navigate('Tabs', { screen: 'Home' })}>
            <Text style={styles.homeBtnText}>HOME</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.historyBtn} onPress={() => navigation.navigate('Tabs', { screen: 'Log' })}>
            <Text style={styles.historyBtnText}>HISTORY</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function makeStyles(colors) {
  return StyleSheet.create({
    safe: { flex: 1, backgroundColor: colors.bg },
    content: { padding: 20, paddingBottom: 40 },
    celebHeader: { alignItems: 'center', paddingVertical: 30 },
    celebPill: {
      borderWidth: 1,
      borderColor: withAlpha(colors.accent, 0.35),
      backgroundColor: withAlpha(colors.accent, 0.08),
      borderRadius: 999,
      paddingHorizontal: 14,
      paddingVertical: 8,
      marginBottom: 12,
    },
    celebIcon: { color: colors.accent, fontSize: 16, fontWeight: '900', letterSpacing: 2 },
    celebTitle: {
      color: colors.text,
      fontSize: 28,
      fontWeight: '900',
      letterSpacing: 4,
      marginBottom: 8,
    },
    sessionDate: { color: colors.subtext, fontSize: 14 },
    sessionTag: { color: colors.muted, fontSize: 12, letterSpacing: 2, marginTop: 6, textTransform: 'uppercase' },
    statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 20 },
    statCard: {
      flex: 1,
      minWidth: '45%',
      backgroundColor: colors.card,
      borderRadius: 12,
      padding: 16,
      alignItems: 'center',
      borderWidth: 1,
      borderColor: colors.cardBorder,
    },
    statValue: {
      fontSize: 28,
      fontWeight: '900',
      letterSpacing: 1,
    },
    statLabel: { color: colors.subtext, fontSize: 10, letterSpacing: 2, marginTop: 4 },
    compareCard: {
      backgroundColor: colors.card,
      borderRadius: 12,
      padding: 16,
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: 20,
      borderWidth: 1,
      borderColor: colors.cardBorder,
    },
    compareLabel: { color: colors.subtext, fontSize: 11, letterSpacing: 2 },
    compareValue: { fontSize: 20, fontWeight: '700', },
    section: { marginBottom: 20 },
    sectionTitle: { color: colors.subtext, fontSize: 11, letterSpacing: 3, marginBottom: 10, fontWeight: '700' },
    prCard: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: withAlpha(colors.accent, 0.06),
      borderRadius: 10,
      padding: 14,
      marginBottom: 8,
      borderWidth: 1,
      borderColor: withAlpha(colors.accent, 0.25),
      gap: 10,
    },
    prName: { color: colors.text, fontSize: 16, fontWeight: '700', flex: 1 },
    exCard: {
      backgroundColor: colors.card,
      borderRadius: 10,
      padding: 14,
      marginBottom: 8,
      borderWidth: 1,
      borderColor: colors.cardBorder,
    },
    exCardHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 10 },
    exCardName: { color: colors.text, fontSize: 15, fontWeight: '700', flex: 1 },
    exCardVol: { color: colors.subtext, fontSize: 13 },
    setsList: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
    setItem: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderRadius: 6,
      paddingHorizontal: 8,
      paddingVertical: 4,
      gap: 4,
    },
    setItemSkipped: { opacity: 0.4 },
    setItemText: { color: colors.subtext, fontSize: 12 },
    setWeight: { color: colors.subtext },
    noteSection: { marginBottom: 20 },
    noteInput: {
      backgroundColor: colors.card,
      borderWidth: 1,
      borderColor: colors.cardBorder,
      borderRadius: 12,
      padding: 14,
      color: colors.text,
      fontSize: 14,
      minHeight: 80,
      textAlignVertical: 'top',
    },
    actions: { flexDirection: 'row', gap: 12, marginTop: 10 },
    homeBtn: { flex: 1, backgroundColor: colors.accent, borderRadius: 12, padding: 16, alignItems: 'center' },
    homeBtnText: {
      color: '#fff',
      fontSize: 16,
      fontWeight: '900',
      letterSpacing: 2,
    },
    historyBtn: {
      flex: 1,
      backgroundColor: colors.card,
      borderRadius: 12,
      padding: 16,
      alignItems: 'center',
      borderWidth: 1,
      borderColor: colors.cardBorder,
    },
    historyBtnText: { color: colors.text, fontSize: 16, fontWeight: '700', letterSpacing: 2 },
  });
}
