import * as Crypto from '../platform/crypto';
import { fromByteArray, toByteArray } from 'base64-js';
import { pbkdf2Async } from '@noble/hashes/pbkdf2.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { gcm } from '@noble/ciphers/aes.js';

const hasTextEncoder = typeof global.TextEncoder === 'function';
const hasTextDecoder = typeof global.TextDecoder === 'function';
const textEncoder = hasTextEncoder ? new global.TextEncoder() : null;
const textDecoder = hasTextDecoder ? new global.TextDecoder() : null;

function normalizePassword(value) {
  return String(value || '').normalize('NFKC');
}

export function encodeUtf8(value) {
  const input = String(value || '');
  if (textEncoder) {
    return textEncoder.encode(input);
  }
  const encoded = unescape(encodeURIComponent(input));
  const bytes = new Uint8Array(encoded.length);
  for (let i = 0; i < encoded.length; i += 1) {
    bytes[i] = encoded.charCodeAt(i);
  }
  return bytes;
}

export function decodeUtf8(value) {
  const bytes = value instanceof Uint8Array ? value : new Uint8Array(value || []);
  if (textDecoder) {
    return textDecoder.decode(bytes);
  }
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  try {
    return decodeURIComponent(escape(binary));
  } catch {
    return binary;
  }
}

export function toBase64(bytes) {
  return fromByteArray(bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes || []));
}

export function fromBase64(value) {
  return toByteArray(String(value || ''));
}

export function bytesToHex(bytes) {
  return Array.from(bytes || [], (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export async function sha256Hex(value) {
  const bytes = typeof value === 'string' ? encodeUtf8(value) : new Uint8Array(value || []);
  const digest = await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, bytes);
  return bytesToHex(new Uint8Array(digest));
}

export async function randomBytes(length) {
  return Crypto.getRandomBytesAsync(length);
}

export async function deriveBackupKey(passphrase, saltBytes, iterations = 210000) {
  return pbkdf2Async(sha256, normalizePassword(passphrase), saltBytes, { c: iterations, dkLen: 32 });
}

export async function fingerprintKey(keyBytes) {
  return sha256Hex(keyBytes);
}

export async function encryptJsonPayload(payload, { keyBytes, saltBytes, aad }) {
  const nonce = await randomBytes(12);
  const plainJson = typeof payload === 'string' ? payload : JSON.stringify(payload);
  const plainBytes = encodeUtf8(plainJson);
  const cipher = gcm(keyBytes, nonce, aad ? encodeUtf8(aad) : undefined);
  const cipherBytes = cipher.encrypt(plainBytes);
  const payloadChecksum = await sha256Hex(plainJson);
  return {
    ciphertext: toBase64(cipherBytes),
    nonce: toBase64(nonce),
    salt: toBase64(saltBytes),
    payloadChecksum,
    byteLength: plainBytes.length,
  };
}

export async function decryptJsonPayload(encrypted, { passphrase, aad }) {
  const saltBytes = fromBase64(encrypted.salt);
  const nonce = fromBase64(encrypted.nonce);
  const cipherBytes = fromBase64(encrypted.ciphertext);
  const keyBytes = await deriveBackupKey(passphrase, saltBytes);
  const cipher = gcm(keyBytes, nonce, aad ? encodeUtf8(aad) : undefined);
  const plainBytes = cipher.decrypt(cipherBytes);
  const plainJson = decodeUtf8(plainBytes);
  const payloadChecksum = await sha256Hex(plainJson);
  return {
    keyBytes,
    payload: JSON.parse(plainJson),
    payloadChecksum,
  };
}

