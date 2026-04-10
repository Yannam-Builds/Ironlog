function normalizeText(value) {
  return String(value || '')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function toArray(value) {
  if (Array.isArray(value)) return value.filter(Boolean);
  if (value == null || value === '') return [];
  return [value];
}

function unique(values) {
  const seen = new Set();
  const result = [];
  values.forEach((value) => {
    const key = normalizeText(value);
    if (!key || seen.has(key)) return;
    seen.add(key);
    result.push(value);
  });
  return result;
}

function normalizeEquipment(value) {
  const text = normalizeText(value);
  if (!text) return 'other';
  if (text.includes('barbell')) return 'barbell';
  if (text.includes('dumbbell')) return 'dumbbell';
  if (text.includes('cable')) return 'cable';
  if (text.includes('machine') || text.includes('smith')) return 'machine';
  if (text.includes('bodyweight') || text.includes('body only') || text.includes('calisthenics')) return 'bodyweight';
  if (text.includes('band')) return 'band';
  if (text.includes('kettlebell')) return 'kettlebell';
  return 'other';
}

function inferEquipmentFromName(name) {
  const text = normalizeText(name);
  if (!text) return 'other';

  if (/\bdumbbell\b|\bdb\b/.test(text)) return 'dumbbell';
  if (/\bbarbell\b|\bez bar\b|\bez-bar\b|\btrap bar\b|\bhex bar\b|\blandmine\b/.test(text)) return 'barbell';
  if (/\bcable\b/.test(text)) return 'cable';
  if (/\bmachine\b|\bsmith\b|\bhammer strength\b|\bselectorized\b/.test(text)) return 'machine';
  if (/\bkettlebell\b|\bkb\b/.test(text)) return 'kettlebell';
  if (/\bband\b|\bresistance band\b/.test(text)) return 'band';

  if (/pull ?up|chin ?up|push ?up|dip|plank|crunch|sit ?up|leg raise|hollow body|mountain climber|bodyweight|body ?weight/.test(text)) {
    return 'bodyweight';
  }

  return 'other';
}

function resolveEquipmentClass(exercise) {
  const fromMeta = normalizeEquipment(exercise.equipment || exercise.mechanic || exercise.category);
  const fromName = inferEquipmentFromName(exercise.name);

  if (fromMeta === fromName) return fromMeta;
  if (fromMeta === 'other') return fromName;

  if (fromName === 'dumbbell' || fromName === 'bodyweight') return fromName;
  if (fromMeta === 'barbell' && (fromName === 'cable' || fromName === 'machine')) return fromName;
  return fromMeta;
}

const FAMILY_RULES = [
  { id: 'incline_press', movementPattern: 'horizontal_press', score: 10, match: [/incline.*press/, /incline.*bench/, /incline.*push/], lane: 'upper' },
  { id: 'decline_press', movementPattern: 'horizontal_press', score: 10, match: [/decline.*press/, /decline.*bench/, /dip/], lane: 'upper' },
  { id: 'horizontal_press', movementPattern: 'horizontal_press', score: 8, match: [/\bbench\b/, /\bpress\b/, /push up/, /pushup/], lane: 'upper' },
  { id: 'chest_fly', movementPattern: 'horizontal_press', score: 9, match: [/fly/, /crossover/, /pec deck/], lane: 'upper' },
  { id: 'vertical_press', movementPattern: 'vertical_press', score: 10, match: [/shoulder press/, /overhead press/, /\bohp\b/, /military press/, /arnold press/], lane: 'upper' },
  { id: 'vertical_pull', movementPattern: 'vertical_pull', score: 9, match: [/pull up/, /pullup/, /chin up/, /chinup/, /pulldown/, /lat pull/], lane: 'upper' },
  { id: 'horizontal_pull', movementPattern: 'horizontal_pull', score: 9, match: [/\brow\b/, /meadows/, /seal row/, /pullover/], lane: 'upper' },
  { id: 'shrug', movementPattern: 'shrug', score: 8, match: [/shrug/], lane: 'upper' },
  { id: 'rear_delt', movementPattern: 'shoulder_accessory', score: 8, match: [/rear delt/, /reverse pec deck/, /reverse fly/, /face pull/], lane: 'upper' },
  { id: 'lateral_raise', movementPattern: 'shoulder_accessory', score: 8, match: [/lateral raise/, /side raise/], lane: 'upper' },
  { id: 'front_raise', movementPattern: 'shoulder_accessory', score: 7, match: [/front raise/], lane: 'upper' },
  { id: 'biceps_curl', movementPattern: 'elbow_flexion', score: 8, match: [/curl/, /preacher/, /concentration/, /zottman/], lane: 'upper' },
  { id: 'hammer_curl', movementPattern: 'elbow_flexion', score: 9, match: [/hammer curl/, /rope hammer/], lane: 'upper' },
  { id: 'triceps_pushdown', movementPattern: 'elbow_extension', score: 8, match: [/pushdown/, /kickback/], lane: 'upper' },
  { id: 'triceps_overhead', movementPattern: 'elbow_extension', score: 9, match: [/overhead extension/, /skull crusher/, /skullcrusher/, /lying extension/], lane: 'upper' },
  { id: 'squat', movementPattern: 'squat', score: 10, match: [/squat/, /hack squat/, /leg press/, /belt squat/], lane: 'lower' },
  { id: 'lunge', movementPattern: 'lunge', score: 9, match: [/split squat/, /lunge/, /step up/, /step-up/, /walking lunge/], lane: 'lower' },
  { id: 'hinge', movementPattern: 'hinge', score: 10, match: [/deadlift/, /\brdl\b/, /romanian deadlift/, /good morning/, /hip thrust/, /glute bridge/, /pull through/, /pull-through/], lane: 'lower' },
  { id: 'leg_extension', movementPattern: 'knee_extension', score: 10, match: [/leg extension/], lane: 'lower' },
  { id: 'leg_curl', movementPattern: 'knee_flexion', score: 10, match: [/leg curl/, /nordic/, /glute ham/], lane: 'lower' },
  { id: 'calf_raise', movementPattern: 'ankle_extension', score: 10, match: [/calf/], lane: 'lower' },
  { id: 'core_crunch', movementPattern: 'core_flexion', score: 10, match: [/crunch/, /sit up/, /sit-up/, /cable crunch/], lane: 'core' },
  { id: 'leg_raise', movementPattern: 'core_flexion', score: 10, match: [/leg raise/, /toes to bar/, /v-up/, /hanging knee raise/], lane: 'core' },
  { id: 'plank', movementPattern: 'core_stability', score: 9, match: [/plank/, /ab wheel/, /ab roller/, /hollow/, /carry/], lane: 'core' },
  { id: 'cardio', movementPattern: 'conditioning', score: 8, match: [/run/, /bike/, /cardio/, /row erg/, /walk/, /conditioning/, /sled/], lane: 'conditioning' },
];

function detectFamily(name, primaryMuscles) {
  const text = normalizeText(name);
  let best = { id: 'general_strength', movementPattern: 'general_strength', lane: 'general', score: 0 };

  FAMILY_RULES.forEach((rule) => {
    const hit = rule.match.some((matcher) => matcher.test(text));
    if (hit && rule.score > best.score) best = rule;
  });

  const muscles = primaryMuscles.map(normalizeText).join(' ');
  if (best.score === 0) {
    if (muscles.includes('chest')) return { id: 'horizontal_press', movementPattern: 'horizontal_press', lane: 'upper', score: 4 };
    if (muscles.includes('lats') || muscles.includes('lat') || muscles.includes('back') || muscles.includes('trap')) return { id: 'horizontal_pull', movementPattern: 'horizontal_pull', lane: 'upper', score: 4 };
    if (muscles.includes('quadriceps') || muscles.includes('quads') || muscles.includes('adductors') || muscles.includes('abductors')) return { id: 'squat', movementPattern: 'squat', lane: 'lower', score: 4 };
    if (muscles.includes('hamstrings') || muscles.includes('glutes') || muscles.includes('lower back')) return { id: 'hinge', movementPattern: 'hinge', lane: 'lower', score: 4 };
    if (muscles.includes('shoulders') || muscles.includes('delt') || muscles.includes('rotator cuff')) return { id: 'vertical_press', movementPattern: 'vertical_press', lane: 'upper', score: 4 };
    if (muscles.includes('abdominals') || muscles.includes('obliques')) return { id: 'core_crunch', movementPattern: 'core_flexion', lane: 'core', score: 4 };
  }

  return best;
}

function detectUnilateral(name) {
  return /single|one arm|one-arm|one leg|single leg|single-leg|unilateral|split squat|lunge|step up|step-up/.test(normalizeText(name));
}

function detectCompound(familyId) {
  return new Set([
    'incline_press',
    'decline_press',
    'horizontal_press',
    'vertical_press',
    'vertical_pull',
    'horizontal_pull',
    'squat',
    'lunge',
    'hinge',
    'plank',
  ]).has(familyId);
}

function inferPrimaryRegion(primaryMuscles, family) {
  const joined = primaryMuscles.map(normalizeText).join(' ');
  if (family.lane === 'lower') return 'legs';
  if (family.lane === 'core') return 'core';
  if (family.id.includes('curl') || family.id.includes('triceps')) return 'arms';
  if (joined.includes('chest')) return 'chest';
  if (joined.includes('back') || joined.includes('lats') || joined.includes('lat') || joined.includes('traps') || joined.includes('lower back')) return 'back';
  if (joined.includes('shoulder') || joined.includes('delt') || joined.includes('rotator cuff')) return 'shoulders';
  if (joined.includes('biceps') || joined.includes('bicep') || joined.includes('triceps') || joined.includes('tricep') || joined.includes('forearm')) return 'arms';
  if (joined.includes('quadriceps') || joined.includes('hamstrings') || joined.includes('glutes') || joined.includes('calves') || joined.includes('adductors') || joined.includes('abductors')) return 'legs';
  if (joined.includes('abdominals') || joined.includes('obliques')) return 'core';
  return family.lane === 'upper' ? 'upper' : family.lane;
}

function deriveLoadModel(equipmentClass, family, compound) {
  if (equipmentClass === 'barbell' && compound && family.lane === 'upper') return 'barbell_upper_compound';
  if (equipmentClass === 'barbell' && compound && family.lane === 'lower') return 'barbell_lower_compound';
  if (equipmentClass === 'dumbbell') return 'dumbbell_increment';
  if (equipmentClass === 'machine' || equipmentClass === 'cable') return 'machine_or_cable';
  if (equipmentClass === 'bodyweight') return 'bodyweight_progression';
  if (equipmentClass === 'band') return 'band_progression';
  return compound ? 'general_compound' : 'general_isolation';
}

function deriveMicroloadStep(profile) {
  switch (profile.loadModel) {
    case 'barbell_upper_compound':
      return 2.5;
    case 'barbell_lower_compound':
      return 5;
    case 'dumbbell_increment':
      return 2.5;
    case 'machine_or_cable':
      return 2.5;
    case 'bodyweight_progression':
      return 2.5;
    default:
      return 1.25;
  }
}

function deriveRepCeiling(profile) {
  if (profile.loadModel === 'bodyweight_progression') return 12;
  if (profile.compound) return 8;
  if (profile.family === 'calf_raise' || profile.family === 'lateral_raise') return 15;
  if (profile.family === 'core_crunch' || profile.family === 'leg_raise') return 15;
  return 12;
}

export function buildExerciseProfile(exercise = {}) {
  const primaryMuscles = unique([
    ...toArray(exercise.primaryMuscles),
    ...toArray(exercise.primaryMuscle),
    ...toArray(exercise.primary),
    ...toArray(exercise.muscle),
    ...toArray(exercise.target),
    ...toArray(exercise.targetMuscle),
    ...toArray(exercise.bodyPart),
  ]);
  const family = detectFamily(exercise.name, primaryMuscles);
  const equipmentClass = resolveEquipmentClass(exercise);
  const compound = detectCompound(family.id);
  const base = {
    id: exercise.exerciseId || exercise.id || exercise.name,
    name: exercise.name || 'Unknown Exercise',
    family: family.id,
    movementPattern: family.movementPattern,
    lane: family.lane,
    equipmentClass,
    compound,
    unilateral: detectUnilateral(exercise.name),
    primaryRegion: inferPrimaryRegion(primaryMuscles, family),
    category: normalizeText(exercise.category || exercise.trackingType || 'strength') || 'strength',
    force: normalizeText(exercise.force),
  };
  const profile = { ...base, loadModel: deriveLoadModel(equipmentClass, family, compound) };
  return {
    ...profile,
    microloadStep: deriveMicroloadStep(profile),
    repCeiling: deriveRepCeiling({ ...profile, family: family.id }),
    confidence: Math.min(0.95, 0.45 + (family.score / 20)),
  };
}

export function buildExerciseProfileCatalog(exercises = []) {
  const byId = {};
  const byName = {};
  exercises.forEach((exercise) => {
    const profile = buildExerciseProfile(exercise);
    byId[profile.id] = profile;
    byName[normalizeText(profile.name)] = profile;
  });
  return { byId, byName };
}

export function resolveExerciseProfile(exercise, catalog = null) {
  const id = exercise?.exerciseId || exercise?.id || exercise?.name;
  const nameKey = normalizeText(exercise?.name);
  if (catalog?.byId?.[id]) return catalog.byId[id];
  if (catalog?.byName?.[nameKey]) return catalog.byName[nameKey];
  return buildExerciseProfile(exercise);
}

export { normalizeText };
