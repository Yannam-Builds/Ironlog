import { Q } from '@nozbe/watermelondb';
import { map } from 'rxjs/operators';
import { database } from '../database';
import { seedExercisesIfNeeded as seedBuiltInExercises, backfillExerciseMusclesIfNeeded as backfillMuscles } from '../seed/exerciseSeed';
import {
  normalizeExerciseName,
  normalizeExerciseNameKey,
  requireNonEmpty,
  validateContributionFraction,
} from '../../utils/validation';

const EXERCISES = database.get('exercises');
const EXERCISE_MUSCLES = database.get('exercise_muscles');

function buildExerciseQuery(filters = {}) {
  const clauses = [];
  if (filters.primaryMuscle && filters.primaryMuscle !== 'all') {
    clauses.push(Q.where('primary_muscle', filters.primaryMuscle));
  }
  if (filters.equipment && filters.equipment !== 'all') {
    clauses.push(Q.where('equipment', filters.equipment));
  }
  if (filters.category && filters.category !== 'all') {
    clauses.push(Q.where('category', filters.category));
  }
  if (filters.customOnly) {
    clauses.push(Q.where('is_custom', true));
  }
  return clauses.length ? EXERCISES.query(...clauses) : EXERCISES.query();
}

export async function seedExercisesIfNeeded() {
  return seedBuiltInExercises();
}

export async function backfillExerciseMusclesIfNeeded() {
  return backfillMuscles();
}

export function getExercisesObservable(filters = {}) {
  return buildExerciseQuery(filters).observe();
}

function mapExerciseRowToLegacyShape(row) {
  const primary = row.primaryMuscle ? [row.primaryMuscle] : [];
  const category = row.category || 'strength';
  const trackingType = category === 'cardio' ? 'duration_distance' : 'weight_reps';
  return {
    id: row.id,
    exerciseId: row.id,
    name: row.name,
    primaryMuscles: primary,
    primaryMuscle: row.primaryMuscle || null,
    secondaryMuscles: [],
    equipment: row.equipment || 'Other',
    category,
    trackingType,
    isCustom: !!row.isCustom,
    aliases: [],
    isBodyweight: !!row.isBodyweight || String(row.equipment || '').toLowerCase() === 'bodyweight',
    movementPattern: row.movementPattern || null,
    difficulty: row.difficulty || null,
    apparatus: row.apparatus || null,
    equipmentDetail: row.equipmentDetail || null,
    sourceTags: [],
    notes: row.notes || '',
  };
}

export async function getExercisesSnapshot(filters = {}) {
  const rows = await buildExerciseQuery(filters).fetch();
  return rows.map(mapExerciseRowToLegacyShape);
}

export function searchExercisesObservable(query, filters = {}) {
  const normalized = normalizeExerciseNameKey(query || '');
  return buildExerciseQuery(filters)
    .observe()
    .pipe(
      map((rows) => {
        if (!normalized) return rows;
        return rows.filter((item) => item.normalizedName.includes(normalized));
      })
    );
}

export async function createCustomExercise(input) {
  const name = normalizeExerciseName(input?.name);
  requireNonEmpty(name, 'name');
  requireNonEmpty(input?.primaryMuscle, 'primary_muscle');
  requireNonEmpty(input?.equipment, 'equipment');
  requireNonEmpty(input?.category, 'category');
  const normalized = normalizeExerciseNameKey(name);

  const duplicate = await EXERCISES.query(Q.where('normalized_name', normalized)).fetch();
  if (duplicate.length > 0) {
    throw new Error('Exercise with this name already exists.');
  }

  const now = Date.now();
  let created = null;
  await database.write(async () => {
    created = await EXERCISES.create((row) => {
      row.name = name;
      row.normalizedName = normalized;
      row.primaryMuscle = input.primaryMuscle;
      row.equipment = input.equipment;
      row.category = input.category;
      row.isCustom = true;
      row.source = 'user_custom';
      row.notes = input.notes ? String(input.notes) : '';
      row._raw.created_at = now;
      row.updatedAt = now;
    });

    if (Array.isArray(input.muscles)) {
      const muscleCreates = input.muscles.map((m) => {
        validateContributionFraction(m.contribution_fraction);
        return EXERCISE_MUSCLES.prepareCreate((row) => {
          row.exercise.set(created);
          row.muscle = String(m.muscle || '').trim();
          row.role = String(m.role || 'secondary').trim();
          row.contributionFraction = Number(m.contribution_fraction);
          row._raw.created_at = now;
          row.updatedAt = now;
        });
      });
      if (muscleCreates.length > 0) await database.batch(...muscleCreates);
    }
  });

  return created;
}

export async function updateExercise(id, input) {
  requireNonEmpty(id, 'id');
  const exercise = await EXERCISES.find(id);
  const now = Date.now();
  await database.write(async () => {
    await exercise.update((row) => {
      if (input.name) {
        row.name = normalizeExerciseName(input.name);
        row.normalizedName = normalizeExerciseNameKey(input.name);
      }
      if (input.primaryMuscle) row.primaryMuscle = input.primaryMuscle;
      if (input.equipment) row.equipment = input.equipment;
      if (input.category) row.category = input.category;
      if (input.notes !== undefined) row.notes = input.notes || '';
      row.updatedAt = now;
    });
  });
  return exercise;
}

export async function deleteCustomExercise(id) {
  requireNonEmpty(id, 'id');
  const exercise = await EXERCISES.find(id);
  if (!exercise.isCustom) {
    throw new Error('Only custom exercises can be deleted.');
  }
  const muscles = await EXERCISE_MUSCLES.query(Q.where('exercise_id', id)).fetch();
  await database.write(async () => {
    for (const muscle of muscles) {
      await muscle.markAsDeleted();
      await muscle.destroyPermanently();
    }
    await exercise.markAsDeleted();
    await exercise.destroyPermanently();
  });
}

export async function getExerciseById(id) {
  requireNonEmpty(id, 'id');
  return EXERCISES.find(id);
}

export async function getExerciseMuscles(exerciseId) {
  requireNonEmpty(exerciseId, 'exerciseId');
  return EXERCISE_MUSCLES.query(Q.where('exercise_id', exerciseId)).fetch();
}
