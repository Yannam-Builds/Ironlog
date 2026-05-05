import { processColor } from 'react-native';

/**
 * Convert any theme color (hex string, rgba string, or PlatformColor object)
 * to a hex string with the given alpha applied.
 */
export function withAlpha(color, alpha, fallback = '#FF4500') {
  const a = Math.max(0, Math.min(1, alpha));
  const aHex = Math.round(a * 255).toString(16).padStart(2, '0').toUpperCase();

  if (typeof color === 'string') {
    if (/^#([0-9a-f]{6})$/i.test(color)) return `${color}${aHex}`;
    if (/^#([0-9a-f]{8})$/i.test(color)) return `${color.slice(0, 7)}${aHex}`;
    if (color.startsWith('rgba(')) return color.replace(/[\d.]+\)$/, `${a})`);
    if (color.startsWith('rgb(')) return color.replace('rgb(', 'rgba(').replace(')', `,${a})`);
  }

  try {
    const resolved = processColor(color);
    if (typeof resolved === 'number') {
      const r = (resolved >> 16) & 0xff;
      const g = (resolved >> 8) & 0xff;
      const b = resolved & 0xff;
      return `#${[r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('')}${aHex}`.toUpperCase();
    }
  } catch {}

  return `${fallback}${aHex}`;
}

export function resolveColor(color, fallback = '#FF4500') {
  return withAlpha(color, 1, fallback).slice(0, 7);
}

export function pickContrastText(hexColor, fallback = '#FFFFFF') {
  const m = /^#?([0-9a-f]{6})$/i.exec(String(hexColor || ''));
  if (!m) return fallback;
  const n = parseInt(m[1], 16);
  const r = (n >> 16) & 0xff;
  const g = (n >> 8) & 0xff;
  const b = n & 0xff;
  const luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  return luma > 0.62 ? '#111111' : '#FFFFFF';
}

export function textOnColor(background, preferredText, fallbackText = '#FFFFFF') {
  const bgHex = resolveColor(background, '#FF4500');
  const preferredHex = preferredText ? resolveColor(preferredText, fallbackText) : fallbackText;
  return preferredHex.toLowerCase() === bgHex.toLowerCase()
    ? pickContrastText(bgHex, fallbackText)
    : preferredHex;
}
