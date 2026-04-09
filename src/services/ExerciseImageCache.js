
import * as FileSystem from 'expo-file-system/legacy';

const CACHE_DIR = FileSystem.documentDirectory + 'exercise-images/';
const BASE_URL = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/';

// Flatten path: "Air_Bike/0.jpg" → "Air_Bike___0.jpg"
const flatten = path => path.replace(/\//g, '___');
const localPath = imagePath => CACHE_DIR + flatten(imagePath);

// Deduplicate concurrent downloads
const inFlight = new Map();

async function ensureCacheDir() {
  const info = await FileSystem.getInfoAsync(CACHE_DIR);
  if (!info.exists) await FileSystem.makeDirectoryAsync(CACHE_DIR, { intermediates: true });
}

export async function getImageUri(imagePath) {
  if (!imagePath) return null;
  const local = localPath(imagePath);
  const info = await FileSystem.getInfoAsync(local);
  if (info.exists) return local;

  // Deduplicate concurrent downloads for same path
  if (inFlight.has(imagePath)) return inFlight.get(imagePath);

  const promise = (async () => {
    try {
      await ensureCacheDir();
      const url = BASE_URL + imagePath;
      const result = await FileSystem.downloadAsync(url, local);
      if (result.status === 200) return local;
      return null;
    } catch (_) {
      return null;
    } finally {
      inFlight.delete(imagePath);
    }
  })();

  inFlight.set(imagePath, promise);
  return promise;
}

export async function downloadAllImages(allImagePaths, onProgress, concurrency = 5) {
  await ensureCacheDir();
  let done = 0;
  const total = allImagePaths.length;

  // Filter already-cached
  const toDownload = [];
  await Promise.all(allImagePaths.map(async p => {
    const info = await FileSystem.getInfoAsync(localPath(p));
    if (!info.exists) toDownload.push(p);
    else { done++; onProgress && onProgress(done, total); }
  }));

  // Batch with concurrency limit
  for (let i = 0; i < toDownload.length; i += concurrency) {
    const batch = toDownload.slice(i, i + concurrency);
    await Promise.allSettled(batch.map(async p => {
      await getImageUri(p);
      done++;
      onProgress && onProgress(done, total);
    }));
  }
}

export async function getCacheSize() {
  try {
    const info = await FileSystem.getInfoAsync(CACHE_DIR);
    if (!info.exists) return 0;
    const files = await FileSystem.readDirectoryAsync(CACHE_DIR);
    let total = 0;
    await Promise.all(files.map(async f => {
      const fi = await FileSystem.getInfoAsync(CACHE_DIR + f);
      if (fi.exists) total += fi.size || 0;
    }));
    return total;
  } catch (_) { return 0; }
}

export async function clearCache() {
  try {
    await FileSystem.deleteAsync(CACHE_DIR, { idempotent: true });
    await FileSystem.makeDirectoryAsync(CACHE_DIR, { intermediates: true });
  } catch (_) {}
}
