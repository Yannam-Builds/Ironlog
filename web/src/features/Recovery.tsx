import { useState } from "react";
import { useApp, navigate, hasTrainingHistory } from "../ui/context";
import { Button, Field, Sheet, Grade, Progress, asset } from "../ui/components";
import { BodyMap } from "../ui/BodyMap";
import { newId, saveCheckin, completeRecoveryCircuit } from "../data/store";
import { isoWeekKey } from "../domain/dates";
export function Recovery() {
  const { data, derived: d, run, busy } = useApp();
  const [side, setSide] = useState<"front" | "back">("front");
  const [selected, setSelected] = useState("");
  const [checkin, setCheckin] = useState(false);
  const [sleep, setSleep] = useState(3);
  const [energy, setEnergy] = useState(3);
  const [soreness, setSoreness] = useState(3);
  const [pain, setPain] = useState<string[]>([]);
  const hasHistory = hasTrainingHistory(data.workouts);
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
        {Object.entries(d.recovery).map(([region, value]) => (
          <button
            className="list-row"
            key={region}
            onClick={() => setSelected(region)}
          >
            <strong>{region}</strong>
            <span>
              {!hasHistory ? (
                "No working-set history"
              ) : (
                <>
                  {Math.round(value)} / 100 ·{" "}
                  {value >= 90
                    ? "Ready"
                    : value >= 72
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
            {hasHistory
              ? `${Math.round(d.recovery[selected])} / 100`
              : "No working-set history yet"}
          </h3>
          <p>
            This is a modeled response to logged training, not a measurement of
            tissue recovery. Missing sets or incorrect exercise metadata can
            affect it.
          </p>
          <p>
            Use your warmup performance and symptoms to guide the session. Don’t
            train through pain because a score is high.
          </p>
        </Sheet>
      )}
      {checkin && (
        <Sheet title="Recovery check-in" onClose={() => setCheckin(false)}>
          {[
            ["Sleep", sleep, setSleep],
            ["Energy", energy, setEnergy],
            ["Soreness", soreness, setSoreness],
          ].map(([label, value, set]) => (
            <Field key={String(label)} label={`${label}: ${value} / 5`}>
              <input
                type="range"
                min="1"
                max="5"
                value={value as number}
                onChange={(e) =>
                  (set as (n: number) => void)(Number(e.target.value))
                }
              />
              <small>
                {label === "Soreness"
                  ? "1 = none · 5 = very sore"
                  : "1 = poor · 5 = excellent"}
              </small>
            </Field>
          ))}
          <fieldset>
            <legend>Pain (not ordinary soreness)</legend>
            {Object.keys(d.recovery).map((r) => (
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
  const [checked, setChecked] = useState(false);
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
        {[
          "Graphite",
          "Iron",
          "Steel",
          "Titanium",
          "Obsidian",
          "Iridium",
          "Aether",
          "Apex",
        ].map((g) => (
          <div key={g}>
            <Grade grade={g} />
            <span>{g}</span>
          </div>
        ))}
      </div>
      <h2>Earned badges</h2>
      {d.unlockedBadges.length ? (
        <div className="badge-list">
          {d.unlockedBadges.map((b) => (
            <div className="list-row" key={b}>
              <img
                src={asset("ic_forge_streak_dumbbell.png")}
                alt=""
                width="48"
                height="48"
              />
              <div>
                <strong>{b.replaceAll("_", " ")}</strong>
                <small>
                  {data.profile.badgeUnlocks[b]
                    ? new Date(
                        data.profile.badgeUnlocks[b],
                      ).toLocaleDateString()
                    : "Earned"}
                </small>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p>
          First proof awaits. Badges are earned from your training, never from
          onboarding answers.
        </p>
      )}
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
            A low-intensity recovery check-in can preserve your weekly rhythm
            once per ISO week. It does not create a workout or award working-set
            volume.
          </p>
          <ol>
            <li>Take a comfortable easy walk.</li>
            <li>Move gently through comfortable ranges.</li>
            <li>Check in with your energy and soreness.</li>
          </ol>
          <label className="check-label">
            <input
              type="checkbox"
              checked={checked}
              onChange={(e) => setChecked(e.target.checked)}
            />
            I completed my recovery activity.
          </label>
          <Button
            disabled={busy || !checked}
            onClick={() =>
              run(
                () => completeRecoveryCircuit(),
                "Recovery Circuit recorded",
              ).then((ok) => {
                if (ok) setCircuit(false);
              })
            }
          >
            Record recovery circuit
          </Button>
        </Sheet>
      )}
    </>
  );
}
