import { NativeModules, Platform } from 'react-native';

const nativeModule = NativeModules.IronlogBackupScheduler;

function isAvailable() {
  return Platform.OS === 'android' && !!nativeModule;
}

export async function scheduleBackupJob(reason, delayMs) {
  if (!isAvailable()) return false;
  await nativeModule.scheduleBackup(String(reason || 'manual'), Number(delayMs || 0));
  return true;
}

export async function cancelBackupJob() {
  if (!isAvailable()) return false;
  await nativeModule.cancelScheduledBackup();
  return true;
}

export async function isBackupSchedulerAvailable() {
  return isAvailable();
}
