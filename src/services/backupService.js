import * as FileSystem from '../platform/filesystem';
import * as Sharing from '../platform/sharing';
import * as DocumentPicker from '../platform/documentPicker';
import * as SecureStore from '../platform/secureStore';
import * as Crypto from '../platform/crypto';
import { APP_VERSION } from '../platform/appInfo';
import { exportDatabase, importAnyPayload } from '../db/repositories/importExportRepository';
import { getSetting, setSetting, removeSetting } from '../db/repositories/settingsRepository';
import {
  ACTIVE_WORKOUT_SESSION_PREFIX,
  BACKUP_CONFIG_KEY,
  BACKUP_DEVICE_ID_KEY,
  BACKUP_INDEX_KEY,
  BACKUP_KEY_MATERIAL_SECURE_KEY,
  BACKUP_LOCAL_DIR_NAME,
  BACKUP_MANAGED_KEYS,
  BACKUP_NOTIFICATION_SETTINGS_KEY,
  BACKUP_QUEUE_KEY,
  BACKUP_RESTOREABLE_DOMAINS,
  BACKUP_STATUS_KEY,
  CURRENT_BACKUP_FORMAT,
  CURRENT_BACKUP_SCHEMA_VERSION,
  DEFAULT_BACKUP_CONFIG,
  DEFAULT_BACKUP_STATUS,
  DEFAULT_NOTIFICATION_SETTINGS,
} from './backupConstants';
import {
  decryptJsonPayload,
  deriveBackupKey,
  encryptJsonPayload,
  fingerprintKey,
  fromBase64,
  randomBytes,
  sha256Hex,
  toBase64,
} from './backupCrypto';

// Drive sync is intentionally disabled in the current Ironlog runtime.
async function isDriveBackupAvailable() { return false; }
async function uploadSnapshotToDrive() { throw new Error('Drive backup is disabled.'); }
async function listDriveSnapshots() { return []; }
async function downloadDriveSnapshot() { throw new Error('Drive backup is disabled.'); }

const SNAPSHOT_EXTENSION = '.ironlog';
const BACKUP_AAD = 'IRONLOG_LOCAL_BACKUP';

function toLegacySnapshotShapeFromExport(exportPayload = {}) {
  const data = exportPayload?.data || {};
  const plans = Array.isArray(data.plans) ? data.plans : [];
  const history = Array.isArray(data.workouts) ? data.workouts : [];
  const bodyMeasurements = Array.isArray(data.body_measurements) ? data.body_measurements : [];
  const bodyWeight = bodyMeasurements
    .filter((row) => row?.bodyweight != null)
    .map((row) => ({ date: new Date(Number(row.measured_at) || Date.now()).toISOString(), weight: Number(row.bodyweight) || 0 }));
  const customExercises = (Array.isArray(data.exercises) ? data.exercises : []).filter((row) => !!row?.is_custom);
  return { plans, history, bodyWeight, bodyMeasurements, customExercises };
}

async function loadTrainingSnapshotCompat() {
  const exported = await exportDatabase();
  return toLegacySnapshotShapeFromExport(exported);
}

async function replaceTrainingSnapshotCompat(snapshot = {}) {
  await importAnyPayload({
    plans: Array.isArray(snapshot?.plans) ? snapshot.plans : [],
    history: Array.isArray(snapshot?.history) ? snapshot.history : [],
    bodyWeight: Array.isArray(snapshot?.bodyWeight) ? snapshot.bodyWeight : [],
    bodyMeasurements: Array.isArray(snapshot?.bodyMeasurements) ? snapshot.bodyMeasurements : [],
    customExercises: Array.isArray(snapshot?.customExercises) ? snapshot.customExercises : [],
  });
}

function parseStoredValue(raw) {
  if (raw == null) return null;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return raw;
  }
}

