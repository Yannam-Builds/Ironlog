import * as SQLite from 'expo-sqlite';

export const TRAINING_DATABASE_NAME = 'ironlog_v2.db';
export const SQLITE_MIGRATION_MARKER_KEY = '@ironlog/v2SqliteMigrated';

let dbPromise = null;

const SCHEMA_SQL = `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS workout_sessions (
  id TEXT PRIMARY KEY NOT NULL,
  occurred_at TEXT NOT NULL,
  plan_id TEXT,
  day_id TEXT,
  day_name TEXT,
  duration_seconds INTEGER DEFAULT 0,
  total_sets INTEGER DEFAULT 0,
  total_volume REAL DEFAULT 0,
  is_deload INTEGER DEFAULT 0,
  rating INTEGER,
  performance_score REAL,
  summary_text TEXT,
  created_at TEXT DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS session_exercises (
  id TEXT PRIMARY KEY NOT NULL,
  session_id TEXT NOT NULL,
  exercise_id TEXT,
  exercise_name TEXT NOT NULL,
  exercise_order INTEGER DEFAULT 0,
  prescribed_sets INTEGER,
  prescribed_reps INTEGER,
  equipment TEXT,
  primary_muscle TEXT,
  muscle_payload_json TEXT,
  set_count INTEGER DEFAULT 0,
  total_volume REAL DEFAULT 0,
  best_e1rm REAL DEFAULT 0,
  best_weight REAL DEFAULT 0,
  note TEXT,
  exercise_profile_json TEXT,
  progression_json TEXT,
  plateau_json TEXT,
  deload_json TEXT,
  substitution_json TEXT,
  metadata_json TEXT,
  FOREIGN KEY (session_id) REFERENCES workout_sessions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS session_sets (
  id TEXT PRIMARY KEY NOT NULL,
  session_exercise_id TEXT NOT NULL,
  set_order INTEGER DEFAULT 0,
  weight REAL DEFAULT 0,
  reps INTEGER DEFAULT 0,
  type TEXT,
  rpe REAL,
  rir REAL,
  note TEXT,
  orm REAL DEFAULT 0,
  target_weight REAL,
  target_reps INTEGER,
  completed INTEGER DEFAULT 1,
  is_warmup INTEGER DEFAULT 0,
  volume REAL DEFAULT 0,
  FOREIGN KEY (session_exercise_id) REFERENCES session_exercises(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plans (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  active_order INTEGER DEFAULT 0,
  metadata_json TEXT
);

CREATE TABLE IF NOT EXISTS plan_days (
  id TEXT PRIMARY KEY NOT NULL,
  plan_id TEXT NOT NULL,
  name TEXT,
  label TEXT,
  color TEXT,
  tag TEXT,
  day_order INTEGER DEFAULT 0,
  metadata_json TEXT,
  FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plan_day_exercises (
  id TEXT PRIMARY KEY NOT NULL,
  day_id TEXT NOT NULL,
  exercise_id TEXT,
  exercise_name TEXT NOT NULL,
  prescribed_sets INTEGER,
  prescribed_reps INTEGER,
  exercise_order INTEGER DEFAULT 0,
  equipment TEXT,
  is_warmup INTEGER DEFAULT 0,
  note TEXT,
  metadata_json TEXT,
  FOREIGN KEY (day_id) REFERENCES plan_days(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bodyweight_entries (
  id TEXT PRIMARY KEY NOT NULL,
  recorded_at TEXT NOT NULL,
  weight REAL NOT NULL,
  source TEXT DEFAULT 'manual'
);

CREATE TABLE IF NOT EXISTS body_measurements (
  id TEXT PRIMARY KEY NOT NULL,
  recorded_at TEXT NOT NULL,
  payload_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS custom_exercises (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  data_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS exercise_profiles (
  exercise_id TEXT PRIMARY KEY NOT NULL,
  exercise_name TEXT NOT NULL,
  family TEXT,
  equipment_class TEXT,
  compound INTEGER DEFAULT 0,
  unilateral INTEGER DEFAULT 0,
  load_model TEXT,
  microload_step REAL,
  rep_ceiling INTEGER,
  confidence REAL,
  profile_json TEXT
);

CREATE TABLE IF NOT EXISTS exercise_muscle_contributions (
  exercise_id TEXT NOT NULL,
  muscle_key TEXT NOT NULL,
  weight REAL DEFAULT 0,
  confidence REAL DEFAULT 0,
  source TEXT,
  is_primary INTEGER DEFAULT 0,
  PRIMARY KEY (exercise_id, muscle_key)
);

CREATE TABLE IF NOT EXISTS progression_suggestions (
  id TEXT PRIMARY KEY NOT NULL,
  exercise_id TEXT,
  exercise_name TEXT,
  context_day_id TEXT,
  generated_at TEXT NOT NULL,
  action TEXT,
  target_weight REAL,
  target_reps INTEGER,
  rationale TEXT,
  confidence REAL,
  payload_json TEXT
);

CREATE TABLE IF NOT EXISTS plateau_events (
  id TEXT PRIMARY KEY NOT NULL,
  exercise_id TEXT,
  exercise_name TEXT,
  detected_at TEXT NOT NULL,
  severity TEXT,
  recommendation TEXT,
  payload_json TEXT
);

CREATE TABLE IF NOT EXISTS deload_recommendations (
  id TEXT PRIMARY KEY NOT NULL,
  context_type TEXT,
  exercise_id TEXT,
  muscle_group TEXT,
  detected_at TEXT NOT NULL,
  recommendation TEXT,
  payload_json TEXT
);

CREATE TABLE IF NOT EXISTS substitution_edges (
  source_exercise_id TEXT NOT NULL,
  target_exercise_id TEXT NOT NULL,
  rank REAL DEFAULT 0,
  reason TEXT,
  payload_json TEXT,
  PRIMARY KEY (source_exercise_id, target_exercise_id)
);

CREATE TABLE IF NOT EXISTS session_insights (
  session_id TEXT PRIMARY KEY NOT NULL,
  generated_at TEXT NOT NULL,
  summary_text TEXT,
  wins INTEGER DEFAULT 0,
  losses INTEGER DEFAULT 0,
  payload_json TEXT
);

CREATE TABLE IF NOT EXISTS pr_events (
  id TEXT PRIMARY KEY NOT NULL,
  exercise_id TEXT,
  exercise_name TEXT,
  session_id TEXT,
  occurred_at TEXT NOT NULL,
  pr_type TEXT,
  value REAL,
  payload_json TEXT
);

CREATE TABLE IF NOT EXISTS muscle_recovery_snapshots (
  id TEXT PRIMARY KEY NOT NULL,
  window_key TEXT NOT NULL,
  snapshot_at TEXT NOT NULL,
  muscle_key TEXT NOT NULL,
  direct_sets REAL DEFAULT 0,
  effective_sets REAL DEFAULT 0,
  frequency REAL DEFAULT 0,
  freshness REAL DEFAULT 0,
  fatigue REAL DEFAULT 0,
  status TEXT,
  confidence REAL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS weekly_analytics_cache (
  id TEXT PRIMARY KEY NOT NULL,
  window_key TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS weekly_summaries (
  id TEXT PRIMARY KEY NOT NULL,
  week_key TEXT NOT NULL,
  summary_text TEXT,
  payload_json TEXT
);

CREATE TABLE IF NOT EXISTS streak_counters (
  id TEXT PRIMARY KEY NOT NULL,
  counter_key TEXT NOT NULL,
  current_value INTEGER DEFAULT 0,
  longest_value INTEGER DEFAULT 0,
  updated_at TEXT NOT NULL,
  payload_json TEXT
);

CREATE TABLE IF NOT EXISTS milestone_unlocks (
  id TEXT PRIMARY KEY NOT NULL,
  milestone_key TEXT UNIQUE NOT NULL,
  unlocked_at TEXT NOT NULL,
  payload_json TEXT
);

CREATE TABLE IF NOT EXISTS manual_recovery_entries (
  id TEXT PRIMARY KEY NOT NULL,
  recorded_at TEXT NOT NULL,
  soreness INTEGER,
  sleep_quality INTEGER,
  energy INTEGER,
  notes TEXT
);

CREATE TABLE IF NOT EXISTS comparison_usage_log (
  id TEXT PRIMARY KEY NOT NULL,
  used_at TEXT NOT NULL,
  object_name TEXT,
  category TEXT,
  total_kg REAL DEFAULT 0,
  context_label TEXT
);

CREATE TABLE IF NOT EXISTS app_meta (
  key TEXT PRIMARY KEY NOT NULL,
  value TEXT,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_occurred_at ON workout_sessions(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_session_exercises_session_id ON session_exercises(session_id, exercise_order);
CREATE INDEX IF NOT EXISTS idx_session_sets_exercise_id ON session_sets(session_exercise_id, set_order);
CREATE INDEX IF NOT EXISTS idx_plan_days_plan_id ON plan_days(plan_id, day_order);
CREATE INDEX IF NOT EXISTS idx_plan_day_exercises_day_id ON plan_day_exercises(day_id, exercise_order);
CREATE INDEX IF NOT EXISTS idx_bodyweight_recorded_at ON bodyweight_entries(recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_progression_exercise_id ON progression_suggestions(exercise_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_plateau_exercise_id ON plateau_events(exercise_id, detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_deload_exercise_id ON deload_recommendations(exercise_id, detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_pr_events_exercise_id ON pr_events(exercise_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_recovery_window_key ON muscle_recovery_snapshots(window_key, snapshot_at DESC, muscle_key);
CREATE INDEX IF NOT EXISTS idx_comparison_usage_used_at ON comparison_usage_log(used_at DESC);
`;

export async function getTrainingDatabase() {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync(TRAINING_DATABASE_NAME);
  }
  return dbPromise;
}

export async function ensureTrainingDatabase() {
  const db = await getTrainingDatabase();
  await db.execAsync(SCHEMA_SQL);
  return db;
}

export async function setAppMeta(key, value) {
  const db = await ensureTrainingDatabase();
  await db.runAsync(
    `INSERT INTO app_meta (key, value, updated_at)
     VALUES (?, ?, ?)
     ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
    key,
    value == null ? null : String(value),
    new Date().toISOString()
  );
}

export async function getAppMeta(key) {
  const db = await ensureTrainingDatabase();
  const row = await db.getFirstAsync('SELECT value FROM app_meta WHERE key = ?', key);
  return row?.value ?? null;
}

export async function runInTransaction(task) {
  const db = await ensureTrainingDatabase();
  return db.withTransactionAsync(() => task(db));
}
