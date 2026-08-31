import { useState } from "react";
import { useApp } from "../ui/context";
import { saveProfile, savePlan } from "../data/store";
import { instantiatePlan } from "../domain/plans";
import {
  Button,
  Field,
  Fox,
  NumberWheel,
  Progress,
  asset,
} from "../ui/components";
import templates from "../generated/templates.json";
import type { Plan } from "../domain/types";
export function Onboarding() {
  const { data, run, busy } = useApp();
  const [draft, setDraft] = useState(data.profile);
  const [starter, setStarter] = useState("");
  const step = data.profile.onboardingStep;
  const change = (p: Partial<typeof draft>) => setDraft({ ...draft, ...p });
  const next = () =>
    run(async () => {
      if (step === 3) {
        let activePlanId: string | undefined;
        const source = templates.find((p) => p.id === starter);
        if (source) {
          const p = instantiatePlan(source as Plan, {
            order: data.plans.length,
          });
          await savePlan(p);
          activePlanId = p.id;
        }
        await saveProfile({
          ...draft,
          activePlanId,
          onboarded: true,
          onboardingStep: 4,
        });
      } else await saveProfile({ ...draft, onboardingStep: step + 1 });
    });
  return (
    <main className="onboarding">
      <header className="onboard-brand">
        <img src={asset("ironlog-logo.svg")} alt="IronLog" />
        <span>IRONLOG</span>
        <small>{step + 1} / 4</small>
      </header>
      <Progress value={step + 1} max={4} label="Onboarding progress" />
      {step === 0 && (
        <>
          <Fox pose="07_determined" />
          <h1>Make it your own.</h1>
          <p>
            A training log that remembers the work. Your profile stays on this
            device.
          </p>
          <Field label="Your name">
            <input
              autoComplete="given-name"
              maxLength={60}
              value={draft.name}
              onChange={(e) => change({ name: e.target.value })}
              placeholder="What should we call you?"
            />
          </Field>
          <div className="two-col">
            <NumberWheel
              label="Age"
              value={draft.age}
              min={13}
              max={100}
              onChange={(age) => change({ age })}
            />
            <NumberWheel
              label="Height (cm)"
              value={draft.heightCm}
              min={100}
              max={250}
              onChange={(heightCm) => change({ heightCm })}
            />
          </div>
          <NumberWheel
            label="Bodyweight (kg)"
            value={draft.weightKg}
            min={20}
            max={400}
            step={0.5}
            onChange={(weightKg) => change({ weightKg })}
          />
        </>
      )}
      {step === 1 && (
        <>
          <h1>What are you training for?</h1>
          <p>
            These choices guide recommendations. They don’t award XP or a grade.
          </p>
          <Field label="Goal">
            <select
              value={draft.goal}
              onChange={(e) => change({ goal: e.target.value })}
            >
              {["General Fitness", "Hypertrophy", "Strength", "Endurance"].map(
                (x) => (
                  <option key={x}>{x}</option>
                ),
              )}
            </select>
          </Field>
          <Field label="Experience">
            <select
              value={draft.experience}
              onChange={(e) => change({ experience: e.target.value })}
            >
              <option value="beginner">Getting started</option>
              <option value="intermediate">Training consistently</option>
              <option value="advanced">Experienced lifter</option>
            </select>
          </Field>
          <NumberWheel
            label="Sessions per week"
            value={draft.weeklyGoal}
            min={1}
            max={7}
            onChange={(weeklyGoal) => change({ weeklyGoal })}
          />
          <NumberWheel
            label="Session length (minutes)"
            value={draft.sessionMinutes}
            min={15}
            max={180}
            step={5}
            onChange={(sessionMinutes) => change({ sessionMinutes })}
          />
        </>
      )}
      {step === 2 && (
        <>
          <h1>Your training, your pace.</h1>
          <Field label="Coaching preference">
            <select
              value={draft.coaching}
              onChange={(e) => change({ coaching: e.target.value })}
            >
              <option value="balanced">Balanced guidance</option>
              <option value="conservative">Conservative progression</option>
              <option value="performance">Performance focused</option>
            </select>
          </Field>
          <Field label="Weight units">
            <select
              value={draft.unit}
              onChange={(e) => change({ unit: e.target.value as "kg" | "lb" })}
            >
              <option value="kg">Kilograms</option>
              <option value="lb">Pounds</option>
            </select>
          </Field>
          <Field label="Set effort">
            <select
              value={draft.effort}
              onChange={(e) =>
                change({ effort: e.target.value as "rpe" | "rir" })
              }
            >
              <option value="rpe">RPE — effort out of 10</option>
              <option value="rir">RIR — reps left in reserve</option>
            </select>
          </Field>
          <div className="notice">
            <h3>Training-profile preview</h3>
            <p>
              {draft.weeklyGoal} sessions · {draft.goal} ·{" "}
              {draft.sessionMinutes} minutes
            </p>
            <p>Level 1 · 0 XP · Uncalibrated</p>
          </div>
          <p className="muted">
            Recovery scores are estimates, not medical advice. Pain takes
            priority over any score.
          </p>
        </>
      )}
      {step === 3 && (
        <>
          <h1>A starting point.</h1>
          <p>
            Choose a program or build your own later. Every exercise and note is
            editable.
          </p>
          <Field label="Starter program">
            <select
              value={starter}
              onChange={(e) => setStarter(e.target.value)}
            >
              <option value="">I’ll choose later</option>
              {templates.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} · {p.days.length} days
                </option>
              ))}
            </select>
          </Field>
          {starter && (
            <p>{templates.find((p) => p.id === starter)?.description}</p>
          )}
          <div className="notice">
            <h3>Local means yours to look after.</h3>
            <p>
              Back up regularly from Settings. Browser storage can be removed by
              the system. Safari and a Home Screen installation may keep
              separate data.
            </p>
          </div>
          <p>
            Reliable locked-screen alarms and Android integrations aren’t
            available in the browser.
          </p>
        </>
      )}
      <div className="onboard-actions">
        {step > 0 && (
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => run(() => saveProfile({ onboardingStep: step - 1 }))}
          >
            Back
          </Button>
        )}
        <Button
          disabled={busy || (step === 0 && !draft.name.trim())}
          onClick={next}
        >
          {busy ? "Saving…" : step === 3 ? "Start training" : "Continue"}
        </Button>
      </div>
      <a href={`${import.meta.env.BASE_URL}#privacy`}>
        Privacy & local storage
      </a>
    </main>
  );
}
