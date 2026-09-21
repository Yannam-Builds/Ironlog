import { useMemo, useState } from "react";
import { buildExerciseTrend, exerciseProgressTabs, filterExerciseTrend, type ExerciseTrendRow } from "../../domain/analytics";
import { setDescription } from "../../domain/tracking";
import { Button, Empty, Sheet } from "../../ui/components";
import { displayWeight, formatNumber, navigate, useApp } from "../../ui/context";

const ranges = [["90D", 90], ["6M", 180], ["1Y", 365], ["ALL", undefined]] as const;
const metricValue = (row: ExerciseTrendRow, metric: string) => metric === "E1RM" ? row.estimatedOneRmKg : metric === "LOAD" ? row.loadKg : metric === "REPS" ? row.meanReps : metric === "DURATION" ? row.durationSeconds : metric === "VOLUME" ? row.volumeKg : row.workingSets;

function TrendChart({ rows, metric, unit }: { rows: ExerciseTrendRow[]; metric: string; unit: string }) {
  const values = rows.map((row) => metricValue(row, metric) ?? 0), max = Math.max(1, ...values), width = 320, height = 130;
  const points = values.map((value, index) => `${values.length === 1 ? width / 2 : index * width / (values.length - 1)},${height - value / max * (height - 16) - 8}`).join(" ");
  return <figure className="trend-chart"><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${metric} trend for ${rows.length} sessions`}><line x1="0" y1={height - 8} x2={width} y2={height - 8} /><polyline points={points} />{rows.map((row, index) => { const value = values[index], x = values.length === 1 ? width / 2 : index * width / (values.length - 1), y = height - value / max * (height - 16) - 8; return <circle key={row.workoutId} cx={x} cy={y} r={row.isPr && metric === "E1RM" ? 5 : 3}><title>{new Date(row.date).toLocaleDateString()}: {formatNumber(value)} {unit}{row.isPr && metric === "E1RM" ? " · PR" : ""}</title></circle>; })}</svg></figure>;
}

export function ExerciseProgress({ identity }: { identity: string }) {
  const { data } = useApp();
  const allRows = useMemo(
    () => buildExerciseTrend(data.workouts, identity, data.profile.prResetAt),
    [data.workouts, data.profile.prResetAt, identity],
  );
  const [range, setRange] = useState<(typeof ranges)[number][0]>("ALL"), [activeMetric, setActiveMetric] = useState("E1RM"), [showTm, setShowTm] = useState(false);
  const rows = filterExerciseTrend(allRows, ranges.find(([label]) => label === range)?.[1]);
  const tabs = exerciseProgressTabs(rows), metric = tabs.includes(activeMetric) ? activeMetric : tabs[0], name = allRows[0]?.name ?? identity;
  const metricRows = rows.filter((row) => metric === "E1RM" ? row.estimatedOneRmKg !== undefined : metric === "LOAD" ? row.loadAvailable : metric === "REPS" ? row.durationSeconds === undefined : metric === "DURATION" ? row.durationSeconds !== undefined : metric === "VOLUME" ? row.volumeAvailable : true);
  const values = metricRows.map((row) => metricValue(row, metric) ?? 0), stat = metric === "REPS" ? (values.reduce((sum, value) => sum + value, 0) / Math.max(1, values.length)) : metric === "DURATION" || metric === "VOLUME" || metric === "CONSISTENCY" ? values.reduce((sum, value) => sum + value, 0) : Math.max(0, ...values);
  const weightMetric = ["E1RM", "LOAD", "VOLUME"].includes(metric), unit = weightMetric ? data.profile.unit : metric === "DURATION" ? "s" : "";
  const shownStat = weightMetric ? displayWeight(stat, data.profile.unit) : stat;
  const latestEstimate = [...rows].reverse().find((row) => row.estimatedOneRmKg !== undefined)?.estimatedOneRmKg;
  const exportCsv = () => { const lines = [`Date,Load (${data.profile.unit}),Mean reps,External volume (${data.profile.unit}),e1RM (${data.profile.unit}),Duration (s),Distance (km)`].concat(rows.map((row) => [new Date(row.date).toISOString(), row.loadAvailable ? displayWeight(row.loadKg, data.profile.unit) : "", row.durationSeconds === undefined ? row.meanReps : "", row.volumeAvailable ? displayWeight(row.volumeKg, data.profile.unit) : "", row.estimatedOneRmKg === undefined ? "" : displayWeight(row.estimatedOneRmKg, data.profile.unit), row.durationSeconds ?? "", row.distanceKm || ""].join(","))); const link = document.createElement("a"); link.href = URL.createObjectURL(new Blob([lines.join("\n")], { type: "text/csv" })); link.download = `ironlog-${name.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.csv`; link.click(); URL.revokeObjectURL(link.href); };
  if (!allRows.length) return <Empty title="No data for this exercise yet"><Button onClick={() => navigate("analytics")}>Back to analytics</Button></Empty>;
  return <><header className="native-screen-title"><span className="eyebrow">Exercise progress</span><h1>{name}</h1><div className="inline-actions">{latestEstimate !== undefined && <Button variant="ghost" onClick={() => setShowTm(true)}>Calc TM</Button>}<Button variant="ghost" onClick={exportCsv}>Export CSV</Button></div></header>
    <div className="metric-tabs" role="tablist">{tabs.map((tab) => <button role="tab" aria-selected={metric === tab} key={tab} onClick={() => setActiveMetric(tab)}>{tab}</button>)}</div>
    {metric === "HISTORY" ? <div>{[...rows].reverse().map((row) => { const workout = data.workouts.find((item) => item.id === row.workoutId), exercise = workout?.exercises.find((item) => item.exerciseId === identity || item.name === name); return <button className="list-row" key={row.workoutId} onClick={() => navigate(`history/${row.workoutId}`)}><span><strong>{new Date(row.date).toLocaleDateString()}</strong><small>{exercise?.loggedSets.filter((set) => set.kind !== "warmup").map((set) => setDescription(exercise, set, data.profile.unit, displayWeight)).join(", ")}</small></span><b>{row.workingSets} sets</b></button>; })}</div> : <><section className="card metric-hero"><span className="eyebrow">{metric === "E1RM" ? "Best est. 1RM" : metric === "LOAD" ? "Top load" : metric === "REPS" ? "Avg reps / set" : metric === "DURATION" ? "Session duration" : metric === "VOLUME" ? "Session volume" : "Sessions"}</span><strong>{formatNumber(shownStat)} {unit}</strong><small>{metricRows.length} sessions in range</small></section><TrendChart rows={metricRows} metric={metric} unit={unit} /><div className="chip-row" aria-label="Date range">{ranges.map(([label]) => <button aria-pressed={range === label} key={label} onClick={() => setRange(label)}>{label}</button>)}</div></>}
    {showTm && latestEstimate !== undefined && <Sheet title="Training Max Calculator" onClose={() => setShowTm(false)}><p>Latest e1RM: {displayWeight(latestEstimate, data.profile.unit)} {data.profile.unit}</p>{[85, 90, 95].map((percent) => <div className="list-row" key={percent}><span>{percent}% TM</span><strong>{displayWeight(latestEstimate * percent / 100, data.profile.unit)} {data.profile.unit}</strong></div>)}</Sheet>}
  </>;
}
