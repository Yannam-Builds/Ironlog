import { GROUPS } from './intelligenceEngine';

// Recovery rate constants for hourly decay
export const RECOVERY_RATES = {
  // fast recovery (k = 0.06 - 0.07)
  biceps_long: 0.06,
  biceps_short: 0.06,
  triceps_medial: 0.06,
  forearms: 0.07,
  calves: 0.07,

  // moderate
  triceps_long: 0.05,
  triceps_lateral: 0.05,
  frontDelts: 0.05,
  lateralDelts: 0.05,
  rearDelts: 0.05,

  // slow (big muscles)
  upperChest: 0.04,
  midChest: 0.04,
  lowerChest: 0.04,
  lats: 0.04,
  upperBack: 0.04,
  quads: 0.035,
  hamstrings: 0.035,
  glutes: 0.035,
  upperAbs: 0.04,
  lowerAbs: 0.04,
  obliques: 0.04,

  // very slow
  lowerBack: 0.03,
  traps: 0.03
};

export function decayFatigueHourly(fatigueObj, hoursElapsed) {
  const decayed = {};
  Object.entries(fatigueObj).forEach(([muscle, value]) => {
    const k = RECOVERY_RATES[muscle] || 0.04;
    decayed[muscle] = value * Math.exp(-k * hoursElapsed);
  });
  return decayed;
}

export function computeReadiness(fatigueObj) {
  const readiness = {};
  // Lower threshold so real logged sessions are reflected more aggressively on the heatmap.
  const FATIGUE_THRESHOLD = 900;

  Object.entries(fatigueObj).forEach(([muscle, value]) => {
    const n = Math.min(value / FATIGUE_THRESHOLD, 1.0); 
    readiness[muscle] = 1 - n;
  });

  // Ensure all tracked muscles are represented even if they have 0 fatigue
  Object.keys(RECOVERY_RATES).forEach(m => {
    if (readiness[m] === undefined) readiness[m] = 1.0;
  });

  return readiness;
}

export function getGroupReadiness(readiness) {
  const result = {};
  Object.entries(GROUPS).forEach(([group, muscles]) => {
    let sum = 0;
    let count = 0;
    muscles.forEach(m => {
      if (readiness[m] !== undefined) {
        sum += readiness[m];
        count++;
      }
    });
    result[group] = count > 0 ? sum / count : 1.0;
  });
  if (typeof readiness?.rearDelts === 'number') {
    result.rearDelts = readiness.rearDelts;
  } else if (typeof result.shoulders === 'number') {
    result.rearDelts = result.shoulders;
  }
  return result;
}

export function computePriority(groupReadiness, volumeStatus) {
  const score = {};
  Object.keys(groupReadiness).forEach(group => {
    let s = groupReadiness[group];
    if (volumeStatus[group] === "undertrained") s += 0.3;
    if (volumeStatus[group] === "overtrained") s -= 0.5;
    score[group] = s;
  });
  return score;
}

export function getTopGroups(groupReadiness) {
  return Object.entries(groupReadiness)
    .sort((a, b) => b[1] - a[1])
    .map(([g]) => g);
}

export function recommendWorkout(groupReadiness, volumeStatus) {
  const priority = computePriority(groupReadiness, volumeStatus);
  const ranked = Object.entries(priority)
    .sort((a, b) => b[1] - a[1])
    .map(([g]) => g);

  if (ranked.length < 2) return null;

  const primary = ranked[0];
  const secondary = ranked[1];

  let intensity = "moderate";
  if (groupReadiness[primary] > 0.8 && groupReadiness[secondary] > 0.8) {
    intensity = "heavy";
  } else if (groupReadiness[primary] < 0.5) {
    intensity = "light / deload";
  }

  return {
    type: `${primary.toUpperCase()} + ${secondary.toUpperCase()}`,
    primary,
    secondary,
    intensity
  };
}

export function smartOverload(readinessVal) {
  if (readinessVal < 0.5) return "deload";
  if (readinessVal > 0.8) return "increase_weight";
  return "maintain";
}
