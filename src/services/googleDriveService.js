import * as AuthSession from 'expo-auth-session';
import * as WebBrowser from 'expo-web-browser';
import * as SecureStore from 'expo-secure-store';
import { exchangeCodeAsync, fetchUserInfoAsync, refreshAsync, revokeAsync, TokenResponse } from 'expo-auth-session';
import {
  BACKUP_DRIVE_TOKEN_SECURE_KEY,
  GOOGLE_DISCOVERY,
  GOOGLE_DRIVE_SCOPES,
} from './backupConstants';

WebBrowser.maybeCompleteAuthSession();

function getGoogleClientId() {
  return (
    process.env.EXPO_PUBLIC_GOOGLE_DRIVE_ANDROID_CLIENT_ID ||
    process.env.EXPO_PUBLIC_GOOGLE_DRIVE_WEB_CLIENT_ID ||
    ''
  );
}

function getRedirectUri() {
  return AuthSession.makeRedirectUri({
    scheme: 'ironlog',
    path: 'oauth',
  });
}

async function getStoredDriveToken() {
  const raw = await SecureStore.getItemAsync(BACKUP_DRIVE_TOKEN_SECURE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return null;
  }
}

async function saveStoredDriveToken(token) {
  await SecureStore.setItemAsync(BACKUP_DRIVE_TOKEN_SECURE_KEY, JSON.stringify(token));
}

async function clearStoredDriveToken() {
  await SecureStore.deleteItemAsync(BACKUP_DRIVE_TOKEN_SECURE_KEY);
}

async function getFreshToken() {
  const token = await getStoredDriveToken();
  if (!token) return null;
  const tokenResponse = new TokenResponse(token);
  if (!tokenResponse.shouldRefresh() || !token.refreshToken) {
    return tokenResponse;
  }
  const clientId = getGoogleClientId();
  if (!clientId) {
    throw new Error('Google Drive backup is not configured on this build.');
  }
  const refreshed = await refreshAsync({
    clientId,
    refreshToken: token.refreshToken,
    scopes: GOOGLE_DRIVE_SCOPES,
  }, GOOGLE_DISCOVERY);
  const nextToken = {
    ...token,
    ...refreshed.getRequestConfig(),
    accessToken: refreshed.accessToken,
    refreshToken: refreshed.refreshToken || token.refreshToken,
    expiresIn: refreshed.expiresIn,
    issuedAt: refreshed.issuedAt,
    idToken: refreshed.idToken || token.idToken,
  };
  await saveStoredDriveToken(nextToken);
  return new TokenResponse(nextToken);
}

async function authorizedFetch(url, options = {}) {
  const token = await getFreshToken();
  if (!token?.accessToken) {
    throw new Error('Google Drive is not connected.');
  }
  const headers = {
    Authorization: `Bearer ${token.accessToken}`,
    ...(options.headers || {}),
  };
  const response = await fetch(url, { ...options, headers });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Drive request failed with ${response.status}`);
  }
  return response;
}

async function pruneRemoteBackups(retentionCount) {
  const snapshots = await listDriveSnapshots();
  const removable = snapshots.filter((snapshot) => !snapshot.isRollback).slice(retentionCount);
  await Promise.all(removable.map(async (snapshot) => {
    await authorizedFetch(`https://www.googleapis.com/drive/v3/files/${snapshot.remoteFileId || snapshot.driveFileId}`, {
      method: 'DELETE',
    });
  }));
}

export function getDriveConfiguration() {
  const clientId = getGoogleClientId();
  return {
    configured: !!clientId,
    clientId,
    redirectUri: getRedirectUri(),
  };
}

export async function isDriveBackupAvailable() {
  const config = getDriveConfiguration();
  if (!config.configured) return false;
  const token = await getStoredDriveToken();
  return !!token?.accessToken;
}

export async function getDriveConnectionStatus() {
  const config = getDriveConfiguration();
  const token = await getStoredDriveToken();
  return {
    configured: config.configured,
    linked: !!token?.accessToken,
    email: token?.email || null,
    connectedAt: token?.connectedAt || null,
    redirectUri: config.redirectUri,
  };
}

