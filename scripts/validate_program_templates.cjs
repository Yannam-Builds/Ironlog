const fs = require('fs');
const path = require('path');
const vm = require('vm');

const VALID_EQUIPMENT = new Set(['barbell', 'dumbbell', 'cable', 'machine', 'bodyweight', 'band', 'kettlebell', 'conditioning', 'other']);
const VALID_TRACKING = new Set(['weight_reps', 'duration', 'duration_distance']);
const VALID_MUSCLES = new Set([
  'Chest', 'Upper Chest', 'Mid Chest', 'Lower Chest',
  'Back', 'Lats', 'Upper Back', 'Traps', 'Spinal Erectors',
  'Shoulders', 'Front Delts', 'Side Delts', 'Rear Delts', 'Rotator Cuff',
  'Arms', 'Biceps', 'Biceps Long Head', 'Biceps Short Head', 'Brachialis',
  'Triceps', 'Triceps Long Head', 'Triceps Lateral Head', 'Triceps Medial Head',
  'Forearms', 'Core', 'Upper Abs', 'Lower Abs', 'Obliques', 'Serratus',
  'Legs', 'Quads', 'Hamstrings', 'Glutes', 'Calves', 'Adductors', 'Abductors',
  'Conditioning', 'Full Body', 'Mobility',
].map((value) => normalizeName(value)));

