import { useMemo, useState } from "react";
import { bodyWeightSummary, filterMeasurements, measurementDelta, movingAverage } from "../../domain/body-composition";
import type { Measurement } from "../../domain/types";
import { deleteMeasurement, newId, saveMeasurement, saveMeasurements, saveProfile } from "../../data/store";
import { Button, Field, IconButton, Sheet } from "../../ui/components";
import { canonicalWeight, displayWeight, formatNumber, navigate, useApp } from "../../ui/context";
import { localDateKey } from "../../domain/dates";

const rangeOptions = [["30D", 30], ["90D", 90], ["1Y", 365], ["All", undefined]] as const;
const fields = [{ key: "chest", label: "Chest", unit: "cm" }, { key: "waist", label: "Waist", unit: "cm" }, { key: "hips", label: "Hips", unit: "cm" }, { key: "shoulders", label: "Shoulders", unit: "cm" }, { key: "neck", label: "Neck", unit: "cm" }, { key: "arms", label: "Arms", unit: "cm" }, { key: "thighs", label: "Thighs", unit: "cm" }, { key: "calves", label: "Calves", unit: "cm" }, { key: "bodyFat", label: "Body Fat", unit: "%" }];

function Series({ rows, average, goal }: { rows: Measurement[]; average?: boolean; goal?: number }) {
  const points = average ? movingAverage(rows).map((row) => ({ value: row.average })) : rows.map((row) => ({ value: row.value }));
  const values = points.map((point) => point.value).concat(goal === undefined ? [] : [goal]), min = Math.min(...values, 0), max = Math.max(...values, 1), span = max - min || 1;
  const polyline = points.map((point, index) => `${points.length === 1 ? 150 : index * 300 / (points.length - 1)},${105 - (point.value - min) / span * 90}`).join(" ");
  const goalY = goal === undefined ? undefined : 105 - (goal - min) / span * 90;
  return <svg className="body-series" viewBox="0 0 300 120" role="img" aria-label={`${average ? "Moving average" : "Measurement"} trend with ${rows.length} entries`}>{goalY !== undefined && <line className="goal-line" x1="0" x2="300" y1={goalY} y2={goalY} />}<polyline points={polyline} /></svg>;
}
const signed = (value: number | undefined, unit: string, convert = false) => value === undefined ? "—" : `${value > 0 ? "+" : ""}${formatNumber(convert ? displayWeight(value, unit) : value)} ${unit}`;

export function BodyWeight() {
  const { data, run, busy } = useApp();
  const [range, setRange] = useState<(typeof rangeOptions)[number][0]>("30D"), [date, setDate] = useState(localDateKey(Date.now())), [value, setValue] = useState(""), [goal, setGoal] = useState(data.profile.goalWeightKg ? String(displayWeight(data.profile.goalWeightKg, data.profile.unit)) : ""), [height, setHeight] = useState(String(data.profile.heightCm));
  const weights = data.measurements.filter((row) => row.type === "bodyweight"), rows = filterMeasurements(weights, rangeOptions.find(([label]) => label === range)?.[1]), summary = bodyWeightSummary(weights, data.profile.goalWeightKg);
  const bmi = summary.currentKg && data.profile.heightCm ? summary.currentKg / (data.profile.heightCm / 100) ** 2 : undefined;
  return <><header className="native-screen-title"><span className="eyebrow">Body composition</span><h1>Body weight</h1><Button variant="ghost" onClick={() => navigate("measurements")}>Measurements</Button></header>
    <section className="card"><h2>Log body weight</h2><div className="two-col"><Field label="Date"><input type="date" max={localDateKey(Date.now())} value={date} onChange={(event) => setDate(event.target.value)} /></Field><Field label={data.profile.unit}><input type="number" min="0.1" step="0.1" value={value} onChange={(event) => setValue(event.target.value)} /></Field></div><Button disabled={busy || Number(value) <= 0} onClick={() => void run(() => saveMeasurement({ id: newId(), date, type: "bodyweight", value: canonicalWeight(Number(value), data.profile.unit), unit: "kg" }), "Body weight saved").then((ok) => { if (ok) setValue(""); })}>Log weight</Button></section>
    <div className="chip-row">{rangeOptions.map(([label]) => <button aria-pressed={range === label} key={label} onClick={() => setRange(label)}>{label}</button>)}</div><Series rows={rows} average goal={data.profile.goalWeightKg} />
    <div className="body-summary-grid">{[["Current", summary.currentKg === undefined ? "—" : `${displayWeight(summary.currentKg, data.profile.unit)} ${data.profile.unit}`], ["Vs previous", signed(summary.previousChangeKg, data.profile.unit, true)], ["Weekly trend", signed(summary.weeklyChangeKg, data.profile.unit, true)], ["This month", signed(summary.monthlyChangeKg, data.profile.unit, true)], ["Total change", signed(summary.totalChangeKg, data.profile.unit, true)], ["To goal", signed(summary.toGoalKg, data.profile.unit, true)]].map(([label, shown]) => <div key={label}><span>{label}</span><strong>{shown}</strong></div>)}</div>
    <section className="card"><h2>Goal & BMI</h2><div className="two-col"><Field label={`Goal (${data.profile.unit})`}><input type="number" min="0.1" step="0.1" value={goal} onChange={(event) => setGoal(event.target.value)} /></Field><Field label="Height (cm)"><input type="number" min="100" max="250" value={height} onChange={(event) => setHeight(event.target.value)} /></Field></div><p>BMI <strong>{bmi?.toFixed(1) ?? "—"}</strong> <span className="muted">· a broad population measure, not a body-composition assessment.</span></p><Button disabled={busy || (goal !== "" && Number(goal) <= 0) || Number(height) < 100 || Number(height) > 250} onClick={() => void run(() => saveProfile({ goalWeightKg: goal ? canonicalWeight(Number(goal), data.profile.unit) : undefined, heightCm: Number(height) }), "Body goals saved")}>Save goals</Button></section>
    <h2>History</h2>{[...weights].sort((a, b) => b.date.localeCompare(a.date)).map((row) => <div className="list-row" key={row.id}><span><strong>{displayWeight(row.value, data.profile.unit)} {data.profile.unit}</strong><small>{row.date}</small></span><IconButton name="trash" label={`Delete weight on ${row.date}`} onClick={() => run(() => deleteMeasurement(row.id), "Weight deleted")} /></div>)}
  </>;
}

