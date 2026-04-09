export function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substr(2, 9);
}

export function calculate1RM(weight, reps) {
  if (reps <= 0 || weight <= 0) return 0;
  return Math.round(weight * (1 + reps / 30));
}

export function formatDuration(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

export function formatTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export const DAY_COLORS = {
  push: '#FF4500',
  pull: '#0080FF',
  legs: '#00C170',
  upper: '#A020F0',
};

export const COLORS = {
  bg: '#080808',
  surface: '#0f0f0f',
  card: '#141414',
  border: '#1e1e1e',
  borderActive: '#333',
  text: '#f0f0f0',
  textSecondary: '#666',
  textMuted: '#333',
  accent: '#FF4500',
  gold: '#FFD700',
  danger: '#CC2222',
};
