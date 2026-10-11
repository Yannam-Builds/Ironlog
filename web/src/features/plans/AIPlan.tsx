import { useEffect, useState } from "react";
import { importPlans } from "../../data/store";
import { decodePlans } from "../../domain/codecs";
import { buildManualPlanPrompt, exerciseCatalogMarkdown } from "../../domain/manual-prompt";
import type { Plan } from "../../domain/types";
import { Button, Field, Sheet } from "../../ui/components";
import { download, navigate, useApp } from "../../ui/context";
import { ExercisePicker } from "../Plans";
import { activeGymEquipment, aiEquipmentOptions, canImportAiPlans, unresolvedPlanRows } from "../../domain/ai-plan-review";
import { useCloudRequest } from "../../ui/useCloudRequest";
import { cloudPlanPrompt, validateGeneratedPlan } from "../../domain/cloud-plan";
import { cloudCoachingEnabled } from "../../domain/coaching-mode";

type Stage = "intro" | "quiz" | "prompt" | "library" | "paste" | "preview";
const equipmentOptions = aiEquipmentOptions;

export function AIPlan({ onClose }: { onClose: () => void }) {
  const { data, run, busy } = useApp();
  const [stage, setStage] = useState<Stage>("intro");
  const [goal, setGoal] = useState("General Fitness");
  const [days, setDays] = useState(3);
  const [minutes, setMinutes] = useState(60);
  const [equipment, setEquipment] = useState(() => activeGymEquipment(data.profile, data.gyms));
  const [limitations, setLimitations] = useState("");
  const [cardio, setCardio] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [raw, setRaw] = useState("");
  const [preview, setPreview] = useState<{ plans: Plan[]; unresolved: number; warnings: string[] }>();
  const [error, setError] = useState("");
  const [reviewRow, setReviewRow] = useState<{ id: string; name: string }>();
  const [reviewedIds, setReviewedIds] = useState<string[]>([]);
  const cloud = useCloudRequest();
  const requestContext = JSON.stringify([goal, days, minutes, equipment, limitations, cardio, data.profile.cloudConfig, data.profile.intelligenceMode]);
  useEffect(() => { cloud.cancel(); }, [requestContext, stage]);
  const unresolved = preview ? unresolvedPlanRows(preview.plans, data.exercises, reviewedIds) : [];
  const eligibleCatalog = data.exercises.filter(exercise => equipment.some(item => item.toLowerCase() === exercise.equipment.toLowerCase()));
  const buildPrompt = () => buildManualPlanPrompt({ goal, daysPerWeek: days, sessionMinutes: minutes, equipment, limitations, cardioEverySession: cardio }, eligibleCatalog);
  const generateCloudPlan = async () => {
    const config = data.profile.cloudConfig; if (!config || !cloudCoachingEnabled(data.profile)) return;
    const context = cloudPlanPrompt({ goal, daysPerWeek: days, sessionMinutes: minutes, equipment, limitations, cardioEverySession: cardio }, eligibleCatalog, data.profile, data.workouts);
    let value = await cloud.start(config, context);
    if (typeof value !== "string") return;
    try { value = validateGeneratedPlan(value, days, eligibleCatalog); }
    catch {
      value = await cloud.start(config, context + `\nCorrect your response: exactly ${days} nonempty days, at most eight exercises per day and five sets per exercise. Return only valid JSON.`);
      if (typeof value !== "string") return;
      try { value = validateGeneratedPlan(value, days, eligibleCatalog); }
      catch { setError("The provider returned a plan that needs correction. Review its JSON before importing."); }
    }
    setRaw(value); setStage("paste");
  };

  const goBack = () => {
    const order: Stage[] = ["intro", "quiz", "prompt", "library", "paste", "preview"];
    const index = order.indexOf(stage);
    cloud.cancel(); if (index <= 0) onClose(); else setStage(order[index - 1]);
  };
  const cleanedJson = (value: string) => value.trim().replace(/```json\s*/gi, "").replace(/```/g, "").trim();

  return <Sheet title="AI plan creator" onClose={onClose}>
    {stage !== "intro" && <Button variant="ghost" onClick={goBack}>Back</Button>}
    {stage === "intro" && <section className="ai-plan-stage">
      <h2>Create plan with AI</h2>
      <p>Answer a few questions, copy a reviewed prompt to any AI assistant, then paste its JSON response back into IronLog.</p>
      <ol><li>Set your goal and available equipment</li><li>Review the generated prompt</li><li>Give the AI your exercise catalog</li><li>Validate and preview before saving</li></ol>
      <Button onClick={() => setStage("quiz")}>Get started</Button>
    </section>}
    {stage === "quiz" && <section className="ai-plan-stage">
      <span className="pill">Step 1 of 4</span><h2>Plan details</h2>
      <Field label="Goal"><select value={goal} onChange={(event) => setGoal(event.target.value)}>{["General Fitness", "Hypertrophy", "Strength", "Endurance", "Aesthetic"].map((value) => <option key={value}>{value}</option>)}</select></Field>
      <div className="two-col">
        <Field label="Days per week"><input type="number" min="1" max="7" value={days} onChange={(event) => setDays(Math.max(1, Math.min(7, Number(event.target.value))))} /></Field>
        <Field label="Session minutes"><input type="number" min="15" max="180" value={minutes} onChange={(event) => setMinutes(Math.max(15, Math.min(180, Number(event.target.value))))} /></Field>
      </div>
      <fieldset className="choice-fieldset"><legend>Available equipment</legend><div className="choice-chips">{equipmentOptions.map((value) => <label className={`choice-chip ${equipment.includes(value) ? "selected" : ""}`} key={value}><input type="checkbox" checked={equipment.includes(value)} onChange={() => setEquipment((current) => current.includes(value) ? current.filter((item) => item !== value) : [...current, value])} />{value}</label>)}</div></fieldset>
      <Field label="Limitations or injuries"><textarea value={limitations} onChange={(event) => setLimitations(event.target.value)} maxLength={1000} /></Field>
      <label className="check-label"><input type="checkbox" checked={cardio} onChange={(event) => setCardio(event.target.checked)} />Include cardio each session</label>
      <Button onClick={() => { setPrompt(buildPrompt()); setStage("prompt"); }}>Build prompt</Button>
      {data.profile.cloudConfig && cloudCoachingEnabled(data.profile) && <><p>Cloud generation sends these plan details, training baseline, five recent session summaries and selected equipment catalog to your provider. A structurally invalid response may be retried once. You will review the response before importing.</p><Button disabled={cloud.pending} onClick={() => void generateCloudPlan()}>Create with Cloud AI</Button></>}
      {cloud.pending && <Button variant="secondary" onClick={cloud.cancel}>Cancel generation</Button>}
      {cloud.error && <p role="alert">{cloud.error}</p>}
    </section>}
    {stage === "prompt" && <section className="ai-plan-stage">
      <span className="pill">Step 2 of 4</span><h2>Review prompt</h2>
      <Field label="Editable AI prompt"><textarea rows={14} value={prompt} onChange={(event) => setPrompt(event.target.value)} /></Field>
      <Button variant="secondary" onClick={() => void navigator.clipboard?.writeText(prompt)}>Copy prompt</Button>
      <Button onClick={() => setStage("library")}>Next: exercise catalog</Button>
    </section>}
    {stage === "library" && <section className="ai-plan-stage">
      <span className="pill">Step 3 of 4</span><h2>Share the exercise catalog</h2>
      <p>This keeps exercise names and tracking types aligned with your IronLog library.</p>
      <Button variant="secondary" onClick={() => download(exerciseCatalogMarkdown(eligibleCatalog), "ironlog-exercise-catalog.md")}>Download exercise catalog</Button>
      <Button onClick={() => setStage("paste")}>Next: paste response</Button>
    </section>}
    {stage === "paste" && <section className="ai-plan-stage">
      <span className="pill">Step 4 of 4</span><h2>Paste AI response</h2>
      <Field label="AI response JSON"><textarea rows={12} value={raw} onChange={(event) => { setRaw(event.target.value); setError(""); }} placeholder='{"version":1,"type":"ironlog_plan",...}' /></Field>
      {error && <p role="alert" className="error">{error}</p>}
      <Button disabled={!raw.trim()} onClick={() => {
        try {
          const decoded = decodePlans(cleanedJson(raw), data.exercises);
          if (!decoded.plans.length) throw Error("No valid plan was found in the response.");
          setPreview({ plans: decoded.plans, unresolved: decoded.result.unresolved, warnings: decoded.result.warnings });
          setStage("preview");
        } catch (caught) {
          setError(caught instanceof Error ? caught.message : "Could not parse the response JSON.");
        }
      }}>Validate and preview</Button>
    </section>}
    {stage === "preview" && preview && <section className="ai-plan-stage">
      <span className="pill">Reviewed preview</span>
      <h2>{preview.plans[0].name}</h2>
      <p>{preview.plans.length} plan(s) · {unresolved.length} exercises need review</p>
      {preview.warnings.length > 0 && <div className="warning-panel" role="status">{preview.warnings.map((warning) => <p key={warning}>{warning}</p>)}</div>}
      {preview.plans.map(plan => <section key={plan.id}><h3>{plan.name}</h3>{plan.days.map(day => <div className="card" key={day.id}><strong>{day.name}</strong>{day.exercises.map(exercise => <div key={exercise.id}><p>{exercise.name} · {exercise.sets} × {exercise.reps}</p>{unresolved.some(row => row.id === exercise.id) && <><Button variant="secondary" onClick={() => setReviewRow({ id: exercise.id, name: exercise.name })}>Match or create exercise</Button><Button variant="ghost" onClick={() => setPreview({ ...preview, plans: preview.plans.map(value => ({ ...value, days: value.days.map(valueDay => ({ ...valueDay, exercises: valueDay.exercises.filter(row => row.id !== exercise.id) })) })) })}>Remove from preview</Button></>}</div>)}</div>)}</section>)}
      {unresolved.length > 0 && <p role="status">Match, create or remove each unresolved exercise before importing. Empty days must be corrected in the JSON.</p>}
      <Button disabled={busy || !canImportAiPlans(preview.plans, data.exercises, reviewedIds)} onClick={() => void run(async () => {
        await importPlans(preview.plans.map((plan, index) => ({ ...plan, order: data.plans.length + index })));
      }, "Plan imported").then((ok) => { if (ok) { onClose(); navigate("plans"); } })}>Import reviewed plan</Button>
      <Button variant="secondary" onClick={() => setStage("paste")}>Back to paste</Button>
    </section>}
    {reviewRow && <ExercisePicker initialQuery={reviewRow.name} onClose={() => setReviewRow(undefined)} onPick={exercise => {
      setReviewedIds(ids => [...ids, exercise.id]);
      setPreview(value => value && ({ ...value, plans: value.plans.map(plan => ({ ...plan, days: plan.days.map(day => ({ ...day, exercises: day.exercises.map(row => row.id === reviewRow.id ? { ...row, exerciseId: exercise.id, name: exercise.name } : row) })) })) }));
      setReviewRow(undefined);
    }} />}
  </Sheet>;
}
