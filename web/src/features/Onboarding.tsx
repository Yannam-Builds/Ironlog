import { useLayoutEffect, useRef, useState } from "react";
import { useApp } from "../ui/context";
import { completeOnboarding, saveProfile } from "../data/store";
import { instantiatePlan } from "../domain/plans";
import { Button, Field, Fox, NumberWheel, Progress, asset } from "../ui/components";
import templates from "../generated/templates.json";
import type { Plan, Profile } from "../domain/types";
import { calculateOnboardingBaseline } from "../domain/onboarding-baseline";

const stages = ["Welcome", "Profile", "Baseline", "Training level", "Schedule", "Goal", "Coaching", "Capabilities", "Calibration", "Starter plan"];
const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const progressionOptions = [
  ["LINEAR", "Structured progression", "Build load and reps through repeatable sessions."],
  ["DOUBLE_PROGRESSION", "Volume first", "Own the rep range before increasing load."],
  ["AUTOREGULATED", "Readiness guided", "Use recent effort and recovery to frame each step."],
] as const;
const goals = [
  ["STRENGTH", "Strength", "Move more weight with repeatable technique."],
  ["HYPERTROPHY", "Muscle growth", "Build size through productive weekly volume."],
  ["GENERAL_FITNESS", "General fitness", "Blend strength, conditioning and health."],
] as const;
const numberValue = (value: string) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.round(parsed)) : 0;
};