function stableStringify(value) {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (!value || typeof value !== 'object') return JSON.stringify(value);
  const keys = Object.keys(value).sort();
  return `{${keys.map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
}

function inferCountFromRaw(raw) {
  const parsed = parseStoredValue(raw);
  if (Array.isArray(parsed)) return parsed.length;
  if (parsed && typeof parsed === 'object') return Object.keys(parsed).length;
  return parsed == null ? 0 : 1;
}

function summarizeDomainItems(items) {
  return Object.values(items || {}).reduce((total, rawValue) => total + inferCountFromRaw(rawValue), 0);
}

function normalizeSnapshotRecord(record) {
  return {
    source: 'local',
    local: true,
    remote: false,
    ...record,
  };
}

async function readJsonStorage(key, fallback) {
  try {
    const value = await getSetting(key);
    if (value == null) return fallback;
    // getSetting returns parsed JSON for 'json' type, plain string otherwise
    if (typeof value === 'object') return value;
    if (typeof value === 'string') {
      try { return JSON.parse(value); } catch (_) { return value; }
    }
    return value;
  } catch (_) {
    return fallback;
  }
}

async function writeJsonStorage(key, value) {
  await setSetting(key, value, 'json');
  return value;
}

function getDocumentDirectory() {
  return FileSystem.documentDirectory || '';
}

function getBackupDirectory() {
  return `${getDocumentDirectory()}${BACKUP_LOCAL_DIR_NAME}/`;
}

async function ensureBackupDirectory() {
  const dir = getBackupDirectory();
  const info = await FileSystem.getInfoAsync(dir);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
  }
  return dir;
}

export async function prepareLocalBackupStorage() {
  return ensureBackupDirectory();
}

async function loadBackupIndex() {
  const index = await readJsonStorage(BACKUP_INDEX_KEY, []);
  return Array.isArray(index) ? index.map(normalizeSnapshotRecord) : [];
}

async function saveBackupIndex(index) {
  const normalized = (Array.isArray(index) ? index : [])
    .map((record) => ({
      ...record,
      source: record.source || (record.remote ? 'drive' : 'local'),
      local: record.local !== false,
      remote: !!record.remote,
    }))
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
  return writeJsonStorage(BACKUP_INDEX_KEY, normalized);
}

export async function loadBackupConfig() {
  return {
    ...DEFAULT_BACKUP_CONFIG,
    ...(await readJsonStorage(BACKUP_CONFIG_KEY, {})),
  };
}

export async function saveBackupConfig(nextConfig) {
  const merged = {
    ...DEFAULT_BACKUP_CONFIG,
    ...(await readJsonStorage(BACKUP_CONFIG_KEY, {})),
    ...(nextConfig || {}),
  };
  await writeJsonStorage(BACKUP_CONFIG_KEY, merged);
  return merged;
}

export async function loadBackupStatus() {
  return {
    ...DEFAULT_BACKUP_STATUS,
    ...(await readJsonStorage(BACKUP_STATUS_KEY, {})),
  };
}

export async function saveBackupStatus(nextStatus) {
  const merged = {
    ...DEFAULT_BACKUP_STATUS,
    ...(await readJsonStorage(BACKUP_STATUS_KEY, {})),
    ...(nextStatus || {}),
  };
  await writeJsonStorage(BACKUP_STATUS_KEY, merged);
  return merged;
}

export async function updateBackupStatus(patch) {
  return saveBackupStatus(patch);
}

export async function loadNotificationSettings() {
  return {
    ...DEFAULT_NOTIFICATION_SETTINGS,
    ...(await readJsonStorage(BACKUP_NOTIFICATION_SETTINGS_KEY, {})),
  };
}

export async function saveNotificationSettings(nextSettings) {
  const merged = {
    ...DEFAULT_NOTIFICATION_SETTINGS,
    ...(await readJsonStorage(BACKUP_NOTIFICATION_SETTINGS_KEY, {})),
    ...(nextSettings || {}),
  };
  await writeJsonStorage(BACKUP_NOTIFICATION_SETTINGS_KEY, merged);
  return merged;
}

export async function getOrCreateDeviceId() {
  const existing = await getSetting(BACKUP_DEVICE_ID_KEY);
  if (existing) return String(existing);
  const generated = Crypto.randomUUID();
  await setSetting(BACKUP_DEVICE_ID_KEY, generated, 'string');
  return generated;
}

export async function getManagedStorageMap() {
  // All training data lives in WatermelonDB — build map from WM export + app_settings table
  const map = {};
  try {
    const exported = await exportDatabase();
    const data = exported?.data || {};

    // Plans
    if (Array.isArray(data.plans) && data.plans.length > 0) {
      map['ironlog_plans'] = JSON.stringify(data.plans);
    }

    // History: workouts with nested exercises+sets (reconstructed for backup compatibility)
    if (Array.isArray(data.workouts) && data.workouts.length > 0) {
      const exercisesByWorkout = {};
      (data.workout_exercises || []).forEach((we) => {
        if (!exercisesByWorkout[we.workout_id]) exercisesByWorkout[we.workout_id] = [];
        exercisesByWorkout[we.workout_id].push(we);
      });
      const setsByExercise = {};
      (data.workout_sets || []).forEach((ws) => {
        if (!setsByExercise[ws.workout_exercise_id]) setsByExercise[ws.workout_exercise_id] = [];
        setsByExercise[ws.workout_exercise_id].push(ws);
      });
      const history = data.workouts.map((workout) => ({
        ...workout,
        exercises: (exercisesByWorkout[workout.id] || []).map((we) => ({
          ...we,
          sets: setsByExercise[we.id] || [],
        })),
      }));
      map['ironlog_history'] = JSON.stringify(history);
    }

    // Body measurements / body weight
    if (Array.isArray(data.body_measurements) && data.body_measurements.length > 0) {
      const bwRows = data.body_measurements.filter((r) => r.measurement_type === 'body_weight');
      const measRows = data.body_measurements.filter((r) => r.measurement_type !== 'body_weight');
      if (bwRows.length > 0) map['ironlog_bw'] = JSON.stringify(bwRows);
      if (measRows.length > 0) map['@ironlog/bodyMeasurements'] = JSON.stringify(measRows);
    }

    // Custom exercises
    if (Array.isArray(data.exercises)) {
      const custom = data.exercises.filter((e) => e.is_custom);
      if (custom.length > 0) map['@ironlog/customExercises'] = JSON.stringify(custom);
    }

    // app_settings → all @ironlog/* keys (active sessions, PRs, gym profiles, etc.)
    if (Array.isArray(data.app_settings)) {
      data.app_settings.forEach((row) => {
        if (row.key && row.value != null) {
          map[row.key] = typeof row.value === 'string' ? row.value : JSON.stringify(row.value);
        }
      });
    }
  } catch (_) {}
  return map;
}

function buildDomainsFromStorage(storageMap) {
  const domains = {};
  Object.entries(BACKUP_MANAGED_KEYS).forEach(([domainId, keys]) => {
    const items = {};
    keys.forEach((key) => {
      if (storageMap[key] != null) items[key] = storageMap[key];
    });
    if (Object.keys(items).length) domains[domainId] = { items };
  });
  const sessionKeys = Object.keys(storageMap).filter((key) => key.startsWith(ACTIVE_WORKOUT_SESSION_PREFIX));
  if (sessionKeys.length) {
    const items = {};
    sessionKeys.forEach((key) => {
      items[key] = storageMap[key];
    });
    domains.sessionRecovery = { items };
  }
  return domains;
}

function buildRecordCounts(domains) {
  const recordCounts = {};
  Object.entries(domains || {}).forEach(([domainId, domain]) => {
    recordCounts[domainId] = summarizeDomainItems(domain.items || {});
  });
  recordCounts.total = Object.values(recordCounts).reduce((sum, value) => sum + (Number(value) || 0), 0);
  return recordCounts;
}

export async function getCurrentBackupPreview() {
  const storageMap = await getManagedStorageMap();
  const domains = buildDomainsFromStorage(storageMap);
  const includedDomains = Object.keys(domains);
  const recordCounts = buildRecordCounts(domains);
  const payload = {
    schemaVersion: CURRENT_BACKUP_SCHEMA_VERSION,
    includedDomains,
    domains,
  };
  const dataHash = await sha256Hex(stableStringify(payload));
  return {
    includedDomains,
    recordCounts,
    dataHash,
    excludesMedia: true,
    photoBackupsIncluded: false,
  };
}

async function loadKeyMaterial() {
  const raw = await SecureStore.getItemAsync(BACKUP_KEY_MATERIAL_SECURE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return null;
  }
}

async function saveKeyMaterial(value) {
  await SecureStore.setItemAsync(BACKUP_KEY_MATERIAL_SECURE_KEY, JSON.stringify(value));
}

export async function isBackupPassphraseConfigured() {
  const config = await loadBackupConfig();
  return !!config.passphraseConfigured;
}

export async function configureBackupPassphrase(passphrase, overrides = {}) {
  if (!String(passphrase || '').trim()) {
    throw new Error('Passphrase cannot be empty.');
  }
  const saltBytes = await randomBytes(16);
  const keyBytes = await deriveBackupKey(passphrase, saltBytes);
  const keyFingerprint = await fingerprintKey(keyBytes);
  await saveKeyMaterial({
    version: 1,
    key: toBase64(keyBytes),
    salt: toBase64(saltBytes),
    keyFingerprint,
  });
  const config = await saveBackupConfig({
    ...overrides,
    enabled: true,
    passphraseConfigured: true,
    passphraseSalt: toBase64(saltBytes),
    keyFingerprint,
  });
  await saveBackupStatus({ enabled: true });
  return { config, keyFingerprint };
}

export async function verifyBackupPassphrase(passphrase) {
  const config = await loadBackupConfig();
  if (!config.passphraseConfigured || !config.passphraseSalt || !config.keyFingerprint) {
    return false;
  }
  const keyBytes = await deriveBackupKey(passphrase, fromBase64(config.passphraseSalt));
  const fingerprint = await fingerprintKey(keyBytes);
  return fingerprint === config.keyFingerprint;
}

export async function clearBackupPassphrase() {
  await SecureStore.deleteItemAsync(BACKUP_KEY_MATERIAL_SECURE_KEY);
  const config = await saveBackupConfig({
    enabled: false,
    passphraseConfigured: false,
    passphraseSalt: null,
    keyFingerprint: null,
  });
  await saveBackupStatus({ enabled: false, driveLinked: false });
  return config;
}

async function resolveEncryptionBundle(passphrase) {
  if (passphrase) {
    const config = await loadBackupConfig();
    const saltBytes = config.passphraseSalt ? fromBase64(config.passphraseSalt) : await randomBytes(16);
    const keyBytes = await deriveBackupKey(passphrase, saltBytes);
    const keyFingerprint = await fingerprintKey(keyBytes);
    return { keyBytes, saltBytes, keyFingerprint };
  }

  const material = await loadKeyMaterial();
  if (!material?.key || !material?.salt) {
    throw new Error('Backup passphrase is not configured yet.');
  }
  return {
    keyBytes: fromBase64(material.key),
    saltBytes: fromBase64(material.salt),
    keyFingerprint: material.keyFingerprint || null,
  };
}

function snapshotFileName(snapshotId) {
  return `${snapshotId}${SNAPSHOT_EXTENSION}`;
}

async function pruneLocalSnapshots(index, config) {
  const keepLocal = Number(config.localRetentionCount || 8);
  const keepRollbacks = Math.min(3, keepLocal);
  const normal = index.filter((record) => !record.isRollback && record.local !== false);
  const rollback = index.filter((record) => record.isRollback && record.local !== false);
  const removable = [
    ...normal.slice(keepLocal),
    ...rollback.slice(keepRollbacks),
  ];
  if (removable.length) {
    await Promise.all(removable.map(async (record) => {
      if (record.localUri) {
        try {
          await FileSystem.deleteAsync(record.localUri, { idempotent: true });
        } catch (_) {
          // Ignore failed cleanup.
        }
      }
    }));
  }
  const removableIds = new Set(removable.map((record) => record.snapshotId));
  return index.filter((record) => !removableIds.has(record.snapshotId));
}

async function persistSnapshotRecord(record, container) {
  const directory = await ensureBackupDirectory();
  const localUri = `${directory}${snapshotFileName(record.snapshotId)}`;
  await FileSystem.writeAsStringAsync(localUri, JSON.stringify(container), { encoding: 'utf8' });
  const nextRecord = normalizeSnapshotRecord({ ...record, localUri });
  const config = await loadBackupConfig();
  const currentIndex = await loadBackupIndex();
  const updatedIndex = [nextRecord, ...currentIndex.filter((entry) => entry.snapshotId !== nextRecord.snapshotId)];
  const pruned = await pruneLocalSnapshots(updatedIndex, config);
  await saveBackupIndex(pruned);
  await saveBackupStatus({
    lastBackupAt: nextRecord.createdAt,
    lastBackupResult: 'success',
    lastFailure: null,
    rollingVersionCount: pruned.filter((entry) => !entry.isRollback).length,
    lastSnapshotId: nextRecord.snapshotId,
    dirty: false,
    lastDataHash: nextRecord.dataHash,
    enabled: true,
  });
  return nextRecord;
}

export async function listLocalSnapshots(options = {}) {
  const includeRollbacks = options.includeRollbacks !== false;
  const index = await loadBackupIndex();
  return index.filter((record) => includeRollbacks || !record.isRollback);
}

export async function readSnapshotContainer(source) {
  const sourceRecord = typeof source === 'string' ? null : (source || null);
  let uri = typeof source === 'string' ? source : source?.localUri || source?.uri;

  // If a remote history item has no readable local file, fetch directly from Drive.
  if (sourceRecord?.remote && (!uri || !(await FileSystem.getInfoAsync(uri)).exists)) {
    const remoteId = sourceRecord.remoteFileId || sourceRecord.driveFileId;
    if (remoteId) {
      return downloadDriveSnapshot(remoteId);
    }
  }

  // Recover stale migrated backup paths by snapshotId in the current backup directory.
  if (sourceRecord?.snapshotId && uri) {
    const currentInfo = await FileSystem.getInfoAsync(uri).catch(() => ({ exists: false }));
    if (!currentInfo?.exists) {
      const recoveredUri = `${getBackupDirectory()}${snapshotFileName(sourceRecord.snapshotId)}`;
      const recoveredInfo = await FileSystem.getInfoAsync(recoveredUri).catch(() => ({ exists: false }));
      if (recoveredInfo?.exists) {
        uri = recoveredUri;
      }
    }
  }

  if (!uri) throw new Error('No backup file selected.');

  let raw;
  try {
    raw = await FileSystem.readAsStringAsync(uri, { encoding: 'utf8' });
  } catch (error) {
    const details = String(error?.message || error || '');
    throw new Error(`Could not read backup file. ${details.includes('ENOENT') ? 'Snapshot file is missing on this device.' : details}`);
  }
  const parsed = JSON.parse(raw);
  if (parsed?.format !== CURRENT_BACKUP_FORMAT || !parsed?.manifest || !parsed?.ciphertext) {
    throw new Error('Invalid IronLog encrypted backup file.');
  }
  return parsed;
}

async function createPayloadFromStorage({ reason, isRollback = false, restoreSourceSnapshotId = null }) {
  const storageMap = await getManagedStorageMap();
  const domains = buildDomainsFromStorage(storageMap);
  const includedDomains = Object.keys(domains);
  const recordCounts = buildRecordCounts(domains);
  const createdAt = new Date().toISOString();
  const deviceId = await getOrCreateDeviceId();
  const appVersion = APP_VERSION;
  const payload = {
    format: CURRENT_BACKUP_FORMAT,
    schemaVersion: CURRENT_BACKUP_SCHEMA_VERSION,
    createdAt,
    appVersion,
    deviceId,
    reason,
    isRollback,
    restoreSourceSnapshotId,
    includedDomains,
    recordCounts,
    domains,
  };
  const dataHash = await sha256Hex(stableStringify(payload.domains));
  return { payload, recordCounts, includedDomains, createdAt, deviceId, appVersion, dataHash };
}

async function buildSnapshotContainer({ reason, isRollback = false, restoreSourceSnapshotId = null, passphrase = null }) {
  const { payload, recordCounts, includedDomains, createdAt, deviceId, appVersion, dataHash } = await createPayloadFromStorage({
    reason,
    isRollback,
    restoreSourceSnapshotId,
  });
  const { keyBytes, saltBytes } = await resolveEncryptionBundle(passphrase);
  const encrypted = await encryptJsonPayload(payload, {
    keyBytes,
    saltBytes,
    aad: BACKUP_AAD,
  });
  const snapshotId = `snapshot_${createdAt.replace(/[:.]/g, '-')}_${Crypto.randomUUID()}`;
  const manifest = {
    snapshotId,
    createdAt,
    schemaVersion: CURRENT_BACKUP_SCHEMA_VERSION,
    appVersion,
    deviceId,
    payloadChecksum: encrypted.payloadChecksum,
    cipherAlgorithm: 'AES-256-GCM',
    salt: encrypted.salt,
    nonce: encrypted.nonce,
    recordCounts,
    includedDomains,
    driveFileId: null,
    isRollback,
    restoreSourceSnapshotId,
    dataHash,
    reason,
    byteLength: encrypted.byteLength,
  };
  return {
    record: normalizeSnapshotRecord({
      ...manifest,
      source: 'local',
      remote: false,
      local: true,
    }),
    container: {
      format: CURRENT_BACKUP_FORMAT,
      manifest,
      ciphertext: encrypted.ciphertext,
    },
  };
}

export async function createRollbackSnapshot(options = {}) {
  const { record, container } = await buildSnapshotContainer({
    reason: 'rollback_before_restore',
    isRollback: true,
    restoreSourceSnapshotId: options.restoreSourceSnapshotId || null,
    passphrase: options.passphrase || null,
  });
  return persistSnapshotRecord(record, container);
}

export async function queueBackup(reason) {
  const queued = {
    reason,
    queuedAt: new Date().toISOString(),
  };
  await writeJsonStorage(BACKUP_QUEUE_KEY, queued);
  await saveBackupStatus({ queuedReason: reason });
  return queued;
}

export async function clearQueuedBackup() {
  await removeSetting(BACKUP_QUEUE_KEY);
  await saveBackupStatus({ queuedReason: null });
}

export async function getQueuedBackup() {
  return readJsonStorage(BACKUP_QUEUE_KEY, null);
}

export async function markBackupDirty(reason = 'data_changed') {
  const preview = await getCurrentBackupPreview();
  await saveBackupStatus({
    dirty: true,
    queuedReason: reason,
    lastFailure: null,
    enabled: (await loadBackupConfig()).enabled,
    lastDataHash: preview.dataHash,
  });
  return preview;
}

export async function setBackupDirtyFlag(reason = 'data_changed') {
  return saveBackupStatus({
    dirty: true,
    queuedReason: reason,
    enabled: (await loadBackupConfig()).enabled,
  });
}

export async function validateBackupContainer(container, passphrase) {
  const decrypted = await decryptJsonPayload({
    salt: container.manifest.salt,
    nonce: container.manifest.nonce,
    ciphertext: container.ciphertext,
  }, {
    passphrase,
    aad: BACKUP_AAD,
  });
  return {
    valid: decrypted.payloadChecksum === container.manifest.payloadChecksum,
    payload: decrypted.payload,
    payloadChecksum: decrypted.payloadChecksum,
  };
}

export async function buildRestorePreview(source, passphrase = null) {
  const container = typeof source?.manifest === 'object' ? source : await readSnapshotContainer(source);
  const preview = {
    snapshotId: container.manifest.snapshotId,
    createdAt: container.manifest.createdAt,
    schemaVersion: container.manifest.schemaVersion,
    appVersion: container.manifest.appVersion,
    checksumValid: null,
    recordCounts: container.manifest.recordCounts || {},
    warnings: [],
    duplicateRisk: 'low',
    conflictSummary: null,
    canRestore: false,
    includedDomains: container.manifest.includedDomains || [],
    deviceId: container.manifest.deviceId || null,
    isRollback: !!container.manifest.isRollback,
  };

  const status = await loadBackupStatus();
  if (status.lastBackupAt && String(status.lastBackupAt) > String(container.manifest.createdAt || '')) {
    preview.warnings.push('This backup is older than your latest local snapshot.');
    preview.duplicateRisk = 'medium';
  }

  const currentDeviceId = await getOrCreateDeviceId();
  preview.conflictSummary = currentDeviceId === container.manifest.deviceId
    ? 'Created on this device'
    : 'Created on another device';

  if (passphrase) {
    const validated = await validateBackupContainer(container, passphrase);
    preview.checksumValid = validated.valid;
    preview.canRestore = validated.valid;
    if (!validated.valid) {
      preview.warnings.push('Checksum validation failed. Do not restore this snapshot.');
    }
  }

  return preview;
}

function keysForDomains(domains, currentStorageMap) {
  const keys = new Set();
  domains.forEach((domainId) => {
    (BACKUP_MANAGED_KEYS[domainId] || []).forEach((key) => keys.add(key));
    if (domainId === 'sessionRecovery') {
      Object.keys(currentStorageMap || {}).forEach((key) => {
        if (key.startsWith(ACTIVE_WORKOUT_SESSION_PREFIX)) keys.add(key);
      });
    }
  });
  return [...keys];
}

function parseMaybeJson(raw, fallback = null) {
  if (raw == null) return fallback;
  if (typeof raw !== 'string') return raw;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return fallback;
  }
}

function safeArrayLength(value) {
  return Array.isArray(value) ? value.length : 0;
}

function buildExpectedCoreCounts(restoredItems = {}, payload = {}, selectedDomains = []) {
  const selected = new Set(selectedDomains || []);
  const plans = parseMaybeJson(restoredItems.ironlog_plans, payload?.plans ?? null);
  const history = parseMaybeJson(restoredItems.ironlog_history, payload?.history ?? null);
  const bodyWeight = parseMaybeJson(restoredItems.ironlog_bw, payload?.bodyWeight ?? null);
  const bodyMeasurements = parseMaybeJson(
    restoredItems['@ironlog/bodyMeasurements'],
    payload?.bodyMeasurements ?? null
  );
  const customExercises = parseMaybeJson(
    restoredItems['@ironlog/customExercises'],
    payload?.customExercises ?? null
  );

  return {
    plans: selected.has('plans') ? safeArrayLength(plans) : null,
    history: selected.has('history') ? safeArrayLength(history) : null,
    bodyWeight: selected.has('metrics') ? safeArrayLength(bodyWeight) : null,
    bodyMeasurements: selected.has('metrics') ? safeArrayLength(bodyMeasurements) : null,
    customExercises: selected.has('customExercises') ? safeArrayLength(customExercises) : null,
  };
}

async function verifyRestoredCoreCounts(expectedCounts = {}, selectedDomains = []) {
  const selected = new Set(selectedDomains || []);
  const currentSnapshot = await loadTrainingSnapshotCompat();
  const actualCounts = {
    plans: safeArrayLength(currentSnapshot?.plans),
    history: safeArrayLength(currentSnapshot?.history),
    bodyWeight: safeArrayLength(currentSnapshot?.bodyWeight),
    bodyMeasurements: safeArrayLength(currentSnapshot?.bodyMeasurements),
    customExercises: safeArrayLength(currentSnapshot?.customExercises),
  };

  const mismatches = [];
  const compareCount = (label, expected, actual) => {
    if (expected == null) return;
    if (expected !== actual) {
      mismatches.push(`${label}: expected ${expected}, actual ${actual}`);
    }
  };

  if (selected.has('plans')) compareCount('plans', expectedCounts.plans, actualCounts.plans);
  if (selected.has('history')) compareCount('history', expectedCounts.history, actualCounts.history);
  if (selected.has('metrics')) {
    compareCount('bodyWeight', expectedCounts.bodyWeight, actualCounts.bodyWeight);
    compareCount('bodyMeasurements', expectedCounts.bodyMeasurements, actualCounts.bodyMeasurements);
  }
  if (selected.has('customExercises')) {
    compareCount('customExercises', expectedCounts.customExercises, actualCounts.customExercises);
  }

  return { actualCounts, mismatches };
}

function collectRestoredStorageItems(payloadDomains = {}, selectedDomains = []) {
  const items = {};
  selectedDomains.forEach((domainId) => {
    const domainItems = payloadDomains?.[domainId]?.items || {};
    Object.entries(domainItems).forEach(([key, value]) => {
      if (value != null) items[key] = value;
    });
  });
  return items;
}

function inferDomainFromStorageKey(storageKey) {
  if (!storageKey) return null;
  const match = Object.entries(BACKUP_MANAGED_KEYS).find(([, keys]) => keys.includes(storageKey));
  return match ? match[0] : null;
}

function mergeDomainItem(domains, domainId, key, value) {
  if (!domainId || !key || value == null) return;
  if (!domains[domainId]) domains[domainId] = { items: {} };
  domains[domainId].items[key] = typeof value === 'string' ? value : JSON.stringify(value);
}

function buildDomainsFromLegacyPayload(payload = {}) {
  const domains = {};
  if (!payload || typeof payload !== 'object') return domains;

  if (payload.domains && typeof payload.domains === 'object') {
    Object.entries(payload.domains).forEach(([domainId, domain]) => {
      Object.entries(domain?.items || {}).forEach(([key, value]) => {
        mergeDomainItem(domains, domainId, key, value);
      });
    });
  }

  const directStorageMaps = [payload.storage, payload.storageMap, payload.items];
  directStorageMaps.forEach((map) => {
    if (!map || typeof map !== 'object') return;
    Object.entries(map).forEach(([key, value]) => {
      mergeDomainItem(domains, inferDomainFromStorageKey(key), key, value);
    });
  });

  if (Array.isArray(payload.plans)) {
    mergeDomainItem(domains, 'plans', 'ironlog_plans', payload.plans);
  }
  if (Array.isArray(payload.history)) {
    mergeDomainItem(domains, 'history', 'ironlog_history', payload.history);
  }
  if (Array.isArray(payload.bodyWeight)) {
    mergeDomainItem(domains, 'metrics', 'ironlog_bw', payload.bodyWeight);
  }
  if (Array.isArray(payload.bodyMeasurements)) {
    mergeDomainItem(domains, 'metrics', '@ironlog/bodyMeasurements', payload.bodyMeasurements);
  }
  if (Array.isArray(payload.customExercises)) {
    mergeDomainItem(domains, 'customExercises', '@ironlog/customExercises', payload.customExercises);
  }
  return domains;
}

export async function restoreBackupContainer(source, options = {}) {
  const container = typeof source?.manifest === 'object' ? source : await readSnapshotContainer(source);
  const passphrase = options.passphrase;
  if (!passphrase) throw new Error('Passphrase is required to restore this backup.');

  const validated = await validateBackupContainer(container, passphrase);
  if (!validated.valid) {
    throw new Error('Backup integrity validation failed.');
  }

  const normalizedDomains = buildDomainsFromLegacyPayload(validated.payload);
  const payloadDomainIds = Object.keys(normalizedDomains);
  const allDomainIds = (Array.isArray(container.manifest.includedDomains) && container.manifest.includedDomains.length
    ? container.manifest.includedDomains.filter((domainId) => payloadDomainIds.includes(domainId))
    : payloadDomainIds);
  const selectedDomains = (options.selectedDomains?.length ? options.selectedDomains : allDomainIds)
    .filter((domainId) => allDomainIds.includes(domainId));

  if (options.createRollback !== false) {
    await createRollbackSnapshot({
      passphrase,
      restoreSourceSnapshotId: container.manifest.snapshotId,
    });
  }

  // App runtime reads core training data from WatermelonDB (SQLite).
  // Keep SQLite in sync with restored snapshot domains so restore is immediately visible.
  const sqliteRelevantDomains = new Set(['plans', 'history', 'metrics', 'customExercises']);
  const shouldSyncSqlite = selectedDomains.some((domainId) => sqliteRelevantDomains.has(domainId));
  if (shouldSyncSqlite) {
    const restoredItems = collectRestoredStorageItems(normalizedDomains || {}, selectedDomains);
    const currentSnapshot = await loadTrainingSnapshotCompat().catch(() => ({
      plans: [],
      history: [],
      bodyWeight: [],
      bodyMeasurements: [],
      customExercises: [],
    }));

    const restoredPlans = parseMaybeJson(restoredItems.ironlog_plans, validated.payload?.plans ?? null);
    const restoredHistory = parseMaybeJson(restoredItems.ironlog_history, validated.payload?.history ?? null);
    const restoredBodyWeight = parseMaybeJson(restoredItems.ironlog_bw, validated.payload?.bodyWeight ?? null);
    const restoredBodyMeasurements = parseMaybeJson(
      restoredItems['@ironlog/bodyMeasurements'],
      validated.payload?.bodyMeasurements ?? null
    );
    const restoredCustomExercises = parseMaybeJson(
      restoredItems['@ironlog/customExercises'],
      validated.payload?.customExercises ?? null
    );

    await replaceTrainingSnapshotCompat({
      plans: Array.isArray(restoredPlans) ? restoredPlans : (currentSnapshot.plans || []),
      history: Array.isArray(restoredHistory) ? restoredHistory : (currentSnapshot.history || []),
      bodyWeight: Array.isArray(restoredBodyWeight) ? restoredBodyWeight : (currentSnapshot.bodyWeight || []),
      bodyMeasurements: Array.isArray(restoredBodyMeasurements) ? restoredBodyMeasurements : (currentSnapshot.bodyMeasurements || []),
      customExercises: Array.isArray(restoredCustomExercises) ? restoredCustomExercises : (currentSnapshot.customExercises || []),
    });

    const expectedCounts = buildExpectedCoreCounts(restoredItems, validated.payload, selectedDomains);
    const { mismatches } = await verifyRestoredCoreCounts(expectedCounts, selectedDomains);
    if (mismatches.length) {
      throw new Error(`Restore verification failed (${mismatches.join('; ')})`);
    }
  }

  await saveBackupStatus({
    lastRestoreAt: new Date().toISOString(),
    lastRestoreResult: 'success',
    lastFailure: null,
  });

  return {
    restoredDomains: selectedDomains,
    snapshotId: container.manifest.snapshotId,
    rollbackCreated: options.createRollback !== false,
  };
}

export async function shareBackupRecord(record) {
  if (!record?.localUri) throw new Error('Local snapshot file is missing.');
  const canShare = await Sharing.isAvailableAsync();
  if (!canShare) throw new Error('Sharing is not available on this device.');
  await Sharing.shareAsync(record.localUri, {
    mimeType: 'application/json',
    dialogTitle: 'Export Ironlog Encrypted Backup',
  });
  return record.localUri;
}

export async function pickImportedBackupFile() {
  const result = await DocumentPicker.getDocumentAsync({
    type: ['application/json', 'text/plain', '*/*'],
    copyToCacheDirectory: true,
  });
  if (result.canceled || !result.assets?.[0]) return null;
  return result.assets[0];
}

export async function exportPreviewAndShareLatest(options = {}) {
  const record = await runBackupNow({ reason: 'manual_export', syncToDrive: false, passphrase: options.passphrase || null });
  await shareBackupRecord(record);
  return record;
}

export async function runBackupNow(options = {}) {
  const reason = options.reason || 'manual';
  const syncToDrive = options.syncToDrive !== false;
  await queueBackup(reason);
  const config = await loadBackupConfig();
  if (!config.passphraseConfigured && !options.passphrase) {
    throw new Error('Set a backup passphrase before creating encrypted backups.');
  }

  const preview = await getCurrentBackupPreview();
  const status = await loadBackupStatus();
  if (reason !== 'manual' && reason !== 'manual_export' && !status.dirty && preview.dataHash === status.lastDataHash) {
    await clearQueuedBackup();
    return null;
  }

  const { record, container } = await buildSnapshotContainer({
    reason,
    passphrase: options.passphrase || null,
  });
  const persisted = await persistSnapshotRecord(record, container);

  if (syncToDrive && config.driveEnabled && config.enabled && (await isDriveBackupAvailable())) {
    try {
      const remoteRecord = await uploadSnapshotToDrive(persisted, container, { retentionCount: config.retentionCount || 5 });
      const index = await loadBackupIndex();
      const nextIndex = index.map((entry) => entry.snapshotId === persisted.snapshotId ? { ...entry, ...remoteRecord, remote: true } : entry);
      await saveBackupIndex(nextIndex);
      await saveBackupStatus({
        driveLinked: true,
        lastSyncedAt: remoteRecord.syncedAt || new Date().toISOString(),
        rollingVersionCount: nextIndex.filter((entry) => !entry.isRollback).length,
      });
      await clearQueuedBackup();
      return { ...persisted, ...remoteRecord, remote: true };
    } catch (error) {
      await saveBackupStatus({
        lastFailure: `Drive sync failed: ${error.message || error}`,
        lastBackupResult: 'local_only',
      });
    }
  }

  await clearQueuedBackup();
  return persisted;
}

export async function restoreFromLocalSnapshot(record, options) {
  return restoreBackupContainer(await readSnapshotContainer(record), options);
}

export async function importBackupWithPreview(passphrase) {
  const picked = await pickImportedBackupFile();
  if (!picked) return null;
  const container = await readSnapshotContainer(picked.uri);
  const preview = await buildRestorePreview(container, passphrase || null);
  return {
    file: picked,
    container,
    preview,
  };
}

export async function validateLatestBackup(passphrase) {
  const latest = (await listLocalSnapshots({ includeRollbacks: false }))[0];
  if (!latest) return { valid: false, reason: 'No local backups found.' };
  const container = await readSnapshotContainer(latest);
  const preview = await buildRestorePreview(container, passphrase || null);
  return {
    valid: preview.checksumValid !== false,
    preview,
  };
}

export async function refreshBackupHistory() {
  const local = await listLocalSnapshots({ includeRollbacks: true });
  if (await isDriveBackupAvailable()) {
    try {
      const remote = await listDriveSnapshots();
      const localIds = new Set(local.map((record) => record.snapshotId));
      return [
        ...local,
        ...remote.filter((record) => !localIds.has(record.snapshotId)),
      ].sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
    } catch (_) {
      return local;
    }
  }
  return local;
}

export async function fetchRemoteSnapshot(record) {
  if (!record?.remoteFileId && !record?.driveFileId) {
    throw new Error('Remote snapshot identifier is missing.');
  }
  return downloadDriveSnapshot(record.remoteFileId || record.driveFileId);
}

export { BACKUP_RESTOREABLE_DOMAINS };

