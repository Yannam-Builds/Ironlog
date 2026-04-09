/**
 * Calculate estimated 1RM using Epley formula.
 * 1RM = weight × (1 + reps / 30)
 */
export function calculate1RM(weight, reps) {
  if (!weight || !reps || reps <= 0) return 0;
  if (reps === 1) return weight;
  return Math.round(weight * (1 + reps / 30));
}

/**
 * Convert weight between kg and lbs.
 */
export function convertWeight(value, from, to) {
  if (from === to) return value;
  if (from === 'kg' && to === 'lbs') return Math.round(value * 2.20462 * 10) / 10;
  if (from === 'lbs' && to === 'kg') return Math.round(value * 0.453592 * 10) / 10;
  return value;
}

/**
 * Format duration in seconds to human readable.
 * e.g. 3661 => "1h 1m 1s"
 */
export function formatDuration(seconds) {
  if (!seconds || seconds < 0) return '0s';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

/**
 * Format rest timer countdown as mm:ss
 */
export function formatRestTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/**
 * Calculate total volume for an array of sets.
 * Each set: { weight, reps, completed, isWarmup }
 * By default excludes warmup sets.
 */
export function calculateVolume(sets, includeWarmup = false) {
  if (!sets || sets.length === 0) return 0;
  return sets.reduce((total, set) => {
    if (!set.completed) return total;
    if (!includeWarmup && set.isWarmup) return total;
    const w = parseFloat(set.weight) || 0;
    const r = parseInt(set.reps) || 0;
    return total + w * r;
  }, 0);
}

/**
 * Calculate total volume for a full workout session.
 */
export function calculateSessionVolume(exercises, includeWarmup = false) {
  if (!exercises) return 0;
  return exercises.reduce((total, ex) => {
    return total + calculateVolume(ex.sets || [], includeWarmup);
  }, 0);
}

/**
 * Generate a simple UUID.
 */
export function generateId() {
  return (
    Math.random().toString(36).substr(2, 9) +
    Date.now().toString(36)
  );
}

/**
 * Get ordinal suffix for a number (1st, 2nd, 3rd, ...)
 */
export function ordinal(n) {
  const s = ['th', 'st', 'nd', 'rd'];
  const v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]);
}

/**
 * Get the day name from a date (Monday, Tuesday, etc.)
 */
export function getDayName(date = new Date()) {
  return date.toLocaleDateString('en-US', { weekday: 'long' });
}

/**
 * Calculate workout streak from history array.
 * Each session has a .date ISO string.
 */
export function calculateStreak(history) {
  if (!history || history.length === 0) return 0;

  const sorted = [...history].sort(
    (a, b) => new Date(b.date) - new Date(a.date),
  );

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  let streak = 0;
  let checkDate = new Date(today);

  const sessionDates = new Set(
    sorted.map((s) => {
      const d = new Date(s.date);
      d.setHours(0, 0, 0, 0);
      return d.toISOString().split('T')[0];
    }),
  );

  // Check today or yesterday to start streak
  const todayStr = today.toISOString().split('T')[0];
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  const yesterdayStr = yesterday.toISOString().split('T')[0];

  if (!sessionDates.has(todayStr) && !sessionDates.has(yesterdayStr)) {
    return 0;
  }

  if (!sessionDates.has(todayStr)) {
    checkDate = yesterday;
  }

  while (true) {
    const dateStr = checkDate.toISOString().split('T')[0];
    if (sessionDates.has(dateStr)) {
      streak++;
      checkDate.setDate(checkDate.getDate() - 1);
    } else {
      break;
    }
  }

  return streak;
}
