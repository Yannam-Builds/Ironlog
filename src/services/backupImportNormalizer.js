const WATERMELON_TYPE = 'ironlog_watermelon_export';

function ensureArray(value) {
  return Array.isArray(value) ? value : [];
}

function ensureObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

export function normalizeBackupImport(raw) {
  if (!raw || typeof raw !== 'object') {
    throw new Error('Backup file is empty or malformed.');
  }
  if (raw?.manifest && raw?.encryptedPayload) {
    throw new Error('This file is an encrypted backup container. Use encrypted restore flow.');
  }

  const isWatermelon =
    raw.type === WATERMELON_TYPE &&
    Number(raw.version) === 1 &&
    raw.data &&
    typeof raw.data === 'object';

  if (!isWatermelon) {
      throw new Error(
        'Unsupported backup format. This version of Ironlog restores its own exports (ironlog_watermelon_export v1).'
      );
  }

  const data = ensureObject(raw.data);
  return {
    format: 'watermelon_export_v1',
    payload: raw,
    stats: {
      plans: ensureArray(data.plans).length,
      history: ensureArray(data.workouts).length,
      bodyWeight: ensureArray(data.body_measurements).length,
      bodyMeasurements: ensureArray(data.body_measurements).length,
      customExercises: ensureArray(data.exercises).filter((item) => item?.is_custom).length,
    },
    unsupportedFields: [],
  };
}
