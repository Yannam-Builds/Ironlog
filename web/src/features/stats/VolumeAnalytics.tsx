import { useMemo, useState } from "react";
import { buildVolumeAnalytics } from "../../domain/analytics";
import { Button } from "../../ui/components";
import { displayWeight, formatNumber, navigate, useApp } from "../../ui/context";

const ranges = [["WEEK", 7], ["7D", 7], ["30D", 30], ["PROGRAM", 42]] as const;
export function VolumeAnalytics() {
  const { data } = useApp();
  const [range, setRange] = useState<(typeof ranges)[number][0]>("30D"), days = ranges.find(([label]) => label === range)![1];
  const result = useMemo(() => buildVolumeAnalytics(data.workouts, days), [data.workouts, days]);
  const maxWeek = Math.max(1, ...result.weeklyVolume.map((row) => row.volumeKg));
  const exercises = [...new Map(data.workouts.filter((workout) => workout.status === "completed").flatMap((workout) => workout.exercises).map((exercise) => [exercise.exerciseId || exercise.name, exercise])).values()].sort((a, b) => a.name.localeCompare(b.name));
  return <><header className="native-screen-title"><span className="eyebrow">Muscle analytics</span><h1>Volume analytics</h1></header>
    <div className="segmented" aria-label="Analytics range">{ranges.map(([label]) => <button aria-pressed={range === label} key={label} onClick={() => setRange(label)}>{label}</button>)}</div>
    <section className="card analytics-summary"><span className="eyebrow">IronLog volume analytics</span><p>{result.sessions ? `${result.workingSets} working sets across ${result.sessions} sessions.` : "Log training to unlock your volume interpretation."}</p><div className="stats-metrics"><div><span>Sessions</span><strong>{result.sessions}</strong></div><div><span>Sets</span><strong>{result.workingSets}</strong></div><div><span>Muscles</span><strong>{Object.keys(result.muscleSets).length}</strong></div><div><span>Volume</span><strong>{formatNumber(displayWeight(result.totalVolumeKg, data.profile.unit))}</strong></div></div></section>
    <div className="two-col"><section className="card"><span className="eyebrow">Volume trend</span><h2 className={`trend-${result.trend.toLowerCase().replaceAll(" ", "-")}`}>{result.trend}</h2><p>{result.deltaPct === undefined ? "No prior data" : `${result.deltaPct >= 0 ? "+" : ""}${formatNumber(result.deltaPct)}% vs prior`}</p></section><section className="card"><span className="eyebrow">Workout consistency</span><h2>{result.sessions} sessions</h2><p>Previous window: {data.workouts.filter((workout) => workout.status === "completed" && workout.startedAt < Date.now() - days * 86400000 && workout.startedAt >= Date.now() - days * 2 * 86400000).length}</p></section></div>
    <section className="card"><h2>Volume trend (weeks)</h2><div className="weekly-bars">{result.weeklyVolume.map((row) => <div key={row.week}><i style={{ height: `${Math.max(4, row.volumeKg / maxWeek * 100)}%` }} /><small>{new Date(row.week).toLocaleDateString(undefined, { month: "short", day: "numeric" })}</small></div>)}</div></section>
    <section className="card"><h2>Effective sets per muscle</h2>{Object.entries(result.muscleSets).sort((a, b) => b[1] - a[1]).map(([muscle, sets]) => <div className="muscle-bar" key={muscle}><span>{muscle}</span><i style={{ "--bar": `${Math.min(100, sets / 25 * 100)}%` } as React.CSSProperties} /><strong>{sets}</strong></div>)}</section>
    <section className="card"><h2>Push / Pull / Legs balance</h2><div className="balance-bar"><i style={{ width: `${result.movementBalance.Push}%` }} /><i style={{ width: `${result.movementBalance.Pull}%` }} /><i style={{ width: `${result.movementBalance.Legs}%` }} /></div><p>Push {result.movementBalance.Push}% · Pull {result.movementBalance.Pull}% · Legs {result.movementBalance.Legs}%</p></section>
    <section className="card"><h2>Exercise progress</h2>{exercises.map((exercise) => <button className="list-row" key={exercise.exerciseId || exercise.name} onClick={() => navigate(`exercise/${encodeURIComponent(exercise.exerciseId || exercise.name)}`)}><span>{exercise.name}</span><strong>Open</strong></button>)}{!exercises.length && <p>No completed exercise data yet.</p>}</section>
    <Button variant="secondary" onClick={() => navigate("body")}>Open body composition stats</Button>
  </>;
}
