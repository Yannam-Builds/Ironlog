function toDateKey(value) {
  const date = new Date(value || 0);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString().slice(0, 10);
}

function uniqueSortedDateKeys(rows = []) {
  return [...new Set(rows.map((row) => toDateKey(row?.date || row?.recordedAt || row)).filter(Boolean))]
    .sort();
}

function getCurrentStreakFromDateKeys(dateKeys = []) {
  if (!dateKeys.length) return 0;
  const today = toDateKey(new Date().toISOString());
  const yesterday = toDateKey(new Date(Date.now() - 86400000).toISOString());
  const latest = dateKeys[dateKeys.length - 1];
  if (latest !== today && latest !== yesterday) return 0;

  let streak = 1;
  for (let i = dateKeys.length - 1; i > 0; i -= 1) {
    const cur = new Date(`${dateKeys[i]}T00:00:00Z`).getTime();
    const prev = new Date(`${dateKeys[i - 1]}T00:00:00Z`).getTime();
    const diff = Math.round((cur - prev) / 86400000);
    if (diff === 1) streak += 1;
    else break;
  }
  return streak;
}

function getLongestStreakFromDateKeys(dateKeys = []) {
  if (!dateKeys.length) return 0;
  let best = 1;
  let current = 1;
  for (let i = 1; i < dateKeys.length; i += 1) {
    const cur = new Date(`${dateKeys[i]}T00:00:00Z`).getTime();
    const prev = new Date(`${dateKeys[i - 1]}T00:00:00Z`).getTime();
    const diff = Math.round((cur - prev) / 86400000);
    if (diff === 1) {
      current += 1;
      if (current > best) best = current;
    } else if (diff > 1) {
      current = 1;
    }
  }
  return best;
}

function countWorkingSets(history = []) {
  return history.reduce((sum, session) => {
    const sets = (session?.exercises || []).reduce((setSum, exercise) => {
      return setSum + (exercise?.sets || []).filter((setItem) => (setItem?.type || 'normal') !== 'warmup').length;
    }, 0);
    return sum + sets;
  }, 0);
}

export function computeStreaks({ history = [], bodyWeight = [] } = {}) {
  const workoutDays = uniqueSortedDateKeys(history);
  const bwDays = uniqueSortedDateKeys(bodyWeight.map((entry) => ({ date: entry?.date })));

  return {
    training: {
      current: getCurrentStreakFromDateKeys(workoutDays),
      longest: getLongestStreakFromDateKeys(workoutDays),
    },
    logging: {
      current: getCurrentStreakFromDateKeys(workoutDays),
      longest: getLongestStreakFromDateKeys(workoutDays),
    },
    bodyweight: {
      current: getCurrentStreakFromDateKeys(bwDays),
      longest: getLongestStreakFromDateKeys(bwDays),
    },
  };
}

export function buildWeeklySummary({ history = [], bodyWeight = [] } = {}) {
  const since = Date.now() - (7 * 86400000);
  const weekSessions = history.filter((session) => new Date(session?.date || 0).getTime() >= since);
  const workouts = weekSessions.length;
  const totalVolume = Math.round(weekSessions.reduce((sum, session) => sum + Number(session?.totalVolume || 0), 0));
  const totalSets = countWorkingSets(weekSessions);
  const prsHit = weekSessions.reduce((sum, session) => sum + ((session?.prEvents || []).length || 0), 0);
  const muscles = {};
  weekSessions.forEach((session) => {
    (session?.exercises || []).forEach((exercise) => {
      const key = String(
        exercise?.primaryMuscle
          || (Array.isArray(exercise?.primaryMuscles) ? exercise.primaryMuscles[0] : null)
          || exercise?.muscle
          || 'other'
      ).toLowerCase();
      const sets = (exercise?.sets || []).filter((setItem) => (setItem?.type || 'normal') !== 'warmup').length;
      muscles[key] = (muscles[key] || 0) + sets;
    });
  });
  const topMuscles = Object.entries(muscles).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([key]) => key);

  const bwRows = bodyWeight
    .filter((entry) => new Date(entry?.date || 0).getTime() >= since)
    .sort((a, b) => new Date(a?.date || 0) - new Date(b?.date || 0));
  const bwTrend = bwRows.length >= 2 ? Math.round((Number(bwRows[bwRows.length - 1].weight || 0) - Number(bwRows[0].weight || 0)) * 10) / 10 : 0;

  return {
    weekKey: toDateKey(new Date().toISOString()),
    workouts,
    totalVolume,
    totalSets,
    prsHit,
    topMuscles,
    bodyweightTrendKg: bwTrend,
    summaryLine: `${workouts} workouts, ${totalVolume.toLocaleString()} kg, ${prsHit} PRs, top focus: ${topMuscles[0] || 'balanced'}.`,
  };
}

const MILESTONES = [
  { key: 'first_pr', label: 'First PR', predicate: ({ prCount }) => prCount >= 1 },
  { key: 'workouts_10', label: '10 Workouts', predicate: ({ workouts }) => workouts >= 10 },
  { key: 'sets_100', label: '100 Sets Logged', predicate: ({ sets }) => sets >= 100 },
  { key: 'volume_1_ton', label: '1 Ton Lifted', predicate: ({ totalVolume }) => totalVolume >= 1000 },
];

export function evaluateMilestones({
  history = [],
  milestoneState = {},
} = {}) {
  const totalVolume = history.reduce((sum, session) => sum + Number(session?.totalVolume || 0), 0);
  const workouts = history.length;
  const sets = countWorkingSets(history);
  const prCount = history.reduce((sum, session) => sum + ((session?.prEvents || []).length || 0), 0);
  const nowIso = new Date().toISOString();

  const nextState = { ...(milestoneState || {}) };
  const unlocked = [];

  MILESTONES.forEach((milestone) => {
    if (nextState[milestone.key]) return;
    if (milestone.predicate({ totalVolume, workouts, sets, prCount })) {
      nextState[milestone.key] = {
        key: milestone.key,
        label: milestone.label,
        unlockedAt: nowIso,
      };
      unlocked.push(nextState[milestone.key]);
    }
  });

  return { unlocked, state: nextState };
}
