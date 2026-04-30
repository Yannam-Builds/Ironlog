export function requireNonEmpty(value, fieldName) {
  if (!String(value || '').trim()) {
    throw new Error(`${fieldName} is required.`);
  }
}

export function requireNumberMin(value, min, fieldName) {
  const n = Number(value);
  if (!Number.isFinite(n) || n < min) {
    throw new Error(`${fieldName} must be >= ${min}.`);
  }
}

export function normalizeExerciseName(value) {
  return String(value || '').trim();
}

export function normalizeExerciseNameKey(value) {
  return normalizeExerciseName(value).toLowerCase();
}

export function validateContributionFraction(value) {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0 || n > 1) {
    throw new Error('contribution_fraction must be between 0 and 1.');
  }
}

