import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
} from "react";
import {
  useApp,
  navigate,
  canonicalWeight,
  displayWeight,
  formatNumber,
} from "../ui/context";
import {
  Button,
  Field,
  Icon,
  IconButton,
  Sheet,
  Switch,
  RollingTimerText,
  Empty,
} from "../ui/components";
import { ExercisePicker, planned } from "./Plans";
import { ExerciseNextNote } from "./ExerciseNextNote";
import { RecentPerformanceControl } from "./RecentPerformance";
import {
  mutateWorkout,
  finishWorkout,
  hasFailedMutation,
  acknowledgeFailedMutation,
  discardWorkout,
  addWarmups,
  logSet,
  swapExercise,
  newId,
  saveProfile,
  clearActiveWorkoutExerciseNotes,
} from "../data/store";
import { plateCalculation, warmupTargets } from "../domain/engine";
import { progressionSuggestion } from "../domain/engine";
import type {
  Exercise,
  LoggedSet,
  SessionExercise,
  SetKind,
  Workout as WorkoutData,
  Tracking,
} from "../domain/types";
import { isTimed, trackingDimensions, trackingOptions, setDescription } from "../domain/tracking";
import { recentPerformances, recentSetLabel, latestPerformedSet } from "../domain/recent-performance";
const colorFor = (weight: number) =>
  weight >= 20
    ? "#EF5454"
    : weight >= 15
      ? "#F0CB55"
      : weight >= 10
        ? "#5299ED"
        : weight >= 5
          ? "#67BE8D"
          : "#C782E7";
const setKinds: SetKind[] = ["normal", "warmup", "drop", "failure", "amrap"];
const setKindLabel: Record<SetKind, string> = {
  normal: "W", warmup: "WU", drop: "DS", failure: "F", amrap: "AMRAP",
};
const volumeComparisons = [
  [0, "a house cat"], [500, "a baby goat"], [1_000, "a large pumpkin"],
  [2_000, "a baby elephant"], [3_500, "a baby hippo"], [5_000, "a grand piano"],
  [7_500, "a polar bear"], [10_000, "a small car"], [15_000, "a T-Rex"],
  [20_000, "a rhino"], [25_000, "an orca whale"], [35_000, "an elephant"],
  [40_000, "a school bus"], [60_000, "a space shuttle"], [100_000, "a blue whale"],
] as const;
const volumeComparison = (kg: number) =>
  volumeComparisons.findLast(([threshold]) => kg >= threshold)?.[1] ?? "a house cat";
const vibrate = (pattern: number | number[]) => {
  if (typeof navigator !== "undefined" && typeof navigator.vibrate === "function") navigator.vibrate(pattern);
};
export const exerciseTutorialUrl = (exerciseName: string) =>
  `https://www.youtube.com/results?search_query=${encodeURIComponent(exerciseName)}+exercise+tutorial`;
