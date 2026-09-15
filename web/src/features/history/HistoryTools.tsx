import { useMemo, useState } from "react";
import { saveHistoricalWorkout, newId } from "../../data/store";
import { buildHistoricalWorkout, localTimeIsRepeated, type FoldChoice } from "../../domain/historical-workout";
import type { LoggedSet, SessionExercise } from "../../domain/types";
import { Button, Field, Icon, Empty } from "../../ui/components";
import { navigate, useApp } from "../../ui/context";
import { ExercisePicker } from "../Plans";
import { SetEditor } from "../Workout";
import { localDateKey } from "../../domain/dates";

const localInputDate = (timestamp: number) => { const d = new Date(timestamp); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; };
const blankSet = (): LoggedSet => ({ id: newId(), weightKg: 0, reps: 0, durationSeconds: 0, distanceKm: 0, kind: "normal", notes: "", loggedAt: Date.now() });

export function HistoricalWorkoutEditor({ initialDate }: { initialDate?: string }) {
  const { data, run, busy } = useApp();
  const yesterday = Date.now() - 86400000;
  const [name, setName] = useState("Past workout"), [date, setDate] = useState(initialDate ?? localInputDate(yesterday)), [time, setTime] = useState("18:00");
  const [duration, setDuration] = useState(60), [rating, setRating] = useState<number | undefined>(), [notes, setNotes] = useState(""), [fold, setFold] = useState<FoldChoice>("earlier");
  const [detailed, setDetailed] = useState(true);
  const [exercises, setExercises] = useState<SessionExercise[]>([]), [picker, setPicker] = useState(false), [editing, setEditing] = useState<{ exerciseIndex: number; setIndex: number }>();
  const conflict = data.workouts.some((workout) => workout.status === "completed" && localDateKey(workout.startedAt) === date);
  const [conflictReviewed, setConflictReviewed] = useState(false);
  let repeated = false; try { repeated = localTimeIsRepeated(date, time); } catch { /* validation appears on save */ }
  return <>
    <header className="native-screen-title"><span className="eyebrow">Workout log</span><h1>Log past workout</h1></header>
    <p className="muted">Save a completed workout to history. No live timer or completion celebration.</p>
    <div className="segmented" role="radiogroup" aria-label="Workout detail level"><button role="radio" aria-checked={!detailed} className={!detailed ? "selected" : ""} onClick={() => setDetailed(false)}>Quick summary</button><button role="radio" aria-checked={detailed} className={detailed ? "selected" : ""} onClick={() => setDetailed(true)}>Detailed sets</button></div>
    <Field label="Workout name"><input value={name} onChange={(event) => setName(event.target.value)} /></Field>
    <div className="two-col"><Field label="Date"><input type="date" value={date} onChange={(event) => { setDate(event.target.value); setConflictReviewed(false); }} /></Field><Field label="Local time"><input type="time" value={time} onChange={(event) => setTime(event.target.value)} /></Field></div>
    {repeated && <Field label="Repeated clock time"><select value={fold} onChange={(event) => setFold(event.target.value as FoldChoice)}><option value="earlier">First occurrence</option><option value="later">Second occurrence</option></select></Field>}
    <div className="two-col"><Field label="Duration minutes"><input type="number" min="1" max="1440" value={duration} onChange={(event) => setDuration(Number(event.target.value))} /></Field><Field label="Rating (optional)"><select value={rating ?? ""} onChange={(event) => setRating(event.target.value ? Number(event.target.value) : undefined)}><option value="">No rating</option>{[1,2,3,4,5].map((value) => <option key={value} value={value}>{value} / 5</option>)}</select></Field></div>
    <Field label="Workout notes"><textarea value={notes} onChange={(event) => setNotes(event.target.value)} /></Field>
    {detailed && <section className="card"><div className="section-title"><h2>Exercises</h2><Button variant="secondary" onClick={() => setPicker(true)}>Add exercise</Button></div>
      {!exercises.length && <p className="muted">Add exercises and record only sets that were actually completed.</p>}
      {exercises.map((exercise, exerciseIndex) => <div className="plan-exercise" key={exercise.id}><div className="section-title"><strong>{exercise.name}</strong><Button variant="ghost" onClick={() => setExercises((current) => current.filter((_, index) => index !== exerciseIndex))}>Remove</Button></div><Field label="Exercise notes"><input value={exercise.notes} onChange={(event) => setExercises((current) => current.map((item, index) => index === exerciseIndex ? { ...item, notes: event.target.value } : item))} /></Field>
        {exercise.loggedSets.map((set, setIndex) => <button className="list-row" key={set.id} onClick={() => setEditing({ exerciseIndex, setIndex })}><span>Set {setIndex + 1}</span><span>{set.weightKg} kg × {set.reps}</span></button>)}
        <Button variant="ghost" onClick={() => { const set = blankSet(); setExercises((current) => current.map((item, index) => index === exerciseIndex ? { ...item, loggedSets: [...item.loggedSets, set] } : item)); setEditing({ exerciseIndex, setIndex: exercise.loggedSets.length }); }}>Add completed set</Button>
      </div>)}
    </section>}
    {conflict && <label className="check-label"><input type="checkbox" checked={conflictReviewed} onChange={(event) => setConflictReviewed(event.target.checked)} />I reviewed the existing workout on this date</label>}
    <div className="sticky-actions"><Button disabled={busy || (conflict && !conflictReviewed)} onClick={() => void run(async () => { if (detailed && (!exercises.length || exercises.some((exercise) => !exercise.loggedSets.length))) throw Error("Add an exercise and at least one completed set for each exercise, or choose Quick summary."); const workout = buildHistoricalWorkout({ name, date, time, fold, durationMinutes: duration, rating, notes, exercises: detailed ? exercises : [] }); await saveHistoricalWorkout(workout); return workout; }, "Past workout saved").then((ok) => { if (ok) navigate("log"); })}>Save past workout</Button><Button variant="secondary" onClick={() => navigate("log")}>Cancel</Button></div>
    {picker && <ExercisePicker onClose={() => setPicker(false)} onPick={(exercise) => { setExercises((current) => [...current, { ...exercise, id: newId(), exerciseId: exercise.id, sets: 0, reps: "", restSeconds: 0, notes: "", supersetGroup: "", isWarmup: false, loggedSets: [], pendingWarmups: [] }]); setPicker(false); }} />}
    {editing && (() => { const exercise = exercises[editing.exerciseIndex], set = exercise?.loggedSets[editing.setIndex]; return exercise && set ? <SetEditor exercise={exercise} set={set} unit={data.profile.unit} effort={data.profile.effort} onClose={() => setEditing(undefined)} onSave={(next) => { setExercises((current) => current.map((item, exerciseIndex) => exerciseIndex === editing.exerciseIndex ? { ...item, loggedSets: item.loggedSets.map((value, setIndex) => setIndex === editing.setIndex ? next : value) } : item)); setEditing(undefined); }} /> : null; })()}
  </>;
}

