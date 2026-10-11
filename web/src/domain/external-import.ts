import { workoutSchema } from "../data/schema";
import { parseHistoryDate } from "./dates";
import { resolveExerciseCandidate } from "./exercise-resolution";
import { legacyId } from "./legacy-import";
import type { Exercise, LoggedSet, SessionExercise, Workout } from "./types";
export type ExternalSource = "strong" | "hevy" | "openweight";
export interface ExternalPreview { workouts: Workout[]; warnings: string[]; skipped: number; unresolved: number }
export function parseCsv(text: string): string[][] {
  if (text.length > 20_000_000) throw Error("CSV exceeds 20 MB.");
  const records: string[][] = []; let row: string[] = [], field = "", quoted = false;
  for (let index = 0; index < text.length; index++) {
    const char = text[index];
    if (char === '"') { if (quoted && text[index + 1] === '"') { field += '"'; index++; } else quoted = !quoted; }
    else if (char === "," && !quoted) { row.push(field); field = ""; }
    else if ((char === "\n" || char === "\r") && !quoted) { if (char === "\r" && text[index + 1] === "\n") index++; row.push(field); if (row.some(value => value.trim())) records.push(row); row = []; field = ""; }
    else field += char;
  }
  if (quoted) throw Error("CSV contains an unfinished quoted field.");
  row.push(field); if (row.some(value => value.trim())) records.push(row);
  if (records.length > 100_000) throw Error("CSV contains too many rows.");
  return records;
}
const norm = (text: string) => text.replace(/^\uFEFF/, "").trim().toLowerCase().replace(/[^a-z0-9]/g, "");
const numeric = (value: unknown, optional = false) => {
  if ((value === "" || value == null) && optional) return 0;
  const number = Number(value); if (value == null || value === "" || !Number.isFinite(number) || number < 0) throw Error("Missing or invalid performance value."); return number;
};
const date = (value: unknown) => {
  const raw = String(value).trim();
  const number = typeof value === "number" ? value : /^\d{4}-\d{2}-\d{2}(?:$|[T ])/.test(raw) ? parseHistoryDate(raw) : Date.parse(raw);
  if (number === undefined || !Number.isFinite(number) || number < 0) throw Error("Invalid workout date."); return number;
};
function sessionExercise(name: string, sets: LoggedSet[], catalog: Exercise[], key: string): SessionExercise {
  const match = resolveExerciseCandidate(name, catalog); const linked = match.status === "matched" ? match.matched : undefined;
  return { ...linked, id: legacyId("externalExercise", key), exerciseId: linked?.id ?? "", name: linked?.name ?? name, muscle: linked?.muscle ?? "", equipment: linked?.equipment ?? "", tracking: linked?.tracking || "weight_reps", sets: Math.max(1, sets.length), reps: "", restSeconds: 90, notes: "", supersetGroup: "", isWarmup: false, pendingWarmups: [], loggedSets: sets };
}
export function previewExternalImport(text: string, source: ExternalSource, catalog: Exercise[], inputUnit: "kg" | "lb" = "kg"): ExternalPreview {
  if (text.length > 20_000_000) throw Error("Import exceeds 20 MB.");
  let skipped = 0; const warnings: string[] = [], workouts: Workout[] = [];
  if (source === "openweight") {
    const payload = JSON.parse(text);
    if (!payload || !Array.isArray(payload.workouts)) throw Error("OpenWeight JSON must contain a workouts array.");
    if (payload.workouts.length > 10_000) throw Error("Too many imported workouts.");
    for (const row of payload.workouts) {
      try {
        if (!row || !Array.isArray(row.exercises)) throw Error("Missing exercises.");
        const startedAt = date(row.startedAt ?? row.date); const durationSeconds = numeric(row.durationSeconds ?? 1800);
        const key = JSON.stringify(row); const id = legacyId("openweight", key);
        const exercises = row.exercises.flatMap((exercise: { name?: string; sets?: unknown[] }, exerciseIndex: number) => {
          if (!exercise?.name?.trim() || !Array.isArray(exercise.sets)) { skipped++; return []; }
          const sets = exercise.sets.flatMap((value,index) => {
            try { const set = value as Record<string, unknown>; return [{ id: legacyId("externalSet", `${id}:${exerciseIndex}:${index}`), weightKg: numeric(set.weightKg ?? set.weight) / (inputUnit === "lb" && set.weightKg == null ? 2.20462262185 : 1), reps: numeric(set.reps), durationSeconds: numeric(set.durationSeconds, true), distanceKm: numeric(set.distanceKm, true), kind: set.isWarmup ? "warmup" as const : "normal" as const, notes: String(set.notes ?? ""), loggedAt: startedAt, rpe: set.rpe == null ? undefined : numeric(set.rpe) }]; }
            catch { skipped++; return []; }
          });
          return sets.length ? [sessionExercise(exercise.name, sets, catalog, `${id}:${exerciseIndex}`)] : [];
        });
        if (!exercises.length) throw Error("No valid performed sets.");
        workouts.push(workoutSchema.parse({ id, name: row.name || "OpenWeight Import", startedAt, completedAt: startedAt + durationSeconds * 1000, durationSeconds, notes: String(row.notes ?? ""), status: "completed", revision: 0, restUsed: false, imported: true, exercises }));
      } catch { skipped++; }
    }
  } else {
    const records = parseCsv(text); const header = records.shift()?.map(norm) ?? [];
    const column = (...names: string[]) => header.findIndex(value => names.some(name => value === norm(name)));
    const dateIndex = column("date", "start_time", "start time", "start"), exerciseIndex = column("exercise", "exercise name", "exercise_title", "exerciseuid"), weightIndex = column("weight", "weight_kg", "weight_lbs", "weight (kg)", "weight (lb)"), repsIndex = column("reps", "rep");
    const workoutIndex = column("workout", "workout name", "title"), endIndex = column("end_time", "end"), durationIndex = column("duration", "durationseconds"), typeIndex = column("set_type", "set type"), rpeIndex = column("rpe"), notesIndex = column("notes", "exercise_notes"), workoutNotesIndex = column("workout notes", "description"), secondsIndex = column("seconds", "duration_seconds"), distanceIndex = column("distance", "distance_km");
    if ([dateIndex, exerciseIndex, weightIndex, repsIndex].some(index => index < 0)) throw Error("CSV headers must identify date/start time, exercise, weight and reps.");
    const unit = header[weightIndex].includes("lbs") || header[weightIndex].endsWith("lb") ? "lb" : header[weightIndex].includes("kg") ? "kg" : inputUnit;
    const groups = new Map<string, { row: string[]; at: number; name: string; sets: { name: string; set: LoggedSet }[] }>();
    records.forEach((row,index) => {
      try {
        const at = date(row[dateIndex]); const name = row[exerciseIndex]?.trim(); if (!name) throw Error("Missing exercise name.");
        const workoutName = row[workoutIndex]?.trim() || (source === "strong" ? "Strong Import" : "Hevy Import"); const key = `${at}:${workoutName}`;
        const set: LoggedSet = { id: legacyId("externalSet", `${source}:${key}:${index}:${JSON.stringify(row)}`), weightKg: numeric(row[weightIndex]) / (unit === "lb" ? 2.20462262185 : 1), reps: numeric(row[repsIndex]), durationSeconds: numeric(row[secondsIndex], true), distanceKm: numeric(row[distanceIndex], true), kind: /warm/.test(row[typeIndex] ?? "") ? "warmup" : /drop/.test(row[typeIndex] ?? "") ? "drop" : /failure/.test(row[typeIndex] ?? "") ? "failure" : "normal", notes: row[notesIndex] ?? "", loggedAt: at, rpe: row[rpeIndex] ? numeric(row[rpeIndex]) : undefined };
        if (set.rpe !== undefined && set.rpe > 10) throw Error("Invalid RPE.");
        const group = groups.get(key) ?? { row, at, name: workoutName, sets: [] }; group.sets.push({ name, set }); groups.set(key, group);
      } catch { skipped++; }
    });
    for (const [key,group] of groups) {
      const id = legacyId(source, `${key}:${JSON.stringify(group.sets.map(({ name, set: { id: _id, ...set } }) => ({ name, set })))}`); const byExercise = new Map<string, LoggedSet[]>();
      group.sets.forEach(({name,set}) => byExercise.set(name, [...(byExercise.get(name) ?? []), set]));
      let durationSeconds = 1800;
      if (group.row[endIndex]) { try { durationSeconds = Math.max(0, (date(group.row[endIndex]) - group.at) / 1000); } catch { warnings.push("An invalid end time was replaced with a 30-minute duration."); } }
      else if (/^\d+$/.test(group.row[durationIndex] ?? "")) durationSeconds = Number(group.row[durationIndex]);
      else { const duration = group.row[durationIndex] ?? ""; if (/\d+\s*[hm]/.test(duration)) { const hours = Number(/(\d+)\s*h/.exec(duration)?.[1] ?? 0), minutes = Number(/(\d+)\s*m/.exec(duration)?.[1] ?? 0); durationSeconds = hours * 3600 + minutes * 60; } }
      workouts.push(workoutSchema.parse({ id, name: group.name, startedAt: group.at, completedAt: group.at + durationSeconds * 1000, durationSeconds, notes: group.row[workoutNotesIndex] ?? "", status: "completed", revision: 0, restUsed: false, imported: true, exercises: [...byExercise].map(([name,sets]) => sessionExercise(name, sets.map((set,index) => ({ ...set, id: legacyId("externalSet", `${id}:${name}:${index}`) })), catalog, `${id}:${name}`)) }));
    }
  }
  if (!workouts.length) throw Error("No valid workouts were found. No data has been imported.");
  if (skipped) warnings.push(`${skipped} invalid row(s) or set(s) were excluded. Review the preview before importing.`);
  const unresolved = workouts.reduce((sum,w) => sum + w.exercises.filter(e => !e.exerciseId).length, 0);
  if (unresolved) warnings.push(`${unresolved} exercise references remain unmatched; their performed values are preserved.`);
  return { workouts, warnings, skipped, unresolved };
}