export function PlateView({
  loadKg,
  barKg,
  platesKg,
  plateInventory,
  unit,
}: {
  loadKg: number;
  barKg: number;
  platesKg: number[];
  plateInventory?: { weightKg: number; quantity: number }[];
  unit: string;
}) {
  const result = plateCalculation(loadKg, barKg, platesKg, plateInventory);
  const plates = result.platesPerSide.flatMap((p) =>
    Array.from({ length: Math.min(p.quantity, 25) }, () => p.weightKg),
  );
  const width = Math.max(4, Math.min(15, 130 / Math.max(1, plates.length)));
  const weight = (kg: number) =>
    `${formatNumber(displayWeight(kg, unit))} ${unit}`;
  return (
    <>
      <h3>Load {weight(loadKg)}</h3>
      <div className="barbell">
        <svg
          viewBox="0 0 400 180"
          role="img"
          aria-label={`${weight(barKg)} bar, ${result.platesPerSide.map((p) => `${p.quantity} × ${weight(p.weightKg)} each side`).join(", ")}`}
        >
          <rect x="12" y="86" width="376" height="8" rx="3" fill="#AEB4BC" />
          <rect x="166" y="82" width="68" height="16" rx="3" fill="#E8EBEF" />
          {[-1, 1].flatMap((side) =>
            plates.map((kg, i) => {
              const height = 42 + Math.min(kg / 25, 1) * 95;
              const x =
                side === -1
                  ? 156 - (i + 1) * (width + 2)
                  : 244 + i * (width + 2);
              return (
                <rect
                  key={`${side}-${i}`}
                  x={x}
                  y={90 - height / 2}
                  width={width}
                  height={height}
                  rx="2"
                  fill={colorFor(kg)}
                  stroke="#141414"
                  strokeWidth="1.5"
                />
              );
            }),
          )}
        </svg>
      </div>
      <div className="list-row">
        <span>Bar</span>
        <strong>{weight(barKg)}</strong>
      </div>
      {result.platesPerSide.map((p) => (
        <div className="list-row" key={p.weightKg}>
          <span>
            <i
              className="plate-dot"
              style={{ background: colorFor(p.weightKg) }}
            />
            {weight(p.weightKg)}
          </span>
          <strong>{p.quantity} per side</strong>
        </div>
      ))}
      <p>
        Achievable: {weight(result.achievedWeightKg)}
        {!result.isValid && ` · Remainder: ${weight(result.remainderKg)}`}
      </p>
      <p className="muted">
        {plateInventory === undefined
          ? "Unlimited pairs assumed. Add physical plate quantities in Settings → Gym profiles."
          : "Uses your saved physical plates, split equally between both sides. Unpaired plates are excluded."}
      </p>
    </>
  );
}
export function SetEditor({
  exercise,
  set,
  unit,
  effort,
  onSave,
  onClose,
}: {
  exercise: SessionExercise;
  set: LoggedSet;
  unit: string;
  effort: string;
  onSave: (set: LoggedSet) => void;
  onClose: () => void;
}) {
  const [value, setValue] = useState({ ...set });
  const update = (p: Partial<LoggedSet>) => setValue({ ...value, ...p });
  const dims = trackingDimensions(exercise);
  const loadLabel = dims.mode === 'assisted_bodyweight' ? 'Assistance' : dims.mode === 'bodyweight_plus_weight_reps' ? 'Added load' : 'Weight';
  return (
    <Sheet title="Edit logged set" onClose={onClose}>
      {!dims.known && <p>Unrecognized or unrecorded tracking. Original performance values are preserved; only effort, type and notes can be edited.</p>}
      {dims.load && <Field label={`${loadLabel} (${unit})`}>
        <input
          type="number"
          inputMode="decimal"
          min="0"
          value={displayWeight(value.weightKg, unit)}
          onChange={(e) =>
            update({ weightKg: canonicalWeight(Number(e.target.value), unit) })
          }
        />
      </Field>}
      {dims.reps && <Field label="Reps">
        <input
          type="number"
          inputMode="numeric"
          min="0"
          value={value.reps}
          onChange={(e) => update({ reps: Number(e.target.value) })}
        />
      </Field>}
      {dims.duration && <Field label="Duration (seconds)">
        <input
          type="number"
          min="0"
          value={value.durationSeconds}
          onChange={(e) => update({ durationSeconds: Number(e.target.value) })}
        />
      </Field>}
      {dims.distance && <Field label="Distance (km)">
        <input
          type="number"
          min="0"
          step="0.01"
          value={value.distanceKm}
          onChange={(e) => update({ distanceKm: Number(e.target.value) })}
        />
      </Field>}
      <Field label={effort.toUpperCase()}>
        <input
          type="number"
          min="0"
          max="10"
          step="0.5"
          value={(effort === "rpe" ? value.rpe : value.rir) ?? ""}
          onChange={(e) =>
            update({
              rpe: undefined,
              rir: undefined,
              [effort]:
                e.target.value === "" ? undefined : Number(e.target.value),
            })
          }
        />
      </Field>
      <Field label="Set type">
        <select
          value={value.kind}
          onChange={(e) => update({ kind: e.target.value as SetKind, toFailure: e.target.value === 'failure' })}
        >
          {["normal", "warmup", "failure", "drop", "amrap"].map((x) => (
            <option key={x}>{x}</option>
          ))}
        </select>
      </Field>
      <Switch
        label="Taken to failure"
        checked={value.kind === 'failure' || !!value.toFailure}
        onChange={checked => update({toFailure: checked, kind: !checked && value.kind === 'failure' ? 'normal' : value.kind})}
      />
      <Field label="Notes">
        <textarea
          value={value.notes}
          onChange={(e) => update({ notes: e.target.value })}
        />
      </Field>
      <Button onClick={() => onSave(value)}>Save set</Button>
    </Sheet>
  );
}
function ExerciseCard({
  exercise: e,
  workout: w,
  index,
  isDragging,
  onDragPointerDown,
  onDragPointerMove,
  onDragPointerUp,
  onDragPointerCancel,
  onDragKeyDown,
}: {
  exercise: SessionExercise;
  workout: WorkoutData;
  index: number;
  isDragging: boolean;
  onDragPointerDown: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onDragPointerMove: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onDragPointerUp: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onDragPointerCancel: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onDragKeyDown: (event: ReactKeyboardEvent<HTMLButtonElement>) => void;
}) {
  const { data, run, busy } = useApp();
  const p = data.profile;
  const dims = trackingDimensions(e);
  const mode = dims.mode;
  const last = e.loggedSets.at(-1);
  const [weight, setWeight] = useState(
    last
      ? String(
          dims.distance
            ? last.distanceKm
            : displayWeight(last.weightKg, p.unit),
        )
      : "",
  );
  const previousUnit = useRef(p.unit);
  useLayoutEffect(() => {
    if (previousUnit.current !== p.unit && dims.load) {
      const oldUnit = previousUnit.current;
      setWeight((value) =>
        value === "" || !Number.isFinite(Number(value))
          ? value
          : String(
              displayWeight(canonicalWeight(Number(value), oldUnit), p.unit),
            ),
      );
    }
    previousUnit.current = p.unit;
  }, [p.unit, dims.load]);
  const [reps, setReps] = useState(
    String(
      (isTimed(e)
        ? last?.durationSeconds
        : last?.reps) ??
        (parseInt(e.reps) || 8),
    ),
  );
  const [effort, setEffort] = useState("");
  const [kind, setKind] = useState<SetKind>(e.isWarmup ? "warmup" : "normal");
  const [note, setNote] = useState("");
  const [menu, setMenu] = useState(false);
  const [plates, setPlates] = useState(false);
  const [editSet, setEditSet] = useState<LoggedSet>();
  const [swapping, setSwapping] = useState(false);
  const [replacement, setReplacement] = useState<Exercise>();
  const [targets, setTargets] = useState(false);
  const [superset, setSuperset] = useState(false);
  const [targetDraft, setTargetDraft] = useState({
    sets: e.sets,
    reps: e.reps,
    restSeconds: e.restSeconds,
    notes: e.notes,
    tracking: e.tracking,
  });
  const openTargets = () => {
    setTargetDraft({
      sets: e.sets,
      reps: e.reps,
      restSeconds: e.restSeconds,
      notes: e.notes,
      tracking: e.tracking,
    });
    setTargets(true);
  };
  const previous = recentPerformances(data.workouts, e).find(row => row.comparable)?.exercise;
  const suggestion = previous ? progressionSuggestion(previous, p.unit) : null;
  const mutate = (
    recipe: (ex: SessionExercise, workout: WorkoutData) => void,
    message?: string,
  ) =>
    run(
      () =>
        mutateWorkout(w.id, w.revision, (next) => {
          const ex = next.exercises.find((x) => x.id === e.id);
          if (!ex) throw Error("Exercise no longer exists");
          recipe(ex, next);
        }),
      message,
    );
  const timed = isTimed(e);
  const kg = canonicalWeight(Number(weight), p.unit);
  const log = () =>
    mutate((ex, workout) => {
      const set: LoggedSet = {
        id: newId(),
        weightKg: dims.load ? kg : 0,
        reps: timed ? 0 : Number(reps),
        durationSeconds: timed ? Number(reps) : 0,
        distanceKm: dims.distance ? Number(weight) : 0,
        kind,
        notes: note,
        loggedAt: Date.now(),
        ...(effort !== "" ? { [p.effort]: Number(effort) } : {}),
      };
      if (set.kind === "warmup") {
        const firstWork = ex.loggedSets.findIndex((s) => s.kind !== "warmup");
        ex.loggedSets.splice(
          firstWork < 0 ? ex.loggedSets.length : firstWork,
          0,
          set,
        );
      } else ex.loggedSets.push(set);
      if (ex.restSeconds > 0) {
        workout.restEndsAt = Date.now() + ex.restSeconds * 1000;
        workout.restUsed = true;
      }
    }, "Set logged").then((ok) => {
      if (ok) {
        setNote("");
        vibrate([18, 20, 30]);
      }
    });
  return (
    <section className={`card exercise-card${isDragging ? " dragging" : ""}`} data-exercise-id={e.id}>
      <div className="section-title">
        <button
          type="button"
          className="workout-drag-handle"
          aria-label={`Drag to reorder ${e.name}`}
          aria-pressed={isDragging}
          disabled={busy}
          onPointerDown={onDragPointerDown}
          onPointerMove={onDragPointerMove}
          onPointerUp={onDragPointerUp}
          onPointerCancel={onDragPointerCancel}
          onKeyDown={onDragKeyDown}
        ><Icon name="menu" size={20} /></button>
        <div className="exercise-card-title">
          <h2>{e.name}</h2>
          <p>
            {e.sets} × {e.reps} · {e.tracking.replaceAll("_", " ")}
            {e.supersetGroup && ` · Superset ${e.supersetGroup}`}
          </p>
        </div>
        <IconButton
          name="more"
          label={`Options for ${e.name}`}
          onClick={() => setMenu(true)}
        />
      </div>
      {!e.exerciseId && (
        <p className="notice">
          Not linked to the library. Review the tracking type in exercise
          options before logging.
        </p>
      )}
      {p.planExerciseNotesVisible && e.notes && <p className="exercise-note">{e.notes}</p>}
      <ExerciseNextNote key={e.exerciseId} exerciseId={e.exerciseId} />
      <RecentPerformanceControl exercise={e} dayId={w.dayId} />
      <div className="sets">
        {e.loggedSets.map((s, i) => (
          <div className="set-row" key={s.id}>
            <span className="set-index-label">SET {i + 1}</span>
            <button
              type="button"
              className={`set-kind set-kind-${s.kind}`}
              aria-label={`Set type for set ${i + 1}`}
              title="Tap to change set type"
              disabled={busy}
              onClick={() => mutate((ex) => {
                const saved = ex.loggedSets.find((row) => row.id === s.id);
                if (!saved) throw Error("Set no longer exists");
                const current = setKinds.indexOf(saved.kind);
                saved.kind = setKinds[(current + 1) % setKinds.length];
                saved.toFailure = saved.kind === "failure";
              }, "Set type updated")}
            >{setKindLabel[s.kind]}</button>
            <div>
              <strong>
                {setDescription(e, s, p.unit, displayWeight)}
              </strong>
              <small>
                {s.kind !== "normal" ? s.kind : ""}
                {s.rpe !== undefined
                  ? ` · RPE ${s.rpe}`
                  : s.rir !== undefined
                    ? ` · RIR ${s.rir}`
                    : ""}
                {s.notes ? ` · ${s.notes}` : ""}
              </small>
              <button
                type="button"
                className="effort-chip"
                onClick={() => setEditSet(s)}
                aria-label={`${p.effort.toUpperCase()} for set ${i + 1}`}
              >{p.effort.toUpperCase()} {((p.effort === "rpe" ? s.rpe : s.rir) ?? "—")}</button>
            </div>
            <div className="set-actions">
              <IconButton
                name="edit"
                label={`Edit set ${i + 1} of ${e.name}`}
                onClick={() => setEditSet(s)}
              />
              <IconButton
                name="trash"
                label={`Delete set ${i + 1} of ${e.name}`}
                disabled={busy}
                onClick={() =>
                  mutate((ex) => {
                    ex.loggedSets = ex.loggedSets.filter((x) => x.id !== s.id);
                  }, "Set deleted")
                }
              />
              {i > 0 && (
                <button
                  className="icon-button"
                  aria-label={`Move set ${i + 1} up`}
                  disabled={busy}
                  onClick={() =>
                    mutate((ex) => {
                      const idx = ex.loggedSets.findIndex((x) => x.id === s.id);
                      [ex.loggedSets[idx - 1], ex.loggedSets[idx]] = [
                        ex.loggedSets[idx],
                        ex.loggedSets[idx - 1],
                      ];
                    })
                  }
                >
                  ↑
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
      {e.pendingWarmups.length > 0 && (
        <div className="warmup-queue">
          <h3>Warmup targets · not logged</h3>
          {e.pendingWarmups.map((t) => (
            <div className="row" key={t.id}>
              <span>
                {displayWeight(t.weightKg, p.unit)} {p.unit} × {t.reps}
              </span>
              <Button
                variant="secondary"
                disabled={busy}
                onClick={() =>
                  run(() =>
                    logSet(w.id, w.revision, e.id, {
                      ...t,
                      id: t.id,
                      kind: "warmup",
                      durationSeconds: 0,
                      distanceKm: 0,
                      notes: "",
                      loggedAt: Date.now(),
                    }),
                  )
                }
              >
                Log warmup
              </Button>
              <IconButton
                name="close"
                label={`Skip ${t.weightKg} kg warmup`}
                disabled={busy}
                onClick={() =>
                  mutate((ex) => {
                    ex.pendingWarmups = ex.pendingWarmups.filter(
                      (x) => x.id !== t.id,
                    );
                  })
                }
              />
            </div>
          ))}
        </div>
      )}
      <div className="workout-inputs">
        {(dims.load || dims.distance) && (
          <Field label={dims.distance ? "Distance (km)" : mode === "assisted_bodyweight" ? `Assistance (${p.unit.toUpperCase()})` : mode === "bodyweight_plus_weight_reps" ? `Added load (${p.unit.toUpperCase()})` : p.unit.toUpperCase()}>
            <input
              type="number"
              min="0"
              step="any"
              inputMode="decimal"
              value={weight}
              onChange={(ev) => setWeight(ev.target.value)}
            />
          </Field>
        )}
        {dims.known && <Field label={timed ? "Seconds" : "Reps"}>
          <input
            type="number"
            min="1"
            step="1"
            inputMode="numeric"
            value={reps}
            onChange={(ev) => setReps(ev.target.value)}
          />
        </Field>}
        <Button
          disabled={
            busy ||
            !dims.known ||
            !Number.isFinite(Number(reps)) ||
            Number(reps) <= 0 ||
            ((dims.load || dims.distance) && (weight === '' || !Number.isFinite(Number(weight)) || Number(weight) < 0)) ||
            (dims.load && mode !== 'assisted_bodyweight' && !!e.requiresExternalLoad && kg <= 0)
          }
          onClick={log}
        >
          Log
        </Button>
      </div>
      {!dims.known && <p className="muted">Choose a supported tracking type in Targets, tracking & notes before logging. Imported values remain preserved.</p>}
      {last && (
        <Button
          variant="ghost"
          onClick={() => {
            setWeight(
              String(
                dims.distance
                  ? last.distanceKm
                  : displayWeight(last.weightKg, p.unit),
              ),
            );
            setReps(String(timed ? last.durationSeconds : last.reps));
            setEffort(String((p.effort === "rpe" ? last.rpe : last.rir) ?? ""));
            setKind(last.kind);
          }}
        >
          Copy previous set
        </Button>
      )}
      {suggestion && (
        <p className="muted">
          Last-session progression option:{" "}
          {displayWeight(suggestion.weightKg, p.unit)} {p.unit} ×{" "}
          {suggestion.reps}. Adjust for form, effort and recovery; this is not a
          prescription.
        </p>
      )}
      <details className="set-details">
        <summary>Effort, type & note</summary>
        <div className="two-col">
          <Field label={p.effort.toUpperCase()}>
            <input
              type="number"
              min="0"
              max="10"
              step="0.5"
              inputMode="decimal"
              value={effort}
              onChange={(ev) => setEffort(ev.target.value)}
            />
          </Field>
          <Field label="Set type">
            <select
              value={kind}
              onChange={(ev) => setKind(ev.target.value as SetKind)}
            >
              {["normal", "warmup", "failure", "drop", "amrap"].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </Field>
        </div>
        <Field label="Set note">
          <textarea value={note} onChange={(ev) => setNote(ev.target.value)} />
        </Field>
      </details>
      <div className="exercise-tools">
        <button
          className="text-button"
          disabled={busy || mode !== "weight_reps" || kg <= 0}
          onClick={() =>
            run(async () => {
              const queue = warmupTargets(kg, p.barKg);
              if (!queue.length)
                throw Error(
                  "Use a higher target than the bar, or log a lighter warmup manually.",
                );
              await addWarmups(w.id, w.revision, e.id, queue);
            })
          }
        >
          <Icon name="plus" size={18} />
          Insert warmups
        </button>
        <button
          className="text-button"
          onClick={() => {
            setTargetDraft({
              sets: e.sets,
              reps: e.reps,
              restSeconds: e.restSeconds,
              notes: e.notes,
              tracking: e.tracking,
            });
            setTargets(true);
          }}
        >
          <Icon name="timer" size={18} />
          {e.restSeconds}s rest
        </button>
      </div>
      {(mode !== "weight_reps" || kg <= 0) && (
        <small className="muted">
          Warmup queue needs a weighted target. Bodyweight and timed warmups can
          be logged using the warmup set type.
        </small>
      )}
      {menu && (
        <Sheet title={e.name} onClose={() => setMenu(false)}>
          <Button
            variant="secondary"
            disabled={e.loggedSets.length > 0}
            onClick={() => {
              setMenu(false);
              setSwapping(true);
            }}
          >
            Swap exercise
          </Button>
          {e.loggedSets.length > 0 && (
            <p>Remove logged sets before swapping to preserve your history.</p>
          )}
          <Button
            variant="secondary"
            onClick={() => {
              setMenu(false);
              openTargets();
            }}
          >
            Targets, tracking & notes
          </Button>
          <Button variant="secondary" onClick={() => { setMenu(false); setSuperset(true); }}>Superset group</Button>
          <a
            className="button secondary"
            href={exerciseTutorialUrl(e.name)}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => setMenu(false)}
          >
            Watch on YouTube
          </a>
          <Button
            variant="secondary"
            disabled={mode !== "weight_reps" || kg <= 0}
            onClick={() => {
              setMenu(false);
              setPlates(true);
            }}
          >
            Plate calculator
          </Button>
          {index > 0 && (
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() =>
                mutate((_, next) => {
                  [next.exercises[index - 1], next.exercises[index]] = [
                    next.exercises[index],
                    next.exercises[index - 1],
                  ];
                }).then((ok) => {
                  if (ok) setMenu(false);
                })
              }
            >
              Move exercise up
            </Button>
          )}
          <Button
            variant="danger"
            disabled={busy}
            onClick={() =>
              mutate((_, next) => {
                next.exercises = next.exercises.filter((x) => x.id !== e.id);
              }, "Exercise removed").then((ok) => {
                if (ok) setMenu(false);
              })
            }
          >
            Remove exercise and its sets
          </Button>
        </Sheet>
      )}
      {plates && (
        <Sheet title="Plate calculator" onClose={() => setPlates(false)}>
          <PlateView
            loadKg={kg}
            barKg={p.barKg}
            platesKg={p.platesKg}
            plateInventory={p.plateInventory}
            unit={p.unit}
          />
        </Sheet>
      )}
      {editSet && (
        <SetEditor
          exercise={e}
          set={editSet}
          unit={p.unit}
          effort={p.effort}
          onClose={() => setEditSet(undefined)}
          onSave={(s) =>
            mutate((ex) => {
              const idx = ex.loggedSets.findIndex((x) => x.id === s.id);
              if (idx < 0) throw Error("Set no longer exists");
              ex.loggedSets[idx] = s;
            }, "Set updated").then((ok) => {
              if (ok) setEditSet(undefined);
            })
          }
        />
      )}
      {swapping && (
        <ExercisePicker
          onClose={() => setSwapping(false)}
          onPick={(ex) => {
            setSwapping(false);
            setReplacement(ex);
          }}
        />
      )}
      {replacement && (
        <Sheet
          title="Where should this change apply?"
          onClose={() => setReplacement(undefined)}
        >
          <p>
            Replace {e.name} with {replacement.name}.
          </p>
          <Button
            disabled={busy}
            onClick={() =>
              run(
                () => swapExercise(w.id, w.revision, e.id, replacement, false),
                "Session exercise replaced",
              ).then((ok) => {
                if (ok) setReplacement(undefined);
              })
            }
          >
            This session only
          </Button>
          <Button
            variant="secondary"
            disabled={busy || !w.planId}
            onClick={() =>
              run(
                () => swapExercise(w.id, w.revision, e.id, replacement, true),
                "Session and plan updated",
              ).then((ok) => {
                if (ok) setReplacement(undefined);
              })
            }
          >
            This session and the plan
          </Button>
        </Sheet>
      )}
      {superset && <Sheet title="Superset" onClose={() => setSuperset(false)}>{[["", "No superset"], ["A", "Group A"], ["B", "Group B"], ["C", "Group C"]].map(([value, label]) => <Button key={label} variant={(e.supersetGroup ?? "") === value ? "primary" : "secondary"} onClick={() => mutate((exercise) => { exercise.supersetGroup = value; }, "Superset updated").then((ok) => { if (ok) setSuperset(false); })}>{label}</Button>)}</Sheet>}
      {targets && (
        <Sheet
          title="Targets & exercise notes"
          onClose={() => setTargets(false)}
        >
          <Field label="Target sets">
            <input
              type="number"
              min="1"
              max="100"
              value={targetDraft.sets}
              onChange={(ev) =>
                setTargetDraft({
                  ...targetDraft,
                  sets: Number(ev.target.value),
                })
              }
            />
          </Field>
          <Field label="Target reps / duration">
            <input
              value={targetDraft.reps}
              onChange={(ev) =>
                setTargetDraft({ ...targetDraft, reps: ev.target.value })
              }
            />
          </Field>
          <Field label="Rest seconds (0 = off)">
            <input
              type="number"
              min="0"
              max="3600"
              value={targetDraft.restSeconds}
              onChange={(ev) =>
                setTargetDraft({
                  ...targetDraft,
                  restSeconds: Number(ev.target.value),
                })
              }
            />
          </Field>
          <Field label="Tracking">
            <select
              disabled={e.loggedSets.length > 0}
              value={targetDraft.tracking}
              onChange={(ev) =>
                setTargetDraft({
                  ...targetDraft,
                  tracking: ev.target.value as Tracking,
                })
              }
            >
              {!trackingOptions.includes(targetDraft.tracking) && <option value={targetDraft.tracking}>{targetDraft.tracking || 'Unrecorded tracking'}</option>}
              {trackingOptions.map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </Field>
          <Field label="Session exercise notes">
            <textarea
              value={targetDraft.notes}
              onChange={(ev) =>
                setTargetDraft({ ...targetDraft, notes: ev.target.value })
              }
            />
          </Field>
          <Button
            disabled={busy}
            onClick={() =>
              mutate(
                (ex) => Object.assign(ex, targetDraft),
                "Targets saved",
              ).then((ok) => {
                if (ok) setTargets(false);
              })
            }
          >
            Save session targets
          </Button>
        </Sheet>
      )}
    </section>
  );
}
export function Workout() {
  const { data, run, busy } = useApp();
  const w = data.workouts.find((w) => w.status === "active");
  const [adding, setAdding] = useState(false);
  const [confirm, setConfirm] = useState<"finish" | "discard">();
  const [acknowledged, setAcknowledged] = useState(false);
  const [workoutMenu, setWorkoutMenu] = useState(false);
  const [notesSettings, setNotesSettings] = useState(false);
  const [confirmDeleteNotes, setConfirmDeleteNotes] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [orderedExerciseIds, setOrderedExerciseIds] = useState<string[]>([]);
  const [draggingExerciseId, setDraggingExerciseId] = useState<string>();
  const orderedExerciseIdsRef = useRef<string[]>([]);
  const draggingExerciseIdRef = useRef<string | undefined>(undefined);
  const exerciseListRef = useRef<HTMLDivElement>(null);
  const exercisePositionsRef = useRef(new Map<string, number>());
  const exerciseDragLayoutRef = useRef<{ id: string; center: number }[]>([]);
  useEffect(() => {
    const tick = () => setNow(Date.now());
    const timer = setInterval(tick, 1000);
    document.addEventListener("visibilitychange", tick);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", tick);
    };
  }, []);
  useEffect(() => {
    if (!w || !data.profile.keepAwake || !("wakeLock" in navigator)) return;
    let sentinel: WakeLockSentinel | undefined;
    let disposed = false;
    const request = async () => {
      try {
        if (document.visibilityState === "visible") {
          const s = await navigator.wakeLock.request("screen");
          if (disposed) await s.release();
          else sentinel = s;
        }
      } catch {
        /* Wake lock is optional. Timers use timestamps. */
      }
    };
    void request();
    document.addEventListener("visibilitychange", request);
    return () => {
      disposed = true;
      void sentinel?.release();
      document.removeEventListener("visibilitychange", request);
    };
  }, [w?.id, data.profile.keepAwake]);
  useEffect(() => {
    const current = w?.exercises.map((exercise) => exercise.id) ?? [];
    setOrderedExerciseIds((previous) => {
      const available = new Set(current);
      const retained = previous.filter((id) => available.has(id));
      const added = current.filter((id) => !retained.includes(id));
      const next = [...retained, ...added];
      orderedExerciseIdsRef.current = next;
      return next;
    });
  }, [w?.id, w?.exercises.map((exercise) => exercise.id).join("|")]);
  useLayoutEffect(() => {
    const cards = Array.from(
      exerciseListRef.current?.querySelectorAll<HTMLElement>("[data-exercise-id]") ?? [],
    );
    const nextPositions = new Map<string, number>();
    cards.forEach((card) => {
      const id = card.dataset.exerciseId;
      if (!id) return;
      const top = card.getBoundingClientRect().top;
      nextPositions.set(id, top);
      const previousTop = exercisePositionsRef.current.get(id);
      if (previousTop === undefined || id === draggingExerciseIdRef.current) return;
      const delta = previousTop - top;
      if (Math.abs(delta) < 1 || typeof card.animate !== "function") return;
      card.animate(
        [{ transform: `translateY(${delta}px)` }, { transform: "translateY(0)" }],
        { duration: 220, easing: "cubic-bezier(0.2, 0.8, 0.2, 1)" },
      );
    });
    exercisePositionsRef.current = nextPositions;
  }, [orderedExerciseIds]);
  useEffect(() => () => document.body.classList.remove("workout-reordering"), []);
  if (!w)
    return (
      <Empty title="No active workout">
        <p>Choose a program or start a freestyle session from Home.</p>
        <Button onClick={() => navigate("home")}>Go Home</Button>
      </Empty>
    );
  const remaining = Math.max(0, Math.ceil(((w.restEndsAt ?? 0) - now) / 1000));
  const lastPerformed = latestPerformedSet(w, now);
  const orderedExercises = orderedExerciseIds
    .map((id) => w.exercises.find((exercise) => exercise.id === id))
    .filter((exercise): exercise is SessionExercise => Boolean(exercise));
  const totalVolumeKg = w.exercises.flatMap((exercise) => exercise.loggedSets)
    .filter((set) => set.kind !== "warmup")
    .reduce((total, set) => total + set.weightKg * set.reps, 0);
  const previousWorkout = data.workouts
    .filter((workout) => workout.status === "completed" && workout.name === w.name)
    .sort((a, b) => b.startedAt - a.startedAt)[0];
  const previousVolumeKg = previousWorkout?.exercises.flatMap((exercise) => exercise.loggedSets)
    .filter((set) => set.kind !== "warmup")
    .reduce((total, set) => total + set.weightKg * set.reps, 0) ?? 0;
  const workingSets = w.exercises.flatMap((exercise) => exercise.loggedSets)
    .filter((set) => set.kind !== "warmup").length;
  const elapsedSeconds = Math.max(0, Math.floor((now - w.startedAt) / 1000));
  const restDuration = Math.max(1, lastPerformed?.exercise.restSeconds ?? data.profile.restSeconds);
  const restProgress = Math.max(0, Math.min(1, remaining / restDuration));
  const moveExerciseToPointer = (draggedId: string, y: number) => {
    const others = exerciseDragLayoutRef.current.filter((entry) => entry.id !== draggedId);
    let insertion = others.findIndex((entry) => y <= entry.center);
    if (insertion < 0) insertion = others.length;
    const next = others.map((entry) => entry.id);
    next.splice(insertion, 0, draggedId);
    if (next.join("|") === orderedExerciseIdsRef.current.join("|")) return;
    orderedExerciseIdsRef.current = next;
    setOrderedExerciseIds(next);
  };
  const commitExerciseOrder = (ids: string[]) => run(
    () => mutateWorkout(w.id, w.revision, (next) => {
      const byId = new Map(next.exercises.map((exercise) => [exercise.id, exercise]));
      const reordered = ids.map((id) => byId.get(id))
        .filter((exercise): exercise is SessionExercise => Boolean(exercise));
      if (reordered.length !== next.exercises.length) throw Error("Exercise list changed during reorder");
      next.exercises = reordered;
    }),
    "Exercise order saved",
  );
  const finishExerciseDrag = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (!draggingExerciseIdRef.current) return;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    draggingExerciseIdRef.current = undefined;
    exerciseDragLayoutRef.current = [];
    setDraggingExerciseId(undefined);
    document.body.classList.remove("workout-reordering");
    vibrate(24);
    void commitExerciseOrder(orderedExerciseIdsRef.current);
  };
  return (
    <>
      <div className="card workout-header-card">
        <div>
          <span className="eyebrow">Active workout</span>
          <h1>{w.name}</h1>
        </div>
        <div className="workout-header-actions">
          <span className="elapsed-pill"><RollingTimerText value={`${String(Math.floor(elapsedSeconds / 60)).padStart(2, "0")}:${String(elapsedSeconds % 60).padStart(2, "0")}`} /></span>
          <small>elapsed</small>
        </div>
        <div className="workout-header-controls">
          <button
            className="text-button workout-minimize"
            aria-label="Minimize workout"
            onClick={() => navigate("home")}
          >MINIMIZE</button>
          <IconButton name="more" label="Workout options" onClick={() => setWorkoutMenu(true)} />
        </div>
        {totalVolumeKg > 0 && (
          <p className={`live-volume${previousVolumeKg > totalVolumeKg ? " behind" : ""}`}>
            You’ve lifted {previousVolumeKg > totalVolumeKg ? "↓" : "↑"} {formatNumber(displayWeight(totalVolumeKg, data.profile.unit))} {data.profile.unit}
            {previousVolumeKg > 0 && ` (${totalVolumeKg >= previousVolumeKg ? "+" : ""}${formatNumber(displayWeight(totalVolumeKg - previousVolumeKg, data.profile.unit))} ${data.profile.unit} vs prev)`}
          </p>
        )}
      </div>
      {w.restEndsAt && (
        <aside className={`rest-banner${remaining === 0 ? " complete" : ""}`} role="status">
          <span className="rest-progress-ring" style={{ background: `conic-gradient(var(--accent) ${restProgress * 360}deg, var(--faint) 0deg)` }} aria-hidden="true">
            <Icon name={remaining === 0 ? "check" : "timer"} size={19} />
          </span>
          <strong>
            <RollingTimerText value={remaining
              ? `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}`
              : "Rest complete"} />
          </strong>
          <span>Rest timer
            {lastPerformed && <small className="rest-context">{lastPerformed.exercise.name}<br />{recentSetLabel(lastPerformed.exercise, lastPerformed.set, data.profile.unit, displayWeight)}</small>}
          </span>
          <button
            onClick={() =>
              run(() =>
                mutateWorkout(w.id, w.revision, (x) => {
                  x.restEndsAt = undefined;
                }),
              )
            }
          >
            Dismiss
          </button>
        </aside>
      )}
      <div ref={exerciseListRef} className="workout-exercise-list" aria-label="Workout exercises">
      {orderedExercises.map((e) => {
        const i = w.exercises.findIndex((exercise) => exercise.id === e.id);
        return (
        <ExerciseCard
          key={`${e.id}-${e.exerciseId}-${e.tracking}`}
          exercise={e}
          workout={w}
          index={i}
          isDragging={draggingExerciseId === e.id}
          onDragPointerDown={(event) => {
            event.preventDefault();
            event.currentTarget.setPointerCapture(event.pointerId);
            draggingExerciseIdRef.current = e.id;
            exerciseDragLayoutRef.current = Array.from(
              exerciseListRef.current?.querySelectorAll<HTMLElement>("[data-exercise-id]") ?? [],
            ).flatMap((card) => {
              const id = card.dataset.exerciseId;
              const box = card.getBoundingClientRect();
              return id ? [{ id, center: box.top + box.height / 2 }] : [];
            });
            setDraggingExerciseId(e.id);
            document.body.classList.add("workout-reordering");
            vibrate(12);
          }}
          onDragPointerMove={(event) => {
            if (draggingExerciseIdRef.current !== e.id) return;
            moveExerciseToPointer(e.id, event.clientY);
          }}
          onDragPointerUp={finishExerciseDrag}
          onDragPointerCancel={(event) => {
            if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
            draggingExerciseIdRef.current = undefined;
            exerciseDragLayoutRef.current = [];
            setDraggingExerciseId(undefined);
            document.body.classList.remove("workout-reordering");
            const restored = w.exercises.map((exercise) => exercise.id);
            orderedExerciseIdsRef.current = restored;
            setOrderedExerciseIds(restored);
          }}
          onDragKeyDown={(event) => {
            if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
            event.preventDefault();
            const position = orderedExerciseIdsRef.current.indexOf(e.id);
            const nextPosition = event.key === "ArrowUp" ? position - 1 : position + 1;
            if (nextPosition < 0 || nextPosition >= orderedExerciseIdsRef.current.length) return;
            const next = [...orderedExerciseIdsRef.current];
            [next[position], next[nextPosition]] = [next[nextPosition], next[position]];
            orderedExerciseIdsRef.current = next;
            setOrderedExerciseIds(next);
            void commitExerciseOrder(next);
          }}
        />
      )})}
      </div>
      <Button variant="secondary" onClick={() => setAdding(true)}>
        <Icon name="plus" />
        Add exercise
      </Button>
      <p className="muted">
        Changes save after every action. Keep this screen open for timer
        feedback; locked-screen alarms aren’t supported.
      </p>
      <section className="card workout-volume-card" aria-label="Workout volume">
        <strong>Volume: {formatNumber(displayWeight(totalVolumeKg, data.profile.unit))} {data.profile.unit}</strong>
        <span>About {volumeComparison(totalVolumeKg)}</span>
      </section>
      <div className="sticky-actions">
        <Button
          disabled={busy}
          onClick={() => {
            setAcknowledged(false);
            setConfirm("finish");
          }}
        >
          Finish workout
        </Button>
        <Button variant="ghost" onClick={() => setConfirm("discard")}>
          Discard
        </Button>
      </div>
      {adding && (
        <ExercisePicker
          onClose={() => setAdding(false)}
          onPick={(e) =>
            run(() =>
              mutateWorkout(w.id, w.revision, (x) => {
                x.exercises.push({
                  ...planned(e),
                  tracking: e.tracking,
                  muscle: e.muscle,
                  equipment: e.equipment,
                  secondaryMuscles: e.secondaryMuscles,
                  primaryMuscles:e.primaryMuscles,muscleContributions:e.muscleContributions,category:e.category,isBodyweight:e.isBodyweight,requiresExternalLoad:e.requiresExternalLoad,
                  loggedSets: [],
                  pendingWarmups: [],
                });
              }),
            ).then((ok) => {
              if (ok) setAdding(false);
            })
          }
        />
      )}
      {workoutMenu && (
        <Sheet title="Workout options" onClose={() => setWorkoutMenu(false)}>
          <Button
            variant="secondary"
            onClick={() => {
              setWorkoutMenu(false);
              setNotesSettings(true);
            }}
          >
            Exercise notes
          </Button>
        </Sheet>
      )}
      {notesSettings && (
        <Sheet title="Workout exercise notes" onClose={() => setNotesSettings(false)}>
          <Switch
            label="Show exercise notes"
            checked={data.profile.planExerciseNotesVisible}
            onChange={(visible) => {
              void run(
                () => saveProfile({ planExerciseNotesVisible: visible }),
                visible ? "Exercise notes shown" : "Exercise notes hidden",
              );
            }}
          />
          <Button
            variant="danger"
            disabled={busy || !w.exercises.some((exercise) => exercise.notes.trim())}
            onClick={() => {
              setNotesSettings(false);
              setConfirmDeleteNotes(true);
            }}
          >
            Delete notes from this session
          </Button>
        </Sheet>
      )}
      {confirmDeleteNotes && (
        <Sheet title="Delete workout exercise notes?" onClose={() => setConfirmDeleteNotes(false)}>
          <p>This removes every exercise-level note from the active workout. The source plan is unchanged.</p>
          <Button
            variant="danger"
            disabled={busy}
            onClick={() =>
              run(
                () => clearActiveWorkoutExerciseNotes(w.id, w.revision),
                "Workout exercise notes deleted",
              ).then((ok) => {
                if (ok) setConfirmDeleteNotes(false);
              })
            }
          >
            Confirm delete notes
          </Button>
          <Button variant="ghost" disabled={busy} onClick={() => setConfirmDeleteNotes(false)}>
            Cancel
          </Button>
        </Sheet>
      )}
      {confirm && (
        <Sheet
          title={
            confirm === "finish"
              ? "Finish this workout?"
              : "Discard this workout?"
          }
          onClose={() => setConfirm(undefined)}
        >
          <p>
            {confirm === "finish"
              ? "Only logged sets will be saved in History. Pending warmup targets do not count."
              : "This session will not count toward your training history."}
          </p>
          {confirm === "finish" && (
            <div className="completion-preview">
              <div className="completion-burst" aria-hidden="true">{Array.from({ length: 14 }, (_, index) => <i key={index} />)}</div>
              <span className="completion-mark"><Icon name="check" size={30} /></span>
              <h3>Session complete</h3>
              <div className="completion-stats">
                <div><strong>{Math.floor(elapsedSeconds / 60)}m</strong><span>Duration</span></div>
                <div><strong>{workingSets}</strong><span>Work sets</span></div>
                <div><strong>{formatNumber(displayWeight(totalVolumeKg, data.profile.unit))}</strong><span>{data.profile.unit} volume</span></div>
              </div>
            </div>
          )}
          {confirm === "finish" && hasFailedMutation(w.id) && (
            <div className="notice">
              <h3>A previous change was not saved.</h3>
              <p>
                Return to the workout and retry the missing change. Before
                finishing, review the visible sets. This confirmation accepts
                only those saved sets; it cannot recover an unsaved change.
              </p>
              <label className="check-label">
                <input
                  type="checkbox"
                  checked={acknowledged}
                  onChange={(e) => setAcknowledged(e.target.checked)}
                />
                I reviewed the workout and accept its saved sets.
              </label>
            </div>
          )}
          <Button
            disabled={
              busy ||
              (confirm === "finish" && hasFailedMutation(w.id) && !acknowledged)
            }
            variant={confirm === "finish" ? "primary" : "danger"}
            onClick={() =>
              run(
                async () => {
                  if (confirm === "finish") {
                    vibrate([30, 35, 55]);
                    if (hasFailedMutation(w.id))
                      await acknowledgeFailedMutation(w.id);
                    await finishWorkout(w.id);
                  } else await discardWorkout(w.id);
                },
                confirm === "finish" ? "Workout saved" : "Workout discarded",
              ).then((ok) => {
                if (ok) {
                  setConfirm(undefined);
                  navigate("log");
                }
              })
            }
          >
            {confirm === "finish"
              ? "Save completed workout"
              : "Discard session"}
          </Button>
        </Sheet>
      )}
    </>
  );
}
