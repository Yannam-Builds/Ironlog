import { useEffect, useState } from 'react';
import { getSetting, setSetting } from '../db/repositories/settingsRepository';

const KEY = 'ironlog_manual_recovery_input';

export default function useWatermelonManualRecovery() {
  const [manualRecoveryInput, setManualRecoveryInput] = useState({
    soreness: 0,
    sleepQuality: 0,
    energy: 0,
    notes: '',
    recordedAt: null,
  });

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const value = await getSetting(KEY);
        if (!mounted) return;
        if (value && typeof value === 'object') {
          setManualRecoveryInput((prev) => ({ ...prev, ...value }));
        }
      } catch (_) {}
    })();
    return () => { mounted = false; };
  }, []);

  const saveManualRecovery = async (input) => {
    const next = {
      soreness: Number(input?.soreness || 0),
      sleepQuality: Number(input?.sleepQuality || 0),
      energy: Number(input?.energy || 0),
      notes: String(input?.notes || ''),
      recordedAt: input?.recordedAt || new Date().toISOString(),
    };
    setManualRecoveryInput(next);
    await setSetting(KEY, next, 'json');
  };

  return { manualRecoveryInput, saveManualRecovery };
}
