import { useState } from "react";
import { useApp, navigate, hasTrainingHistory } from "../ui/context";
import {
  AchievementBadge,
  Button,
  Field,
  Sheet,
  Grade,
  Progress,
  asset,
} from "../ui/components";
import { BodyMap } from "../ui/BodyMap";
import { newId, saveCheckin, completeRecoveryCircuit } from "../data/store";
import { isoWeekKey } from "../domain/dates";
import { badgeDefinitions } from "../domain/badges";
import { gradeRequirements } from "../domain/engine";
import { readinessTrend, recoverySourceLabel, recoverySuggestions, regionRecoveryEvidence, RECOVERY_WINDOWS } from "../domain/recovery-evidence";

function RecoveryTrend({ points }: { points: ReturnType<typeof readinessTrend> }) {
  const x = (index: number) => 8 + index * (284 / 13), y = (score: number) => 112 - score;
  return <section className="card recovery-trend"><span className="eyebrow">Readiness trend — 14 days</span><svg viewBox="0 0 300 120" role="img" aria-label="Fourteen day readiness trend"><rect x="0" y="52" width="300" height="60" className="trend-low" /><rect x="0" y="27" width="300" height="25" className="trend-mid" /><rect x="0" y="12" width="300" height="15" className="trend-high" />{points.slice(1).map((point, index) => { const previous = points[index]; return previous.score !== undefined && point.score !== undefined ? <line key={point.at} x1={x(index)} y1={y(previous.score)} x2={x(index + 1)} y2={y(point.score)} /> : null; })}{points.map((point, index) => point.score === undefined ? null : <circle key={point.at} cx={x(index)} cy={y(point.score)} r="3" />)}</svg><div className="row"><small>{new Date(points[0].at).toLocaleDateString(undefined, { month: "2-digit", day: "2-digit" })}</small><small>{new Date(points.at(-1)!.at).toLocaleDateString(undefined, { month: "2-digit", day: "2-digit" })}</small></div></section>;
}
export function Recovery() {
  const { data, derived: d, run, busy } = useApp();
  const latest = [...data.checkins].filter((row) => row.at <= Date.now()).sort((a, b) => b.at - a.at)[0];
  const [side, setSide] = useState<"front" | "back">("front");
  const [range, setRange] = useState<(typeof RECOVERY_WINDOWS)[number][0]>("30D");
  const [selected, setSelected] = useState("");
  const [checkin, setCheckin] = useState(false);
  const [sleep, setSleep] = useState(latest?.sleep ?? 3);
  const [energy, setEnergy] = useState(latest?.energy ?? 3);
  const [soreness, setSoreness] = useState(latest?.soreness ?? 3);
  const [pain, setPain] = useState<string[]>(latest?.painRegions ?? []);
  const [notes, setNotes] = useState(latest?.notes ?? "");
  const hasHistory = Object.keys(d.recovery).length > 0;
  const rangeDays = RECOVERY_WINDOWS.find(([label]) => label === range)?.[1] ?? 30;
  const evidence = selected ? regionRecoveryEvidence(data.workouts, selected, rangeDays) : [];
  const suggestions = recoverySuggestions(d.recovery, pain);
  const source = recoverySourceLabel(data.workouts, data.checkins, rangeDays);
  const trend = readinessTrend(data);
  return (
    <>
      <h1>Recovery map</h1>
      <div className="recovery-summary">
        <strong>
          {hasHistory ? d.readiness : "—"}
          <small>/100</small>
        </strong>
        <div>
          <h2>{hasHistory ? d.state : "No history yet"}</h2>
          <p>Whole-body training estimate</p>
        </div>
      </div>
      <p className="muted">
        Recent working sets, effort, elapsed time, and your latest check-in
        shape this estimate. An overall score doesn’t mean every muscle is
        recovered.
      </p>
      <div className="segmented recovery-ranges">{RECOVERY_WINDOWS.map(([label]) => <button key={label} aria-pressed={range === label} onClick={() => setRange(label)}>{label}</button>)}</div>
      <div className="segmented">
        <button
          aria-pressed={side === "front"}
          onClick={() => setSide("front")}
        >
          Front
        </button>
        <button aria-pressed={side === "back"} onClick={() => setSide("back")}>
          Back
        </button>
      </div>
      <div className="full-map">
        <BodyMap
          scores={hasHistory ? d.recovery : {}}
          side={side}
          onSelect={setSelected}
        />
      </div>
      <div className="region-list">
        {["Push", "Pull", "Legs", "Core", "Arms", "Shoulders"].map((region) => (
          <button
            className="list-row"
            key={region}
            onClick={() => setSelected(region)}
          >
            <strong>{region}</strong>
            <span>
              {pain.includes(region) ? "Pain flagged" : d.recovery[region] === undefined ? (
                "No mapped workload"
              ) : (
                <>
                  {Math.round(d.recovery[region])} / 100 ·{" "}
                  {d.recovery[region] >= 90
                    ? "Ready"
                    : d.recovery[region] >= 72
                      ? "Building back"
                      : "Recovering"}
                </>
              )}
            </span>
          </button>
        ))}
      </div>
      <Button onClick={() => setCheckin(true)}>How do you feel today?</Button>
      <button className="text-button" onClick={() => navigate("research")}>
        Read the methodology and limitations
      </button>
      {selected && (
        <Sheet title={`${selected} estimate`} onClose={() => setSelected("")}>
          <h3>
            {d.recovery[selected] !== undefined
              ? `${Math.round(d.recovery[selected])} / 100`
              : "No working-set history yet"}
          </h3>
          <p>
            Source: {source}
          </p>
          <p className={pain.includes(selected) ? "danger-text" : ""}>{pain.includes(selected) ? "Pain flagged: avoid painful movements; readiness is not medical clearance." : d.recovery[selected] === undefined ? "Not enough recorded workload to estimate this region." : d.recovery[selected] >= 90 ? "Train" : d.recovery[selected] >= 72 ? "Maintain" : "Back off"}</p>
          <h3>Recent contributing exercises</h3>
          {evidence.length ? evidence.slice(0, 5).map((row) => <p key={row.exerciseName}>{row.exerciseName} ({row.sessions} sessions · {row.workingSets} working sets)<br /><small>Last trained {new Date(row.latestAt).toLocaleDateString()}</small></p>) : <p>Not enough recent data.</p>}
        </Sheet>
      )}
      <RecoveryTrend points={trend} />
      <section className="card"><h2>Suggestions</h2>{suggestions.length ? suggestions.map((tip) => <p key={tip}>• {tip}</p>) : <p className="muted">Not enough recent data to generate suggestions.</p>}<Button variant="ghost" onClick={() => navigate("analytics")}>Open volume analytics</Button></section>
      {checkin && (
        <Sheet title="Recovery check-in" onClose={() => setCheckin(false)}>
          {[
            ["Sleep", sleep, setSleep],
            ["Energy", energy, setEnergy],
            ["Soreness", soreness, setSoreness],
          ].map(([label, value, set]) => (
            <Field key={String(label)} label={`${label}: ${value} / 5`}>
              <div className="score-choices" role="radiogroup" aria-label={String(label)}>{[1, 2, 3, 4, 5].map((score) => <button type="button" role="radio" aria-label={`${label}: ${score}`} aria-checked={value === score} key={score} onClick={() => (set as (n: number) => void)(score)}>{score}</button>)}</div>
              <small>
                {label === "Soreness"
                  ? "1 = none · 5 = very sore"
                  : "1 = poor · 5 = excellent"}
              </small>
            </Field>
          ))}
          <fieldset>
            <legend>Pain (not ordinary soreness)</legend>
            {["Push", "Pull", "Legs", "Core", "Arms", "Shoulders"].map((r) => (
              <label className="check-label" key={r}>
                <input
                  type="checkbox"
                  checked={pain.includes(r)}
                  onChange={(e) =>
                    setPain(
                      e.target.checked
                        ? [...pain, r]
                        : pain.filter((x) => x !== r),
                    )
                  }
                />
                {r}
              </label>
            ))}
          </fieldset>
          <Field label="Notes (optional)"><textarea placeholder="How are you feeling?" value={notes} onChange={(event) => setNotes(event.target.value)} /></Field>
          <Button
            disabled={busy}
            onClick={() =>
              run(
                () =>
                  saveCheckin({
                    id: newId(),
                    at: Date.now(),
                    sleep,
                    energy,
                    soreness,
                    painRegions: pain,
                    notes: notes.trim(),
                  }),
                "Check-in saved",
              ).then((ok) => {
                if (ok) setCheckin(false);
              })
            }
          >
            Save check-in
          </Button>
        </Sheet>
      )}
    </>
  );
}
export function Ledger() {
  const { data, derived: d, run, busy } = useApp();
  const [circuit, setCircuit] = useState(false);
  const [selectedCircuit, setSelectedCircuit] = useState<string>();
  const [circuitStarted, setCircuitStarted] = useState(false);
  const circuits = [
    { id: "push_basic", name: "Push Circuit", category: "Push", exercises: ["20 Push-ups", "15 Tricep Dips (chair)", "10 Pike Push-ups"], instructions: "3 rounds, 60 sec rest between rounds." },
    { id: "pull_basic", name: "Pull Circuit", category: "Pull", exercises: ["10 Pull-ups (or 15 Inverted Rows)", "12 Chin-ups", "20 Band Pull-Aparts"], instructions: "3 rounds, 90 sec rest between rounds." },
    { id: "core_basic", name: "Core Circuit", category: "Core", exercises: ["30 Crunches", "20 Leg Raises", "60 sec Plank", "20 Russian Twists"], instructions: "3 rounds, 45 sec rest between rounds." },
    { id: "fullbody_basic", name: "Full Body Circuit", category: "Full Body", exercises: ["20 Burpees", "20 Squats", "15 Push-ups", "10 Pull-ups", "30 sec Plank"], instructions: "4 rounds, 90 sec rest between rounds." },
  ];
  const activeCircuit = circuits.find((row) => row.id === selectedCircuit);
  const eligible =
    d.weeklyCount === data.profile.weeklyGoal - 1 &&
    !data.profile.recoveryWeeks.includes(isoWeekKey(Date.now()));
  return (
    <>
      <h1>Iron Ledger</h1>
      <section className="ledger-hero">
        <Grade grade={d.grade} size={140} />
        <h2>{d.grade}</h2>
        <p>
          Level {d.level} · {d.xp} all-time XP
        </p>
        <p className="muted">
          {d.xpBreakdown.selfReported.toLocaleString()} self-reported ·{" "}
          {d.xpBreakdown.verified.toLocaleString()} verified XP
        </p>
        <Progress value={d.levelProgress * 100} label="Level progress" />
        <small>
          {d.level === 100
            ? "Maximum level"
            : `${Math.round(d.levelProgress * d.nextLevelXp)} / ${d.nextLevelXp} to level ${d.level + 1}`}
        </small>
      </section>
      <div className="stat-pair">
        <div>
          <span>Credited sessions</span>
          <strong>{d.creditedCount}</strong>
        </div>
        <div>
          <span>Qualifying weeks</span>
          <strong>{d.qualifyingWeeks}</strong>
        </div>
      </div>
      <p>
        {d.weeklyStreak} week streak · {d.streak} day streak · Integrity{" "}
        {Math.round(d.integrity * 100)}%
      </p>
      {d.onboardingBaseline && (
        <section className="card">
          <h2>Self-reported onboarding baseline</h2>
          <p>
            {d.onboardingBaseline.estimatedLifetimeSessions.toLocaleString()}{" "}
            estimated lifetime sessions ·{" "}
            {d.onboardingBaseline.xp.toLocaleString()} provisional XP · 50%
            trust
          </p>
          <p>
            Provisional profile rank: {d.onboardingBaseline.grade}, capped at
            Titanium. Verified workout XP adds separately and never turns an
            estimate into a credited session.
          </p>
        </section>
      )}
      <h2>Training signals</h2>
      <div className="stat-pair">
        {Object.entries(d.trainingSignals).map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
      <details>
        <summary>What counts as proof?</summary>
        <p>
          A completed, nonfuture workout with at least eight working sets; three
          working sets plus twenty minutes; or ten minutes of cardio. Warmups
          never count. Imported workouts use the same rules.
        </p>
        <p>
          Grades require sustained sessions, weeks, and training tenure. Higher
          grades also require balanced training signals and integrity.
        </p>
      </details>
      <h2>Grade milestones</h2>
      <div className="grade-grid">
        {gradeRequirements.filter(([name]) => name !== "Uncalibrated").map(([g, sessions, weeks, days]) => (
          <div key={g}>
            <Grade grade={g} />
            <span>{g}</span>
            <small>{d.creditedCount >= sessions && d.qualifyingWeeks >= weeks && d.tenureDays >= days ? "Unlocked" : "Locked"}</small>
            <small>{sessions} sessions · {weeks} weeks · {days} days</small>
          </div>
        ))}
      </div>
      <h2>App badges</h2>
      <div className="badge-list">{badgeDefinitions.map((badge) => { const unlocked = d.unlockedBadges.includes(badge.id); return <div className={`list-row ${unlocked ? "" : "locked"}`} key={badge.id}><AchievementBadge id={badge.id} /><div><strong>{badge.title}</strong><small>{unlocked ? data.profile.badgeUnlocks[badge.id] ? `Unlocked ${new Date(data.profile.badgeUnlocks[badge.id]).toLocaleDateString()}` : "Unlocked" : "Locked"}</small><p>{badge.description}</p></div></div>; })}</div>
      <section className="card">
        <div className="row">
          <img
            src={asset("recovery_circuit_emblem.png")}
            alt=""
            width="72"
            height="72"
          />
          <div>
            <h2>Recovery Circuit</h2>
            <p>
              {eligible
                ? "One credited session short this week."
                : `Available when you’re exactly one session short of your ${data.profile.weeklyGoal}-session goal.`}
            </p>
          </div>
        </div>
        <Button
          variant="secondary"
          disabled={!eligible}
          onClick={() => setCircuit(true)}
        >
          Open recovery circuit
        </Button>
      </section>
      {circuit && (
        <Sheet title="Recovery Circuit" onClose={() => setCircuit(false)}>
          <p>
            Use this only when a full workout is not practical. Pick a circuit you can perform with clean technique; stop if an exercise causes pain.
          </p>
          {!circuitStarted ? <><div className="circuit-grid">{circuits.map((row) => <button key={row.id} aria-pressed={selectedCircuit === row.id} onClick={() => setSelectedCircuit(row.id)}><strong>{row.name}</strong><small>{row.category}</small>{row.exercises.map((exercise) => <span key={exercise}>• {exercise}</span>)}<small>{row.instructions}</small></button>)}</div><Button disabled={!selectedCircuit} onClick={() => setCircuitStarted(true)}>Start Circuit</Button></> : activeCircuit && <><h3>Complete this circuit:</h3>{activeCircuit.exercises.map((exercise) => <p key={exercise}>• {exercise}</p>)}<p className="muted">{activeCircuit.instructions}</p><Button disabled={busy} onClick={() => run(() => completeRecoveryCircuit(), "Recovery Circuit recorded").then((ok) => { if (ok) setCircuit(false); })}>Complete & check proof eligibility</Button><Button variant="ghost" disabled={busy} onClick={() => setCircuitStarted(false)}>Choose a different circuit</Button></>}
        </Sheet>
      )}
    </>
  );
}
