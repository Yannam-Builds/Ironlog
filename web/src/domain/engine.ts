import type {
  AppSnapshot,
  LoggedSet,
  SessionExercise,
  TrainingSignals,
  Workout,
  WarmupTarget,
} from "./types";
import { isoWeekKey, localDateKey, previousDay } from "./dates";
import { calculateOnboardingBaseline } from "./onboarding-baseline";
import muscles from "../data/native-muscles.json";

// Ported from CreditedProof.kt, IronLedgerEngine.kt, GamificationSummary.kt,
// RecoveryReadinessEngine.kt and MuscleContributionEngine.kt. No biometric inputs on web.
const clamp = (v: number, min: number, max: number) =>
  Math.max(min, Math.min(max, v));
const sum = (a: number[]) => a.reduce((s, v) => s + v, 0);
// StatsViewModel constructs HistoryEntry.date from WorkoutEntity.startedAt.
const when = (w: Workout) => w.startedAt;
export const workoutDurationSeconds = (w: Workout) =>
  w.durationSeconds ??
  Math.max(0, ((w.completedAt ?? w.startedAt) - w.startedAt) / 1000);
const duration = workoutDurationSeconds;
const isCardio = (e: SessionExercise) =>
  [
    "cardio",
    "run",
    "treadmill",
    "bike",
    "cycle",
    "rower",
    "swim",
    "elliptical",
    "conditioning",
  ].some((x) => `${e.name} ${e.muscle}`.toLowerCase().includes(x));
const nativeReps = (e: SessionExercise, s: LoggedSet) =>
  e.tracking.startsWith("duration") ? s.durationSeconds : s.reps;
export const isWorkingSet = (e: SessionExercise, s: LoggedSet) =>
  s.kind !== "warmup" && (s.weightKg > 0 || nativeReps(e, s) > 0);
const hardSets = (w: Workout) =>
  sum(
    w.exercises.map(
      (e) => e.loggedSets.filter((s) => isWorkingSet(e, s)).length,
    ),
  );
