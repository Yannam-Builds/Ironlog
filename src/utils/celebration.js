export function resolveCelebrationConfig(settings = {}) {
  const legacy = settings?.celebrationAnimation;
  const colorModeSetting = settings?.celebrationColorMode;
  const styleSetting = settings?.celebrationStyle;

  if (legacy === 'off') {
    return { enabled: false, style: 'fireworks', colorMode: 'gold' };
  }

  let colorMode = colorModeSetting || 'theme';
  if (legacy === 'confetti' && !colorModeSetting) colorMode = 'multicolor';
  if (legacy === 'gold'     && !colorModeSetting) colorMode = 'gold';

  let style = styleSetting || 'fireworks';
  if (!['fireworks', 'wave'].includes(style)) {
    style = 'fireworks';
  }

  const enabled = settings?.celebrationEnabled !== false;
  return { enabled, style, colorMode };
}
