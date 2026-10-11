import { useEffect, useState } from "react";
import {
  useApp,
  navigate,
  displayWeight,
  canonicalWeight,
  formatNumber,
} from "../ui/context";
import {
  Button,
  Field,
  Icon,
  IconButton,
  Progress,
  Sheet,
  Empty,
} from "../ui/components";
import {
  deleteWorkout,
  editHistory,
  saveMeasurement,
  deleteMeasurement,
  saveProfile,
  savePhoto,
  deletePhoto,
  newId,
  deleteCompletedWorkouts,
  resetPersonalRecords,
} from "../data/store";
import { SetEditor } from "./Workout";
import type { LoggedSet, Photo, Workout } from "../domain/types";
import { localDateKey } from "../domain/dates";
import { setDescription, externalLoadVolume } from "../domain/tracking";
import { creditedProof, isWorkingSet, workoutDurationSeconds } from "../domain/engine";
import { CloudInsight } from "./intelligence/CloudInsight";
import { historyMetadataDraft, editHistoryMetadata } from "../domain/history-metadata";
import { shareCard } from "../ui/shareCard";
export function History() {
  const { data, run, busy } = useApp();
  const [selecting, setSelecting] = useState(false);
  const [selected, setSelected] = useState<{ id: string; revision: number }[]>([]);
  const [confirmSelection, setConfirmSelection] = useState(false);
  const [date, setDate] = useState("");
  const [query, setQuery] = useState("");
  const workouts = data.workouts.filter(
    (w) =>
      w.status === "completed" &&
      (!date || localDateKey(w.startedAt) === date) &&
      (!query || `${w.name} ${w.exercises.map((exercise) => exercise.name).join(" ")}`.toLowerCase().includes(query.toLowerCase())),
  );
  return (
    <>
      <header className="native-screen-title log-title">
        <span className="eyebrow">Workout log</span>
        <h1>History</h1>
        <p>{workouts.length} session{workouts.length === 1 ? "" : "s"}</p>
        <button className="text-button log-past" onClick={() => navigate("history/new")}>Log past workout</button>
      </header>
      <div className="history-search">
        <label>
          <Icon name="search" />
          <input aria-label="Search workouts or exercises" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search workouts or exercises…" />
        </label>
        <label className="history-filter" aria-label="Filter by date">
          <Icon name="filter" />
          <input type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </label>
      </div>
      {date && (
        <Button variant="ghost" onClick={() => setDate("")}>
          Show all workouts
        </Button>
      )}
      <div className="inline-actions"><Button variant="secondary" onClick={() => { setSelecting(!selecting); setSelected([]); }}>{selecting ? "Cancel selection" : "Select workouts"}</Button>{selecting && <><Button variant="secondary" onClick={() => setSelected(workouts.map(w => ({ id: w.id, revision: w.revision })))}>Select visible workouts</Button><Button variant="danger" disabled={!selected.length || busy} onClick={() => setConfirmSelection(true)}>Delete selected ({selected.length})</Button></>}</div>
      <button className="list-row progress-photos-row" onClick={() => navigate("photos")}>
        <Icon name="camera" /><strong>Progress photos</strong><Icon name="next" />
      </button>
      {!workouts.length && (
        <div className="native-empty-state history-empty">
          <Icon name="log" size={42} />
          <h2>No workouts yet</h2>
          <p>Start your first session to see your history here.</p>
          <Button onClick={() => navigate("home")}>Go to today</Button>
        </div>
      )}
      {workouts.map((w) => (
        <button
          className="card history-row"
          key={w.id}
          aria-pressed={selecting ? selected.some(row => row.id === w.id) : undefined}
          onClick={() => selecting ? setSelected(rows => rows.some(row => row.id === w.id) ? rows.filter(row => row.id !== w.id) : [...rows, { id: w.id, revision: w.revision }]) : navigate(`history/${w.id}`)}
        >
          <time>
            <strong>{new Date(w.startedAt).getDate()}</strong>
            {new Date(w.startedAt).toLocaleString(undefined, {
              month: "short",
            })}
          </time>
          <div>
            <h2>{w.name}</h2>
            <p>
              {w.exercises.length} exercises ·{" "}
              {Math.round(workoutDurationSeconds(w) / 60)} min
            </p>
            {w.imported && <small>Imported workout</small>}
          </div>
          <Icon name="next" />
        </button>
      ))}
      {confirmSelection && <Sheet title={`Delete ${selected.length} completed workouts?`} onClose={() => setConfirmSelection(false)}><p>The selected history and sets will be removed. Training estimates recalculate; earned badges remain. Active workouts are preserved.</p><Button variant="danger" disabled={busy} onClick={() => void run(() => deleteCompletedWorkouts(selected), "Selected workouts deleted").then(ok => { if (ok) { setConfirmSelection(false); setSelected([]); setSelecting(false); } })}>Delete selected workouts</Button><Button variant="secondary" onClick={() => setConfirmSelection(false)}>Keep workouts</Button></Sheet>}
    </>
  );
}
export function HistoryDetail({ id }: { id: string }) {
  const { data, run, busy } = useApp();
  const w = data.workouts.find((w) => w.id === id);
  const [editing, setEditing] = useState<{
    exerciseId: string;
    set: LoggedSet;
    revision: number;
  }>();
  const [confirm, setConfirm] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [metadata, setMetadata] = useState(() => w ? historyMetadataDraft(w) : undefined);
  const [editBaseline, setEditBaseline] = useState<Workout>();
  useEffect(() => { setRenaming(false); setEditing(undefined); setConfirm(false); }, [id]);
  if (!w)
    return (
      <Empty title="Workout not found">
        <Button onClick={() => navigate("log")}>Back to log</Button>
      </Empty>
    );
  return (
    <>
      <div className="page-title">
        <h1>{w.name}</h1>
        <IconButton
          name="edit"
          label="Edit workout name and notes"
          onClick={() => { setEditBaseline(structuredClone(w)); setMetadata(historyMetadataDraft(w)); setRenaming(true); }}
        />
      </div>
      <p>
        {new Date(w.completedAt ?? w.startedAt).toLocaleString()} ·{" "}
        {Math.round(workoutDurationSeconds(w) / 60)} min
      </p>
      {w.notes && <p>{w.notes}</p>}
      <Button variant="secondary" onClick={() => void run(() => shareCard(w.name, [new Date(w.startedAt).toLocaleDateString(), `${Math.round(workoutDurationSeconds(w) / 60)} minutes · ${w.exercises.length} exercises`, ...w.exercises.slice(0, 8).map(e => `${e.name}: ${e.loggedSets.filter(s => isWorkingSet(e, s)).length} working sets`)], "ironlog-workout.png"), "Workout share card ready")}>Share workout</Button>
      <CloudInsight title="Workout debrief" prompt={`Summarize this completed workout in under 80 words. Give one conservative next-session suggestion. Session: ${w.name}; duration ${Math.round(workoutDurationSeconds(w) / 60)} minutes; exercises: ${JSON.stringify(w.exercises.map(e => ({ name: e.name, workingSets: e.loggedSets.filter(s => isWorkingSet(e, s)).map(s => ({ kg: s.weightKg, reps: s.reps, seconds: s.durationSeconds, rpe: s.rpe, rir: s.rir })) })))}. Do not diagnose injuries.`} />
      {w.exercises.map((e) => (
        <section className="card" key={e.id}>
          <h2>{e.name}</h2>
          {e.notes && <p className="exercise-note">{e.notes}</p>}
          {e.loggedSets.map((s, i) => (
            <div className="set-row" key={s.id}>
              <span className="set-number">
                {s.kind === "warmup" ? "W" : i + 1}
              </span>
              <div>
                <strong>
                  {setDescription(e, s, data.profile.unit, displayWeight)}
                </strong>
                {(s.rpe !== undefined || s.rir !== undefined) && (
                  <small>
                    {s.rpe !== undefined ? `RPE ${s.rpe}` : `RIR ${s.rir}`}
                  </small>
                )}
                {s.notes && <small>{s.notes}</small>}
              </div>
              <IconButton
                name="edit"
                label={`Edit set ${i + 1} of ${e.name}`}
                onClick={() => setEditing({ exerciseId: e.id, set: s, revision: w.revision })}
              />
              <IconButton
                name="trash"
                label={`Delete set ${i + 1} of ${e.name}`}
                disabled={busy}
                onClick={() =>
                  run(
                    () =>
                      editHistory(w.id, w.revision, (x) => {
                        const ex = x.exercises.find((y) => y.id === e.id)!;
                        ex.loggedSets = ex.loggedSets.filter(
                          (y) => y.id !== s.id,
                        );
                      }),
                    "History set deleted",
                  )
                }
              />
            </div>
          ))}
        </section>
      ))}
      <Button variant="danger" onClick={() => setConfirm(true)}>
        Delete workout
      </Button>
      {editing && (
        <SetEditor
          exercise={w.exercises.find(e => e.id === editing.exerciseId)!}
          set={editing.set}
          unit={data.profile.unit}
          effort={data.profile.effort}
          onClose={() => setEditing(undefined)}
          onSave={(s) =>
            run(
              () =>
                editHistory(w.id, editing.revision, (x) => {
                  const ex = x.exercises.find(
                    (y) => y.id === editing.exerciseId,
                  )!;
                  const idx = ex.loggedSets.findIndex((y) => y.id === s.id);
                  if (idx < 0) throw Error("Set not found");
                  ex.loggedSets[idx] = s;
                }),
              "History updated",
            ).then((ok) => {
              if (ok) setEditing(undefined);
            })
          }
        />
      )}
      {renaming && metadata && editBaseline && (
        <Sheet title="Edit workout" onClose={() => setRenaming(false)}>
          <Field label="Workout name">
            <input value={metadata.name} onChange={(e) => setMetadata({ ...metadata, name: e.target.value })} />
          </Field>
          <Field label="Workout notes">
            <textarea
              value={metadata.notes}
              onChange={(e) => setMetadata({ ...metadata, notes: e.target.value })}
            />
          </Field>
          <div className="two-col"><Field label="Workout date"><input type="date" value={metadata.date} onChange={e => setMetadata({ ...metadata, date: e.target.value })} /></Field><Field label="Workout local time"><input type="time" value={metadata.time} onChange={e => setMetadata({ ...metadata, time: e.target.value })} /></Field></div>
          <Field label="Clock occurrence"><select value={metadata.fold} onChange={e => setMetadata({ ...metadata, fold: e.target.value as "earlier" | "later" })}><option value="earlier">First occurrence</option><option value="later">Second occurrence (repeated DST time only)</option></select></Field>
          <Field label="Workout duration minutes"><input type="number" min="0" max="1440" step="0.01" value={metadata.durationMinutes} onChange={e => setMetadata({ ...metadata, durationMinutes: Number(e.target.value) })} /></Field>
          <Field label="Workout rating"><select value={metadata.rating ?? ""} onChange={e => setMetadata({ ...metadata, rating: e.target.value ? Number(e.target.value) : undefined })}><option value="">No rating</option>{[1,2,3,4,5].map(rating => <option key={rating} value={rating}>{rating} / 5</option>)}</select></Field>
          <Button
            disabled={busy || !metadata.name.trim()}
            onClick={() =>
              run(
                () =>
                  editHistory(w.id, editBaseline.revision, (x) => {
                    Object.assign(x, editHistoryMetadata(editBaseline, metadata));
                  }),
                "Workout updated",
              ).then((ok) => {
                if (ok) setRenaming(false);
              })
            }
          >
            Save changes
          </Button>
        </Sheet>
      )}
      {confirm && (
        <Sheet
          title="Delete this workout permanently?"
          onClose={() => setConfirm(false)}
        >
          <p>
            Its sets will be removed. XP and training estimates recalculate;
            previously earned badges remain.
          </p>
          <Button
            variant="danger"
            disabled={busy}
            onClick={() =>
              run(async () => {
                await deleteWorkout(id);
                setConfirm(false);
                navigate("log");
              })
            }
          >
            Delete workout
          </Button>
        </Sheet>
      )}
    </>
  );
}
export function Stats() {
  const { data, derived: d, run, busy } = useApp();
  const [chartRange, setChartRange] = useState("14D");
  const [resetPr, setResetPr] = useState(false);
  const completed = data.workouts.filter((workout) => workout.status === "completed");
  const totalSets = completed.reduce((total, workout) => total + workout.exercises.reduce(
    (count, exercise) => count + exercise.loggedSets.filter((set) => isWorkingSet(exercise, set)).length,
    0,
  ), 0);
  const averageMinutes = completed.length
    ? Math.round(completed.slice(0, 10).reduce((total, workout) => total + workoutDurationSeconds(workout), 0) / Math.min(10, completed.length) / 60)
    : 0;
  const activeDays = new Set(completed.filter((workout) => creditedProof(workout)).map((workout) => localDateKey(workout.startedAt)));
  const counts = new Map<string, number>();
  completed.filter(w => w.startedAt <= Date.now()).forEach(w => { const key = localDateKey(w.startedAt); counts.set(key, (counts.get(key) ?? 0) + 1); });
  const chartDays = parseInt(chartRange) || 14;
  const frequency = chartRange === "All" ? [...counts].sort(([a],[b]) => a.localeCompare(b)).map(([date,sessions]) => ({ at: new Date(`${date}T00:00:00`).getTime(), sessions, active: activeDays.has(date) })) : Array.from({ length: chartDays }, (_, index) => {
    const day = new Date(); day.setHours(0,0,0,0); day.setDate(day.getDate() - (chartDays - 1 - index));
    const at = day.getTime();
    return { at, active: activeDays.has(localDateKey(at)), sessions: counts.get(localDateKey(at)) ?? 0 };
  });
  const maxSessions = Math.max(1, ...frequency.map(day => day.sessions));
  const chartPoints = frequency.map((day,index) => `${frequency.length === 1 ? 160 : index / (frequency.length - 1) * 320},${110 - day.sessions / maxSessions * 90}`).join(" ");
  return (
    <>
      <header className="native-screen-title stats-title">
        <span className="eyebrow">Analytics</span>
        <h1>Stats</h1>
        <p>{completed.length} sessions · {d.streak}-day streak</p>
        <Button variant="ghost" onClick={() => void run(() => shareCard("Training stats", [`${completed.length} completed sessions`, `${d.streak}-day streak · ${totalSets} working sets`, `${averageMinutes} minutes average session`, `${d.xp} XP · ${d.grade} grade`], "ironlog-stats.png"), "Stats share card ready")}>Share stats</Button>
      </header>
      <div className="stats-destinations">
        <button onClick={() => navigate("calendar")}><Icon name="calendar" /><strong>Calendar</strong></button>
        <button onClick={() => navigate("analytics")}><Icon name="stats" /><strong>Volume</strong></button>
        <button onClick={() => navigate("body")}><Icon name="body" /><strong>Body</strong></button>
      </div>
      <div className="stats-metrics">
        <div><span>Sessions</span><strong>{completed.length}</strong></div>
        <div><span>Streak</span><strong>{d.streak}</strong></div>
        <div><span>Sets</span><strong>{totalSets}</strong></div>
        <div><span>Avg Min</span><strong>{averageMinutes}</strong></div>
      </div>
      <button className="status-window-card" onClick={() => navigate("ledger")}>
        <span className="eyebrow">Status window</span>
        <div><strong>{d.grade} grade - Level {d.level}</strong><b>Open</b></div>
        <p>Ledger Initiate · {completed.length} logged sessions</p>
        <Progress value={d.levelProgress * 100} label="Ledger level progress" />
        <div><span>{d.xp} / {d.nextLevelXp} XP</span><b>{d.weeklyStreak}w streak</b></div>
      </button>
      <span className="eyebrow performance-label">Performance stats</span>
      <div className="performance-tabs">
        <button onClick={() => navigate("recovery")}>Recovery</button>
        <button className="active" onClick={() => navigate("intelligence")}>✦ Coach</button>
        <button onClick={() => navigate("analytics")}>Progress</button>
      </div>
      <section className="card frequency-card">
        <h2>Session frequency</h2>
        <div className="segmented" aria-label="Frequency range">{["14D", "30D", "90D", "All"].map(range => <button key={range} aria-pressed={chartRange === range} onClick={() => setChartRange(range)}>{range}</button>)}</div>
        <svg viewBox="0 0 320 130" role="img" aria-label={`${chartRange} session frequency, maximum ${maxSessions} per day`} style={{ width: "100%" }}><line x1="0" y1="110" x2="320" y2="110" stroke="currentColor" opacity=".2" /><polyline points={chartPoints} fill="none" stroke="var(--accent)" strokeWidth="2" />{frequency.filter((_,index) => index === 0 || index === frequency.length - 1 || index === Math.floor(frequency.length / 2)).map(day => <text key={day.at} x={frequency.length === 1 ? 160 : frequency.indexOf(day) / (frequency.length - 1) * 320} y="128" textAnchor={day === frequency[0] ? "start" : day === frequency.at(-1) ? "end" : "middle"} fontSize="10" fill="currentColor">{new Date(day.at).toLocaleDateString(undefined, { day: "numeric", month: "short" })}</text>)}</svg>
      </section>
      <h2>Personal records</h2>
      <Button variant="ghost" onClick={() => setResetPr(true)}>Reset personal records</Button>
      {resetPr && <Sheet title="Reset personal records?" onClose={() => setResetPr(false)}><p>Workout history remains. Future sets establish new personal-record baselines.</p><Button disabled={busy} variant="danger" onClick={() => void run(() => resetPersonalRecords(), "Personal-record baselines reset").then(ok => { if (ok) setResetPr(false); })}>Reset PRs</Button><Button variant="secondary" onClick={() => setResetPr(false)}>Keep current records</Button></Sheet>}
      <CloudInsight title="Training summary" prompt={`Summarize training progress in under 100 words with one conservative next step. Goal: ${data.profile.goal}. Completed sessions: ${completed.length}. Last ten session durations (minutes): ${completed.slice(0, 10).map(w => Math.round(workoutDurationSeconds(w) / 60)).join(", ")}. Weekly goal: ${data.profile.weeklyGoal}. Current streak: ${d.streak}. Recent personal records: ${JSON.stringify(d.prs.slice(0, 10).map(pr => ({ exercise: pr.name, kg: pr.weightKg })))}. Do not diagnose injuries.`} />
      <p className="muted">
        Estimated one-rep max from retained working sets. Warmups excluded.
      </p>
      {d.prs.map((pr) => (
        <button className="list-row" key={pr.exerciseId} onClick={() => navigate(`exercise/${encodeURIComponent(pr.exerciseId)}`)}>
          <div>
            <strong>{pr.name}</strong>
            <small>
              {displayWeight(pr.weightKg, data.profile.unit)}{" "}
              {data.profile.unit} × {pr.reps}
            </small>
          </div>
          <strong>
            {displayWeight(pr.oneRmKg, data.profile.unit)} {data.profile.unit}
          </strong>
        </button>
      ))}
      {!d.prs.length && (
        <p>Log weighted working sets to build your record book.</p>
      )}
    </>
  );
}
export function Analytics() {
  const { data } = useApp();
  const [selected, setSelected] = useState("");
  const names = [
    ...new Set(
      data.workouts
        .filter((w) => w.status === "completed")
        .flatMap((w) => w.exercises.map((e) => e.name)),
    ),
  ].sort();
  const rows = data.workouts
    .filter((w) => w.status === "completed")
    .flatMap((w) =>
      w.exercises
        .filter((e) => !selected || e.name === selected)
        .map((e) => ({
          id: `${w.id}-${e.id}`,
          date: w.startedAt,
          name: e.name,
          volume: e.loggedSets
            .filter((s) => s.kind !== "warmup")
            .reduce((n, s) => n + externalLoadVolume(e, s), 0),
          sets: e.loggedSets.filter((s) => s.kind !== "warmup").length,
        })),
    )
    .sort((a, b) => b.date - a.date);
  const max = Math.max(1, ...rows.map((r) => r.volume));
  return (
    <>
      <h1>Volume & progress</h1>
      <Field label="Exercise">
        <select value={selected} onChange={(e) => setSelected(e.target.value)}>
          <option value="">All exercises</option>
          {names.map((x) => (
            <option key={x}>{x}</option>
          ))}
        </select>
      </Field>
      <p className="muted">
        External load × reps. Duration and bodyweight-only work are not
        represented as lifting volume.
      </p>
      {rows.map((r) => (
        <div className="analytics-row" key={r.id}>
          <div className="row">
            <strong>{r.name}</strong>
            <small>{new Date(r.date).toLocaleDateString()}</small>
          </div>
          <div
            className="volume-bar"
            style={{ width: `${Math.max(1, (r.volume / max) * 100)}%` }}
          />
          <p>
            {displayWeight(r.volume, data.profile.unit)} {data.profile.unit} ·{" "}
            {r.sets} working sets
          </p>
        </div>
      ))}
      {!rows.length && <p>Your completed sessions will appear here.</p>}
    </>
  );
}
export function Body() {
  const { data, run, busy } = useApp();
  const [type, setType] = useState("bodyweight");
  const [date, setDate] = useState(localDateKey(Date.now()));
  const [value, setValue] = useState("");
  const [height, setHeight] = useState(String(data.profile.heightCm));
  const unit =
    type === "bodyweight" ? data.profile.unit : type === "bodyfat" ? "%" : "cm";
  const rows = data.measurements
    .filter((x) => x.type === type)
    .sort((a, b) => b.date.localeCompare(a.date));
  const weights = data.measurements
    .filter((x) => x.type === "bodyweight")
    .sort((a, b) => b.date.localeCompare(a.date));
  const latest = weights[0]?.value;
  const bmi =
    latest && data.profile.heightCm
      ? latest / (data.profile.heightCm / 100) ** 2
      : undefined;
  const delta = (days: number) => {
    const past = rows.find(
      (r) => r.date <= localDateKey(Date.now() - days * 86400000),
    );
    return past && rows.length ? rows[0].value - past.value : undefined;
  };
  const fmt = (n: number | undefined) =>
    n === undefined
      ? "—"
      : `${n > 0 ? "+" : ""}${formatNumber(type === "bodyweight" ? displayWeight(n, unit) : n)} ${unit}`;
  return (
    <>
      <h1>Body & measurements</h1>
      <Field label="Measurement">
        <select value={type} onChange={(e) => setType(e.target.value)}>
          {[
            "bodyweight",
            "bodyfat",
            "waist",
            "chest",
            "hips",
            "arms",
            "thighs",
          ].map((x) => (
            <option key={x}>{x}</option>
          ))}
        </select>
      </Field>
      <section className="card">
        <h2>Log a measurement</h2>
        <Field label="Date">
          <input
            type="date"
            max={localDateKey(Date.now())}
            value={date}
            onChange={(e) => setDate(e.target.value)}
          />
        </Field>
        <div className="workout-inputs">
          <Field label={unit}>
            <input
              type="number"
              min="0.1"
              step="0.1"
              inputMode="decimal"
              value={value}
              onChange={(e) => setValue(e.target.value)}
            />
          </Field>
          <Button
            disabled={busy || Number(value) <= 0}
            onClick={() =>
              run(
                () =>
                  saveMeasurement({
                    id: newId(),
                    date,
                    type,
                    value:
                      type === "bodyweight"
                        ? canonicalWeight(Number(value), unit)
                        : Number(value),
                    unit: type === "bodyweight" ? "kg" : unit,
                  }),
                "Measurement saved",
              ).then((ok) => {
                if (ok) setValue("");
              })
            }
          >
            Log
          </Button>
        </div>
      </section>
      <div className="stat-pair">
        <div>
          <span>This week</span>
          <strong>{fmt(delta(7))}</strong>
        </div>
        <div>
          <span>This month</span>
          <strong>{fmt(delta(30))}</strong>
        </div>
      </div>
      <section className="card bmi-card">
        <h2>
          BMI <span className="muted">{bmi?.toFixed(1) ?? "—"}</span>
        </h2>
        <p className="muted">
          A broad population measure, not a body-composition assessment.
        </p>
        <div className="workout-inputs">
          <Field label="Height (cm)">
            <input
              type="number"
              min="100"
              max="250"
              value={height}
              onChange={(e) => setHeight(e.target.value)}
            />
          </Field>
          <Button
            disabled={busy || Number(height) < 100 || Number(height) > 250}
            onClick={() =>
              run(
                () => saveProfile({ heightCm: Number(height) }),
                "Height saved",
              )
            }
          >
            Set
          </Button>
        </div>
      </section>
      <h2>History</h2>
      {rows.map((r) => (
        <div className="list-row" key={r.id}>
          <div>
            <strong>
              {type === "bodyweight" ? displayWeight(r.value, unit) : r.value}{" "}
              {unit}
            </strong>
            <small>{r.date}</small>
          </div>
          <IconButton
            name="trash"
            label={`Delete measurement on ${r.date}`}
            onClick={() => run(() => deleteMeasurement(r.id))}
          />
        </div>
      ))}
    </>
  );
}
function LocalPhoto({ photo }: { photo: Photo }) {
  const [url, setUrl] = useState("");
  useEffect(() => {
    const u = URL.createObjectURL(photo.blob);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [photo.blob]);
  return (
    <figure>
      {url && <img src={url} alt={`Progress photo from ${photo.date}`} />}
      <figcaption>
        {photo.date}
        {photo.notes && ` · ${photo.notes}`}
      </figcaption>
    </figure>
  );
}
export function Photos() {
  const { data, run, busy } = useApp();
  const photos = [...data.photos].sort((a, b) => a.date.localeCompare(b.date));
  const [date, setDate] = useState(localDateKey(Date.now()));
  const [before, setBefore] = useState("");
  const [after, setAfter] = useState("");
  return (
    <>
      <h1>Progress photos</h1>
      <p>
        Stored only in this browser. Complete browser backups include these
        image files; Android exports do not.
      </p>
      <Field label="Photo date">
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
        />
      </Field>
      <Field label="Add a photo">
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          disabled={busy}
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file)
              void run(async () => {
                if (file.size > 15_000_000)
                  throw Error("Choose an image smaller than 15 MB");
                if (
                  !["image/jpeg", "image/png", "image/webp"].includes(file.type)
                )
                  throw Error("Choose JPEG, PNG or WebP");
                await savePhoto({ id: newId(), date, notes: "", blob: file });
              }, "Photo saved");
          }}
        />
      </Field>
      {photos.length >= 2 && (
        <>
          <h2>Compare</h2>
          <div className="two-col">
            <Field label="Before">
              <select
                value={before}
                onChange={(e) => setBefore(e.target.value)}
              >
                <option value="">Choose photo</option>
                {photos.map((p) => (
                  <option value={p.id} key={p.id}>
                    {p.date}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="After">
              <select value={after} onChange={(e) => setAfter(e.target.value)}>
                <option value="">Choose photo</option>
                {photos.map((p) => (
                  <option value={p.id} key={p.id}>
                    {p.date}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <div className="photo-compare">
            {[before, after].map((id, i) => {
              const p = photos.find((p) => p.id === id);
              return p ? <LocalPhoto key={i} photo={p} /> : null;
            })}
          </div>
        </>
      )}
      {photos.map((p) => (
        <section key={p.id} className="photo-entry">
          <LocalPhoto photo={p} />
          <Button
            variant="danger"
            onClick={() => run(() => deletePhoto(p.id), "Photo deleted")}
          >
            Delete photo from {p.date}
          </Button>
        </section>
      ))}
    </>
  );
}
