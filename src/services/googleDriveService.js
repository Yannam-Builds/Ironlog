import Share from 'react-native-share';
import RNFS from 'react-native-fs';

/**
 * Share a backup file via the native Android share sheet.
 * The user picks the destination (Google Drive, Files, email, etc.) — no OAuth required.
 */
export async function shareBackupFile(filePath, fileName) {
  const exists = await RNFS.exists(filePath);
  if (!exists) throw new Error('Backup file not found at the expected path.');

  await Share.open({
    url: `file://${filePath}`,
    type: 'application/json',
    filename: fileName || 'ironlogdb_backup.json',
    title: 'Save IronlogDB backup',
    failOnCancel: false,
  });
}

export async function hydrateDriveOAuthConfig() {
  return '';
}
