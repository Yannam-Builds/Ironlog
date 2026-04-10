function normalizeName(value) {
  return String(value || '').trim().toLowerCase();
}

function getComparableExercise(sessionExercise, exercise) {
  const a = sessionExercise?.exerciseId || sessionExercise?.id;
  const b = exercise?.exerciseId || exercise?.id;
  if (a && b && a === b) return true;
  return normalizeName(sessionExercise?.name) === normalizeName(exercise?.name);
}

function getWorkingSets(exercise) {
  return (exercise?.sets || []).filter((setItem) => (setItem?.type || 'normal') !== 'warmup');
}

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function estimateE1rm(weight, reps) {
  if (weight <= 0 || reps <= 0) return 0;
  return weight * (1 + (reps / 30));
}

function getSessionVolume(session) {
  return (session?.exercises || []).reduce((total, exercise) => {
    return total + getWorkingSets(exercise).reduce((sum, setItem) => {
      const weight = toNumber(setItem?.weight);
      const reps = toNumber(setItem?.reps);
      return sum + (weight > 0 && reps > 0 ? weight * reps : 0);
    }, 0);
  }, 0);
}

function getBestMetrics(exercise) {
  const workingSets = getWorkingSets(exercise);
  let bestWeight = 0;
  let bestReps = 0;
  let bestE1rm = 0;
  let bestVolume = 0;

  workingSets.forEach((setItem) => {
    const weight = toNumber(setItem?.weight);
    const reps = toNumber(setItem?.reps);
    const volume = weight * reps;
    const e1rm = toNumber(setItem?.orm) || estimateE1rm(weight, reps);
    if (weight > bestWeight) bestWeight = weight;
    if (reps > bestReps) bestReps = reps;
    if (e1rm > bestE1rm) bestE1rm = e1rm;
    if (volume > bestVolume) bestVolume = volume;
  });

  return { bestWeight, bestReps, bestE1rm, bestVolume };
}

function getHistoricalBest(history = [], exercise) {
  const baseline = {
    bestWeight: 0,
    bestReps: 0,
    bestE1rm: 0,
    bestVolume: 0,
  };

  history
    .filter((session) => !session?.isDeload)
    .forEach((session) => {
      const match = (session?.exercises || []).find((candidate) => getComparableExercise(candidate, exercise));
      if (!match) return;
      const best = getBestMetrics(match);
      baseline.bestWeight = Math.max(baseline.bestWeight, best.bestWeight);
      baseline.bestReps = Math.max(baseline.bestReps, best.bestReps);
      baseline.bestE1rm = Math.max(baseline.bestE1rm, best.bestE1rm);
      baseline.bestVolume = Math.max(baseline.bestVolume, best.bestVolume);
    });

  return baseline;
}

export function detectPREvents(session, history = []) {
  if (!session?.exercises?.length) return [];
  const events = [];

  session.exercises.forEach((exercise) => {
    const current = getBestMetrics(exercise);
    const previous = getHistoricalBest(history, exercise);
    const id = exercise.exerciseId || exercise.id || exercise.name;

    if (current.bestWeight > previous.bestWeight && current.bestWeight > 0) {
      events.push({
        id: `${id}:weight`,
        type: 'weight',
        exerciseId: id,
        exerciseName: exercise.name,
        value: current.bestWeight,
        previousValue: previous.bestWeight,
      });
    }
    if (current.bestReps > previous.bestReps && current.bestReps > 0) {
      events.push({
        id: `${id}:reps`,
        type: 'reps',
        exerciseId: id,
        exerciseName: exercise.name,
        value: current.bestReps,
        previousValue: previous.bestReps,
      });
    }
    if (current.bestE1rm > previous.bestE1rm && current.bestE1rm > 0) {
      events.push({
        id: `${id}:e1rm`,
        type: 'e1rm',
        exerciseId: id,
        exerciseName: exercise.name,
        value: Math.round(current.bestE1rm * 10) / 10,
        previousValue: Math.round(previous.bestE1rm * 10) / 10,
      });
    }
    if (current.bestVolume > previous.bestVolume && current.bestVolume > 0) {
      events.push({
        id: `${id}:volume`,
        type: 'volume',
        exerciseId: id,
        exerciseName: exercise.name,
        value: Math.round(current.bestVolume),
        previousValue: Math.round(previous.bestVolume),
      });
    }
  });

  return events;
}

