const fs = require('fs');
const path = require('path');
function w(p, c) {
  const d = path.dirname(p);
  if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
  fs.writeFileSync(p, c);
  console.log('W: ' + p);
}

// ── NAVIGATION ──
w('src/navigation/AppNavigator.js', `
import React, { useContext } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createStackNavigator } from '@react-navigation/stack';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import HomeScreen from '../screens/HomeScreen';
import HistoryScreen from '../screens/HistoryScreen';
import StatsScreen from '../screens/StatsScreen';
import SettingsScreen from '../screens/SettingsScreen';
import PlansScreen from '../screens/PlansScreen';
import ActiveWorkoutScreen from '../screens/ActiveWorkoutScreen';
import PlanEditorScreen from '../screens/PlanEditorScreen';
import ExerciseLibraryScreen from '../screens/ExerciseLibraryScreen';
import BodyWeightScreen from '../screens/BodyWeightScreen';

const Tab = createBottomTabNavigator();
const Stack = createStackNavigator();

const NAV_OPTS = {
  headerStyle: { backgroundColor: '#080808', shadowColor: 'transparent', elevation: 0, borderBottomWidth: 1, borderBottomColor: '#111' },
  headerTintColor: '#f0f0f0',
  headerTitleStyle: { fontWeight: '900', letterSpacing: 2, fontSize: 16 },
};

function Tabs() {
  return (
    <Tab.Navigator screenOptions={({ route }) => ({
      ...NAV_OPTS,
      tabBarIcon: ({ color, size }) => {
        const icons = { Home: 'barbell', Plans: 'list', Log: 'time', Stats: 'stats-chart', Settings: 'settings-outline' };
        return <Ionicons name={icons[route.name]} size={size} color={color} />;
      },
      tabBarActiveTintColor: '#FF4500',
      tabBarInactiveTintColor: '#444',
      tabBarStyle: { backgroundColor: '#080808', borderTopColor: '#111', borderTopWidth: 1 },
      tabBarLabelStyle: { fontSize: 10, letterSpacing: 1 },
    })}>
      <Tab.Screen name="Home" component={HomeScreen} options={{ headerShown: false }} />
      <Tab.Screen name="Plans" component={PlansScreen} options={{ title: 'PLANS' }} />
      <Tab.Screen name="Log" component={HistoryScreen} options={{ title: 'LOG' }} />
      <Tab.Screen name="Stats" component={StatsScreen} options={{ title: 'STATS' }} />
      <Tab.Screen name="Settings" component={SettingsScreen} options={{ title: 'SETTINGS' }} />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  const { initialized } = useContext(AppContext);
  if (!initialized) return (
    <View style={{ flex: 1, backgroundColor: '#080808', justifyContent: 'center', alignItems: 'center' }}>
      <ActivityIndicator color="#FF4500" size="large" />
    </View>
  );
  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Tabs" component={Tabs} />
        <Stack.Screen name="ActiveWorkout" component={ActiveWorkoutScreen} options={{ gestureEnabled: false }} />
        <Stack.Screen name="PlanEditor" component={PlanEditorScreen} options={{ headerShown: true, ...NAV_OPTS, title: 'EDIT PLAN' }} />
        <Stack.Screen name="ExerciseLibrary" component={ExerciseLibraryScreen} options={{ headerShown: true, ...NAV_OPTS, title: 'EXERCISE LIBRARY' }} />
        <Stack.Screen name="BodyWeight" component={BodyWeightScreen} options={{ headerShown: true, ...NAV_OPTS, title: 'BODY WEIGHT' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
`);

// ── HOME SCREEN ──
w('src/screens/HomeScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';

function getStreak(history) {
  if (!history.length) return 0;
  const days = [...new Set(history.map(h => h.date.split('T')[0]))].sort().reverse();
  let streak = 0;
  const today = new Date().toISOString().split('T')[0];
  const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];
  if (days[0] !== today && days[0] !== yesterday) return 0;
  let prev = days[0];
  for (const d of days) {
    const diff = (new Date(prev) - new Date(d)) / 86400000;
    if (diff <= 1) { streak++; prev = d; } else break;
  }
  return streak;
}

