import { Image } from 'react-native';
import ImageResizer from '@bam.tech/react-native-image-resizer';

export const SaveFormat = {
  JPEG: 'jpeg',
  PNG: 'png',
  WEBP: 'webp',
};

function getImageSize(uri) {
  return new Promise((resolve, reject) => {
    Image.getSize(uri, (width, height) => resolve({ width, height }), reject);
  });
}

export async function manipulateAsync(uri, actions = [], options = {}) {
  const resizeAction = actions.find((action) => action?.resize);
  if (!resizeAction) {
    return { uri, width: null, height: null };
  }

  const current = await getImageSize(uri).catch(() => ({ width: 1080, height: 1080 }));
  const targetWidth = Number(resizeAction.resize?.width || current.width);
  const targetHeight = Number(resizeAction.resize?.height || Math.round((current.height / current.width) * targetWidth));
  const quality = Math.max(1, Math.min(100, Math.round(Number(options.compress ?? 0.8) * 100)));
  const format = String(options.format || SaveFormat.JPEG).toUpperCase();

  const resized = await ImageResizer.createResizedImage(uri, targetWidth, targetHeight, format, quality, 0);
  return {
    uri: resized.uri,
    width: resized.width,
    height: resized.height,
  };
}

export default {
  SaveFormat,
  manipulateAsync,
};
