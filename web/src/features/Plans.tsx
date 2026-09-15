import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { useApp, navigate, download } from "../ui/context";
import {
  Button,
  Field,
  Icon,
  IconButton,
  Sheet,
  Empty,
} from "../ui/components";
import {
  newId,
  savePlan,
  deletePlan,
  duplicatePlan,
  saveProfile,
  saveExercise,
  startWorkout,
  importPlans,
  savePlanIfUnchanged,
  reorderPlans,
} from "../data/store";
import { decodePlans, encodePlan } from "../domain/codecs";
import { instantiatePlan } from "../domain/plans";
import { trackingOptions } from "../domain/tracking";
import { AIPlan } from "./plans/AIPlan";
import type {
  Exercise,
  Plan,
  PlannedExercise,
  Tracking,
} from "../domain/types";
import templates from "../generated/templates.json";
export function ExercisePicker({
  onPick,
  onClose,
}: {
  onPick: (e: Exercise) => void;
  onClose: () => void;
}) {
  const { data, run, busy } = useApp();
  const [query, setQuery] = useState("");
  const [custom, setCustom] = useState(false);
  const [muscle, setMuscle] = useState("Chest");
  const [equipment, setEquipment] = useState("Barbell");
  const [tracking, setTracking] = useState<Tracking>("weight_reps");
  const [movementPattern, setMovementPattern] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [secondaryMuscles, setSecondaryMuscles] = useState<string[]>([]);
  const muscleOptions = [
    "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms", "Abs",
    "Obliques", "Glutes", "Quads", "Hamstrings", "Calves", "Lower Back",
  ];
  const trackingLabels: Record<string, string> = {
    weight_reps: "Weight × reps",
    bodyweight_reps: "Bodyweight × reps",
    bodyweight_plus_weight_reps: "Bodyweight + weight × reps",
    assisted_bodyweight: "Assisted bodyweight × reps",
    duration: "Duration",
    duration_weight: "Duration & weight",
    duration_distance: "Duration & distance",
    cardio: "Cardio duration",
  };
  const list = data.exercises
    .filter((e) => e.name.toLowerCase().includes(query.toLowerCase()))
    .slice(0, 50);
  return (
    <Sheet
      title={custom ? "Create custom exercise" : "Choose exercise"}
      onClose={onClose}
    >
      <Field label="Exercise name">
        <input
          autoFocus
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search the library"
          maxLength={160}
        />
      </Field>
      {custom ? (
        <>
          <Field label="Primary muscle">
            <select value={muscle} onChange={(e) => {
              setMuscle(e.target.value);
              setSecondaryMuscles((current) => current.filter((value) => value !== e.target.value));
            }}>
              {muscleOptions.map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </Field>
          <fieldset className="choice-fieldset">
            <legend>Secondary muscles (optional)</legend>
            <div className="choice-chips">
              {muscleOptions.map((option) => (
                <label className={`choice-chip ${secondaryMuscles.includes(option) ? "selected" : ""} ${option === muscle ? "disabled" : ""}`} key={option}>
                  <input
                    type="checkbox"
                    checked={secondaryMuscles.includes(option)}
                    disabled={option === muscle}
                    onChange={() => setSecondaryMuscles((current) => current.includes(option)
                      ? current.filter((value) => value !== option)
                      : [...current, option])}
                  />
                  {option}
                </label>
              ))}
            </div>
          </fieldset>
          <Field label="Equipment">
            <select value={equipment} onChange={(e) => setEquipment(e.target.value)}>
              {["Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Band", "Kettlebell", "Other"].map((option) => <option key={option}>{option}</option>)}
            </select>
          </Field>
          <Field label="Tracking">
            <select
              value={tracking}
              onChange={(e) => setTracking(e.target.value as Tracking)}
            >
              {trackingOptions.map((option) => <option value={option} key={option}>{trackingLabels[option] ?? option}</option>)}
            </select>
          </Field>
          <Field label="Movement pattern">
            <select value={movementPattern} onChange={(e) => setMovementPattern(e.target.value)}>
              <option value="">Not set</option>
              {["Push", "Pull", "Hinge", "Squat", "Carry", "Rotation", "Isolation"].map((option) => <option key={option}>{option}</option>)}
            </select>
          </Field>
          <Field label="Difficulty">
            <select value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
              <option value="">Not set</option>
              {["beginner", "intermediate", "advanced", "expert"].map((option) => <option key={option} value={option}>{option[0].toUpperCase() + option.slice(1)}</option>)}
            </select>
          </Field>
          <Button
            disabled={busy || !query.trim()}
            onClick={() => {
              const ex: Exercise = {
                id: newId(),
                name: query.trim(),
                muscle,
                equipment,
                tracking,
                category: "strength",
                movementPattern: movementPattern || undefined,
                difficulty: difficulty || undefined,
                secondaryMuscles,
                primaryMuscles: [muscle],
                isBodyweight: equipment === "Bodyweight" || tracking.includes("bodyweight"),
                requiresExternalLoad: ["weight_reps", "duration_weight"].includes(tracking),
                custom: true,
              };
              void run(() => saveExercise(ex)).then((ok) => {
                if (ok) onPick(ex);
              });
            }}
          >
            Create and select
          </Button>
        </>
      ) : (
        <>
          <Button variant="secondary" onClick={() => setCustom(true)}>
            <Icon name="plus" />
            Create custom exercise
          </Button>
          <div className="search-results">
            {list.map((e) => (
              <button className="list-row" key={e.id} onClick={() => onPick(e)}>
                <div>
                  <strong>{e.name}</strong>
                  <small>
                    {e.muscle} · {e.equipment}
                  </small>
                </div>
                <Icon name="plus" />
              </button>
            ))}
            {!list.length && (
              <p>
                No match. Create this exercise with its own equipment and
                tracking type.
              </p>
            )}
            {list.length === 50 && (
              <p className="muted">
                Showing the first 50 matches. Keep typing to narrow the list.
              </p>
            )}
          </div>
        </>
      )}
    </Sheet>
  );
}
export const planned = (e: Exercise): PlannedExercise => ({
  id: newId(),
  exerciseId: e.id,
  name: e.name,
  sets: 3,
  reps: "8–12",
  restSeconds: 90,
  notes: "",
  supersetGroup: "",
  isWarmup: false,
});
export function Plans() {
  const { data, run, busy } = useApp();
  const [library, setLibrary] = useState(false);
  const [programQuery, setProgramQuery] = useState("");
  const [programCategory, setProgramCategory] = useState("All");
  const [selectedProgram, setSelectedProgram] = useState<Plan>();
  const [importing, setImporting] = useState(false);
  const [aiBuilder, setAiBuilder] = useState(false);
  const [raw, setRaw] = useState("");
  const [result, setResult] = useState("");
  const [orderedIds, setOrderedIds] = useState(() => data.plans.map((plan) => plan.id));
  const [draggingId, setDraggingId] = useState<string>();
  const orderedIdsRef = useRef(orderedIds);
  const draggingIdRef = useRef<string | undefined>(undefined);
  const planListRef = useRef<HTMLDivElement>(null);
  const planPositionsRef = useRef(new Map<string, number>());
  const dragPointerRef = useRef({ x: 0, y: 0 });
  const autoScrollFrameRef = useRef<number | undefined>(undefined);
  const planDragLayoutRef = useRef<{ id: string; center: number }[]>([]);

  const updateOrder = (recipe: (ids: string[]) => string[]) => {
    setOrderedIds((current) => {
      const next = recipe(current);
      orderedIdsRef.current = next;
      return next;
    });
  };

  useEffect(() => {
    updateOrder((current) => {
      const available = new Set(data.plans.map((plan) => plan.id));
      const retained = current.filter((id) => available.has(id));
      const added = data.plans.map((plan) => plan.id).filter((id) => !retained.includes(id));
      return [...retained, ...added];
    });
  }, [data.plans]);

  const orderedPlans = orderedIds
    .map((id) => data.plans.find((plan) => plan.id === id))
    .filter((plan): plan is Plan => Boolean(plan));
  const programCategories = ["All", ...Array.from(new Set(templates.map((template) => template.goal || "General Fitness")))];
  const filteredPrograms = templates.filter((template) => {
    const inCategory = programCategory === "All" || template.goal === programCategory;
    const query = programQuery.trim().toLowerCase();
    return inCategory && (!query || `${template.name} ${template.description} ${template.goal}`.toLowerCase().includes(query));
  });

  useLayoutEffect(() => {
    const cards = Array.from(
      planListRef.current?.querySelectorAll<HTMLElement>("[data-plan-id]") ?? [],
    );
    const nextPositions = new Map<string, number>();
    cards.forEach((card) => {
      const id = card.dataset.planId;
      if (!id) return;
      const top = card.getBoundingClientRect().top;
      nextPositions.set(id, top);
      const previousTop = planPositionsRef.current.get(id);
      if (previousTop === undefined || id === draggingIdRef.current) return;
      const delta = previousTop - top;
      if (Math.abs(delta) < 1 || typeof card.animate !== "function") return;
      card.animate(
        [{ transform: `translateY(${delta}px)` }, { transform: "translateY(0)" }],
        { duration: 220, easing: "cubic-bezier(0.2, 0.8, 0.2, 1)" },
      );
    });
    planPositionsRef.current = nextPositions;
  }, [orderedIds]);

  const movePlanToPointer = (draggedId: string, y: number) => {
    const others = planDragLayoutRef.current.filter((entry) => entry.id !== draggedId);
    let insertion = others.findIndex((entry) => y <= entry.center);
    if (insertion < 0) insertion = others.length;
    const next = others.map((entry) => entry.id);
    next.splice(insertion, 0, draggedId);
    if (next.join("|") === orderedIdsRef.current.join("|")) return;
    orderedIdsRef.current = next;
    setOrderedIds(next);
  };

  const stopPlanAutoScroll = () => {
    if (autoScrollFrameRef.current !== undefined) {
      cancelAnimationFrame(autoScrollFrameRef.current);
      autoScrollFrameRef.current = undefined;
    }
  };

  const startPlanAutoScroll = () => {
    stopPlanAutoScroll();
    const tick = () => {
      const draggedId = draggingIdRef.current;
      if (!draggedId) return;
      const { x, y } = dragPointerRef.current;
      const edge = Math.min(110, window.innerHeight * 0.18);
      const upward = y < edge ? -Math.ceil((edge - y) / 7) : 0;
      const downward = y > window.innerHeight - edge
        ? Math.ceil((y - (window.innerHeight - edge)) / 7)
        : 0;
      const delta = Math.max(-22, Math.min(22, upward + downward));
      if (delta) {
        window.scrollBy(0, delta);
        movePlanToPointer(draggedId, y);
      }
      autoScrollFrameRef.current = requestAnimationFrame(tick);
    };
    autoScrollFrameRef.current = requestAnimationFrame(tick);
  };

  useEffect(() => () => {
    stopPlanAutoScroll();
    document.body.classList.remove("plan-reordering");
  }, []);

  const commitPlanOrder = (ids: string[]) => run(async () => {
    await reorderPlans(ids);
    if (ids[0] && ids[0] !== data.profile.activePlanId) {
      await saveProfile({ activePlanId: ids[0] });
    }
  }, ids[0] && ids[0] !== data.profile.activePlanId
    ? "Plan order saved · top plan is now active"
    : "Plan order saved");

  const finishPlanDrag = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (!draggingIdRef.current) return;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    draggingIdRef.current = undefined;
    planDragLayoutRef.current = [];
    setDraggingId(undefined);
    stopPlanAutoScroll();
    document.body.classList.remove("plan-reordering");
    void commitPlanOrder(orderedIdsRef.current);
  };
  const createBlankPlan = () => {
    const plan: Plan = {
      id: newId(),
      name: "New plan",
      description: "",
      goal: "General Fitness",
      days: [],
      order: data.plans.length,
    };
    void run(() => savePlan(plan)).then((ok) => {
      if (ok) navigate(`plan/${plan.id}`);
    });
  };
  return (
    <>
      <header className="native-screen-title">
        <span className="eyebrow">Training</span>
        <h1>Plans</h1>
        <p>{data.plans.length} program{data.plans.length === 1 ? "" : "s"}</p>
      </header>
      <div className="plan-actions">
        <button onClick={() => setLibrary(true)}><Icon name="program" />Browse programs</button>
        <button onClick={() => setImporting(true)}><Icon name="import" />Import plan</button>
        <button onClick={() => setAiBuilder(true)}><Icon name="spark" />Create with AI</button>
      </div>
      {!data.plans.length && (
        <div className="native-empty-state">
          <Icon name="list" size={34} />
          <h2>No plans yet</h2>
          <p>Browse a program above, import one, or create a routine with AI.</p>
        </div>
      )}
      <div ref={planListRef} className="plan-reorder-list" aria-label="Saved plans">
      {orderedPlans.map((p, index) => (
        <section
          className={`card plan-card${p.id === data.profile.activePlanId ? " active" : ""}${draggingId === p.id ? " dragging" : ""}`}
          data-plan-id={p.id}
          key={p.id}
        >
          <div className="section-title">
            <div>
              {p.id === data.profile.activePlanId && (
                <span className="pill">Active program</span>
              )}
              <h2>{p.name}</h2>
            </div>
            <IconButton
              name="edit"
              label={`Edit ${p.name}`}
              onClick={() => navigate(`plan/${p.id}`)}
            />
            <button
              type="button"
              className="plan-drag-handle"
              aria-label={`Drag to reorder ${p.name}`}
              aria-pressed={draggingId === p.id}
              disabled={busy}
              onPointerDown={(event) => {
                event.preventDefault();
                event.currentTarget.setPointerCapture(event.pointerId);
                draggingIdRef.current = p.id;
                dragPointerRef.current = { x: event.clientX, y: event.clientY };
                planDragLayoutRef.current = Array.from(
                  planListRef.current?.querySelectorAll<HTMLElement>("[data-plan-id]") ?? [],
                ).flatMap((card) => {
                  const id = card.dataset.planId;
                  const box = card.getBoundingClientRect();
                  return id ? [{ id, center: box.top + box.height / 2 }] : [];
                });
                setDraggingId(p.id);
                document.body.classList.add("plan-reordering");
                startPlanAutoScroll();
              }}
              onPointerMove={(event) => {
                if (draggingIdRef.current !== p.id) return;
                dragPointerRef.current = { x: event.clientX, y: event.clientY };
                movePlanToPointer(p.id, event.clientY);
              }}
              onPointerUp={finishPlanDrag}
              onPointerCancel={(event) => {
                if (event.currentTarget.hasPointerCapture(event.pointerId)) {
                  event.currentTarget.releasePointerCapture(event.pointerId);
                }
                draggingIdRef.current = undefined;
                planDragLayoutRef.current = [];
                setDraggingId(undefined);
                stopPlanAutoScroll();
                document.body.classList.remove("plan-reordering");
                const restored = data.plans.map((plan) => plan.id);
                orderedIdsRef.current = restored;
                setOrderedIds(restored);
              }}
              onKeyDown={(event) => {
                if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
                event.preventDefault();
                const nextIndex = event.key === "ArrowUp" ? index - 1 : index + 1;
                if (nextIndex < 0 || nextIndex >= orderedPlans.length) return;
                const next = [...orderedIdsRef.current];
                [next[index], next[nextIndex]] = [next[nextIndex], next[index]];
                orderedIdsRef.current = next;
                setOrderedIds(next);
                void commitPlanOrder(next);
              }}
            >
              <Icon name="menu" size={18} />
            </button>
          </div>
          <p>{p.description || `${p.days.length} days · ${p.goal}`}</p>
          <div className="chips">
            {p.days.map((d) => (
              <button
                key={d.id}
                onClick={() =>
                  run(async () => {
                    await startWorkout(p.id, d.id);
                    navigate("workout");
                  })
                }
              >
                {d.name}
              </button>
            ))}
          </div>
          <div className="toolbar">
            <Button
              variant="secondary"
              onClick={() =>
                run(
                  () => saveProfile({ activePlanId: p.id }),
                  "Active program updated",
                )
              }
            >
              {p.id === data.profile.activePlanId ? "Selected" : "Make active"}
            </Button>
          </div>
        </section>
      ))}
      </div>
      <Button className="new-plan-action" disabled={busy} onClick={createBlankPlan}>
        <Icon name="plus" />New plan
      </Button>
      {library && (
        <Sheet title={selectedProgram ? selectedProgram.name : "Program library"} onClose={() => {
          if (selectedProgram) setSelectedProgram(undefined);
          else setLibrary(false);
        }}>
          {selectedProgram ? <>
            <div className="program-detail-badges">
              <span className="pill">{selectedProgram.days.length}× / week</span>
              <span className="pill">{selectedProgram.goal}</span>
            </div>
            <p>{selectedProgram.description}</p>
            <div className="program-day-preview">
              {selectedProgram.days.map((day) => <div className="list-row" key={day.id}>
                <strong>{day.name}</strong><small>{day.exercises.length} exercises</small>
              </div>)}
            </div>
            <Button variant="secondary" onClick={() => setSelectedProgram(undefined)}>Back to programs</Button>
            <Button disabled={busy} onClick={() => {
              const copy = instantiatePlan(selectedProgram, { order: data.plans.length });
              void run(() => savePlan(copy)).then((ok) => {
                if (ok) {
                  setSelectedProgram(undefined);
                  setLibrary(false);
                  navigate(`plan/${copy.id}`);
                }
              });
            }}>Add to my plans</Button>
          </> : <>
            <p>Programs from the native library, ready to review before adding.</p>
            <Field label="Search programs">
              <input value={programQuery} onChange={(event) => setProgramQuery(event.target.value)} placeholder="Name, goal, or description" />
            </Field>
            <div className="choice-chips" aria-label="Program categories">
              {programCategories.map((category) => <button
                type="button"
                className={`choice-chip ${programCategory === category ? "selected" : ""}`}
                aria-pressed={programCategory === category}
                key={category}
                onClick={() => setProgramCategory(category)}
              >{category}</button>)}
            </div>
            {filteredPrograms.map((program) => (
              <button className="list-row" key={program.id} onClick={() => setSelectedProgram(program as Plan)}>
                <div>
                  <strong>{program.name}</strong>
                  <small>{program.days.length} days · {program.goal} · {program.description}</small>
                </div>
                <Icon name="right" />
              </button>
            ))}
            {!filteredPrograms.length && <Empty title={`No programs match “${programQuery.trim()}”`}><p>Try another name, goal, or category.</p></Empty>}
          </>}
        </Sheet>
      )}
      {importing && (
        <Sheet title="Import a plan" onClose={() => setImporting(false)}>
          <Field label="Choose a JSON file">
            <input
              type="file"
              accept=".json,application/json"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f)
                  void run(async () => {
                    if (f.size > 5_000_000)
                      throw Error("Plan file exceeds 5 MB");
                    setRaw(await f.text());
                  });
              }}
            />
          </Field>
          <Field label="Or paste plan JSON">
            <textarea
              rows={8}
              value={raw}
              onChange={(e) => setRaw(e.target.value)}
              placeholder="Canonical IronLog wrapper or legacy plan JSON"
            />
          </Field>
          <Button
            disabled={!raw.trim() || busy}
            onClick={() =>
              run(async () => {
                const decoded = decodePlans(raw, data.exercises);
                await importPlans(
                  decoded.plans.map((p, index) => ({
                    ...p,
                    order: data.plans.length + index,
                  })),
                );
                setResult(
                  `${decoded.result.imported} imported · ${decoded.result.unresolved} unresolved · ${decoded.result.skipped} skipped. ${decoded.result.warnings.join(" ")}`,
                );
                setRaw("");
              })
            }
          >
            Import plans
          </Button>
          {result && <p role="status">{result}</p>}
        </Sheet>
      )}
      {aiBuilder && (
        <AIPlan onClose={() => setAiBuilder(false)} />
      )}
    </>
  );
}
export function PlanEditor({ id }: { id: string }) {
  const { data, run, busy } = useApp();
  const source = data.plans.find((p) => p.id === id);
  const [baseline, setBaseline] = useState(
    source ? structuredClone(source) : undefined,
  );
  const [draft, setDraft] = useState<Plan | undefined>(
    source ? structuredClone(source) : undefined,
  );
  const [addTo, setAddTo] = useState<string>();
  const [confirm, setConfirm] = useState(false);
  const [confirmClose, setConfirmClose] = useState(false);
  const [notesSettings, setNotesSettings] = useState(false);
  const [confirmDeleteNotes, setConfirmDeleteNotes] = useState(false);
  const [notesVisible, setNotesVisible] = useState(data.profile.planExerciseNotesVisible);
  const dirty = Boolean(draft && baseline && JSON.stringify(draft) !== JSON.stringify(baseline));
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  if (!draft)
    return (
      <Empty title="Plan not found">
        <Button onClick={() => navigate("plans")}>Back to plans</Button>
      </Empty>
    );
  const edit = (recipe: (p: Plan) => void) => {
    const p = structuredClone(draft);
    recipe(p);
    setDraft(p);
  };
  return (
    <>
      <div className="page-title">
        <div className="row">
          <Button variant="ghost" onClick={() => dirty ? setConfirmClose(true) : navigate("plans")}>Back to plans</Button>
          <h1>Edit plan</h1>
        </div>
        <IconButton name="note" label="Plan note settings" onClick={() => setNotesSettings(true)} />
        <IconButton
          name="share"
          label="Export plan JSON"
          onClick={() => download(encodePlan(draft), `${draft.name}.json`)}
        />
      </div>
      <Field label="Plan name">
        <input
          value={draft.name}
          onChange={(e) =>
            edit((p) => {
              p.name = e.target.value;
            })
          }
          maxLength={160}
        />
      </Field>
      <Field label="Description">
        <textarea
          value={draft.description}
          onChange={(e) =>
            edit((p) => {
              p.description = e.target.value;
            })
          }
        />
      </Field>
      <Field label="Training goal">
        <input
          value={draft.goal}
          onChange={(e) =>
            edit((p) => {
              p.goal = e.target.value;
            })
          }
        />
      </Field>
      {draft.days.map((day, di) => (
        <section className="card plan-day" key={day.id}>
          <div className="row">
            <Field label={`Day ${di + 1}`}>
              <input
                value={day.name}
                onChange={(e) =>
                  edit((p) => {
                    p.days[di].name = e.target.value;
                  })
                }
              />
            </Field>
            <IconButton
              name="trash"
              label={`Remove day ${day.name}`}
              onClick={() =>
                edit((p) => {
                  p.days.splice(di, 1);
                })
              }
            />
          </div>
          {day.exercises.map((ex, ei) => (
            <div className="plan-exercise" key={ex.id}>
              <div className="row">
                <strong>{ex.name}</strong>
                <IconButton
                  name="trash"
                  label={`Remove ${ex.name}`}
                  onClick={() =>
                    edit((p) => {
                      p.days[di].exercises.splice(ei, 1);
                    })
                  }
                />
              </div>
              <div className="three-col">
                <Field label="Sets">
                  <input
                    type="number"
                    min="1"
                    max="100"
                    value={ex.sets}
                    onChange={(e) =>
                      edit((p) => {
                        p.days[di].exercises[ei].sets = Number(e.target.value);
                      })
                    }
                  />
                </Field>
                <Field label="Reps / time">
                  <input
                    value={ex.reps}
                    onChange={(e) =>
                      edit((p) => {
                        p.days[di].exercises[ei].reps = e.target.value;
                      })
                    }
                  />
                </Field>
                <Field label="Rest (s)">
                  <input
                    type="number"
                    min="0"
                    max="3600"
                    value={ex.restSeconds}
                    onChange={(e) =>
                      edit((p) => {
                        p.days[di].exercises[ei].restSeconds = Number(
                          e.target.value,
                        );
                      })
                    }
                  />
                </Field>
              </div>
              {notesVisible && <Field label="Exercise notes">
                  <textarea
                    value={ex.notes}
                    onChange={(e) =>
                      edit((p) => {
                        p.days[di].exercises[ei].notes = e.target.value;
                      })
                    }
                  />
                </Field>}
              <div className="row">
                <Field label="Superset group">
                  <input
                    value={ex.supersetGroup}
                    placeholder="e.g. A"
                    onChange={(e) =>
                      edit((p) => {
                        p.days[di].exercises[ei].supersetGroup = e.target.value;
                      })
                    }
                  />
                </Field>
                <label className="check-label">
                  <input
                    type="checkbox"
                    checked={ex.isWarmup}
                    onChange={(e) =>
                      edit((p) => {
                        p.days[di].exercises[ei].isWarmup = e.target.checked;
                      })
                    }
                  />
                  Warmup
                </label>
              </div>
              {ei > 0 && (
                <Button
                  variant="ghost"
                  onClick={() =>
                    edit((p) => {
                      const list = p.days[di].exercises;
                      [list[ei - 1], list[ei]] = [list[ei], list[ei - 1]];
                    })
                  }
                >
                  Move up
                </Button>
              )}
              {ei < day.exercises.length - 1 && (
                <Button variant="ghost" onClick={() => edit((p) => {
                  const list = p.days[di].exercises;
                  [list[ei], list[ei + 1]] = [list[ei + 1], list[ei]];
                })}>Move down</Button>
              )}
              {draft.days.length > 1 && <Field label={`Move ${ex.name} to day`}>
                <select value={day.id} onChange={(event) => {
                  const targetId = event.target.value;
                  if (targetId === day.id) return;
                  edit((p) => {
                    const [moved] = p.days[di].exercises.splice(ei, 1);
                    p.days.find((candidate) => candidate.id === targetId)?.exercises.push(moved);
                  });
                }}>
                  {draft.days.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.name}</option>)}
                </select>
              </Field>}
            </div>
          ))}
          <Button variant="secondary" onClick={() => setAddTo(day.id)}>
            <Icon name="plus" />
            Add exercise
          </Button>
        </section>
      ))}
      <Button
        variant="secondary"
        onClick={() =>
          edit((p) => {
            p.days.push({
              id: newId(),
              name: `Day ${p.days.length + 1}`,
              color: "#FF4500",
              exercises: [],
            });
          })
        }
      >
        Add day
      </Button>
      <div className="sticky-actions">
        <Button
          disabled={busy || !draft.name.trim()}
          onClick={() =>
            run(async () => {
              if (!baseline)
                throw Error("Plan not found. Reload your saved plans.");
              await savePlanIfUnchanged(draft, baseline);
              navigate("plans");
            }, "Plan saved")
          }
        >
          Save plan
        </Button>
        <Button
          variant="ghost"
          onClick={() => {
            if (source) {
              setDraft(structuredClone(source));
              setBaseline(structuredClone(source));
            }
          }}
        >
          Discard edits & reload saved plan
        </Button>
        <Button
          variant="ghost"
          onClick={() =>
            run(async () => {
              await duplicatePlan(id);
              navigate("plans");
            })
          }
        >
          Duplicate saved plan
        </Button>
      </div>
      <Button variant="danger" onClick={() => setConfirm(true)}>
        Delete plan
      </Button>
      {addTo && (
        <ExercisePicker
          onClose={() => setAddTo(undefined)}
          onPick={(e) => {
            edit((p) =>
              p.days.find((d) => d.id === addTo)!.exercises.push(planned(e)),
            );
            setAddTo(undefined);
          }}
        />
      )}
      {confirm && (
        <Sheet title="Delete this plan?" onClose={() => setConfirm(false)}>
          <p>Completed workouts stay in History. This cannot be undone.</p>
          <Button
            variant="danger"
            disabled={busy}
            onClick={() =>
              run(async () => {
                await deletePlan(id);
                navigate("plans");
              })
            }
          >
            Delete plan
          </Button>
        </Sheet>
      )}
      {notesSettings && (
        <Sheet title="Plan notes" onClose={() => setNotesSettings(false)}>
          <label className="check-label">
            <input type="checkbox" checked={notesVisible} onChange={(event) => {
              const next = event.target.checked;
              setNotesVisible(next);
              void run(() => saveProfile({ planExerciseNotesVisible: next }));
            }} />
            Show exercise notes
          </label>
          <p className="muted">Hiding notes changes only the editor display. Saved notes remain available.</p>
          <Button variant="danger" onClick={() => setConfirmDeleteNotes(true)}>Delete all notes in this plan</Button>
        </Sheet>
      )}
      {confirmDeleteNotes && (
        <Sheet title="Delete all plan notes?" onClose={() => setConfirmDeleteNotes(false)}>
          <p>This removes the plan description and every exercise note in {draft.name}.</p>
          <Button variant="danger" onClick={() => {
            edit((plan) => {
              plan.description = "";
              plan.days.forEach((day) => day.exercises.forEach((exercise) => { exercise.notes = ""; }));
            });
            setConfirmDeleteNotes(false);
            setNotesSettings(false);
          }}>Confirm delete all notes</Button>
        </Sheet>
      )}
      {confirmClose && (
        <Sheet title="Discard plan edits?" onClose={() => setConfirmClose(false)}>
          <p>Your unsaved plan changes will be lost.</p>
          <Button variant="danger" onClick={() => navigate("plans")}>Discard and leave</Button>
          <Button variant="secondary" onClick={() => setConfirmClose(false)}>Keep editing</Button>
        </Sheet>
      )}
    </>
  );
}