function normalizeName(value) {
  return String(value || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

function loadModule(filePath, exportsList) {
  const source = fs.readFileSync(filePath, 'utf8');
  const transformed = source
    .replace(/export const /g, 'const ')
    .replace(/export function /g, 'function ')
    .concat(`\nmodule.exports = { ${exportsList.join(', ')} };`);
  const script = new vm.Script(transformed, { filename: filePath });
  const sandbox = { module: { exports: {} }, exports: {}, console };
  script.runInNewContext(sandbox);
  return sandbox.module.exports;
}

function loadAllData() {
  const root = path.join(__dirname, '..', 'src', 'data');
  const v2LibraryPath = path.join(root, 'ironlog_exercise_library_v2.json');
  const v2Library = JSON.parse(fs.readFileSync(v2LibraryPath, 'utf8'));
  const EXERCISES = Array.isArray(v2Library.exercises) ? v2Library.exercises : [];
  const { EXERCISE_LIBRARY_ADDITIONS } = loadModule(path.join(root, 'exerciseLibraryAdditions.js'), ['EXERCISE_LIBRARY_ADDITIONS']);
  const { PROGRAM_TEMPLATE_CATALOG } = loadModule(path.join(root, 'programTemplates.js'), ['PROGRAM_TEMPLATE_CATALOG']);
  const { EXERCISE_ALIAS_MAP } = loadModule(path.join(root, 'exerciseAliases.js'), ['EXERCISE_ALIAS_MAP']);
  const { EXERCISE_ID_MAP } = loadModule(path.join(root, 'exerciseMapping.js'), ['EXERCISE_ID_MAP']);
  const { MUSCLE_FILTER_OPTIONS, buildFilterChipOptions } = loadModule(
    path.join(__dirname, '..', 'src', 'utils', 'exerciseFilters.js'),
    ['MUSCLE_FILTER_OPTIONS', 'buildFilterChipOptions']
  );
  return {
    EXERCISES,
    EXERCISE_LIBRARY_ADDITIONS,
    PROGRAM_TEMPLATE_CATALOG,
    EXERCISE_ALIAS_MAP,
    EXERCISE_ID_MAP,
    MUSCLE_FILTER_OPTIONS,
    buildFilterChipOptions,
  };
}

function normalizeEquipment(value) {
  const key = normalizeName(value);
  if (key === 'resistance band') return 'band';
  return key;
}

function dedupeExercises(exercises = []) {
  const byKey = new Map();
  for (const exercise of exercises) {
    const id = String(exercise?.id || '').trim();
    const nameKey = normalizeName(exercise?.name);
    const key = id || nameKey;
    if (!key) continue;
    if (!byKey.has(key)) {
      byKey.set(key, exercise);
      continue;
    }
    const existing = byKey.get(key);
    byKey.set(key, {
      ...existing,
      ...exercise,
      aliases: Array.from(new Set([...(existing.aliases || []), ...(exercise.aliases || [])].filter(Boolean))),
      primaryMuscles: (existing.primaryMuscles || []).length ? existing.primaryMuscles : exercise.primaryMuscles,
      secondaryMuscles: (existing.secondaryMuscles || []).length ? existing.secondaryMuscles : exercise.secondaryMuscles,
    });
  }
  return Array.from(byKey.values());
}

function normalizeMuscle(value) {
  const key = normalizeName(value);
  const map = {
    abdominals: 'core',
    quadriceps: 'quads',
    'middle back': 'upper back',
    'lower back': 'spinal erectors',
    neck: 'traps',
  };
  return map[key] || key;
}

function buildExerciseLookup(allExercises, aliasMap) {
  const bySlug = new Map();
  const byName = new Map();
  const issues = [];
  const normalizedNames = [];

  for (const exercise of allExercises) {
    const id = String(exercise.id || '');
    const name = String(exercise.name || '');
    const key = normalizeName(name);
    if (!id) issues.push(`exercise "${name}" missing id`);
    if (!name) issues.push(`exercise id "${id}" missing name`);

    if (id) {
      if (bySlug.has(id)) issues.push(`duplicate slug: ${id}`);
      bySlug.set(id, exercise);
    }
    if (key) {
      if (!byName.has(key)) byName.set(key, exercise);
      normalizedNames.push(key);
    }

    const trackingType = String(exercise.trackingType || '');
    if (trackingType && !VALID_TRACKING.has(trackingType)) {
      issues.push(`invalid trackingType "${trackingType}" on "${name}"`);
    }
    const equipment = normalizeEquipment(exercise.equipment);
    if (equipment && !VALID_EQUIPMENT.has(equipment)) {
      issues.push(`invalid equipment "${equipment}" on "${name}"`);
    }

    const muscles = Array.isArray(exercise.primaryMuscles)
      ? exercise.primaryMuscles
      : exercise.primaryMuscle ? [exercise.primaryMuscle] : [];
    for (const muscle of muscles) {
      if (!VALID_MUSCLES.has(normalizeMuscle(String(muscle)))) {
        issues.push(`invalid muscle "${muscle}" on "${name}"`);
      }
    }
  }

  for (const canonical of Object.values(aliasMap || {})) {
    const canonicalKey = normalizeName(canonical);
    if (canonicalKey && !normalizedNames.includes(canonicalKey)) {
      normalizedNames.push(canonicalKey);
    }
  }

  return { bySlug, byName, normalizedNames, issues };
}

function hasFuzzyExerciseMatch(name, lookup) {
  const key = normalizeName(name);
  if (!key) return false;
  if (lookup.byName.has(key)) return true;
  const tokens = key.split(' ').filter((token) => token.length > 2);
  if (!tokens.length) return false;
  return lookup.normalizedNames.some((candidate) =>
    tokens.every((token) => candidate.includes(token))
  );
}

function checkTemplate(template, exerciseLookup, exerciseIdMap) {
  const issues = [];
  const declaredEquipment = new Set((template.equipment || []).map((item) => normalizeEquipment(item)));

  if ((template.days || []).length !== Number(template.daysPerWeek || 0)) {
    issues.push(`daysPerWeek mismatch: expected ${template.daysPerWeek}, got ${(template.days || []).length}`);
  }

  if (!template.progressionModel || !template.deloadProtocol || !template.effortTarget) {
    issues.push('missing progression metadata');
  }

  if (String(template.category || '').toUpperCase() === 'SPECIALIZATION') {
    if (!template.blockDurationWeeks || !template.guardrailNotes) {
      issues.push('specialization template missing block duration or guardrail notes');
    }
  }

  const isHomeMinimal = String(template.category || '').toUpperCase() === 'HOME_MINIMAL';
  const isBandOnly = template.id === 'resistance_band_only_program';
  const lowerPowerFatigueSignals = [];

  for (const day of template.days || []) {
    const exercises = day.exercises || [];
    if (!exercises.length) {
      issues.push(`day "${day.name}" has no exercises`);
      continue;
    }

    const daySignals = {
      squat: false,
      deadlift: false,
      rdl: false,
    };

    for (const exercise of exercises) {
      const exerciseKey = normalizeName(exercise.name);
      if (!hasFuzzyExerciseMatch(exercise.name, exerciseLookup) && !exerciseIdMap[exercise.name]) {
        issues.push(`missing exercise reference: "${exercise.name}"`);
      }

      const eq = String(exercise.equipment || '').trim();
      if (!eq) {
        issues.push(`exercise "${exercise.name}" missing equipment`);
      } else if (!VALID_EQUIPMENT.has(normalizeEquipment(eq))) {
        issues.push(`exercise "${exercise.name}" has invalid equipment "${eq}"`);
      }

      const eqLower = normalizeEquipment(eq);
      if (eqLower && eqLower !== 'bodyweight' && !declaredEquipment.has(eqLower)) {
        issues.push(`equipment mismatch: "${exercise.name}" uses ${eq}`);
      }

      if (isHomeMinimal && ['barbell', 'cable', 'machine'].includes(eqLower)) {
        issues.push(`home/minimal template includes gym equipment: "${exercise.name}" (${eq})`);
      }

      if (isBandOnly && !['band', 'bodyweight'].includes(eqLower)) {
        issues.push(`band-only template contains "${exercise.name}" (${eq})`);
      }

      if (day.name && day.name.toLowerCase().includes('lower power')) {
        if (exerciseKey.includes('squat')) daySignals.squat = true;
        if (exerciseKey.includes('deadlift')) daySignals.deadlift = true;
        if (exerciseKey.includes('romanian deadlift') || exerciseKey.includes('rdl')) daySignals.rdl = true;
      }
    }

    if (daySignals.squat && daySignals.deadlift && daySignals.rdl) {
      lowerPowerFatigueSignals.push(day.name);
    }
  }

  if (lowerPowerFatigueSignals.length) {
    issues.push(`fatigue conflict in lower power day: squat + deadlift + RDL stacked (${lowerPowerFatigueSignals.join(', ')})`);
  }

  return issues;
}

function run() {
  const {
    EXERCISES,
    EXERCISE_LIBRARY_ADDITIONS,
    PROGRAM_TEMPLATE_CATALOG,
    EXERCISE_ALIAS_MAP,
    EXERCISE_ID_MAP,
    MUSCLE_FILTER_OPTIONS,
    buildFilterChipOptions,
  } = loadAllData();

  const mergedExercises = dedupeExercises([...EXERCISES, ...EXERCISE_LIBRARY_ADDITIONS]);
  const lookup = buildExerciseLookup(mergedExercises, EXERCISE_ALIAS_MAP);
  const templateReports = PROGRAM_TEMPLATE_CATALOG.map((template) => ({
    id: template.id,
    name: template.name,
    issues: checkTemplate(template, lookup, EXERCISE_ID_MAP),
  }));

  const duplicateTemplateNames = new Set();
  const seenTemplateNames = new Set();
  for (const template of PROGRAM_TEMPLATE_CATALOG) {
    const key = normalizeName(template.name);
    if (seenTemplateNames.has(key)) duplicateTemplateNames.add(template.name);
    seenTemplateNames.add(key);
  }
  if (duplicateTemplateNames.size) {
    templateReports.push({
      id: 'catalog',
      name: 'Template Catalog',
      issues: [`duplicate template names: ${Array.from(duplicateTemplateNames).join(', ')}`],
    });
  }

  const allIssues = [
    ...lookup.issues.map((issue) => ({ id: 'library', name: 'Exercise Library', issue })),
    ...templateReports.flatMap((report) => report.issues.map((issue) => ({ id: report.id, name: report.name, issue }))),
  ];

  const chipWarnings = [];
  try {
    const chips = buildFilterChipOptions(mergedExercises, { includeCategory: false, includeEquipment: false });
    const chipSet = new Set(chips.map((chip) => normalizeName(chip)));
    const intentionallyHiddenChips = new Set([
      'Mid Chest',
      'Biceps Long Head',
      'Biceps Short Head',
      'Serratus',
    ].map((value) => normalizeName(value)));
    const zeroChipMuscles = (MUSCLE_FILTER_OPTIONS || [])
      .filter((chip) => !chipSet.has(normalizeName(chip)))
      .filter((chip) => !intentionallyHiddenChips.has(normalizeName(chip)));
    if (zeroChipMuscles.length) {
      chipWarnings.push(`zero-chip muscle categories (hidden in UI): ${zeroChipMuscles.join(', ')}`);
    }
  } catch (error) {
    chipWarnings.push(`chip warning check failed: ${String(error?.message || error)}`);
  }

  if (allIssues.length) {
    console.log(`FAIL: ${allIssues.length} issue(s) detected`);
    for (const row of allIssues) {
      console.log(`- [${row.id}] ${row.name}: ${row.issue}`);
    }
    process.exitCode = 1;
    return;
  }

  if (chipWarnings.length) {
    console.log(`WARN: ${chipWarnings.length} warning(s)`);
    chipWarnings.forEach((warning) => console.log(`- ${warning}`));
  }

  console.log(`PASS: validated ${PROGRAM_TEMPLATE_CATALOG.length} templates and ${EXERCISES.length + EXERCISE_LIBRARY_ADDITIONS.length} exercises`);
}

run();
