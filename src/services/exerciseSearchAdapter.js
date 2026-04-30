import MiniSearch from 'minisearch';

function fuzzyForTerm(term) {
  if (term.length >= 6) return 0.2;
  if (term.length >= 4) return 0.1;
  return false;
}

export function buildExerciseSearchIndex(exercises = []) {
  const index = new MiniSearch({
    idField: 'id',
    fields: ['name', 'aliases', 'primaryMuscles', 'secondaryMuscles', 'equipment', 'category', 'movementPattern', 'difficulty'],
    storeFields: ['id'],
  });
  index.addAll((exercises || []).map((exercise) => ({
    id: exercise.id,
    name: String(exercise.name || ''),
    aliases: Array.isArray(exercise.aliases) ? exercise.aliases.join(' ') : '',
    primaryMuscles: Array.isArray(exercise.primaryMuscles)
      ? exercise.primaryMuscles.join(' ')
      : (exercise.primaryMuscle || ''),
    secondaryMuscles: Array.isArray(exercise.secondaryMuscles) ? exercise.secondaryMuscles.join(' ') : '',
    equipment: String(exercise.equipment || ''),
    category: String(exercise.category || ''),
    movementPattern: String(exercise.movementPattern || ''),
    difficulty: String(exercise.difficulty || ''),
  })));
  return index;
}

export function queryExerciseSearch({
  query,
  index,
  exercises = [],
}) {
  const normalizedQuery = String(query || '').trim();
  if (!normalizedQuery) return null;
  const terms = normalizedQuery.split(/\s+/).filter(Boolean);
  const searchOpts = {
    prefix: true,
    fuzzy: fuzzyForTerm,
    maxFuzzy: 2,
    boost: { name: 4, aliases: 3, primaryMuscles: 1.5, secondaryMuscles: 1.0, equipment: 1.2, category: 0.8, movementPattern: 0.8, difficulty: 0.6 },
    combineWith: 'AND',
  };
  let hits = index.search(normalizedQuery, searchOpts);
  if (hits.length === 0 && terms.length > 1) {
    hits = index.search(normalizedQuery, { ...searchOpts, combineWith: 'OR' });
  }
  const rankMap = new Map();
  hits.forEach((hit, indexOrder) => {
    // Smaller rank value = better result.
    rankMap.set(hit.id, indexOrder);
  });
  const result = new Set(hits.map((hit) => hit.id));
  if (result.size === 0) {
    const lowered = normalizedQuery.toLowerCase();
    exercises.forEach((exercise) => {
      const aliasText = Array.isArray(exercise.aliases) ? exercise.aliases.join(' ').toLowerCase() : '';
      if (String(exercise.name || '').toLowerCase().includes(lowered) || aliasText.includes(lowered)) {
        result.add(exercise.id);
        if (!rankMap.has(exercise.id)) {
          rankMap.set(exercise.id, rankMap.size + 1000);
        }
      }
    });
  }
  // Backward-compatible Set return + shared rank metadata for sort consistency.
  result.rankMap = rankMap;
  return result;
}
