const MS_PER_DAY = 24 * 60 * 60 * 1000;

export const BODY_WEIGHT_RANGES = ['7D', '30D', '3M', '1Y', 'All'];

const RANGE_DAYS = {
  '7D': 7,
  '30D': 30,
  '3M': 90,
  '1Y': 365,
};

function toDayKey(timestamp) {
  const d = new Date(timestamp);
  const year = d.getFullYear();
  const month = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function average(values) {
  if (!values.length) return null;
  const sum = values.reduce((total, value) => total + value, 0);
  return sum / values.length;
}

function startOfIsoWeek(timestamp) {
  const d = new Date(timestamp);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function startOfMonth(timestamp) {
  const d = new Date(timestamp);
  d.setDate(1);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function getAnchorTimestamp(entriesAsc) {
  if (!entriesAsc.length) return Date.now();
  return entriesAsc[entriesAsc.length - 1].timestamp;
}

export function normalizeBodyWeightEntries(rawEntries) {
  if (!Array.isArray(rawEntries)) return [];

  return rawEntries
    .map((entry, sourceIndex) => {
      const weight = Number.parseFloat(entry?.weight);
      const timestamp = new Date(entry?.date).getTime();
      if (!Number.isFinite(weight) || !Number.isFinite(timestamp)) return null;

      return {
        id: `${timestamp}-${sourceIndex}`,
        weight,
        timestamp,
        date: entry.date,
        sourceIndex,
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.timestamp - b.timestamp);
}

export function filterEntriesByRange(entriesAsc, range) {
  if (!entriesAsc.length || range === 'All') return entriesAsc;
  const days = RANGE_DAYS[range];
  if (!days) return entriesAsc;

  const anchor = getAnchorTimestamp(entriesAsc);
  const cutoff = anchor - days * MS_PER_DAY;
  return entriesAsc.filter((entry) => entry.timestamp >= cutoff && entry.timestamp <= anchor);
}

export function dedupeLatestEntryByDay(entriesAsc) {
  const byDay = new Map();

  entriesAsc.forEach((entry) => {
    byDay.set(toDayKey(entry.timestamp), entry);
  });

  return [...byDay.values()].sort((a, b) => a.timestamp - b.timestamp);
}

export function downsampleSeries(points, maxPoints = 140) {
  if (points.length <= maxPoints) return points;
  const stride = (points.length - 1) / (maxPoints - 1);
  const result = [];
  for (let i = 0; i < maxPoints; i += 1) {
    const idx = Math.round(i * stride);
    result.push(points[idx]);
  }
  return result;
}

export function calculateBodyWeightSummary(allEntriesAsc, selectedEntriesAsc) {
  const latestOverall = allEntriesAsc[allEntriesAsc.length - 1] || null;
  const firstOverall = allEntriesAsc[0] || null;
  const latestSelected = selectedEntriesAsc[selectedEntriesAsc.length - 1] || latestOverall;
  const latestSelectedIndex = latestSelected
    ? allEntriesAsc.findIndex((entry) => entry.id === latestSelected.id)
    : -1;
  const previousEntry = latestSelectedIndex > 0 ? allEntriesAsc[latestSelectedIndex - 1] : null;
  const anchorTs = latestSelected?.timestamp || getAnchorTimestamp(allEntriesAsc);

  const getWindowAverage = (windowStart, windowEnd) => {
    const values = allEntriesAsc
      .filter((entry) => entry.timestamp > windowStart && entry.timestamp <= windowEnd)
      .map((entry) => entry.weight);
    return average(values);
  };

  const weekStart = startOfIsoWeek(anchorTs);
  const monthStart = startOfMonth(anchorTs);
  const weekEntries = allEntriesAsc.filter((entry) => entry.timestamp >= weekStart && entry.timestamp <= anchorTs);
  const monthEntries = allEntriesAsc.filter((entry) => entry.timestamp >= monthStart && entry.timestamp <= anchorTs);
  const recentAvg = getWindowAverage(anchorTs - 7 * MS_PER_DAY, anchorTs);
  const previousAvg = getWindowAverage(anchorTs - 14 * MS_PER_DAY, anchorTs - 7 * MS_PER_DAY);

  return {
    currentWeight: latestSelected?.weight ?? null,
    changeFromPrevious:
      latestSelected && previousEntry ? latestSelected.weight - previousEntry.weight : null,
    weeklyTrend:
      recentAvg != null && previousAvg != null ? recentAvg - previousAvg : null,
    weekChange:
      weekEntries.length >= 2
        ? weekEntries[weekEntries.length - 1].weight - weekEntries[0].weight
        : null,
    monthChange:
      monthEntries.length >= 2
        ? monthEntries[monthEntries.length - 1].weight - monthEntries[0].weight
        : null,
    totalChange:
      latestOverall && firstOverall ? latestOverall.weight - firstOverall.weight : null,
  };
}

