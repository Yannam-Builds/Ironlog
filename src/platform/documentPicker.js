import { errorCodes, isErrorWithCode, keepLocalCopy, pick, types } from '@react-native-documents/picker';

export async function getDocumentAsync(options = {}) {
  try {
    const result = await pick({
      type: options.type || [types.allFiles],
      mode: 'import',
      allowMultiSelection: false,
    });
    const selected = Array.isArray(result) ? result[0] : result;
    let resolvedUri = selected.uri;
    // Android commonly returns content:// URIs. Keep a local copy so filesystem reads are stable.
    if (String(selected?.uri || '').startsWith('content://')) {
      try {
        const copied = await keepLocalCopy({
          destination: 'cachesDirectory',
          files: [{
            uri: selected.uri,
            fileName: selected.name || `import-${Date.now()}.ironlog`,
          }],
        });
        const localUri = Array.isArray(copied) ? copied[0]?.localUri : null;
        if (localUri) {
          resolvedUri = localUri;
        }
      } catch (_) {
        // Fallback to original URI if keepLocalCopy fails; caller will surface a readable error.
      }
    }
    return {
      canceled: false,
      assets: [{
        uri: resolvedUri,
        name: selected.name,
        size: selected.size,
        mimeType: selected.type,
      }],
    };
  } catch (error) {
    if (isErrorWithCode(error) && error.code === errorCodes.OPERATION_CANCELED) {
      return { canceled: true, assets: [] };
    }
    throw error;
  }
}

export default {
  getDocumentAsync,
};
