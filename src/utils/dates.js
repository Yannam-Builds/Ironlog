export function nowTs() {
  return Date.now();
}

export function toIso(ts) {
  return new Date(ts).toISOString();
}

export function startOfWeek(ts = Date.now()) {
  const d = new Date(ts);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

