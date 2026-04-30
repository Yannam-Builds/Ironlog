import AsyncStorage from '@react-native-async-storage/async-storage';

const FAVORITE_EXERCISES_KEY = '@ironlog/favoriteExerciseIds';

function normalizeIds(ids = []) {
  return [...new Set((ids || []).map((id) => String(id || '').trim()).filter(Boolean))];
}

export async function getFavoriteExerciseIds() {
  try {
    const raw = await AsyncStorage.getItem(FAVORITE_EXERCISES_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return normalizeIds(Array.isArray(parsed) ? parsed : []);
  } catch {
    return [];
  }
}

export async function setFavoriteExerciseIds(ids = []) {
  const normalized = normalizeIds(ids);
  await AsyncStorage.setItem(FAVORITE_EXERCISES_KEY, JSON.stringify(normalized));
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

