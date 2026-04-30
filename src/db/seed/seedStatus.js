import { Q } from '@nozbe/watermelondb';
import { database } from '../database';

const SEED_KEY = 'exercise_seed_v1_complete';

export async function isExerciseSeedComplete() {
  const rows = await database
    .get('app_settings')
    .query(Q.where('key', SEED_KEY))
    .fetch();
  return rows.length > 0 && rows[0].value === 'true';
}

export async function markExerciseSeedComplete() {
  const settings = database.get('app_settings');
  await database.write(async () => {
    const existing = await settings.query(Q.where('key', SEED_KEY)).fetch();
    if (existing.length > 0) {
      await existing[0].update((row) => {
        row.value = 'true';
        row.valueType = 'boolean';
        row.updatedAt = Date.now();
      });
      return;
    }
    await settings.create((row) => {
      row.key = SEED_KEY;
      row.value = 'true';
      row.valueType = 'boolean';
      row.updatedAt = Date.now();
    });
  });
}

