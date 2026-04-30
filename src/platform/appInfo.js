import { NativeModules, Platform } from 'react-native';

export const APP_VERSION = '1.0-alpha';
export const APP_VERSION_LABEL = '1.0 alpha';
export const LEGACY_VERSION_LABELS = {
  v1: 'pre-alpha 0.1',
  v110: '0.5 alpha',
};

function getNativeValue(key) {
  if (Platform.OS !== 'android') return '';
  const value = NativeModules?.IronlogNativeConfig?.[key];
  return value ? String(value).trim() : '';
}

export function getEnvValue(...keys) {
  for (const key of keys) {
    if (key === 'IRONLOG_GOOGLE_DRIVE_ANDROID_CLIENT_ID') {
      const nativeValue = getNativeValue('googleDriveAndroidClientId');
      if (nativeValue) return nativeValue;
    }
    const value = process?.env?.[key];
    if (value) return String(value).trim();
  }
  return '';
}