export function Onboarding() {
  const { data, run, busy } = useApp();
  const [draft, setDraft] = useState(data.profile);
  const [starter, setStarter] = useState("");
  const step = Math.min(9, data.profile.onboardingStep);
  const content = useRef<HTMLElement>(null);
  const baselinePreview = calculateOnboardingBaseline(draft, 0);

  useLayoutEffect(() => {
    const heading = content.current?.querySelector("h1");
    heading?.setAttribute("tabindex", "-1");
    heading?.focus({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: "instant" });
  }, [step]);

  const change = (partial: Partial<Profile>) => setDraft((current) => ({ ...current, ...partial }));
  const persistStep = (next: number) => run(() => saveProfile({ ...draft, onboardingStep: next }));
  const finish = () => {
    const source = templates.find((plan) => plan.id === starter);
    const plan = source ? instantiatePlan(source as Plan, { order: data.plans.length }) : undefined;
    return run(() => completeOnboarding(draft, plan));
  };
  const explore = () => run(() => completeOnboarding({
    name: "Athlete",
    trainingAgeMonths: 0,
    hasPastTraining: false,
    onboardingBodyweightKg: undefined,
    baselinePushups: 0,
    baselinePullups: 0,
    baselineBenchKg: 0,
    baselineLatPulldownKg: 0,
    baselineMileRunSeconds: 0,
    badgeUnlocks: {},
  }));

  return (
    <main className="onboarding" ref={content}>
      <header className="onboard-brand">
        <img src={asset("ironlog-logo.svg")} alt="IronLog" />
        <span>IRONLOG</span>
        <small>{step === 0 ? stages[step] : `${step} / 9 · ${stages[step]}`}</small>
      </header>
      <Progress value={step} max={9} label="Onboarding progress" />
      <div className="onboarding-step" key={step}>
        {step === 0 && <>
          <Fox pose="07_determined" />
          <h1>Train with evidence. Progress like a game.</h1>
          <p>IronLog turns your recorded training into adaptive programming, recovery guidance and a progression ledger. Self-reported history stays clearly marked until your workouts add proof.</p>
          <Button disabled={busy} onClick={() => persistStep(1)}>Build my training system</Button>
          <Button variant="ghost" disabled={busy} onClick={explore}>Explore with sensible defaults</Button>
        </>}

        {step === 1 && <>
          <h1>What should your ledger call you?</h1>
          <p>This stays on this device and appears on training summaries.</p>
          <Field label="Your name"><input autoFocus maxLength={30} value={draft.name} onChange={(event) => change({ name: event.target.value })} /></Field>
          <Button disabled={busy || !draft.name.trim()} onClick={() => persistStep(2)}>{draft.name.trim() ? `Continue as ${draft.name.trim()}` : "Enter a name to continue"}</Button>
        </>}

        {step === 2 && <>
          <h1>Tell us where training begins.</h1>
          <p>These answers seed a reduced-trust starting profile. They never create completed workouts or claim verified performance.</p>
          <div className="two-col">
            <NumberWheel label="Year of birth" value={draft.yearOfBirth} min={1900} max={new Date().getFullYear()} onChange={(yearOfBirth) => change({ yearOfBirth })} />
            <NumberWheel label="Bodyweight (kg)" value={draft.onboardingBodyweightKg ?? draft.weightKg} min={20} max={500} onChange={(onboardingBodyweightKg) => change({ onboardingBodyweightKg, weightKg: onboardingBodyweightKg })} />
          </div>
          <NumberWheel label="Training age (months)" value={draft.trainingAgeMonths} min={0} max={960} onChange={(trainingAgeMonths) => change({ trainingAgeMonths, hasPastTraining: trainingAgeMonths > 0 })} />
          <label className="check-label"><input type="checkbox" checked={draft.hasGymAccess} onChange={(event) => change({ hasGymAccess: event.target.checked })} />I have gym or resistance-equipment access</label>
          <div className="onboarding-baseline-grid">
            {[
              ["Push-ups", "baselinePushups"], ["Pull-ups", "baselinePullups"],
              ["Bench press (kg)", "baselineBenchKg"], ["Lat pulldown (kg)", "baselineLatPulldownKg"],
              ["One-mile time (seconds)", "baselineMileRunSeconds"],
            ].map(([label, key]) => <Field label={label} key={key}><input type="number" min="0" value={draft[key as keyof Profile] as number} onChange={(event) => change({ [key]: numberValue(event.target.value) } as Partial<Profile>)} /></Field>)}
          </div>
          <section className="notice calibration-preview">
            <h2>Self-reported estimate</h2>
            <strong>{baselinePreview.grade} provisional rank</strong>
            <span>{baselinePreview.xp.toLocaleString()} XP</span>
            <p>Performance checks are optional. Leave an unknown result at zero; verified logs refine every signal later.</p>
          </section>
          <Button disabled={busy} onClick={() => persistStep(3)}>Use this baseline</Button>
        </>}

        {step === 3 && <>
          <h1>How should progression begin?</h1><p>Choose the closest fit. Verified training will refine it.</p>
          <div className="onboarding-options">{progressionOptions.map(([value, label, description]) => <button key={value} type="button" aria-pressed={draft.progressionStyle === value} onClick={() => change({ progressionStyle: value })}><strong>{label}</strong><span>{description}</span></button>)}</div>
          <Button disabled={busy} onClick={() => persistStep(4)}>Use this progression</Button>
        </>}

        {step === 4 && <>
          <h1>Choose days you can actually protect.</h1><p>These days set Home’s weekly rhythm and session target.</p>
          <fieldset className="weekday-grid"><legend>Training days</legend>{weekdays.map((day, index) => {
            const selected = draft.selectedTrainingDays.includes(index);
            return <label key={day}><input type="checkbox" aria-label={day} checked={selected} onChange={() => {
              const next = selected ? draft.selectedTrainingDays.filter((value) => value !== index) : [...draft.selectedTrainingDays, index].sort();
              if (next.length) change({ selectedTrainingDays: next, weeklyGoal: next.length });
            }} /><span>{day.slice(0, 3)}</span></label>;
          })}</fieldset>
          <Field label="Weight display"><select value={draft.unit} onChange={(event) => change({ unit: event.target.value as "kg" | "lb" })}><option value="kg">Kilograms</option><option value="lb">Pounds</option></select></Field>
          <Button disabled={busy} onClick={() => persistStep(5)}>Save weekly rhythm</Button>
        </>}

        {step === 5 && <>
          <h1>What should the plan optimize first?</h1><p>Your choice frames rep ranges, rest and training insights.</p>
          <div className="onboarding-options">{goals.map(([value, label, description]) => <button key={value} type="button" aria-pressed={draft.goalMode === value} onClick={() => change({ goalMode: value, goal: label })}><strong>{label}</strong><span>{description}</span></button>)}</div>
          <Button disabled={busy} onClick={() => persistStep(6)}>Use this goal</Button>
        </>}

        {step === 6 && <>
          <h1>Local by default. Cloud only by choice.</h1><p>Recovery, progression, suggestions and gamification work without an account or API key. Optional cloud setup remains available in Settings.</p>
          <section className="notice"><h2>Local coaching is active</h2><p>Your training record stays in this browser.</p></section>
          <Button disabled={busy} onClick={() => persistStep(7)}>Continue with local coaching</Button>
        </>}

        {step === 7 && <>
          <h1>Browser capabilities are optional.</h1><p>Nothing here blocks training. You can change supported access later.</p>
          <section className="card"><h2>Photos</h2><p>Choose camera or photo files only when you add a progress photo.</p></section>
          <section className="card"><h2>Notifications</h2><p>Browser delivery depends on platform support and is never guaranteed.</p></section>
          <Button disabled={busy} onClick={() => persistStep(8)}>Continue without integrations</Button>
        </>}

        {step === 8 && <>
          <h1>Your provisional profile is ready.</h1><p>Self-reported history seeds a reduced-trust baseline. Verified workouts add proof without double counting earlier sessions.</p>
          <section className="notice calibration-preview"><strong>{baselinePreview.grade} provisional rank</strong><span>{baselinePreview.xp.toLocaleString()} XP</span><span>{baselinePreview.estimatedLifetimeSessions.toLocaleString()} estimated lifetime sessions</span></section>
          <Button disabled={busy} onClick={() => persistStep(9)}>Save my baseline</Button>
        </>}

        {step === 9 && <>
          <h1>Start with structure, not a blank page.</h1><p>Pick a template or enter IronLog without one. Plans stay editable.</p>
          <Field label="Starter program"><select value={starter} onChange={(event) => setStarter(event.target.value)}><option value="">I’ll choose later</option>{templates.map((plan) => <option key={plan.id} value={plan.id}>{plan.name} · {plan.days.length} days</option>)}</select></Field>
          {starter && <p>{templates.find((plan) => plan.id === starter)?.description}</p>}
          <Button disabled={busy} onClick={finish}>Start training</Button>
        </>}
      </div>
      {step > 0 && <div className="onboard-actions"><Button variant="secondary" disabled={busy} onClick={() => persistStep(Math.max(0, step - 1))}>Back</Button></div>}
      <a href={`${import.meta.env.BASE_URL}#privacy`} target="_top">Privacy & local storage</a>
    </main>
  );
}
