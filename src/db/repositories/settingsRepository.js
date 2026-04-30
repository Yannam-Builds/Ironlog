import { Q } from '@nozbe/watermelondb';
import { database } from '../database';
import { requireNonEmpty } from '../../utils/validation';

const SETTINGS_TABLE = database.get('app_settings');

async function findSettingRow(key) {
  const rows = await SETTINGS_TABLE.query(Q.where('key', key)).fetch();
  return rows[0] || null;
}

export async function getSetting(key) {
  requireNonEmpty(key, 'key');
  const row = await findSettingRow(key);
  if (!row) return null;
  if (row.valueType === 'number') return Number(row.value);
  if (row.valueType === 'boolean') return row.value === 'true';
  if (row.valueType === 'json') return JSON.parse(row.value);
  return row.value;
}

export async function setSetting(key, value, valueType = 'string') {
  requireNonEmpty(key, 'key');
  const serialized =
    valueType === 'json'
      ? JSON.stringify(value ?? null)
      : valueType === 'boolean'
      ? String(!!value)
      : String(value ?? '');
  const now = Date.now();

  await database.write(async () => {
    const existing = await findSettingRow(key);
    if (existing) {
      await existing.update((row) => {
        row.value = serialized;
        row.valueType = valueType;
        row.updatedAt = now;
      });
      return;
    }
    await SETTINGS_TABLE.create((row) => {
      row.key = key;
      row.value = serialized;
      row.valueType = valueType;
      row.updatedAt = now;
    });
  });
}

export async function removeSetting(key) {
  requireNonEmpty(key, 'key');
  const row = await findSettingRow(key);
  if (!row) return;
  await database.write(async () => {
    await row.markAsDeleted();
    await row.destroyPermanently();
  });
}

export async function getTheme() {
  return (await getSetting('theme')) || 'dark';
}

export async function setTheme(theme) {
  return setSetting('theme', theme, 'string');
}

export async function getActiveWorkoutId() {
  return getSetting('active_workout_id');
}

export async function setActiveWorkoutId(workoutId) {
  if (!workoutId) {
    await removeSetting('active_workout_id');
    return;
  }
  await setSetting('active_workout_id', String(workoutId), 'string');
}

export async function clearActiveWorkoutId() {
  await removeSetting('active_workout_id');
}

