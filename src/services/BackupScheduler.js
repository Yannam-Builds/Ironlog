import { NativeModules, Platform } from 'react-native';

const nativeModule = NativeModules.IronlogBackupScheduler;

function isAvailable() {
  return Platform.OS === 'android' && !!nativeModule;
}

/** Schedule a one-time backup job with a delay (milliseconds). */
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

/**
 * Schedule a repeating daily backup at the given 24-hour clock time using
 * WorkManager PeriodicWorkRequest (24h interval, initial delay to next occurrence).
 */
export async function schedulePeriodicBackupAt(hour, minute) {
  if (!isAvailable()) return false;
  const clampedHour = Math.max(0, Math.min(23, Math.floor(hour)));
  const clampedMinute = Math.max(0, Math.min(59, Math.floor(minute)));
  await nativeModule.schedulePeriodicBackup(clampedHour, clampedMinute);
  return true;
}

export async function cancelPeriodicBackup() {
  if (!isAvailable()) return false;
  await nativeModule.cancelPeriodicBackup();
  return true;
}

export async function checkBatteryOptimizationIgnored() {
  if (!isAvailable()) return true;
  return nativeModule.isBatteryOptimizationIgnored();
}

export async function requestBatteryOptimizationExemption() {
  if (!isAvailable()) return false;
  await nativeModule.requestBatteryOptimizationExemption();
  return true;
}

export async function isBackupSchedulerAvailable() {
  return isAvailable();
}
