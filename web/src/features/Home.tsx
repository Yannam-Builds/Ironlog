import { useApp, navigate, hasTrainingHistory } from "../ui/context";
import { Button, Fox, Grade, Icon, Progress } from "../ui/components";
import { BodyMap } from "../ui/BodyMap";
import { startWorkout } from "../data/store";
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
  const active = data.workouts.find((workout) => workout.status === "active");
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
  const proofHeadline = active
    ? "Finish what you started"
    : !plan
      ? "Set your proof loop"
      : d.weeklyCount >= p.weeklyGoal
        ? "Weekly proof secured"
        : "Your next proof is ready";
  const proofDetail = active
    ? `${active.name} is saved and ready to resume.`
    : !plan
      ? "Pick a plan so Home can drive the next session automatically."
      : d.weeklyCount >= p.weeklyGoal
        ? "Your weekly goal is complete. Keep building when recovery supports it."
        : `${day?.name ?? plan.name} is ready when you are.`;
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
            <h2>{proofHeadline}</h2>
            <p>{proofDetail}</p>
          </div>
          <Fox pose="20_clipboard" />
        </div>
        <div className="daily-proof-meta">
          <span>{d.grade} · Level {d.level}</span>
          <button className="text-button" onClick={() => navigate("ledger")}>
            {d.streak ? `${d.streak}d streak · Ledger` : "Open Ledger"}
          </button>
        </div>
        {!plan && !active && <>
          <Button onClick={() => navigate("plans")}>Choose a program</Button>
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

      {(plan || active) && (
        <section className={`card today-card native-card${data.profile.cardShineEnabled ? " animated-card-shine" : ""}`}>
          <div className="row">
            <span className="eyebrow">Today’s workout</span>
            <Icon name="log" />
          </div>
          <h2>{active?.name ?? day?.name ?? "Make room for the work."}</h2>
          <p>{active
            ? "Your session is saved. Pick up where you left off."
            : plan && day
              ? `${plan.name} · ${day.exercises.length} exercises`
              : "Pick a program or start a freestyle session."}</p>
          <Button
            disabled={busy}
            onClick={() => active
              ? navigate("workout")
              : plan && day
                ? run(async () => {
                    await startWorkout(plan.id, day.id);
                    navigate("workout");
                  })
                : navigate("plans")}
          >
            {active ? "Resume workout" : plan ? "Start workout" : "Choose a program"}
            <Icon name="next" />
          </Button>
        </section>
      )}

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
