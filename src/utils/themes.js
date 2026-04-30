
import { PlatformColor, Platform } from 'react-native';

const isAndroid12 = Platform.OS === 'android' && Platform.Version >= 31;

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
    text: '#ffffff',
    subtext: '#aaaaaa',
    muted: '#777777',
    faint: '#1a1a1a',
    tabBg: '#000000',
    gold: '#FFD700',
    textOnAccent: '#ffffff',
    // contrast-safe aliases
    onCard: '#ffffff',
    ghostText: '#4d4d4d',
  },
  dark: {
    name: 'DARK',
    bg: '#121212',
    card: '#1c1c1e',
    cardBorder: '#2c2c2e',
    surface: '#252527',
    accent: '#FF4500',
    accentSoft: '#FF450022',
    accentBorder: '#FF450055',
    text: '#ffffff',
    subtext: '#bbbbbb',
    muted: '#909090',
    faint: '#2c2c2e',
    tabBg: '#1c1c1e',
    gold: '#FFD700',
    textOnAccent: '#ffffff',
    onCard: '#ffffff',
    ghostText: '#555555',
  },
  monet: {
    name: 'MONET',
    bg:           isAndroid12 ? PlatformColor('@android:color/system_neutral1_900') : '#1A1A2E',
    card:         isAndroid12 ? PlatformColor('@android:color/system_neutral1_800') : '#252540',
    cardBorder:   isAndroid12 ? PlatformColor('@android:color/system_neutral2_600') : '#52527A',
    surface:      isAndroid12 ? PlatformColor('@android:color/system_neutral1_800') : '#252540',
    accent:       isAndroid12 ? PlatformColor('@android:color/system_accent1_400') : '#D0BCFF',
    accentSoft:   isAndroid12 ? PlatformColor('@android:color/system_accent1_100') : '#3D3060',
    accentBorder: isAndroid12 ? PlatformColor('@android:color/system_accent1_200') : '#6040A0',
    text:         isAndroid12 ? PlatformColor('@android:color/system_neutral1_10')  : '#FFFFFF',
    subtext:      isAndroid12 ? PlatformColor('@android:color/system_neutral2_200') : '#CCCCE0',
    muted:        isAndroid12 ? PlatformColor('@android:color/system_neutral2_300') : '#9090BB',
    faint:        isAndroid12 ? PlatformColor('@android:color/system_neutral2_700') : '#454567',
    tabBg:        isAndroid12 ? PlatformColor('@android:color/system_neutral1_900') : '#1A1A2E',
    gold:         isAndroid12 ? PlatformColor('@android:color/system_accent3_300') : '#F5C842',
    textOnAccent: isAndroid12 ? PlatformColor('@android:color/system_neutral1_900') : '#1A1A2E',
    onCard:       isAndroid12 ? PlatformColor('@android:color/system_neutral1_10') : '#FFFFFF',
    ghostText:    isAndroid12 ? PlatformColor('@android:color/system_neutral2_500') : '#505070',
  },
  light: {
    name: 'LIGHT',
    bg: '#f2f2f7',
    card: '#ffffff',
    cardBorder: '#c8c8cc',
    surface: '#f2f2f7',
    accent: '#D42010',
    accentSoft: '#D4201015',
    accentBorder: '#D4201044',
    text: '#111111',
    subtext: '#333333',
    muted: '#555555',
    faint: '#e0e0e4',
    tabBg: '#ffffff',
    gold: '#B8720A',
    textOnAccent: '#ffffff',
    onCard: '#111111',
    ghostText: '#999999',
  },
};

export const DEFAULT_THEME = 'amoled';

// Radius scale - use these instead of raw numbers in new UI work.
export const RADIUS = {
  xs:   6,
  sm:   10,
  md:   14,
  lg:   18,
  xl:   24,
  full: 999,
};

// Elevation scale - platform-correct shadow presets.
export const ELEVATION = {
  card: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 6,
    elevation: 2,
  },
  raised: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.14,
    shadowRadius: 12,
    elevation: 6,
  },
  sheet: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.2,
    shadowRadius: 16,
    elevation: 12,
  },
};

// Typography scale - prefer these tokens on new or touched surfaces.
export const TYPE = {
  display: { fontSize: 32, fontWeight: '900', letterSpacing: -0.5, lineHeight: 36 },
  title:   { fontSize: 20, fontWeight: '900', letterSpacing: 0.3,  lineHeight: 26 },
  section: { fontSize: 16, fontWeight: '800', letterSpacing: 0.2,  lineHeight: 22 },
  body:    { fontSize: 14, fontWeight: '500', letterSpacing: 0,    lineHeight: 20 },
  meta:    { fontSize: 12, fontWeight: '500', letterSpacing: 0.1,  lineHeight: 16 },
  eyebrow: { fontSize: 10, fontWeight: '800', letterSpacing: 2.5,  lineHeight: 14 },
  button:  { fontSize: 12, fontWeight: '800', letterSpacing: 1.5,  lineHeight: 16 },
  micro:   { fontSize: 9,  fontWeight: '800', letterSpacing: 1.2,  lineHeight: 12 },
};

