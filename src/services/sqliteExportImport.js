import * as FileSystem from '../platform/filesystem';
import * as Sharing from '../platform/sharing';
import * as DocumentPicker from '../platform/documentPicker';
import { APP_VERSION } from '../platform/appInfo';
import {
  exportDatabase,
  importAnyPayload,
  detectImportFormat,
  normalizeLegacyPayload,
} from '../db/repositories/importExportRepository';

export const SQLITE_EXPORT_SCHEMA = 'IRONLOG_SQLITE_EXPORT_V1';

function toLegacyPayload(wmExport) {
  const data = wmExport?.data || {};
  return {
    plans: Array.isArray(data.plans) ? data.plans : [],
    history: Array.isArray(data.workouts) ? data.workouts : [],
    bodyWeight: [],
    bodyMeasurements: Array.isArray(data.body_measurements) ? data.body_measurements : [],
    customExercises: Array.isArray(data.exercises)
      ? data.exercises.filter((ex) => !!ex.is_custom)
      : [],
  };
}

function parseAppSettingsRows(rows = []) {
  const out = {};
  rows.forEach((row) => {
    const key = row?.key;
    if (!key) return;
    const t = row?.value_type;
    const raw = row?.value;
    if (t === 'boolean') out[key] = raw === 'true';
    else if (t === 'number') out[key] = Number(raw);
    else if (t === 'json') {
      try {
        out[key] = JSON.parse(raw);
      } catch (_) {
        out[key] = raw;
      }
    } else out[key] = raw;
  });
  return out;
}

function countRows(payload = {}) {
  return {
    plans: Array.isArray(payload.plans) ? payload.plans.length : 0,
    history: Array.isArray(payload.history) ? payload.history.length : 0,
    bodyWeight: Array.isArray(payload.bodyWeight) ? payload.bodyWeight.length : 0,
    bodyMeasurements: Array.isArray(payload.bodyMeasurements) ? payload.bodyMeasurements.length : 0,
    customExercises: Array.isArray(payload.customExercises) ? payload.customExercises.length : 0,
  };
}

export async function buildSQLiteExportBundle() {
  const wmExport = await exportDatabase();
  const payload = toLegacyPayload(wmExport);
  const appState = parseAppSettingsRows(wmExport?.data?.app_settings || []);
  return {
    schema: SQLITE_EXPORT_SCHEMA,
    exportedAt: new Date().toISOString(),
    appVersion: APP_VERSION,
    payload,
    appState,
    counts: countRows(payload),
  };
}

export async function exportSQLiteBundleAndShare() {
  const bundle = await buildSQLiteExportBundle();
  const filePath = `${FileSystem.cacheDirectory}ironlog_sqlite_export_${Date.now()}.json`;
  await FileSystem.writeAsStringAsync(filePath, JSON.stringify(bundle, null, 2), { encoding: 'utf8' });
  const canShare = await Sharing.isAvailableAsync();
  if (!canShare) throw new Error('Sharing is unavailable on this device.');
  await Sharing.shareAsync(filePath, {
    mimeType: 'application/json',
    dialogTitle: 'Export Ironlog SQLite Data',
  });
  return bundle;
}

export async function pickSQLiteBundleFile() {
  const picked = await DocumentPicker.getDocumentAsync({
    type: ['application/json', 'text/plain', '*/*'],
    copyToCacheDirectory: true,
  });
  if (picked.canceled || !picked.assets?.[0]) return null;
  const raw = await FileSystem.readAsStringAsync(picked.assets[0].uri, { encoding: 'utf8' });
  return JSON.parse(raw);
}

export function validateSQLiteBundle(bundle) {
  if (!bundle || typeof bundle !== 'object') {
    return { valid: false, reason: 'Bundle is empty or malformed.' };
  }
  if (bundle.schema !== SQLITE_EXPORT_SCHEMA) {
    return { valid: false, reason: 'Unsupported export schema.' };
  }
  if (!bundle.payload || typeof bundle.payload !== 'object') {
    return { valid: false, reason: 'Missing export payload.' };
  }
  if (bundle.appState != null && typeof bundle.appState !== 'object') {
    return { valid: false, reason: 'Invalid app state block.' };
  }
  return {
    valid: true,
    counts: bundle.counts || countRows(bundle.payload),
    appStateKeys: bundle.appState ? Object.keys(bundle.appState).length : 0,
    exportedAt: bundle.exportedAt || null,
    appVersion: bundle.appVersion || null,
  };
}

export async function importSQLiteBundle(bundle) {
  const validation = validateSQLiteBundle(bundle);
  if (!validation.valid) throw new Error(validation.reason || 'Invalid SQLite export bundle.');

  // Use shared Watermelon import normalizer so legacy payloads never become runtime storage.
  const format = detectImportFormat(bundle);
  if (format === 'unknown') {
    const normalized = normalizeLegacyPayload(bundle);
    await importAnyPayload(normalized.payload);
  } else {
    await importAnyPayload(bundle);
  }

  return validation;
}
