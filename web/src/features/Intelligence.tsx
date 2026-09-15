import { useState } from "react";
import { useApp, navigate, hasTrainingHistory } from "../ui/context";
import { manualPlanPrompt } from "../domain/manual-prompt";
import { Button, Field } from "../ui/components";
import { buildTrainingIntelligence, computeProgramInsights, resolveProgressionPolicy } from "../domain/program-intelligence";
export function Intelligence() {
  const { data, derived: d, run } = useApp();
  const [equipment, setEquipment] = useState("Barbell, dumbbells, cables");
  const [limitations, setLimitations] = useState("");
  const p = data.profile;
  const prompt = manualPlanPrompt(p, equipment, limitations);
  const intelligence = buildTrainingIntelligence(data.workouts, { goalMode: p.goalMode, weeklyGoalDays: p.weeklyGoal });
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
      <section className="card">
        <h2>Weekly training load</h2>
        <p className="muted">Weighted working-set equivalents against goal-adjusted reference bands.</p>
        {Object.entries(intelligence.volumeLandmarks).map(([muscle, landmark]) => <div className="list-row" key={muscle}>
          <div><strong>{muscle}</strong><small>{landmark.min}–{landmark.max} reference band</small></div>
          <span className={`pill ${landmark.status}`}>{landmark.sets} · {landmark.status}</span>
        </div>)}
        <p>Push {intelligence.movementBalance.Push}% · Pull {intelligence.movementBalance.Pull}% · Legs {intelligence.movementBalance.Legs}%</p>
      </section>
      <section className="card">
        <h2>Performance evidence</h2>
        <div className="insight-metrics"><div><strong>{intelligence.prLast30}</strong><small>PR sessions · last 30 days</small></div><div><strong>{intelligence.prTrend}</strong><small>Compared with prior 30 days</small></div></div>
        <p>{intelligence.bestWindow}</p>
        <p><strong>{intelligence.trainingAgeLabel}</strong> · {intelligence.trainingAgeTip}</p>
        {intelligence.neuralFatigue.isFlagged
          ? <p className="warning-panel">Heavy compound work appears on {intelligence.neuralFatigue.consecutiveDays} consecutive days. Consider an easier session and review recovery.</p>
          : <p className="muted">No three-day heavy-compound sequence is visible in recent valid logs.</p>}
      </section>
      <h2>Program insights</h2>
      {data.plans.map((plan) => {
        const insight = computeProgramInsights(plan, data.workouts, p.weeklyGoal);
        const policy = resolveProgressionPolicy(undefined, plan.progressionRules, p.progressionStyle);
        return <section className="insight" key={plan.id}>
          <h3>{plan.name}</h3>
          <p>{plan.days.length} training days · {plan.days.reduce((n, day) => n + day.exercises.filter((exercise) => !exercise.isWarmup).reduce((sum, exercise) => sum + exercise.sets, 0), 0)} planned working sets per rotation</p>
          <div className="insight-metrics">
            <div><strong>{insight.adherencePct}%</strong><small>Adherence</small><progress max="100" value={insight.adherencePct} /></div>
            <div><strong>{insight.consistencyPct}%</strong><small>Weeks hitting goal</small><progress max="100" value={insight.consistencyPct} /></div>
          </div>
          <p className="muted">Based on {insight.weekCount} week{insight.weekCount === 1 ? "" : "s"} · {insight.sessionsPerWeek.toFixed(1)} sessions/week</p>
          {insight.perDay.map((day) => <div className="list-row" key={day.id}><strong>{day.name}</strong><span>×{day.count}</span></div>)}
          <p><strong>{policy.label}</strong> · {policy.source.replaceAll("_", " ")}</p>
          <p>{insight.recommendation}</p>
          <Button variant="secondary" onClick={() => navigate(`plan/${plan.id}`)}>Review plan</Button>
        </section>;
      })}
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
