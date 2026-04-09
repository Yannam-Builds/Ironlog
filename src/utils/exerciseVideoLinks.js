import { EXERCISE_YOUTUBE_BY_NORMALIZED_NAME } from '../data/exerciseYoutubeLinks';

export function normalizeExerciseNameKey(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '');
}

function toSearchTerm(value) {
  return String(value || '')
    .trim()
    .replace(/\s+/g, ' ');
}

function buildYoutubeSearchUrl(query) {
  const encoded = encodeURIComponent(query);
  return `https://www.youtube.com/results?search_query=${encoded}`;
}

export function getBundledYoutubeMetaByName(name) {
  const key = normalizeExerciseNameKey(name);
  if (!key) return null;
  return EXERCISE_YOUTUBE_BY_NORMALIZED_NAME[key] || null;
}

export function getYoutubeMetaForExercise(exercise) {
  if (!exercise) return null;

  const bundled = getBundledYoutubeMetaByName(exercise.name);
  if (bundled) {
    return {
      youtubeLink: bundled.youtubeLink,
      youtubeShortsLink: bundled.youtubeShortsLink,
      youtubeSearchQuery: bundled.youtubeSearchQuery,
      hasBundledYoutubeLink: true,
    };
  }

  if (exercise.youtubeLink || exercise.youtubeShortsLink || exercise.youtubeSearchQuery) {
    return {
      youtubeLink: exercise.youtubeLink || null,
      youtubeShortsLink: exercise.youtubeShortsLink || null,
      youtubeSearchQuery: exercise.youtubeSearchQuery || null,
      hasBundledYoutubeLink: false,
    };
  }

  return null;
}

export function buildExerciseYoutubeFallback(exercise) {
  const name = toSearchTerm(exercise?.name || 'Exercise');
  const primary = toSearchTerm(exercise?.primaryMuscle || (exercise?.primaryMuscles || [])[0] || 'exercise');
  const query = `${name} ${primary} exercise tutorial`;
  return {
    youtubeLink: buildYoutubeSearchUrl(query),
    youtubeShortsLink: buildYoutubeSearchUrl(`${query} shorts`),
    youtubeSearchQuery: query,
    hasBundledYoutubeLink: false,
  };
}

export function resolveExerciseYoutubeMeta(exercise) {
  return getYoutubeMetaForExercise(exercise) || buildExerciseYoutubeFallback(exercise);
}
