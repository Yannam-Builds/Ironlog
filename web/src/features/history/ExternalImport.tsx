import { useState } from "react";
import { previewExternalImport, type ExternalPreview, type ExternalSource } from "../../domain/external-import";
import { appendImportedWorkouts } from "../../data/store";
import { Button, Field, Sheet } from "../../ui/components";
import { useApp } from "../../ui/context";
export function ExternalImport({ onClose }: { onClose: () => void }) {
  const { data, run, busy } = useApp();
  const [source, setSource] = useState<ExternalSource>("strong"), [unit,setUnit] = useState<"kg" | "lb">("kg"), [text,setText] = useState("");
  const [preview,setPreview] = useState<ExternalPreview>(), [reviewed,setReviewed] = useState(false);
  const resetPreview = () => { setPreview(undefined); setReviewed(false); };
  return <Sheet title="Import workout history" onClose={onClose}>
    <p>Preview Strong or Hevy CSV, or OpenWeight JSON, then append completed workouts. Your existing plans, photos and active workout are preserved. Reimported identical sessions are skipped.</p>
    <Field label="Import source"><select value={source} onChange={e => { setSource(e.target.value as ExternalSource); resetPreview(); }}><option value="strong">Strong CSV</option><option value="hevy">Hevy CSV</option><option value="openweight">OpenWeight JSON</option></select></Field>
    <Field label="Source weight unit"><select value={unit} onChange={e => { setUnit(e.target.value as "kg" | "lb"); resetPreview(); }}><option value="kg">Kilograms</option><option value="lb">Pounds</option></select></Field><p className="muted">A CSV column explicitly labeled kg or lb overrides this choice.</p>
    <Field label="History export file"><input type="file" accept=".csv,.json,text/csv,application/json" disabled={busy} onChange={e => { const file = e.target.files?.[0]; resetPreview(); if (file) void run(async () => { if (file.size > 20_000_000) throw Error("Import exceeds 20 MB."); setText(await file.text()); }); }} /></Field>
    <Field label="Export contents"><textarea rows={6} value={text} onChange={e => { setText(e.target.value); resetPreview(); }} /></Field>
    <Button disabled={busy || !text.trim()} variant="secondary" onClick={() => void run(async () => { setPreview(previewExternalImport(text, source, data.exercises, unit)); setReviewed(false); })}>Preview import</Button>
    {preview && <><h3>{preview.workouts.length} workouts · {preview.workouts.reduce((sum,w) => sum + w.exercises.reduce((count,e) => count + e.loggedSets.length, 0),0)} sets</h3>
      {preview.warnings.map((warning,index) => <p className="notice" key={index}>{warning}</p>)}
      {preview.workouts.slice(0, 20).map(w => <details key={w.id}><summary>{w.name} · {new Date(w.startedAt).toLocaleString()}</summary>{w.exercises.map(e => <p key={e.id}>{e.name} · {e.loggedSets.length} sets{!e.exerciseId ? " · unmatched reference" : ""}</p>)}</details>)}
      {preview.workouts.length > 20 && <p>{preview.workouts.length - 20} additional workouts will also be appended.</p>}
      <label className="check-label"><input type="checkbox" checked={reviewed} onChange={e => setReviewed(e.target.checked)} />I reviewed the units, counts, dates and import warnings.</label>
      <Button disabled={busy || !reviewed} onClick={() => void run(async () => { const result = await appendImportedWorkouts(preview.workouts); if (!result.imported) throw Error("These workouts are already present; nothing was changed."); }, "Workout history imported").then(ok => { if (ok) onClose(); })}>Append reviewed workouts</Button>
    </>}
  </Sheet>;
}
