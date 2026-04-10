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

export const MUSCLE_FILTER_OPTIONS = [
  'Chest',
  'Upper Chest',
  'Mid Chest',
  'Lower Chest',
  'Back',
  'Lats',
  'Upper Back',
  'Traps',
  'Spinal Erectors',
  'Shoulders',
  'Front Delts',
  'Side Delts',
  'Rear Delts',
  'Rotator Cuff',
  'Arms',
  'Biceps',
  'Biceps Long Head',
  'Biceps Short Head',
  'Brachialis',
  'Triceps',
  'Triceps Long Head',
  'Triceps Lateral Head',
  'Triceps Medial Head',
  'Forearms',
  'Core',
  'Upper Abs',
  'Lower Abs',
  'Obliques',
  'Serratus',
  'Legs',
  'Quads',
  'Hamstrings',
  'Glutes',
  'Calves',
  'Adductors',
  'Abductors',
];

const CHIP_PRIORITY = Object.fromEntries([
  ...MUSCLE_FILTER_OPTIONS,
  'Strength',
  'Cardio',
  'Mobility',
  'Olympic',
  'Bodyweight',
  'Barbell',
  'Dumbbell',
  'Cable',
  'Machine',
  'Band',
  'Kettlebell',
  'Other',
].map((label, index) => [label, index]));

const RULES = [
  { patterns: ['serratus'], tags: ['Chest', 'Serratus'] },
  { patterns: ['upper chest', 'clavicular chest'], tags: ['Chest', 'Upper Chest'] },
  { patterns: ['mid chest', 'middle chest'], tags: ['Chest', 'Mid Chest'] },
  { patterns: ['lower chest', 'sternal chest'], tags: ['Chest', 'Lower Chest'] },
  { patterns: ['pec', 'pectoral', 'chest'], tags: ['Chest'] },

  { patterns: ['lats', 'lat'], tags: ['Back', 'Lats'] },
  { patterns: ['upper back', 'middle back', 'mid back', 'rhomboid'], tags: ['Back', 'Upper Back'] },
  { patterns: ['trap', 'trapezius'], tags: ['Back', 'Traps'] },
  { patterns: ['lower back', 'spinal erector', 'erector'], tags: ['Back', 'Spinal Erectors'] },
  { patterns: ['back'], tags: ['Back'] },

  { patterns: ['front delt', 'anterior delt', 'shoulder front', 'shoulder - front'], tags: ['Shoulders', 'Front Delts'] },
  { patterns: ['side delt', 'lateral delt', 'medial delt', 'shoulder side', 'shoulder - side'], tags: ['Shoulders', 'Side Delts'] },
  { patterns: ['rear delt', 'posterior delt', 'shoulder back', 'shoulder - back'], tags: ['Shoulders', 'Rear Delts'] },
  { patterns: ['rotator cuff', 'external shoulder rotation', 'internal shoulder rotation'], tags: ['Shoulders', 'Rotator Cuff'] },
  { patterns: ['shoulder', 'deltoid', 'delt'], tags: ['Shoulders'] },

  { patterns: ['biceps long head', 'long head biceps'], tags: ['Arms', 'Biceps', 'Biceps Long Head'] },
  { patterns: ['biceps short head', 'short head biceps'], tags: ['Arms', 'Biceps', 'Biceps Short Head'] },
  { patterns: ['brachialis'], tags: ['Arms', 'Biceps', 'Brachialis'] },
  { patterns: ['biceps', 'bicep'], tags: ['Arms', 'Biceps'] },
  { patterns: ['triceps long head', 'long head triceps'], tags: ['Arms', 'Triceps', 'Triceps Long Head'] },
  { patterns: ['triceps lateral', 'lateral head triceps'], tags: ['Arms', 'Triceps', 'Triceps Lateral Head'] },
  { patterns: ['triceps medial', 'medial head triceps'], tags: ['Arms', 'Triceps', 'Triceps Medial Head'] },
  { patterns: ['triceps', 'tricep'], tags: ['Arms', 'Triceps'] },
  { patterns: ['forearm', 'wrist', 'hand'], tags: ['Arms', 'Forearms'] },

  { patterns: ['upper abs'], tags: ['Core', 'Upper Abs'] },
  { patterns: ['lower abs'], tags: ['Core', 'Lower Abs'] },
  { patterns: ['oblique'], tags: ['Core', 'Obliques'] },
  { patterns: ['abdominal', 'abs', 'core'], tags: ['Core'] },

  { patterns: ['quad', 'quadriceps'], tags: ['Legs', 'Quads'] },
  { patterns: ['hamstring'], tags: ['Legs', 'Hamstrings'] },
  { patterns: ['glute', 'gluteus'], tags: ['Legs', 'Glutes'] },
  { patterns: ['calf', 'tibialis'], tags: ['Legs', 'Calves'] },
  { patterns: ['adductor', 'thigh inner', 'groin'], tags: ['Legs', 'Adductors'] },
  { patterns: ['abductor', 'thigh outer', 'hip abduction'], tags: ['Legs', 'Abductors'] },
  { patterns: ['legs'], tags: ['Legs'] },
];