function getMuscleVolume(history) {
  const weekAgo = Date.now() - 7 * 86400000;
  const recent = history.filter(h => new Date(h.date).getTime() > weekAgo);
  const vol = {};
  recent.forEach(h => {
    const key = h.dayId === 'push' ? 'PUSH' : h.dayId === 'pull' ? 'PULL' : h.dayId === 'legs' ? 'LEGS' : 'UPPER';
    vol[key] = (vol[key] || 0) + 1;
  });
  return vol;
}

export default function HomeScreen({ navigation }) {
  const { plans, history, pb, bodyWeight } = useContext(AppContext);
  const streak = getStreak(history);
  const muscleVol = getMuscleVolume(history);
  const weekAgo = Date.now() - 7 * 86400000;
  const thisWeek = new Set(history.filter(h => new Date(h.date).getTime() > weekAgo).map(h => h.dayId)).size;
  const totalWorkouts = history.length;
  const avgDuration = history.length ? Math.round(history.reduce((a, h) => a + h.duration, 0) / history.length / 60) : 0;
  const latestBW = bodyWeight[0];

  return (
    <ScrollView style={s.container} contentContainerStyle={{ paddingBottom: 40 }}>
      <View style={s.header}>
        <Text style={s.subtitle}>PRANAV / AESTHETIC SPLIT</Text>
        <Text style={s.iron}>IRON</Text>
        <Text style={s.log}>LOG</Text>
        <Text style={s.desc}>cutting phase \u00b7 4-day split</Text>
      </View>

      {/* Stats bar */}
      <View style={s.statsBar}>
        {[
          { label: 'THIS WEEK', val: thisWeek + '/4' },
          { label: 'STREAK', val: streak + (streak === 1 ? ' day' : ' days') },
          { label: 'AVG TIME', val: avgDuration + 'm' },
        ].map((stat, i) => (
          <View key={stat.label} style={[s.statBox, i < 2 && { borderRightWidth: 1, borderRightColor: '#111' }]}>
            <Text style={s.statVal}>{stat.val}</Text>
            <Text style={s.statLabel}>{stat.label}</Text>
          </View>
        ))}
      </View>

      {/* Muscle hit indicator */}
      <View style={{ paddingHorizontal: 20, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#111' }}>
        <Text style={s.sectionLabel}>THIS WEEK</Text>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          {[['PUSH','#FF4500'],['PULL','#0080FF'],['LEGS','#00C170'],['UPPER','#A020F0']].map(([label, color]) => (
            <View key={label} style={{ flex: 1, alignItems: 'center' }}>
              <View style={[s.muscleChip, { borderColor: muscleVol[label] ? color : '#1a1a1a', backgroundColor: muscleVol[label] ? color + '22' : 'transparent' }]}>
                <Text style={[s.muscleChipText, { color: muscleVol[label] ? color : '#333' }]}>{label}</Text>
              </View>
              {muscleVol[label] ? <Text style={[s.muscleCount, { color }]}>{muscleVol[label]}x</Text> : null}
            </View>
          ))}
        </View>
      </View>

      {/* Body weight quick */}
      <TouchableOpacity style={s.bwBar} onPress={() => navigation.navigate('BodyWeight')}>
        <View>
          <Text style={s.sectionLabel}>BODY WEIGHT</Text>
          <Text style={{ fontSize: 22, fontWeight: '900', color: '#f0f0f0' }}>
            {latestBW ? latestBW.weight + ' kg' : 'Tap to log'}
          </Text>
        </View>
        <Ionicons name="chevron-forward" size={20} color="#444" />
      </TouchableOpacity>

      {/* Select Workout */}
      <View style={s.section}>
        <Text style={s.sectionLabel}>SELECT WORKOUT</Text>
        {plans[0]?.days.map((d, i) => {
          const lastDone = history.find(h => h.dayId === d.id);
          const daysSince = lastDone ? Math.floor((Date.now() - new Date(lastDone.date).getTime()) / 86400000) : null;
          const overloadNudge = history.filter(h => h.dayId === d.id).slice(0, 3).length === 3;
          return (
            <TouchableOpacity key={d.id} style={[s.dayCard, { borderLeftColor: d.color }]}
              onPress={() => navigation.navigate('ActiveWorkout', { planIndex: 0, dayIndex: i })}
              activeOpacity={0.7}>
              <View style={{ flex: 1 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                  <Text style={[s.dayLabel, { color: d.color }]}>{d.label}</Text>
                  <Text style={s.dayName}>{d.name}</Text>
                  {overloadNudge && <Text style={s.nudge}>\u2191 OVERLOAD</Text>}
                </View>
                <Text style={s.dayTag}>{d.tag}</Text>
                <Text style={s.dayMeta}>
                  {d.exercises.filter(e => !e.isWarmup).length} exercises
                  {daysSince !== null ? ' \u00b7 last: ' + (daysSince === 0 ? 'today' : daysSince === 1 ? 'yesterday' : daysSince + 'd ago') : ' \u00b7 never done'}
                </Text>
              </View>
              <Text style={{ fontSize: 28, color: '#222' }}>\u203a</Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* PBs */}
      {Object.keys(pb).length > 0 && (
        <View style={s.section}>
          <Text style={s.sectionLabel}>PERSONAL BESTS</Text>
          {Object.entries(pb).slice(0, 5).map(([k, v]) => (
            <View key={k} style={s.pbRow}>
              <Text style={{ fontSize: 14, fontWeight: '600', color: '#f0f0f0' }}>{k.split('-').slice(1).join(' ')}</Text>
              <Text style={{ fontSize: 14, fontWeight: '800', color: '#FFD700' }}>\ud83c\udfc6 {v}kg</Text>
            </View>
          ))}
        </View>
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  header: { padding: 20, paddingTop: 50, borderBottomWidth: 1, borderBottomColor: '#111' },
  subtitle: { fontSize: 10, letterSpacing: 5, color: '#444', marginBottom: 4 },
  iron: { fontSize: 48, fontWeight: '900', letterSpacing: -2, lineHeight: 52, color: '#f0f0f0' },
  log: { fontSize: 48, fontWeight: '900', letterSpacing: -2, lineHeight: 52, color: '#FF4500', marginTop: -10 },
  desc: { fontSize: 12, color: '#555', marginTop: 8 },
  statsBar: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#111' },
  statBox: { flex: 1, padding: 16, alignItems: 'center' },
  statVal: { fontSize: 22, fontWeight: '900', color: '#f0f0f0' },
  statLabel: { fontSize: 9, letterSpacing: 3, color: '#444', marginTop: 4 },
  sectionLabel: { fontSize: 10, letterSpacing: 4, color: '#444', marginBottom: 10 },
  muscleChip: { borderWidth: 1, paddingHorizontal: 8, paddingVertical: 4, marginBottom: 4 },
  muscleChipText: { fontSize: 9, fontWeight: '700', letterSpacing: 1 },
  muscleCount: { fontSize: 10, fontWeight: '800' },
  bwBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 20, borderBottomWidth: 1, borderBottomColor: '#111' },
  section: { padding: 20 },
  dayCard: {
    backgroundColor: 'transparent', borderWidth: 1, borderColor: '#1a1a1a',
    borderLeftWidth: 4, padding: 18, marginBottom: 10,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
  },
  dayLabel: { fontSize: 9, letterSpacing: 3 },
  dayName: { fontSize: 26, fontWeight: '900', letterSpacing: -1, color: '#f0f0f0' },
  dayTag: { fontSize: 12, color: '#555', marginTop: 2 },
  dayMeta: { fontSize: 11, color: '#333', marginTop: 4 },
  nudge: { fontSize: 8, color: '#FF4500', letterSpacing: 1, backgroundColor: '#FF450022', paddingHorizontal: 6, paddingVertical: 2 },
  pbRow: { flexDirection: 'row', justifyContent: 'space-between', padding: 12, backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', marginBottom: 6 },
});
`);

console.log('Nav + Home done');
