export function localDateKey(at: number = Date.now()): string {
  const d = new Date(at);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
export function parseHistoryDate(raw: string): number | undefined {
  const s = raw.trim();
  if (!/^\d{4}-\d{2}-\d{2}(?:$|[T ])/.test(s)) return undefined;
  const year = Number(s.slice(0, 4));
  const month = Number(s.slice(5, 7));
  const day = Number(s.slice(8, 10));
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const monthDays = [
    31,
    leap ? 29 : 28,
    31,
    30,
    31,
    30,
    31,
    31,
    30,
    31,
    30,
    31,
  ];
  if (month < 1 || month > 12 || day < 1 || day > monthDays[month - 1])
    return undefined;
  const value = Date.parse(
    s.length === 10 ? `${s}T00:00:00` : s.replace(" ", "T"),
  );
  return Number.isFinite(value) ? value : undefined;
}
export function isoWeekKey(at: number): string {
  const local = new Date(at);
  const d = new Date(
    Date.UTC(local.getFullYear(), local.getMonth(), local.getDate()),
  );
  d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
  const y = d.getUTCFullYear();
  const week = Math.ceil(
    ((d.getTime() - Date.UTC(y, 0, 1)) / 86400000 + 1) / 7,
  );
  return `${y}-W${String(week).padStart(2, "0")}`;
}
export function previousDay(at: number, days = 1): number {
  const d = new Date(at);
  d.setDate(d.getDate() - days);
  return d.getTime();
}
