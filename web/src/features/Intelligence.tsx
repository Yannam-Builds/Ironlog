import { useState } from "react";
import { useApp, navigate, hasTrainingHistory } from "../ui/context";
import { manualPlanPrompt } from "../domain/manual-prompt";
import { Button, Field } from "../ui/components";
export function Intelligence() {
  const { data, derived: d, run } = useApp();
  const [equipment, setEquipment] = useState("Barbell, dumbbells, cables");
  const [limitations, setLimitations] = useState("");
  const p = data.profile;
  const prompt = manualPlanPrompt(p, equipment, limitations);
  return (
    <>
      <h1>Training intelligence</h1>
      <section className="card">
        <h2>Your next session</h2>
        <p>
          {hasTrainingHistory(data.workouts)
            ? `${d.limitingRegion} is currently the limiting region in your training estimate. Review that region and how you feel before selecting the next session.`
            : "Complete a session to give recovery guidance a training history to work with."}
        </p>
        <Button variant="secondary" onClick={() => navigate("recovery")}>
          Review recovery
        </Button>
      </section>
      <h2>Program insights</h2>
      {data.plans.map((plan) => (
        <section className="insight" key={plan.id}>
          <h3>{plan.name}</h3>
          <p>
            {plan.days.length} training days ·{" "}
            {plan.days.reduce(
              (n, day) =>
                n +
                day.exercises
                  .filter((e) => !e.isWarmup)
                  .reduce((s, e) => s + e.sets, 0),
              0,
            )}{" "}
            planned working sets per rotation
          </p>
          {plan.days.map((day) => (
            <div className="list-row" key={day.id}>
              <strong>{day.name}</strong>
              <span>
                {day.exercises
                  .filter((e) => !e.isWarmup)
                  .reduce((n, e) => n + e.sets, 0)}{" "}
                sets
              </span>
            </div>
          ))}
        </section>
      ))}
      <section>
        <h2>Build a plan with an external AI</h2>
        <p>
          You control what leaves this browser. Review the prompt, copy it into
          your preferred AI, then import the returned JSON in Plans. IronLog
          sends nothing automatically.
        </p>
        <Field label="Available equipment">
          <input
            value={equipment}
            onChange={(e) => setEquipment(e.target.value)}
          />
        </Field>
        <Field label="Preferences and limitations (optional)">
          <textarea
            value={limitations}
            onChange={(e) => setLimitations(e.target.value)}
          />
        </Field>
        <Field label="Review your prompt">
          <textarea rows={12} readOnly value={prompt} />
        </Field>
        <Button
          onClick={() =>
            run(() => navigator.clipboard.writeText(prompt), "Prompt copied")
          }
        >
          Copy prompt
        </Button>
        <Button variant="secondary" onClick={() => navigate("plans")}>
          Go to Plans to import the response
        </Button>
        <p className="muted">
          External AI output can be wrong. Review exercise selection, volume,
          and technique cues before training. No cloud API key is stored or
          requested here.
        </p>
      </section>
    </>
  );
}
