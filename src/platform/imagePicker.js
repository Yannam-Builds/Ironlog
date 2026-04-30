import { PermissionsAndroid, Platform } from 'react-native';
import { launchCamera, launchImageLibrary } from 'react-native-image-picker';

async function request(permission) {
  if (Platform.OS !== 'android') return { granted: true };
  const result = await PermissionsAndroid.request(permission);
  return { granted: result === PermissionsAndroid.RESULTS.GRANTED };
}

export async function requestCameraPermissionsAsync() {
  return request(PermissionsAndroid.PERMISSIONS.CAMERA);
}

export async function requestMediaLibraryPermissionsAsync() {
  const permission = Platform.Version >= 33
    ? PermissionsAndroid.PERMISSIONS.READ_MEDIA_IMAGES
    : PermissionsAndroid.PERMISSIONS.READ_EXTERNAL_STORAGE;
  return request(permission);
}

function normalizeResult(result) {
  if (result.didCancel) return { canceled: true, assets: [] };
  if (result.errorCode) throw new Error(result.errorMessage || result.errorCode);
  return { canceled: false, assets: (result.assets || []).map((asset) => ({
    uri: asset.uri,
    width: asset.width,
    height: asset.height,
    fileSize: asset.fileSize,
    type: asset.type,
    fileName: asset.fileName,
  })) };
}

export async function launchCameraAsync() {
  const result = await launchCamera({ mediaType: 'photo', quality: 1, includeBase64: false });
  return normalizeResult(result);
}

export async function launchImageLibraryAsync() {
  const result = await launchImageLibrary({ mediaType: 'photo', quality: 1, selectionLimit: 1 });
  return normalizeResult(result);
}

export default {
  requestCameraPermissionsAsync,
  requestMediaLibraryPermissionsAsync,
  launchCameraAsync,
  launchImageLibraryAsync,
};
