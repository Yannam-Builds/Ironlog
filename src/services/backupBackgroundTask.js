import RNFS from 'react-native-fs';
import { exportDatabase } from '../db/repositories/importExportRepository';
import { getSetting, setSetting } from '../db/repositories/settingsRepository';

const BACKUP_DIR = `${RNFS.DocumentDirectoryPath}/ironlogdb_backups`;
const BACKUP_FILE_PREFIX = 'ironlogdb_';
const BACKUP_FILE_EXT = '.json';
const DEFAULT_RETENTION_COUNT = 4;
const MIN_RETENTION = 2;
const MAX_RETENTION = 20;

async function ensureBackupDir() {
  const exists = await RNFS.exists(BACKUP_DIR);
  if (!exists) {
    await RNFS.mkdir(BACKUP_DIR);
  }
}

/** Read retention count from WatermelonDB settings (key persisted by the UI). */
async function readRetentionCount() {
  try {
    const stored = await getSetting('local_retention_count');
    if (stored != null) {
      const n = Number(stored);
      if (Number.isFinite(n) && n >= MIN_RETENTION) return Math.min(n, MAX_RETENTION);
    }
  } catch (_) { /* fall through to default */ }
  return DEFAULT_RETENTION_COUNT;
}

/**
 * List all backup JSON files in BACKUP_DIR sorted by filename ascending
 * (ISO timestamps in filenames sort lexicographically = chronologically).
 */
async function listBackupFiles() {
  try {
    const items = await RNFS.readDir(BACKUP_DIR);
    return items
      .filter((item) => item.isFile() && item.name.startsWith(BACKUP_FILE_PREFIX) && item.name.endsWith(BACKUP_FILE_EXT))
      .map((item) => ({ path: item.path, name: item.name }))
      .sort((a, b) => a.name.localeCompare(b.name)); // oldest first
  } catch (_) {
    return [];
  }
}

/** Delete backup files beyond the retention limit (oldest are removed first). */
async function pruneBackupFiles(retentionCount) {
  const files = await listBackupFiles();
  if (files.length <= retentionCount) return;
  const toDelete = files.slice(0, files.length - retentionCount); // remove oldest
  await Promise.all(
    toDelete.map((file) =>
      RNFS.unlink(file.path).catch(() => { /* ignore missing file errors */ })
    )
  );
}

export async function runHeadlessBackupTask(data = {}) {
  const reason = data?.reason || 'background';
  try {
    await ensureBackupDir();
    const payload = await exportDatabase();
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const outputPath = `${BACKUP_DIR}/${BACKUP_FILE_PREFIX}${reason}_${stamp}${BACKUP_FILE_EXT}`;
    await RNFS.writeFile(outputPath, JSON.stringify(payload, null, 2), 'utf8');

    // Prune old backups to stay within the configured retention window
    const retentionCount = await readRetentionCount();
    await pruneBackupFiles(retentionCount);

    await setSetting('last_backup_file', outputPath, 'string');
    await setSetting('last_backup_at', new Date().toISOString(), 'string');
    await setSetting('last_backup_result', 'success', 'string');
  } catch (error) {
    await setSetting('last_backup_result', `failed:${String(error?.message || error)}`, 'string');
  }
}
