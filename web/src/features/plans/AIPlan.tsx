import { useState } from "react";
import { importPlans } from "../../data/store";
import { decodePlans } from "../../domain/codecs";
import { buildManualPlanPrompt, exerciseCatalogMarkdown } from "../../domain/manual-prompt";
import type { Plan } from "../../domain/types";
import { Button, Field, Sheet } from "../../ui/components";
import { download, navigate, useApp } from "../../ui/context";

type Stage = "intro" | "quiz" | "prompt" | "library" | "paste" | "preview";
const equipmentOptions = ["Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Band", "Kettlebell", "Other"];

export function AIPlan({ onClose }: { onClose: () => void }) {
  const { data, run, busy } = useApp();
  const [stage, setStage] = useState<Stage>("intro");
  const [goal, setGoal] = useState("General Fitness");
  const [days, setDays] = useState(3);
  const [minutes, setMinutes] = useState(60);
  const [equipment, setEquipment] = useState(["Barbell", "Dumbbell", "Cable", "Machine"]);
  const [limitations, setLimitations] = useState("");
  const [cardio, setCardio] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [raw, setRaw] = useState("");
  const [preview, setPreview] = useState<{ plans: Plan[]; unresolved: number; warnings: string[] }>();
  const [error, setError] = useState("");

  const goBack = () => {
    const order: Stage[] = ["intro", "quiz", "prompt", "library", "paste", "preview"];
    const index = order.indexOf(stage);
    if (index <= 0) onClose(); else setStage(order[index - 1]);
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
      <Button onClick={() => { setPrompt(buildManualPlanPrompt({ goal, daysPerWeek: days, sessionMinutes: minutes, equipment, limitations, cardioEverySession: cardio }, data.exercises)); setStage("prompt"); }}>Build prompt</Button>
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
      <Button variant="secondary" onClick={() => download(exerciseCatalogMarkdown(data.exercises), "ironlog-exercise-catalog.md")}>Download exercise catalog</Button>
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
      <p>{preview.plans[0].days.length} days · {preview.plans[0].days.reduce((sum, day) => sum + day.exercises.length, 0)} exercises · {preview.unresolved} unresolved</p>
      {preview.warnings.length > 0 && <div className="warning-panel" role="status">{preview.warnings.map((warning) => <p key={warning}>{warning}</p>)}</div>}
      {preview.plans[0].days.map((day) => <div className="card" key={day.id}><strong>{day.name}</strong>{day.exercises.map((exercise) => <p key={exercise.id}>{exercise.name} · {exercise.sets} × {exercise.reps}</p>)}</div>)}
      <Button disabled={busy} onClick={() => void run(async () => {
        await importPlans(preview.plans.map((plan, index) => ({ ...plan, order: data.plans.length + index })));
      }, "Plan imported").then((ok) => { if (ok) { onClose(); navigate("plans"); } })}>Import reviewed plan</Button>
      <Button variant="secondary" onClick={() => setStage("paste")}>Back to paste</Button>
    </section>}
  </Sheet>;
}
