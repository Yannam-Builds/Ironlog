function toCountLabel(value, singular) {
  const count = Number.isFinite(Number(value)) ? Number(value) : 0;
  return `${count} ${singular}${count === 1 ? '' : 's'}`;
}

export function formatRestoreCounts(counts = {}) {
  const normalized = {
    plans: Number(counts.plans || 0),
    history: Number(counts.history || 0),
    bodyWeight: Number(counts.bodyWeight || 0),
    bodyMeasurements: Number(counts.bodyMeasurements || 0),
    customExercises: Number(counts.customExercises || 0),
  };
  return normalized;
}

export function buildRestoreSummary({
  sourceLabel = 'restore source',
  counts = {},
  unsupportedFields = [],
  partialNotes = [],
} = {}) {
  const c = formatRestoreCounts(counts);
  const primaryLine = [
    toCountLabel(c.plans, 'plan'),
    toCountLabel(c.history, 'workout'),
    toCountLabel(c.bodyWeight, 'bodyweight entry'),
  ].join(', ');

  const extraLine = [
    toCountLabel(c.bodyMeasurements, 'measurement'),
    toCountLabel(c.customExercises, 'custom exercise'),
  ].join(', ');

  const notes = [];
  if (Array.isArray(unsupportedFields) && unsupportedFields.length > 0) {
    notes.push(`Skipped unsupported fields: ${unsupportedFields.join(', ')}`);
  }
  if (Array.isArray(partialNotes) && partialNotes.length > 0) {
    notes.push(...partialNotes.filter(Boolean));
  }

  return {
    title: 'Restore complete',
    message: `${sourceLabel} restored.\n\n${primaryLine}\n${extraLine}${notes.length ? `\n\n${notes.join('\n')}` : ''}`,
    counts: c,
    unsupportedFields: Array.isArray(unsupportedFields) ? unsupportedFields : [],
    partialNotes: Array.isArray(partialNotes) ? partialNotes : [],
  };
}

