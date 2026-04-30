import { NativeModules, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import RNFS from 'react-native-fs';
import { TRAINING_DATABASE_NAME } from '../domain/storage/trainingDatabase';
import * as SecureStore from '../platform/secureStore';
import {
  BACKUP_DRIVE_TOKEN_SECURE_KEY,
  BACKUP_KEY_MATERIAL_SECURE_KEY,
} from './backupConstants';

const MIGRATION_MARKER_KEY = '@ironlog/bareRnMigrationV1';

const LegacySecureStoreImporter = NativeModules.LegacySecureStoreImporter;

const LEGACY_SCAN_MAX_DEPTH = 6;
const LEGACY_DIRS = ['backups', 'progress-photos', 'exercise-images'];

function normalizePath(path) {
  return String(path || '').replace(/\\/g, '/');
}

function parentDir(path) {
  const normalized = normalizePath(path).replace(/\/+$/, '');
  const idx = normalized.lastIndexOf('/');
  return idx > 0 ? normalized.slice(0, idx) : normalized;
}

function buildLikelyRoots() {
  const roots = new Set([
    RNFS.DocumentDirectoryPath,
    RNFS.CachesDirectoryPath,
    RNFS.TemporaryDirectoryPath,
    RNFS.ExternalDirectoryPath,
    RNFS.ExternalStorageDirectoryPath,
    `${RNFS.DocumentDirectoryPath}/../`,
  ].filter(Boolean).map(normalizePath));
  return [...roots];
}

async function exists(path) {
  try {
    return await RNFS.exists(path);
  } catch (_) {
    return false;
  }
}

async function ensureDir(path) {
  const ok = await exists(path);
  if (!ok) await RNFS.mkdir(path);
}

async function copyIfMissing(source, target) {
  const srcExists = await exists(source);
  const dstExists = await exists(target);
  if (!srcExists || dstExists) return false;
  await ensureDir(parentDir(target));
  await RNFS.copyFile(source, target);
  return true;
}

async function safeReadDir(path) {
  try {
    return await RNFS.readDir(path);
  } catch (_) {
    return [];
  }
}

async function findPathByName(startPath, targetName, maxDepth = LEGACY_SCAN_MAX_DEPTH, depth = 0) {
  if (!startPath || depth > maxDepth) return null;
  const entries = await safeReadDir(startPath);
  const matched = entries.find((entry) => normalizePath(entry.path).toLowerCase().endsWith(`/${targetName.toLowerCase()}`));
  if (matched) return matched.path;
  for (const entry of entries) {
    if (entry.isDirectory()) {
      const nested = await findPathByName(entry.path, targetName, maxDepth, depth + 1);
      if (nested) return nested;
    }
  }
  return null;
}

async function importLegacyDb() {
  const filesRoot = normalizePath(RNFS.DocumentDirectoryPath);
  const appRoot = parentDir(filesRoot);
  const dbDir = normalizePath(`${appRoot}/databases`);
  const targetDb = `${dbDir}/${TRAINING_DATABASE_NAME}`;
  await ensureDir(dbDir);

  if (await exists(targetDb)) return { migrated: false, reason: 'target_exists' };

  const candidates = [
    `${filesRoot}/SQLite/${TRAINING_DATABASE_NAME}`,
    `${filesRoot}/${TRAINING_DATABASE_NAME}`,
    `${appRoot}/files/SQLite/${TRAINING_DATABASE_NAME}`,
  ];

  for (const root of buildLikelyRoots()) {
    candidates.push(`${normalizePath(root)}/${TRAINING_DATABASE_NAME}`);
    candidates.push(`${normalizePath(root)}/SQLite/${TRAINING_DATABASE_NAME}`);
  }

  let source = null;
  for (const candidate of candidates) {
    if (await exists(candidate)) {
      source = candidate;
      break;
    }
  }

  if (!source) {
    for (const root of buildLikelyRoots()) {
      source = await findPathByName(root, TRAINING_DATABASE_NAME);
      if (source) break;
    }
  }

  if (!source) return { migrated: false, reason: 'source_not_found' };

  await RNFS.copyFile(source, targetDb);
  await copyIfMissing(`${source}-wal`, `${targetDb}-wal`);
  await copyIfMissing(`${source}-shm`, `${targetDb}-shm`);
  return { migrated: true, source, target: targetDb };
}

async function importLegacyDirs() {
  const filesRoot = normalizePath(RNFS.DocumentDirectoryPath);
  const results = [];

  for (const dirName of LEGACY_DIRS) {
    const target = `${filesRoot}/${dirName}`;
    const targetExists = await exists(target);
    if (targetExists) {
      results.push({ dirName, migrated: false, reason: 'target_exists' });
      continue;
    }

    let source = null;
    for (const root of buildLikelyRoots()) {
      const found = await findPathByName(root, dirName);
      if (found && normalizePath(found) !== normalizePath(target)) {
        source = found;
        break;
      }
    }

    if (!source) {
      results.push({ dirName, migrated: false, reason: 'source_not_found' });
      continue;
    }

    await RNFS.mkdir(target);
    await RNFS.copyFile(source, target).catch(async () => {
      // copyFile fails for dirs; fallback to recursive copy
      const stack = [{ from: source, to: target }];
      while (stack.length) {
        const current = stack.pop();
        const entries = await safeReadDir(current.from);
        for (const entry of entries) {
          const toPath = `${current.to}/${entry.name}`;
          if (entry.isDirectory()) {
            await ensureDir(toPath);
            stack.push({ from: entry.path, to: toPath });
          } else {
            await RNFS.copyFile(entry.path, toPath);
          }
        }
      }
    });

    results.push({ dirName, migrated: true, source, target });
  }
  return results;
}

async function importLegacySecureStore() {
  if (Platform.OS !== 'android') return { migrated: false, reason: 'not_android' };
  if (!LegacySecureStoreImporter?.importKeys) return { migrated: false, reason: 'native_importer_missing' };

  const keys = [BACKUP_KEY_MATERIAL_SECURE_KEY, BACKUP_DRIVE_TOKEN_SECURE_KEY];
  const current = await Promise.all(keys.map((key) => SecureStore.getItemAsync(key)));
  const missing = keys.filter((_, index) => !current[index]);
  if (!missing.length) return { migrated: false, reason: 'already_present' };

  const imported = await LegacySecureStoreImporter.importKeys(missing);
  const migratedKeys = [];
  for (const key of missing) {
    const value = imported?.[key];
    if (typeof value === 'string' && value.length) {
      await SecureStore.setItemAsync(key, value);
      migratedKeys.push(key);
    }
  }
  return { migrated: migratedKeys.length > 0, migratedKeys };
}

export async function runLegacyPlatformMigration() {
  const done = await AsyncStorage.getItem(MIGRATION_MARKER_KEY);
  if (done) return { skipped: true };

  const [secureStore, db, dirs] = await Promise.all([
    importLegacySecureStore().catch((error) => ({ migrated: false, error: String(error?.message || error) })),
    importLegacyDb().catch((error) => ({ migrated: false, error: String(error?.message || error) })),
    importLegacyDirs().catch((error) => [{ migrated: false, error: String(error?.message || error) }]),
  ]);

  const result = {
    secureStore,
    db,
    dirs,
    completedAt: new Date().toISOString(),
  };
  await AsyncStorage.setItem(MIGRATION_MARKER_KEY, JSON.stringify(result));
  return result;
}