export function computeWorkoutPerformanceScore(session, history = [], prEvents = []) {
  if (!session) return 0;
  const recent = history.slice(0, 8);
  const baselineVolume = recent.length
    ? recent.reduce((sum, item) => sum + toNumber(item.totalVolume || getSessionVolume(item)), 0) / recent.length
    : 0;
  const currentVolume = toNumber(session.totalVolume || getSessionVolume(session));
  const plannedSetCount = (session.exercises || []).reduce((sum, ex) => {
    return sum + Math.max(0, Number(ex?.prescribedSets || ex?.sets || 0));
  }, 0);
  const completedSetCount = (session.exercises || []).reduce((sum, ex) => sum + getWorkingSets(ex).length, 0);
  const completionRate = plannedSetCount > 0 ? Math.min(1, completedSetCount / plannedSetCount) : 1;
  const volumeRatio = baselineVolume > 0 ? Math.min(1.35, currentVolume / baselineVolume) : 1;
  const prBoost = Math.min(1, (prEvents.length || 0) / 5);

  const score = (
    (completionRate * 42) +
    (Math.max(0.65, volumeRatio) * 34) +
    (prBoost * 14) +
    (session?.isDeload ? 6 : 10)
  );

  return Math.round(Math.max(0, Math.min(100, score)));
}

export function buildExerciseTrend(history = [], exerciseRef) {
  const rows = history
    .map((session) => {
      const exercise = (session?.exercises || []).find((candidate) => getComparableExercise(candidate, exerciseRef));
      if (!exercise) return null;
      const metrics = getBestMetrics(exercise);
      const workingSets = getWorkingSets(exercise);
      const volume = workingSets.reduce((sum, setItem) => {
        return sum + (toNumber(setItem?.weight) * toNumber(setItem?.reps));
      }, 0);
      const avgReps = workingSets.length
        ? workingSets.reduce((sum, setItem) => sum + toNumber(setItem?.reps), 0) / workingSets.length
        : 0;
      return {
        date: session.date,
        volume,
        load: metrics.bestWeight,
        reps: Math.round(avgReps * 10) / 10,
        e1rm: Math.round(metrics.bestE1rm * 10) / 10,
      };
    })
    .filter(Boolean)
    .sort((a, b) => new Date(a.date) - new Date(b.date));

  const consistency = rows.length
    ? Math.min(1, rows.length / 8)
    : 0;

  return {
    points: rows,
    consistency: Math.round(consistency * 100),
  };
}

export function computeConsistencyMetrics({
  history = [],
  activePlan = null,
  bodyWeight = [],
} = {}) {
  const now = Date.now();
  const sevenDaysAgo = now - (7 * 86400000);
  const twentyEightDaysAgo = now - (28 * 86400000);

  const last7 = history.filter((session) => new Date(session?.date || 0).getTime() >= sevenDaysAgo);
  const last28 = history.filter((session) => new Date(session?.date || 0).getTime() >= twentyEightDaysAgo);

  const planDayIds = new Set((activePlan?.days || []).map((day) => day.id));
  const planAlignedSessions = history.filter((session) => planDayIds.has(session?.dayId));
  const planAdherence = history.length ? Math.round((planAlignedSessions.length / history.length) * 100) : 0;

  const exerciseCounts = {};
  history.forEach((session) => {
    (session?.exercises || []).forEach((exercise) => {
      const key = normalizeName(exercise?.exerciseId || exercise?.name);
      if (!key) return;
      exerciseCounts[key] = (exerciseCounts[key] || 0) + 1;
    });
  });
  const repeatConsistency = Object.keys(exerciseCounts).length
    ? Math.round(
        (Object.values(exerciseCounts).filter((count) => count >= 3).length / Object.keys(exerciseCounts).length) * 100
      )
    : 0;

  const bwLast28 = bodyWeight.filter((entry) => new Date(entry?.date || 0).getTime() >= twentyEightDaysAgo);
  const bodyweightLoggingConsistency = Math.min(100, Math.round((bwLast28.length / 8) * 100));

  return {
    workoutsPerWeek: Math.round((last28.length / 4) * 10) / 10,
    workoutsLast7d: last7.length,
    adherenceToProgram: planAdherence,
    exerciseRepeatConsistency: repeatConsistency,
    bodyweightLoggingConsistency,
  };
}

