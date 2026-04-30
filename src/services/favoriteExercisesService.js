import { getSetting, setSetting } from '../db/repositories/settingsRepository';

const FAVORITE_EXERCISES_KEY = '@ironlog/favoriteExerciseIds';

function normalizeIds(ids = []) {
  return [...new Set((ids || []).map((id) => String(id || '').trim()).filter(Boolean))];
}

export async function getFavoriteExerciseIds() {
  try {
    const parsed = await getSetting(FAVORITE_EXERCISES_KEY);
    return normalizeIds(Array.isArray(parsed) ? parsed : []);
  } catch {
    return [];
  }
}

export async function setFavoriteExerciseIds(ids = []) {
  const normalized = normalizeIds(ids);
  await setSetting(FAVORITE_EXERCISES_KEY, normalized, 'json');
  return normalized;
}

export async function toggleFavoriteExerciseId(exerciseId) {
  const id = String(exerciseId || '').trim();
  if (!id) return [];
  const current = await getFavoriteExerciseIds();
  const next = current.includes(id)
    ? current.filter((value) => value !== id)
    : [...current, id];
  return setFavoriteExerciseIds(next);
}