const NAME_HINTS = [
  { patterns: ['incline bench', 'incline press', 'incline fly'], tags: ['Chest', 'Upper Chest'] },
  { patterns: ['decline bench', 'decline press'], tags: ['Chest', 'Lower Chest'] },
  { patterns: ['bench press', 'chest press', 'push up', 'push-up'], tags: ['Chest'] },
  { patterns: ['face pull', 'reverse fly', 'rear delt row'], tags: ['Shoulders', 'Rear Delts', 'Back', 'Upper Back'] },
  { patterns: ['shrug'], tags: ['Back', 'Traps'] },
  { patterns: ['pullover'], tags: ['Back', 'Lats', 'Chest'] },
  { patterns: ['lat pulldown', 'pull-up', 'pull up', 'chin-up', 'chin up'], tags: ['Back', 'Lats', 'Arms', 'Biceps'] },
  { patterns: ['high row', 't-bar', 'row'], tags: ['Back', 'Upper Back'] },
  { patterns: ['upright row'], tags: ['Back', 'Traps', 'Shoulders', 'Side Delts'] },
  { patterns: ['lateral raise'], tags: ['Shoulders', 'Side Delts'] },
  { patterns: ['front raise'], tags: ['Shoulders', 'Front Delts'] },
  { patterns: ['curl'], tags: ['Arms', 'Biceps'] },
  { patterns: ['hammer curl'], tags: ['Arms', 'Biceps', 'Brachialis', 'Forearms'] },
  { patterns: ['pushdown'], tags: ['Arms', 'Triceps', 'Triceps Lateral Head', 'Triceps Medial Head'] },
  { patterns: ['overhead tricep extension', 'overhead triceps extension', 'skull crusher'], tags: ['Arms', 'Triceps', 'Triceps Long Head'] },
  { patterns: ['wood chop', 'woodchop', 'twist', 'rotation'], tags: ['Core', 'Obliques'] },
  { patterns: ['crunch'], tags: ['Core', 'Upper Abs'] },
  { patterns: ['leg raise', 'hanging knee raise'], tags: ['Core', 'Lower Abs'] },
  { patterns: ['deadlift', 'romanian deadlift', 'rdl'], tags: ['Legs', 'Hamstrings', 'Glutes', 'Back', 'Spinal Erectors'] },
  { patterns: ['hip thrust', 'glute bridge', 'glute kickback'], tags: ['Legs', 'Glutes'] },
  { patterns: ['belt squat', 'hack squat', 'leg press', 'squat'], tags: ['Legs', 'Quads'] },
  { patterns: ['lunge', 'split squat'], tags: ['Legs', 'Quads', 'Glutes'] },
  { patterns: ['hip adduction'], tags: ['Legs', 'Adductors'] },
  { patterns: ['hip abduction', 'lateral walk'], tags: ['Legs', 'Abductors', 'Glutes'] },
  { patterns: ['calf raise'], tags: ['Legs', 'Calves'] },
];

const CATEGORY_RULES = [
  { patterns: ['cardio', 'conditioning', 'distance', 'duration', 'metcon'], tag: 'Cardio' },
  { patterns: ['stretch', 'mobility'], tag: 'Mobility' },
  { patterns: ['olympic'], tag: 'Olympic' },
  { patterns: ['strength', 'weight', 'rep', 'powerlifting'], tag: 'Strength' },
];

