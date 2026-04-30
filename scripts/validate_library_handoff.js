const fs = require('fs');
const path = require('path');

const baseDir = path.resolve(__dirname, '..');
const handoffPath = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.join(baseDir, 'ironlog_exercise_library_v2.json');
const appRoot = process.argv[3] ? path.resolve(process.argv[3]) : 'Z:\\ironlog';

const appLibraryPath = path.join(appRoot, 'src', 'data', 'exerciseLibrary.js');
const appAdditionsPath = path.join(appRoot, 'src', 'data', 'exerciseLibraryAdditions.js');

const requiredTopLevel = [
  'meta',
  'exercises',
  'idMigrationMap',
  'duplicateResolutionLog',
  'breakingChanges',
  'validationReport',
  'smokeTestSamples',
];

const requiredExerciseFields = [
  'id',
  'name',
  'primaryMuscle',
  'primaryMuscles',
  'secondaryMuscles',
  'equipment',
  'equipmentDetail',
  'apparatus',
  'trackingType',
  'category',
  'isBodyweight',
  'requiresExternalLoad',
  'movementPattern',
  'difficulty',
  'aliases',
  'sourceTags',
];

function fail(errors, counts = {}) {
  console.error('FAIL');
  console.error(JSON.stringify({ counts, errors }, null, 2));
  process.exit(1);
}

function extractExportArray(text, exportName) {
  const marker = `export const ${exportName}`;
  const start = text.indexOf(marker);
  if (start < 0) {
    throw new Error(`Missing export ${exportName}`);
  }
  const bracket = text.indexOf('[', start);
  if (bracket < 0) {
    throw new Error(`Missing array for export ${exportName}`);
  }

  let depth = 0;
  let inString = false;
  let quote = '';
  let escape = false;
  for (let i = bracket; i < text.length; i += 1) {
    const ch = text[i];
    if (inString) {
      if (escape) {
        escape = false;
      } else if (ch === '\\') {
        escape = true;
      } else if (ch === quote) {
        inString = false;
      }
      continue;
    }
    if (ch === '"' || ch === "'") {
      inString = true;
      quote = ch;
    } else if (ch === '[') {
      depth += 1;
    } else if (ch === ']') {
      depth -= 1;
      if (depth === 0) {
        return text.slice(bracket, i + 1);
      }
    }
  }
  throw new Error(`Could not parse array for export ${exportName}`);
}

function parseAppObjects(filePath, exportName) {
  const text = fs.readFileSync(filePath, 'utf8');
  return JSON.parse(extractExportArray(text, exportName));
}

function duplicates(values) {
  const seen = new Set();
  const dupes = new Set();
  for (const value of values) {
    if (seen.has(value)) dupes.add(value);
    seen.add(value);
  }
  return [...dupes];
}

const errors = [];

let handoff;
try {
  handoff = JSON.parse(fs.readFileSync(handoffPath, 'utf8'));
} catch (error) {
  fail([`Could not parse handoff JSON: ${error.message}`]);
}

for (const key of requiredTopLevel) {
  if (!(key in handoff)) errors.push(`Missing top-level key: ${key}`);
}

if (errors.length) fail(errors);

const { meta, exercises, idMigrationMap, duplicateResolutionLog, validationReport, smokeTestSamples } = handoff;

if (!Array.isArray(exercises)) errors.push('exercises must be an array');
if (!Array.isArray(duplicateResolutionLog)) errors.push('duplicateResolutionLog must be an array');
if (!Array.isArray(smokeTestSamples)) errors.push('smokeTestSamples must be an array');
if (!idMigrationMap || typeof idMigrationMap !== 'object' || Array.isArray(idMigrationMap)) {
  errors.push('idMigrationMap must be an object');
}

if (meta.schemaVersion !== '2.0.0') errors.push('meta.schemaVersion must be 2.0.0');

const ids = exercises.map((exercise) => exercise.id);
const names = exercises.map((exercise) => exercise.name);
const idDupes = duplicates(ids);
const nameDupes = duplicates(names);
if (idDupes.length) errors.push(`Duplicate exercise ids: ${idDupes.join(', ')}`);
if (nameDupes.length) errors.push(`Duplicate canonical exercise names: ${nameDupes.join(', ')}`);

