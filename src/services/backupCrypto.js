import * as Crypto from 'expo-crypto';
import { fromByteArray, toByteArray } from 'base64-js';
import { pbkdf2Async } from '@noble/hashes/pbkdf2.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { gcm } from '@noble/ciphers/aes.js';

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

function normalizePassword(value) {
  return String(value || '').normalize('NFKC');
}

export function encodeUtf8(value) {
  return textEncoder.encode(String(value || ''));
}

export function decodeUtf8(value) {
  return textDecoder.decode(value);
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
