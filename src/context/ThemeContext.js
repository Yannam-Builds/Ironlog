
import React, { createContext, useContext } from 'react';
import { useApp } from './AppContext';
import { THEMES, DEFAULT_THEME } from '../utils/themes';

export const ThemeContext = createContext(THEMES[DEFAULT_THEME]);

export function ThemeProvider({ children }) {
  const { settings } = useApp();
  const colors = THEMES[settings?.theme || DEFAULT_THEME] || THEMES[DEFAULT_THEME];
  return <ThemeContext.Provider value={colors}>{children}</ThemeContext.Provider>;
}

export const useTheme = () => useContext(ThemeContext);
