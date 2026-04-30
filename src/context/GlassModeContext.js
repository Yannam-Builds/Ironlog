import React, { createContext, useContext, useState, useEffect } from 'react';
import { getSetting as getWatermelonSetting, setSetting as setWatermelonSetting } from '../db/repositories/settingsRepository';

const STORAGE_KEY = 'tab_bar_glass_mode_v1';

const GlassModeContext = createContext({
  glassMode: 'frosted',
  setGlassMode: () => {},
});

export function GlassModeProvider({ children }) {
  const [glassMode, setGlassModeState] = useState('frosted');

  useEffect(() => {
    getWatermelonSetting(STORAGE_KEY)
      .then(v => { if (v) setGlassModeState(v); })
      .catch(() => {});
  }, []);

  const setGlassMode = (mode) => {
    setGlassModeState(mode);
    setWatermelonSetting(STORAGE_KEY, mode, 'string').catch(() => {});
  };

  return (
    <GlassModeContext.Provider value={{ glassMode, setGlassMode }}>
      {children}
    </GlassModeContext.Provider>
  );
}

export const useGlassMode = () => useContext(GlassModeContext);
