const fs = require('fs');
const path = require('path');
function w(p, c) {
  const d = path.dirname(p);
  if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
  fs.writeFileSync(p, c);
  console.log('W: ' + p);
}

// ── APPCONTEXT (full) ──
w('src/context/AppContext.js', `
import React, { createContext, useContext, useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

const HEAVY = new Set([
  'Weighted Pull-Up', 'Weighted Pull-Up or Lat Pulldown',
  'Romanian Deadlift', 'Bulgarian Split Squat', 'DB Shrugs', 'Incline Smith Press',
  'Barbell Shrugs', 'Deadlift', 'Back Squat', 'Front Squat',
]);

const defaultDays = [
  {
    id: 'push', label: 'D1', name: 'PUSH', tag: 'Chest \u00b7 Shoulders \u00b7 Triceps', color: '#FF4500',
    exercises: [
      { name: 'Incline Smith Press', sets: 4, reps: '8\u201310', primary: 'Upper Chest', note: 'Full stretch at bottom, squeeze hard at top.' },
      { name: 'Cable Fly Low to High', sets: 3, reps: '12\u201315', primary: 'Upper Chest', note: 'Cables at lowest point, pull upward in an arc.' },
      { name: 'Cable Lateral Raise', sets: 4, reps: '15', primary: 'Side Delts', note: '3-second negative. Lead with elbow.' },
      { name: 'DB Lateral Raise', sets: 3, reps: '15\u201320', primary: 'Side Delts', note: 'Slight forward lean. Elbow leads.' },
      { name: 'Rope Pushdown', sets: 3, reps: '12', primary: 'Triceps', note: 'Flare rope at bottom. Elbows pinned.' },
      { name: 'Single Arm OHE', sets: 3, reps: '12', primary: 'Triceps', note: 'Long head stretch. Slow eccentric.' },
      { name: 'Weighted Cable Crunch', sets: 4, reps: '15\u201320', primary: 'Abs', note: 'Crunch into hips. Add weight weekly.', isAbs: true },
    ],
  },
  {
    id: 'pull', label: 'D2', name: 'PULL', tag: 'Lats \u00b7 Upper Traps \u00b7 Biceps', color: '#0080FF',
    exercises: [
      { name: 'Weighted Pull-Up', sets: 4, reps: '6\u20138', primary: 'Lats', note: 'Best lat builder. Add weight progressively.' },
      { name: 'Single Arm DB Row', sets: 4, reps: '10\u201312', primary: 'Lats', note: 'Incline bench 30\u00b0. Pull elbow back.' },
      { name: 'Single Arm Cable Pulldown', sets: 3, reps: '12', primary: 'Lats', note: 'Chest-down on incline bench.' },
      { name: 'DB Shrugs', sets: 4, reps: '15\u201320', primary: 'Upper Traps', note: 'Heavy. 1-second hold at top.' },
      { name: 'Hammer Curl', sets: 3, reps: '12', primary: 'Biceps', note: 'Brachialis = thicker arm.' },
      { name: 'Incline DB Curl', sets: 3, reps: '10', primary: 'Biceps', note: 'Full long head stretch.' },
      { name: 'Hanging Leg Raise', sets: 4, reps: '12\u201315', primary: 'Abs', note: 'Add ankle weights when easy.', isAbs: true },
    ],
  },
  {
    id: 'legs', label: 'D3', name: 'LEGS', tag: 'Strength \u00b7 Mobility \u00b7 Dance & Run', color: '#00C170',
    exercises: [
      { name: 'Hip 90/90 Stretch', sets: 2, reps: '60s/side', primary: 'Hip Mobility', note: 'WARMUP. Non-negotiable.', isWarmup: true },
      { name: "World's Greatest Stretch", sets: 2, reps: '5/side', primary: 'Full Chain', note: 'WARMUP. Full chain opener.', isWarmup: true },
      { name: 'Bulgarian Split Squat', sets: 4, reps: '8\u201310/leg', primary: 'Quads \u00b7 Glutes', note: 'Front shin vertical. Go deep.' },
      { name: 'Romanian Deadlift', sets: 4, reps: '10', primary: 'Hamstrings', note: 'Hip hinge = running power.' },
      { name: 'Leg Press High Feet', sets: 3, reps: '12', primary: 'Glutes', note: 'High placement = posterior chain.' },
      { name: 'Lateral Band Walk', sets: 3, reps: '15 steps', primary: 'Glute Med', note: 'Lateral stability for cutting.' },
      { name: 'Single Leg Calf Raise', sets: 4, reps: '20', primary: 'Calves', note: 'On a step for full ROM.' },
      { name: 'Ankle Mobility Drill', sets: 2, reps: '60s/side', primary: 'Ankles', note: 'COOLDOWN. Don\'t skip.', isWarmup: true },
    ],
  },
  {
    id: 'upper', label: 'D4', name: 'UPPER', tag: 'Aesthetic \u00b7 Arms \u00b7 Abs', color: '#A020F0',
    exercises: [
      { name: 'Weighted Pull-Up or Lat Pulldown', sets: 4, reps: '8', primary: 'Lats', note: 'Second lat session. Full stretch.' },
      { name: 'Cable Lateral Raise', sets: 4, reps: '15', primary: 'Side Delts', note: 'Second hit this week. 3s negative.' },
      { name: 'DB Shrugs', sets: 3, reps: '20', primary: 'Upper Traps', note: 'Push weight up from Pull day.' },
      { name: 'Preacher Curl', sets: 3, reps: '10\u201312', primary: 'Biceps', note: 'No cheating. Full extension.' },
      { name: 'Rope Overhead Extension', sets: 3, reps: '12', primary: 'Triceps', note: 'Long head. Full stretch.' },
      { name: 'Weighted Cable Crunch', sets: 4, reps: '15\u201320', primary: 'Abs', note: 'Heavier than Push day.', isAbs: true },
      { name: 'Hanging Leg Raise', sets: 4, reps: '15', primary: 'Abs', note: 'Add ankle weights.', isAbs: true },
      { name: 'Ab Wheel Rollout', sets: 3, reps: '10', primary: 'Full Core', note: 'Hardest ab exercise.', isAbs: true },
    ],
  },
];

export const AppContext = createContext();
export const useApp = () => useContext(AppContext);

export function AppContextProvider({ children }) {
  const [plans, setPlans] = useState([{ id: 'aesthetic-split', name: 'Aesthetic Split', days: defaultDays }]);
  const [history, setHistory] = useState([]);
  const [pb, setPb] = useState({});
  const [bodyWeight, setBodyWeight] = useState([]);
  const [exerciseNotes, setExerciseNotes] = useState({});
  const [settings, setSettings] = useState({
    weightUnit: 'kg', defaultRestNormal: 120, defaultRestHeavy: 180, barWeight: 20,
  });
  const [initialized, setInitialized] = useState(false);

  useEffect(() => { init(); }, []);

  const init = async () => {
    try {
      const keys = ['ironlog_plans', 'ironlog_history', 'ironlog_pb', 'ironlog_bw', 'ironlog_notes', 'ironlog_settings'];
      const vals = await AsyncStorage.multiGet(keys);
      const map = Object.fromEntries(vals.map(([k, v]) => [k, v ? JSON.parse(v) : null]));
      if (map.ironlog_plans) setPlans(map.ironlog_plans);
      if (map.ironlog_history) setHistory(map.ironlog_history);
      if (map.ironlog_pb) setPb(map.ironlog_pb);
      if (map.ironlog_bw) setBodyWeight(map.ironlog_bw);
      if (map.ironlog_notes) setExerciseNotes(map.ironlog_notes);
      if (map.ironlog_settings) setSettings(prev => ({ ...prev, ...map.ironlog_settings }));
    } catch (e) { console.warn('Init error:', e); }
    setInitialized(true);
  };

  const save = async (key, value) => {
    try { await AsyncStorage.setItem(key, JSON.stringify(value)); } catch (e) { console.warn(e); }
  };

  const savePlans = async (v) => { setPlans(v); await save('ironlog_plans', v); };
  const addHistory = async (entry) => {
    const h = [entry, ...history].slice(0, 200);
    setHistory(h); await save('ironlog_history', h);
  };
  const clearHistory = async () => { setHistory([]); await AsyncStorage.removeItem('ironlog_history'); };
  const updatePb = async (key, val) => {
    const n = { ...pb, [key]: val }; setPb(n); await save('ironlog_pb', n);
  };
  const clearPbs = async () => { setPb({}); await AsyncStorage.removeItem('ironlog_pb'); };
  const logBodyWeight = async (entry) => {
    const bw = [entry, ...bodyWeight].slice(0, 365);
    setBodyWeight(bw); await save('ironlog_bw', bw);
  };
  const saveExerciseNotes = async (exName, note) => {
    const n = { ...exerciseNotes, [exName]: note };
    setExerciseNotes(n); await save('ironlog_notes', n);
  };
  const updateSettings = async (s) => { setSettings(s); await save('ironlog_settings', s); };

  const getAllData = () => ({ plans, history, pb, bodyWeight, exerciseNotes, settings });
  const restoreData = async (data) => {
    if (data.plans) { setPlans(data.plans); await save('ironlog_plans', data.plans); }
    if (data.history) { setHistory(data.history); await save('ironlog_history', data.history); }
    if (data.pb) { setPb(data.pb); await save('ironlog_pb', data.pb); }
    if (data.bodyWeight) { setBodyWeight(data.bodyWeight); await save('ironlog_bw', data.bodyWeight); }
    if (data.exerciseNotes) { setExerciseNotes(data.exerciseNotes); await save('ironlog_notes', data.exerciseNotes); }
    if (data.settings) { setSettings(data.settings); await save('ironlog_settings', data.settings); }
  };

  return (
    <AppContext.Provider value={{
      plans, history, pb, bodyWeight, exerciseNotes, settings, initialized,
      savePlans, addHistory, clearHistory, updatePb, clearPbs,
      logBodyWeight, saveExerciseNotes, updateSettings,
      getAllData, restoreData,
      isHeavy: (name) => HEAVY.has(name),
    }}>
      {children}
    </AppContext.Provider>
  );
}
`);

console.log('AppContext done');