export async function connectGoogleDrive() {
  const { configured, clientId, redirectUri } = getDriveConfiguration();
  if (!configured) {
    throw new Error('Google Drive backup is not configured on this build. Set EXPO_PUBLIC_GOOGLE_DRIVE_ANDROID_CLIENT_ID first.');
  }

  const request = await AuthSession.loadAsync({
    clientId,
    scopes: GOOGLE_DRIVE_SCOPES,
    redirectUri,
    responseType: 'code',
    usePKCE: true,
    extraParams: {
      access_type: 'offline',
      prompt: 'consent',
    },
  }, GOOGLE_DISCOVERY);

  const result = await request.promptAsync(GOOGLE_DISCOVERY);
  if (result.type !== 'success' || !result.params?.code) {
    return { connected: false, cancelled: result.type !== 'success' };
  }

  const tokenResponse = await exchangeCodeAsync({
    clientId,
    code: result.params.code,
    redirectUri,
    extraParams: {
      code_verifier: request.codeVerifier || '',
    },
  }, GOOGLE_DISCOVERY);

  let email = null;
  try {
    const user = await fetchUserInfoAsync({ accessToken: tokenResponse.accessToken }, {
      userInfoEndpoint: 'https://openidconnect.googleapis.com/v1/userinfo',
    });
    email = user?.email || null;
  } catch (_) {
    // Ignore user-info failures; Drive backup can still work.
  }

  const nextToken = {
    ...tokenResponse.getRequestConfig(),
    accessToken: tokenResponse.accessToken,
    refreshToken: tokenResponse.refreshToken || null,
    expiresIn: tokenResponse.expiresIn,
    issuedAt: tokenResponse.issuedAt,
    idToken: tokenResponse.idToken || null,
    email,
    connectedAt: new Date().toISOString(),
  };
  await saveStoredDriveToken(nextToken);
  return {
    connected: true,
    email,
    connectedAt: nextToken.connectedAt,
  };
}

export async function disconnectGoogleDrive() {
  const token = await getStoredDriveToken();
  if (token?.refreshToken) {
    try {
      await revokeAsync({
        token: token.refreshToken,
        clientId: getGoogleClientId(),
      }, GOOGLE_DISCOVERY);
    } catch (_) {
      // Ignore revoke failures; local disconnect still succeeds.
    }
  }
  await clearStoredDriveToken();
}

export async function uploadSnapshotToDrive(record, container, options = {}) {
  const metadata = {
    name: `${record.snapshotId}.ironlog.json`,
    parents: ['appDataFolder'],
    mimeType: 'application/json',
    appProperties: {
      snapshotId: record.snapshotId,
      createdAt: record.createdAt,
      isRollback: record.isRollback ? '1' : '0',
      dataHash: record.dataHash || '',
    },
  };
  const boundary = `ironlog-boundary-${Date.now()}`;
  const body = [
    `--${boundary}`,
    'Content-Type: application/json; charset=UTF-8',
    '',
    JSON.stringify(metadata),
    `--${boundary}`,
    'Content-Type: application/json',
    '',
    JSON.stringify(container),
    `--${boundary}--`,
  ].join('\r\n');

  const response = await authorizedFetch('https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime', {
    method: 'POST',
    headers: {
      'Content-Type': `multipart/related; boundary=${boundary}`,
    },
    body,
  });
  const uploaded = await response.json();
  if (options.retentionCount) {
    await pruneRemoteBackups(options.retentionCount);
  }
  return {
    driveFileId: uploaded.id,
    remoteFileId: uploaded.id,
    remote: true,
    syncedAt: new Date().toISOString(),
  };
}

export async function listDriveSnapshots() {
  const response = await authorizedFetch(
    "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,createdTime,appProperties)&q=trashed=false"
  );
  const payload = await response.json();
  return (payload.files || [])
    .map((file) => ({
      snapshotId: file.appProperties?.snapshotId || file.id,
      createdAt: file.appProperties?.createdAt || file.createdTime,
      driveFileId: file.id,
      remoteFileId: file.id,
      source: 'drive',
      local: false,
      remote: true,
      isRollback: file.appProperties?.isRollback === '1',
      dataHash: file.appProperties?.dataHash || null,
      localUri: null,
    }))
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
}

export async function downloadDriveSnapshot(fileId) {
  const response = await authorizedFetch(`https://www.googleapis.com/drive/v3/files/${fileId}?alt=media`);
  return response.json();
}
