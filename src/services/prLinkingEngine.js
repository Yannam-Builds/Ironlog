import prIndex from '../data/ironlog_pr_linking_index_resolved.json';

const exercises = (prIndex && prIndex.exercises) || [];

const byExactName = new Map();
const byExerciseKey = new Map();

for (const ex of exercises) {
  byExactName.set(normalizeName(ex.exerciseName), ex);
  byExerciseKey.set(ex.exerciseKey, ex);
}

export function normalizeName(input) {
  return String(input || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/['\u2019]/g, '')
    .replace(/\bpush[-\s]?ups?\b/g, 'push up')
    .replace(/\bpull[-\s]?ups?\b/g, 'pull up')
    .replace(/\bchin[-\s]?ups?\b/g, 'chin up')
    .replace(/\bsit[-\s]?ups?\b/g, 'sit up')
    .replace(/\bflyes?\b/g, 'fly')
    .replace(/\bcurls\b/g, 'curl')
    .replace(/\brows\b/g, 'row')
    .replace(/\braises\b/g, 'raise')
    .replace(/\bpulldowns\b/g, 'pulldown')
    .replace(/\bextensions\b/g, 'extension')
    .replace(/\bcrunches\b/g, 'crunch')
    .replace(/\bez[-\s]?bar\b/g, 'ezbar')
    .replace(/\bt[-\s]?bar\b/g, 'tbar')
    .replace(/\bv[-\s]?bar\b/g, 'vbar')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

export function slug(input) {
  return normalizeName(input).replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'unknown';
}

function tokenSet(input) {
  const stop = new Set([
    'barbell', 'dumbbell', 'cable', 'machine', 'kettlebell', 'band', 'bodyweight',
    'with', 'without', 'the', 'a', 'an', 'on', 'to', 'from', 'of', 'and', 'for',
  ]);
  return new Set(normalizeName(input).split(' ').filter((token) => token && !stop.has(token)));
}

function jaccard(a, b) {
  if (!a.size || !b.size) return 0;
  let intersection = 0;
  for (const item of a) if (b.has(item)) intersection += 1;
  const union = new Set([...Array.from(a), ...Array.from(b)]).size;
  return intersection / union;
}

function hardBlocker(a, b) {
  const blockerGroups = [
    ['incline', 'decline', 'flat'],
    ['close', 'wide', 'neutral', 'reverse', 'underhand', 'overhand'],
    ['smith', 'machine', 'barbell', 'dumbbell', 'cable', 'kettlebell', 'band'],
    ['single', 'one', 'two', 'alternating'],
    ['band', 'chain', 'deficit', 'rack', 'block', 'pin', 'board'],
    ['weighted', 'bodyweight'],
  ];
  const ta = tokenSet(a);
  const tb = tokenSet(b);
  for (const group of blockerGroups) {
    const ga = group.filter((x) => ta.has(x));
    const gb = group.filter((x) => tb.has(x));
    if (ga.length && gb.length && ga.join(',') !== gb.join(',')) return true;
  }
  return false;
}

export function inferEquipmentKey(name, metadata = {}) {
  const n = normalizeName(name);
  const raw = normalizeName(metadata?.equipment || '');

  if (/\bsmith\b/.test(n)) return 'machine_smith';
  if (/\blandmine\b/.test(n)) return 'barbell_landmine';
  if (/\bsuspended\b|\btrx\b/.test(n)) return 'bodyweight_suspension';
  if (/\bmedicine ball\b/.test(n)) return 'other_medicine_ball';
  if (/\bsled\b|\bprowler\b/.test(n)) return 'other_sled';
  if (/\bsandbag\b/.test(n)) return 'other_sandbag';

  if (raw === 'barbell') return 'barbell_straight';
  if (raw === 'dumbbell') return 'dumbbell';
  if (raw === 'cable') return 'cable';
  if (raw === 'machine') return 'machine';
  if (raw === 'kettlebell') return 'kettlebell';
  if (raw === 'band') return 'band';
  if (raw === 'bodyweight') return 'bodyweight';

  if (/\bdumbbell\b|\bdb\b/.test(n)) return 'dumbbell';
  if (/\bbarbell\b|\bbb\b/.test(n)) return 'barbell_straight';
  if (/\bcable\b|\bpulley\b/.test(n)) return 'cable';
  if (/\bmachine\b/.test(n)) return 'machine';
  if (/\bkettlebell\b|\bkb\b/.test(n)) return 'kettlebell';
  if (/\bband\b/.test(n)) return 'band';
  if (/\bpush up\b|\bpull up\b|\bchin up\b|\bdip\b|\bplank\b/.test(n)) return 'bodyweight';

  return raw || 'other_unspecified';
}

export function getKnownExerciseIdentity(exerciseName) {
  const found = byExactName.get(normalizeName(exerciseName)) || byExerciseKey.get(slug(exerciseName));
  if (!found) return null;
  return { ...found, source: 'known-library' };
}

export function inferExerciseIdentity(exerciseName, metadata = {}) {
  const exact = getKnownExerciseIdentity(exerciseName);
  if (exact) return exact;

  const equipmentKey = inferEquipmentKey(exerciseName, metadata);
  const inputTokens = tokenSet(exerciseName);
  let best = null;
  let bestScore = 0;

  for (const candidate of exercises) {
    if (!candidate.prEligible) continue;
    if (candidate.equipmentKey !== equipmentKey) continue;
    if (hardBlocker(exerciseName, candidate.exerciseName)) continue;

    const scoreBase = jaccard(inputTokens, tokenSet(candidate.exerciseName));
    let score = scoreBase;
    if (metadata?.primaryMuscle && normalizeName(metadata.primaryMuscle) === normalizeName(candidate.primaryMuscle)) {
      score += 0.12;
    }
    if (normalizeName(exerciseName).includes(String(candidate.movementRoot || '').replace(/_/g, ' '))) {
      score += 0.08;
    }
    if (score > bestScore) {
      bestScore = score;
      best = candidate;
    }
  }

  if (best && bestScore >= 0.72 && best.autoShareEnabled) {
    return {
      ...best,
      exerciseName,
      exerciseKey: slug(exerciseName),
      source: 'inferred-user-exercise',
      matchScore: bestScore,
      suggestedMatchName: best.exerciseName,
    };
  }

  const exactPrGroupId = `exact_${slug(exerciseName)}`;
  return {
    exerciseName,
    exerciseKey: slug(exerciseName),
    exactPrGroupId,
    autoPrGroupId: exactPrGroupId,
    smartPrGroupId: exactPrGroupId,
    candidatePrGroupId: null,
    movementFamilyId: best && bestScore >= 0.62 ? best.movementFamilyId : null,
    equipmentKey,
    movementRoot: best && bestScore >= 0.62 ? best.movementRoot : 'unknown',
    muscleKey: best && bestScore >= 0.62 ? best.muscleKey : slug(metadata?.primaryMuscle || 'unknown'),
    linkConfidence: 'exact',
    prEligible: metadata?.category ? ['strength', 'olympic'].includes(normalizeName(metadata.category)) : true,
    autoShareEnabled: false,
    reviewRecommended: false,
    source: 'inferred-user-exercise',
    matchScore: bestScore || undefined,
    suggestedMatchName: best?.exerciseName,
  };
}

export function getPrGroupId(exerciseName, metadata = {}, mode = 'safe') {
  const identity = inferExerciseIdentity(exerciseName, metadata);
  if (!identity.prEligible) return null;
  if (mode === 'family') return identity.movementFamilyId || identity.autoPrGroupId || identity.exactPrGroupId;
  return identity.autoPrGroupId || identity.exactPrGroupId;
}

export function shouldSharePr(aName, bName, aMeta = {}, bMeta = {}, mode = 'safe') {
  const a = inferExerciseIdentity(aName, aMeta);
  const b = inferExerciseIdentity(bName, bMeta);
  if (!a.prEligible || !b.prEligible) return false;
  if (a.equipmentKey !== b.equipmentKey) return false;
  const aGroup = getPrGroupId(aName, aMeta, mode);
  const bGroup = getPrGroupId(bName, bMeta, mode);
  return Boolean(aGroup && bGroup && aGroup === bGroup);
}

export function getRelatedExerciseNames(exerciseName, limit = 20) {
  const identity = inferExerciseIdentity(exerciseName);
  if (!identity.movementFamilyId) return [];
  const related = exercises
    .filter((entry) => entry.movementFamilyId === identity.movementFamilyId && entry.exerciseName !== exerciseName)
    .map((entry) => entry.exerciseName);
  return Array.from(new Set(related)).slice(0, limit);
}

export function attachPrIdentityToSet(set, mode = 'safe') {
  const identity = inferExerciseIdentity(set.exerciseName, set.exercise || {});
  return {
    ...set,
    exercisePrGroupId: getPrGroupId(set.exerciseName, set.exercise || {}, mode),
    exercisePrIdentity: identity,
  };
}

export function getPrLinkingStats() {
  return {
    exerciseCount: exercises.length,
    reviewCandidateExerciseCount: exercises.filter((ex) => ex.reviewRecommended).length,
  };
}
