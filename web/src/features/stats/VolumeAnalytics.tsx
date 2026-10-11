import { useState } from "react";
import { buildVolumeAnalytics } from "../../domain/analytics";
import { analyticsWindows, granularVolume, radarAxes, weeklyConsistency, type AnalyticsRange } from "../../domain/analytics-windows";
import { fineMuscleContribution } from "../../domain/engine";
import { validWorkingSet } from "../../domain/tracking";
import { Button, Sheet } from "../../ui/components";
import { displayWeight, formatNumber, navigate, useApp } from "../../ui/context";
import { shareCard } from "../../ui/shareCard";

function MuscleRadar({ current, prior }: { current: Record<string, number>; prior: Record<string, number> }) {
  const max = Math.max(1, ...Object.values(current), ...Object.values(prior));
  const point = (index: number, radius: number) => { const angle = index * Math.PI / 3 - Math.PI / 2; return [160 + Math.cos(angle) * radius, 160 + Math.sin(angle) * radius]; };
  const polygon = (values: Record<string, number>) => radarAxes.map((axis, index) => point(index, (values[axis] ?? 0) / max * 100).join(",")).join(" ");
  return <figure><svg viewBox="0 0 320 320" role="img" aria-label="Effective muscle sets radar: current and previous windows" style={{ width: "100%", maxHeight: 360 }}>
    {[.25, .5, .75, 1].map(scale => <polygon key={scale} points={radarAxes.map((_, index) => point(index, 100 * scale).join(",")).join(" ")} fill="none" stroke="currentColor" opacity=".12" />)}
    <polygon points={polygon(prior)} fill="none" stroke="currentColor" strokeDasharray="5 4" opacity=".6" />
    <polygon points={polygon(current)} fill="var(--accent)" fillOpacity=".2" stroke="var(--accent)" strokeWidth="2" />
    {radarAxes.map((axis, index) => { const [x,y] = point(index, 130); return <text key={axis} x={x} y={y} textAnchor="middle" dominantBaseline="middle" fill="currentColor" fontSize="12">{axis}</text>; })}
  </svg><figcaption>Accent: current · dashed: previous · scale {formatNumber(max)} effective sets</figcaption></figure>;
}
export function VolumeAnalytics() {
  const { data, run } = useApp();
  const [range, setRange] = useState<AnalyticsRange>("30D"), [drill, setDrill] = useState<string>();
  const windows = analyticsWindows(data.workouts, range, data.plans.find(plan => plan.id === data.profile.activePlanId));
  const result = buildVolumeAnalytics(windows.current, Infinity), previous = buildVolumeAnalytics(windows.previous, Infinity);
  const granular = granularVolume(windows.current), priorGranular = granularVolume(windows.previous);
  const delta = previous.totalVolumeKg > 0 ? (result.totalVolumeKg / previous.totalVolumeKg - 1) * 100 : undefined;
  const trend = delta === undefined ? "No prior data" : delta > 3 ? "Progressing" : delta < -3 ? "Regressing" : "Stable";
  const push = granular.radar.Chest + granular.radar.Shoulders, pull = granular.radar.Back, legs = granular.radar.Legs;
  const movementTotal = push + pull + legs;
  result.movementBalance = movementTotal ? { Push: Math.round(push / movementTotal * 100), Pull: Math.round(pull / movementTotal * 100), Legs: Math.round(legs / movementTotal * 100) } : { Push: 0, Pull: 0, Legs: 0 };
  const consistency = weeklyConsistency(windows.current, windows.cutoff);
  const maxWeek = Math.max(1, ...result.weeklyVolume.map(row => row.volumeKg));
  const exercises = [...new Map(data.workouts.filter(w => w.status === "completed").flatMap(w => w.exercises).map(e => [e.exerciseId || e.name, e])).values()].sort((a,b) => a.name.localeCompare(b.name));
  const muscleRows = windows.current.flatMap(w => w.exercises.flatMap(e => { const fraction = fineMuscleContribution(e)[drill ?? ""] ?? 0; const sets = e.loggedSets.filter(s => validWorkingSet(e,s)).length * fraction; return sets > 0 ? [{ workout: w, exercise: e, sets }] : []; }));
  return <><header className="native-screen-title"><span className="eyebrow">Muscle analytics</span><h1>Volume analytics</h1><Button variant="ghost" onClick={() => void run(() => shareCard(range + " training", [result.sessions + " sessions · " + result.workingSets + " working sets", "External volume: " + formatNumber(displayWeight(result.totalVolumeKg, data.profile.unit)) + " " + data.profile.unit, ...radarAxes.map(axis => axis + ": " + formatNumber(granular.radar[axis]) + " effective sets")], "ironlog-volume.png"), "Analytics share card ready")}>Share analytics</Button></header>
    <div className="segmented" aria-label="Analytics range">{(["WEEK", "7D", "30D", "PROGRAM"] as const).map(label => <button aria-pressed={range === label} key={label} onClick={() => setRange(label)}>{label}</button>)}</div>
    <section className="card analytics-summary"><span className="eyebrow">IronLog volume analytics</span><p>{result.sessions ? result.workingSets + " working sets across " + result.sessions + " sessions." : "Log training to unlock your volume interpretation."}</p><div className="stats-metrics"><div><span>Sessions</span><strong>{result.sessions}</strong></div><div><span>Sets</span><strong>{result.workingSets}</strong></div><div><span>Muscles</span><strong>{Object.keys(granular.fine).length}</strong></div><div><span>Volume ({data.profile.unit})</span><strong>{formatNumber(displayWeight(result.totalVolumeKg, data.profile.unit))}</strong></div></div></section>
    <div className="two-col"><section className="card"><span className="eyebrow">Volume trend</span><h2>{trend}</h2><p>{delta === undefined ? "No prior data" : (delta >= 0 ? "+" : "") + formatNumber(delta) + "% vs prior"}</p></section><section className="card"><span className="eyebrow">Workout consistency</span><h2>{result.sessions} sessions</h2><p>Previous window: {previous.sessions}</p></section></div>
    <section className="card"><h2>Muscle balance</h2><MuscleRadar current={granular.radar} prior={priorGranular.radar} /><table><thead><tr><th>Muscle group</th><th>Current</th><th>Previous</th></tr></thead><tbody>{radarAxes.map(axis => <tr key={axis}><th>{axis}</th><td>{formatNumber(granular.radar[axis])}</td><td>{formatNumber(priorGranular.radar[axis])}</td></tr>)}</tbody></table></section>
    <section className="card"><h2>Volume trend (weeks)</h2><div className="weekly-bars">{result.weeklyVolume.map(row => <div key={row.week}><i style={{ height: Math.max(4, row.volumeKg / maxWeek * 100) + "%" }} title={formatNumber(displayWeight(row.volumeKg, data.profile.unit)) + " " + data.profile.unit} /><small>{new Date(row.week).toLocaleDateString(undefined, { month: "short", day: "numeric" })}</small></div>)}</div></section>
    <section className="card"><h2>Weekly consistency</h2>{consistency.map(row => <div className="list-row" key={row.week}><span>{row.week}</span><strong>{row.sessions} / {data.profile.weeklyGoal} sessions</strong></div>)}</section>
    <section className="card"><h2>Effective sets per muscle</h2>{Object.entries(granular.fine).filter(([,sets]) => sets >= .05).sort((a,b) => b[1] - a[1]).map(([muscle,sets]) => <button className="list-row" key={muscle} onClick={() => setDrill(muscle)}><span>{muscle.replace(/([A-Z])/g, " $1")}</span><strong>{formatNumber(sets)} sets</strong></button>)}</section>
    <section className="card"><h2>Push / Pull / Legs balance</h2><div className="balance-bar"><i style={{ width: result.movementBalance.Push + "%" }} /><i style={{ width: result.movementBalance.Pull + "%" }} /><i style={{ width: result.movementBalance.Legs + "%" }} /></div><p>Push {result.movementBalance.Push}% · Pull {result.movementBalance.Pull}% · Legs {result.movementBalance.Legs}%</p></section>
    <section className="card"><h2>Exercise progress</h2>{exercises.map(e => <button className="list-row" key={e.exerciseId || e.name} onClick={() => navigate("exercise/" + encodeURIComponent(e.exerciseId || e.name))}><span>{e.name}</span><strong>Open</strong></button>)}{!exercises.length && <p>No completed exercise data yet.</p>}</section>
    <Button variant="secondary" onClick={() => navigate("body")}>Open body composition stats</Button>
    {drill && <Sheet title={drill.replace(/([A-Z])/g, " $1") + " contributions"} onClose={() => setDrill(undefined)}><p>Effective sets are contribution-weighted; warmups and invalid sets are excluded.</p>{muscleRows.map((row,index) => <button className="list-row" key={row.workout.id + ":" + row.exercise.id + ":" + index} onClick={() => navigate("exercise/" + encodeURIComponent(row.exercise.exerciseId || row.exercise.name))}><span>{row.exercise.name}<small>{new Date(row.workout.startedAt).toLocaleDateString()}</small></span><strong>{formatNumber(row.sets)} sets</strong></button>)}</Sheet>}
  </>;
}
