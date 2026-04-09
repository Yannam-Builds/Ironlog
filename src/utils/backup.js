
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import * as DocumentPicker from 'expo-document-picker';

export async function exportBackup(data) {
  const json = JSON.stringify(data, null, 2);
  const date = new Date().toISOString().split('T')[0];
  const uri = FileSystem.documentDirectory + 'ironlog_backup_' + date + '.json';
  await FileSystem.writeAsStringAsync(uri, json, { encoding: 'utf8' });
  await Sharing.shareAsync(uri, { mimeType: 'application/json', dialogTitle: 'Save IRONLOG Backup' });
  return uri;
}

export async function importBackup() {
  const result = await DocumentPicker.getDocumentAsync({ type: 'application/json', copyToCacheDirectory: true });
  if (result.canceled) return null;
  const asset = result.assets[0];
  const content = await FileSystem.readAsStringAsync(asset.uri, { encoding: 'utf8' });
  return JSON.parse(content);
}
