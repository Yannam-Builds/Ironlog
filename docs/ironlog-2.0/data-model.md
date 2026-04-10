# IRONLOG 2.0 Data Model

## Core Tables
- `workout_sessions`
  - `id`, `occurred_at`, `plan_id`, `day_id`, `day_name`, `duration_seconds`, `total_sets`, `total_volume`, `is_deload`, `rating`, `performance_score`, `summary_text`
- `session_exercises`
  - `id`, `session_id`, `exercise_id`, `exercise_name`, `exercise_order`, `prescribed_sets`, `prescribed_reps`, `equipment`, `primary_muscle`, `muscle_payload_json`, `set_count`, `total_volume`, `best_e1rm`, `best_weight`, `note`, `exercise_profile_json`, `progression_json`, `plateau_json`, `deload_json`, `substitution_json`
- `session_sets`
  - `id`, `session_exercise_id`, `set_order`, `weight`, `reps`, `type`, `rpe`, `rir`, `note`, `orm`, `target_weight`, `target_reps`, `completed`, `is_warmup`, `volume`
- `plans`
  - `id`, `name`, `active_order`, `metadata_json`
- `plan_days`
  - `id`, `plan_id`, `name`, `label`, `color`, `tag`, `day_order`, `metadata_json`
- `plan_day_exercises`
  - `id`, `day_id`, `exercise_id`, `exercise_name`, `prescribed_sets`, `prescribed_reps`, `exercise_order`, `equipment`, `is_warmup`, `note`, `metadata_json`
- `bodyweight_entries`
  - `id`, `recorded_at`, `weight`, `source`
- `body_measurements`
  - `id`, `recorded_at`, `payload_json`
- `custom_exercises`
  - `id`, `name`, `data_json`

## Intelligence Tables
- `exercise_profiles`
  - `exercise_id`, `exercise_name`, `family`, `equipment_class`, `compound`, `unilateral`, `load_model`, `microload_step`, `rep_ceiling`, `confidence`, `profile_json`
- `exercise_muscle_contributions`
  - `exercise_id`, `muscle_key`, `weight`, `confidence`, `source`, `is_primary`
- `progression_suggestions`
  - `id`, `exercise_id`, `exercise_name`, `context_day_id`, `generated_at`, `action`, `target_weight`, `target_reps`, `rationale`, `confidence`, `payload_json`
- `plateau_events`
  - `id`, `exercise_id`, `exercise_name`, `detected_at`, `severity`, `recommendation`, `payload_json`
- `deload_recommendations`
  - `id`, `context_type`, `exercise_id`, `muscle_group`, `detected_at`, `recommendation`, `payload_json`
- `substitution_edges`
  - `source_exercise_id`, `target_exercise_id`, `rank`, `reason`, `payload_json`
- `session_insights`
  - `session_id`, `generated_at`, `summary_text`, `wins`, `losses`, `payload_json`
- `pr_events`
  - `id`, `exercise_id`, `exercise_name`, `session_id`, `occurred_at`, `pr_type`, `value`, `payload_json`
- `muscle_recovery_snapshots`
  - `id`, `window_key`, `snapshot_at`, `muscle_key`, `direct_sets`, `effective_sets`, `frequency`, `freshness`, `fatigue`, `status`, `confidence`
- `weekly_analytics_cache`
  - `id`, `window_key`, `payload_json`, `updated_at`
- `weekly_summaries`
  - `id`, `week_key`, `summary_text`, `payload_json`
- `streak_counters`
  - `id`, `counter_key`, `current_value`, `longest_value`, `updated_at`, `payload_json`
- `milestone_unlocks`
  - `id`, `milestone_key`, `unlocked_at`, `payload_json`
- `manual_recovery_entries`
  - `id`, `recorded_at`, `soreness`, `sleep_quality`, `energy`, `notes`
- `comparison_usage_log`
  - `id`, `used_at`, `object_name`, `category`, `total_kg`, `context_label`
- `app_meta`
  - `key`, `value`, `updated_at`

## Relationships
- One `workout_session` has many `session_exercises`.
- One `session_exercise` has many `session_sets`.
- One `plan` has many `plan_days`.
- One `plan_day` has many `plan_day_exercises`.
- One `exercise_profile` can have many `exercise_muscle_contributions`.

## Migration Notes
- Keep legacy AsyncStorage migrations intact.
- Add one-time SQLite import after legacy migrations complete.
- Verify migrated counts for plans, sessions, exercises, sets, and bodyweight before setting `@ironlog/v2SqliteMigrated`.
- Continue mirroring writes to legacy keys until backup/restore fully reads from SQLite-backed exports.