export function WorkoutCalendar() {
  const { data } = useApp();
  const [month, setMonth] = useState(() => localInputDate(Date.now()).slice(0, 7));
  const [year, monthNumber] = month.split("-").map(Number), first = new Date(year, monthNumber - 1, 1), count = new Date(year, monthNumber, 0).getDate();
  const days = useMemo(() => Array.from({ length: count }, (_, index) => index + 1), [count]);
  const byDate = new Map<string, typeof data.workouts>(); data.workouts.filter((workout) => workout.status === "completed").forEach((workout) => { const key = localDateKey(workout.startedAt); byDate.set(key, [...(byDate.get(key) ?? []), workout]); });
  return <><header className="native-screen-title"><span className="eyebrow">Workout log</span><h1>Calendar</h1></header><Field label="Month"><input type="month" value={month} onChange={(event) => setMonth(event.target.value)} /></Field>
    <div className="calendar-weekdays" aria-hidden="true">{["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((day) => <span key={day}>{day}</span>)}</div>
    <div className="workout-calendar" style={{ "--first-day": ((first.getDay() + 6) % 7) + 1 } as React.CSSProperties}>{days.map((day) => { const key = `${month}-${String(day).padStart(2, "0")}`, sessions = byDate.get(key) ?? [], future = key > localInputDate(Date.now()); return <div className={sessions.length ? "has-workout" : ""} key={key}><button className="calendar-day" disabled={future} aria-label={`Log workout on ${key}`} onClick={() => navigate(`history/new/${key}`)}><strong>{day}</strong></button>{sessions.map((workout) => <button key={workout.id} onClick={() => navigate(`history/${workout.id}`)}>{workout.name}</button>)}</div>; })}</div>
    {!data.workouts.some((workout) => workout.status === "completed") && <Empty title="No workouts yet"><p>Past and completed workouts will appear here.</p></Empty>}<Button variant="secondary" onClick={() => navigate("log")}>Back to history</Button></>;
}
