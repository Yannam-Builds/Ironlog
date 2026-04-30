import Share from 'react-native-share';

const toFileUrl = (uri) => {
  if (!uri) return uri;
  return uri.startsWith('file://') ? uri : `file://${uri}`;
};

export async function isAvailableAsync() {
  return true;
}

export async function shareAsync(uri, options = {}) {
  await Share.open({
    url: toFileUrl(uri),
    type: options.mimeType || options.type || 'application/octet-stream',
    title: options.dialogTitle || 'Share',
    failOnCancel: false,
  });
}

export default {
  isAvailableAsync,
  shareAsync,
};
