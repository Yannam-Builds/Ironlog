import { getQueuedBackup, runBackupNow, saveBackupStatus } from './backupService';

export async function runHeadlessBackupTask(data = {}) {
  const queued = await getQueuedBackup();
  const reason = data?.reason || queued?.reason || 'background';
  try {
    await runBackupNow({ reason, syncToDrive: true });
  } catch (error) {
    await saveBackupStatus({
      lastFailure: `Background backup failed: ${error.message || error}`,
      lastBackupResult: 'failed',
    });
  }
}
