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
} from "../data/store";
import { SetEditor } from "./Workout";
import type { LoggedSet, Photo } from "../domain/types";
import { localDateKey } from "../domain/dates";
import { workoutDurationSeconds } from "../domain/engine";
export function History() {
  const { data } = useApp();
  const [date, setDate] = useState("");
  const workouts = data.workouts.filter(
    (w) =>
      w.status === "completed" &&
      (!date || localDateKey(w.completedAt!) === date),
  );
  return (
    <>
      <h1>Training log</h1>
      <Field label="Calendar date">
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
        />
      </Field>
      {date && (
        <Button variant="ghost" onClick={() => setDate("")}>
          Show all workouts
        </Button>
      )}
      {!workouts.length && (
        <Empty title="Your work belongs here">
          <p>Finish a workout to see your sets, notes, and progress.</p>
          <Button onClick={() => navigate("home")}>Start from Home</Button>
        </Empty>
      )}
      {workouts.map((w) => (
        <button
          className="card history-row"
          key={w.id}
          onClick={() => navigate(`history/${w.id}`)}
        >
          <time>
            <strong>{new Date(w.completedAt!).getDate()}</strong>
            {new Date(w.completedAt!).toLocaleString(undefined, {
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
    </>
  );
}
export function HistoryDetail({ id }: { id: string }) {
  const { data, run, busy } = useApp();
  const w = data.workouts.find((w) => w.id === id);
  const [editing, setEditing] = useState<{
    exerciseId: string;
    set: LoggedSet;
  }>();
  const [confirm, setConfirm] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [name, setName] = useState(w?.name ?? "");
  const [notes, setNotes] = useState(w?.notes ?? "");
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
          onClick={() => setRenaming(true)}
        />
      </div>
      <p>
        {new Date(w.completedAt ?? w.startedAt).toLocaleString()} ·{" "}
        {Math.round(workoutDurationSeconds(w) / 60)} min
      </p>
      {w.notes && <p>{w.notes}</p>}
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
                  {e.tracking.startsWith("duration")
                    ? `${s.durationSeconds} seconds${s.distanceKm ? ` · ${s.distanceKm} km` : ""}`
                    : `${e.tracking === "bodyweight_reps" ? "BW" : `${displayWeight(s.weightKg, data.profile.unit)} ${data.profile.unit}`} × ${s.reps}`}
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
                onClick={() => setEditing({ exerciseId: e.id, set: s })}
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
          set={editing.set}
          unit={data.profile.unit}
          effort={data.profile.effort}
          onClose={() => setEditing(undefined)}
          onSave={(s) =>
            run(
              () =>
                editHistory(w.id, w.revision, (x) => {
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
      {renaming && (
        <Sheet title="Edit workout" onClose={() => setRenaming(false)}>
          <Field label="Workout name">
            <input value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Workout notes">
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </Field>
          <Button
            disabled={busy || !name.trim()}
            onClick={() =>
              run(
                () =>
                  editHistory(w.id, w.revision, (x) => {
                    x.name = name;
                    x.notes = notes;
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
  const { data, derived: d } = useApp();
  return (
    <>
      <h1>Stats</h1>
      <div className="stat-pair">
        <div>
          <span>Completed workouts</span>
          <strong>
            {data.workouts.filter((w) => w.status === "completed").length}
          </strong>
        </div>
        <div>
          <span>Working volume</span>
          <strong>
            {formatNumber(displayWeight(d.volumeKg, data.profile.unit))}
            <small> {data.profile.unit}</small>
          </strong>
        </div>
      </div>
      <div className="link-list">
        {[
          ["body", "Bodyweight & measurements"],
          ["photos", "Progress photos"],
          ["analytics", "Volume & exercise progress"],
          ["ledger", "Iron Ledger"],
          ["intelligence", "Training intelligence"],
        ].map(([route, name]) => (
          <button
            className="list-row"
            key={route}
            onClick={() => navigate(route)}
          >
            <strong>{name}</strong>
            <Icon name="next" />
          </button>
        ))}
      </div>
      <h2>Personal records</h2>
      <p className="muted">
        Estimated one-rep max from retained working sets. Warmups excluded.
      </p>
      {d.prs.map((pr) => (
        <div className="list-row" key={pr.exerciseId}>
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
        </div>
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
          date: w.completedAt!,
          name: e.name,
          volume: e.loggedSets
            .filter((s) => s.kind !== "warmup")
            .reduce((n, s) => n + s.weightKg * s.reps, 0),
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
