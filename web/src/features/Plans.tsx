import { useState } from "react";
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
            <select value={muscle} onChange={(e) => setMuscle(e.target.value)}>
              {[
                "Chest",
                "Back",
                "Shoulders",
                "Biceps",
                "Triceps",
                "Quadriceps",
                "Hamstrings",
                "Glutes",
                "Calves",
                "Abdominals",
                "Cardio",
              ].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </Field>
          <Field label="Equipment">
            <input
              value={equipment}
              onChange={(e) => setEquipment(e.target.value)}
            />
          </Field>
          <Field label="Tracking">
            <select
              value={tracking}
              onChange={(e) => setTracking(e.target.value as Tracking)}
            >
              <option value="weight_reps">Weight × reps</option>
              <option value="bodyweight_reps">Bodyweight × reps</option>
              <option value="duration">Duration</option>
              <option value="duration_distance">Duration & distance</option>
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
  const [importing, setImporting] = useState(false);
  const [aiBuilder, setAiBuilder] = useState(false);
  const [aiGoal, setAiGoal] = useState("General Fitness");
  const [aiDays, setAiDays] = useState(3);
  const [raw, setRaw] = useState("");
  const [result, setResult] = useState("");
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
      {data.plans.map((p, index) => (
        <section className="card" key={p.id}>
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
            {index > 0 && (
              <Button
                variant="ghost"
                onClick={() =>
                  run(async () => {
                    const ids = data.plans.map((p) => p.id);
                    [ids[index - 1], ids[index]] = [ids[index], ids[index - 1]];
                    await reorderPlans(ids);
                  })
                }
              >
                Move up
              </Button>
            )}
          </div>
        </section>
      ))}
      <Button className="new-plan-action" disabled={busy} onClick={createBlankPlan}>
        <Icon name="plus" />New plan
      </Button>
      {library && (
        <Sheet title="Program library" onClose={() => setLibrary(false)}>
          <p>
            32 programs from the current native app. Review the exercise
            selection for your equipment and experience.
          </p>
          {templates.map((p) => (
            <button
              className="list-row"
              key={p.id}
              disabled={busy}
              onClick={() => {
                const copy = instantiatePlan(p as Plan, {
                  order: data.plans.length,
                });
                void run(() => savePlan(copy)).then((ok) => {
                  if (ok) {
                    setLibrary(false);
                    navigate(`plan/${copy.id}`);
                  }
                });
              }}
            >
              <div>
                <strong>{p.name}</strong>
                <small>
                  {p.days.length} days · {p.description}
                </small>
              </div>
              <Icon name="plus" />
            </button>
          ))}
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
        <Sheet title="Create with AI" onClose={() => setAiBuilder(false)}>
          <p>Build a local starting plan from IronLog’s current program library. You can edit every exercise afterward.</p>
          <Field label="Goal">
            <select value={aiGoal} onChange={(event) => setAiGoal(event.target.value)}>
              <option>General Fitness</option>
              <option>Hypertrophy</option>
              <option>Strength</option>
              <option>Endurance</option>
            </select>
          </Field>
          <Field label="Training days">
            <select value={aiDays} onChange={(event) => setAiDays(Number(event.target.value))}>
              {[2, 3, 4, 5, 6].map((days) => <option key={days} value={days}>{days} days</option>)}
            </select>
          </Field>
          <Button disabled={busy} onClick={() => {
            const goal = aiGoal.toLowerCase();
            const matches = templates.filter((template) => template.days.length === aiDays);
            const source = matches.find((template) =>
              `${template.name} ${template.description}`.toLowerCase().includes(goal),
            ) ?? matches[0] ?? templates[0];
            const copy = instantiatePlan(source as Plan, { order: data.plans.length });
            copy.name = `${aiGoal} ${aiDays}-Day Plan`;
            copy.goal = aiGoal;
            void run(() => savePlan(copy)).then((ok) => {
              if (ok) {
                setAiBuilder(false);
                navigate(`plan/${copy.id}`);
              }
            });
          }}>Generate plan</Button>
        </Sheet>
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
        <h1>Edit plan</h1>
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
              <Field label="Exercise notes">
                <textarea
                  value={ex.notes}
                  onChange={(e) =>
                    edit((p) => {
                      p.days[di].exercises[ei].notes = e.target.value;
                    })
                  }
                />
              </Field>
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
    </>
  );
}
