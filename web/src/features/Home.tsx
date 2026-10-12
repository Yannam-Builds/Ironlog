import { useEffect, useState } from "react";
import { NativeCardShine } from "../ui/NativeCardShine";
import { workoutElapsedSeconds } from "../domain/workout-clock";
import { dailyProof } from "../domain/daily-proof";
import { useApp, navigate, hasTrainingHistory } from "../ui/context";
import { Button, Fox, Grade, Icon, Progress, Sheet, RollingTimerText } from "../ui/components";
import { BodyMap } from "../ui/BodyMap";
import { startWorkout, discardWorkout } from "../data/store";
import {
  creditedProof,
  isWorkingSet,
  suggestPlanDay,
  workoutDurationSeconds,
} from "../domain/engine";
import { externalLoadVolume } from "../domain/tracking";
import { isoWeekKey } from "../domain/dates";

export function Home() {
  const { data, derived: d, run, busy } = useApp();
  const p = data.profile;
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const [now, setNow] = useState(Date.now());
  const active = data.workouts.find((workout) => workout.status === "active");
  useEffect(() => {
    if (!active) return;
    const tick = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(tick);
  }, [active?.id]);
  const elapsed = active ? workoutElapsedSeconds(active, now) : 0;
  const timer = elapsed >= 3600
    ? `${Math.floor(elapsed / 3600)}:${String(Math.floor(elapsed / 60) % 60).padStart(2, "0")}:${String(elapsed % 60).padStart(2, "0")}`
    : `${String(Math.floor(elapsed / 60)).padStart(2, "0")}:${String(elapsed % 60).padStart(2, "0")}`;
  const plan = data.plans.find((candidate) => candidate.id === p.activePlanId);
  const previous = data.workouts.find(
    (workout) => workout.status === "completed" && workout.planId === plan?.id,
  );
  const nextIndex = plan
    ? (plan.days.findIndex((candidate) => candidate.id === previous?.dayId) + 1) %
      Math.max(1, plan.days.length)
    : 0;
  const rotation = plan
    ? [...plan.days.slice(nextIndex), ...plan.days.slice(0, nextIndex)].filter(
        (candidate) => candidate.exercises.length,
      )
    : [];
  const hasHistory = hasTrainingHistory(data.workouts);
  const day =
    rotation[
      hasHistory
        ? suggestPlanDay(
            d.recovery,
            rotation.map((candidate) =>
              candidate.exercises.map((exercise) => exercise.name),
            ),
          )
        : 0
    ];

  const weekday = (new Date().getDay() + 6) % 7;
  const nextProtectedOffset = Array.from({ length: 7 }, (_, i) => i + 1).find((offset) => p.selectedTrainingDays.includes((weekday + offset) % 7)) ?? 1;
  const nextProtectedDate = new Date();
  nextProtectedDate.setDate(nextProtectedDate.getDate() + nextProtectedOffset);
  const rhythmBlurb = !p.selectedTrainingDays.includes(weekday)
    ? `Today is outside your protected training rhythm. Next protected day: ${nextProtectedDate.toLocaleDateString(undefined, { weekday: "long" })}. You can still train if recovery supports it.`
    : null;
  const completed = data.workouts.filter((workout) => workout.status === "completed");
  const credited = completed.filter((workout) => creditedProof(workout));
  const recentSessions = credited.filter(
    (workout) => Date.now() - workout.startedAt <= 30 * 86400000,
  ).length;
  const recentForAverage = completed.slice(0, 10);
  const averageMinutes = recentForAverage.length
    ? Math.max(
        1,
        Math.round(
          recentForAverage.reduce(
            (sum, workout) => sum + workoutDurationSeconds(workout),
            0,
          ) /
            recentForAverage.length /
            60,
        ),
      )
    : 0;
  const thisWeek = credited.filter(
    (workout) => isoWeekKey(workout.startedAt) === isoWeekKey(Date.now()),
  );
  const thisWeekSets = thisWeek.reduce(
    (total, workout) => total + workout.exercises.reduce(
      (count, exercise) => count + exercise.loggedSets.filter(
        (set) => isWorkingSet(exercise, set),
      ).length,
      0,
    ),
    0,
  );
  const thisWeekVolumeKg = thisWeek.reduce(
    (total, workout) => total + workout.exercises.reduce(
      (exerciseTotal, exercise) => exerciseTotal + exercise.loggedSets.reduce(
        (setTotal, set) => setTotal + externalLoadVolume(exercise, set),
        0,
      ),
      0,
    ),
    0,
  );
  const displayVolume = p.unit === "lb"
    ? thisWeekVolumeKg * 2.2046226218487757
    : thisWeekVolumeKg;
  const compactVolume = displayVolume >= 1000
    ? `${(displayVolume / 1000).toFixed(1)}k`
    : `${Math.round(displayVolume)}`;
  const hour = new Date().getHours();
  const greeting = hour < 5
    ? "Good night"
    : hour < 12
      ? "Good morning"
      : hour < 18
        ? "Good afternoon"
        : hour < 22
          ? "Good evening"
          : "Good night";
  const latestCheckin = data.checkins.filter(c => c.at <= now && now - c.at <= 48 * 3600000).sort((a,b) => b.at - a.at)[0];
  const proof = dailyProof(data.workouts, !!plan, d.readiness, latestCheckin?.painRegions ?? [], now);
  const shareWeeklySummary = async () => {
    const text = [
      "IronLog Weekly Summary",
      `${d.weeklyCount} workouts`,
      `${thisWeekSets} working sets`,
      `${compactVolume} ${p.unit} volume`,
      `${d.streak} day streak`,
    ].join(" · ");
    try {
      if (navigator.share) {
        await navigator.share({ title: "IronLog Weekly Summary", text });
      } else {
        await navigator.clipboard.writeText(text);
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      throw error;
    }
  };

  return (
    <>
      <header className="greeting">
        <p>{new Intl.DateTimeFormat(undefined, {
          weekday: "long",
          month: "short",
          day: "numeric",
        }).format(new Date())}</p>
        <span>{greeting}</span>
        <h1>{p.name}</h1>
      </header>

      <section className="card daily-proof native-card">
        <div className="daily-proof-main">
          <div>
            <span className="eyebrow">Daily proof</span>
            <h2>{proof.headline}</h2>
            <p>{proof.detail}</p>
          </div>
          <Fox pose={proof.fox} />
        </div>
        <div className="daily-proof-meta">
          <span>{d.grade} · Level {d.level}</span>
          <button className="text-button" onClick={() => navigate("ledger")}>
            {d.streak ? `${d.streak}d streak · Ledger` : "Open Ledger"}
          </button>
        </div>
        {(proof.route === "plans" || proof.route === "recovery") && <Button onClick={() => navigate(proof.route)}>{proof.action}</Button>}
        {!plan && !active && <>
          <Button
            variant="ghost"
            disabled={busy}
            onClick={() => run(async () => {
              await startWorkout();
              navigate("workout");
            })}
          >
            Start freestyle
          </Button>
        </>}
      </section>

      <section className={`card today-card native-card${p.cardShineEnabled ? " animated-card-shine" : ""}`}>
        {p.cardShineEnabled && <NativeCardShine />}
        <div className="row">
          <span className="eyebrow">{active ? "IN PROGRESS" : "TODAY'S WORKOUT"}</span>
          <button className="today-round-start" aria-label={active ? "Resume workout" : "Start workout"} disabled={busy} onClick={() => {
            if (active) navigate("workout");
            else void run(async () => { await startWorkout(plan?.id, day?.id); navigate("workout"); });
          }}><Icon name={active ? "resume" : "play-outline"} size={22} /></button>
        </div>
        <h2>{active?.name ?? plan?.name ?? "Start Workout"}</h2>
        <div className="today-badges">
          {active ? <><span className="today-progress-badge">● IN PROGRESS</span><RollingTimerText value={timer} /></> : <>
            <span className="today-goal-badge">{p.goalMode.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase())}</span>
            {rotation.length > 0 && <span>{rotation.length} days{averageMinutes > 0 ? ` · avg ${averageMinutes}m` : ""}</span>}
          </>}
        </div>
        {active ? <>
          <Button disabled={busy} onClick={() => navigate("workout")}>RESUME WORKOUT</Button>
          <Button variant="danger" disabled={busy} onClick={() => setConfirmDiscard(true)}>DISCARD WORKOUT</Button>
        </> : rotation.length ? <>
          <div className="today-day-carousel" aria-label="Plan days">
            {[day!, ...rotation.filter((candidate) => candidate.id !== day?.id)].map((candidate) => <button key={candidate.id} disabled={busy}
              className={candidate.id === day?.id ? "recommended" : ""}
              aria-label={`${candidate.name}, ${candidate.exercises.length} exercises${candidate.id === day?.id ? ", recommended for today" : ""}`}
              onClick={() => void run(async () => { await startWorkout(plan!.id, candidate.id); navigate("workout"); })}>
              <strong>{candidate.name}</strong><span>{candidate.exercises.length} exercises</span>
            </button>)}
          </div>
          {rhythmBlurb && <p className="today-rhythm">{rhythmBlurb}</p>}
          <Button variant="ghost" disabled={busy} onClick={() => void run(async () => { await startWorkout(); navigate("workout"); })}><Icon name="plus" size={12} />START WITHOUT PLAN</Button>
        </> : <Button disabled={busy} onClick={() => void run(async () => { await startWorkout(); navigate("workout"); })}>START WORKOUT</Button>}
      </section>
      {confirmDiscard && active && <Sheet title="Discard workout?" onClose={() => setConfirmDiscard(false)}>
        <p>This will discard your in-progress workout. Completed workout history is preserved.</p>
        <Button variant="danger" disabled={busy} onClick={() => void run(() => discardWorkout(active.id), "Workout discarded").then((ok) => { if (ok) setConfirmDiscard(false); })}>Discard workout</Button>
        <Button variant="ghost" onClick={() => setConfirmDiscard(false)}>Keep workout</Button>
      </Sheet>}

      <section className="card home-stats native-card" aria-label="Training overview">
        <div><strong>{recentSessions}</strong><span>Sessions</span></div>
        <i aria-hidden="true" />
        <div className="streak-stat"><Icon name="fire" size={14} /><strong>{d.streak}</strong><span>Streak</span></div>
        <i aria-hidden="true" />
        <div><strong>{averageMinutes ? `${averageMinutes}m` : "--"}</strong><span>Avg Min</span></div>
      </section>

      <section className="card weekly-goal native-card">
        <div className="row">
          <h2>Weekly Goal</h2>
          <strong>{d.weeklyCount} / {p.weeklyGoal}</strong>
        </div>
        <Progress value={(d.weeklyCount / Math.max(1, p.weeklyGoal)) * 100} label="Weekly goal progress" />
        <small>Goal streak: {d.weeklyStreak} week{d.weeklyStreak === 1 ? "" : "s"}</small>
      </section>

      {plan && plan.days.length > 0 && (
        <section className="this-week-days">
          <span className="eyebrow">This week</span>
          <div className="home-day-chips">
            {plan.days.map((planDay) => <span key={planDay.id}>{planDay.name}</span>)}
          </div>
        </section>
      )}

      <section className="card weekly-summary native-card">
        <div className="row">
          <span className="eyebrow">Weekly summary</span>
          <button className="text-button" onClick={() => void shareWeeklySummary()}>Share</button>
        </div>
        <div className="summary-grid">
          <div><strong>{d.weeklyCount}</strong><span>Workouts</span></div>
          <div><strong>{thisWeekSets}</strong><span>Sets</span></div>
          <div><strong>{compactVolume} {p.unit}</strong><span>Volume</span></div>
          <div><strong>{d.streak} d</strong><span>Streak</span></div>
        </div>
      </section>

      <section className="card native-card">
        <div className="section-title">
          <h2>Muscle recovery</h2>
          <button onClick={() => navigate("recovery")} className="text-button">
            Open map <Icon name="next" size={16} />
          </button>
        </div>
        <div className="recovery-summary">
          <strong>{hasHistory ? d.readiness : "—"}<small> / 100</small></strong>
          <p>{hasHistory
            ? `${d.limitingRegion} is your limiting region`
            : "No training history yet"}<br /><small>Training estimate · not a diagnosis</small></p>
        </div>
        <div className="body-pair">
          <div><BodyMap scores={hasHistory ? d.recovery : {}} /><small>Front</small></div>
          <div><BodyMap scores={hasHistory ? d.recovery : {}} side="back" /><small>Back</small></div>
        </div>
        <div className="legend"><span>● Recovering</span><span>● Building back</span><span>● Ready</span></div>
      </section>

      <button className="card ledger-link native-card" onClick={() => navigate("ledger")}>
        <Grade grade={d.grade} />
        <div>
          <span className="eyebrow">Iron Ledger</span>
          <h2>{d.grade}</h2>
          <p>Level {d.level} · {d.xp} XP · {d.streak} day streak</p>
          <Progress value={d.levelProgress * 100} label="Ledger level progress" />
        </div>
        <Icon name="next" />
      </button>

      <section>
        <div className="section-title">
          <h2>Recent work</h2>
          <button className="text-button" onClick={() => navigate("log")}>History</button>
        </div>
        {completed.slice(0, 3).map((workout) => (
          <button key={workout.id} className="list-row" onClick={() => navigate(`history/${workout.id}`)}>
            <div>
              <strong>{workout.name}</strong>
              <small>{new Date(workout.completedAt!).toLocaleDateString()} · {workout.exercises.reduce(
                (count, exercise) => count + exercise.loggedSets.filter((set) => set.kind !== "warmup").length,
                0,
              )} sets</small>
            </div>
            <Icon name="next" />
          </button>
        ))}
        {!d.creditedCount && (
          <p className="muted">Completed workouts appear here. Working sets count toward progress; warmups don’t.</p>
        )}
      </section>
    </>
  );
}
