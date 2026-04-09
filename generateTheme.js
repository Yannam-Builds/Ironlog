const fs = require('fs');
function w(p, c) { fs.writeFileSync(p, c); console.log('W: ' + p); }

w('src/utils/themes.js', `
export const THEMES = {
  amoled: {
    name: 'AMOLED',
    bg: '#000000',
    card: '#080808',
    cardBorder: '#111111',
    surface: '#0d0d0d',
    accent: '#FF4500',
    accentSoft: '#FF450022',
    accentBorder: '#FF450044',
    text: '#f0f0f0',
    subtext: '#888888',
    muted: '#444444',
    faint: '#1a1a1a',
    tabBg: '#000000',
    gold: '#FFD700',
  },
  dark: {
    name: 'DARK',
    bg: '#121212',
    card: '#1c1c1e',
    cardBorder: '#2c2c2e',
    surface: '#1c1c1e',
    accent: '#FF4500',
    accentSoft: '#FF450022',
    accentBorder: '#FF450055',
    text: '#ffffff',
    subtext: '#adadad',
    muted: '#6e6e6e',
    faint: '#2c2c2e',
    tabBg: '#1c1c1e',
    gold: '#FFD700',
  },
  monet: {
    name: 'MONET',
    bg: '#0d0f14',
    card: '#141820',
    cardBorder: '#1e2530',
    surface: '#1a2030',
    accent: '#7B9FE8',
    accentSoft: '#7B9FE822',
    accentBorder: '#7B9FE844',
    text: '#e8eaf6',
    subtext: '#9fa8c4',
    muted: '#5c6480',
    faint: '#1e2530',
    tabBg: '#0d0f14',
    gold: '#F5C842',
  },
  light: {
    name: 'LIGHT',
    bg: '#f2f2f7',
    card: '#ffffff',
    cardBorder: '#e5e5ea',
    surface: '#ffffff',
    accent: '#FF3B30',
    accentSoft: '#FF3B3015',
    accentBorder: '#FF3B3033',
    text: '#1c1c1e',
    subtext: '#6e6e73',
    muted: '#aeaeb2',
    faint: '#e5e5ea',
    tabBg: '#ffffff',
    gold: '#FF9500',
  },
};

export const DEFAULT_THEME = 'amoled';
`);

w('src/context/ThemeContext.js', `
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
`);

console.log('Theme files done');
