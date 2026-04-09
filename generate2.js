const fs = require('fs');
const path = require('path');
function w(p, c) {
  const d = path.dirname(p);
  if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
  fs.writeFileSync(p, c);
  console.log('W: ' + p);
}

// ── EXERCISE LIBRARY DATA ──
w('src/data/exerciseLibrary.js', `
export const EXERCISES = [
  // CHEST
  { name: 'Barbell Bench Press', muscle: 'Chest', category: 'Push', equipment: 'Barbell', cue: 'Arch back, retract scapula, drive feet' },
  { name: 'Incline Barbell Bench Press', muscle: 'Chest', category: 'Push', equipment: 'Barbell', cue: 'Upper chest priority, 30-45 degree incline' },
  { name: 'DB Bench Press', muscle: 'Chest', category: 'Push', equipment: 'Dumbbell', cue: 'Full ROM, squeeze at top' },
  { name: 'Incline DB Bench Press', muscle: 'Chest', category: 'Push', equipment: 'Dumbbell', cue: 'Controlled negative, upper chest' },
  { name: 'Cable Fly Low to High', muscle: 'Chest', category: 'Push', equipment: 'Cable', cue: 'Upward arc, hard contraction at top' },
  { name: 'Cable Fly', muscle: 'Chest', category: 'Push', equipment: 'Cable', cue: 'Cross hands, squeeze at center' },
  { name: 'Pec Deck', muscle: 'Chest', category: 'Push', equipment: 'Machine', cue: 'Squeeze hard at center' },
  { name: 'Incline Smith Press', muscle: 'Chest', category: 'Push', equipment: 'Machine', cue: 'Upper chest, controlled path' },
  { name: 'Push-Up', muscle: 'Chest', category: 'Push', equipment: 'Bodyweight', cue: 'Core tight, full depth' },
  { name: 'Dips (Chest)', muscle: 'Chest', category: 'Push', equipment: 'Bodyweight', cue: 'Lean forward, elbows flared' },
  // BACK
  { name: 'Pull-Up', muscle: 'Back', category: 'Pull', equipment: 'Bodyweight', cue: 'Full hang to chin over bar' },
  { name: 'Weighted Pull-Up', muscle: 'Back', category: 'Pull', equipment: 'Bodyweight', cue: 'Add weight progressively' },
  { name: 'Chin-Up', muscle: 'Back', category: 'Pull', equipment: 'Bodyweight', cue: 'Supinated grip, bicep assist' },
  { name: 'Lat Pulldown', muscle: 'Back', category: 'Pull', equipment: 'Cable', cue: 'Wide grip, pull to upper chest' },
  { name: 'Single Arm Cable Pulldown', muscle: 'Back', category: 'Pull', equipment: 'Cable', cue: 'Incline bench chest down, best MMC' },
  { name: 'Barbell Row', muscle: 'Back', category: 'Pull', equipment: 'Barbell', cue: 'Hinge at hips, pull to navel' },
  { name: 'Single Arm DB Row', muscle: 'Back', category: 'Pull', equipment: 'Dumbbell', cue: 'Incline bench support, pull elbow back' },
  { name: 'Seated Cable Row', muscle: 'Back', category: 'Pull', equipment: 'Cable', cue: 'Sit upright, pull to lower chest' },
  { name: 'Face Pull', muscle: 'Back', category: 'Pull', equipment: 'Cable', cue: 'Pull to face, external rotate' },
  { name: 'Straight Arm Pulldown', muscle: 'Back', category: 'Pull', equipment: 'Cable', cue: 'Arms straight, pull with lats' },
  { name: 'Deadlift', muscle: 'Back', category: 'Pull', equipment: 'Barbell', cue: 'Hip hinge, flat back, drive legs' },
  { name: 'Sumo Deadlift', muscle: 'Back', category: 'Pull', equipment: 'Barbell', cue: 'Wide stance, toes out' },
  { name: 'Hyperextension', muscle: 'Back', category: 'Pull', equipment: 'Bodyweight', cue: 'Controlled extension, squeeze glutes' },
  { name: 'Meadows Row', muscle: 'Back', category: 'Pull', equipment: 'Barbell', cue: 'Perpendicular landmine stance' },
  { name: 'Chest Supported Row', muscle: 'Back', category: 'Pull', equipment: 'Dumbbell', cue: 'Incline bench, both arms' },
  // SHOULDERS
  { name: 'Barbell OHP', muscle: 'Shoulders', category: 'Push', equipment: 'Barbell', cue: 'Strict press overhead' },
  { name: 'DB OHP', muscle: 'Shoulders', category: 'Push', equipment: 'Dumbbell', cue: 'Seated or standing press' },
  { name: 'Arnold Press', muscle: 'Shoulders', category: 'Push', equipment: 'Dumbbell', cue: 'Rotate from front to press' },
  { name: 'Cable Lateral Raise', muscle: 'Shoulders', category: 'Push', equipment: 'Cable', cue: '3s negative, lead with elbow' },
  { name: 'DB Lateral Raise', muscle: 'Shoulders', category: 'Push', equipment: 'Dumbbell', cue: 'Slight forward lean, elbow leads' },
  { name: 'Lateral Raise Machine', muscle: 'Shoulders', category: 'Push', equipment: 'Machine', cue: 'Pad on forearms, lift out' },
  { name: 'Rear Delt Fly', muscle: 'Shoulders', category: 'Pull', equipment: 'Dumbbell', cue: 'Bent over, arms wide' },
  { name: 'Reverse Pec Deck', muscle: 'Shoulders', category: 'Pull', equipment: 'Machine', cue: 'Face pad, pull handles back' },
  { name: 'DB Shrugs', muscle: 'Shoulders', category: 'Pull', equipment: 'Dumbbell', cue: 'Straight up, 1s hold at top' },
  { name: 'Barbell Shrugs', muscle: 'Shoulders', category: 'Pull', equipment: 'Barbell', cue: 'Heavy, straight up and down' },
  // BICEPS
  { name: 'Barbell Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Barbell', cue: 'Strict curl, no swinging' },
  { name: 'EZ Bar Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Barbell', cue: 'Easier on wrists' },
  { name: 'DB Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Dumbbell', cue: 'Supinate at the top' },
  { name: 'Hammer Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Dumbbell', cue: 'Neutral grip, brachialis focus' },
  { name: 'Incline DB Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Dumbbell', cue: 'Full long head stretch' },
  { name: 'Concentration Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Dumbbell', cue: 'Elbow braced on inner thigh' },
  { name: 'Preacher Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Machine', cue: 'No cheating, full extension' },
  { name: 'Cable Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Cable', cue: 'Constant tension' },
  { name: 'Spider Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Dumbbell', cue: 'Incline bench, arms hanging' },
  { name: 'Zottman Curl', muscle: 'Biceps', category: 'Pull', equipment: 'Dumbbell', cue: 'Curl up supinated, lower pronated' },
  // TRICEPS
  { name: 'Close Grip Bench Press', muscle: 'Triceps', category: 'Push', equipment: 'Barbell', cue: 'Narrow grip, elbows tucked' },
  { name: 'Skull Crusher', muscle: 'Triceps', category: 'Push', equipment: 'Barbell', cue: 'Lower to forehead, extend' },
  { name: 'Rope Pushdown', muscle: 'Triceps', category: 'Push', equipment: 'Cable', cue: 'Flare rope at bottom' },
  { name: 'Single Arm Pushdown', muscle: 'Triceps', category: 'Push', equipment: 'Cable', cue: 'One arm, full extension' },
  { name: 'Rope Overhead Extension', muscle: 'Triceps', category: 'Push', equipment: 'Cable', cue: 'Face away, extend overhead' },
  { name: 'Single Arm OHE', muscle: 'Triceps', category: 'Push', equipment: 'Dumbbell', cue: 'One arm, deep stretch' },
  { name: 'Dips (Triceps)', muscle: 'Triceps', category: 'Push', equipment: 'Bodyweight', cue: 'Upright torso, elbows back' },
  { name: 'Diamond Push-Up', muscle: 'Triceps', category: 'Push', equipment: 'Bodyweight', cue: 'Hands together, elbows tight' },
  // LEGS
  { name: 'Back Squat', muscle: 'Legs', category: 'Legs', equipment: 'Barbell', cue: 'Bar on traps, break at hips and knees' },
  { name: 'Front Squat', muscle: 'Legs', category: 'Legs', equipment: 'Barbell', cue: 'Bar on front delts, upright torso' },
  { name: 'Bulgarian Split Squat', muscle: 'Legs', category: 'Legs', equipment: 'Dumbbell', cue: 'Front shin vertical, go deep' },
  { name: 'Romanian Deadlift', muscle: 'Legs', category: 'Legs', equipment: 'Barbell', cue: 'Hip hinge, hamstring stretch' },
  { name: 'Leg Press', muscle: 'Legs', category: 'Legs', equipment: 'Machine', cue: 'Standard foot placement' },
  { name: 'Leg Press High Feet', muscle: 'Legs', category: 'Legs', equipment: 'Machine', cue: 'High placement, posterior chain' },
  { name: 'Hack Squat', muscle: 'Legs', category: 'Legs', equipment: 'Machine', cue: 'Quad focus' },
  { name: 'Leg Extension', muscle: 'Legs', category: 'Legs', equipment: 'Machine', cue: 'Full quad contraction at top' },
  { name: 'Leg Curl Machine', muscle: 'Legs', category: 'Legs', equipment: 'Machine', cue: 'Curl heels to glutes' },
  { name: 'Nordic Curl', muscle: 'Legs', category: 'Legs', equipment: 'Bodyweight', cue: 'Eccentric hamstring, control down' },
  { name: 'Hip Thrust', muscle: 'Legs', category: 'Legs', equipment: 'Barbell', cue: 'Back on bench, drive hips up' },
  { name: 'Walking Lunge', muscle: 'Legs', category: 'Legs', equipment: 'Dumbbell', cue: 'Long stride, upright torso' },
  { name: 'Step Up', muscle: 'Legs', category: 'Legs', equipment: 'Dumbbell', cue: 'Drive through front foot' },
  { name: 'Lateral Band Walk', muscle: 'Legs', category: 'Legs', equipment: 'Band', cue: 'Stay low, band at ankles' },
  { name: 'Single Leg Calf Raise', muscle: 'Legs', category: 'Legs', equipment: 'Bodyweight', cue: 'On a step, full ROM' },
  { name: 'Standing Calf Raise', muscle: 'Legs', category: 'Legs', equipment: 'Machine', cue: 'Full stretch and squeeze' },
  { name: 'Seated Calf Raise', muscle: 'Legs', category: 'Legs', equipment: 'Machine', cue: 'Soleus focus' },
  // CORE
  { name: 'Weighted Cable Crunch', muscle: 'Core', category: 'Push', equipment: 'Cable', cue: 'Crunch into hips, not knees' },
  { name: 'Hanging Leg Raise', muscle: 'Core', category: 'Pull', equipment: 'Bodyweight', cue: 'Controlled, zero swinging' },
  { name: 'Ab Wheel Rollout', muscle: 'Core', category: 'Push', equipment: 'Other', cue: 'Full extension, core tight' },
  { name: 'Plank', muscle: 'Core', category: 'Other', equipment: 'Bodyweight', cue: 'Hold rigid, core engaged' },
  { name: 'Side Plank', muscle: 'Core', category: 'Other', equipment: 'Bodyweight', cue: 'Oblique hold' },
  { name: 'Dragon Flag', muscle: 'Core', category: 'Other', equipment: 'Bodyweight', cue: 'Advanced full body lever' },
  { name: 'L-Sit', muscle: 'Core', category: 'Other', equipment: 'Bodyweight', cue: 'Hold legs parallel to ground' },
  { name: 'Toes to Bar', muscle: 'Core', category: 'Pull', equipment: 'Bodyweight', cue: 'Hang, touch toes to bar' },
  { name: 'Russian Twist', muscle: 'Core', category: 'Other', equipment: 'Other', cue: 'Rotate with weight' },
  { name: 'Dead Bug', muscle: 'Core', category: 'Other', equipment: 'Bodyweight', cue: 'Opposite arm and leg extend' },
  { name: 'V-Up', muscle: 'Core', category: 'Other', equipment: 'Bodyweight', cue: 'Touch toes at top' },
  // MOBILITY
  { name: 'Hip 90/90 Stretch', muscle: 'Mobility', category: 'Other', equipment: 'Bodyweight', cue: 'Internal/external hip rotation' },
  { name: "World's Greatest Stretch", muscle: 'Mobility', category: 'Other', equipment: 'Bodyweight', cue: 'T-spine + hip flexor + hamstring' },
  { name: 'Ankle Mobility Drill', muscle: 'Mobility', category: 'Other', equipment: 'Bodyweight', cue: 'Circles + dorsiflexion' },
  { name: 'Cat Cow', muscle: 'Mobility', category: 'Other', equipment: 'Bodyweight', cue: 'Spine flexion and extension' },
  { name: 'Couch Stretch', muscle: 'Mobility', category: 'Other', equipment: 'Bodyweight', cue: 'Quad and hip flexor' },
  { name: 'Deep Squat Hold', muscle: 'Mobility', category: 'Other', equipment: 'Bodyweight', cue: 'Hold at bottom of squat' },
  // CARDIO
  { name: 'Incline Treadmill Walk', muscle: 'Cardio', category: 'Other', equipment: 'Machine', cue: '10-15% incline, brisk walk' },
  { name: 'Running', muscle: 'Cardio', category: 'Other', equipment: 'Other', cue: 'Steady state or intervals' },
  { name: 'Jump Rope', muscle: 'Cardio', category: 'Other', equipment: 'Other', cue: 'Light on feet, wrist rotation' },
  { name: 'Rowing Machine', muscle: 'Cardio', category: 'Other', equipment: 'Machine', cue: 'Full body pull' },
  { name: 'Assault Bike', muscle: 'Cardio', category: 'Other', equipment: 'Machine', cue: 'Full body bike' },
];

export const MUSCLES = ['All', 'Chest', 'Back', 'Shoulders', 'Biceps', 'Triceps', 'Legs', 'Core', 'Mobility', 'Cardio'];
export const CATEGORIES = ['All', 'Push', 'Pull', 'Legs', 'Other'];
`);

