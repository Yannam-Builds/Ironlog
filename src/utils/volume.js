export function calculateSetVolume(weight, reps) {
  const w = Number(weight) || 0;
  const r = Number(reps) || 0;
  return Math.max(0, w) * Math.max(0, r);
}

