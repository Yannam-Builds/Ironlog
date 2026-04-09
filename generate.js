const fs = require('fs');
const path = require('path');

function w(p, c) {
  const dir = path.dirname(p);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(p, c.trimStart());
  console.log('Wrote: ' + p);
}

// ── AppContext.js ──
w('src/context/AppContext.js', `
import React, { createContext, useContext, useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

const HEAVY = new Set([
  "Weighted Pull-Up", "Weighted Pull-Up or Lat Pulldown",
  "Romanian Deadlift", "Bulgarian Split Squat",
  "DB Shrugs", "Incline Smith Press",
]);

const defaultDays = [
  {
    id: "push", label: "D1", name: "PUSH", tag: "Chest \u00b7 Shoulders \u00b7 Triceps", color: "#FF4500",
    exercises: [
      { name: "Incline Smith Press", sets: 4, reps: "8\u201310", primary: "Upper Chest", note: "Full stretch at bottom, squeeze hard at top." },
      { name: "Cable Fly (low to high)", sets: 3, reps: "12\u201315", primary: "Upper Chest", note: "Cables at lowest point, pull upward in an arc." },
      { name: "Cable Lateral Raise", sets: 4, reps: "15", primary: "Side Delts", note: "3-second negative. Lead with elbow." },
      { name: "DB Lateral Raise", sets: 3, reps: "15\u201320", primary: "Side Delts", note: "Slight forward lean. Elbow leads." },
      { name: "Rope Pushdown", sets: 3, reps: "12", primary: "Triceps", note: "Flare rope at bottom. Elbows pinned." },
      { name: "Single Arm OHE", sets: 3, reps: "12", primary: "Triceps", note: "Long head stretch. Slow eccentric." },
      { name: "Weighted Cable Crunch", sets: 4, reps: "15\u201320", primary: "Abs", note: "Crunch into hips. Add weight weekly.", isAbs: true },
    ]
  },
  {
    id: "pull", label: "D2", name: "PULL", tag: "Lats \u00b7 Upper Traps \u00b7 Biceps", color: "#0080FF",
    exercises: [
      { name: "Weighted Pull-Up", sets: 4, reps: "6\u20138", primary: "Lats", note: "Best lat builder. Add weight progressively." },
      { name: "Single Arm DB Row", sets: 4, reps: "10\u201312", primary: "Lats", note: "Incline bench 30\u00b0. Pull elbow back." },
      { name: "Single Arm Cable Pulldown", sets: 3, reps: "12", primary: "Lats", note: "Chest-down on incline bench." },
      { name: "DB Shrugs", sets: 4, reps: "15\u201320", primary: "Upper Traps", note: "Heavy. 1-second hold at top." },
      { name: "Hammer Curl", sets: 3, reps: "12", primary: "Biceps", note: "Brachialis = thicker arm." },
      { name: "Incline DB Curl", sets: 3, reps: "10", primary: "Biceps", note: "Full long head stretch." },
      { name: "Hanging Leg Raise", sets: 4, reps: "12\u201315", primary: "Abs", note: "Add ankle weights when easy.", isAbs: true },
    ]
  },
  {
    id: "legs", label: "D3", name: "LEGS", tag: "Strength \u00b7 Mobility \u00b7 Dance & Run", color: "#00C170",
    exercises: [
      { name: "Hip 90/90 Stretch", sets: 2, reps: "60s/side", primary: "Hip Mobility", note: "WARMUP. Non-negotiable.", isWarmup: true },
      { name: "World\u2019s Greatest Stretch", sets: 2, reps: "5/side", primary: "Full Chain", note: "WARMUP. Full chain opener.", isWarmup: true },
      { name: "Bulgarian Split Squat", sets: 4, reps: "8\u201310/leg", primary: "Quads \u00b7 Glutes", note: "Front shin vertical. Go deep." },
      { name: "Romanian Deadlift", sets: 4, reps: "10", primary: "Hamstrings", note: "Hip hinge = running power." },
      { name: "Leg Press (high feet)", sets: 3, reps: "12", primary: "Glutes", note: "High placement = posterior chain." },
      { name: "Lateral Band Walk", sets: 3, reps: "15 steps", primary: "Glute Med", note: "Lateral stability for cutting." },
      { name: "Single Leg Calf Raise", sets: 4, reps: "20", primary: "Calves", note: "On a step for full ROM." },
      { name: "Ankle Mobility Drill", sets: 2, reps: "60s/side", primary: "Ankles", note: "COOLDOWN. Don\u2019t skip.", isWarmup: true },
    ]
  },
  {
    id: "upper", label: "D4", name: "UPPER", tag: "Aesthetic \u00b7 Arms \u00b7 Abs", color: "#A020F0",
    exercises: [
      { name: "Weighted Pull-Up or Lat Pulldown", sets: 4, reps: "8", primary: "Lats", note: "Second lat session. Full stretch." },
      { name: "Cable Lateral Raise", sets: 4, reps: "15", primary: "Side Delts", note: "Second hit this week. 3s negative." },
      { name: "DB Shrugs", sets: 3, reps: "20", primary: "Upper Traps", note: "Push weight up from Pull day." },
      { name: "Preacher Curl", sets: 3, reps: "10\u201312", primary: "Biceps", note: "No cheating. Full extension." },
      { name: "Rope Overhead Extension", sets: 3, reps: "12", primary: "Triceps", note: "Long head. Full stretch." },
      { name: "Weighted Cable Crunch", sets: 4, reps: "15\u201320", primary: "Abs", note: "Heavier than Push day.", isAbs: true },
      { name: "Hanging Leg Raise", sets: 4, reps: "15", primary: "Abs", note: "Add ankle weights.", isAbs: true },
      { name: "Ab Wheel Rollout", sets: 3, reps: "10", primary: "Full Core", note: "Hardest ab exercise.", isAbs: true },
    ]
  }
];

export const AppContext = createContext();
export const useApp = () => useContext(AppContext);

export function AppContextProvider({ children }) {
  const [history, setHistory] = useState([]);
  const [pb, setPb] = useState({});
  const [settings, setSettings] = useState({ weightUnit: 'kg', defaultRestNormal: 120, defaultRestHeavy: 180 });
  const [initialized, setInitialized] = useState(false);

  useEffect(() => { init(); }, []);

  const init = async () => {
    try {
      const isInit = await AsyncStorage.getItem('ironlog_init');
      if (isInit) {
        const h = await AsyncStorage.getItem('ironlog_history');
        if (h) setHistory(JSON.parse(h));
        const p = await AsyncStorage.getItem('ironlog_pb');
        if (p) setPb(JSON.parse(p));
        const s = await AsyncStorage.getItem('ironlog_settings');
        if (s) setSettings(JSON.parse(s));
      } else {
        await AsyncStorage.setItem('ironlog_init', 'true');
      }
    } catch(e) { console.warn(e); }
    setInitialized(true);
  };

  const addHistory = async (entry) => {
    const h = [entry, ...history].slice(0, 100);
    setHistory(h);
    await AsyncStorage.setItem('ironlog_history', JSON.stringify(h));
  };

  const clearHistory = async () => {
    setHistory([]);
    await AsyncStorage.removeItem('ironlog_history');
  };

  const updatePb = async (key, value) => {
    const n = { ...pb, [key]: value };
    setPb(n);
    await AsyncStorage.setItem('ironlog_pb', JSON.stringify(n));
  };

  const clearPbs = async () => {
    setPb({});
    await AsyncStorage.removeItem('ironlog_pb');
  };

  const updateSettings = async (newSettings) => {
    setSettings(newSettings);
    await AsyncStorage.setItem('ironlog_settings', JSON.stringify(newSettings));
  };

  return (
    <AppContext.Provider value={{
      history, pb, settings, initialized, days: defaultDays,
      addHistory, clearHistory, updatePb, clearPbs, updateSettings,
      isHeavy: (name) => HEAVY.has(name),
    }}>
      {children}
    </AppContext.Provider>
  );
}
`);

