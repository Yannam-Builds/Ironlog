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
