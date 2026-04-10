function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function scoreFromReadiness(readiness = {}) {
  const rows = Object.values(readiness).filter((value) => Number.isFinite(value));
  if (!rows.length) return 70;
  const avg = rows.reduce((sum, value) => sum + value, 0) / rows.length;
  return Math.round(clamp(avg * 100, 20, 98));
}

function scoreFromManualInput(input = null) {
  if (!input) return null;
  const soreness = Number(input.soreness || 0);
  const sleep = Number(input.sleepQuality || 0);
  const energy = Number(input.energy || 0);
  if (!soreness && !sleep && !energy) return null;
  const normalized = clamp(((sleep + energy) / 10) - (soreness / 10), -1, 1);
  return Math.round(normalized * 15);
}

export function computeRecoveryScore({
  readiness = {},
  manualInput = null,
} = {}) {
  const base = scoreFromReadiness(readiness);
  const manualOffset = scoreFromManualInput(manualInput);
  const score = Math.round(clamp(base + (manualOffset || 0), 1, 99));
  const state = score >= 78 ? 'fresh' : score >= 55 ? 'recovering' : 'fatigued';
  const explanation = manualOffset == null
    ? 'Based on recent muscle workload and time since training.'
    : 'Blended from workload history and your soreness/sleep/energy check-in.';

  return { score, state, explanation };
}

export function buildReadinessSuggestions({ readiness = {} } = {}) {
  const rows = Object.entries(readiness || {}).filter(([, value]) => Number.isFinite(value));
  if (!rows.length) return [];
  const sorted = rows.sort((a, b) => b[1] - a[1]);
  const best = sorted[0];
  const worst = sorted[sorted.length - 1];
  const nice = (value) => Math.round(clamp(value * 100, 0, 100));

  const suggestions = [
    `${best[0]} is most recovered (${nice(best[1])}%) and can handle focus work.`,
  ];
  if (worst && worst[0] !== best[0]) {
    suggestions.push(`${worst[0]} is still fatigued (${nice(worst[1])}%) so keep intensity moderate there.`);
  }
  return suggestions;
}