// ── RestTimerCircle ──
w('src/components/RestTimerCircle.js', `
import React from 'react';
import { View, Text } from 'react-native';
import Svg, { Circle } from 'react-native-svg';

export default function RestTimerCircle({ time, total, color, size = 130 }) {
  const sw = 6;
  const r = (size - sw * 2) / 2;
  const circ = 2 * Math.PI * r;
  const pct = total > 0 ? time / total : 0;
  const offset = circ * (1 - pct);
  const mins = Math.floor(time / 60);
  const secs = time % 60;

  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Svg width={size} height={size} style={{ transform: [{ rotate: '-90deg' }], position: 'absolute' }}>
        <Circle cx={size/2} cy={size/2} r={r} fill="none" stroke="#1a1a1a" strokeWidth={sw} />
        <Circle cx={size/2} cy={size/2} r={r} fill="none" stroke={color} strokeWidth={sw}
          strokeDasharray={String(circ)} strokeDashoffset={offset} strokeLinecap="round" />
      </Svg>
      <Text style={{ color: '#fff', fontSize: 28, fontWeight: '900' }}>
        {mins}:{secs.toString().padStart(2, '0')}
      </Text>
    </View>
  );
}
`);

// ── AppNavigator ──
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
import ActiveWorkoutScreen from '../screens/ActiveWorkoutScreen';

const Tab = createBottomTabNavigator();
const Stack = createStackNavigator();

function HomeTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarIcon: ({ color, size }) => {
          let iconName;
          if (route.name === 'Home') iconName = 'barbell';
          else if (route.name === 'Log') iconName = 'list';
          else if (route.name === 'Stats') iconName = 'stats-chart';
          else if (route.name === 'Settings') iconName = 'settings';
          return <Ionicons name={iconName} size={size} color={color} />;
        },
        tabBarActiveTintColor: '#FF4500',
        tabBarInactiveTintColor: '#444',
        tabBarStyle: { backgroundColor: '#080808', borderTopColor: '#111', borderTopWidth: 1, paddingBottom: 4 },
        tabBarLabelStyle: { fontSize: 10, letterSpacing: 2 },
        headerStyle: { backgroundColor: '#080808', shadowColor: 'transparent', elevation: 0 },
        headerTintColor: '#f0f0f0',
        headerTitleStyle: { fontWeight: '900', letterSpacing: 2 },
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} options={{ headerShown: false }} />
      <Tab.Screen name="Log" component={HistoryScreen} options={{ title: 'LOG' }} />
      <Tab.Screen name="Stats" component={StatsScreen} options={{ title: 'STATS' }} />
      <Tab.Screen name="Settings" component={SettingsScreen} options={{ title: 'SETTINGS' }} />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  const { initialized } = useContext(AppContext);

  if (!initialized) {
    return (
      <View style={{ flex: 1, backgroundColor: '#080808', justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator color="#FF4500" size="large" />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Main" component={HomeTabs} />
        <Stack.Screen name="ActiveWorkout" component={ActiveWorkoutScreen} options={{ gestureEnabled: false }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
`);

// ── HomeScreen ──
w('src/screens/HomeScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';

export default function HomeScreen({ navigation }) {
  const { days, history, pb } = useContext(AppContext);

  const completedThisWeek = () => {
    const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
    return new Set(history.filter(h => new Date(h.date).getTime() > weekAgo).map(h => h.dayId)).size;
  };

  const totalWorkouts = history.length;
  const avgDuration = history.length ? Math.round(history.reduce((a, h) => a + h.duration, 0) / history.length / 60) : 0;

  return (
    <ScrollView style={s.container} contentContainerStyle={{ paddingBottom: 40 }}>
      {/* Header */}
      <View style={s.header}>
        <Text style={s.subtitle}>PRANAV / AESTHETIC SPLIT</Text>
        <View style={{ flexDirection: 'row' }}>
          <Text style={s.title}>IRON</Text>
        </View>
        <Text style={[s.title, { color: '#FF4500', marginTop: -10 }]}>LOG</Text>
        <Text style={s.desc}>4-day push \u00b7 pull \u00b7 legs \u00b7 upper \u00b7 cutting phase</Text>
      </View>

      {/* Stats bar */}
      <View style={s.statsBar}>
        {[
          { label: 'THIS WEEK', val: completedThisWeek() + '/4' },
          { label: 'TOTAL', val: String(totalWorkouts) },
          { label: 'AVG TIME', val: avgDuration + 'm' },
        ].map((stat, i) => (
          <View key={stat.label} style={[s.statBox, i < 2 && { borderRightWidth: 1, borderRightColor: '#111' }]}>
            <Text style={s.statVal}>{stat.val}</Text>
            <Text style={s.statLabel}>{stat.label}</Text>
          </View>
        ))}
      </View>

      {/* Select Workout */}
      <View style={s.section}>
        <Text style={s.sectionLabel}>SELECT WORKOUT</Text>
        {days.map((d, i) => {
          const lastDone = history.find(h => h.dayId === d.id);
          const daysSince = lastDone ? Math.floor((Date.now() - new Date(lastDone.date).getTime()) / 86400000) : null;
          return (
            <TouchableOpacity
              key={d.id}
              style={[s.dayCard, { borderLeftColor: d.color }]}
              onPress={() => navigation.navigate('ActiveWorkout', { dayIndex: i })}
              activeOpacity={0.7}
            >
              <View style={{ flex: 1 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                  <Text style={[s.dayLabel, { color: d.color }]}>{d.label}</Text>
                  <Text style={s.dayName}>{d.name}</Text>
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
          {Object.entries(pb).map(([k, v]) => (
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
  title: { fontSize: 48, fontWeight: '900', letterSpacing: -2, lineHeight: 52, color: '#f0f0f0' },
  desc: { fontSize: 12, color: '#555', marginTop: 8 },
  statsBar: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#111' },
  statBox: { flex: 1, padding: 16, alignItems: 'center' },
  statVal: { fontSize: 28, fontWeight: '900', color: '#f0f0f0' },
  statLabel: { fontSize: 9, letterSpacing: 3, color: '#444', marginTop: 4 },
  section: { padding: 20 },
  sectionLabel: { fontSize: 10, letterSpacing: 4, color: '#444', marginBottom: 16 },
  dayCard: {
    backgroundColor: 'transparent', borderWidth: 1, borderColor: '#1a1a1a',
    borderLeftWidth: 4, padding: 18, marginBottom: 10,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
  },
  dayLabel: { fontSize: 9, letterSpacing: 3 },
  dayName: { fontSize: 26, fontWeight: '900', letterSpacing: -1, color: '#f0f0f0' },
  dayTag: { fontSize: 12, color: '#555', marginTop: 2 },
  dayMeta: { fontSize: 11, color: '#333', marginTop: 4 },
  pbRow: {
    flexDirection: 'row', justifyContent: 'space-between', padding: 12,
    backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', marginBottom: 6,
  },
});
`);

// ── ActiveWorkoutScreen ──
w('src/screens/ActiveWorkoutScreen.js', `
import React, { useState, useEffect, useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, TextInput, StyleSheet, Alert, Vibration } from 'react-native';
import RestTimerCircle from '../components/RestTimerCircle';
import { AppContext } from '../context/AppContext';

export default function ActiveWorkoutScreen({ route, navigation }) {
  const { dayIndex } = route.params;
  const { days, isHeavy, addHistory, pb, updatePb, settings } = useContext(AppContext);
  const day = days[dayIndex];

  const [activeExIdx, setActiveExIdx] = useState(0);
  const [setsDone, setSetsDone] = useState({});
  const [weights, setWeights] = useState({});
  const [repsLogged, setRepsLogged] = useState({});
  const [notes, setNotes] = useState({});
  const [restPhase, setRestPhase] = useState(false);
  const [restTime, setRestTime] = useState(0);
  const [restTotal, setRestTotal] = useState(0);
  const [restRunning, setRestRunning] = useState(false);
  const [inputWeight, setInputWeight] = useState('');
  const [inputReps, setInputReps] = useState('');
  const [notification, setNotification] = useState(null);
  const [workoutStart] = useState(Date.now());
  const [elapsedSecs, setElapsedSecs] = useState(0);

  const ex = day.exercises[activeExIdx];
  const exKey = day.id + '-' + activeExIdx;
  const currentSetDone = setsDone[exKey] || 0;
  const totalSets = ex.sets;
  const lastWeight = weights[exKey] ? weights[exKey][weights[exKey].length - 1] : '';

  useEffect(() => {
    const iv = setInterval(() => setElapsedSecs(Math.floor((Date.now() - workoutStart) / 1000)), 1000);
    return () => clearInterval(iv);
  }, []);

  useEffect(() => {
    let iv;
    if (restRunning && restTime > 0) {
      iv = setInterval(() => {
        setRestTime(t => {
          if (t <= 1) {
            setRestRunning(false);
            Vibration.vibrate([0, 500, 200, 500]);
            setNotification('\u2713 REST DONE \u2014 GO!');
            return 0;
          }
          return t - 1;
        });
      }, 1000);
    }
    return () => iv && clearInterval(iv);
  }, [restRunning, restTime]);

  useEffect(() => {
    if (notification) {
      const t = setTimeout(() => setNotification(null), 3000);
      return () => clearTimeout(t);
    }
  }, [notification]);

  const totalVolume = weights[exKey]
    ? weights[exKey].reduce((a, w, i) => {
        const r = parseInt(repsLogged[exKey + '-' + i]) || parseInt(ex.reps) || 10;
        return a + (w || 0) * r;
      }, 0) : 0;

  const pbKey = day.id + '-' + ex.name;
  const currentPB = pb[pbKey] || null;
  const progress = (activeExIdx + currentSetDone / totalSets) / day.exercises.length;
  const elapMins = Math.floor(elapsedSecs / 60);
  const elapSecs = elapsedSecs % 60;

  const logSet = () => {
    const w = parseFloat(inputWeight) || 0;
    const r = inputReps || ex.reps;
    const newCount = currentSetDone + 1;
    setSetsDone(p => ({ ...p, [exKey]: newCount }));
    setWeights(p => {
      const arr = [...(p[exKey] || [])];
      arr[currentSetDone] = w;
      return { ...p, [exKey]: arr };
    });
    setRepsLogged(p => ({ ...p, [exKey + '-' + currentSetDone]: r }));

    if (w > 0 && (!pb[pbKey] || w > pb[pbKey])) {
      updatePb(pbKey, w);
      setNotification('\ud83c\udfc6 NEW PB! ' + w + 'kg on ' + ex.name);
    }

    if (newCount >= totalSets) {
      setRestPhase(false);
      const nextIdx = activeExIdx + 1;
      if (nextIdx < day.exercises.length) {
        setActiveExIdx(nextIdx);
        setInputWeight('');
        setInputReps('');
      } else {
        finishWorkout();
      }
    } else {
      const secs = isHeavy(ex.name) ? (settings.defaultRestHeavy || 180) : (settings.defaultRestNormal || 120);
      setRestPhase(true);
      setRestTotal(secs);
      setRestTime(secs);
      setRestRunning(true);
    }
    setInputReps('');
  };

  const skipRest = () => { setRestRunning(false); setRestPhase(false); setRestTime(0); };

  const finishWorkout = () => {
    const duration = Math.floor((Date.now() - workoutStart) / 1000);
    addHistory({
      date: new Date().toISOString(),
      day: day.name,
      dayId: day.id,
      duration,
      totalSets: Object.values(setsDone).reduce((a, b) => a + b, 0),
    });
    Alert.alert('\ud83d\udcaa Workout Complete!',
      'Duration: ' + Math.floor(duration/60) + 'm ' + (duration%60) + 's\\nSets: ' + Object.values(setsDone).reduce((a,b) => a+b, 0),
      [{ text: 'OK', onPress: () => navigation.goBack() }]
    );
  };

  return (
    <View style={s.container}>
      {notification && (
        <View style={[s.notifBanner, { backgroundColor: day.color }]}>
          <Text style={s.notifText}>{notification}</Text>
        </View>
      )}

      <View style={s.topBar}>
        <TouchableOpacity onPress={() => Alert.alert('End workout?', '', [
          { text: 'Cancel' },
          { text: 'End', style: 'destructive', onPress: finishWorkout }
        ])}>
          <Text style={s.endBtn}>\u2715 END</Text>
        </TouchableOpacity>
        <Text style={[s.dayTagText, { color: day.color }]}>{day.name}</Text>
        <Text style={s.timer}>{elapMins}:{elapSecs.toString().padStart(2, '0')}</Text>
      </View>

      <View style={s.progressBg}>
        <View style={[s.progressFg, { width: (progress * 100) + '%', backgroundColor: day.color }]} />
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.pillsRow} contentContainerStyle={{ paddingHorizontal: 20, gap: 6 }}>
        {day.exercises.map((e, i) => {
          const done = i < activeExIdx;
          const active = i === activeExIdx;
          return (
            <TouchableOpacity key={i} onPress={() => { setActiveExIdx(i); setRestPhase(false); setRestRunning(false); setInputWeight(''); setInputReps(''); }}
              style={[s.pill, {
                backgroundColor: done ? day.color + '33' : active ? day.color + '22' : '#111',
                borderColor: active ? day.color : done ? day.color + '44' : '#1a1a1a',
              }]}>
              <Text style={[s.pillText, { color: active ? day.color : done ? day.color + '99' : '#444', fontWeight: active ? '700' : '400' }]}>
                {e.name.split(' ').slice(0, 2).join(' ')}
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 20, paddingBottom: 40, gap: 16 }}>
        <View>
          <View style={{ flexDirection: 'row', gap: 8, marginBottom: 4, flexWrap: 'wrap' }}>
            {isHeavy(ex.name) && <View style={[s.badge, { backgroundColor: '#FF450022', borderColor: '#FF450044' }]}><Text style={[s.badgeText, { color: '#FF4500' }]}>HEAVY \u00b7 3 MIN REST</Text></View>}
            {ex.isWarmup && <View style={[s.badge, { backgroundColor: '#333' }]}><Text style={[s.badgeText, { color: '#888' }]}>WARMUP / COOLDOWN</Text></View>}
            {ex.isAbs && <View style={[s.badge, { backgroundColor: '#A020F022', borderColor: '#A020F044' }]}><Text style={[s.badgeText, { color: '#A020F0' }]}>ABS</Text></View>}
          </View>
          <Text style={s.exName}>{ex.name}</Text>
          <Text style={s.exNote}>{ex.note}</Text>
        </View>

        <View>
          <Text style={s.label}>SETS</Text>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {Array.from({ length: totalSets }).map((_, i) => {
              const done = i < currentSetDone;
              const active = i === currentSetDone;
              const w = weights[exKey] && weights[exKey][i];
              const r = repsLogged[exKey + '-' + i];
              return (
                <View key={i} style={[s.setBox, {
                  backgroundColor: done ? day.color + '22' : active ? '#111' : '#0a0a0a',
                  borderColor: done ? day.color + '66' : active ? day.color : '#1a1a1a',
                }]}>
                  <Text style={{ fontSize: 18, fontWeight: '800', color: done ? day.color : active ? '#f0f0f0' : '#333' }}>
                    {done ? '\u2713' : i + 1}
                  </Text>
                  {done && w > 0 && <Text style={{ fontSize: 10, color: '#666' }}>{w}kg</Text>}
                  {done && r ? <Text style={{ fontSize: 10, color: '#666' }}>{r}</Text> : null}
                </View>
              );
            })}
          </View>
        </View>

        <View style={{ flexDirection: 'row', gap: 8 }}>
          {[
            { label: 'TARGET', val: ex.reps },
            { label: 'VOLUME', val: totalVolume > 0 ? totalVolume + 'kg' : '\u2014' },
            { label: 'PB', val: currentPB ? currentPB + 'kg \ud83c\udfc6' : '\u2014' },
          ].map(stat => (
            <View key={stat.label} style={s.miniStat}>
              <Text style={{ fontSize: 18, fontWeight: '800', color: '#f0f0f0' }}>{stat.val}</Text>
              <Text style={s.miniStatLabel}>{stat.label}</Text>
            </View>
          ))}
        </View>

        {restPhase && (
          <View style={[s.restBox, { borderColor: day.color + '33' }]}>
            <Text style={[s.label, { color: day.color, marginBottom: 16, textAlign: 'center' }]}>
              {isHeavy(ex.name) ? 'HEAVY LIFT \u2014 3 MIN REST' : '2 MIN REST'}
            </Text>
            <View style={{ alignItems: 'center', marginBottom: 16 }}>
              <RestTimerCircle time={restTime} total={restTotal} color={day.color} size={130} />
            </View>
            <View style={{ flexDirection: 'row', gap: 10, justifyContent: 'center' }}>
              <TouchableOpacity style={s.restBtn} onPress={() => setRestRunning(r => !r)}>
                <Text style={s.restBtnText}>{restRunning ? '\u23f8 PAUSE' : '\u25b6 RESUME'}</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[s.restBtn, { backgroundColor: day.color }]} onPress={skipRest}>
                <Text style={s.restBtnText}>SKIP REST \u203a</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {!restPhase && currentSetDone < totalSets && !ex.isWarmup && (
          <View>
            <Text style={s.label}>
              LOG SET {currentSetDone + 1} OF {totalSets}
              {lastWeight ? ' \u00b7 LAST: ' + lastWeight + 'kg' : ''}
            </Text>
            <View style={{ flexDirection: 'row', gap: 10, marginBottom: 12 }}>
              <View style={{ flex: 1 }}>
                <Text style={s.inputLabel}>WEIGHT (kg)</Text>
                <TextInput style={s.input} keyboardType="numeric" placeholder={String(lastWeight || '0')} placeholderTextColor="#333"
                  value={inputWeight} onChangeText={setInputWeight} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={s.inputLabel}>REPS</Text>
                <TextInput style={s.input} placeholder={ex.reps} placeholderTextColor="#333"
                  value={inputReps} onChangeText={setInputReps} />
              </View>
            </View>
            <TouchableOpacity style={[s.logBtn, { backgroundColor: day.color }]} onPress={logSet} activeOpacity={0.8}>
              <Text style={s.logBtnText}>\u2713 LOG SET {currentSetDone + 1}</Text>
            </TouchableOpacity>
          </View>
        )}

        {!restPhase && ex.isWarmup && (
          <TouchableOpacity style={[s.warmupBtn, { borderColor: day.color }]} onPress={() => {
            const nextIdx = activeExIdx + 1;
            if (nextIdx < day.exercises.length) { setActiveExIdx(nextIdx); setInputWeight(''); setInputReps(''); }
            else finishWorkout();
          }}>
            <Text style={[s.warmupBtnText, { color: day.color }]}>DONE \u00b7 NEXT EXERCISE \u203a</Text>
          </TouchableOpacity>
        )}

        <View>
          <Text style={[s.label, { color: '#333' }]}>NOTES (optional)</Text>
          <TextInput style={s.notesInput} multiline placeholder="How did this feel?"
            placeholderTextColor="#333" value={notes[exKey] || ''} onChangeText={t => setNotes(p => ({ ...p, [exKey]: t }))} />
        </View>
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  notifBanner: { padding: 16, alignItems: 'center', paddingTop: 50 },
  notifText: { fontSize: 16, fontWeight: '800', letterSpacing: 3, color: '#fff' },
  topBar: { paddingHorizontal: 20, paddingTop: 50, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  endBtn: { color: '#444', fontSize: 12, letterSpacing: 2 },
  dayTagText: { fontSize: 11, letterSpacing: 3 },
  timer: { fontWeight: '700', fontSize: 14, color: '#888' },
  progressBg: { height: 2, backgroundColor: '#111', marginTop: 12 },
  progressFg: { height: '100%' },
  pillsRow: { paddingVertical: 10, maxHeight: 50 },
  pill: { paddingHorizontal: 10, paddingVertical: 4, borderWidth: 1 },
  pillText: { fontSize: 10, letterSpacing: 1 },
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderWidth: 1, borderColor: 'transparent' },
  badgeText: { fontSize: 9, letterSpacing: 2 },
  exName: { fontSize: 34, fontWeight: '900', letterSpacing: -1, lineHeight: 38, color: '#f0f0f0' },
  exNote: { fontSize: 13, color: '#555', marginTop: 6, lineHeight: 20 },
  label: { fontSize: 10, letterSpacing: 3, color: '#444', marginBottom: 10 },
  setBox: { flex: 1, padding: 10, alignItems: 'center', borderWidth: 1 },
  miniStat: { flex: 1, backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', padding: 10, alignItems: 'center' },
  miniStatLabel: { fontSize: 9, letterSpacing: 2, color: '#444', marginTop: 4 },
  restBox: { backgroundColor: '#0d0d0d', borderWidth: 1, padding: 20 },
  restBtn: { backgroundColor: '#111', borderWidth: 1, borderColor: '#333', paddingHorizontal: 20, paddingVertical: 10 },
  restBtnText: { color: '#f0f0f0', fontSize: 13, fontWeight: '700', letterSpacing: 2 },
  inputLabel: { fontSize: 10, letterSpacing: 2, color: '#444', marginBottom: 6 },
  input: { backgroundColor: '#111', borderWidth: 1, borderColor: '#2a2a2a', color: '#f0f0f0', padding: 12, fontSize: 20, fontWeight: '700', textAlign: 'center' },
  logBtn: { padding: 18, alignItems: 'center' },
  logBtnText: { color: '#fff', fontSize: 18, fontWeight: '900', letterSpacing: 3 },
  warmupBtn: { borderWidth: 1, padding: 18, alignItems: 'center', backgroundColor: '#111' },
  warmupBtnText: { fontSize: 16, fontWeight: '800', letterSpacing: 3 },
  notesInput: { backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', color: '#666', padding: 10, fontSize: 13 },
});
`);

// ── HistoryScreen ──
w('src/screens/HistoryScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { AppContext } from '../context/AppContext';

export default function HistoryScreen() {
  const { history, days, clearHistory } = useContext(AppContext);

  return (
    <ScrollView style={s.container} contentContainerStyle={{ padding: 20, paddingBottom: 40 }}>
      {history.length === 0 && <Text style={s.empty}>No workouts logged yet.</Text>}
      {history.map((h, i) => {
        const d = days.find(x => x.id === h.dayId);
        const color = d ? d.color : '#444';
        const date = new Date(h.date);
        return (
          <View key={i} style={[s.session, { borderLeftColor: color }]}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <View>
                <Text style={s.dayName}>{h.day}</Text>
                <Text style={s.meta}>
                  {date.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })} \u00b7 {Math.floor(h.duration / 60)}m {h.duration % 60}s \u00b7 {h.totalSets} sets
                </Text>
              </View>
              <Text style={{ fontSize: 11, color: '#444' }}>#{history.length - i}</Text>
            </View>
          </View>
        );
      })}
      {history.length > 0 && (
        <TouchableOpacity style={s.clearBtn} onPress={() => Alert.alert('Clear all history?', '', [
          { text: 'Cancel' },
          { text: 'Clear', style: 'destructive', onPress: clearHistory }
        ])}>
          <Text style={s.clearBtnText}>CLEAR HISTORY</Text>
        </TouchableOpacity>
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  empty: { color: '#444', fontSize: 14, textAlign: 'center', marginTop: 40 },
  session: {
    backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a',
    borderLeftWidth: 4, padding: 14, marginBottom: 8,
  },
  dayName: { fontSize: 22, fontWeight: '800', color: '#f0f0f0' },
  meta: { fontSize: 12, color: '#555', marginTop: 4 },
  clearBtn: { marginTop: 16, borderWidth: 1, borderColor: '#1a1a1a', padding: 12, alignItems: 'center' },
  clearBtnText: { color: '#333', fontSize: 12, letterSpacing: 2 },
});
`);

// ── StatsScreen ──
w('src/screens/StatsScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { AppContext } from '../context/AppContext';

export default function StatsScreen() {
  const { history, days, pb, clearPbs } = useContext(AppContext);

  const totalWorkouts = history.length;
  const avgDuration = history.length ? Math.round(history.reduce((a, h) => a + h.duration, 0) / history.length / 60) : 0;
  const totalSetsAll = history.reduce((a, h) => a + (h.totalSets || 0), 0);

  const longestStreak = (() => {
    if (!history.length) return 0;
    let streak = 1, max = 1;
    const dates = history.map(h => new Date(h.date).toDateString());
    for (let i = 1; i < dates.length; i++) {
      const diff = Math.abs(new Date(dates[i - 1]) - new Date(dates[i])) / 86400000;
      if (diff <= 2) { streak++; max = Math.max(max, streak); } else streak = 1;
    }
    return max;
  })();

  const dayCount = {};
  days.forEach(d => { dayCount[d.id] = history.filter(h => h.dayId === d.id).length; });

  return (
    <ScrollView style={s.container} contentContainerStyle={{ padding: 20, paddingBottom: 40, gap: 16 }}>
      <View style={s.statsGrid}>
        {[
          { label: 'TOTAL SESSIONS', val: String(totalWorkouts) },
          { label: 'TOTAL SETS', val: String(totalSetsAll) },
          { label: 'AVG SESSION', val: avgDuration + 'm' },
          { label: 'BEST STREAK', val: longestStreak + ' days' },
        ].map(stat => (
          <View key={stat.label} style={s.statCard}>
            <Text style={s.statVal}>{stat.val}</Text>
            <Text style={s.statLabel}>{stat.label}</Text>
          </View>
        ))}
      </View>

      <View>
        <Text style={s.sectionLabel}>SESSIONS PER DAY</Text>
        {days.map(d => (
          <View key={d.id} style={{ marginBottom: 10 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
              <Text style={{ fontSize: 14, fontWeight: '700', color: '#f0f0f0' }}>{d.name}</Text>
              <Text style={{ fontSize: 14, color: '#555' }}>{dayCount[d.id]} sessions</Text>
            </View>
            <View style={s.barBg}>
              <View style={[s.barFg, { width: (totalWorkouts ? (dayCount[d.id] / totalWorkouts) * 100 : 0) + '%', backgroundColor: d.color }]} />
            </View>
          </View>
        ))}
      </View>

      <View>
        <Text style={s.sectionLabel}>PERSONAL BESTS</Text>
        {Object.keys(pb).length === 0 && <Text style={{ color: '#333', fontSize: 13 }}>No PBs yet. Log weights to track them.</Text>}
        {Object.entries(pb).map(([k, v]) => {
          const dayId = k.split('-')[0];
          const d = days.find(x => x.id === dayId);
          return (
            <View key={k} style={[s.pbRow, { borderLeftColor: d ? d.color : '#444' }]}>
              <Text style={{ fontSize: 15, fontWeight: '700', color: '#f0f0f0' }}>{k.split('-').slice(1).join(' ')}</Text>
              <Text style={{ fontSize: 20, fontWeight: '900', color: '#FFD700' }}>\ud83c\udfc6 {v}kg</Text>
            </View>
          );
        })}
      </View>

      {Object.keys(pb).length > 0 && (
        <TouchableOpacity style={s.resetBtn} onPress={() => Alert.alert('Reset all PBs?', '', [
          { text: 'Cancel' },
          { text: 'Reset', style: 'destructive', onPress: clearPbs }
        ])}>
          <Text style={s.resetBtnText}>RESET PBs</Text>
        </TouchableOpacity>
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  statCard: { width: '47%', backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', padding: 16, alignItems: 'center' },
  statVal: { fontSize: 32, fontWeight: '900', color: '#f0f0f0' },
  statLabel: { fontSize: 9, letterSpacing: 3, color: '#444', marginTop: 4 },
  sectionLabel: { fontSize: 10, letterSpacing: 4, color: '#444', marginBottom: 12 },
  barBg: { height: 6, backgroundColor: '#111', borderRadius: 3 },
  barFg: { height: '100%', borderRadius: 3 },
  pbRow: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    padding: 14, backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a',
    borderLeftWidth: 3, marginBottom: 6,
  },
  resetBtn: { borderWidth: 1, borderColor: '#1a1a1a', padding: 12, alignItems: 'center' },
  resetBtnText: { color: '#333', fontSize: 12, letterSpacing: 2 },
});
`);

// ── SettingsScreen ──
w('src/screens/SettingsScreen.js', `
import React, { useContext } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Switch, Alert } from 'react-native';
import { AppContext } from '../context/AppContext';

export default function SettingsScreen() {
  const { settings, updateSettings } = useContext(AppContext);

  const toggleUnit = () => {
    updateSettings({ ...settings, weightUnit: settings.weightUnit === 'kg' ? 'lbs' : 'kg' });
  };

  return (
    <ScrollView style={s.container} contentContainerStyle={{ padding: 20, paddingBottom: 40, gap: 16 }}>
      <View style={s.card}>
        <Text style={s.cardTitle}>WEIGHT UNIT</Text>
        <View style={s.row}>
          <Text style={s.settingLabel}>Unit</Text>
          <TouchableOpacity onPress={toggleUnit} style={s.unitBtn}>
            <Text style={s.unitBtnText}>{settings.weightUnit.toUpperCase()}</Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={s.card}>
        <Text style={s.cardTitle}>REST TIMERS</Text>
        <View style={s.row}>
          <Text style={s.settingLabel}>Normal exercises</Text>
          <Text style={s.settingVal}>{settings.defaultRestNormal}s</Text>
        </View>
        <View style={s.row}>
          <Text style={s.settingLabel}>Heavy exercises</Text>
          <Text style={s.settingVal}>{settings.defaultRestHeavy}s</Text>
        </View>
      </View>

      <View style={s.card}>
        <Text style={s.cardTitle}>ABOUT</Text>
        <Text style={s.aboutText}>IRONLOG v1.0.0</Text>
        <Text style={s.aboutText}>4-day aesthetic split tracker</Text>
        <Text style={s.aboutText}>Built for Pranav</Text>
      </View>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808' },
  card: { backgroundColor: '#0d0d0d', borderWidth: 1, borderColor: '#1a1a1a', padding: 16 },
  cardTitle: { fontSize: 10, letterSpacing: 3, color: '#444', marginBottom: 12 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  settingLabel: { fontSize: 14, color: '#f0f0f0' },
  settingVal: { fontSize: 14, color: '#666' },
  unitBtn: { backgroundColor: '#FF4500', paddingHorizontal: 16, paddingVertical: 6 },
  unitBtnText: { color: '#fff', fontWeight: '800', letterSpacing: 2 },
  aboutText: { fontSize: 13, color: '#555', marginBottom: 4 },
});
`);

console.log('\n=== All files generated successfully! ===');
