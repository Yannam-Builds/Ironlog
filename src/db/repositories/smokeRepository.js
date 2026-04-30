import { Q } from '@nozbe/watermelondb';
import { database } from '../database';

const SMOKE = database.get('db_smoke_tests');

export function getSmokeRowsObservable() {
  return SMOKE.query(Q.sortBy('created_at', Q.desc)).observe();
}

export async function addSmokeRow(label = null) {
  const now = Date.now();
  await database.write(async () => {
    await SMOKE.create((row) => {
      row.label = label || `Smoke ${new Date(now).toLocaleTimeString()}`;
      row._raw.created_at = now;
    });
  });
}
