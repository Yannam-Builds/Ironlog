import { useEffect, useMemo, useState } from 'react';
import { getSetting, setSetting } from '../db/repositories/settingsRepository';

const KEY_PROFILES = 'ironlog_gym_profiles';
const KEY_ACTIVE = 'ironlog_active_gym_profile_id';

const DEFAULT_PROFILES = [
  {
    id: 'default',
    name: 'Default Gym',
    barWeight: 20,
    plates: [25, 20, 15, 10, 5, 2.5, 1.25],
  },
];

export default function useWatermelonGymProfiles() {
  const [gymProfiles, setGymProfiles] = useState(DEFAULT_PROFILES);
  const [activeGymProfileId, setActiveGymProfileIdState] = useState('default');

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const profiles = await getSetting(KEY_PROFILES);
        const activeId = await getSetting(KEY_ACTIVE);
        if (!mounted) return;
        if (Array.isArray(profiles) && profiles.length > 0) {
          setGymProfiles(profiles);
        }
        if (activeId) {
          setActiveGymProfileIdState(String(activeId));
        }
      } catch (_) {}
    })();
    return () => { mounted = false; };
  }, []);

  const saveGymProfiles = async (nextProfiles) => {
    const value = Array.isArray(nextProfiles) && nextProfiles.length > 0 ? nextProfiles : DEFAULT_PROFILES;
    setGymProfiles(value);
    await setSetting(KEY_PROFILES, value, 'json');
  };

  const setActiveGymProfileId = async (id) => {
    const next = String(id || 'default');
    setActiveGymProfileIdState(next);
    await setSetting(KEY_ACTIVE, next, 'string');
  };

  const activeGymProfile = useMemo(() => {
    return gymProfiles.find((p) => p.id === activeGymProfileId) || gymProfiles[0] || DEFAULT_PROFILES[0];
  }, [activeGymProfileId, gymProfiles]);

  return {
    gymProfiles,
    saveGymProfiles,
    activeGymProfileId,
    setActiveGymProfileId,
    activeGymProfile,
  };
}