// ── PLATE CALCULATOR ──
w('src/utils/plateCalc.js', `
const PLATES = [25, 20, 15, 10, 5, 2.5, 1.25];
const BAR_WEIGHT = 20;

export function calcPlates(targetKg) {
  const remaining = (targetKg - BAR_WEIGHT) / 2;
  if (remaining <= 0) return { valid: false, each: [], total: targetKg };
  const each = [];
  let left = remaining;
  for (const p of PLATES) {
    while (left >= p) { each.push(p); left = Math.round((left - p) * 100) / 100; }
  }
  return { valid: true, each, total: targetKg };
}
`);

// ── BACKUP UTILS ──
w('src/utils/backup.js', `
import * as FileSystem from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as DocumentPicker from 'expo-document-picker';

export async function exportBackup(data) {
  const json = JSON.stringify(data, null, 2);
  const date = new Date().toISOString().split('T')[0];
  const uri = FileSystem.documentDirectory + 'ironlog_backup_' + date + '.json';
  await FileSystem.writeAsStringAsync(uri, json, { encoding: FileSystem.EncodingType.UTF8 });
  await Sharing.shareAsync(uri, { mimeType: 'application/json', dialogTitle: 'Save IRONLOG Backup' });
  return uri;
}

export async function importBackup() {
  const result = await DocumentPicker.getDocumentAsync({ type: 'application/json', copyToCacheDirectory: true });
  if (result.canceled) return null;
  const asset = result.assets[0];
  const content = await FileSystem.readAsStringAsync(asset.uri, { encoding: FileSystem.EncodingType.UTF8 });
  return JSON.parse(content);
}
`);

// ── 1RM UTIL ──
w('src/utils/oneRM.js', `
export function epley(weight, reps) {
  if (!weight || !reps || reps <= 0) return 0;
  return Math.round(weight * (1 + reps / 30));
}
`);

console.log('Part 1 done');