function TrendSheet({ field, rows, onClose }: { field: (typeof fields)[number]; rows: Measurement[]; onClose: () => void }) {
  const { data, run } = useApp();
  const [range, setRange] = useState<(typeof rangeOptions)[number][0]>("30D");
  const [goal, setGoal] = useState(String(data.profile.measurementGoals?.[field.key] ?? ""));
  const shown = filterMeasurements(rows, rangeOptions.find(([label]) => label === range)?.[1]);
  const nextGoals = goal
    ? { ...data.profile.measurementGoals, [field.key]: Number(goal) }
    : Object.fromEntries(Object.entries(data.profile.measurementGoals ?? {}).filter(([key]) => key !== field.key));
  return <Sheet title={`${field.label} trend`} onClose={onClose}>
    <div className="chip-row">{rangeOptions.map(([label]) => <button aria-pressed={range === label} key={label} onClick={() => setRange(label)}>{label}</button>)}</div>
    {shown.length >= 2 ? <Series rows={shown} goal={goal ? Number(goal) : undefined} /> : <p>Not enough data for selected range.</p>}
    <Field label={`Goal (${field.unit})`}><input type="number" min="0.1" step="0.1" value={goal} onChange={(event) => setGoal(event.target.value)} /></Field>
    <Button disabled={goal !== "" && Number(goal) <= 0} onClick={() => run(() => saveProfile({ measurementGoals: nextGoals }), "Measurement goal saved")}>Save goal</Button>
  </Sheet>;
}

export function BodyMeasurements() {
  const { data, run, busy } = useApp(); const [adding, setAdding] = useState(false), [selected, setSelected] = useState<(typeof fields)[number]>(), [date, setDate] = useState(localDateKey(Date.now())), [values, setValues] = useState<Record<string, string>>({});
  const byType = useMemo(() => Object.fromEntries(fields.map((field) => [field.key, data.measurements.filter((row) => row.type.toLowerCase() === field.key.toLowerCase()).sort((a, b) => a.date.localeCompare(b.date))])), [data.measurements]);
  const share = async () => { const text = fields.map((field) => { const row = byType[field.key].at(-1); return row ? `${field.label}: ${row.value} ${field.unit}` : undefined; }).filter(Boolean).join("\n"); if (navigator.share) await navigator.share({ title: "IronLog measurements", text }); else await navigator.clipboard.writeText(text); };
  return <><header className="native-screen-title"><span className="eyebrow">Body composition</span><h1>Measurements</h1><div className="inline-actions"><Button variant="ghost" onClick={() => void share()}>Share progress</Button><Button onClick={() => setAdding(true)}>Add measurement</Button></div></header>
    <div className="measurement-grid">{fields.map((field) => { const rows = byType[field.key], latest = rows.at(-1), delta = measurementDelta(rows), goal = data.profile.measurementGoals?.[field.key]; return <button className="card measurement-card" key={field.key} onClick={() => setSelected(field)}><span className="eyebrow">{field.label}</span><strong>{latest ? `${formatNumber(latest.value)} ${field.unit}` : "—"}</strong><span>{delta === undefined ? "No trend yet" : signed(delta, field.unit)}{goal ? ` · Goal ${goal}` : ""}</span>{rows.length >= 2 && <Series rows={rows.slice(-8)} />}</button>; })}</div>
    {adding && <Sheet title="Add measurement" onClose={() => setAdding(false)}><Field label="Date"><input type="date" max={localDateKey(Date.now())} value={date} onChange={(event) => setDate(event.target.value)} /></Field>{fields.map((field) => <Field label={`${field.label} (${field.unit})`} key={field.key}><input type="number" min="0.1" step="0.1" value={values[field.key] ?? ""} onChange={(event) => setValues((current) => ({ ...current, [field.key]: event.target.value }))} /></Field>)}<Button disabled={busy || !Object.values(values).some((entry) => Number(entry) > 0)} onClick={() => void run(() => saveMeasurements(fields.filter((field) => Number(values[field.key]) > 0).map((field) => ({ id: newId(), date, type: field.key, value: Number(values[field.key]), unit: field.unit }))), "Measurements saved").then((ok) => { if (ok) { setValues({}); setAdding(false); } })}>Save</Button></Sheet>}
    {selected && <TrendSheet field={selected} rows={byType[selected.key]} onClose={() => setSelected(undefined)} />}
  </>;
}
