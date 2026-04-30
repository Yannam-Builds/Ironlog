const ALIAS_ENTRIES = [
  ['pullups', 'Pull-Up'],
  ['pull up', 'Pull-Up'],
  ['pull-up', 'Pull-Up'],
  ['pullups bw', 'Pull-Up'],
  ['pushups', 'Push-Up'],
  ['push up', 'Push-Up'],
  ['push-up', 'Push-Up'],
  ['weighted pull ups', 'Weighted Pull-Up'],
  ['weighted pullup', 'Weighted Pull-Up'],
  ['weighted pull-up', 'Weighted Pull-Up'],
  ['weighted chin up', 'Weighted Pull-Up'],
  ['seated cable rows', 'Seated Cable Row'],
  ['seated cable row', 'Seated Cable Row'],
  ['cable rows seated', 'Seated Cable Row'],
  ['leg extensions', 'Leg Extension'],
  ['leg extension machine', 'Leg Extension'],
  ['hammer curls', 'Hammer Curl'],
  ['hammer curl', 'Hammer Curl'],
  ['db hammer curl', 'Hammer Curl'],
  ["worlds greatest stretch", "World's Greatest Stretch"],
  ["world's greatest stretch", "World's Greatest Stretch"],
  ['bench press smith machine', 'Bench Press - Smith Machine'],
  ['smith machine bench press', 'Bench Press - Smith Machine'],
  ['smith bench press', 'Bench Press - Smith Machine'],
  ['incline bench press smith machine', 'Incline Bench Press - Smith Machine'],
  ['smith machine incline bench press', 'Incline Bench Press - Smith Machine'],
  ['smith incline bench', 'Incline Bench Press - Smith Machine'],
  ['squat smith machine', 'Squat - Smith Machine'],
  ['smith machine squat', 'Squat - Smith Machine'],
  ['barbell hack squat', 'Hack Squat - Barbell'],
  ['hack squat barbell', 'Hack Squat - Barbell'],
  ['rear delt row barbell', 'Rear Delt Row - Barbell'],
  ['barbell rear delt row', 'Rear Delt Row - Barbell'],
  ['assisted pullup machine', 'Assisted Pull-Up Machine'],
  ['assisted pull up machine', 'Assisted Pull-Up Machine'],
  ['machine assisted pull up', 'Assisted Pull-Up Machine'],
  ['machine lateral raise', 'Machine Lateral Raise'],
  ['reverse pec deck fly', 'Reverse Pec Deck'],
  ['single arm cable lateral raise', 'Cable Lateral Raise (Single Arm)'],
  ['chest supported row', 'Chest-Supported Row'],
  ['chest supported dumbbell row', 'Chest-Supported Dumbbell Row'],
  ['pendlay barbell row', 'Pendlay Row'],
  ['nordic curl', 'Nordic Hamstring Curl'],
  ['tib raise', 'Tibialis Raise'],
  ['tibialis machine', 'Tibialis Raise (Machine)'],
  ['ski erg', 'Ski Erg'],
  ['airbike', 'Air Bike'],
];

export const EXERCISE_ALIAS_MAP = ALIAS_ENTRIES.reduce((acc, [key, canonical]) => {
  acc[key] = canonical;
  return acc;
}, {});

export function normalizeAliasKey(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

export function resolveCanonicalExerciseName(value) {
  const key = normalizeAliasKey(value);
  return EXERCISE_ALIAS_MAP[key] || String(value || '').trim();
}


