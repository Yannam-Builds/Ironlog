
const PLATES = [25, 20, 15, 10, 5, 2.5, 1.25];

export function calcPlates(targetKg, barWeight = 20) {
  const remaining = (targetKg - barWeight) / 2;
  if (remaining <= 0) return { valid: false, each: [], total: targetKg };
  const raw = [];
  let left = remaining;
  for (const p of PLATES) {
    while (left >= p) { raw.push(p); left = Math.round((left - p) * 100) / 100; }
  }
  // Group into { kg, count } for UI display
  const each = [];
  for (const p of raw) {
    const last = each[each.length - 1];
    if (last && last.kg === p) { last.count++; }
    else { each.push({ kg: p, count: 1 }); }
  }
  return { valid: true, each, total: targetKg };
}
