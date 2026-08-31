import {
  useApp,
  navigate,
  displayWeight,
  formatNumber,
  hasTrainingHistory,
} from "../ui/context";
import { Button, Fox, Grade, Icon, Progress } from "../ui/components";
import { BodyMap } from "../ui/BodyMap";
import { startWorkout } from "../data/store";
import { suggestPlanDay } from "../domain/engine";
export function Home() {
  const { data, derived: d, run, busy } = useApp();
  const p = data.profile;
  const active = data.workouts.find((w) => w.status === "active");
  const plan = data.plans.find((x) => x.id === p.activePlanId);
  const previous = data.workouts.find(
    (w) => w.status === "completed" && w.planId === plan?.id,
  );
  const nextIndex = plan
    ? (plan.days.findIndex((x) => x.id === previous?.dayId) + 1) %
      Math.max(1, plan.days.length)
    : 0;
  const rotation = plan
    ? [...plan.days.slice(nextIndex), ...plan.days.slice(0, nextIndex)].filter(
        (day) => day.exercises.length,
      )
    : [];
  const hasHistory = hasTrainingHistory(data.workouts);
  const day =
    rotation[
      hasHistory
        ? suggestPlanDay(
            d.recovery,
            rotation.map((day) => day.exercises.map((e) => e.name)),
          )
        : 0
    ];
  return (
    <>
      <header className="greeting">
        <p>
          {new Intl.DateTimeFormat(undefined, {
            weekday: "long",
            month: "short",
            day: "numeric",
          }).format(new Date())}
        </p>
        <span>Good to see you,</span>
        <h1>{p.name}</h1>
      </header>
      <section className="card daily-proof">
        <div>
          <span className="eyebrow">Daily proof</span>
          <h2>
            {d.creditedCount
              ? `${d.weeklyCount} / ${p.weeklyGoal} this week`
              : "First proof awaits"}
          </h2>
          <p>
            {d.creditedCount
              ? "Consistency, written down."
              : "Your first workout starts the story."}
          </p>
        </div>
        <Fox pose="20_clipboard" />
      </section>
      <section className="card today-card">
        <div className="row">
          <span className="eyebrow">Today’s workout</span>
          <Icon name="log" />
        </div>
        <h2>{active?.name ?? day?.name ?? "Make room for the work."}</h2>
        <p>
          {active
            ? "Your session is saved. Pick up where you left off."
            : plan && day
              ? `${plan.name} · ${day?.exercises.length ?? 0} exercises`
              : "Pick a program or start a freestyle session."}
        </p>
        <Button
          disabled={busy}
          onClick={() =>
            active
              ? navigate("workout")
              : plan && day
                ? run(async () => {
                    await startWorkout(plan.id, day?.id);
                    navigate("workout");
                  })
                : navigate("plans")
          }
        >
          {active
            ? "Resume workout"
            : plan
              ? "Start workout"
              : "Choose a program"}
          <Icon name="next" />
        </Button>
        {!plan && !active && (
          <Button
            variant="ghost"
            disabled={busy}
            onClick={() =>
              run(async () => {
                await startWorkout();
                navigate("workout");
              })
            }
          >
            Start freestyle
          </Button>
        )}
      </section>
      <section className="card">
        <div className="section-title">
          <h2>Muscle recovery</h2>
          <button onClick={() => navigate("recovery")} className="text-button">
            Open map <Icon name="next" size={16} />
          </button>
        </div>
        <div className="recovery-summary">
          <strong>
            {hasHistory ? d.readiness : "—"}
            <small> / 100</small>
          </strong>
          <p>
            {hasHistory
              ? `${d.limitingRegion} is your limiting region`
              : "No training history yet"}
            <br />
            <small>Training estimate · not a diagnosis</small>
          </p>
        </div>
        <div className="body-pair">
          <div>
            <BodyMap scores={hasHistory ? d.recovery : {}} />
            <small>Front</small>
          </div>
          <div>
            <BodyMap scores={hasHistory ? d.recovery : {}} side="back" />
            <small>Back</small>
          </div>
        </div>
        <div className="legend">
          <span>● Recovering</span>
          <span>● Building back</span>
          <span>● Ready</span>
        </div>
      </section>
      <button className="card ledger-link" onClick={() => navigate("ledger")}>
        <Grade grade={d.grade} />
        <div>
          <span className="eyebrow">Iron Ledger</span>
          <h2>{d.grade}</h2>
          <p>
            Level {d.level} · {d.xp} XP · {d.streak} day streak
          </p>
          <Progress
            value={d.levelProgress * 100}
            label="Ledger level progress"
          />
        </div>
        <Icon name="next" />
      </button>
      <section>
        <div className="section-title">
          <h2>Recent work</h2>
          <button className="text-button" onClick={() => navigate("log")}>
            History
          </button>
        </div>
        {data.workouts
          .filter((w) => w.status === "completed")
          .slice(0, 3)
          .map((w) => (
            <button
              key={w.id}
              className="list-row"
              onClick={() => navigate(`history/${w.id}`)}
            >
              <div>
                <strong>{w.name}</strong>
                <small>
                  {new Date(w.completedAt!).toLocaleDateString()} ·{" "}
                  {w.exercises.reduce(
                    (n, e) =>
                      n +
                      e.loggedSets.filter((s) => s.kind !== "warmup").length,
                    0,
                  )}{" "}
                  sets
                </small>
              </div>
              <Icon name="next" />
            </button>
          ))}
        {!d.creditedCount && (
          <p className="muted">
            Completed workouts appear here. Working sets count toward progress;
            warmups don’t.
          </p>
        )}
      </section>
    </>
  );
}
