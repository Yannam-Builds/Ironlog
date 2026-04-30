import RNFS from 'react-native-fs';

const withScheme = (path) => {
  if (!path) return path;
  if (path.startsWith('file://')) return path;
  return `file://${path}`;
};

const toPath = (uri) => {
  if (!uri) return uri;
  return String(uri).replace(/^file:\/\//, '');
};

export const documentDirectory = withScheme(RNFS.DocumentDirectoryPath + '/');
export const cacheDirectory = withScheme(RNFS.CachesDirectoryPath + '/');

export const EncodingType = {
  Base64: 'base64',
  UTF8: 'utf8',
};

export async function getInfoAsync(uri) {
  const path = toPath(uri);
  const stat = await RNFS.stat(path).catch(() => null);
  if (!stat) return { exists: false, isDirectory: false, size: 0, uri: withScheme(path) };
  return {
    exists: true,
    isDirectory: stat.isDirectory(),
    size: Number(stat.size || 0),
    uri: withScheme(path),
    modificationTime: stat.mtime ? new Date(stat.mtime).getTime() : null,
  };
}

export async function makeDirectoryAsync(uri) {
  const path = toPath(uri);
  await RNFS.mkdir(path);
}

export async function copyAsync({ from, to }) {
  await RNFS.copyFile(toPath(from), toPath(to));
}

export async function deleteAsync(uri, options = {}) {
  const path = toPath(uri);
  const exists = await RNFS.exists(path);
  if (!exists) {
    if (options.idempotent) return;
    throw new Error('Path does not exist.');
  }
  await RNFS.unlink(path);
}

export async function readAsStringAsync(uri, options = {}) {
  const encoding = options.encoding === EncodingType.Base64 ? 'base64' : 'utf8';
  return RNFS.readFile(toPath(uri), encoding);
}

export async function writeAsStringAsync(uri, contents, options = {}) {
  const encoding = options.encoding === EncodingType.Base64 ? 'base64' : 'utf8';
  await RNFS.writeFile(toPath(uri), contents, encoding);
}

export async function readDirectoryAsync(uri) {
  return RNFS.readDir(toPath(uri)).then((entries) => entries.map((entry) => entry.name));
}

export async function downloadAsync(url, fileUri) {
  const toFile = toPath(fileUri);
  const result = await RNFS.downloadFile({ fromUrl: url, toFile }).promise;
  return {
    uri: withScheme(toFile),
    status: result.statusCode,
    headers: result.headers || {},
    md5: null,
  };
}

export default {
  documentDirectory,
  cacheDirectory,
  EncodingType,
  getInfoAsync,
  makeDirectoryAsync,
  copyAsync,
  deleteAsync,
  readAsStringAsync,
  writeAsStringAsync,
  readDirectoryAsync,
  downloadAsync,
};