const EQUIPMENT_RULES = [
  { patterns: ['barbell', 'trap bar', 'landmine'], tag: 'Barbell' },
  { patterns: ['dumbbell'], tag: 'Dumbbell' },
  { patterns: ['cable'], tag: 'Cable' },
  { patterns: ['machine', 'smith'], tag: 'Machine' },
  { patterns: ['bodyweight', 'body only', 'ring', 'rings', 'dip bar', 'pull-up bar'], tag: 'Bodyweight' },
  { patterns: ['band'], tag: 'Band' },
  { patterns: ['kettlebell'], tag: 'Kettlebell' },
];

function addTags(tagSet, tags = []) {
  tags.forEach((tag) => {
    if (tag) tagSet.add(tag);
  });
}

function collectSignals(exercise) {
  return [
    exercise?.name,
    ...toArray(exercise?.primaryMuscles),
    ...toArray(exercise?.secondaryMuscles),
    ...toArray(exercise?.primaryMuscle),
    ...toArray(exercise?.primary),
    ...toArray(exercise?.muscle),
    ...toArray(exercise?.muscleGroup),
    ...toArray(exercise?.target),
    ...toArray(exercise?.targetMuscle),
    ...toArray(exercise?.bodyPart),
    exercise?.category,
    exercise?.trackingType,
    exercise?.equipment,
    exercise?.mechanic,
  ]
    .map(normalizeText)
    .filter(Boolean);
}

function matchesPattern(signal, pattern) {
  return signal.includes(pattern);
}

function sortTags(tags = []) {
  return [...tags].sort((a, b) => {
    const pa = CHIP_PRIORITY[a] ?? 999;
    const pb = CHIP_PRIORITY[b] ?? 999;
    if (pa !== pb) return pa - pb;
    return a.localeCompare(b);
  });
}

export function getExerciseFilterTags(exercise, {
  includeCategory = true,
  includeEquipment = false,
} = {}) {
  const signals = collectSignals(exercise);
  const tags = new Set();

  signals.forEach((signal) => {
    RULES.forEach((rule) => {
      if (rule.patterns.some((pattern) => matchesPattern(signal, pattern))) {
        addTags(tags, rule.tags);
      }
    });
  });

  const nameSignal = normalizeText(exercise?.name);
  if (nameSignal) {
    NAME_HINTS.forEach((rule) => {
      if (rule.patterns.some((pattern) => nameSignal.includes(pattern))) {
        addTags(tags, rule.tags);
      }
    });
  }

  if (includeCategory) {
    const categorySignals = [normalizeText(exercise?.category), normalizeText(exercise?.trackingType)];
    categorySignals.forEach((signal) => {
      CATEGORY_RULES.forEach((rule) => {
        if (signal && rule.patterns.some((pattern) => signal.includes(pattern))) {
          tags.add(rule.tag);
        }
      });
    });
    if (![...tags].some((tag) => ['Cardio', 'Mobility', 'Olympic'].includes(tag))) {
      tags.add('Strength');
    }
  }

  if (includeEquipment) {
    const equipmentSignals = [normalizeText(exercise?.equipment), nameSignal];
    equipmentSignals.forEach((signal) => {
      EQUIPMENT_RULES.forEach((rule) => {
        if (signal && rule.patterns.some((pattern) => signal.includes(pattern))) {
          tags.add(rule.tag);
        }
      });
    });
  }

  if (!tags.size) tags.add('Other');
  return sortTags(tags);
}

export function buildFilterChipOptions(exercises = [], options = {}) {
  const map = new Map();
  exercises.forEach((exercise) => {
    getExerciseFilterTags(exercise, options).forEach((tag) => {
      const key = tag.toLowerCase();
      if (!map.has(key)) map.set(key, tag);
    });
  });
  return sortTags([...map.values()]);
}

export function matchesExerciseFilter(exercise, filter, options = {}) {
  if (!filter || filter === 'All') return true;
  return getExerciseFilterTags(exercise, options).includes(filter);
}

export function getExercisePrimaryFocus(exercise) {
  const tags = getExerciseFilterTags(exercise, { includeCategory: false, includeEquipment: false });
  return tags[0] || 'Other';
}

export function getExerciseFilterSummary(exercise, limit = 4) {
  return getExerciseFilterTags(exercise, { includeCategory: false, includeEquipment: false }).slice(0, limit);
}

