import { Q } from '@nozbe/watermelondb';
import { database } from '../database';
import seedPayload from './exerciseLibrary.json';
import { isExerciseSeedComplete, markExerciseSeedComplete } from './seedStatus';

const BATCH_SIZE = 150;

function toTitleCase(value) {
  return String(value || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0].toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
}

function normalizeName(value) {
  return String(value || '').trim().toLowerCase();
}

function normalizeEquipment(value) {
  const raw = String(value || '').trim();
  if (!raw) return 'Other';
  return toTitleCase(raw);
}

function normalizeCategory(exercise) {
  const fromCategory = String(exercise?.category || '').toLowerCase();
  if (fromCategory.includes('cardio') || fromCategory.includes('conditioning')) return 'cardio';
  if (fromCategory.includes('stretch') || fromCategory.includes('mobility')) return 'mobility';
  return 'strength';
}

function buildMuscleRows(exercise) {
  const primary = Array.isArray(exercise.primaryMuscles)
    ? exercise.primaryMuscles.filter(Boolean)
    : [exercise.primaryMuscle].filter(Boolean);
  const secondary = Array.isArray(exercise.secondaryMuscles)
    ? exercise.secondaryMuscles.filter(Boolean)
    : [];
  if (primary.length === 0 && secondary.length === 0) {
    return [];
  }

  if (secondary.length === 0) {
    const each = 1 / Math.max(primary.length, 1);
    return primary.map((muscle) => ({
      muscle: toTitleCase(muscle),
      role: 'primary',
      contribution: each,
    }));
  }

  const primaryWeight = 0.7 / Math.max(primary.length, 1);
  const secondaryWeight = 0.3 / Math.max(secondary.length, 1);
  return [
    ...primary.map((muscle) => ({
      muscle: toTitleCase(muscle),
      role: 'primary',
      contribution: primaryWeight,
    })),
    ...secondary.map((muscle) => ({
      muscle: toTitleCase(muscle),
      role: 'secondary',
      contribution: secondaryWeight,
    })),
  ];
}

/**
 * Write prepared WatermelonDB operations in small chunks.
 * Each chunk is its own database.write() → database.batch() transaction,
 * releasing the SQLite write lock between chunks so the event loop stays
 * responsive. batch() must always be called inside write() per WM v0.28.
 */
async function batchInChunks(preparedOps) {
  for (let i = 0; i < preparedOps.length; i += BATCH_SIZE) {
    const chunk = preparedOps.slice(i, i + BATCH_SIZE);
    // eslint-disable-next-line no-await-in-loop
    await database.write(async () => {
      await database.batch(...chunk);
    });
  }
}

export async function seedExercisesIfNeeded() {
  if (await isExerciseSeedComplete()) {
    return { seeded: false, reason: 'already_seeded' };
  }

  const source = Array.isArray(seedPayload?.exercises) ? seedPayload.exercises : [];
  const rows = source.filter((entry) => String(entry?.name || '').trim());
  const exercisesCollection = database.get('exercises');
  const musclesCollection = database.get('exercise_muscles');
  const now = Date.now();

  // ── Phase 1: read existing exercises (no write lock held) ─────────────────
  const existing = await exercisesCollection.query().fetch();
  const existingKeys = new Set(existing.map((item) => item.normalizedName));

  const creates = [];
  const exerciseByKey = new Map();

  rows.forEach((entry) => {
    const name = String(entry.name).trim();
    const normalizedName = normalizeName(name);
    if (!normalizedName || existingKeys.has(normalizedName)) return;
    existingKeys.add(normalizedName);
    creates.push(
      exercisesCollection.prepareCreate((record) => {
        record.name = name;
        record.normalizedName = normalizedName;
        record.primaryMuscle = toTitleCase(
          entry.primaryMuscle ||
            (Array.isArray(entry.primaryMuscles) ? entry.primaryMuscles[0] : '') ||
            'Other'
        );
        record.equipment = normalizeEquipment(entry.equipment);
        record.category = normalizeCategory(entry);
        record.isCustom = false;
        record.source = 'built_in';
        record.notes = entry.notes ? String(entry.notes) : '';
        record._raw.created_at = now;
        record.updatedAt = now;
      })
    );
    exerciseByKey.set(normalizedName, entry);
  });

  // ── Phase 2: write exercises in small chunks (lock released between each) ─
  if (creates.length > 0) {
    await batchInChunks(creates);
  }

  // ── Phase 3: read inserted rows to get their WM IDs (no write lock held) ──
  const muscleCreates = [];
  if (exerciseByKey.size > 0) {
    const inserted = await exercisesCollection
      .query(Q.where('source', 'built_in'))
      .fetch();

    inserted.forEach((exercise) => {
      const sourceExercise = exerciseByKey.get(exercise.normalizedName);
      if (!sourceExercise) return;
      buildMuscleRows(sourceExercise).forEach((row) => {
        muscleCreates.push(
          musclesCollection.prepareCreate((muscle) => {
            muscle.exercise.set(exercise);
            muscle.muscle = row.muscle;
            muscle.role = row.role;
            muscle.contributionFraction = row.contribution;
            muscle._raw.created_at = now;
            muscle.updatedAt = now;
          })
        );
      });
    });
  }

  // ── Phase 4: write muscle rows in small chunks ────────────────────────────
  if (muscleCreates.length > 0) {
    await batchInChunks(muscleCreates);
  }

  await markExerciseSeedComplete();
  return { seeded: true, count: rows.length };
}

/**
 * Backfill exercise_muscles rows for any built-in exercise that has none.
 *
 * This is needed for users who installed before exercise_muscles was
 * populated during seeding — their seed flag is already set so
 * seedExercisesIfNeeded() skips, leaving the muscles table empty.
 *
 * Safe to call on every launch: it queries current state before doing
 * any work and is a no-op when every built-in exercise already has rows.
 */
export async function backfillExerciseMusclesIfNeeded() {
  const musclesCollection = database.get('exercise_muscles');
  const exercisesCollection = database.get('exercises');

  // Fast path: if there are already muscle rows, nothing to do.
  const existingCount = await musclesCollection.query().fetchCount();
  if (existingCount > 0) return { backfilled: false, reason: 'already_populated' };

  const source = Array.isArray(seedPayload?.exercises) ? seedPayload.exercises : [];
  const libraryByName = new Map(
    source
      .filter((e) => String(e?.name || '').trim())
      .map((e) => [normalizeName(e.name), e])
  );

  const allExercises = await exercisesCollection.query().fetch();
  const now = Date.now();

  const muscleCreates = [];
  allExercises.forEach((exercise) => {
    const sourceEntry = libraryByName.get(exercise.normalizedName);
    if (!sourceEntry) return;
    buildMuscleRows(sourceEntry).forEach((row) => {
      muscleCreates.push(
        musclesCollection.prepareCreate((muscle) => {
          muscle.exercise.set(exercise);
          muscle.muscle = row.muscle;
          muscle.role = row.role;
          muscle.contributionFraction = row.contribution;
          muscle._raw.created_at = now;
          muscle.updatedAt = now;
        })
      );
    });
  });

  if (muscleCreates.length === 0) return { backfilled: false, reason: 'no_exercises_matched' };

  // Write in small chunks — each chunk is its own write transaction
  await batchInChunks(muscleCreates);

  return { backfilled: true, count: muscleCreates.length };
}
