import { useEffect, useRef, useState } from 'react';
import { Q } from '@nozbe/watermelondb';
import { database } from '../db/database';
import { setSetting } from '../db/repositories/settingsRepository';

const APP_SETTINGS = database.get('app_settings');
const DEFAULTS = {
  weightUnit: 'kg',
  hapticFeedback: true,
  keepAwake: true,
  barWeight: 20,
  defaultRestNormal: 120,
  defaultRestHeavy: 180,
  goalMode: 'hypertrophy',
  weeklyGoalDays: 4,
  theme: 'amoled',
};

// Module-level broadcast so every hook instance receives updates made by any instance.
// This is needed because:
//  - WatermelonDB query.observe() only re-emits on result-set changes (rows added/removed),
//    not on field-value changes to existing rows.
//  - Each useWatermelonSettings() call is an independent hook instance with its own state.
// When one instance calls updateSettings, _broadcast pushes the merged value to all others.
const _listeners = new Set();
function _broadcast(merged) {
  _listeners.forEach(fn => fn(merged));
}

export default function useWatermelonSettings() {
  const [settings, setSettings] = useState(DEFAULTS);
  const latestSettingsRef = useRef(DEFAULTS);

  useEffect(() => {
    latestSettingsRef.current = settings;
  }, [settings]);

  useEffect(() => {
    // Subscribe to DB observable (fires on row add/remove, e.g. first write)
    const sub = APP_SETTINGS.query(Q.where('key', 'ironlog_settings')).observe().subscribe(rows => {
      if (!rows.length) { setSettings(DEFAULTS); return; }
      try {
        setSettings({ ...DEFAULTS, ...JSON.parse(rows[0].value) });
      } catch (_) { setSettings(DEFAULTS); }
    });

    // Subscribe to cross-instance broadcast (fires on any updateSettings call)
    _listeners.add(setSettings);

    return () => {
      sub.unsubscribe();
      _listeners.delete(setSettings);
    };
  }, []);

  const updateSettings = async (updates) => {
    const merged = { ...latestSettingsRef.current, ...updates };
    latestSettingsRef.current = merged;
    // Optimistic update for this instance + broadcast to all other instances
    setSettings(merged);
    _broadcast(merged);
    await setSetting('ironlog_settings', merged, 'json');
  };

  return { settings, updateSettings };
}
