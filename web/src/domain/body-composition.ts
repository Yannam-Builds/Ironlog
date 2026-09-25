import type { Measurement } from "./types";

export const measurementTimestamp = (date: string) => {
  const value = new Date(`${date}T12:00:00`).getTime();
  return Number.isFinite(value) ? value : NaN;
};
const ascending = (rows: Measurement[]) => rows.filter((row) => Number.isFinite(measurementTimestamp(row.date))).slice().sort((a, b) => measurementTimestamp(a.date) - measurementTimestamp(b.date));

export function movingAverage(rows: Measurement[], windowDays = 7) {
  const sorted = ascending(rows);
  return sorted.map((row, index) => {
    const timestamp = measurementTimestamp(row.date), cutoff = timestamp - windowDays * 86400000;
    const window = sorted.slice(0, index + 1).filter((candidate) => measurementTimestamp(candidate.date) >= cutoff);
    return { date: row.date, value: row.value, average: window.reduce((sum, candidate) => sum + candidate.value, 0) / window.length };
  });
}

export function filterMeasurements(rows: Measurement[], days?: number, now = Date.now()) {
  const cutoff = days === undefined ? -Infinity : now - days * 86400000;
  return ascending(rows).filter((row) => { const at = measurementTimestamp(row.date); return at <= now && at >= cutoff; });
}

export function measurementDelta(rows: Measurement[]) {
  const sorted = ascending(rows);
  return sorted.length > 1 ? sorted.at(-1)!.value - sorted[0].value : undefined;
}

export function bodyWeightSummary(rows: Measurement[], goalWeightKg?: number, now = Date.now()) {
  const sorted = ascending(rows.filter((row) => row.type === "bodyweight" && row.value > 0));
  const current = sorted.at(-1), previous = sorted.at(-2);
  const changeAt = (days: number) => { if (!current) return undefined; const cutoff = now - days * 86400000; const baseline = sorted.filter((row) => measurementTimestamp(row.date) <= cutoff).at(-1); return baseline ? current.value - baseline.value : undefined; };
  return {
    currentKg: current?.value,
    previousChangeKg: current && previous ? current.value - previous.value : undefined,
    weeklyChangeKg: changeAt(7), monthlyChangeKg: changeAt(30),
    totalChangeKg: current && sorted[0] ? current.value - sorted[0].value : undefined,
    toGoalKg: current && goalWeightKg !== undefined ? goalWeightKg - current.value : undefined,
  };
}
