import 'react-native-get-random-values';
import { sha256 } from '@noble/hashes/sha2.js';

function asBytes(value) {
  if (value instanceof Uint8Array) return value;
  if (value instanceof ArrayBuffer) return new Uint8Array(value);
  if (ArrayBuffer.isView(value)) return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
  return new TextEncoder().encode(String(value || ''));
}

function randomBytes(length) {
  const out = new Uint8Array(length);
  global.crypto.getRandomValues(out);
  return out;
}

function randomUuid() {
  if (global.crypto?.randomUUID) return global.crypto.randomUUID();
  const bytes = randomBytes(16);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export const CryptoDigestAlgorithm = {
  SHA256: 'SHA-256',
};

export async function digest(_algorithm, data) {
  const hashed = sha256(asBytes(data));
  return hashed.buffer.slice(hashed.byteOffset, hashed.byteOffset + hashed.byteLength);
}

export async function getRandomBytesAsync(length) {
  return randomBytes(length);
}

export function randomUUID() {
  return randomUuid();
}

export default {
  CryptoDigestAlgorithm,
  digest,
  getRandomBytesAsync,
  randomUUID,
};
