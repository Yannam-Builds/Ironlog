import { useEffect, useState } from 'react';
import { combineLatest } from 'rxjs';
import { Q } from '@nozbe/watermelondb';
import { database } from '../db/database';

const BODY = database.get('body_measurements');
const APP_SETTINGS = database.get('app_settings');

function assembleSettings(settingsRows) {
  const defaults = { weightUnit: 'kg', weeklyGoalDays: 4, goalMode: 'hypertrophy', theme: 'amoled' };
  if (!settingsRows.length) return defaults;
  try {
    return { ...defaults, ...JSON.parse(settingsRows[0].value) };
  } catch (_) { return defaults; }
}

export default function useWatermelonHome() {
  const [data, setData] = useState({
    settings: { weightUnit: 'kg', weeklyGoalDays: 4, goalMode: 'hypertrophy' },
    bodyWeight: [],
    loading: true,
  });

  useEffect(() => {
    const sub = combineLatest([
      BODY.query(Q.sortBy('measured_at', Q.desc)).observe(),
      APP_SETTINGS.query(Q.where('key', 'ironlog_settings')).observe(),
    ]).subscribe(([body, settingsRows]) => {
      const settings = assembleSettings(settingsRows);
      const bodyWeight = body
        .filter(b => b.bodyweight != null)
        .map(b => ({ weight: b.bodyweight, date: new Date(b.measuredAt).toISOString() }));
      setData({ settings, bodyWeight, loading: false });
    });
    return () => sub.unsubscribe();
  }, []);

  return data;
}