export function creditedProof(w: Workout, now = Date.now()) {
  if (w.status !== "completed" || when(w) > now) return false;
  const hard = hardSets(w);
  const cardio = sum(
    w.exercises
      .filter(isCardio)
      .map(
        (e) => sum(e.loggedSets.map((s) => Math.max(0, nativeReps(e, s)))) / 60,
      ),
  );
  return hard >= 8 || (hard >= 3 && duration(w) >= 1200) || cardio >= 10;
}
const normalize = (map: Record<string, number>) => {
  const total = sum(Object.values(map));
  return total > 0
    ? Object.fromEntries(Object.entries(map).map(([k, v]) => [k, v / total]))
    : {};
};
function detectFamily(name: string) {
  const text = name.toLowerCase();
  let best = "",
    score = 0;
  for (const rule of muscles.FAMILY_RULES)
    if (
      rule.score > score &&
      rule.patterns.some((p) => new RegExp(p).test(text))
    ) {
      best = rule.id;
      score = rule.score;
    }
  if (best) return best;
  return (
    [
      ["chest", "horizontal_press"],
      ["lat", "horizontal_pull"],
      ["back", "horizontal_pull"],
      ["squat", "squat"],
      ["quad", "squat"],
      ["deadlift", "hinge"],
      ["hinge", "hinge"],
      ["glute", "hinge"],
      ["shoulder", "vertical_press"],
      ["delt", "vertical_press"],
      ["curl", "biceps_curl"],
      ["bicep", "biceps_curl"],
      ["tricep", "triceps_pushdown"],
      ["extension", "triceps_pushdown"],
      ["calf", "calf_raise"],
      ["ab", "core_crunch"],
      ["core", "core_crunch"],
    ].find(([term]) => text.includes(term))?.[1] ?? ""
  );
}
function regionContribution(e: SessionExercise): Record<string, number> {
  const anchors = muscles.ANCHOR_OVERRIDES as Record<
      string,
      Record<string, number>
    >,
    templates = muscles.FAMILY_TEMPLATES as Record<
      string,
      Record<string, number>
    >,
    library = muscles.LIBRARY_GROUP_MAP as Record<
      string,
      Record<string, number>
    >;
  const anchor = anchors[e.name.toLowerCase().replace(/[^a-z0-9]+/g, "")];
  let fine: Record<string, number>;
  if (anchor) fine = normalize(anchor);
  else {
    const t = normalize(templates[detectFamily(e.name)] ?? {}),
      l = normalize(library[e.muscle.toLowerCase().trim()] ?? {});
    if (Object.keys(t).length && Object.keys(l).length) {
      const merged: Record<string, number> = {};
      for (const [k, v] of Object.entries(t)) merged[k] = v * 0.72;
      for (const [k, v] of Object.entries(l))
        merged[k] = (merged[k] ?? 0) + v * 0.28;
      fine = normalize(merged);
    } else fine = Object.keys(t).length ? t : l;
  }
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries(fine)) {
    const r = (muscles.FINE_MUSCLE_TO_REGION as Record<string, string>)[k];
    if (r) out[r] = (out[r] ?? 0) + v;
  }
  if (!Object.keys(out).length) {
    const m = e.muscle.toLowerCase();
    const r =
      m === "chest"
        ? "Push"
        : ["back", "lats"].includes(m)
          ? "Pull"
          : ["quads", "hamstrings", "glutes", "calves", "legs", "leg"].includes(
                m,
              )
            ? "Legs"
            : ["biceps", "triceps", "arms", "forearms"].includes(m)
              ? "Arms"
              : ["shoulders", "delts"].includes(m)
                ? "Shoulders"
                : "Core";
    out[r] = 1;
  }
  return out;
}
function exerciseFactor(e: SessionExercise) {
  const text = `${e.name} ${e.equipment}`.toLowerCase();
  const lower = [
    "squat",
    "deadlift",
    "leg press",
    "lunge",
    "split squat",
    "hinge",
  ].some((s) => text.includes(s));
  const lengthened = [
    "romanian",
    "stiff leg",
    "good morning",
    "nordic",
    "fly",
    "pullover",
  ].some((s) => text.includes(s));
  const isolation = ["curl", "extension", "raise", "pushdown", "calf"].some(
    (s) => text.includes(s),
  );
  return lower && lengthened
    ? 1.22
    : lower
      ? 1.14
      : lengthened
        ? 1.1
        : isolation
          ? 0.9
          : 1;
}
function setFactor(e: SessionExercise, s: LoggedSet) {
  const rir = s.rir ?? (s.rpe === undefined ? undefined : 10 - s.rpe);
  const effort =
    rir === undefined
      ? 0.9
      : rir <= 0
        ? 1.28
        : rir <= 1
          ? 1.16
          : rir <= 2
            ? 1.05
            : rir <= 3
              ? 0.95
              : 0.8;
  const type =
    s.kind === "failure"
      ? 1.18
      : s.kind === "drop"
        ? 1.14
        : s.kind === "amrap"
          ? 1.1
          : 1;
  return clamp(effort * type * (nativeReps(e, s) >= 12 ? 1.05 : 1), 0.65, 1.55);
}
export function readinessByRegion(
  history: Workout[],
  painFlags: string[] = [],
  now = Date.now(),
): Record<string, number> {
  const fatigue: Record<string, number> = {
    Push: 0,
    Pull: 0,
    Legs: 0,
    Core: 0,
    Arms: 0,
    Shoulders: 0,
  };
  for (const w of history) {
    if (w.status !== "completed" || when(w) > now + 300000) continue;
    const hours = Math.max(0, now - when(w)) / 3600000;
    const dose: Record<string, number> = {};
    let failure = false,
      lower = false;
    for (const e of w.exercises) {
      const sets = e.loggedSets.filter((s) => s.kind !== "warmup");
      if (!sets.length) continue;
      const fold = regionContribution(e),
        ef = exerciseFactor(e);
      if (ef > 1.08 && "Legs" in fold) lower = true;
      for (const s of sets) {
        if (
          s.kind === "failure" ||
          (s.rir !== undefined && s.rir <= 0) ||
          (s.rpe !== undefined && s.rpe >= 10)
        )
          failure = true;
        for (const [r, f] of Object.entries(fold))
          dose[r] = (dose[r] ?? 0) + f * setFactor(e, s) * ef;
      }
    }
    const half = clamp(
      18 *
        (1 + (failure ? 0.28 : 0)) *
        (1 + (lower ? 0.12 : 0)) *
        (1 + clamp(sum(Object.values(dose)) / 24, 0, 0.3)),
      16,
      36,
    );
    const remaining =
      0.35 * Math.exp((-Math.LN2 * hours) / 8) +
      0.65 * Math.exp((-Math.LN2 * hours) / half);
    for (const [r, d] of Object.entries(dose)) {
      const deficit = (1 - Math.exp(-d / 3.5)) * remaining;
      fatigue[r] = 1 - (1 - (fatigue[r] ?? 0)) * (1 - deficit);
    }
  }
  return Object.fromEntries(
    Object.entries(fatigue).map(([r, f]) => [
      r,
      painFlags.includes(r) ? 0 : clamp(1 - f, 0.05, 1) * 100,
    ]),
  );
}
const grades: [string, number, number, number][] = [
  ["Uncalibrated", 0, 0, 0],
  ["Graphite", 4, 2, 14],
  ["Iron", 12, 3, 28],
  ["Steel", 36, 8, 90],
  ["Titanium", 80, 20, 180],
  ["Obsidian", 160, 40, 365],
  ["Iridium", 300, 90, 730],
  ["Aether", 450, 140, 1095],
  ["Apex", 650, 200, 1460],
];
export function deriveSnapshot(snapshot: AppSnapshot, now = Date.now()) {
  const storedOnboardingBaseline = snapshot.profile.onboarded
    ? (snapshot.profile.ledgerBaseline ??
      calculateOnboardingBaseline(snapshot.profile, 0))
    : undefined;
  const history = snapshot.workouts
    .filter((w) => w.status === "completed" && when(w) <= now)
    .sort((a, b) => when(a) - when(b));
  const qualified = history.filter((w) => creditedProof(w, now));
  const baselineOverlap = storedOnboardingBaseline
    ? qualified.filter((w) => when(w) <= storedOnboardingBaseline.seededAt)
        .length
    : 0;
  const onboardingBaseline = storedOnboardingBaseline
    ? {
        ...storedOnboardingBaseline,
        xp:
          Math.max(
            0,
            storedOnboardingBaseline.estimatedLifetimeSessions -
              baselineOverlap,
          ) * 20,
      }
    : undefined;
  const daily: Record<string, number> = {};
  for (const w of qualified) {
    const k = localDateKey(when(w));
    daily[k] = (daily[k] ?? 0) + 1;
  }
  const integrity = clamp(
    1 -
      Math.max(0, Math.max(0, ...Object.values(daily)) - 2) * 0.12 -
      qualified.filter((w) => duration(w) >= 1 && duration(w) < 600).length *
        0.03,
    0.35,
    1,
  );
  let verifiedXp = 0;
  const verifiedPrWorkouts = new Set<string>();
  const bestPerformance: Record<string, number> = {};
  const prs: Record<
    string,
    {
      exerciseId: string;
      name: string;
      weightKg: number;
      reps: number;
      oneRmKg: number;
    }
  > = {};
  for (const w of qualified) {
    const count = daily[localDateKey(when(w))];
    const trust = integrity * (count <= 1 ? 1 : count === 2 ? 0.45 : 0);
    verifiedXp += Math.round((40 + Math.min(18, hardSets(w)) * 2) * trust);
    const sessionBest: Record<string, number> = {};
    for (const e of w.exercises) {
      const key = e.exerciseId || e.name.toLowerCase();
      for (const s of e.loggedSets.filter((s) => isWorkingSet(e, s))) {
        const perf =
          isCardio(e) || s.weightKg <= 0
            ? nativeReps(e, s)
            : s.weightKg * (1 + nativeReps(e, s) / 30);
        sessionBest[key] = Math.max(sessionBest[key] ?? 0, perf);
      }
    }
    for (const [k, v] of Object.entries(sessionBest)) {
      if (
        bestPerformance[k] !== undefined &&
        v > bestPerformance[k] * 1.025 &&
        trust >= 0.75
      ) {
        verifiedXp += 35;
        verifiedPrWorkouts.add(w.id);
      }
      bestPerformance[k] = Math.max(bestPerformance[k] ?? 0, v);
    }
  }
  let volumeKg = 0;
  for (const w of history)
    for (const e of w.exercises)
      for (const s of e.loggedSets) {
        if (s.kind === "warmup" || e.tracking !== "weight_reps") continue;
        volumeKg += s.weightKg * s.reps;
        if (s.weightKg <= 0 || s.reps <= 0) continue;
        const one = s.weightKg * (1 + s.reps / 30);
        const key = e.exerciseId || e.name.toLowerCase();
        if (!prs[key] || one > prs[key].oneRmKg)
          prs[key] = {
            exerciseId: key,
            name: e.name,
            weightKg: s.weightKg,
            reps: s.reps,
            oneRmKg: one,
          };
      }
  const selfReportedXp = onboardingBaseline?.xp ?? 0;
  const xp = selfReportedXp + verifiedXp;
  let level = 1,
    remaining = xp;
  while (level < 100 && remaining >= 125 * level * level) {
    remaining -= 125 * level * level;
    level++;
  }
  const nextLevelXp = level >= 100 ? 0 : 125 * level * level;
  const weeks = new Set(qualified.map((w) => isoWeekKey(when(w))));
  const tenureDays = qualified.length
    ? Math.max(
        0,
        Math.floor((when(qualified.at(-1)!) - when(qualified[0])) / 86400000),
      )
    : 0;
  const working = qualified.flatMap((w) =>
    w.exercises.flatMap((e) =>
      e.loggedSets.filter((s) => isWorkingSet(e, s)).map((s) => ({ e, s })),
    ),
  );
  const toStat = (v: number, scale: number) =>
    v <= 0 ? 0 : clamp(Math.round(1 + Math.log(1 + v / scale) * 180), 1, 999);
  const strength = toStat(
    Math.max(
      0,
      ...working
        .filter(
          ({ e, s }) => !isCardio(e) && s.weightKg > 0 && nativeReps(e, s) > 0,
        )
        .map(({ e, s }) => s.weightKg * (1 + nativeReps(e, s) / 30)),
    ),
    120,
  );
  const power = toStat(
    working.filter(
      ({ e, s }) =>
        !isCardio(e) &&
        s.weightKg > 0 &&
        nativeReps(e, s) >= 1 &&
        nativeReps(e, s) <= 5,
    ).length,
    20,
  );
  const hypertrophy = toStat(working.length, 80);
  const endurance = toStat(
    sum(
      working
        .filter(({ e }) => isCardio(e))
        .map(({ e, s }) => Math.max(0, nativeReps(e, s)) / 60),
    ) +
      qualified.filter((w) => duration(w) >= 45 * 60).length * 2,
    20,
  );
  const agility = toStat(
    working.filter(({ e }) => {
      const name = e.name.toLowerCase();
      const category = `${e.muscle} ${e.equipment}`.toLowerCase();
      return (
        ["jump", "lunge", "single", "carry", "crawl"].some((term) =>
          name.includes(term),
        ) || category.includes("conditioning")
      );
    }).length,
    20,
  );
  const discipline = toStat(
    weeks.size * 4 +
      working.filter(({ s }) => s.rpe !== undefined || s.rir !== undefined)
        .length,
    50,
  );
  const recoverySignal = toStat(
    weeks.size * 3 +
      qualified.filter((w) => duration(w) >= 20 * 60 && duration(w) <= 120 * 60)
        .length,
    50,
  );
  const verifiedSignals: TrainingSignals = {
    strength,
    power,
    hypertrophy,
    endurance,
    agility,
    discipline,
    recovery: recoverySignal,
  };
  const trainingSignals: TrainingSignals = Object.fromEntries(
    Object.entries(verifiedSignals).map(([key, value]) => [
      key,
      Math.max(
        value,
        onboardingBaseline?.stats[key as keyof TrainingSignals] ?? 0,
      ),
    ]),
  ) as unknown as TrainingSignals;
  const balance = (strength + hypertrophy + discipline) / 3;
  const verifiedGrade = grades
    .filter(
      ([, sessions, weeksNeeded, days], index) =>
        qualified.length >= sessions &&
        weeks.size >= weeksNeeded &&
        tenureDays >= days &&
        (index < 5 || integrity >= 0.88) &&
        (index < 6 || balance >= 300) &&
        (index < 8 || integrity >= 0.95),
    )
    .at(-1)![0];
  const grade = onboardingBaseline
    ? grades.findIndex(([name]) => name === onboardingBaseline.grade) >
      grades.findIndex(([name]) => name === verifiedGrade)
      ? onboardingBaseline.grade
      : verifiedGrade
    : verifiedGrade;
  const dates = new Set(qualified.map((w) => localDateKey(when(w))));
  let cursor = dates.has(localDateKey(now)) ? now : previousDay(now),
    streak = 0;
  while (dates.has(localDateKey(cursor))) {
    streak++;
    cursor = previousDay(cursor);
  }
  const week = isoWeekKey(now);
  const weeklyCount = qualified.filter(
    (w) => isoWeekKey(when(w)) === week,
  ).length;
  const weekCounts: Record<string, number> = {};
  for (const w of qualified) {
    const k = isoWeekKey(when(w));
    weekCounts[k] = (weekCounts[k] ?? 0) + 1;
  }
  const goal = Math.max(1, snapshot.profile.weeklyGoal);
  const qualifiesWeek = (at: number) => {
    const k = isoWeekKey(at),
      n = weekCounts[k] ?? 0;
    return (
      n >= goal ||
      (n === goal - 1 && snapshot.profile.recoveryWeeks.includes(k))
    );
  };
  let weekCursor = qualifiesWeek(now) ? now : previousDay(now, 7),
    weeklyStreak = 0;
  while (qualified.length && qualifiesWeek(weekCursor)) {
    weeklyStreak++;
    weekCursor = previousDay(weekCursor, 7);
  }
  const checkin = snapshot.checkins
    .filter((c) => c.at <= now)
    .sort((a, b) => b.at - a.at)[0];
  const recovery = readinessByRegion(history, checkin?.painRegions ?? [], now);
  const rows = Object.entries(recovery).sort((a, b) => a[1] - b[1]);
  let readiness = Math.round(
    clamp(
      0.75 * rows[0][1] + (0.25 * sum(rows.slice(0, 3).map((x) => x[1]))) / 3,
      1,
      99,
    ),
  );
  if (checkin && now - checkin.at <= 48 * 3600000) {
    const signals = [];
    if (checkin.soreness >= 1 && checkin.soreness <= 5)
      signals.push(1 - (checkin.soreness - 1) / 4);
    if (checkin.sleep >= 1 && checkin.sleep <= 5)
      signals.push((checkin.sleep - 1) / 4);
    if (checkin.energy >= 1 && checkin.energy <= 5)
      signals.push((checkin.energy - 1) / 4);
    if (signals.length)
      readiness = clamp(
        readiness + Math.round((sum(signals) / signals.length - 0.5) * 30),
        1,
        99,
      );
  }
  const durableUnlocked = new Set(
    Object.keys(snapshot.profile.badgeUnlocks).filter((id) => id !== "s_rank"),
  );
  const unlocked = new Set(durableUnlocked);
  for (const id of onboardingBaseline?.supportedBadgeIds ?? [])
    unlocked.add(id);
  let progression = 0;
  for (const w of [...qualified].reverse()) {
    if (verifiedPrWorkouts.has(w.id)) progression++;
    else break;
  }
  const qualifiedVolume = sum(
    qualified.flatMap((w) =>
      w.exercises.flatMap((e) =>
        e.loggedSets
          .filter((s) => s.kind !== "warmup" && e.tracking === "weight_reps")
          .map((s) => s.weightKg * s.reps),
      ),
    ),
  );
  const earned: [string, boolean][] = [
    ["first_workout", qualified.length >= 1],
    ["streak_3", streak >= 3],
    ["first_rest_timer", snapshot.workouts.some((w) => w.restUsed)],
    ["first_plan", snapshot.plans.length > 0],
    ["workouts_10", qualified.length >= 10],
    ["consistency_4w", weeks.size >= 4],
    ["first_pr", verifiedPrWorkouts.size > 0],
    ["progressive_streak", progression >= 4],
    ["workouts_50", qualified.length >= 50],
    ["streak_30", streak >= 30],
    ["workouts_100", qualified.length >= 100],
    ["volume_milestone", qualifiedVolume >= 100000],
    [
      "member_365",
      !!qualified.length &&
        Math.floor((now - when(qualified[0])) / 86400000) >= 365,
    ],
  ];
  for (const [id, ok] of earned)
    if (ok) {
      unlocked.add(id);
      durableUnlocked.add(id);
    }
  return {
    xp,
    xpBreakdown: { selfReported: selfReportedXp, verified: verifiedXp },
    onboardingBaseline,
    trainingSignals,
    level,
    nextLevelXp,
    levelProgress: nextLevelXp ? remaining / nextLevelXp : 1,
    grade,
    streak,
    weeklyStreak,
    weeklyCount,
    creditedCount: qualified.length,
    volumeKg,
    prs: Object.values(prs).sort((a, b) => b.oneRmKg - a.oneRmKg),
    recovery,
    readiness,
    state:
      readiness >= 85 ? "ready" : readiness >= 60 ? "recovering" : "fatigued",
    limitingRegion: rows[0]?.[0],
    unlockedBadges: [...unlocked],
    durableUnlockedBadges: [...durableUnlocked],
    integrity,
    qualifyingWeeks: weeks.size,
    tenureDays,
  };
}
export function plateCalculation(
  loadKg: number,
  barKg: number,
  platesKg: number[],
) {
  const platesPerSide: { weightKg: number; quantity: number }[] = [];
  if (!Number.isFinite(loadKg) || !Number.isFinite(barKg) || barKg < 0)
    throw Error("Invalid load");
  let remaining = Math.round(((loadKg - barKg) / 2) * 100) / 100;
  if (remaining < 0)
    return {
      isValid: false,
      platesPerSide,
      achievedWeightKg: barKg,
      remainderKg: loadKg - barKg,
      achievable: barKg,
      remainder: loadKg - barKg,
    };
  // The web profile lists denominations rather than finite quantities; available pairs are unlimited.
  for (const weightKg of [...new Set(platesKg)]
    .filter((p) => Number.isFinite(p) && p > 0)
    .sort((a, b) => b - a)) {
    let quantity = 0;
    while (remaining >= weightKg - 0.001 && quantity < 1000) {
      quantity++;
      remaining = Math.round((remaining - weightKg) * 100) / 100;
    }
    if (quantity) platesPerSide.push({ weightKg, quantity });
  }
  const achievedWeightKg = Math.round((loadKg - remaining * 2) * 100) / 100,
    remainderKg = Math.round((loadKg - achievedWeightKg) * 100) / 100;
  return {
    isValid: remaining <= 0.001,
    platesPerSide,
    achievedWeightKg,
    remainderKg,
    achievable: achievedWeightKg,
    remainder: remainderKg,
  };
}
export function warmupTargets(targetKg: number, barKg = 20): WarmupTarget[] {
  if (!Number.isFinite(targetKg) || targetKg <= 0 || targetKg <= barKg)
    return [];
  const seen = new Set<number>();
  return (
    [
      [0.4, 8],
      [0.55, 5],
      [0.7, 3],
      [0.85, 1],
    ] as const
  )
    .map(([fraction, reps]) => ({
      id: crypto.randomUUID(),
      weightKg: Math.max(
        Math.round((targetKg * fraction) / 2.5) * 2.5,
        Math.min(barKg, targetKg),
      ),
      reps,
    }))
    .filter((t) => {
      if (t.weightKg >= targetKg || seen.has(t.weightKg)) return false;
      seen.add(t.weightKg);
      return true;
    });
}
/** Native ghost-set suggestion, not an automatic load prescription. Never used for cardio. */
export function progressionSuggestion(
  exercise: SessionExercise,
  unit: "kg" | "lb" = "kg",
): { weightKg: number; reps: number } | null {
  if (exercise.tracking.startsWith("duration")) return null;
  const sets = exercise.loggedSets.filter((s) => s.kind !== "warmup");
  if (!sets.length) return null;
  const top = sets.reduce((a, b) => (b.weightKg > a.weightKg ? b : a));
  if (top.weightKg > 0)
    return {
      weightKg: top.weightKg + (unit === "lb" ? 5 / 2.20462262185 : 2.5),
      reps: Math.trunc(top.reps),
    };
  return {
    weightKg: 0,
    reps: Math.trunc(Math.max(...sets.map((s) => s.reps))) + 2,
  };
}
const suggestionRegions: Record<string, string[]> = {
  Shoulders: [
    "lateral raise",
    "front raise",
    "face pull",
    "upright row",
    "shoulder",
    "overhead press",
    "military press",
    "ohp",
    "rear delt",
  ],
  Arms: [
    "bicep",
    "tricep",
    "curl",
    "pushdown",
    "arm extension",
    "skull crusher",
    "forearm",
    "wrist",
  ],
  Legs: [
    "squat",
    "leg",
    "lunge",
    "calf",
    "glute",
    "hip thrust",
    "hip abduction",
    "hip adduction",
    "rdl",
    "hamstring",
    "quad",
    "deadlift",
  ],
  Push: [
    "bench",
    "chest press",
    "press",
    "dip",
    "push up",
    "pushup",
    "fly",
    "flye",
    "chest",
  ],
  Pull: [
    "row",
    "pull up",
    "pullup",
    "pulldown",
    "lat",
    "shrug",
    "chin up",
    "chinup",
  ],
  Core: [
    "plank",
    "crunch",
    "ab",
    "oblique",
    "core",
    "sit up",
    "situp",
    "hollow",
    "russian twist",
  ],
};
/** Readiness input is 0..100 throughout the web API (native uses 0..1). */
export function suggestPlanDay(
  readiness: Record<string, number>,
  dayExerciseNames: string[][],
): number {
  const scores = dayExerciseNames.map((names) => {
    const values = names.flatMap((name) => {
      const padded = ` ${name
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, " ")
        .trim()} `;
      const region = Object.entries(suggestionRegions).find(([, words]) =>
        words.some((word) => padded.includes(` ${word} `)),
      )?.[0];
      return region && readiness[region] !== undefined
        ? [readiness[region] / 100]
        : [];
    });
    return values.length
      ? clamp(
          0.5 +
            ((sum(values) / values.length - 0.5) * values.length) /
              Math.max(1, names.length),
          0,
          1,
        )
      : 0.5;
  });
  return scores.length ? scores.indexOf(Math.max(...scores)) : 0;
}
