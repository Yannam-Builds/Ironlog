import { useState } from "react";
import { useApp, navigate } from "../ui/context";
import { startWorkout } from "../data/store";
import { recommendedPlanDay, todayDirective } from "../domain/plan-coaching";
import { CloudSettings } from "./intelligence/CloudSettings";
import { CloudInsight } from "./intelligence/CloudInsight";
import { cloudCoachingEnabled } from "../domain/coaching-mode";
import { manualPlanPrompt } from "../domain/manual-prompt";
import { Button, Field } from "../ui/components";
import { buildTrainingIntelligence, computeProgramInsights, resolveProgressionPolicy } from "../domain/program-intelligence";
export function Intelligence() {
  const { data, derived: d, run } = useApp();
  const [equipment, setEquipment] = useState("Barbell, dumbbells, cables");
  const [limitations, setLimitations] = useState("");
  const [cloudSettings, setCloudSettings] = useState(false);
  const p = data.profile;
  const prompt = manualPlanPrompt(p, equipment, limitations);
  const intelligence = buildTrainingIntelligence(data.workouts, { goalMode: p.goalMode, weeklyGoalDays: p.weeklyGoal });
  const activePlan = data.plans.find(plan => plan.id === p.activePlanId);
  const recommended = recommendedPlanDay(activePlan, data.workouts);
  const directive = todayDirective(intelligence);
  const activeWorkout = data.workouts.find(workout => workout.status === "active");
  const latestCheckin = data.checkins.filter(c => c.at <= Date.now()).sort((a,b) => b.at-a.at)[0];
  const pain = latestCheckin && Date.now()-latestCheckin.at <= 48*3600000 ? latestCheckin.painRegions : [];
  const rules = activePlan?.progressionRules;
  const deload = rules && rules.deloadEveryWeeks > 0 && rules.currentWeek % rules.deloadEveryWeeks === 0;
  return (
    <>
      <h1>Training intelligence</h1>
      {cloudCoachingEnabled(p) ? <section className="card"><h2>Cloud AI</h2><p>Optional cloud tools run only when you ask.</p><Button variant="secondary" onClick={() => setCloudSettings(true)}>Cloud AI settings</Button></section> : <Button variant="secondary" onClick={() => setCloudSettings(true)}>Cloud AI settings</Button>}
      <CloudInsight title="Recovery advice" prompt={`Give one conservative recovery recommendation in under 60 words. Do not diagnose injuries. Recovery scores: ${JSON.stringify(d.readiness)}.`} />
      <CloudInsight title="Split suggestion" prompt={`Suggest a ${p.weeklyGoal}-day split for ${p.goal}, under 80 words. Recent trained regions: ${[...new Set(data.workouts.filter(w => w.status === "completed").slice(0, 20).flatMap(w => w.exercises.map(e => e.muscle)))].join(", ")}.`} />
      <section className="card">
        <h2>Today's directive</h2>
        <h3>{directive.title}</h3><p>{directive.reason}</p>
        <p className="muted">{d.readiness ? `Recovery estimate: ${d.readiness}/100 · ${d.state}.` : "Recovery estimate needs logged training evidence."}</p>
        {!!pain.length && <p className="warning-panel">Current pain flags: {pain.join(", ")}. Review your check-in and recovery before training.</p>}
        {recommended && <p>Next planned session: <strong>{recommended.name}</strong> · {activePlan?.name}</p>}
        <Button onClick={() => {
          if (activeWorkout) { navigate("workout"); return; }
          if (!activePlan || !recommended) { navigate("plans"); return; }
          void run(() => startWorkout(activePlan.id, recommended.id)).then(ok => { if (ok) navigate("workout"); });
        }}>{activeWorkout ? "Resume active workout" : recommended ? `Start ${recommended.name}` : "Choose a plan"}</Button>
        <Button variant="secondary" onClick={() => navigate("recovery")}>Recovery map</Button>
        <Button variant="ghost" onClick={() => navigate("plans")}>Create with AI / program insights</Button>
      </section>
      {deload && <section className="card warning-panel">
        <h2>Deload week</h2><h3>Week {rules.currentWeek} — Scheduled deload</h3>
        <p>Reduce load to 60% of working weight. Reduce working sets by about 40% (3 → 2). Keep rep ranges the same.</p>
        <Button variant="secondary" onClick={() => navigate(`plan/${activePlan!.id}`)}>Review or generate deload day</Button>
      </section>}
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
          {plan.days.map(day => <CloudInsight key={day.id} title={`Evaluate ${day.name}`} prompt={`Evaluate this session for ${p.goal} in under 70 words. Note missing movement patterns without medical claims. Day: ${day.name}. Exercises: ${day.exercises.slice(0, 12).map(exercise => exercise.name).join(", ")}. Progression policy: ${policy.label}.`} />)}
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
          and technique cues before training.
        </p>
      </section>
      {cloudSettings && <CloudSettings onClose={() => setCloudSettings(false)} />}
    </>
  );
}