const enumChecks = [
  ['equipment', meta.appEquipmentValues],
  ['equipmentDetail', meta.detailedEquipmentValues],
  ['trackingType', meta.trackingTypeValues],
  ['category', meta.categoryValues],
  ['movementPattern', meta.movementPatternValues],
  ['difficulty', meta.difficultyValues],
];

for (const exercise of exercises) {
  for (const field of requiredExerciseFields) {
    if (!(field in exercise)) errors.push(`${exercise.name || exercise.id || '<unknown>'}: missing ${field}`);
    if (exercise[field] === null || exercise[field] === undefined) errors.push(`${exercise.name || exercise.id || '<unknown>'}: null ${field}`);
  }
  if (!Array.isArray(exercise.primaryMuscles)) errors.push(`${exercise.name}: primaryMuscles must be array`);
  if (!Array.isArray(exercise.secondaryMuscles)) errors.push(`${exercise.name}: secondaryMuscles must be array`);
  if (!Array.isArray(exercise.apparatus)) errors.push(`${exercise.name}: apparatus must be array`);
  if (!Array.isArray(exercise.aliases)) errors.push(`${exercise.name}: aliases must be array`);
  if (!Array.isArray(exercise.sourceTags)) errors.push(`${exercise.name}: sourceTags must be array`);
  if (typeof exercise.isBodyweight !== 'boolean') errors.push(`${exercise.name}: isBodyweight must be boolean`);
  if (typeof exercise.requiresExternalLoad !== 'boolean') errors.push(`${exercise.name}: requiresExternalLoad must be boolean`);
  for (const [field, allowed] of enumChecks) {
    if (!allowed.includes(exercise[field])) {
      errors.push(`${exercise.name}: invalid ${field}=${exercise[field]}`);
    }
  }
  for (const muscle of [exercise.primaryMuscle, ...exercise.primaryMuscles, ...exercise.secondaryMuscles]) {
    if (!meta.muscleValues.includes(muscle)) {
      errors.push(`${exercise.name}: invalid muscle=${muscle}`);
    }
  }
}

let oldIds = [];
try {
  const appExercises = parseAppObjects(appLibraryPath, 'EXERCISES');
  const additions = parseAppObjects(appAdditionsPath, 'EXERCISE_LIBRARY_ADDITIONS');
  oldIds = [...appExercises, ...additions].map((exercise) => exercise.id);
} catch (error) {
  errors.push(`Could not extract old IDs from app files: ${error.message}`);
}

for (const oldId of oldIds) {
  if (!(oldId in idMigrationMap)) errors.push(`Old ID missing from migration map: ${oldId}`);
}

for (const entry of duplicateResolutionLog) {
  if (!entry.keptId || !ids.includes(entry.keptId)) {
    errors.push(`duplicateResolutionLog keptId missing from exercises: ${entry.keptId}`);
  }
  for (const removedId of entry.removedIds || []) {
    if (!(removedId in idMigrationMap)) errors.push(`removedId missing from migration map: ${removedId}`);
    if (idMigrationMap[removedId] !== entry.keptId) {
      errors.push(`removedId ${removedId} does not map to keptId ${entry.keptId}`);
    }
  }
}

if (smokeTestSamples.length !== 7) errors.push('smokeTestSamples must contain exactly 7 records');
for (const sample of smokeTestSamples) {
  if (!sample.id || !ids.includes(sample.id)) {
    errors.push(`Smoke sample ID is not present in exercises: ${sample.id}`);
  }
}

if (validationReport.allOldIdsAccountedFor !== true) errors.push('validationReport.allOldIdsAccountedFor must be true');
if (validationReport.idCollisions !== 0) errors.push('validationReport.idCollisions must be 0');
if (validationReport.nameCollisions !== 0) errors.push('validationReport.nameCollisions must be 0');
if (validationReport.manualReviewRemaining !== 0) errors.push('validationReport.manualReviewRemaining must be 0');
if (validationReport.sourceVerificationComplete !== true) errors.push('validationReport.sourceVerificationComplete must be true');

const counts = {
  exercises: exercises.length,
  oldIds: oldIds.length,
  migrations: Object.keys(idMigrationMap).length,
  duplicateLogEntries: duplicateResolutionLog.length,
  smokeSamples: smokeTestSamples.length,
  manualReviewRemaining: validationReport.manualReviewRemaining,
};

if (errors.length) fail(errors, counts);

console.log('PASS');
console.log(JSON.stringify(counts, null, 2));
